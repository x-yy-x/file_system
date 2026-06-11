package com.fs.api;

import com.fs.fs.FileSystem;
import com.fs.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

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
        server.createContext("/api/format", jsonHandler(this::handleFormat));
        server.createContext("/api/save", jsonHandler(this::handleSave));
        server.createContext("/api/info", jsonHandler(this::handleInfo));
        server.createContext("/api/mkdir", jsonHandler(this::handleMkdir));
        server.createContext("/api/rmdir", jsonHandler(this::handleRmdir));
        server.createContext("/api/ls", jsonHandler(this::handleLs));
        server.createContext("/api/cd", jsonHandler(this::handleCd));
        server.createContext("/api/pwd", jsonHandler(this::handlePwd));
        server.createContext("/api/tree", jsonHandler(this::handleTree));
        server.createContext("/api/create", jsonHandler(this::handleCreate));
        server.createContext("/api/open", jsonHandler(this::handleOpen));
        server.createContext("/api/close", jsonHandler(this::handleClose));
        server.createContext("/api/read", jsonHandler(this::handleRead));
        server.createContext("/api/write", jsonHandler(this::handleWrite));
        server.createContext("/api/delete", jsonHandler(this::handleDelete));
        server.createContext("/api/rename", jsonHandler(this::handleRename));
        server.createContext("/api/copy", jsonHandler(this::handleCopy));
        server.createContext("/api/move", jsonHandler(this::handleMove));
        server.createContext("/api/stat", jsonHandler(this::handleStat));
        server.createContext("/api/file/content", jsonHandler(this::handleFileContent));
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
            if (fd < 0) { error(exchange, 404, "文件不存在"); return; }
            FileStat stat = fs.stat(path);
            byte[] data = fs.read(fd, stat != null ? stat.size : 4096);
            fs.close(fd);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("path", path);
            res.put("content", data != null ? new String(data, StandardCharsets.UTF_8) : "");
            success(exchange, res);
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
        byte[] dataBytes = content.getBytes(StandardCharsets.UTF_8);
        int written = fs.write(fd, dataBytes);
        fs.close(fd);

        Map<String, Integer> res = new HashMap<>();
        res.put("bytesWritten", written);
        success(exchange, res);
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
