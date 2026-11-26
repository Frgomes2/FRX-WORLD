package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Paladino com escudo (qualquer escudo, inclusive os do Shield Expansion):
 * - Escudo na mão (principal ou secundária): leva 15% menos dano.
 * - Bloquear com o escudo: devolve 30% do dano bloqueado a quem atacou e cura meio coração (no máximo 2 vezes por segundo).
 */
public class EscudoPaladino {
    private static final float MENOS_DANO = 0.15f, REFLEXO = 0.30f;
    private static final Map<UUID, Long> CURA = new HashMap<>();

    static boolean paladino(ServerPlayer p) { return Progressao.temDados(p) && "PALADINO".equals(Progressao.dados(p).classe()); }

    static boolean comEscudo(ServerPlayer p) { return escudo(p.getMainHandItem()) || escudo(p.getOffhandItem()); }

    private static boolean escudo(ItemStack s) { return !s.isEmpty() && s.canPerformAction(ToolActions.SHIELD_BLOCK); }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoLevarDano(LivingHurtEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.getAmount() <= 0 || !paladino(p) || !comEscudo(p)) return;
        ev.setAmount(ev.getAmount() * (1 - MENOS_DANO));
    }

    @SubscribeEvent
    public void aoBloquear(ShieldBlockEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !paladino(p) || ev.getBlockedDamage() <= 0) return;
        if (ev.getDamageSource().getEntity() instanceof LivingEntity atacante && atacante != p && atacante.isAlive()
                && ev.getDamageSource().getDirectEntity() == atacante) {   // só golpe corpo a corpo (flecha não volta)
            atacante.hurt(p.damageSources().thorns(p), ev.getBlockedDamage() * REFLEXO);
            p.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, atacante.getX(), atacante.getY() + atacante.getBbHeight() * 0.6, atacante.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        }
        long agora = System.currentTimeMillis();
        if (agora - CURA.getOrDefault(p.getUUID(), 0L) >= 500) {
            CURA.put(p.getUUID(), agora);
            p.heal(1f);
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.5f);
        }
    }
}
