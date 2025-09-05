package top.c0r3.talk.database;

import top.c0r3.talk.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {
    private static DatabaseConfig instance;
    private Connection connection;
    private AppConfig config;
    
    private DatabaseConfig() throws SQLException {
        config = AppConfig.getInstance();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(
                config.getDatabaseUrl(), 
                config.getDatabaseUsername(), 
                config.getDatabasePassword()
            );
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
    }
    
    public static DatabaseConfig getInstance() throws SQLException {
        if (instance == null || instance.getConnection().isClosed()) {
            instance = new DatabaseConfig();
        }
        return instance;
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    public void initDatabase() throws SQLException {
        // 创建用户表
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS `users` (
                id VARCHAR(50) PRIMARY KEY,
                nickname VARCHAR(100) NOT NULL UNIQUE,
                passwdhash VARCHAR(255) NOT NULL,
                last_login BIGINT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_nickname (nickname),
                INDEX idx_last_login (last_login)
            )
        """;
        
        // 创建群聊表 (改名避免关键字冲突)
        String createGroupsTable = """
            CREATE TABLE IF NOT EXISTS `chat_groups` (
                id VARCHAR(50) PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                passwdhash VARCHAR(255),
                owner_id VARCHAR(50) NOT NULL,
                create_time BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (owner_id) REFERENCES `users`(id) ON DELETE CASCADE,
                INDEX idx_owner (owner_id),
                INDEX idx_create_time (create_time)
            )
        """;
        
        // 创建群成员表
        String createGroupMembersTable = """
            CREATE TABLE IF NOT EXISTS `group_members` (
                group_id VARCHAR(50),
                user_id VARCHAR(50),
                joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (group_id, user_id),
                FOREIGN KEY (group_id) REFERENCES `chat_groups`(id) ON DELETE CASCADE,
                FOREIGN KEY (user_id) REFERENCES `users`(id) ON DELETE CASCADE,
                INDEX idx_user_groups (user_id),
                INDEX idx_group_members (group_id)
            )
        """;
        
        // 创建消息表
        String createMessagesTable = """
            CREATE TABLE IF NOT EXISTS `messages` (
                id VARCHAR(50) PRIMARY KEY,
                group_id VARCHAR(50) NOT NULL,
                sender_id VARCHAR(50),
                sender_nickname VARCHAR(100) NOT NULL,
                content TEXT NOT NULL,
                message_type VARCHAR(20) DEFAULT 'TEXT',
                timestamp BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (group_id) REFERENCES `chat_groups`(id) ON DELETE CASCADE,
                FOREIGN KEY (sender_id) REFERENCES `users`(id) ON DELETE SET NULL,
                INDEX idx_group_time (group_id, timestamp),
                INDEX idx_sender (sender_id),
                INDEX idx_timestamp (timestamp)
            )
        """;
        
        connection.createStatement().execute(createUsersTable);
        connection.createStatement().execute(createGroupsTable);
        connection.createStatement().execute(createGroupMembersTable);
        connection.createStatement().execute(createMessagesTable);
        
        System.out.println("Database tables initialized successfully");
    }
}
