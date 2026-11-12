package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.rede.NumeroDano;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Sensação de combate:
 * - números de dano saltando das criaturas (quando um jogador bate ou apanha);
 * - fases dos chefes: aos 66% e aos 33% da vida o chefe enfurece, ganha força e todos por perto são avisados.
 */
public class Combate {
    private static final String K_FASE = "frx_fase";

    public Combate() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void dano(LivingDamageEvent ev) {
        LivingEntity alvo = ev.getEntity();
        if (alvo.level().isClientSide() || ev.getAmount() <= 0.05f) return;
        boolean jogadorBate = ev.getSource().getEntity() instanceof ServerPlayer, jogadorApanha = alvo instanceof ServerPlayer;
        if (jogadorBate || jogadorApanha) {
            int tipo = jogadorApanha ? 2 : ev.getAmount() >= alvo.getMaxHealth() * 0.3f ? 1 : 0;
            Rede.CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> alvo), new NumeroDano(alvo.getId(), ev.getAmount(), tipo));
        }
        fases(alvo, ev.getAmount());
    }

    private static void fases(LivingEntity chefe, float dano) {
        String rank = Ranks.rank(chefe);
        if (!rank.equals(Ranks.CHEFE) && !rank.equals(Ranks.MUNDIAL)) return;
        float max = chefe.getMaxHealth(), resta = chefe.getHealth() - dano;
        if (max <= 0 || resta <= 0) return;
        CompoundTag d = chefe.getPersistentData();
        int fase = Math.max(1, d.getInt(K_FASE)), nova = resta <= max * 0.33f ? 3 : resta <= max * 0.66f ? 2 : 1;
        if (nova <= fase) return;
        d.putInt(K_FASE, nova);
        chefe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, true));
        if (nova >= 3) {
            chefe.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 0, false, true));
            chefe.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, true));
        }
        if (!(chefe.level() instanceof ServerLevel nivel)) return;
        nivel.playSound(null, chefe.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2f, nova == 3 ? 0.7f : 1f);
        String nome = chefe.getDisplayName().getString();
        for (ServerPlayer p : nivel.players()) {
            if (p.distanceToSqr(chefe) > 48 * 48) continue;
            Sistema.avisar(p, "Fase " + nova + " — " + nome, nova == 2 ? "O chefe enfureceu: está mais rápido!" : "Fúria final: mais forte e mais resistente. Acabem com ele!", Sistema.VERMELHO);
        }
    }
}
