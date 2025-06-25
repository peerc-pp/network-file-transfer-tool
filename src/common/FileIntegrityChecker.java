package common;

import java.util.zip.CRC32;
import java.io.*;

public class FileIntegrityChecker {

    /**
     * 计算文件的CRC32校验和
     * @param file 要计算校验和的文件
     * @return CRC32校验和值
     * @throws IOException 如果读取文件时发生错误
     */
    public static long calculateCRC32(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                crc.update(buffer, 0, bytesRead);
            }
            return crc.getValue();
        }
    }

    /**
     * 获取文件校验和的字符串表示（兼容旧代码）
     * @param file 要计算校验和的文件
     * @return 校验和的字符串表示
     * @throws IOException 如果读取文件时发生错误
     */
    public static String getFileChecksum(File file) throws IOException {
        return String.valueOf(calculateCRC32(file));
    }

    /**
     * 验证文件完整性
     * @param receivedFile 接收到的文件
     * @param originalChecksum 原始校验和
     */
    public static void verifyChecksum(File receivedFile, long originalChecksum) {
        try {
            long receivedChecksum = calculateCRC32(receivedFile);
            if (receivedChecksum == originalChecksum) {
                System.out.println("File integrity verified successfully.");
            } else {
                System.out.println("File corruption detected! Checksum mismatch.");
            }
        } catch (IOException e) {
            System.err.println("Checksum verification failed: " + e.getMessage());
        }
    }
}