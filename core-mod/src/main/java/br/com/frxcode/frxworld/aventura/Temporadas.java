package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Sistema;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Temporadas: placar mensal (mês do calendário real). Cada feito soma pontos; na virada do mês os três
 * primeiros ganham moedas e título, e o placar zera. Tudo em frx_temporada.json, na pasta do mundo.
 */
public class Temporadas {
    /** Categoria → pontos por unidade. */
    private static final Map<String, Integer> PESOS = Map.of("abates", 1, "chefes", 50, "portais", 80, "invasoes", 40,
            "procurados", 60, "torre", 20, "tesouros", 30, "historia", 100);
    private static final Map<String, String> ROTULOS = Map.of("abates", "abates", "chefes", "chefes", "portais", "portais", "invasoes", "invasões",
            "procurados", "procurados", "torre", "andares novos da Torre", "tesouros", "tesouros", "historia", "capítulos");
    /** Contadores das conquistas que também contam para a temporada. */
    private static final Map<String, String> DE_CONTADOR = Map.of("matar_total", "abates", "chefes", "chefes", "portais", "portais",
            "invasoes", "invasoes", "procurados", "procurados", "tesouros", "tesouros");
    private static final long[] PREMIOS = {3, 2, 1};   // runas de nível II para 1º, 2º e 3º
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static JsonObject dados;
    private static boolean sujo;

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_temporada.json"); }
    private static String mesAtual() { return YearMonth.now().toString(); }

    private static JsonObject obj(JsonObject pai, String chave) {
        if (!pai.has(chave) || !pai.get(chave).isJsonObject()) pai.add(chave, new JsonObject());
        return pai.getAsJsonObject(chave);
    }

    private static void carregar(MinecraftServer s) {
        dados = new JsonObject();
        try {
            Path a = arquivo(s);
            if (Files.exists(a)) dados = GSON.fromJson(Files.readString(a, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            FrxWorld.LOG.warn("Temporada: não li frx_temporada.json: {}", e.toString());
        }
        if (dados == null) dados = new JsonObject();
        if (!dados.has("mes")) dados.addProperty("mes", mesAtual());
    }

    private static void gravar(MinecraftServer s) {
        if (dados == null) return;
        try { Files.writeString(arquivo(s), GSON.toJson(dados), StandardCharsets.UTF_8); sujo = false; }
        catch (Exception e) { FrxWorld.LOG.warn("Temporada: não gravei: {}", e.toString()); }
    }

    // ------------------------------------------------------------------ pontuação

    /** Chamado por Conquistas.somar: só os contadores que valem ponto entram. */
    public static void doContador(ServerPlayer p, String chave, int quanto) {
        String cat = DE_CONTADOR.get(chave);
        if (cat != null) pontuar(p, cat, quanto);
    }

    public static void pontuar(ServerPlayer p, String categoria, int quanto) {
        if (dados == null || quanto <= 0 || !PESOS.containsKey(categoria)) return;
        JsonObject j = obj(obj(dados, "pontos"), p.getUUID().toString());
        j.addProperty("nome", p.getGameProfile().getName());
        j.addProperty(categoria, (j.has(categoria) ? j.get(categoria).getAsInt() : 0) + quanto);
        j.addProperty("pts", (j.has("pts") ? j.get("pts").getAsLong() : 0) + (long) quanto * PESOS.get(categoria));
        sujo = true;
    }

    private record Linha(String uuid, String nome, long pts, JsonObject j) { }

    private static List<Linha> placar() {
        List<Linha> l = new ArrayList<>();
        if (dados == null) return l;
        for (Map.Entry<String, JsonElement> e : obj(dados, "pontos").entrySet()) {
            JsonObject j = e.getValue().getAsJsonObject();
            l.add(new Linha(e.getKey(), j.has("nome") ? j.get("nome").getAsString() : "?", j.has("pts") ? j.get("pts").getAsLong() : 0, j));
        }
        l.sort((a, b) -> Long.compare(b.pts(), a.pts()));
        return l;
    }

    /** Títulos ganhos em temporadas passadas. */
    public static List<String> titulos(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        if (dados == null) return l;
        JsonObject t = obj(dados, "titulos");
        if (t.has(p.getUUID().toString())) for (JsonElement e : t.getAsJsonArray(p.getUUID().toString())) l.add(e.getAsString());
        return l;
    }

    // ------------------------------------------------------------------ virada

    private static void fechar(MinecraftServer s) {
        List<Linha> l = placar();
        String mes = dados.get("mes").getAsString();
        if (!l.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, l.size()); i++) {
                Linha x = l.get(i);
                if (x.pts() <= 0) break;
                String titulo = i == 0 ? "Campeão da Temporada" : "Pódio da Temporada";
                JsonObject pend = obj(obj(dados, "premios"), x.uuid());
                pend.addProperty("moedas", (pend.has("moedas") ? pend.get("moedas").getAsLong() : 0) + PREMIOS[i]);
                pend.addProperty("texto", (i + 1) + "º lugar na temporada " + mes);
                JsonObject tit = obj(dados, "titulos");
                JsonArray arr = tit.has(x.uuid()) ? tit.getAsJsonArray(x.uuid()) : new JsonArray();
                boolean tem = false;
                for (JsonElement e : arr) if (e.getAsString().equals(titulo)) tem = true;
                if (!tem) arr.add(titulo);
                tit.add(x.uuid(), arr);
                sb.append(i > 0 ? ", " : "").append(i + 1).append("º ").append(x.nome()).append(" (").append(x.pts()).append(" pts)");
            }
            if (sb.length() > 0) s.getPlayerList().broadcastSystemMessage(Component.literal("🏆 Fim da temporada " + mes + "! " + sb + ". Nova temporada começou: /temporada")
                    .withStyle(ChatFormatting.GOLD), false);
        }
        dados.add("pontos", new JsonObject());
        dados.addProperty("mes", mesAtual());
        gravar(s);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) pagarPendente(p);
    }

    private static void pagarPendente(ServerPlayer p) {
        if (dados == null) return;
        JsonObject premios = obj(dados, "premios");
        String id = p.getUUID().toString();
        if (!premios.has(id)) return;
        JsonObject j = premios.getAsJsonObject(id);
        long moedas = j.has("moedas") ? j.get("moedas").getAsLong() : 0;
        premios.remove(id);
        sujo = true;
        for (long i = 0; i < moedas; i++) { net.minecraft.world.item.ItemStack r = Runas.criarAleatoriaNivel(2); if (!p.getInventory().add(r)) p.drop(r, false); }
        Sistema.avisar(p, "Prêmio de temporada", (j.has("texto") ? j.get("texto").getAsString() : "Pódio") + ": " + moedas + (moedas == 1 ? " runa" : " runas") + " de nível II e um título novo (/titulo).", Sistema.OURO);
    }

    // ------------------------------------------------------------------ eventos e comandos

    @SubscribeEvent
    public void aoIniciar(ServerStartedEvent ev) { carregar(ev.getServer()); }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) { gravar(ev.getServer()); }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) pagarPendente(p);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 1200 != 300 || dados == null) return;
        if (!mesAtual().equals(dados.get("mes").getAsString())) fechar(ev.getServer());
        else if (sujo) gravar(ev.getServer());
    }

    private static void mostrar(ServerPlayer p) {
        List<Linha> l = placar();
        p.sendSystemMessage(Component.literal("🏆 Temporada " + (dados != null ? dados.get("mes").getAsString() : "") + " — termina na virada do mês. Prêmios: "
                + "3 / 2 / 1 runas de nível II e título.").withStyle(ChatFormatting.GOLD));
        if (l.isEmpty()) { p.sendSystemMessage(Component.literal("   Ninguém pontuou ainda. Mate chefes, feche portais, suba a Torre!").withStyle(ChatFormatting.GRAY)); return; }
        String eu = p.getUUID().toString();
        for (int i = 0; i < l.size(); i++) {
            Linha x = l.get(i);
            boolean sou = x.uuid().equals(eu);
            if (i >= 10 && !sou) continue;
            p.sendSystemMessage(Component.literal("   " + (i + 1) + "º " + x.nome() + " — " + x.pts() + " pts")
                    .withStyle(sou ? ChatFormatting.YELLOW : i < 3 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            if (sou) {
                StringBuilder sb = new StringBuilder();
                for (String cat : PESOS.keySet()) if (x.j().has(cat)) sb.append(sb.length() > 0 ? " · " : "").append(x.j().get(cat).getAsInt()).append(" ").append(ROTULOS.get(cat));
                p.sendSystemMessage(Component.literal("      " + sb).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        p.sendSystemMessage(Component.literal("   Pontos: chefe 50 · portal 80 · procurado 60 · invasão 40 · tesouro 30 · andar novo da Torre 20 · capítulo 100 · abate 1").withStyle(ChatFormatting.DARK_GRAY));
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("temporada").executes(c -> { mostrar(c.getSource().getPlayerOrException()); return 1; }));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("temporada").then(Commands.literal("fechar").executes(c -> {
                    if (dados == null) return 0;
                    fechar(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Temporada encerrada e prêmios distribuídos."), true);
                    return 1;
                }))));
    }
}
