package top.c0r3.talk.client;

import top.c0r3.talk.encrypt.ZzSecurityHelper;
import top.c0r3.talk.encrypt.generateEncryptKey;

import java.io.IOException;
import java.sql.*;
import java.util.Scanner;

public class Command {
    public static void Process(String command){
        if(!command.startsWith("/")){
            return;
        }
        switch (command) {
            case "/exit", "/reject" -> System.exit(0);
            case "/help" -> {
                System.out.println("Command List:");
                System.out.println("/exit: Exit the program");
                System.out.println("/help: Show this message");
                System.out.println("/clear: Clear the screen");
                System.out.println("/connect: Connect to another person");
                System.out.println("/record: Record the chat history");
            }
            case "/clear" -> {
                try{
                    if (System.getProperty("os.name").contains("Windows")) {
                        // Windows系统
                        new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                    } else {
                        // Unix/Linux/MacOS系统
                        System.out.print("\033[H\033[2J");
                        System.out.flush();
                    }
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            case "/accept", "/connect" -> {
                //Do nothing
            }
            case "/record" ->{
                Scanner sc = new Scanner(System.in);
                System.out.print("Input Your Encryption Key:");
                String seed = sc.next();
                generateEncryptKey kg = new generateEncryptKey();
                ZzSecurityHelper.setKey(kg.generateKey(seed));
                ZzSecurityHelper.setIv(kg.generateKey(seed+seed));

                System.out.print("Input Your NickName:");
                String nickname = sc.next();
                System.out.print("Input Another User's NickName:");
                String nickname2 = sc.next();
                try {
                    GetMsgFromSQL(nickname,nickname2);
                } catch (SQLException e) {
                    System.out.println("SQL Error, please check your nickname");
                }
            }
            default -> System.out.println("Unknown Command, type /help to show command list");
        }
    }

    private static void GetMsgFromSQL (String nickname,String nickname2) throws SQLException {
        String url = "jdbc:mysql://oj.c0r3.top:3306/java-talk";
        String usr = "Java-Talk";
        String pwd = System.getenv("DB_PASSWORD");

        String sql = "SELECT nickname,timestamp, message FROM messages WHERE nickname = ? OR nickname = ? ORDER BY timestamp ";

        try (Connection conn = DriverManager.getConnection(url, usr, pwd);
             PreparedStatement stmt = conn.prepareStatement(sql)
        ){
            stmt.setString(1, nickname);
            stmt.setString(2, nickname2);
            //System.out.println(stmt.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String timestamp = rs.getString("timestamp");
                    String message = rs.getString("message");
                    String msgNickname = rs.getString("nickname");

                    System.out.print(timestamp + " \"" + msgNickname + "\": ");
                    System.out.println(ZzSecurityHelper.decryptAES(message));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}