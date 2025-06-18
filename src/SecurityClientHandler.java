// 2. 客户端安全认证模块 (SecurityClientHandler.java)
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.io.*;

public class SecurityClientHandler {
    public static void handleAuthentication(Socket clientSocket) {
        PrintWriter out = null;
        BufferedReader in = null;
        try (Scanner scanner = new Scanner(System.in)) {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // 接收认证请求
            String serverMessage = in.readLine();
            if (!"AUTH_REQUEST".equals(serverMessage)) {
                System.err.println("预期认证请求，但收到: " + serverMessage);
                return;
            }

            // 获取用户密码
            System.out.print("请输入密码: ");
            String password = scanner.nextLine();
            String passwordHash = SecurityServerHandler.sha1(password); // 复用服务器端哈希方法

            // 发送认证请求
            out.println("AUTH " + passwordHash);
            System.out.println("已发送认证请求（哈希值: " + passwordHash + ")");

            // 处理服务器响应
            serverMessage = in.readLine();
            switch (serverMessage) {
                case "AUTH_SUCCESS":
                    System.out.println("认证成功！");
                    break;
                case "REGISTER_REQUIRED":
                    System.out.println("用户尚未注册，请设置新密码:");
                    String newPassword = scanner.nextLine();
                    String newHash = SecurityServerHandler.sha1(newPassword);
                    out.println("REGISTER " + newHash);
                    System.out.println("注册成功！");
                    break;
                case "AUTH_FAILURE":
                    System.out.println("认证失败，请检查密码后重试。");
                    break;
                default:
                    System.out.println("未知响应: " + serverMessage);
            }
        } catch (IOException e) {
            System.err.println("Authentication communication error: " + e.getMessage());
        }
        finally {
            // 确保输入输出流关闭（Socket本身由外部管理）
            // 注意：我们只关闭 I/O 流，不关闭 Socket，由外部管理其生命周期
            /*
            try{ if (out != null) out.close();
                System.out.println("关闭输出");}
            catch (Exception e) {   }
            try { if (in != null) in.close();
                System.out.println("关闭输入");} catch (Exception e) {  }*/
        }
    }
}

