package server;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpMetadataServer implements Runnable {
    @Override
    public void run() {
        // 监听一个和TCP不同的端口，例如 9998
        try (DatagramSocket socket = new DatagramSocket(9998)) {
            System.out.println("--- UDP元数据服务器已在端口 9998 启动 ---");
            while (true) {
                // 1. 准备接收UDP包
                byte[] buffer = new byte[1024];
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(requestPacket); // 阻塞等待

                // 2. 解析请求
                String requestMessage = new String(requestPacket.getData(), 0, requestPacket.getLength()).trim();
                if ("LIST_FILES".equals(requestMessage)) {
                    System.out.println("收到来自 " + requestPacket.getAddress() + " 的文件列表请求");

                    // 3. 准备响应数据
                    File directory = new File("server_files");

                    // --- 添加第一行侦探代码 ---
                    // 打印服务器正在扫描的文件夹的绝对路径
                    System.out.println("[服务器调试] 正在扫描目录: " + directory.getAbsolutePath());

                    File[] files = directory.listFiles();
                    StringBuilder fileList = new StringBuilder("服务器文件列表:\n");
                    if (files != null) {
                        // --- 添加第二行侦探代码 ---
                        // 打印找到了多少个文件/文件夹
                        System.out.println("[服务器调试] 在该目录中找到了 " + files.length + " 个条目。");
                        for (File file : files) {
                            if (file.isFile()) {
                                fileList.append(file.getName())
                                        .append("|")
                                        .append(file.length()) // 文件大小
                                        .append("|")
                                        .append(file.lastModified()) // 修改时间（毫秒时间戳）
                                        .append("\n"); // 每个文件占一行
                            }
                        }
                    }else {
                        // --- 添加第三行侦探代码 ---
                        System.out.println("[服务器调试] 'server_files' 目录不存在或不是一个目录！");
                    }
                    // --- 添加第四行侦探代码 ---
                    // 打印最终准备发送的内容
                    System.out.println("[服务器调试] 准备发送的响应内容:\n---\n" + fileList.toString() + "---");

                    // 4. 将响应数据发送回客户端
                    byte[] responseData = fileList.toString().getBytes();
                    InetAddress clientAddress = requestPacket.getAddress();
                    int clientPort = requestPacket.getPort();
                    DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                    socket.send(responsePacket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}