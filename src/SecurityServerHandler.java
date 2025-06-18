import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class SecurityServerHandler {
    private static final String CREDENTIALS_FILE = "user_credentials.txt";

    public static boolean handleAuthentication(Socket clientSocket) throws IOException {
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        Map<String, String> credentials = loadCredentials();

        try {
            out.println("AUTH_REQUEST");
            String response = in.readLine();

            if (response == null) {
                System.err.println("客户端连接已关闭，未收到任何认证响应。");
                return false;
            }

            if (response.startsWith("AUTH ")) {
                String clientHash = response.substring(5).trim();
                if (credentials.containsKey(clientIP)) {
                    String storedHash = credentials.get(clientIP).substring(9); // 去掉"REGISTER "
                    if (storedHash.equals(clientHash)) {
                        out.println("AUTH_SUCCESS");
                        return true;
                    } else {
                        out.println("AUTH_FAILURE");
                        return false;
                    }
                } else {
                    out.println("REGISTER_REQUIRED");
                    String newHashLine = in.readLine();
                    if (newHashLine == null) {
                        System.err.println("注册流程中断，客户端未响应。");
                        return false;
                    }
                    credentials.put(clientIP, newHashLine.trim());
                    saveCredentials(credentials);
                    out.println("REGISTER_SUCCESS");
                    return true;
                }
            } else if (response.startsWith("REGISTER ")) {
                String newHash = response.substring(9).trim();
                credentials.put(clientIP, newHash);
                saveCredentials(credentials);
                out.println("REGISTER_SUCCESS");
                return true;
            } else {
                out.println("UNKNOWN_COMMAND");
                return false;
            }

        } catch (IOException e) {
            System.err.println("认证通信错误: " + e.getMessage());
            e.printStackTrace();  // 打印完整异常堆栈
            throw e;
        }
    }


    private static Map<String, String> loadCredentials() throws IOException {
        Map<String, String> credentials = new HashMap<>();
        File file = new File(CREDENTIALS_FILE);
        if (!file.exists()) return credentials;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    credentials.put(parts[0], parts[1].trim()); // 去除值中的空白字符
                }
            }
        }
        return credentials;
    }

    private static void saveCredentials(Map<String, String> credentials) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CREDENTIALS_FILE))) {
            for (Map.Entry<String, String> entry : credentials.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue().trim()); // 保存时去除值的末尾空白
            }
        }
    }

    // 辅助方法：计算SHA-1哈希（保持与客户端一致）
    public static String sha1(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}