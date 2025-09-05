package top.c0r3.talk.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import top.c0r3.talk.model.Group;
import top.c0r3.talk.model.Message;
import top.c0r3.talk.model.User;
import top.c0r3.talk.protocol.MessageProtocol;

import javax.net.ssl.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GroupClient extends WebSocketClient {
    private Gson gson;
    private Scanner scanner;
    private User currentUser;
    private Group currentGroup;
    private boolean isLoggedIn = false;
    private boolean isConnected = false;
    private CountDownLatch connectionLatch;
    private SimpleDateFormat timeFormat;
    
    public GroupClient(URI serverUri) {
        super(serverUri);
        System.err.println("Initializing GroupClient for " + serverUri);
        this.gson = new Gson();
        this.scanner = new Scanner(System.in);
        this.connectionLatch = new CountDownLatch(1);
        this.timeFormat = new SimpleDateFormat("HH:mm:ss");
        
        // 如果是WSS连接，配置SSL
        if ("wss".equalsIgnoreCase(serverUri.getScheme())) {
            setupSSLForSelfSignedCert();
        }
        
        // 设置命令处理器
        GroupCommand.setClient(this);
    }
    
    /**
     * 配置SSL以接受自签名证书
     */
    private void setupSSLForSelfSignedCert() {
        try {
            // 创建信任所有证书的TrustManager
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // 信任所有客户端证书
                    }
                    
                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // 信任所有服务器证书（包括自签名证书）
                    }
                }
            };
            
            // 创建SSL上下文
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            // 设置WebSocket的SSL工厂
            this.setSocketFactory(sslContext.getSocketFactory());
            
            System.out.println("SSL configured to accept self-signed certificates");
            
        } catch (Exception e) {
            System.err.println("Failed to setup SSL: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        try {
            // 默认使用SSL连接
            URI serverUri = new URI("wss://localhost:60033");
            GroupClient client = new GroupClient(serverUri);
            client.connectAndStart();
        } catch (Exception e) {
            System.err.println("Failed to start client: " + e.getMessage());
        }
    }
    
    public void connectAndStart() {
        try {
            connect();
            
            // 等待连接建立
            if (connectionLatch.await(5, TimeUnit.SECONDS)) {
                System.out.println("Connected to Group Chat Server!");
                startUserInterface();
            } else {
                System.err.println("Failed to connect to server");
            }
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        isConnected = true;
        connectionLatch.countDown();
        System.out.println("Connected to server");
    }
    
    @Override
    public void onMessage(String message) {
        try {
            // System.out.println("Received raw message: [" + message + "]");
            // System.out.println("Message length: " + (message != null ? message.length() : "null"));
            
            if (message == null || message.trim().isEmpty()) {
                System.err.println("Received null or empty message from server");
                return;
            }
            
            MessageProtocol protocol = gson.fromJson(message, MessageProtocol.class);
            if (protocol == null) {
                System.err.println("Failed to parse server message: " + message);
                return;
            }
            handleServerMessage(protocol);
        } catch (Exception e) {
            System.err.println("Error processing server message: " + e.getMessage());
            System.err.println("Message content: " + message);
            e.printStackTrace();
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        isConnected = false;
        System.out.println("Disconnected from server: " + reason);
        if (scanner != null) {
            scanner.close();
        }
    }
    
    @Override
    public void onError(Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
    }
    
    private void handleServerMessage(MessageProtocol protocol) {
        switch (protocol.getType()) {
            case LOGIN_SUCCESS:
                currentUser = gson.fromJson(gson.toJson(protocol.getData()), User.class);
                isLoggedIn = true;
                System.out.println("Login successful! Welcome, " + currentUser.getNickname());
                break;
                
            case LOGIN_FAILED:
                System.out.println("Login failed: " + protocol.getContent());
                break;
                
            case REGISTER_SUCCESS:
                System.out.println("Registration successful! You can now login.");
                break;
                
            case REGISTER_FAILED:
                System.out.println("Registration failed: " + protocol.getContent());
                break;
                
            case CREATE_GROUP_SUCCESS:
                currentGroup = gson.fromJson(gson.toJson(protocol.getData()), Group.class);
                System.out.println("Group created successfully: " + currentGroup.getName());
                System.out.println("  Group ID: " + currentGroup.getId());
                break;
                
            case CREATE_GROUP_FAILED:
                System.out.println("Failed to create group: " + protocol.getContent());
                break;
                
            case JOIN_GROUP_SUCCESS:
                currentGroup = gson.fromJson(gson.toJson(protocol.getData()), Group.class);
                System.out.println("Joined group: " + currentGroup.getName());
                System.out.println("Group ID: " + currentGroup.getId());
                System.out.println("You can now start chatting! Type /help for commands.");
                break;
                
            case JOIN_GROUP_FAILED:
                System.out.println("Failed to join group: " + protocol.getContent());
                break;
                
            case LEAVE_GROUP_SUCCESS:
                System.out.println("Left group successfully");
                currentGroup = null;
                break;
                
            case DELETE_GROUP_SUCCESS:
                System.out.println("Group deleted successfully");
                currentGroup = null;
                break;
                
            case BROADCAST_MESSAGE:
                Message msg = gson.fromJson(gson.toJson(protocol.getData()), Message.class);
                displayMessage(msg);
                break;
                
            case HISTORY_RESPONSE:
                displayHistoryMessages(protocol);
                break;
                
            case USER_JOIN:
                System.out.println(">>> " + protocol.getFromUserNickname() + " joined the group");
                break;
                
            case USER_LEAVE:
                System.out.println(">>> " + protocol.getFromUserNickname() + " left the group");
                break;
                
            case SUCCESS:
                if (protocol.getData() instanceof List) {
                    displayGroups(protocol);
                } else {
                    System.out.println("" + protocol.getContent());
                }
                break;
                
            case ERROR:
                System.out.println("Error: " + protocol.getContent());
                break;
                
            case HEARTBEAT:
                // 静默处理心跳
                break;
                
            default:
                System.out.println("Unknown message type: " + protocol.getType());
        }
    }
    
    private void startUserInterface() {
        System.out.println("\n=== Core's Group Chat Client ===");
        System.out.println("Type /help for available commands");
        System.out.println();
        
        while (isConnected) {
            try {
                System.out.print("> ");
                
                // 检查输入流是否可用
                if (!scanner.hasNextLine()) {
                    System.out.println("\nInput stream closed. Exiting...");
                    break;
                }
                
                String input = scanner.nextLine().trim();
                
                if (input.isEmpty()) continue;
                
                if (input.startsWith("/")) {
                    GroupCommand.processCommand(input);
                } else if (isLoggedIn && currentGroup != null) {
                    sendChatMessage(input);
                } else {
                    System.out.println("Please login and join a group first, or use /help for commands");
                }
            } catch (java.util.NoSuchElementException e) {
                // 处理Ctrl+C或输入流关闭
                System.out.println("\nInput interrupted. Exiting...");
                break;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                break;
            }
        }
    }
    
    // 公共访问方法
    public boolean isLoggedIn() {
        return isLoggedIn;
    }
    
    public Group getCurrentGroup() {
        return currentGroup;
    }
    
    public User getCurrentUser() {
        return currentUser;
    }
    
    // 发送消息的公共方法
    public void sendLogin(String nickname, String password) {
        MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.LOGIN, nickname + ":" + password);
        send(gson.toJson(protocol));
    }
    
    public void sendRegister(String nickname, String password) {
        MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.REGISTER, nickname + ":" + password);
        send(gson.toJson(protocol));
    }
    
    public void sendLogout() {
        MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.LOGOUT, "");
        send(gson.toJson(protocol));
        isLoggedIn = false;
        currentUser = null;
        currentGroup = null;
    }
    
    public void sendCreateGroup(String groupName, String password) {
        MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.CREATE_GROUP, groupName + ":" + password);
        send(gson.toJson(protocol));
    }
    
    public void sendJoinGroup(String groupId, String password) {
        MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.JOIN_GROUP, groupId + ":" + password);
        send(gson.toJson(protocol));
    }
    
    public void sendLeaveGroup() {
        if (currentGroup != null) {
            MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.LEAVE_GROUP, "");
            protocol.setToGroupId(currentGroup.getId());
            send(gson.toJson(protocol));
        }
    }
    
    public void sendDeleteGroup() {
        if (currentGroup != null) {
            MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.DELETE_GROUP, "");
            protocol.setToGroupId(currentGroup.getId());
            send(gson.toJson(protocol));
        }
    }
    
    public void sendListGroups() {
        MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.LIST_GROUPS, "");
        send(gson.toJson(protocol));
    }
    
    public void sendGetHistory(int hours) {
        if (currentGroup != null) {
            MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.GET_HISTORY, String.valueOf(hours));
            protocol.setToGroupId(currentGroup.getId());
            send(gson.toJson(protocol));
        }
    }
    
    public void sendGetRecentMessages() {
        if (currentGroup != null) {
            MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.GET_RECENT_MESSAGES, "");
            protocol.setToGroupId(currentGroup.getId());
            send(gson.toJson(protocol));
        }
    }
    
    protected void sendChatMessage(String message) {
        if (currentGroup != null) {
            MessageProtocol protocol = new MessageProtocol(MessageProtocol.MessageType.CHAT_MESSAGE, message);
            protocol.setToGroupId(currentGroup.getId());
            send(gson.toJson(protocol));
        } else {
            System.out.println("You are not in any group. Please join a group first using '/join <group_id> <password>'");
        }
    }
    
    private void displayMessage(Message message) {
        String timestamp = timeFormat.format(new Date(message.getTimestamp()));
        System.out.println("[" + timestamp + "] " + message.getSenderNickname() + ": " + message.getContent());
    }
    
    private void displayHistoryMessages(MessageProtocol protocol) {
        try {
            System.out.println("\n" + protocol.getContent());
            
            Type listType = new TypeToken<List<Message>>(){}.getType();
            List<Message> messages = gson.fromJson(gson.toJson(protocol.getData()), listType);
            
            if (messages.isEmpty()) {
                System.out.println("No messages found.");
            } else {
                for (Message message : messages) {
                    displayMessage(message);
                }
            }
            System.out.println("=== End of History ===\n");
        } catch (Exception e) {
            System.err.println("Error displaying history: " + e.getMessage());
        }
    }
    
    private void displayGroups(MessageProtocol protocol) {
        try {
            System.out.println("\n=== Your Groups ===");
            
            Type listType = new TypeToken<List<Group>>(){}.getType();
            List<Group> groups = gson.fromJson(gson.toJson(protocol.getData()), listType);
            
            if (groups.isEmpty()) {
                System.out.println("You are not a member of any groups.");
            } else {
                for (Group group : groups) {
                    String status = (group.equals(currentGroup)) ? " (Current)" : "";
                    System.out.println("- " + group.getName() + " (ID: " + group.getId() + ") - " + 
                        group.getMemberIds().size() + " members" + status);
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.err.println("Error displaying groups: " + e.getMessage());
        }
    }
}
