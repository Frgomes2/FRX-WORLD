package br.com.frxcode.frxworld.habilidades;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mecânicas especiais de habilidade, escolhidas pelo campo "efeito" com o prefixo frx:
 * (puxar, provocar, salto, fenda, executar, vampirismo, refletir, lancar, explosiva, ricochete, marca, gancho,
 * leque, voleio, perfurar, armadilha, congelar, misseis, escudo_mana, trocar, sifao, zona, singularidade).
 * Os números (dano, alcance, raio, duração, potência) continuam vindo do painel.
 */
public class Especiais {
    private record Salto(HabilidadeDef h, double mult, int inicio) { }
    private record Aura(long fimMs, double fracao) { }

    private static final Map<UUID, Salto> saltos = new ConcurrentHashMap<>();
    private static final Map<UUID, Aura> vampiros = new ConcurrentHashMap<>();
    private static final Map<UUID, Aura> espelhos = new ConcurrentHashMap<>();
    private static final Set<UUID> reembolsos = ConcurrentHashMap.newKeySet();

    private record Marca(UUID dono, long fimMs, double fracao) { }
    private record Armadilha(UUID dono, ServerLevel nivel, Vec3 pos, HabilidadeDef h, double mult, int fimTick) { }
    private static final Map<UUID, Marca> marcas = new ConcurrentHashMap<>();
    private static final List<Armadilha> armadilhas = new java.util.concurrent.CopyOnWriteArrayList<>();
    private record Missil(UUID dono, UUID alvo, int tick, HabilidadeDef h, double mult) { }
    private record Zona(UUID dono, ServerLevel nivel, Vec3 pos, HabilidadeDef h, double mult, int inicio, int fim, boolean puxa) { }
    private static final List<Missil> misseis = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<Zona> zonas = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<UUID, Aura> barreiras = new ConcurrentHashMap<>();
    /** Jogadores que não tomam dano de queda até este tick (gancho, voleio). */
    private static final Map<UUID, Integer> semQueda = new ConcurrentHashMap<>();

    /** null = não é um efeito especial conhecido (segue o tipo normal). */
    static Boolean executar(ServerPlayer p, HabilidadeDef h, double mult) {
        return switch (h.efeito()) {
            case "frx:puxar" -> puxar(p, h, mult);
            case "frx:provocar" -> provocar(p, h, mult);
            case "frx:salto" -> salto(p, h, mult);
            case "frx:fenda" -> fenda(p, h, mult);
            case "frx:executar" -> executarAlvo(p, h, mult);
            case "frx:vampirismo" -> vampirismo(p, h);
            case "frx:refletir" -> refletir(p, h);
            case "frx:lancar" -> lancar(p, h, mult);
            case "frx:explosiva" -> explosiva(p, h, mult);
            case "frx:ricochete" -> ricochete(p, h, mult);
            case "frx:marca" -> marca(p, h, mult);
            case "frx:gancho" -> gancho(p, h);
            case "frx:leque" -> leque(p, h, mult);
            case "frx:voleio" -> voleio(p, h, mult);
            case "frx:perfurar" -> perfurar(p, h, mult);
            case "frx:armadilha" -> armadilha(p, h, mult);
            case "frx:congelar" -> congelar(p, h, mult);
            case "frx:misseis" -> misseis(p, h, mult);
            case "frx:escudo_mana" -> escudoMana(p, h);
            case "frx:trocar" -> trocar(p, h, mult);
            case "frx:sifao" -> sifao(p, h, mult);
            case "frx:zona" -> zona(p, h, mult, false);
            case "frx:singularidade" -> zona(p, h, mult, true);
            default -> null;
        };
    }

    /** A habilidade matou o alvo e pediu a recarga de volta? (consome o pedido) */
    static boolean consumirReembolso(ServerPlayer p) {
        return reembolsos.remove(p.getUUID());
    }

    private static void aviso(ServerPlayer p, String texto, ChatFormatting cor) {
        p.displayClientMessage(Component.literal(texto).withStyle(cor), true);
    }

    private static int ticksDe(HabilidadeDef h, int minimo) {
        return (int) Math.max(minimo, h.duracao() * 20);
    }

    // ------------------------------------------------------------------ gancho

    private static boolean puxar(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(2, h.alcance()), 0.9, false);
        if (alvo == null) { aviso(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        ServerLevel nivel = p.serverLevel();
        Habilidades.linha(nivel, p.getEyePosition().add(p.getLookAngle().scale(0.6)), alvo.getBoundingBox().getCenter(), ParticleTypes.CRIT);
        nivel.playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
        nivel.playSound(null, alvo.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.2f, 0.6f);
        if (h.dano() > 0) Habilidades.ferir(p, alvo, h.dano() * mult, h);
        var resist = alvo.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resist != null && resist.getValue() >= 1) {
            aviso(p, "Pesado demais para puxar — mas ficou preso.", ChatFormatting.GRAY);
        } else {
            Vec3 d = p.position().subtract(alvo.position());
            double dist = d.length();
            if (dist > 2) {
                Vec3 v = d.normalize().scale(Math.min(2.2, 0.35 + dist * 0.13));
                alvo.setDeltaMovement(v.x, Math.min(0.6, 0.25 + dist * 0.03), v.z);
                alvo.hurtMarked = true;
            }
        }
        alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticksDe(h, 30), 3));
        if (alvo instanceof Mob m) m.setTarget(p);
        return true;
    }

    // ------------------------------------------------------------------ provocar

    private static boolean provocar(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        double raio = Math.max(3, h.raio());
        List<Mob> mobs = nivel.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(raio, 4, raio),
                e -> e.isAlive() && Habilidades.inimigo(p, e) && e.distanceTo(p) <= raio + 0.5);
        if (mobs.isEmpty()) { aviso(p, "Ninguém por perto para provocar.", ChatFormatting.GRAY); return false; }
        int dur = ticksDe(h, 60);
        for (Mob m : mobs) {
            m.setTarget(p);
            m.setLastHurtByMob(p);
            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
            nivel.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 3, 0.2, 0.1, 0.2, 0);
            if (h.dano() > 0) Habilidades.ferir(p, m, h.dano() * mult, h);
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, Math.max(0, h.potencia() - 1)));
        nivel.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1f, 1.2f);
        Habilidades.marcarCombate(p);
        aviso(p, mobs.size() + (mobs.size() == 1 ? " inimigo veio" : " inimigos vieram") + " para cima de você!", ChatFormatting.GOLD);
        return true;
    }

    // ------------------------------------------------------------------ salto esmagador

    private static boolean salto(ServerPlayer p, HabilidadeDef h, double mult) {
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize();
        double forca = Math.max(0, h.alcance()) * 0.13;
        p.setDeltaMovement(dir.x * forca, 0.95, dir.z * forca);
        p.hurtMarked = true;
        p.fallDistance = 0;
        ServerLevel nivel = p.serverLevel();
        nivel.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.4, 0.05, 0.4, 0.03);
        nivel.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1f, 0.6f);
        saltos.put(p.getUUID(), new Salto(h, mult, p.server.getTickCount()));
        return true;
    }

    private static void impacto(ServerPlayer p, Salto s) {
        ServerLevel nivel = p.serverLevel();
        double raio = Math.max(2, s.h().raio());
        Vec3 c = p.position();
        BlockState chao = nivel.getBlockState(p.blockPosition().below());
        if (chao.isAir()) chao = Blocks.STONE.defaultBlockState();
        BlockParticleOption lascas = new BlockParticleOption(ParticleTypes.BLOCK, chao);
        for (int i = 0; i < 28; i++) {
            double ang = Math.PI * 2 * i / 28;
            for (double r = raio; r > 0.5; r -= 1.5)
                nivel.sendParticles(lascas, c.x + Math.cos(ang) * r, c.y + 0.2, c.z + Math.sin(ang) * r, 3, 0.1, 0.3, 0.1, 0.1);
        }
        nivel.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, 0.6, 0.1, 0.6, 0);
        nivel.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 0.7f);
        nivel.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.5f);
        for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(raio, 3, raio),
                e -> e != p && e.isAlive() && Habilidades.inimigo(p, e) && e.position().distanceTo(c) <= raio + 0.5)) {
            Habilidades.ferir(p, e, s.h().dano() * s.mult(), s.h());
            e.setDeltaMovement(e.getDeltaMovement().x, 0.55, e.getDeltaMovement().z);
            e.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ fenda sísmica

    private static boolean fenda(ServerPlayer p, HabilidadeDef h, double mult) {
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-4) { aviso(p, "Olhe para a frente.", ChatFormatting.GRAY); return false; }
        dir = dir.normalize();
        ServerLevel nivel = p.serverLevel();
        double alcance = Math.max(3, h.alcance());
        Vec3 inicio = p.position();
        Vec3 fim = inicio.add(dir.scale(alcance));
        for (double i = 1; i <= alcance; i += 0.75) {
            Vec3 ponto = inicio.add(dir.scale(i));
            BlockPos chao = BlockPos.containing(ponto.x, ponto.y - 0.5, ponto.z);
            for (int dy = 0; dy < 3 && nivel.getBlockState(chao).isAir(); dy++) chao = chao.below();
            for (int dy = 0; dy < 3 && !nivel.getBlockState(chao.above()).isAir(); dy++) chao = chao.above();
            BlockState estado = nivel.getBlockState(chao);
            if (estado.isAir()) continue;
            nivel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, estado), ponto.x, chao.getY() + 1.1, ponto.z, 10, 0.3, 0.4, 0.3, 0.15);
            nivel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, ponto.x, chao.getY() + 1.1, ponto.z, 1, 0.2, 0.1, 0.2, 0.01);
        }
        nivel.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 0.5f);
        nivel.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
        Vec3 seg = fim.subtract(inicio);
        int acertos = 0;
        for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, new AABB(inicio, fim).inflate(2, 3, 2),
                e -> e != p && e.isAlive() && Habilidades.inimigo(p, e))) {
            Vec3 rel = e.position().subtract(inicio);
            double t = Math.max(0, Math.min(1, rel.dot(seg) / seg.lengthSqr()));
            Vec3 perto = inicio.add(seg.scale(t));
            if (perto.subtract(e.position()).multiply(1, 0, 1).length() > 1.8 + e.getBbWidth() / 2) continue;
            Habilidades.ferir(p, e, h.dano() * mult, h);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 0.5, e.getDeltaMovement().z * 0.3);
            e.hurtMarked = true;
            acertos++;
        }
        if (acertos == 0) Habilidades.marcarCombate(p);
        return true;
    }

    // ------------------------------------------------------------------ executar

    private static boolean executarAlvo(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(2, h.alcance()), 0.9, false);
        if (alvo == null) { aviso(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        ServerLevel nivel = p.serverLevel();
        double falta = 1 - Math.max(0, Math.min(1, alvo.getHealth() / Math.max(1, alvo.getMaxHealth())));
        Habilidades.linha(nivel, p.getEyePosition(), alvo.getBoundingBox().getCenter(), ParticleTypes.DAMAGE_INDICATOR);
        nivel.playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 0.6f);
        Habilidades.ferir(p, alvo, h.dano() * mult * (1 + 2 * falta), h);
        if (!alvo.isAlive() || alvo.isDeadOrDying()) {
            reembolsos.add(p.getUUID());
            Habilidades.ganharFuria(p, h.custo() * 0.5);
            nivel.sendParticles(ParticleTypes.SOUL, alvo.getX(), alvo.getY() + 1, alvo.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
            nivel.playSound(null, p.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.5f, 1.4f);
            aviso(p, "Executado! Recarga zerada e metade da fúria de volta.", ChatFormatting.DARK_RED);
        } else if (falta >= 0.6) {
            aviso(p, "Golpe de misericórdia!", ChatFormatting.RED);
        }
        return true;
    }

    // ------------------------------------------------------------------ auras

    private static boolean vampirismo(ServerPlayer p, HabilidadeDef h) {
        double fracao = h.potencia() > 0 ? Math.min(1, h.potencia() * 0.1) : 0.2;
        vampiros.put(p.getUUID(), new Aura(System.currentTimeMillis() + ticksDe(h, 60) * 50L, fracao));
        ServerLevel nivel = p.serverLevel();
        nivel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.7, 0.5, 0.1);
        nivel.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2f, 1.2f);
        aviso(p, "Sede de sangue: " + Math.round(fracao * 100) + "% do dano volta como vida.", ChatFormatting.DARK_RED);
        return true;
    }

    private static boolean refletir(ServerPlayer p, HabilidadeDef h) {
        double fracao = h.potencia() > 0 ? Math.min(2, h.potencia() * 0.1) : 0.4;
        int dur = ticksDe(h, 60);
        espelhos.put(p.getUUID(), new Aura(System.currentTimeMillis() + dur * 50L, fracao));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 0));
        ServerLevel nivel = p.serverLevel();
        nivel.sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.8, 0.6, 0.1);
        nivel.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2f, 0.7f);
        aviso(p, "Postura de revide: " + Math.round(fracao * 100) + "% do dano volta em quem bater.", ChatFormatting.AQUA);
        return true;
    }

    // ------------------------------------------------------------------ ira do titã

    private static boolean lancar(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        double raio = Math.max(3, h.raio());
        Vec3 c = p.position();
        nivel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 48; i++) {
            double ang = Math.PI * 2 * i / 48;
            nivel.sendParticles(ParticleTypes.LAVA, c.x + Math.cos(ang) * raio, c.y + 0.3, c.z + Math.sin(ang) * raio, 1, 0, 0, 0, 0);
            nivel.sendParticles(ParticleTypes.CRIT, c.x + Math.cos(ang) * raio / 2, c.y + 0.6, c.z + Math.sin(ang) * raio / 2, 3, 0.2, 0.4, 0.2, 0.2);
        }
        nivel.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 0.5f);
        nivel.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 0.5f);
        for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(raio, 4, raio),
                e -> e != p && e.isAlive() && Habilidades.inimigo(p, e) && e.position().distanceTo(c) <= raio + 0.5)) {
            Habilidades.ferir(p, e, h.dano() * mult, h);
            Vec3 fora = e.position().subtract(c).multiply(1, 0, 1);
            fora = fora.lengthSqr() < 1e-4 ? Vec3.ZERO : fora.normalize().scale(0.45);
            e.setDeltaMovement(fora.x, 0.95, fora.z);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
        if (h.potencia() > 0) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticksDe(h, 100), h.potencia() - 1));
        Habilidades.marcarCombate(p);
        return true;
    }

    // ------------------------------------------------------------------ arqueiro

    private static void flecha(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f, 1.1f);
    }

    private static boolean explosiva(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        double alcance = Math.max(3, h.alcance());
        LivingEntity mirado = Habilidades.mirar(p, alcance, 0.35, false);
        Vec3 ponto = mirado != null ? mirado.getBoundingBox().getCenter() : Habilidades.pontoBloco(p, alcance);
        Habilidades.linha(nivel, p.getEyePosition().add(p.getLookAngle().scale(0.8)), ponto, Habilidades.particula(h.elemento()));
        flecha(p);
        double raio = Math.max(1.5, h.raio());
        nivel.sendParticles(ParticleTypes.EXPLOSION, ponto.x, ponto.y, ponto.z, 4, raio / 3, 0.3, raio / 3, 0);
        nivel.sendParticles(ParticleTypes.FLAME, ponto.x, ponto.y, ponto.z, 40, raio / 2, 0.5, raio / 2, 0.08);
        nivel.playSound(null, BlockPos.containing(ponto), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 1.2f);
        for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, new AABB(ponto, ponto).inflate(raio, raio, raio),
                e -> e != p && e.isAlive() && Habilidades.inimigo(p, e) && e.getBoundingBox().getCenter().distanceTo(ponto) <= raio + 0.8))
            Habilidades.ferir(p, e, h.dano() * mult, h);
        Habilidades.marcarCombate(p);
        return true;
    }

    private static boolean ricochete(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(3, h.alcance()), 0.5, false);
        if (alvo == null) { aviso(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        ServerLevel nivel = p.serverLevel();
        boolean magia = !"FISICO".equals(h.elemento());
        if (magia) Habilidades.som(p, h.elemento()); else flecha(p);
        Set<UUID> ja = new java.util.HashSet<>();
        Vec3 de = p.getEyePosition();
        double dano = h.dano() * mult;
        double raio = Math.max(3, h.raio());
        int pulos = h.potencia() > 0 ? h.potencia() : 3;
        LivingEntity atual = alvo;
        for (int i = 0; i <= pulos && atual != null; i++) {
            Vec3 ate = atual.getBoundingBox().getCenter();
            Habilidades.linha(nivel, de, ate, magia ? Habilidades.particula(h.elemento()) : i == 0 ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT);
            if (i > 0) nivel.playSound(null, atual.blockPosition(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1f, 1.2f + i * 0.15f);
            Habilidades.ferir(p, atual, dano, h);
            ja.add(atual.getUUID());
            dano *= 0.75;
            de = ate;
            final Vec3 centro = ate;
            atual = nivel.getEntitiesOfClass(LivingEntity.class, new AABB(centro, centro).inflate(raio),
                            e -> e != p && e.isAlive() && Habilidades.inimigo(p, e) && !ja.contains(e.getUUID()) && e.getBoundingBox().getCenter().distanceTo(centro) <= raio)
                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(centro))).orElse(null);
        }
        if (ja.size() > 1) aviso(p, "Ricocheteou em " + ja.size() + " alvos!", ChatFormatting.AQUA);
        return true;
    }

    private static boolean marca(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(3, h.alcance()), 0.9, false);
        if (alvo == null) { aviso(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        ServerLevel nivel = p.serverLevel();
        if (h.dano() > 0) Habilidades.ferir(p, alvo, h.dano() * mult, h);
        double fracao = h.potencia() > 0 ? Math.min(2, h.potencia() * 0.1) : 0.25;
        int dur = ticksDe(h, 100);
        marcas.put(alvo.getUUID(), new Marca(p.getUUID(), System.currentTimeMillis() + dur * 50L, fracao));
        alvo.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
        nivel.sendParticles(ParticleTypes.ANGRY_VILLAGER, alvo.getX(), alvo.getY() + alvo.getBbHeight() + 0.4, alvo.getZ(), 1, 0, 0, 0, 0);
        nivel.sendParticles(ParticleTypes.CRIT, alvo.getX(), alvo.getY() + alvo.getBbHeight() / 2, alvo.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
        nivel.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.PLAYERS, 1f, 0.6f);
        Habilidades.marcarCombate(p);
        aviso(p, "Presa marcada: +" + Math.round(fracao * 100) + "% de dano seu nela por " + dur / 20 + "s.", ChatFormatting.GOLD);
        return true;
    }

    private static boolean gancho(ServerPlayer p, HabilidadeDef h) {
        double alcance = Math.max(5, Math.abs(h.alcance()));
        Vec3 olho = p.getEyePosition();
        Vec3 fim = olho.add(p.getLookAngle().scale(alcance));
        var r = p.level().clip(new net.minecraft.world.level.ClipContext(olho, fim, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (r.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) { aviso(p, "Mire num bloco ao alcance (" + Math.round(alcance) + " blocos).", ChatFormatting.GRAY); return false; }
        ServerLevel nivel = p.serverLevel();
        Vec3 d = r.getLocation().subtract(p.position());
        double dist = d.length();
        if (dist < 2.5) { aviso(p, "Perto demais.", ChatFormatting.GRAY); return false; }
        Vec3 v = d.normalize().scale(Math.min(3.4, 0.7 + dist * 0.11));
        p.setDeltaMovement(v.x, v.y + 0.3, v.z);
        p.hurtMarked = true;
        p.fallDistance = 0;
        semQueda.put(p.getUUID(), p.server.getTickCount() + 100);
        Habilidades.linha(nivel, olho, r.getLocation(), ParticleTypes.CRIT);
        nivel.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1f, 0.8f);
        nivel.playSound(null, BlockPos.containing(r.getLocation()), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5f, 1f);
        return true;
    }

    private static boolean leque(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        double alcance = Math.max(3, h.alcance());
        Vec3 olho = p.getEyePosition();
        Vec3 olhar = p.getLookAngle();
        for (int i = -2; i <= 2; i++) {
            Vec3 dir = olhar.yRot((float) Math.toRadians(i * 15));
            Habilidades.linha(nivel, olho.add(dir.scale(0.8)), olho.add(dir.scale(alcance)), ParticleTypes.CRIT);
        }
        flecha(p);
        nivel.playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f, 0.8f);
        double cos = Math.cos(Math.toRadians(38));
        for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(alcance),
                e -> e != p && e.isAlive() && Habilidades.inimigo(p, e))) {
            Vec3 para = e.getBoundingBox().getCenter().subtract(olho);
            if (para.length() > alcance + 0.5 || para.normalize().dot(olhar) < cos || !p.hasLineOfSight(e)) continue;
            Habilidades.ferir(p, e, h.dano() * mult, h);
            e.knockback(0.5, -olhar.x, -olhar.z);
        }
        Habilidades.marcarCombate(p);
        return true;
    }

    private static boolean voleio(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        LivingEntity alvo = Habilidades.mirar(p, Math.max(3, Math.abs(h.alcance())), 0.6, false);
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize();
        p.setDeltaMovement(-dir.x * 1.35, 0.55, -dir.z * 1.35);
        p.hurtMarked = true;
        p.fallDistance = 0;
        semQueda.put(p.getUUID(), p.server.getTickCount() + 60);
        nivel.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
        flecha(p);
        if (alvo != null) {
            Habilidades.linha(nivel, p.getEyePosition(), alvo.getBoundingBox().getCenter(), ParticleTypes.CRIT);
            Habilidades.ferir(p, alvo, h.dano() * mult, h);
            alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        }
        return true;
    }

    private static boolean perfurar(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        double alcance = Math.max(8, h.alcance());
        Vec3 olho = p.getEyePosition();
        Vec3 fim = Habilidades.pontoBloco(p, alcance);
        Habilidades.linha(nivel, olho.add(p.getLookAngle().scale(0.8)), fim, ParticleTypes.END_ROD);
        Habilidades.linha(nivel, olho.add(p.getLookAngle().scale(0.8)), fim, ParticleTypes.CRIT);
        nivel.playSound(null, p.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1f, 1.4f);
        nivel.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2f, 0.5f);
        int acertos = 0;
        for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, new AABB(olho, fim).inflate(1.5),
                e -> e != p && e.isAlive() && Habilidades.inimigo(p, e))) {
            var hit = e.getBoundingBox().inflate(0.5).clip(olho, fim);
            if (hit.isEmpty()) continue;
            double longe = Math.min(1, olho.distanceTo(hit.get()) / alcance);
            Habilidades.ferir(p, e, h.dano() * mult * (1 + longe), h);
            acertos++;
        }
        Habilidades.marcarCombate(p);
        if (acertos > 1) aviso(p, "Atravessou " + acertos + " inimigos!", ChatFormatting.GOLD);
        return true;
    }

    private static boolean armadilha(ServerPlayer p, HabilidadeDef h, double mult) {
        double alcance = Math.max(3, h.alcance());
        Vec3 olho = p.getEyePosition();
        var r = p.level().clip(new net.minecraft.world.level.ClipContext(olho, olho.add(p.getLookAngle().scale(alcance)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (r.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) { aviso(p, "Mire no chão, até " + Math.round(alcance) + " blocos.", ChatFormatting.GRAY); return false; }
        ServerLevel nivel = p.serverLevel();
        Vec3 pos = r.getLocation();
        List<Armadilha> minhas = armadilhas.stream().filter(a -> a.dono().equals(p.getUUID())).toList();
        if (minhas.size() >= 2) armadilhas.remove(minhas.get(0));
        armadilhas.add(new Armadilha(p.getUUID(), nivel, pos, h, mult, p.server.getTickCount() + 20 * 45));
        nivel.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.2, pos.z, 12, 0.3, 0.1, 0.3, 0.01);
        nivel.playSound(null, BlockPos.containing(pos), SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 1.2f, 0.8f);
        aviso(p, "Armadilha armada (dura 45 s, no máximo 2).", ChatFormatting.GREEN);
        return true;
    }

    private static void tickArmadilhas(MinecraftServer server) {
        for (Armadilha a : armadilhas) {
            ServerPlayer dono = server.getPlayerList().getPlayer(a.dono());
            if (dono == null || server.getTickCount() > a.fimTick()) { armadilhas.remove(a); continue; }
            double raio = Math.max(1.5, a.h().raio());
            a.nivel().sendParticles(ParticleTypes.CRIT, a.pos().x, a.pos().y + 0.15, a.pos().z, 2, raio / 3, 0.02, raio / 3, 0);
            List<LivingEntity> presos = a.nivel().getEntitiesOfClass(LivingEntity.class, new AABB(a.pos(), a.pos()).inflate(raio, 2, raio),
                    e -> e.isAlive() && Habilidades.inimigo(dono, e) && e.position().distanceTo(a.pos()) <= raio + 0.5);
            if (presos.isEmpty()) continue;
            armadilhas.remove(a);
            int dur = ticksDe(a.h(), 60);
            for (LivingEntity e : presos) {
                Habilidades.ferir(dono, e, a.h().dano() * a.mult(), a.h());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 6));
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
            }
            a.nivel().sendParticles(ParticleTypes.CRIT, a.pos().x, a.pos().y + 0.5, a.pos().z, 40, raio / 2, 0.5, raio / 2, 0.2);
            a.nivel().playSound(null, BlockPos.containing(a.pos()), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.5f, 0.6f);
            a.nivel().playSound(null, BlockPos.containing(a.pos()), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.5f, 0.7f);
            dono.displayClientMessage(Component.literal("Sua armadilha pegou " + presos.size() + (presos.size() == 1 ? " inimigo!" : " inimigos!")).withStyle(ChatFormatting.GREEN), true);
        }
    }

    // ------------------------------------------------------------------ mago

    private static boolean congelar(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        double raio = Math.max(2, h.raio());
        Vec3 c = p.position();
        int dur = ticksDe(h, 40);
        for (int i = 0; i < 60; i++) {
            double ang = Math.PI * 2 * i / 60;
            for (double r = raio; r > 0.5; r -= 1.2)
                nivel.sendParticles(ParticleTypes.SNOWFLAKE, c.x + Math.cos(ang) * r, c.y + 0.3, c.z + Math.sin(ang) * r, 1, 0.05, 0.2, 0.05, 0.01);
        }
        nivel.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
        nivel.playSound(null, p.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.5f, 0.7f);
        for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(raio, 3, raio),
                e -> e != p && e.isAlive() && Habilidades.inimigo(p, e) && e.position().distanceTo(c) <= raio + 0.5)) {
            Habilidades.ferir(p, e, h.dano() * mult, h);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 9));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 1));
            e.setTicksFrozen(dur + 140);
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
        }
        Habilidades.marcarCombate(p);
        return true;
    }

    private static boolean misseis(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(3, h.alcance()), 1.0, false);
        if (alvo == null) { aviso(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        int qtd = h.potencia() > 0 ? Math.min(12, h.potencia()) : 3;
        int agora = p.server.getTickCount();
        for (int i = 0; i < qtd; i++) misseis.add(new Missil(p.getUUID(), alvo.getUUID(), agora + i * 4, h, mult));
        return true;
    }

    private static void dispararMissil(ServerPlayer p, Missil m) {
        ServerLevel nivel = p.serverLevel();
        double alcance = Math.max(3, m.h().alcance()) + 6;
        LivingEntity alvo = nivel.getEntity(m.alvo()) instanceof LivingEntity l && l.isAlive() && l.distanceTo(p) <= alcance ? l : null;
        if (alvo == null) {
            alvo = nivel.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(alcance / 2),
                            e -> e != p && e.isAlive() && Habilidades.inimigo(p, e) && p.hasLineOfSight(e))
                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
        }
        if (alvo == null) return;
        Vec3 de = p.getEyePosition();
        Vec3 ate = alvo.getBoundingBox().getCenter();
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        Vec3 meio = de.add(ate).scale(0.5).add(rnd.nextDouble(-2, 2), rnd.nextDouble(0.5, 2.5), rnd.nextDouble(-2, 2));
        Habilidades.linha(nivel, de, meio, Habilidades.particula(m.h().elemento()));
        Habilidades.linha(nivel, meio, ate, Habilidades.particula(m.h().elemento()));
        nivel.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.2f + rnd.nextFloat() * 0.6f);
        Habilidades.ferir(p, alvo, m.h().dano() * m.mult(), m.h());
    }

    private static boolean escudoMana(ServerPlayer p, HabilidadeDef h) {
        double fracao = h.potencia() > 0 ? Math.min(1, h.potencia() * 0.1) : 0.5;
        barreiras.put(p.getUUID(), new Aura(System.currentTimeMillis() + ticksDe(h, 100) * 50L, fracao));
        ServerLevel nivel = p.serverLevel();
        nivel.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.6, 0.8, 0.6, 0.5);
        nivel.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        aviso(p, "Barreira de mana: " + Math.round(fracao * 100) + "% do dano sai da mana (2 de mana por ponto).", ChatFormatting.AQUA);
        return true;
    }

    private static boolean trocar(ServerPlayer p, HabilidadeDef h, double mult) {
        double alcance = Math.max(3, h.alcance());
        LivingEntity alvo = Habilidades.mirar(p, alcance, 0.9, false);
        boolean aliado = false;
        if (alvo == null) { alvo = Habilidades.mirar(p, alcance, 0.9, true); aliado = alvo != null; }
        if (alvo == null) { aviso(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        var resist = alvo.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (!aliado && resist != null && resist.getValue() >= 1) { aviso(p, "Esse aí não sai do lugar.", ChatFormatting.GRAY); return false; }
        ServerLevel nivel = p.serverLevel();
        Vec3 a = p.position(), b = alvo.position();
        nivel.sendParticles(ParticleTypes.PORTAL, a.x, a.y + 1, a.z, 40, 0.4, 0.8, 0.4, 0.3);
        nivel.sendParticles(ParticleTypes.PORTAL, b.x, b.y + 1, b.z, 40, 0.4, 0.8, 0.4, 0.3);
        alvo.teleportTo(a.x, a.y, a.z);
        p.teleportTo(b.x, b.y, b.z);
        p.fallDistance = 0;
        alvo.fallDistance = 0;
        nivel.playSound(null, BlockPos.containing(a), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.3f);
        nivel.playSound(null, BlockPos.containing(b), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.9f);
        if (!aliado) {
            if (h.dano() > 0) Habilidades.ferir(p, alvo, h.dano() * mult, h);
            alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
        }
        return true;
    }

    private static boolean sifao(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(3, h.alcance()), 0.9, false);
        if (alvo == null) { aviso(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        ServerLevel nivel = p.serverLevel();
        Habilidades.linha(nivel, alvo.getBoundingBox().getCenter(), p.getEyePosition().add(0, -0.3, 0), ParticleTypes.ENCHANT);
        nivel.playSound(null, p.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.2f, 0.7f);
        Habilidades.ferir(p, alvo, h.dano() * mult, h);
        double ganho = (h.potencia() > 0 ? h.potencia() : 3) * 10.0;
        if (br.com.frxcode.frxworld.progressao.Progressao.temDados(p)) {
            var d = br.com.frxcode.frxworld.progressao.Progressao.dados(p);
            d.definirRecurso(Math.min(Habilidades.maximo(p), d.recurso() + ganho));
        }
        aviso(p, "+" + Math.round(ganho) + " de " + Habilidades.nomeRecurso(Habilidades.recursoDa(h.classe())).toLowerCase() + " sugada.", ChatFormatting.AQUA);
        return true;
    }

    private static boolean zona(ServerPlayer p, HabilidadeDef h, double mult, boolean puxa) {
        ServerLevel nivel = p.serverLevel();
        Vec3 pos;
        if (h.alcance() > 0) {
            LivingEntity mirado = Habilidades.mirar(p, h.alcance(), 0.5, false);
            pos = mirado != null ? mirado.position() : Habilidades.pontoBloco(p, h.alcance());
        } else pos = p.position();
        List<Zona> minhas = zonas.stream().filter(z -> z.dono().equals(p.getUUID())).toList();
        if (minhas.size() >= 2) zonas.remove(minhas.get(0));
        int agora = p.server.getTickCount();
        zonas.add(new Zona(p.getUUID(), nivel, puxa ? pos.add(0, 1.2, 0) : pos, h, mult, agora, agora + ticksDe(h, 60), puxa));
        Habilidades.som(p, h.elemento());
        if (puxa) nivel.playSound(null, BlockPos.containing(pos), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6f, 1.6f);
        Habilidades.marcarCombate(p);
        return true;
    }

    private static void tickZonas(MinecraftServer server) {
        int agora = server.getTickCount();
        for (Zona z : zonas) {
            ServerPlayer dono = server.getPlayerList().getPlayer(z.dono());
            if (dono == null || dono.serverLevel() != z.nivel()) { zonas.remove(z); continue; }
            double raio = Math.max(2, z.h().raio());
            Vec3 c = z.pos();
            int idade = agora - z.inicio();
            boolean acabou = agora >= z.fim();
            List<LivingEntity> dentro = (z.puxa() && idade % 2 == 0) || idade % 20 == 0 || acabou
                    ? z.nivel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(raio, 4, raio),
                            e -> e.isAlive() && Habilidades.inimigo(dono, e) && e.position().distanceTo(c) <= raio + 0.5)
                    : List.of();
            if (z.puxa()) {
                if (idade % 2 == 0) {
                    z.nivel().sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0.01);
                    for (int i = 0; i < 10; i++) {
                        double ang = idade * 0.35 + Math.PI * 2 * i / 10, r = raio * (1 - (idade % 20) / 20.0);
                        z.nivel().sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(ang) * r, c.y, c.z + Math.sin(ang) * r, 1, 0, 0.1, 0, 0);
                    }
                    for (LivingEntity e : dentro) {
                        var resist = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                        if (resist != null && resist.getValue() >= 1) continue;
                        Vec3 d = c.subtract(e.position());
                        if (d.length() < 0.8) { e.setDeltaMovement(Vec3.ZERO); }
                        else { Vec3 v = d.normalize().scale(0.32); e.setDeltaMovement(v.x, Math.max(-0.1, Math.min(0.25, v.y)), v.z); }
                        e.hurtMarked = true;
                    }
                }
            } else if (idade % 5 == 0) {
                var part = Habilidades.particula(z.h().elemento());
                z.nivel().sendParticles(part, c.x, c.y + 1.5, c.z, 25, raio / 2, 1.2, raio / 2, 0.02);
                for (int i = 0; i < 24; i++) {
                    double ang = Math.PI * 2 * i / 24;
                    z.nivel().sendParticles(part, c.x + Math.cos(ang) * raio, c.y + 0.2, c.z + Math.sin(ang) * raio, 1, 0, 0.05, 0, 0);
                }
            }
            if (idade % 20 == 0 && !acabou) {
                for (LivingEntity e : dentro) Habilidades.ferir(dono, e, z.h().dano() * z.mult(), z.h());
                if (!z.puxa()) z.nivel().playSound(null, BlockPos.containing(c), "GELO".equals(z.h().elemento()) ? SoundEvents.POWDER_SNOW_STEP : SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.8f);
            }
            if (acabou) {
                zonas.remove(z);
                if (z.puxa()) {
                    z.nivel().sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                    z.nivel().sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 0.3, 0.3, 0.3, 0.4);
                    z.nivel().playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.6f);
                    for (LivingEntity e : dentro) {
                        Habilidades.ferir(dono, e, z.h().dano() * z.mult() * 2, z.h());
                        Vec3 fora = e.position().subtract(c).multiply(1, 0, 1);
                        fora = fora.lengthSqr() < 1e-4 ? Vec3.ZERO : fora.normalize().scale(0.9);
                        e.setDeltaMovement(fora.x, 0.6, fora.z);
                        e.hurtMarked = true;
                    }
                }
            }
        }
    }

    /** Barreira de mana: parte do dano sai do recurso em vez da vida. */
    @SubscribeEvent
    public void aoFerirComBarreira(net.minecraftforge.event.entity.living.LivingHurtEvent evento) {
        if (barreiras.isEmpty() || !(evento.getEntity() instanceof ServerPlayer p) || evento.getAmount() <= 0) return;
        Aura a = barreiras.get(p.getUUID());
        if (a == null) return;
        if (a.fimMs() < System.currentTimeMillis() || !br.com.frxcode.frxworld.progressao.Progressao.temDados(p)) { barreiras.remove(p.getUUID()); return; }
        var d = br.com.frxcode.frxworld.progressao.Progressao.dados(p);
        double absorver = Math.min(evento.getAmount() * a.fracao(), d.recurso() / 2);
        if (absorver <= 0) {
            barreiras.remove(p.getUUID());
            aviso(p, "Sem mana: a barreira quebrou!", ChatFormatting.RED);
            p.level().playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 1.4f);
            return;
        }
        d.definirRecurso(d.recurso() - absorver * 2);
        evento.setAmount((float) (evento.getAmount() - absorver));
        Habilidades.atualizarRecurso(p);
        p.serverLevel().sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 15, 0.4, 0.6, 0.4, 0.5);
    }

    /** Presa marcada leva mais dano de quem marcou. */
    @SubscribeEvent
    public void aoFerirMarcado(net.minecraftforge.event.entity.living.LivingHurtEvent evento) {
        if (marcas.isEmpty()) return;
        Marca m = marcas.get(evento.getEntity().getUUID());
        if (m == null) return;
        if (m.fimMs() < System.currentTimeMillis() || !evento.getEntity().isAlive()) { marcas.remove(evento.getEntity().getUUID()); return; }
        if (evento.getSource().getEntity() instanceof ServerPlayer a && a.getUUID().equals(m.dono()))
            evento.setAmount((float) (evento.getAmount() * (1 + m.fracao())));
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END) return;
        MinecraftServer server = evento.getServer();
        if (!armadilhas.isEmpty() && server.getTickCount() % 5 == 0) {
            try { tickArmadilhas(server); } catch (Exception ignorado) { armadilhas.clear(); }
        }
        if (!zonas.isEmpty()) {
            try { tickZonas(server); } catch (Exception ignorado) { zonas.clear(); }
        }
        if (!misseis.isEmpty()) {
            for (Missil m : misseis) {
                if (server.getTickCount() < m.tick()) continue;
                misseis.remove(m);
                ServerPlayer p = server.getPlayerList().getPlayer(m.dono());
                if (p == null || !p.isAlive()) continue;
                try { dispararMissil(p, m); } catch (Exception ignorado) { }
            }
        }
        if (!semQueda.isEmpty()) {
            for (Iterator<Map.Entry<UUID, Integer>> it = semQueda.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Integer> ent = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(ent.getKey());
                if (p == null || server.getTickCount() > ent.getValue()) { it.remove(); continue; }
                p.fallDistance = 0;
            }
        }
        if (marcas.size() > 200) marcas.values().removeIf(m -> m.fimMs() < System.currentTimeMillis());
        for (Iterator<Map.Entry<UUID, Salto>> it = saltos.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Salto> ent = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(ent.getKey());
            if (p == null || !p.isAlive()) { it.remove(); continue; }
            p.fallDistance = 0;
            int idade = server.getTickCount() - ent.getValue().inicio();
            if (idade > 120) { it.remove(); continue; }
            if (idade >= 6 && (p.onGround() || p.isInWater())) {
                it.remove();
                try { impacto(p, ent.getValue()); } catch (Exception ignorado) { }
            }
        }
    }

    @SubscribeEvent
    public void aoSofrerDano(LivingDamageEvent evento) {
        if (evento.getAmount() <= 0 || evento.getSource().is(DamageTypes.THORNS)) return;
        long agora = System.currentTimeMillis();
        LivingEntity vitima = evento.getEntity();
        if (evento.getSource().getEntity() instanceof ServerPlayer atacante && atacante != vitima) {
            Aura a = vampiros.get(atacante.getUUID());
            if (a != null) {
                if (a.fimMs() < agora) vampiros.remove(atacante.getUUID());
                else {
                    atacante.heal((float) Math.min(8, evento.getAmount() * a.fracao()));
                    atacante.serverLevel().sendParticles(ParticleTypes.HEART, atacante.getX(), atacante.getY() + 1.6, atacante.getZ(), 1, 0.3, 0.2, 0.3, 0);
                }
            }
        }
        if (vitima instanceof ServerPlayer p && evento.getSource().getEntity() instanceof LivingEntity agressor && agressor != p) {
            Aura a = espelhos.get(p.getUUID());
            if (a != null) {
                if (a.fimMs() < agora) espelhos.remove(p.getUUID());
                else {
                    agressor.hurt(p.damageSources().thorns(p), (float) (evento.getAmount() * a.fracao()));
                    p.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, agressor.getX(), agressor.getY() + 1, agressor.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
                    p.level().playSound(null, p.blockPosition(), SoundEvents.THORNS_HIT, SoundSource.PLAYERS, 0.8f, 1f);
                }
            }
        }
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent evento) {
        UUID id = evento.getEntity().getUUID();
        saltos.remove(id);
        vampiros.remove(id);
        espelhos.remove(id);
        reembolsos.remove(id);
        semQueda.remove(id);
        barreiras.remove(id);
        zonas.removeIf(z -> z.dono().equals(id));
        misseis.removeIf(m -> m.dono().equals(id));
        armadilhas.removeIf(a -> a.dono().equals(id));
    }
}
