package com.fs.model;

/**
 * 索引节点 — 描述文件或目录的元数据，固定 64 字节。
 * 每个 inode 占用一个槽位，磁盘的 inode 表中连续存放。
 */
public class Inode {
    public static final int SIZE = 64;
    public static final int DIRECT_COUNT = 9;
    public static final int POINTERS_PER_BLOCK = 128; // 512 / 4

    public int inodeNumber;
    public byte fileType;   // 0=空闲, 1=文件, 2=目录
    public byte linkCount;
    public int fileSize;
    public int createTime;
    public int modifyTime;
    public int[] direct;           // 直接块指针，DIRECT_COUNT 个
    public int singleIndirect;     // 一级间接块指针
    public int doubleIndirect;     // 二级间接块指针

    public Inode() {
        direct = new int[DIRECT_COUNT];
        for (int i = 0; i < DIRECT_COUNT; i++) direct[i] = -1;
        singleIndirect = -1;
        doubleIndirect = -1;
    }

    public boolean isFree() { return fileType == 0; }
    public boolean isFile() { return fileType == 1; }
    public boolean isDirectory() { return fileType == 2; }
}
