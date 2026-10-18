package org.chiterok.claudeLang.lang;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.chiterok.claudeLang.utils.ColorUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads {@code lang/<code>/<code>.yml} (index) and the {@code text/<section>.yml} files it references.
 * A language is valid as soon as its index file exists; broken sections are skipped with a warning.
 */
final class LangLoader {

    private static final String TEXT_DIR = "text";

    private LangLoader() {}

    /** Loads every language folder under {@code langRoot}. Never throws: bad files are logged and skipped. */
    static Map<String, LangPack> loadAll(File langRoot, Logger log) {
        Map<String, LangPack> packs = new HashMap<>();
        File[] dirs = langRoot.listFiles(File::isDirectory);
        if (dirs == null) return packs;
        for (File dir : dirs) {
            String code = ColorUtil.key(dir.getName());
            File index = new File(dir, dir.getName() + ".yml");
            if (!index.isFile()) continue; // not a language folder
            LangPack pack = loadPack(code, dir, index, log);
            if (pack != null) packs.put(code, pack);
        }
        return packs;
    }

    private static @Nullable LangPack loadPack(String code, File dir, File index, Logger log) {
        YamlConfiguration indexCfg = read(index, log);
        if (indexCfg == null) return null;

        Map<String, String> strings = new HashMap<>();
        Map<String, List<String>> lists = new HashMap<>();
        Path textDir = new File(dir, TEXT_DIR).toPath().toAbsolutePath().normalize();

        for (String section : indexCfg.getKeys(false)) {
            String fileName = indexCfg.getString(section + ".file");
            if (fileName == null || fileName.isBlank()) {
                log.warning("[" + code + "] section '" + section + "' in " + index.getName() + " has no 'file' entry");
                continue;
            }
            // The index is editable by server owners; keep it from reading files outside text/.
            Path file = textDir.resolve(fileName).normalize();
            if (!file.startsWith(textDir)) {
                log.warning("[" + code + "] section '" + section + "' points outside text/: " + fileName);
                continue;
            }
            if (!file.toFile().isFile()) {
                log.warning("[" + code + "] section '" + section + "': missing file text/" + fileName);
                continue;
            }
            YamlConfiguration textCfg = read(file.toFile(), log);
            if (textCfg == null) continue;

            ConfigurationSection root = textCfg.getConfigurationSection(section);
            if (root == null) {
                log.warning("[" + code + "] text/" + fileName + " has no root key '" + section
                        + "' (it must match the section name in the index)");
                continue;
            }
            flatten(ColorUtil.key(section), root, strings, lists);
        }
        return new LangPack(code, strings, lists);
    }

    private static void flatten(String prefix, ConfigurationSection root,
                                Map<String, String> strings, Map<String, List<String>> lists) {
        for (String path : root.getKeys(true)) {
            Object value = root.get(path);
            if (value == null || value instanceof ConfigurationSection) continue;
            String key = prefix + "." + ColorUtil.key(path);
            if (value instanceof List<?> raw) {
                List<String> lines = new ArrayList<>(raw.size());
                for (Object line : raw) lines.add(String.valueOf(line));
                lists.put(key, lines);
            } else {
                strings.put(key, String.valueOf(value));
            }
        }
    }

    private static @Nullable YamlConfiguration read(File file, Logger log) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
            return cfg;
        } catch (IOException | InvalidConfigurationException e) {
            log.warning("Could not read " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
