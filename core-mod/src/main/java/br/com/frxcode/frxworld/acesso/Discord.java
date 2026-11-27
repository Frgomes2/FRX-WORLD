package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.Config;
import br.com.frxcode.frxworld.FrxWorld;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Avisos dos grandes eventos num canal do Discord, por webhook (config: discord.webhook em frxworld-common.toml).
 * Sem endereço configurado, não faz nada. Envio assíncrono; no máximo um aviso igual a cada 10 minutos.
 */
public final class Discord {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final java.util.Map<String, Long> ULTIMO = new java.util.concurrent.ConcurrentHashMap<>();

    private Discord() {}

    public static void avisar(String texto) {
        String url = Config.DISCORD_WEBHOOK.get().trim();
        if (url.isEmpty() || !url.startsWith("https://")) return;
        long agora = System.currentTimeMillis();
        Long antes = ULTIMO.put(texto, agora);
        if (antes != null && agora - antes < 600_000) return;
        String json = "{\"username\":\"FRX-WORLD\",\"allowed_mentions\":{\"parse\":[]},\"content\":\"" + escapar(texto) + "\"}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, e) -> {
            if (e != null) FrxWorld.LOG.warn("Discord: não enviei o aviso: {}", e.toString());
            else if (r.statusCode() >= 300) FrxWorld.LOG.warn("Discord: o webhook respondeu {}", r.statusCode());
        });
    }

    private static String escapar(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                default -> { if (c < 0x20) b.append(' '); else b.append(c); }
            }
        }
        return b.toString();
    }
}
