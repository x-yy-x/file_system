package com.fs.driver;

import com.fs.model.SuperBlock;
import java.io.*;

/**
 * 虚拟磁盘驱动 — 管理内存字节数组，提供盘块级读写和持久化。
 *
 * 布局：
 *   Block 0:         SuperBlock (512B)
 *   Block 1..8:      Bitmap (4096B = 32768 bits)
 *   Block 9..136:    Inode 表 (128 blocks = 65536B)
 *   Block 137..:     数据区
 */
public class DiskDriver {
    public static final int DEFAULT_DISK_SIZE = 16 * 1024 * 1024; // 16 MB
    public static final int BLOCK_SIZE = 512;
    public static final int TOTAL_BLOCKS = DEFAULT_DISK_SIZE / BLOCK_SIZE; // 32768

    public static final int BITMAP_START = 1;
    public static final int BITMAP_BLOCKS = 8;
    public static final int INODE_START = 9;
    public static final int INODE_BLOCKS = 128;
    public static final int DATA_START = 137;
    public static final int TOTAL_INODES = 1024;
    public static final int ROOT_INODE = 0;

    private final byte[] disk;
    private final String imagePath;

    public DiskDriver(String imagePath) {
        this.disk = new byte[DEFAULT_DISK_SIZE];
        this.imagePath = imagePath;
    }

    /** 读取一个盘块的全部数据。 */
    public byte[] readBlock(int blockNum) {
        checkBlock(blockNum);
        int offset = blockNum * BLOCK_SIZE;
        byte[] data = new byte[BLOCK_SIZE];
        System.arraycopy(disk, offset, data, 0, BLOCK_SIZE);
        return data;
    }

    /** 写入一个盘块的数据。 */
    public void writeBlock(int blockNum, byte[] data) {
        checkBlock(blockNum);
        if (data.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("数据长度必须为 " + BLOCK_SIZE + " 字节");
        }
        int offset = blockNum * BLOCK_SIZE;
        System.arraycopy(data, 0, disk, offset, BLOCK_SIZE);
    }

    /** 从盘块中读取一个 int (4 字节，大端序)。 */
    public int readInt(int blockNum, int offset) {
        int pos = blockNum * BLOCK_SIZE + offset;
        return ((disk[pos] & 0xFF) << 24) |
               ((disk[pos + 1] & 0xFF) << 16) |
               ((disk[pos + 2] & 0xFF) << 8) |
               (disk[pos + 3] & 0xFF);
    }

    /** 向盘块中写入一个 int。 */
    public void writeInt(int blockNum, int offset, int value) {
        int pos = blockNum * BLOCK_SIZE + offset;
        disk[pos]     = (byte) (value >> 24);
        disk[pos + 1] = (byte) (value >> 16);
        disk[pos + 2] = (byte) (value >> 8);
        disk[pos + 3] = (byte) value;
    }

    /** 从盘块中读取一个 byte。 */
    public byte readByte(int blockNum, int offset) {
        return disk[blockNum * BLOCK_SIZE + offset];
    }

    /** 向盘块中写入一个 byte。 */
    public void writeByte(int blockNum, int offset, byte value) {
        disk[blockNum * BLOCK_SIZE + offset] = value;
    }

    /** 将整个虚拟磁盘持久化到文件。 */
    public boolean saveToFile() {
        try (FileOutputStream fos = new FileOutputStream(imagePath);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            bos.write(disk);
            bos.flush();
            return true;
        } catch (IOException e) {
            System.err.println("保存失败: " + e.getMessage());
            return false;
        }
    }

    /** 从磁盘文件加载虚拟磁盘。 */
    public boolean loadFromFile() {
        File file = new File(imagePath);
        if (!file.exists()) return false;

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            byte[] header = new byte[4];
            if (bis.read(header) != 4) return false;
            int magic = ((header[0] & 0xFF) << 24) |
                        ((header[1] & 0xFF) << 16) |
                        ((header[2] & 0xFF) << 8) |
                        (header[3] & 0xFF);
            if (magic != SuperBlock.MAGIC) return false;

            System.arraycopy(header, 0, disk, 0, 4);
            int remaining = DEFAULT_DISK_SIZE - 4;
            int read = bis.read(disk, 4, remaining);
            return read == remaining;
        } catch (IOException e) {
            System.err.println("加载失败: " + e.getMessage());
            return false;
        }
    }

    /** 格式化虚拟磁盘 — 清零所有数据。 */
    public void format() {
        java.util.Arrays.fill(disk, (byte) 0);
    }

    /** 获取磁盘总大小。 */
    public int getDiskSize() { return DEFAULT_DISK_SIZE; }
    public int getBlockSize() { return BLOCK_SIZE; }
    public int getTotalBlocks() { return TOTAL_BLOCKS; }
    public String getImagePath() { return imagePath; }

    private void checkBlock(int blockNum) {
        if (blockNum < 0 || blockNum >= TOTAL_BLOCKS) {
            throw new IllegalArgumentException("块号越界: " + blockNum);
        }
    }
}
