package org.chiterok.claudeLang.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.chiterok.claudeLang.lang.LangManager;
import org.chiterok.claudeLang.utils.ColorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dispatches {@code /claudelang <sub-command> ...} to the registered {@link SubCommand}s. */
public final class CommandRegistry implements TabExecutor {

    private final LangManager lang;
    private final Map<String, SubCommand> commands = new LinkedHashMap<>();

    public CommandRegistry(LangManager lang) {
        this.lang = lang;
    }

    public CommandRegistry register(SubCommand command) {
        commands.put(ColorUtil.key(command.name()), command);
        return this;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            List<String> allowed = allowedNames(sender);
            if (allowed.isEmpty()) {
                lang.send(sender, "messages.no_permission");
            } else {
                lang.send(sender, "messages.usage", Map.of("subcommands", String.join("|", allowed)));
            }
            return true;
        }

        SubCommand sub = commands.get(ColorUtil.key(args[0]));
        if (sub == null) {
            lang.send(sender, "messages.unknown_subcommand", Map.of("input", args[0]));
        } else if (!sender.hasPermission(sub.permission())) {
            lang.send(sender, "messages.no_permission");
        } else {
            sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return SubCommand.complete(allowedNames(sender), args[0]);

        SubCommand sub = commands.get(ColorUtil.key(args[0]));
        if (sub == null || !sender.hasPermission(sub.permission())) return List.of();
        return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private List<String> allowedNames(CommandSender sender) {
        return commands.entrySet().stream()
                .filter(entry -> sender.hasPermission(entry.getValue().permission()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
