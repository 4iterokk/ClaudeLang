package org.chiterok.claudeLang.lang;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Immutable, fully flattened strings of one language. Keys are the full dot-separated keys
 * ({@code <section>.<path>}), already lower-cased.
 *
 * @param code    language code, equal to the folder name
 * @param strings single-line values
 * @param lists   multi-line values (lore, tab lines, ...)
 */
public record LangPack(String code, Map<String, String> strings, Map<String, List<String>> lists) {

    public LangPack {
        strings = Map.copyOf(strings);
        lists = Map.copyOf(lists);
    }

    /** A list value is joined with newlines, so a key can be read as text regardless of how a translator wrote it. */
    public @Nullable String string(String key) {
        String value = strings.get(key);
        if (value != null) return value;
        List<String> list = lists.get(key);
        return list == null ? null : String.join("\n", list);
    }

    /** A single-line value is returned as a one-element list. */
    public @Nullable List<String> list(String key) {
        List<String> value = lists.get(key);
        if (value != null) return value;
        String single = strings.get(key);
        return single == null ? null : List.of(single);
    }

    public boolean contains(String key) {
        return strings.containsKey(key) || lists.containsKey(key);
    }
}
