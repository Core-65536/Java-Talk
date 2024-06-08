package top.c0r3.talk;

import top.c0r3.talk.client.client;
import top.c0r3.talk.server.ServerInfo;
import top.c0r3.talk.server.server;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws InterruptedException {
          Scanner sc = new Scanner(System.in);
          ServerInfo serverInfo = new ServerInfo();

          System.out.println("Welcome to Core's Talk!");
          System.out.print("Input Your Port:");
          serverInfo.ServerPort = sc.nextInt();
          System.out.print("Input Your NickName:");
          serverInfo.ServerName = sc.next();

          server.SelfServerInfo = serverInfo;

          server server = new server();
          new Thread(server::Init).start();

          Thread.sleep(500);

          client client = new client();
          new Thread(client::Init).start();
    }
}