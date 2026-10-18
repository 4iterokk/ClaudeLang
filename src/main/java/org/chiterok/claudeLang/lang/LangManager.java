package org.chiterok.claudeLang.lang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.chiterok.claudeLang.utils.ColorUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up translated strings by key.
 * <p>
 * Fallback chain for every lookup: the viewer's language, {@code default-lang}, {@link #FALLBACK_LANG}, and
 * finally the key itself so a missing translation is visible in game. A missing key is logged once.
 */
public final class LangManager {

    /** Last language of the documented fallback chain; the only language code the code base knows about. */
    private static final String FALLBACK_LANG = "en";

    /** Everything a reload replaces, swapped in one step so lookups never see a half-loaded state. */
    private record Snapshot(Map<String, LangPack> packs, boolean dependGameLang, String defaultLang) {}

    private final JavaPlugin plugin;
    private final File jarFile;
    private final PlayerLangStore overrides;
    private final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();
    private volatile Snapshot snapshot = new Snapshot(Map.of(), true, FALLBACK_LANG);

    /**
     * @param jarFile the plugin jar ({@code JavaPlugin#getFile} is protected, so the plugin passes it in)
     */
    public LangManager(JavaPlugin plugin, File jarFile) {
        this.plugin = plugin;
        this.jarFile = jarFile;
        this.overrides = new PlayerLangStore(new File(plugin.getDataFolder(), "players.yml"), plugin.getLogger());
    }

    /**
     * Reloads {@code config.yml} and every language file without a restart.
     *
     * @return the number of loaded languages
     */
    public int reload() {
        plugin.reloadConfig();
        LangInstaller.installMissing(plugin, jarFile);

        Map<String, LangPack> packs = LangLoader.loadAll(new File(plugin.getDataFolder(), "lang"), plugin.getLogger());
        String defaultLang = ColorUtil.key(plugin.getConfig().getString("default-lang", FALLBACK_LANG));
        if (!packs.containsKey(defaultLang)) {
            plugin.getLogger().warning("default-lang '" + defaultLang + "' has no language folder; using '"
                    + FALLBACK_LANG + "' instead");
        }

        overrides.load();
        warnedKeys.clear();
        snapshot = new Snapshot(Map.copyOf(packs), plugin.getConfig().getBoolean("depend-game-lang", true), defaultLang);
        plugin.getLogger().info("Loaded " + packs.size() + " language(s): " + String.join(", ", new TreeSet<>(packs.keySet())));
        return packs.size();
    }

    /**
     * Returns the raw (not yet colorized) text for {@code key} in the viewer's language. Pass the result to
     * {@link ColorUtil#colorize(String, Map, Player)} together with the same viewer.
     *
     * @param viewer the player who will see the text, or {@code null} for console/global text (uses default-lang)
     */
    public String get(@Nullable Player viewer, String key) {
        String normalized = ColorUtil.key(key);
        List<LangPack> chain = chain(viewer);
        for (LangPack pack : chain) {
            String value = pack.string(normalized);
            if (value != null) return value;
        }
        warnMissing(key, normalized, chain);
        return key;
    }

    /** Same as {@link #get(Player, String)} for multi-line values such as item lore. */
    public List<String> getList(@Nullable Player viewer, String key) {
        String normalized = ColorUtil.key(key);
        List<LangPack> chain = chain(viewer);
        for (LangPack pack : chain) {
            List<String> value = pack.list(normalized);
            if (value != null) return value;
        }
        warnMissing(key, normalized, chain);
        return List.of(key);
    }

    /** Looks up {@code key} for the receiver, colorizes it and sends it. Console receivers use default-lang. */
    public void send(CommandSender receiver, String key, Map<String, String> placeholders) {
        Player viewer = receiver instanceof Player player ? player : null;
        receiver.sendMessage(ColorUtil.colorize(get(viewer, key), placeholders, viewer));
    }

    public void send(CommandSender receiver, String key) {
        send(receiver, key, Map.of());
    }

    /** Codes of all loaded languages, sorted; used for tab completion. */
    public List<String> languages() {
        return new ArrayList<>(new TreeSet<>(snapshot.packs().keySet()));
    }

    public boolean hasLanguage(String code) {
        return snapshot.packs().containsKey(ColorUtil.key(code));
    }

    /** Pins a player to {@code code}, ignoring the client locale. The language must be loaded. */
    public void setPlayerLanguage(UUID player, String code) {
        overrides.set(player, ColorUtil.key(code));
    }

    /** Removes the override so the player's client locale (or default-lang) applies again. */
    public void clearPlayerLanguage(UUID player) {
        overrides.clear(player);
    }

    private List<LangPack> chain(@Nullable Player viewer) {
        Snapshot current = snapshot;
        Set<String> codes = new LinkedHashSet<>();
        String preferred = preferredLanguage(viewer, current);
        if (preferred != null) codes.add(preferred);
        codes.add(current.defaultLang());
        codes.add(FALLBACK_LANG);

        List<LangPack> packs = new ArrayList<>(codes.size());
        for (String code : codes) {
            LangPack pack = current.packs().get(code);
            if (pack != null) packs.add(pack);
        }
        return packs;
    }

    private @Nullable String preferredLanguage(@Nullable Player viewer, Snapshot current) {
        if (viewer == null) return null;
        String override = overrides.get(viewer.getUniqueId());
        if (override != null) return override;
        if (!current.dependGameLang()) return null;
        String language = ColorUtil.key(viewer.locale().getLanguage());
        return language.isEmpty() ? null : language;
    }

    private void warnMissing(String rawKey, String normalizedKey, List<LangPack> chain) {
        if (!warnedKeys.add(normalizedKey)) return;
        List<String> tried = chain.stream().map(LangPack::code).toList();
        plugin.getLogger().warning("Missing translation key '" + rawKey + "' (tried: " + String.join(", ", tried) + ")");
    }
}
