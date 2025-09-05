package top.c0r3.talk.database;

import top.c0r3.talk.model.Group;
import top.c0r3.talk.model.User;
import top.c0r3.talk.cache.RedisManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {
    private Connection connection;
    private RedisManager redisManager;
    
    public GroupDAO() throws SQLException {
        this.connection = DatabaseConfig.getInstance().getConnection();
        this.redisManager = RedisManager.getInstance();
    }
    
    public boolean createGroup(Group group) {
        if(group.getName().contains(" ")){
            return false; // 群聊名称不能包含空格
        }
        // 使用事务确保数据一致性
        try {
            connection.setAutoCommit(false); // 开始事务
            
            // 1. 首先检查群聊名称是否已存在
            String checkSql = "SELECT COUNT(*) FROM `chat_groups` WHERE name = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setString(1, group.getName());
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    connection.rollback();
                    System.err.println("Group name already exists: " + group.getName());
                    return false; // 群聊名称已存在
                }
            }
            
            // 2. 创建群组
            String sql = "INSERT INTO `chat_groups` (uuid, name, passwdhash, owner_id, create_time) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, group.getId());
                stmt.setString(2, group.getName());
                stmt.setString(3, group.getPasswdhash());
                
                // 需要先根据owner UUID获取数据库ID
                UserDAO userDAO = new UserDAO();
                User owner = userDAO.getUserById(group.getOwnerId());
                if (owner == null) {
                    connection.rollback();
                    return false;
                }
                
                stmt.setLong(4, owner.getDbId());
                stmt.setLong(5, group.getCreateTime());
                
                int result = stmt.executeUpdate();
                if (result <= 0) {
                    connection.rollback();
                    return false;
                }
                
                // 获取生成的数据库ID
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    group.setDbId(generatedKeys.getLong(1));
                    group.setOwnerDbId(owner.getDbId());
                } else {
                    connection.rollback();
                    return false;
                }
            }
            
            // 2. 添加群主到群成员表（在同一事务中）
            String memberSql = "INSERT INTO `group_members` (group_id, user_id) VALUES (?, ?)";
            try (PreparedStatement memberStmt = connection.prepareStatement(memberSql)) {
                memberStmt.setLong(1, group.getDbId());
                memberStmt.setLong(2, group.getOwnerDbId());
                int memberResult = memberStmt.executeUpdate();
                if (memberResult <= 0) {
                    connection.rollback();
                    return false;
                }
            }
            
            // 3. 提交事务
            connection.commit();
            
            // 4. 只有数据库操作成功后才缓存到Redis
            try {
                redisManager.cacheGroupInfo(group);
            } catch (Exception e) {
                // Redis缓存失败不应该影响数据库操作的成功
                System.err.println("Warning: Failed to cache group info to Redis: " + e.getMessage());
            }
            
            return true;
            
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
            }
            System.err.println("Error creating group: " + e.getMessage());
            return false;
        } finally {
            try {
                connection.setAutoCommit(true); // 恢复自动提交
            } catch (SQLException e) {
                System.err.println("Error restoring auto commit: " + e.getMessage());
            }
        }
    }
    
    public Group getGroupById(String id) {
        // 先尝试从Redis缓存获取（加入群组时高频查询）
        Group cachedGroup = redisManager.getCachedGroup(id);
        if (cachedGroup != null) {
            return cachedGroup;
        }
        
        // 缓存未命中，从数据库查询
        String sql = "SELECT * FROM `chat_groups` WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Group group = mapResultSetToGroup(rs);
                // 缓存群组信息
                redisManager.cacheGroupInfo(group);
                return group;
            }
        } catch (SQLException e) {
            System.err.println("Error getting group: " + e.getMessage());
        }
        return null;
    }
    
    public Group getGroupByDbId(Long dbId) {
        String sql = "SELECT * FROM `chat_groups` WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, dbId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToGroup(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting group by dbId: " + e.getMessage());
        }
        return null;
    }
    
    public Group getGroupByName(String name) {
        // 从数据库查询群聊名称
        String sql = "SELECT * FROM `chat_groups` WHERE name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Group group = mapResultSetToGroup(rs);
                // 缓存群组信息
                redisManager.cacheGroupInfo(group);
                return group;
            }
        } catch (SQLException e) {
            System.err.println("Error getting group by name: " + e.getMessage());
        }
        return null;
    }
    
    public List<Group> getAllGroups() {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT * FROM `chat_groups` ORDER BY id";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                groups.add(mapResultSetToGroup(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all groups: " + e.getMessage());
        }
        return groups;
    }
    
    public boolean deleteGroup(String groupId, String userId) {
        try {
            // 获取群组信息
            Group group = getGroupById(groupId);
            if (group == null) {
                return false;
            }
            
            // 只有群主可以删除群聊
            if (!group.getOwnerId().equals(userId)) {
                return false;
            }
            
            // 使用事务确保数据一致性
            connection.setAutoCommit(false);
            
            // 1. 先删除群成员
            String deleteMembersSql = "DELETE FROM `group_members` WHERE group_id = ?";
            try (PreparedStatement deleteMembersStmt = connection.prepareStatement(deleteMembersSql)) {
                deleteMembersStmt.setLong(1, group.getDbId());
                deleteMembersStmt.executeUpdate();
            }
            
            // 2. 删除群聊
            String deleteGroupSql = "DELETE FROM `chat_groups` WHERE id = ?";
            try (PreparedStatement deleteStmt = connection.prepareStatement(deleteGroupSql)) {
                deleteStmt.setLong(1, group.getDbId());
                int result = deleteStmt.executeUpdate();
                
                if (result <= 0) {
                    connection.rollback();
                    return false;
                }
            }
            
            // 3. 提交事务
            connection.commit();
            
            // 4. 只有数据库操作成功后才清理Redis缓存
            try {
                redisManager.clearGroupCache(groupId);
            } catch (Exception e) {
                System.err.println("Warning: Failed to clear group cache from Redis: " + e.getMessage());
            }
            
            return true;
            
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
            }
            System.err.println("Error deleting group: " + e.getMessage());
            return false;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Error restoring auto commit: " + e.getMessage());
            }
        }
    }
    
    public boolean addMemberToGroup(String groupId, String userId) {
        try {
            // 获取group和user的数据库ID
            Group group = getGroupById(groupId);
            
            UserDAO userDAO = new UserDAO();
            User user = userDAO.getUserById(userId);
            
            if (group == null || user == null) {
                return false;
            }
            
            // 检查用户是否已经在群组中
            String checkSql = "SELECT COUNT(*) FROM `group_members` WHERE group_id = ? AND user_id = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setLong(1, group.getDbId());
                checkStmt.setLong(2, user.getDbId());
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    return true; // 用户已经在群组中，返回true
                }
            }
            
            String sql = "INSERT INTO `group_members` (group_id, user_id) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, group.getDbId());
                stmt.setLong(2, user.getDbId());
                int result = stmt.executeUpdate();
                
                // 只有数据库操作成功后才更新Redis缓存
                if (result > 0) {
                    try {
                        redisManager.addGroupMember(groupId, userId);
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to update group member cache in Redis: " + e.getMessage());
                    }
                }
                
                return result > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error adding member to group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean addMemberToGroupByName(String groupName, String userId) {
        try {
            // 通过群聊名称获取群聊
            Group group = getGroupByName(groupName);
            
            UserDAO userDAO = new UserDAO();
            User user = userDAO.getUserById(userId);
            
            if (group == null || user == null) {
                return false;
            }
            
            // 检查用户是否已经在群组中
            String checkSql = "SELECT COUNT(*) FROM `group_members` WHERE group_id = ? AND user_id = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setLong(1, group.getDbId());
                checkStmt.setLong(2, user.getDbId());
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    return true; // 用户已经在群组中，返回true
                }
            }
            
            String sql = "INSERT INTO `group_members` (group_id, user_id) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, group.getDbId());
                stmt.setLong(2, user.getDbId());
                int result = stmt.executeUpdate();
                
                // 只有数据库操作成功后才更新Redis缓存
                if (result > 0) {
                    try {
                        redisManager.addGroupMember(group.getId(), userId);
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to update group member cache in Redis: " + e.getMessage());
                    }
                }
                
                return result > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error adding member to group by name: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean removeMemberFromGroup(String groupId, String userId) {
        try {
            // 获取group和user的数据库ID
            Group group = getGroupById(groupId);
            UserDAO userDAO = new UserDAO();
            User user = userDAO.getUserById(userId);
            
            if (group == null || user == null) {
                return false;
            }
            
            String sql = "DELETE FROM `group_members` WHERE group_id = ? AND user_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, group.getDbId());
                stmt.setLong(2, user.getDbId());
                int result = stmt.executeUpdate();
                
                // 只有数据库操作成功后才更新Redis缓存
                if (result > 0) {
                    try {
                        redisManager.removeGroupMember(groupId, userId);
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to update group member cache in Redis: " + e.getMessage());
                    }
                }
                
                return result > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error removing member from group: " + e.getMessage());
            return false;
        }
    }
    
    public List<String> getGroupMembers(String groupId) {
        List<String> members = new ArrayList<>();
        String sql = """
            SELECT u.uuid FROM `users` u 
            JOIN `group_members` gm ON u.id = gm.user_id 
            JOIN `chat_groups` g ON g.id = gm.group_id
            WHERE g.uuid = ?
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                members.add(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting group members: " + e.getMessage());
        }
        return members;
    }
    
    public List<Group> getUserGroups(String userId) {
        List<Group> groups = new ArrayList<>();
        try {
            UserDAO userDAO = new UserDAO();
            User user = userDAO.getUserById(userId);
            if (user == null) return groups;
            
            String sql = """
                SELECT g.* FROM `chat_groups` g 
                JOIN `group_members` gm ON g.id = gm.group_id 
                WHERE gm.user_id = ?
                ORDER BY g.id
            """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, user.getDbId());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    groups.add(mapResultSetToGroup(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user groups: " + e.getMessage());
        }
        return groups;
    }
    
    private Group mapResultSetToGroup(ResultSet rs) throws SQLException {
        Group group = new Group();
        group.setDbId(rs.getLong("id"));
        group.setId(rs.getString("uuid"));
        group.setName(rs.getString("name"));
        group.setPasswdhash(rs.getString("passwdhash"));
        
        // 从owner_id (数据库ID) 获取owner的UUID
        UserDAO userDAO = new UserDAO();
        User owner = userDAO.getUserByDbId(rs.getLong("owner_id"));
        if (owner != null) {
            group.setOwnerId(owner.getId());
            group.setOwnerDbId(owner.getDbId());
        }
        
        group.setCreateTime(rs.getLong("create_time"));
        
        // 加载群成员
        group.setMemberIds(getGroupMembers(group.getId()));
        return group;
    }
}
