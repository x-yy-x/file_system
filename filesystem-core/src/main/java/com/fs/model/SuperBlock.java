package com.fs.model;

/**
 * 超级块 — 虚拟磁盘的第 0 块，描述磁盘整体布局和状态。
 */
public class SuperBlock {
    public static final int MAGIC = 0xF1E5F1E5;
    public static final int SIZE = 512;

    public int magicNumber;
    public int totalBlocks;
    public int blockSize;
    public int bitmapStartBlock;
    public int bitmapBlockCount;
    public int inodeStartBlock;
    public int inodeBlockCount;
    public int dataStartBlock;
    public int totalInodes;
    public int freeBlockCount;
    public int freeInodeCount;
    public int rootInode;

    public SuperBlock() {}

    public SuperBlock(int totalBlocks, int blockSize, int bitmapStartBlock, int bitmapBlockCount,
                      int inodeStartBlock, int inodeBlockCount, int dataStartBlock,
                      int totalInodes, int rootInode) {
        this.magicNumber = MAGIC;
        this.totalBlocks = totalBlocks;
        this.blockSize = blockSize;
        this.bitmapStartBlock = bitmapStartBlock;
        this.bitmapBlockCount = bitmapBlockCount;
        this.inodeStartBlock = inodeStartBlock;
        this.inodeBlockCount = inodeBlockCount;
        this.dataStartBlock = dataStartBlock;
        this.totalInodes = totalInodes;
        this.freeBlockCount = totalBlocks - dataStartBlock;
        this.freeInodeCount = totalInodes - 1; // root takes one
        this.rootInode = rootInode;
    }

    public boolean isValid() {
        return magicNumber == MAGIC;
    }
}
