package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baús das capitais viram baús do Lootr (cada jogador abre o seu) com saque do nível do reino:
 * frxworld:chests/reino_1 … reino_5. Chunks novos: processador na hora de colar a fatia.
 * Chunks que já existiam: ao carregar, baús VAZIOS que vieram da fatia são convertidos (os com itens ficam como estão).
 */
public class BausReino {
    public BausReino() {}

    /** Nível do saque de cada capital (1 = cidade inicial … 5 = Lordes Demônios). */
    public static int nivel(String reino) {
        return switch (reino) {
            case "tempest" -> 1;
            case "leste-sakura", "oeste" -> 2;
            case "dwargon", "leste", "sarion" -> 3;
            case "lubelius" -> 4;
            case "demonios" -> 5;
            default -> 2;
        };
    }

    public static ResourceLocation tabela(String reino) {
        return new ResourceLocation("frxworld", "chests/reino_" + nivel(reino));
    }

    private static Block lootr(String nome) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("lootr", nome));
        return b == null || b == Blocks.AIR ? null : b;
    }

    /** Estado equivalente do Lootr (ou null se não for baú/barril ou o Lootr não estiver instalado). */
    static BlockState trocar(BlockState s) {
        Block alvo = null;
        if (s.is(Blocks.CHEST)) alvo = lootr("lootr_chest");
        else if (s.is(Blocks.TRAPPED_CHEST)) alvo = lootr("lootr_trapped_chest");
        else if (s.is(Blocks.BARREL)) alvo = lootr("lootr_barrel");
        if (alvo == null) return null;
        BlockState n = alvo.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && n.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            n = n.setValue(BlockStateProperties.HORIZONTAL_FACING, s.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (s.hasProperty(BlockStateProperties.FACING) && n.hasProperty(BlockStateProperties.FACING))
            n = n.setValue(BlockStateProperties.FACING, s.getValue(BlockStateProperties.FACING));
        if (s.hasProperty(BlockStateProperties.WATERLOGGED) && n.hasProperty(BlockStateProperties.WATERLOGGED))
            n = n.setValue(BlockStateProperties.WATERLOGGED, s.getValue(BlockStateProperties.WATERLOGGED));
        return n;
    }

    static boolean ehBau(BlockState s) {
        return s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST) || s.is(Blocks.BARREL);
    }

    /** Processador usado ao colar a fatia: baú/barril → versão do Lootr com a tabela do reino. */
    public static StructureProcessor processador(Reino r) {
        final String tabela = tabela(r.id()).toString();
        return new StructureProcessor() {
            @Override
            public StructureTemplate.StructureBlockInfo processBlock(net.minecraft.world.level.LevelReader nivel, BlockPos origem, BlockPos pivo,
                                                                     StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo info,
                                                                     StructurePlaceSettings cfg) {
                if (!ehBau(info.state())) return info;
                BlockState novo = trocar(info.state());
                CompoundTag nbt = new CompoundTag();
                nbt.putString("LootTable", tabela);
                nbt.putLong("LootTableSeed", cfg.getRandom(info.pos()).nextLong());
                return new StructureTemplate.StructureBlockInfo(info.pos(), novo != null ? novo : info.state(), nbt);
            }

            @Override
            protected StructureProcessorType<?> getType() {
                return StructureProcessorType.NOP;
            }
        };
    }

    // ------------------------------------------------------------------ chunks que já existiam

    private static final Set<String> VISTOS = ConcurrentHashMap.newKeySet();
    private static final ArrayDeque<long[]> FILA = new ArrayDeque<>();   // {reinoIdx, cx, cz}
    private static final Map<String, List<BlockPos>> CACHE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void aoCarregar(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel nivel) || nivel.dimension() != Level.OVERWORLD) return;
        ChunkPos cp = e.getChunk().getPos();
        List<Reino> todos = Reinos.todos();
        for (int i = 0; i < todos.size(); i++) {
            Reino r = todos.get(i);
            if (!r.contemChunk(cp.x, cp.z)) continue;
            if (VISTOS.add(r.id() + ":" + cp.x + ":" + cp.z)) synchronized (FILA) { FILA.add(new long[]{i, cp.x, cp.z}); }
            return;
        }
    }

    @SubscribeEvent
    public void aoTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null) return;
        ServerLevel nivel = e.getServer().overworld();
        List<Reino> todos = Reinos.todos();
        for (int n = 0; n < 4; n++) {
            long[] item;
            synchronized (FILA) { item = FILA.poll(); }
            if (item == null) return;
            if (item[0] >= todos.size()) continue;
            Reino r = todos.get((int) item[0]);
            ChunkPos cp = new ChunkPos((int) item[1], (int) item[2]);
            if (!nivel.hasChunk(cp.x, cp.z)) continue;
            List<BlockPos> baus = CACHE.computeIfAbsent(r.id() + ":" + cp.x + ":" + cp.z, k -> FeatureReinos.bausDaFatia(r, cp));
            for (BlockPos p : baus) converter(nivel, p, r, nivel.getRandom());
        }
    }

    private static void converter(ServerLevel nivel, BlockPos p, Reino r, RandomSource rnd) {
        BlockState s = nivel.getBlockState(p);
        if (!ehBau(s)) return;
        BlockEntity be = nivel.getBlockEntity(p);
        if (!(be instanceof RandomizableContainerBlockEntity rc) || !rc.isEmpty()) return;   // com itens = de algum jogador: não mexe
        BlockState novo = trocar(s);
        if (novo != null) {
            nivel.removeBlockEntity(p);
            nivel.setBlock(p, novo, 2);
            be = nivel.getBlockEntity(p);
        }
        if (be instanceof RandomizableContainerBlockEntity alvo) {
            alvo.setLootTable(tabela(r.id()), rnd.nextLong());
            alvo.setChanged();
        }
    }
}
