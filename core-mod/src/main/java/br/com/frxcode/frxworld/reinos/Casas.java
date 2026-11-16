package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Arruma as casas de uma capital: põe porta em todo vão de porta vazio (1 de largura, 2 de altura, com verga),
 * e garante em cada casa uma cama e, em parte delas, um baú de saque do reino.
 * "Casa" é o cômodo coberto que fica atrás de uma porta que dá para a rua. Comando: /frx casas <reino>.
 */
public class Casas {
    private static final int MAX_COMODO = 400, MIN_COMODO = 6;
    private static final int CHANCE_BAU = 60;

    private record Troca(BlockPos pos, BlockState antes) {}
    /** O que a última execução mudou, para /frx casas desfazer (vale até o servidor reiniciar). */
    private static final List<Troca> ULTIMAS = new ArrayList<>();

    private static void por(ServerLevel n, BlockPos p, BlockState novo) {
        ULTIMAS.add(new Troca(p.immutable(), n.getBlockState(p)));
        n.setBlock(p, novo, 2 | 16);
    }

    public Casas() {}

    private static boolean livre(ServerLevel n, BlockPos p) { return n.getBlockState(p).isAir(); }
    private static boolean parede(ServerLevel n, BlockPos p) { BlockState b = n.getBlockState(p); return !b.isAir() && b.blocksMotion(); }
    private static boolean coberto(ServerLevel n, BlockPos p) { return !n.canSeeSky(p); }

    /** Porta de madeira parecida com o que há em volta do vão (verga e ombreiras); carvalho se nada combinar. */
    private static Block portaPara(ServerLevel n, BlockPos p, Direction lado) {
        String[] madeiras = {"dark_oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped", "oak"};
        Block[] portas = {Blocks.DARK_OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR, Blocks.JUNGLE_DOOR, Blocks.ACACIA_DOOR, Blocks.MANGROVE_DOOR,
                Blocks.CHERRY_DOOR, Blocks.BAMBOO_DOOR, Blocks.CRIMSON_DOOR, Blocks.WARPED_DOOR, Blocks.OAK_DOOR};
        for (BlockPos v : new BlockPos[]{p.above(2), p.relative(lado), p.relative(lado.getOpposite()), p.below()}) {
            net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(n.getBlockState(v).getBlock());
            if (id == null) continue;
            for (int i = 0; i < madeiras.length; i++) if (id.getPath().contains(madeiras[i])) return portas[i];
        }
        return Blocks.OAK_DOOR;
    }

    /**
     * Vão de porta em p (nível dos pés) atravessado na direção d? Devolve true se: 2 de ar, piso firme, verga em cima,
     * ombreiras dos dois lados, e a parede tem só 1 bloco de espessura (abre para um espaço mais largo dos dois lados).
     */
    private static boolean vao(ServerLevel n, BlockPos p, Direction d) {
        Direction lado = d.getClockWise();
        if (!parede(n, p.below()) || !parede(n, p.above(2))) return false;
        if (!parede(n, p.relative(lado)) || !parede(n, p.relative(lado).above()) || !parede(n, p.relative(lado.getOpposite())) || !parede(n, p.relative(lado.getOpposite()).above())) return false;
        for (Direction f : new Direction[]{d, d.getOpposite()}) {
            BlockPos q = p.relative(f);
            if (!livre(n, q) || !livre(n, q.above())) return false;
            if (!livre(n, q.relative(lado)) && !livre(n, q.relative(lado.getOpposite()))) return false;   // corredor estreito, não porta
        }
        return coberto(n, p.relative(d)) || coberto(n, p.relative(d.getOpposite()));
    }

    private record Resultado(int portas, int casas, int camas, int baus, int semPorta) {}

    private static boolean enfeite(ServerLevel n, BlockPos p) {
        BlockState b = n.getBlockState(p);
        if (b.isAir()) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b.getBlock());
        String c = id == null ? "" : id.getPath();
        for (String k : new String[]{"stairs", "slab", "trapdoor", "fence", "wall", "bars", "carpet", "lantern", "pane"}) if (c.contains(k)) return true;
        return false;
    }

    /**
     * As casas das capitais coladas são maquetes: um corpo maciço apoiado num anel, com um vão fechado de 1 bloco
     * de altura no nível do chão. Aqui cada vão desses vira um cômodo de verdade: o mod cava 2 blocos para cima
     * (o teto continua sendo o da maquete), põe piso, abre uma porta para o lado mais livre, uma cama e, em parte
     * das casas, um baú de saque do reino.
     */
    private static Resultado arrumar(ServerLevel n, Reino r) {
        int x0 = r.chunkX0() << 4, z0 = r.chunkZ0() << 4, x1 = (r.chunkX1() << 4) + 15, z1 = (r.chunkZ1() << 4) + 15;
        for (int cx = r.chunkX0(); cx <= r.chunkX1(); cx++) for (int cz = r.chunkZ0(); cz <= r.chunkZ1(); cz++) n.getChunk(cx, cz);
        final int Y = r.chao() + 1;                       // nível em que se anda
        net.minecraft.util.RandomSource rnd = n.getRandom();
        int portas = 0, casas = 0, camas = 0, baus = 0, semPorta = 0;
        ULTIMAS.clear();

        // 1) vãos fechados: ar no nível do chão, com 2 blocos maciços por cima, sem contato com ar aberto
        Set<BlockPos> vistos = new HashSet<>();
        List<List<BlockPos>> regioes = new ArrayList<>();
        Set<BlockPos> todas = new HashSet<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0 + 2; x < x1 - 1; x++) for (int z = z0 + 2; z < z1 - 1; z++) {
            m.set(x, Y, z);
            if (vistos.contains(m) || !vaoFechado(n, m)) continue;
            List<BlockPos> celulas = new ArrayList<>();
            ArrayDeque<BlockPos> fila = new ArrayDeque<>();
            BlockPos ini = m.immutable();
            fila.add(ini); vistos.add(ini);
            boolean vaza = false;
            while (!fila.isEmpty()) {
                BlockPos c = fila.poll();
                celulas.add(c);
                if (celulas.size() > MAX_COMODO) { vaza = true; break; }
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos v = c.relative(d);
                    if (v.getX() <= x0 || v.getX() >= x1 || v.getZ() <= z0 || v.getZ() >= z1) { vaza = true; continue; }
                    if (!n.getBlockState(v).isAir()) continue;
                    if (!vaoFechado(n, v)) { vaza = true; continue; }       // ar sem tampa encostado: é um espaço aberto
                    if (vistos.add(v)) fila.add(v);
                }
            }
            if (!vaza && celulas.size() >= 4) { regioes.add(celulas); todas.addAll(celulas); }
        }

        for (List<BlockPos> celulas : regioes) {
            Set<BlockPos> R = new HashSet<>(celulas);
            // 2) melhor lugar para a porta: parede de 1 bloco que dá para o lado mais livre
            BlockPos melhorDentro = null, melhorParede = null, melhorFora = null;
            Direction melhorDir = null;
            int nota = -1;
            for (BlockPos c : celulas) for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos w = c.relative(d), o = w.relative(d);
                if (R.contains(w) || n.getBlockState(w).isAir()) continue;
                if (o.getX() <= x0 + 1 || o.getX() >= x1 - 1 || o.getZ() <= z0 + 1 || o.getZ() >= z1 - 1) continue;
                if (todas.contains(o) || !n.getBlockState(o).isAir() || n.getBlockState(o.below()).isAir()) continue;
                if (n.getBlockState(w.above(2)).isAir()) continue;                                  // precisa de verga
                if (!n.getBlockState(o.above()).isAir() && !enfeite(n, o.above())) continue;          // beiral pode sair; parede não
                int livre = 0;
                for (int i = 0; i < 8; i++) {
                    BlockPos q = o.relative(d, i);
                    if (!n.getBlockState(q).isAir() || todas.contains(q)) break;
                    livre++;
                }
                int lados = 0;
                for (Direction s : new Direction[]{d.getClockWise(), d.getCounterClockWise()}) if (!n.getBlockState(w.relative(s)).isAir()) lados++;
                int nt = livre * 10 + lados * 5;
                if (nt > nota) { nota = nt; melhorDentro = c; melhorParede = w; melhorFora = o; melhorDir = d; }
            }
            if (melhorParede == null) { semPorta++; continue; }
            casas++;

            // 3) cava o cômodo e põe o piso
            Block tabua = r.id().equals("lubelius") ? Blocks.BIRCH_PLANKS : Blocks.OAK_PLANKS;
            for (BlockPos c : celulas) {
                if (!n.getBlockState(c.above()).isAir()) por(n, c.above(), Blocks.AIR.defaultBlockState());
                if (!n.getBlockState(c.above(2)).isAir() && !n.getBlockState(c.above(3)).isAir()) por(n, c.above(2), Blocks.AIR.defaultBlockState());
                BlockState chao = n.getBlockState(c.below());
                if (chao.is(Blocks.GRASS_BLOCK) || chao.is(Blocks.DIRT) || chao.is(Blocks.STONE)) por(n, c.below(), tabua.defaultBlockState());
            }

            // 4) porta (abre para dentro) e beiral livre na frente
            Block porta = r.id().equals("lubelius") ? Blocks.BIRCH_DOOR : portaPara(n, melhorParede, melhorDir.getClockWise());
            BlockState baixo = porta.defaultBlockState().setValue(DoorBlock.FACING, melhorDir.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            por(n, melhorParede, baixo);
            por(n, melhorParede.above(), baixo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            if (!n.getBlockState(melhorFora.above()).isAir()) por(n, melhorFora.above(), Blocks.AIR.defaultBlockState());
            portas++;

            // 5) cama: cabeceira na parede, sem tapar a frente da porta
            Set<BlockPos> ocupadas = new HashSet<>();
            ocupadas.add(melhorDentro);
            busca:
            for (BlockPos cab : celulas) {
                if (ocupadas.contains(cab)) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos pe = cab.relative(d.getOpposite());
                    if (R.contains(cab.relative(d)) || !R.contains(pe) || ocupadas.contains(pe) || cab.relative(d).equals(melhorParede)) continue;
                    Block cama = r.id().equals("lubelius") ? Blocks.WHITE_BED : r.id().equals("demonios") ? Blocks.BLACK_BED : Blocks.RED_BED;
                    BlockState st = cama.defaultBlockState().setValue(BedBlock.FACING, d);
                    por(n, pe, st.setValue(BedBlock.PART, BedPart.FOOT));
                    por(n, cab, st.setValue(BedBlock.PART, BedPart.HEAD));
                    ocupadas.add(pe); ocupadas.add(cab);
                    camas++;
                    break busca;
                }
            }
            // 6) baú de saque do reino, em parte das casas, num canto
            if (rnd.nextInt(100) < CHANCE_BAU) {
                for (BlockPos c : celulas) {
                    if (ocupadas.contains(c)) continue;
                    int paredes = 0;
                    boolean naPorta = false;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (!R.contains(c.relative(d))) paredes++;
                        if (c.relative(d).equals(melhorParede)) naPorta = true;
                    }
                    if (paredes < 2 || naPorta) continue;
                    ULTIMAS.add(new Troca(c.immutable(), n.getBlockState(c)));
                    Masmorras.porBau(n, c, BausReino.tabela(r.id()), rnd);
                    baus++;
                    break;
                }
            }
        }
        return new Resultado(portas, casas, camas, baus, semPorta);
    }

    private static boolean vaoFechado(ServerLevel n, BlockPos p) {
        return n.getBlockState(p).isAir() && !n.getBlockState(p.above()).isAir() && !n.getBlockState(p.above(2)).isAir();
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("casas").then(Commands.literal("desfazer").executes(ctx -> {
                    ServerLevel n = ctx.getSource().getServer().overworld();
                    int q = ULTIMAS.size();
                    for (int i = q - 1; i >= 0; i--) {
                        Troca t = ULTIMAS.get(i);
                        n.removeBlockEntity(t.pos());
                        n.setBlock(t.pos(), t.antes(), 2 | 16);
                    }
                    ULTIMAS.clear();
                    ctx.getSource().sendSuccess(() -> Component.literal("Casas: " + q + " blocos devolvidos ao que eram.").withStyle(ChatFormatting.GOLD), true);
                    return 1;
                })).then(Commands.argument("reino", StringArgumentType.word())
                        .suggests((c, b) -> { List<String> ids = new ArrayList<>(); for (Reino r : Reinos.todos()) ids.add(r.id()); return SharedSuggestionProvider.suggest(ids, b); })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "reino");
                            Reino alvo = null;
                            for (Reino r : Reinos.todos()) if (r.id().equals(id)) alvo = r;
                            if (alvo == null) { ctx.getSource().sendFailure(Component.literal("Reino desconhecido.")); return 0; }
                            long t0 = System.currentTimeMillis();
                            Resultado res = arrumar(ctx.getSource().getServer().overworld(), alvo);
                            String txt = "Casas de " + alvo.nome() + ": " + res.casas() + " casas abertas (" + res.portas() + " portas, " + res.camas() + " camas, "
                                    + res.baus() + " baús); " + res.semPorta() + " sem lugar livre para porta (" + (System.currentTimeMillis() - t0) / 1000 + " s).";
                            FrxWorld.LOG.info(txt);
                            ctx.getSource().sendSuccess(() -> Component.literal(txt).withStyle(ChatFormatting.GOLD), true);
                            return 1;
                        }))));
    }
}
