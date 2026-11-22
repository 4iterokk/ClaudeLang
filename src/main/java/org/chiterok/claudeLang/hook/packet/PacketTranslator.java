package org.chiterok.claudeLang.hook.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBossBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.chiterok.claudeLang.lang.LangManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Rewrites {@code {lang:<key>}} markers in outgoing packets for each viewer. Packets are re-encoded only when a
 * marker was actually replaced.
 */
final class PacketTranslator extends PacketListenerAbstract {

    private final MarkerRenderer renderer;

    PacketTranslator(LangManager lang) {
        super(PacketListenerPriority.NORMAL);
        this.renderer = new MarkerRenderer(lang);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        // Null while the player is still logging in or configuring; nothing is translated then.
        Player viewer = event.getPlayer();
        if (viewer == null) return;

        PacketTypeCommon type = event.getPacketType();
        boolean changed;
        if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            var packet = new WrapperPlayServerSystemChatMessage(event);
            changed = text(packet.getMessage(), viewer, packet::setMessage);
        } else if (type == PacketType.Play.Server.SET_TITLE_TEXT) {
            var packet = new WrapperPlayServerSetTitleText(event);
            changed = text(packet.getTitle(), viewer, packet::setTitle);
        } else if (type == PacketType.Play.Server.SET_TITLE_SUBTITLE) {
            var packet = new WrapperPlayServerSetTitleSubtitle(event);
            changed = text(packet.getSubtitle(), viewer, packet::setSubtitle);
        } else if (type == PacketType.Play.Server.ACTION_BAR) {
            var packet = new WrapperPlayServerActionBar(event);
            changed = text(packet.getActionBarText(), viewer, packet::setActionBarText);
        } else if (type == PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER) {
            var packet = new WrapperPlayServerPlayerListHeaderAndFooter(event);
            changed = text(packet.getHeader(), viewer, packet::setHeader)
                    | text(packet.getFooter(), viewer, packet::setFooter);
        } else if (type == PacketType.Play.Server.BOSS_BAR) {
            var packet = new WrapperPlayServerBossBar(event);
            changed = text(packet.getTitle(), viewer, packet::setTitle);
        } else if (type == PacketType.Play.Server.SCOREBOARD_OBJECTIVE) {
            var packet = new WrapperPlayServerScoreboardObjective(event);
            changed = text(packet.getDisplayName(), viewer, packet::setDisplayName);
        } else if (type == PacketType.Play.Server.TEAMS) {
            changed = teams(new WrapperPlayServerTeams(event), viewer);
        } else if (type == PacketType.Play.Server.OPEN_WINDOW) {
            var packet = new WrapperPlayServerOpenWindow(event);
            changed = text(packet.getTitle(), viewer, packet::setTitle);
        } else if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            changed = !skipItems(viewer) && windowItems(new WrapperPlayServerWindowItems(event), viewer);
        } else if (type == PacketType.Play.Server.SET_SLOT) {
            var packet = new WrapperPlayServerSetSlot(event);
            changed = !skipItems(viewer) && item(packet.getItem(), viewer);
        } else if (type == PacketType.Play.Server.SET_CURSOR_ITEM) {
            var packet = new WrapperPlayServerSetCursorItem(event);
            changed = !skipItems(viewer) && item(packet.getStack(), viewer);
        } else if (type == PacketType.Play.Server.ENTITY_METADATA) {
            changed = metadata(new WrapperPlayServerEntityMetadata(event), viewer);
        } else {
            return;
        }
        if (changed) event.markForReEncode(true);
    }

    /**
     * A creative client sends its whole item back to the server ({@code SET_CREATIVE_SLOT}), which would make the
     * translated text permanent. Creative players therefore see the raw markers on items.
     */
    private static boolean skipItems(Player viewer) {
        return viewer.getGameMode() == GameMode.CREATIVE;
    }

    private boolean text(@Nullable Component original, Player viewer, Consumer<Component> setter) {
        Component rendered = renderer.render(original, viewer, false);
        if (rendered == original) return false;
        setter.accept(rendered);
        return true;
    }

    private boolean teams(WrapperPlayServerTeams packet, Player viewer) {
        Optional<WrapperPlayServerTeams.ScoreBoardTeamInfo> info = packet.getTeamInfo();
        if (info.isEmpty()) return false;
        WrapperPlayServerTeams.ScoreBoardTeamInfo team = info.get();
        return text(team.getDisplayName(), viewer, team::setDisplayName)
                | text(team.getPrefix(), viewer, team::setPrefix)
                | text(team.getSuffix(), viewer, team::setSuffix);
    }

    private boolean windowItems(WrapperPlayServerWindowItems packet, Player viewer) {
        boolean changed = false;
        for (ItemStack stack : packet.getItems()) changed |= item(stack, viewer);
        Optional<ItemStack> carried = packet.getCarriedItem();
        if (carried.isPresent()) changed |= item(carried.get(), viewer);
        return changed;
    }

    /** The stack is the packet's own decoded copy, so changing it never touches the server-side item. */
    private boolean item(@Nullable ItemStack stack, Player viewer) {
        if (stack == null || stack.isEmpty()) return false;
        boolean changed = itemName(stack, ComponentTypes.CUSTOM_NAME, viewer)
                | itemName(stack, ComponentTypes.ITEM_NAME, viewer);

        Optional<ItemLore> lore = stack.getComponent(ComponentTypes.LORE);
        if (lore.isPresent()) {
            List<Component> lines = lore.get().getLines();
            List<Component> rendered = new ArrayList<>(lines.size());
            boolean loreChanged = false;
            for (Component line : lines) {
                Component out = renderer.render(line, viewer, true);
                loreChanged |= out != line;
                rendered.add(out);
            }
            if (loreChanged) {
                stack.setComponent(ComponentTypes.LORE, new ItemLore(rendered));
                changed = true;
            }
        }
        return changed;
    }

    private boolean itemName(ItemStack stack, ComponentType<Component> type, Player viewer) {
        Optional<Component> name = stack.getComponent(type);
        if (name.isEmpty()) return false;
        Component rendered = renderer.render(name.get(), viewer, true);
        if (rendered == name.get()) return false;
        stack.setComponent(type, rendered);
        return true;
    }

    /** Custom names and text-display text. */
    @SuppressWarnings("unchecked")
    private boolean metadata(WrapperPlayServerEntityMetadata packet, Player viewer) {
        boolean changed = false;
        for (EntityData<?> data : packet.getEntityMetadata()) {
            if (data.getType() == EntityDataTypes.OPTIONAL_ADV_COMPONENT) {
                EntityData<Optional<Component>> entry = (EntityData<Optional<Component>>) data;
                Optional<Component> value = entry.getValue();
                if (value == null || value.isEmpty()) continue;
                changed |= text(value.get(), viewer, rendered -> entry.setValue(Optional.of(rendered)));
            } else if (data.getType() == EntityDataTypes.ADV_COMPONENT) {
                EntityData<Component> entry = (EntityData<Component>) data;
                changed |= text(entry.getValue(), viewer, entry::setValue);
            }
        }
        return changed;
    }
}
