package org.chiterok.claudeLang.hook.packet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.bukkit.entity.Player;
import org.chiterok.claudeLang.lang.LangManager;
import org.chiterok.claudeLang.utils.ColorUtil;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Replaces {@code {lang:<key>}} markers in a component with the text of that key in the viewer's language.
 * <p>
 * Runs on netty threads for every handled packet, so the common case (no marker) only walks the tree and returns
 * the same instance. PlaceholderAPI is not applied: many expansions are not thread-safe.
 */
final class MarkerRenderer {

    private static final String MARKER = "{lang:";
    private static final Pattern PATTERN = Pattern.compile("\\{lang:([A-Za-z0-9_.-]+)}");

    private final LangManager lang;

    MarkerRenderer(LangManager lang) {
        this.lang = lang;
    }

    /** @return the translated component, or the same instance when it has no marker */
    @Nullable Component render(@Nullable Component component, Player viewer, boolean item) {
        if (component == null || !hasMarker(component)) return component;
        TextReplacementConfig config = TextReplacementConfig.builder()
                .match(PATTERN)
                .replacement((match, builder) -> {
                    String text = lang.get(viewer, match.group(1));
                    // The marker's own style applies where the lang text sets none.
                    Component value = item ? ColorUtil.colorizeItem(text) : ColorUtil.colorize(text);
                    return value.applyFallbackStyle(builder.build().style());
                })
                .build();
        return component.replaceText(config);
    }

    private static boolean hasMarker(Component component) {
        if (component instanceof TextComponent text && text.content().contains(MARKER)) return true;
        if (component instanceof TranslatableComponent translatable) {
            for (TranslationArgument argument : translatable.arguments()) {
                if (hasMarker(argument.asComponent())) return true;
            }
        }
        for (Component child : component.children()) {
            if (hasMarker(child)) return true;
        }
        return false;
    }
}
