package com.fs.fs;

import com.fs.model.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileSystemTest {
    private static final String TEST_IMAGE = "test_fs.img";
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
    void testCreateFile() {
        assertTrue(fs.create("/test.txt"));
        List<EntryInfo> entries = fs.ls("/");
        assertTrue(entries.stream().anyMatch(e -> e.name.equals("test.txt")));
    }

    @Test
    @Order(2)
    void testOpenCloseFile() {
        fs.create("/test.txt");
        int fd = fs.open("/test.txt", FileDescriptor.READ_WRITE);
        assertTrue(fd >= 0);
        assertTrue(fs.close(fd));
        // 重复关闭应失败
        assertFalse(fs.close(fd));
    }

    @Test
    @Order(3)
    void testWriteAndRead() {
        fs.create("/test.txt");
        int fd = fs.open("/test.txt", FileDescriptor.READ_WRITE);
        String content = "Hello, 文件系统!";
        int written = fs.write(fd, content.getBytes());
        int expectedBytes = content.getBytes().length;
        assertEquals(expectedBytes, written);
        fs.close(fd);

        // 重新打开读取
        fd = fs.open("/test.txt", FileDescriptor.READ);
        byte[] data = fs.read(fd, 100);
        assertEquals(content, new String(data).trim());
        fs.close(fd);
    }

    @Test
    @Order(4)
    void testWriteMultipleBlocks() {
        fs.create("/large.txt");
        int fd = fs.open("/large.txt", FileDescriptor.READ_WRITE);
        // 写入超过一块的数据 (512 bytes)
        byte[] data = new byte[2000];
        for (int i = 0; i < 2000; i++) data[i] = (byte) (i % 256);
        int written = fs.write(fd, data);
        assertEquals(2000, written);
        fs.close(fd);

        fd = fs.open("/large.txt", FileDescriptor.READ);
        byte[] readBack = fs.read(fd, 3000);
        assertEquals(2000, readBack.length);
        for (int i = 0; i < 2000; i++) assertEquals(data[i], readBack[i]);
        fs.close(fd);
    }

    @Test
    @Order(5)
    void testDeleteFile() {
        fs.create("/todelete.txt");
        assertTrue(fs.delete("/todelete.txt"));
        // 删除后不可打开
        int fd = fs.open("/todelete.txt", FileDescriptor.READ);
        assertTrue(fd < 0);
    }

    @Test
    @Order(6)
    void testDeleteOpenedFile() {
        fs.create("/open.txt");
        fs.open("/open.txt", FileDescriptor.READ_WRITE);
        assertFalse(fs.delete("/open.txt"));
    }

    @Test
    @Order(7)
    void testRename() {
        fs.create("/old.txt");
        assertTrue(fs.rename("/old.txt", "/new.txt"));
        assertNull(fs.stat("/old.txt"));
        assertNotNull(fs.stat("/new.txt"));
    }

    @Test
    @Order(8)
    void testCopyAndMove() {
        fs.create("/src.txt");
        int fd = fs.open("/src.txt", FileDescriptor.READ_WRITE);
        fs.write(fd, "copy test data".getBytes());
        fs.close(fd);

        // 复制
        assertTrue(fs.copy("/src.txt", "/dst.txt"));
        FileStat dstStat = fs.stat("/dst.txt");
        assertNotNull(dstStat);
        assertTrue(dstStat.size > 0);

        // 移动
        assertTrue(fs.move("/src.txt", "/moved.txt"));
        assertNull(fs.stat("/src.txt"));
        assertNotNull(fs.stat("/moved.txt"));
    }
}
