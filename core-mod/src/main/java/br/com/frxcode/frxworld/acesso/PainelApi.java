package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.Config;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Chamadas HTTP do servidor para a API do painel (/api/servidor/...). Tudo assíncrono. */
public final class PainelApi {
    // HTTP/1.1 fixo: com o padrão (HTTP/2) o Java manda "Upgrade: h2c", e o servidor Node do painel
    // fecha a conexão sem responder ("header parser received no bytes").
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private PainelApi() {}

    /** true se a conta já entrou com convite e está ativa. */
    public static CompletableFuture<Boolean> acessoPermitido(UUID uuid, String nick) {
        JsonObject corpo = new JsonObject();
        corpo.addProperty("uuid", uuid.toString());
        corpo.addProperty("nick", nick);
        return post("/api/servidor/acesso", corpo)
                .thenApply(json -> json.has("permitido") && json.get("permitido").getAsBoolean());
    }

    /** Servidor sem convite: garante a ficha do jogador no painel (cria se não existir) e marca o login. */
    public static CompletableFuture<Boolean> registrar(UUID uuid, String nick) {
        JsonObject corpo = new JsonObject();
        corpo.addProperty("uuid", uuid.toString());
        corpo.addProperty("nick", nick);
        corpo.addProperty("registrar", true);
        return post("/api/servidor/acesso", corpo)
                .thenApply(json -> json.has("permitido") && json.get("permitido").getAsBoolean());
    }

    /** Resultado do /convite: ok + mensagem para mostrar ao jogador. */
    public record ResultadoConvite(boolean ok, String motivo) {}

    public static CompletableFuture<ResultadoConvite> usarConvite(UUID uuid, String nick, String codigo) {
        JsonObject corpo = new JsonObject();
        corpo.addProperty("uuid", uuid.toString());
        corpo.addProperty("nick", nick);
        corpo.addProperty("codigo", codigo);
        return post("/api/servidor/convite", corpo).thenApply(json -> new ResultadoConvite(
                json.has("ok") && json.get("ok").getAsBoolean(),
                json.has("motivo") ? json.get("motivo").getAsString() : "Resposta inesperada do painel."));
    }

    /** Configuração de jogo (aba Progressão do painel). */
    public static CompletableFuture<JsonObject> lerConfig() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/config"))
                .timeout(Duration.ofSeconds(8))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode()
                    + (resp.body() == null || resp.body().isBlank() ? "" : ": " + resp.body().substring(0, Math.min(300, resp.body().length()))));
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        });
    }

    /** Missões ativas (aba Missões do painel). */
    public static CompletableFuture<com.google.gson.JsonArray> lerMissoes() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/missoes"))
                .timeout(Duration.ofSeconds(8))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode()
                    + (resp.body() == null || resp.body().isBlank() ? "" : ": " + resp.body().substring(0, Math.min(300, resp.body().length()))));
            return JsonParser.parseString(resp.body()).getAsJsonArray();
        });
    }

    /** Dungeons ativas (aba Dungeons do painel). */
    public static CompletableFuture<com.google.gson.JsonArray> lerDungeons() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/dungeons"))
                .timeout(Duration.ofSeconds(8))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode()
                    + (resp.body() == null || resp.body().isBlank() ? "" : ": " + resp.body().substring(0, Math.min(300, resp.body().length()))));
            return JsonParser.parseString(resp.body()).getAsJsonArray();
        });
    }

    /** Chefes e estruturas de outros mods (aba Chefes de mods). */
    public static CompletableFuture<JsonObject> lerExternos() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/externos"))
                .timeout(Duration.ofSeconds(8))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode()
                    + (resp.body() == null || resp.body().isBlank() ? "" : ": " + resp.body().substring(0, Math.min(300, resp.body().length()))));
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        });
    }

    /** Skins de NPC feitas no painel. versao = a que o servidor já tem (resposta {igual:true} se nada mudou). */
    public static CompletableFuture<JsonObject> lerSkinsNpc(String versao) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/npc-skins?versao="
                        + java.net.URLEncoder.encode(versao == null ? "" : versao, java.nio.charset.StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(15))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode());
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        });
    }

    /** Eventos de estrada: configuração + lista (aba Eventos de estrada). */
    public static CompletableFuture<JsonObject> lerEventos() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/eventos"))
                .timeout(Duration.ofSeconds(8))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode()
                    + (resp.body() == null || resp.body().isBlank() ? "" : ": " + resp.body().substring(0, Math.min(300, resp.body().length()))));
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        });
    }

    /** Habilidades (aba Habilidades). */
    public static CompletableFuture<com.google.gson.JsonArray> lerHabilidades() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/habilidades"))
                .timeout(Duration.ofSeconds(8))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode()
                    + (resp.body() == null || resp.body().isBlank() ? "" : ": " + resp.body().substring(0, Math.min(300, resp.body().length()))));
            return JsonParser.parseString(resp.body()).getAsJsonArray();
        });
    }

    /** Lojas das capitais (aba Economia do painel). */
    public static CompletableFuture<JsonObject> lerLojas() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/lojas"))
                .timeout(Duration.ofSeconds(8))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode());
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        });
    }

    /** Masmorras fixas (aba Masmorras do painel), no formato do posicoes.json. */
    public static CompletableFuture<com.google.gson.JsonArray> lerMasmorras() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/masmorras"))
                .timeout(Duration.ofSeconds(8))
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode());
            return JsonParser.parseString(resp.body()).getAsJsonArray();
        });
    }

    /** Ficha completa de um jogador para a aba Jogadores. */
    public static void enviarDetalheJogador(JsonObject ficha) {
        post("/api/servidor/jogador-detalhe", ficha).exceptionally(e -> null);
    }

    /** Lista de criaturas do modpack (seletores com imagem no painel). */
    public static void enviarEntidades(JsonObject corpo) {
        post("/api/servidor/entidades", corpo).exceptionally(e -> null);
    }

    /** Lista de itens do modpack: [[id, chave de tradução, pilha máxima], ...]. */
    public static void enviarItens(JsonObject corpo) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + "/api/servidor/itens"))
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json")
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString()))
                .build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally(e -> null);
    }

    /** Retrato do servidor para a Visão geral (sem esperar resposta). */
    public static void enviarStatus(JsonObject status) {
        post("/api/servidor/status", status).exceptionally(e -> null);
    }

    /** Onde cada masmorra ficou: [{id, x, y, z, estrutura, nova?}]. */
    public static void enviarMasmorras(com.google.gson.JsonArray colocadas) {
        JsonObject corpo = new JsonObject();
        corpo.add("colocadas", colocadas);
        post("/api/servidor/masmorras", corpo).exceptionally(e -> null);
    }

    /** Progresso do jogador para a aba Jogadores (sem esperar resposta). */
    public static void enviarProgresso(UUID uuid, String nick, int nivel, long xp, String rank, boolean morreu, String classe) {
        enviarProgresso(uuid, nick, nivel, xp, rank, morreu, classe, -1, -1, null, -1);
    }

    public static void enviarProgresso(UUID uuid, String nick, int nivel, long xp, String rank, boolean morreu, String classe, long moedas, long banco,
                                       String titulo, int conquistas) {
        enviarProgresso(uuid, nick, nivel, xp, rank, morreu, classe, moedas, banco, titulo, conquistas, null);
    }

    /** extra: campos a mais copiados no corpo (reputacao, campanhas). */
    public static void enviarProgresso(UUID uuid, String nick, int nivel, long xp, String rank, boolean morreu, String classe, long moedas, long banco,
                                       String titulo, int conquistas, JsonObject extra) {
        JsonObject corpo = new JsonObject();
        if (extra != null) for (var e : extra.entrySet()) corpo.add(e.getKey(), e.getValue());
        corpo.addProperty("uuid", uuid.toString());
        corpo.addProperty("nick", nick);
        corpo.addProperty("nivel", nivel);
        corpo.addProperty("xp", xp);
        corpo.addProperty("rank", rank);
        corpo.addProperty("morreu", morreu);
        corpo.addProperty("classe", classe);
        if (moedas >= 0) corpo.addProperty("moedas", Math.min(moedas, Integer.MAX_VALUE));
        if (banco >= 0) corpo.addProperty("banco", Math.min(banco, Integer.MAX_VALUE));
        if (titulo != null) corpo.addProperty("titulo", titulo);
        if (conquistas >= 0) corpo.addProperty("conquistas", conquistas);
        post("/api/servidor/progresso", corpo).exceptionally(e -> null);
    }

    private static CompletableFuture<JsonObject> post(String caminho, JsonObject corpo) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.PAINEL_URL.get() + caminho))
                .timeout(Duration.ofSeconds(8))
                .header("content-type", "application/json")
                .header("x-servidor-token", Config.SERVIDOR_TOKEN.get())
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString()))
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Painel respondeu HTTP " + resp.statusCode());
            }
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        });
    }
}
