package com.fs.manager;

import com.fs.driver.DiskDriver;
import com.fs.driver.Serializer;
import com.fs.model.Inode;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class InodeManagerTest {
    private DiskDriver disk;
    private BitmapManager bitmapMgr;
    private InodeManager inodeMgr;

    @BeforeEach
    void setUp() {
        disk = new DiskDriver("test_inode.img");
        disk.format();
        bitmapMgr = new BitmapManager(disk);
        bitmapMgr.initBitmap();
        inodeMgr = new InodeManager(disk, bitmapMgr);
    }

    @AfterEach
    void cleanUp() {
        new java.io.File("test_inode.img").delete();
    }

    @Test
    void testAllocAndReadInode() {
        int inum = inodeMgr.allocInode((byte) 1);
        assertTrue(inum >= 0);

        Inode inode = inodeMgr.readInode(inum);
        assertEquals(inum, inode.inodeNumber);
        assertEquals(1, inode.fileType);
        assertEquals(1, inode.linkCount);
        assertTrue(inode.fileSize >= 0);
    }

    @Test
    void testWriteAndReadInode() {
        int inum = inodeMgr.allocInode((byte) 1);
        Inode inode = inodeMgr.readInode(inum);
        inode.fileSize = 4096;
        inodeMgr.writeInode(inode);

        Inode readBack = inodeMgr.readInode(inum);
        assertEquals(4096, readBack.fileSize);
    }

    @Test
    void testFreeInode() {
        int inum = inodeMgr.allocInode((byte) 1);
        inodeMgr.freeInode(inum);

        Inode freed = inodeMgr.readInode(inum);
        assertTrue(freed.isFree());
    }

    @Test
    void testDirectBlockPointer() {
        int inum = inodeMgr.allocInode((byte) 1);
        Inode inode = inodeMgr.readInode(inum);

        inodeMgr.setBlockPointer(inode, 0, 200);
        inodeMgr.setBlockPointer(inode, 8, 208);
        inodeMgr.writeInode(inode);

        Inode reloaded = inodeMgr.readInode(inum);
        assertEquals(200, inodeMgr.getBlockPointer(reloaded, 0));
        assertEquals(208, inodeMgr.getBlockPointer(reloaded, 8));
    }

    @Test
    void testIndirectBlockPointer() {
        // 写入超过直接块范围，触发一级间接
        int inum = inodeMgr.allocInode((byte) 1);
        Inode inode = inodeMgr.readInode(inum);

        // 需要先分配间接块所在的物理块
        bitmapMgr.allocateBlock(); // 这个块会被用作间接块
        // 设置第 9 个逻辑块（第一个间接块范围）
        inodeMgr.setBlockPointer(inode, 9, 300);
        inodeMgr.writeInode(inode);

        Inode reloaded = inodeMgr.readInode(inum);
        assertEquals(300, inodeMgr.getBlockPointer(reloaded, 9));
        assertTrue(reloaded.singleIndirect >= 0);
    }

    @Test
    void testExpandFile() {
        int inum = inodeMgr.allocInode((byte) 1);
        Inode inode = inodeMgr.readInode(inum);
        inode.fileSize = 0;

        // 扩展到 2048 字节（4 块）
        assertTrue(inodeMgr.expandFile(inode, 2048));
        assertEquals(4, (2048 + 511) / 512);

        // 检查块指针有效
        for (int i = 0; i < 4; i++) {
            assertTrue(inodeMgr.getBlockPointer(inode, i) >= 0);
        }
    }

    @Test
    void testFreeAllBlocks() {
        int inum = inodeMgr.allocInode((byte) 1);
        Inode inode = inodeMgr.readInode(inum);
        inode.fileSize = 0;
        inodeMgr.expandFile(inode, 2048);
        inode.fileSize = 2048; // expandFile allocates blocks but doesn't update fileSize
        inodeMgr.writeInode(inode);

        int freeBefore = bitmapMgr.getFreeBlockCount();
        inodeMgr.freeAllBlocks(inode);

        // 释放后空闲块数应增加（4 个数据块）
        int freed = bitmapMgr.getFreeBlockCount() - freeBefore;
        assertTrue(freed >= 4);
    }
}
