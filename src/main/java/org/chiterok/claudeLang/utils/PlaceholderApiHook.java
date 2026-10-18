package org.chiterok.claudeLang.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Soft bridge to PlaceholderAPI. Reflection keeps the plugin free of a compile-time (and hard runtime) dependency.
 */
final class PlaceholderApiHook {

    private static final String PLUGIN_NAME = "PlaceholderAPI";

    private PlaceholderApiHook() {}

    /** Returns {@code text} with PlaceholderAPI placeholders resolved, or unchanged when the hook is unavailable. */
    static String resolve(Player viewer, String text) {
        if (!Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) return text;
        Method method = Holder.SET_PLACEHOLDERS;
        if (method == null) return text;
        try {
            return method.invoke(null, viewer, text) instanceof String resolved ? resolved : text;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A broken expansion must not take the whole message down.
            return text;
        }
    }

    /** Lazy holder: the lookup runs once, and only after PlaceholderAPI was seen enabled. */
    private static final class Holder {
        static final @Nullable Method SET_PLACEHOLDERS = lookup();

        private static @Nullable Method lookup() {
            try {
                Plugin papi = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
                if (papi == null) return null;
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, papi.getClass().getClassLoader());
                return api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                return null;
            }
        }
    }
}
