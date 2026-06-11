package com.fs.integration;

import com.fs.fs.FileSystem;
import com.fs.model.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整工作流集成测试 — 模拟真实的用户操作场景。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullWorkflowTest {
    private static final String TEST_IMAGE = "test_workflow.img";
    private FileSystem fs;

    @BeforeEach
    void setUp() {
        fs = new FileSystem(TEST_IMAGE);
        fs.format();
    }

    @AfterAll
    static void cleanUp() {
        new java.io.File(TEST_IMAGE).delete();
    }

    @Test
    @Order(1)
    void testCreateDirectoryStructure() {
        assertTrue(fs.mkdir("/home"));
        assertTrue(fs.mkdir("/home/user1"));
        assertTrue(fs.mkdir("/home/user1/docs"));
        assertTrue(fs.mkdir("/home/user1/photos"));
        assertTrue(fs.mkdir("/home/user2"));

        List<EntryInfo> rootEntries = fs.ls("/");
        assertEquals(1, rootEntries.size()); // only 'home' under root
        assertTrue(rootEntries.stream().anyMatch(e -> e.name.equals("home") && e.isDirectory()));
    }

    @Test
    @Order(2)
    void testNavigateAndCreateFile() {
        fs.mkdir("/home");
        fs.mkdir("/home/user1");
        assertTrue(fs.cd("/home/user1"));
        assertEquals("/home/user1", fs.pwd());

        assertTrue(fs.create("test.txt"));
    }

    @Test
    @Order(3)
    void testWriteAndReadFile() {
        fs.mkdir("/home");
        fs.mkdir("/home/user1");
        fs.cd("/home/user1");
        fs.create("test.txt");

        int fd = fs.open("test.txt", FileDescriptor.READ_WRITE);
        assertTrue(fd >= 0);

        String content = "Hello, 文件系统! This is a test.";
        byte[] contentBytes = content.getBytes();
        int written = fs.write(fd, contentBytes);
        assertEquals(contentBytes.length, written);

        byte[] data = fs.read(fd, 100);
        // offset moved after write, so read returns 0
        assertEquals(0, data.length);

        fs.close(fd);

        // reopen and read from start
        fd = fs.open("test.txt", FileDescriptor.READ);
        data = fs.read(fd, 100);
        assertEquals(content, new String(data).trim());
        fs.close(fd);
    }

    @Test
    @Order(4)
    void testCopyAndVerify() {
        fs.mkdir("/home");
        fs.mkdir("/home/user1");
        fs.mkdir("/home/user2");
        fs.cd("/home/user1");
        fs.create("test.txt");

        int fd = fs.open("test.txt", FileDescriptor.READ_WRITE);
        fs.write(fd, "Data for copy test".getBytes());
        fs.close(fd);

        // 跨目录复制
        assertTrue(fs.copy("/home/user1/test.txt", "/home/user2/test.txt"));

        // 验证复制结果
        fd = fs.open("/home/user2/test.txt", FileDescriptor.READ);
        byte[] copied = fs.read(fd, 100);
        assertEquals("Data for copy test", new String(copied).trim());
        fs.close(fd);

        // 列出 user2 内容验证
        List<EntryInfo> user2Entries = fs.ls("/home/user2");
        assertTrue(user2Entries.stream().anyMatch(e -> e.name.equals("test.txt")));
    }

    @Test
    @Order(5)
    void testRenameAndStat() {
        fs.mkdir("/home");
        fs.mkdir("/home/user1");
        fs.cd("/home/user1");
        fs.create("readme.md");

        // 重命名
        assertTrue(fs.rename("/home/user1/readme.md", "/home/user1/README.md"));

        // 旧文件名不应存在
        assertNull(fs.stat("/home/user1/readme.md"));

        // 新文件名存在
        FileStat stat = fs.stat("/home/user1/README.md");
        assertNotNull(stat);
        assertEquals("README.md", stat.name);
        assertEquals(1, stat.type); // file
    }

    @Test
    @Order(6)
    void testDeleteFile() {
        fs.mkdir("/home");
        fs.create("/home/tmp.txt");
        assertNotNull(fs.stat("/home/tmp.txt"));

        assertTrue(fs.delete("/home/tmp.txt"));
        assertNull(fs.stat("/home/tmp.txt"));
    }

    @Test
    @Order(7)
    void testDirectoryOperations() {
        fs.mkdir("/a");
        fs.mkdir("/a/b");
        fs.mkdir("/a/b/c");

        assertTrue(fs.cd("/a/b/c"));
        assertEquals("/a/b/c", fs.pwd());

        // 回到上级
        assertTrue(fs.cd(".."));
        assertEquals("/a/b", fs.pwd());

        // 回根
        assertTrue(fs.cd("/"));
        assertEquals("/", fs.pwd());

        // rmdir
        assertTrue(fs.rmdir("/a/b/c")); // c is empty
        assertFalse(fs.rmdir("/a/b/c")); // 已删除
    }

    @Test
    @Order(8)
    void testSaveAndRestore() {
        // 创建一些内容
        fs.mkdir("/home");
        fs.create("/home/hello.txt");
        int fd = fs.open("/home/hello.txt", FileDescriptor.READ_WRITE);
        fs.write(fd, "Persist me!".getBytes());
        fs.close(fd);

        // 保存
        fs.save();

        // 重新加载
        FileSystem fs2 = new FileSystem(TEST_IMAGE);
        assertTrue(fs2.init());

        // 验证内容
        List<EntryInfo> entries = fs2.ls("/");
        assertTrue(entries.size() > 0);

        fd = fs2.open("/home/hello.txt", FileDescriptor.READ);
        byte[] data = fs2.read(fd, 100);
        assertEquals("Persist me!", new String(data).trim());
        fs2.close(fd);
    }

    @Test
    @Order(9)
    void testSystemInfo() {
        SystemInfo info = fs.getInfo();
        assertEquals(32768, info.totalBlocks);
        assertEquals(512, info.blockSize);
        assertTrue(info.usageRate >= 0);
    }

    @Test
    @Order(10)
    void testTreeOutput() {
        fs.mkdir("/home");
        fs.mkdir("/home/user1");
        fs.create("/home/user1/file.txt");
        String tree = fs.tree("/");
        assertNotNull(tree);
        assertTrue(tree.contains("home"));
        assertTrue(tree.contains("user1"));
        assertTrue(tree.contains("file.txt"));
    }
}
