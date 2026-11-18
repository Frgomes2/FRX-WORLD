package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.SyncDespertar;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Despertar: o poder especial de cada classe, inspirado num anime (tecla B).
 * Uma barra de 0 a 100 enche do jeito de cada classe; cheia, a tecla libera o poder.
 *   Guerreiro  · Armadura do Berserker (Berserk)       – enche batendo e apanhando
 *   Arqueiro   · Chuva de Luz (Bleach, Quincy)          – enche acertando flechas (mais de longe)
 *   Mago       · Explosão! (Konosuba)                   – enche gastando mana
 *   Clérigo    · Estandarte da Donzela (Fate)           – enche gastando mana e ferindo mortos-vivos
 *   Ladino     · Roubar! (Konosuba / Lupin III)         – enche com golpes, mais pelas costas
 *   Assassino  · Respiração do Trovão (Demon Slayer)    – enche com golpes, mais pelas costas e em alvos quase mortos
 *   Invocador  · Levante-se! (Solo Leveling)            – a tecla ergue a sombra mais próxima (sem barra)
 *   Paladino   · Égide da Aurora (original)             – enche apanhando, batendo de perto e gastando mana
 *   Bardo      · Bis! (original)                        – enche gastando mana e ferindo
 *   Monge      · Portão Interior (original)             – enche com socos e golpes de perto
 *   Druida     · Coração da Floresta (original)         – enche gastando mana, batendo de perto e apanhando
 */
public class Despertar {
    public static final int NIVEL_MIN = 10;
    private static final String TAG_LUZ = "frx_licht";
    private static final UUID MOD_BERSERK = UUID.nameUUIDFromBytes("frxworld:berserk".getBytes());
    private static final UUID MOD_BERSERK_VEL = UUID.nameUUIDFromBytes("frxworld:berserk_vel".getBytes());
    private static final long COMBATE_MS = 30_000;

    public Despertar() {}

    /** Estado de cada jogador (carga da barra e o poder em andamento). */
    private static final class Estado {
        double carga;
        long ultimoCombate;
        int ativo, total, extra;          // ticks restantes, duração total, cargas extras (investidas do Assassino)
        Vec3 ponto;                       // alvo da Explosão / Chuva de Luz / centro do Estandarte
        ServerLevel nivel;
        float vidaInicio;                 // Mago: vida ao começar o canto (para saber se foi interrompido)
        double guardado;                  // Paladino: dano que a Égide barrou e devolve no clarão final
        int enviadoCarga = -1, enviadoAtivo = -1, enviadoExtra = -1;
    }

    private static final Map<UUID, Estado> ESTADOS = new ConcurrentHashMap<>();
    private static final List<Arrow> FLECHAS = new ArrayList<>();

    private static Estado est(Player p) { return ESTADOS.computeIfAbsent(p.getUUID(), k -> new Estado()); }

    private static String classe(ServerPlayer p) {
        return Progressao.temDados(p) ? Progressao.dados(p).classe() : "";
    }

    private static int nivel(ServerPlayer p) { return Progressao.dados(p).nivel(); }

    public static String nome(String classe) {
        return switch (classe) {
            case "GUERREIRO" -> "Armadura do Berserker";
            case "ARQUEIRO" -> "Chuva de Luz";
            case "MAGO" -> "Explosão!";
            case "CLERIGO" -> "Estandarte da Donzela";
            case "LADINO" -> "Roubar!";
            case "ASSASSINO" -> "Respiração do Trovão";
            case "INVOCADOR" -> "Levante-se!";
            case "PALADINO" -> "Égide da Aurora";
            case "BARDO" -> "Bis!";
            case "MONGE" -> "Portão Interior";
            case "DRUIDA" -> "Coração da Floresta";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ carregar a barra

    private static void carregar(ServerPlayer p, double quanto) {
        if (quanto <= 0 || nivel(p) < NIVEL_MIN || "INVOCADOR".equals(classe(p))) return;
        Estado e = est(p);
        e.ultimoCombate = System.currentTimeMillis();
        if (e.ativo > 0) return;                                  // não enche durante o próprio poder
        double antes = e.carga;
        e.carga = Math.min(100, e.carga + quanto);
        if (antes < 100 && e.carga >= 100) {
            p.displayClientMessage(Component.literal("✦ " + nome(classe(p)) + " pronto! Aperte a tecla do Despertar.").withStyle(ChatFormatting.GOLD), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.4f);
        }
    }

    /** Chamado quando uma habilidade gasta mana (Mago e Clérigo enchem assim). */
    public static void gastouRecurso(ServerPlayer p, double custo) {
        switch (classe(p)) {
            case "MAGO" -> carregar(p, custo * 0.55);
            case "CLERIGO" -> carregar(p, custo * 0.4);
            case "PALADINO" -> carregar(p, custo * 0.3);
            case "BARDO" -> carregar(p, custo * 0.45);
            case "DRUIDA" -> carregar(p, custo * 0.35);
            default -> { }
        }
    }

    private static boolean pelasCostas(Player p, LivingEntity alvo) {
        Vec3 olharAlvo = alvo.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 deAlvoParaJogador = p.position().subtract(alvo.position()).multiply(1, 0, 1).normalize();
        return olharAlvo.dot(deAlvoParaJogador) < -0.3;
    }

    static boolean aliado(LivingEntity e) {
        return e instanceof Player || e instanceof AbstractVillager || e.getTags().contains(Habilidades.TAG_INVOCADO)
                || e.getTags().contains("frx_npc") || e.getTags().contains("frx_cidadao") || (e instanceof TamableAnimal t && t.isTame());
    }

    static boolean inimigo(LivingEntity e) {
        return e.isAlive() && !aliado(e) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && !(e instanceof net.minecraft.world.entity.animal.IronGolem g && g.isPlayerCreated());
    }

    private static boolean chefe(LivingEntity e) { return e.getType().is(Tags.EntityTypes.BOSSES); }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoFerir(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide()) return;
        float dano = ev.getAmount();

        // quem bateu
        Entity fonte = ev.getSource().getEntity();
        if (fonte instanceof ServerPlayer p && p != vitima && !aliado(vitima)) {
            Estado e = est(p);
            boolean flecha = ev.getSource().getDirectEntity() instanceof AbstractArrow;
            boolean corpo = ev.getSource().getDirectEntity() == p;
            switch (classe(p)) {
                case "GUERREIRO" -> {
                    if (e.ativo > 0) dano *= 1.5f;
                    carregar(p, Math.min(8, dano * 1.2));
                }
                case "ARQUEIRO" -> {
                    if (flecha && !ev.getSource().getDirectEntity().getTags().contains(TAG_LUZ))
                        carregar(p, 10 + (p.distanceTo(vitima) >= 20 ? 6 : 0));
                }
                case "CLERIGO" -> carregar(p, dano * (vitima.getMobType() == MobType.UNDEAD ? 1.5 : 0.4));
                case "LADINO" -> { if (corpo) carregar(p, pelasCostas(p, vitima) ? 10 : 4); }
                case "PALADINO" -> { if (corpo) carregar(p, Math.min(6, dano * 0.6)); }
                case "BARDO" -> carregar(p, Math.min(5, dano * 0.4));
                case "MONGE" -> { if (corpo) carregar(p, 4); }
                case "DRUIDA" -> { if (corpo) carregar(p, 4); }
                case "ASSASSINO" -> {
                    if (corpo) carregar(p, pelasCostas(p, vitima) || vitima.getHealth() < vitima.getMaxHealth() * 0.3f ? 10 : 5);
                }
                default -> { }
            }
        }

        // quem apanhou
        if (vitima instanceof ServerPlayer p) {
            Estado e = est(p);
            String c = classe(p);
            if ("GUERREIRO".equals(c)) {
                if (e.ativo > 0) {
                    dano *= 0.75f;
                    if (dano >= p.getHealth()) dano = Math.max(0, p.getHealth() - 1);   // a armadura não deixa cair
                } else carregar(p, Math.min(10, dano * 2.5));
            }
            if ("PALADINO".equals(c) && e.ativo <= 0) carregar(p, Math.min(10, dano * 2));
            if ("DRUIDA".equals(c) && e.ativo <= 0) carregar(p, Math.min(6, dano));
            if ("MAGO".equals(c) && e.ativo > 0 && e.extra == 1 && dano >= p.getMaxHealth() * 0.3f) {
                e.ativo = 0;
                e.extra = 0;
                e.carga = 50;
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                p.displayClientMessage(Component.literal("O canto foi interrompido! Metade do poder se perdeu.").withStyle(ChatFormatting.RED), true);
            }
        }

        // dentro do Estandarte da Donzela: aliados levam metade
        if (aliado(vitima)) {
            for (Map.Entry<UUID, Estado> en : ESTADOS.entrySet()) {
                Estado e = en.getValue();
                if (e.ativo > 0 && e.ponto != null && e.nivel == vitima.level() && e.extra == 2
                        && vitima.position().distanceTo(e.ponto) <= 7.5) { dano *= 0.5f; break; }
            }
            // dentro da Égide da Aurora (segue o Paladino): 60% do dano é barrado e fica guardado para o clarão final
            for (Map.Entry<UUID, Estado> en : ESTADOS.entrySet()) {
                Estado e = en.getValue();
                if (e.ativo <= 0 || e.extra != 3 || e.nivel != vitima.level() || vitima.getServer() == null) continue;
                ServerPlayer dono = vitima.getServer().getPlayerList().getPlayer(en.getKey());
                if (dono == null || dono.distanceTo(vitima) > 6.5) continue;
                e.guardado += dano * 0.6;
                dano *= 0.4f;
                break;
            }
        }
        ev.setAmount(dano);
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        if (ev.getSource().getEntity() instanceof ServerPlayer p && !aliado(ev.getEntity())) {
            switch (classe(p)) {
                case "LADINO" -> carregar(p, 8);
                case "ASSASSINO" -> carregar(p, 10);
                default -> { }
            }
        }
    }

    /** Flechas da Chuva de Luz não ferem aliados e somem ao tocar o chão. */
    @SubscribeEvent
    public void flecha(ProjectileImpactEvent ev) {
        if (!ev.getProjectile().getTags().contains(TAG_LUZ)) return;
        HitResult r = ev.getRayTraceResult();
        if (r instanceof EntityHitResult eh) {
            if (!(eh.getEntity() instanceof LivingEntity le) || !inimigo(le)) ev.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        } else if (r.getType() == HitResult.Type.BLOCK) {
            Entity f = ev.getProjectile();
            if (f.level() instanceof ServerLevel nv) nv.sendParticles(ParticleTypes.END_ROD, f.getX(), f.getY(), f.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
            f.discard();
            ev.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) { terminarBerserk(p, false); ESTADOS.remove(p.getUUID()); }
    }

    @SubscribeEvent
    public void aoMorrerJogador(PlayerEvent.PlayerRespawnEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) {
            Estado e = est(p);
            if (e.ativo > 0 && "GUERREIRO".equals(classe(p))) terminarBerserk(p, false);
            e.ativo = 0;
            e.extra = 0;
            e.enviadoCarga = -1;
        }
    }

    // ------------------------------------------------------------------ tecla

    public static void acionar(ServerPlayer p) {
        String c = classe(p);
        if (c.isEmpty()) { avisar(p, "Escolha sua classe primeiro (tecla K).", ChatFormatting.YELLOW); return; }
        if ("INVOCADOR".equals(c)) { Sombras.erguer(p); return; }
        if (nivel(p) < NIVEL_MIN) { avisar(p, "O Despertar (" + nome(c) + ") libera no nível " + NIVEL_MIN + ".", ChatFormatting.GRAY); return; }
        Estado e = est(p);
        if ("ASSASSINO".equals(c) && e.ativo > 0 && e.extra > 0) { investida(p, e); return; }
        if (e.ativo > 0) { avisar(p, nome(c) + " já está ativo.", ChatFormatting.GRAY); return; }
        if (e.carga < 100) { avisar(p, "✦ " + nome(c) + ": " + (int) e.carga + "% — continue lutando para encher.", ChatFormatting.GRAY); return; }
        e.carga = 0;
        e.nivel = p.serverLevel();
        br.com.frxcode.frxworld.progressao.Marcos.marcar(p, "despertar");
        switch (c) {
            case "GUERREIRO" -> iniciarBerserk(p, e);
            case "ARQUEIRO" -> iniciarChuva(p, e);
            case "MAGO" -> iniciarExplosao(p, e);
            case "CLERIGO" -> iniciarEstandarte(p, e);
            case "LADINO" -> roubar(p, e);
            case "ASSASSINO" -> iniciarTrovao(p, e);
            case "PALADINO" -> iniciarEgide(p, e);
            case "BARDO" -> iniciarBis(p, e);
            case "MONGE" -> iniciarPortao(p, e);
            case "DRUIDA" -> iniciarFloresta(p, e);
            default -> { }
        }
        sincronizar(p, e, true);
    }

    private static void titulo(ServerPlayer p, String t, String sub, ChatFormatting cor) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(4, 30, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t).withStyle(cor, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
    }

    private static void avisar(ServerPlayer p, String t, ChatFormatting cor) {
        p.displayClientMessage(Component.literal(t).withStyle(cor), true);
    }

    private static void som(ServerPlayer p, Vec3 onde, SoundEvent s, float vol, float tom) {
        p.serverLevel().playSound(null, onde.x, onde.y, onde.z, s, SoundSource.PLAYERS, vol, tom);
    }

    private static Vec3 pontoMirado(ServerPlayer p, double alcance) {
        Vec3 olho = p.getEyePosition();
        Vec3 fim = olho.add(p.getLookAngle().scale(alcance));
        BlockHitResult r = p.level().clip(new ClipContext(olho, fim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return r.getType() == HitResult.Type.MISS ? fim : r.getLocation();
    }

    private static List<LivingEntity> inimigosEm(ServerLevel nv, Vec3 centro, double raio, int max) {
        List<LivingEntity> l = nv.getEntitiesOfClass(LivingEntity.class, new AABB(centro, centro).inflate(raio),
                x -> inimigo(x) && x.position().distanceTo(centro) <= raio);
        l.sort((a, b) -> Double.compare(a.position().distanceTo(centro), b.position().distanceTo(centro)));
        return l.size() > max ? l.subList(0, max) : l;
    }

    // ------------------------------------------------------------------ Guerreiro · Armadura do Berserker (Berserk)

    private static void iniciarBerserk(ServerPlayer p, Estado e) {
        e.total = e.ativo = 20 * (10 + Math.min(10, nivel(p) / 5));
        var vel = p.getAttribute(Attributes.ATTACK_SPEED);
        if (vel != null) { vel.removeModifier(MOD_BERSERK); vel.addTransientModifier(new AttributeModifier(MOD_BERSERK, "FRX berserk", 0.4, AttributeModifier.Operation.MULTIPLY_TOTAL)); }
        var mov = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (mov != null) { mov.removeModifier(MOD_BERSERK_VEL); mov.addTransientModifier(new AttributeModifier(MOD_BERSERK_VEL, "FRX berserk", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL)); }
        var kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) { kb.removeModifier(MOD_BERSERK); kb.addTransientModifier(new AttributeModifier(MOD_BERSERK, "FRX berserk", 1.0, AttributeModifier.Operation.ADDITION)); }
        titulo(p, "ARMADURA DO BERSERKER", "+50% dano · −25% dano recebido · não cai", ChatFormatting.DARK_RED);
        som(p, p.position(), SoundEvents.WITHER_SPAWN, 0.6f, 1.4f);
        p.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
    }

    private static void tickBerserk(ServerPlayer p, Estado e) {
        if (e.ativo % 4 == 0)
            p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.55f, 0.05f, 0.05f), 1.4f), p.getX(), p.getY() + 1, p.getZ(), 4, 0.35, 0.6, 0.35, 0);
        if (e.ativo % 40 == 0 && p.getHealth() > 2) p.setHealth(p.getHealth() - 1);   // a armadura cobra sangue
        if (e.ativo == 1) terminarBerserk(p, true);
    }

    private static void terminarBerserk(ServerPlayer p, boolean cansaco) {
        var vel = p.getAttribute(Attributes.ATTACK_SPEED);
        if (vel != null) vel.removeModifier(MOD_BERSERK);
        var mov = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (mov != null) mov.removeModifier(MOD_BERSERK_VEL);
        var kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(MOD_BERSERK);
        if (cansaco) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
            avisar(p, "A armadura solta seu corpo... você está exausto.", ChatFormatting.DARK_RED);
        }
    }

    // ------------------------------------------------------------------ Arqueiro · Chuva de Luz (Bleach)

    private static void iniciarChuva(ServerPlayer p, Estado e) {
        e.ponto = pontoMirado(p, 48);
        e.total = e.ativo = 60;
        titulo(p, "CHUVA DE LUZ", "flechas espirituais caem sobre os inimigos", ChatFormatting.AQUA);
        som(p, e.ponto, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.6f);
    }

    private static void tickChuva(ServerPlayer p, Estado e) {
        ServerLevel nv = e.nivel;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {   // luz caindo pela área toda
            double x = e.ponto.x + r.nextDouble(-9, 9), z = e.ponto.z + r.nextDouble(-9, 9);
            nv.sendParticles(ParticleTypes.END_ROD, x, e.ponto.y + 8, z, 0, 0, -1, 0, 0.8);
        }
        if (e.ativo % 12 != 0) return;
        double base = 2.0 + nivel(p) * 0.08;
        for (LivingEntity alvo : inimigosEm(nv, e.ponto, 9, 12)) {
            Arrow a = new Arrow(nv, p);
            a.setPos(alvo.getX() + r.nextDouble(-0.3, 0.3), alvo.getY() + alvo.getBbHeight() + 7, alvo.getZ() + r.nextDouble(-0.3, 0.3));
            a.setDeltaMovement(0, -2.4, 0);
            a.setBaseDamage(base);
            a.setCritArrow(true);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            a.addTag(TAG_LUZ);
            a.setGlowingTag(true);
            nv.addFreshEntity(a);
            FLECHAS.add(a);
        }
        som(p, e.ponto, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.8f);
    }

    // ------------------------------------------------------------------ Mago · Explosão! (Konosuba)

    private static final String[] CANTO = {
            "“Ó escuridão além da noite, carmesim além do sangue...”",
            "“...desperta, chama que dorme sob o mundo...”",
            "“...que tudo diante de mim vire cinza!”"
    };

    private static void iniciarExplosao(ServerPlayer p, Estado e) {
        e.ponto = pontoMirado(p, 50);
        e.total = e.ativo = 60;
        e.extra = 1;   // cantando
        e.vidaInicio = p.getHealth();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 62, 3, false, false));
        som(p, p.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1f, 0.6f);
    }

    private static void tickExplosao(ServerPlayer p, Estado e) {
        ServerLevel nv = e.nivel;
        int passo = e.total - e.ativo;
        if (passo % 20 == 0 && passo / 20 < CANTO.length) avisar(p, CANTO[passo / 20], ChatFormatting.RED);
        // círculo mágico se fechando no alvo
        double raio = 7 * e.ativo / (double) e.total + 0.5;
        for (int i = 0; i < 16; i++) {
            double ang = Math.PI * 2 * i / 16 + passo * 0.15;
            nv.sendParticles(ParticleTypes.FLAME, e.ponto.x + Math.cos(ang) * raio, e.ponto.y + 0.2, e.ponto.z + Math.sin(ang) * raio, 1, 0, 0.05, 0, 0);
        }
        if (passo % 4 == 0) nv.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.5, p.getZ(), 10, 0.5, 0.5, 0.5, 0.5);
        if (e.ativo != 1) return;

        e.extra = 0;
        int lvl = nivel(p);
        double danoMax = Math.min(140, 22 + lvl * 1.4);
        nv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, e.ponto.x, e.ponto.y + 1, e.ponto.z, 3, 1.5, 1, 1.5, 0);
        nv.sendParticles(ParticleTypes.FLAME, e.ponto.x, e.ponto.y + 1, e.ponto.z, 250, 3, 2, 3, 0.25);
        nv.sendParticles(ParticleTypes.LAVA, e.ponto.x, e.ponto.y + 1, e.ponto.z, 40, 2, 1, 2, 0);
        nv.playSound(null, e.ponto.x, e.ponto.y, e.ponto.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6f, 0.6f);
        nv.playSound(null, e.ponto.x, e.ponto.y, e.ponto.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3f, 0.5f);
        int atingidos = 0;
        for (LivingEntity alvo : inimigosEm(nv, e.ponto, 7, 40)) {
            double d = alvo.position().distanceTo(e.ponto);
            alvo.invulnerableTime = 0;
            alvo.hurt(p.damageSources().indirectMagic(p, p), (float) (danoMax * (1 - 0.5 * d / 7)));
            alvo.setSecondsOnFire(5);
            Vec3 emp = alvo.position().subtract(e.ponto).normalize().scale(1.2);
            alvo.push(emp.x, 0.6, emp.z);
            atingidos++;
        }
        titulo(p, "EXPLOSÃO!!", atingidos + " inimigo(s) atingido(s)", ChatFormatting.RED);
        // o preço: a maga desaba
        Progressao.dados(p).definirRecurso(0);
        Habilidades.atualizarRecurso(p);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 3));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
        avisar(p, "Você gastou tudo e desabou... (8 s exausto, mana zerada)", ChatFormatting.GRAY);
    }

    // ------------------------------------------------------------------ Clérigo · Estandarte da Donzela (Fate)

    private static void iniciarEstandarte(ServerPlayer p, Estado e) {
        e.ponto = p.position();
        e.total = e.ativo = 20 * (10 + Math.min(8, nivel(p) / 10));
        e.extra = 2;
        titulo(p, "ESTANDARTE DA DONZELA", "aliados curados e protegidos · mortos-vivos queimam", ChatFormatting.YELLOW);
        som(p, e.ponto, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.2f);
        som(p, e.ponto, SoundEvents.BELL_BLOCK, 1.5f, 1f);
    }

    private static void tickEstandarte(ServerPlayer p, Estado e) {
        ServerLevel nv = e.nivel;
        Vec3 c = e.ponto;
        if (e.ativo % 5 == 0) {
            for (int i = 0; i < 28; i++) {
                double ang = Math.PI * 2 * i / 28;
                nv.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(ang) * 7, c.y + 0.2, c.z + Math.sin(ang) * 7, 1, 0, 0.02, 0, 0);
            }
            for (int y = 0; y < 6; y++) nv.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.85f, 0.3f), 1.6f), c.x, c.y + y * 0.6, c.z, 2, 0.05, 0.1, 0.05, 0);
        }
        if (e.ativo % 20 != 0) return;
        int lvl = nivel(p);
        for (LivingEntity x : nv.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(7.5), x -> x.isAlive() && x.position().distanceTo(c) <= 7.5)) {
            if (aliado(x)) {
                x.heal((float) (1.5 + lvl * 0.06));
                x.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, true));
                nv.sendParticles(ParticleTypes.HEART, x.getX(), x.getY() + x.getBbHeight() + 0.3, x.getZ(), 1, 0.2, 0.1, 0.2, 0);
            } else if (inimigo(x)) {
                x.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                x.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0));
                if (x.getMobType() == MobType.UNDEAD) {
                    x.invulnerableTime = 0;
                    x.hurt(p.damageSources().indirectMagic(p, p), (float) (2 + lvl * 0.12));
                    x.setSecondsOnFire(2);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Paladino · Égide da Aurora (original do FRX-WORLD)

    private static void iniciarEgide(ServerPlayer p, Estado e) {
        e.total = e.ativo = 20 * (8 + Math.min(6, nivel(p) / 12));
        e.extra = 3;
        e.guardado = 0;
        e.ponto = p.position();
        titulo(p, "ÉGIDE DA AURORA", "a cúpula barra 60% do dano · o que ela barrar volta no clarão final", ChatFormatting.GOLD);
        som(p, p.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 1.5f);
        som(p, p.position(), SoundEvents.SHIELD_BLOCK, 1.5f, 0.7f);
    }

    private static void tickEgide(ServerPlayer p, Estado e) {
        ServerLevel nv = p.serverLevel();
        e.nivel = nv;
        e.ponto = p.position();
        Vec3 c = p.position();
        if (e.ativo % 4 == 0) {
            double giro = e.ativo * 0.12;
            for (int i = 0; i < 20; i++) {
                double ang = giro + Math.PI * 2 * i / 20;
                nv.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(ang) * 6, c.y + 0.3 + (i % 4) * 0.7, c.z + Math.sin(ang) * 6, 1, 0, 0.02, 0, 0);
            }
        }
        if (e.ativo % 20 == 0) {
            for (LivingEntity x : nv.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(6.5), x -> x.isAlive() && x.distanceTo(p) <= 6.5 && aliado(x)))
                x.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, true));
        }
        if (e.ativo != 1) return;
        // clarão final: tudo o que a cúpula barrou, mais um tanto pelo nível
        float dano = (float) Math.min(40 + nivel(p), 6 + nivel(p) * 0.3 + e.guardado * 1.5);
        for (LivingEntity x : nv.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(8), x -> x.isAlive() && x.distanceTo(p) <= 8)) {
            if (aliado(x)) { x.heal((float) Math.min(20, 2 + e.guardado * 0.2)); continue; }
            if (!inimigo(x)) continue;
            x.invulnerableTime = 0;
            x.hurt(p.damageSources().indirectMagic(p, p), x.getMobType() == MobType.UNDEAD ? dano * 2 : dano);
            Vec3 fora = x.position().subtract(c).multiply(1, 0, 1);
            if (fora.lengthSqr() > 0.01) { fora = fora.normalize(); x.setDeltaMovement(fora.x * 1.1, 0.5, fora.z * 1.1); x.hurtMarked = true; }
        }
        nv.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 3, 0.5, 0.5, 0.5, 0);
        nv.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 120, 0.4, 0.6, 0.4, 0.6);
        som(p, c, SoundEvents.GENERIC_EXPLODE, 1.5f, 1.5f);
        som(p, c, SoundEvents.BELL_BLOCK, 2f, 0.6f);
        p.displayClientMessage(Component.literal("A Égide devolveu " + Math.round(dano) + " de dano em luz.").withStyle(ChatFormatting.GOLD), true);
        e.guardado = 0;
    }

    // ------------------------------------------------------------------ Bardo · Bis! (original do FRX-WORLD)

    private static void iniciarBis(ServerPlayer p, Estado e) {
        e.total = e.ativo = 20 * 8;
        e.extra = 5;
        ServerLevel nv = p.serverLevel();
        int quantos = 0;
        for (ServerPlayer a : nv.players()) {
            if (!a.isAlive() || a.isSpectator() || a.distanceTo(p) > 12) continue;
            Habilidades.encurtarRecargas(a, 1);
            Habilidades.darRecurso(a, 1000);
            Cancoes.inspirar(a, 8000, 0.20);
            a.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1, true, true));
            nv.sendParticles(ParticleTypes.NOTE, a.getX(), a.getY() + 2.1, a.getZ(), 8, 0.4, 0.3, 0.4, 1);
            if (a != p) { titulo(a, "BIS!", p.getGameProfile().getName() + " zerou as suas recargas · +20% de dano por 8 s", ChatFormatting.LIGHT_PURPLE); quantos++; }
        }
        titulo(p, "BIS!", "recargas zeradas e recurso cheio para você" + (quantos > 0 ? " e mais " + quantos : "") + " · +20% de dano por 8 s", ChatFormatting.LIGHT_PURPLE);
        som(p, p.position(), SoundEvents.NOTE_BLOCK_BELL.value(), 2f, 1.2f);
        som(p, p.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.5f);
    }

    // ------------------------------------------------------------------ Monge · Portão Interior (original do FRX-WORLD)

    /** O Portão Interior do Monge está aberto? (socos em dobro com onda de choque, habilidades sem custo de chi) */
    public static boolean portaoAtivo(ServerPlayer p) {
        Estado e = ESTADOS.get(p.getUUID());
        return e != null && e.ativo > 0 && e.extra == 4;
    }

    private static void iniciarPortao(ServerPlayer p, Estado e) {
        e.total = e.ativo = 20 * (8 + Math.min(4, nivel(p) / 15));
        e.extra = 4;
        Habilidades.darRecurso(p, 1000);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, e.ativo, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, e.ativo, 1, false, false));
        titulo(p, "PORTÃO INTERIOR", "socos em dobro, com onda de choque · o chi não gasta", ChatFormatting.AQUA);
        som(p, p.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        som(p, p.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 1.8f);
    }

    private static void tickPortao(ServerPlayer p, Estado e) {
        ServerLevel nv = p.serverLevel();
        e.nivel = nv;
        if (e.ativo % 3 == 0) nv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 0.9, p.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
        if (e.ativo != 1) return;
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
        p.displayClientMessage(Component.literal("O portão se fecha. Você fica exausto por 5 s.").withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ Druida · Coração da Floresta (original do FRX-WORLD)

    /** Coração da Floresta ativo? (habilidades sem custo, garras +50%, formas sem a fraqueza de dano recebido) */
    public static boolean florestaAtiva(ServerPlayer p) {
        Estado e = ESTADOS.get(p.getUUID());
        return e != null && e.ativo > 0 && e.extra == 6;
    }

    private static void iniciarFloresta(ServerPlayer p, Estado e) {
        e.total = e.ativo = 20 * 10;
        e.extra = 6;
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, e.ativo, 1, false, true));
        titulo(p, "CORAÇÃO DA FLORESTA", "habilidades sem custo · garras +50% · as formas perdem as fraquezas", ChatFormatting.GREEN);
        som(p, p.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 0.9f);
        som(p, p.position(), SoundEvents.WOLF_HOWL, 1.2f, 1f);
        p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 60, 1.5, 1, 1.5, 0.1);
    }

    // ------------------------------------------------------------------ Ladino · Roubar! (Konosuba / Lupin III)

    private static void roubar(ServerPlayer p, Estado e) {
        ServerLevel nv = p.serverLevel();
        int lvl = nivel(p);
        double chance = Math.min(0.95, 0.6 + lvl * 0.008);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int alvos = 0, itens = 0, cobre = 0, armas = 0;
        boolean sorte = false;
        for (LivingEntity alvo : inimigosEm(nv, p.position(), 8, 10)) {
            alvos++;
            if (r.nextDouble() >= chance) {
                nv.sendParticles(ParticleTypes.SMOKE, alvo.getX(), alvo.getY() + 1, alvo.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                continue;
            }
            boolean boss = chefe(alvo);
            // desarma
            if (!boss && alvo instanceof Mob m && !m.getMainHandItem().isEmpty()) {
                dar(p, m.getMainHandItem().copy());
                m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                armas++;
            }
            // tira um pouco do que ela carrega (uma rolagem do saque)
            if (!boss && !alvo.getTags().contains("frx_roubado")) {
                alvo.addTag("frx_roubado");
                try {
                    LootTable tabela = nv.getServer().getLootData().getLootTable(alvo.getLootTable());
                    LootParams params = new LootParams.Builder(nv)
                            .withParameter(LootContextParams.THIS_ENTITY, alvo)
                            .withParameter(LootContextParams.ORIGIN, alvo.position())
                            .withParameter(LootContextParams.DAMAGE_SOURCE, p.damageSources().playerAttack(p))
                            .withOptionalParameter(LootContextParams.KILLER_ENTITY, p)
                            .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, p)
                            .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, p)
                            .withLuck(p.getLuck())
                            .create(LootContextParamSets.ENTITY);
                    for (ItemStack s : tabela.getRandomItems(params)) { dar(p, s); itens += s.getCount(); }
                } catch (Exception ignorar) { }
            }
            int c = r.nextInt(2, 9) * (1 + lvl / 20) * (boss ? 5 : 1);
            cobre += c;
            if (r.nextDouble() < 0.02) sorte = true;
            alvo.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
            alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0));
            nv.sendParticles(ParticleTypes.WAX_OFF, alvo.getX(), alvo.getY() + 1, alvo.getZ(), 10, 0.3, 0.4, 0.3, 0.1);
        }
        if (cobre > 0) Moedas.dar(p, cobre);
        if (sorte) dar(p, new ItemStack(Moedas.OURO.get()));
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false));
        som(p, p.position(), SoundEvents.ITEM_PICKUP, 1f, 0.7f);
        som(p, p.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
        titulo(p, "ROUBAR!", alvos == 0 ? "...ninguém por perto. O vento levou sua chance." :
                (itens + " item(ns) · " + armas + " arma(s) · " + cobre + " cobre" + (sorte ? " · ✦ MOEDA DE OURO!" : "")), ChatFormatting.GREEN);
    }

    private static void dar(ServerPlayer p, ItemStack s) {
        if (s.isEmpty()) return;
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    // ------------------------------------------------------------------ Assassino · Respiração do Trovão (Demon Slayer)

    private static void iniciarTrovao(ServerPlayer p, Estado e) {
        e.total = e.ativo = 100;   // 5 s para usar as investidas
        e.extra = 3;
        titulo(p, "RESPIRAÇÃO DO TROVÃO", "Primeira Forma · aperte de novo para mais investidas (3)", ChatFormatting.YELLOW);
        investida(p, e);
    }

    private static void investida(ServerPlayer p, Estado e) {
        ServerLevel nv = p.serverLevel();
        Vec3 dir = p.getLookAngle();
        dir = new Vec3(dir.x, Math.max(-0.3, Math.min(0.3, dir.y)), dir.z).normalize();
        Vec3 ini = p.position().add(0, 0.5, 0);
        Vec3 fim = ini.add(dir.scale(12));
        BlockHitResult r = nv.clip(new ClipContext(ini, fim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (r.getType() != HitResult.Type.MISS) fim = r.getLocation().subtract(dir.scale(0.6));
        // volta até achar um lugar onde o jogador cabe
        Vec3 destino = fim.subtract(0, 0.5, 0);
        Vec3 origem = p.position();
        for (int i = 0; i < 30; i++) {
            if (nv.noCollision(p, p.getBoundingBox().move(destino.subtract(origem)))) break;
            destino = destino.subtract(dir.scale(0.5));
        }
        double mult = Math.min(2.6, 1.6 + nivel(p) * 0.02);
        float dano = (float) (p.getAttributeValue(Attributes.ATTACK_DAMAGE) * mult);
        Vec3 a = origem.add(0, 1, 0), b = destino.add(0, 1, 0);
        int acertos = 0;
        for (LivingEntity alvo : nv.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.8), Despertar::inimigo)) {
            if (distanciaSegmento(alvo.getBoundingBox().getCenter(), a, b) > 1.8) continue;
            alvo.invulnerableTime = 0;
            alvo.hurt(p.damageSources().playerAttack(p), dano);
            nv.sendParticles(ParticleTypes.ELECTRIC_SPARK, alvo.getX(), alvo.getY() + 1, alvo.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
            acertos++;
        }
        // raio no caminho
        double dist = a.distanceTo(b);
        for (double t = 0; t <= dist; t += 0.4) {
            Vec3 pt = a.add(b.subtract(a).normalize().scale(t));
            nv.sendParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 2, 0.1, 0.1, 0.1, 0.05);
            nv.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.9f, 0.2f), 1.2f), pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0);
        }
        p.teleportTo(destino.x, destino.y, destino.z);
        p.fallDistance = 0;
        p.invulnerableTime = 10;
        nv.playSound(null, destino.x, destino.y, destino.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.8f);
        nv.playSound(null, destino.x, destino.y, destino.z, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1f, 1.3f);
        e.extra--;
        avisar(p, "⚡ Investida" + (acertos > 0 ? " · " + acertos + " corte(s)" : "") + (e.extra > 0 ? " · resta(m) " + e.extra : ""), ChatFormatting.YELLOW);
        if (e.extra <= 0) e.ativo = 1;
        sincronizar(p, e, true);
    }

    private static double distanciaSegmento(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    // ------------------------------------------------------------------ tick

    private int ticks;

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        ticks++;
        long agora = System.currentTimeMillis();
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) {
            Estado e = ESTADOS.get(p.getUUID());
            if (e == null) {
                if (ticks % 20 == 0 && Progressao.temDados(p)) sincronizar(p, est(p), false);
                continue;
            }
            if (e.ativo > 0) {
                if (e.nivel != p.serverLevel() && !"GUERREIRO".equals(classe(p)) && !"ASSASSINO".equals(classe(p)) && !"PALADINO".equals(classe(p)) && !"MONGE".equals(classe(p)) && !"DRUIDA".equals(classe(p))) { e.ativo = 0; e.extra = 0; }
                else {
                    switch (classe(p)) {
                        case "GUERREIRO" -> tickBerserk(p, e);
                        case "ARQUEIRO" -> tickChuva(p, e);
                        case "MAGO" -> tickExplosao(p, e);
                        case "CLERIGO" -> tickEstandarte(p, e);
                        case "PALADINO" -> tickEgide(p, e);
                        case "MONGE" -> tickPortao(p, e);
                        default -> { }
                    }
                    e.ativo--;
                    if (e.ativo <= 0) { e.extra = 0; e.ponto = null; }
                }
            } else if (ticks % 20 == 0 && e.carga > 0 && agora - e.ultimoCombate > COMBATE_MS) {
                e.carga = Math.max(0, e.carga - 1);   // esfria devagar fora de combate
            }
            if (ticks % 5 == 0) sincronizar(p, e, false);
        }
        if (ticks % 20 == 0) {
            for (Iterator<Arrow> it = FLECHAS.iterator(); it.hasNext(); ) {
                Arrow a = it.next();
                if (!a.isAlive()) it.remove();
                else if (a.tickCount > 60) { a.discard(); it.remove(); }
            }
        }
    }

    private static void sincronizar(ServerPlayer p, Estado e, boolean sempre) {
        int carga = (int) e.carga, ativo = e.ativo / 5, extra = e.extra;
        if (!sempre && carga == e.enviadoCarga && ativo == e.enviadoAtivo && extra == e.enviadoExtra) return;
        e.enviadoCarga = carga;
        e.enviadoAtivo = ativo;
        e.enviadoExtra = extra;
        int lvl = Progressao.temDados(p) ? nivel(p) : 0;
        Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new SyncDespertar(carga, e.ativo, e.total, extra, lvl >= NIVEL_MIN));
    }

    /** Admin: enche a barra (testes). */
    public static void encher(ServerPlayer p) {
        est(p).carga = 100;
        sincronizar(p, est(p), true);
    }

    @SubscribeEvent
    public void comandos(net.minecraftforge.event.RegisterCommandsEvent ev) {
        ev.getDispatcher().register(net.minecraft.commands.Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("despertar").executes(ctx -> { encher(ctx.getSource().getPlayerOrException()); return 1; })));
    }
}
