package top.c0r3.talk;

import top.c0r3.talk.client.client;
import top.c0r3.talk.encrypt.KeyGenerator;
import top.c0r3.talk.encrypt.ZzSecurityHelper;
import top.c0r3.talk.encrypt.generateEncryptKey;
import top.c0r3.talk.server.ServerInfo;
import top.c0r3.talk.server.server;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
          Scanner sc = new Scanner(System.in);
          ServerInfo serverInfo = new ServerInfo();
          KeyGenerator kg = new generateEncryptKey();

          System.out.println("Welcome to Core's Talk!");
          System.out.print("Input Your Port:");
          serverInfo.ServerPort = sc.nextInt();
          System.out.print("Input Your NickName:");
          serverInfo.ServerName = sc.next();
          System.out.print("Input Your Encryption Key:");
          String seed = sc.next();

          ZzSecurityHelper.setKey(kg.generateKey(seed));
          server.SelfServerInfo = serverInfo;

          server server = new server();
          new Thread(server::Init).start();
          Thread.sleep(500);
          client client = new client();
          new Thread(client::Init).start();
    }
}