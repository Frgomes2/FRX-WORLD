package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Floresta Sombria: cinturão de mata fechada em volta de uma capital (padrão Tempest).
 * Worldgen (chamado pelo FeatureReinos em chunks novos): árvores gigantes de carvalho escuro e pinheiros altos,
 * copa fechada (escuro de dia), sub-bosque, teias, cogumelos e 4 trilhas estreitas nos eixos da capital.
 * No jogo: monstros sombrios aparecem perto de quem está dentro, efeito de escuridão, e só dá para passar a pé
 * (élitro e montaria são cortados). Config na aba Progressão do painel → "Floresta sombria" (seção "floresta").
 */
public class FlorestaSombria {
    public static final String TAG = "frx_floresta";
    private static final Set<UUID> DENTRO = new HashSet<>();
    private static int contador = 0;

    // ------------------------------------------------------------------ config (painel) com padrões

    private static JsonObject cfg() {
        JsonObject o = ConfigJogo.secaoCrua("floresta");
        return o == null ? new JsonObject() : o;
    }

    private static boolean bool(String k, boolean p) { try { return cfg().has(k) ? cfg().get(k).getAsBoolean() : p; } catch (Exception e) { return p; } }

    private static int num(String k, int p) { try { return cfg().has(k) ? cfg().get(k).getAsInt() : p; } catch (Exception e) { return p; } }

    private static String txt(String k, String p) { try { return cfg().has(k) ? cfg().get(k).getAsString() : p; } catch (Exception e) { return p; } }

    private static final String[] FRACOS = {"minecraft:zombie", "minecraft:spider", "minecraft:cave_spider", "graveyard:ghoul",
            "born_in_chaos_v1:decaying_zombie", "born_in_chaos_v1:decrepit_skeleton", "born_in_chaos_v1:restless_spirit", "mowziesmobs:foliaath"};
    private static final String[] FORTES = {"graveyard:revenant", "graveyard:wraith", "born_in_chaos_v1:dread_hound",
            "born_in_chaos_v1:nightmare_stalker", "born_in_chaos_v1:skeleton_thrasher", "minecraft:witch", "graveyard:nightmare"};

    private static List<String> lista(String k, String[] padrao) {
        try {
            if (cfg().has(k)) {
                JsonArray a = cfg().getAsJsonArray(k);
                List<String> l = new java.util.ArrayList<>();
                a.forEach(e -> l.add(e.getAsString()));
                if (!l.isEmpty()) return l;
            }
        } catch (Exception ignorado) {}
        return List.of(padrao);
    }

    public static boolean ativa() { return bool("ativo", true); }

    public static Reino reino() {
        String id = txt("reino", "tempest");
        for (Reino r : Reinos.todos()) if (r.id().equals(id) && !r.subterraneo()) return r;
        return null;
    }

    /** 0 = fora; 1..largura = profundidade dentro do cinturão. */
    public static int profundidade(Reino r, int x, int z) {
        int d = r.distancia(x, z), ini = num("inicio", 40), larg = num("largura", 220);
        if (d < ini || d > ini + larg) return 0;
        return d - ini + 1;
    }

    // ------------------------------------------------------------------ worldgen

    private static final BlockState AR = Blocks.AIR.defaultBlockState();

    /** Trilha: faixa estreita e sinuosa nos eixos da capital (norte, sul, leste, oeste). */
    private static boolean trilha(Reino r, int x, int z, int folga) {
        int cx = r.centroX(), cz = r.centroZ();
        double ox = 7 * Math.sin(z / 23.0) + 4 * Math.sin(z / 9.0);
        double oz = 7 * Math.sin(x / 21.0) + 4 * Math.sin(x / 8.0);
        return Math.abs(x - cx - ox) <= 1 + folga || Math.abs(z - cz - oz) <= 1 + folga;
    }

    private static boolean livre(BlockState b) {
        return b.isAir() || b.is(net.minecraft.tags.BlockTags.LEAVES) || b.canBeReplaced() || b.is(Blocks.VINE);
    }

    private static boolean chaoBom(BlockState b) {
        return b.is(Blocks.GRASS_BLOCK) || b.is(Blocks.DIRT) || b.is(Blocks.PODZOL) || b.is(Blocks.COARSE_DIRT) || b.is(Blocks.MOSS_BLOCK)
                || b.is(Blocks.ROOTED_DIRT) || b.is(Blocks.MYCELIUM) || b.is(Blocks.MUD) || b.is(Blocks.SNOW_BLOCK);
    }

    private static void por(WorldGenLevel n, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState b, boolean forcar) {
        m.set(x, y, z);
        if (!n.ensureCanWrite(m)) return;
        if (forcar || livre(n.getBlockState(m))) n.setBlock(m, b, 2);
    }

    public static boolean gerar(WorldGenLevel n, ChunkPos cp, Reino r, RandomSource rnd) {
        if (!ativa()) return false;
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        int larg = num("largura", 220);
        boolean algum = false;
        int[] solo = new int[256];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // chão
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx, z = z0 + lz;
            solo[lz * 16 + lx] = Integer.MIN_VALUE;
            if (profundidade(r, x, z) == 0) continue;
            int y = n.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
            if (n.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1 > y) continue;   // água
            BlockState b = n.getBlockState(m.set(x, y, z));
            if (!chaoBom(b)) continue;
            solo[lz * 16 + lx] = y;
            algum = true;
            if (trilha(r, x, z, 0)) {
                n.setBlock(m, rnd.nextInt(4) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState(), 2);
                for (int k = 1; k <= 4; k++) { m.set(x, y + k, z); if (!n.getBlockState(m).isAir() && livre(n.getBlockState(m))) n.setBlock(m, AR, 2); }
                continue;
            }
            int t = rnd.nextInt(100);
            BlockState novo = t < 40 ? Blocks.PODZOL.defaultBlockState() : t < 60 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : t < 78 ? Blocks.MOSS_BLOCK.defaultBlockState() : t < 86 ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            n.setBlock(m, novo, 2);
        }
        if (!algum) return false;

        // árvores (troncos dentro deste chunk; copas podem passar para os vizinhos)
        int arvores = 7 + rnd.nextInt(5);
        for (int i = 0; i < arvores; i++) {
            int lx = 1 + rnd.nextInt(14), lz = 1 + rnd.nextInt(14);
            int y = solo[lz * 16 + lx];
            int x = x0 + lx, z = z0 + lz;
            if (y == Integer.MIN_VALUE || trilha(r, x, z, 3)) continue;
            int prof = profundidade(r, x, z);
            if ((prof < 12 || prof > larg - 12) && rnd.nextInt(3) == 0) continue;   // bordas menos densas
            if (rnd.nextInt(3) == 0) pinheiro(n, m, x, y + 1, z, rnd);
            else carvalhoGigante(n, m, x, y + 1, z, rnd);
        }
        // sub-bosque
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int y = solo[lz * 16 + lx];
            int x = x0 + lx, z = z0 + lz;
            if (y == Integer.MIN_VALUE || trilha(r, x, z, 0)) continue;
            m.set(x, y + 1, z);
            if (!n.getBlockState(m).isAir()) continue;
            int t = rnd.nextInt(100);
            BlockState b = null;
            if (t < 14) b = Blocks.FERN.defaultBlockState();
            else if (t < 20) b = Blocks.MOSS_CARPET.defaultBlockState();
            else if (t < 24) b = Blocks.BROWN_MUSHROOM.defaultBlockState();
            else if (t < 26) b = Blocks.RED_MUSHROOM.defaultBlockState();
            else if (t < 28) b = Blocks.DEAD_BUSH.defaultBlockState();
            else if (t < 29) b = Blocks.COBWEB.defaultBlockState();
            else if (t < 31) b = Blocks.SWEET_BERRY_BUSH.defaultBlockState();
            else if (t < 38) {   // arbusto de folhas
                BlockState f = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                n.setBlock(m, f, 2);
                if (rnd.nextBoolean()) por(n, m, x, y + 2, z, f, false);
                continue;
            } else if (t < 41 && n.getBlockState(m.set(x, y + 2, z)).isAir()) {
                n.setBlock(m.set(x, y + 1, z), Blocks.LARGE_FERN.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 2);
                n.setBlock(m.set(x, y + 2, z), Blocks.LARGE_FERN.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
                continue;
            }
            if (b != null) n.setBlock(m.set(x, y + 1, z), b, 2);
        }
        return true;
    }

    private static void carvalhoGigante(WorldGenLevel n, BlockPos.MutableBlockPos m, int x, int y, int z, RandomSource rnd) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState folha = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int h = 12 + rnd.nextInt(9);
        for (int k = -1; k < h; k++)
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) por(n, m, x + dx, y + k, z + dz, log, true);
        // raízes
        for (int[] d : new int[][]{{-1, 0}, {2, 1}, {0, -1}, {1, 2}})
            if (rnd.nextBoolean()) { por(n, m, x + d[0], y, z + d[1], log, false); if (rnd.nextBoolean()) por(n, m, x + d[0], y + 1, z + d[1], log, false); }
        // galhos
        for (int g = 0; g < 3; g++) {
            int gy = y + h - 4 - rnd.nextInt(5);
            int dx = rnd.nextInt(3) - 1, dz = rnd.nextInt(3) - 1;
            if (dx == 0 && dz == 0) dx = 1;
            for (int k = 1; k <= 3; k++) por(n, m, x + dx * (k + 1), gy + k / 2, z + dz * (k + 1),
                    log.setValue(RotatedPillarBlock.AXIS, dx != 0 ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z), false);
        }
        // copa larga e baixa
        int topo = y + h;
        int raio = 5 + rnd.nextInt(3);
        for (int dy = -4; dy <= 2; dy++) {
            int rr = dy <= 0 ? raio - (dy < -2 ? 2 : 0) : raio - 2 * dy;
            for (int dx = -rr; dx <= rr + 1; dx++) for (int dz = -rr; dz <= rr + 1; dz++) {
                double ex = dx - .5, ez = dz - .5;
                if (ex * ex + ez * ez > rr * rr + 2 || rnd.nextInt(10) == 0) continue;
                por(n, m, x + dx, topo + dy, z + dz, folha, false);
                // cipós pendurados na borda
                if (dy == -4 && ex * ex + ez * ez > (rr - 1) * (rr - 1) && rnd.nextInt(4) == 0) cipo(n, m, x + dx, topo + dy - 1, z + dz, dx, dz, rnd);
            }
        }
    }

    private static void cipo(WorldGenLevel n, BlockPos.MutableBlockPos m, int x, int y, int z, int dx, int dz, RandomSource rnd) {
        m.set(x, y, z);
        if (n.ensureCanWrite(m) && n.getBlockState(m).isAir()) n.setBlock(m, Blocks.VINE.defaultBlockState().setValue(VineBlock.UP, true), 2);
    }

    private static void pinheiro(WorldGenLevel n, BlockPos.MutableBlockPos m, int x, int y, int z, RandomSource rnd) {
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState folha = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int h = 18 + rnd.nextInt(10);
        for (int k = -1; k < h; k++) por(n, m, x, y + k, z, log, true);
        for (int k = 5; k <= h + 1; k++) {
            int rr = Math.max(0, (h + 1 - k) / 3 + (k % 2 == 0 ? 1 : 0));
            rr = Math.min(rr, 4);
            for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++)
                if (Math.abs(dx) + Math.abs(dz) <= rr + 1 && !(dx == 0 && dz == 0 && k < h)) por(n, m, x + dx, y + k, z + dz, folha, false);
        }
    }

    // ------------------------------------------------------------------ no jogo

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++contador % 20 != 0 || !ativa()) return;
        if (e.getServer() == null) return;
        ServerLevel nivel = e.getServer().overworld();
        Reino r = reino();
        if (r == null) return;
        int seg = contador / 20;
        for (ServerPlayer p : nivel.players()) {
            if (p.isSpectator()) continue;
            int prof = profundidade(r, p.getBlockX(), p.getBlockZ());
            boolean dentro = prof > 0;
            if (dentro && DENTRO.add(p.getUUID())) entrou(p);
            else if (!dentro && DENTRO.remove(p.getUUID())) p.displayClientMessage(Component.literal("Você saiu da Floresta Sombria.").withStyle(ChatFormatting.GRAY), true);
            if (!dentro || p.isCreative()) continue;

            if (bool("soAPe", true)) {
                if (p.isFallFlying()) { p.stopFallFlying(); p.displayClientMessage(Component.literal("Os galhos fechados não deixam voar aqui.").withStyle(ChatFormatting.DARK_RED), true); }
                if (p.getVehicle() != null) { p.stopRiding(); p.displayClientMessage(Component.literal("A mata é fechada demais para montaria. Siga a pé.").withStyle(ChatFormatting.DARK_RED), true); }
            }
            if (bool("escuridao", true) && seg % 4 == 0)
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, true, false, false));
            if (seg % Math.max(2, num("intervaloSeg", 5)) == 0) povoar(nivel, r, p, prof);
        }
    }

    private void entrou(ServerPlayer p) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Floresta Sombria").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Só se atravessa a pé. Não se perca das trilhas.").withStyle(ChatFormatting.GRAY)));
    }

    private void povoar(ServerLevel nivel, Reino r, ServerPlayer p, int prof) {
        int max = num("maxMonstros", 10);
        AABB caixa = p.getBoundingBox().inflate(40);
        int perto = nivel.getEntitiesOfClass(Mob.class, caixa, m -> m.getTags().contains(TAG) && m.isAlive()).size();
        if (perto >= max) return;
        RandomSource rnd = p.getRandom();
        int larg = num("largura", 220);
        boolean fundo = prof > larg / 3;   // do primeiro terço para dentro: mais fortes
        List<String> fracos = lista("monstros1", FRACOS), fortes = lista("monstros2", FORTES);
        int quantos = 1 + rnd.nextInt(fundo ? 3 : 2);
        for (int i = 0; i < quantos && perto < max; i++) {
            String id = (fundo && rnd.nextInt(100) < num("chanceForte", 35)) ? fortes.get(rnd.nextInt(fortes.size())) : fracos.get(rnd.nextInt(fracos.size()));
            EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
            if (tipo == null) continue;
            BlockPos pos = lugar(nivel, r, p, rnd);
            if (pos == null) continue;
            Entity ent = tipo.create(nivel);
            if (!(ent instanceof Mob mob)) { if (ent != null) ent.discard(); continue; }
            mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, rnd.nextFloat() * 360f, 0);
            try { mob.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null); } catch (Exception ignorado) {}
            mob.addTag(TAG);
            nivel.addFreshEntity(mob);
            perto++;
        }
    }

    private BlockPos lugar(ServerLevel nivel, Reino r, ServerPlayer p, RandomSource rnd) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int t = 0; t < 10; t++) {
            double ang = rnd.nextDouble() * Math.PI * 2, dist = 14 + rnd.nextInt(18);
            int x = p.getBlockX() + (int) (Math.cos(ang) * dist), z = p.getBlockZ() + (int) (Math.sin(ang) * dist);
            if (profundidade(r, x, z) == 0 || !nivel.hasChunkAt(m.set(x, 0, z))) continue;
            // procura chão perto da altura do jogador (debaixo da copa)
            for (int dy = 6; dy >= -8; dy--) {
                int y = p.getBlockY() + dy;
                m.set(x, y, z);
                if (nivel.getBlockState(m).isAir() && nivel.getBlockState(m.above()).isAir()
                        && nivel.getBlockState(m.below()).isSolidRender(nivel, m.below())
                        && nivel.getFluidState(m).isEmpty()) return m.immutable();
            }
        }
        return null;
    }

    static {
        FrxWorld.LOG.debug("Floresta Sombria carregada");
    }
}
