package top.c0r3.talk.client;

public class Command {
    public static void Process(String command) {
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
            }
            case "/clear" -> {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
            case "/accept", "/connect" -> {
                //Do nothing
            }
            default -> System.out.println("Unknown Command, type /help to show command list");
        }
    }
}
