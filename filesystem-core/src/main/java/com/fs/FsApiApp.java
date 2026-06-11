package com.fs;

import com.fs.api.ApiServer;
import com.fs.fs.FileSystem;

/**
 * REST API 入口 — 启动 HTTP 服务器，提供文件系统 REST 接口。
 * 供 Vue 前端或其他 HTTP 客户端调用。
 */
public class FsApiApp {
    public static void main(String[] args) throws Exception {
        String imagePath = "filesystem.img";
        int port = 8080;

        for (int i = 0; i < args.length; i++) {
            if ("--img".equals(args[i]) && i + 1 < args.length) {
                imagePath = args[i + 1];
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
            }
        }

        FileSystem fs = new FileSystem(imagePath);
        boolean loaded = fs.init();

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║    文件系统 REST API 服务            ║");
        System.out.println("╚══════════════════════════════════════╝");
        if (loaded) {
            System.out.println("已加载磁盘映像: " + imagePath);
        } else {
            System.out.println("已创建新的虚拟磁盘: " + imagePath);
        }

        // 注册关闭钩子，退出时自动保存
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n正在保存...");
            fs.save();
            System.out.println("保存完成。");
        }));

        ApiServer apiServer = new ApiServer(fs, port);
        apiServer.start();
    }
}
