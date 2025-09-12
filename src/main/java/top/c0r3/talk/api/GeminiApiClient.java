package top.c0r3.talk.api;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ThinkingConfig;


// import top.c0r3.talk.config.GeminiBotConfig;

// import java.net.InetSocketAddress;
// import java.net.Proxy;

/**
 * Gemini API客户端
 * 用于调用Google Gemini API生成回复
 */
public class GeminiApiClient {
    private static final String GEMINI_MODEL_STRING = "gemini-2.5-flash";
    
    // System Prompt - AI助手的人格设定
    private static final String SYSTEM_PROMPT = "尽量用控制台可以显示的字符回复我。";


    private ThinkingConfig disabledThinkingConfig = ThinkingConfig.builder()
            .thinkingBudget(0)
            .build();

    private  GenerateContentConfig finalConfig = GenerateContentConfig.builder()
            .thinkingConfig(disabledThinkingConfig)
            .build();

    // private String Proxy_Host = GeminiBotConfig.PROXY_HOST;
    // private int Proxy_Port = GeminiBotConfig.PROXY_PORT;
    // private Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Proxy_Host, Proxy_Port));



    /**
     * 调用Gemini API生成回复
     * @param prompt 用户输入的提示
     * @return AI生成的回复
     */
    public String generateResponse(String prompt) {
        try {
            // 将System Prompt和用户问题组合
            String fullPrompt = SYSTEM_PROMPT + "\n\nUser Prompt: " + prompt;
            Client client = new Client();

            GenerateContentResponse response = client.models.generateContent(
                GEMINI_MODEL_STRING,
                fullPrompt,
                finalConfig
            );
            client.close();
            return response.text();
            // Close the client to free resources

        } catch (Exception e) {
            System.err.println("调用Gemini API失败: " + e.getMessage());
            return "抱歉,处理您的消息时出现了问题。请稍后再试。";
        }
    }

    /**
     * 解析API响应
     * @param responseBody 响应体JSON字符串
     * @return 提取的文本内容
     */
    // private String parseResponse(String responseBody) {
    //     try {
    //         JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
            
    //         if (response.has("candidates")) {
    //             JsonArray candidates = response.getAsJsonArray("candidates");
    //             if (candidates.size() > 0) {
    //                 JsonObject candidate = candidates.get(0).getAsJsonObject();
    //                 if (candidate.has("content")) {
    //                     JsonObject content = candidate.getAsJsonObject("content");
    //                     if (content.has("parts")) {
    //                         JsonArray parts = content.getAsJsonArray("parts");
    //                         if (parts.size() > 0) {
    //                             JsonObject part = parts.get(0).getAsJsonObject();
    //                             if (part.has("text")) {
    //                                 return part.get("text").getAsString().trim();
    //                             }
    //                         }
    //                     }
    //                 }
    //             }
    //         }
            
    //         // 如果无法解析出文本内容,返回错误信息
    //         return "抱歉,我现在无法理解您的消息。请稍后再试。";
            
    //     } catch (Exception e) {
    //         System.err.println("解析Gemini API响应失败: " + e.getMessage());
    //         return "抱歉,处理您的消息时出现了问题。";
    //     }
    // }

    /**
     * 测试API连接
     * @return 是否连接成功
     */
    public boolean testConnection() {
        try {
            String response = generateResponse("Hello");
            return response != null && !response.isEmpty() && !response.contains("抱歉");
        } catch (Exception e) {
            System.err.println("Gemini API连接测试失败: " + e.getMessage());
            return false;
        }
    }
}

