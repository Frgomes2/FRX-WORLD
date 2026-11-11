package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Waystones das capitais: 1 no centro (praça) e 4 nos portões (norte, sul, leste, oeste), em todos os reinos.
 * São colocadas quando o chunk do ponto carrega pela primeira vez (ninguém precisa gerar o mapa antes).
 * Registro por mundo em <mundo>/frx_waystones.txt. Usa o mod Waystones por reflexão: sem ele, não faz nada.
 */
public class WaystonesReino {
    private static final String[][] LADOS = {{"centro", "Centro"}, {"norte", "Portão Norte"}, {"sul", "Portão Sul"}, {"leste", "Portão Leste"}, {"oeste", "Portão Oeste"}};
    private static final Map<String, String> CURTO = Map.of("tempest", "Tempest", "dwargon", "Dwargon", "sarion", "Sarion", "leste", "Império do Leste",
            "leste-sakura", "Sakura", "oeste", "Oeste", "lubelius", "Lubelius", "demonios", "Lordes Demônios");
    private static final Map<String, String> ESTILO = Map.of("dwargon", "DEEPSLATE", "demonios", "BLACKSTONE", "sarion", "MOSSY", "leste-sakura", "MOSSY");
    /** Distância (blocos) para fora da borda do reino onde ficam os portões. */
    private static final int FORA = 6;

    private record Ponto(String chave, String nome, String reino, int x, int z, int yRef, boolean subterraneo, boolean centro) {}

    private final Set<String> feitas = new LinkedHashSet<>();
    private final List<Ponto> pendentes = new ArrayList<>();
    private int ticks;
    private boolean ativo;

    private static Path registro(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("frx_waystones.txt");
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        ativo = ModList.get().isLoaded("waystones");
        if (!ativo) return;
        feitas.clear();
        try {
            Path r = registro(ev.getServer());
            if (Files.exists(r)) for (String l : Files.readAllLines(r)) if (!l.isBlank()) feitas.add(l.split(";")[0]);
        } catch (Exception ignorado) { }
        montarPendentes();
        FrxWorld.LOG.info("Waystones dos reinos: {} colocadas, {} esperando o chunk carregar", feitas.size(), pendentes.size());
    }

    private void montarPendentes() {
        pendentes.clear();
        JsonObject pos = new JsonObject();
        try {
            pos = JsonParser.parseString(Files.readString(Reinos.pasta().resolve("posicoes.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignorado) { }
        for (Reino r : Reinos.todos()) {
            JsonObject p = pos.has(r.id()) ? pos.getAsJsonObject(r.id()) : new JsonObject();
            int x0 = r.chunkX0() << 4, z0 = r.chunkZ0() << 4, x1 = (r.chunkX1() << 4) + 15, z1 = (r.chunkZ1() << 4) + 15;
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            String nome = CURTO.getOrDefault(r.id(), r.nome());
            // centro: praça (se houver) ou o meio; no subterrâneo, perto da taverna/rei
            int mx = r.centroX(), mz = r.centroZ(), my = r.chao();
            if (p.has("praca")) { JsonArray a = p.getAsJsonArray("praca"); mx = a.get(0).getAsInt(); mz = a.get(1).getAsInt(); }
            if (r.subterraneo() && p.has("npcs")) {
                JsonObject n = p.getAsJsonObject("npcs");
                JsonArray a = n.has("taverneiro") ? n.getAsJsonArray("taverneiro") : n.has("rei") ? n.getAsJsonArray("rei") : null;
                if (a != null) { mx = a.get(0).getAsInt() + 3; my = a.get(1).getAsInt(); mz = a.get(2).getAsInt(); }
            }
            pendentes.add(new Ponto(r.id() + ":centro", nome + " · Centro", r.id(), mx, mz, my, r.subterraneo(), true));
            pendentes.add(new Ponto(r.id() + ":norte", nome + " · " + LADOS[1][1], r.id(), cx, z0 - FORA, 0, false, false));
            pendentes.add(new Ponto(r.id() + ":sul", nome + " · " + LADOS[2][1], r.id(), cx, z1 + FORA, 0, false, false));
            pendentes.add(new Ponto(r.id() + ":leste", nome + " · " + LADOS[3][1], r.id(), x1 + FORA, cz, 0, false, false));
            pendentes.add(new Ponto(r.id() + ":oeste", nome + " · " + LADOS[4][1], r.id(), x0 - FORA, cz, 0, false, false));
        }
        pendentes.removeIf(pt -> feitas.contains(pt.chave()));
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !ativo || pendentes.isEmpty() || ++ticks % 100 != 0) return;
        ServerLevel nivel = ev.getServer().overworld();
        for (var it = pendentes.iterator(); it.hasNext(); ) {
            Ponto pt = it.next();
            if (!nivel.hasChunk(pt.x() >> 4, pt.z() >> 4)) continue;   // espera alguém chegar perto
            String res = colocar(nivel, pt);
            if (res != null) {
                feitas.add(pt.chave());
                it.remove();
                gravar(ev.getServer(), pt.chave() + ";" + res);
            }
        }
    }

    private static void gravar(MinecraftServer s, String linha) {
        try {
            Files.writeString(registro(s), linha + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            FrxWorld.LOG.warn("Não consegui gravar frx_waystones.txt: {}", e.toString());
        }
    }

    /** Coloca a waystone; devolve "x;y;z" ou null se ainda não deu (tenta de novo depois). */
    private String colocar(ServerLevel nivel, Ponto pt) {
        BlockPos base = pt.subterraneo() ? chaoSubterraneo(nivel, pt) : chaoSuperficie(nivel, pt);
        if (base == null) return null;
        try {
            Optional<?> existente = (Optional<?>) Class.forName("net.blay09.mods.waystones.api.WaystonesAPI")
                    .getMethod("getWaystoneAt", ServerLevel.class, BlockPos.class).invoke(null, nivel, base);
            Object w;
            if (existente.isPresent()) w = existente.get();
            else {
                Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
                Class<?> estiloCls = Class.forName("net.blay09.mods.waystones.api.WaystoneStyle");
                Object estilo = Class.forName("net.blay09.mods.waystones.api.WaystoneStyles").getField(ESTILO.getOrDefault(pt.reino(), "DEFAULT")).get(null);
                // base firme embaixo e espaço livre em cima
                BlockState chao = nivel.getBlockState(base.below());
                if (!chao.isSolidRender(nivel, base.below())) nivel.setBlock(base.below(), net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                nivel.setBlock(base, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                nivel.setBlock(base.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                Optional<?> r = (Optional<?>) api.getMethod("placeWaystone", Level.class, BlockPos.class, estiloCls).invoke(null, nivel, base, estilo);
                if (r.isEmpty()) return null;
                w = r.get();
                pedestal(nivel, base, pt.centro());
            }
            Class.forName("net.blay09.mods.waystones.api.IMutableWaystone").getMethod("setName", String.class).invoke(w, pt.nome());
            Class<?> gerente = Class.forName("net.blay09.mods.waystones.core.WaystoneManager");
            Object g = gerente.getMethod("get", MinecraftServer.class).invoke(null, nivel.getServer());
            Method atualizar = gerente.getMethod("updateWaystone", Class.forName("net.blay09.mods.waystones.api.IWaystone"));
            atualizar.invoke(g, w);
            FrxWorld.LOG.info("Waystone '{}' colocada em {}, {}, {}", pt.nome(), base.getX(), base.getY(), base.getZ());
            return base.getX() + ";" + base.getY() + ";" + base.getZ();
        } catch (ReflectiveOperationException | RuntimeException e) {
            FrxWorld.LOG.warn("Waystone {} falhou: {}", pt.chave(), e.toString());
            return null;
        }
    }

    /** Laje de pedra 3×3 em volta (só troca blocos naturais do chão) e lanternas no centro. */
    private static void pedestal(ServerLevel nivel, BlockPos base, boolean centro) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos c = base.offset(dx, -1, dz);
                BlockState s = nivel.getBlockState(c);
                if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(net.minecraft.world.level.block.Blocks.GRAVEL) || s.is(net.minecraft.world.level.block.Blocks.STONE))
                    nivel.setBlock(c, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        if (!centro) {
            for (int[] d : new int[][]{{1, 1}, {-1, -1}}) {
                BlockPos l = base.offset(d[0], 0, d[1]);
                if (nivel.getBlockState(l).isAir() && nivel.getBlockState(l.below()).isSolidRender(nivel, l.below()))
                    nivel.setBlock(l, net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState(), 3);
            }
        }
    }

    private static boolean livre(ServerLevel n, BlockPos p) {
        BlockState a = n.getBlockState(p), b = n.getBlockState(p.above());
        return (a.isAir() || a.canBeReplaced()) && b.isAir() && a.getFluidState().isEmpty();
    }

    private static boolean firme(ServerLevel n, BlockPos p) {
        BlockState s = n.getBlockState(p);
        return s.isSolidRender(n, p) && !s.is(BlockTags.LEAVES) && s.getFluidState().isEmpty();
    }

    /** Procura em espiral um chão firme; no centro do reino, só no nível do chão da cidade (evita telhados). */
    private static BlockPos chaoSuperficie(ServerLevel n, Ponto pt) {
        for (int r = 0; r <= 24; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = pt.x() + dx, z = pt.z() + dz;
                    if (!n.hasChunk(x >> 4, z >> 4)) continue;
                    int y = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos p = new BlockPos(x, y, z);
                    if (pt.centro() && Math.abs(y - 1 - pt.yRef()) > 3) continue;
                    if (firme(n, p.below()) && livre(n, p)) return p;
                }
        if (pt.centro()) {   // nada no nível da cidade: aceita o que tiver no ponto
            int y = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pt.x(), pt.z());
            BlockPos p = new BlockPos(pt.x(), y, pt.z());
            return firme(n, p.below()) ? p : null;
        }
        return null;
    }

    /** Subterrâneo: procura chão com 2 blocos livres perto da altura de referência. */
    private static BlockPos chaoSubterraneo(ServerLevel n, Ponto pt) {
        for (int r = 0; r <= 10; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 2; dy >= -6; dy--) {
                        BlockPos p = new BlockPos(pt.x() + dx, pt.yRef() + dy, pt.z() + dz);
                        if (firme(n, p.below()) && livre(n, p)) return p;
                    }
                }
        return null;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("waystones")
                        .executes(ctx -> {
                            if (!ativo) { ctx.getSource().sendFailure(Component.literal("O mod Waystones não está instalado.")); return 0; }
                            ctx.getSource().sendSuccess(() -> Component.literal("Waystones dos reinos: " + feitas.size() + " colocadas, " + pendentes.size()
                                    + " esperando alguém chegar perto.").withStyle(ChatFormatting.GOLD), false);
                            for (Ponto pt : pendentes)
                                ctx.getSource().sendSuccess(() -> Component.literal(" · " + pt.nome() + " em " + pt.x() + ", " + pt.z()).withStyle(ChatFormatting.GRAY), false);
                            return 1;
                        })
                        .then(Commands.literal("refazer").executes(ctx -> {
                            // esquece o registro: as que faltarem são colocadas de novo (as já existentes só são renomeadas)
                            try { Files.deleteIfExists(registro(ctx.getSource().getServer())); } catch (Exception ignorado) { }
                            feitas.clear();
                            montarPendentes();
                            ctx.getSource().sendSuccess(() -> Component.literal("Waystones dos reinos serão conferidas de novo (" + pendentes.size() + ").").withStyle(ChatFormatting.GOLD), true);
                            return 1;
                        }))));
    }
}
