package br.com.frxcode.frxworld.blocos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.dungeon.BlocoNucleo;
import br.com.frxcode.frxworld.dungeon.BlocoPortao;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Blocos {
    private static final DeferredRegister<Block> BLOCOS = DeferredRegister.create(ForgeRegistries.BLOCKS, FrxWorld.MOD_ID);
    private static final DeferredRegister<Item> ITENS = DeferredRegister.create(ForgeRegistries.ITEMS, FrxWorld.MOD_ID);

    public static final RegistryObject<Block> QUADRO_MISSOES = BLOCOS.register("quadro_missoes",
            () -> new BlocoQuadro(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2.5f)));
    public static final RegistryObject<Item> QUADRO_MISSOES_ITEM = ITENS.register("quadro_missoes",
            () -> new BlockItem(QUADRO_MISSOES.get(), new Item.Properties()));

    public static final RegistryObject<Item> TOMO_ARCANO = ITENS.register("tomo_arcano",
            br.com.frxcode.frxworld.habilidades.ItemTomo::new);
    public static final RegistryObject<Item> CARTAO = ITENS.register("cartao_aventureiro",
            br.com.frxcode.frxworld.progressao.ItemCartao::new);

    public static final RegistryObject<Block> NUCLEO_DUNGEON = BLOCOS.register("nucleo_dungeon",
            () -> new BlocoNucleo(BlockBehaviour.Properties.copy(Blocks.BEDROCK).lightLevel(s -> 7)));
    public static final RegistryObject<Item> NUCLEO_DUNGEON_ITEM = ITENS.register("nucleo_dungeon",
            () -> new BlockItem(NUCLEO_DUNGEON.get(), new Item.Properties()));

    public static final RegistryObject<Block> PORTAO_SELADO = BLOCOS.register("portao_selado",
            () -> new BlocoPortao(BlockBehaviour.Properties.copy(Blocks.BEDROCK).lightLevel(s -> 4)));
    public static final RegistryObject<Item> PORTAO_SELADO_ITEM = ITENS.register("portao_selado",
            () -> new BlockItem(PORTAO_SELADO.get(), new Item.Properties()));
    /** Chave da Camada: guarda no NBT a camada e de qual catacumba veio. */
    public static final RegistryObject<Item> CHAVE_CAMADA = ITENS.register("chave_camada",
            () -> new Item(new Item.Properties().stacksTo(1)));

    private Blocos() {}

    public static void registrar(IEventBus modBus) {
        BLOCOS.register(modBus);
        ITENS.register(modBus);
        modBus.addListener(Blocos::abaCriativo);
    }

    private static void abaCriativo(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            e.accept(QUADRO_MISSOES_ITEM);
            e.accept(NUCLEO_DUNGEON_ITEM);
            e.accept(PORTAO_SELADO_ITEM);
        }
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(TOMO_ARCANO);
    }
}
