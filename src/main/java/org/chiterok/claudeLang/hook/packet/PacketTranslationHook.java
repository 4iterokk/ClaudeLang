package org.chiterok.claudeLang.hook.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.chiterok.claudeLang.lang.LangManager;

/**
 * Registers the {@code {lang:<key>}} packet translator with PacketEvents.
 * <p>
 * Together with the rest of {@code hook.packet}, this is the only code that touches
 * {@code com.github.retrooper.*}. It is loaded only after PacketEvents was seen enabled, so servers without it
 * never load these classes.
 */
public final class PacketTranslationHook {

    private final PacketListenerCommon listener;

    private PacketTranslationHook(PacketListenerCommon listener) {
        this.listener = listener;
    }

    public static PacketTranslationHook register(LangManager lang) {
        return new PacketTranslationHook(PacketEvents.getAPI().getEventManager().registerListener(new PacketTranslator(lang)));
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
}
