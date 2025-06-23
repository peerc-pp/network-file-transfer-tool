package ui; // 确保包名正确

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 加载我们刚刚创建的 FXML 文件
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("MainView.fxml"));

        // 创建场景，并设置窗口的初始大小
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);

        // 设置窗口标题
        stage.setTitle("网络文件传输工具");
        stage.setScene(scene);

        // 显示窗口
        stage.show();
    }

    public static void main(String[] args) {
        // 启动JavaFX应用
        launch(args);
    }
}