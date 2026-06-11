package com.fs.model;

/**
 * ls 命令返回的条目信息。
 */
public class EntryInfo {
    public String name;
    public byte type; // 1=文件, 2=目录
    public int size;
    public int modifyTime;

    public EntryInfo(String name, byte type, int size, int modifyTime) {
        this.name = name;
        this.type = type;
        this.size = size;
        this.modifyTime = modifyTime;
    }

    public boolean isDirectory() { return type == 2; }
}
