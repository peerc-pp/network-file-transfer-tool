import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.*;
import javax.swing.JFileChooser;

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

    /**
     * 打开一个图形化的文件选择器，让用户选择一个文件。
     * @return 用户选择的文件对象，如果用户取消了选择，则返回 null。
     */
    private static File chooseFile() {
        // 创建一个文件选择器对象
        JFileChooser fileChooser = new JFileChooser();

        // 设置对话框的标题
        fileChooser.setDialogTitle("请选择要上传的文件");

        // 设置默认打开的目录为当前项目目录 "."
        fileChooser.setCurrentDirectory(new File("."));

        // 打开文件选择对话框
        // showOpenDialog(null) 会让对话框显示在屏幕中央
        int result = fileChooser.showOpenDialog(null);

        // 判断用户是否点击了“打开”或“确定”按钮
        if (result == JFileChooser.APPROVE_OPTION) {
            // 获取用户选择的文件
            File selectedFile = fileChooser.getSelectedFile();
            System.out.println("您选择了文件: " + selectedFile.getAbsolutePath());
            return selectedFile;
        } else {
            System.out.println("您取消了文件选择。");
            return null; // 用户取消选择，返回 null
        }
    }

    public static void main(String[] args) {

        String serverIP="127.0.0.1";

        System.out.println("--- 测试UDP文件列表获取 ---");
        requestFileList(serverIP);
        System.out.println("----------------------------\n");
        try {
            // 1. 创建 Socket，连接到服务器的 9999 端口
            // "127.0.0.1" 或 "localhost" 代表本机
            Socket socket = new Socket(serverIP, 9999);
            System.out.println("--- 已连接到服务器 ---");
            // 处理身份认证 校验身份
            if (!SecurityClientHandler.handleAuthentication(socket)) {
                System.out.println("登陆失败");
                socket.close();
                return;
            }
            // 要发送的文件的路径 (为了测试，可以先写死)
            // 在你的项目根目录下创建一个名为 "test.txt" 的文件用于测试
            // 1. 调用文件选择器，让用户选择文件
            File file = chooseFile();

            // 2. 检查用户是否选择了文件
            if (file == null) {
                System.out.println("没有选择任何文件，程序退出。");
                return; // 退出程序
            }


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
                // 接收并验证校验和 在发送完后校验文件是否发送完成
                try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
                    long serverChecksum = dis.readLong();
                    FileIntegrityChecker.verifyChecksum(file, serverChecksum);
                }

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