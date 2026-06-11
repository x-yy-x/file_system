package com.fs.driver;

import com.fs.model.*;
import java.nio.charset.StandardCharsets;

/**
 * 序列化工具 — 负责 SuperBlock、Inode、DirectoryEntry 与字节数组的互转。
 */
public class Serializer {

    /** 将 SuperBlock 写入磁盘的第 0 块。 */
    public static void writeSuperBlock(DiskDriver disk, SuperBlock sb) {
        disk.writeInt(0, 0, sb.magicNumber);
        disk.writeInt(0, 4, sb.totalBlocks);
        disk.writeInt(0, 8, sb.blockSize);
        disk.writeInt(0, 12, sb.bitmapStartBlock);
        disk.writeInt(0, 16, sb.bitmapBlockCount);
        disk.writeInt(0, 20, sb.inodeStartBlock);
        disk.writeInt(0, 24, sb.inodeBlockCount);
        disk.writeInt(0, 28, sb.dataStartBlock);
        disk.writeInt(0, 32, sb.totalInodes);
        disk.writeInt(0, 36, sb.freeBlockCount);
        disk.writeInt(0, 40, sb.freeInodeCount);
        disk.writeInt(0, 44, sb.rootInode);
    }

    /** 从磁盘第 0 块读取 SuperBlock。 */
    public static SuperBlock readSuperBlock(DiskDriver disk) {
        SuperBlock sb = new SuperBlock();
        sb.magicNumber = disk.readInt(0, 0);
        sb.totalBlocks = disk.readInt(0, 4);
        sb.blockSize = disk.readInt(0, 8);
        sb.bitmapStartBlock = disk.readInt(0, 12);
        sb.bitmapBlockCount = disk.readInt(0, 16);
        sb.inodeStartBlock = disk.readInt(0, 20);
        sb.inodeBlockCount = disk.readInt(0, 24);
        sb.dataStartBlock = disk.readInt(0, 28);
        sb.totalInodes = disk.readInt(0, 32);
        sb.freeBlockCount = disk.readInt(0, 36);
        sb.freeInodeCount = disk.readInt(0, 40);
        sb.rootInode = disk.readInt(0, 44);
        return sb;
    }

    /** 将 Inode 序列化为 64 字节数组，写入 inode 表对应位置。 */
    public static void writeInode(DiskDriver disk, int inodeNum, Inode inode) {
        int perBlock = DiskDriver.BLOCK_SIZE / Inode.SIZE; // 8 inodes per block
        int block = DiskDriver.INODE_START + inodeNum / perBlock;
        int offset = (inodeNum % perBlock) * Inode.SIZE;

        disk.writeInt(block, offset, inode.inodeNumber);
        disk.writeByte(block, offset + 4, inode.fileType);
        disk.writeByte(block, offset + 5, inode.linkCount);
        disk.writeInt(block, offset + 6, inode.fileSize);
        disk.writeInt(block, offset + 10, inode.createTime);
        disk.writeInt(block, offset + 14, inode.modifyTime);

        for (int i = 0; i < Inode.DIRECT_COUNT; i++) {
            disk.writeInt(block, offset + 18 + i * 4, inode.direct[i]);
        }
        disk.writeInt(block, offset + 54, inode.singleIndirect);
        disk.writeInt(block, offset + 58, inode.doubleIndirect);
    }

    /** 从 inode 表读取一个 Inode。 */
    public static Inode readInode(DiskDriver disk, int inodeNum) {
        int perBlock = DiskDriver.BLOCK_SIZE / Inode.SIZE;
        int block = DiskDriver.INODE_START + inodeNum / perBlock;
        int offset = (inodeNum % perBlock) * Inode.SIZE;

        Inode inode = new Inode();
        inode.inodeNumber = disk.readInt(block, offset);
        inode.fileType = disk.readByte(block, offset + 4);
        inode.linkCount = disk.readByte(block, offset + 5);
        inode.fileSize = disk.readInt(block, offset + 6);
        inode.createTime = disk.readInt(block, offset + 10);
        inode.modifyTime = disk.readInt(block, offset + 14);

        for (int i = 0; i < Inode.DIRECT_COUNT; i++) {
            inode.direct[i] = disk.readInt(block, offset + 18 + i * 4);
        }
        inode.singleIndirect = disk.readInt(block, offset + 54);
        inode.doubleIndirect = disk.readInt(block, offset + 58);
        return inode;
    }

    /** 将 DirectoryEntry 序列化为 48 字节数组。 */
    public static byte[] dirEntryToBytes(DirectoryEntry entry) {
        byte[] data = new byte[DirectoryEntry.SIZE];
        // inodeNumber (4 bytes)
        data[0] = (byte) (entry.inodeNumber >> 24);
        data[1] = (byte) (entry.inodeNumber >> 16);
        data[2] = (byte) (entry.inodeNumber >> 8);
        data[3] = (byte) entry.inodeNumber;
        // fileType (1 byte)
        data[4] = entry.fileType;
        // nameLen (2 bytes)
        data[5] = (byte) (entry.nameLen >> 8);
        data[6] = (byte) entry.nameLen;
        // fileName (40 bytes, UTF-8)
        byte[] nameBytes = entry.fileName != null
                ? entry.fileName.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        int copyLen = Math.min(nameBytes.length, DirectoryEntry.MAX_NAME_BYTES);
        System.arraycopy(nameBytes, 0, data, 7, copyLen);
        return data;
    }

    /** 从 48 字节数组反序列化 DirectoryEntry。 */
    public static DirectoryEntry bytesToDirEntry(byte[] data) {
        DirectoryEntry entry = new DirectoryEntry();
        entry.inodeNumber = ((data[0] & 0xFF) << 24) |
                            ((data[1] & 0xFF) << 16) |
                            ((data[2] & 0xFF) << 8) |
                            (data[3] & 0xFF);
        entry.fileType = data[4];
        entry.nameLen = (short) (((data[5] & 0xFF) << 8) | (data[6] & 0xFF));
        entry.fileName = new String(data, 7, DirectoryEntry.MAX_NAME_BYTES, StandardCharsets.UTF_8).trim();
        return entry;
    }
}
