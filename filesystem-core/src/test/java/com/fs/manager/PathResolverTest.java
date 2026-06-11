package com.fs.manager;

import com.fs.model.PathInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PathResolverTest {
    private final PathResolver resolver = new PathResolver();

    @Test
    void testValidateFileName() {
        assertTrue(resolver.validateFileName("test.txt"));
        assertTrue(resolver.validateFileName("my_file-1"));
        assertTrue(resolver.validateFileName("中文文件"));
        assertFalse(resolver.validateFileName(""));
        assertFalse(resolver.validateFileName("."));
        assertFalse(resolver.validateFileName(".."));
        assertFalse(resolver.validateFileName("file/name"));
        assertFalse(resolver.validateFileName(null));
    }

    @Test
    void testIsAbsolutePath() {
        assertTrue(resolver.isAbsolutePath("/home/user"));
        assertFalse(resolver.isAbsolutePath("home/user"));
        assertFalse(resolver.isAbsolutePath(""));
    }

    @Test
    void testSplitPath() {
        assertArrayEquals(new String[]{"home", "user", "file.txt"},
                resolver.splitPath("/home/user/file.txt"));
        assertArrayEquals(new String[]{"a", "b"},
                resolver.splitPath("a/b/"));
        assertEquals(0, resolver.splitPath("/").length);
        assertEquals(0, resolver.splitPath("").length);
    }

    @Test
    void testGetParentPath() {
        assertEquals("/home", resolver.getParentPath("/home/user"));
        assertEquals("/", resolver.getParentPath("/home"));
        assertEquals("/", resolver.getParentPath("/"));
        assertEquals("/", resolver.getParentPath(""));
    }

    @Test
    void testNormalizePathUnixStyle() {
        String[] parts = resolver.splitPath("home//user///docs");
        assertArrayEquals(new String[]{"home", "user", "docs"}, parts);
    }
}
