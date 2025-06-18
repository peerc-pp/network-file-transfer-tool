
// 3. 文件校验模块 (FileIntegrityChecker.java)
import java.util.zip.CRC32;
import java.io.*;

public class FileIntegrityChecker {
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
