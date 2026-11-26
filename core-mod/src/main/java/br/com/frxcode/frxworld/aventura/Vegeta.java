package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.musica.Discos;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.racas.RacasJogador;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Príncipe Vegeta, o guardião do topo do Templo Karin. Quem chega ao templo é desafiado; vencer libera a raça Saiyajin
 * para todos os que lutaram.
 *  - Mais gente na luta = mais vida (1.000 de vida × participantes) e mais dano (+30% por participante extra).
 *  - Todo ataque especial tem aviso para dar tempo de desviar: brilho e som na carga, linha de partículas onde o feixe vai passar.
 *  - Fases: abaixo de 66% vira Super Saiyajin (cabelo dourado, mais forte); abaixo de 33% ganha relâmpagos e o Final Flash.
 *  - Trilha sonora: o disco do painel de código "luta_vegeta" (se existir), tocando em volta durante a luta.
 *  - Se todos caírem, morrerem ou fugirem, ele vai embora; dá para tentar de novo 1 minuto depois.
 */
public class Vegeta {
    public static final DeferredRegister<EntityType<?>> TIPOS = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FrxWorld.MOD_ID);
    public static final RegistryObject<EntityType<VegetaEntidade>> VEGETA = TIPOS.register("principe_vegeta",
            () -> EntityType.Builder.<VegetaEntidade>of(VegetaEntidade::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(10).fireImmune()
                    .build("principe_vegeta"));

    public static void registrar(IEventBus modBus) {
        TIPOS.register(modBus);
        modBus.addListener(Vegeta::atributos);
    }

    private static void atributos(EntityAttributeCreationEvent ev) { ev.put(VEGETA.get(), VegetaEntidade.atributos().build()); }

    private static final double VIDA_BASE = 1000, DANO_BASE = 12;
    private static final String MUSICA = "luta_vegeta";
    private static final UUID PARADO = UUID.nameUUIDFromBytes("frxworld:vegeta:parado".getBytes());

    private enum Atq { NADA, RAJADA, GALICK, INVESTIDA, BIGBANG, CHUVA, FINAL_FLASH, TRANSFORMAR }

    private static final class Orbe {
        Vec3 pos, vel; double raio, explosao; float dano; int vida = 90; boolean grande;
    }

    // estado da luta
    private static UUID ent;
    private static int intro = -1, semNinguem, fase, espera = 60, t, contaFinalFlash;
    private static long proximaLuta, musicaRecomeca;
    private static final Set<UUID> PARTICIPANTES = new LinkedHashSet<>();
    private static int escalaDe = 1;
    private static ServerBossEvent barra;
    private static Atq atq = Atq.NADA;
    private static Vec3 dir, alvoPos;
    private static final Set<UUID> ATINGIDOS = new LinkedHashSet<>();
    private static final List<Orbe> ORBES = new ArrayList<>();
    private static boolean adicionando;

    // ------------------------------------------------------------------ utilidades

    private static DustParticleOptions cor(float r, float g, float b, float tam) { return new DustParticleOptions(new Vector3f(r, g, b), tam); }
    private static final DustParticleOptions OURO = cor(1f, 0.85f, 0.2f, 1.6f), ROXO = cor(0.6f, 0.2f, 0.95f, 1.4f), KI = cor(0.55f, 0.8f, 1f, 1.3f),
            AVISO_ROXO = cor(0.75f, 0.4f, 1f, 0.8f), AVISO_OURO = cor(1f, 0.9f, 0.3f, 0.9f), VERMELHO = cor(1f, 0.25f, 0.15f, 1.2f);

    private static List<ServerPlayer> naArena(ServerLevel nv) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : nv.players()) if (p.isAlive() && !p.isSpectator() && !p.isCreative() && Karin.naArena(p)) l.add(p);
        return l;
    }

    private static List<ServerPlayer> perto(ServerLevel nv, double raio) {
        Vec3 c = Karin.centroArena();
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : nv.players()) if (p.position().distanceTo(c) <= raio) l.add(p);
        return l;
    }

    private static void falar(ServerLevel nv, String texto) {
        Component c = Component.literal("[Príncipe Vegeta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(texto).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : perto(nv, 64)) p.sendSystemMessage(c);
    }

    private static void titulo(ServerLevel nv, String t1, String t2, ChatFormatting cor) {
        for (ServerPlayer p : perto(nv, 64)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t1).withStyle(cor, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(t2).withStyle(ChatFormatting.YELLOW)));
        }
    }

    private static void grito(ServerLevel nv, String t1) {
        for (ServerPlayer p : perto(nv, 64)) p.displayClientMessage(Component.literal(t1).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
    }

    private static void raio(ServerLevel nv, Vec3 v) {
        LightningBolt r = EntityType.LIGHTNING_BOLT.create(nv);
        if (r == null) return;
        r.moveTo(v.x, v.y, v.z);
        r.setVisualOnly(true);
        nv.addFreshEntity(r);
    }

    private static VegetaEntidade entidade(ServerLevel nv) {
        return ent != null && nv.getEntity(ent) instanceof VegetaEntidade v ? v : null;
    }

    private static double multDano() { return (1 + 0.3 * (escalaDe - 1)) * (fase == 0 ? 1 : fase == 1 ? 1.25 : 1.5); }

    private static void ferir(VegetaEntidade v, ServerPlayer p, double dano, boolean magico) {
        if (!p.isAlive() || p.isCreative() || p.isSpectator()) return;
        p.invulnerableTime = 0;
        p.hurt(magico ? v.damageSources().indirectMagic(v, v) : v.damageSources().mobAttack(v), (float) dano);
    }

    private static void parar(VegetaEntidade v, boolean sim) {
        AttributeInstance a = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        a.removeModifier(PARADO);
        if (sim) a.addTransientModifier(new AttributeModifier(PARADO, "FRX Vegeta parado", -1, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static Vec3 mao(VegetaEntidade v) { return v.position().add(v.getLookAngle().scale(0.7)).add(0, 1.35, 0); }

    private static ServerPlayer alvo(ServerLevel nv, VegetaEntidade v) {
        if (v.getTarget() instanceof ServerPlayer p && Karin.naArena(p) && p.isAlive() && !p.isCreative()) return p;
        List<ServerPlayer> l = naArena(nv);
        if (l.isEmpty()) return null;
        ServerPlayer p = l.get(ThreadLocalRandom.current().nextInt(l.size()));
        v.setTarget(p);
        return p;
    }

    // ------------------------------------------------------------------ início e fim

    private static void comecarIntro(ServerLevel nv) {
        intro = 0;
        PARTICIPANTES.clear();
        for (ServerPlayer p : naArena(nv)) PARTICIPANTES.add(p.getUUID());
    }

    private static void tickIntro(ServerLevel nv) {
        Vec3 c = Karin.centroArena();
        if (intro == 0) {
            falar(nv, "Hmph. Então um verme conseguiu subir a torre inteira.");
            nv.playSound(null, BlockPos.containing(c), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.6f);
        }
        if (intro % 5 == 0) nv.sendParticles(KI, c.x, c.y + 8 - intro * 0.12, c.z, 25, 0.6, 0.6, 0.6, 0.05);
        if (intro == 25) falar(nv, "Eu sou Vegeta, o príncipe da raça guerreira mais orgulhosa do universo.");
        if (intro == 40) {
            raio(nv, c);
            VegetaEntidade v = VEGETA.get().create(nv);
            if (v != null) {
                v.moveTo(c.x, c.y + 3, c.z, 0, 0);
                v.setCustomName(Component.literal("Príncipe Vegeta").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                v.setCustomNameVisible(true);
                br.com.frxcode.frxworld.progressao.Zonas.marcarNivel(v, 60);
                adicionando = true;
                try { nv.addFreshEntity(v); } finally { adicionando = false; }
                ent = v.getUUID();
                escalaDe = 1;
                escalar(v, Math.max(1, PARTICIPANTES.size()));
                v.setHealth(v.getMaxHealth());
            }
        }
        if (intro == 50) falar(nv, "Se querem o sangue Saiyajin, provem que valem alguma coisa!");
        if (++intro < 65) return;
        intro = -1;
        VegetaEntidade v = entidade(nv);
        if (v == null) { encerrar(nv, false); return; }
        fase = 0; atq = Atq.NADA; espera = 40; semNinguem = 0; contaFinalFlash = 0;
        barra = new ServerBossEvent(Component.literal("Príncipe Vegeta").withStyle(ChatFormatting.GOLD), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
        titulo(nv, "PRÍNCIPE VEGETA", "O guardião do Templo Karin", ChatFormatting.GOLD);
        musicaRecomeca = 0;
    }

    /** Ajusta vida e dano ao número de participantes (só sobe). */
    private static void escalar(VegetaEntidade v, int n) {
        if (n <= escalaDe && v.getMaxHealth() >= VIDA_BASE * n - 1) return;
        float prop = v.getHealth() / Math.max(1, v.getMaxHealth());
        escalaDe = Math.max(escalaDe, n);
        AttributeInstance vida = v.getAttribute(Attributes.MAX_HEALTH);
        if (vida != null) vida.setBaseValue(VIDA_BASE * escalaDe);
        AttributeInstance dano = v.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dano != null) dano.setBaseValue(DANO_BASE * multDano());
        v.setHealth(Math.max(1, prop * v.getMaxHealth()));
    }

    private static void encerrar(ServerLevel nv, boolean venceram) {
        VegetaEntidade v = entidade(nv);
        if (v != null && v.isAlive()) {
            nv.sendParticles(KI, v.getX(), v.getY() + 1, v.getZ(), 60, 0.5, 1, 0.5, 0.2);
            v.discard();
        }
        if (barra != null) { barra.removeAllPlayers(); barra = null; }
        pararMusica(nv);
        ent = null; intro = -1; atq = Atq.NADA; ORBES.clear(); PARTICIPANTES.clear();
        proximaLuta = System.currentTimeMillis() + (venceram ? 120_000 : 60_000);
    }

    private static void vitoria(ServerLevel nv, VegetaEntidade v) {
        Vec3 p0 = v.position();
        raio(nv, p0);
        nv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p0.x, p0.y + 1, p0.z, 2, 0.5, 0.5, 0.5, 0);
        nv.sendParticles(OURO, p0.x, p0.y + 1, p0.z, 200, 2, 2, 2, 0.3);
        falar(nv, "...Tsc. Vocês venceram. Eu reconheço: o sangue guerreiro corre em vocês.");
        List<String> nomes = new ArrayList<>();
        MinecraftServer s = nv.getServer();
        for (UUID id : PARTICIPANTES) {
            ServerPlayer p = s.getPlayerList().getPlayer(id);
            if (p == null) continue;
            nomes.add(p.getGameProfile().getName());
            if (Progressao.temDados(p)) Progressao.dados(p).contadores().merge("c:vegeta", 1, Integer::sum);
            if (RacasJogador.liberarRaca(p, "saiyajin")) {
                p.sendSystemMessage(Component.literal("[Príncipe Vegeta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("Você é digno. A partir de hoje, pode se chamar de Saiyajin. Não me envergonhe.").withStyle(ChatFormatting.WHITE)));
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("SAIYAJIN LIBERADO").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Abra /raca para despertar o sangue guerreiro").withStyle(ChatFormatting.YELLOW)));
            } else {
                p.sendSystemMessage(Component.literal("[Príncipe Vegeta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("Você de novo? Hmph... foi um bom treino.").withStyle(ChatFormatting.WHITE)));
            }
            Boolean primeiro = br.com.frxcode.frxworld.protecao.AreasProtegidas.registrar(nv, "vegeta", p);
            if (Boolean.TRUE.equals(primeiro))
                s.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + p.getGameProfile().getName() + " foi o primeiro do servidor a vencer o Príncipe Vegeta! ✦")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        }
        if (!nomes.isEmpty())
            s.getPlayerList().broadcastSystemMessage(Component.literal("⚡ " + String.join(", ", nomes) + (nomes.size() == 1 ? " derrotou" : " derrotaram")
                    + " o Príncipe Vegeta no topo do Templo Karin! O sangue Saiyajin desperta. ⚡").withStyle(ChatFormatting.GOLD), false);
        FrxWorld.LOG.info("Vegeta vencido por {}.", nomes);
    }

    // ------------------------------------------------------------------ música

    private static void tocarMusica(ServerLevel nv) {
        Discos.Def d = Discos.def(MUSICA);
        if (d == null) return;
        long agora = System.currentTimeMillis();
        if (agora < musicaRecomeca) return;
        musicaRecomeca = agora + Math.max(30, d.duracao()) * 1000L;
        Vec3 c = Karin.centroArena();
        nv.playSound(null, c.x, c.y + 4, c.z, SoundEvent.createVariableRangeEvent(Discos.som(MUSICA)), SoundSource.RECORDS, 5f, 1f);
    }

    private static void pararMusica(ServerLevel nv) {
        ResourceLocation som = Discos.som(MUSICA);
        for (ServerPlayer p : perto(nv, 120)) p.connection.send(new ClientboundStopSoundPacket(som, SoundSource.RECORDS));
        musicaRecomeca = 0;
    }

    // ------------------------------------------------------------------ laço da luta

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        ServerLevel nv = ev.getServer().overworld();
        if (!ORBES.isEmpty()) orbes(nv);
        if (ent == null && intro < 0) {
            if (nv.getGameTime() % 10 != 0 || System.currentTimeMillis() < proximaLuta) return;
            if (!nv.hasChunkAt(BlockPos.containing(Karin.centroArena()))) return;
            if (!naArena(nv).isEmpty()) comecarIntro(nv);
            return;
        }
        if (intro >= 0) { tickIntro(nv); return; }
        VegetaEntidade v = entidade(nv);
        if (v == null) {
            if (!nv.hasChunkAt(BlockPos.containing(Karin.centroArena()))) encerrar(nv, false);
            return;
        }
        if (!v.isAlive()) return;
        // participantes e escala
        List<ServerPlayer> arena = naArena(nv);
        for (ServerPlayer p : arena) PARTICIPANTES.add(p.getUUID());
        escalar(v, PARTICIPANTES.size());
        if (barra != null) {
            barra.setProgress(Math.max(0, Math.min(1, v.getHealth() / v.getMaxHealth())));
            Set<ServerPlayer> ver = new LinkedHashSet<>(perto(nv, 48));
            for (ServerPlayer p : new ArrayList<>(barra.getPlayers())) if (!ver.contains(p)) barra.removePlayer(p);
            for (ServerPlayer p : ver) barra.addPlayer(p);
        }
        tocarMusica(nv);
        // abandono
        if (arena.isEmpty()) {
            if (++semNinguem > 200) {
                falar(nv, "Hmph. Fugiram? Nem valia o meu tempo.");
                encerrar(nv, false);
                return;
            }
        } else semNinguem = 0;
        // não sai da arena
        Vec3 c = Karin.centroArena();
        double dx = v.getX() - c.x, dz = v.getZ() - c.z;
        double limite = Karin.RAIO_ARENA - 2.5;
        if (dx * dx + dz * dz > limite * limite || v.getY() < Karin.TOPO - 3) {
            nv.sendParticles(KI, v.getX(), v.getY() + 1, v.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
            v.teleportTo(c.x, c.y, c.z);
            v.setDeltaMovement(Vec3.ZERO);
        }
        // fases
        float prop = v.getHealth() / v.getMaxHealth();
        if (atq == Atq.NADA && ((fase == 0 && prop <= 0.66f) || (fase == 1 && prop <= 0.33f))) iniciar(nv, v, Atq.TRANSFORMAR);
        // aura
        if (fase >= 1 && nv.getGameTime() % 3 == 0) nv.sendParticles(OURO, v.getX(), v.getY() + 1, v.getZ(), 3, 0.35, 0.8, 0.35, 0.02);
        if (fase >= 2 && nv.getGameTime() % 4 == 0) nv.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getY() + 1.2, v.getZ(), 4, 0.4, 0.7, 0.4, 0.1);
        // ataques
        if (atq != Atq.NADA) { passo(nv, v); return; }
        if (contaFinalFlash > 0) contaFinalFlash--;
        if (--espera > 0 || arena.isEmpty()) return;
        List<Atq> opcoes = new ArrayList<>(List.of(Atq.RAJADA, Atq.RAJADA, Atq.GALICK, Atq.INVESTIDA));
        if (fase >= 1) { opcoes.add(Atq.BIGBANG); opcoes.add(Atq.CHUVA); opcoes.add(Atq.GALICK); }
        if (fase >= 2 && contaFinalFlash == 0) { opcoes.add(Atq.FINAL_FLASH); opcoes.add(Atq.FINAL_FLASH); }
        iniciar(nv, v, opcoes.get(ThreadLocalRandom.current().nextInt(opcoes.size())));
    }

    private static void iniciar(ServerLevel nv, VegetaEntidade v, Atq a) {
        atq = a;
        t = 0;
        ATINGIDOS.clear();
        ServerPlayer p = alvo(nv, v);
        if (p == null && a != Atq.TRANSFORMAR) { atq = Atq.NADA; espera = 20; return; }
        Vec3 olho = v.position().add(0, 1.5, 0);
        if (p != null) {
            alvoPos = p.position();
            dir = p.position().add(0, 1, 0).subtract(olho).normalize();
            v.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
        }
        if (a != Atq.INVESTIDA && a != Atq.CHUVA) parar(v, true);
    }

    private static void terminar(VegetaEntidade v, int proxima) {
        atq = Atq.NADA;
        parar(v, false);
        v.setNoGravity(false);
        espera = proxima - fase * 8;
    }

    /** Um tick do ataque em andamento. */
    private static void passo(ServerLevel nv, VegetaEntidade v) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double m = multDano();
        switch (atq) {
            case RAJADA -> {
                Vec3 h = mao(v);
                if (t < 15) nv.sendParticles(KI, h.x, h.y, h.z, 4, 0.15, 0.15, 0.15, 0.01);
                if (t == 0) nv.playSound(null, v.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1f, 1.8f);
                if (t == 15 || t == 21 || t == 27) {
                    ServerPlayer p = alvo(nv, v);
                    if (p != null) disparar(h, p.position().add(0, 1, 0), 0.85, 1.2, 2.5, (float) (7 * m), false);
                    nv.playSound(null, v.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1f, 1.6f);
                }
                if (t >= 32) terminar(v, 45);
            }
            case GALICK -> {
                Vec3 h = mao(v);
                if (t < 32) {
                    nv.sendParticles(ROXO, h.x, h.y, h.z, 6, 0.4, 0.4, 0.4, 0.02);
                    if (t % 3 == 0) linha(nv, h, dir, 30, AVISO_ROXO, 1);
                    if (t == 0) { grito(nv, "Vegeta junta energia roxa nas mãos... saia da linha!"); nv.playSound(null, v.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1f, 1.2f); }
                } else if (t < 48) {
                    if (t == 32) { grito(nv, "GALICK HO!"); nv.playSound(null, v.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2f, 1.1f); }
                    feixe(nv, v, h, dir, 30, 1.6, 15 * m, ROXO);
                } else terminar(v, 55);
            }
            case INVESTIDA -> {
                if (t < 14) {
                    parar(v, true);
                    nv.sendParticles(VERMELHO, v.getX(), v.getY() + 0.2, v.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
                    if (t == 0) nv.playSound(null, v.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8f, 1.6f);
                } else if (t < 24) {
                    parar(v, false);
                    Vec3 d = alvoPos.subtract(v.position()).multiply(1, 0, 1);
                    if (d.lengthSqr() > 1) {
                        Vec3 vel = d.normalize().scale(1.5);
                        v.setDeltaMovement(vel.x, 0.05, vel.z);
                        v.hurtMarked = true;
                    }
                    nv.sendParticles(KI, v.getX(), v.getY() + 1, v.getZ(), 4, 0.2, 0.5, 0.2, 0.01);
                    for (ServerPlayer p : naArena(nv))
                        if (p.distanceTo(v) < 1.9 && ATINGIDOS.add(p.getUUID())) {
                            ferir(v, p, 10 * m, false);
                            Vec3 k = p.position().subtract(v.position()).multiply(1, 0, 1).normalize().scale(1.1);
                            p.push(k.x, 0.45, k.z);
                            p.hurtMarked = true;
                        }
                } else terminar(v, 40);
            }
            case BIGBANG -> {
                Vec3 h = mao(v);
                if (t < 30) {
                    nv.sendParticles(KI, h.x, h.y, h.z, 3 + t / 3, 0.1 + t * 0.012, 0.1 + t * 0.012, 0.1 + t * 0.012, 0.01);
                    if (t == 0) { grito(nv, "Uma esfera enorme cresce na mão de Vegeta!"); nv.playSound(null, v.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.2f, 0.6f); }
                } else if (t == 30) {
                    grito(nv, "BIG BANG ATTACK!");
                    ServerPlayer p = alvo(nv, v);
                    if (p != null) disparar(h, p.position().add(0, 0.8, 0), 0.5, 2.0, 4.5, (float) (18 * m), true);
                    nv.playSound(null, v.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 1.5f, 0.6f);
                } else if (t > 36) terminar(v, 55);
            }
            case CHUVA -> {
                if (t == 0) { v.setNoGravity(true); grito(nv, "Vegeta sobe no ar... chuva de rajadas!"); }
                if (t < 15) { v.setDeltaMovement(0, 0.33, 0); v.hurtMarked = true; }
                else if (t < 45) {
                    v.setDeltaMovement(Vec3.ZERO);
                    if (t % 3 == 0) {
                        List<ServerPlayer> l = naArena(nv);
                        if (!l.isEmpty()) {
                            ServerPlayer p = l.get(r.nextInt(l.size()));
                            Vec3 alvo = p.position().add(r.nextDouble(-2, 2), 0.3, r.nextDouble(-2, 2));
                            nv.sendParticles(VERMELHO, alvo.x, Karin.TOPO + 1.1, alvo.z, 8, 0.6, 0, 0.6, 0);
                            disparar(mao(v), alvo, 0.9, 1.0, 2.2, (float) (5 * m), false);
                            nv.playSound(null, v.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.7f, 1.8f);
                        }
                    }
                } else if (t == 45) v.setNoGravity(false);
                else if (t > 55) terminar(v, 50);
            }
            case FINAL_FLASH -> {
                Vec3 c = Karin.centroArena();
                if (t == 0) {
                    v.setNoGravity(true);
                    v.teleportTo(c.x, c.y + 7, c.z);
                    titulo(nv, "FINAL FLASH", "Vegeta vai varrer a arena: saia da linha dourada!", ChatFormatting.GOLD);
                    nv.playSound(null, v.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1f, 0.6f);
                    ServerPlayer p = alvo(nv, v);
                    Vec3 de = v.position().add(0, 1.4, 0);
                    dir = (p != null ? p.position().add(0, 0.5, 0) : c).subtract(de).normalize();
                }
                Vec3 de = v.position().add(0, 1.4, 0);
                v.setDeltaMovement(Vec3.ZERO);
                if (t < 60) {
                    nv.sendParticles(OURO, de.x, de.y, de.z, 8 + t / 4, 0.3 + t * 0.01, 0.3 + t * 0.01, 0.3 + t * 0.01, 0.02);
                    nv.sendParticles(ParticleTypes.ELECTRIC_SPARK, de.x, de.y, de.z, 3, 0.6, 0.6, 0.6, 0.1);
                    if (t % 3 == 0) {
                        Vec3 lado = dir.cross(new Vec3(0, 1, 0));
                        if (lado.lengthSqr() < 0.01) lado = new Vec3(1, 0, 0);
                        lado = lado.normalize().scale(2.6);
                        linha(nv, de, dir, 45, AVISO_OURO, 1);
                        linha(nv, de.add(lado), dir, 45, AVISO_OURO, 2);
                        linha(nv, de.subtract(lado), dir, 45, AVISO_OURO, 2);
                    }
                    if (t % 20 == 0) nv.playSound(null, v.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.2f, 0.5f + t * 0.01f);
                } else if (t < 85) {
                    if (t == 60) { grito(nv, "FINAL FLASH!!"); nv.playSound(null, v.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2f, 0.6f); }
                    feixe(nv, v, de, dir, 45, 2.8, 26 * m, OURO);
                } else if (t == 85) v.setNoGravity(false);
                else if (t > 95) { contaFinalFlash = 500; terminar(v, 60); }
            }
            case TRANSFORMAR -> {
                if (t == 0) {
                    v.setInvulnerable(true);
                    v.setNoGravity(true);
                    falar(nv, fase == 0 ? "Chega de brincadeira! Vou mostrar o verdadeiro poder de um Saiyajin!"
                            : "Impossível... ninguém humilha o príncipe! Eu vou varrer este templo!");
                    nv.playSound(null, v.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5f, 1.2f);
                }
                if (t < 40) {
                    v.setDeltaMovement(0, t < 12 ? 0.08 : 0, 0);
                    nv.sendParticles(OURO, v.getX(), v.getY() + 1, v.getZ(), 12, 0.4, 1.2, 0.4, 0.06);
                    if (fase >= 1) nv.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getY() + 1, v.getZ(), 6, 0.6, 1, 0.6, 0.15);
                    if (t % 10 == 0) raio(nv, v.position());
                } else if (t == 40) {
                    fase++;
                    v.fase(Math.min(2, fase));
                    nv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, v.getX(), v.getY() + 1, v.getZ(), 1, 0, 0, 0, 0);
                    nv.sendParticles(OURO, v.getX(), v.getY() + 1, v.getZ(), 150, 2, 2, 2, 0.3);
                    nv.playSound(null, v.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 1.2f);
                    for (ServerPlayer p : naArena(nv)) {
                        if (p.distanceTo(v) > 7) continue;
                        Vec3 k = p.position().subtract(v.position()).multiply(1, 0, 1);
                        if (k.lengthSqr() < 0.01) k = new Vec3(1, 0, 0);
                        k = k.normalize().scale(1.0);
                        p.push(k.x, 0.35, k.z);
                        p.hurtMarked = true;
                        ferir(v, p, 4, true);
                    }
                    titulo(nv, fase == 1 ? "O PRÍNCIPE SE TRANSFORMOU" : "PODER MÁXIMO", fase == 1 ? "Super Saiyajin" : "Relâmpagos correm pelo corpo de Vegeta", ChatFormatting.GOLD);
                    AttributeInstance vel = v.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (vel != null) vel.setBaseValue(0.33 + fase * 0.04);
                    AttributeInstance dano = v.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (dano != null) dano.setBaseValue(DANO_BASE * multDano());
                    v.setInvulnerable(false);
                    v.setNoGravity(false);
                } else if (t > 50) terminar(v, 30);
            }
            default -> terminar(v, 40);
        }
        t++;
    }

    // ------------------------------------------------------------------ projéteis e feixes

    private static void disparar(Vec3 de, Vec3 para, double velocidade, double raio, double explosao, float dano, boolean grande) {
        Orbe o = new Orbe();
        o.pos = de;
        o.vel = para.subtract(de).normalize().scale(velocidade);
        o.raio = raio; o.explosao = explosao; o.dano = dano; o.grande = grande;
        ORBES.add(o);
    }

    private static void orbes(ServerLevel nv) {
        VegetaEntidade v = entidade(nv);
        for (Iterator<Orbe> it = ORBES.iterator(); it.hasNext(); ) {
            Orbe o = it.next();
            o.pos = o.pos.add(o.vel);
            nv.sendParticles(o.grande ? OURO : KI, o.pos.x, o.pos.y, o.pos.z, o.grande ? 10 : 4, o.raio * 0.3, o.raio * 0.3, o.raio * 0.3, 0.01);
            nv.sendParticles(ParticleTypes.END_ROD, o.pos.x, o.pos.y, o.pos.z, 1, 0.05, 0.05, 0.05, 0);
            boolean bate = --o.vida <= 0 || !nv.getBlockState(BlockPos.containing(o.pos)).getCollisionShape(nv, BlockPos.containing(o.pos)).isEmpty();
            if (!bate)
                for (ServerPlayer p : nv.players())
                    if (!p.isSpectator() && !p.isCreative() && p.getBoundingBox().getCenter().distanceTo(o.pos) < o.raio + 0.5) { bate = true; break; }
            if (!bate) continue;
            it.remove();
            nv.sendParticles(o.grande ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, o.pos.x, o.pos.y, o.pos.z, o.grande ? 1 : 3, 0.5, 0.5, 0.5, 0);
            nv.playSound(null, BlockPos.containing(o.pos), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, o.grande ? 1.5f : 0.7f, o.grande ? 0.8f : 1.4f);
            if (v == null) continue;
            for (ServerPlayer p : nv.players())
                if (p.getBoundingBox().getCenter().distanceTo(o.pos) <= o.explosao) {
                    ferir(v, p, o.dano, true);
                    Vec3 k = p.position().subtract(o.pos).normalize().scale(o.grande ? 0.9 : 0.4);
                    p.push(k.x, 0.25, k.z);
                    p.hurtMarked = true;
                }
        }
    }

    /** Linha de aviso (onde o feixe vai passar). */
    private static void linha(ServerLevel nv, Vec3 de, Vec3 d, double comp, ParticleOptions part, int passo) {
        for (double s = 1; s <= comp; s += passo) {
            Vec3 pt = de.add(d.scale(s));
            nv.sendParticles(part, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
    }

    /** Feixe: desenha e fere quem estiver a até "largura" da linha (uma vez por feixe). */
    private static void feixe(ServerLevel nv, VegetaEntidade v, Vec3 de, Vec3 d, double comp, double largura, double dano, ParticleOptions cor) {
        for (double s = 0.5; s <= comp; s += 0.7) {
            Vec3 pt = de.add(d.scale(s));
            nv.sendParticles(cor, pt.x, pt.y, pt.z, 2, largura * 0.3, largura * 0.3, largura * 0.3, 0.01);
            if (((int) (s * 10)) % 21 == 0) nv.sendParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
        for (ServerPlayer p : nv.players()) {
            if (p.isSpectator() || p.isCreative() || ATINGIDOS.contains(p.getUUID())) continue;
            Vec3 c = p.getBoundingBox().getCenter();
            double s = c.subtract(de).dot(d);
            if (s < 0 || s > comp) continue;
            if (c.distanceTo(de.add(d.scale(s))) > largura + 0.4) continue;
            ATINGIDOS.add(p.getUUID());
            ferir(v, p, dano, true);
            p.push(d.x * 0.8, 0.3, d.z * 0.8);
            p.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ eventos do corpo

    @SubscribeEvent
    public void morreu(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof VegetaEntidade v) || !(v.level() instanceof ServerLevel nv) || !v.getUUID().equals(ent)) return;
        vitoria(nv, v);
        encerrar(nv, true);
    }

    @SubscribeEvent
    public void semDrops(LivingDropsEvent ev) { if (ev.getEntity() instanceof VegetaEntidade) ev.setCanceled(true); }

    /** Vegeta que sobrou de antes de um reinício (sem luta em andamento) some ao carregar. */
    @SubscribeEvent
    public void aoCarregar(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || !(e instanceof VegetaEntidade) || adicionando) return;
        if (!e.getUUID().equals(ent)) ev.setCanceled(true);
    }

    /** Jogador que cai no meio da luta: o alvo muda para outro. */
    public static boolean emLuta() { return ent != null; }

    static boolean ehVegeta(Entity e) { return e instanceof VegetaEntidade; }

    @SuppressWarnings("unused")
    private static boolean jogador(Entity e) { return e instanceof Player; }
}
