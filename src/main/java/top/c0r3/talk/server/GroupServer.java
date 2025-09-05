package top.c0r3.talk.server;

import com.google.gson.Gson;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import top.c0r3.talk.cache.RedisManager;
import top.c0r3.talk.config.AppConfig;
import top.c0r3.talk.database.*;
import top.c0r3.talk.model.*;
import top.c0r3.talk.protocol.MessageProtocol;
import top.c0r3.talk.bot.GeminiBotLauncher;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupServer extends WebSocketServer {
    private static final Logger log = LoggerFactory.getLogger(GroupServer.class);
    
    public static final int MAX_CLIENTS = 100;
    private static AppConfig config = AppConfig.getInstance();
    public static final int port = config.getServerPort();
    public static final String ip = config.getServerHost();
    
    private Gson gson;
    private RedisManager redisManager;
    private UserDAO userDAO;
    private GroupDAO groupDAO;
    private MessageDAO messageDAO;
    private GeminiBotLauncher geminiBotLauncher;  // 添加GeminiBot启动器
    
    // 存储WebSocket连接对应的用户信息
    private Map<WebSocket, String> connectionToUserId;
    private Map<String, WebSocket> userIdToConnection;
    private Map<String, Set<String>> groupMembers; // groupId -> Set of userIds (已加入群聊的所有用户)
    private Map<String, Set<String>> activeGroupUsers; // groupId -> Set of userIds (当前在线且在群聊内的用户)
    
    public GroupServer() throws SQLException {
        super(new InetSocketAddress(ip, port));
        initializeServer();
    }
    
    public GroupServer(boolean useSSL) throws Exception {
        super(new InetSocketAddress(ip, port));
        if (useSSL) {
            setupSSL();
        }
        initializeServer();
    }
    
    private void initializeServer() throws SQLException {
        this.gson = new Gson();
        this.redisManager = RedisManager.getInstance();
        this.userDAO = new UserDAO();
        this.groupDAO = new GroupDAO();
        this.messageDAO = new MessageDAO();
        
        this.connectionToUserId = new ConcurrentHashMap<>();
        this.userIdToConnection = new ConcurrentHashMap<>();
        this.groupMembers = new ConcurrentHashMap<>();
        this.activeGroupUsers = new ConcurrentHashMap<>();
        this.geminiBotLauncher = new GeminiBotLauncher();  // 初始化GeminiBot启动器
        
        // 初始化数据库
        try {
            DatabaseConfig.getInstance().initDatabase();
            log.info("数据库初始化成功");
        } catch (SQLException e) {
            log.error("数据库初始化失败: {}", e.getMessage());
            throw e;
        }
    }
    
    private void setupSSL() throws Exception {
        String keystorePath = config.getProperty("ssl.keystore.path", "keystore.p12");
        String keystorePassword = config.getProperty("ssl.keystore.password", "changeit");
        String keyPassword = config.getProperty("ssl.key.password", "changeit");
        
        try {
            // 自动检测keystore类型
            String keystoreType = "PKCS12"; // 默认使用PKCS12
            if (keystorePath.endsWith(".jks")) {
                keystoreType = "JKS";
            }
            
            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            keyStore.load(new FileInputStream(keystorePath), keystorePassword.toCharArray());
            
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyPassword.toCharArray());
            
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            
            this.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            log.info("WebSocket服务器SSL/TLS已启用 (使用 {} keystore)", keystoreType);
            
        } catch (Exception e) {
            log.error("SSL设置失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    public static void StartServer() {
        StartServer(true); // 默认使用SSL
    }
    
    public static void StartServer(boolean useSSL) {
        try {
            GroupServer server;
            if (useSSL) {
                server = new GroupServer(true);
                log.info("群聊服务器已启动 (SSL/TLS): wss://{}:{}", ip, port);
            } else {
                server = new GroupServer();
                log.info("群聊服务器已启动: ws://{}:{}", ip, port);
            }
            
            server.start();
            log.info("等待客户端连接...");
            
            // 延迟启动GeminiBot，确保服务器完全启动
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // 等待3秒确保服务器稳定运行
                    server.geminiBotLauncher.startBot();
                } catch (InterruptedException e) {
                    log.warn("GeminiBot启动被中断: {}", e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            log.error("服务器启动失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // 新连接建立
        log.info("新客户端连接: {}", conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        try {
            String userId = connectionToUserId.remove(conn);
            if (userId != null) {
                userIdToConnection.remove(userId);
                redisManager.removeUserSession(userId);
                
                // 从所有活跃群聊中移除该用户
                for (Set<String> activeUsers : activeGroupUsers.values()) {
                    activeUsers.remove(userId);
                }
                
                // 通知所有群聊该用户离线
                notifyUserOffline(userId);
                log.info("用户 {} 已断开连接", userId);
            }
        } catch (Exception e) {
            // 忽略连接关闭时的清理错误
            log.debug("连接清理完成");
        }
    }
    
    @Override // 处理收到的消息
    public void onMessage(WebSocket conn, String message) {
        try {
            MessageProtocol protocol = gson.fromJson(message, MessageProtocol.class);
            handleMessage(conn, protocol);
        } catch (Exception e) {
            log.error("消息处理错误: {}", e.getMessage());
            sendError(conn, "Invalid message format");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String clientAddress = (conn != null) ? conn.getRemoteSocketAddress().toString() : "未知客户端";
        // 判断是否是常见的、可忽略的IO异常
        if (ex instanceof java.io.IOException) {
            String message = ex.getMessage();
            if (message != null && (
                message.contains("Connection reset by peer") ||
                message.contains("Broken pipe") ||
                message.contains("Connection closed")
            )) {
                log.info("客户端 [{}] 非正常断开连接: {}", clientAddress, message);
                return;
            }
        }
        
        log.error("WebSocket连接发生未处理的异常, 客户端: [{}]", clientAddress, ex);
    }
    
    @Override
    public void onStart() {
        log.info("群聊服务器启动成功!");
    }
    
    // 消息路由
    private void handleMessage(WebSocket conn, MessageProtocol protocol) {
        switch (protocol.getType()) {
            case LOGIN:
                handleLogin(conn, protocol);
                break;
            case REGISTER:
                handleRegister(conn, protocol);
                break;
            case LOGOUT:
                handleLogout(conn, protocol);
                break;
            case CREATE_GROUP:
                handleCreateGroup(conn, protocol);
                break;
            case DELETE_GROUP:
                handleDeleteGroup(conn, protocol);
                break;
            case JOIN_GROUP:
                handleJoinGroup(conn, protocol);
                break;
            case LEAVE_GROUP:
                handleLeaveGroup(conn, protocol);
                break;
            case LIST_GROUPS:
                handleListGroups(conn, protocol);
                break;
            case CHAT_MESSAGE:
                handleChatMessage(conn, protocol);
                break;
            case GET_HISTORY:
                handleGetHistory(conn, protocol);
                break;
            case GET_RECENT_MESSAGES:
                handleGetRecentMessages(conn, protocol);
                break;
            case HEARTBEAT:
                handleHeartbeat(conn, protocol);
                break;
            default:
                sendError(conn, "Unknown message type");
        }
    }
    
    private void handleLogin(WebSocket conn, MessageProtocol protocol) {
        try {
            // protocol.content 应该是 "nickname:password" 格式
            String[] parts = protocol.getContent().split(":", 2);
            if (parts.length != 2) {
                sendMessage(conn, MessageProtocol.MessageType.LOGIN_FAILED, "Invalid login format");
                return;
            }
            
            String nickname = parts[0];
            String password = parts[1];
            String passwordHash = hashPassword(password);
            
            User user = userDAO.getUserByNickname(nickname);
            if (user != null && user.getPasswdhash().equals(passwordHash)) {
                // 如果用户已在线，直接踢掉并发送通知
                WebSocket existingConn = userIdToConnection.get(user.getId());
                if (existingConn != null && existingConn != conn) {
                    sendMessage(existingConn, MessageProtocol.MessageType.LOGIN_FAILED, "You have been logged out due to a new login from another location.");
                    existingConn.close();
                }
                // 如果想登录GeminiBot, 检查是否为本地连接
                if(user.getNickname().equals("GeminiBot") && !conn.getRemoteSocketAddress().getAddress().isLoopbackAddress()) {
                    sendMessage(conn, MessageProtocol.MessageType.LOGIN_FAILED, "GeminiBot can only be logged in from localhost.");
                    conn.close();
                    return;
                }
                // 记录新连接
                connectionToUserId.put(conn, user.getId());
                userIdToConnection.put(user.getId(), conn);
                redisManager.cacheUserSession(user.getId(), conn.toString());
                userDAO.updateLastLogin(user.getId());
                
                MessageProtocol response = new MessageProtocol(MessageProtocol.MessageType.LOGIN_SUCCESS, "Login successful");
                response.setFromUserId(user.getId()); 
                response.setFromUserNickname(user.getNickname());
                response.setData(user);
                sendMessage(conn, response);
            } else {
                sendMessage(conn, MessageProtocol.MessageType.LOGIN_FAILED, "Invalid credentials");
            }
        } catch (Exception e) {
            sendError(conn, "Login error: " + e.getMessage());
        }
    }
    
    private void handleRegister(WebSocket conn, MessageProtocol protocol) {
        try {
            String[] parts = protocol.getContent().split(":", 2);
            if (parts.length != 2) {
                sendMessage(conn, MessageProtocol.MessageType.REGISTER_FAILED, "Invalid register format");
                return;
            }
            
            String nickname = parts[0];
            String password = parts[1];
            
            // 检查用户名是否已存在
            if (userDAO.getUserByNickname(nickname) != null) {
                sendMessage(conn, MessageProtocol.MessageType.REGISTER_FAILED, "Nickname already exists");
                return;
            }
            
            // 创建新用户
            String userId = UUID.randomUUID().toString();
            String passwordHash = hashPassword(password);
            User newUser = new User(userId, nickname, passwordHash);
            
            if (userDAO.createUser(newUser)) {
                sendMessage(conn, MessageProtocol.MessageType.REGISTER_SUCCESS, "Registration successful");
            } else {
                sendMessage(conn, MessageProtocol.MessageType.REGISTER_FAILED, "Registration failed");
            }
        } catch (Exception e) {
            sendError(conn, "Registration error: " + e.getMessage());
        }
    }
    
    private void handleLogout(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.remove(conn);
        if (userId != null) {
            userIdToConnection.remove(userId);
            redisManager.removeUserSession(userId);
            notifyUserOffline(userId);
        }
        conn.close();
    }
    
    private void handleCreateGroup(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "Not logged in");
            return;
        }
        
        try {
            String[] parts = protocol.getContent().split(":", 2);
            String groupName = parts[0];
            String groupPassword = parts.length > 1 ? parts[1] : "";
            
            String groupId = UUID.randomUUID().toString();
            String passwordHash = groupPassword.isEmpty() ? "" : hashPassword(groupPassword);
            
            Group group = new Group(groupId, groupName, passwordHash, userId);
            
            if (groupDAO.createGroup(group)) {
                groupMembers.put(groupId, new HashSet<>(Arrays.asList(userId)));
                
                MessageProtocol response = new MessageProtocol(MessageProtocol.MessageType.CREATE_GROUP_SUCCESS, "Group created successfully");
                response.setData(group);
                sendMessage(conn, response);
            } else {
                sendMessage(conn, MessageProtocol.MessageType.CREATE_GROUP_FAILED, "Failed to create group, name may already exist or contain invalid characters.");
            }
        } catch (Exception e) {
            sendError(conn, "Create group error: " + e.getMessage());
        }
    }
    
    private void handleDeleteGroup(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "Not logged in");
            return;
        }
        
        try {
            String groupId = protocol.getToGroupId();
            if (groupDAO.deleteGroup(groupId, userId)) {
                groupMembers.remove(groupId);
                redisManager.clearGroupCache(groupId);
                
                // 通知群成员群聊已删除
                broadcastToGroup(groupId, new MessageProtocol(MessageProtocol.MessageType.DELETE_GROUP_SUCCESS, "Group has been deleted"));
                
                sendMessage(conn, MessageProtocol.MessageType.DELETE_GROUP_SUCCESS, "Group deleted successfully");
            } else {
                sendMessage(conn, MessageProtocol.MessageType.DELETE_GROUP_FAILED, "Failed to delete group or not authorized");
            }
        } catch (Exception e) {
            sendError(conn, "Delete group error: " + e.getMessage());
        }
    }
    
    private void handleJoinGroup(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "Not logged in");
            return;
        }
        
        try {
            String[] parts = protocol.getContent().split(":", 2);
            String groupNameOrId = parts[0];  // 可能是群聊名称或ID
            String password = parts.length > 1 ? parts[1] : "";
            
            // 首先尝试通过名称查找群聊
            Group group = groupDAO.getGroupByName(groupNameOrId);
            
            // 如果通过名称没找到，尝试通过ID查找（向后兼容）
            if (group == null) {
                group = groupDAO.getGroupById(groupNameOrId);
            }
            
            if (group == null) {
                sendMessage(conn, MessageProtocol.MessageType.JOIN_GROUP_FAILED, "Group not found");
                return;
            }
            
            String groupId = group.getId();  // 获取群聊的UUID
            
            // 检查密码（优先使用Redis缓存的密码哈希）
            String hashedPassword = hashPassword(password);
            String cachedPasswordHash = redisManager.getCachedGroupPassword(groupId);
            
            if (cachedPasswordHash != null) {
                // 使用缓存的密码验证
                if (!cachedPasswordHash.isEmpty() && !cachedPasswordHash.equals(hashedPassword)) {
                    sendMessage(conn, MessageProtocol.MessageType.JOIN_GROUP_FAILED, "Invalid group password");
                    return;
                }
            } else {
                // 缓存未命中，使用群组对象验证
                if (!group.getPasswdhash().isEmpty() && !group.getPasswdhash().equals(hashedPassword)) {
                    sendMessage(conn, MessageProtocol.MessageType.JOIN_GROUP_FAILED, "Invalid group password");
                    return;
                }
            }
            
            if (groupDAO.addMemberToGroup(groupId, userId)) {
                groupMembers.computeIfAbsent(groupId, k -> new HashSet<>()).add(userId);
                activeGroupUsers.computeIfAbsent(groupId, k -> new HashSet<>()).add(userId);
                redisManager.addOnlineUser(groupId, userId);
                
                User user = userDAO.getUserById(userId);
                
                // 发送成功消息给用户
                MessageProtocol response = new MessageProtocol(MessageProtocol.MessageType.JOIN_GROUP_SUCCESS, "Joined group successfully");
                response.setData(group);
                sendMessage(conn, response);
                
                // 自动发送3小时历史记录
                sendHistoryOnJoin(conn, groupId, 3);
                
                // 通知群内其他成员
                Message joinMessage = new Message(UUID.randomUUID().toString(), groupId, userId, user.getNickname(), 
                    user.getNickname() + " joined the group", Message.MessageType.JOIN);
                broadcastMessage(joinMessage);
            } else {
                sendMessage(conn, MessageProtocol.MessageType.JOIN_GROUP_FAILED, "Failed to join group");
            }
        } catch (Exception e) {
            sendError(conn, "Join group error: " + e.getMessage());
        }
    }
    
    private void handleLeaveGroup(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "Not logged in");
            return;
        }
        
        try {
            String groupId = protocol.getToGroupId();
            
            if (groupDAO.removeMemberFromGroup(groupId, userId)) {
                Set<String> members = groupMembers.get(groupId);
                if (members != null) {
                    members.remove(userId);
                }
                Set<String> activeUsers = activeGroupUsers.get(groupId);
                if (activeUsers != null) {
                    activeUsers.remove(userId);
                }
                redisManager.removeOnlineUser(groupId, userId);
                
                User user = userDAO.getUserById(userId);
                
                sendMessage(conn, MessageProtocol.MessageType.LEAVE_GROUP_SUCCESS, "Left group successfully");
                
                // 通知群内其他成员
                Message leaveMessage = new Message(UUID.randomUUID().toString(), groupId, userId, user.getNickname(), 
                    user.getNickname() + " left the group", Message.MessageType.LEAVE);
                broadcastMessage(leaveMessage);
            } else {
                sendMessage(conn, MessageProtocol.MessageType.LEAVE_GROUP_FAILED, "Failed to leave group");
            }
        } catch (Exception e) {
            sendError(conn, "Leave group error: " + e.getMessage());
        }
    }
    
    private void handleListGroups(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "Not logged in");
            return;
        }
        
        try {
            List<Group> userGroups = groupDAO.getUserGroups(userId);
            MessageProtocol response = new MessageProtocol(MessageProtocol.MessageType.SUCCESS, "Groups list");
            response.setData(userGroups);
            sendMessage(conn, response);
        } catch (Exception e) {
            sendError(conn, "List groups error: " + e.getMessage());
        }
    }
    
    private void handleChatMessage(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "Not logged in");
            return;
        }
        
        try {
            User user = userDAO.getUserById(userId);
            String groupId = protocol.getToGroupId();
            
            if (groupId == null || groupId.isEmpty()) {
                sendError(conn, "No group specified");
                return;
            }
            
            // 检查用户是否在群内（直接查询数据库确保准确性）
            List<String> groupMembers = groupDAO.getGroupMembers(groupId);
            if (!groupMembers.contains(userId)) {
                sendError(conn, "Not a member of this group");
                return;
            }
            
            // 确保用户在活跃群聊列表中（如果不在则添加，可能是重连后的情况）
            activeGroupUsers.computeIfAbsent(groupId, k -> new HashSet<>()).add(userId);
            
            Message message = new Message(UUID.randomUUID().toString(), groupId, userId, user.getNickname(), 
                protocol.getContent(), Message.MessageType.TEXT);
            
            // 广播消息
            broadcastMessage(message);
        } catch (Exception e) {
            log.error("聊天消息处理错误: {}", e.getMessage(), e);
            sendError(conn, "Chat message error: " + e.getMessage());
        }
    }
    
    private void handleHeartbeat(WebSocket conn, MessageProtocol protocol) {
        sendMessage(conn, MessageProtocol.MessageType.HEARTBEAT, "pong");
    }
    
    private void handleGetHistory(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "Not logged in");
            return;
        }
        
        try {
            String groupId = protocol.getToGroupId();
            if (groupId == null || groupId.isEmpty()) {
                sendError(conn, "Group ID required");
                return;
            }
            
            // 检查用户是否在群内
            Group group = groupDAO.getGroupById(groupId);
            if (group == null || !group.getMemberIds().contains(userId)) {
                sendError(conn, "Not a member of this group");
                return;
            }
            
            // 解析时间范围（小时数）
            int hours = 3; // 默认3小时
            if (protocol.getContent() != null && !protocol.getContent().isEmpty()) {
                try {
                    hours = Integer.parseInt(protocol.getContent());
                } catch (NumberFormatException e) {
                    sendError(conn, "Invalid hours format");
                    return;
                }
            }
            
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (hours * 60 * 60 * 1000L); // 转换为毫秒
            
            // 从数据库获取历史消息
            List<Message> historyMessages = messageDAO.getGroupMessagesInTimeRange(groupId, startTime, endTime, 200);
            
            // 从Redis获取缓存消息并合并
            List<Message> cachedMessages = redisManager.getAllCachedMessages(groupId);
            
            // 合并并去重消息
            Map<String, Message> messageMap = new HashMap<>();
            for (Message msg : historyMessages) {
                messageMap.put(msg.getId(), msg);
            }
            for (Message msg : cachedMessages) {
                if (msg.getTimestamp() >= startTime) {
                    messageMap.put(msg.getId(), msg);
                }
            }
            
            // 按时间排序
            List<Message> allMessages = new ArrayList<>(messageMap.values());
            allMessages.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
            
            MessageProtocol response = new MessageProtocol(MessageProtocol.MessageType.HISTORY_RESPONSE, 
                "History for last " + hours + " hours");
            response.setData(allMessages);
            sendMessage(conn, response);
        } catch (Exception e) {
            sendError(conn, "Get history error: " + e.getMessage());
        }
    }
    
    private void handleGetRecentMessages(WebSocket conn, MessageProtocol protocol) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "Not logged in");
            return;
        }
        
        try {
            String groupId = protocol.getToGroupId();
            if (groupId == null || groupId.isEmpty()) {
                sendError(conn, "Group ID required");
                return;
            }
            
            // 检查用户是否在群内
            Group group = groupDAO.getGroupById(groupId);
            if (group == null || !group.getMemberIds().contains(userId)) {
                sendError(conn, "Not a member of this group");
                return;
            }
            
            // 先尝试从Redis获取缓存消息
            List<Message> cachedMessages = redisManager.getCachedMessages(groupId, 50);
            
            List<Message> recentMessages;
            if (cachedMessages.size() >= 50) {
                recentMessages = cachedMessages;
            } else {
                // Redis中消息不足，从数据库补充
                List<Message> dbMessages = messageDAO.getGroupMessages(groupId, 50);
                
                // 合并去重
                Map<String, Message> messageMap = new HashMap<>();
                for (Message msg : dbMessages) {
                    messageMap.put(msg.getId(), msg);
                }
                for (Message msg : cachedMessages) {
                    messageMap.put(msg.getId(), msg);
                }
                
                recentMessages = new ArrayList<>(messageMap.values());
                recentMessages.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
                
                // 限制数量
                if (recentMessages.size() > 50) {
                    recentMessages = recentMessages.subList(recentMessages.size() - 50, recentMessages.size());
                }
            }
            
            MessageProtocol response = new MessageProtocol(MessageProtocol.MessageType.HISTORY_RESPONSE, 
                "Recent messages");
            response.setData(recentMessages);
            sendMessage(conn, response);
        } catch (Exception e) {
            sendError(conn, "Get recent messages error: " + e.getMessage());
        }
    }
    
    private void sendHistoryOnJoin(WebSocket conn, String groupId, int hours) {
        try {
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (hours * 60 * 60 * 1000L);
            
            // 从数据库获取历史消息
            List<Message> historyMessages = messageDAO.getGroupMessagesInTimeRange(groupId, startTime, endTime, 100);
            
            // 从Redis获取缓存消息并合并
            List<Message> cachedMessages = redisManager.getAllCachedMessages(groupId);
            
            // 合并并去重消息
            Map<String, Message> messageMap = new HashMap<>();
            for (Message msg : historyMessages) {
                if (msg != null) {
                    messageMap.put(msg.getId(), msg);
                }
            }
            for (Message msg : cachedMessages) {
                if (msg != null && msg.getTimestamp() >= startTime) {
                    messageMap.put(msg.getId(), msg);
                }
            }
            
            // 按时间排序
            List<Message> allMessages = new ArrayList<>(messageMap.values());
            allMessages.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
            
            if (!allMessages.isEmpty()) {
                MessageProtocol response = new MessageProtocol(MessageProtocol.MessageType.HISTORY_RESPONSE, 
                    "=== Chat History (Last " + hours + " hours) ===");
                response.setData(allMessages);
                sendMessage(conn, response);
            }
        } catch (Exception e) {
            log.error("加入时发送历史消息错误: {}", e.getMessage(), e);
        }
    }
    
    private void broadcastMessage(Message message) {
        try {
            // 保存到数据库（MessageDAO.saveMessage 会自动同步到Redis）
            messageDAO.saveMessage(message);
            
            // 广播给在线用户
            MessageProtocol protocol = new MessageProtocol(
                MessageProtocol.MessageType.BROADCAST_MESSAGE, 
                message.getSenderId(), 
                message.getGroupId(), 
                message.getContent()
            );
            protocol.setFromUserNickname(message.getSenderNickname());
            protocol.setData(message);
            
            broadcastToGroup(message.getGroupId(), protocol);
        } catch (Exception e) {
            log.error("广播消息错误: {}", e.getMessage(), e);
        }
    }
    
    private void broadcastToGroup(String groupId, MessageProtocol protocol) {
        // 获取当前在线且在该群聊内的用户列表
        Set<String> activeUsers = activeGroupUsers.get(groupId);
        if (activeUsers == null || activeUsers.isEmpty()) {
            return;
        }
        
        for (String userId : activeUsers) {
            WebSocket memberConn = userIdToConnection.get(userId);
            if (memberConn != null && memberConn.isOpen()) {
                sendMessage(memberConn, protocol);
            } else {
                // 如果连接断开，从活跃列表中移除
                activeUsers.remove(userId);
            }
        }
    }
    
    // Public method for Bot to broadcast messages
    public void broadcastBotMessage(String groupId, MessageProtocol protocol) {
        broadcastToGroup(groupId, protocol);
    }
    
    /**
     * 添加用户到活跃群聊列表（当用户加入群聊时）
     */
    public void addUserToActiveGroup(String groupId, String userId) {
        activeGroupUsers.computeIfAbsent(groupId, k -> new HashSet<>()).add(userId);
    }
    
    /**
     * 从活跃群聊列表中移除用户（当用户离开群聊时）
     */
    public void removeUserFromActiveGroup(String groupId, String userId) {
        Set<String> activeUsers = activeGroupUsers.get(groupId);
        if (activeUsers != null) {
            activeUsers.remove(userId);
        }
    }
    
    /**
     * 获取群聊中的活跃用户数量
     */
    public int getActiveUsersCount(String groupId) {
        Set<String> activeUsers = activeGroupUsers.get(groupId);
        return activeUsers != null ? activeUsers.size() : 0;
    }
    
    private void notifyUserOffline(String userId) {
        // 通知用户所在的所有群聊
        List<Group> userGroups = groupDAO.getUserGroups(userId);
        for (Group group : userGroups) {
            redisManager.removeOnlineUser(group.getId(), userId);
            // 从群成员列表中移除（如果需要）
            Set<String> members = groupMembers.get(group.getId());
            if (members != null) {
                members.remove(userId);
            }
            // 从活跃用户列表中移除
            Set<String> activeUsers = activeGroupUsers.get(group.getId());
            if (activeUsers != null) {
                activeUsers.remove(userId);
            }
        }
    }
    
    private void sendMessage(WebSocket conn, MessageProtocol protocol) {
        if (conn.isOpen()) {
            if (protocol == null) {
                log.warn("尝试发送空协议消息");
                return;
            }
            try {
                String jsonMessage = gson.toJson(protocol);
                if (jsonMessage == null || jsonMessage.equals("null")) {
                    log.warn("JSON序列化返回null，协议: {}", protocol);
                    return;
                }
                conn.send(jsonMessage);
            } catch (Exception e) {
                log.error("消息序列化错误: {}", e.getMessage());
            }
        }
    }
    
    private void sendMessage(WebSocket conn, MessageProtocol.MessageType type, String content) {
        sendMessage(conn, new MessageProtocol(type, content));
    }
    
    private void sendError(WebSocket conn, String errorMessage) {
        sendMessage(conn, MessageProtocol.MessageType.ERROR, errorMessage);
    }
    
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
    
    /**
     * 关闭服务器并停止GeminiBot
     */
    public void shutdown() {
        try {
            if (geminiBotLauncher != null) {
                geminiBotLauncher.stopBot();
            }
            stop();
            log.info("群聊服务器关闭完成");
        } catch (Exception e) {
            log.error("服务器关闭时发生错误: {}", e.getMessage());
        }
    }
}