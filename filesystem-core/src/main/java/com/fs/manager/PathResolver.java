package com.fs.manager;

import com.fs.model.PathInfo;
import java.util.ArrayList;

/**
 * 路径解析器 — 处理绝对/相对路径的解析、规范化、合法性校验。
 */
public class PathResolver {
    private static final String VALID_FILE_NAME_REGEX = "^[a-zA-Z0-9_\\-.\\u4e00-\\u9fa5]+$";

    /**
     * 解析路径字符串为 PathInfo。
     * @param path  路径字符串
     * @param cwdInode 当前工作目录的完整路径（用于相对路径转绝对）
     * @param dirMgr   DirectoryManager（用于获取路径对应的 inode）
     */
    public PathInfo parsePath(String path, String cwd, DirectoryManager dirMgr) {
        if (path == null || path.isEmpty()) return null;

        boolean isAbsolute = path.startsWith("/");
        String normalized = normalizePath(path);
        String[] components = splitPath(normalized);

        if (components.length == 0 && isAbsolute) {
            // 根目录
            return new PathInfo(0, "", true, new String[0]);
        }

        if (components.length == 0) {
            // 当前目录
            return new PathInfo(dirMgr.getCurrentDirInode(), "", false, new String[0]);
        }

        // 找到父目录 inode 和文件名
        int parentInode;
        String fileName = components[components.length - 1];

        if (isAbsolute) {
            parentInode = 0; // 从根开始
            // 导航到父目录
            for (int i = 0; i < components.length - 1; i++) {
                parentInode = dirMgr.findSubDirInode(parentInode, components[i]);
                if (parentInode < 0) return null;
            }
        } else {
            // 从当前目录开始解析 components[0] 的路径
            // 先分解 cwd 路径
            String[] cwdComponents = splitPath(cwd);
            // 处理路径中的 '..'
            ArrayList<String> finalComponents = new ArrayList<>();
            for (String comp : cwdComponents) {
                if (comp.equals("..")) {
                    if (finalComponents.size() > 0) finalComponents.remove(finalComponents.size() - 1);
                } else if (!comp.equals(".") && !comp.isEmpty()) {
                    finalComponents.add(comp);
                }
            }
            for (String comp : components) {
                if (comp.equals("..")) {
                    if (finalComponents.size() > 0) finalComponents.remove(finalComponents.size() - 1);
                } else if (!comp.equals(".") && !comp.isEmpty()) {
                    finalComponents.add(comp);
                }
            }

            if (finalComponents.isEmpty()) {
                return new PathInfo(0, "", false, new String[0]);
            }

            // 分离父目录和文件名
            fileName = finalComponents.remove(finalComponents.size() - 1);

            // 从根开始导航
            parentInode = 0;
            for (String comp : finalComponents) {
                parentInode = dirMgr.findSubDirInode(parentInode, comp);
                if (parentInode < 0) return null;
            }
        }

        return new PathInfo(parentInode, fileName, isAbsolute, components);
    }

    /** 解析路径，返回父目录 inode 和文件名。如果没有父目录则 dirInode=-1。 */
    public int[] resolveParentAndName(String path, int cwdInode, DirectoryManager dirMgr) {
        if (path == null || path.isEmpty()) return null;

        boolean isAbsolute = path.startsWith("/");
        String normalized = normalizePath(path);
        String[] components = splitPath(normalized);

        if (components.length == 0) {
            return new int[]{0, 0}; // 根目录，特殊处理
        }

        String fileName = components[components.length - 1];

        // 验证文件名
        if (!validateFileName(fileName)) return null;

        int parentInode;
        if (isAbsolute) {
            parentInode = 0;
            for (int i = 0; i < components.length - 1; i++) {
                parentInode = dirMgr.findSubDirInode(parentInode, components[i]);
                if (parentInode < 0) return null;
            }
        } else {
            // 处理相对路径
            ArrayList<String> dirParts = new ArrayList<>();
            String cwdPath = dirMgr.getFullPath(cwdInode);
            if (cwdPath != null && !cwdPath.isEmpty() && !cwdPath.equals("/")) {
                for (String s : splitPath(cwdPath)) {
                    if (!s.isEmpty()) dirParts.add(s);
                }
            }

            for (int i = 0; i < components.length - 1; i++) {
                if (components[i].equals("..")) {
                    if (!dirParts.isEmpty()) dirParts.remove(dirParts.size() - 1);
                } else if (!components[i].equals(".") && !components[i].isEmpty()) {
                    dirParts.add(components[i]);
                }
            }

            // 从根开始导航
            parentInode = 0;
            for (String comp : dirParts) {
                parentInode = dirMgr.findSubDirInode(parentInode, comp);
                if (parentInode < 0) return null;
            }
        }

        return new int[]{parentInode, 0}; // 用数组返回，第 0 个是父目录 inode
    }

    /** 规范化路径，去除多余的 /、.、..。 */
    private String normalizePath(String path) {
        // 替换反斜杠
        path = path.replace('\\', '/');
        // 合并连续的 /
        path = path.replaceAll("/+", "/");
        return path;
    }

    /** 将路径分割为各个分量。 */
    public String[] splitPath(String path) {
        if (path == null || path.isEmpty()) return new String[0];
        String normalized = normalizePath(path);
        // 去除首尾的 /
        String trimmed = normalized.replaceAll("^/+|/+$", "");
        if (trimmed.isEmpty()) return new String[0];
        return trimmed.split("/");
    }

    /** 验证文件名是否合法：只含字母、数字、下划线、点、中文字符。 */
    public boolean validateFileName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.equals(".") || name.equals("..")) return false;
        if (name.length() > 40) return false;
        return name.matches(VALID_FILE_NAME_REGEX);
    }

    /** 判断是否为绝对路径。 */
    public boolean isAbsolutePath(String path) {
        return path != null && path.startsWith("/");
    }

    /** 获取父目录路径。 */
    public String getParentPath(String path) {
        if (path == null || path.isEmpty()) return "/";
        String normalized = normalizePath(path);
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash <= 0) return "/";
        return normalized.substring(0, lastSlash);
    }
}
