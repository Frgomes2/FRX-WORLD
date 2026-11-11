package br.com.frxcode.frxworld.reinos;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decorações colocadas no mundo já gerado (funciona em mundos antigos e novos): listas "x y z estado" em
 * data/frxworld/frx_decor/<id>.txt, em coordenadas absolutas do Overworld. Cada uma é colocada uma vez,
 * quando um jogador chega perto e todos os chunks dela estão carregados. Feitas ficam em frx_decor.txt no mundo.
 * /frx decor refazer <id> coloca de novo.
 */
public class Decoracoes {
    private static final List<String> IDS = List.of("dwargon_trono");
    private static final Set<String> feitas = new HashSet<>();
    private static final Map<String, List<Object[]>> cache = new LinkedHashMap<>();
    private static boolean carregado;
    private static int ticks;

    public Decoracoes() {}

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_decor.txt"); }

    private static void carregar(MinecraftServer s) {
        if (carregado) return;
        carregado = true;
        feitas.clear();
        try {
            Path p = arquivo(s);
            if (Files.exists(p)) for (String l : Files.readAllLines(p)) if (!l.isBlank()) feitas.add(l.trim());
        } catch (Exception ignorado) {}
    }

    private static void salvar(MinecraftServer s) {
        try { Files.write(arquivo(s), feitas); } catch (Exception ignorado) {}
    }

    /** [BlockPos, BlockState] */
    private static List<Object[]> blocos(String id) {
        return cache.computeIfAbsent(id, k -> {
            List<Object[]> l = new ArrayList<>();
            try (InputStream in = Decoracoes.class.getResourceAsStream("/data/frxworld/frx_decor/" + k + ".txt")) {
                if (in == null) return l;
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String linha;
                while ((linha = r.readLine()) != null) {
                    linha = linha.trim();
                    if (linha.isEmpty() || linha.startsWith("#")) continue;
                    String[] p = linha.split(" ", 4);
                    try {
                        BlockState st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), p[3], false).blockState();
                        l.add(new Object[]{new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])), st});
                    } catch (Exception ex) {
                        br.com.frxcode.frxworld.FrxWorld.LOG.warn("Decoração {}: bloco inválido '{}'", k, p.length > 3 ? p[3] : linha);
                    }
                }
            } catch (Exception ex) {
                br.com.frxcode.frxworld.FrxWorld.LOG.warn("Decoração {}: {}", k, ex.toString());
            }
            return l;
        });
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent e) { carregado = false; carregar(e.getServer()); }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 40 != 0) return;
        MinecraftServer s = e.getServer();
        carregar(s);
        ServerLevel nivel = s.overworld();
        for (String id : IDS) {
            if (feitas.contains(id)) continue;
            List<Object[]> l = blocos(id);
            if (l.isEmpty()) continue;
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (Object[] b : l) {
                BlockPos p = (BlockPos) b[0];
                x0 = Math.min(x0, p.getX()); x1 = Math.max(x1, p.getX()); z0 = Math.min(z0, p.getZ()); z1 = Math.max(z1, p.getZ());
            }
            double mx = (x0 + x1) / 2.0, mz = (z0 + z1) / 2.0;
            boolean perto = false;
            for (ServerPlayer p : nivel.players()) {
                double dx = p.getX() - mx, dz = p.getZ() - mz;
                if (dx * dx + dz * dz < 128 * 128) { perto = true; break; }
            }
            if (!perto) continue;
            boolean pronto = true;
            for (int cx = x0 >> 4; cx <= x1 >> 4 && pronto; cx++)
                for (int cz = z0 >> 4; cz <= z1 >> 4 && pronto; cz++)
                    if (!nivel.hasChunk(cx, cz)) pronto = false;
            if (!pronto) continue;
            colocar(nivel, l);
            feitas.add(id);
            salvar(s);
            br.com.frxcode.frxworld.FrxWorld.LOG.info("Decoração {} colocada ({} blocos).", id, l.size());
        }
    }

    private static void colocar(ServerLevel nivel, List<Object[]> l) {
        for (Object[] b : l) nivel.setBlock((BlockPos) b[0], (BlockState) b[1], Block.UPDATE_CLIENTS);
        // segunda passada: velas, tapetes e vasos que dependem do bloco de baixo
        for (Object[] b : l) {
            BlockPos p = (BlockPos) b[0];
            if (!nivel.getBlockState(p).equals(b[1])) nivel.setBlock(p, (BlockState) b[1], Block.UPDATE_CLIENTS);
        }
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("decor").then(Commands.literal("refazer")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(IDS, b))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    MinecraftServer s = ctx.getSource().getServer();
                                    carregar(s);
                                    feitas.remove(id);
                                    cache.remove(id);
                                    salvar(s);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Decoração " + id + " será colocada quando alguém estiver perto."), true);
                                    return 1;
                                })))));
    }
}
