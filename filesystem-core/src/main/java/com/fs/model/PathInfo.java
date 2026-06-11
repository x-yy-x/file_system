package com.fs.model;

/**
 * 路径解析结果 — 包含父目录的 inode 和文件名。
 */
public class PathInfo {
    public int parentDirInode;
    public String fileName;
    public boolean isAbsolute;
    public String[] components;

    public PathInfo(int parentDirInode, String fileName, boolean isAbsolute, String[] components) {
        this.parentDirInode = parentDirInode;
        this.fileName = fileName;
        this.isAbsolute = isAbsolute;
        this.components = components;
    }
}
