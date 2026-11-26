package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.musica.Discos;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evento "Titã à solta": um gigante de 35 blocos de altura marcha para as muralhas de Lubelius, o reino principal.
 *  - No máximo 1 vez por dia real (vira à meia-noite de São Paulo) e só com 3 ou mais jogadores online.
 *    Com 3+ online, a cada minuto há 4% de chance de começar (em média uns 25 minutos depois de juntar gente).
 *  - Ele nasce a 320 blocos das muralhas e anda devagar em linha reta, atravessando árvores e morros. Todos veem a barra com a distância.
 *  - Vida: 5.000. Só jogadores ferem; pernas, tronco e cabeça levam golpe e flecha (o corpo é dividido em partes). Golpe na NUCA (por trás e do alto) vale 3x.
 *  - A cada 20% de vida perdida ele cambaleia e para 6 segundos. De tempos em tempos dá um pisão (com aviso) em quem está aos pés.
 *  - Derrotado: quem causou dano ganha 30 de ouro, 10% de um nível de XP e +150 de reputação em Lubelius; quem mais bateu ganha o dobro do ouro e da reputação.
 *  - Se chegar às muralhas: some numa nuvem de vapor e os NPCs de Lubelius (menos a Deusa) ficam 30 minutos sem atender.
 *  - No mapa (JourneyMap): quadrado vermelho "TITÃ" onde ele está (atualiza a cada 5 s) e pontilhado laranja do caminho até Lubelius.
 *  - Música: o disco do painel de código "luta_tita" (se existir). Skin: painel → NPCs e skins → Titã.
 * Estado em <mundo>/frx_tita.json. Admin: /frx tita iniciar | parar.
 */
public class Tita {
    public static final DeferredRegister<EntityType<?>> TIPOS = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FrxWorld.MOD_ID);
    public static final RegistryObject<EntityType<TitaEntidade>> TITA = TIPOS.register("tita",
            () -> EntityType.Builder.<TitaEntidade>of(TitaEntidade::new, MobCategory.MONSTER).sized(9f, 13.5f).clientTrackingRange(16).updateInterval(2).fireImmune().build("tita"));

    public static void registrar(IEventBus modBus) {
        TIPOS.register(modBus);
        modBus.addListener(Tita::atributos);
    }

    private static void atributos(EntityAttributeCreationEvent ev) { ev.put(TITA.get(), TitaEntidade.atributos().build()); }

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String REINO = "lubelius", MUSICA = "luta_tita";
    private static final int MIN_JOGADORES = 3, CHANCE_MINUTO = 4, DISTANCIA = 320, CAMBALEIO = 120, PISAO_CADA = 180, PISAO_AVISO = 50;
    private static final double PASSO = 0.04, VIDA = 5000;
    private static final long LOJAS_FECHADAS = 30 * 60_000L;

    // estado guardado
    private static boolean carregado;
    private static long dia, lojasAte;

    // estado da marcha
    private static boolean ativo;
    private static UUID ent;
    private static double x, y, z;
    private static double inicioX, inicioZ;
    private static int pontosRota;
    private static float vida, vidaMax, limiar;
    private static int cambaleio, pisao, relogio;
    private static long musicaRecomeca;
    private static final Map<UUID, Float> DANO = new HashMap<>();
    private static final Map<UUID, String> NOMES = new HashMap<>();
    private static final ServerBossEvent BARRA = new ServerBossEvent(Component.literal("Titã"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    public static boolean ativo() { return ativo; }

    // ------------------------------------------------------------------ arquivo

    private static Path arquivo(MinecraftServer srv) { return srv.getWorldPath(LevelResource.ROOT).resolve("frx_tita.json"); }

    private static void carregar(MinecraftServer srv) {
        carregado = true;
        try {
            Path a = arquivo(srv);
            if (!Files.exists(a)) return;
            JsonObject o = JsonParser.parseString(Files.readString(a, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("dia")) dia = o.get("dia").getAsLong();
            if (o.has("lojasAte")) lojasAte = o.get("lojasAte").getAsLong();
        } catch (Exception e) { FrxWorld.LOG.warn("Titã: não li o estado: {}", e.toString()); }
    }

    private static void salvar(MinecraftServer srv) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("dia", dia);
            o.addProperty("lojasAte", lojasAte);
            Files.writeString(arquivo(srv), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) { FrxWorld.LOG.warn("Titã: não gravei o estado: {}", e.toString()); }
    }

    private static Reino reino() {
        for (Reino r : Reinos.todos()) if (r.id().equals(REINO)) return r;
        return null;
    }

    /** NPCs de Lubelius parados depois que o Titã chegou às muralhas. Avisa o jogador e devolve true se estiver fechado. */
    public static boolean lojasFechadas(ServerPlayer p) {
        if (!carregado) carregar(p.server);
        long falta = lojasAte - System.currentTimeMillis();
        if (falta <= 0) return false;
        p.displayClientMessage(Component.literal("🏚 Estamos consertando o estrago do Titã. Volte em " + (falta / 60_000 + 1) + " min.").withStyle(ChatFormatting.RED), true);
        return true;
    }

    // ------------------------------------------------------------------ começo e fim

    public static boolean iniciar(MinecraftServer srv) {
        Reino r = reino();
        if (ativo || r == null) return false;
        ServerLevel nv = srv.overworld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double a0 = rnd.nextDouble(Math.PI * 2), sx = 0, sz = 0;
        boolean achou = false;
        for (int k = 0; k < 12 && !achou; k++) {   // procura um lado em terra firme e longe do Templo Karin
            double a = a0 + k * Math.PI / 6;
            double d = 40;
            while (r.distancia((int) (r.centroX() + Math.cos(a) * d), (int) (r.centroZ() + Math.sin(a) * d)) < DISTANCIA) d += 8;
            sx = r.centroX() + Math.cos(a) * d;
            sz = r.centroZ() + Math.sin(a) * d;
            if (Math.abs(sx - Karin.CX) < 200 && Math.abs(sz - Karin.CZ) < 200) continue;
            BlockPos topo = new BlockPos((int) sx, nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) sx, (int) sz) - 1, (int) sz);
            achou = nv.getFluidState(topo).isEmpty() || k == 11;
        }
        int online = srv.getPlayerList().getPlayerCount();
        ativo = true;
        ent = null;
        x = sx; z = sz;
        inicioX = sx; inicioZ = sz; pontosRota = 0;
        y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
        vidaMax = (float) VIDA;
        vida = vidaMax;
        limiar = 0.8f;
        cambaleio = 0; pisao = PISAO_CADA; relogio = 0; musicaRecomeca = 0;
        DANO.clear(); NOMES.clear();
        dia = LocalDate.now(FUSO).toEpochDay();
        salvar(srv);
        BARRA.setProgress(1f);
        BARRA.setDarkenScreen(false);
        Component t1 = Component.literal("⚠ UM TITÃ SE APROXIMA").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component t2 = Component.literal("Ele marcha para Lubelius. Derrubem antes das muralhas!").withStyle(ChatFormatting.GOLD);
        for (ServerPlayer o : srv.getPlayerList().getPlayers()) {
            o.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 100, 20));
            o.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(t2));
            o.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(t1));
            o.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1f, 0.5f);
        }
        srv.getPlayerList().broadcastSystemMessage(Component.literal("⚠ Um Titã apareceu perto de " + (int) x + ", " + (int) z + " e marcha para as muralhas de Lubelius! "
                + "Ele tem " + (int) vidaMax + " de vida. Golpe na NUCA (por trás e do alto) vale 3x. Se ele chegar, Lubelius para por 30 minutos.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        FrxWorld.LOG.info("Titã começou em {} {} com {} de vida.", (int) x, (int) z, (int) vidaMax);
        return true;
    }

    private static void encerrar(MinecraftServer srv, boolean vapor) {
        ServerLevel nv = srv.overworld();
        if (ent != null && nv.getEntity(ent) instanceof TitaEntidade t) {
            if (vapor) nv.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, t.getX(), t.getY() + 18, t.getZ(), 600, 6, 14, 6, 0.05);
            if (t.isAlive()) t.discard();
        }
        pararMusica(srv);
        for (ServerPlayer o : srv.getPlayerList().getPlayers()) {
            br.com.frxcode.frxworld.protecao.MapaJm.remover(o, "tita");
            for (int k = 0; k < pontosRota; k++) br.com.frxcode.frxworld.protecao.MapaJm.remover(o, "tita_rota" + k);
        }
        ativo = false;
        ent = null;
        BARRA.removeAllPlayers();
    }

    private static void pararMusica(MinecraftServer srv) {
        if (Discos.def(MUSICA) == null) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) p.connection.send(new ClientboundStopSoundPacket(Discos.som(MUSICA), SoundSource.RECORDS));
    }

    private static void vencer(MinecraftServer srv) {
        List<Map.Entry<UUID, Float>> ordem = new ArrayList<>(DANO.entrySet());
        ordem.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(3, ordem.size()); i++)
            top.append(i == 0 ? "" : ", ").append(i + 1).append("º ").append(NOMES.getOrDefault(ordem.get(i).getKey(), "?")).append(" (").append(Math.round(ordem.get(i).getValue())).append(")");
        srv.getPlayerList().broadcastSystemMessage(Component.literal("✦ O Titã caiu! Lubelius está a salvo. Quem mais feriu: " + top + ".").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (int i = 0; i < ordem.size(); i++) {
            ServerPlayer p = srv.getPlayerList().getPlayer(ordem.get(i).getKey());
            if (p == null) continue;
            long ouro = i == 0 ? 6000 : 3000;
            Moedas.dar(p, ouro);
            long xp = Math.max(1, Progressao.xpParaProximo(Progressao.dados(p).nivel()) / 10);
            Progressao.ganharXp(p, xp);
            int rep = i == 0 ? 300 : 150;
            br.com.frxcode.frxworld.reinos.Reputacao.ganhar(p, REINO, rep, false, false);
            p.displayClientMessage(Component.literal("✦ Recompensa do Titã: " + Moedas.texto(ouro) + ", " + xp + " de XP e +" + rep + " de reputação em Lubelius." + (i == 0 ? " Você foi quem mais feriu!" : "")).withStyle(ChatFormatting.GREEN), false);
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
        FrxWorld.LOG.info("Titã derrotado: {}", top);
        encerrar(srv, true);
    }

    private static void perder(MinecraftServer srv) {
        lojasAte = System.currentTimeMillis() + LOJAS_FECHADAS;
        salvar(srv);
        srv.getPlayerList().broadcastSystemMessage(Component.literal("🏚 O Titã chegou às muralhas de Lubelius e sumiu numa nuvem de vapor! Os NPCs do reino ficam 30 minutos sem atender.")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        for (ServerPlayer o : srv.getPlayerList().getPlayers()) o.playNotifySound(SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1f, 0.5f);
        FrxWorld.LOG.info("Titã chegou às muralhas.");
        encerrar(srv, true);
    }

    // ------------------------------------------------------------------ marcha

    private static TitaEntidade corpo(ServerLevel nv) {
        TitaEntidade t = TITA.get().create(nv);
        if (t == null) return null;
        AttributeInstance a = t.getAttribute(Attributes.MAX_HEALTH);
        if (a != null) a.setBaseValue(vidaMax);
        br.com.frxcode.frxworld.progressao.Zonas.marcarNivel(t, 1);
        t.moveTo(x, y, z, 0, 0);
        t.setHealth(Math.max(1f, vida));
        t.setCustomName(Component.literal("Titã").withStyle(ChatFormatting.DARK_RED));
        ent = t.getUUID();
        nv.addFreshEntity(t);
        return t;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = ev.getServer();
        if (!ativo) {
            if (++relogio % 1200 != 0) return;
            if (!carregado) carregar(srv);
            if (dia == LocalDate.now(FUSO).toEpochDay() || srv.getPlayerList().getPlayerCount() < MIN_JOGADORES) return;
            if (ThreadLocalRandom.current().nextInt(100) < CHANCE_MINUTO) iniciar(srv);
            return;
        }
        relogio++;
        Reino r = reino();
        if (r == null) { encerrar(srv, false); return; }
        ServerLevel nv = srv.overworld();
        BlockPos aqui = BlockPos.containing(x, y, z);
        boolean visivel = nv.isPositionEntityTicking(aqui);
        TitaEntidade t = ent != null && nv.getEntity(ent) instanceof TitaEntidade e ? e : null;
        if (t != null && !t.isAlive()) return;   // morreu neste tick: o evento de morte fecha
        if (visivel && t == null) t = corpo(nv);
        else if (!visivel && t != null) { vida = t.getHealth(); t.discard(); t = null; ent = null; }
        if (t != null) vida = t.getHealth();

        // anda (ou cambaleia)
        double dx = r.centroX() + 0.5 - x, dz = r.centroZ() + 0.5 - z, d = Math.sqrt(dx * dx + dz * dz);
        float giro = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        if (cambaleio > 0) {
            cambaleio--;
            if (t != null && cambaleio % 5 == 0) nv.sendParticles(ParticleTypes.CLOUD, x, y + 20, z, 30, 4.5, 10, 4.5, 0.03);
        } else if (d > 1) {
            x += dx / d * PASSO;
            z += dz / d * PASSO;
        }
        if (t != null) {
            double chao = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            y += Math.max(-0.25, Math.min(0.25, chao - y));
            t.setPos(x, y, z);
            t.setYRot(giro); t.yBodyRot = giro; t.yHeadRot = giro;
            if (cambaleio == 0 && relogio % 22 == 0) nv.playSound(null, x, y, z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 4f, 0.5f);
        }
        if (r.distancia((int) Math.floor(x), (int) Math.floor(z)) <= 1) { perder(srv); return; }

        // cambaleio a cada 20% de vida
        if (limiar > 0.05f && vida / vidaMax < limiar) {
            limiar -= 0.2f;
            cambaleio = CAMBALEIO;
            nv.playSound(null, x, y, z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 5f, 0.5f);
            for (ServerPlayer o : BARRA.getPlayers()) o.displayClientMessage(Component.literal("💨 O Titã cambaleia! Aproveitem!").withStyle(ChatFormatting.GOLD), true);
        }

        // pisão em quem está aos pés
        if (t != null && cambaleio == 0) {
            pisao--;
            if (pisao == PISAO_AVISO) {
                boolean alguem = false;
                for (ServerPlayer o : nv.players()) if (aosPes(o, 20)) { alguem = true; o.displayClientMessage(Component.literal("⚠ O Titã levanta o pé: SAIA DE PERTO!").withStyle(ChatFormatting.RED), true); }
                if (!alguem) pisao = PISAO_CADA / 2;
                else {
                    nv.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 4f, 0.6f);
                    for (int k = 0; k < 72; k++) { double a = Math.toRadians(k * 5); nv.sendParticles(ParticleTypes.FLAME, x + Math.cos(a) * 14, y + 0.3, z + Math.sin(a) * 14, 1, 0, 0, 0, 0); }
                }
            } else if (pisao <= 0) {
                pisao = PISAO_CADA;
                nv.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4f, 0.6f);
                nv.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, 40, 6, 0.3, 6, 0);
                for (ServerPlayer o : new ArrayList<>(nv.players())) {
                    if (!aosPes(o, 14) || o.isCreative() || o.isSpectator()) continue;
                    o.hurt(nv.damageSources().mobAttack(t), Math.max(8f, o.getMaxHealth() * 0.25f));
                    Vec3 fora = new Vec3(o.getX() - x, 0, o.getZ() - z);
                    if (fora.lengthSqr() < 0.01) fora = new Vec3(1, 0, 0);
                    fora = fora.normalize().scale(1.2);
                    o.push(fora.x, 0.7, fora.z);
                    o.hurtMarked = true;
                }
            }
        }

        if (relogio % 20 != 0) return;
        int falta = r.distancia((int) Math.floor(x), (int) Math.floor(z));
        BARRA.setName(Component.literal("Titã — a " + falta + " blocos das muralhas de Lubelius").withStyle(ChatFormatting.DARK_RED));
        BARRA.setProgress(Math.max(0f, Math.min(1f, vida / vidaMax)));
        for (ServerPlayer o : srv.getPlayerList().getPlayers()) BARRA.addPlayer(o);
        if (relogio % 100 == 20) mapa(srv, r, relogio % 600 == 20);
        Discos.Def disco = Discos.def(MUSICA);
        if (disco != null && t != null && System.currentTimeMillis() >= musicaRecomeca) {
            musicaRecomeca = System.currentTimeMillis() + Math.max(30, disco.duracao()) * 1000L;
            nv.playSound(null, x, y + 10, z, SoundEvent.createVariableRangeEvent(Discos.som(MUSICA)), SoundSource.RECORDS, 6f, 1f);
        }
    }

    /** Mapa do JourneyMap: quadrado vermelho onde o Titã está e pontilhado laranja do caminho até Lubelius. */
    private static void mapa(MinecraftServer srv, Reino r, boolean rota) {
        List<int[]> pontos = new ArrayList<>();
        if (rota) {
            double dx = r.centroX() + 0.5 - inicioX, dz = r.centroZ() + 0.5 - inicioZ, d = Math.sqrt(dx * dx + dz * dz);
            for (double k = 0; k < d && pontos.size() < 16; k += 32) {
                int px = (int) (inicioX + dx / d * k), pz = (int) (inicioZ + dz / d * k);
                if (r.distancia(px, pz) <= 1) break;
                pontos.add(new int[]{px, pz});
            }
            pontosRota = Math.max(pontosRota, pontos.size());
        }
        int tx = (int) Math.floor(x), tz = (int) Math.floor(z);
        for (ServerPlayer o : srv.getPlayerList().getPlayers()) {
            for (int k = 0; k < pontos.size(); k++)
                br.com.frxcode.frxworld.protecao.MapaJm.mostrar(o, "tita_rota" + k, pontos.get(k)[0] - 6, pontos.get(k)[1] - 6, pontos.get(k)[0] + 6, pontos.get(k)[1] + 6, 0xFF8800, 0.5f,
                        k == 0 ? "Onde o Titã surgiu" : "", "Caminho do Titã");
            br.com.frxcode.frxworld.protecao.MapaJm.mostrar(o, "tita", tx - 24, tz - 24, tx + 24, tz + 24, 0xFF0000, 0.6f, "TITÃ", "Titã marchando para Lubelius");
        }
    }

    private static boolean aosPes(ServerPlayer o, double raio) {
        double dx = o.getX() - x, dz = o.getZ() - z;
        return o.isAlive() && dx * dx + dz * dz <= raio * raio && o.getY() >= y - 4 && o.getY() <= y + 6;
    }

    // ------------------------------------------------------------------ dano, morte e sobras

    @SubscribeEvent
    public void ferido(LivingHurtEvent ev) {
        if (!ativo || !(ev.getEntity() instanceof TitaEntidade t) || !(ev.getSource().getEntity() instanceof ServerPlayer p)) return;
        float valor = ev.getAmount();
        Vec3 frente = Vec3.directionFromRotation(0, t.getYRot()), ate = new Vec3(p.getX() - t.getX(), 0, p.getZ() - t.getZ());
        boolean atras = ate.lengthSqr() < 0.01 || ate.normalize().dot(frente) < -0.2;
        if (atras && p.getY() >= t.getY() + 20) {
            valor *= 3;
            ev.setAmount(valor);
            p.displayClientMessage(Component.literal("⚔ GOLPE NA NUCA! Dano x3").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            p.serverLevel().sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 30, t.getZ(), 60, 2.4, 1.5, 2.4, 0.4);
            p.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 0.7f);
        }
        DANO.merge(p.getUUID(), Math.min(valor, t.getHealth()), Float::sum);
        NOMES.put(p.getUUID(), p.getGameProfile().getName());
    }

    @SubscribeEvent
    public void morreu(LivingDeathEvent ev) {
        if (!ativo || !(ev.getEntity() instanceof TitaEntidade t) || !t.getUUID().equals(ent) || t.getServer() == null) return;
        vencer(t.getServer());
    }

    /** Titã que sobrou de um evento antigo (reinício no meio da marcha) não entra no mundo. */
    @SubscribeEvent
    public void sobra(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !(ev.getEntity() instanceof TitaEntidade t)) return;
        if (!ativo || !t.getUUID().equals(ent)) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("tita")
                        .then(Commands.literal("iniciar").executes(ctx -> {
                            boolean ok = iniciar(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Titã a caminho de Lubelius." : "Já há um Titã marchando (ou Lubelius não existe)."), true);
                            return 1;
                        }))
                        .then(Commands.literal("parar").executes(ctx -> {
                            if (ativo) encerrar(ctx.getSource().getServer(), true);
                            ctx.getSource().sendSuccess(() -> Component.literal("Evento do Titã encerrado."), true);
                            return 1;
                        }))
                        .then(Commands.literal("abrirlojas").executes(ctx -> {
                            lojasAte = 0;
                            salvar(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("NPCs de Lubelius voltaram a atender."), true);
                            return 1;
                        }))));
    }
}
