package top.c0r3.talk.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * GeminiBot启动器, 支持TLS
 */
public class GeminiBotLauncher {
    private static final Logger log = LoggerFactory.getLogger(GeminiBotLauncher.class);
    private static final String SERVER_URL = "wss://localhost:60033";
    private GeminiBot geminiBot;
    
    public boolean startBot() {
        try {
            URI serverUri = new URI(SERVER_URL);
            geminiBot = new GeminiBot(serverUri);
            return geminiBot.start();
        } catch (Exception e) {
            log.error("启动GeminiBot失败: {}", e.getMessage());
            return false;
        }
    }
    
    public void stopBot() {
        if (geminiBot != null) {
            geminiBot.stop();
            geminiBot = null;
        }
    }
    
    public static void main(String[] args) {
        GeminiBotLauncher launcher = new GeminiBotLauncher();
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭GeminiBot...");
            launcher.stopBot();
        }));
        
        // 启动Bot
        if (launcher.startBot()) {
            log.info("GeminiBot启动成功!");
            
            // 保持主线程运行
            try {
                while (true) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                log.info("主线程被中断，正在关闭...");
                launcher.stopBot();
            }
        } else {
            log.error("GeminiBot启动失败");
        }
    }
}
