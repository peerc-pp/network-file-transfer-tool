package ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UIFile {
    private final StringProperty name;
    private final StringProperty size;
    private final StringProperty lastModified;
    private final File originalFile;
    private final String fileType;

    // 用于本地文件的构造函数
    public UIFile(File file) {
        this.originalFile = file;
        this.name = new SimpleStringProperty(file.getName());
        if (file.isDirectory()) {
            this.size = new SimpleStringProperty("<DIR>");
            this.fileType = "dir";
        } else {
            this.size = new SimpleStringProperty(formatSize(file.length()));
            this.fileType = getFileExtension(file.getName());
        }
        this.lastModified = new SimpleStringProperty(
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified()))
        );
    }

    // 用于服务器文件的构造函数
    public UIFile(String name, long sizeInBytes, long lastModifiedTimestamp) {
        this.originalFile = null;
        this.name = new SimpleStringProperty(name);
        this.size = new SimpleStringProperty(formatSize(sizeInBytes));
        this.lastModified = new SimpleStringProperty(
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastModifiedTimestamp))
        );
        this.fileType = getFileExtension(name);
    }

    // 用于".."返回项的构造函数
    public UIFile(String displayName, File linkedFile) {
        this.originalFile = linkedFile;
        this.name = new SimpleStringProperty(displayName);
        this.size = new SimpleStringProperty("<UP>");
        this.lastModified = new SimpleStringProperty("");
        this.fileType = "dir";
    }

    // JavaFX TableView 所需的 Property 方法
    public StringProperty nameProperty() { return name; }
    public StringProperty sizeProperty() { return size; }
    public StringProperty lastModifiedProperty() { return lastModified; }

    // Getter 方法
    public String getName() { return name.get(); }
    public String getSize() { return size.get(); }
    public String getLastModified() { return lastModified.get(); }
    public File getOriginalFile() { return originalFile; }

    // 获取文件类型（供IconManager使用）
    public String getFileType() {
        return fileType;
    }

    // 获取文件扩展名
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "file";
    }

    // 格式化文件大小
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}