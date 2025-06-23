# network-file-transfer-tool
# 测试用户名：123 密码：123
GUI配置
1.安装sdk17(选择17是因为我的Scene Builder版本)
解压压缩包，复制里面lib文件夹的文件地址
然后再ide中配置
![alt text](image-1.png)
![alt text](image-3.png)
![alt text](image-2.png)
选择lib文件夹后一路点确定
![alt text](image-4.png)
2.配置参数
先找到源码中的MainApplication
![alt text](image-5.png)
点一下这个绿色的三角
![alt text](image-6.png)
当上面这里显示这个类名的时候，选择配置
![alt text](image-7.png)
![alt text](image-8.png)
按图配置
![alt text](image-9.png)
![alt text](image-10.png)
输入你自己的lib路径，点确定
--module-path "\your\path\to\javafx-sdk-21.0.2\lib" --add-modules javafx.controls,javafx.fxml
![alt text](image-11.png)

Scene Builder的配置（如果有修改ui的需求）
一路安装后记住路径
如图选自己的安装路径就好了
![alt text](image-12.png)
启动
![alt text](image-13.png)
进入界面就可以设计ui且自动生成前端代码，但是监听什么的还是要自己写，不会的上网查
安装包都发群里