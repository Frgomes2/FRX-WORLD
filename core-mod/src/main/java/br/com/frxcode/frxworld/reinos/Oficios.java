package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Casinhas dos NPCs principais das capitais: cada ofício ganha uma barraca 5x5 com balcão e móveis do tema
 * (forja do ferreiro, bancada do armeiro, barraca de vendas do mercador...). O NPC fica parado dentro dela,
 * de frente para o balcão, então é sempre fácil de achar. As barracas não podem ser quebradas (só em criativo).
 * Registro das caixas em <mundo>/frx_oficios.txt.
 */
public class Oficios {

    /** chão, poste, parede, telhado, balcão (3, da esquerda p/ direita), fundo (3), luz. */
    private record Tema(String chao, String poste, String parede, String telhado, String[] balcao, String[] fundo, String luz) {}

    private static final Map<String, Tema> TEMAS = new java.util.HashMap<>(Map.of(
            "ferreiro", new Tema("minecraft:polished_andesite", "minecraft:stripped_dark_oak_log", "minecraft:cobblestone", "minecraft:deepslate_tiles",
                    new String[]{"minecraft:grindstone", "minecraft:anvil", "minecraft:grindstone"},
                    new String[]{"minecraft:blast_furnace", "minecraft:lava_cauldron", "minecraft:blast_furnace"}, "minecraft:lantern"),
            "armeiro", new Tema("minecraft:stone_bricks", "minecraft:stripped_spruce_log", "minecraft:bricks", "minecraft:gray_wool",
                    new String[]{"minecraft:smithing_table", "minecraft:chipped_anvil", "minecraft:smithing_table"},
                    new String[]{"minecraft:furnace", "minecraft:blast_furnace", "minecraft:furnace"}, "minecraft:lantern"),
            "mercador", new Tema("minecraft:spruce_planks", "minecraft:stripped_oak_log", "minecraft:oak_planks", "minecraft:yellow_wool",
                    new String[]{"minecraft:barrel", "minecraft:spruce_slab", "minecraft:barrel"},
                    new String[]{"minecraft:hay_block", "minecraft:barrel", "minecraft:melon"}, "minecraft:lantern"),
            "comprador", new Tema("minecraft:oak_planks", "minecraft:stripped_birch_log", "minecraft:birch_planks", "minecraft:lime_wool",
                    new String[]{"minecraft:barrel", "minecraft:birch_slab", "minecraft:barrel"},
                    new String[]{"minecraft:composter", "minecraft:barrel", "minecraft:composter"}, "minecraft:lantern"),
            "banqueiro", new Tema("minecraft:polished_blackstone", "minecraft:stripped_dark_oak_log", "minecraft:polished_blackstone_bricks", "minecraft:black_wool",
                    new String[]{"minecraft:iron_bars", "minecraft:polished_blackstone_slab", "minecraft:iron_bars"},
                    new String[]{"minecraft:chiseled_polished_blackstone", "minecraft:lectern", "minecraft:chiseled_polished_blackstone"}, "minecraft:soul_lantern"),
            "sacerdote", new Tema("minecraft:smooth_quartz", "minecraft:quartz_pillar", "minecraft:quartz_bricks", "minecraft:white_wool",
                    new String[]{"minecraft:quartz_slab", "minecraft:brewing_stand", "minecraft:quartz_slab"},
                    new String[]{"minecraft:chiseled_quartz_block", "minecraft:amethyst_block", "minecraft:chiseled_quartz_block"}, "minecraft:lantern"),
            "sabio", new Tema("minecraft:dark_oak_planks", "minecraft:stripped_dark_oak_log", "minecraft:dark_oak_planks", "minecraft:blue_wool",
                    new String[]{"minecraft:bookshelf", "minecraft:lectern", "minecraft:bookshelf"},
                    new String[]{"minecraft:bookshelf", "minecraft:chiseled_bookshelf", "minecraft:bookshelf"}, "minecraft:lantern"),
            "cartografo", new Tema("minecraft:birch_planks", "minecraft:stripped_birch_log", "minecraft:birch_planks", "minecraft:light_blue_wool",
                    new String[]{"minecraft:birch_slab", "minecraft:cartography_table", "minecraft:birch_slab"},
                    new String[]{"minecraft:bookshelf", "minecraft:cartography_table", "minecraft:bookshelf"}, "minecraft:lantern"),
            "taverneiro", new Tema("minecraft:spruce_planks", "minecraft:stripped_spruce_log", "minecraft:spruce_planks", "minecraft:brown_wool",
                    new String[]{"minecraft:barrel", "minecraft:spruce_slab", "minecraft:barrel"},
                    new String[]{"minecraft:barrel", "minecraft:smoker", "minecraft:barrel"}, "minecraft:lantern"),
            "cambista", new Tema("minecraft:polished_granite", "minecraft:stripped_acacia_log", "minecraft:smooth_sandstone", "minecraft:orange_wool",
                    new String[]{"minecraft:iron_bars", "minecraft:acacia_slab", "minecraft:iron_bars"},
                    new String[]{"minecraft:barrel", "minecraft:lectern", "minecraft:barrel"}, "minecraft:lantern")));
    static {
        TEMAS.put("recepcionista", new Tema("minecraft:dark_oak_planks", "minecraft:stripped_dark_oak_log", "minecraft:dark_oak_planks", "minecraft:blue_wool",
                new String[]{"minecraft:dark_oak_slab", "minecraft:lectern", "minecraft:dark_oak_slab"},
                new String[]{"minecraft:bookshelf", "minecraft:chiseled_bookshelf", "minecraft:bookshelf"}, "minecraft:lantern"));
        TEMAS.put("cozinheiro", new Tema("minecraft:bricks", "minecraft:stripped_oak_log", "minecraft:bricks", "minecraft:red_wool",
                new String[]{"minecraft:barrel", "minecraft:oak_slab", "minecraft:hay_block"},
                new String[]{"minecraft:smoker", "minecraft:cauldron", "minecraft:smoker"}, "minecraft:lantern"));
    }

    /** Ofícios com casinha, na ordem em que ocupam o anel em volta da praça. */
    public static final List<String> ORDEM = List.of("mercador", "cambista", "comprador", "ferreiro", "armeiro", "banqueiro",
            "sacerdote", "sabio", "cartografo", "taverneiro", "cozinheiro", "recepcionista");
    /** Ângulos do anel (graus; 0 = leste, 90 = sul): 3 por quadrante, longe das 4 ruas que saem da praça. */
    private static final int[] ANGULOS = {18, 45, 72, 108, 135, 162, 198, 225, 252, 288, 315, 342};
    public static final int RAIO = 14;

    public static boolean temCasa(String papel) { return TEMAS.containsKey(papel); }

    /** Lugar do NPC no anel em volta do centro c. */
    public static int[] noAnel(BlockPos c, String papel) {
        double ang = Math.toRadians(ANGULOS[Math.max(0, ORDEM.indexOf(papel)) % ANGULOS.length]);
        return new int[]{c.getX() + (int) Math.round(Math.cos(ang) * RAIO), c.getZ() + (int) Math.round(Math.sin(ang) * RAIO)};
    }

    /** Lado aberto da barraca: virado para o centro da praça. */
    public static Direction paraCentro(BlockPos de, BlockPos centro) {
        int dx = centro.getX() - de.getX(), dz = centro.getZ() - de.getZ();
        if (dx == 0 && dz == 0) return Direction.SOUTH;
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    // ------------------------------------------------------------------ construção

    private static BlockState bloco(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(id));
        return (b == null ? Blocks.STONE : b).defaultBlockState();
    }

    private static BlockState virado(BlockState s, Direction frente) {
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, frente);
        if (s.hasProperty(BlockStateProperties.FACING)) return s.setValue(BlockStateProperties.FACING, frente);
        return s;
    }

    /**
     * Monta a casinha do ofício com o NPC em `npc` (pés), aberta para `frente`. Coordenadas locais:
     * lx -2..2 (largura), lz 0..4 (0 = lado aberto, 4 = fundo); o NPC fica em lz 2, atrás do balcão (lz 1).
     */
    public static void construir(ServerLevel nivel, Reino r, String papel, BlockPos npc, Direction frente) {
        Tema t = TEMAS.get(papel);
        if (t == null) return;
        Direction fundo = frente.getOpposite(), lado = frente.getClockWise();
        int y0 = npc.getY() - 1;   // piso
        for (int lx = -2; lx <= 2; lx++) {
            for (int lz = 0; lz <= 4; lz++) {
                BlockPos col = npc.relative(lado, lx).relative(fundo, lz - 2);
                nivel.getChunk(col.getX() >> 4, col.getZ() >> 4);
                // base firme embaixo do piso (praça em desnível, buraco ou água)
                for (int y = y0 - 1; y >= y0 - 4; y--) {
                    BlockPos b = col.atY(y);
                    BlockState s = nivel.getBlockState(b);
                    if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
                    nivel.setBlock(b, bloco(t.parede()), 2);
                }
                nivel.setBlock(col.atY(y0), bloco(t.chao()), 2);
                for (int y = y0 + 1; y <= y0 + 5; y++) nivel.setBlock(col.atY(y), Blocks.AIR.defaultBlockState(), 2);
                nivel.setBlock(col.atY(y0 + 4), bloco(t.telhado()), 2);
                boolean canto = Math.abs(lx) == 2 && (lz == 0 || lz == 4);
                if (canto) {
                    for (int y = y0 + 1; y <= y0 + 3; y++) nivel.setBlock(col.atY(y), bloco(t.poste()), 2);
                } else if (Math.abs(lx) == 2) {
                    for (int y = y0 + 1; y <= y0 + 2; y++) nivel.setBlock(col.atY(y), bloco(t.parede()), 2);
                } else if (lz == 4) {
                    nivel.setBlock(col.atY(y0 + 1), virado(bloco(t.fundo()[lx + 1]), frente), 2);
                    for (int y = y0 + 2; y <= y0 + 3; y++) nivel.setBlock(col.atY(y), bloco(t.parede()), 2);
                } else if (lz == 1) {
                    nivel.setBlock(col.atY(y0 + 1), virado(bloco(t.balcao()[lx + 1]), frente), 2);
                }
            }
        }
        // luz pendurada no teto, atrás do NPC
        BlockState luz = bloco(t.luz());
        if (luz.hasProperty(LanternBlock.HANGING)) luz = luz.setValue(LanternBlock.HANGING, true);
        nivel.setBlock(npc.relative(fundo, 1).atY(y0 + 3), luz, 3);
        BlockPos a = npc.relative(lado, -2).relative(fundo, -2), b = npc.relative(lado, 2).relative(fundo, 2);
        registrar(nivel, r.id() + " " + papel, Math.min(a.getX(), b.getX()), y0 - 4, Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), y0 + 5, Math.max(a.getZ(), b.getZ()));
    }

    // ------------------------------------------------------------------ proteção

    private record Caixa(String nome, int x0, int y0, int z0, int x1, int y1, int z1) {
        boolean contem(BlockPos p) {
            return p.getX() >= x0 && p.getX() <= x1 && p.getY() >= y0 && p.getY() <= y1 && p.getZ() >= z0 && p.getZ() <= z1;
        }
    }

    private static List<Caixa> caixas;
    private static Object servidor;

    private static Path arquivo(ServerLevel nivel) {
        return nivel.getServer().getWorldPath(LevelResource.ROOT).resolve("frx_oficios.txt");
    }

    private static List<Caixa> caixas(ServerLevel nivel) {
        if (caixas != null && servidor == nivel.getServer()) return caixas;
        caixas = new ArrayList<>();
        servidor = nivel.getServer();
        try {
            Path p = arquivo(nivel);
            if (Files.exists(p)) for (String l : Files.readAllLines(p)) {
                String[] c = l.trim().split(" ");
                if (c.length != 8) continue;
                caixas.add(new Caixa(c[0] + " " + c[1], Integer.parseInt(c[2]), Integer.parseInt(c[3]), Integer.parseInt(c[4]),
                        Integer.parseInt(c[5]), Integer.parseInt(c[6]), Integer.parseInt(c[7])));
            }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Não li frx_oficios.txt: {}", e.toString());
        }
        return caixas;
    }

    private static void registrar(ServerLevel nivel, String nome, int x0, int y0, int z0, int x1, int y1, int z1) {
        List<Caixa> lista = caixas(nivel);
        lista.removeIf(c -> c.nome().equals(nome));
        lista.add(new Caixa(nome, x0, y0, z0, x1, y1, z1));
        List<String> linhas = new ArrayList<>();
        for (Caixa c : lista) linhas.add(c.nome() + " " + c.x0() + " " + c.y0() + " " + c.z0() + " " + c.x1() + " " + c.y1() + " " + c.z1());
        try { Files.write(arquivo(nivel), linhas); } catch (Exception e) { FrxWorld.LOG.warn("Não salvei frx_oficios.txt: {}", e.toString()); }
    }

    /** Já existe uma casinha ocupando esse lugar? (para não montar uma em cima da outra) */
    public static boolean ocupado(ServerLevel nivel, String reino, String papel, BlockPos npc) {
        for (Caixa c : caixas(nivel)) {
            if (c.nome().equals(reino + " " + papel) || !c.nome().startsWith(reino + " ")) continue;
            if (npc.getX() + 2 >= c.x0() && npc.getX() - 2 <= c.x1() && npc.getZ() + 2 >= c.z0() && npc.getZ() - 2 <= c.z1()) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void naoQuebrar(BlockEvent.BreakEvent ev) {
        if (!(ev.getLevel() instanceof ServerLevel nivel) || !(ev.getPlayer() instanceof ServerPlayer p) || p.isCreative() || br.com.frxcode.frxworld.protecao.AreasProtegidas.tumulo(ev.getState())) return;
        for (Caixa c : caixas(nivel)) {
            if (c.contem(ev.getPos())) {
                ev.setCanceled(true);
                p.displayClientMessage(Component.literal("Esta é a casa de um morador do reino: não dá para quebrar.").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
    }
}
