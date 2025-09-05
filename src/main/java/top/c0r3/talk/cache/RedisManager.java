package top.c0r3.talk.cache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import top.c0r3.talk.config.AppConfig;
import top.c0r3.talk.model.Message;
import top.c0r3.talk.model.User;
import top.c0r3.talk.model.Group;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RedisManager {
    private static RedisManager instance;
    private JedisPool jedisPool;
    private Gson gson;
    private static int CACHE_TTL = 7 * 24 * 60 * 60; // 7天过期，可配置
    
    private RedisManager() {
        AppConfig config = AppConfig.getInstance();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(2);
        
        // 使用配置文件中的Redis连接信息
        String password = config.getRedisPassword();
        if (password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, config.getRedisHost(), config.getRedisPort(), 2000, password);
        } else {
            jedisPool = new JedisPool(poolConfig, config.getRedisHost(), config.getRedisPort());
        }
        
        gson = new Gson();
        CACHE_TTL = config.getCacheTTL();
        
        // 测试Redis连接
        testRedisConnection();
    }
    
    private void testRedisConnection() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            // Redis连接成功
        } catch (Exception e) {
            System.err.println("Redis connection failed: " + e.getMessage());
            System.err.println("Redis features will be disabled");
        }
    }
    
    public static RedisManager getInstance() {
        if (instance == null) {
            synchronized (RedisManager.class) {
                if (instance == null) {
                    instance = new RedisManager();
                }
            }
        }
        return instance;
    }
    
    public void cacheMessage(String groupId, Message message) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "group_messages:" + groupId;
            String messageJson = gson.toJson(message);
            
            // 使用列表存储消息，右侧推入
            jedis.rpush(key, messageJson);
            
            // 设置过期时间
            jedis.expire(key, CACHE_TTL);
            
            // 限制列表长度，只保留最近500条消息
            jedis.ltrim(key, -500, -1);
        } catch (Exception e) {
            System.err.println("Error caching message (Redis may be unavailable): " + e.getMessage());
            // Redis不可用时不影响正常功能
        }
    }
    
    public List<Message> getCachedMessages(String groupId, int limit) {
        List<Message> messages = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "group_messages:" + groupId;
            
            // 从右侧开始取最新的消息
            List<String> messageJsons = jedis.lrange(key, -limit, -1);
            
            Type messageType = new TypeToken<Message>(){}.getType();
            for (String messageJson : messageJsons) {
                if (messageJson != null && !messageJson.trim().isEmpty()) {
                    try {
                        Message message = gson.fromJson(messageJson, messageType);
                        if (message != null) {
                            messages.add(message);
                        }
                    } catch (Exception e) {
                        // 忽略JSON解析错误
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting cached messages (Redis may be unavailable): " + e.getMessage());
            // Redis不可用时返回空列表，不影响正常功能
        }
        return messages;
    }
    
    public List<Message> getAllCachedMessages(String groupId) {
        List<Message> messages = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "group_messages:" + groupId;
            List<String> messageJsons = jedis.lrange(key, 0, -1);
            
            Type messageType = new TypeToken<Message>(){}.getType();
            for (String messageJson : messageJsons) {
                if (messageJson != null && !messageJson.trim().isEmpty()) {
                    try {
                        Message message = gson.fromJson(messageJson, messageType);
                        if (message != null) {
                            messages.add(message);
                        } else {
                            System.err.println("Redis: parsed message is null for JSON: " + messageJson);
                        }
                    } catch (Exception e) {
                        // 忽略JSON解析错误
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting all cached messages (Redis may be unavailable): " + e.getMessage());
            // Redis不可用时返回空列表，不影响正常功能
        }
        return messages;
    }
    
    public void cacheUserSession(String userId, String sessionId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "user_session:" + userId;
            jedis.setex(key, CACHE_TTL, sessionId);
        } catch (Exception e) {
            System.err.println("Error caching user session: " + e.getMessage());
        }
    }
    
    public String getUserSession(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "user_session:" + userId;
            return jedis.get(key);
        } catch (Exception e) {
            System.err.println("Error getting user session: " + e.getMessage());
            return null;
        }
    }
    
    public void removeUserSession(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "user_session:" + userId;
            jedis.del(key);
        } catch (Exception e) {
            System.err.println("Error removing user session: " + e.getMessage());
        }
    }
    
    public void cacheOnlineUsers(String groupId, List<String> userIds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "group_online:" + groupId;
            jedis.del(key); // 清除旧数据
            if (!userIds.isEmpty()) {
                jedis.sadd(key, userIds.toArray(new String[0]));
                jedis.expire(key, 300); // 5分钟过期
            }
        } catch (Exception e) {
            System.err.println("Error caching online users: " + e.getMessage());
        }
    }
    
    public void addOnlineUser(String groupId, String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "group_online:" + groupId;
            jedis.sadd(key, userId);
            jedis.expire(key, 300);
        } catch (Exception e) {
            System.err.println("Error adding online user: " + e.getMessage());
        }
    }
    
    public void removeOnlineUser(String groupId, String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "group_online:" + groupId;
            jedis.srem(key, userId);
        } catch (Exception e) {
            System.err.println("Error removing online user: " + e.getMessage());
        }
    }
    
    public List<String> getOnlineUsers(String groupId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "group_online:" + groupId;
            return new ArrayList<>(jedis.smembers(key));
        } catch (Exception e) {
            System.err.println("Error getting online users: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public void clearGroupCache(String groupId) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 清理群组相关的所有缓存
            jedis.del("group:" + groupId);
            jedis.del("group_pwd:" + groupId);
            jedis.del("members:" + groupId);
            jedis.del("group_messages:" + groupId);
            jedis.del("group_online:" + groupId);
        } catch (Exception e) {
            System.err.println("Error clearing group cache: " + e.getMessage());
        }
    }
    
    // ===== 高频查询缓存策略 =====
    
    // 1. 用户认证信息缓存（登录时高频查询）
    public void cacheUserAuth(String nickname, User user) {
        try (Jedis jedis = jedisPool.getResource()) {
            String userJson = gson.toJson(user);
            // 缓存用户认证信息，用于快速登录验证
            jedis.setex("auth:" + nickname, 3600, userJson); // 1小时过期
            jedis.setex("user:" + user.getId(), 1800, userJson); // 30分钟过期
        } catch (Exception e) {
            System.err.println("Error caching user auth: " + e.getMessage());
        }
    }
    
    public User getCachedUserAuth(String nickname) {
        try (Jedis jedis = jedisPool.getResource()) {
            String userJson = jedis.get("auth:" + nickname);
            if (userJson != null && !userJson.trim().isEmpty()) {
                return gson.fromJson(userJson, User.class);
            }
        } catch (Exception e) {
            System.err.println("Error getting cached user auth: " + e.getMessage());
        }
        return null;
    }
    
    public User getCachedUser(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String userJson = jedis.get("user:" + userId);
            if (userJson != null && !userJson.trim().isEmpty()) {
                return gson.fromJson(userJson, User.class);
            }
        } catch (Exception e) {
            System.err.println("Error getting cached user: " + e.getMessage());
        }
        return null;
    }
    
    // 2. 群组基本信息缓存（加入群组时高频查询）
    public void cacheGroupInfo(Group group) {
        try (Jedis jedis = jedisPool.getResource()) {
            String groupJson = gson.toJson(group);
            jedis.setex("group:" + group.getId(), 1800, groupJson); // 30分钟过期
            
            // 缓存群组密码哈希，用于快速验证
            if (group.getPasswdhash() != null && !group.getPasswdhash().isEmpty()) {
                jedis.setex("group_pwd:" + group.getId(), 3600, group.getPasswdhash());
            }
        } catch (Exception e) {
            System.err.println("Error caching group info: " + e.getMessage());
        }
    }
    
    public Group getCachedGroup(String groupId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String groupJson = jedis.get("group:" + groupId);
            if (groupJson != null && !groupJson.trim().isEmpty()) {
                return gson.fromJson(groupJson, Group.class);
            }
        } catch (Exception e) {
            System.err.println("Error getting cached group: " + e.getMessage());
        }
        return null;
    }
    
    public String getCachedGroupPassword(String groupId) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get("group_pwd:" + groupId);
        } catch (Exception e) {
            System.err.println("Error getting cached group password: " + e.getMessage());
            return null;
        }
    }
    
    // 3. 群组成员缓存（权限检查时高频查询）
    public void cacheGroupMembers(String groupId, List<String> memberIds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "members:" + groupId;
            jedis.del(key);
            if (!memberIds.isEmpty()) {
                jedis.sadd(key, memberIds.toArray(new String[0]));
                jedis.expire(key, 1800); // 30分钟过期
            }
        } catch (Exception e) {
            System.err.println("Error caching group members: " + e.getMessage());
        }
    }
    
    public boolean isMemberOfGroup(String groupId, String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember("members:" + groupId, userId);
        } catch (Exception e) {
            System.err.println("Error checking group membership: " + e.getMessage());
            return false; // 缓存失败时返回false，强制查数据库
        }
    }
    
    public void addGroupMember(String groupId, String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("members:" + groupId, userId);
            jedis.expire("members:" + groupId, 1800);
        } catch (Exception e) {
            System.err.println("Error adding group member to cache: " + e.getMessage());
        }
    }
    
    public void removeGroupMember(String groupId, String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.srem("members:" + groupId, userId);
        } catch (Exception e) {
            System.err.println("Error removing group member from cache: " + e.getMessage());
        }
    }

    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
