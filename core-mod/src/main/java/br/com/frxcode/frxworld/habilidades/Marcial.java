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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monge: luta corpo a corpo de mão vazia. Só vale para quem é da classe E está sem nada na mão principal;
 * com qualquer item na mão (ou em qualquer outra classe) o combate é o de sempre.
 *
 *  - Punho: o soco soma 2,5 + 0,3 × nível de dano (cresce com o talento de dano corpo a corpo).
 *  - Combo: socos seguidos (até 2,5 s entre um e outro) sobem de força; o 4º é o golpe final (empurra e atordoa).
 *  - Chi: cada soco rende 10 de chi (20 no golpe final). O chi não volta sozinho e esfria fora de combate.
 *  - Corpo leve: cada peça de armadura a menos dá esquiva e reduz o dano recebido.
 *  - Mecânicas especiais: frx:palma, frx:rajada, frx:meditar, frx:pontos, frx:vazio e frx:cem.
 */
public class Marcial {
    private static final class Combo { int golpes; long ultimoMs; }
    private record Rajada(UUID dono, UUID alvo, ServerLevel nivel, HabilidadeDef h, double mult, int[] restam) { }
    private record Pontos(UUID dono, UUID alvo, ServerLevel nivel, HabilidadeDef h, double mult, int fimTick, double[] acumulado) { }
    private record Cem(UUID dono, ServerLevel nivel, HabilidadeDef h, double mult, int fimTick) { }
    private record Medita(UUID dono, ServerLevel nivel, HabilidadeDef h, int fimTick) { }
    private record Vazio(long fimMs, double chance) { }

    private static final Map<UUID, Combo> combos = new ConcurrentHashMap<>();
    private static final Map<UUID, Vazio> vazios = new ConcurrentHashMap<>();
    private static final List<Rajada> rajadas = new CopyOnWriteArrayList<>();
    private static final List<Pontos> pontos = new CopyOnWriteArrayList<>();
    private static final List<Cem> cems = new CopyOnWriteArrayList<>();
    private static final List<Medita> meditas = new CopyOnWriteArrayList<>();
    private static boolean ondaDeChoque;

    public Marcial() {}

    private static void aviso(ServerPlayer p, String texto, ChatFormatting cor) { p.displayClientMessage(Component.literal(texto).withStyle(cor), true); }
    private static int ticksDe(HabilidadeDef h, int minimo) { return (int) Math.max(minimo, h.duracao() * 20); }
    static boolean monge(ServerPlayer p) { return Progressao.temDados(p) && "MONGE".equals(Progressao.dados(p).classe()); }

    private static int pecasLivres(ServerPlayer p) {
        int n = 0;
        for (EquipmentSlot s : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) if (p.getItemBySlot(s).isEmpty()) n++;
        return n;
    }

    // ------------------------------------------------------------------ o soco

    private static float soco(ServerPlayer p, LivingEntity alvo, float base) {
        Combo c = combos.computeIfAbsent(p.getUUID(), k -> new Combo());
        long agora = System.currentTimeMillis();
        if (agora - c.ultimoMs > 2500) c.golpes = 0;
        c.golpes++;
        c.ultimoMs = agora;
        int passo = (c.golpes - 1) % 4 + 1;
        boolean golpeFinal = passo == 4;
        boolean portao = Despertar.portaoAtivo(p);
        double punho = (2.5 + Progressao.dados(p).nivel() * 0.3) * Talentos.fator(p, Talentos.Atributo.DANO_FISICO);
        double dano = (base + punho) * (1 + 0.1 * (passo - 1)) * (golpeFinal ? 1.6 : 1) * (portao ? 2 : 1);
        Habilidades.darRecurso(p, golpeFinal ? 20 : 10);
        ServerLevel n = p.serverLevel();
        Vec3 meio = alvo.getBoundingBox().getCenter();
        if (golpeFinal) {
            Vec3 dir = alvo.position().subtract(p.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() > 0.01) { dir = dir.normalize(); alvo.setDeltaMovement(dir.x * 0.9, 0.35, dir.z * 0.9); alvo.hurtMarked = true; }
            alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 5));
            n.sendParticles(ParticleTypes.EXPLOSION, meio.x, meio.y, meio.z, 1, 0, 0, 0, 0);
            n.sendParticles(ParticleTypes.CRIT, meio.x, meio.y, meio.z, 18, 0.3, 0.3, 0.3, 0.4);
            n.playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.8f);
            aviso(p, "Golpe final! " + Math.round(dano) + " de dano", ChatFormatting.GOLD);
        } else {
            n.sendParticles(ParticleTypes.CRIT, meio.x, meio.y, meio.z, 4 + passo * 2, 0.2, 0.2, 0.2, 0.2);
            n.playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 0.9f + passo * 0.15f);
            aviso(p, "Combo " + passo + " de 4", ChatFormatting.AQUA);
        }
        if (portao && !ondaDeChoque) {
            // Portão Interior: cada soco estoura em volta do alvo
            ondaDeChoque = true;
            try {
                for (LivingEntity e : n.getEntitiesOfClass(LivingEntity.class, alvo.getBoundingBox().inflate(2.5), x -> x != alvo && x.isAlive() && Habilidades.inimigo(p, x))) {
                    e.invulnerableTime = 0;
                    e.hurt(p.damageSources().indirectMagic(p, p), (float) (dano * 0.5));
                }
            } finally { ondaDeChoque = false; }
            n.sendParticles(ParticleTypes.SWEEP_ATTACK, meio.x, meio.y, meio.z, 3, 0.8, 0.2, 0.8, 0);
        }
        return (float) dano;
    }

    // ------------------------------------------------------------------ frx:palma — empurrão forte

    static boolean palma(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(3.5, h.alcance()), 0.9, false);
        if (alvo == null) { aviso(p, "Nenhum alvo ao alcance da palma.", ChatFormatting.GRAY); return false; }
        Habilidades.ferir(p, alvo, h.dano() * mult, h);
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() > 0.01) { dir = dir.normalize(); alvo.setDeltaMovement(dir.x * 1.6, 0.45, dir.z * 1.6); alvo.hurtMarked = true; }
        alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticksDe(h, 30), 2));
        ServerLevel n = p.serverLevel();
        Vec3 meio = alvo.getBoundingBox().getCenter();
        n.sendParticles(ParticleTypes.CLOUD, meio.x, meio.y, meio.z, 14, 0.3, 0.3, 0.3, 0.15);
        n.playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3f, 0.6f);
        p.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ------------------------------------------------------------------ frx:rajada — vários socos em sequência

    static boolean rajada(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(3.5, h.alcance()), 0.9, false);
        if (alvo == null) { aviso(p, "Nenhum alvo ao alcance.", ChatFormatting.GRAY); return false; }
        rajadas.removeIf(r -> r.dono().equals(p.getUUID()));
        rajadas.add(new Rajada(p.getUUID(), alvo.getUUID(), p.serverLevel(), h, mult, new int[]{Math.max(3, h.potencia())}));
        return true;
    }

    private static void tickRajada(MinecraftServer s, Rajada r) {
        if (s.getTickCount() % 3 != 0) return;
        ServerPlayer p = s.getPlayerList().getPlayer(r.dono());
        Entity e = r.nivel().getEntity(r.alvo());
        if (p == null || !p.isAlive() || p.serverLevel() != r.nivel() || !(e instanceof LivingEntity alvo) || !alvo.isAlive() || p.distanceTo(alvo) > Math.max(3.5, r.h().alcance()) + 3) { rajadas.remove(r); return; }
        boolean ultimo = --r.restam()[0] <= 0;
        Habilidades.ferir(p, alvo, r.h().dano() * r.mult() * (ultimo ? 2 : 1), r.h());
        p.swing(r.restam()[0] % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        Vec3 meio = alvo.getBoundingBox().getCenter();
        r.nivel().sendParticles(ParticleTypes.CRIT, meio.x, meio.y, meio.z, 8, 0.25, 0.3, 0.25, 0.3);
        r.nivel().playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.9f, 1.3f);
        if (ultimo) {
            Vec3 dir = alvo.position().subtract(p.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() > 0.01) { dir = dir.normalize(); alvo.setDeltaMovement(dir.x * 1.0, 0.4, dir.z * 1.0); alvo.hurtMarked = true; }
            r.nivel().playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.7f);
            rajadas.remove(r);
        }
    }

    // ------------------------------------------------------------------ frx:meditar — cura e chi ao longo do tempo

    static boolean meditar(ServerPlayer p, HabilidadeDef h) {
        int dur = ticksDe(h, 60);
        meditas.removeIf(m -> m.dono().equals(p.getUUID()));
        meditas.add(new Medita(p.getUUID(), p.serverLevel(), h, p.server.getTickCount() + dur));
        for (var ruim : List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.CONFUSION)) p.removeEffect(ruim);
        p.clearFire();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 2, false, false));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.6f);
        aviso(p, "Você respira fundo: vida e energia voltam por " + dur / 20 + " s.", ChatFormatting.AQUA);
        return true;
    }

    private static void tickMedita(MinecraftServer s, Medita m) {
        ServerPlayer p = s.getPlayerList().getPlayer(m.dono());
        if (p == null || !p.isAlive() || p.serverLevel() != m.nivel() || s.getTickCount() >= m.fimTick()) { meditas.remove(m); return; }
        if (s.getTickCount() % 10 != 0) return;
        p.heal((float) (Math.abs(m.h().dano()) * Talentos.fator(p, Talentos.Atributo.CURA) / 2));
        Habilidades.darRecurso(p, Math.max(1, m.h().potencia()));
        m.nivel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.4, p.getZ(), 4, 0.5, 0.1, 0.5, 0.01);
    }

    // ------------------------------------------------------------------ frx:pontos — pontos de pressão que estouram depois

    static boolean pontos(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(3.5, h.alcance()), 0.9, false);
        if (alvo == null) { aviso(p, "Nenhum alvo ao alcance.", ChatFormatting.GRAY); return false; }
        int dur = ticksDe(h, 60);
        pontos.removeIf(x -> x.dono().equals(p.getUUID()));
        pontos.add(new Pontos(p.getUUID(), alvo.getUUID(), p.serverLevel(), h, mult, p.server.getTickCount() + dur, new double[]{0}));
        alvo.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
        Vec3 meio = alvo.getBoundingBox().getCenter();
        p.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, meio.x, meio.y, meio.z, 14, 0.3, 0.4, 0.3, 0.1);
        p.serverLevel().playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1.6f);
        p.swing(InteractionHand.MAIN_HAND, true);
        aviso(p, "Pontos tocados: em " + dur / 20 + " s estouram com metade do dano que você causar até lá.", ChatFormatting.LIGHT_PURPLE);
        Habilidades.marcarCombate(p);
        return true;
    }

    private static void tickPontos(MinecraftServer s, Pontos x) {
        if (s.getTickCount() < x.fimTick()) return;
        pontos.remove(x);
        ServerPlayer p = s.getPlayerList().getPlayer(x.dono());
        if (p == null || !(x.nivel().getEntity(x.alvo()) instanceof LivingEntity alvo) || !alvo.isAlive()) return;
        double dano = x.h().dano() * x.mult() + x.acumulado()[0] * 0.5;
        Habilidades.ferir(p, alvo, dano, x.h());
        Vec3 meio = alvo.getBoundingBox().getCenter();
        x.nivel().sendParticles(ParticleTypes.EXPLOSION, meio.x, meio.y, meio.z, 2, 0.2, 0.2, 0.2, 0);
        x.nivel().sendParticles(ParticleTypes.ENCHANTED_HIT, meio.x, meio.y, meio.z, 30, 0.4, 0.5, 0.4, 0.3);
        x.nivel().playSound(null, alvo.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.6f);
        aviso(p, "Os pontos de pressão estouraram: " + Math.round(dano) + " de dano.", ChatFormatting.LIGHT_PURPLE);
    }

    // ------------------------------------------------------------------ frx:vazio — esquiva quase tudo por alguns segundos

    static boolean vazio(ServerPlayer p, HabilidadeDef h) {
        int dur = ticksDe(h, 40);
        double chance = Math.max(0.3, Math.min(1, h.potencia() * 0.1));
        vazios.put(p.getUUID(), new Vazio(System.currentTimeMillis() + dur * 50L, chance));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1));
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 1.8f);
        aviso(p, "Corpo Vazio: " + Math.round(chance * 100) + "% de esquiva por " + dur / 20 + " s.", ChatFormatting.AQUA);
        return true;
    }

    // ------------------------------------------------------------------ frx:cem — socos de chi em tudo o que estiver em volta

    static boolean cem(ServerPlayer p, HabilidadeDef h, double mult) {
        int dur = ticksDe(h, 80);
        cems.removeIf(c -> c.dono().equals(p.getUUID()));
        cems.add(new Cem(p.getUUID(), p.serverLevel(), h, mult, p.server.getTickCount() + dur));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 1));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.2f, 1.4f);
        aviso(p, "Cem Punhos: " + dur / 20 + " s de socos em tudo à sua volta.", ChatFormatting.GOLD);
        return true;
    }

    private static void tickCem(MinecraftServer s, Cem c) {
        ServerPlayer p = s.getPlayerList().getPlayer(c.dono());
        int agora = s.getTickCount();
        if (p == null || !p.isAlive() || p.serverLevel() != c.nivel()) { cems.remove(c); return; }
        double raio = Math.max(4, c.h().raio());
        ServerLevel n = c.nivel();
        List<LivingEntity> perto = n.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(raio, 3, raio), x -> x.isAlive() && Habilidades.inimigo(p, x) && x.distanceTo(p) <= raio + 0.5);
        if (agora >= c.fimTick()) {
            cems.remove(c);
            for (LivingEntity e : perto) {
                Habilidades.ferir(p, e, c.h().dano() * c.mult() * 3, c.h());
                Vec3 fora = e.position().subtract(p.position()).multiply(1, 0, 1);
                if (fora.lengthSqr() > 0.01) { fora = fora.normalize(); e.setDeltaMovement(fora.x * 1.3, 0.5, fora.z * 1.3); e.hurtMarked = true; }
            }
            n.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 1, p.getZ(), 6, raio / 2, 0.4, raio / 2, 0);
            n.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.2f);
            return;
        }
        if (agora % 3 != 0 || perto.isEmpty()) return;
        LivingEntity alvo = perto.get(ThreadLocalRandom.current().nextInt(perto.size()));
        Habilidades.ferir(p, alvo, c.h().dano() * c.mult(), c.h());
        Habilidades.linha(n, p.getEyePosition().add(0, -0.3, 0), alvo.getBoundingBox().getCenter(), ParticleTypes.CRIT);
        p.swing(agora % 6 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        n.playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8f, 1.5f);
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        for (Rajada r : rajadas) { try { tickRajada(s, r); } catch (Exception e) { rajadas.remove(r); } }
        for (Pontos x : pontos) { try { tickPontos(s, x); } catch (Exception e) { pontos.remove(x); } }
        for (Cem c : cems) { try { tickCem(s, c); } catch (Exception e) { cems.remove(c); } }
        for (Medita m : meditas) { try { tickMedita(s, m); } catch (Exception e) { meditas.remove(m); } }
    }

    /** Esquiva do Monge: 6% + 3,5% por peça de armadura a menos (até 20%); Corpo Vazio vale por cima. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoSerAtacado(LivingAttackEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || p.level().isClientSide() || ev.getSource().getEntity() == null || ev.getSource().getEntity() == p || !monge(p)) return;
        double chance = 0.06 + 0.035 * pecasLivres(p);
        Vazio v = vazios.get(p.getUUID());
        if (v != null) {
            if (System.currentTimeMillis() > v.fimMs()) vazios.remove(p.getUUID());
            else chance = Math.max(chance, v.chance());
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        ev.setCanceled(true);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.4, 0.3, 0.03);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8f, 1.7f);
        aviso(p, "Esquivou!", ChatFormatting.AQUA);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoFerir(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide() || ev.getAmount() <= 0) return;
        if (ev.getSource().getEntity() instanceof ServerPlayer p && p != vitima) {
            if (!Habilidades.emHabilidade && !ondaDeChoque && ev.getSource().getDirectEntity() == p && p.getMainHandItem().isEmpty() && monge(p)
                    && (vitima instanceof Player || Habilidades.inimigo(p, vitima)))
                ev.setAmount(soco(p, vitima, ev.getAmount()));
            for (Pontos x : pontos) if (x.dono().equals(p.getUUID()) && x.alvo().equals(vitima.getUUID())) x.acumulado()[0] += ev.getAmount();
        }
        // Corpo leve: cada peça de armadura a menos tira de 3% a 10% do dano (cresce com o nível)
        if (vitima instanceof ServerPlayer m && monge(m)) {
            double porPeca = Math.min(0.10, 0.03 + Progressao.dados(m).nivel() * 0.0015);
            ev.setAmount((float) (ev.getAmount() * (1 - porPeca * pecasLivres(m))));
        }
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        UUID id = ev.getEntity().getUUID();
        combos.remove(id);
        vazios.remove(id);
        rajadas.removeIf(r -> r.dono().equals(id));
        pontos.removeIf(x -> x.dono().equals(id));
        cems.removeIf(c -> c.dono().equals(id));
        meditas.removeIf(m -> m.dono().equals(id));
    }
}
