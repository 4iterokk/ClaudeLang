package org.chiterok.claudeLang.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Per-player language overrides ({@code /claudelang set}), persisted in {@code players.yml}. */
final class PlayerLangStore {

    private final File file;
    private final Logger log;
    private final Map<UUID, String> overrides = new ConcurrentHashMap<>();

    PlayerLangStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    void load() {
        overrides.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String raw : cfg.getKeys(false)) {
            String code = cfg.getString(raw);
            if (code == null) continue;
            try {
                overrides.put(UUID.fromString(raw), code);
            } catch (IllegalArgumentException e) {
                log.warning("Ignoring invalid UUID in " + file.getName() + ": " + raw);
            }
        }
    }

    @Nullable String get(UUID player) {
        return overrides.get(player);
    }

    void set(UUID player, String code) {
        overrides.put(player, code);
        save();
    }

    void clear(UUID player) {
        if (overrides.remove(player) != null) save();
    }

    private synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        overrides.forEach((uuid, code) -> cfg.set(uuid.toString(), code));
        try {
            cfg.save(file);
        } catch (IOException e) {
            log.warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
