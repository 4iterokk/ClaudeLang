package org.chiterok.claudeLang;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.chiterok.claudeLang.command.CommandRegistry;
import org.chiterok.claudeLang.command.commands.Reload;
import org.chiterok.claudeLang.command.commands.Set;
import org.chiterok.claudeLang.hook.ClaudeLangExpansion;
import org.chiterok.claudeLang.lang.LangManager;

public final class ClaudeLang extends JavaPlugin {

    private LangManager lang;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        lang = new LangManager(this, getFile());
        lang.reload();

        CommandRegistry registry = new CommandRegistry(lang)
                .register(new Reload(lang))
                .register(new Set(lang));

        PluginCommand command = getCommand("claudelang");
        if (command == null) {
            getLogger().severe("Command 'claudelang' is missing from plugin.yml");
            return;
        }
        command.setExecutor(registry);
        command.setTabCompleter(registry);

        registerPlaceholderExpansion();
    }

    @Override
    public void onDisable() {
        // Nothing to release: overrides are saved when they change.
    }

    /** Entry point for other features: {@code getLang().get(viewer, "tab.online")}. */
    public LangManager getLang() {
        return lang;
    }

    /**
     * Registers {@code %claudelang_<key>%}. The expansion class is only touched inside this guard, so a server
     * without PlaceholderAPI never loads it (and never sees a missing {@code me.clip} class).
     */
    private void registerPlaceholderExpansion() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        if (!new ClaudeLangExpansion(this, lang).register()) {
            getLogger().warning("Could not register the PlaceholderAPI expansion 'claudelang'");
        }
    }
}
