package com.fs.model;

/**
 * 系统概要信息。
 */
public class SystemInfo {
    public int totalBlocks;
    public int freeBlocks;
    public int totalInodes;
    public int freeInodes;
    public int blockSize;
    public double usageRate;

    public SystemInfo(int totalBlocks, int freeBlocks, int totalInodes, int freeInodes, int blockSize) {
        this.totalBlocks = totalBlocks;
        this.freeBlocks = freeBlocks;
        this.totalInodes = totalInodes;
        this.freeInodes = freeInodes;
        this.blockSize = blockSize;
        this.usageRate = (double) (totalBlocks - freeBlocks) / totalBlocks * 100;
    }
}
