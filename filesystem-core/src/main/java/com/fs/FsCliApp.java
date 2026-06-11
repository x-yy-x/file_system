package com.fs;

import com.fs.cli.CommandParser;
import com.fs.fs.FileSystem;

/**
 * CLI 入口 — 启动文件系统命令行交互界面。
 */
public class FsCliApp {
    public static void main(String[] args) {
        String imagePath = "filesystem.img";
        if (args.length > 0) {
            imagePath = args[0];
        }

        FileSystem fs = new FileSystem(imagePath);
        boolean loaded = fs.init();

        if (loaded) {
            System.out.println("已加载磁盘映像: " + imagePath);
        } else {
            System.out.println("已创建新的虚拟磁盘: " + imagePath);
        }

        CommandParser parser = new CommandParser(fs);
        parser.run();
    }
}
