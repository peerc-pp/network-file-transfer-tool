package ui;

import javafx.scene.image.Image;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IconManager {

    private static final Map<String, Image> iconCache = new HashMap<>();
    private static final Image GENERIC_FILE_ICON;
    private static final Image FOLDER_ICON;

    // 使用静态代码块，在类加载时就将图标读入内存
    static {
        FOLDER_ICON = loadImage("/icon/tabler_folder.png");
        GENERIC_FILE_ICON = loadImage("/icon/tabler_file.png");

        // 缓存特定文件类型的图标
        iconCache.put("dir", FOLDER_ICON);
        iconCache.put("file", GENERIC_FILE_ICON);
        iconCache.put("jpg", loadImage("/icon/material-symbols_image-outline.png"));
        iconCache.put("jpeg", loadImage("/icon/material-symbols_image-outline.png"));
        iconCache.put("png", loadImage("/icon/material-symbols_image-outline.png"));
        iconCache.put("gif", loadImage("/icon/material-symbols_image-outline.png"));
        iconCache.put("txt", loadImage("/icon/lsicon_file-txt-outline.png"));
        iconCache.put("pdf", loadImage("/icon/mingcute_pdf-line.png"));
        iconCache.put("doc", loadImage("/icon/lsicon_file-doc-outline.png"));

    }

    /**
     * 根据文件类型获取对应的图标
     * @param fileType 文件扩展名 (如 "txt", "jpg") 或特殊类型 ("dir" 表示文件夹)
     * @return 对应的 Image 对象
     */
    public static Image getIcon(String fileType) {
        // 从缓存中获取图标，如果某种类型没有特定图标，则返回通用文件图标
        return iconCache.getOrDefault(fileType.toLowerCase(), GENERIC_FILE_ICON);
    }

    private static Image loadImage(String path) {
        try {
            return new Image(Objects.requireNonNull(IconManager.class.getResourceAsStream(path)));
        } catch (Exception e) {
            System.err.println("无法加载图标: " + path);
            // 如果某个图标加载失败，返回一个null或者通用图标
            return GENERIC_FILE_ICON;
        }
    }
}