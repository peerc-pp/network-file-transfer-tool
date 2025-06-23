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

    // 用于本地文件的构造函数
    public UIFile(File file) {
        this.originalFile = file;
        this.name = new SimpleStringProperty(file.getName());
        this.size = new SimpleStringProperty(formatSize(file.length()));
        this.lastModified = new SimpleStringProperty(
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified()))
        );
    }

    // 用于服务器文件的构造函数
    public UIFile(String name) {
        this.originalFile = null;
        this.name = new SimpleStringProperty(name);
        this.size = new SimpleStringProperty("N/A");
        this.lastModified = new SimpleStringProperty("N/A");
    }

    // JavaFX TableView 所需的 Property 方法
    public StringProperty nameProperty() { return name; }
    public StringProperty sizeProperty() { return size; }
    public StringProperty lastModifiedProperty() { return lastModified; }
    public String getName() { return name.get(); }
    public File getOriginalFile() { return originalFile; }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}