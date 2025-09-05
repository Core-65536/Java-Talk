package top.c0r3.talk.config;

/**
 * GeminiBot配置类
 * 管理Gemini API相关配置
 */
public class GeminiBotConfig {
    // Gemini API密钥，从环境变量读取
    private String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");
    
    // Bot用户信息
    public static final String BOT_NICKNAME = "GeminiBot";
    public static final String BOT_PASSWORD = "Bot.Login.Gemini";
    
    // Bot群组信息
    public static final String BOT_GROUP_NAME = "GeminiAIChat";
    public static final String BOT_GROUP_PASSWORD = "";
    
    // 服务器连接信息
    public static final String SERVER_HOST = "localhost";
    public static final int SERVER_PORT = 60033;
    
    // Bot行为配置
    public static final String TRIGGER_PREFIX = "@Gemini";
    public static final long RESPONSE_DELAY_MS = 500; // 回复延迟500毫秒，避免过快回复

    // 代理配置（用于国内访问Google API）
    public static final boolean USE_PROXY = true; // 是否使用代理，默认关闭    
    public static final String PROXY_HOST = "127.0.0.1";
    public static final int PROXY_PORT = 10809; // 常见的代理端口，可根据实际情况修改
    
    // 常见代理端口说明：
    // Clash: 7890 (HTTP), 7891 (SOCKS)
    // V2rayN: 10809 (HTTP), 10808 (SOCKS) 
    // Shadowsocks: 1080 (SOCKS), 1087 (HTTP)
    // 请根据你的代理软件设置正确的端口
    
    /**
     * 获取Gemini API密钥
     * @return API密钥
     */
    public String getGeminiApiKey() {
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.trim().isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY环境变量未设置。请设置环境变量: GEMINI_API_KEY=your_api_key");
        }
        return GEMINI_API_KEY;
    }
    
    /**
     * 检查是否消息需要Bot响应
     * @param message 消息内容
     * @return 是否需要响应
     */
    public static boolean shouldRespond(String message) {
        return message != null && message.contains(TRIGGER_PREFIX);
    }
    
    /**
     * 提取用户问题（移除@Gemini标记）
     * @param message 原始消息
     * @return 清理后的问题
     */
    public static String extractQuestion(String message) {
        if (message == null) return "";
        
        return message.replace(TRIGGER_PREFIX, "").trim();
    }
    
    /**
     * 验证配置是否正确
     * @return 配置验证结果
     */
    public boolean validateConfig() {
        try {
            getGeminiApiKey();
            return true;
        } catch (Exception e) {
            System.err.println("GeminiBot配置验证失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取代理配置信息
     * @return 代理配置描述
     */
    public static String getProxyInfo() {
        if (USE_PROXY) {
            return "使用代理: " + PROXY_HOST + ":" + PROXY_PORT;
        } else {
            return "直连模式";
        }
    }
}
