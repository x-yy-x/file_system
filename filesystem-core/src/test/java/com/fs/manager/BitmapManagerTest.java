package com.fs.manager;

import com.fs.driver.DiskDriver;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class BitmapManagerTest {
    private DiskDriver disk;
    private BitmapManager bitmapMgr;

    @BeforeEach
    void setUp() {
        disk = new DiskDriver("test_bm.img");
        disk.format();
        bitmapMgr = new BitmapManager(disk);
    }

    @AfterEach
    void cleanUp() {
        new java.io.File("test_bm.img").delete();
    }

    @Test
    void testInitBitmap() {
        bitmapMgr.initBitmap();
        // DATA_START 之前的块应标记为已分配
        for (int i = 0; i < DiskDriver.DATA_START; i++) {
            assertFalse(bitmapMgr.isFree(i));
        }
        // DATA_START 之后的块应空闲
        assertTrue(bitmapMgr.isFree(DiskDriver.DATA_START));
    }

    @Test
    void testAllocateBlock() {
        bitmapMgr.initBitmap();
        int block = bitmapMgr.allocateBlock();
        assertTrue(block >= DiskDriver.DATA_START);
        assertFalse(bitmapMgr.isFree(block));
    }

    @Test
    void testFreeBlock() {
        bitmapMgr.initBitmap();
        int block = bitmapMgr.allocateBlock();
        assertFalse(bitmapMgr.isFree(block));

        bitmapMgr.freeBlock(block);
        assertTrue(bitmapMgr.isFree(block));
    }

    @Test
    void testGetFreeBlockCount() {
        bitmapMgr.initBitmap();
        int initialFree = bitmapMgr.getFreeBlockCount();

        bitmapMgr.allocateBlock();
        assertEquals(initialFree - 1, bitmapMgr.getFreeBlockCount());

        bitmapMgr.allocateBlock();
        assertEquals(initialFree - 2, bitmapMgr.getFreeBlockCount());
    }

    @Test
    void testBitmapPersistence() {
        bitmapMgr.initBitmap();
        bitmapMgr.allocateBlock();
        bitmapMgr.saveBitmap();

        // 重新加载
        BitmapManager bm2 = new BitmapManager(disk);
        bm2.loadBitmap();
        assertFalse(bm2.isFree(DiskDriver.DATA_START));
        assertTrue(bm2.isFree(DiskDriver.DATA_START + 1));
    }
}
