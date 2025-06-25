package server;

import common.FileIntegrityChecker;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.io.EOFException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.CRC32;

public class ClientSessionHandler implements Runnable {
    private final Socket socket;

    public ClientSessionHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.println("会话处理器启动，处理客户端: " + socket.getRemoteSocketAddress());
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            // 1. 首先进行身份认证
            if (!SecurityServerHandler.handleAuthentication(dis, dos)) {
                System.out.println("认证失败，关闭会话: " + socket.getRemoteSocketAddress());
                return; // 认证失败，此线程结束
            }
            System.out.println("客户端 " + socket.getRemoteSocketAddress() + " 认证成功。");

            // 2. 认证成功后，进入指令循环，等待客户端发送指令
            while (!socket.isClosed()) {
                String command = dis.readUTF(); // 阻塞等待客户端发送指令

                switch (command) {
                    case "UPLOAD_RESUME": handleUploadResume(dis, dos); break;
                    case "QUERY_UPLOAD_PROGRESS": handleQueryUploadProgress(dis, dos); break;
                    case "DOWNLOAD_RESUME": handleDownloadResume(dis, dos); break;
                    case "UPLOAD":
                        System.out.println("收到 UPLOAD 指令");
                        receiveFile(dis, dos);
                        break;
                    case "DOWNLOAD":
                        System.out.println("收到 DOWNLOAD 指令 (功能待实现)");
                        sendFile(dis, dos); // 下载功能的实现位置
                        break;
                    case "QUIT":
                        System.out.println("客户端 " + socket.getRemoteSocketAddress() + " 请求断开连接。");
                        return; // 结束此线程，try-with-resources会自动关闭所有资源
                    default:
                        System.out.println("收到未知指令: " + command);
                        break;
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("客户端 " + socket.getRemoteSocketAddress() + " 意外断开连接。");
        } catch (Exception e) {
            System.err.println("与客户端 " + socket.getRemoteSocketAddress() + " 的会话出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("会话结束，关闭与 " + socket.getRemoteSocketAddress() + " 的连接。");
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ClientSessionHandler.java

    /**
     * 【已修正】根据客户端请求，发送服务器上的文件，并在之后发送校验和
     * @param dis 从客户端读取请求
     * @param dos 向客户端发送文件和校验和
     * @throws IOException
     */
    private void sendFile(DataInputStream dis, DataOutputStream dos) throws IOException {
        String requestedFileName = dis.readUTF();
        File fileToSend = new File("server_files", requestedFileName);

        if (fileToSend.exists() && fileToSend.isFile()) {
            dos.writeLong(fileToSend.length()); // 发送文件大小

            System.out.println("开始发送文件: " + requestedFileName);
            long checksum = 0L;

            // 发送文件内容
            try (FileInputStream fis = new FileInputStream(fileToSend)) {
                CRC32 crc = new CRC32(); // 创建CRC32实例
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                    crc.update(buffer, 0, bytesRead); // 实时更新CRC
                }
                dos.flush();
                checksum = crc.getValue(); // 获取最终的校验和
            }

            // 【新增】发送文件的CRC32校验和
            dos.writeLong(checksum);
            dos.flush();

            System.out.println("文件发送完毕: " + requestedFileName + ", 校验和: " + checksum);
        } else {
            dos.writeLong(-1L); // 文件不存在，发送-1作为错误信号
            System.out.println("请求的文件不存在: " + requestedFileName);
        }
    }


    // 从旧的FileServer中移过来的文件接收逻辑
    private void receiveFile(DataInputStream dis, DataOutputStream dos) throws IOException {
        String fileName = dis.readUTF();
        long fileLength = dis.readLong();
        System.out.println("接收文件: " + fileName + ", 大小: " + fileLength + " bytes");
        File directory = new File("server_files");
        if (!directory.exists()) {
            directory.mkdir();
        }
        // 生成唯一文件名：用户传输的文件名 + 时间戳
        String uniqueFileName = generateUniqueFileName(fileName);
        File file = new File(directory, uniqueFileName);




        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            while (totalRead < fileLength
                    && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileLength - totalRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
        }
        System.out.println("文件接收完毕: " + file.getAbsolutePath());
        long checksum = FileIntegrityChecker.calculateCRC32(file);
        dos.writeLong(checksum);
        dos.flush();
        System.out.println("已发送校验和: " + checksum);
    }

    private String generateUniqueFileName(String originalFileName) {
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String extension = originalFileName.substring(originalFileName.lastIndexOf('.'));
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String randomNum = String.valueOf((int) (Math.random() * 1000));
        return baseName + "_" + timestamp + "_" + randomNum + extension;
    }

    // 客户端查询服务端已接收的大小
    private void handleQueryUploadProgress(DataInputStream dis, DataOutputStream dos) throws IOException {
        String name = dis.readUTF();
        File f = new File("server_files", name + ".part");
        long pos = f.exists() ? f.length() : 0;
        dos.writeLong(pos);
        dos.flush();
    }

    // 服务器端断点上传
    private void handleUploadResume(DataInputStream dis, DataOutputStream dos) throws IOException {
        String name = dis.readUTF();
        long total = dis.readLong();
        long offset = dis.readLong();

        File dir = new File("server_files");
        dir.mkdirs();
        File part = new File(dir, name + ".part");
        try (RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
            raf.setLength(total);
            raf.seek(offset);
            byte[] buf = new byte[8192];
            long read = offset;
            while (read < total) {
                int n = dis.read(buf, 0, (int) Math.min(buf.length, total - read));
                if (n < 0) break;
                raf.write(buf,0,n);
                read += n;
            }
        }
        long checksum = FileIntegrityChecker.calculateCRC32(part);
        dos.writeLong(checksum);
        dos.flush();
        // 可选：上传完成后重命名去除 .part 扩展
    }

    // 服务器端断点下载
    private void handleDownloadResume(DataInputStream dis, DataOutputStream dos) throws IOException {
        String name = dis.readUTF();
        long offset = dis.readLong();

        File full = new File("server_files", name);
        if (!full.exists()) {
            dos.writeLong(-1);
            return;
        }
        long total = full.length();
        dos.writeLong(total);
        try (RandomAccessFile raf = new RandomAccessFile(full, "r")) {
            raf.seek(offset);
            CRC32 crc = new CRC32();
            byte[] buf = new byte[8192];
            long sent = offset;
            int n;
            while ((n = raf.read(buf)) != -1) {
                dos.write(buf,0,n);
                crc.update(buf,0,n);
                sent += n;
            }
            dos.flush();
            long checksum = crc.getValue();
            dos.writeLong(checksum);
            dos.flush();
        }
    }



}

