# CLAUDE.md — ClaudeLang

Guidance for working on the **ClaudeLang** plugin: a Paper plugin (API 1.21.11) that shows text to each player in the language set by their Minecraft client.

## 1. Purpose

- Every player-facing string (chat, tab list, scoreboard, item names/lore, titles, action bars, messages) is looked up by key and rendered in the player's language.
- The player's language comes from the client locale (`Player#locale()`). If it is missing, fall back to `default-lang` from `config.yml`.
- Adding a new language must require nothing more than creating files. No Java changes.
- Strings must work in every rendering context through one API and one syntax.

## 2. Tech stack and build

- Java 21 (`pom.xml`, `java.version`). The IDE project is set to JDK 26; keep code compatible with Java 21.
- Maven, `mvn clean package`. The output is shaded (`maven-shade-plugin`).
- Dependencies (both `provided`, so neither is shaded): `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` and `me.clip:placeholderapi:2.12.3` (repository `extendedclip`, `https://repo.extendedclip.com/releases/`).
- `src/main/resources` is Maven-filtered. Any `${...}` in resource files is replaced at build time. Do not put literal `${` in YAML lang files. Escape it or avoid it.
- `plugin.yml`: `main: org.chiterok.claudeLang.ClaudeLang`, `api-version: '1.21.11'`, `load: POSTWORLD`.
- Base package: `org.chiterok.claudeLang`.

## 3. Repository layout

```
src/main/java/org/chiterok/claudeLang/
  ClaudeLang.java            # JavaPlugin entry point: wires lang manager, commands, PlaceholderAPI expansion
  utils/
    ColorUtil.java           # Single entry point: String -> Kyori Component
    PlaceholderApiHook.java  # Reflective, optional PlaceholderAPI bridge (package-private)
  hook/
    ClaudeLangExpansion.java # %claudelang_<key>% expansion; the only class that references me.clip.*
  lang/
    LangManager.java         # Lookup by key, fallback chain, reload, per-player overrides
    LangLoader.java          # Reads <code>.yml index + text/<section>.yml into a LangPack
    LangInstaller.java       # Copies bundled lang files to the data folder (never overwrites)
    LangPack.java            # Immutable flattened strings of one language
    PlayerLangStore.java     # players.yml: /claudelang set overrides
  command/
    CommandRegistry.java     # /claudelang dispatcher + tab completion
    SubCommand.java          # Sub-command contract
    commands/Reload.java     # /claudelang reload
    commands/Set.java        # /claudelang set <lang|reset> [player]
src/main/resources/
  plugin.yml                 # command, permissions, softdepend PlaceholderAPI
  config.yml                 # depend-game-lang, default-lang
  lang/
    en/en.yml                # Language index (maps sections to files)
    en/text/tab.yml          # Strings for the "tab" section
    en/text/messages.yml     # Command/feedback messages
    ru/ru.yml                # Russian index
    ru/text/tab.yml
    ru/text/messages.yml
```

When you add a language or a feature, keep this structure. Don't invent a new layout.

## 4. Language file system (the most important part)

### 4.1 Folder per language

```
lang/<code>/
  <code>.yml        # index: one entry per section, pointing to a file in text/
  text/
    <section>.yml   # strings for one feature area
```

- `<code>` is a lowercase language code matching the client locale prefix: `en`, `ru`, `uk`, `de`, ...
- Client locales look like `en_us`, `ru_ru`. Use the part before `_` for the folder name.

### 4.2 One file per feature area

Split strings by feature, not by language. A feature owns one file:

- `text/tab.yml`: tab list headers/footers and labels
- `text/scoreboard.yml`, `text/chat.yml`, `text/items.yml`, `text/messages.yml`, ...

Do not put everything in one file. Each file must stay small enough to scan at a glance.

### 4.3 Index file

`<code>.yml` maps a top-level section to its file, as in `en.yml`:

```yaml
tab:
  file: "tab.yml"
```

The loader reads the index, then loads each referenced file under `text/`. A language is valid if it has an index file. A missing section falls back to the default language.

### 4.4 Keys

- Keys are dot-separated and lowercase: `tab.online`, `chat.join`, `items.kit.name`.
- The full key is `<section>.<path inside file>`. Example: `tab.online` is the `online` key in `text/tab.yml` under `tab:`.
- Snake_case inside a key segment is fine: `ever_joined`.
- Never rename a key that exists in `en` without updating every language. A missing key in another language falls back to `en`, so it still works, but it is wrong.

### 4.5 Fallback chain

For each lookup, try in this order and stop at the first hit:

1. The player's client language (`player.locale().getLanguage()`)
2. `default-lang` from `config.yml`
3. `en`
4. The key itself (so missing keys are visible in game instead of silent)

Log a warning once per missing key, not on every lookup.

### 4.6 Config

`config.yml`:
- `depend-game-lang: true`: use the client locale. If false, always use `default-lang`.
- `default-lang: en`: fallback language.

Do not add settings that break the rule "a new language is just files."

## 5. Text syntax

All strings in lang files go through `ColorUtil`. Supported syntax can be mixed freely:

| Syntax | Example | Meaning |
|---|---|---|
| Legacy code | `&a`, `&l`, `&r` | Color/format codes `0-9 a-f k-o r` |
| Hex (ampersand) | `&#FF8800` | Hex color |
| Hex (tag) | `<#FF8800>` | Hex color (MiniMessage) |
| MiniMessage | `<gradient:#ff0000:#00ff00>text</gradient>` | Full MiniMessage |
| Placeholder | `{player}`, `{kit.name}` | Replaced at render time |

Placeholder names match `[A-Za-z0-9_.-]+`.

Rules:
- Prefer legacy codes for simple colors in lang files. They are the easiest for translators.
- Use MiniMessage only for gradients, hover, click, and other features legacy codes can't do.
- Do not mix styles in a way that makes a line hard to read. If a line has more than two kinds of syntax, split it into two keys.

## 6. Placeholders

- Syntax is always `{name}`. Never use `%name%` (that is PlaceholderAPI's syntax; see 6.2).
- Placeholder values are trusted plugin-side text (player names, kit names, numbers). They may contain legacy or hex codes and are converted with the rest of the string.
- A placeholder with no value is left as-is (`{name}` stays visible). This is intentional, so mistakes show up in game.
- Translators keep placeholder names unchanged. Only the surrounding text is translated.

### 6.1 Usage in code

```java
Component c = ColorUtil.colorize(
    lang.get(viewer, "tab.online"),
    Map.of("count", String.valueOf(online))
);
```

Always pass the viewer when the text is per-player. Omitting the viewer skips PlaceholderAPI and per-player locale.

### 6.2 PlaceholderAPI

- If PlaceholderAPI is installed and a viewer is given, PlaceholderAPI placeholders (`%player_name%`, ...) are resolved after `{name}` placeholders.
- If PlaceholderAPI is not installed, skip this step silently. Do not make it a hard dependency. Detect it at runtime with `Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")`.

## 6a. PlaceholderAPI expansion

Other plugins (TAB, scoreboard, chat, menus) can show ClaudeLang text without any Java code by writing `%claudelang_<key>%` in their own config. Example: `%claudelang_tab.online%`. The class is `hook/ClaudeLangExpansion.java`.

- The key is the full lang key (4.4) and is case-insensitive (`ColorUtil.key`).
- The viewer is the player PlaceholderAPI passes in. The client language, `/claudelang set` overrides and the fallback chain (4.5) all apply, because the expansion only calls `LangManager#get`. A missing key returns the key itself (and logs one warning).
- The output is the raw lang text. Legacy and hex codes stay in place for the calling plugin to render. It is not converted to a `Component` or MiniMessage.
- `{name}` placeholders are **not** supported in the expansion output. PlaceholderAPI has no argument source for them, so they stay visible as written. Use plain text or `%...%` placeholders in strings meant for the expansion.
- Nested `%...%` placeholders (for example `%player_name%`) inside a value are resolved once for the viewer. A `%claudelang_...%` inside a value is not expanded again, so self-references cannot loop.
- The expansion is registered in `ClaudeLang#onEnable` only when PlaceholderAPI is enabled, after `LangManager` is ready. Nothing outside the `hook` package may reference `me.clip.*`.
- Nothing is cached, so `/claudelang reload` takes effect immediately without re-registering.

## 7. Rendering API (`ColorUtil`)

`ColorUtil` is the only place that converts strings to Components. Do not call MiniMessage or `LegacyComponentSerializer` directly in feature code.

Public methods (keep these signatures stable):

- `colorize(String)`, `colorize(String, Map)`, `colorize(String, Map, Player)`
- `colorize(List<String>, ...)` overloads for lines
- `colorizeItem(...)`: same as `colorize` but forces italics off unless the text sets `&o`/`<italic>`. Use it for item names and lore.
- `plain(Component)`: plain text for logs and comparisons
- `key(String)`: normalizes registry keys with `Locale.ROOT`

Rules:
- Use `colorizeItem` for anything shown on an item. Use `colorize` everywhere else.
- Never build strings by concatenation for player-facing text. Use placeholders.
- Never hardcode player-facing English in Java. Always go through a key.

## 8. Coding conventions

- Keep classes small and focused. The command package and the lang loader should each be separate from `ColorUtil`.
- No static mutable state except the lang cache, which must be replaceable on `/claudelang reload`.
- Use `final` for utility classes and `private` constructors (see `ColorUtil`).
- Use `@Nullable` (`org.jetbrains.annotations`) on parameters and returns that can be null.
- Use `Locale.ROOT` for any case conversion of keys or codes.
- Use Java 21 features where they make code clearer (switch expressions, records, `Map.of`), but do not add features that need a newer JDK.
- Comments: explain *why*, not *what*. Javadoc on public API methods.
- Do not add dependencies without a clear reason. Paper API and Kyori Adventure (bundled with Paper) cover most needs.

## 9. Commands

- Root command: `/claudelang` (plan), with sub-commands implementing `SubCommand`.
- Planned sub-commands:
    - `reload`: reload `config.yml` and all lang files, without a server restart
    - `set <lang|reset> [player]`: set a player's language override (if `depend-game-lang` is false, or as an override); `reset` removes it; targeting another player needs `claudelang.set.others`
- Every command must have a permission node: `claudelang.<subcommand>`. Declare them in `plugin.yml`.
- Tab-complete language codes from the loaded language folders.

## 10. Current state and known gaps

Implemented: `ColorUtil` pipeline (placeholders, MiniMessage, optional PlaceholderAPI), the `lang` package (loader, fallback chain, warn-once, reload, per-player overrides), `/claudelang reload` and `/claudelang set`, permissions in `plugin.yml`, `en` and `ru` files, `onEnable` wiring, `maven-compiler-plugin` pinned with `<release>21</release>`, and the PlaceholderAPI expansion `%claudelang_<key>%` (section 6a; PlaceholderAPI 2.12.3, `provided`). `tab.header` and `tab.footer` exist in `en` and `ru` as example keys for it.

Design notes worth knowing:

- Placeholder values are passed through `MiniMessage#escapeTags` and then converted once, so a value can carry `&` / `&#RRGGBB` codes but can never inject MiniMessage tags (click/hover). Write tags in the lang text, not in values.
- Bundled language files are copied to `plugins/ClaudeLang/lang/` only when missing. Existing files are never overwritten, so keys added in a later plugin version (for example `tab.header` and `tab.footer`) do not reach files a server owner already has. Those keys fall back along the chain in 4.5.
- Overrides from `/claudelang set` live in `players.yml` in the data folder and apply even when `depend-game-lang` is true.
- `en` is the only language code known to Java (the last step of the fallback chain in 4.5). Everything else comes from the client locale or `default-lang`.
- The expansion output is raw text: `{name}` placeholders are not filled in (6a).

Open gaps:

1. No ClaudeLang feature renders strings itself yet. Strings are reachable from Java (`LangManager#get`) and from other plugins through `%claudelang_<key>%`, but there is no built-in tab-list, scoreboard, or chat code.
2. There are no automated tests (loader, fallback chain, `ColorUtil` escaping, expansion).
3. The IDE project is set to JDK 26. Maven builds with `--release 21`; keep the IDE language level compatible.

## 11. Working rules for Claude

- Before adding a feature, check which lang section it belongs to. If it is new, create a section index and a `text/<section>.yml` in `en`.
- When adding a new player-facing string, add it to `en` first, then to every other language that exists. Do not leave a language missing a key that `en` has.
- When changing `ColorUtil`, keep its public signatures stable. Other code depends on them.
- Do not rewrite the language folder structure unless the user asks. It is the core design.
- Do not add a hard dependency on PlaceholderAPI or any other plugin. `me.clip.*` classes may be referenced only inside the `hook` package.
- When the user asks for a new language, create `lang/<code>/<code>.yml` and the `text/` files, copying the `en` keys as a template and translating the values. Keep placeholder names and color codes identical.
- Never hardcode a language code in Java. Always derive it from the client locale or `default-lang`.
- Run `mvn clean package` after changes to Java code and fix compile errors before reporting done.
- If a requested change conflicts with sections 4–6, point out the conflict and ask before changing the design.

## 12. Quick reference

| Task | Where |
|---|---|
| Add a string | `lang/en/text/<section>.yml` (+ index entry in `lang/en/en.yml` if new section) |
| Add a language | New folder `lang/<code>/` with index and `text/` |
| Change default language | `config.yml` → `default-lang` |
| Render text | `ColorUtil.colorize` / `colorizeItem` |
| Show text from another plugin | `%claudelang_<key>%` (section 6a) |
| Add a command | `command/commands/` + implement `SubCommand` + register in `CommandRegistry` + `plugin.yml` |
| Build | `mvn clean package` → shaded jar in `target/` |
