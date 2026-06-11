package com.fs.manager;

import com.fs.driver.DiskDriver;
import com.fs.model.DirectoryEntry;
import com.fs.model.EntryInfo;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class DirectoryManagerTest {
    private DiskDriver disk;
    private BitmapManager bitmapMgr;
    private InodeManager inodeMgr;
    private FileAllocator fileAlloc;
    private PathResolver pathRes;
    private DirectoryManager dirMgr;

    @BeforeEach
    void setUp() {
        disk = new DiskDriver("test_dir.img");
        disk.format();
        bitmapMgr = new BitmapManager(disk);
        bitmapMgr.initBitmap();
        inodeMgr = new InodeManager(disk, bitmapMgr);
        fileAlloc = new FileAllocator(disk, bitmapMgr, inodeMgr);
        pathRes = new PathResolver();
        dirMgr = new DirectoryManager(disk, inodeMgr, fileAlloc, pathRes);
        dirMgr.initRootDir();
    }

    @AfterEach
    void cleanUp() {
        new java.io.File("test_dir.img").delete();
    }

    @Test
    void testMkdir() {
        assertTrue(dirMgr.mkdir("/home"));
        assertTrue(dirMgr.mkdir("/home/user1"));
        // 创建同名目录应失败
        assertFalse(dirMgr.mkdir("/home"));
    }

    @Test
    void testMkdirAndLs() {
        dirMgr.mkdir("/home");
        List<EntryInfo> entries = dirMgr.ls("/");
        assertNotNull(entries);
        assertTrue(entries.stream().anyMatch(e -> e.name.equals("home") && e.isDirectory()));
    }

    @Test
    void testRmdir() {
        dirMgr.mkdir("/home");
        assertTrue(dirMgr.rmdir("/home"));
        // 删除不存在的目录应失败
        assertFalse(dirMgr.rmdir("/home"));
    }

    @Test
    void testRmdirNonEmpty() {
        dirMgr.mkdir("/home");
        dirMgr.mkdir("/home/user1");
        assertFalse(dirMgr.rmdir("/home")); // 非空
    }

    @Test
    void testCdAndPwd() {
        dirMgr.mkdir("/home");
        assertTrue(dirMgr.cd("/home"));
        assertEquals("/home", dirMgr.pwd());
        assertTrue(dirMgr.cd("/"));
        assertEquals("/", dirMgr.pwd());
    }

    @Test
    void testCdDotDot() throws Exception {
        dirMgr.mkdir("/home");
        dirMgr.mkdir("/home/user1");
        assertTrue(dirMgr.cd("/home/user1"));
        assertEquals("/home/user1", dirMgr.pwd());
        assertTrue(dirMgr.cd(".."));
        assertEquals("/home", dirMgr.pwd());
    }

    @Test
    void testFindEntry() {
        dirMgr.mkdir("/home");
        DirectoryEntry entry = dirMgr.findEntry(0, "home");
        assertNotNull(entry);
        assertEquals("home", entry.fileName);
        assertEquals(2, entry.fileType);
    }

    @Test
    void testDuplicateEntry() {
        dirMgr.mkdir("/home");
        assertFalse(dirMgr.mkdir("/home"));
    }

    @Test
    void testTree() {
        dirMgr.mkdir("/home");
        dirMgr.mkdir("/home/user1");
        String tree = dirMgr.tree("/");
        assertNotNull(tree);
        assertTrue(tree.contains("home"));
        assertTrue(tree.contains("user1"));
    }

    @Test
    void testNestedDirs() {
        assertTrue(dirMgr.mkdir("/a"));
        assertTrue(dirMgr.mkdir("/a/b"));
        assertTrue(dirMgr.mkdir("/a/b/c"));
        assertTrue(dirMgr.cd("/a/b/c"));
        assertEquals("/a/b/c", dirMgr.pwd());
    }
}
