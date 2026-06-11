package com.fs.manager;

import com.fs.driver.DiskDriver;
import com.fs.model.Inode;

/**
 * 文件分配器 — 负责文件数据的盘块分配、读取和写入。
 * 协调 BitmapManager 和 InodeManager 完成数据操作。
 */
public class FileAllocator {
    private final DiskDriver disk;
    private final BitmapManager bitmapMgr;
    private final InodeManager inodeMgr;

    public FileAllocator(DiskDriver disk, BitmapManager bitmapMgr, InodeManager inodeMgr) {
        this.disk = disk;
        this.bitmapMgr = bitmapMgr;
        this.inodeMgr = inodeMgr;
    }

    /**
     * 读取文件指定偏移处指定长度的数据。
     * @param inodeNum 文件 inode 编号
     * @param offset   读取起始偏移
     * @param length   读取长度
     * @return 读取的数据，可能小于请求的长度
     */
    public byte[] readFileData(int inodeNum, int offset, int length) {
        Inode inode = inodeMgr.readInode(inodeNum);
        if (offset >= inode.fileSize) return new byte[0];

        int actualLen = Math.min(length, inode.fileSize - offset);
        byte[] result = new byte[actualLen];

        int blockSize = DiskDriver.BLOCK_SIZE;
        int pos = 0;
        while (pos < actualLen) {
            int logicBlock = (offset + pos) / blockSize;
            int blockOffset = (offset + pos) % blockSize;

            int physBlock = inodeMgr.getBlockPointer(inode, logicBlock);
            if (physBlock < 0) break;

            byte[] blockData = disk.readBlock(physBlock);
            int toCopy = Math.min(blockSize - blockOffset, actualLen - pos);
            System.arraycopy(blockData, blockOffset, result, pos, toCopy);
            pos += toCopy;
        }

        return result;
    }

    /**
     * 向文件指定偏移处写入数据。
     * @param inodeNum 文件 inode 编号
     * @param offset   写入偏移
     * @param data     要写入的数据
     * @return 实际写入的字节数
     */
    public int writeFileData(int inodeNum, int offset, byte[] data) {
        Inode inode = inodeMgr.readInode(inodeNum);
        int blockSize = DiskDriver.BLOCK_SIZE;
        int newSize = offset + data.length;

        // 扩展文件（分配新盘块）
        if (newSize > inode.fileSize) {
            if (!inodeMgr.expandFile(inode, newSize)) {
                return -1; // 磁盘满
            }
            inode.fileSize = newSize;
        }

        int written = 0;
        while (written < data.length) {
            int logicBlock = (offset + written) / blockSize;
            int blockOffset = (offset + written) % blockSize;

            int physBlock = inodeMgr.getBlockPointer(inode, logicBlock);

            byte[] blockData;
            if (physBlock < 0) {
                // 需要分配新块（文件空洞）
                physBlock = bitmapMgr.allocateBlock();
                if (physBlock < 0) return written;
                inodeMgr.setBlockPointer(inode, logicBlock, physBlock);
                blockData = new byte[blockSize];
            } else {
                blockData = disk.readBlock(physBlock);
            }

            int toCopy = Math.min(blockSize - blockOffset, data.length - written);
            System.arraycopy(data, written, blockData, blockOffset, toCopy);
            disk.writeBlock(physBlock, blockData);
            written += toCopy;
        }

        if (newSize > inode.fileSize) {
            inode.fileSize = newSize;
        }
        inodeMgr.writeInode(inode);
        return written;
    }

    /** 截断文件到指定大小。 */
    public boolean truncateFile(int inodeNum, int newSize) {
        Inode inode = inodeMgr.readInode(inodeNum);
        if (newSize >= inode.fileSize) return true;

        int oldBlocks = (inode.fileSize + DiskDriver.BLOCK_SIZE - 1) / DiskDriver.BLOCK_SIZE;
        int newBlocks = (newSize + DiskDriver.BLOCK_SIZE - 1) / DiskDriver.BLOCK_SIZE;

        // 释放多余的块
        for (int i = newBlocks; i < oldBlocks; i++) {
            int physBlock = inodeMgr.getBlockPointer(inode, i);
            if (physBlock >= 0) {
                bitmapMgr.freeBlock(physBlock);
                inodeMgr.setBlockPointer(inode, i, -1);
            }
        }

        // 如果恰好落在块边界后面，需要清零尾部
        if (newSize % DiskDriver.BLOCK_SIZE != 0 && newBlocks > 0) {
            int lastBlock = newBlocks - 1;
            int physBlock = inodeMgr.getBlockPointer(inode, lastBlock);
            if (physBlock >= 0) {
                byte[] blockData = disk.readBlock(physBlock);
                int tailStart = newSize % DiskDriver.BLOCK_SIZE;
                java.util.Arrays.fill(blockData, tailStart, DiskDriver.BLOCK_SIZE, (byte) 0);
                disk.writeBlock(physBlock, blockData);
            }
        }

        inode.fileSize = newSize;
        inodeMgr.writeInode(inode);
        return true;
    }
}
