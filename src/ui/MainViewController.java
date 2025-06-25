package ui;

import common.FileIntegrityChecker;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.image.ImageView;
import javafx.geometry.Pos;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import client.FileTransferService;

import javax.swing.*;

import javafx.scene.control.SelectionMode;
import java.util.stream.Collectors;


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
        localFileNameColumn.setCellFactory(column -> new FileIconCell()); // 应用自定义单元格
        localFileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        localFileDateColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        remoteFileNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        remoteFileNameColumn.setCellFactory(column -> new FileIconCell()); // 应用自定义单元格
        remoteFileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        remoteFileDateColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        //qjr_add
        localFileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        remoteFileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        localFileTable.setRowFactory(tv -> {
            TableRow<UIFile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    UIFile rowData = row.getItem();
                    File file = rowData.getOriginalFile();
                    if (file != null && file.isDirectory()) {
                        loadLocalFiles(file); // 双击目录，进入该目录
                    }
                }
            });
            return row;
        });

        // 在 initialize() 方法中添加选择监听器：
        localFileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            int count = localFileTable.getSelectionModel().getSelectedItems().size();
            if (count > 1) {
                uploadButton.setText("上传 (" + count + ") ->");
            } else {
                uploadButton.setText("上传 ->");
            }
        });

        remoteFileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            int count = remoteFileTable.getSelectionModel().getSelectedItems().size();
            if (count > 1) {
                downloadButton.setText("<- 下载 (" + count + ")");
            } else {
                downloadButton.setText("<- 下载");
            }
        });

        // 【新功能】为本地文件表格设置双击和右键菜单
        setupLocalFileTableInteractions();

        // 【新功能】为远程文件表格设置右键菜单
        setupRemoteFileTableInteractions();

        // 初始时禁用部分按钮
        setButtonsDisabled(true);

        // 加载用户的主目录作为初始目录
        currentLocalDirectory = new File(System.getProperty("user.home"));
        loadLocalFiles(currentLocalDirectory);
    }

    // ================== 【新功能实现】双击导航 ==================
    private void setupLocalFileTableInteractions() {
        // 为本地文件列表创建右键菜单
        ContextMenu localContextMenu = new ContextMenu();
        MenuItem uploadItem = new MenuItem("upload");
        uploadItem.setOnAction(e -> handleUploadButton());
        localContextMenu.getItems().add(uploadItem);
        localFileTable.setContextMenu(localContextMenu);
    }

    // ================== 【新功能实现】右键菜单 ==================
    private void setupRemoteFileTableInteractions() {
        ContextMenu remoteContextMenu = new ContextMenu();
        MenuItem downloadItem = new MenuItem("download");
        MenuItem deleteRemoteItem = new MenuItem("delete (待实现)");

        downloadItem.setOnAction(e -> handleDownloadButton());

        remoteContextMenu.getItems().addAll(downloadItem, deleteRemoteItem);
        remoteFileTable.setContextMenu(remoteContextMenu);

        // 在显示菜单前，根据是否选中文件来决定是否禁用菜单项
        remoteContextMenu.setOnShowing(e -> {
            boolean isItemSelected = remoteFileTable.getSelectionModel().getSelectedItem() != null;
            downloadItem.setDisable(!isItemSelected);
            deleteRemoteItem.setDisable(!isItemSelected);
        });
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

    // 新增：处理"向上"按钮点击事件
    @FXML
    private void handleUpButton() {
        File parentDir = currentLocalDirectory.getParentFile();
        if (parentDir != null) {
            loadLocalFiles(parentDir);
        }
    }

    @FXML
    private void handleUploadButton() {
        ObservableList<UIFile> selectedFiles = localFileTable.getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) {
            showAlert("错误", "请先在左侧选择要上传的文件。");
            return;
        }

        // 过滤出实际的文件（排除目录和".."项）
        List<File> filesToUpload = selectedFiles.stream()
                .map(UIFile::getOriginalFile)
                .filter(file -> file != null && file.isFile())
                .collect(Collectors.toList());

        if (filesToUpload.isEmpty()) {
            showAlert("错误", "请选择有效的文件（不是文件夹）。");
            return;
        }

        // 如果只有一个文件，使用原有的单文件上传逻辑
        if (filesToUpload.size() == 1) {
            // 调用原有的单文件上传逻辑
            uploadSingleFile(new UIFile(filesToUpload.get(0)));
            return;
        }

        // 批量上传
        log("准备批量上传 " + filesToUpload.size() + " 个文件");
        setButtonsDisabled(true);

        Task<Void> batchUploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                transferService.batchUpload(filesToUpload, (currentIndex, totalFiles, fileProgress) -> {
                    // 计算总体进度
                    double overallProgress = (currentIndex + fileProgress) / totalFiles;
                    updateProgress(overallProgress, 1.0);

                    // 更新消息
                    String currentFileName = filesToUpload.get(currentIndex).getName();
                    updateMessage(String.format("上传 %d/%d: %s (%.0f%%)",
                            currentIndex + 1, totalFiles, currentFileName, fileProgress * 100));
                });
                return null;
            }
        };

        // 绑定进度
        progressBar.progressProperty().bind(batchUploadTask.progressProperty());
        progressLabel.textProperty().bind(batchUploadTask.messageProperty());

        batchUploadTask.setOnSucceeded(e -> {
            log("批量上传完成！");
            setButtonsDisabled(false);
            handleRefreshButton();
            unbindProgress();
        });

        batchUploadTask.setOnFailed(e -> {
            logError("批量上传失败", batchUploadTask.getException());
            setButtonsDisabled(false);
            unbindProgress();
        });

        new Thread(batchUploadTask).start();
    }

    // 将原有的单文件上传逻辑抽取为独立方法
    private void uploadSingleFile(UIFile selectedUiFile) {
        File selectedFile = selectedUiFile.getOriginalFile();
        if (selectedFile == null) {
            showAlert("错误", "选择的文件无效。");
            return;
        }

        log("准备上传文件: " + selectedFile.getName());
        setButtonsDisabled(true);

        Task<Void> uploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long existing = transferService.queryUploadedBytes(selectedFile.getName());
                DataOutputStream dos = transferService.prepareUploadResume(selectedFile, existing);
                try (RandomAccessFile raf = new RandomAccessFile(selectedFile, "r")) {
                    raf.seek(existing);
                    byte[] buf = new byte[8192];
                    long sent = existing, total = selectedFile.length();
                    int n;
                    while ((n = raf.read(buf)) != -1) {
                        dos.write(buf, 0, n);
                        sent += n;
                        updateProgress(sent, total);
                    }
                    dos.flush();

                    long serverCrc = transferService.getInputStream().readLong();


                    long localCrc = FileIntegrityChecker.calculateCRC32(selectedFile);
                    if (serverCrc != localCrc) throw new IOException("校验失败");
                }
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
            reconnectAndResume(() -> handleUploadButton());
            updateProgress(0);
        });

        new Thread(uploadTask).start();
    }

    @FXML
    private void handleDownloadButton() {
        ObservableList<UIFile> selectedFiles = remoteFileTable.getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) {
            showAlert("错误", "请先在右侧选择要下载的文件。");
            return;
        }

        List<String> fileNames = selectedFiles.stream()
                .map(UIFile::getName)
                .collect(Collectors.toList());

        File destinationDirectory = chooseDirectory();
        if (destinationDirectory == null) {
            log("用户取消了保存位置的选择。");
            return;
        }

        // 如果只有一个文件，使用原有的单文件下载逻辑
        if (fileNames.size() == 1) {
            downloadSingleFile(selectedFiles.get(0), destinationDirectory);
            return;
        }

        // 批量下载
        log("准备批量下载 " + fileNames.size() + " 个文件");
        setButtonsDisabled(true);

        Task<Void> batchDownloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                transferService.batchDownload(fileNames, destinationDirectory,
                        (currentIndex, totalFiles, fileProgress) -> {
                            double overallProgress = (currentIndex + fileProgress) / totalFiles;
                            updateProgress(overallProgress, 1.0);

                            String currentFileName = fileNames.get(currentIndex);
                            updateMessage(String.format("下载 %d/%d: %s (%.0f%%)",
                                    currentIndex + 1, totalFiles, currentFileName, fileProgress * 100));
                        });
                return null;
            }
        };

        progressBar.progressProperty().bind(batchDownloadTask.progressProperty());
        progressLabel.textProperty().bind(batchDownloadTask.messageProperty());

        batchDownloadTask.setOnSucceeded(e -> {
            log("批量下载完成！");
            setButtonsDisabled(false);
            loadLocalFiles(currentLocalDirectory);
            unbindProgress();
        });

        batchDownloadTask.setOnFailed(e -> {
            logError("批量下载失败", batchDownloadTask.getException());
            setButtonsDisabled(false);
            unbindProgress();
        });

        new Thread(batchDownloadTask).start();
    }

    // 将原有的单文件下载逻辑抽取为独立方法
    private void downloadSingleFile(UIFile selectedUiFile, File destinationDirectory) {
        String remoteFileName = selectedUiFile.getName();

        log("准备下载文件: " + remoteFileName);
        setButtonsDisabled(true);

        Task<Void> downloadTask = new Task<>() {
            @Override public Void call() throws Exception {
                File out = new File(destinationDirectory, remoteFileName);
                long have = out.exists() ? out.length() : 0;
                long total = transferService.prepareDownloadResume(remoteFileName, have);
                if (total == -1L) throw new FileNotFoundException();

                try (RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
                    raf.seek(have);
                    DataInputStream dis = transferService.getInputStream();
                    byte[] buf = new byte[8192];
                    long read = have;
                    int n;
                    while (read < total && (n = dis.read(buf)) != -1) {
                        raf.write(buf, 0, n);
                        read += n;
                        updateProgress(read, total);
                    }

                    long serverCrc = dis.readLong();
                    if (serverCrc != FileIntegrityChecker.calculateCRC32(out)) {
                        throw new IOException("下载校验失败");
                    }
                }
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
            reconnectAndResume(() -> handleDownloadButton());

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
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        currentLocalDirectory = directory; // 更新当前目录
        localPathField.setText(directory.getAbsolutePath()); // 更新路径显示框

        File[] filesInDir = directory.listFiles();
        ObservableList<UIFile> uiFiles = FXCollections.observableArrayList();

        // 1. 【新增】检查是否存在父目录，如果存在，则添加 ".." 条目
        File parentDir = directory.getParentFile();
        if (parentDir != null) {
            uiFiles.add(new UIFile("..", parentDir)); // 使用新构造函数创建返回项
        }

        if (filesInDir != null) {
            // 2. 排序：文件夹在前，文件在后
            Arrays.sort(filesInDir, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            // 3. 将文件夹和文件都添加进去
            for (File file : filesInDir) {
                uiFiles.add(new UIFile(file));
            }
        }

        localFileTable.setItems(uiFiles);
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

    class FileIconCell extends TableCell<UIFile, String> {
        private final HBox graphicBox = new HBox(5); // 5是图标和文字的间距
        private final ImageView iconView = new ImageView();
        private final Label label = new Label();

        public FileIconCell() {
            // ================== 【核心修改点在这里】 ==================
            // 为 ImageView 设置固定的显示大小
            iconView.setFitHeight(18); // 设置图标的目标高度为18像素
            iconView.setFitWidth(18);  // 设置图标的目标宽度为18像素
            iconView.setPreserveRatio(true); // 缩放时保持图标的原始宽高比，防止变形
            // =======================================================

            graphicBox.setAlignment(Pos.CENTER_LEFT); // 确保图标和文字垂直居中
            graphicBox.getChildren().addAll(iconView, label);
        }

        @Override
        protected void updateItem(String itemName, boolean empty) {
            super.updateItem(itemName, empty);

            if (empty || getItem() == null) {
                setGraphic(null);
            } else {
                // 获取当前行对应的UIFile对象
                UIFile uiFile = getTableView().getItems().get(getIndex());

                // 从IconManager获取图标
                iconView.setImage(IconManager.getIcon(uiFile.getFileType()));

                // 设置文件名
                label.setText(itemName);

                // 将HBox（包含图标和文字）设置为此单元格的图形
                setGraphic(graphicBox);
            }
        }
    }
    private void reconnectAndResume(Runnable resumeAction) {
        log("网络中断，尝试重连...");
        try {
            // 使用之前保存的连接参数重新连接
            transferService.connectAndAuthenticate(ipField.getText(), Integer.parseInt(portField.getText()),
                    usernameField.getText(), passwordField.getText());
            log("重连成功，继续操作…");
            resumeAction.run();
        } catch (Exception ex) {
            logError("重连失败，操作中止", ex);
            setButtonsDisabled(false);
            unbindProgress();
        }
    }

}