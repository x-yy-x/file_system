package com.fs.fs;

import com.fs.driver.DiskDriver;
import com.fs.driver.Serializer;
import com.fs.manager.*;
import com.fs.model.*;
import java.util.List;

/**
 * 文件系统核心 API — 协调所有 Manager，对外提供统一的文件系统操作。
 *
 * 职责：
 * - 虚拟磁盘的格式化、保存、加载
 * - 目录操作（mkdir/rmdir/ls/cd/pwd/tree）
 * - 文件操作（create/open/close/read/write/delete）
 * - 附加操作（rename/copy/move/stat）
 * - 系统信息查询
 */
public class FileSystem {
    private final DiskDriver disk;
    private final BitmapManager bitmapMgr;
    private final InodeManager inodeMgr;
    private final FileAllocator fileAllocator;
    private final PathResolver pathResolver;
    private final DirectoryManager dirMgr;

    private static final int MAX_OPEN_FILES = 64;
    private final FileDescriptor[] fdTable;

    private SuperBlock superBlock;

    public FileSystem(String imagePath) {
        this.disk = new DiskDriver(imagePath);
        this.bitmapMgr = new BitmapManager(disk);
        this.inodeMgr = new InodeManager(disk, bitmapMgr);
        this.fileAllocator = new FileAllocator(disk, bitmapMgr, inodeMgr);
        this.pathResolver = new PathResolver();
        this.dirMgr = new DirectoryManager(disk, inodeMgr, fileAllocator, pathResolver);
        this.fdTable = new FileDescriptor[MAX_OPEN_FILES];
    }

    // ==================== 初始化与持久化 ====================

    /** 初始化文件系统：尝试加载已有磁盘映像，否则格式化新的。 */
    public boolean init() {
        if (disk.loadFromFile()) {
            superBlock = Serializer.readSuperBlock(disk);
            if (superBlock.isValid()) {
                bitmapMgr.loadBitmap();
                dirMgr.initRootDir();
                return true;
            }
        }
        // 加载失败，格式化新磁盘
        format();
        return false;
    }

    /** 格式化虚拟磁盘。 */
    public void format() {
        disk.format();
        superBlock = new SuperBlock(
                DiskDriver.TOTAL_BLOCKS, DiskDriver.BLOCK_SIZE,
                DiskDriver.BITMAP_START, DiskDriver.BITMAP_BLOCKS,
                DiskDriver.INODE_START, DiskDriver.INODE_BLOCKS,
                DiskDriver.DATA_START, DiskDriver.TOTAL_INODES,
                DiskDriver.ROOT_INODE
        );
        Serializer.writeSuperBlock(disk, superBlock);
        bitmapMgr.initBitmap();
        bitmapMgr.saveBitmap();
        dirMgr.initRootDir();
    }

    /** 保存文件系统状态到磁盘文件。 */
    public void save() {
        // 更新超级块
        superBlock.freeBlockCount = bitmapMgr.getFreeBlockCount();
        superBlock.freeInodeCount = getFreeInodeCount();
        Serializer.writeSuperBlock(disk, superBlock);
        // 写回位图
        bitmapMgr.saveBitmap();
        // 持久化到文件
        disk.saveToFile();
    }

    /** 保存并退出。 */
    public void exit() {
        save();
    }

    /** 加载文件系统。 */
    public boolean load() {
        return init();
    }

    // ==================== 目录操作 ====================

    public boolean mkdir(String path) { return dirMgr.mkdir(path); }

    public boolean rmdir(String path) { return dirMgr.rmdir(path); }

    public List<EntryInfo> ls(String path) { return dirMgr.ls(path); }

    public boolean cd(String path) { return dirMgr.cd(path); }

    public String pwd() { return dirMgr.pwd(); }

    public String tree(String path) { return dirMgr.tree(path); }

    // ==================== 文件操作 ====================

    /** 创建文件。 */
    public boolean create(String path) {
        String[] components = pathResolver.splitPath(path);
        if (components.length == 0) return false;

        String fileName = components[components.length - 1];
        if (!pathResolver.validateFileName(fileName)) return false;

        int[] info = pathResolver.resolveParentAndName(path, dirMgr.getCurrentDirInode(), dirMgr);
        if (info == null) return false;
        int parentInode = info[0];

        // 检查同名是否已存在
        if (dirMgr.findEntry(parentInode, fileName) != null) return false;

        // 分配 inode
        int inodeNum = inodeMgr.allocInode((byte) 1);
        if (inodeNum < 0) return false;

        // 添加目录项
        DirectoryEntry entry = new DirectoryEntry(inodeNum, fileName, (byte) 1);
        if (!dirMgr.addEntry(parentInode, entry)) {
            inodeMgr.freeInode(inodeNum);
            return false;
        }
        return true;
    }

    /** 打开文件。 */
    public int open(String path, int mode) {
        int[] info = pathResolver.resolveParentAndName(path, dirMgr.getCurrentDirInode(), dirMgr);
        if (info == null) return -1;
        int parentInode = info[0];

        String[] components = pathResolver.splitPath(path);
        String fileName = components[components.length - 1];

        DirectoryEntry entry = dirMgr.findEntry(parentInode, fileName);
        if (entry == null) return -1;
        if (entry.fileType != 1) return -1;

        // 检查是否已打开
        for (int i = 0; i < MAX_OPEN_FILES; i++) {
            if (fdTable[i] != null && fdTable[i].inodeNumber == entry.inodeNumber) {
                return -1; // 文件已打开
            }
        }

        // 找空 fd 槽位
        for (int i = 0; i < MAX_OPEN_FILES; i++) {
            if (fdTable[i] == null) {
                fdTable[i] = new FileDescriptor(i, entry.inodeNumber, mode);
                return i;
            }
        }
        return -1; // 文件描述符表满
    }

    /** 关闭文件。 */
    public boolean close(int fd) {
        if (fd < 0 || fd >= MAX_OPEN_FILES || fdTable[fd] == null) return false;
        fdTable[fd] = null;
        return true;
    }

    /** 读文件。 */
    public byte[] read(int fd, int size) {
        if (fd < 0 || fd >= MAX_OPEN_FILES || fdTable[fd] == null) return null;
        FileDescriptor fdesc = fdTable[fd];
        if (fdesc.mode == FileDescriptor.WRITE) return null;

        byte[] data = fileAllocator.readFileData(fdesc.inodeNumber, fdesc.offset, size);
        if (data != null) fdesc.offset += data.length;
        return data;
    }

    /** 写文件。 */
    public int write(int fd, byte[] data) {
        if (fd < 0 || fd >= MAX_OPEN_FILES || fdTable[fd] == null) return -1;
        FileDescriptor fdesc = fdTable[fd];
        if (fdesc.mode == FileDescriptor.READ) return -1;

        int written = fileAllocator.writeFileData(fdesc.inodeNumber, fdesc.offset, data);
        if (written > 0) {
            fdesc.offset += written;
            fdesc.dirty = true;
        }
        return written;
    }

    /** 删除文件。 */
    public boolean delete(String path) {
        int[] info = pathResolver.resolveParentAndName(path, dirMgr.getCurrentDirInode(), dirMgr);
        if (info == null) return false;
        int parentInode = info[0];

        String[] components = pathResolver.splitPath(path);
        String fileName = components[components.length - 1];

        DirectoryEntry entry = dirMgr.findEntry(parentInode, fileName);
        if (entry == null) return false;
        if (entry.fileType != 1) return false;

        // 检查文件是否已打开
        for (FileDescriptor fd : fdTable) {
            if (fd != null && fd.inodeNumber == entry.inodeNumber) {
                return false; // 文件已打开不能删除
            }
        }

        // 释放 inode（含所有盘块）
        inodeMgr.freeInode(entry.inodeNumber);
        // 移除目录项
        dirMgr.removeEntry(parentInode, fileName);
        return true;
    }

    // ==================== 附加操作 ====================

    /** 重命名文件或目录。 */
    public boolean rename(String oldPath, String newPath) {
        // 解析旧路径
        int[] oldInfo = pathResolver.resolveParentAndName(oldPath, dirMgr.getCurrentDirInode(), dirMgr);
        if (oldInfo == null) return false;
        int oldParent = oldInfo[0];
        String[] oldComponents = pathResolver.splitPath(oldPath);
        String oldName = oldComponents[oldComponents.length - 1];

        DirectoryEntry entry = dirMgr.findEntry(oldParent, oldName);
        if (entry == null) return false;

        // 解析新路径（仅检查新文件名合法性）
        int[] newInfo = pathResolver.resolveParentAndName(newPath, dirMgr.getCurrentDirInode(), dirMgr);
        if (newInfo == null) return false;
        int newParent = newInfo[0];
        String[] newComponents = pathResolver.splitPath(newPath);
        String newName = newComponents[newComponents.length - 1];

        if (!pathResolver.validateFileName(newName)) return false;

        // 检查新路径是否已存在
        if (dirMgr.findEntry(newParent, newName) != null) return false;

        if (oldParent == newParent) {
            // 同目录重命名，直接更新目录项
            entry.fileName = newName;
            entry.nameLen = (short) newName.length();
            return dirMgr.updateEntry(oldParent, oldName, entry);
        } else {
            // 跨目录重命名 = 复制 + 删除
            return false; // 暂不支持跨目录重命名
        }
    }

    /** 复制文件。 */
    public boolean copy(String srcPath, String dstPath) {
        // 解析源文件
        int[] srcInfo = pathResolver.resolveParentAndName(srcPath, dirMgr.getCurrentDirInode(), dirMgr);
        if (srcInfo == null) return false;
        String[] srcComponents = pathResolver.splitPath(srcPath);
        String srcName = srcComponents[srcComponents.length - 1];

        // 找到源 parent 的目录 inode
        int srcParentInode = resolveDirInodeFromPath(srcPath, true);
        if (srcParentInode < 0) return false;

        DirectoryEntry srcEntry = dirMgr.findEntry(srcParentInode, srcName);
        if (srcEntry == null || srcEntry.fileType != 1) return false;

        // 读源文件数据
        Inode srcInode = inodeMgr.readInode(srcEntry.inodeNumber);
        byte[] data = fileAllocator.readFileData(srcEntry.inodeNumber, 0, srcInode.fileSize);

        // 在目标路径创建新文件
        int[] dstInfo = pathResolver.resolveParentAndName(dstPath, dirMgr.getCurrentDirInode(), dirMgr);
        if (dstInfo == null) return false;
        String[] dstComponents = pathResolver.splitPath(dstPath);
        String dstName = dstComponents[dstComponents.length - 1];
        int dstParentInode = dstInfo[0];

        if (!pathResolver.validateFileName(dstName)) return false;
        if (dirMgr.findEntry(dstParentInode, dstName) != null) return false;

        int newInodeNum = inodeMgr.allocInode((byte) 1);
        if (newInodeNum < 0) return false;

        DirectoryEntry newEntry = new DirectoryEntry(newInodeNum, dstName, (byte) 1);
        if (!dirMgr.addEntry(dstParentInode, newEntry)) {
            inodeMgr.freeInode(newInodeNum);
            return false;
        }

        // 写数据到新文件
        if (data.length > 0) {
            int written = fileAllocator.writeFileData(newInodeNum, 0, data);
            if (written != data.length) {
                delete(dstPath);
                return false;
            }
        }
        return true;
    }

    /** 移动文件。 */
    public boolean move(String srcPath, String dstPath) {
        if (!copy(srcPath, dstPath)) return false;
        return delete(srcPath);
    }

    /** 查看文件/目录详细信息。 */
    public FileStat stat(String path) {
        int[] info = pathResolver.resolveParentAndName(path, dirMgr.getCurrentDirInode(), dirMgr);
        if (info == null) return null;
        int parentInode = info[0];
        String[] components = pathResolver.splitPath(path);
        String name = components[components.length - 1];

        DirectoryEntry entry = dirMgr.findEntry(parentInode, name);
        if (entry == null) return null;

        Inode inode = inodeMgr.readInode(entry.inodeNumber);

        // 计算占用块数
        int blockCount = (inode.fileSize + DiskDriver.BLOCK_SIZE - 1) / DiskDriver.BLOCK_SIZE;
        if (blockCount == 0 && inode.fileSize > 0) blockCount = 1;

        return new FileStat(
                entry.fileName, entry.fileType,
                inode.fileSize, entry.inodeNumber,
                inode.createTime, inode.modifyTime,
                inode.linkCount, blockCount
        );
    }

    // ==================== 系统管理 ====================

    /** 获取系统信息。 */
    public SystemInfo getInfo() {
        return new SystemInfo(
                DiskDriver.TOTAL_BLOCKS,
                bitmapMgr.getFreeBlockCount(),
                DiskDriver.TOTAL_INODES,
                getFreeInodeCount(),
                DiskDriver.BLOCK_SIZE
        );
    }

    // ==================== 内部方法 ====================

    private int getFreeInodeCount() {
        int count = 0;
        for (int i = 0; i < DiskDriver.TOTAL_INODES; i++) {
            Inode inode = inodeMgr.readInode(i);
            if (inode.isFree()) count++;
        }
        return count;
    }

    /** 解析路径，获取父目录的 inode。 */
    private int resolveDirInodeFromPath(String path, boolean isParent) {
        String[] components = pathResolver.splitPath(path);
        if (components.length == 0) return -1;

        boolean isAbsolute = pathResolver.isAbsolutePath(path);
        int current = isAbsolute ? DiskDriver.ROOT_INODE : dirMgr.getCurrentDirInode();
        int limit = isParent ? components.length - 1 : components.length;

        for (int i = 0; i < limit; i++) {
            if (components[i].equals("..")) {
                // find parent
                Inode inode = inodeMgr.readInode(current);
                current = dirMgr.findSubDirInode(current, "..");
                if (current < 0) return -1;
            } else if (!components[i].equals(".") && !components[i].isEmpty()) {
                current = dirMgr.findSubDirInode(current, components[i]);
                if (current < 0) return -1;
            }
        }
        return current;
    }
}
