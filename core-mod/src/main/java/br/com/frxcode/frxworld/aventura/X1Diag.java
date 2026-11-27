package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * DIAGNÓSTICO TEMPORÁRIO do bug "X1 sem dano" (27/11/2026): só registra no log golpes de jogador em jogador durante um duelo,
 * em cada prioridade, para saber em que ponto o golpe é cancelado ou zerado. Remover depois de achar a causa.
 */
public class X1Diag {
    private static boolean duelo(Object a, Object b) {
        return a instanceof Player pa && b instanceof Player pb && pa != pb && X1.danoEmJogadores(pa) != null && X1.danoEmJogadores(pb) != null;
    }

    private static void log(String etapa, Object autor, Object vitima, String fonte, float valor, boolean cancelado) {
        FrxWorld.LOG.info("[X1DIAG] {} · {} -> {} · fonte={} · valor={} · cancelado={}", etapa,
                autor instanceof Player p ? p.getGameProfile().getName() : "?", vitima instanceof Player p ? p.getGameProfile().getName() : "?", fonte, valor, cancelado);
    }

    // golpe com a mão (antes de qualquer dano)
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void golpeInicio(AttackEntityEvent ev) { if (duelo(ev.getEntity(), ev.getTarget())) log("golpe:inicio", ev.getEntity(), ev.getTarget(), "mão", 0, ev.isCanceled()); }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void golpeFim(AttackEntityEvent ev) { if (duelo(ev.getEntity(), ev.getTarget())) log("golpe:fim", ev.getEntity(), ev.getTarget(), "mão", 0, ev.isCanceled()); }

    private static void ataque(String etapa, LivingAttackEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer v && duelo(ev.getSource().getEntity(), v))
            log(etapa, ev.getSource().getEntity(), v, ev.getSource().getMsgId(), ev.getAmount(), ev.isCanceled());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true) public void a1(LivingAttackEvent ev) { ataque("ataque:HIGHEST", ev); }
    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true) public void a2(LivingAttackEvent ev) { ataque("ataque:HIGH", ev); }
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true) public void a3(LivingAttackEvent ev) { ataque("ataque:NORMAL", ev); }
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true) public void a4(LivingAttackEvent ev) { ataque("ataque:LOW", ev); }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true) public void a5(LivingAttackEvent ev) { ataque("ataque:LOWEST", ev); }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void h1(LivingHurtEvent ev) { if (duelo(ev.getSource().getEntity(), ev.getEntity())) log("ferida:HIGHEST", ev.getSource().getEntity(), ev.getEntity(), ev.getSource().getMsgId(), ev.getAmount(), ev.isCanceled()); }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void h2(LivingHurtEvent ev) { if (duelo(ev.getSource().getEntity(), ev.getEntity())) log("ferida:LOWEST", ev.getSource().getEntity(), ev.getEntity(), ev.getSource().getMsgId(), ev.getAmount(), ev.isCanceled()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void d1(LivingDamageEvent ev) { if (duelo(ev.getSource().getEntity(), ev.getEntity())) log("dano:HIGHEST", ev.getSource().getEntity(), ev.getEntity(), ev.getSource().getMsgId(), ev.getAmount(), ev.isCanceled()); }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void d2(LivingDamageEvent ev) { if (duelo(ev.getSource().getEntity(), ev.getEntity())) log("dano:LOWEST", ev.getSource().getEntity(), ev.getEntity(), ev.getSource().getMsgId(), ev.getAmount(), ev.isCanceled()); }
}
