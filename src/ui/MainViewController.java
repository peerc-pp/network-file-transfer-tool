package ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class MainViewController {

    // --- 顶部连接区控件 ---
    @FXML
    private TextField ipField;
    @FXML
    private TextField portField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button connectButton;

    // --- 中间文件列表区控件 ---
    @FXML
    private TableView<?> localFileTable; // 泛型 <?> 稍后会用自定义类替换
    @FXML
    private TableView<?> remoteFileTable;
    @FXML
    private Button refreshButton;

    // --- 右侧操作按钮 ---
    @FXML
    private Button uploadButton;
    @FXML
    private Button downloadButton;

    // --- 底部状态区控件 ---
    @FXML
    private TextArea logArea;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;

    /**
     * 当 FXML 文件加载完成后，JavaFX 会自动调用这个方法。
     * 适合进行一些初始化操作。
     */
    @FXML
    public void initialize() {
        log("欢迎使用文件传输工具！请输入服务器信息并连接。");
    }

    // --- 事件处理方法 ---

    @FXML
    private void handleConnectButton() {
        String ip = ipField.getText();
        log("尝试连接到服务器: " + ip + " (逻辑未实现)");
    }

    @FXML
    private void handleRefreshButton() {
        log("请求刷新服务器文件列表... (逻辑未实现)");
    }

    @FXML
    private void handleUploadButton() {
        log("上传按钮被点击... (逻辑未实现)");
    }

    @FXML
    private void handleDownloadButton() {
        log("下载按钮被点击... (逻辑未实现)");
    }

    /**
     * 向日志区域追加一条消息的辅助方法
     * @param message 要记录的消息
     */
    private void log(String message) {
        // 使用 Platform.runLater 确保在任何线程中都能安全地更新UI
        javafx.application.Platform.runLater(() -> logArea.appendText(message + "\n"));
    }
}