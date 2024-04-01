package lol.koblizek.runtimeinject;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class UwUCommand implements CommandExecutor {

    public static final JShell J_SHELL = JShell
            .builder()
            .executionEngine(new CustomLocalExecutionControlProvider(), null)
            .build();

    public UwUCommand() {
        try {
            for (Path jar : Files.walk(Path.of("libraries/")).filter(p -> p.toFile().getName().endsWith(".jar"))
                    .toList()) {
                J_SHELL.addToClasspath(jar.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String code = scanner.nextLine();
            for (SnippetEvent event : J_SHELL.eval(code)) {
                if (event.status() == Snippet.Status.VALID) {
                    System.out.println(ChatColor.GREEN + event.value());
                } else {
                    if (event.exception() != null)
                        System.out.println(ChatColor.RED + event.exception().getClass().getSimpleName() + ": " + event.exception().getMessage());
                    else {
                        System.out.println(ChatColor.RED + "" + event.status() + ": " + event.value());
                        System.out.println(event);
                    };
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] strings) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Op is required to execute this command!");
            return true;
        }

        String code = String.join(" ", strings);

        if (code.contains("File") || code.contains("Path") || code.contains("..")) {
            sender.sendMessage( ChatColor.RED+ "This code contains potentially dangerous code, here we go...");
        }

        for (SnippetEvent event : J_SHELL.eval(code)) {
            if (event.status() == Snippet.Status.VALID) {
                sender.sendMessage(ChatColor.GREEN + event.value());
            } else {
                if (event.exception() != null)
                    sender.sendMessage(ChatColor.RED + event.exception().getClass().getSimpleName() + ": " + event.exception().getMessage());
                else {
                    sender.sendMessage(ChatColor.RED + "" + event.status() + ": " + event.value());
                };
            }
        }
        return true;
    }
}
