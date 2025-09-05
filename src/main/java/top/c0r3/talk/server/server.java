package top.c0r3.talk.server;

import top.c0r3.talk.encrypt.ZzSecurityHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

public class server{
    public static ServerInfo SelfServerInfo = new ServerInfo();

    private volatile static ServerInfo ConnectServerInfo = new ServerInfo();

    public static ServerInfo getConnectServerInfo() {
        return ConnectServerInfo;
    }

    public void Init(){

        try{
            @SuppressWarnings("resource")
            ServerSocket ss = new ServerSocket(SelfServerInfo.ServerPort);
            System.out.println("Server is running on "+InetAddress.getLocalHost()+":"+SelfServerInfo.ServerPort);
            SelfServerInfo.ServerIP = InetAddress.getLocalHost().toString().substring(
                    InetAddress.getLocalHost().toString().indexOf("/")+1
            );
            SelfServerInfo.ServerIP = "60.16.218.40";
            Socket s = ss.accept();

            //获取客户端信息
            ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
            ConnectServerInfo = (ServerInfo) ois.readObject();
            if(!Objects.equals(ConnectServerInfo.ServerIP, "0")){
                System.out.println("Connecting with "+ConnectServerInfo.ServerName+
                        " ,input /reject to reject the connection," +
                        "input /accept to accept the connection");
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            // 创建一个线程读取客户端消息并打印到控制台
            new Thread(() -> {
                try {
                    String message;

                    while ((message = in.readLine()) != null) {
                        message = ZzSecurityHelper.decryptAES(message);
                        if (message != null && message.endsWith("\n")) {
                            message = message.substring(0, message.length() - 1);
                        }
                        System.out.print("\u001B[32m");  //设置背景色为绿色
                        System.out.println(ConnectServerInfo.ServerName + " : " + message);
                        System.out.print("\u001B[0m");  //重置背景色
                    }
                } catch (IOException e) {
                    System.out.println("连接已断开");
                    System.exit(0);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }catch(java.net.BindException e){
            System.out.println("端口被占用,程序即将退出");
            System.exit(0);
        }catch(IOException | ClassNotFoundException e){
            throw new RuntimeException(e);
        }catch(java.lang.IllegalArgumentException e){
            System.out.println("输入非法,程序即将退出");
            System.exit(0);
        }
    }
}

