package top.c0r3.talk.database;

import top.c0r3.talk.model.User;
import top.c0r3.talk.cache.RedisManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {
    private Connection connection;
    private RedisManager redisManager;
    
    public UserDAO() throws SQLException {
        this.connection = DatabaseConfig.getInstance().getConnection();
        this.redisManager = RedisManager.getInstance();
    }
    
    public boolean createUser(User user) {
        String sql = "INSERT INTO `users` (uuid, nickname, passwdhash, last_login) VALUES (?, ?, ?, ?)";
        
        try {
            // 开始事务
            connection.setAutoCommit(false);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, user.getId());
                stmt.setString(2, user.getNickname());
                stmt.setString(3, user.getPasswdhash());
                stmt.setLong(4, user.getLastLogin());
                
                int result = stmt.executeUpdate();
                if (result > 0) {
                    // 获取生成的数据库ID
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        user.setDbId(generatedKeys.getLong(1));
                    }
                    
                    // 提交数据库事务
                    connection.commit();
                    connection.setAutoCommit(true);
                    
                    // 数据库操作成功后，缓存用户认证信息（用于快速登录）
                    try {
                        redisManager.cacheUserAuth(user.getNickname(), user);
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to cache user auth to Redis: " + e.getMessage());
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
            System.err.println("Error creating user: " + e.getMessage());
        }
        return false;
    }
    
    public User getUserById(String id) {
        // 先尝试从Redis缓存获取（消息发送时高频查询）
        User cachedUser = redisManager.getCachedUser(id);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        // 缓存未命中，从数据库查询
        String sql = "SELECT * FROM `users` WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User user = mapResultSetToUser(rs);
                // 缓存用户信息
                redisManager.cacheUserAuth(user.getNickname(), user);
                return user;
            }
        } catch (SQLException e) {
            System.err.println("Error getting user: " + e.getMessage());
        }
        return null;
    }
    
    public User getUserByDbId(Long dbId) {
        String sql = "SELECT * FROM `users` WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, dbId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting user by dbId: " + e.getMessage());
        }
        return null;
    }
    
    public User getUserByNickname(String nickname) {
        // 先尝试从Redis缓存获取（登录时高频查询）
        User cachedUser = redisManager.getCachedUserAuth(nickname);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        // 缓存未命中，从数据库查询
        String sql = "SELECT * FROM `users` WHERE nickname = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User user = mapResultSetToUser(rs);
                // 缓存认证信息
                redisManager.cacheUserAuth(nickname, user);
                return user;
            }
        } catch (SQLException e) {
            System.err.println("Error getting user by nickname: " + e.getMessage());
        }
        return null;
    }
    
    public boolean updateLastLogin(String userId) {
        String sql = "UPDATE `users` SET last_login = ? WHERE uuid = ?";
        
        try {
            // 开始事务
            connection.setAutoCommit(false);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                long currentTime = System.currentTimeMillis();
                stmt.setLong(1, currentTime);
                stmt.setString(2, userId);
                
                if (stmt.executeUpdate() > 0) {
                    // 提交数据库事务
                    connection.commit();
                    connection.setAutoCommit(true);
                    
                    // 数据库更新成功后，更新Redis缓存中的用户信息（如果存在）
                    try {
                        User cachedUser = redisManager.getCachedUser(userId);
                        if (cachedUser != null) {
                            cachedUser.setLastLogin(currentTime);
                            redisManager.cacheUserAuth(cachedUser.getNickname(), cachedUser);
                        }
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to update cached user info in Redis: " + e.getMessage());
                        // Redis缓存更新失败不影响数据库操作的成功
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
            System.err.println("Error updating last login: " + e.getMessage());
        }
        return false;
    }
    
    public boolean updateLastLoginByDbId(Long dbId) {
        String sql = "UPDATE `users` SET last_login = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setLong(2, dbId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating last login by dbId: " + e.getMessage());
            return false;
        }
    }
    
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM `users` ORDER BY id";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all users: " + e.getMessage());
        }
        return users;
    }
    
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setDbId(rs.getLong("id"));
        user.setId(rs.getString("uuid"));
        user.setNickname(rs.getString("nickname"));
        user.setPasswdhash(rs.getString("passwdhash"));
        user.setLastLogin(rs.getLong("last_login"));
        return user;
    }
}
