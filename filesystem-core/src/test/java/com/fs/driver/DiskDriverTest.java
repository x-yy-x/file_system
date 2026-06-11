package com.fs.driver;

import com.fs.model.SuperBlock;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DiskDriverTest {
    private static final String TEST_IMAGE = "test_disk.img";
    private DiskDriver disk;

    @BeforeEach
    void setUp() {
        disk = new DiskDriver(TEST_IMAGE);
        disk.format();
    }

    @AfterAll
    static void cleanUp() {
        new java.io.File(TEST_IMAGE).delete();
    }

    @Test
    @Order(1)
    void testReadWriteBlock() {
        byte[] data = new byte[512];
        for (int i = 0; i < 512; i++) data[i] = (byte) (i % 256);
        disk.writeBlock(100, data);

        byte[] read = disk.readBlock(100);
        assertArrayEquals(data, read);
    }

    @Test
    @Order(2)
    void testReadWriteOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> disk.readBlock(-1));
        assertThrows(IllegalArgumentException.class, () -> disk.readBlock(32768));
    }

    @Test
    @Order(3)
    void testSaveAndLoad() {
        // 写入 SuperBlock 使加载时魔数校验通过
        Serializer.writeSuperBlock(disk, new SuperBlock(32768, 512, 1, 8, 9, 128, 137, 1024, 0));
        // 写入测试数据
        disk.writeInt(100, 0, 0xDEADBEEF);
        disk.writeInt(200, 10, 12345);
        assertTrue(disk.saveToFile());

        // 重新加载
        DiskDriver disk2 = new DiskDriver(TEST_IMAGE);
        assertTrue(disk2.loadFromFile());

        assertEquals(0xDEADBEEF, disk2.readInt(100, 0));
        assertEquals(12345, disk2.readInt(200, 10));
    }

    @Test
    @Order(4)
    void testFormatAndSuperBlock() {
        disk.format();
        // 格式化后所有字节应为 0
        byte[] block0 = disk.readBlock(0);
        for (byte b : block0) assertEquals(0, b);

        // 写入 SuperBlock 后能读取
        Serializer.writeSuperBlock(disk, new SuperBlock(
                32768, 512, 1, 8, 9, 128, 137, 1024, 0
        ));
        SuperBlock sb = Serializer.readSuperBlock(disk);
        assertTrue(sb.isValid());
        assertEquals(32768, sb.totalBlocks);
    }

    @Test
    @Order(5)
    void testLoadInvalidFile() {
        DiskDriver d = new DiskDriver("nonexistent.img");
        assertFalse(d.loadFromFile());
    }
}
