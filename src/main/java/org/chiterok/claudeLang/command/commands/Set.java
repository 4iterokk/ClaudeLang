package org.chiterok.claudeLang.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.chiterok.claudeLang.command.SubCommand;
import org.chiterok.claudeLang.lang.LangManager;
import org.chiterok.claudeLang.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /claudelang set <lang|reset> [player]}: pins a player to a language regardless of the client locale.
 * Changing someone else's language needs {@value #OTHERS_PERMISSION}.
 */
public final class Set implements SubCommand {

    private static final String OTHERS_PERMISSION = "claudelang.set.others";
    /** Command keyword that removes the override. It is not a language code. */
    private static final String RESET = "reset";

    private final LangManager lang;

    public Set(LangManager lang) {
        this.lang = lang;
    }

    @Override
    public String name() {
        return "set";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            lang.send(sender, "messages.set_usage");
            return;
        }

        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission(OTHERS_PERMISSION)) {
                lang.send(sender, "messages.no_permission");
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                lang.send(sender, "messages.player_not_found", Map.of("player", args[1]));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            lang.send(sender, "messages.console_needs_player");
            return;
        }

        boolean self = target.equals(sender);
        String code = ColorUtil.key(args[0]);

        if (code.equals(RESET)) {
            lang.clearPlayerLanguage(target.getUniqueId());
            lang.send(sender, self ? "messages.set_reset" : "messages.set_reset_other", Map.of("player", target.getName()));
            return;
        }
        if (!lang.hasLanguage(code)) {
            lang.send(sender, "messages.set_unknown_lang",
                    Map.of("lang", args[0], "available", String.join(", ", lang.languages())));
            return;
        }

        lang.setPlayerLanguage(target.getUniqueId(), code);
        lang.send(sender, self ? "messages.set_done" : "messages.set_done_other",
                Map.of("lang", code, "player", target.getName()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(lang.languages());
            options.add(RESET);
            return SubCommand.complete(options, args[0]);
        }
        if (args.length == 2 && sender.hasPermission(OTHERS_PERMISSION)) {
            return SubCommand.complete(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }
}
