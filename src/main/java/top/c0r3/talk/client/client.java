package top.c0r3.talk.client;

import top.c0r3.talk.server.ServerInfo;
import top.c0r3.talk.server.server;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class client extends Thread{
    private volatile int RunningMode = 0;
    public void SetRunningMode(int mode){
        this.RunningMode = mode;
    }
    private static ServerInfo ConnectTo = new ServerInfo();
    public void SetConnectTo(ServerInfo serverInfo){
        ConnectTo = serverInfo;
    }
    public static ServerInfo GetConnectTo(){
        return ConnectTo;
    }
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
                RunningMode = 1;
            }
        }
        //System.out.println("Connecting to "+ConnectTo.ServerIP+":"+ConnectTo.ServerPort);
        try{
            Socket s = new Socket(ConnectTo.ServerIP, ConnectTo.ServerPort);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));
            //发送自己的服务端信息
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
            oos.writeObject(server.SelfServerInfo);
            //System.out.println(server.SelfServerInfo.ServerPort);

            System.out.println("Connected!");
            //发送自己的信息
            new Thread(()->{
                while(true){
                    try{
                        String input;
                        while ((input = consoleInput.readLine()) != null) {
                            Command.Process(input);
                            out.println(input);
                        }
                    }catch(IOException e){
                        throw new RuntimeException(e);
                    }
                }
            }).start();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
