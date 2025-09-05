package top.c0r3.talk.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class GroupCommand {
    private static Map<String, CommandHandler> commandHandlers = new HashMap<>();
    private static GroupClient client;
    
    // 命令处理接口
    @FunctionalInterface
    public interface CommandHandler {
        void handle(String[] args);
    }
    
    static {
        initializeCommands();
    }
    
    public static void setClient(GroupClient groupClient) {
        client = groupClient;
    }
    
    private static void initializeCommands() {
        // 基础命令
        commandHandlers.put("help", args -> showHelp());
        commandHandlers.put("exit", args -> exit());
        commandHandlers.put("quit", args -> exit());
        commandHandlers.put("clear", args -> clearScreen());
        
        // 认证命令
        commandHandlers.put("login", args -> handleLogin(args));
        commandHandlers.put("register", args -> handleRegister(args));
        commandHandlers.put("logout", args -> handleLogout());
        
        // 群聊管理命令
        commandHandlers.put("create", args -> handleCreateGroup(args));
        commandHandlers.put("join", args -> handleJoinGroup(args));
        commandHandlers.put("leave", args -> handleLeaveGroup());
        commandHandlers.put("delete", args -> handleDeleteGroup());
        commandHandlers.put("list", args -> handleListGroups());
        
        // 历史记录命令
        commandHandlers.put("history", args -> handleHistory(args));
        commandHandlers.put("recent", args -> handleRecentHistory());
    }
    
    public static boolean processCommand(String input) {
        if (!input.startsWith("/")) {
            return false;
        }
        
        String commandLine = input.substring(1);
        String[] parts = commandLine.split(" ");
        String command = parts[0].toLowerCase();
        
        CommandHandler handler = commandHandlers.get(command);
        if (handler != null) {
            try {
                handler.handle(parts);
            } catch (Exception e) {
                System.out.println("Error executing command: " + e.getMessage());
            }
            return true;
        } else {
            System.out.println("Unknown command: " + command + ". Type /help for available commands.");
            return true;
        }
    }
    
    private static void showHelp() {
        System.out.println("\n=== Available Commands ===");
        System.out.println("Basic Commands:");
        System.out.println("  /help - Show this help message");
        System.out.println("  /clear - Clear the screen");
        System.out.println("  /exit, /quit - Exit the application");
        
        if (client != null && !client.isLoggedIn()) {
            System.out.println("\nAuthentication:");
            System.out.println("  /login <nickname> <password> - Login to your account");
            System.out.println("  /register <nickname> <password> - Register a new account");
        } else if (client != null && client.isLoggedIn()) {
            System.out.println("\nGroup Management:");
            System.out.println("  /create <groupname> [password] - Create a new group");
            System.out.println("  /join <group_name> [password] - Join an existing group by name");
            System.out.println("  /leave - Leave current group");
            System.out.println("  /delete - Delete current group (owner only)");
            System.out.println("  /list - List your groups");
            System.out.println("  /logout - Logout from account");
            
            System.out.println("\nHistory Commands:");
            System.out.println("  /history [hours] - Show chat history (default: 3 hours)");
            System.out.println("  /recent - Show recent messages (last 50)");
            
            if (client.getCurrentGroup() != null) {
                System.out.println("\nChat:");
                System.out.println("  Type any message to send to group: " + client.getCurrentGroup().getName());
            }
        }
        System.out.println();
    }
    
    private static void exit() {
        if (client != null && client.isLoggedIn()) {
            client.sendLogout();
        }
        if (client != null) {
            client.close();
        }
        System.out.println("Goodbye!");
        System.exit(0);
    }
    
    private static void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to clear screen: " + e.getMessage());
        }
    }
    
    private static void handleLogin(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: /login <nickname> <password>");
            return;
        }
        if (client == null) {
            System.out.println("Client not initialized");
            return;
        }
        client.sendLogin(args[1], args[2]);
    }
    
    private static void handleRegister(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: /register <nickname> <password>");
            return;
        }
        if (client == null) {
            System.out.println("Client not initialized");
            return;
        }
        client.sendRegister(args[1], args[2]);
    }
    
    private static void handleLogout() {
        if (client == null) {
            System.out.println("Client not initialized");
            return;
        }
        client.sendLogout();
    }
    
    private static void handleCreateGroup(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: /create <groupname> [password]");
            return;
        }
        if (client == null || !client.isLoggedIn()) {
            System.out.println("Please login first");
            return;
        }
        
        String groupName = args[1];
        String password = args.length >= 3 ? args[2] : "";
        client.sendCreateGroup(groupName, password);
    }
    
    private static void handleJoinGroup(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: /join <group_name> [password]");
            return;
        }
        if (client == null || !client.isLoggedIn()) {
            System.out.println("Please login first");
            return;
        }
        
        String groupName = args[1];  // 改为群聊名称
        String password = args.length >= 3 ? args[2] : "";
        client.sendJoinGroup(groupName, password);
    }
    
    private static void handleLeaveGroup() {
        if (client == null || !client.isLoggedIn()) {
            System.out.println("Please login first");
            return;
        }
        if (client.getCurrentGroup() == null) {
            System.out.println("You are not in any group");
            return;
        }
        client.sendLeaveGroup();
    }
    
    private static void handleDeleteGroup() {
        if (client == null || !client.isLoggedIn()) {
            System.out.println("Please login first");
            return;
        }
        if (client.getCurrentGroup() == null) {
            System.out.println("You are not in any group");
            return;
        }
        
        System.out.print("Are you sure you want to delete this group? (y/N): ");
        try (Scanner scanner = new Scanner(System.in)) {
            String confirm = scanner.nextLine().trim().toLowerCase();
            if ("y".equals(confirm) || "yes".equals(confirm)) {
                client.sendDeleteGroup();
            } else {
                System.out.println("Delete cancelled");
            }
        } catch (Exception e) {
            System.out.println("Input cancelled");
        }
    }
    
    private static void handleListGroups() {
        if (client == null || !client.isLoggedIn()) {
            System.out.println("Please login first");
            return;
        }
        client.sendListGroups();
    }
    
    private static void handleHistory(String[] args) {
        if (client == null || !client.isLoggedIn()) {
            System.out.println("Please login first");
            return;
        }
        if (client.getCurrentGroup() == null) {
            System.out.println("You are not in any group");
            return;
        }
        
        int hours = 3; // 默认3小时
        if (args.length >= 2) {
            try {
                hours = Integer.parseInt(args[1]);
                if (hours <= 0) {
                    System.out.println("Hours must be positive");
                    return;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid hours format");
                return;
            }
        }
        
        client.sendGetHistory(hours);
    }
    
    private static void handleRecentHistory() {
        if (client == null || !client.isLoggedIn()) {
            System.out.println("Please login first");
            return;
        }
        if (client.getCurrentGroup() == null) {
            System.out.println("You are not in any group");
            return;
        }
        
        client.sendGetRecentMessages();
    }
}
