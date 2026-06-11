package com.fs.model;

/**
 * stat 命令返回的文件/目录详细信息。
 */
public class FileStat {
    public String name;
    public byte type;
    public int size;
    public int inodeNumber;
    public int createTime;
    public int modifyTime;
    public int linkCount;
    public int blockCount;

    public FileStat(String name, byte type, int size, int inodeNumber,
                    int createTime, int modifyTime, int linkCount, int blockCount) {
        this.name = name;
        this.type = type;
        this.size = size;
        this.inodeNumber = inodeNumber;
        this.createTime = createTime;
        this.modifyTime = modifyTime;
        this.linkCount = linkCount;
        this.blockCount = blockCount;
    }
}
