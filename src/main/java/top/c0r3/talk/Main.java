package top.c0r3.talk;

import top.c0r3.talk.client.client;
import top.c0r3.talk.encrypt.ZzSecurityHelper;
import top.c0r3.talk.server.ServerInfo;
import top.c0r3.talk.server.server;

import java.util.Random;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {

          Scanner sc = new Scanner(System.in);
          ServerInfo serverInfo = new ServerInfo();

          System.out.println("Welcome to Core's Talk!");
          System.out.print("Input Your Port:");
          serverInfo.ServerPort = sc.nextInt();
          System.out.print("Input Your NickName:");
          serverInfo.ServerName = sc.next();
          System.out.print("Input Your Encryption Key:");
          String seed = sc.next();
          Random rand = new Random(seed.hashCode());
          String key = "";
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

          server.SelfServerInfo = serverInfo;

          server server = new server();
          new Thread(server::Init).start();

          Thread.sleep(500);

          client client = new client();
          new Thread(client::Init).start();
    }
}