package com.fs.model;

/**
 * 目录项 — 存放在目录文件的数据块中，每个项描述一个文件或子目录。
 * 固定 48 字节。
 */
public class DirectoryEntry {
    public static final int SIZE = 48;
    public static final int MAX_NAME_BYTES = 40;
    public static final int ENTRIES_PER_BLOCK = 10; // 512 / 48

    public int inodeNumber;
    public String fileName;
    public byte fileType; // 1=文件, 2=目录
    public short nameLen;

    public DirectoryEntry() {}

    public DirectoryEntry(int inodeNumber, String fileName, byte fileType) {
        this.inodeNumber = inodeNumber;
        this.fileName = fileName;
        this.fileType = fileType;
        this.nameLen = (short) fileName.length();
    }
}
