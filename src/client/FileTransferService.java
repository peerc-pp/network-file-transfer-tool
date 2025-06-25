package client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import server.SecurityServerHandler;
import common.FileIntegrityChecker;
import ui.UIFile;

public class FileTransferService {
    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;

    // 批量进度回调接口
    @FunctionalInterface
    public interface BatchProgressCallback {
        void onProgress(int currentFileIndex, int totalFiles, double currentFileProgress);
    }

    public void connectAndAuthenticate(String host, int port, String username, String password) throws Exception {
        this.socket = new Socket(host, port);
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());

        boolean authenticated = handleAuthentication(username, password);
        if (!authenticated) {
            disconnect();
            throw new Exception("认证失败！请检查用户名和密码。");
        }
    }


    private boolean handleAuthentication(String username, String password) throws IOException {
        String serverMessage = dis.readUTF();
        if (!"AUTH_REQUEST".equals(serverMessage)) return false;

        dos.writeUTF("AUTH " + username);
        serverMessage = dis.readUTF();

        if ("NAME_SUCCESS".equals(serverMessage)) {
            String passwordHash = SecurityServerHandler.sha1(password);
            dos.writeUTF("AUTH " + passwordHash);
            serverMessage = dis.readUTF();
            return "AUTH_SUCCESS".equals(serverMessage);
        } else if ("REGISTER_REQUIRED".equals(serverMessage)) {
            String passwordHash = SecurityServerHandler.sha1(password);
            dos.writeUTF("REGISTER " + passwordHash);
            serverMessage = dis.readUTF();
            return "REGISTER_SUCCESS".equals(serverMessage);
        }
        return false;
    }

    public List<UIFile> getRemoteFileList(String host) throws IOException {
        int udpPort = 9998;
        List<UIFile> fileList = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            byte[] requestData = "LIST_FILES".getBytes();
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, address, udpPort);
            socket.send(requestPacket);
            byte[] buffer = new byte[4096];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (response.trim().isEmpty()) {
                return fileList; // 如果服务器没文件，返回空列表
            }
            String[] lines = response.split("\n");
            for (String line : lines) {
                String[] parts = line.split("\\|"); // 注意：'|'是特殊字符，需要转义
                if (parts.length == 3) {
                    String name = parts[0];
                    long size = Long.parseLong(parts[1]);
                    long lastModified = Long.parseLong(parts[2]);
                    fileList.add(new UIFile(name, size, lastModified));// 使用新的构造函数来创建包含完整信息的UIFile对象

            // 跳过第一行（标题）
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 3) {
                        String name = parts[0];
                        long size = Long.parseLong(parts[1]);
                        long lastModified = Long.parseLong(parts[2]);
                        fileList.add(new UIFile(name, size, lastModified));
                    }
                }
            }}return fileList;}
            }
        }

        return fileList;
    }

    // 单文件上传准备
    public DataOutputStream prepareUpload(File file) throws IOException {
        dos.writeUTF("UPLOAD");
        dos.writeUTF(file.getName());
        dos.writeLong(file.length());
        return dos;
    }

    // 单文件上传完成
    public void finishUpload(File file) throws IOException {
        long checksum = FileIntegrityChecker.calculateCRC32(file);
        dos.writeLong(checksum);
        dos.flush();

        long serverChecksum = dis.readLong();
        if (checksum != serverChecksum) {
            throw new IOException("文件校验失败！");
        }
    }

    // 批量上传
    public void batchUpload(List<File> files, BatchProgressCallback callback) throws IOException {
        dos.writeUTF("BATCH_UPLOAD");
        dos.writeInt(files.size());

        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);

            // 发送文件信息
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            // 传输文件内容
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalSent = 0;
                long fileSize = file.length();

                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;

                    // 更新进度
                    double fileProgress = (double) totalSent / fileSize;
                    if (callback != null) {
                        callback.onProgress(i, files.size(), fileProgress);
                    }
                }
                dos.flush();
            }

            // 发送校验和
            long checksum = FileIntegrityChecker.calculateCRC32(file);
            dos.writeLong(checksum);
            dos.flush();

            // 等待服务器确认
            long serverChecksum = dis.readLong();
            if (checksum != serverChecksum) {
                throw new IOException("文件校验失败: " + file.getName());
            }
        }
    }

    // 单文件下载准备
    public long prepareDownload(String fileName) throws IOException {
        dos.writeUTF("DOWNLOAD");
        dos.writeUTF(fileName);

        long fileSize = dis.readLong();
        return fileSize; // 如果是-1表示文件不存在
    }

    // 单文件下载完成
    public void finishDownload(File file) throws IOException {
        long serverChecksum = dis.readLong();
        long localChecksum = FileIntegrityChecker.calculateCRC32(file);

        if (serverChecksum != localChecksum) {
            file.delete();
            throw new IOException("文件校验失败！文件可能已损坏。");
        }
    }

    // 批量下载
    public void batchDownload(List<String> fileNames, File destinationDir, BatchProgressCallback callback) throws IOException {
        dos.writeUTF("BATCH_DOWNLOAD");
        dos.writeInt(fileNames.size());

        // 发送所有文件名
        for (String fileName : fileNames) {
            dos.writeUTF(fileName);
        }

        // 接收每个文件
        for (int i = 0; i < fileNames.size(); i++) {
            String fileName = fileNames.get(i);
            long fileSize = dis.readLong();

            if (fileSize == -1) {
                throw new IOException("文件未找到: " + fileName);
            }

            File outputFile = new File(destinationDir, fileName);

            // 接收文件内容
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;

                while (totalRead < fileSize) {
                    int toRead = (int) Math.min(buffer.length, fileSize - totalRead);
                    bytesRead = dis.read(buffer, 0, toRead);
                    if (bytesRead == -1) break;

                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    // 更新进度
                    double fileProgress = (double) totalRead / fileSize;
                    if (callback != null) {
                        callback.onProgress(i, fileNames.size(), fileProgress);
                    }
                }
            }

            // 验证校验和
            long serverChecksum = dis.readLong();
            long localChecksum = FileIntegrityChecker.calculateCRC32(outputFile);

            if (serverChecksum != localChecksum) {
                outputFile.delete();
                throw new IOException("文件校验失败: " + fileName);
            }
        }
    }

    public DataInputStream getInputStream() {
        return dis;
    }

    public void disconnect() throws IOException {
        if (dos != null) {
            try {
                dos.writeUTF("QUIT");
            } catch (IOException ignored) {}
            dos.close();
        }
        if (dis != null) dis.close();
        if (socket != null) socket.close();
    }
    // 查询已上传的字节数
    public long queryUploadedBytes(String name) throws IOException {
        dos.writeUTF("QUERY_UPLOAD_PROGRESS");
        dos.writeUTF(name);
        dos.flush();
        return dis.readLong();
    }

    // 断点上传
    public DataOutputStream prepareUploadResume(File f, long offset) throws IOException {
        dos.writeUTF("UPLOAD_RESUME");
        dos.writeUTF(f.getName());
        dos.writeLong(f.length());
        dos.writeLong(offset);
        dos.flush();
        return dos;
    }
    // 断点下载
    public long prepareDownloadResume(String name, long have) throws IOException {
        dos.writeUTF("DOWNLOAD_RESUME");
        dos.writeUTF(name);
        dos.writeLong(have);
        dos.flush();
        return dis.readLong();
    }



}