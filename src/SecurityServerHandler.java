import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class SecurityServerHandler {
    private static final String CREDENTIALS_FILE = "user_credentials.txt";
    private static final int    MAX_ATTEMPTS     = 3;   // 允许的最大密码尝试次数

    /** 认证入口：与客户端完成用户名/密码-哈希握手 */
    public static boolean handleAuthentication(Socket clientSocket) throws IOException {
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        Map<String, String> credentials = loadCredentials();

        /* === 1. 发起认证请求 === */
        out.println("AUTH_REQUEST");

        /* === 2. 等待客户端发送用户名 === */
        String line = in.readLine();
        if (line == null || !line.startsWith("AUTH ")) {
            out.println("UNKNOWN_COMMAND");
            return false;
        }
        String username = line.substring(5).trim();

        /* === 3. 用户已存在 → 多次校验密码 === */
        if (credentials.containsKey(username)) {
            out.println("NAME_SUCCESS");

            int attempts = 0;
            while ((line = in.readLine()) != null && attempts < MAX_ATTEMPTS) {
                if (!line.startsWith("AUTH ")) {
                    out.println("UNKNOWN_COMMAND");
                    continue;
                }

                attempts++;
                String pwdHash = line.substring(5).trim();

                // 从存储行中提取注册哈希
                String storedLine  = credentials.get(username);      // 可能是 "REGISTER <hash>"
                int idx            = storedLine.indexOf("REGISTER ");
                String storedHash  = (idx != -1)
                        ? storedLine.substring(idx + "REGISTER ".length()).trim()
                        : "";

                if (storedHash.equals(pwdHash)) {
                    out.println("AUTH_SUCCESS");
                    System.out.printf("用户 %s 认证成功%n", username);
                    return true;
                } else {
                    out.println("AUTH_FAILURE");
                    System.out.printf("用户 %s 第 %d 次密码错误%n", username, attempts);
                    if (attempts >= MAX_ATTEMPTS) {
                        return false;  // 超过次数直接断开
                    }
                }
            }
            return false; // 循环意外结束
        }

        /* === 4. 用户不存在 → 注册流程 === */
        out.println("REGISTER_REQUIRED");

        while ((line = in.readLine()) != null) {
            if (line.startsWith("REGISTER ")) {
                String newHash = line.substring(9).trim();
                credentials.put(username, "REGISTER " + newHash);
                saveCredentials(credentials);
                out.println("REGISTER_SUCCESS");
                System.out.printf("用户 %s 完成注册%n", username);
                return true;
            }
            // 若客户端又重复发 AUTH <username>，忽略继续等待
            if (!line.startsWith("AUTH ")) {
                out.println("UNKNOWN_COMMAND");
            }
        }

        System.err.println("注册流程中断：客户端断开");
        return false;
    }

    /* ============ 工具方法 ============ */
    private static Map<String, String> loadCredentials() throws IOException {
        Map<String, String> map = new HashMap<>();
        File file = new File(CREDENTIALS_FILE);
        if (!file.exists()) return map;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String l;
            while ((l = reader.readLine()) != null) {
                String[] parts = l.split(":", 2);
                if (parts.length == 2) map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }

    private static void saveCredentials(Map<String, String> map) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CREDENTIALS_FILE))) {
            for (var e : map.entrySet()) writer.println(e.getKey() + ":" + e.getValue());
        }
    }

    /** 计算 SHA-1（与客户端一致） */
    public static String sha1(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 unavailable", e);
        }
    }
}
