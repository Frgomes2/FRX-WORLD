package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.grupos.Grupos;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dungeon de portal: área temporária, uma por grupo, criada quando alguém atravessa um portal.
 * São três câmaras de monstros e, na quarta, um chefão sorteado pelo rank do portal (Chefes).
 * Quem morre lá dentro é levado para o portal antes de cair, e a lápide com os itens fica do lado de fora.
 */
public class Fenda {
    private static final String TAG = "frx_fenda";
    private static final int X0 = 24000, Z0 = 24128, Y = 200, PASSO = 64, VAGAS = 6, RMAX = 15, HMAX = 13, CAMARAS = 3;
    private static final long TEMPO_MS = 18 * 60_000L;

    private enum Fase { PAUSA, LUTA }

    private static final class Inst {
        final int vaga, rank, nivel;
        final BlockPos saida;
        final Set<UUID> membros = new HashSet<>();
        /** Todos que já entraram nesta dungeon: contam na lotação mesmo depois de morrer ou sair (e podem voltar). */
        final Set<UUID> entraram = new HashSet<>();
        final long limite = System.currentTimeMillis() + TEMPO_MS;
        final long semente = ThreadLocalRandom.current().nextLong();
        int etapa = 1, total = 1, tema;
        Fase fase = Fase.PAUSA;
        long ate = System.currentTimeMillis() + 5000;
        ServerBossEvent barra;
        Inst(int vaga, int rank, int nivel, BlockPos saida) { this.vaga = vaga; this.rank = rank; this.nivel = nivel; this.saida = saida; }
        BlockPos centro() { return new BlockPos(X0 + vaga * PASSO, Y, Z0); }
        AABB caixa() { BlockPos c = centro(); return new AABB(c.getX() - RMAX - 1, Y - 1, c.getZ() - RMAX - 1, c.getX() + RMAX + 2, Y + HMAX + 2, c.getZ() + RMAX + 2); }
    }

    private static final Map<Integer, Inst> insts = new HashMap<>();

    /** Quantos aventureiros cabem na dungeon de um portal, por rank (F a S): quanto mais alto o rank, mais gente. */
    private static final int[] LOTACAO = { 2, 3, 3, 4, 4, 5, 6 };
    public static int lotacao(int rank) { return LOTACAO[Math.max(0, Math.min(LOTACAO.length - 1, rank))]; }

    /** A área das dungeons é protegida como a da Torre (ninguém quebra nem coloca bloco). */
    static boolean naZona(int x, int z) {
        return x >= X0 - 48 && x <= X0 + VAGAS * PASSO + 48 && z >= Z0 - 48 && z <= Z0 + 48;
    }

    private static Inst de(Player p) {
        for (Inst i : insts.values()) if (i.membros.contains(p.getUUID())) return i;
        return null;
    }

    /** Há grupo lutando numa dungeon aberta por este portal? */
    public static boolean emAndamento(BlockPos portal) {
        for (Inst i : insts.values()) if (i.saida.equals(portal)) return true;
        return false;
    }

    private static List<ServerPlayer> membros(MinecraftServer s, Inst i) {
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID u : i.membros) { ServerPlayer p = s.getPlayerList().getPlayer(u); if (p != null) l.add(p); }
        return l;
    }

    private static void fora(ServerPlayer p, BlockPos saida) {
        p.teleportTo(p.server.overworld(), saida.getX() + 0.5, saida.getY() + 0.2, saida.getZ() + 3.5, p.getYRot(), 0);
        p.fallDistance = 0;
    }

    private static void montar(ServerLevel n, Inst i) {
        i.tema = (int) Math.floorMod(i.semente + i.etapa * 7L, Torre.TEMAS.length);
        for (Entity e : n.getEntities((Entity) null, i.caixa(), e -> !(e instanceof Player))) e.discard();
        Torre.montarSalaEm(n, i.centro(), Torre.TEMAS[i.tema], i.semente + i.etapa);
    }

    /** Atravessar o portal: entra na dungeon deste portal (a mesma para todos) ou abre uma nova se ainda não há. */
    public static boolean entrar(ServerPlayer p, int rank, int nivel, BlockPos portal) {
        if (de(p) != null) return false;
        MinecraftServer s = p.server;
        ServerLevel n = s.overworld();
        // um portal = uma dungeon só: todo mundo que atravessa cai no mesmo lugar, até a lotação do rank
        Inst inst = null;
        for (Inst i : insts.values()) if (i.saida.equals(portal)) inst = i;
        if (inst != null && !inst.entraram.contains(p.getUUID()) && inst.entraram.size() >= lotacao(inst.rank)) {
            p.displayClientMessage(Component.literal("A dungeon está lotada: um portal de Rank " + br.com.frxcode.frxworld.progressao.ConfigJogo.nomeRank(inst.rank)
                    + " aceita no máximo " + lotacao(inst.rank) + " aventureiros por vez.").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (inst == null) {
            int vaga = -1;
            for (int v = 0; v < VAGAS; v++) if (!insts.containsKey(v)) { vaga = v; break; }
            if (vaga < 0) { p.displayClientMessage(Component.literal("O portal está instável. Tente em instantes.").withStyle(ChatFormatting.RED), true); return false; }
            inst = new Inst(vaga, rank, nivel, portal.immutable());
            insts.put(vaga, inst);
            montar(n, inst);
        }
        inst.membros.add(p.getUUID());
        inst.entraram.add(p.getUUID());
        BlockPos o = inst.centro();
        p.teleportTo(n, o.getX() + 0.5, Y + 1, o.getZ() + 0.5, p.getYRot(), 0);
        p.fallDistance = 0;
        n.playSound(null, o.above(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 1.2f);
        Sistema.avisar(p, "Dungeon do Portal · Rank " + br.com.frxcode.frxworld.progressao.ConfigJogo.nomeRank(rank), CAMARAS + " câmaras de monstros e um chefão no fim. Vocês têm "
                + TEMPO_MS / 60_000 + " minutos. Quem morrer deixa a lápide do lado de fora, junto ao portal. /portal sair para desistir.", Sistema.ROXO);
        return true;
    }

    private static void comecarEtapa(MinecraftServer s, Inst i) {
        ServerLevel n = s.overworld();
        List<ServerPlayer> gente = membros(s, i);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        BlockPos o = i.centro();
        boolean chefe = i.etapa > CAMARAS;
        String[] tipos = Torre.MONSTROS[i.tema % Torre.MONSTROS.length];
        int raio = Torre.TEMAS[i.tema].raio();
        int qtd = chefe ? 2 : Math.min(14, 4 + i.rank + 2 * gente.size());
        if (chefe) {
            Mob ch = Chefes.criar(n, new Vec3(o.getX() + 0.5, Y + 1, o.getZ() + 0.5 - 6), i.rank, i.nivel + 2, (gente.size() - 1) * 0.7);
            if (ch != null) { ch.addTag(TAG); if (!gente.isEmpty()) ch.setTarget(gente.get(rnd.nextInt(gente.size()))); }
        }
        for (int k = 0; k < qtd; k++) {
            EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(tipos[rnd.nextInt(tipos.length)]));
            Entity e = tipo == null ? null : tipo.create(n);
            if (!(e instanceof Mob m)) continue;
            double x = o.getX() + 0.5 + 5, z = o.getZ() + 0.5 + 5;
            for (int t = 0; t < 12; t++) {
                int dx = rnd.nextInt(-raio + 2, raio - 1), dz = rnd.nextInt(-raio + 2, raio - 1);
                BlockPos pe = o.offset(dx, 1, dz);
                if (Math.max(Math.abs(dx), Math.abs(dz)) < 4 || !n.getBlockState(pe).getCollisionShape(n, pe).isEmpty() || !n.getBlockState(pe.above()).getCollisionShape(n, pe.above()).isEmpty()) continue;
                x = o.getX() + 0.5 + dx; z = o.getZ() + 0.5 + dz;
                break;
            }
            m.moveTo(x, Y + 1, z, rnd.nextFloat() * 360f, 0);
            m.finalizeSpawn(n, n.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            Zonas.aplicarNivel(m, i.nivel);
            m.setPersistenceRequired();
            m.addTag(TAG);
            n.addFreshEntity(m);
            if (!gente.isEmpty()) m.setTarget(gente.get(rnd.nextInt(gente.size())));
            n.sendParticles(ParticleTypes.PORTAL, x, Y + 1.5, z, 12, 0.3, 0.6, 0.3, 0.2);
        }
        i.total = qtd + (chefe ? 1 : 0);
        i.fase = Fase.LUTA;
        n.playSound(null, o.above(), chefe ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, chefe ? 0.5f : 2f, 1f);
    }

    private static void barra(MinecraftServer s, Inst i, int vivos, long agora) {
        boolean chefe = i.etapa > CAMARAS;
        long resta = Math.max(0, (i.limite - agora) / 1000);
        String texto = "Dungeon do Portal · Rank " + br.com.frxcode.frxworld.progressao.ConfigJogo.nomeRank(i.rank) + " — "
                + (chefe ? "CHEFÃO" : "câmara " + i.etapa + " de " + CAMARAS) + " · " + Torre.TEMAS[i.tema].nome()
                + (i.fase == Fase.LUTA ? " · faltam " + vivos : " · prepare-se") + " · " + resta / 60 + ":" + String.format("%02d", resta % 60);
        if (i.barra == null) i.barra = new ServerBossEvent(Component.literal(texto), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        i.barra.setName(Component.literal(texto).withStyle(chefe ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
        i.barra.setProgress(i.fase == Fase.LUTA ? Math.max(0f, Math.min(1f, vivos / (float) Math.max(1, i.total))) : 1f);
        List<ServerPlayer> gente = membros(s, i);
        for (ServerPlayer p : new ArrayList<>(i.barra.getPlayers())) if (!gente.contains(p)) i.barra.removePlayer(p);
        for (ServerPlayer p : gente) i.barra.addPlayer(p);
    }

    private static void encerrar(MinecraftServer s, Inst i, boolean tirar) {
        if (i.barra != null) { i.barra.removeAllPlayers(); i.barra = null; }
        if (tirar) for (ServerPlayer p : membros(s, i)) fora(p, i.saida);
        for (Entity e : s.overworld().getEntities((Entity) null, i.caixa(), e -> !(e instanceof Player))) e.discard();
        insts.remove(i.vaga);
    }

    private static void tickInst(MinecraftServer s, ServerLevel n, Inst i, long agora) {
        AABB caixa = i.caixa();
        i.membros.removeIf(u -> {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            return p == null || !p.isAlive() || p.level() != n || !caixa.inflate(4).contains(p.position());
        });
        if (i.membros.isEmpty()) { encerrar(s, i, false); return; }
        if (agora > i.limite) {
            for (ServerPlayer p : membros(s, i)) p.sendSystemMessage(Component.literal("◈ O tempo acabou. A dungeon se desfaz e joga vocês para fora.").withStyle(ChatFormatting.RED));
            encerrar(s, i, true);
            return;
        }
        List<Mob> vivos = i.fase == Fase.LUTA ? n.getEntitiesOfClass(Mob.class, caixa, m -> m.isAlive() && (m instanceof Enemy || m.getTags().contains(TAG))) : List.of();
        barra(s, i, vivos.size(), agora);
        if (i.fase == Fase.PAUSA) {
            if (agora >= i.ate) comecarEtapa(s, i);
            return;
        }
        List<ServerPlayer> gente = membros(s, i);
        for (Mob m : vivos) if (m.getTarget() == null && !gente.isEmpty()) m.setTarget(gente.get(n.random.nextInt(gente.size())));
        if (!vivos.isEmpty()) return;
        if (i.etapa > CAMARAS) {
            br.com.frxcode.frxworld.reinos.Portais.aoVencerDungeon(s, gente, i.rank, i.nivel, i.saida);
            encerrar(s, i, true);
            return;
        }
        i.etapa++;
        i.fase = Fase.PAUSA;
        i.ate = agora + 6000;
        montar(n, i);
        for (ServerPlayer p : gente) {
            p.heal(p.getMaxHealth() * 0.25f);
            p.displayClientMessage(Component.literal(i.etapa > CAMARAS ? "Câmaras vencidas. O chefão está chegando..." : "Câmara vencida! Próxima: " + i.etapa + " de " + CAMARAS)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        n.playSound(null, i.centro().above(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.1f);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 10 != 6) return;
        MinecraftServer s = ev.getServer();
        ServerLevel n = s.overworld();
        long agora = System.currentTimeMillis();
        for (Inst i : new ArrayList<>(insts.values())) {
            try { tickInst(s, n, i, agora); }
            catch (Exception e) { FrxWorld.LOG.warn("Dungeon de portal {} falhou: {}", i.vaga, e.toString()); encerrar(s, i, true); }
        }
        if (s.getTickCount() % 100 == 6)
            for (ServerPlayer p : n.players())
                if (naZona(p.getBlockX(), p.getBlockZ()) && de(p) == null && !(p.isCreative() && p.hasPermissions(2))) {
                    BlockPos sp = n.getSharedSpawnPos();
                    p.teleportTo(n, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
                }
    }

    /** Morte na dungeon: o jogador vai para o portal antes de cair, e a lápide fica lá fora. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoMorrer(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Inst i = de(p);
        if (i == null) return;
        i.membros.remove(p.getUUID());
        if (i.barra != null) i.barra.removePlayer(p);
        fora(p, i.saida);
        Sistema.avisar(p, "Você caiu na dungeon", "Seus itens ficaram numa lápide junto ao portal, em " + i.saida.getX() + ", " + i.saida.getY() + ", " + i.saida.getZ() + ".", Sistema.VERMELHO);
        if (i.membros.isEmpty()) encerrar(p.server, i, false);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        Inst i = de(ev.getEntity());
        if (i != null && ev.getEntity() instanceof ServerPlayer p) {
            i.membros.remove(p.getUUID());
            fora(p, i.saida);
            if (i.membros.isEmpty()) encerrar(p.server, i, false);
        }
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        for (Inst i : new ArrayList<>(insts.values())) encerrar(ev.getServer(), i, true);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("portal").then(Commands.literal("sair").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            Inst i = de(p);
            if (i == null) { p.sendSystemMessage(Component.literal("Você não está numa dungeon de portal.").withStyle(ChatFormatting.GRAY)); return 0; }
            i.membros.remove(p.getUUID());
            if (i.barra != null) i.barra.removePlayer(p);
            fora(p, i.saida);
            if (i.membros.isEmpty()) encerrar(p.server, i, false);
            return 1;
        })));
    }
}
