package com.fs.model;

/**
 * 文件系统业务异常 — 封装操作失败原因。
 */
public class FileSystemException extends RuntimeException {
    public FileSystemException(String message) {
        super(message);
    }
}
