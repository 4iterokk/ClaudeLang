package org.chiterok.claudeLang.command.commands;

import org.bukkit.command.CommandSender;
import org.chiterok.claudeLang.command.SubCommand;
import org.chiterok.claudeLang.lang.LangManager;

import java.util.Map;

/** {@code /claudelang reload}: reloads {@code config.yml} and all language files. */
public final class Reload implements SubCommand {

    private final LangManager lang;

    public Reload(LangManager lang) {
        this.lang = lang;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        int count = lang.reload();
        // Sent after the reload so the message already uses the freshly loaded files.
        lang.send(sender, "messages.reloaded", Map.of("count", String.valueOf(count)));
    }
}
