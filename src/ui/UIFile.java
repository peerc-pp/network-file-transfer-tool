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
    private final File originalFile; // 仅用于本地文件，引用原始File对象
    private final String fileType;

    // 用于本地文件的构造函数
    public UIFile(File file) {
        this.originalFile = file;
        this.name = new SimpleStringProperty(file.getName());
        if (file.isDirectory()) {
            this.size = new SimpleStringProperty("<DIR>"); // 为文件夹显示 <DIR>
            this.fileType="dir";
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
        this.originalFile = null; // 服务器文件在本地没有对应的File对象
        this.name = new SimpleStringProperty(name);
        this.size = new SimpleStringProperty(formatSize(sizeInBytes)); // 使用格式化方法
        this.lastModified = new SimpleStringProperty(
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastModifiedTimestamp))
        );
        this.fileType = getFileExtension(name);
    }

    public UIFile(String displayName, File linkedFile) {
        this.originalFile = linkedFile; // 链接到真正的上一级目录
        this.name = new SimpleStringProperty(displayName); // 显示名称为 ".."
        this.size = new SimpleStringProperty("<UP>");
        this.lastModified = new SimpleStringProperty("");this.fileType="dir";
    }

    // JavaFX TableView 所需的 Property 方法
    public StringProperty nameProperty() { return name; }
    public StringProperty sizeProperty() { return size; }
    public StringProperty lastModifiedProperty() { return lastModified; }
    public String getName() { return name.get(); }
    public File getOriginalFile() { return originalFile; }
    public String getFileType() {
        return fileType;
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "file"; // 如果没有扩展名，返回通用文件类型
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}