package org.chiterok.claudeLang.lang;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Copies the language files bundled in the jar to the data folder. Existing files are never overwritten, so
 * server owners can edit translations and add languages by only creating files.
 */
final class LangInstaller {

    private static final String ROOT = "lang/";

    private LangInstaller() {}

    static void installMissing(JavaPlugin plugin, File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().startsWith(ROOT))
                    .map(JarEntry::getName)
                    // Checked here because saveResource logs a warning for every file that already exists.
                    .filter(name -> !new File(plugin.getDataFolder(), name).exists())
                    .forEach(name -> plugin.saveResource(name, false));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not install bundled languages: " + e.getMessage());
        }
    }
}
