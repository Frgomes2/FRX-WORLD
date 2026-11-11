package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Capitais dos reinos: cada pasta em server/frxworld/reinos/<id>/ tem reino.json + fatias por chunk;
 * a posição de cada capital vem de posicoes.json. A FeatureReinos cola as fatias durante a geração do mundo.
 */
public class Reinos {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, FrxWorld.MOD_ID);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> FEATURE =
            FEATURES.register("reinos", () -> new FeatureReinos(NoneFeatureConfiguration.CODEC));

    private static volatile List<Reino> reinos = List.of();
    private static final Map<UUID, String> ondeEsta = new ConcurrentHashMap<>();
    private static final Map<String, Object[]> PADRAO = new LinkedHashMap<>();

    static {
        PADRAO.put("sarion", new Object[]{"Dinastia Feiticeira de Sarion", 2200, -1400});
        PADRAO.put("leste", new Object[]{"Império do Leste", -2600, -300});
        PADRAO.put("dwargon", new Object[]{"Reino Armado de Dwargon", 700, 2300});
        PADRAO.put("oeste", new Object[]{"Nações do Oeste", -1200, 2400});
        PADRAO.put("lubelius", new Object[]{"Império Sagrado de Lubelius", 1800, 1600});
        PADRAO.put("demonios", new Object[]{"Territórios dos Lordes Demônios", -1600, -2600});
    }

    public static void registrar(IEventBus modBus) {
        FEATURES.register(modBus);
    }

    public static List<Reino> todos() {
        return reinos;
    }

    static Path pasta() {
        return FMLPaths.GAMEDIR.get().resolve("frxworld").resolve("reinos");
    }

    @SubscribeEvent
    public void antesDeLigar(ServerAboutToStartEvent evento) {
        carregar();
    }

    public static void carregar() {
        Path raiz = pasta();
        List<Reino> lista = new ArrayList<>();
        try {
            Files.createDirectories(raiz);
            Path posicoes = raiz.resolve("posicoes.json");
            JsonObject pos;
            if (Files.exists(posicoes)) {
                pos = JsonParser.parseString(Files.readString(posicoes, StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                JsonObject novo = new JsonObject();
                PADRAO.forEach((id, v) -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("nome", (String) v[0]);
                    o.addProperty("x", (Integer) v[1]);
                    o.addProperty("z", (Integer) v[2]);
                    novo.add(id, o);
                });
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(posicoes, gson.toJson(novo), StandardCharsets.UTF_8);
                pos = novo;
            }
            try (Stream<Path> dirs = Files.list(raiz)) {
                for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                    Path man = dir.resolve("reino.json");
                    if (!Files.exists(man)) continue;
                    String id = dir.getFileName().toString();
                    JsonObject m = JsonParser.parseString(Files.readString(man, StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonObject p = pos.has(id) ? pos.getAsJsonObject(id) : null;
                    if (p == null) {
                        FrxWorld.LOG.warn("Reino {} sem posição em posicoes.json; ignorado.", id);
                        continue;
                    }
                    Set<Long> fatias = new HashSet<>();
                    for (JsonElement e : m.getAsJsonArray("chunks")) {
                        JsonArray a = e.getAsJsonArray();
                        fatias.add(Reino.chave(a.get(0).getAsInt(), a.get(1).getAsInt()));
                    }
                    lista.add(new Reino(id, p.get("nome").getAsString(), m.get("modo").getAsString(),
                            m.get("larguraChunks").getAsInt(), m.get("profundidadeChunks").getAsInt(), m.get("altura").getAsInt(),
                            m.get("yBase").getAsInt(), m.get("chao").getAsInt(), fatias, dir, p.get("x").getAsInt(), p.get("z").getAsInt()));
                }
            }
        } catch (IOException | RuntimeException e) {
            FrxWorld.LOG.error("Falha ao carregar os reinos", e);
        }
        reinos = List.copyOf(lista);
        for (Reino r : reinos) {
            FrxWorld.LOG.info("Reino {} ({}) em {}, {} — {}x{} chunks, {} fatias", r.id(), r.nome(), r.centroX(), r.centroZ(),
                    r.larguraChunks(), r.profundidadeChunks(), r.fatias().size());
        }
    }

    /** Na primeira vez de cada mundo, põe o spawn no ponto "spawn": [x, z] do posicoes.json (a cidade inicial). */
    @SubscribeEvent
    public void aoLigar(net.minecraftforge.event.server.ServerStartedEvent evento) {
        try {
            Path marca = evento.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("frx_spawn.txt");
            Path posicoes = pasta().resolve("posicoes.json");
            if (Files.exists(marca) || !Files.exists(posicoes)) return;
            JsonObject pos = JsonParser.parseString(Files.readString(posicoes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!pos.has("spawn")) return;
            JsonArray s = pos.getAsJsonArray("spawn");
            int x = s.get(0).getAsInt(), z = s.get(1).getAsInt();
            ServerLevel nivel = evento.getServer().overworld();
            nivel.getChunk(x >> 4, z >> 4);
            int y = nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            nivel.setDefaultSpawnPos(new net.minecraft.core.BlockPos(x, y, z), 0f);
            Files.writeString(marca, x + " " + y + " " + z);
            FrxWorld.LOG.info("Spawn do mundo definido na cidade inicial: {} {} {}", x, y, z);
        } catch (Exception e) {
            FrxWorld.LOG.warn("Não consegui definir o spawn da cidade inicial: {}", e.toString());
        }
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.PlayerTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END || !(evento.player instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
        if (p.level().dimension() != Level.OVERWORLD) return;
        Reino dentro = null;
        for (Reino r : reinos) {
            if (r.distancia(p.getBlockX(), p.getBlockZ()) == 0) { dentro = r; break; }
        }
        String antes = dentro == null ? ondeEsta.remove(p.getUUID()) : ondeEsta.put(p.getUUID(), dentro.id());
        if (dentro != null) { NpcsReino.garantir(p, dentro); Populacao.aoRedor(p, dentro); }
        if (dentro == null || dentro.id().equals(antes)) return;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(dentro.nome()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Você entrou numa capital").withStyle(ChatFormatting.YELLOW)));
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reino")
                        .executes(ctx -> {
                            if (reinos.isEmpty()) ctx.getSource().sendFailure(Component.literal("Nenhum reino carregado (server/frxworld/reinos)."));
                            for (Reino r : reinos) {
                                ctx.getSource().sendSuccess(() -> Component.literal(r.id() + ": " + r.nome() + " em " + r.centroX() + ", " + r.centroZ()), false);
                            }
                            return reinos.size();
                        })
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(reinos.stream().map(Reino::id), b))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    for (Reino r : reinos) {
                                        if (!r.id().equals(id)) continue;
                                        ServerLevel nivel = ctx.getSource().getServer().overworld();
                                        nivel.getChunk(r.centroX() >> 4, r.centroZ() >> 4);
                                        int y = nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, r.centroX(), r.centroZ());
                                        p.teleportTo(nivel, r.centroX() + 0.5, y + 1, r.centroZ() + 0.5, p.getYRot(), p.getXRot());
                                        return 1;
                                    }
                                    ctx.getSource().sendFailure(Component.literal("Reino não encontrado: " + id));
                                    return 0;
                                }))));
    }
}
