package com.fs.manager;

import com.fs.driver.DiskDriver;
import com.fs.driver.Serializer;
import com.fs.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 目录管理器 — 维护多级树形目录结构，提供目录项的增删改查。
 *
 * 目录本身是一个"特殊文件"，其数据块中存放 DirectoryEntry 数组。
 * 根目录固定为 inode 0。
 */
public class DirectoryManager {
    private final DiskDriver disk;
    private final InodeManager inodeMgr;
    private final FileAllocator fileAllocator;
    private final PathResolver pathResolver;
    private int currentDirInode; // 当前工作目录 inode 号

    public DirectoryManager(DiskDriver disk, InodeManager inodeMgr,
                            FileAllocator fileAllocator, PathResolver pathResolver) {
        this.disk = disk;
        this.inodeMgr = inodeMgr;
        this.fileAllocator = fileAllocator;
        this.pathResolver = pathResolver;
        this.currentDirInode = DiskDriver.ROOT_INODE;
    }

    /** 初始化根目录。 */
    public void initRootDir() {
        // 根目录 inode 已经在 allocInode 时初始化
        Inode rootInode = inodeMgr.readInode(DiskDriver.ROOT_INODE);
        if (rootInode.isFree()) {
            rootInode.inodeNumber = DiskDriver.ROOT_INODE;
            rootInode.fileType = 2; // 目录
            rootInode.linkCount = 1;
            rootInode.createTime = (int) (System.currentTimeMillis() / 1000);
            rootInode.modifyTime = rootInode.createTime;
            inodeMgr.writeInode(rootInode);
        }
        currentDirInode = DiskDriver.ROOT_INODE;
    }

    /** 创建目录。 */
    public boolean mkdir(String path) {
        // 对于 mkdir，需要创建目录并分配 inode
        String[] components = pathResolver.splitPath(path);
        if (components.length == 0) return false;

        String dirName = components[components.length - 1];
        if (!pathResolver.validateFileName(dirName)) return false;

        // 检查是否已存在
        int[] parentInfo = pathResolver.resolveParentAndName(path, currentDirInode, this);
        if (parentInfo == null) return false;
        int parentInode = parentInfo[0];

        // 检查同名是否已存在
        if (findEntry(parentInode, dirName) != null) return false;

        // 分配 inode
        int inodeNum = inodeMgr.allocInode((byte) 2);
        if (inodeNum < 0) return false;

        // 添加目录项到父目录
        DirectoryEntry entry = new DirectoryEntry(inodeNum, dirName, (byte) 2);
        if (!addEntry(parentInode, entry)) {
            inodeMgr.freeInode(inodeNum);
            return false;
        }
        return true;
    }

    /** 删除空目录。 */
    public boolean rmdir(String path) {
        int[] info = pathResolver.resolveParentAndName(path, currentDirInode, this);
        if (info == null) return false;
        int parentInode = info[0];

        String[] components = pathResolver.splitPath(path);
        String dirName = components[components.length - 1];

        DirectoryEntry entry = findEntry(parentInode, dirName);
        if (entry == null) return false;
        if (entry.fileType != 2) return false;

        // 检查目录是否为空
        if (!isDirectoryEmpty(entry.inodeNumber)) return false;

        // 释放 inode
        inodeMgr.freeInode(entry.inodeNumber);
        // 移除目录项
        removeEntry(parentInode, dirName);
        return true;
    }

    /** 列出目录内容。 */
    public List<EntryInfo> ls(String path) {
        int targetInode = currentDirInode;
        if (path != null && !path.isEmpty() && !path.equals(".")) {
            targetInode = resolvePathToInode(path);
        }
        if (targetInode < 0) return null;

        Inode dirInode = inodeMgr.readInode(targetInode);
        if (!dirInode.isDirectory()) return null;

        List<EntryInfo> entries = new ArrayList<>();
        byte[] data = fileAllocator.readFileData(targetInode, 0, dirInode.fileSize);
        int entryCount = data.length / DirectoryEntry.SIZE;

        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(data, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType != 0 && !de.fileName.isEmpty()) {
                Inode inode = inodeMgr.readInode(de.inodeNumber);
                entries.add(new EntryInfo(de.fileName, de.fileType, inode.fileSize, inode.modifyTime));
            }
        }
        return entries;
    }

    /** 切换当前目录。 */
    public boolean cd(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            currentDirInode = DiskDriver.ROOT_INODE;
            return true;
        }

        int targetInode = resolvePathToInode(path);
        if (targetInode < 0) return false;

        Inode inode = inodeMgr.readInode(targetInode);
        if (!inode.isDirectory()) return false;

        currentDirInode = targetInode;
        return true;
    }

    /** 获取当前工作目录的完整路径字符串。 */
    public String pwd() {
        return getFullPath(currentDirInode);
    }

    /** 获取当前目录 inode 号。 */
    public int getCurrentDirInode() {
        return currentDirInode;
    }

    /** 在指定目录中查找条目。 */
    public DirectoryEntry findEntry(int dirInodeNum, String name) {
        Inode dirInode = inodeMgr.readInode(dirInodeNum);
        if (!dirInode.isDirectory()) return null;

        byte[] data = fileAllocator.readFileData(dirInodeNum, 0, dirInode.fileSize);
        int entryCount = data.length / DirectoryEntry.SIZE;

        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(data, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType != 0 && de.fileName.equals(name)) {
                return de;
            }
        }
        return null;
    }

    /** 查找子目录的 inode 号（用于路径导航）。 */
    public int findSubDirInode(int dirInodeNum, String name) {
        if (name == null || name.isEmpty()) return dirInodeNum;
        if (name.equals(".")) return dirInodeNum;
        if (name.equals("..")) {
            // 找到当前目录的父目录
            // 对于根目录，.. 指向自己
            if (dirInodeNum == DiskDriver.ROOT_INODE) return dirInodeNum;
            // 扫描根目录查找当前目录的父目录
            return findParentInode(dirInodeNum);
        }

        DirectoryEntry entry = findEntry(dirInodeNum, name);
        if (entry == null) return -1;
        return entry.inodeNumber;
    }

    /** 添加目录项到目录。 */
    public boolean addEntry(int dirInodeNum, DirectoryEntry entry) {
        Inode dirInode = inodeMgr.readInode(dirInodeNum);
        if (!dirInode.isDirectory()) return false;

        int currentSize = dirInode.fileSize;
        byte[] existingData = currentSize > 0
                ? fileAllocator.readFileData(dirInodeNum, 0, currentSize)
                : new byte[0];

        // 检查是否有空位
        int entryCount = existingData.length / DirectoryEntry.SIZE;
        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(existingData, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType == 0 || de.fileName.isEmpty()) {
                // 重用空位
                byte[] newEntryData = Serializer.dirEntryToBytes(entry);
                System.arraycopy(newEntryData, 0, existingData, i * DirectoryEntry.SIZE, DirectoryEntry.SIZE);
                fileAllocator.writeFileData(dirInodeNum, 0, existingData);
                return true;
            }
        }

        // 追加新目录项
        byte[] newData = new byte[existingData.length + DirectoryEntry.SIZE];
        System.arraycopy(existingData, 0, newData, 0, existingData.length);
        byte[] entryBytes = Serializer.dirEntryToBytes(entry);
        System.arraycopy(entryBytes, 0, newData, existingData.length, DirectoryEntry.SIZE);

        int written = fileAllocator.writeFileData(dirInodeNum, 0, newData);
        return written == newData.length;
    }

    /** 从目录中移除一个目录项。 */
    public boolean removeEntry(int dirInodeNum, String name) {
        Inode dirInode = inodeMgr.readInode(dirInodeNum);
        if (!dirInode.isDirectory()) return false;

        byte[] data = fileAllocator.readFileData(dirInodeNum, 0, dirInode.fileSize);
        int entryCount = data.length / DirectoryEntry.SIZE;

        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(data, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType != 0 && de.fileName.equals(name)) {
                // 清空该目录项
                java.util.Arrays.fill(data, i * DirectoryEntry.SIZE, (i + 1) * DirectoryEntry.SIZE, (byte) 0);
                fileAllocator.writeFileData(dirInodeNum, 0, data);
                return true;
            }
        }
        return false;
    }

    /** 更新目录项（重命名时使用）。 */
    public boolean updateEntry(int dirInodeNum, String oldName, DirectoryEntry newEntry) {
        Inode dirInode = inodeMgr.readInode(dirInodeNum);
        if (!dirInode.isDirectory()) return false;

        byte[] data = fileAllocator.readFileData(dirInodeNum, 0, dirInode.fileSize);
        int entryCount = data.length / DirectoryEntry.SIZE;

        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(data, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType != 0 && de.fileName.equals(oldName)) {
                byte[] newBytes = Serializer.dirEntryToBytes(newEntry);
                System.arraycopy(newBytes, 0, data, i * DirectoryEntry.SIZE, DirectoryEntry.SIZE);
                fileAllocator.writeFileData(dirInodeNum, 0, data);
                return true;
            }
        }
        return false;
    }

    /** 获取 inode 对应的完整路径。 */
    public String getFullPath(int inodeNum) {
        if (inodeNum == DiskDriver.ROOT_INODE) return "/";

        // 从根目录开始向下搜索
        List<String> pathParts = new ArrayList<>();
        if (!findPathRecursive(DiskDriver.ROOT_INODE, inodeNum, pathParts)) {
            return "/";
        }

        StringBuilder sb = new StringBuilder();
        for (String part : pathParts) {
            sb.append("/").append(part);
        }
        return sb.toString();
    }

    /** 生成目录树字符串。 */
    public String tree(String path) {
        int targetInode = currentDirInode;
        if (path != null && !path.isEmpty() && !path.equals(".")) {
            targetInode = resolvePathToInode(path);
        }
        if (targetInode < 0) return "路径不存在";

        StringBuilder sb = new StringBuilder();
        sb.append(getFullPath(targetInode)).append("\n");
        buildTree(targetInode, "", sb);
        return sb.toString();
    }

    /** 检查目录是否为空。 */
    private boolean isDirectoryEmpty(int dirInodeNum) {
        Inode dirInode = inodeMgr.readInode(dirInodeNum);
        byte[] data = fileAllocator.readFileData(dirInodeNum, 0, dirInode.fileSize);
        int entryCount = data.length / DirectoryEntry.SIZE;

        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(data, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType != 0 && !de.fileName.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 递归查找 inode 的路径。 */
    private boolean findPathRecursive(int currentInode, int targetInode, List<String> path) {
        if (currentInode == targetInode) return true;

        Inode dirInode = inodeMgr.readInode(currentInode);
        if (!dirInode.isDirectory()) return false;

        byte[] data = fileAllocator.readFileData(currentInode, 0, dirInode.fileSize);
        int entryCount = data.length / DirectoryEntry.SIZE;

        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(data, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType == 2 && !de.fileName.isEmpty() && !de.fileName.equals(".") && !de.fileName.equals("..")) {
                path.add(de.fileName);
                if (findPathRecursive(de.inodeNumber, targetInode, path)) {
                    return true;
                }
                path.remove(path.size() - 1);
            }
        }
        return false;
    }

    /** 递归构建目录树。 */
    private void buildTree(int inodeNum, String prefix, StringBuilder sb) {
        Inode inode = inodeMgr.readInode(inodeNum);
        byte[] data = fileAllocator.readFileData(inodeNum, 0, inode.fileSize);
        int entryCount = data.length / DirectoryEntry.SIZE;

        List<DirectoryEntry> dirs = new ArrayList<>();
        List<DirectoryEntry> files = new ArrayList<>();

        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(data, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType != 0 && !de.fileName.isEmpty()
                    && !de.fileName.equals(".") && !de.fileName.equals("..")) {
                if (de.fileType == 2) dirs.add(de);
                else files.add(de);
            }
        }

        int totalItems = dirs.size() + files.size();
        int index = 0;

        for (DirectoryEntry dir : dirs) {
            index++;
            boolean isLast = (index == totalItems);
            sb.append(prefix).append(isLast ? "└── " : "├── ").append(dir.fileName).append("/\n");
            String childPrefix = prefix + (isLast ? "    " : "│   ");
            buildTree(dir.inodeNumber, childPrefix, sb);
        }

        for (DirectoryEntry file : files) {
            index++;
            boolean isLast = (index == totalItems);
            Inode fileInode = inodeMgr.readInode(file.inodeNumber);
            String sizeStr = formatFileSize(fileInode.fileSize);
            sb.append(prefix).append(isLast ? "└── " : "├── ")
              .append(file.fileName).append(" (").append(sizeStr).append(")\n");
        }
    }

    /** 将路径解析为 inode 号。 */
    private int resolvePathToInode(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return DiskDriver.ROOT_INODE;
        }
        if (path.equals(".")) return currentDirInode;
        if (path.equals("..")) {
            return findParentInode(currentDirInode);
        }

        String[] components = pathResolver.splitPath(path);
        boolean isAbsolute = pathResolver.isAbsolutePath(path);

        int current = isAbsolute ? DiskDriver.ROOT_INODE : currentDirInode;

        for (String comp : components) {
            if (comp.isEmpty() || comp.equals(".")) continue;
            if (comp.equals("..")) {
                // 只有在根目录时 .. 才指向自身
                if (current == DiskDriver.ROOT_INODE && components.length == 1) {
                    return DiskDriver.ROOT_INODE;
                }
                current = findParentInode(current);
                if (current < 0) return -1;
                continue;
            }
            int next = findSubDirInode(current, comp);
            if (next < 0) return -1;
            current = next;
        }
        return current;
    }

    /** 查找指定目录的父目录 inode。 */
    private int findParentInode(int dirInodeNum) {
        if (dirInodeNum == DiskDriver.ROOT_INODE) return DiskDriver.ROOT_INODE;
        return findParentInodeRecursive(DiskDriver.ROOT_INODE, dirInodeNum);
    }

    private int findParentInodeRecursive(int currentInode, int targetInode) {
        Inode inode = inodeMgr.readInode(currentInode);
        if (!inode.isDirectory()) return -1;

        byte[] data = fileAllocator.readFileData(currentInode, 0, inode.fileSize);
        int entryCount = data.length / DirectoryEntry.SIZE;

        for (int i = 0; i < entryCount; i++) {
            byte[] entryData = new byte[DirectoryEntry.SIZE];
            System.arraycopy(data, i * DirectoryEntry.SIZE, entryData, 0, DirectoryEntry.SIZE);
            DirectoryEntry de = Serializer.bytesToDirEntry(entryData);
            if (de.fileType == 2 && !de.fileName.isEmpty()
                    && !de.fileName.equals(".") && !de.fileName.equals("..")) {
                if (de.inodeNumber == targetInode) {
                    return currentInode;
                }
                int result = findParentInodeRecursive(de.inodeNumber, targetInode);
                if (result >= 0) return result;
            }
        }
        return -1;
    }

    private String formatFileSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
