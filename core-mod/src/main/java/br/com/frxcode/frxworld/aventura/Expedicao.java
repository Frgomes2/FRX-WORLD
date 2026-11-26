package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Sistema;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Terras Inexploradas (expedição): um mundo à parte, como o Nether, só para explorar conteúdo novo.
 *
 * O Forge 1.20.1 não cria nem apaga dimensões com o servidor ligado e todas usam a seed do mundo principal. Por isso o
 * mundo `frxworld:expedicao` é fixo e cada expedição é uma ÁREA nova dele (2000 × 2000, longe das anteriores):
 * terreno, estruturas e masmorras inéditos a cada troca, sem tocar no mundo principal.
 *
 * - Troca a cada 2 dias às 05:00 (horário de Brasília), com o servidor ligado: quem está lá volta, a borda muda de
 *   lugar e a área nova (pré-gerada antes, com o servidor vazio) é liberada. Se o servidor estava desligado na hora,
 *   a troca acontece ao ligar, antes de o mundo carregar.
 * - Os arquivos das áreas velhas só são apagados ao ligar o servidor (mundo descarregado), e só dentro de
 *   dimensions/frxworld/expedicao. O mundo principal e o Nether nunca são tocados.
 * - Entrada pelo pórtico das Terras Inexploradas (rank D da Guilda ou mais); volta pelo pórtico da chegada.
 * - Morrer lá não é morte: volta ao mundo principal com tudo e fica 30 min sem poder entrar.
 * - Lá dentro não funcionam portais de nenhum tipo (Nether, End, mods), waystones, camas nem âncoras.
 */
public class Expedicao {
    public static final ResourceKey<Level> MUNDO = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(FrxWorld.MOD_ID, "expedicao"));

    private static final int RAIO = 1000;              // área de 2000 × 2000
    private static final int ESPACO = 4000;            // distância entre os centros de duas áreas
    private static final int BASE = 200_000;           // longe de tudo o que já foi explorado
    private static final int POR_LINHA = 40;
    private static final int DIAS = 2;
    private static final int HORA = 5;
    private static final long ESPERA_MORTE_MS = 30 * 60_000L;
    private static final String RANK_MINIMO = "D";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");
    private static final String TAG_ROTULO = "frx_expedicao_rotulo";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ARQUIVO_REGIAO = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");

    // estado (gravado em <mundo>/frx_expedicao.json)
    private static boolean ativa;
    private static int numero;
    private static long inicio, proxima;
    private static int pronta;                 // maior número de área já pré-gerada
    private static BlockPos portal;            // piso do centro do pórtico no mundo principal
    private static int voltaDe;                // número da expedição cujo pórtico de volta já existe
    private static BlockPos volta;             // piso do centro do pórtico de volta
    private static int rotuloDe;
    private static JsonArray historico = new JsonArray();

    private static Path arquivoEstado;
    private static final Set<UUID> PERMITIDO = new HashSet<>();
    private static final Map<UUID, Long> RECARGA = new HashMap<>();
    private static final Set<UUID> NO_MAPA = new HashSet<>();
    private static long ultimaTarefa;
    private static int avisado;                // 10 = aviso de 10 min dado, 1 = aviso de 1 min dado

    // ------------------------------------------------------------------ área

    private static int centroX(int n) { return BASE + ((n - 1) % POR_LINHA) * ESPACO; }
    private static int centroZ(int n) { return BASE + ((n - 1) / POR_LINHA) * ESPACO; }

    public static boolean naExpedicao(Level l) { return l != null && l.dimension() == MUNDO; }

    private static boolean dentro(double x, double z) {
        return Math.abs(x - (centroX(numero) + 0.5)) <= RAIO - 1 && Math.abs(z - (centroZ(numero) + 0.5)) <= RAIO - 1;
    }

    private static long calcularProxima(long agora) {
        ZonedDateTime z = Instant.ofEpochMilli(agora).atZone(FUSO);
        ZonedDateTime base = z.toLocalDate().atTime(LocalTime.of(HORA, 0)).atZone(FUSO);
        if (z.isBefore(base)) base = base.minusDays(1);
        return base.plusDays(DIAS).toInstant().toEpochMilli();
    }

    private static String quando(long ms) { return Instant.ofEpochMilli(ms).atZone(FUSO).format(DATA_HORA); }

    private static String falta(long agora) {
        long m = Math.max(0, (proxima - agora) / 60_000);
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min";
    }

    // ------------------------------------------------------------------ estado

    private static void ler(MinecraftServer s) {
        arquivoEstado = s.getWorldPath(LevelResource.ROOT).resolve("frx_expedicao.json");
        ativa = false; numero = 0; inicio = 0; proxima = 0; pronta = 0; portal = null; voltaDe = 0; volta = null; rotuloDe = 0;
        historico = new JsonArray();
        if (!Files.exists(arquivoEstado)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(arquivoEstado, StandardCharsets.UTF_8)).getAsJsonObject();
            ativa = o.has("ativa") && o.get("ativa").getAsBoolean();
            numero = o.has("numero") ? o.get("numero").getAsInt() : 0;
            inicio = o.has("inicio") ? o.get("inicio").getAsLong() : 0;
            proxima = o.has("proxima") ? o.get("proxima").getAsLong() : 0;
            pronta = o.has("pronta") ? o.get("pronta").getAsInt() : 0;
            portal = pos(o, "portal");
            volta = pos(o, "volta");
            voltaDe = o.has("voltaDe") ? o.get("voltaDe").getAsInt() : 0;
            rotuloDe = o.has("rotuloDe") ? o.get("rotuloDe").getAsInt() : 0;
            if (o.has("historico") && o.get("historico").isJsonArray()) historico = o.getAsJsonArray("historico");
        } catch (Exception e) {
            FrxWorld.LOG.warn("Expedição: não consegui ler {}: {}", arquivoEstado, e.toString());
        }
    }

    private static BlockPos pos(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonArray()) return null;
        JsonArray a = o.getAsJsonArray(k);
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    private static JsonArray arr(BlockPos p) { JsonArray a = new JsonArray(); a.add(p.getX()); a.add(p.getY()); a.add(p.getZ()); return a; }

    private static void gravar() {
        if (arquivoEstado == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("ativa", ativa);
        o.addProperty("numero", numero);
        o.addProperty("inicio", inicio);
        o.addProperty("proxima", proxima);
        o.addProperty("pronta", pronta);
        if (portal != null) o.add("portal", arr(portal));
        if (volta != null) o.add("volta", arr(volta));
        o.addProperty("voltaDe", voltaDe);
        o.addProperty("rotuloDe", rotuloDe);
        o.add("historico", historico);
        try { Files.writeString(arquivoEstado, GSON.toJson(o), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Expedição: não consegui gravar: {}", e.toString()); }
    }

    /** Começa a expedição seguinte (só o estado; quem chama cuida dos jogadores e da borda). */
    private static void avancar(long agora) {
        numero++;
        inicio = agora;
        proxima = calcularProxima(agora);
        JsonObject h = new JsonObject();
        h.addProperty("numero", numero);
        h.addProperty("data", Instant.ofEpochMilli(agora).atZone(FUSO).format(DATA));
        h.addProperty("inicio", agora);
        h.addProperty("centroX", centroX(numero));
        h.addProperty("centroZ", centroZ(numero));
        historico.add(h);
        while (historico.size() > 200) historico.remove(0);
        avisado = 0;
        FrxWorld.LOG.info("Expedição #{} aberta: centro {}, {} (até {})", numero, centroX(numero), centroZ(numero), quando(proxima));
    }

    // ------------------------------------------------------------------ estatísticas

    /** O que cada participante fez na expedição atual. */
    private static final class Ficha {
        String nick = "";
        long primeira, ultima, tempoMs;
        int mortes, chefes;
        final Set<Long> chunks = new HashSet<>();
        final Set<String> estruturas = new HashSet<>();
        final Set<Long> tesouros = new HashSet<>();
    }

    private static final Map<UUID, Ficha> FICHAS = new HashMap<>();
    private static final Set<String> ESTRUTURAS = new HashSet<>();   // da expedição inteira
    private static int fichasDe;                                     // número da expedição dessas fichas
    private static boolean sujo;
    private static Path arquivoFichas;
    private static final Set<String> ESTRUTURAS_IGNORADAS = Set.of("minecraft:mineshaft", "minecraft:mineshaft_mesa", "minecraft:buried_treasure",
            "minecraft:ruined_portal", "minecraft:ruined_portal_desert", "minecraft:ruined_portal_jungle", "minecraft:ruined_portal_swamp",
            "minecraft:ruined_portal_mountain", "minecraft:ruined_portal_ocean", "minecraft:ruined_portal_nether");

    private static Ficha ficha(ServerPlayer p) {
        Ficha f = FICHAS.computeIfAbsent(p.getUUID(), k -> new Ficha());
        long agora = System.currentTimeMillis();
        if (f.primeira == 0) f.primeira = agora;
        f.nick = p.getGameProfile().getName();
        sujo = true;
        return f;
    }

    private static boolean contando(ServerPlayer p) { return ativa && numero > 0 && naExpedicao(p.level()) && !p.isSpectator() && !admin(p); }

    private static void lerFichas(MinecraftServer s) {
        arquivoFichas = s.getWorldPath(LevelResource.ROOT).resolve("frx_expedicao_stats.json");
        FICHAS.clear(); ESTRUTURAS.clear(); fichasDe = 0; sujo = false;
        if (!Files.exists(arquivoFichas)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(arquivoFichas, StandardCharsets.UTF_8)).getAsJsonObject();
            fichasDe = o.get("numero").getAsInt();
            if (o.has("estruturas")) o.getAsJsonArray("estruturas").forEach(e -> ESTRUTURAS.add(e.getAsString()));
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("jogadores").entrySet()) {
                JsonObject j = e.getValue().getAsJsonObject();
                Ficha f = new Ficha();
                f.nick = j.get("nick").getAsString();
                f.primeira = j.get("primeira").getAsLong();
                f.ultima = j.get("ultima").getAsLong();
                f.tempoMs = j.get("tempoMs").getAsLong();
                f.mortes = j.get("mortes").getAsInt();
                f.chefes = j.get("chefes").getAsInt();
                j.getAsJsonArray("chunks").forEach(x -> f.chunks.add(x.getAsLong()));
                j.getAsJsonArray("estruturas").forEach(x -> f.estruturas.add(x.getAsString()));
                j.getAsJsonArray("tesouros").forEach(x -> f.tesouros.add(x.getAsLong()));
                FICHAS.put(UUID.fromString(e.getKey()), f);
            }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Expedição: não consegui ler as estatísticas: {}", e.toString());
            FICHAS.clear(); ESTRUTURAS.clear(); fichasDe = 0;
        }
    }

    private static void gravarFichas() {
        if (arquivoFichas == null || fichasDe == 0) return;
        JsonObject o = new JsonObject();
        o.addProperty("numero", fichasDe);
        JsonArray es = new JsonArray(); ESTRUTURAS.forEach(es::add); o.add("estruturas", es);
        JsonObject js = new JsonObject();
        for (Map.Entry<UUID, Ficha> e : FICHAS.entrySet()) {
            Ficha f = e.getValue();
            JsonObject j = new JsonObject();
            j.addProperty("nick", f.nick); j.addProperty("primeira", f.primeira); j.addProperty("ultima", f.ultima);
            j.addProperty("tempoMs", f.tempoMs); j.addProperty("mortes", f.mortes); j.addProperty("chefes", f.chefes);
            JsonArray c = new JsonArray(); f.chunks.forEach(c::add); j.add("chunks", c);
            JsonArray t = new JsonArray(); f.estruturas.forEach(t::add); j.add("estruturas", t);
            JsonArray q = new JsonArray(); f.tesouros.forEach(q::add); j.add("tesouros", q);
            js.add(e.getKey().toString(), j);
        }
        o.add("jogadores", js);
        try { Files.writeString(arquivoFichas, o.toString(), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Expedição: não consegui gravar as estatísticas: {}", e.toString()); }
    }

    /** Manda ao painel a expedição `n` com as fichas atuais; `fim` > 0 encerra a expedição no site. */
    private static void enviar(MinecraftServer s, int n, long ini, long prox, long fim) {
        if (n <= 0) return;
        JsonObject e = new JsonObject();
        e.addProperty("numero", n);
        e.addProperty("inicio", ini);
        e.addProperty("proxima", prox);
        if (fim > 0) e.addProperty("fim", fim);
        e.addProperty("tamanho", RAIO * 2);
        e.addProperty("tipo", "normal");
        e.addProperty("ativa", ativa);
        e.addProperty("versao", net.minecraftforge.fml.ModList.get().getModContainerById(FrxWorld.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse(""));
        e.addProperty("estruturas", n == fichasDe ? ESTRUTURAS.size() : 0);
        JsonArray agoraLa = new JsonArray();
        ServerLevel nivel = s.getLevel(MUNDO);
        if (fim == 0 && nivel != null) for (ServerPlayer p : nivel.players()) if (!p.isSpectator() && !admin(p)) agoraLa.add(p.getGameProfile().getName());
        e.add("agora", agoraLa);
        JsonArray ps = new JsonArray();
        if (n == fichasDe) for (Map.Entry<UUID, Ficha> x : FICHAS.entrySet()) {
            Ficha f = x.getValue();
            JsonObject j = new JsonObject();
            j.addProperty("uuid", x.getKey().toString());
            j.addProperty("nick", f.nick);
            j.addProperty("primeira", f.primeira);
            j.addProperty("ultima", Math.max(f.primeira, f.ultima));
            j.addProperty("tempoSeg", f.tempoMs / 1000);
            j.addProperty("chunks", f.chunks.size());
            j.addProperty("mortes", f.mortes);
            j.addProperty("chefes", f.chefes);
            j.addProperty("estruturas", f.estruturas.size());
            j.addProperty("tesouros", f.tesouros.size());
            ps.add(j);
        }
        JsonObject corpo = new JsonObject();
        corpo.add("expedicao", e);
        corpo.add("participantes", ps);
        br.com.frxcode.frxworld.acesso.PainelApi.enviarExpedicao(corpo);
    }

    /** Fecha as fichas da expedição que terminou (manda ao site) e começa as da atual. */
    private static void virarFichas(MinecraftServer s, long fimDaAnterior) {
        if (fichasDe != 0 && fichasDe != numero) {
            long ini = inicioDe(fichasDe);
            enviar(s, fichasDe, ini, fimDaAnterior, fimDaAnterior);
        }
        if (fichasDe != numero) {
            FICHAS.clear(); ESTRUTURAS.clear();
            fichasDe = numero;
            sujo = true;
            gravarFichas();
        }
    }

    private static long inicioDe(int n) {
        for (int i = historico.size() - 1; i >= 0; i--) {
            JsonObject h = historico.get(i).getAsJsonObject();
            if (h.get("numero").getAsInt() == n && h.has("inicio")) return h.get("inicio").getAsLong();
        }
        return 0;
    }

    private static void contarJogadores(MinecraftServer s, ServerLevel n, long agora) {
        for (ServerPlayer p : n.players()) {
            if (!contando(p)) continue;
            Ficha f = ficha(p);
            if (f.ultima > 0) f.tempoMs += Math.max(0, Math.min(5000, agora - f.ultima));
            f.ultima = agora;
            f.chunks.add(p.chunkPosition().toLong());
        }
    }

    private static void procurarEstruturas(ServerLevel n) {
        var registro = n.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ServerPlayer p : n.players()) {
            if (!contando(p)) continue;
            BlockPos pe = p.blockPosition();
            for (net.minecraft.world.level.levelgen.structure.Structure st : n.structureManager().getAllStructuresAt(pe).keySet()) {
                ResourceLocation id = registro.getKey(st);
                if (id == null || ESTRUTURAS_IGNORADAS.contains(id.toString())) continue;
                var inicio = n.structureManager().getStructureWithPieceAt(pe, st);
                if (!inicio.isValid()) continue;
                String chave = id + "@" + inicio.getChunkPos().x + "," + inicio.getChunkPos().z;
                if (ficha(p).estruturas.add(chave)) {
                    boolean nova = ESTRUTURAS.add(chave);
                    p.displayClientMessage(Component.literal((nova ? "🏰 Estrutura descoberta: " : "🏰 Estrutura: ") + nomeEstrutura(id)).withStyle(nova ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
                }
            }
        }
    }

    private static String nomeEstrutura(ResourceLocation id) {
        String s = id.getPath().replace('_', ' ').replace('/', ' ');
        return s.isEmpty() ? id.toString() : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Chefão derrotado na expedição: conta para todos que estavam na luta (até 40 blocos). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoMatarChefe(LivingDeathEvent ev) {
        net.minecraft.world.entity.LivingEntity morto = ev.getEntity();
        if (morto instanceof net.minecraft.world.entity.player.Player || !(morto.level() instanceof ServerLevel n) || !naExpedicao(n)) return;
        boolean chefao = Chefes.ehChefao(morto) || morto.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES) || morto.getMaxHealth() >= 200;
        if (!chefao) return;
        for (ServerPlayer p : n.players()) if (contando(p) && p.isAlive() && p.distanceTo(morto) <= 40) ficha(p).chefes++;
    }

    /** Baú de tesouro (Lootr) aberto pela primeira vez por aquele jogador nesta expedição. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoAbrirTesouro(PlayerInteractEvent.RightClickBlock ev) {
        if (ev.isCanceled() || !(ev.getEntity() instanceof ServerPlayer p) || !contando(p)) return;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(ev.getLevel().getBlockState(ev.getPos()).getBlock());
        if (id != null && id.getNamespace().equals("lootr")) ficha(p).tesouros.add(ev.getPos().asLong());
    }

    // ------------------------------------------------------------------ ligar

    /** Antes de o mundo carregar: troca atrasada (servidor estava desligado às 05:00) e limpeza das áreas velhas. */
    @SubscribeEvent
    public void antesDeLigar(ServerAboutToStartEvent ev) {
        MinecraftServer s = ev.getServer();
        ler(s);
        PERMITIDO.clear(); RECARGA.clear(); NO_MAPA.clear(); ultimaTarefa = 0; avisado = 0;
        lerFichas(s);
        if (!ativa || numero == 0) return;
        long agora = System.currentTimeMillis();
        if (agora >= proxima) avancar(agora);
        gravar();
        limparAreasVelhas(s);
    }

    private static void limparAreasVelhas(MinecraftServer s) {
        Path raiz = s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path dim = raiz.resolve("dimensions").resolve(FrxWorld.MOD_ID).resolve("expedicao").normalize();
        if (!dim.startsWith(raiz.resolve("dimensions")) || !dim.endsWith(Path.of("dimensions", FrxWorld.MOD_ID, "expedicao"))) {
            FrxWorld.LOG.error("Expedição: caminho inesperado {}, limpeza cancelada", dim);
            return;
        }
        int apagados = 0;
        long bytes = 0;
        for (String sub : new String[]{"region", "entities", "poi"}) {
            Path pasta = dim.resolve(sub);
            if (!Files.isDirectory(pasta)) continue;
            List<Path> arquivos;
            try (Stream<Path> st = Files.list(pasta)) { arquivos = st.toList(); }
            catch (Exception e) { continue; }
            for (Path a : arquivos) {
                if (!a.toAbsolutePath().normalize().getParent().equals(pasta)) continue;
                Matcher m = ARQUIVO_REGIAO.matcher(a.getFileName().toString());
                if (!m.matches()) continue;
                int rx = Integer.parseInt(m.group(1)), rz = Integer.parseInt(m.group(2));
                if (manter(rx, rz, numero) || manter(rx, rz, numero + 1)) continue;
                try { bytes += Files.size(a); Files.delete(a); apagados++; }
                catch (Exception e) { FrxWorld.LOG.warn("Expedição: não apaguei {}: {}", a, e.toString()); }
            }
        }
        if (apagados > 0) FrxWorld.LOG.info("Expedição: {} arquivos de áreas antigas apagados ({} MB)", apagados, bytes / 1_048_576);
    }

    private static boolean manter(int rx, int rz, int n) {
        int x0 = rx * 512, z0 = rz * 512, folga = 64;
        int cx = centroX(n), cz = centroZ(n);
        return x0 + 511 >= cx - RAIO - folga && x0 <= cx + RAIO + folga && z0 + 511 >= cz - RAIO - folga && z0 <= cz + RAIO + folga;
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        MinecraftServer s = ev.getServer();
        aplicarBorda(s);
        if (ativa && numero > 0) { virarFichas(s, inicio); enviar(s, numero, inicio, proxima, 0); }
    }

    @SubscribeEvent
    public void aoDesligar(net.minecraftforge.event.server.ServerStoppingEvent ev) { gravarFichas(); }

    private static void aplicarBorda(MinecraftServer s) {
        ServerLevel n = s.getLevel(MUNDO);
        if (n == null || numero == 0) return;
        n.getWorldBorder().setCenter(centroX(numero) + 0.5, centroZ(numero) + 0.5);
        n.getWorldBorder().setSize(RAIO * 2);
    }

    // ------------------------------------------------------------------ entrar e sair

    private static int indiceRank(String letra) {
        for (int i = 0; i < ConfigJogo.totalRanks(); i++) if (ConfigJogo.nomeRank(i).equalsIgnoreCase(letra)) return i;
        return 2;
    }

    private static boolean admin(ServerPlayer p) { return p.hasPermissions(2) && p.isCreative(); }

    private static void tentarEntrar(ServerPlayer p, boolean forcar) {
        long agora = System.currentTimeMillis();
        if (!forcar) {
            if (!ativa || numero == 0) { p.displayClientMessage(Component.literal("As Terras Inexploradas ainda não foram abertas.").withStyle(ChatFormatting.RED), true); return; }
            if (proxima - agora <= 60_000) { p.displayClientMessage(Component.literal("A expedição está terminando. A próxima abre em instantes.").withStyle(ChatFormatting.RED), true); return; }
            if (!admin(p) && Guilda.indice(p) < indiceRank(RANK_MINIMO)) {
                p.displayClientMessage(Component.literal("Só aventureiros de Rank " + RANK_MINIMO + " ou mais da Guilda entram nas Terras Inexploradas.").withStyle(ChatFormatting.RED), true);
                return;
            }
            long espera = p.getPersistentData().getLong("frx_exp_bloqueio") - agora;
            if (espera > 0) { p.displayClientMessage(Component.literal("Você caiu na expedição há pouco. Volte em " + (espera / 60_000 + 1) + " min.").withStyle(ChatFormatting.RED), true); return; }
        }
        ServerLevel n = p.server.getLevel(MUNDO);
        if (n == null) { p.sendSystemMessage(Component.literal("O mundo da expedição não carregou. Avise um administrador.").withStyle(ChatFormatting.RED)); return; }
        if (numero == 0) { avancar(agora); gravar(); aplicarBorda(p.server); }
        BlockPos chegada = garantirVolta(n).offset(0, 1, 2);
        p.getPersistentData().putInt("frx_exp_num", numero);
        teleportar(p, n, chegada);
        sujo = true;
        Sistema.avisar(p, "Expedição #" + numero, "Terras Inexploradas. Tudo aqui some na troca, " + quando(proxima)
                + ". Morrer não tira seus itens, mas deixa você 30 min fora. Portais, camas e waystones não funcionam.", Sistema.VERDE);
    }

    private static void voltarAoPrincipal(ServerPlayer p, String motivo) {
        ServerLevel principal = p.server.getLevel(Level.OVERWORLD);
        if (principal == null) return;
        BlockPos d = portal != null ? portal.offset(0, 1, 3) : principal.getSharedSpawnPos();
        sujo = true;
        teleportar(p, principal, d);
        if (motivo != null) p.sendSystemMessage(Component.literal(motivo).withStyle(ChatFormatting.GOLD));
    }

    private static void teleportar(ServerPlayer p, ServerLevel n, BlockPos d) {
        PERMITIDO.add(p.getUUID());
        try {
            RECARGA.put(p.getUUID(), System.currentTimeMillis() + 4000);
            p.teleportTo(n, d.getX() + 0.5, d.getY(), d.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.fallDistance = 0;
            n.sendParticles(ParticleTypes.REVERSE_PORTAL, d.getX() + 0.5, d.getY() + 1, d.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.05);
            n.playSound(null, d, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f);
        } finally {
            PERMITIDO.remove(p.getUUID());
        }
    }

    /** Pórtico de volta no centro da área atual; construído na primeira entrada de cada expedição. */
    private static BlockPos garantirVolta(ServerLevel n) {
        if (volta != null && voltaDe == numero) return volta;
        int x = centroX(numero), z = centroZ(numero);
        n.getChunk(x >> 4, z >> 4);
        int y = Math.max(n.getSeaLevel(), n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)) - 1;
        BlockPos base = new BlockPos(x, Math.min(y, n.getMaxBuildHeight() - 12), z);
        porticoDe(n, base, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        rotulo(n, base, "⇦ Mundo principal", "Pise no centro para voltar");
        volta = base;
        voltaDe = numero;
        gravar();
        return volta;
    }

    /** Pórtico 5×5: piso, quatro pilares com lanterna e o centro brilhante onde se pisa. `base` = piso do centro. */
    private static void porticoDe(ServerLevel n, BlockPos base, BlockState piso, BlockState pilar) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean borda = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            n.setBlock(base.offset(dx, 0, dz), dx == 0 && dz == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : borda ? piso : Blocks.MOSS_BLOCK.defaultBlockState(), 2);
            for (int dy = 1; dy <= 4; dy++) n.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            for (int dy = 1; dy <= 4; dy++) {   // apoio, para não flutuar em morro ou água
                BlockPos b = base.offset(dx, -dy, dz);
                BlockState e = n.getBlockState(b);
                if (!e.isAir() && e.getFluidState().isEmpty() && e.canBeReplaced() == false) break;
                n.setBlock(b, Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
        }
        for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2}) {
            for (int dy = 1; dy <= 3; dy++) n.setBlock(base.offset(dx, dy, dz), pilar, 2);
            n.setBlock(base.offset(dx, 4, dz), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
    }

    private static void rotulo(ServerLevel n, BlockPos p, String linha1, String linha2) {
        for (ArmorStand a : n.getEntitiesOfClass(ArmorStand.class, new AABB(p).inflate(4), e -> e.getTags().contains(TAG_ROTULO))) a.discard();
        String[] linhas = {linha1, linha2};
        for (int i = 0; i < linhas.length; i++) {
            ArmorStand a = EntityType.ARMOR_STAND.create(n);
            if (a == null) continue;
            a.moveTo(p.getX() + 0.5, p.getY() + 2.6 - i * 0.3, p.getZ() + 0.5, 0, 0);
            a.setInvisible(true);
            a.setNoGravity(true);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.getPersistentData().putBoolean("frx_fixo", true);
            a.addTag(TAG_ROTULO);
            a.setCustomName(Component.literal(linhas[i]).withStyle(i == 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY, i == 0 ? ChatFormatting.BOLD : ChatFormatting.ITALIC));
            a.setCustomNameVisible(true);
            n.addFreshEntity(a);
        }
    }

    private static void rotuloDoPortal(ServerLevel principal) {
        if (portal == null) return;
        rotulo(principal, portal, "🌍 Terras Inexploradas", ativa && numero > 0 ? "Expedição #" + numero + " · Rank " + RANK_MINIMO + "+" : "Ainda fechadas");
        rotuloDe = ativa ? numero : -1;
    }

    // ------------------------------------------------------------------ troca

    private static void trocar(MinecraftServer s, long agora) {
        ServerLevel n = s.getLevel(MUNDO);
        if (n != null) for (ServerPlayer p : new ArrayList<>(n.players()))
            voltarAoPrincipal(p, "A expedição #" + numero + " terminou. Você voltou ao mundo principal com tudo o que carregava.");
        avancar(agora);
        gravar();
        aplicarBorda(s);
        virarFichas(s, agora);
        enviar(s, numero, inicio, proxima, 0);
        s.getPlayerList().broadcastSystemMessage(Component.literal("🌍 A expedição #" + numero + " às Terras Inexploradas está aberta, com terras nunca vistas. Próxima troca: "
                + quando(proxima) + ".").withStyle(ChatFormatting.GREEN), false);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        int t = s.getTickCount();
        if (t % 5 != 0) return;
        ServerLevel principal = s.getLevel(Level.OVERWORLD);
        ServerLevel n = s.getLevel(MUNDO);
        long agora = System.currentTimeMillis();

        // pórtico de entrada (mundo principal) e de volta (expedição)
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.isSpectator() || RECARGA.getOrDefault(p.getUUID(), 0L) > agora) continue;
            BlockPos pe = p.blockPosition();
            if (portal != null && p.level() == principal && pe.equals(portal.above())) { RECARGA.put(p.getUUID(), agora + 3000); tentarEntrar(p, false); }
            else if (volta != null && voltaDe == numero && p.level() == n && pe.equals(volta.above())) voltarAoPrincipal(p, "Você voltou das Terras Inexploradas.");
        }

        if (t % 20 != 0 || numero == 0) return;

        if (n != null && ativa) {
            contarJogadores(s, n, agora);
            if (t % 100 == 0) procurarEstruturas(n);
            if (t % 1200 == 0 && sujo) { sujo = false; enviar(s, numero, inicio, proxima, 0); }
            if (t % 6000 == 0) gravarFichas();
        }

        // borda: quem passar é trazido de volta para dentro (a borda do Minecraft é do mundo principal)
        if (n != null) for (ServerPlayer p : n.players()) {
            if (dentro(p.getX(), p.getZ())) continue;
            double cx = centroX(numero) + 0.5, cz = centroZ(numero) + 0.5, lim = RAIO - 3;
            double x = Math.max(cx - lim, Math.min(cx + lim, p.getX())), z = Math.max(cz - lim, Math.min(cz + lim, p.getZ()));
            if (Math.abs(p.getX() - cx) > RAIO + 200 || Math.abs(p.getZ() - cz) > RAIO + 200) { voltarAoPrincipal(p, "Aquela expedição já terminou."); continue; }
            int y = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            p.teleportTo(x, Math.max(p.getY(), y + 1), z);
            p.displayClientMessage(Component.literal("Fim da área desta expedição.").withStyle(ChatFormatting.RED), true);
        }

        if (!ativa) return;

        // rótulo do pórtico desatualizado (troca, abertura) e marca no mapa
        if (principal != null && portal != null && rotuloDe != numero && principal.isLoaded(portal)) { rotuloDoPortal(principal); gravar(); }
        if (portal != null && t % 100 == 0) for (ServerPlayer p : s.getPlayerList().getPlayers())
            if (p.tickCount > 100 && NO_MAPA.add(p.getUUID()))
                br.com.frxcode.frxworld.protecao.MapaJm.mostrar(p, "expedicao_portal", portal.getX() - 4, portal.getZ() - 4, portal.getX() + 4, portal.getZ() + 4, 0x2E7D32, 0.55f,
                        "🌍 Terras Inexploradas", "Portal das Terras Inexploradas (Rank " + RANK_MINIMO + "+)");

        // avisos e troca
        long resta = proxima - agora;
        if (resta <= 10 * 60_000 && resta > 60_000 && avisado == 0) {
            avisado = 10;
            s.getPlayerList().broadcastSystemMessage(Component.literal("🌍 A expedição #" + numero + " termina em 10 minutos. Quem estiver lá volta sozinho ao mundo principal, com tudo o que carrega. Baús e construções de lá somem.").withStyle(ChatFormatting.GOLD), false);
        }
        if (resta <= 60_000 && resta > 0 && avisado < 100) {
            avisado = 100;
            s.getPlayerList().broadcastSystemMessage(Component.literal("🌍 A expedição #" + numero + " termina em 1 minuto. A entrada já fechou.").withStyle(ChatFormatting.RED), false);
        }
        if (resta <= 0) trocar(s, agora);

        // pré-geração: área atual e depois a próxima, só com o servidor vazio
        if (t % 1200 == 0 && s.getPlayerCount() == 0) preGerar(s, agora);
    }

    private static void preGerar(MinecraftServer s, long agora) {
        if (agora - ultimaTarefa < 3 * 60_000) return;
        Path tarefa = FMLPaths.GAMEDIR.get().resolve("config").resolve("chunky").resolve("tasks").resolve(FrxWorld.MOD_ID).resolve("expedicao.properties");
        if (Files.exists(tarefa)) {
            Map<String, String> v = new HashMap<>();
            try {
                for (String l : Files.readAllLines(tarefa, StandardCharsets.ISO_8859_1)) {
                    int i = l.indexOf('=');
                    if (i > 0) v.put(l.substring(0, i).trim(), l.substring(i + 1).trim());
                }
            } catch (Exception e) { return; }
            boolean cancelada = "true".equals(v.get("cancelled"));
            int area = 0;
            try {
                double x = Double.parseDouble(v.getOrDefault("center-x", "0")), z = Double.parseDouble(v.getOrDefault("center-z", "0"));
                for (int k : new int[]{numero, numero + 1}) if (Math.abs(x - centroX(k)) < 2 && Math.abs(z - centroZ(k)) < 2) area = k;
            } catch (NumberFormatException e) { return; }
            if (!cancelada) {
                if (area != 0) return;   // gerando a área certa
                comando(s, "chunky cancel " + MUNDO.location());   // tarefa de uma área que já passou
                ultimaTarefa = agora;
                return;
            }
            if (area > pronta) { pronta = area; gravar(); FrxWorld.LOG.info("Expedição: área #{} pré-gerada", area); }
        }
        int alvo = pronta < numero ? numero : pronta < numero + 1 ? numero + 1 : 0;
        if (alvo == 0) return;
        FrxWorld.LOG.info("Expedição: pré-gerando a área #{} ({}, {})", alvo, centroX(alvo), centroZ(alvo));
        comando(s, "chunky world " + MUNDO.location());
        comando(s, "chunky shape square");
        comando(s, "chunky center " + centroX(alvo) + " " + centroZ(alvo));
        comando(s, "chunky radius " + RAIO);
        comando(s, "chunky start");
        ultimaTarefa = agora;
    }

    private static void comando(MinecraftServer s, String c) {
        try { s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), c); }
        catch (Exception e) { FrxWorld.LOG.warn("Expedição: {} falhou: {}", c, e.toString()); }
    }

    // ------------------------------------------------------------------ regras

    /** Morrer na expedição não é morte: volta ao mundo principal com tudo e fica 30 min sem poder entrar. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoMorrer(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !naExpedicao(p.level())) return;
        ev.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.setAirSupply(p.getMaxAirSupply());
        for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects()))
            if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(e.getEffect());
        if (contando(p)) ficha(p).mortes++;
        p.getPersistentData().putLong("frx_exp_bloqueio", System.currentTimeMillis() + ESPERA_MORTE_MS);
        voltarAoPrincipal(p, null);
        Sistema.avisar(p, "Você caiu na expedição", "Voltou ao mundo principal com todos os seus itens. Pode entrar de novo em 30 minutos.", Sistema.VERMELHO);
    }

    /** Ninguém entra nem sai da expedição por portal de mod, waystone ou comando de outro sistema: só pelos pórticos. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoViajar(EntityTravelToDimensionEvent ev) {
        boolean deLa = naExpedicao(ev.getEntity().level()), paraLa = ev.getDimension() == MUNDO;
        if (!deLa && !paraLa) return;
        if (PERMITIDO.contains(ev.getEntity().getUUID())) return;
        if (ev.getEntity() instanceof ServerPlayer p && admin(p)) return;
        ev.setCanceled(true);
        if (ev.getEntity() instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal(deLa ? "Portais não funcionam nas Terras Inexploradas. Use o pórtico da chegada." : "Só se entra nas Terras Inexploradas pelo pórtico.").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public void aoAcenderPortal(BlockEvent.PortalSpawnEvent ev) {
        if (ev.getLevel() instanceof Level l && naExpedicao(l)) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoUsarBloco(PlayerInteractEvent.RightClickBlock ev) {
        if (!naExpedicao(ev.getLevel())) return;
        BlockState b = ev.getLevel().getBlockState(ev.getPos());
        boolean olhoNoPortal = b.is(Blocks.END_PORTAL_FRAME) && ev.getItemStack().is(Items.ENDER_EYE);
        boolean cama = b.getBlock() instanceof net.minecraft.world.level.block.BedBlock || b.is(Blocks.RESPAWN_ANCHOR);
        if (!olhoNoPortal && !cama) return;
        ev.setCanceled(true);
        if (ev.getEntity() instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal(olhoNoPortal ? "O portal do End não desperta nas Terras Inexploradas." : "Não dá para dormir nem marcar retorno nas Terras Inexploradas.").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public void aoMarcarRetorno(PlayerSetSpawnEvent ev) {
        if (ev.getSpawnLevel() == MUNDO) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoColocar(BlockEvent.EntityPlaceEvent ev) {
        if (!(ev.getLevel() instanceof Level l) || !naExpedicao(l)) return;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(ev.getPlacedBlock().getBlock());
        if (id == null || !id.getNamespace().equals("waystones")) return;
        ev.setCanceled(true);
        if (ev.getEntity() instanceof ServerPlayer p) p.displayClientMessage(Component.literal("Waystones não funcionam nas Terras Inexploradas.").withStyle(ChatFormatting.RED), true);
    }

    private static boolean noPortico(Level l, BlockPos b) {
        BlockPos c = l.dimension() == Level.OVERWORLD ? portal : naExpedicao(l) && voltaDe == numero ? volta : null;
        return c != null && Math.abs(b.getX() - c.getX()) <= 2 && Math.abs(b.getZ() - c.getZ()) <= 2 && b.getY() >= c.getY() - 1 && b.getY() <= c.getY() + 4;
    }

    @SubscribeEvent
    public void aoQuebrar(BlockEvent.BreakEvent ev) {
        if (ev.getLevel() instanceof Level l && noPortico(l, ev.getPos()) && !(ev.getPlayer() instanceof ServerPlayer p && admin(p))) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoExplodir(ExplosionEvent.Detonate ev) {
        ev.getAffectedBlocks().removeIf(b -> noPortico(ev.getLevel(), b));
    }

    /** Quem saiu do jogo dentro de uma expedição que já terminou volta ao mundo principal ao entrar. */
    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !naExpedicao(p.level())) return;
        if (!ativa || p.getPersistentData().getInt("frx_exp_num") != numero || !dentro(p.getX(), p.getZ()))
            voltarAoPrincipal(p, "A expedição em que você estava terminou. Você voltou ao mundo principal com tudo o que carregava.");
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        NO_MAPA.remove(ev.getEntity().getUUID());
        RECARGA.remove(ev.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ comandos

    private static int info(CommandContext<CommandSourceStack> c, boolean detalhe) {
        long agora = System.currentTimeMillis();
        CommandSourceStack f = c.getSource();
        if (!ativa || numero == 0) {
            f.sendSuccess(() -> Component.literal("🌍 As Terras Inexploradas ainda não foram abertas.").withStyle(ChatFormatting.GRAY), false);
            if (!detalhe) return 1;
        } else {
            String data = Instant.ofEpochMilli(inicio).atZone(FUSO).format(DATA);
            f.sendSuccess(() -> Component.literal("╔══════════════════════════╗\n").withStyle(ChatFormatting.DARK_GREEN)
                    .append(Component.literal("      EXPEDIÇÃO #" + numero + "\n").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                    .append(Component.literal("╠══════════════════════════╣\n").withStyle(ChatFormatting.DARK_GREEN))
                    .append(Component.literal(" Aberta em: " + data + "\n Tamanho: " + (RAIO * 2) + " × " + (RAIO * 2) + "\n Troca: " + quando(proxima)
                            + " (em " + falta(agora) + ")\n Acesso: Rank " + RANK_MINIMO + " ou mais\n").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("╚══════════════════════════╝").withStyle(ChatFormatting.DARK_GREEN)), false);
        }
        if (detalhe) {
            String extra = "ativa=" + ativa + " · centro " + centroX(Math.max(1, numero)) + ", " + centroZ(Math.max(1, numero)) + " · pré-gerada até #" + pronta
                    + " · próxima área " + centroX(numero + 1) + ", " + centroZ(numero + 1)
                    + " · pórtico " + (portal == null ? "não colocado" : portal.getX() + ", " + portal.getY() + ", " + portal.getZ())
                    + " · volta " + (volta != null && voltaDe == numero ? volta.getX() + ", " + volta.getY() + ", " + volta.getZ() : "ainda não feita");
            f.sendSuccess(() -> Component.literal(extra).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("expedicao")
                .executes(c -> info(c, false))
                .then(Commands.literal("sair").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!naExpedicao(p.level())) { c.getSource().sendFailure(Component.literal("Você não está numa expedição.")); return 0; }
                    voltarAoPrincipal(p, "Você voltou das Terras Inexploradas.");
                    return 1;
                }))
                .then(Commands.literal("info").requires(c -> c.hasPermission(2)).executes(c -> info(c, true)))
                .then(Commands.literal("ir").requires(c -> c.hasPermission(2)).executes(c -> {
                    tentarEntrar(c.getSource().getPlayerOrException(), true);   // funciona fechada: é o teste antes de abrir
                    return 1;
                }))
                .then(Commands.literal("portal").requires(c -> c.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (p.level().dimension() != Level.OVERWORLD) { c.getSource().sendFailure(Component.literal("O pórtico fica no mundo principal.")); return 0; }
                    ServerLevel principal = (ServerLevel) p.level();
                    if (portal != null && principal.isLoaded(portal))
                        for (ArmorStand a : principal.getEntitiesOfClass(ArmorStand.class, new AABB(portal).inflate(4), e -> e.getTags().contains(TAG_ROTULO))) a.discard();
                    portal = p.blockPosition().below();
                    porticoDe(principal, portal, Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState());
                    rotuloDoPortal(principal);
                    NO_MAPA.clear();
                    gravar();
                    c.getSource().sendSuccess(() -> Component.literal("Pórtico das Terras Inexploradas em " + portal.getX() + ", " + portal.getY() + ", " + portal.getZ() + "."), true);
                    return 1;
                }))
                .then(Commands.literal("abrir").requires(c -> c.hasPermission(2)).executes(c -> {
                    ativa = true;
                    if (numero == 0) avancar(System.currentTimeMillis());
                    gravar();
                    virarFichas(c.getSource().getServer(), System.currentTimeMillis());
                    enviar(c.getSource().getServer(), numero, inicio, proxima, 0);
                    aplicarBorda(c.getSource().getServer());
                    rotuloDe = -2;
                    c.getSource().sendSuccess(() -> Component.literal("Terras Inexploradas abertas: expedição #" + numero + ", troca " + quando(proxima) + "."), true);
                    return 1;
                }))
                .then(Commands.literal("fechar").requires(c -> c.hasPermission(2)).executes(c -> {
                    ativa = false;
                    gravar();
                    ServerLevel n = c.getSource().getServer().getLevel(MUNDO);
                    if (n != null) for (ServerPlayer p : new ArrayList<>(n.players())) voltarAoPrincipal(p, "As Terras Inexploradas foram fechadas.");
                    rotuloDe = -2;
                    c.getSource().sendSuccess(() -> Component.literal("Terras Inexploradas fechadas (a área atual fica guardada)."), true);
                    return 1;
                }))
                .then(Commands.literal("trocar").requires(c -> c.hasPermission(2)).executes(c -> {
                    if (!ativa || numero == 0) { c.getSource().sendFailure(Component.literal("Abra primeiro com /expedicao abrir.")); return 0; }
                    trocar(c.getSource().getServer(), System.currentTimeMillis());
                    c.getSource().sendSuccess(() -> Component.literal("Expedição trocada: agora é a #" + numero + ". As áreas velhas são apagadas no próximo reinício."), true);
                    return 1;
                }))
                .then(Commands.literal("historico").requires(c -> c.hasPermission(2)).executes(c -> {
                    StringBuilder b = new StringBuilder("Últimas expedições:");
                    for (int i = Math.max(0, historico.size() - 10); i < historico.size(); i++) {
                        JsonObject h = historico.get(i).getAsJsonObject();
                        b.append("\n #").append(h.get("numero").getAsInt()).append(" · ").append(h.get("data").getAsString())
                                .append(" · centro ").append(h.get("centroX").getAsInt()).append(", ").append(h.get("centroZ").getAsInt());
                    }
                    c.getSource().sendSuccess(() -> Component.literal(b.toString()), false);
                    return 1;
                })));
    }
}
