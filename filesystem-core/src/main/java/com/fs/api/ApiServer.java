package com.fs.api;

import com.fs.fs.FileSystem;
import com.fs.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * REST API 服务器 — 通过 Java 内置 HttpServer 提供 HTTP 接口。
 * 前端 Vue 应用通过此 API 与文件系统交互。
 */
public class ApiServer {
    private final FileSystem fs;
    private final HttpServer server;
    private final int port;

    public ApiServer(FileSystem fs, int port) throws IOException {
        this.fs = fs;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        setupRoutes();
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
        System.out.println("REST API 服务器已启动: http://localhost:" + port);
    }

    public void stop() {
        server.stop(0);
    }

    private void setupRoutes() {
        // /api 处理精确路径 /api（显示 API 根信息）
        server.createContext("/api", jsonHandler(this::handleApiRoot));
        // /api/ 处理所有 /api/xxx 子路径
        server.createContext("/api/", jsonHandler(this::handleApiSubExchange));
    }


    /** 处理 /api/xxx 子路径请求。 */
    private void handleApiSubExchange(HttpExchange exchange) throws Exception {
        String path = exchange.getRequestURI().getPath();
        String sub = path.length() > 4 ? path.substring(4) : "/";
        if (sub.isEmpty()) sub = "/";
        if (sub.equals("/")) {
            handleApiRoot(exchange);
        } else {
            dispatchBySubPath(exchange, sub);
        }
    }

    /** 根据子路径分发到各处理方法。 */
    private void dispatchBySubPath(HttpExchange exchange, String sub) throws Exception {
        if (sub.equals("/")) {
            handleApiRoot(exchange);
            return;
        }

        switch (sub) {
            case "/format":        handleFormat(exchange); break;
            case "/save":          handleSave(exchange); break;
            case "/info":          handleInfo(exchange); break;
            case "/mkdir":         handleMkdir(exchange); break;
            case "/rmdir":         handleRmdir(exchange); break;
            case "/ls":            handleLs(exchange); break;
            case "/cd":            handleCd(exchange); break;
            case "/pwd":           handlePwd(exchange); break;
            case "/tree":          handleTree(exchange); break;
            case "/create":        handleCreate(exchange); break;
            case "/open":          handleOpen(exchange); break;
            case "/close":         handleClose(exchange); break;
            case "/read":          handleRead(exchange); break;
            case "/write":         handleWrite(exchange); break;
            case "/delete":        handleDelete(exchange); break;
            case "/rename":        handleRename(exchange); break;
            case "/copy":          handleCopy(exchange); break;
            case "/move":          handleMove(exchange); break;
            case "/stat":          handleStat(exchange); break;
            case "/file/content":  handleFileContent(exchange); break;
            default:
                sendJson(exchange, 404, errorJson("未知端点: /api" + sub));
        }
    }

    /** 包装处理器，统一处理 CORS 和 JSON 响应。 */
    private HttpHandler jsonHandler(ApiAction action) {
        return exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                action.handle(exchange);
            } catch (Exception e) {
                sendJson(exchange, 500, errorJson("服务器内部错误: " + e.getMessage()));
            }
        };
    }

    @FunctionalInterface
    private interface ApiAction {
        void handle(HttpExchange exchange) throws Exception;
    }

    // ==================== HTTP 辅助方法 ====================

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void success(HttpExchange exchange, Object data) throws IOException {
        sendJson(exchange, 200, successJson(data));
    }

    private void error(HttpExchange exchange, int code, String msg) throws IOException {
        sendJson(exchange, code, errorJson(msg));
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        String body = new BufferedReader(new InputStreamReader(
                exchange.getRequestBody(), StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining());
        if (body.isEmpty()) return new HashMap<>();
        return JsonUtil.parse(body);
    }

    private Map<String, String> getParams(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                params.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                           URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static String successJson(Object data) {
        StringBuilder sb = new StringBuilder("{\"success\":true,\"message\":\"OK\"");
        if (data != null) sb.append(",\"data\":").append(JsonUtil.toJson(data));
        sb.append("}");
        return sb.toString();
    }

    private static String errorJson(String msg) {
        return "{\"success\":false,\"message\":" + JsonUtil.toJson(msg) + "}";
    }

    // ==================== 请求处理器 ====================

    private void handleFormat(HttpExchange exchange) throws Exception {
        fs.format();
        success(exchange, null);
    }

    /** /api 根路径 — 返回可用端点列表。 */
    private void handleApiRoot(HttpExchange exchange) throws Exception {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("service", "文件系统 REST API");
        data.put("version", "1.0");
        data.put("endpoints", java.util.List.of(
            "GET  /api/info         系统信息",
            "POST /api/format       格式化",
            "POST /api/save         保存",
            "POST /api/mkdir        创建目录",
            "POST /api/rmdir        删除目录",
            "GET  /api/ls           列出目录",
            "POST /api/cd           切换目录",
            "GET  /api/pwd          当前路径",
            "GET  /api/tree         目录树",
            "POST /api/create       创建文件",
            "POST /api/open         打开文件",
            "POST /api/close        关闭文件",
            "POST /api/read         读文件",
            "POST /api/write        写文件",
            "POST /api/delete       删除文件",
            "POST /api/rename       重命名",
            "POST /api/copy         复制",
            "POST /api/move         移动",
            "GET  /api/stat         文件信息",
            "GET  /api/file/content 读文件内容",
            "POST /api/file/content 写文件内容"
        ));
        success(exchange, data);
    }

    private void handleSave(HttpExchange exchange) throws Exception {
        fs.save();
        success(exchange, null);
    }

    private void handleInfo(HttpExchange exchange) throws Exception {
        SystemInfo info = fs.getInfo();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalBlocks", info.totalBlocks);
        data.put("freeBlocks", info.freeBlocks);
        data.put("blockSize", info.blockSize);
        data.put("totalInodes", info.totalInodes);
        data.put("freeInodes", info.freeInodes);
        data.put("usageRate", info.usageRate);
        success(exchange, data);
    }

    private void handleMkdir(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String path = getString(body, "path");
        if (path == null) { error(exchange, 400, "缺少 path 参数"); return; }
        if (fs.mkdir(path)) success(exchange, null);
        else error(exchange, 400, "创建目录失败");
    }

    private void handleRmdir(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String path = getString(body, "path");
        if (path == null) { error(exchange, 400, "缺少 path 参数"); return; }
        if (fs.rmdir(path)) success(exchange, null);
        else error(exchange, 400, "删除目录失败（可能非空或不存在）");
    }

    private void handleLs(HttpExchange exchange) throws Exception {
        Map<String, String> params = getParams(exchange);
        String path = params.getOrDefault("path", ".");
        List<EntryInfo> entries = fs.ls(path);
        if (entries == null) { error(exchange, 404, "路径不存在"); return; }
        List<Map<String, Object>> list = new ArrayList<>();
        for (EntryInfo e : entries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", e.name);
            item.put("type", e.isDirectory() ? "directory" : "file");
            item.put("size", e.size);
            item.put("modifyTime", e.modifyTime);
            list.add(item);
        }
        success(exchange, list);
    }

    private void handleCd(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String path = getString(body, "path");
        if (path == null) { error(exchange, 400, "缺少 path 参数"); return; }
        if (fs.cd(path)) {
            Map<String, String> res = new HashMap<>();
            res.put("pwd", fs.pwd());
            success(exchange, res);
        } else {
            error(exchange, 400, "目录不存在");
        }
    }

    private void handlePwd(HttpExchange exchange) throws Exception {
        Map<String, String> res = new HashMap<>();
        res.put("path", fs.pwd());
        success(exchange, res);
    }

    private void handleTree(HttpExchange exchange) throws Exception {
        Map<String, String> params = getParams(exchange);
        String path = params.getOrDefault("path", ".");
        Map<String, String> res = new HashMap<>();
        res.put("tree", fs.tree(path));
        success(exchange, res);
    }

    private void handleCreate(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String path = getString(body, "path");
        if (path == null) { error(exchange, 400, "缺少 path 参数"); return; }
        if (fs.create(path)) success(exchange, null);
        else error(exchange, 400, "创建文件失败");
    }

    private void handleOpen(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String path = getString(body, "path");
        int mode = getInt(body, "mode", com.fs.model.FileDescriptor.READ_WRITE);
        if (path == null) { error(exchange, 400, "缺少 path 参数"); return; }
        int fd = fs.open(path, mode);
        if (fd >= 0) {
            Map<String, Integer> res = new HashMap<>();
            res.put("fd", fd);
            success(exchange, res);
        } else {
            error(exchange, 400, "打开文件失败");
        }
    }

    private void handleClose(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        int fd = getInt(body, "fd", -1);
        if (fd < 0) { error(exchange, 400, "缺少或无效的 fd 参数"); return; }
        if (fs.close(fd)) success(exchange, null);
        else error(exchange, 400, "关闭失败或 fd 无效");
    }

    private void handleRead(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        int fd = getInt(body, "fd", -1);
        int size = getInt(body, "size", 1024);
        if (fd < 0) { error(exchange, 400, "缺少或无效的 fd 参数"); return; }
        byte[] data = fs.read(fd, size);
        if (data != null) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("data", new String(data, StandardCharsets.UTF_8));
            res.put("bytesRead", data.length);
            success(exchange, res);
        } else {
            error(exchange, 400, "读取失败");
        }
    }

    private void handleWrite(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        int fd = getInt(body, "fd", -1);
        String dataStr = getString(body, "data");
        if (fd < 0 || dataStr == null) { error(exchange, 400, "缺少 fd 或 data 参数"); return; }
        int written = fs.write(fd, dataStr.getBytes(StandardCharsets.UTF_8));
        if (written >= 0) {
            Map<String, Integer> res = new HashMap<>();
            res.put("bytesWritten", written);
            success(exchange, res);
        } else {
            error(exchange, 400, "写入失败");
        }
    }

    private void handleDelete(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String path = getString(body, "path");
        if (path == null) { error(exchange, 400, "缺少 path 参数"); return; }
        if (fs.delete(path)) success(exchange, null);
        else error(exchange, 400, "删除失败（文件不存在、已打开或是目录）");
    }

    private void handleRename(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String oldPath = getString(body, "oldPath");
        String newPath = getString(body, "newPath");
        if (oldPath == null || newPath == null) { error(exchange, 400, "缺少 oldPath 或 newPath"); return; }
        if (fs.rename(oldPath, newPath)) success(exchange, null);
        else error(exchange, 400, "重命名失败");
    }

    private void handleCopy(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String srcPath = getString(body, "srcPath");
        String dstPath = getString(body, "dstPath");
        if (srcPath == null || dstPath == null) { error(exchange, 400, "缺少 srcPath 或 dstPath"); return; }
        if (fs.copy(srcPath, dstPath)) success(exchange, null);
        else error(exchange, 400, "复制失败");
    }

    private void handleMove(HttpExchange exchange) throws Exception {
        Map<String, Object> body = readBody(exchange);
        String srcPath = getString(body, "srcPath");
        String dstPath = getString(body, "dstPath");
        if (srcPath == null || dstPath == null) { error(exchange, 400, "缺少 srcPath 或 dstPath"); return; }
        if (fs.move(srcPath, dstPath)) success(exchange, null);
        else error(exchange, 400, "移动失败");
    }

    private void handleStat(HttpExchange exchange) throws Exception {
        Map<String, String> params = getParams(exchange);
        String path = params.get("path");
        if (path == null) { error(exchange, 400, "缺少 path 参数"); return; }
        FileStat stat = fs.stat(path);
        if (stat == null) { error(exchange, 404, "路径不存在"); return; }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", stat.name);
        data.put("type", stat.type == 2 ? "directory" : "file");
        data.put("size", stat.size);
        data.put("inodeNumber", stat.inodeNumber);
        data.put("createTime", stat.createTime);
        data.put("modifyTime", stat.modifyTime);
        data.put("linkCount", stat.linkCount);
        data.put("blockCount", stat.blockCount);
        success(exchange, data);
    }

    private void handleFileContent(HttpExchange exchange) throws Exception {
        if ("GET".equals(exchange.getRequestMethod())) {
            Map<String, String> params = getParams(exchange);
            String path = params.get("path");
            if (path == null) { error(exchange, 400, "缺少 path 参数"); return; }

            int fd = fs.open(path, com.fs.model.FileDescriptor.READ);
            if (fd < 0) {
                // 尝试作为目录处理
                FileStat dirStat = fs.stat(path);
                if (dirStat != null && dirStat.type == 2) {
                    error(exchange, 400, "是目录，不是文件");
                } else {
                    error(exchange, 404, "文件不存在");
                }
                return;
            }
            try {
                FileStat stat = fs.stat(path);
                // 空文件也能正常读取（size=0 → 读0字节 → 返回空字符串）
                byte[] data = fs.read(fd, stat != null ? Math.max(stat.size, 0) : 4096);

                Map<String, Object> res = new LinkedHashMap<>();
                res.put("path", path);
                res.put("content", data != null ? new String(data, StandardCharsets.UTF_8) : "");
                success(exchange, res);
            } finally {
                fs.close(fd);
            }
            return;
        }

        // POST — 写入文件内容（覆盖写入）
        Map<String, Object> body = readBody(exchange);
        String path = getString(body, "path");
        String content = getString(body, "content");
        if (path == null || content == null) { error(exchange, 400, "缺少 path 或 content"); return; }

        boolean existed = fs.stat(path) != null;
        if (existed) fs.delete(path);
        if (!fs.create(path)) { error(exchange, 500, "创建文件失败"); return; }

        int fd = fs.open(path, com.fs.model.FileDescriptor.WRITE);
        if (fd < 0) { error(exchange, 500, "打开文件失败"); return; }
        try {
            byte[] dataBytes = content.getBytes(StandardCharsets.UTF_8);
            int written = fs.write(fd, dataBytes);
            Map<String, Integer> res = new HashMap<>();
            res.put("bytesWritten", written);
            success(exchange, res);
        } finally {
            fs.close(fd);
        }
    }

    // ==================== 辅助方法 ====================

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return defaultValue;
    }
}
