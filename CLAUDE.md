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
- Dependency: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` (`provided`).
- `src/main/resources` is Maven-filtered. Any `${...}` in resource files is replaced at build time. Do not put literal `${` in YAML lang files. Escape it or avoid it.
- `plugin.yml`: `main: org.chiterok.claudeLang.ClaudeLang`, `api-version: '1.21.11'`, `load: POSTWORLD`.
- Base package: `org.chiterok.claudeLang`.

## 3. Repository layout

```
src/main/java/org/chiterok/claudeLang/
  ClaudeLang.java            # JavaPlugin entry point (onEnable/onDisable)
  utils/ColorUtil.java       # Single entry point: String -> Kyori Component
  command/
    CommandRegistry.java     # Command registration (stub)
    SubCommand.java          # Sub-command contract (stub)
    commands/Reload.java     # /claudelang reload (stub)
    commands/Set.java        # /claudelang set <lang> (stub)
src/main/resources/
  plugin.yml
  config.yml                 # depend-game-lang, default-lang
  lang/
    en/en.yml                # Language index (maps sections to files)
    en/text/tab.yml          # Actual strings for the "tab" section
    ru/ru.yml                # Russian index (currently empty)
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
    - `set <lang>`: set a player's language override (if `depend-game-lang` is false, or as an override)
- Every command must have a permission node: `claudelang.<subcommand>`. Declare them in `plugin.yml`.
- Tab-complete language codes from the loaded language folders.

## 10. Current state and known gaps

Be honest about these. Do not assume they work:

1. **`ColorUtil.colorize(String, Map, Player)` returns `null`.** The method converts the text into `converted` and then returns `null` without rendering it. Fix this first: apply placeholders, convert with `MINI.deserialize`, and resolve PlaceholderAPI if present.
2. **`applyPlaceholders` is defined but never called.** Wire it into the colorize pipeline.
3. **`toMiniMessage` is applied to placeholder values inside `applyPlaceholders`**, but the value is converted again later if it contains `<`. Check for double conversion. Plain user input containing `<` may be interpreted as a tag. Escape user-provided values with `MiniMessage#escapeTags` before converting, or convert only once.
4. **The PlaceholderAPI section is an empty comment block.** Implement it per section 6.2.
5. **Language loader does not exist yet.** The index/`text/` structure in section 4 is the target design; implement it in a `lang` package (for example `org.chiterok.claudeLang.lang.LangManager`).
6. **`lang/ru/ru.yml` is empty.** Add the index and `text/` files when Russian is ready.
7. **Command classes are empty stubs** (`CommandRegistry`, `SubCommand`, `Reload`, `Set`).
8. **`en.yml` index points at `tab.yml`, but `text/tab.yml` uses `tab:` as its root key.** Keep this structure consistent: the index section name must match the root key in its file.
9. **`ClaudeLang.onEnable` is empty.** Wire the lang manager, config, and commands here.
10. **IDE JDK is 26, pom targets 21.** Build against 21 to match the target server runtime.

## 11. Working rules for Claude

- Before adding a feature, check which lang section it belongs to. If it is new, create a section index and a `text/<section>.yml` in `en`.
- When adding a new player-facing string, add it to `en` first, then to every other language that exists. Do not leave a language missing a key that `en` has.
- When changing `ColorUtil`, keep its public signatures stable. Other code depends on them.
- Do not rewrite the language folder structure unless the user asks. It is the core design.
- Do not add a hard dependency on PlaceholderAPI or any other plugin.
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
| Add a command | `command/commands/` + implement `SubCommand` + register in `CommandRegistry` + `plugin.yml` |
| Build | `mvn clean package` → shaded jar in `target/` |