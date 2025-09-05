package top.c0r3.talk.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static AppConfig instance;
    private Properties properties;
    
    private AppConfig() {
        properties = new Properties();
        loadProperties();
    }
    
    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }
    
    private void loadProperties() {
        setDefaultProperties();
        loadFromFile();
    }
    
    private void setDefaultProperties() {
        properties.setProperty("db.url", "jdbc:mysql://localhost:3306/javatalk?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        
        // 数据库用户名和密码优先从环境变量读取
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbUsername != null && !dbUsername.trim().isEmpty()) {
            properties.setProperty("db.username", dbUsername);
        }
        if (dbPassword != null && !dbPassword.trim().isEmpty()) {
            properties.setProperty("db.password", dbPassword);
        }

        properties.setProperty("redis.host", "localhost");
        properties.setProperty("redis.port", "6379");
        properties.setProperty("redis.password", "");
        
        properties.setProperty("server.port", "60033");
        properties.setProperty("server.host", "0.0.0.0");
        properties.setProperty("server.ssl.enabled", "true");
        
        // SSL配置 - 默认使用PKCS12格式
        properties.setProperty("ssl.keystore.path", "keystore.p12");
        properties.setProperty("ssl.keystore.password", "changeit");
        properties.setProperty("ssl.key.password", "changeit");
        
        properties.setProperty("cache.ttl.message", "604800");
        properties.setProperty("cache.message.limit", "500");
        
        // Gemini API配置
        String geminiKey = System.getenv("GEMINI_API_KEY");
        if (geminiKey != null) {
            properties.setProperty("gemini.api.key", geminiKey);
        } else {
            properties.setProperty("gemini.api.key", "");
        }
    }
    
    private void loadFromFile() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream != null) {
                Properties fileProps = new Properties();
                fileProps.load(inputStream);
                // 覆盖默认配置，但不覆盖空值
                for (String key : fileProps.stringPropertyNames()) {
                    String value = fileProps.getProperty(key);
                    // 只覆盖非空值，避免环境变量被空字符串覆盖
                    if (value != null && !value.trim().isEmpty()) {
                        properties.setProperty(key, value);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("无法加载application.properties文件: " + e.getMessage());
        }
    }
    
    public String getDatabaseUrl() {
        return properties.getProperty("db.url");
    }
    
    public String getDatabaseUsername() {
        return properties.getProperty("db.username");
    }
    
    public String getDatabasePassword() {
        return properties.getProperty("db.password");
    }
    
    public String getRedisHost() {
        return properties.getProperty("redis.host");
    }
    
    public int getRedisPort() {
        return Integer.parseInt(properties.getProperty("redis.port", "6379"));
    }
    
    public String getRedisPassword() {
        return properties.getProperty("redis.password", "");
    }
    
    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "60033"));
    }
    
    public String getServerHost() {
        return properties.getProperty("server.host", "0.0.0.0");
    }
    
    public int getCacheTTL() {
        return Integer.parseInt(properties.getProperty("cache.ttl.message", "604800"));
    }
    
    public int getCacheMessageLimit() {
        return Integer.parseInt(properties.getProperty("cache.message.limit", "500"));
    }
    
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public String getGeminiApiKey() {
        return properties.getProperty("gemini.api.key", "");
    }
    
    public boolean isSSLEnabled() {
        return Boolean.parseBoolean(properties.getProperty("server.ssl.enabled", "true"));
    }
    
    public String getSSLKeystorePath() {
        return properties.getProperty("ssl.keystore.path", "keystore.jks");
    }
    
    public String getSSLKeystorePassword() {
        return properties.getProperty("ssl.keystore.password", "changeit");
    }
    
    public String getSSLKeyPassword() {
        return properties.getProperty("ssl.key.password", "changeit");
    }
}
