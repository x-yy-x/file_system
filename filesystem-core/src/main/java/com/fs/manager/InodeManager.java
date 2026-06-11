package com.fs.manager;

import com.fs.driver.DiskDriver;
import com.fs.driver.Serializer;
import com.fs.model.Inode;

/**
 * 索引节点管理器 — inode 的分配、释放、读写，以及间接块指针管理。
 */
public class InodeManager {
    private final DiskDriver disk;
    private final BitmapManager bitmapMgr;

    public InodeManager(DiskDriver disk, BitmapManager bitmapMgr) {
        this.disk = disk;
        this.bitmapMgr = bitmapMgr;
    }

    /** 分配一个空闲 inode，标记为指定类型。 */
    public synchronized int allocInode(byte fileType) {
        for (int i = 0; i < DiskDriver.TOTAL_INODES; i++) {
            Inode inode = Serializer.readInode(disk, i);
            if (inode.isFree()) {
                Inode newInode = new Inode();
                newInode.inodeNumber = i;
                newInode.fileType = fileType;
                newInode.linkCount = 1;
                newInode.createTime = (int) (System.currentTimeMillis() / 1000);
                newInode.modifyTime = newInode.createTime;
                Serializer.writeInode(disk, i, newInode);
                return i;
            }
        }
        return -1; // inode 耗尽
    }

    /** 释放一个 inode，清空其数据并回收所有盘块。 */
    public synchronized void freeInode(int inodeNum) {
        Inode inode = Serializer.readInode(disk, inodeNum);
        freeAllBlocks(inode);
        Inode empty = new Inode();
        empty.inodeNumber = inodeNum;
        Serializer.writeInode(disk, inodeNum, empty);
    }

    /** 读取 inode。 */
    public Inode readInode(int inodeNum) {
        return Serializer.readInode(disk, inodeNum);
    }

    /** 写回 inode。 */
    public void writeInode(Inode inode) {
        inode.modifyTime = (int) (System.currentTimeMillis() / 1000);
        Serializer.writeInode(disk, inode.inodeNumber, inode);
    }

    /**
     * 获取文件逻辑块号对应的物理盘块号。
     * 寻址：0..8 直接块 → 9..136 一级间接 → 137+ 二级间接
     */
    public int getBlockPointer(Inode inode, int logicBlockNum) {
        if (logicBlockNum < Inode.DIRECT_COUNT) {
            return inode.direct[logicBlockNum];
        }

        int indirectOffset = logicBlockNum - Inode.DIRECT_COUNT;
        int pointersPerBlock = Inode.POINTERS_PER_BLOCK;

        if (indirectOffset < pointersPerBlock) {
            // 一级间接
            if (inode.singleIndirect < 0) return -1;
            return readIndirectPointer(inode.singleIndirect, indirectOffset);
        }

        // 二级间接
        int doubleOffset = indirectOffset - pointersPerBlock;
        int level1 = doubleOffset / pointersPerBlock;
        int level2 = doubleOffset % pointersPerBlock;

        if (inode.doubleIndirect < 0) return -1;
        int l1Block = readIndirectPointer(inode.doubleIndirect, level1);
        if (l1Block < 0) return -1;
        return readIndirectPointer(l1Block, level2);
    }

    /** 设置文件逻辑块号对应的物理盘块号。 */
    public void setBlockPointer(Inode inode, int logicBlockNum, int physBlockNum) {
        if (logicBlockNum < Inode.DIRECT_COUNT) {
            inode.direct[logicBlockNum] = physBlockNum;
            return;
        }

        int indirectOffset = logicBlockNum - Inode.DIRECT_COUNT;
        int pointersPerBlock = Inode.POINTERS_PER_BLOCK;

        if (indirectOffset < pointersPerBlock) {
            if (inode.singleIndirect < 0) {
                inode.singleIndirect = bitmapMgr.allocateBlock();
            }
            writeIndirectPointer(inode.singleIndirect, indirectOffset, physBlockNum);
            return;
        }

        int doubleOffset = indirectOffset - pointersPerBlock;
        int level1 = doubleOffset / pointersPerBlock;
        int level2 = doubleOffset % pointersPerBlock;

        if (inode.doubleIndirect < 0) {
            inode.doubleIndirect = bitmapMgr.allocateBlock();
        }
        int l1Block;
        if (level1 == 0 && inode.doubleIndirect >= 0) {
            l1Block = readIndirectPointer(inode.doubleIndirect, level1);
            if (l1Block < 0) {
                l1Block = bitmapMgr.allocateBlock();
                writeIndirectPointer(inode.doubleIndirect, level1, l1Block);
            }
        } else {
            l1Block = readIndirectPointer(inode.doubleIndirect, level1);
            if (l1Block < 0) {
                l1Block = bitmapMgr.allocateBlock();
                writeIndirectPointer(inode.doubleIndirect, level1, l1Block);
            }
        }
        writeIndirectPointer(l1Block, level2, physBlockNum);
    }

    /** 扩展文件到目标大小，分配新盘块。 */
    public boolean expandFile(Inode inode, int targetSize) {
        int oldMaxBlock = (inode.fileSize + DiskDriver.BLOCK_SIZE - 1) / DiskDriver.BLOCK_SIZE;
        int newMaxBlock = (targetSize + DiskDriver.BLOCK_SIZE - 1) / DiskDriver.BLOCK_SIZE;

        for (int i = oldMaxBlock; i < newMaxBlock; i++) {
            int block = bitmapMgr.allocateBlock();
            if (block < 0) return false;
            setBlockPointer(inode, i, block);
        }
        return true;
    }

    /** 释放文件的所有盘块。 */
    public void freeAllBlocks(Inode inode) {
        int maxBlocks = (inode.fileSize + DiskDriver.BLOCK_SIZE - 1) / DiskDriver.BLOCK_SIZE;
        int pointersPerBlock = Inode.POINTERS_PER_BLOCK;

        // 释放直接块
        for (int i = 0; i < Inode.DIRECT_COUNT && i < maxBlocks; i++) {
            if (inode.direct[i] >= 0) {
                bitmapMgr.freeBlock(inode.direct[i]);
                inode.direct[i] = -1;
            }
        }

        if (maxBlocks <= Inode.DIRECT_COUNT) return;

        // 释放一级间接块
        int indirectCount = Math.min(maxBlocks - Inode.DIRECT_COUNT, pointersPerBlock);
        if (inode.singleIndirect >= 0) {
            for (int i = 0; i < indirectCount; i++) {
                int block = readIndirectPointer(inode.singleIndirect, i);
                if (block >= 0) bitmapMgr.freeBlock(block);
            }
            bitmapMgr.freeBlock(inode.singleIndirect);
            inode.singleIndirect = -1;
        }

        if (maxBlocks <= Inode.DIRECT_COUNT + pointersPerBlock) return;

        // 释放二级间接块
        int doubleCount = maxBlocks - Inode.DIRECT_COUNT - pointersPerBlock;
        if (inode.doubleIndirect >= 0) {
            int l1Count = (doubleCount + pointersPerBlock - 1) / pointersPerBlock;
            for (int i = 0; i < l1Count; i++) {
                int l1Block = readIndirectPointer(inode.doubleIndirect, i);
                if (l1Block >= 0) {
                    for (int j = 0; j < pointersPerBlock; j++) {
                        int l2Block = readIndirectPointer(l1Block, j);
                        if (l2Block >= 0) bitmapMgr.freeBlock(l2Block);
                    }
                    bitmapMgr.freeBlock(l1Block);
                }
            }
            bitmapMgr.freeBlock(inode.doubleIndirect);
            inode.doubleIndirect = -1;
        }
    }

    /** 读一个间接块中的指针。 */
    private int readIndirectPointer(int blockNum, int index) {
        return disk.readInt(blockNum, index * 4);
    }

    /** 写一个间接块中的指针。 */
    private void writeIndirectPointer(int blockNum, int index, int value) {
        disk.writeInt(blockNum, index * 4, value);
    }
}
