package top.c0r3.talk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.c0r3.talk.client.GroupClient;
import top.c0r3.talk.client.client;
import top.c0r3.talk.config.AppConfig;
import top.c0r3.talk.encrypt.KeyGenerator;
import top.c0r3.talk.encrypt.ZzSecurityHelper;
import top.c0r3.talk.encrypt.generateEncryptKey;
import top.c0r3.talk.server.GroupServer;
import top.c0r3.talk.server.ServerInfo;
import top.c0r3.talk.server.server;

import java.net.URI;
import java.util.Scanner;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) throws Exception {
        // 强制设置系统编码为UTF-8
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        
        // 设置JVM默认字符集
        System.setProperty("user.language", "zh");
        System.setProperty("user.country", "CN");
        System.setProperty("user.timezone", "Asia/Shanghai");
        
        // 设置Logback编码
        System.setProperty("logback.configurationFile", "logback-spring.xml");
        
        // 添加关闭钩子处理Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("程序正在优雅关闭...");
        }));
        
        System.out.println("=== Core's Talk ===");
        System.out.println("Select mode:");
        System.out.println("1. P2P Mode (Original)");
        System.out.println("2. Group Chat Client");
        System.out.println("3. Group Chat Server");
        System.out.println("4. Group Chat Server with SSL");
        
        // if -Group in args
        if(args.length > 0 && args[0].equals("-g")){
            log.info("启动群聊客户端...");
            startGroupClient();
            return;
        }else if(args.length > 0 && args[0].equals("-gs")){
            log.info("启动群聊服务器...");
            AppConfig config = AppConfig.getInstance();
            GroupServer.StartServer(config.isSSLEnabled());
            return;
        }else if(args.length > 0 && args[0].equals("-gss")){
            log.info("启动SSL群聊服务器...");
            GroupServer.StartServer(true);
            return;
        }
        
        Scanner sc = new Scanner(System.in);
        try {
            System.out.print("Enter mode (1-4): ");
            if (!sc.hasNextInt()) {
                log.warn("无效输入，程序退出");
                return;
            }
            int mode = sc.nextInt();
            
            switch (mode) {
                case 1:
                    startP2PMode(sc);
                    break;
                case 2:
                    startGroupClient();
                    break;
                case 3:
                    AppConfig config = AppConfig.getInstance();
                    GroupServer.StartServer(config.isSSLEnabled());
                    break;
                case 4:
                    GroupServer.StartServer(true);
                    break;
                default:
                    log.warn("选择的模式无效，启动P2P模式...");
                    startP2PMode(sc);
            }
        } catch (java.util.NoSuchElementException e) {
            log.warn("输入被中断，程序退出");
        } catch (Exception e) {
            log.error("程序出错: {}", e.getMessage());
        } finally {
            if (sc != null) {
                sc.close();
            }
        }
    }
    
    private static void startGroupClient() {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.print("Enter server address (default: localhost): ");
            String serverHost = sc.nextLine().trim();
            if (serverHost.isEmpty()) {
                serverHost = "localhost";
            }
            
            System.out.print("Enter server port (default: 60033): ");
            String portInput = sc.nextLine().trim();
            int serverPort = 60033;
            if (!portInput.isEmpty()) {
                try {
                    serverPort = Integer.parseInt(portInput);
                } catch (NumberFormatException e) {
                    log.warn("端口号无效，使用默认端口: 60033");
                }
            }
            System.out.println("Connecting to " + serverHost + ":" + serverPort + " ...");
            URI serverUri = new URI("wss://" + serverHost + ":" + serverPort);
            GroupClient client = new GroupClient(serverUri);
            client.connectAndStart();
        } catch (Exception e) {
            log.error("启动群聊客户端失败: {}", e.getMessage());
        }
    }
    
    private static void startP2PMode(Scanner sc) throws Exception {
        log.info("启动P2P模式...");
        
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
        ZzSecurityHelper.setIv(kg.generateKey(seed+seed));
        server.SelfServerInfo = serverInfo;

        server server = new server();
        new Thread(server::Init).start();
        Thread.sleep(500);
        client client = new client();
        new Thread(client::Init).start();
    }
}