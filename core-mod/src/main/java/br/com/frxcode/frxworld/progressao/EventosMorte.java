package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.efeitos.Efeitos;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Punição por morte (tabela da aba Progressão do painel):
 * perda de XP (pode descer de nível nas faixas altas), desgaste do equipamento e efeito Ferido ao renascer.
 * Perda de ouro e itens no túmulo entram em fases futuras.
 */
public class EventosMorte {

    private record Pendente(long xpPerdida, boolean desceuNivel, int feridoTicks) {}

    private final Map<UUID, Pendente> aoRenascer = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoMorrer(LivingDeathEvent evento) {
        if (evento.isCanceled() || !(evento.getEntity() instanceof ServerPlayer jogador)) return;

        DadosJogador d = Progressao.dados(jogador);
        ConfigJogo.FaixaMorte faixa = ConfigJogo.faixaMorte(d.nivel());

        // 1. XP
        long perda = Math.round(faixa.xpPct() / 100.0 * Progressao.xpParaProximo(d.nivel()));
        int nivel = d.nivel();
        long xp = d.xp();
        boolean desceu = false;
        if (perda > 0) {
            if (xp >= perda) {
                xp -= perda;
            } else if (faixa.podePerderNivel() && nivel > 1) {
                nivel--;
                xp = Math.max(0, Progressao.xpParaProximo(nivel) - (perda - xp));
                desceu = true;
            } else {
                perda = xp;
                xp = 0;
            }
            d.definir(nivel, xp);
        }

        // 2. Durabilidade do que está equipado (cai junto com os itens, se o mundo não tiver keepInventory)
        if (faixa.durabilidadePct() > 0) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack item = jogador.getItemBySlot(slot);
                if (item.isEmpty() || !item.isDamageableItem()) continue;
                int desgaste = (int) Math.round(item.getMaxDamage() * faixa.durabilidadePct() / 100.0);
                item.setDamageValue(Math.min(item.getMaxDamage() - 1, item.getDamageValue() + desgaste));
            }
        }

        aoRenascer.put(jogador.getUUID(), new Pendente(perda, desceu, (int) Math.round(faixa.feridoMin() * 60 * 20)));
        Progressao.enviarAoPainel(jogador, true);
    }

    @SubscribeEvent
    public void renascer(PlayerEvent.PlayerRespawnEvent evento) {
        if (evento.isEndConquered() || !(evento.getEntity() instanceof ServerPlayer jogador)) return;
        Pendente p = aoRenascer.remove(jogador.getUUID());
        if (p == null) return;

        if (p.feridoTicks() > 0) {
            jogador.addEffect(new MobEffectInstance(Efeitos.FERIDO.get(), p.feridoTicks(), 0, false, true, true));
        }
        if (p.xpPerdida() > 0 || p.feridoTicks() > 0) {
            Component msg = Component.literal("Você morreu. ").withStyle(ChatFormatting.RED);
            if (p.xpPerdida() > 0) msg = msg.copy().append(Component.literal("-" + p.xpPerdida() + " XP").withStyle(ChatFormatting.GRAY));
            if (p.desceuNivel()) msg = msg.copy().append(Component.literal(" (desceu de nível)").withStyle(ChatFormatting.DARK_RED));
            if (p.feridoTicks() > 0) {
                msg = msg.copy().append(Component.literal("  Ferido por " + (p.feridoTicks() / 1200) + " min.").withStyle(ChatFormatting.GOLD));
            }
            jogador.sendSystemMessage(msg);
        }
        Progressao.atualizarNome(jogador);
        Rede.sincronizar(jogador);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent evento) {
        if (evento.getEntity() instanceof ServerPlayer jogador) {
            aoRenascer.remove(jogador.getUUID());
            Progressao.enviarAoPainel(jogador, false);
        }
    }
}
