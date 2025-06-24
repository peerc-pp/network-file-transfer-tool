package ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import client.FileTransferService;

import javax.swing.*;


public class MainViewController {

    // --- FXML 控件 ---
    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button connectButton;
    @FXML private Button refreshButton;
    @FXML private Button uploadButton;
    @FXML private Button downloadButton;
    @FXML private Button selectDirectoryButton;
    @FXML private TableView<UIFile> localFileTable;
    @FXML private TableColumn<UIFile, String> localFileNameColumn;
    @FXML private TableColumn<UIFile, String> localFileSizeColumn;
    @FXML private TableColumn<UIFile, String> localFileDateColumn;
    @FXML private TableView<UIFile> remoteFileTable;
    @FXML private TableColumn<UIFile, String> remoteFileNameColumn;
    @FXML private TableColumn<UIFile, String> remoteFileSizeColumn;
    @FXML private TableColumn<UIFile, String> remoteFileDateColumn;
    @FXML private TextArea logArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private TextField localPathField; // 新增：本地路径显示框
    @FXML private Button upButton; // 新增：向上按钮

    private final FileTransferService transferService = new FileTransferService();
    private boolean isConnected = false;
    private Stage stage;
    private File currentLocalDirectory; // 新增：用于跟踪当前本地目录

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        // 初始化本地和远程文件表格的列
        localFileNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        localFileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        localFileDateColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        remoteFileNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        remoteFileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        remoteFileDateColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        localFileTable.setRowFactory(tv -> {
            TableRow<UIFile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    UIFile rowData = row.getItem();
                    File file = rowData.getOriginalFile();
                    if (file.isDirectory()) {
                        loadLocalFiles(file); // 双击目录，进入该目录
                    }
                }
            });
            return row;
        });

        // 初始时禁用部分按钮
        setButtonsDisabled(true);
        loadLocalFiles(new File(System.getProperty("user.dir"))); // 加载当前目录文件

        // 加载用户的主目录作为初始目录
        currentLocalDirectory = new File(System.getProperty("user.home"));
        loadLocalFiles(currentLocalDirectory);
    }

    @FXML
    private void handleConnectButton() {
        if (isConnected) {
            disconnectFromServer();
        } else {
            connectToServer();
        }
    }

    @FXML
    private void handleRefreshButton() {
        log("正在获取服务器文件列表...");
        Task<List<UIFile>> task = new Task<>() { // <-- 确认这里的泛型是 <List<UIFile>>
            @Override
            protected List<UIFile> call() throws Exception {
                return transferService.getRemoteFileList(ipField.getText());
            }
        };

        task.setOnSucceeded(e -> {
            List<UIFile> remoteFiles = task.getValue(); // <-- remoteFiles 现在是 List<UIFile> 类型
            remoteFileTable.setItems(FXCollections.observableArrayList(remoteFiles));
            log("服务器文件列表已刷新。");
        });
        task.setOnFailed(e -> logError("刷新失败", task.getException()));
        new Thread(task).start();
    }

    // 新增：处理“向上”按钮点击事件
    @FXML
    private void handleUpButton() {
        File parentDir = currentLocalDirectory.getParentFile();
        if (parentDir != null) {
            loadLocalFiles(parentDir);
        }
    }


    @FXML
    private void handleUploadButton() {
        UIFile selectedUiFile = localFileTable.getSelectionModel().getSelectedItem();
        if (selectedUiFile == null || selectedUiFile.getOriginalFile() == null) {
            showAlert("错误", "请先在左侧选择一个要上传的本地文件。");
            return;
        }
        File selectedFile = selectedUiFile.getOriginalFile();

        log("准备上传文件: " + selectedFile.getName());
        setButtonsDisabled(true);

        Task<Void> uploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // 1. 服务层准备上传，获取流
                DataOutputStream dos = transferService.prepareUpload(selectedFile);

                // 2. 在Task内部执行文件读写循环，并更新进度
                try (FileInputStream fis = new FileInputStream(selectedFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalSent = 0;
                    long fileSize = selectedFile.length();

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                        totalSent += bytesRead;
                        // 在这里调用 updateProgress 是完全合法的！
                        updateProgress(totalSent, fileSize);
                    }
                    dos.flush();
                }

                // 3. 服务层完成上传（接收校验和）
                transferService.finishUpload(selectedFile);

                return null;
            }
        };



        // --- 绑定和事件处理部分保持不变 ---
        progressBar.progressProperty().bind(uploadTask.progressProperty());
        progressLabel.textProperty().bind(
                uploadTask.progressProperty().multiply(100).asString("%.0f%%")
        );

        uploadTask.setOnSucceeded(e -> {
            log("文件上传成功: " + selectedFile.getName());
            setButtonsDisabled(false);
            handleRefreshButton();
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            updateProgress(0);
        });

        uploadTask.setOnFailed(e -> {
            logError("文件上传失败", uploadTask.getException());
            setButtonsDisabled(false);
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            updateProgress(0);
        });

        new Thread(uploadTask).start();
    }



    @FXML
    private void handleDownloadButton() {
        UIFile selectedUiFile = remoteFileTable.getSelectionModel().getSelectedItem();
        if (selectedUiFile == null) {
            showAlert("错误", "请先在右侧选择一个要下载的服务器文件。");
            return;
        }
        String remoteFileName = selectedUiFile.getName();

        File destinationDirectory = chooseDirectory();
        if (destinationDirectory == null) {
            log("用户取消了保存位置的选择。");
            return; // 如果用户点击了取消，则中止整个下载流程
        }

        log("准备下载文件: " + remoteFileName);
        setButtonsDisabled(true);

        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // 1. 调用服务层准备下载，获取文件大小
                long fileSize = transferService.prepareDownload(remoteFileName);
                if (fileSize == -1L) {
                    throw new FileNotFoundException("服务器上未找到文件: " + remoteFileName);
                }

                File downloadsDir = new File("downloads");
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                File outputFile = new File(destinationDirectory, remoteFileName);

                // 2. 在Task内部执行文件接收循环，并更新进度
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    DataInputStream dis = transferService.getInputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    while (totalRead < fileSize && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        // 在这里调用 updateProgress 是完全合法的！
                        updateProgress(totalRead, fileSize);
                    }
                }

                // 3. 调用服务层完成下载（校验和验证）
                transferService.finishDownload(outputFile);

                return null;
            }
        };

        // --- 绑定和事件处理部分与上传逻辑相同 ---
        progressBar.progressProperty().bind(downloadTask.progressProperty());
        progressLabel.textProperty().bind(
                downloadTask.progressProperty().multiply(100).asString("%.0f%%")
        );

        downloadTask.setOnSucceeded(e -> {
            log("文件下载成功: " + remoteFileName);
            setButtonsDisabled(false);
            loadLocalFiles(currentLocalDirectory);
            unbindProgress();
        });

        downloadTask.setOnFailed(e -> {
            logError("文件下载失败", downloadTask.getException());
            setButtonsDisabled(false);
            unbindProgress();
        });

        new Thread(downloadTask).start();
    }

    // --- 辅助逻辑 ---

    private void connectToServer() {
        String ip = ipField.getText();
        int port = Integer.parseInt(portField.getText());
        String user = usernameField.getText();
        String pass = passwordField.getText();

        log("正在连接到 " + ip + ":" + port + "...");
        Task<Void> connectTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                transferService.connectAndAuthenticate(ip, port, user, pass);
                return null;
            }
        };

        connectTask.setOnSucceeded(e -> {
            isConnected = true;
            connectButton.setText("断开");

            log("成功连接到服务器！");
            setButtonsDisabled(false);
            handleRefreshButton();
        });

        connectTask.setOnFailed(e -> logError("连接失败", connectTask.getException()));

        new Thread(connectTask).start();
    }

    private void disconnectFromServer() {
        try {
            transferService.disconnect();
            log("已断开连接。");
            isConnected = false;
            connectButton.setText("连接");
            setButtonsDisabled(true);
            remoteFileTable.getItems().clear();
        } catch (Exception e) {
            logError("断开连接时出错", e);
        }
    }

    private void loadLocalFiles(File directory) {
        currentLocalDirectory = directory; // 更新当前目录
        localPathField.setText(directory.getAbsolutePath()); // 更新路径显示框

        File[] files = directory.listFiles();
        if (files != null) {
            ObservableList<UIFile> uiFiles = FXCollections.observableArrayList();
            // 先添加目录，再添加文件
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (File file : files) {
                uiFiles.add(new UIFile(file));
            }
            localFileTable.setItems(uiFiles);
        }
    }

    /**
     * 打开一个图形化的文件选择器，让用户选择一个文件夹。
     * @return 用户选择的文件夹对象，如果用户取消了选择，则返回 null。
     */
    private File chooseDirectory() {
        // 1. 创建一个 JavaFX 的目录选择器
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("请选择一个文件夹");

        // 2. 设置初始显示的目录（可选，这里设置为用户的主目录）
        File initialDirectory = new File(System.getProperty("user.home"));
        if (initialDirectory.exists()) {
            directoryChooser.setInitialDirectory(initialDirectory);
        }

        // 3. 显示对话框，并将主窗口(stage)作为父窗口
        //    这能确保对话框显示在主窗口之上
        File selectedDirectory = directoryChooser.showDialog(stage);

        // 4. 处理用户的选择
        if (selectedDirectory != null) {
            log("您选择了文件夹: " + selectedDirectory.getAbsolutePath());
            return selectedDirectory;
        } else {
            log("您取消了文件夹选择。");
            return null;
        }
    }
    @FXML
    private void handleSelectDirectoryButton() {
        File selectedDirectory = chooseDirectory();
        if (selectedDirectory != null) {
            // 当用户成功选择一个文件夹后，调用 loadLocalFiles 方法来更新左侧的表格
            loadLocalFiles(selectedDirectory);
        }
    }

    private void setButtonsDisabled(boolean disabled) {
        refreshButton.setDisable(disabled);
        uploadButton.setDisable(disabled);
        downloadButton.setDisable(disabled);
    }

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private void logError(String prefix, Throwable e) {
        log(prefix + ": " + e.getMessage());
        e.printStackTrace();
    }

    private void unbindProgress() {
        // 解除 progressBar 的 progressProperty 绑定
        progressBar.progressProperty().unbind();

        // 解除 progressLabel 的 textProperty 绑定
        progressLabel.textProperty().unbind();

        // 调用我们已有的 updateProgress 方法，将进度条和百分比都重置为 0
        updateProgress(0);
    }

    private void updateProgress(double progress) {
        progressBar.setProgress(progress);
        progressLabel.setText(String.format("%.0f%%", progress * 100));
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}