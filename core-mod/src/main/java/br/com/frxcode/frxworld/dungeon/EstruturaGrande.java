package br.com.frxcode.frxworld.dungeon;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

/**
 * Estrutura de jigsaw igual à vanilla, mas com profundidade até 30 (a vanilla aceita no máximo 7).
 * Usada pela Catacumba, que precisa de muitas peças em sequência para descer as camadas.
 */
public class EstruturaGrande extends Structure {
    public static final Codec<EstruturaGrande> CODEC = RecordCodecBuilder.<EstruturaGrande>mapCodec(i -> i.group(
            settingsCodec(i),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
            Codec.intRange(0, 30).fieldOf("size").forGetter(s -> s.size),
            HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
            Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter)
    ).apply(i, EstruturaGrande::new)).codec();

    private static final DeferredRegister<StructureType<?>> TIPOS = DeferredRegister.create(Registries.STRUCTURE_TYPE, FrxWorld.MOD_ID);
    public static final RegistryObject<StructureType<EstruturaGrande>> TIPO = TIPOS.register("jigsaw_grande", () -> () -> CODEC);

    public static void registrar(IEventBus modBus) {
        TIPOS.register(modBus);
    }

    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int size;
    private final HeightProvider startHeight;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;

    public EstruturaGrande(StructureSettings config, Holder<StructureTemplatePool> startPool, Optional<ResourceLocation> startJigsawName,
                           int size, HeightProvider startHeight, Optional<Heightmap.Types> projectStartToHeightmap, int maxDistanceFromCenter) {
        super(config);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.size = size;
        this.startHeight = startHeight;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int y = startHeight.sample(ctx.random(), new WorldGenerationContext(ctx.chunkGenerator(), ctx.heightAccessor()));
        ChunkPos chunk = ctx.chunkPos();
        BlockPos pos = new BlockPos(chunk.getMinBlockX(), y, chunk.getMinBlockZ());
        return JigsawPlacement.addPieces(ctx, startPool, startJigsawName, size, pos, false, projectStartToHeightmap, maxDistanceFromCenter);
    }

    @Override
    public StructureType<?> type() {
        return TIPO.get();
    }
}
