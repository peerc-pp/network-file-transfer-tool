package ui; // 确保包名正确

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 加载 FXML 文件
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("MainView.fxml"));

        // 创建场景
        Scene scene = new Scene(fxmlLoader.load(), 900, 600);

        // 设置窗口标题并显示
        stage.setTitle("网络文件传输工具 v1.0");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        // 启动JavaFX应用
        launch(args);
    }
}