package top.c0r3.talk.database;

import top.c0r3.talk.model.Message;
import top.c0r3.talk.model.Group;
import top.c0r3.talk.model.User;
import top.c0r3.talk.cache.RedisManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    private Connection connection;
    private RedisManager redisManager;
    
    public MessageDAO() throws SQLException {
        this.connection = DatabaseConfig.getInstance().getConnection();
        this.redisManager = RedisManager.getInstance();
    }
    
    public boolean saveMessage(Message message) {
        String sql = "INSERT INTO `messages` (uuid, group_id, sender_id, sender_nickname, content, message_type, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try {
            // 开始事务
            connection.setAutoCommit(false);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, message.getId());
                
                // 获取group和sender的数据库ID
                GroupDAO groupDAO = new GroupDAO();
                UserDAO userDAO = new UserDAO();
                Group group = groupDAO.getGroupById(message.getGroupId());
                User sender = userDAO.getUserById(message.getSenderId());
                
                if (group == null || sender == null) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                    return false;
                }
                
                stmt.setLong(2, group.getDbId());
                stmt.setLong(3, sender.getDbId());
                stmt.setString(4, message.getSenderNickname());
                stmt.setString(5, message.getContent());
                stmt.setString(6, message.getType().name());
                stmt.setLong(7, message.getTimestamp());
                
                int result = stmt.executeUpdate();
                if (result > 0) {
                    // 获取生成的数据库ID
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        message.setDbId(generatedKeys.getLong(1));
                        message.setGroupDbId(group.getDbId());
                        message.setSenderDbId(sender.getDbId());
                    }
                    
                    // 提交数据库事务
                    connection.commit();
                    connection.setAutoCommit(true);
                    
                    // 数据库操作成功后，同步到Redis缓存
                    try {
                        redisManager.cacheMessage(message.getGroupId(), message);
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to cache message to Redis: " + e.getMessage());
                        // Redis缓存失败不影响数据库操作的成功
                    }
                    
                    return true;
                } else {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException rollbackException) {
                System.err.println("Error during rollback: " + rollbackException.getMessage());
            }
            System.err.println("Error saving message: " + e.getMessage());
        }
        return false;
    }
    
    public List<Message> getRecentMessages(String groupId, int limit) {
        String sql = "SELECT * FROM `messages` WHERE group_id = ? AND timestamp > ? ORDER BY timestamp ASC LIMIT ?";
        List<Message> messages = new ArrayList<>();
        long threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000);
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setLong(2, threeDaysAgo);
            stmt.setInt(3, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message message = new Message(
                    rs.getString("id"),
                    rs.getString("group_id"),
                    rs.getString("sender_id"),
                    rs.getString("sender_nickname"),
                    rs.getString("content"),
                    Message.MessageType.valueOf(rs.getString("message_type")),
                    rs.getLong("timestamp")
                );
                messages.add(message);
            }
        } catch (SQLException e) {
            System.err.println("Error getting recent messages: " + e.getMessage());
        }
        
        return messages;
    }
    
    public List<Message> getGroupMessagesInTimeRange(String groupId, long startTime, long endTime, int limit) {
        String sql = "SELECT * FROM `messages` WHERE group_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC LIMIT ?";
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);
            stmt.setInt(4, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message message = new Message(
                    rs.getString("id"),
                    rs.getString("group_id"),
                    rs.getString("sender_id"),
                    rs.getString("sender_nickname"),
                    rs.getString("content"),
                    Message.MessageType.valueOf(rs.getString("message_type")),
                    rs.getLong("timestamp")
                );
                messages.add(message);
            }
        } catch (SQLException e) {
            System.err.println("Error getting group messages in time range: " + e.getMessage());
        }
        
        return messages;
    }

    public List<Message> getGroupMessages(String groupId, int limit) {
        String sql = "SELECT * FROM `messages` WHERE group_id = ? ORDER BY timestamp DESC LIMIT ?";
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setInt(2, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message message = new Message(
                    rs.getString("id"),
                    rs.getString("group_id"),
                    rs.getString("sender_id"),
                    rs.getString("sender_nickname"),
                    rs.getString("content"),
                    Message.MessageType.valueOf(rs.getString("message_type")),
                    rs.getLong("timestamp")
                );
                messages.add(0, message); // 插入到开头，保持时间顺序
            }
        } catch (SQLException e) {
            System.err.println("Error getting group messages: " + e.getMessage());
        }
        
        return messages;
    }

    public List<Message> getGroupMessagesInTimeRange(String groupId, long startTime, long endTime) {
        String sql = "SELECT * FROM `messages` WHERE group_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC";
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message message = new Message(
                    rs.getString("id"),
                    rs.getString("group_id"),
                    rs.getString("sender_id"),
                    rs.getString("sender_nickname"),
                    rs.getString("content"),
                    Message.MessageType.valueOf(rs.getString("message_type")),
                    rs.getLong("timestamp")
                );
                messages.add(message);
            }
        } catch (SQLException e) {
            System.err.println("Error getting group messages in time range: " + e.getMessage());
        }
        
        return messages;
    }
    
    public List<Message> saveAndGetRecentMessage(Message message, int limit) {
        if (saveMessage(message)) {
            return getRecentMessages(message.getGroupId(), limit);
        }
        return new ArrayList<>();
    }
    
    public void saveBulkMessages(List<Message> messages) {
        String sql = "INSERT INTO `messages` (id, group_id, sender_id, sender_nickname, content, message_type, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            
            for (Message message : messages) {
                stmt.setString(1, message.getId());
                stmt.setString(2, message.getGroupId());
                stmt.setString(3, message.getSenderId());
                stmt.setString(4, message.getSenderNickname());
                stmt.setString(5, message.getContent());
                stmt.setString(6, message.getType().name());
                stmt.setLong(7, message.getTimestamp());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException rollbackException) {
                System.err.println("Error during rollback: " + rollbackException.getMessage());
            }
            System.err.println("Error saving bulk messages: " + e.getMessage());
        }
    }
    
    public boolean deleteMessage(String messageId) {
        String sql = "DELETE FROM `messages` WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, messageId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting message: " + e.getMessage());
            return false;
        }
    }
    
    public Message getMessageById(String messageId) {
        String sql = "SELECT * FROM `messages` WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, messageId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new Message(
                    rs.getString("id"),
                    rs.getString("group_id"),
                    rs.getString("sender_id"),
                    rs.getString("sender_nickname"),
                    rs.getString("content"),
                    Message.MessageType.valueOf(rs.getString("message_type")),
                    rs.getLong("timestamp")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting message by ID: " + e.getMessage());
        }
        
        return null;
    }
    
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}
