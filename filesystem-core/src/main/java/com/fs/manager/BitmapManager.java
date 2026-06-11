package com.fs.manager;

import com.fs.driver.DiskDriver;

/**
 * 位图管理器 — 维护磁盘块分配状态，支持分配与回收。
 *
 * 位图位于磁盘 Blocks 1..8，共 4096 字节 = 32768 位，对应每个盘块。
 * 0 = 空闲，1 = 已分配。
 */
public class BitmapManager {
    private final DiskDriver disk;
    private final byte[] bitmap; // 内存缓存，共 BITMAP_SIZE 字节

    public static final int BITMAP_SIZE = DiskDriver.BITMAP_BLOCKS * DiskDriver.BLOCK_SIZE; // 4096

    public BitmapManager(DiskDriver disk) {
        this.disk = disk;
        this.bitmap = new byte[BITMAP_SIZE];
    }

    /** 从磁盘加载位图到内存缓存。 */
    public void loadBitmap() {
        for (int i = 0; i < DiskDriver.BITMAP_BLOCKS; i++) {
            byte[] block = disk.readBlock(DiskDriver.BITMAP_START + i);
            System.arraycopy(block, 0, bitmap, i * DiskDriver.BLOCK_SIZE, DiskDriver.BLOCK_SIZE);
        }
    }

    /** 将内存缓存写回磁盘。 */
    public void saveBitmap() {
        for (int i = 0; i < DiskDriver.BITMAP_BLOCKS; i++) {
            byte[] block = new byte[DiskDriver.BLOCK_SIZE];
            System.arraycopy(bitmap, i * DiskDriver.BLOCK_SIZE, block, 0, DiskDriver.BLOCK_SIZE);
            disk.writeBlock(DiskDriver.BITMAP_START + i, block);
        }
    }

    /** 初始化位图：标记超级块、位图区、inode 表区为已分配。 */
    public void initBitmap() {
        java.util.Arrays.fill(bitmap, (byte) 0);
        // 标记 Block 0..DATA_START-1 为已分配
        for (int i = 0; i < DiskDriver.DATA_START; i++) {
            markAllocated(i);
        }
    }

    /** 分配一个空闲盘块。 */
    public synchronized int allocateBlock() {
        for (int i = DiskDriver.DATA_START; i < DiskDriver.TOTAL_BLOCKS; i++) {
            if (isFree(i)) {
                markAllocated(i);
                return i;
            }
        }
        return -1; // 磁盘已满
    }

    /** 回收一个盘块。 */
    public synchronized void freeBlock(int blockNum) {
        if (blockNum < 0 || blockNum >= DiskDriver.TOTAL_BLOCKS) return;
        markFree(blockNum);
    }

    /** 批量分配 count 个盘块。 */
    public synchronized int[] allocateBlocks(int count) {
        int[] blocks = new int[count];
        int allocated = 0;
        for (int i = DiskDriver.DATA_START; i < DiskDriver.TOTAL_BLOCKS && allocated < count; i++) {
            if (isFree(i)) {
                markAllocated(i);
                blocks[allocated++] = i;
            }
        }
        if (allocated < count) {
            // 回滚已分配的
            for (int j = 0; j < allocated; j++) {
                markFree(blocks[j]);
            }
            return null; // 空间不足
        }
        return blocks;
    }

    /** 检查某块是否空闲。 */
    public boolean isFree(int blockNum) {
        int byteIdx = blockNum / 8;
        int bitIdx = blockNum % 8;
        return (bitmap[byteIdx] & (1 << bitIdx)) == 0;
    }

    /** 获取空闲块总数。 */
    public int getFreeBlockCount() {
        int count = 0;
        for (int i = DiskDriver.DATA_START; i < DiskDriver.TOTAL_BLOCKS; i++) {
            if (isFree(i)) count++;
        }
        return count;
    }

    private void markAllocated(int blockNum) {
        int byteIdx = blockNum / 8;
        int bitIdx = blockNum % 8;
        bitmap[byteIdx] |= (byte) (1 << bitIdx);
    }

    private void markFree(int blockNum) {
        int byteIdx = blockNum / 8;
        int bitIdx = blockNum % 8;
        bitmap[byteIdx] &= (byte) ~(1 << bitIdx);
    }
}
