package top.c0r3.talk.client;

import top.c0r3.talk.encrypt.KeyGenerator;
import top.c0r3.talk.encrypt.ZzSecurityHelper;
import top.c0r3.talk.encrypt.generateEncryptKey;
import top.c0r3.talk.server.ServerInfo;
import top.c0r3.talk.server.server;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class client extends Thread{
    private volatile int RunningMode = 0;
    private static ServerInfo ConnectTo = new ServerInfo();
    public void Init() {
        while(RunningMode == 0){
            Scanner sc = new Scanner(System.in);
            KeyGenerator kg = new generateEncryptKey();

            String cmd = sc.nextLine();
            Command.Process(cmd);

            if(server.getConnectServerInfo().ServerPort != 0){
                RunningMode = 1;
                ConnectTo = server.getConnectServerInfo();
            }
            if(server.getConnectServerInfo().ServerPort != 0){
                RunningMode = 1;
            }

            if(cmd.equals("/connect")){

                System.out.println("请输入另一个人的IP:");
                ConnectTo.ServerIP = sc.next();
                System.out.println("请输入另一个人的端口:");
                ConnectTo.ServerPort = sc.nextInt();
                System.out.print("通讯的加密密钥是:");
                String seed = sc.next();

                ZzSecurityHelper.setKey(kg.generateKey(seed));
                RunningMode = 1;
            }
        }
        try{
            Socket s = new Socket(ConnectTo.ServerIP, ConnectTo.ServerPort);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));
            //发送自己的服务端信息
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
            oos.writeObject(server.SelfServerInfo);

            System.out.println("Connected!");
            //发送自己的信息
            new Thread(()->{
                while(true){
                    try{
                        String input;
                        while ((input = consoleInput.readLine()) != null) {
                            Command.Process(input);
                            if(!input.startsWith("/")){
                                input += "\n";
                                input = ZzSecurityHelper.encryptAES(input);
                                out.println(input);
                                out.flush();
                            }
                        }
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                }
            }).start();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
