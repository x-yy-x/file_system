package com.fs.model;

/**
 * 文件描述符 — 记录一个已打开文件的读写状态。
 */
public class FileDescriptor {
    public static final int READ = 0;
    public static final int WRITE = 1;
    public static final int READ_WRITE = 2;

    public int fd;
    public int inodeNumber;
    public int offset;
    public int mode;
    public boolean dirty;

    public FileDescriptor(int fd, int inodeNumber, int mode) {
        this.fd = fd;
        this.inodeNumber = inodeNumber;
        this.mode = mode;
        this.offset = 0;
        this.dirty = false;
    }
}
