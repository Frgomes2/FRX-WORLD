package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.Zonas;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chefões com mecânicas, sorteados pelo rank (0 = F ... 6 = S). Usados nas dungeons de portal, nos andares de chefe da Torre e nas emboscadas do Nether.
 * Os do Códex de Criaturas (painel) têm preferência; a lista DEFS é a reserva de fábrica.
 * Cada um é um monstro reforçado com nome, barra de vida e um repertório de golpes anunciados na tela.
 */
public class Chefes {
    public static final String TAG = "frx_chefao", TAG_LACAIO = "frx_lacaio_chefao";

    private enum Golpe { INVOCAR, ONDA, RAIO, FOGO, GELO, VENENO, SALTO, CURA, FLECHAS, FURIA }

    private record Def(String nome, String tipo, int rankMin, int rankMax, String lacaio, Golpe... golpes) { }

    private static final List<Def> DEFS = List.of(
            new Def("Rei Goblin", "minecraft:zombie", 0, 1, "minecraft:zombie", Golpe.SALTO, Golpe.INVOCAR),
            new Def("Aranha Rainha", "minecraft:spider", 0, 1, "minecraft:cave_spider", Golpe.VENENO, Golpe.INVOCAR),
            new Def("Capitão Esqueleto", "minecraft:skeleton", 0, 2, "minecraft:skeleton", Golpe.FLECHAS, Golpe.INVOCAR),
            new Def("Xamã do Pântano", "minecraft:witch", 1, 3, "minecraft:zombie", Golpe.VENENO, Golpe.CURA, Golpe.RAIO),
            new Def("Carrasco de Ferro", "minecraft:vindicator", 1, 3, "minecraft:vindicator", Golpe.SALTO, Golpe.ONDA, Golpe.FURIA),
            new Def("Arqueiro Gélido", "minecraft:stray", 1, 3, "minecraft:stray", Golpe.GELO, Golpe.FLECHAS, Golpe.ONDA),
            new Def("Besta de Guerra", "minecraft:ravager", 2, 4, "minecraft:pillager", Golpe.ONDA, Golpe.SALTO, Golpe.FURIA),
            new Def("Cavaleiro do Abismo", "minecraft:wither_skeleton", 2, 4, "minecraft:wither_skeleton", Golpe.ONDA, Golpe.INVOCAR, Golpe.FOGO, Golpe.FURIA),
            new Def("Senhor das Chamas", "minecraft:blaze", 3, 5, "minecraft:blaze", Golpe.FOGO, Golpe.RAIO, Golpe.INVOCAR),
            new Def("Mestre das Ilusões", "minecraft:illusioner", 3, 5, "minecraft:vindicator", Golpe.RAIO, Golpe.GELO, Golpe.CURA),
            new Def("Bruxa Suprema", "minecraft:witch", 3, 6, "minecraft:witch", Golpe.VENENO, Golpe.RAIO, Golpe.GELO, Golpe.CURA, Golpe.INVOCAR),
            new Def("Devorador do Abismo", "minecraft:zoglin", 4, 6, "minecraft:zoglin", Golpe.SALTO, Golpe.ONDA, Golpe.VENENO, Golpe.FURIA),
            new Def("Colosso de Guerra", "minecraft:ravager", 4, 6, "minecraft:vindicator", Golpe.ONDA, Golpe.SALTO, Golpe.FLECHAS, Golpe.INVOCAR, Golpe.FURIA),
            new Def("Arauto do Vazio", "minecraft:wither_skeleton", 5, 6, "minecraft:wither_skeleton", Golpe.ONDA, Golpe.RAIO, Golpe.FOGO, Golpe.GELO, Golpe.INVOCAR, Golpe.FURIA));

    private static final class Ativo {
        final Mob mob; final Def def; final int nivel; final ServerBossEvent barra;
        int segundos; boolean furia;
        Ativo(Mob mob, Def def, int nivel) {
            this.mob = mob; this.def = def; this.nivel = nivel;
            barra = new ServerBossEvent(Component.literal(def.nome()).withStyle(ChatFormatting.DARK_PURPLE), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    private record Agendado(int tick, Runnable acao) { }

    private static final Map<UUID, Ativo> ativos = new ConcurrentHashMap<>();
    private static final List<Agendado> agenda = new CopyOnWriteArrayList<>();

    /** Cria um chefão sorteado para o rank (só os de fábrica). vidaExtra soma ao multiplicador de vida (grupo maior, andar alto). */
    public static Mob criar(ServerLevel n, Vec3 pos, int rank, int nivel, double vidaExtra) { return criar(n, pos, rank, nivel, vidaExtra, ""); }

    /**
     * Cria um chefão para o lugar ("portal", "torre", "nether") e o rank. Primeiro sorteia entre os chefes do Códex de Criaturas (painel);
     * se não houver nenhum para esse lugar e rank, usa os de fábrica.
     */
    public static Mob criar(ServerLevel n, Vec3 pos, int rank, int nivel, double vidaExtra, String local) {
        int r = Math.max(0, Math.min(6, rank));
        Criaturas.Def cd = local.isEmpty() ? null : Criaturas.sortearChefe(local, r);
        if (cd != null) {
            Mob m = montar(n, pos, r, nivel, vidaExtra, doCodex(cd), cd);
            if (m != null) return m;
        }
        List<Def> opcoes = DEFS.stream().filter(d -> r >= d.rankMin() && r <= d.rankMax()).toList();
        if (opcoes.isEmpty()) opcoes = DEFS;
        return montar(n, pos, r, nivel, vidaExtra, opcoes.get(ThreadLocalRandom.current().nextInt(opcoes.size())), null);
    }

    /** Cria um chefe específico do Códex (painel: "Invocar perto de mim"). */
    public static Mob criarDe(ServerLevel n, Vec3 pos, Criaturas.Def cd, int rank, int nivel, double vidaExtra) {
        return montar(n, pos, Math.max(0, Math.min(6, rank)), nivel, vidaExtra, doCodex(cd), cd);
    }

    private static Def doCodex(Criaturas.Def cd) {
        List<Golpe> golpes = new ArrayList<>();
        for (String g : cd.golpes()) try { golpes.add(Golpe.valueOf(g)); } catch (IllegalArgumentException e) { /* golpe que este servidor não conhece */ }
        if (golpes.stream().allMatch(g -> g == Golpe.FURIA)) golpes.add(0, Golpe.ONDA);
        return new Def(cd.nome(), cd.entidade(), cd.rankMin(), cd.rankMax(), cd.lacaio().isEmpty() ? cd.entidade() : cd.lacaio(), golpes.toArray(new Golpe[0]));
    }

    private static Mob montar(ServerLevel n, Vec3 pos, int r, int nivel, double vidaExtra, Def def, Criaturas.Def cd) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(def.tipo()));
        Entity e = tipo == null ? null : tipo.create(n);
        if (!(e instanceof Mob m)) return null;
        m.moveTo(pos.x, pos.y, pos.z, 0, 0);
        m.finalizeSpawn(n, n.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        Zonas.aplicarNivel(m, Math.max(1, nivel));
        var vida = m.getAttribute(Attributes.MAX_HEALTH);
        if (vida != null) vida.addPermanentModifier(new AttributeModifier(UUID.fromString("5b7d9e21-3c4f-4a60-8e12-9d0c1b2a3f01"), "FRX chefao", 3.0 + r * 0.7 + Math.max(0, vidaExtra), AttributeModifier.Operation.MULTIPLY_TOTAL));
        var kb = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(Math.max(kb.getBaseValue(), 0.7));
        var alcance = m.getAttribute(Attributes.FOLLOW_RANGE);
        if (alcance != null) alcance.setBaseValue(Math.max(alcance.getBaseValue(), 40));
        if (cd != null) Criaturas.aplicar(m, cd);
        m.setHealth(m.getMaxHealth());
        m.setPersistenceRequired();
        m.setCustomName(Component.literal("☠ " + def.nome()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        m.setCustomNameVisible(true);
        if (cd == null || cd.brilho()) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        m.addTag(TAG);
        ativos.put(m.getUUID(), new Ativo(m, def, Math.max(1, nivel)));
        n.addFreshEntity(m);
        n.playSound(null, m.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 1.1f);
        return m;
    }

    public static boolean ehChefao(Entity e) { return e.getTags().contains(TAG); }

    // ------------------------------------------------------------------ golpes

    private static List<ServerPlayer> alvos(Ativo a, double raio) {
        return ((ServerLevel) a.mob.level()).players().stream().filter(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceTo(a.mob) <= raio).toList();
    }

    private static float dano(Ativo a, double fator) { return (float) ((4 + a.nivel * 0.22) * fator); }

    private static void anunciar(Ativo a, String texto) {
        for (ServerPlayer p : alvos(a, 40)) p.displayClientMessage(Component.literal(a.def.nome() + " " + texto).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void depois(MinecraftServer s, int ticks, Runnable acao) { agenda.add(new Agendado(s.getTickCount() + ticks, acao)); }

    private static void anel(ServerLevel n, Vec3 c, double raio, ParticleOptions p) {
        for (int i = 0; i < 40; i++) {
            double ang = Math.PI * 2 * i / 40;
            n.sendParticles(p, c.x + Math.cos(ang) * raio, c.y + 0.3, c.z + Math.sin(ang) * raio, 1, 0, 0.1, 0, 0);
        }
    }

    private static void onda(Ativo a, double raio, double fator) {
        ServerLevel n = (ServerLevel) a.mob.level();
        Vec3 c = a.mob.position();
        anel(n, c, raio, ParticleTypes.EXPLOSION);
        anel(n, c, raio / 2, ParticleTypes.CRIT);
        n.playSound(null, a.mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.6f);
        for (ServerPlayer p : alvos(a, raio)) {
            p.hurt(a.mob.damageSources().mobAttack(a.mob), dano(a, fator));
            Vec3 fora = p.position().subtract(c).multiply(1, 0, 1);
            fora = fora.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : fora.normalize();
            p.setDeltaMovement(fora.x * 1.1, 0.5, fora.z * 1.1);
            p.hurtMarked = true;
        }
    }

    private static void usar(MinecraftServer s, Ativo a, Golpe g) {
        ServerLevel n = (ServerLevel) a.mob.level();
        List<ServerPlayer> perto = alvos(a, 32);
        if (perto.isEmpty()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ServerPlayer alvo = perto.get(rnd.nextInt(perto.size()));
        switch (g) {
            case INVOCAR -> {
                long vivos = n.getEntities(a.mob, a.mob.getBoundingBox().inflate(24), e -> e.getTags().contains(TAG_LACAIO) && e.isAlive()).size();
                if (vivos >= 5) { usar(s, a, Golpe.ONDA); return; }
                anunciar(a, "chama reforços!");
                EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(a.def.lacaio()));
                for (int i = 0; i < 2 + (a.furia ? 1 : 0); i++) {
                    Entity e = tipo == null ? null : tipo.create(n);
                    if (!(e instanceof Mob l)) continue;
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    l.moveTo(a.mob.getX() + Math.cos(ang) * 2.5, a.mob.getY(), a.mob.getZ() + Math.sin(ang) * 2.5, 0, 0);
                    l.finalizeSpawn(n, n.getCurrentDifficultyAt(l.blockPosition()), MobSpawnType.EVENT, null, null);
                    Zonas.aplicarNivel(l, Math.max(1, a.nivel - 3));
                    l.setPersistenceRequired();
                    l.addTag(TAG_LACAIO);
                    n.addFreshEntity(l);
                    l.setTarget(alvo);
                    n.sendParticles(ParticleTypes.POOF, l.getX(), l.getY() + 1, l.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                }
                n.playSound(null, a.mob.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5f, 0.8f);
            }
            case ONDA -> {
                anunciar(a, "vai soltar uma onda de choque — afaste-se!");
                anel(n, a.mob.position(), 6, ParticleTypes.SMOKE);
                depois(s, 25, () -> { if (a.mob.isAlive()) onda(a, 6, 1.0); });
            }
            case RAIO -> {
                anunciar(a, "invoca um raio em " + alvo.getGameProfile().getName() + " — saia do lugar!");
                Vec3 ponto = alvo.position();
                anel(n, ponto, 2.5, ParticleTypes.ELECTRIC_SPARK);
                depois(s, 30, () -> {
                    LightningBolt raio = EntityType.LIGHTNING_BOLT.create(n);
                    if (raio != null) { raio.moveTo(ponto); raio.setVisualOnly(true); n.addFreshEntity(raio); }
                    if (!a.mob.isAlive()) return;
                    for (ServerPlayer p : n.players()) if (p.isAlive() && p.position().distanceTo(ponto) <= 2.8) p.hurt(a.mob.damageSources().mobAttack(a.mob), dano(a, 1.6));
                });
            }
            case FOGO -> {
                anunciar(a, "incendeia o chão!");
                for (ServerPlayer p : perto) {
                    Vec3 ponto = p.position();
                    anel(n, ponto, 2.5, ParticleTypes.FLAME);
                    for (int k = 1; k <= 5; k++) depois(s, 20 + k * 20, () -> {
                        n.sendParticles(ParticleTypes.FLAME, ponto.x, ponto.y + 0.3, ponto.z, 30, 1.2, 0.2, 1.2, 0.02);
                        if (!a.mob.isAlive()) return;
                        for (ServerPlayer q : n.players()) if (q.isAlive() && q.position().distanceTo(ponto) <= 2.6) { q.hurt(a.mob.damageSources().mobAttack(a.mob), dano(a, 0.45)); q.setSecondsOnFire(3); }
                    });
                }
                n.playSound(null, a.mob.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.7f);
            }
            case GELO -> {
                anunciar(a, "congela tudo em volta!");
                anel(n, a.mob.position(), 8, ParticleTypes.SNOWFLAKE);
                n.playSound(null, a.mob.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
                for (ServerPlayer p : alvos(a, 8)) {
                    p.hurt(a.mob.damageSources().mobAttack(a.mob), dano(a, 0.5));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
                    p.setTicksFrozen(220);
                }
            }
            case VENENO -> {
                anunciar(a, "espalha uma nuvem de veneno!");
                AreaEffectCloud nuvem = new AreaEffectCloud(n, alvo.getX(), alvo.getY(), alvo.getZ());
                nuvem.setOwner(a.mob);
                nuvem.setRadius(3.2f);
                nuvem.setDuration(160);
                nuvem.setWaitTime(15);
                nuvem.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                nuvem.addTag(TAG_LACAIO);
                n.addFreshEntity(nuvem);
            }
            case SALTO -> {
                anunciar(a, "salta sobre " + alvo.getGameProfile().getName() + "!");
                Vec3 d = alvo.position().subtract(a.mob.position());
                double dist = Math.max(0.1, d.multiply(1, 0, 1).length());
                Vec3 v = d.multiply(1, 0, 1).normalize().scale(Math.min(1.8, 0.4 + dist * 0.16));
                a.mob.setDeltaMovement(v.x, 0.7, v.z);
                a.mob.hurtMarked = true;
                depois(s, 18, () -> { if (a.mob.isAlive()) onda(a, 4, 0.8); });
            }
            case CURA -> {
                if (a.mob.getHealth() > a.mob.getMaxHealth() * 0.9f) { usar(s, a, a.def.golpes()[0]); return; }
                anunciar(a, "se cura!");
                a.mob.heal(a.mob.getMaxHealth() * 0.06f);
                n.sendParticles(ParticleTypes.HEART, a.mob.getX(), a.mob.getY() + a.mob.getBbHeight(), a.mob.getZ(), 10, 0.5, 0.4, 0.5, 0.02);
            }
            case FLECHAS -> {
                anunciar(a, "ordena uma chuva de flechas!");
                for (ServerPlayer p : perto) for (int i = 0; i < 6; i++) {
                    Arrow f = new Arrow(n, p.getX() + rnd.nextDouble(-2.5, 2.5), p.getY() + 9 + rnd.nextDouble(2), p.getZ() + rnd.nextDouble(-2.5, 2.5));
                    f.setOwner(a.mob);
                    f.setBaseDamage(Math.max(2, dano(a, 0.5)));
                    f.pickup = AbstractArrow.Pickup.DISALLOWED;
                    f.setDeltaMovement(0, -1.2, 0);
                    f.addTag(TAG_LACAIO);
                    n.addFreshEntity(f);
                }
                n.playSound(null, a.mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 2f, 0.6f);
            }
            case FURIA -> usar(s, a, Golpe.ONDA);
        }
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        int agora = s.getTickCount();
        for (Agendado g : agenda) {
            if (agora < g.tick()) continue;
            agenda.remove(g);
            try { g.acao().run(); } catch (Exception e) { FrxWorld.LOG.warn("Chefão: golpe agendado falhou: {}", e.toString()); }
        }
        if (agora % 20 != 15 || ativos.isEmpty()) return;
        for (Ativo a : new ArrayList<>(ativos.values())) {
            try {
                if (!a.mob.isAlive() || a.mob.isRemoved()) {
                    a.barra.removeAllPlayers();
                    ativos.remove(a.mob.getUUID());
                    if (a.mob.level() instanceof ServerLevel n)
                        for (Entity e : n.getEntities(a.mob, a.mob.getBoundingBox().inflate(40), e -> e.getTags().contains(TAG_LACAIO))) e.discard();
                    continue;
                }
                List<ServerPlayer> perto = alvos(a, 40);
                a.barra.setProgress(Math.max(0, Math.min(1, a.mob.getHealth() / a.mob.getMaxHealth())));
                for (ServerPlayer p : new ArrayList<>(a.barra.getPlayers())) if (!perto.contains(p)) a.barra.removePlayer(p);
                for (ServerPlayer p : perto) a.barra.addPlayer(p);
                if (perto.isEmpty()) continue;
                if (a.mob.getTarget() == null || !a.mob.getTarget().isAlive()) a.mob.setTarget(perto.get(ThreadLocalRandom.current().nextInt(perto.size())));
                boolean temFuria = false;
                for (Golpe g : a.def.golpes()) if (g == Golpe.FURIA) temFuria = true;
                if (temFuria && !a.furia && a.mob.getHealth() < a.mob.getMaxHealth() * 0.35f) {
                    a.furia = true;
                    a.mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false));
                    a.mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 0, false, false));
                    a.barra.setColor(BossEvent.BossBarColor.RED);
                    anunciar(a, "entrou em FÚRIA!");
                    a.mob.level().playSound(null, a.mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.6f);
                }
                a.segundos++;
                if (a.segundos % (a.furia ? 5 : 7) != 0) continue;
                List<Golpe> lista = new ArrayList<>();
                for (Golpe g : a.def.golpes()) if (g != Golpe.FURIA) lista.add(g);
                if (!lista.isEmpty()) usar(s, a, lista.get(ThreadLocalRandom.current().nextInt(lista.size())));
            } catch (Exception e) {
                FrxWorld.LOG.warn("Chefão {} falhou: {}", a.def.nome(), e.toString());
            }
        }
    }

    /** Chefão ou lacaio que sobrou gravado no mundo não volta. */
    @SubscribeEvent
    public void aoEntrarNoMundo(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || !ev.loadedFromDisk()) return;
        if (e.getTags().contains(TAG) || e.getTags().contains(TAG_LACAIO)) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        for (Ativo a : ativos.values()) { a.barra.removeAllPlayers(); a.mob.discard(); }
        ativos.clear();
        agenda.clear();
    }
}
