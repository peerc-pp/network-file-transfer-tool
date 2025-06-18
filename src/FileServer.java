import java.io.*;
import java.net.*;
import java.lang.*;
public class FileServer {


    public static void main(String[] args) {
        //UDP
        Thread udpServerThread = new Thread(new UdpMetadataServer());
        udpServerThread.start();
        //TCP
        try {
            // 1. 创建 ServerSocket，监听 9999 端口
            ServerSocket serverSocket = new ServerSocket(9999);
            System.out.println("--- 服务器启动，等待客户端连接 ---");

            // 2. 循环等待客户端连接 (accept() 是阻塞的)
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("一个客户端已连接：" + socket.getRemoteSocketAddress());
                // 先进行身份认证 当用户身份验证不通过时断开连接
                if (!SecurityServerHandler.handleAuthentication(socket)) {
                    System.out.println("Authentication failed for client: " + socket.getInetAddress());
                    socket.close();
                    return;
                }

                // 为每个客户端连接创建一个新的线程来处理文件传输
                // (这是为后续多线程改造预留的思路，目前先在主线程处理)
                saveFile(socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveFile(Socket socket) {
        // 使用 try-with-resources 确保流被正确关闭
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            // --- 开始执行我们的应用层协议 ---

            // 1. 读取文件名
            String fileName = dis.readUTF();
            System.out.println("接收到文件名: " + fileName);

            // 2. 读取文件大小
            long fileLength = dis.readLong();
            System.out.println("接收到文件大小: " + fileLength + " bytes");

            // 3. 创建文件输出流，准备保存文件
            // 定义文件存储的目录
            File directory = new File("server_files");
            if (!directory.exists()) {
                directory.mkdir(); // 如果目录不存在，则创建
            }
            File file = new File(directory.getAbsolutePath() + File.separatorChar + fileName);
            FileOutputStream fos = new FileOutputStream(file);

            // 4. 从输入流读取文件数据，并写入到文件输出流
            System.out.println("开始接收文件...");
            byte[] buffer = new byte[8192]; // 8KB 缓冲区
            int bytesRead;
            long totalRead = 0;

            while (totalRead < fileLength && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileLength - totalRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            fos.close(); // 关闭文件输出流

            System.out.println("文件接收完毕，保存在: " + file.getAbsolutePath());
            System.out.println("----------------------------------------");
            // 传输完成后发送校验和
            long checksum = FileIntegrityChecker.calculateCRC32(file);//file为传输完成的文件，发送校验码。
            try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
                dos.writeLong(checksum);
            }

        }
        catch (Exception  e) {
            System.err.println("与客户端 " + socket.getRemoteSocketAddress() + " 的连接出错: " + e.getMessage());
        } finally {
            try {
                socket.close(); // 确保Socket被关闭
            } catch (Exception e) {
                // ignore
            }
        }
    }
}