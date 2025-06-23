package client;// 2. 客户端安全认证模块 (client.SecurityClientHandler.java)
import server.SecurityServerHandler;

import java.util.Scanner;
import java.io.*;

public class SecurityClientHandler {
    public static boolean handleAuthentication(DataInputStream dis, DataOutputStream dos) {
        int max_loginnum=3;//最多可以输入3次密码

        try (Scanner scanner = new Scanner(System.in)) {


            // 接收认证请求
            String serverMessage = dis.readUTF();
            if (!"AUTH_REQUEST".equals(serverMessage)) {
                System.err.println("预期认证请求，但收到: " + serverMessage);
                return false;
            }

            // 获取用户密码
//            System.out.print("请输入密码: ");
//            String password = scanner.nextLine();
//            String passwordHash = server.SecurityServerHandler.sha1(password);
            // 复用服务器端哈希方法
            System.out.print("请输入用户名: ");
            String username= scanner.nextLine();

            // 发送认证请求
            dos.writeUTF("AUTH " + username);
            System.out.println("已发送用户名 " + username );

            // 处理服务器响应
            serverMessage = dis.readUTF();
            if (serverMessage == null) {                     // 服务器断开
                System.out.println("连接已关闭，认证失败。");
                return false;
            }
            switch (serverMessage)
            {
                case "NAME_SUCCESS":
                    int  loginnum=0;
                    System.out.print("请输入密码: ");
                    String password = scanner.nextLine();
                    String passwordHash = SecurityServerHandler.sha1(password);
                    dos.writeUTF("AUTH " + passwordHash);
                    System.out.println("已发送密码哈希值 " + passwordHash );
                    loginnum++;
                    // 再次等待服务器最终结果
                    while(loginnum<max_loginnum)
                    {
                        if (loginnum ==max_loginnum)
                        {
                            System.out.println("连续 " + loginnum + " 次密码错误，已终止。");
                            return false;
                        }
                        serverMessage = dis.readUTF();
                        if (serverMessage == null) {                     // 服务器断开
                                System.out.println("连接已关闭，认证失败。");
                                return false;
                        }
                        if ("AUTH_SUCCESS".equals(serverMessage)) {
                            System.out.println("认证成功！");
                            return true;
                        } else if ("AUTH_FAILURE".equals(serverMessage)) {
                            System.out.println("认证失败，请检查密码后重试。");
                            System.out.print("请输入密码: ");
                            password = scanner.nextLine();
                            passwordHash = SecurityServerHandler.sha1(password);
                            dos.writeUTF("AUTH " + passwordHash);
                            System.out.println("已发送密码哈希值 " + passwordHash );
                            loginnum++;
                        } else {
                            System.out.println("未知响应: " + serverMessage);
                            return false;
                        }
                    }
                    return false;
                case "AUTH_SUCCESS":
                    System.out.println("认证成功！");
                    return true;
                case "REGISTER_REQUIRED":
                    dos.writeUTF("AUTH " + username);
                    System.out.println("已发送用户名 " + username );
                    System.out.println("用户尚未注册，请设置新密码:");
                    String newPassword = scanner.nextLine();
                    String newHash = SecurityServerHandler.sha1(newPassword);
                    dos.writeUTF("REGISTER " + newHash);
                    System.out.println("注册成功！");
                    return true;
                case "AUTH_FAILURE":
                    System.out.println("认证失败，请检查密码后重试。");
                    return false;
                default:
                    System.out.println("未知响应: " + serverMessage);
                    return false;
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
        return false;
    }
}

