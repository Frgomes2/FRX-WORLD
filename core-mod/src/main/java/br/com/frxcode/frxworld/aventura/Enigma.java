package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.Personagens;
import br.com.frxcode.frxworld.armas.Codex;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.protecao.AreasProtegidas;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Enigmas: corridas de pistas pelo mundo. Em cada enigma, só o primeiro personagem a chegar ao fim leva o prêmio (um item do Códex).
 *
 * O conteúdo (pistas, lugares, falas, prêmio) fica fora do código, em arquivos Base64 na pasta do mundo, para ninguém
 * estragar a surpresa lendo o projeto:
 *   <mundo>/frx_enigma.dat              o primeiro enigma (estado em <mundo>/frx_enigma_estado.json);
 *   <mundo>/frx_enigmas/<id>.dat        os demais (estado em <mundo>/frx_enigmas/<id>.estado.json).
 * Vários enigmas podem correr ao mesmo tempo. Campos de cada definição:
 *   nome, abertura (a primeira pista), premio (código do Códex), etapas;
 *   inicio (opcional, epoch em ms): antes dele o enigma fica anunciado (o Diário já mostra a primeira pista e o chat avisa
 *     de hora em hora e, na última hora, a cada 10 minutos), mas ninguém avança; sem inicio, começa assim que o prêmio existe;
 *   grupo (opcional): quem vence um enigma do grupo não avança nem vence os outros do mesmo grupo (uma peça por pessoa);
 *   conjunto / conjuntoNome (opcional): os prêmios do mesmo conjunto, vestidos juntos, dão um efeito (ver Conjuntos).
 * Tipos de etapa: "lugar" (chegar a um ponto, opcionalmente em certo horário), "npc" (falar com um NPC de um reino,
 * opcionalmente levando itens) e "final" (chegar ao último ponto). Uma etapa final com "chefe" (índice do rank, 0 = F ... 6 = S)
 * faz surgir um guardião: o baú do prêmio só aparece quando ele cai diante de quem chegou ao fim.
 */
public class Enigma {
    private static final String DIARIO = "frx_enigma_diario", TAG_GUARDIAO = "frx_enigma_guardiao";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private record Etapa(String tipo, String dim, int x, int y, int z, int raio, String quando, String papel, String reino,
                         String item, int qtd, String fala, String pista, int chefe, int chefeNivel) {}

    private static final class Def {
        String id, nome, abertura, premio, grupo, conjunto, conjuntoNome, area;
        long inicio;
        List<Etapa> etapas;
        JsonObject estado;
        Path arqEstado;
        boolean avisouSemPremio;
    }

    private static final class Guardiao {
        final UUID mob; final String enigma; final ServerLevel nivel;
        long sozinhoDesde; final long criado = System.currentTimeMillis();
        Guardiao(UUID mob, String enigma, ServerLevel nivel) { this.mob = mob; this.enigma = enigma; this.nivel = nivel; }
    }

    private static final List<Def> defs = new ArrayList<>();
    private static final Map<String, Guardiao> guardioes = new ConcurrentHashMap<>();
    private static JsonObject diarios = new JsonObject();
    private static boolean carregado;
    private static long ultimoAviso = -1;
    private int ticks;

    // ------------------------------------------------------------------ arquivos

    private static Path pasta(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT); }
    private static Path pastaEnigmas(MinecraftServer s) { return pasta(s).resolve("frx_enigmas"); }

    private static String txt(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
    private static int num(JsonObject o, String k, int padrao) { return o.has(k) ? o.get(k).getAsInt() : padrao; }

    private static void carregar(MinecraftServer s) {
        carregado = true;
        defs.clear();
        Def antigo = lerDef(pasta(s).resolve("frx_enigma.dat"), "classico", pasta(s).resolve("frx_enigma_estado.json"), "enigma");
        if (antigo != null) defs.add(antigo);
        Path dir = pastaEnigmas(s);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> l = Files.list(dir)) {
                for (Path p : l.filter(f -> f.getFileName().toString().endsWith(".dat")).sorted().toList()) {
                    String id = p.getFileName().toString().replace(".dat", "");
                    Def d = lerDef(p, id, dir.resolve(id + ".estado.json"), "enigma_" + id);
                    if (d != null) defs.add(d);
                }
            } catch (Exception ex) { FrxWorld.LOG.warn("Enigma: não li a pasta frx_enigmas: {}", ex.toString()); }
        }
        diarios = new JsonObject();
        try {
            Path arq = dir.resolve("diarios.json");
            if (Files.exists(arq)) diarios = JsonParser.parseString(Files.readString(arq, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) { FrxWorld.LOG.warn("Enigma: não li diarios.json: {}", ex.toString()); }
        for (Def d : defs)
            FrxWorld.LOG.info("Enigma {}: {} etapas; prêmio no Códex: {}; {}.", d.id, d.etapas.size(), Codex.def(d.premio) != null ? "existe" : "ainda não criado",
                    vencido(d) ? "já desvendado" : iniciado(d) ? "em andamento" : d.inicio > 0 ? "começa em " + quando(d.inicio) : "esperando o prêmio");
    }

    private static Def lerDef(Path arq, String id, Path arqEstado, String area) {
        if (!Files.exists(arq)) return null;
        Def d = new Def();
        try {
            String json = new String(Base64.getMimeDecoder().decode(Files.readString(arq, StandardCharsets.UTF_8).trim()), StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            d.id = id;
            d.area = area;
            d.nome = txt(o, "nome").isEmpty() ? "Enigma" : txt(o, "nome");
            d.abertura = txt(o, "abertura");
            d.premio = txt(o, "premio");
            d.grupo = txt(o, "grupo");
            d.conjunto = txt(o, "conjunto");
            d.conjuntoNome = txt(o, "conjuntoNome").isEmpty() ? d.conjunto : txt(o, "conjuntoNome");
            d.inicio = o.has("inicio") ? o.get("inicio").getAsLong() : 0;
            List<Etapa> l = new ArrayList<>();
            for (JsonElement el : o.getAsJsonArray("etapas")) {
                JsonObject e = el.getAsJsonObject();
                l.add(new Etapa(txt(e, "tipo"), txt(e, "dim").isEmpty() ? "minecraft:overworld" : txt(e, "dim"), num(e, "x", 0), num(e, "y", -999), num(e, "z", 0),
                        num(e, "raio", 6), txt(e, "quando"), txt(e, "papel"), txt(e, "reino"), txt(e, "item"), num(e, "qtd", 0), txt(e, "fala"), txt(e, "pista"),
                        num(e, "chefe", -1), num(e, "chefeNivel", 0)));
            }
            if (l.isEmpty()) return null;
            d.etapas = l;
        } catch (Exception ex) {
            FrxWorld.LOG.warn("Enigma {}: definição inválida: {}", id, ex.toString());
            return null;
        }
        d.arqEstado = arqEstado;
        d.estado = new JsonObject();
        try {
            if (Files.exists(arqEstado)) d.estado = JsonParser.parseString(Files.readString(arqEstado, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) { FrxWorld.LOG.warn("Enigma {}: não li o estado: {}", id, ex.toString()); }
        if (!d.estado.has("progresso")) d.estado.add("progresso", new JsonObject());
        return d;
    }

    private static void gravar(Def d) {
        try { Files.writeString(d.arqEstado, d.estado.toString(), StandardCharsets.UTF_8); }
        catch (Exception ex) { FrxWorld.LOG.warn("Enigma {}: não gravei o estado: {}", d.id, ex.toString()); }
    }

    private static void gravarDiarios(MinecraftServer s) {
        try {
            Files.createDirectories(pastaEnigmas(s));
            Files.writeString(pastaEnigmas(s).resolve("diarios.json"), diarios.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) { FrxWorld.LOG.warn("Enigma: não gravei diarios.json: {}", ex.toString()); }
    }

    // ------------------------------------------------------------------ situação

    private static boolean iniciado(Def d) { return d.estado.has("iniciado"); }
    private static boolean vencido(Def d) { return d.estado.has("vencedor"); }
    private static boolean ativo(Def d) { return iniciado(d) && !vencido(d); }
    /** Anunciado com data, mas ainda não começou: a primeira pista já aparece no Diário. */
    private static boolean anunciado(Def d) { return !iniciado(d) && !vencido(d) && d.inicio > 0; }
    private static boolean visivel(Def d) { return ativo(d) || anunciado(d); }
    private static boolean algumVisivel() { return defs.stream().anyMatch(Enigma::visivel); }

    private static boolean conta(ServerPlayer p) { return p.isAlive() && !p.isCreative() && !p.isSpectator(); }
    private static String id(ServerPlayer p) { return Personagens.id(p).toString(); }

    /**
     * Antes: quem vencia um enigma do grupo não podia fazer os outros (uma peça por pessoa).
     * Decisão do Flavio (22/11): todo mundo pode fazer todos os enigmas. O campo "grupo" continua lido, mas não bloqueia mais.
     */
    private static boolean bloqueado(ServerPlayer p, Def d) {
        if (true) return false;
        if (d.grupo.isEmpty()) return false;
        String eu = id(p);
        for (Def o : defs)
            if (o != d && o.grupo.equals(d.grupo) && vencido(o) && eu.equals(txt(o.estado.getAsJsonObject("vencedor"), "id"))) return true;
        return false;
    }

    private static int etapa(ServerPlayer p, Def d) {
        JsonObject pr = d.estado.getAsJsonObject("progresso");
        String id = id(p);
        return pr.has(id) ? pr.getAsJsonObject(id).get("etapa").getAsInt() : 0;
    }

    /** Pronto para o fim: está na última etapa deste enigma e pode vencê-lo. */
    private static boolean noFim(ServerPlayer p, Def d) {
        return ativo(d) && conta(p) && !bloqueado(p, d) && etapa(p, d) == d.etapas.size() - 1;
    }

    private static String pistaDa(Def d, int i) { return i == 0 ? d.abertura : d.etapas.get(i - 1).pista(); }

    private static boolean horario(Etapa e, ServerLevel n) {
        long t = n.getDayTime() % 24000L;
        return switch (e.quando()) {
            case "noite" -> t >= 13000 && t < 23000;
            case "meianoite" -> t >= 16000 && t < 20000;
            case "dia" -> t < 12000;
            default -> true;
        };
    }

    private static String quando(long ms) { return DATA.format(Instant.ofEpochMilli(ms).atZone(FUSO)); }

    private static String falta(long ms) {
        long min = Math.max(1, (ms - System.currentTimeMillis() + 59_999) / 60_000);
        if (min < 60) return min + (min == 1 ? " minuto" : " minutos");
        long h = min / 60, m = min % 60;
        if (h >= 24) { long dias = h / 24; h %= 24; return dias + (dias == 1 ? " dia" : " dias") + (h > 0 ? " e " + h + "h" : ""); }
        return h + "h" + (m > 0 ? String.format("%02d", m) : "");
    }

    /** Códigos de prêmio de cada conjunto ("conjunto" -> nome e códigos). Usado por Conjuntos. */
    public static Map<String, Map.Entry<String, Set<String>>> conjuntos() {
        Map<String, Map.Entry<String, Set<String>>> m = new LinkedHashMap<>();
        for (Def d : defs) {
            if (d.conjunto.isEmpty() || d.premio.isEmpty()) continue;
            m.computeIfAbsent(d.conjunto, k -> Map.entry(d.conjuntoNome, new HashSet<>())).getValue().add(d.premio);
        }
        return m;
    }

    // ------------------------------------------------------------------ diário

    private static ItemStack diario() {
        ItemStack s = new ItemStack(Items.BOOK);
        s.getOrCreateTag().putBoolean(DIARIO, true);
        s.getOrCreateTag().put("Enchantments", new ListTag());
        s.getTag().getList("Enchantments", 10).add(new CompoundTag());
        s.setHoverName(Component.literal("Diário do Enigma").withStyle(x -> x.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
        ListTag lore = new ListTag();
        for (String l : new String[]{"Guarda as pistas que você já descobriu.", "Clique com ele na mão para ler (ou use /enigma)."})
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(l).withStyle(x -> x.withColor(ChatFormatting.GRAY).withItalic(false)))));
        s.getOrCreateTagElement("display").put("Lore", lore);
        return s;
    }

    private static void darDiario(ServerPlayer p) {
        String id = id(p);
        if (diarios.has(id)) return;
        diarios.addProperty(id, true);
        ItemStack s = diario();
        if (!p.getInventory().add(s)) p.drop(s, false);
        gravarDiarios(p.server);
    }

    /**
     * Abre o Diário como um livro (cliente/TelaDiarioEnigma): um marcador por enigma, com estado e progresso, e as
     * pistas que o jogador já descobriu. Só vão para o cliente as pistas já descobertas (nada é revelado antes).
     */
    private static void abrirDiario(ServerPlayer p) {
        List<Def> lista = defs.stream().filter(d -> visivel(d) || (vencido(d) && d.inicio > 0)).toList();
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        long agora = System.currentTimeMillis();
        for (Def d : lista) {
            CompoundTag e = new CompoundTag();
            e.putString("id", d.id);
            e.putString("nome", d.nome);
            e.putInt("total", d.etapas.size());
            int estado = vencido(d) ? 2 : !iniciado(d) ? 0 : bloqueado(p, d) ? 3 : 1;   // 0 aguardando, 1 em andamento, 2 desvendado, 3 não responde mais a você
            e.putInt("estado", estado);
            if (estado == 2) e.putString("vencedor", txt(d.estado.getAsJsonObject("vencedor"), "nome"));
            if (estado == 0) { e.putString("quando", quando(d.inicio)); e.putLong("faltaMs", Math.max(0, d.inicio - agora)); }
            int atual = estado == 1 ? etapa(p, d) : estado == 0 ? 0 : -1;
            e.putInt("atual", Math.max(0, atual));
            ListTag pistas = new ListTag();
            if (atual >= 0) for (int i = 0; i <= atual && i < d.etapas.size(); i++) pistas.add(StringTag.valueOf(pistaDa(d, i)));
            e.put("pistas", pistas);
            e.putBoolean("guardiao", estado == 1 && atual == d.etapas.size() - 1 && d.etapas.get(d.etapas.size() - 1).chefe() >= 0);
            l.add(e);
        }
        t.put("enigmas", l);
        br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new br.com.frxcode.frxworld.rede.AbrirMenu("enigma", t));
    }

    /** Tem o Diário na mochila ou no baú de Ender. */
    private static boolean temDiario(ServerPlayer p) {
        for (net.minecraft.world.Container c : new net.minecraft.world.Container[]{p.getInventory(), p.getEnderChestInventory()})
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack it = c.getItem(i);
                if (it.is(Items.BOOK) && it.hasTag() && it.getTag().getBoolean(DIARIO)) return true;
            }
        return false;
    }

    /** /enigma: entrega o Diário a quem não tem e abre o livro. */
    @SubscribeEvent
    public void comandos(net.minecraftforge.event.RegisterCommandsEvent ev) {
        ev.getDispatcher().register(net.minecraft.commands.Commands.literal("enigma").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (!carregado) carregar(p.server);
            if (!algumVisivel()) { p.sendSystemMessage(Component.literal("Nenhum enigma desperto agora. Fique de olho nos avisos do chat.").withStyle(ChatFormatting.GRAY)); return 1; }
            if (!temDiario(p)) {
                ItemStack s = diario();
                if (!p.getInventory().add(s)) p.drop(s, false);
                diarios.addProperty(id(p), true);
                gravarDiarios(p.server);
                p.sendSystemMessage(Component.literal("✦ Você recebeu o Diário do Enigma. Clique com ele na mão para abrir.").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            abrirDiario(p);
            return 1;
        }));
    }

    private static void ler(ServerPlayer p) {
        List<Def> lista = defs.stream().filter(d -> visivel(d) || (vencido(d) && d.inicio > 0)).toList();
        p.sendSystemMessage(Component.literal("══════ Diário do Enigma ══════").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        if (lista.isEmpty()) { p.sendSystemMessage(Component.literal("As páginas ainda estão em branco.").withStyle(ChatFormatting.GRAY)); return; }
        for (Def d : lista) {
            p.sendSystemMessage(Component.literal("── " + d.nome + " ──").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (vencido(d)) {
                p.sendSystemMessage(Component.literal("Desvendado por " + txt(d.estado.getAsJsonObject("vencedor"), "nome") + ". O tesouro tem dono.").withStyle(ChatFormatting.GRAY));
                continue;
            }
            if (!iniciado(d)) {
                p.sendSystemMessage(Component.literal("➤ Pista 1: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(d.abertura).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
                p.sendSystemMessage(Component.literal("Começa " + quando(d.inicio) + " (faltam " + falta(d.inicio) + "). Antes disso, nada responde.").withStyle(ChatFormatting.DARK_PURPLE));
                continue;
            }
            if (bloqueado(p, d)) {
                p.sendSystemMessage(Component.literal("Você já carrega uma relíquia deste conjunto. Este enigma não responde mais a você.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                continue;
            }
            int atual = etapa(p, d);
            for (int i = 0; i <= atual && i < d.etapas.size(); i++) {
                boolean ultima = i == atual;
                p.sendSystemMessage(Component.literal((ultima ? "➤ Pista " : "   Pista ") + (i + 1) + ": ").withStyle(ultima ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)
                        .append(Component.literal(pistaDa(d, i)).withStyle(ultima ? ChatFormatting.WHITE : ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            }
            p.sendSystemMessage(Component.literal("Você desvendou " + atual + " de " + d.etapas.size() + " passos.").withStyle(ChatFormatting.DARK_PURPLE));
        }
        p.sendSystemMessage(Component.literal("Só o primeiro a chegar ao fim de cada enigma leva o tesouro.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ avançar e vencer

    private static void avancar(ServerPlayer p, Def d, int i, BlockPos onde) {
        Etapa e = d.etapas.get(i);
        JsonObject eu = new JsonObject();
        eu.addProperty("etapa", i + 1);
        eu.addProperty("nome", Personagens.nome(p));
        eu.addProperty("quando", System.currentTimeMillis());
        d.estado.getAsJsonObject("progresso").add(id(p), eu);
        FrxWorld.LOG.info("Enigma {}: {} cumpriu o passo {} de {}.", d.id, Personagens.nome(p), i + 1, d.etapas.size());
        if (!e.fala().isEmpty() && !(e.tipo().equals("final") && e.chefe() >= 0)) p.sendSystemMessage(Component.literal(e.fala()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        if (e.tipo().equals("final")) { vencer(p, d, e, onde); gravar(d); return; }
        gravar(d);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 0.8f);
        Sistema.avisar(p, d.nome + " · pista " + (i + 2) + " de " + d.etapas.size(), pistaDa(d, i + 1), 0xC77DFF);
        p.sendSystemMessage(Component.literal("✦ Nova pista (" + d.nome + "): ").withStyle(ChatFormatting.GOLD).append(Component.literal(pistaDa(d, i + 1)).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p) o.sendSystemMessage(Component.literal("✦ Alguém acaba de desvendar o passo " + (i + 1) + " de " + d.etapas.size() + " do " + d.nome + ".").withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static void vencer(ServerPlayer p, Def d, Etapa e, BlockPos onde) {
        ServerLevel n = p.serverLevel();
        BlockPos b;
        if (onde != null) b = onde;
        else {
            b = n.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(e.x(), 0, e.z()));
            if (Math.abs(b.getY() - p.getBlockY()) > 6) b = p.blockPosition().relative(p.getDirection(), 2);
        }
        if (!n.getBlockState(b).isAir() && !n.getBlockState(b).canBeReplaced()) b = p.blockPosition().relative(p.getDirection(), 2);
        n.setBlock(b, Blocks.CHEST.defaultBlockState(), 3);
        Codex.Def def = Codex.def(d.premio);
        ItemStack premio = def == null ? ItemStack.EMPTY : Codex.emitir(p, def);
        if (n.getBlockEntity(b) instanceof ChestBlockEntity bau) {
            if (!premio.isEmpty()) bau.setItem(13, premio);
            bau.setItem(4, new ItemStack(Items.NETHER_STAR));
        } else if (!premio.isEmpty() && !p.getInventory().add(premio)) p.drop(premio, false);
        LightningBolt raio = EntityType.LIGHTNING_BOLT.create(n);
        if (raio != null) { raio.moveTo(Vec3.atBottomCenterOf(b)); raio.setVisualOnly(true); n.addFreshEntity(raio); }
        JsonObject v = new JsonObject();
        v.addProperty("nome", Personagens.nome(p));
        v.addProperty("id", id(p));
        v.addProperty("quando", System.currentTimeMillis());
        d.estado.add("vencedor", v);
        AreasProtegidas.registrar(n, d.area, p);
        FrxWorld.LOG.info("Enigma {}: desvendado por {}; prêmio {} {}.", d.id, Personagens.nome(p), d.premio, premio.isEmpty() ? "NÃO entregue (não existe ou acabou)" : "entregue no baú");
        Sistema.avisar(p, d.nome + " desvendado!", "Um baú surgiu diante de você. O tesouro é seu.", 0xFFD166);
        n.playSound(null, b, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1f);
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + Personagens.nome(p) + " desvendou o " + d.nome + " e encontrou o tesouro escondido! ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    // ------------------------------------------------------------------ guardião do fim

    private static boolean guardiaoVivo(Def d) {
        Guardiao g = guardioes.get(d.id);
        if (g == null) return false;
        Entity m = g.nivel.getEntity(g.mob);
        return m != null && m.isAlive();
    }

    private static void despertarGuardiao(ServerPlayer p, Def d, Etapa e) {
        ServerLevel n = p.serverLevel();
        Vec3 olhar = p.getLookAngle();
        int gx = (int) Math.floor(p.getX() + olhar.x * 6), gz = (int) Math.floor(p.getZ() + olhar.z * 6);
        BlockPos chao = n.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(gx, 0, gz));
        if (Math.abs(chao.getY() - p.getBlockY()) > 8) chao = p.blockPosition();
        int rank = Math.max(0, Math.min(6, e.chefe()));
        int nivel = e.chefeNivel() > 0 ? e.chefeNivel() : ConfigJogo.nivelMinDoRank(rank) + 4;
        Mob m = Chefes.criar(n, new Vec3(chao.getX() + 0.5, chao.getY(), chao.getZ() + 0.5), rank, nivel, 0.8);
        if (m == null) {   // sem chefão possível: o fim vale como antes
            avancar(p, d, d.etapas.size() - 1, null);
            return;
        }
        m.addTag(TAG_GUARDIAO);
        m.getPersistentData().putString("frx_enigma", d.id);
        m.setCustomName(Component.literal("☠ Guardião do " + d.nome).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        m.setTarget(p);
        Guardiao g = new Guardiao(m.getUUID(), d.id, n);
        guardioes.put(d.id, g);
        LightningBolt raio = EntityType.LIGHTNING_BOLT.create(n);
        if (raio != null) { raio.moveTo(m.position()); raio.setVisualOnly(true); n.addFreshEntity(raio); }
        FrxWorld.LOG.info("Enigma {}: {} despertou o guardião (rank {}, nível {}).", d.id, Personagens.nome(p), ConfigJogo.nomeRank(rank), nivel);
        if (!d.etapas.get(d.etapas.size() - 1).fala().isEmpty())
            p.sendSystemMessage(Component.literal(d.etapas.get(d.etapas.size() - 1).fala()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        Sistema.avisar(p, "O guardião despertou!", "Derrote-o para que o tesouro se revele.", 0xB5179E);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p) o.sendSystemMessage(Component.literal("✦ Alguém chegou ao fim do " + d.nome + " e despertou o seu guardião!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
    }

    /** Guardião sem ninguém por perto que possa vencer, por 60 segundos, volta a dormir (some). */
    private static void cuidarGuardioes() {
        long agora = System.currentTimeMillis();
        for (Guardiao g : List.copyOf(guardioes.values())) {
            Def d = defs.stream().filter(x -> x.id.equals(g.enigma)).findFirst().orElse(null);
            Entity m = g.nivel.getEntity(g.mob);
            if (m == null || !m.isAlive()) {
                if (agora - g.criado > 10_000) guardioes.remove(g.enigma, g);
                continue;
            }
            boolean alguem = d != null && g.nivel.players().stream().anyMatch(p -> p.distanceTo(m) <= 48 && noFim(p, d));
            if (alguem) { g.sozinhoDesde = 0; continue; }
            if (g.sozinhoDesde == 0) g.sozinhoDesde = agora;
            else if (agora - g.sozinhoDesde > 60_000 || d == null || !ativo(d)) {
                m.discard();
                guardioes.remove(g.enigma, g);
                FrxWorld.LOG.info("Enigma {}: o guardião voltou a dormir.", g.enigma);
            }
        }
    }

    /**
     * Depois que todos os enigmas acabam, o Diário do Enigma some da mochila (e do baú de Ender) de quem
     * entrar ou estiver online.
     */
    private static void recolherDiarios(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            int n = 0;
            for (net.minecraft.world.Container c : new net.minecraft.world.Container[]{p.getInventory(), p.getEnderChestInventory()})
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack it = c.getItem(i);
                    if (it.is(Items.BOOK) && it.hasTag() && it.getTag().getBoolean(DIARIO)) { c.setItem(i, ItemStack.EMPTY); n++; }
                }
            if (n > 0) p.sendSystemMessage(Component.literal("O Diário do Enigma se desfez em pó: todos os enigmas foram desvendados.")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        if (diarios.size() > 0) { diarios = new JsonObject(); gravarDiarios(s); }
    }

    /** Chamado pelos NPCs dos reinos antes do menu deles. true = o enigma tratou o clique. */
    public static boolean aoFalar(ServerPlayer p, Entity npc) {
        if (!conta(p)) return false;
        String papel = npc.getPersistentData().getString("frx_papel"), reino = npc.getPersistentData().getString("frx_reino");
        for (Def d : defs) {
            if (!visivel(d) || bloqueado(p, d)) continue;
            int i = anunciado(d) ? 0 : etapa(p, d);
            if (i >= d.etapas.size()) continue;
            Etapa e = d.etapas.get(i);
            if (!e.tipo().equals("npc") || !e.papel().equals(papel) || !e.reino().equals(reino)) continue;
            if (anunciado(d)) {
                p.sendSystemMessage(Component.literal("\"Ainda não é a hora. Volte no dia marcado.\"").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                return true;
            }
            if (!horario(e, p.serverLevel())) continue;
            if (!e.item().isEmpty() && e.qtd() > 0) {
                Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(e.item()));
                if (it == null) continue;
                int tem = 0;
                for (ItemStack s : p.getInventory().items) if (s.is(it)) tem += s.getCount();
                if (tem < e.qtd()) {
                    p.sendSystemMessage(Component.literal("\"Vejo nos seus olhos que você procura algo... mas as suas mãos ainda estão vazias.\"").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                    return true;
                }
                int falta = e.qtd();
                for (ItemStack s : p.getInventory().items) {
                    if (falta <= 0) break;
                    if (!s.is(it)) continue;
                    int q = Math.min(falta, s.getCount());
                    s.shrink(q);
                    falta -= q;
                }
            }
            avancar(p, d, i, null);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ eventos

    private static void comecar(MinecraftServer s) {
        List<Def> agora = new ArrayList<>();
        long t = System.currentTimeMillis();
        for (Def d : defs) {
            if (iniciado(d) || vencido(d)) continue;
            if (d.inicio > 0 ? t < d.inicio : false) continue;
            if (Codex.def(d.premio) == null) {
                if (!d.avisouSemPremio) { d.avisouSemPremio = true; FrxWorld.LOG.warn("Enigma {}: chegou a hora, mas o prêmio {} não existe no Códex. Esperando.", d.id, d.premio); }
                continue;
            }
            d.estado.addProperty("iniciado", t);
            gravar(d);
            agora.add(d);
            FrxWorld.LOG.info("Enigma {}: começou.", d.id);
        }
        if (agora.isEmpty()) return;
        String nomes = String.join(", ", agora.stream().map(d -> d.nome).toList());
        String msg = agora.size() == 1 ? "O " + nomes + " despertou! Leia o Diário do Enigma: só o primeiro a chegar ao fim levará o tesouro."
                : "Os enigmas despertaram: " + nomes + ". Leia o Diário do Enigma: só o primeiro a desvendar cada um levará o seu tesouro.";
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Sistema.avisar(p, "Os enigmas despertaram", "Leia o Diário do Enigma.", 0xC77DFF);
            darDiario(p);
            p.level().playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        s.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + msg + " ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
    }

    /** Avisos antes do início: de hora em hora e, na última hora, a cada 10 minutos (e a 5 e a 1 minuto). */
    private static void avisarEvento(MinecraftServer s) {
        long t = System.currentTimeMillis();
        List<Def> pendentes = defs.stream().filter(d -> anunciado(d) && d.inicio > t).toList();
        if (pendentes.isEmpty()) return;
        long inicio = pendentes.stream().mapToLong(d -> d.inicio).min().getAsLong();
        long min = (inicio - t + 59_999) / 60_000;
        boolean hora = min > 60 ? min % 60 == 0 : (min % 10 == 0 || min == 5 || min == 1);
        if (!hora || min == ultimoAviso) return;
        ultimoAviso = min;
        long qtd = pendentes.stream().filter(d -> d.inicio == inicio).count();
        s.getPlayerList().broadcastSystemMessage(Component.literal("✦ Evento: " + (qtd == 1 ? "um enigma desperta" : qtd + " enigmas despertam") + " " + quando(inicio)
                + " (faltam " + falta(inicio) + "). Cada um guarda uma relíquia única: só o primeiro a desvendá-lo a leva. A primeira pista já está no Diário do Enigma (perdeu o seu? use /enigma). ✦")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++ticks % 20 != 9) return;
        MinecraftServer s = ev.getServer();
        if (!carregado) carregar(s);
        if (defs.isEmpty()) return;
        if (!algumVisivel() && defs.stream().allMatch(d -> vencido(d) || (d.inicio == 0 && !iniciado(d)))) {
            if (defs.stream().anyMatch(Enigma::vencido)) recolherDiarios(s);
        }
        comecar(s);
        avisarEvento(s);
        cuidarGuardioes();
        if (ticks % 1200 == 9 && algumVisivel()) for (ServerPlayer p : s.getPlayerList().getPlayers()) darDiario(p);
        for (Def d : defs) {
            if (!ativo(d)) continue;
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (!conta(p) || bloqueado(p, d)) continue;
                int i = etapa(p, d);
                if (i >= d.etapas.size()) continue;
                Etapa e = d.etapas.get(i);
                if (e.tipo().equals("npc") || !p.level().dimension().location().toString().equals(e.dim())) continue;
                double dx = p.getX() - (e.x() + 0.5), dz = p.getZ() - (e.z() + 0.5);
                if (dx * dx + dz * dz > (double) e.raio() * e.raio()) continue;
                if (e.y() != -999 && Math.abs(p.getY() - e.y()) > 12) continue;
                if (!horario(e, p.serverLevel())) continue;
                if (e.tipo().equals("final") && e.chefe() >= 0) {
                    if (!guardiaoVivo(d)) despertarGuardiao(p, d, e);
                    continue;
                }
                avancar(p, d, i, null);
                if (vencido(d)) break;
            }
        }
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        Entity m = ev.getEntity();
        if (!m.getTags().contains(TAG_GUARDIAO) || m.level().isClientSide()) return;
        String eid = m.getPersistentData().getString("frx_enigma");
        guardioes.remove(eid);
        Def d = defs.stream().filter(x -> x.id.equals(eid)).findFirst().orElse(null);
        if (d == null || !ativo(d)) return;
        ServerPlayer quem = ev.getSource().getEntity() instanceof ServerPlayer sp && noFim(sp, d) ? sp : null;
        if (quem == null) {
            double melhor = 40 * 40;
            for (ServerPlayer p : ((ServerLevel) m.level()).players())
                if (noFim(p, d) && p.distanceToSqr(m) < melhor) { melhor = p.distanceToSqr(m); quem = p; }
        }
        if (quem == null) return;
        BlockPos onde = BlockPos.containing(m.position());
        avancar(quem, d, d.etapas.size() - 1, onde);
    }

    /** Guardião que sobrou de antes de um reinício (ou que voltou a carregar sem dono) some. */
    @SubscribeEvent
    public void aoSurgir(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !ev.getEntity().getTags().contains(TAG_GUARDIAO)) return;
        UUID u = ev.getEntity().getUUID();
        if (guardioes.values().stream().noneMatch(g -> g.mob.equals(u))) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        if (!carregado) carregar(p.server);
        if (!algumVisivel()) return;
        darDiario(p);
        long t = System.currentTimeMillis();
        long proximo = defs.stream().filter(d -> anunciado(d) && d.inicio > t).mapToLong(d -> d.inicio).min().orElse(0);
        long ativos = defs.stream().filter(Enigma::ativo).count();
        if (proximo > 0)
            p.sendSystemMessage(Component.literal("✦ Evento de enigmas " + quando(proximo) + " (faltam " + falta(proximo) + "). Relíquias únicas esperam por quem desvendá-los. A primeira pista já está no Diário do Enigma (use /enigma para abrir).")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        if (ativos > 0)
            p.sendSystemMessage(Component.literal("✦ " + (ativos == 1 ? "Há um enigma sem dono" : "Há " + ativos + " enigmas sem dono") + ". Use /enigma ou clique com o Diário na mão para ler as suas pistas.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @SubscribeEvent
    public void aoUsar(PlayerInteractEvent.RightClickItem ev) {
        ItemStack s = ev.getItemStack();
        if (!s.is(Items.BOOK) || !s.hasTag() || !s.getTag().getBoolean(DIARIO)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.SUCCESS);
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        if (!carregado) carregar(p.server);
        abrirDiario(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        for (Guardiao g : guardioes.values()) {
            Entity m = g.nivel.getEntity(g.mob);
            if (m != null) m.discard();
        }
        guardioes.clear();
        carregado = false;
        defs.clear();
        ultimoAviso = -1;
    }
}
