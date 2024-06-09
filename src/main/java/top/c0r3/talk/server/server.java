package top.c0r3.talk.server;

import top.c0r3.talk.encrypt.ZzSecurityHelper;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

public class server {
    public static ServerInfo SelfServerInfo = new ServerInfo();

    private volatile static ServerInfo ConnectServerInfo = new ServerInfo();

    public static ServerInfo getConnectServerInfo() {
        return ConnectServerInfo;
    }

    public void Init(){

        try{
            ServerSocket ss = new ServerSocket(SelfServerInfo.ServerPort);
            System.out.println("Server is running on "+InetAddress.getLocalHost()+":"+SelfServerInfo.ServerPort);
            SelfServerInfo.ServerIP = InetAddress.getLocalHost().toString().substring(
                    InetAddress.getLocalHost().toString().indexOf("/")+1
            );
            Socket s = ss.accept();

            //获取客户端信息
            ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
            ConnectServerInfo = (ServerInfo) ois.readObject();
            if(!Objects.equals(ConnectServerInfo.ServerIP, "0")){
                System.out.println("Connecting with "+ConnectServerInfo.ServerName+
                        " ,input /reject to reject the connection");
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));

            // 创建一个线程读取客户端消息并打印到控制台
            new Thread(() -> {
                try {
                    String message;

                    while ((message = in.readLine()) != null) {
                        message = ZzSecurityHelper.decryptAES(message);
                        if (message != null && message.endsWith("\n")) {
                            message = message.substring(0, message.length() - 1);
                        }
                        System.out.println("\u001B[32m");  //设置背景色为绿色
                        System.out.println(ConnectServerInfo.ServerName + " : " + message);
                        System.out.println("\u001B[0m");  //重置背景色
                    }
                } catch (IOException e) {
                    System.out.println("连接已断开");
                    System.exit(0);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}

