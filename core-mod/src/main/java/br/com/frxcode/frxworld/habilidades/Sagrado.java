package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Paladino: a aura da classe e as mecânicas especiais sagradas (efeitos frx:voto, frx:sacrificio, frx:intervencao e frx:juizo).
 * Aura de Proteção (passiva): jogadores a até 6 blocos de um Paladino levam 10% menos dano; o próprio Paladino leva 15% menos de mortos-vivos.
 */
public class Sagrado {
    private record Voto(UUID paladino, long fimMs, double fracao) { }
    private record Juizo(UUID dono, ServerLevel nivel, HabilidadeDef h, double mult, int fimTick) { }

    private static final Map<UUID, Voto> votos = new ConcurrentHashMap<>();
    private static final List<Juizo> juizos = new CopyOnWriteArrayList<>();
    private static boolean repassando;

    public Sagrado() {}

    private static void aviso(ServerPlayer p, String texto, ChatFormatting cor) { p.displayClientMessage(Component.literal(texto).withStyle(cor), true); }
    private static int ticksDe(HabilidadeDef h, int minimo) { return (int) Math.max(minimo, h.duracao() * 20); }
    private static boolean paladino(ServerPlayer p) { return Progressao.temDados(p) && "PALADINO".equals(Progressao.dados(p).classe()); }

    private static List<ServerPlayer> aliadosPerto(ServerPlayer p, double raio, boolean comEle) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer o : p.serverLevel().players()) if (o.isAlive() && !o.isSpectator() && (comEle || o != p) && o.distanceTo(p) <= raio) l.add(o);
        return l;
    }

    private static void anel(ServerLevel n, Vec3 c, double raio) {
        for (int i = 0; i < 32; i++) {
            double ang = Math.PI * 2 * i / 32;
            n.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(ang) * raio, c.y + 0.2, c.z + Math.sin(ang) * raio, 1, 0, 0.05, 0, 0);
        }
    }

    // ------------------------------------------------------------------ frx:voto — parte do dano dos aliados vem para o Paladino

    static boolean voto(ServerPlayer p, HabilidadeDef h) {
        double fracao = Math.max(0.1, Math.min(0.8, h.potencia() * 0.1));
        int dur = ticksDe(h, 100);
        List<ServerPlayer> alvos = new ArrayList<>();
        LivingEntity mira = Habilidades.mirar(p, Math.max(6, h.alcance()), 0.9, true);
        if (mira instanceof ServerPlayer um) alvos.add(um);
        else alvos.addAll(aliadosPerto(p, Math.max(4, h.raio()), false));
        if (alvos.isEmpty()) { aviso(p, "Nenhum aliado por perto para proteger.", ChatFormatting.GRAY); return false; }
        long fim = System.currentTimeMillis() + dur * 50L;
        ServerLevel n = p.serverLevel();
        for (ServerPlayer a : alvos) {
            votos.put(a.getUUID(), new Voto(p.getUUID(), fim, fracao));
            Habilidades.linha(n, p.getEyePosition(), a.getBoundingBox().getCenter(), ParticleTypes.END_ROD);
            a.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            aviso(a, p.getGameProfile().getName() + " jurou proteger você: " + Math.round(fracao * 100) + "% do dano vai para ele.", ChatFormatting.YELLOW);
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 0));
        n.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1f, 1.4f);
        aviso(p, "Voto feito: você recebe " + Math.round(fracao * 100) + "% do dano de " + alvos.size() + (alvos.size() == 1 ? " aliado." : " aliados."), ChatFormatting.YELLOW);
        return true;
    }

    // ------------------------------------------------------------------ frx:sacrificio — dá a própria vida para curar o grupo

    static boolean sacrificio(ServerPlayer p, HabilidadeDef h, double mult) {
        List<ServerPlayer> alvos = aliadosPerto(p, Math.max(4, h.raio()), false);
        if (alvos.isEmpty()) { aviso(p, "Não há aliados por perto para receber o seu sacrifício.", ChatFormatting.GRAY); return false; }
        float gasto = Math.min(p.getHealth() - 1, p.getHealth() * Math.max(1, Math.min(8, h.potencia())) * 0.1f);
        if (gasto < 1) { aviso(p, "Você não tem vida para dar.", ChatFormatting.RED); return false; }
        p.setHealth(p.getHealth() - gasto);
        float cura = (float) (gasto * 1.5 + Math.abs(h.dano()) * mult);
        ServerLevel n = p.serverLevel();
        for (ServerPlayer a : alvos) {
            a.heal(cura);
            for (var ruim : List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.BLINDNESS)) a.removeEffect(ruim);
            a.clearFire();
            Habilidades.linha(n, p.getBoundingBox().getCenter(), a.getBoundingBox().getCenter(), ParticleTypes.HEART);
            n.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + 1.4, a.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
        }
        n.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
        n.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.5f);
        aviso(p, "Você deu " + Math.round(gasto) + " de vida: " + alvos.size() + (alvos.size() == 1 ? " aliado curado" : " aliados curados") + " em " + Math.round(cura) + " e limpos.", ChatFormatting.GOLD);
        return true;
    }

    // ------------------------------------------------------------------ frx:intervencao — voa até o aliado e toma os inimigos dele

    static boolean intervencao(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity mira = Habilidades.mirar(p, Math.max(6, h.alcance()), 1.2, true);
        if (!(mira instanceof ServerPlayer a)) { aviso(p, "Mire num aliado.", ChatFormatting.GRAY); return false; }
        ServerLevel n = p.serverLevel();
        Vec3 de = p.position();
        Vec3 lado = a.position().subtract(de).multiply(1, 0, 1);
        Vec3 chegada = a.position().subtract(lado.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : lado.normalize());
        Habilidades.linha(n, de.add(0, 1, 0), a.position().add(0, 1, 0), ParticleTypes.END_ROD);
        p.teleportTo(chegada.x, a.getY(), chegada.z);
        p.fallDistance = 0;
        int dur = ticksDe(h, 80);
        for (ServerPlayer x : List.of(p, a)) {
            x.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, dur, Math.max(0, h.potencia() - 1)));
            x.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 0));
        }
        double raio = Math.max(4, h.raio());
        int tomados = 0;
        for (Mob m : n.getEntitiesOfClass(Mob.class, a.getBoundingBox().inflate(raio, 3, raio), e -> e.isAlive() && Habilidades.inimigo(p, e))) {
            m.setTarget(p);
            m.setLastHurtByMob(p);
            if (h.dano() > 0) Habilidades.ferir(p, m, h.dano() * mult, h);
            Vec3 fora = m.position().subtract(a.position()).multiply(1, 0, 1);
            if (fora.lengthSqr() > 0.01) { fora = fora.normalize(); m.setDeltaMovement(fora.x * 0.8, 0.35, fora.z * 0.8); m.hurtMarked = true; }
            tomados++;
        }
        anel(n, a.position(), raio);
        n.playSound(null, a.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.3f, 0.8f);
        n.playSound(null, a.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.6f);
        aviso(a, p.getGameProfile().getName() + " veio em seu socorro!", ChatFormatting.YELLOW);
        aviso(p, "Você chegou a " + a.getGameProfile().getName() + (tomados > 0 ? " e tomou " + tomados + (tomados == 1 ? " inimigo" : " inimigos") + " para si." : "."), ChatFormatting.YELLOW);
        Habilidades.marcarCombate(p);
        return true;
    }

    // ------------------------------------------------------------------ frx:juizo — coluna de luz que fere e cura a cada segundo

    static boolean juizo(ServerPlayer p, HabilidadeDef h, double mult) {
        int dur = ticksDe(h, 100);
        juizos.removeIf(j -> j.dono().equals(p.getUUID()));
        juizos.add(new Juizo(p.getUUID(), p.serverLevel(), h, mult, p.server.getTickCount() + dur));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 2));
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.8f);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5f, 0.7f);
        aviso(p, "Juízo Final: a luz desce com você por " + dur / 20 + " s.", ChatFormatting.GOLD);
        return true;
    }

    private static void tickJuizo(MinecraftServer s, Juizo j) {
        ServerPlayer p = s.getPlayerList().getPlayer(j.dono());
        int agora = s.getTickCount();
        if (p == null || !p.isAlive() || p.serverLevel() != j.nivel() || agora >= j.fimTick()) { juizos.remove(j); return; }
        ServerLevel n = j.nivel();
        double raio = Math.max(4, j.h().raio());
        if (agora % 5 == 0) {
            anel(n, p.position(), raio);
            for (int y = 0; y < 10; y++) n.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + y * 0.8, p.getZ(), 2, 0.15, 0.2, 0.15, 0.01);
        }
        if ((j.fimTick() - agora) % 20 != 0) return;
        boolean ultimo = j.fimTick() - agora <= 20;
        int raios = 0;
        for (LivingEntity e : n.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(raio, 4, raio), x -> x.isAlive() && x != p && x.distanceTo(p) <= raio + 0.5)) {
            if (e instanceof ServerPlayer a) {
                a.heal((float) Math.max(1, j.h().potencia()));
                n.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + 1.6, a.getZ(), 2, 0.3, 0.2, 0.3, 0);
            } else if (Habilidades.inimigo(p, e)) {
                Habilidades.ferir(p, e, j.h().dano() * j.mult() * (ultimo ? 2 : 1), j.h());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                if (e.getMobType() == MobType.UNDEAD) e.setSecondsOnFire(3);
                if (raios++ < 3) {
                    LightningBolt raio2 = EntityType.LIGHTNING_BOLT.create(n);
                    if (raio2 != null) { raio2.moveTo(e.position()); raio2.setVisualOnly(true); n.addFreshEntity(raio2); }
                }
            }
        }
        p.heal((float) Math.max(1, j.h().potencia()));
        if (ultimo) n.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.4f);
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || juizos.isEmpty()) return;
        for (Juizo j : juizos) {
            try { tickJuizo(ev.getServer(), j); } catch (Exception e) { juizos.remove(j); }
        }
    }

    // ------------------------------------------------------------------ dano: voto e aura

    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoFerir(LivingHurtEvent ev) {
        if (repassando || !(ev.getEntity() instanceof ServerPlayer vitima) || vitima.level().isClientSide() || ev.getAmount() <= 0) return;
        float dano = ev.getAmount();
        // Aura de Proteção: algum Paladino a até 6 blocos (que não a própria vítima)
        boolean daPropria = paladino(vitima);
        for (ServerPlayer o : vitima.serverLevel().players()) {
            if (o != vitima && o.isAlive() && o.distanceTo(vitima) <= 6 && paladino(o)) { dano *= 0.9f; break; }
        }
        if (daPropria && ev.getSource().getEntity() instanceof LivingEntity agressor && agressor.getMobType() == MobType.UNDEAD) dano *= 0.85f;
        // Voto do Guardião: parte do dano vai para o Paladino (ele sente 60% do que tomou para si)
        Voto v = votos.get(vitima.getUUID());
        if (v != null) {
            ServerPlayer pal = vitima.server.getPlayerList().getPlayer(v.paladino());
            if (System.currentTimeMillis() > v.fimMs() || pal == null || !pal.isAlive() || pal.serverLevel() != vitima.serverLevel() || pal.distanceTo(vitima) > 32) votos.remove(vitima.getUUID());
            else {
                float desviado = dano * (float) v.fracao();
                dano -= desviado;
                repassando = true;
                try { pal.hurt(pal.damageSources().magic(), Math.min(desviado * 0.6f, Math.max(0, pal.getHealth() - 1))); } finally { repassando = false; }
                vitima.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, vitima.getX(), vitima.getY() + 1, vitima.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
            }
        }
        ev.setAmount(dano);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        UUID id = ev.getEntity().getUUID();
        votos.remove(id);
        votos.values().removeIf(v -> v.paladino().equals(id));
        juizos.removeIf(j -> j.dono().equals(id));
    }
}
