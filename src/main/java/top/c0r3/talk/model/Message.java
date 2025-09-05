package top.c0r3.talk.model;

public class Message {
    private Long dbId;          // 数据库自增ID
    private String id;          // UUID，业务ID
    private String groupId;     // 群组UUID
    private Long groupDbId;     // 群组数据库ID
    private String senderId;    // 发送者UUID
    private Long senderDbId;    // 发送者数据库ID
    private String senderNickname;
    private String content;
    private long timestamp;
    private MessageType type;

    public enum MessageType {
        TEXT, SYSTEM, JOIN, LEAVE
    }

    public Message() {}

    public Message(String id, String groupId, String senderId, String senderNickname, String content, MessageType type) {
        this.id = id;
        this.groupId = groupId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String id, String groupId, String senderId, String senderNickname, String content, MessageType type, long timestamp) {
        this.id = id;
        this.groupId = groupId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public Long getGroupDbId() { return groupDbId; }
    public void setGroupDbId(Long groupDbId) { this.groupDbId = groupDbId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public Long getSenderDbId() { return senderDbId; }
    public void setSenderDbId(Long senderDbId) { this.senderDbId = senderDbId; }

    public String getSenderNickname() { return senderNickname; }
    public void setSenderNickname(String senderNickname) { this.senderNickname = senderNickname; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    @Override
    public String toString() {
        return "[" + senderNickname + "] " + content;
    }
}
