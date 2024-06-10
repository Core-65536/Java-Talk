package top.c0r3.talk.client;

import top.c0r3.talk.encrypt.KeyGenerator;
import top.c0r3.talk.encrypt.ZzSecurityHelper;
import top.c0r3.talk.encrypt.generateEncryptKey;
import top.c0r3.talk.server.ServerInfo;
import top.c0r3.talk.server.server;

import java.io.*;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

/*
* class.java : 客户端
* 作用 : 连接到服务器，发送消息
* @Author : Core_65536
* */
public class client{
    //运行模式
    //0 : 未连接 , 1 : 已连接
    private volatile int RunningMode = 0;
    //连接到的服务器信息
    private static ServerInfo ConnectTo = new ServerInfo();
    public void Init (){//初始化
        while(RunningMode == 0){
            //当未连接时为命令输入&等待连接模式
            Scanner sc = new Scanner(System.in);
            KeyGenerator kg = new generateEncryptKey();
            //获取是否连接到服务器
            new Thread(() ->{
                while(true){
                    if(server.getConnectServerInfo().ServerPort != 0){
                        RunningMode = 1;
                        ConnectTo = server.getConnectServerInfo();
                        break;
                    }
                }
            }).start();
            //命令输入
            String cmd = sc.nextLine();
            Command.Process(cmd);

            if(cmd.equals("/connect")){

                System.out.println("请输入另一个人的IP:");
                ConnectTo.ServerIP = sc.next();
                System.out.println("请输入另一个人的端口:");
                ConnectTo.ServerPort = sc.nextInt();
                System.out.print("通讯的加密密钥是:");
                String seed = sc.next();

                ZzSecurityHelper.setKey(kg.generateKey(seed));
                ZzSecurityHelper.setIv(kg.generateKey(seed+seed));
                RunningMode = 1;
            }
        }
        try{
            @SuppressWarnings("resource")
            Socket s = new Socket(ConnectTo.ServerIP, ConnectTo.ServerPort);
            //连接到对方
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));
            //发送自己的服务端信息
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
            oos.writeObject(server.SelfServerInfo);

            //发送自己的信息
            new Thread(()->{
                while(true){
                    try{
                        String input;
                        while ((input = consoleInput.readLine()) != null) {//读取控制台输入
                            Command.Process(input);//处理命令
                            if(!input.startsWith("/")){
                                input = ZzSecurityHelper.encryptAES(input);//加密
                                SendMsgToSQL(server.SelfServerInfo.ServerName, input);//发送到数据库
                                out.println(input);//发送到对方
                            }
                        }
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                }
            }).start();

        } catch (IOException e ) {
            throw new RuntimeException(e);
        }
    }
    private void SendMsgToSQL(String Name, String msg){
        String url = "jdbc:mysql://oj.c0r3.top:3306/java-talk";
        String usr = "Java-Talk";
        String pwd = System.getenv("DB_PASSWORD");

        String sql = "INSERT INTO `messages` (`nickname`,`timestamp`, `message`) VALUES (? , NOW() , ?)";
        try(Connection conn = DriverManager.getConnection(url, usr, pwd);
            PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setString(1, Name);
            stmt.setString(2, msg);
            stmt.executeUpdate();
        }catch (SQLException e){
            throw new RuntimeException(e);
        }
    }
}
