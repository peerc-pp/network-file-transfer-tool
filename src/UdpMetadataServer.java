import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

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
                    File[] files = directory.listFiles();
                    StringBuilder fileList = new StringBuilder("服务器文件列表:\n");
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile()) {
                                fileList.append(file.getName()).append("\n");
                            }
                        }
                    }

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