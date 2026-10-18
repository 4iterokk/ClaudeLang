package org.chiterok.claudeLang.command;

import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** One {@code /claudelang <name>} sub-command. The registry checks {@link #permission()} before calling it. */
public interface SubCommand {

    /** Lower-case name typed after {@code /claudelang}. */
    String name();

    /** Permission node; every sub-command has one and it must be declared in {@code plugin.yml}. */
    default String permission() {
        return "claudelang." + name();
    }

    /** @param args the arguments after the sub-command name */
    void execute(CommandSender sender, String[] args);

    /** @param args the arguments after the sub-command name; the last element is the one being completed */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }

    /** Case-insensitive prefix filter for tab completion. */
    static List<String> complete(Collection<String> options, String typed) {
        String prefix = typed.toLowerCase(java.util.Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
