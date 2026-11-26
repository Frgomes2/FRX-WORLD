package br.com.frxcode.frxworld.protecao;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pontos públicos do JourneyMap. O jogador marca o ponto no próprio JourneyMap, como sempre, e coloca no grupo
 * "Público" (o mod cria esse grupo para cada um) ou começa o nome com "*". O ponto passa a aparecer no mapa de
 * todos (desenho do nosso mod, como os reinos). Quem marcou apaga no JourneyMap dele e some para todos; os outros
 * não conseguem apagar (o ponto é do dono). Limite pelo rank da Guilda: F 3, E 4, D 5, C 6, B 7, A 8, S 10.
 * Só no mundo normal. O mod lê os pontos que o JourneyMap guarda no servidor (API de servidor, por reflexão).
 * Guardado em <mundo>/frx_pontos_mapa.json para continuar aparecendo com o dono fora do jogo.
 *
 * Admin: /frx mapa publicos (lista) · /frx mapa apagarpublico <n> · /frx mapa pontos · /frx mapa limparpontos
 * (pontos globais do próprio JourneyMap, que só admin cria).
 */
public class PontosMapa {
    private static final int[] LIMITE_POR_RANK = {3, 4, 5, 6, 7, 8, 10};
    private static final String GRUPO = "Público";
    private static final Set<String> APAGAR_GLOBAIS = Set.of("nação de lubilius", "dinastia feiticeira de sarion", "imperio lest distrito sakura", "dwargon",
            "tempest", "nações do oeste", "aqui", "teste", "nao va cereias");

    /** Ponto público de um dono: guid do JourneyMap, nome, posição e cor. */
    private record Ponto(String guid, String nome, int x, int y, int z, int cor) { }
    private record Dono(String nome, List<Ponto> pontos) { }

    private static final Map<UUID, Dono> DONOS = new LinkedHashMap<>();
    private static final Map<UUID, Set<String>> DESENHADOS = new HashMap<>();   // jogador → ids de desenho que ele já recebeu
    private static final Set<UUID> AVISADO_LIMITE = new HashSet<>();
    private static Path arquivo;

    // ------------------------------------------------------------------ JourneyMap (reflexão)

    private static Object api() throws Exception { return Class.forName("journeymap.api.server.ServerAPI").getField("INSTANCE").get(null); }

    private static Object chamar(Object alvo, String metodo, Class<?>[] tipos, Object... args) throws Exception {
        return alvo.getClass().getMethod(metodo, tipos).invoke(alvo, args);
    }

    private static String texto(Object o, String metodo) {
        try { Object v = o.getClass().getMethod(metodo).invoke(o); return v == null ? "" : String.valueOf(v); } catch (Throwable t) { return ""; }
    }

    private static int inteiro(Object o, String metodo) {
        try { return ((Number) o.getClass().getMethod(metodo).invoke(o)).intValue(); } catch (Throwable t) { return 0; }
    }

    /** Garante o grupo "Público" na lista de grupos do jogador e devolve os guids dos grupos com esse nome. */
    private static Set<String> gruposPublicos(Object api, ServerPlayer p) throws Exception {
        Set<String> ids = new HashSet<>();
        Object grupos = chamar(api, "getAllGroups", new Class<?>[]{UUID.class}, p.getUUID());
        if (grupos instanceof Collection<?> c) for (Object g : c) if (texto(g, "getName").trim().equalsIgnoreCase(GRUPO) || texto(g, "getName").trim().equalsIgnoreCase("Publico")) ids.add(texto(g, "getGuid"));
        if (ids.isEmpty()) {
            Object novo = Class.forName("journeymap.api.v2.common.waypoint.WaypointFactory").getMethod("createWaypointGroup", String.class, String.class).invoke(null, "journeymap", GRUPO);
            try { novo.getClass().getMethod("setDescription", String.class).invoke(novo, "Pontos deste grupo aparecem no mapa de todos os jogadores"); } catch (Throwable ignorado) { }
            for (java.lang.reflect.Method m : api.getClass().getMethods())
                if (m.getName().equals("addPlayerGroup") && m.getParameterCount() == 2) { m.invoke(api, p.getUUID(), novo); break; }
            ids.add(texto(novo, "getGuid"));
            FrxWorld.LOG.info("Pontos do mapa: grupo Público criado para {}", p.getGameProfile().getName());
        }
        return ids;
    }

    /** Lê do JourneyMap os pontos públicos de um jogador online. */
    private static List<Ponto> lerPublicos(Object api, ServerPlayer p) throws Exception {
        Set<String> grupos = gruposPublicos(api, p);
        List<Ponto> l = new ArrayList<>();
        Object todos = chamar(api, "getWaypoints", new Class<?>[]{ServerPlayer.class}, p);
        if (!(todos instanceof Collection<?> c)) return l;
        for (Object w : c) {
            String nome = texto(w, "getName").trim();
            boolean publico = grupos.contains(texto(w, "getGroupId")) || nome.startsWith("*");
            if (!publico || !texto(w, "getPrimaryDimension").equals("minecraft:overworld")) continue;
            if (nome.startsWith("*")) nome = nome.substring(1).trim();
            if (nome.isEmpty()) nome = "Ponto";
            if (nome.length() > 32) nome = nome.substring(0, 32);
            l.add(new Ponto(texto(w, "getGuid"), nome, inteiro(w, "getX"), inteiro(w, "getY"), inteiro(w, "getZ"), inteiro(w, "getColor") & 0xFFFFFF));
        }
        return l;
    }

    // ------------------------------------------------------------------ regras

    private static int limite(ServerPlayer p) {
        if (p.hasPermissions(2) && p.isCreative()) return 99;
        int i = br.com.frxcode.frxworld.progressao.Guilda.indice(p);
        return LIMITE_POR_RANK[Math.max(0, Math.min(LIMITE_POR_RANK.length - 1, i))];
    }

    private static String idDesenho(UUID dono, Ponto pt) { return "pub_" + dono.toString().substring(0, 8) + "_" + pt.guid(); }

    /** Manda a cada jogador online o que mudou (desenha os novos, tira os que sumiram). O dono não recebe os dele (já vê no JourneyMap). */
    private static void sincronizar(MinecraftServer s) {
        Map<String, String[]> atuais = new HashMap<>();   // id → {dono, rótulo, título}
        Map<String, int[]> pos = new HashMap<>();
        for (Map.Entry<UUID, Dono> e : DONOS.entrySet())
            for (Ponto pt : e.getValue().pontos()) {
                String id = idDesenho(e.getKey(), pt);
                atuais.put(id, new String[]{e.getKey().toString(), "📍 " + pt.nome(), pt.nome() + " · de " + e.getValue().nome()});
                pos.put(id, new int[]{pt.x(), pt.z(), pt.cor() == 0 ? 0xFFFFFF : pt.cor()});
            }
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.tickCount < 100) continue;
            Set<String> ja = DESENHADOS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            for (String id : new ArrayList<>(ja)) if (!atuais.containsKey(id)) { MapaJm.remover(p, id); ja.remove(id); }
            for (Map.Entry<String, String[]> e : atuais.entrySet()) {
                if (ja.contains(e.getKey()) || e.getValue()[0].equals(p.getUUID().toString())) continue;
                int[] q = pos.get(e.getKey());
                MapaJm.mostrar(p, e.getKey(), q[0] - 3, q[1] - 3, q[0] + 3, q[1] + 3, q[2], 0.6f, e.getValue()[1], e.getValue()[2]);
                ja.add(e.getKey());
            }
        }
    }

    /** Relê os pontos públicos de quem está online e guarda; quem passou do limite do rank fica só com os primeiros. */
    private static void atualizar(MinecraftServer s) {
        Object api;
        try { api = api(); } catch (Throwable t) { return; }
        boolean mudou = false;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.tickCount < 100) continue;
            List<Ponto> l;
            try { l = lerPublicos(api, p); } catch (Throwable t) { continue; }
            int max = limite(p);
            if (l.size() > max) {
                if (AVISADO_LIMITE.add(p.getUUID()))
                    p.sendSystemMessage(Component.literal("📍 Você tem " + l.size() + " pontos públicos, mas o Rank " + br.com.frxcode.frxworld.progressao.Guilda.rank(p)
                            + " mostra só " + max + " para os outros. Subir de rank libera mais.").withStyle(ChatFormatting.YELLOW));
                l = new ArrayList<>(l.subList(0, max));
            } else AVISADO_LIMITE.remove(p.getUUID());
            Dono antes = DONOS.get(p.getUUID());
            if (antes == null ? !l.isEmpty() : !antes.pontos().equals(l)) {
                if (l.isEmpty()) DONOS.remove(p.getUUID()); else DONOS.put(p.getUUID(), new Dono(p.getGameProfile().getName(), l));
                mudou = true;
            }
        }
        if (mudou) gravar();
        sincronizar(s);
    }

    // ------------------------------------------------------------------ arquivo

    private static void ler(MinecraftServer s) {
        arquivo = s.getWorldPath(LevelResource.ROOT).resolve("frx_pontos_mapa.json");
        DONOS.clear(); DESENHADOS.clear();
        if (!Files.exists(arquivo)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(arquivo, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.has("donos")) return;
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("donos").entrySet()) {
                JsonObject d = e.getValue().getAsJsonObject();
                List<Ponto> l = new ArrayList<>();
                for (var x : d.getAsJsonArray("pontos")) {
                    JsonObject j = x.getAsJsonObject();
                    l.add(new Ponto(j.get("guid").getAsString(), j.get("nome").getAsString(), j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt(), j.get("cor").getAsInt()));
                }
                DONOS.put(UUID.fromString(e.getKey()), new Dono(d.get("nome").getAsString(), l));
            }
        } catch (Exception e) { FrxWorld.LOG.warn("Pontos do mapa: não consegui ler: {}", e.toString()); }
    }

    private static void gravar() {
        if (arquivo == null) return;
        JsonObject donos = new JsonObject();
        for (Map.Entry<UUID, Dono> e : DONOS.entrySet()) {
            JsonObject d = new JsonObject();
            d.addProperty("nome", e.getValue().nome());
            JsonArray a = new JsonArray();
            for (Ponto pt : e.getValue().pontos()) {
                JsonObject j = new JsonObject();
                j.addProperty("guid", pt.guid()); j.addProperty("nome", pt.nome()); j.addProperty("x", pt.x()); j.addProperty("y", pt.y()); j.addProperty("z", pt.z()); j.addProperty("cor", pt.cor());
                a.add(j);
            }
            d.add("pontos", a);
            donos.add(e.getKey().toString(), d);
        }
        JsonObject o = new JsonObject();
        o.add("donos", donos);
        try { Files.writeString(arquivo, o.toString(), StandardCharsets.UTF_8); } catch (Exception e) { FrxWorld.LOG.warn("Pontos do mapa: não consegui gravar: {}", e.toString()); }
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) { ler(ev.getServer()); }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 200 != 0) return;
        try { atualizar(ev.getServer()); } catch (Throwable t) { FrxWorld.LOG.warn("Pontos do mapa: {}", t.toString()); }
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) { DESENHADOS.remove(ev.getEntity().getUUID()); }

    // ------------------------------------------------------------------ comandos de admin

    private static List<Object[]> listaPublicos() {
        List<Object[]> l = new ArrayList<>();
        for (Map.Entry<UUID, Dono> e : DONOS.entrySet()) for (Ponto pt : e.getValue().pontos()) l.add(new Object[]{e.getKey(), e.getValue().nome(), pt});
        return l;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2)).then(Commands.literal("mapa")
                .then(Commands.literal("publicos").executes(c -> {
                    StringBuilder b = new StringBuilder("Pontos públicos dos jogadores:");
                    int i = 1;
                    for (Object[] x : listaPublicos()) { Ponto pt = (Ponto) x[2]; b.append("\n").append(i++).append(". ").append(pt.nome()).append(" · de ").append(x[1]).append(" · ").append(pt.x()).append(", ").append(pt.z()); }
                    c.getSource().sendSuccess(() -> Component.literal(b.toString()), false);
                    return 1;
                }))
                .then(Commands.literal("apagarpublico").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                    List<Object[]> l = listaPublicos();
                    int n = IntegerArgumentType.getInteger(c, "n");
                    if (n > l.size()) { c.getSource().sendFailure(Component.literal("Não existe o ponto " + n + ". Veja /frx mapa publicos.")); return 0; }
                    Object[] x = l.get(n - 1);
                    Ponto pt = (Ponto) x[2];
                    try { chamar(api(), "deletePlayerWaypoint", new Class<?>[]{UUID.class, String.class}, x[0], pt.guid()); } catch (Throwable t) { FrxWorld.LOG.warn("Pontos do mapa: não apaguei no JourneyMap: {}", t.toString()); }
                    Dono d = DONOS.get((UUID) x[0]);
                    if (d != null) { List<Ponto> resto = new ArrayList<>(d.pontos()); resto.remove(pt); if (resto.isEmpty()) DONOS.remove((UUID) x[0]); else DONOS.put((UUID) x[0], new Dono(d.nome(), resto)); }
                    gravar();
                    sincronizar(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Ponto \"" + pt.nome() + "\" de " + x[1] + " apagado."), true);
                    return 1;
                })))
                .then(Commands.literal("pontos").executes(c -> {
                    try {
                        StringBuilder b = new StringBuilder("Pontos globais do JourneyMap:");
                        Object lista = api().getClass().getMethod("getGlobalWaypoints").invoke(api());
                        if (lista instanceof Collection<?> col) for (Object w : col) b.append("\n- ").append(texto(w, "getName"));
                        c.getSource().sendSuccess(() -> Component.literal(b.toString()), false);
                        return 1;
                    } catch (Throwable t) { c.getSource().sendFailure(Component.literal("JourneyMap não respondeu: " + t)); return 0; }
                }))
                .then(Commands.literal("limparpontos").executes(c -> {
                    try {
                        Object api = api();
                        Object lista = api.getClass().getMethod("getGlobalWaypoints").invoke(api);
                        Set<String> vistos = new HashSet<>();
                        List<String> apagados = new ArrayList<>();
                        if (lista instanceof Collection<?> col) for (Object w : new ArrayList<>(col)) {
                            String nome = texto(w, "getName").trim(), chave = nome.toLowerCase();
                            if (APAGAR_GLOBAIS.contains(chave) || !vistos.add(chave)) {
                                try { chamar(api, "deleteGlobalWaypoint", new Class<?>[]{String.class}, texto(w, "getGuid")); apagados.add(nome); } catch (Throwable ignorado) { }
                            }
                        }
                        c.getSource().sendSuccess(() -> Component.literal(apagados.size() + " pontos globais apagados: " + String.join(", ", apagados)), true);
                        return apagados.size();
                    } catch (Throwable t) { c.getSource().sendFailure(Component.literal("JourneyMap não respondeu: " + t)); return 0; }
                }))));
    }
}
