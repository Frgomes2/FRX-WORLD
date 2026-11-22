package br.com.frxcode.frxworld.progressao;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Élitros só voam para aventureiros de Rank A ou superior da Guilda. Abaixo disso dá para vestir, mas não planar. */
public class Elitros {
    private static final int RANK = 5;   // A (0 = F ... 6 = S)
    private static final Map<UUID, Long> AVISO = new HashMap<>();

    public Elitros() {}

    @SubscribeEvent
    public void aoTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || !p.isFallFlying()) return;
        if (p.isCreative() || p.isSpectator() || Guilda.indice(p) >= RANK) return;
        p.stopFallFlying();
        long agora = System.currentTimeMillis();
        if (agora - AVISO.getOrDefault(p.getUUID(), 0L) < 3000) return;
        AVISO.put(p.getUUID(), agora);
        p.displayClientMessage(Component.literal("Os élitros só respondem a aventureiros de Rank " + ConfigJogo.nomeRank(RANK)
                + " ou superior da Guilda (você é Rank " + Guilda.rank(p) + ").").withStyle(ChatFormatting.RED), true);
    }
}
