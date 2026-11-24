package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Templo Karin: torre branca altíssima a leste de Lubelius, escalada no braço até o templo do topo,
 * onde o Príncipe Vegeta testa quem chega (ver Vegeta.java).
 *  - Escada por fora, em trechos de 21 blocos, girando pela torre com passarelas estreitas; dois patamares de descanso.
 *  - Fosso de água em volta da base: quem cai junto da torre não morre.
 *  - Na área (45 blocos em volta) não vale élitro, voo, montaria nem pérola/coro; subir sem apoio é cortado.
 *  - Acima da altura 160 sopram ventanias com aviso: agachado (Shift) você se segura.
 *  - Monstro não nasce sozinho na área.
 * Construir: /frx karin construir (admin). A proteção de blocos é a área "karin" do frx_protegidas.txt.
 */
public class Karin {
    public static final int CX = 2060, CZ = 1640, CHAO = 63, TOPO = 288, RAIO_AREA = 45, RAIO_ARENA = 30;
    private static final int VENTO_DE = 160;

    private static final Map<UUID, int[]> ESTADO = new ConcurrentHashMap<>();   // [subindo sem apoio, aviso de vento]

    public static boolean naArea(Level n, double x, double z) {
        return n.dimension() == Level.OVERWORLD && Math.abs(x - CX) <= RAIO_AREA && Math.abs(z - CZ) <= RAIO_AREA;
    }

    public static boolean naArena(Entity e) {
        double dx = e.getX() - (CX + 0.5), dz = e.getZ() - (CZ + 0.5);
        return e.level().dimension() == Level.OVERWORLD && dx * dx + dz * dz <= (RAIO_ARENA + 0.5) * (RAIO_ARENA + 0.5)
                && e.getY() >= TOPO && e.getY() <= TOPO + 26;
    }

    public static Vec3 centroArena() { return new Vec3(CX + 0.5, TOPO + 1, CZ + 0.5); }

    // ------------------------------------------------------------------ construção

    private static void s(ServerLevel nv, int x, int y, int z, BlockState b) { nv.setBlock(new BlockPos(x, y, z), b, 2); }

    private static double dist(int dx, int dz) { return Math.sqrt(dx * dx + dz * dz); }

    private static final int[][] FACES = {{0, -4}, {4, 0}, {0, 4}, {-4, 0}};
    private static final Direction[] DIR = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public static int construir(ServerLevel nv) {
        int n = 0;
        BlockState ar = Blocks.AIR.defaultBlockState(), agua = Blocks.WATER.defaultBlockState(), quartzo = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        // terreno: limpa por cima, fosso, borda e grama
        for (int dx = -15; dx <= 15; dx++)
            for (int dz = -15; dz <= 15; dz++) {
                double r = dist(dx, dz);
                if (r > 15.5) continue;
                int x = CX + dx, z = CZ + dz;
                nv.getChunk(x >> 4, z >> 4);
                for (int y = CHAO + 1; y <= CHAO + 40; y++) s(nv, x, y, z, ar);
                if (r <= 3.5) continue;
                if (r <= 9.5) {
                    for (int y = CHAO - 8; y <= CHAO - 6; y++) s(nv, x, y, z, Blocks.STONE.defaultBlockState());
                    for (int y = CHAO - 5; y <= CHAO; y++) s(nv, x, y, z, agua);
                } else if (r <= 11.5) {
                    for (int y = CHAO - 8; y < CHAO; y++) s(nv, x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                    s(nv, x, CHAO, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                } else {
                    for (int y = CHAO - 4; y < CHAO; y++) s(nv, x, y, z, Blocks.DIRT.defaultBlockState());
                    s(nv, x, CHAO, z, r <= 12.5 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                }
                n++;
            }
        // fuste branco com faixas douradas
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (dist(dx, dz) > 3.4) continue;
                for (int y = CHAO - 8; y < TOPO; y++) {
                    BlockState b = quartzo;
                    if (y > CHAO && (y - CHAO) % 24 == 0) b = Blocks.GOLD_BLOCK.defaultBlockState();
                    else if (y > CHAO && (y - CHAO) % 8 == 0) b = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
                    s(nv, CX + dx, y, CZ + dz, b);
                }
            }
        // bojo por baixo do santuário: meia esfera branca com a base vermelha
        for (int y = TOPO - 16; y < TOPO; y++) {
            double f = (TOPO - y) / 17.0, raio = Math.sqrt(1 - f * f) * RAIO_ARENA;
            BlockState b = y < TOPO - 10 ? Blocks.RED_TERRACOTTA.defaultBlockState() : quartzo;
            for (int dx = -RAIO_ARENA; dx <= RAIO_ARENA; dx++)
                for (int dz = -RAIO_ARENA; dz <= RAIO_ARENA; dz++) {
                    double r = dist(dx, dz);
                    if (r <= 3.4 || r > raio || r < raio - 3.5) continue;   // casca de 3 blocos (oco por dentro)
                    if (dx == FACES[2][0] && dz == FACES[2][1]) continue;   // túnel da última escada
                    s(nv, CX + dx, y, CZ + dz, b);
                }
        }
        // escadas e passarelas
        BlockState degrau = Blocks.QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int i = 0; ; i++) {
            int base = CHAO + 1 + 21 * i, topo = Math.min(base + 20, TOPO);
            int f = i % 4;
            int fx = CX + FACES[f][0], fz = CZ + FACES[f][1];
            for (int y = (i == 0 ? CHAO - 5 : base); y <= topo; y++)
                s(nv, fx, y, fz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, DIR[f])
                        .setValue(BlockStateProperties.WATERLOGGED, y <= CHAO));
            if (topo >= TOPO) break;
            // passarela em quarto de volta até a próxima face, no nível do topo da escada
            double a0 = Math.toRadians(-90 + 90 * f);
            for (double a = 0; a <= Math.PI / 2 + 0.001; a += Math.toRadians(2)) {
                for (double r : new double[]{4.0, 4.6}) {
                    int x = (int) Math.round(Math.cos(a0 + a) * r), z = (int) Math.round(Math.sin(a0 + a) * r);
                    if (dist(x, z) <= 3.4 || (x == FACES[f][0] && z == FACES[f][1])) continue;
                    s(nv, CX + x, topo, CZ + z, degrau);
                }
            }
            // patamares de descanso
            if (i == 3 || i == 7) {
                for (int dx = -7; dx <= 7; dx++)
                    for (int dz = -7; dz <= 7; dz++) {
                        double r = dist(dx, dz);
                        if (r <= 3.4 || r > 6.5 || (dx == FACES[f][0] && dz == FACES[f][1])) continue;
                        s(nv, CX + dx, topo, CZ + dz, degrau);
                    }
                for (int[] d : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
                    s(nv, CX + d[0], topo + 1, CZ + d[1], Blocks.QUARTZ_PILLAR.defaultBlockState());
                    s(nv, CX + d[0], topo + 2, CZ + d[1], Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        // santuário do topo: plataforma larga para a luta, templo de cúpulas douradas ao norte, mirantes e fileiras de árvores
        int R = RAIO_ARENA;
        for (int dx = -R - 1; dx <= R + 1; dx++)
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double r = dist(dx, dz);
                if (r > R + 0.4) continue;
                int x = CX + dx, z = CZ + dz;
                for (int y = TOPO + 1; y <= TOPO + 24; y++) s(nv, x, y, z, ar);
                if (dx == FACES[2][0] && dz == FACES[2][1]) {   // chegada da escada: alçapão para ninguém cair no meio da luta
                    s(nv, x, TOPO, z, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                    continue;
                }
                BlockState piso = r <= 1.2 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState()
                        : (r >= 10.5 && r <= 11.5) ? Blocks.GOLD_BLOCK.defaultBlockState()
                        : r >= R - 0.6 ? Blocks.RED_TERRACOTTA.defaultBlockState() : quartzo;
                s(nv, x, TOPO, z, piso);
                if (r > R - 0.6) s(nv, x, TOPO + 1, z, Blocks.POLISHED_DIORITE_SLAB.defaultBlockState());
            }
        // templo (ao norte): salão com faixa vermelha e cúpula dourada em degraus, duas alas menores e toldo na porta
        BlockState ouro = Blocks.GOLD_BLOCK.defaultBlockState(), faixa = Blocks.RED_TERRACOTTA.defaultBlockState();
        int[][] salas = {{-7, 7, -26, -16, 6}, {-14, -9, -24, -18, 5}, {9, 14, -24, -18, 5}};
        for (int[] c : salas) {
            for (int dx = c[0]; dx <= c[1]; dx++)
                for (int dz = c[2]; dz <= c[3]; dz++) {
                    boolean borda = dx == c[0] || dx == c[1] || dz == c[2] || dz == c[3];
                    for (int y = 1; y <= c[4]; y++) {
                        boolean porta = dz == c[3] && Math.abs(dx - (c[0] + c[1]) / 2) <= 1 && y <= 3;
                        if (borda && !porta) s(nv, CX + dx, TOPO + y, CZ + dz, y == c[4] ? faixa : quartzo);
                    }
                    s(nv, CX + dx, TOPO + c[4] + 1, CZ + dz, ouro);
                }
            for (int k = 1; ; k++) {   // cúpula: camadas que encolhem
                int x0 = c[0] + k, x1 = c[1] - k, z0 = c[2] + k, z1 = c[3] - k;
                if (x0 > x1 || z0 > z1) break;
                for (int dx = x0; dx <= x1; dx++)
                    for (int dz = z0; dz <= z1; dz++) s(nv, CX + dx, TOPO + c[4] + 1 + k, CZ + dz, ouro);
            }
            s(nv, CX + (c[0] + c[1]) / 2, TOPO + c[4] - 1, CZ + (c[2] + c[3]) / 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            s(nv, CX + (c[0] + c[1]) / 2, TOPO + c[4], CZ + (c[2] + c[3]) / 2, Blocks.CHAIN.defaultBlockState());
        }
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -15; dz <= -13; dz++) s(nv, CX + dx, TOPO + 4, CZ + dz, Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState());
        for (int dx : new int[]{-3, 3}) for (int y = 1; y <= 3; y++) s(nv, CX + dx, TOPO + y, CZ - 13, Blocks.SPRUCE_FENCE.defaultBlockState());
        // mirantes: pilar alto com uma plataforma coberta
        for (int[] m : new int[][]{{-21, -13}, {21, -13}, {-26, 1}, {26, 1}}) {
            for (int y = 1; y <= 9; y++) s(nv, CX + m[0], TOPO + y, CZ + m[1], Blocks.QUARTZ_PILLAR.defaultBlockState());
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    s(nv, CX + m[0] + dx, TOPO + 10, CZ + m[1] + dz, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                    s(nv, CX + m[0] + dx, TOPO + 14, CZ + m[1] + dz, Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState());
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) for (int y = 11; y <= 13; y++) s(nv, CX + m[0] + dx, TOPO + y, CZ + m[1] + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            s(nv, CX + m[0], TOPO + 13, CZ + m[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // fileiras de árvores (ciprestes): duas no caminho da entrada e uma de cada lado
        java.util.List<int[]> arvores = new java.util.ArrayList<>();
        for (int dz = 15; dz <= 24; dz += 3) { arvores.add(new int[]{-7, dz}); arvores.add(new int[]{7, dz}); }
        for (int dz = 6; dz <= 15; dz += 3) { arvores.add(new int[]{-22, dz}); arvores.add(new int[]{22, dz}); }
        for (int dx = -18; dx <= -12; dx += 3) { arvores.add(new int[]{dx, -12}); arvores.add(new int[]{-dx, -12}); }
        BlockState folha = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        for (int[] a : arvores) {
            s(nv, CX + a[0], TOPO, CZ + a[1], Blocks.GRASS_BLOCK.defaultBlockState());
            s(nv, CX + a[0], TOPO + 1, CZ + a[1], Blocks.SPRUCE_LOG.defaultBlockState());
            for (int y = 2; y <= 6; y++) s(nv, CX + a[0], TOPO + y, CZ + a[1], folha);
        }
        // lanternas baixas em volta do círculo de duelo
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int x = CX + (int) Math.round(Math.cos(a) * 14), z = CZ + (int) Math.round(Math.sin(a) * 14);
            s(nv, x, TOPO + 1, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            s(nv, x, TOPO + 2, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
        waystone(nv, new BlockPos(CX, TOPO + 1, CZ + 26));
        // ponte curta da beira do fosso até a primeira escada
        for (int dz = -11; dz <= -5; dz++) s(nv, CX, CHAO, CZ + dz, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)
                .setValue(BlockStateProperties.WATERLOGGED, dz > -10));
        FrxWorld.LOG.info("Templo Karin construído em {} {} (topo {}).", CX, CZ, TOPO);
        return n;
    }

    /** Waystone "Templo Karin" na ponta sul do santuário (quem já subiu uma vez pode voltar direto). */
    private static void waystone(ServerLevel nv, BlockPos base) {
        try {
            Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
            java.util.Optional<?> w = (java.util.Optional<?>) api.getMethod("getWaystoneAt", ServerLevel.class, BlockPos.class).invoke(null, nv, base);
            if (w.isEmpty()) {
                Class<?> estiloCls = Class.forName("net.blay09.mods.waystones.api.WaystoneStyle");
                Object estilo = Class.forName("net.blay09.mods.waystones.api.WaystoneStyles").getField("DEFAULT").get(null);
                w = (java.util.Optional<?>) api.getMethod("placeWaystone", Level.class, BlockPos.class, estiloCls).invoke(null, nv, base, estilo);
            }
            if (w.isEmpty()) return;
            Class.forName("net.blay09.mods.waystones.api.IMutableWaystone").getMethod("setName", String.class).invoke(w.get(), "Templo Karin");
            Class<?> gerente = Class.forName("net.blay09.mods.waystones.core.WaystoneManager");
            Object g = gerente.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, nv.getServer());
            gerente.getMethod("updateWaystone", Class.forName("net.blay09.mods.waystones.api.IWaystone")).invoke(g, w.get());
        } catch (Throwable e) {
            FrxWorld.LOG.warn("Templo Karin: não consegui colocar a waystone: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ regras da área

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        if (!naArea(p.level(), p.getX(), p.getZ())) { ESTADO.remove(p.getUUID()); return; }
        int[] st = ESTADO.computeIfAbsent(p.getUUID(), k -> new int[2]);
        if (p.isFallFlying()) { p.stopFallFlying(); aviso(p, "O vento do templo não deixa planar: suba no braço."); }
        if (p.getAbilities().flying || p.getAbilities().mayfly) {
            p.getAbilities().flying = false;
            p.getAbilities().mayfly = false;
            p.onUpdateAbilities();
        }
        if (p.isPassenger()) { p.stopRiding(); aviso(p, "Montarias não sobem o Templo Karin."); }
        boolean apoio = p.onGround() || p.onClimbable() || p.isInWater() || p.isInLava();
        Vec3 v = p.getDeltaMovement();
        if (apoio) st[0] = 0;
        else if (v.y > 0.02) st[0]++;
        if (st[0] > 14) {   // subindo sem apoio por muito tempo: voo de algum jeito
            p.setDeltaMovement(v.x * 0.5, -0.25, v.z * 0.5);
            p.hurtMarked = true;
        }
        // ventania nas partes altas
        if (p.getY() < VENTO_DE || p.getY() >= TOPO) { st[1] = 0; return; }
        ServerLevel nv = p.serverLevel();
        if (st[1] == 0) {
            if (p.getRandom().nextInt(220) == 0) {
                st[1] = 30;
                aviso(p, "💨 Uma ventania se aproxima! Agache (Shift) para se segurar!");
                nv.playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 0.6f, 0.6f);
            }
            return;
        }
        if (st[1] % 4 == 0) nv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 6, 1.5, 0.8, 1.5, 0.05);
        if (--st[1] > 0) return;
        if (p.isShiftKeyDown()) { aviso(p, "Você se segurou firme. A ventania passou."); return; }
        Vec3 fora = new Vec3(p.getX() - (CX + 0.5), 0, p.getZ() - (CZ + 0.5));
        if (fora.lengthSqr() < 0.01) fora = new Vec3(1, 0, 0);
        fora = fora.normalize().scale(0.85);
        p.push(fora.x, 0.2, fora.z);
        p.hurtMarked = true;
        nv.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.AMBIENT, 1f, 0.5f);
    }

    private static void aviso(ServerPlayer p, String t) { p.displayClientMessage(Component.literal(t).withStyle(ChatFormatting.AQUA), true); }

    @SubscribeEvent
    public void perola(EntityTeleportEvent.EnderPearl ev) {
        if (naArea(ev.getEntity().level(), ev.getTargetX(), ev.getTargetZ()) || naArea(ev.getEntity().level(), ev.getEntity().getX(), ev.getEntity().getZ()))
            ev.setCanceled(true);
    }

    @SubscribeEvent
    public void coro(EntityTeleportEvent.ChorusFruit ev) {
        if (naArea(ev.getEntity().level(), ev.getTargetX(), ev.getTargetZ()) || naArea(ev.getEntity().level(), ev.getEntity().getX(), ev.getEntity().getZ()))
            ev.setCanceled(true);
    }

    @SubscribeEvent
    public void semMonstros(MobSpawnEvent.FinalizeSpawn ev) {
        if (ev.getSpawnType() != MobSpawnType.NATURAL || ev.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (!(ev.getLevel() instanceof ServerLevel nv) || !naArea(nv, ev.getX(), ev.getZ())) return;
        ev.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("karin")
                        .then(Commands.literal("construir").executes(ctx -> {
                            int n = construir(ctx.getSource().getServer().overworld());
                            ctx.getSource().sendSuccess(() -> Component.literal("Templo Karin construído em " + CX + ", " + CZ + " (" + n + " colunas de terreno)."), true);
                            return 1;
                        }))
                        .then(Commands.literal("ir").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.teleportTo(ctx.getSource().getServer().overworld(), CX + 0.5, CHAO + 1, CZ - 11.5, 0, 0);
                            return 1;
                        }))));
    }
}
