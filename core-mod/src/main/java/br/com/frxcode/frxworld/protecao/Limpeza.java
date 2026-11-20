package br.com.frxcode.frxworld.protecao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Limpeza automática: a cada 30 minutos some com os itens que estão no chão há mais de 2 minutos,
 * em todas as dimensões carregadas. Item recém-largado fica. Sem aviso prévio; só informa quantos saíram. Túmulos são blocos: não são afetados.
 */
public class Limpeza {
    private static final int INTERVALO = 30 * 60 * 20;   // 30 minutos, em ticks
    /**
     * Só some o item que já está no chão há pelo menos 2 minutos (em ticks), para não levar o que um jogador acabou de largar.
     * Não adianta pedir 5 minutos: nesse tempo o próprio Minecraft já apaga o item, e a limpeza não acharia nada.
     */
    private static final int IDADE_MINIMA = 2 * 60 * 20;
    private int ticks;

    private static void avisar(MinecraftServer s, String texto) {
        s.getPlayerList().broadcastSystemMessage(Component.literal("🧹 " + texto).withStyle(ChatFormatting.YELLOW), false);
    }

    @SubscribeEvent
    public void aoTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        ticks++;
        MinecraftServer s = ev.getServer();
        if (ticks < INTERVALO) return;
        ticks = 0;
        int n = 0;
        for (ServerLevel nivel : s.getAllLevels()) {
            List<Entity> itens = new ArrayList<>();
            for (Entity e : nivel.getAllEntities()) if (e instanceof ItemEntity item && item.getAge() >= IDADE_MINIMA) itens.add(e);
            for (Entity e : itens) e.discard();
            n += itens.size();
        }
        if (n > 0) avisar(s, "Limpeza: " + n + (n == 1 ? " item esquecido no chão foi removido." : " itens esquecidos no chão foram removidos."));
        FrxWorld.LOG.info("Limpeza automática: {} itens removidos do chão.", n);
    }
}
