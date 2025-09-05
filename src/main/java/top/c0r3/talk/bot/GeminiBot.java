package top.c0r3.talk.bot;

import com.google.gson.Gson;
import top.c0r3.talk.client.GroupClient;
import top.c0r3.talk.protocol.MessageProtocol;
import top.c0r3.talk.config.GeminiBotConfig;
import top.c0r3.talk.api.GeminiApiClient;

import java.net.URI;

/**
 * GeminiBot - 简化的Gemini AI聊天机器人
 */
public class GeminiBot extends GroupClient {
    private final Gson gson;
    private final GeminiApiClient geminiApiClient;
    private String geminiGroupId = null;
    
    public GeminiBot(URI serverUri) {
        super(serverUri);
        this.gson = new Gson();
        this.geminiApiClient = new GeminiApiClient();
        System.out.println("GeminiBot initialized");
    }
    
    /**
     * 启动Bot
     */
    public boolean start() {
        try {
            // 连接服务器
            this.connect();
            Thread.sleep(1000);
            
            if (!this.isOpen()) {
                System.err.println("Failed to connect to server");
                return false;
            }
            
            // 登录
            if (!login()) {
                System.err.println("Failed to login");
                return false;
            }
            
            // 设置群组
            setupGroup();
            
            System.out.println("GeminiBot is ready!");
            return true;
            
        } catch (Exception e) {
            System.err.println("GeminiBot startup error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 登录
     */
    private boolean login() {
        try {
            sendRegister(GeminiBotConfig.BOT_NICKNAME, GeminiBotConfig.BOT_PASSWORD);
            Thread.sleep(500);
            
            sendLogin(GeminiBotConfig.BOT_NICKNAME, GeminiBotConfig.BOT_PASSWORD);
            Thread.sleep(1000);
            
            return isLoggedIn() && getCurrentUser() != null && 
                   getCurrentUser().getNickname().equals(GeminiBotConfig.BOT_NICKNAME);
                   
        } catch (Exception e) {
            System.err.println("Login error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 设置群组
     */
    private void setupGroup() {
        try {
            sendListGroups();
            Thread.sleep(1000);
            
            // 如果没有找到群组，创建一个
            if (geminiGroupId == null) {
                MessageProtocol createMsg = new MessageProtocol();
                createMsg.setType(MessageProtocol.MessageType.CREATE_GROUP);
                createMsg.setContent(GeminiBotConfig.BOT_GROUP_NAME + ":" + GeminiBotConfig.BOT_GROUP_PASSWORD);
                send(gson.toJson(createMsg));
                Thread.sleep(1000);
            }
            
        } catch (Exception e) {
            System.err.println("Group setup error: " + e.getMessage());
        }
    }
    
    /**
     * 处理接收到的消息
     */
    @Override
    public void onMessage(String message) {
        super.onMessage(message);
        
        try {
            MessageProtocol protocol = gson.fromJson(message, MessageProtocol.class);
            
            // 处理群组相关消息
            handleGroupMessage(protocol);
            
            // 处理聊天消息
            if (shouldRespond(protocol)) {
                handleChatMessage(protocol);
            }
            
        } catch (Exception e) {
            System.err.println("Message handling error: " + e.getMessage());
        }
    }
    
    /**
     * 处理群组消息
     */
    private void handleGroupMessage(MessageProtocol protocol) {
        if (protocol.getType() == MessageProtocol.MessageType.SUCCESS && protocol.getData() != null) {
            // 处理群组列表
            if (protocol.getData() instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> groups = 
                    (java.util.List<java.util.Map<String, Object>>) protocol.getData();
                
                for (java.util.Map<String, Object> group : groups) {
                    String groupName = (String) group.get("name");
                    if (GeminiBotConfig.BOT_GROUP_NAME.equals(groupName)) {
                        String groupId = (String) group.get("id");
                        sendJoinGroup(groupId, GeminiBotConfig.BOT_GROUP_PASSWORD);
                        return;
                    }
                }
            }
        }
        
        // 处理群组创建/加入成功
        if ((protocol.getType() == MessageProtocol.MessageType.CREATE_GROUP_SUCCESS ||
             protocol.getType() == MessageProtocol.MessageType.JOIN_GROUP_SUCCESS) &&
            protocol.getData() instanceof java.util.Map) {
            
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> groupData = (java.util.Map<String, Object>) protocol.getData();
            geminiGroupId = (String) groupData.get("id");
            String groupName = (String) groupData.get("name");
            System.out.println("Joined group: " + groupName + " (ID: " + geminiGroupId + ")");
        }
    }
    
    /**
     * 判断是否需要回复
     */
    private boolean shouldRespond(MessageProtocol message) {
        return message != null &&
               message.getType() == MessageProtocol.MessageType.BROADCAST_MESSAGE &&
               !GeminiBotConfig.BOT_NICKNAME.equals(message.getFromUserNickname()) &&
               geminiGroupId != null &&
               geminiGroupId.equals(message.getToGroupId()) &&
               message.getContent() != null &&
               message.getContent().contains("@Gemini");
    }
    
    /**
     * 处理聊天消息
     */
    private void handleChatMessage(MessageProtocol message) {
        try {
            String content = message.getContent();
            String prompt = extractPrompt(content);
            
            if (prompt.isEmpty()) {
                prompt = "你好！有什么我可以帮助你的吗？";
            }
            
            System.out.println("Processing: " + prompt);
            
            String response = geminiApiClient.generateResponse(prompt);
            sendResponse(response);
            
        } catch (Exception e) {
            System.err.println("Chat handling error: " + e.getMessage());
            sendResponse("抱歉，我遇到了技术问题，请稍后再试。");
        }
    }
    
    /**
     * 提取提示词
     */
    private String extractPrompt(String content) {
        if (content == null) return "";
        
        int index = content.indexOf("@Gemini");
        if (index == -1) return "";
        
        String afterTrigger = content.substring(index + 7).trim();
        if (afterTrigger.startsWith(":") || afterTrigger.startsWith("，") || 
            afterTrigger.startsWith(",")) {
            afterTrigger = afterTrigger.substring(1).trim();
        }
        
        return afterTrigger;
    }
    
    /**
     * 发送回复
     */
    private void sendResponse(String content) {
        if (geminiGroupId != null) {
            MessageProtocol response = new MessageProtocol();
            response.setType(MessageProtocol.MessageType.CHAT_MESSAGE);
            response.setFromUserNickname(GeminiBotConfig.BOT_NICKNAME);
            response.setToGroupId(geminiGroupId);
            response.setContent(content);
            send(gson.toJson(response));
        }
    }
    
    /**
     * 停止Bot
     */
    public void stop() {
        this.close();
        System.out.println("GeminiBot stopped");
    }
    
    /**
     * 获取群组ID
     */
    public String getGeminiGroupId() {
        return geminiGroupId;
    }
}
