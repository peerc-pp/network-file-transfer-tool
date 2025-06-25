package server;

import java.net.ServerSocket;
import java.net.Socket;

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
                    System.out.println("新的客户端连接: " + socket.getRemoteSocketAddress());
                    // 为每个客户端创建新线程处理
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
}