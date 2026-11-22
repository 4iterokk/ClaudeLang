package org.chiterok.claudeLang.lang;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.translation.Translator;
import org.chiterok.claudeLang.utils.ColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * Adventure {@code GlobalTranslator} source: {@code Component.translatable("claudelang.<key>")} is rendered by Paper
 * in each client's locale, with translatable arguments filling {@code {0}}, {@code {1}}, ...
 * <p>
 * Only keys with the {@link #PREFIX} are handled, so vanilla keys such as {@code chat.type.text} are never taken over.
 * Adventure passes only a locale, so {@code /claudelang set} overrides and PlaceholderAPI do not apply here.
 */
public final class ClaudeLangTranslator implements Translator {

    public static final String PREFIX = "claudelang.";
    private static final Key NAME = Key.key("claudelang", "lang");

    private final LangManager lang;

    public ClaudeLangTranslator(LangManager lang) {
        this.lang = lang;
    }

    @Override
    public @NotNull Key name() {
        return NAME;
    }

    /** Not used: every lookup goes through {@link #translate(TranslatableComponent, Locale)}. */
    @Override
    public @Nullable MessageFormat translate(@NotNull String key, @NotNull Locale locale) {
        return null;
    }

    @Override
    public @Nullable Component translate(@NotNull TranslatableComponent component, @NotNull Locale locale) {
        String key = component.key();
        if (!key.startsWith(PREFIX)) return null;
        Component text = ColorUtil.colorize(lang.get(locale, key.substring(PREFIX.length())), component.arguments());
        // Keep the style and children of the original component, as vanilla translation does.
        Component styled = text.applyFallbackStyle(component.style());
        if (component.children().isEmpty()) return styled;
        return styled.append(Component.join(JoinConfiguration.noSeparators(), component.children()));
    }
}
