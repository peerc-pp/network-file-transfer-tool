package client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import server.SecurityServerHandler;
import common.FileIntegrityChecker;
import javafx.concurrent.Task; // 导入 Task
import ui.UIFile;

public class FileTransferService {
    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;

    // 用于进度更新的回调接口
    @FunctionalInterface
    public interface ProgressUpdateCallback {
        void onProgressUpdate(double progress);
    }

    public void connectAndAuthenticate(String host, int port, String username, String password) throws Exception {
        this.socket = new Socket(host, port);
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());

        // 使用重构后的认证逻辑
        boolean authenticated = handleAuthentication(username, password);
        if (!authenticated) {
            disconnect(); // 认证失败，清理资源
            throw new Exception("认证失败！请检查用户名和密码。");
        }
    }

    private boolean handleAuthentication(String username, String password) throws IOException {
        // ... (这是从原SecurityClientHandler精简和重构后的逻辑)
        String serverMessage = dis.readUTF();
        if (!"AUTH_REQUEST".equals(serverMessage)) return false;

        dos.writeUTF("AUTH " + username);
        serverMessage = dis.readUTF();

        if ("NAME_SUCCESS".equals(serverMessage)) {
            String passwordHash = SecurityServerHandler.sha1(password); // 复用sha1方法
            dos.writeUTF("AUTH " + passwordHash);
            serverMessage = dis.readUTF();
            return "AUTH_SUCCESS".equals(serverMessage);
        } else if ("REGISTER_REQUIRED".equals(serverMessage)) {
            // 在GUI版本中，注册流程可以更复杂，这里简化为使用相同密码注册
            String passwordHash = SecurityServerHandler.sha1(password);
            dos.writeUTF("REGISTER " + passwordHash);
            serverMessage = dis.readUTF();
            return "AUTH_SUCCESS".equals(serverMessage);
        }
        return false;
    }


    public List<UIFile> getRemoteFileList(String host) throws IOException {
        int udpPort = 9998; // 根据服务器设计
        List<UIFile> fileList = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000); // 5秒超时
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

                    // 使用新的构造函数来创建包含完整信息的UIFile对象
                    fileList.add(new UIFile(name, size, lastModified));
                }
            }
        }
        return fileList;
    }

    /**
     * 【已修正】准备上传：发送指令和文件元数据，并返回可用的输出流。
     * @param file 要上传的文件
     * @return 用于写入文件数据的 DataOutputStream
     * @throws IOException
     */
    public DataOutputStream prepareUpload(File file) throws IOException {
        dos.writeUTF("UPLOAD");
        dos.writeUTF(file.getName());
        dos.writeLong(file.length());
        dos.flush();
        return dos; // 将流返回给Task使用
    }

    /**
     * 【已修正】完成上传：接收并验证服务器返回的校验和。
     * @param localFile 本地上传的文件，用于计算本地校验和
     * @throws IOException
     */
    public void finishUpload(File localFile) throws IOException {
        long serverChecksum = dis.readLong();
        long localChecksum = FileIntegrityChecker.calculateCRC32(localFile);
        if (serverChecksum != localChecksum) {
            throw new IOException("文件校验失败！上传的文件可能已损坏。");
        }
    }

    // FileTransferService.java
    public DataInputStream getInputStream() {
        return this.dis;
    }

    /**
     * 【新增】准备下载：发送指令，接收文件大小
     * @param remoteFileName 要下载的文件名
     * @return 服务器上该文件的大小，如果文件不存在则返回-1
     * @throws IOException
     */
    public long prepareDownload(String remoteFileName) throws IOException {
        dos.writeUTF("DOWNLOAD");
        dos.writeUTF(remoteFileName);
        dos.flush();
        return dis.readLong(); // 返回文件大小或-1
    }

    /**
     * 【新增】完成下载：接收并验证校验和
     * @param downloadedFile 刚刚下载到本地的文件
     * @throws IOException
     */
    public void finishDownload(File downloadedFile) throws IOException {
        long serverChecksum = dis.readLong();
        long localChecksum = FileIntegrityChecker.calculateCRC32(downloadedFile);
        if (serverChecksum != localChecksum) {
            throw new IOException("文件校验失败！下载的文件可能已损坏。");
        }
        System.out.println("下载文件校验成功！");
    }

    /**
     * 向服务器发送退出指令，并关闭本地资源
     * @throws IOException
     */
    public void disconnect() throws IOException {
        try {
            if (dos != null) {
                dos.writeUTF("QUIT");
                dos.flush();
            }
        } finally {
            if (dos != null) dos.close();
            if (dis != null) dis.close();
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }
}