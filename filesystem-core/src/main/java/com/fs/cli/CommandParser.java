package com.fs.cli;

import com.fs.fs.FileSystem;
import com.fs.model.*;
import java.util.List;
import java.util.Scanner;

/**
 * CLI 命令解释器 — 读取用户输入，调用 FileSystem API，输出结果。
 */
public class CommandParser {
    private final FileSystem fs;
    private final Scanner scanner;
    private boolean running;

    public CommandParser(FileSystem fs) {
        this.fs = fs;
        this.scanner = new Scanner(System.in);
        this.running = true;
    }

    /** 启动 CLI 主循环。 */
    public void run() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║    文件系统管理模拟器 v1.0           ║");
        System.out.println("║    输入 help 查看命令帮助            ║");
        System.out.println("╚══════════════════════════════════════╝");

        while (running) {
            System.out.print(fs.pwd() + "> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            executeCommand(input);
        }
    }

    /** 解析并执行一条命令。 */
    public void executeCommand(String input) {
        String[] parts = splitCommand(input);
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        try {
            switch (cmd) {
                case "format":    cmdFormat(); break;
                case "mkdir":     cmdMkdir(args); break;
                case "rmdir":     cmdRmdir(args); break;
                case "ls":        cmdLs(args); break;
                case "cd":        cmdCd(args); break;
                case "pwd":       cmdPwd(); break;
                case "create":    cmdCreate(args); break;
                case "open":      cmdOpen(args); break;
                case "close":     cmdClose(args); break;
                case "read":      cmdRead(args); break;
                case "write":     cmdWrite(args); break;
                case "delete":    cmdDelete(args); break;
                case "rename":    cmdRename(args); break;
                case "copy":      cmdCopy(args); break;
                case "move":      cmdMove(args); break;
                case "stat":      cmdStat(args); break;
                case "tree":      cmdTree(args); break;
                case "save":      cmdSave(); break;
                case "info":      cmdInfo(); break;
                case "exit":      cmdExit(); break;
                case "quit":      cmdExit(); break;
                case "help":
                case "?":         cmdHelp(); break;
                default:
                    System.out.println("未知命令: " + cmd + "，输入 help 查看帮助");
            }
        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
        }
    }

    // ==================== 命令实现 ====================

    private void cmdFormat() {
        System.out.print("确认格式化？所有数据将丢失 (y/N): ");
        String confirm = scanner.nextLine().trim();
        if (confirm.equalsIgnoreCase("y") || confirm.equalsIgnoreCase("yes")) {
            fs.format();
            System.out.println("格式化完成。");
        }
    }

    private void cmdMkdir(String[] args) {
        if (args.length < 1) { System.out.println("用法: mkdir <路径>"); return; }
        if (fs.mkdir(args[0])) System.out.println("目录已创建: " + args[0]);
        else System.out.println("创建目录失败。");
    }

    private void cmdRmdir(String[] args) {
        if (args.length < 1) { System.out.println("用法: rmdir <路径>"); return; }
        if (fs.rmdir(args[0])) System.out.println("目录已删除: " + args[0]);
        else System.out.println("删除目录失败（可能非空或不存在）。");
    }

    private void cmdLs(String[] args) {
        String path = args.length > 0 ? args[0] : ".";
        List<EntryInfo> entries = fs.ls(path);
        if (entries == null) { System.out.println("路径不存在。"); return; }
        if (entries.isEmpty()) { System.out.println("（空）"); return; }

        System.out.printf("%-30s %-6s %-10s %s\n", "名称", "类型", "大小", "修改时间");
        System.out.println("-".repeat(60));
        for (EntryInfo e : entries) {
            String type = e.isDirectory() ? "目录" : "文件";
            String size = e.isDirectory() ? "-" : formatSize(e.size);
            String time = formatTime(e.modifyTime);
            System.out.printf("%-30s %-6s %-10s %s\n", e.name, type, size, time);
        }
    }

    private void cmdCd(String[] args) {
        String path = args.length > 0 ? args[0] : "/";
        if (fs.cd(path)) {
            // 成功切换
        } else {
            System.out.println("目录不存在: " + path);
        }
    }

    private void cmdPwd() {
        System.out.println(fs.pwd());
    }

    private void cmdCreate(String[] args) {
        if (args.length < 1) { System.out.println("用法: create <路径>"); return; }
        if (fs.create(args[0])) System.out.println("文件已创建: " + args[0]);
        else System.out.println("创建文件失败。");
    }

    private void cmdOpen(String[] args) {
        if (args.length < 1) { System.out.println("用法: open <路径> [模式(0=只读,1=只写,2=读写)]"); return; }
        int mode = args.length >= 2 ? Integer.parseInt(args[1]) : FileDescriptor.READ_WRITE;
        int fd = fs.open(args[0], mode);
        if (fd >= 0) System.out.println("文件已打开, fd=" + fd);
        else System.out.println("打开文件失败。");
    }

    private void cmdClose(String[] args) {
        if (args.length < 1) { System.out.println("用法: close <fd>"); return; }
        int fd = Integer.parseInt(args[0]);
        if (fs.close(fd)) System.out.println("文件已关闭, fd=" + fd);
        else System.out.println("关闭失败或 fd 无效。");
    }

    private void cmdRead(String[] args) {
        if (args.length < 2) { System.out.println("用法: read <fd> <大小>"); return; }
        int fd = Integer.parseInt(args[0]);
        int size = Integer.parseInt(args[1]);
        byte[] data = fs.read(fd, size);
        if (data == null) { System.out.println("读取失败。"); return; }
        String text = new String(data);
        System.out.println(text);
        System.out.println("--- 读取 " + data.length + " 字节 ---");
    }

    private void cmdWrite(String[] args) {
        if (args.length < 2) { System.out.println("用法: write <fd> <内容>"); return; }
        int fd = Integer.parseInt(args[0]);
        // 合并剩余参数作为写入内容
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        byte[] data = sb.toString().getBytes();
        int written = fs.write(fd, data);
        if (written >= 0) System.out.println("写入 " + written + " 字节");
        else System.out.println("写入失败。");
    }

    private void cmdDelete(String[] args) {
        if (args.length < 1) { System.out.println("用法: delete <路径>"); return; }
        if (fs.delete(args[0])) System.out.println("文件已删除: " + args[0]);
        else System.out.println("删除失败（文件不存在、已打开或是目录）。");
    }

    private void cmdRename(String[] args) {
        if (args.length < 2) { System.out.println("用法: rename <旧路径> <新路径>"); return; }
        if (fs.rename(args[0], args[1])) System.out.println("重命名成功。");
        else System.out.println("重命名失败。");
    }

    private void cmdCopy(String[] args) {
        if (args.length < 2) { System.out.println("用法: copy <源路径> <目标路径>"); return; }
        if (fs.copy(args[0], args[1])) System.out.println("复制成功。");
        else System.out.println("复制失败。");
    }

    private void cmdMove(String[] args) {
        if (args.length < 2) { System.out.println("用法: move <源路径> <目标路径>"); return; }
        if (fs.move(args[0], args[1])) System.out.println("移动成功。");
        else System.out.println("移动失败。");
    }

    private void cmdStat(String[] args) {
        if (args.length < 1) { System.out.println("用法: stat <路径>"); return; }
        FileStat stat = fs.stat(args[0]);
        if (stat == null) { System.out.println("路径不存在。"); return; }

        System.out.println("文件名: " + stat.name);
        System.out.println("类型: " + (stat.type == 2 ? "目录" : "文件"));
        System.out.println("大小: " + stat.size + " 字节");
        System.out.println("Inode: " + stat.inodeNumber);
        System.out.println("创建时间: " + formatTime(stat.createTime));
        System.out.println("修改时间: " + formatTime(stat.modifyTime));
        System.out.println("链接数: " + stat.linkCount);
        System.out.println("占用盘块: " + stat.blockCount);
    }

    private void cmdTree(String[] args) {
        String path = args.length > 0 ? args[0] : ".";
        String tree = fs.tree(path);
        System.out.println(tree);
    }

    private void cmdSave() {
        fs.save();
        System.out.println("保存完成。");
    }

    private void cmdInfo() {
        SystemInfo info = fs.getInfo();
        System.out.println("╔══════════════════════════════╗");
        System.out.println("║      系统信息               ║");
        System.out.println("╠══════════════════════════════╣");
        System.out.printf("║ 总盘块数: %-17d ║\n", info.totalBlocks);
        System.out.printf("║ 空闲盘块: %-17d ║\n", info.freeBlocks);
        System.out.printf("║ 盘块大小: %-17d ║\n", info.blockSize);
        System.out.printf("║ 总 inode: %-17d ║\n", info.totalInodes);
        System.out.printf("║ 空闲 inode: %-15d ║\n", info.freeInodes);
        System.out.printf("║ 使用率: %-17.2f%%║\n", info.usageRate);
        System.out.println("╚══════════════════════════════╝");
    }

    private void cmdExit() {
        System.out.print("保存并退出？(Y/n): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("n") && !confirm.equalsIgnoreCase("no")) {
            fs.save();
            System.out.println("保存完成。再见！");
        } else {
            System.out.println("未保存，再见！");
        }
        running = false;
    }

    private void cmdHelp() {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║                     命令帮助                        ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  系统:                                             ║");
        System.out.println("║    format        格式化虚拟磁盘                     ║");
        System.out.println("║    save          手动保存                           ║");
        System.out.println("║    info          查看系统信息                       ║");
        System.out.println("║    exit/quit     保存并退出                         ║");
        System.out.println("║  目录:                                             ║");
        System.out.println("║    mkdir <路径>  创建目录                           ║");
        System.out.println("║    rmdir <路径>  删除空目录                         ║");
        System.out.println("║    ls [路径]     列出目录内容                       ║");
        System.out.println("║    cd <路径>     切换目录                           ║");
        System.out.println("║    pwd           显示当前路径                       ║");
        System.out.println("║    tree [路径]   树形显示目录                       ║");
        System.out.println("║  文件:                                             ║");
        System.out.println("║    create <路径> 创建文件                           ║");
        System.out.println("║    open <路径>   打开文件 (模式:0=只读 1=只写 2=读写)║");
        System.out.println("║    close <fd>    关闭文件                           ║");
        System.out.println("║    read <fd> <大小>  读文件                         ║");
        System.out.println("║    write <fd> <内容> 写文件                        ║");
        System.out.println("║    delete <路径> 删除文件                           ║");
        System.out.println("║  附加:                                             ║");
        System.out.println("║    rename <旧> <新>  重命名                         ║");
        System.out.println("║    copy <源> <目标>  复制文件                       ║");
        System.out.println("║    move <源> <目标>  移动文件                       ║");
        System.out.println("║    stat <路径>      查看详细信息                    ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }

    // ==================== 辅助方法 ====================

    private String[] splitCommand(String input) {
        // 简单分词，支持引号
        List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (char c : input.toCharArray()) {
            if (c == '"') { inQuote = !inQuote; continue; }
            if (c == ' ' && !inQuote) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String formatTime(int timestamp) {
        if (timestamp == 0) return "-";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new java.util.Date((long) timestamp * 1000));
    }
}
