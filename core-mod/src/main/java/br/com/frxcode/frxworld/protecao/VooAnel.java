package br.com.frxcode.frxworld.protecao;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Anel de Voo com limite. Em vez de gastar o recurso da classe (Guerreiro e Monge começam a fúria e o chi zerados e só
 * enchem lutando; o Mago perderia mana das magias), todo mundo tem o mesmo **fôlego de voo**:
 * - 90 s de voo com o fôlego cheio; enche de novo em 45 s com os pés no chão. Zerou: cai devagar e só volta a voar com 25%.
 * - Combate: bater ou apanhar de uma criatura (ou jogador) corta o voo por 5 s.
 * - Não funciona na Torre, em X1, na Fenda nem com um chefão a até 48 blocos (masmorra com chefe vivo, Titã, a Morte).
 * O mod do anel reativa o "pode voar" a cada tick, então aqui só se derruba o voo em andamento, sem brigar com ele.
 */
public class VooAnel {
    private static final double GASTO = 100.0 / (90 * 20), RECARGA = 100.0 / (45 * 20), VOLTA = 25;
    private static final long COMBATE_MS = 5000;
    private static final Map<UUID, Double> FOLEGO = new HashMap<>();
    private static final Map<UUID, Long> COMBATE = new HashMap<>();
    private static final Map<UUID, Boolean> CANSADO = new HashMap<>();
    private static final Map<UUID, Long> AVISO = new HashMap<>();
    private static final Map<UUID, Boolean> CHEFE = new HashMap<>();
    private static final Map<UUID, Boolean> ANEL = new HashMap<>();

    private static boolean temAnel(Player p) {
        IItemHandlerModifiable postos = AneisUnicos.aneis(p);
        if (postos == null) return false;
        for (int i = 0; i < postos.getSlots(); i++) if (AneisUnicos.ehVoo(postos.getStackInSlot(i))) return true;
        return false;
    }

    private static boolean chefePerto(ServerPlayer p) {
        return !p.level().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(48), e -> e.isAlive() && !(e instanceof Player)
                && (br.com.frxcode.frxworld.aventura.Chefes.ehChefao(e) || e.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES) || e.getMaxHealth() >= 200)).isEmpty();
    }

    /** Por que não pode voar agora (null = pode). */
    private static String bloqueio(ServerPlayer p, long agora) {
        if (br.com.frxcode.frxworld.aventura.Torre.naTorre(p)) return "O voo não funciona na Torre.";
        if (br.com.frxcode.frxworld.aventura.X1.emDuelo(p)) return "O voo não funciona em X1.";
        if (br.com.frxcode.frxworld.aventura.Fenda.dentro(p)) return "O voo não funciona na Fenda.";
        if (COMBATE.getOrDefault(p.getUUID(), 0L) > agora) return "Em combate: o voo volta em " + ((COMBATE.get(p.getUUID()) - agora) / 1000 + 1) + " s.";
        if (CANSADO.getOrDefault(p.getUUID(), false)) return "Sem fôlego de voo: descanse no chão (" + (int) Math.floor(FOLEGO.getOrDefault(p.getUUID(), 0.0)) + "%).";
        if (CHEFE.getOrDefault(p.getUUID(), false)) return "Um chefão por perto: o voo não funciona aqui.";
        return null;
    }

    private static void barra(ServerPlayer p, String texto, ChatFormatting cor, long agora, long intervalo) {
        if (agora - AVISO.getOrDefault(p.getUUID(), 0L) < intervalo) return;
        AVISO.put(p.getUUID(), agora);
        p.displayClientMessage(Component.literal(texto).withStyle(cor), true);
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        UUID id = p.getUUID();
        double f = FOLEGO.getOrDefault(id, 100.0);
        boolean voando = p.getAbilities().flying;
        if (!voando && f >= 100 && !CANSADO.getOrDefault(id, false)) return;   // nada a fazer: parado e cheio
        if (p.tickCount % 20 == 0 || !ANEL.containsKey(id)) {
            boolean tem = temAnel(p);
            ANEL.put(id, tem);
            if (!tem) { FOLEGO.put(id, 100.0); CANSADO.remove(id); }
            CHEFE.put(id, tem && voando && chefePerto(p));
        }
        if (!ANEL.get(id)) return;   // voo de outra fonte (forma de ave do Druida, por exemplo): não mexe
        long agora = System.currentTimeMillis();
        if (voando) {
            String motivo = bloqueio(p, agora);
            if (motivo != null) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
                barra(p, motivo, ChatFormatting.RED, agora, 1000);
                return;
            }
            f = Math.max(0, f - GASTO);
            FOLEGO.put(id, f);
            if (f <= 0) {
                CANSADO.put(id, true);
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
                barra(p, "Seu fôlego de voo acabou. Descanse no chão para voar de novo.", ChatFormatting.RED, agora, 0);
                return;
            }
            barra(p, "🪽 Fôlego de voo: " + (int) Math.ceil(f) + "%", f < 20 ? ChatFormatting.RED : f < 50 ? ChatFormatting.GOLD : ChatFormatting.AQUA, agora, 1000);
        } else if (p.onGround() || p.isInWater()) {
            f = Math.min(100, f + RECARGA);
            FOLEGO.put(id, f);
            if (CANSADO.getOrDefault(id, false) && f >= VOLTA) {
                CANSADO.remove(id);
                barra(p, "🪽 Fôlego recuperado: já dá para voar de novo.", ChatFormatting.AQUA, agora, 0);
            }
        }
    }

    /** Bater ou apanhar de criatura ou jogador corta o voo por 5 s. */
    @SubscribeEvent
    public void combate(LivingHurtEvent ev) {
        long ate = System.currentTimeMillis() + COMBATE_MS;
        if (ev.getEntity() instanceof ServerPlayer vitima && ev.getSource().getEntity() instanceof LivingEntity) COMBATE.put(vitima.getUUID(), ate);
        if (ev.getSource().getEntity() instanceof ServerPlayer atacante && ev.getEntity() != atacante) COMBATE.put(atacante.getUUID(), ate);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        UUID id = ev.getEntity().getUUID();
        COMBATE.remove(id); AVISO.remove(id); CHEFE.remove(id); ANEL.remove(id);
    }
}
