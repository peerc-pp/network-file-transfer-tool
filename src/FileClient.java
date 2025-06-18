import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.*;

public class FileClient {

    public static void requestFileList(String serverIp) {
        try (DatagramSocket socket = new DatagramSocket()) {
            // 设置5秒超时，防止服务器没响应时无限等待
            socket.setSoTimeout(5000);

            // 1. 准备并发送请求包
            String requestMessage = "LIST_FILES";
            byte[] requestData = requestMessage.getBytes();
            InetAddress serverAddress = InetAddress.getByName(serverIp);
            DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, serverAddress, 9998);
            socket.send(requestPacket);
            System.out.println("已向服务器发送文件列表请求...");

            // 2. 准备接收响应包
            byte[] buffer = new byte[4096]; // 缓冲区大一点，防止文件列表太长
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);

            // 3. 解析并打印响应
            String fileList = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println(fileList);

        } catch (SocketTimeoutException e) {
            System.err.println("请求超时，服务器未在5秒内响应。");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        System.out.println("--- 测试UDP文件列表获取 ---");
        requestFileList("127.0.0.1");
        System.out.println("----------------------------\n");
        try {
            // 要发送的文件的路径 (为了测试，可以先写死)
            // 在你的项目根目录下创建一个名为 "test.txt" 的文件用于测试
            String filePath = "test2.txt";
            File file = new File(filePath);

            if (!file.exists()) {
                System.out.println("文件不存在: " + filePath);
                return;
            }

            // 1. 创建 Socket，连接到服务器的 9999 端口
            // "127.0.0.1" 或 "localhost" 代表本机
            Socket socket = new Socket("127.0.0.1", 9999);
            System.out.println("--- 已连接到服务器 ---");

            // 使用 try-with-resources
            try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 FileInputStream fis = new FileInputStream(file)) {

                // --- 开始执行我们的应用层协议 ---

                // 1. 发送文件名
                dos.writeUTF(file.getName());
                dos.flush();
                System.out.println("已发送文件名: " + file.getName());

                // 2. 发送文件大小
                dos.writeLong(file.length());
                dos.flush();
                System.out.println("已发送文件大小: " + file.length() + " bytes");

                // 3. 发送文件数据
                System.out.println("开始发送文件...");
                byte[] buffer = new byte[8192]; // 8KB 缓冲区
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                dos.flush(); // 确保所有数据都被发送出去

                System.out.println("文件发送完毕!");
                System.out.println("----------------------------------------");

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                socket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}