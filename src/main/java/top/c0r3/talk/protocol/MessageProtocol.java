package top.c0r3.talk.protocol;

public class MessageProtocol {
    public enum MessageType {
        // 认证相关
        LOGIN, LOGIN_SUCCESS, LOGIN_FAILED,
        REGISTER, REGISTER_SUCCESS, REGISTER_FAILED,
        LOGOUT,
        
        // 群聊管理
        CREATE_GROUP, CREATE_GROUP_SUCCESS, CREATE_GROUP_FAILED,
        DELETE_GROUP, DELETE_GROUP_SUCCESS, DELETE_GROUP_FAILED,
        JOIN_GROUP, JOIN_GROUP_SUCCESS, JOIN_GROUP_FAILED,
        LEAVE_GROUP, LEAVE_GROUP_SUCCESS, LEAVE_GROUP_FAILED,
        LIST_GROUPS,
        
        // 消息相关
        CHAT_MESSAGE, BROADCAST_MESSAGE,
        USER_JOIN, USER_LEAVE,
        ONLINE_USERS, GROUP_INFO,
        
        // 历史记录相关
        GET_HISTORY, GET_RECENT_MESSAGES, HISTORY_RESPONSE,
        
        // 系统消息
        ERROR, SUCCESS, HEARTBEAT
    }
    
    private MessageType type;
    private String fromUserId;
    private String fromUserNickname;
    private String toGroupId;
    private String content;
    private Object data;
    private long timestamp;
    
    public MessageProtocol() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public MessageProtocol(MessageType type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
    
    public MessageProtocol(MessageType type, String fromUserId, String toGroupId, String content) {
        this.type = type;
        this.fromUserId = fromUserId;
        this.toGroupId = toGroupId;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    
    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
    
    public String getFromUserNickname() { return fromUserNickname; }
    public void setFromUserNickname(String fromUserNickname) { this.fromUserNickname = fromUserNickname; }
    
    public String getToGroupId() { return toGroupId; }
    public void setToGroupId(String toGroupId) { this.toGroupId = toGroupId; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return "MessageProtocol{" +
                "type=" + type +
                ", fromUserId='" + fromUserId + '\'' +
                ", toGroupId='" + toGroupId + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
