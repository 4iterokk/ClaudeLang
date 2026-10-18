package org.chiterok.claudeLang.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.chiterok.claudeLang.lang.LangManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes every language key as {@code %claudelang_<key>%} (for example {@code %claudelang_tab.online%}).
 * <p>
 * This is the only class that touches {@code me.clip.*}. It is loaded only after PlaceholderAPI was seen enabled,
 * so servers without PlaceholderAPI never load it.
 */
public final class ClaudeLangExpansion extends PlaceholderExpansion {

    /**
     * Set while the nested pass of this thread runs. A nested {@code %claudelang_...%} inside a value would
     * otherwise call back into {@link #onRequest} and a value that references itself would never end.
     */
    private static final ThreadLocal<Boolean> NESTED = new ThreadLocal<>();

    private final Plugin plugin;
    private final LangManager lang;

    public ClaudeLangExpansion(Plugin plugin, LangManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "claudelang";
    }

    @Override
    public @NotNull String getAuthor() {
        return "chiterok";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Keeps the expansion registered across {@code /papi reload}. */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * Returns the raw text of the key {@code params} in the language of the player who asks.
     * <p>
     * The language comes from the viewer (client locale or {@code /claudelang set} override), because the calling
     * plugin passes the player it renders for. Lookup and fallback stay in {@link LangManager#get}, and nothing is
     * cached so {@code /claudelang reload} applies at once. Legacy and hex codes stay in the text for the calling
     * plugin to render. {@code {name}} placeholders are not filled in: PlaceholderAPI gives this expansion no
     * argument source for them. Nested {@code %...%} placeholders are resolved once for the viewer.
     *
     * @param offline the player the placeholder is parsed for, or {@code null}
     * @param params  the full language key, for example {@code tab.online}
     * @return the text, or the key itself when it is missing, never {@code null}
     */
    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offline, @NotNull String params) {
        Player viewer = offline == null ? null : offline.getPlayer();
        String text = lang.get(viewer, params);

        if (viewer == null || text.indexOf('%') < 0 || NESTED.get() != null) return text;
        NESTED.set(Boolean.TRUE);
        try {
            return PlaceholderAPI.setPlaceholders(viewer, text);
        } finally {
            NESTED.remove();
        }
    }
}
