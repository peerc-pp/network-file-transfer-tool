package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import common.FileIntegrityChecker;
public class FileServer {
    public static void main(String[] args) {
// 启动UDP元数据服务器线程
        Thread udpServerThread = new Thread(new UdpMetadataServer());
        udpServerThread.start();

        System.out.println("--- TCP服务器启动，等待客户端连接 端口:9999 ---");
        try (ServerSocket serverSocket = new ServerSocket(9999)) {
            while (true) {
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                    new Thread(new ClientSessionHandler(socket)).start();

                } catch (Exception e) {
                    System.err.println("与客户端的会话出错: " + e.getMessage());
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void saveFile(DataInputStream dis, DataOutputStream dos) throws IOException {
// --- 协议：读取文件名和大小 ---
        String fileName = dis.readUTF();
        long fileLength = dis.readLong();
        System.out.println("接收文件: " + fileName + ", 大小: " + fileLength + " bytes");
        File directory = new File("server_files");
        if (!directory.exists()) {
            directory.mkdir();
        }
        File file = new File(directory, fileName);
// --- 接收文件内容 ---
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            while (totalRead < fileLength && (bytesRead = dis.read(buffer, 0, (int)
                    Math.min(buffer.length, fileLength - totalRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
        }
        System.out.println("文件接收完毕: " + file.getAbsolutePath());
// --- 发送文件校验和 ---
        long checksum = FileIntegrityChecker.calculateCRC32(file);
        dos.writeLong(checksum);
        dos.flush();
        System.out.println("已发送校验和: " + checksum);
        System.out.println("----------------------------------------");
    }
}