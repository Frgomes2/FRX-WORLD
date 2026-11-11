package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.File;
import java.util.Arrays;

/** Cola, chunk a chunk, as capitais dos reinos e suaviza o terreno em volta. */
public class FeatureReinos extends Feature<NoneFeatureConfiguration> {
    private static final int MARGEM = 32;
    private static final BlockState AR = Blocks.AIR.defaultBlockState(), GRAMA = Blocks.GRASS_BLOCK.defaultBlockState(),
            TERRA = Blocks.DIRT.defaultBlockState(), PEDRA = Blocks.STONE.defaultBlockState();

    public FeatureReinos(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel nivel = ctx.level();
        if (nivel.getLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        ChunkPos cp = new ChunkPos(ctx.origin());
        boolean fez = false;
        for (Reino r : Reinos.todos()) {
            try {
                if (r.subterraneo()) {
                    if (r.contemChunk(cp.x, cp.z)) fez |= subterraneo(nivel, cp, r, ctx);
                } else if (r.contemChunk(cp.x, cp.z)) {
                    fez |= superficie(nivel, cp, r, ctx);
                } else if (r.distancia(cp.getMinBlockX() + 8, cp.getMinBlockZ() + 8) <= MARGEM + 12) {
                    fez |= margem(nivel, cp, r);
                }
            } catch (Exception e) {
                FrxWorld.LOG.warn("Reino {}: falha no chunk {}: {}", r.id(), cp, e.toString());
            }
        }
        try {   // Floresta Sombria em volta da capital configurada (padrão: Tempest)
            Reino fr = FlorestaSombria.reino();
            if (fr != null && FlorestaSombria.ativa() && !fr.contemChunk(cp.x, cp.z)) {
                int d = fr.distancia(cp.getMinBlockX() + 8, cp.getMinBlockZ() + 8);
                if (d >= 20 && d <= 600) fez |= FlorestaSombria.gerar(nivel, cp, fr, ctx.random());
            }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Floresta: falha no chunk {}: {}", cp, e.toString());
        }
        return fez;
    }

    // ------------------------------------------------------------------ fatias

    private record Fatia(StructureTemplate modelo, int[] topo) {}

    /**
     * As árvores decorativas dos mapas vêm com folhas "naturais" (persistent=false) longe de troncos:
     * no jogo elas apodrecem e sobram galhos flutuando. Aqui toda folha colada vira persistente.
     */
    private static final net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor FOLHAS_FIXAS =
            new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor() {
                @Override
                public StructureTemplate.StructureBlockInfo processBlock(net.minecraft.world.level.LevelReader nivel, BlockPos origem, BlockPos pivo,
                                                                         StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo info,
                                                                         StructurePlaceSettings cfg) {
                    BlockState b = info.state();
                    if (b.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT) && !b.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT))
                        return new StructureTemplate.StructureBlockInfo(info.pos(), b.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), info.nbt());
                    return info;
                }

                @Override
                protected net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?> getType() {
                    return net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.NOP;
                }
            };

    /** Topo (y mais alto da construção) de cada coluna das fatias já lidas; MIN_VALUE = coluna sem construção. */
    private static final java.util.Map<String, int[]> TOPOS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int[] SEM_FATIA = new int[0];

    private static int[] topoDe(Reino r, ChunkPos cp) {
        String k = r.id() + ":" + cp.x + ":" + cp.z;
        int[] t = TOPOS.get(k);
        if (t != null) return t;
        try {
            Fatia f = carregar(r, cp);
            t = f == null ? SEM_FATIA : f.topo();
        } catch (Exception e) {
            t = SEM_FATIA;
        }
        TOPOS.put(k, t);
        return t;
    }

    private static boolean deArvore(BlockState b) {
        return b.is(net.minecraft.tags.BlockTags.LOGS) || b.is(net.minecraft.tags.BlockTags.LEAVES) || b.is(Blocks.VINE)
                || b.is(Blocks.BEE_NEST) || b.is(Blocks.COCOA) || b.is(Blocks.MUSHROOM_STEM) || b.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || b.is(Blocks.RED_MUSHROOM_BLOCK) || b.is(Blocks.MANGROVE_ROOTS) || b.is(Blocks.MOSS_CARPET) || b.is(Blocks.SNOW);
    }

    /**
     * Árvores dos chunks vizinhos são geradas depois e invadem a capital: tira tronco/folha que esteja
     * acima da construção em todas as colunas da capital ao alcance deste chunk (3x3).
     */
    private static void limparArvores(WorldGenLevel nivel, ChunkPos centro, Reino r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int teto = nivel.getMaxBuildHeight() - 1;
        for (int dcx = -1; dcx <= 1; dcx++) for (int dcz = -1; dcz <= 1; dcz++) {
            ChunkPos cp = new ChunkPos(centro.x + dcx, centro.z + dcz);
            if (!r.contemChunk(cp.x, cp.z)) continue;
            int[] topo = topoDe(r, cp);
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                int base = topo.length == 0 || topo[lz * 16 + lx] == Integer.MIN_VALUE ? r.chao() : topo[lz * 16 + lx];
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                int ate = Math.min(teto, base + 40);
                for (int y = base + 1; y <= ate; y++) {
                    m.set(x, y, z);
                    if (deArvore(nivel.getBlockState(m))) nivel.setBlock(m, AR, 2);
                }
            }
        }
    }

    private static Fatia carregar(Reino r, ChunkPos cp) throws Exception {
        int ox = cp.x - r.chunkX0(), oz = cp.z - r.chunkZ0();
        if (!r.fatias().contains(Reino.chave(ox, oz))) return null;
        File arq = r.pasta().resolve(ox + "_" + oz + ".nbt").toFile();
        if (!arq.exists()) return null;
        CompoundTag tag = NbtIo.readCompressed(arq);
        StructureTemplate t = new StructureTemplate();
        t.load(BuiltInRegistries.BLOCK.asLookup(), tag);
        ListTag paleta = tag.getList("palette", Tag.TAG_COMPOUND);
        boolean[] ar = new boolean[paleta.size()];
        for (int i = 0; i < paleta.size(); i++) ar[i] = paleta.getCompound(i).getString("Name").endsWith(":air");
        int[] topo = new int[256];
        Arrays.fill(topo, Integer.MIN_VALUE);
        ListTag blocos = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocos.size(); i++) {
            CompoundTag b = blocos.getCompound(i);
            int s = b.getInt("state");
            if (s < ar.length && ar[s]) continue;
            ListTag p = b.getList("pos", Tag.TAG_INT);
            int x = p.getInt(0), y = p.getInt(1) + r.yBase(), z = p.getInt(2);
            if (x < 0 || x > 15 || z < 0 || z > 15) continue;
            topo[z * 16 + x] = Math.max(topo[z * 16 + x], y);
        }
        return new Fatia(t, topo);
    }

    /** Posições absolutas dos baús/barris que a fatia deste chunk tem (para converter chunks gerados antes). */
    static java.util.List<BlockPos> bausDaFatia(Reino r, ChunkPos cp) {
        java.util.List<BlockPos> lista = new java.util.ArrayList<>();
        try {
            int ox = cp.x - r.chunkX0(), oz = cp.z - r.chunkZ0();
            if (!r.fatias().contains(Reino.chave(ox, oz))) return lista;
            File arq = r.pasta().resolve(ox + "_" + oz + ".nbt").toFile();
            if (!arq.exists()) return lista;
            CompoundTag tag = NbtIo.readCompressed(arq);
            ListTag paleta = tag.getList("palette", Tag.TAG_COMPOUND);
            boolean[] bau = new boolean[paleta.size()];
            for (int i = 0; i < paleta.size(); i++) {
                String n = paleta.getCompound(i).getString("Name");
                bau[i] = n.equals("minecraft:chest") || n.equals("minecraft:trapped_chest") || n.equals("minecraft:barrel");
            }
            ListTag blocos = tag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocos.size(); i++) {
                CompoundTag b = blocos.getCompound(i);
                int s = b.getInt("state");
                if (s >= bau.length || !bau[s]) continue;
                ListTag p = b.getList("pos", Tag.TAG_INT);
                lista.add(new BlockPos(cp.getMinBlockX() + p.getInt(0), r.yBase() + p.getInt(1), cp.getMinBlockZ() + p.getInt(2)));
            }
        } catch (Exception ignorado) {}
        return lista;
    }

    private static void colar(WorldGenLevel nivel, ChunkPos cp, Reino r, Fatia f, FeaturePlaceContext<?> ctx) {
        BlockPos base = new BlockPos(cp.getMinBlockX(), r.yBase(), cp.getMinBlockZ());
        StructurePlaceSettings cfg = new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true).addProcessor(FOLHAS_FIXAS)
                .addProcessor(BausReino.processador(r));
        f.modelo().placeInWorld(nivel, base, base, cfg, ctx.random(), 2);
    }

    // ------------------------------------------------------------------ superfície

    private boolean superficie(WorldGenLevel nivel, ChunkPos cp, Reino r, FeaturePlaceContext<?> ctx) throws Exception {
        Fatia f = carregar(r, cp);
        int[] alturaNatural = new int[256];
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
            int topo = nivel.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            alturaNatural[lz * 16 + lx] = topo;
            if (f == null || f.topo()[lz * 16 + lx] == Integer.MIN_VALUE)
                ajustar(nivel, x, z, topo, nivel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1, r.chao());
        }
        if (f == null) { limparArvores(nivel, cp, r); return true; }
        colar(nivel, cp, r, f, ctx);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int t = f.topo()[lz * 16 + lx];
            if (t == Integer.MIN_VALUE) continue;
            int ate = Math.max(alturaNatural[lz * 16 + lx], r.chao() + 2);
            for (int y = t + 1; y <= ate; y++) {
                m.set(cp.getMinBlockX() + lx, y, cp.getMinBlockZ() + lz);
                if (!nivel.getBlockState(m).isAir()) nivel.setBlock(m, AR, 2);
            }
        }
        limparArvores(nivel, cp, r);
        return true;
    }

    /** Levanta ou rebaixa o chão de uma coluna até a altura alvo (grama em cima, terra e pedra embaixo). */
    private static void ajustar(WorldGenLevel nivel, int x, int z, int topo, int fundo, int alvo) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = topo; y > alvo; y--) nivel.setBlock(m.set(x, y, z), AR, 2);
        if (fundo < alvo) {
            for (int y = fundo + 1; y < alvo; y++) nivel.setBlock(m.set(x, y, z), y < alvo - 3 ? PEDRA : TERRA, 2);
            nivel.setBlock(m.set(x, alvo, z), GRAMA, 2);
        } else if (topo > alvo) {
            BlockState no = nivel.getBlockState(m.set(x, alvo, z));
            if (no.is(Blocks.DIRT) || no.is(Blocks.STONE) || no.isAir()) nivel.setBlock(m, GRAMA, 2);
        }
    }

    private boolean margem(WorldGenLevel nivel, ChunkPos cp, Reino r) {
        boolean fez = false;
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
            int d = r.distancia(x, z);
            if (d <= 0 || d > MARGEM) continue;
            int topo = nivel.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            int fundo = nivel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
            double t = (double) d / MARGEM;
            double s = t * t * (3 - 2 * t);
            int natural = fundo;
            int alvo = (int) Math.round(r.chao() + (natural - r.chao()) * s);
            if (alvo == fundo && topo == fundo) continue;
            ajustar(nivel, x, z, topo, fundo, alvo);
            fez = true;
        }
        if (!r.subterraneo()) limparArvores(nivel, cp, r);
        return fez;
    }

    // ------------------------------------------------------------------ subterrâneo (Dwargon)

    private boolean subterraneo(WorldGenLevel nivel, ChunkPos cp, Reino r, FeaturePlaceContext<?> ctx) throws Exception {
        Fatia f = carregar(r, cp);
        if (f != null) colar(nivel, cp, r, f, ctx);
        // poço de entrada no centro da caverna (entrada relativa gravada no manifesto)
        int[] e = Entradas.de(r);
        if (e == null) return f != null;
        int ex = (r.chunkX0() << 4) + e[0], ey = e[1], ez = (r.chunkZ0() << 4) + e[2];
        if (ex >> 4 != cp.x || ez >> 4 != cp.z) return f != null;
        int topo = nivel.getHeight(Heightmap.Types.WORLD_SURFACE_WG, ex, ez);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState parede = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), borda = Blocks.STONE_BRICKS.defaultBlockState();
        for (int y = ey; y <= topo + 3; y++) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean anel = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                m.set(ex + dx, y, ez + dz);
                if (!anel) nivel.setBlock(m, AR, 2);
                else if (y >= ey + 4 && y < topo) nivel.setBlock(m, parede, 2);
                else if (y == topo) nivel.setBlock(m, borda, 2);
                else if (y > topo) nivel.setBlock(m, AR, 2);
            }
            if (y >= ey && y < topo) nivel.setBlock(m.set(ex, y, ez - 1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 2);
            if (y > ey + 4 && y < topo && (y - ey) % 8 == 0)
                nivel.setBlock(m.set(ex + 1, y, ez + 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 2);
        }
        // portal de pedra na superfície
        for (int dx = -2; dx <= 2; dx += 4) for (int dz = -2; dz <= 2; dz += 4) {
            for (int y = topo + 1; y <= topo + 3; y++) nivel.setBlock(m.set(ex + dx, y, ez + dz), borda, 2);
            nivel.setBlock(m.set(ex + dx, topo + 4, ez + dz), Blocks.LANTERN.defaultBlockState(), 2);
        }
        // a escada precisa de parede atrás: garante a parede norte em todo o poço
        for (int y = ey; y < topo; y++) {
            m.set(ex, y, ez - 2);
            if (nivel.getBlockState(m).isAir()) nivel.setBlock(m, parede, 2);
        }
        return true;
    }
}
