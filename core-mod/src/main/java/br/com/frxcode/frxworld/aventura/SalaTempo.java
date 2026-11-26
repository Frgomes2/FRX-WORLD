package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Zonas;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sala do Tempo: salão branco e fechado, longe de tudo, para treinar.
 *  - Entrada: a passagem no fundo do salão do Templo Karin (lá em cima). Precisa de rank B ou maior na Guilda.
 *  - 30 minutos por dia real para cada jogador (o dia vira à meia-noite de São Paulo). O tempo aparece na barra de ação.
 *  - Lá dentro: XP em dobro, dano recebido em dobro e fome mais rápida.
 *  - Monstros de treino aparecem no nível do jogador mais forte da sala e somem quando ela esvazia. Não deixam itens.
 *  - Saída: agachar no círculo dourado do centro, ou acabar o tempo. Volta para o salão do templo.
 * Construir (sala + passagem no templo): /frx sala construir. Proteção de blocos: área "sala_tempo" do frx_protegidas.txt.
 */
public class SalaTempo {
    public static final int SX = 26000, SY = 200, SZ = 26000, MEIO = 24, ALTURA = 11;
    private static final int LIMITE = 30 * 60, MAX_MOBS = 18;
    private static final String PERS = Player.PERSISTED_NBT_TAG, DIA = "frx_sala_dia", SEG = "frx_sala_seg", TAG = "frx_sala", TAG_TXT = "frx_sala_txt";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final EntityType<?>[] TREINO = {EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.VINDICATOR};

    private static final Map<UUID, Long> AVISO = new HashMap<>();
    private static int relogio;

    public static boolean dentro(Entity e) {
        return e.level().dimension() == Level.OVERWORLD && Math.abs(e.getX() - (SX + 0.5)) <= MEIO + 1.5 && Math.abs(e.getZ() - (SZ + 0.5)) <= MEIO + 1.5
                && e.getY() >= SY - 1 && e.getY() <= SY + ALTURA + 2;
    }

    private static AABB caixa() { return new AABB(SX - MEIO - 1, SY, SZ - MEIO - 1, SX + MEIO + 2, SY + ALTURA + 1, SZ + MEIO + 2); }

    private static int rankB() {
        for (int i = 0; i < ConfigJogo.totalRanks(); i++) if (ConfigJogo.nomeRank(i).equalsIgnoreCase("B")) return i;
        return 4;
    }

    private static CompoundTag pers(ServerPlayer p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(PERS)) raiz.put(PERS, new CompoundTag());
        CompoundTag t = raiz.getCompound(PERS);
        long hoje = LocalDate.now(FUSO).toEpochDay();
        if (t.getLong(DIA) != hoje) { t.putLong(DIA, hoje); t.putInt(SEG, 0); }
        return t;
    }

    public static int restante(ServerPlayer p) { return Math.max(0, LIMITE - pers(p).getInt(SEG)); }

    private static String relogio(int s) { return String.format("%02d:%02d", s / 60, s % 60); }

    // ------------------------------------------------------------------ construção

    private static void s(ServerLevel nv, int x, int y, int z, BlockState b) { nv.setBlock(new BlockPos(x, y, z), b, 2); }

    public static boolean construir(ServerLevel nv) {
        if (!nv.getWorldBorder().isWithinBounds(new BlockPos(SX + MEIO + 2, SY, SZ + MEIO + 2))) return false;
        BlockState branco = Blocks.WHITE_CONCRETE.defaultBlockState(), quartzo = Blocks.SMOOTH_QUARTZ.defaultBlockState(), ar = Blocks.AIR.defaultBlockState();
        int b = MEIO + 1;
        for (int dx = -b; dx <= b; dx++)
            for (int dz = -b; dz <= b; dz++)
                for (int y = 0; y <= ALTURA + 1; y++) {
                    boolean parede = Math.abs(dx) == b || Math.abs(dz) == b;
                    BlockState bl = ar;
                    if (y == 0) bl = (Math.floorMod(dx, 8) == 0 || Math.floorMod(dz, 8) == 0) ? quartzo : branco;
                    else if (parede || y == ALTURA + 1) bl = branco;
                    else if (y == 7 && Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0) bl = Blocks.LIGHT.defaultBlockState();
                    s(nv, SX + dx, SY + y, SZ + dz, bl);
                }
        // pavilhão do centro: é por ele que se sai
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) s(nv, SX + dx, SY, SZ + dz, Blocks.GOLD_BLOCK.defaultBlockState());
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}})
            for (int y = 1; y <= 4; y++) s(nv, SX + c[0], SY + y, SZ + c[1], Blocks.QUARTZ_PILLAR.defaultBlockState());
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) s(nv, SX + dx, SY + 5, SZ + dz, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) s(nv, SX + dx, SY + 6, SZ + dz, Blocks.GOLD_BLOCK.defaultBlockState());
        s(nv, SX, SY + 7, SZ, Blocks.GOLD_BLOCK.defaultBlockState());
        for (Display.TextDisplay t : nv.getEntitiesOfClass(Display.TextDisplay.class, caixa(), e -> e.getTags().contains(TAG_TXT))) t.discard();
        CompoundTag placa = new CompoundTag();
        placa.putString("id", "minecraft:text_display");
        placa.putString("text", Component.Serializer.toJson(Component.literal("⬇ SAÍDA ⬇\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Agache (Shift) no círculo dourado").withStyle(ChatFormatting.WHITE))));
        placa.putString("billboard", "center");
        ListTag tagsPlaca = new ListTag();
        tagsPlaca.add(StringTag.valueOf(TAG_TXT));
        placa.put("Tags", tagsPlaca);
        Entity ePlaca = EntityType.loadEntityRecursive(placa, nv, x -> { x.moveTo(SX + 0.5, SY + 3.2, SZ + 0.5, 0, 0); return x; });
        if (ePlaca != null) nv.addFreshEntity(ePlaca);
        porta(nv);
        FrxWorld.LOG.info("Sala do Tempo construída em {} {} {}.", SX, SY, SZ);
        return true;
    }

    /** A passagem no fundo do salão do Templo Karin (o templo chama isto quando é reconstruído). */
    public static void porta(ServerLevel nv) {
        int z = Karin.CZ - 25, y0 = Karin.TOPO;
        for (int dx = -1; dx <= 1; dx++) {
            s(nv, Karin.CX + dx, y0, z, Blocks.SEA_LANTERN.defaultBlockState());
            s(nv, Karin.CX + dx, y0 + 4, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            for (int y = 1; y <= 3; y++) s(nv, Karin.CX + dx, y0 + y, z, Blocks.AIR.defaultBlockState());
        }
        for (int dx : new int[]{-2, 2}) for (int y = 1; y <= 4; y++) s(nv, Karin.CX + dx, y0 + y, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
        AABB perto = new AABB(Karin.CX - 4, y0, z - 3, Karin.CX + 5, y0 + 8, z + 4);
        for (Display.TextDisplay t : nv.getEntitiesOfClass(Display.TextDisplay.class, perto, e -> e.getTags().contains(TAG_TXT))) t.discard();
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:text_display");
        t.putString("text", Component.Serializer.toJson(Component.literal("⏳ Sala do Tempo\n").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Component.literal("Rank B ou maior · 30 min por dia").withStyle(ChatFormatting.GRAY))));
        t.putString("billboard", "center");
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG_TXT));
        t.put("Tags", tags);
        Entity e = EntityType.loadEntityRecursive(t, nv, x -> { x.moveTo(Karin.CX + 0.5, y0 + 3.4, z + 1.2, 0, 0); return x; });
        if (e != null) nv.addFreshEntity(e);
    }

    // ------------------------------------------------------------------ entrar e sair

    private static void recusar(ServerPlayer p, String msg) {
        p.teleportTo(p.server.overworld(), Karin.CX + 0.5, Karin.TOPO + 1, Karin.CZ - 20.5, 0, 0);
        long agora = System.currentTimeMillis();
        Long ultimo = AVISO.put(p.getUUID(), agora);
        if (ultimo == null || agora - ultimo > 3000) p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.RED), false);
    }

    private static void entrar(ServerPlayer p) {
        if (!p.isCreative()) {
            if (Guilda.indice(p) < rankB()) { recusar(p, "⏳ A Sala do Tempo só abre para rank B ou maior na Guilda. O seu é " + Guilda.rank(p) + "."); return; }
            if (restante(p) <= 0) { recusar(p, "⏳ Você já usou os seus 30 minutos de hoje. Volte amanhã."); return; }
        }
        ServerLevel nv = p.server.overworld();
        p.teleportTo(nv, SX + 0.5, SY + 1, SZ + 7.5, 180, 0);
        nv.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.2f);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("XP em dobro · dano em dobro · " + relogio(restante(p)) + " hoje").withStyle(ChatFormatting.GRAY)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("⏳ Sala do Tempo").withStyle(ChatFormatting.WHITE)));
        p.displayClientMessage(Component.literal("⏳ Para sair, agache no círculo dourado do centro.").withStyle(ChatFormatting.AQUA), false);
    }

    private static void sair(ServerPlayer p, String msg) {
        ServerLevel nv = p.server.overworld();
        p.teleportTo(nv, Karin.CX + 0.5, Karin.TOPO + 1, Karin.CZ - 20.5, 0, 0);
        nv.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 1.2f);
        p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.AQUA), false);
    }

    // ------------------------------------------------------------------ regras

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        relogio++;
        if (relogio % 10 != 0) return;
        MinecraftServer srv = ev.getServer();
        ServerLevel nv = srv.overworld();
        boolean segundo = relogio % 20 == 0;
        List<ServerPlayer> naSala = new ArrayList<>();
        for (ServerPlayer p : new ArrayList<>(srv.getPlayerList().getPlayers())) {
            if (p.level().dimension() != Level.OVERWORLD || p.isSpectator() || !p.isAlive()) continue;
            if (p.getBlockZ() == Karin.CZ - 25 && Math.abs(p.getBlockX() - Karin.CX) <= 1 && p.getBlockY() >= Karin.TOPO + 1 && p.getBlockY() <= Karin.TOPO + 2) { entrar(p); continue; }
            if (!dentro(p)) continue;
            boolean noCentro = Math.abs(p.getX() - (SX + 0.5)) <= 1.5 && Math.abs(p.getZ() - (SZ + 0.5)) <= 1.5 && p.getY() < SY + 3;
            if (noCentro && p.isShiftKeyDown()) { sair(p, "⏳ Você saiu da Sala do Tempo. Restam " + relogio(restante(p)) + " hoje."); continue; }
            if (p.isCreative()) {
                if (segundo) p.displayClientMessage(Component.literal("⏳ Sala do Tempo (criativo: sem relógio)" + (noCentro ? " · agache para sair" : " · saída: agache no círculo dourado do centro")).withStyle(ChatFormatting.WHITE), true);
                naSala.add(p);
                continue;
            }
            if (Guilda.indice(p) < rankB()) { recusar(p, "⏳ A Sala do Tempo só abre para rank B ou maior na Guilda."); continue; }
            if (segundo) {
                CompoundTag t = pers(p);
                int usado = t.getInt(SEG) + 1;
                t.putInt(SEG, usado);
                if (usado >= LIMITE) { sair(p, "⏳ O seu tempo de hoje acabou. A Sala do Tempo abre de novo amanhã."); continue; }
                p.causeFoodExhaustion(0.1f);
                p.displayClientMessage(Component.literal("⏳ " + relogio(LIMITE - usado) + " · XP ×2 · dano ×2" + (noCentro ? " · agache para sair" : " · saída no círculo dourado")).withStyle(ChatFormatting.WHITE), true);
            }
            naSala.add(p);
        }
        if (!segundo) return;
        if (naSala.isEmpty()) {
            if (relogio % 100 == 0 && nv.hasChunk(SX >> 4, SZ >> 4))
                for (Mob m : nv.getEntitiesOfClass(Mob.class, caixa(), e -> e.getTags().contains(TAG))) m.discard();
            return;
        }
        if (relogio % 160 != 0) return;
        List<Mob> vivos = nv.getEntitiesOfClass(Mob.class, caixa(), e -> e.getTags().contains(TAG));
        int alvo = Math.min(MAX_MOBS, 5 * naSala.size());
        int nivel = 1;
        for (ServerPlayer p : naSala) if (!p.isCreative()) nivel = Math.max(nivel, Progressao.dados(p).nivel());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 3 && vivos.size() + k < alvo; k++) {
            double x = 0, z = 0;
            boolean ok = false;
            for (int t = 0; t < 12 && !ok; t++) {
                x = SX + 0.5 + r.nextDouble(-MEIO + 2, MEIO - 2);
                z = SZ + 0.5 + r.nextDouble(-MEIO + 2, MEIO - 2);
                ok = Math.abs(x - SX) > 6 || Math.abs(z - SZ) > 6;
                for (ServerPlayer p : naSala) if (p.distanceToSqr(x, p.getY(), z) < 100) ok = false;
            }
            if (!ok) continue;
            Entity e = TREINO[r.nextInt(TREINO.length)].create(nv);
            if (!(e instanceof Mob m)) continue;
            m.moveTo(x, SY + 1, z, r.nextFloat() * 360, 0);
            m.finalizeSpawn(nv, nv.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            m.addTag(TAG);
            Zonas.aplicarNivel(m, nivel);
            nv.addFreshEntity(m);
            nv.sendParticles(ParticleTypes.END_ROD, x, SY + 2, z, 12, 0.3, 0.8, 0.3, 0.02);
        }
    }

    @SubscribeEvent
    public void danoEmDobro(LivingHurtEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && !p.isCreative() && dentro(p)) ev.setAmount(ev.getAmount() * 2);
    }

    @SubscribeEvent
    public void semItens(LivingDropsEvent ev) {
        if (ev.getEntity().getTags().contains(TAG)) ev.getDrops().clear();
    }

    @SubscribeEvent
    public void semNaturais(MobSpawnEvent.FinalizeSpawn ev) {
        if (ev.getSpawnType() == MobSpawnType.NATURAL && ev.getLevel() instanceof ServerLevel nv && nv.dimension() == Level.OVERWORLD
                && Math.abs(ev.getX() - SX) <= MEIO + 2 && Math.abs(ev.getZ() - SZ) <= MEIO + 2 && ev.getY() >= SY - 2 && ev.getY() <= SY + ALTURA + 3)
            ev.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("sala")
                        .then(Commands.literal("construir").executes(ctx -> {
                            boolean ok = construir(ctx.getSource().getServer().overworld());
                            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Sala do Tempo construída em " + SX + ", " + SY + ", " + SZ + " e passagem aberta no Templo Karin."
                                    : "A Sala do Tempo ficaria fora da borda do mundo."), true);
                            return 1;
                        }))
                        .then(Commands.literal("ir").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.teleportTo(ctx.getSource().getServer().overworld(), SX + 0.5, SY + 1, SZ + 7.5, 180, 0);
                            return 1;
                        }))));
    }
}
