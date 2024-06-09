package top.c0r3.talk.client;

import top.c0r3.talk.encrypt.ZzSecurityHelper;
import top.c0r3.talk.server.ServerInfo;
import top.c0r3.talk.server.server;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;

public class client extends Thread{
    private volatile int RunningMode = 0;
    private static ServerInfo ConnectTo = new ServerInfo();
    private String key = "";
    public void Init() {

        while(RunningMode == 0){
            Scanner sc = new Scanner(System.in);
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
                Random rand = new Random(seed.hashCode());
                for(int i = 0; i < 16; i++){
                    int mode = rand.nextInt(3);
                    switch (mode) {
                        case 0:
                            char c = (char) (rand.nextInt(26) + 65);
                            key += c;
                            break;
                        case 1:
                            char d = (char) (rand.nextInt(26) + 97);
                            key += d;
                            break;
                        case 2:
                            char e = (char) (rand.nextInt(10) + 48);
                            key += e;
                            break;
                    }
                }
                ZzSecurityHelper.setKey(key);
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
