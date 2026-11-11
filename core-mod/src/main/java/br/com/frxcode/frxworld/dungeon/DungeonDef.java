package br.com.frxcode.frxworld.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Dungeon cadastrada no painel (aba Dungeons). */
public record DungeonDef(String id, String nome, String descricao, int distanciaMin, int distanciaMax, int nivelRecomendado,
                         String bossTipo, String bossNome, int bossVida, double bossDano, double bossVelocidade,
                         String lacaioTipo, int lacaioQtd, long recompensaXp, List<Loot> loot, int recargaHoras,
                         List<Guardiao> andares) {

    /** Guardião de um andar (antes do Portão Selado). */
    public record Guardiao(String tipo, String nome, int vida, double dano, double velocidade, String lacaioTipo, int lacaioQtd) {}

    /** Guardião final. */
    public Guardiao finalBoss() {
        return new Guardiao(bossTipo, bossNome, bossVida, bossDano, bossVelocidade, lacaioTipo, lacaioQtd);
    }

    /** Guardião do andar 1 ou 2: o do painel, ou um derivado do final (40% e 65% da força). */
    public Guardiao andar(int n) {
        if (n - 1 < andares.size()) return andares.get(n - 1);
        double f = n == 1 ? 0.4 : 0.65;
        return new Guardiao(lacaioTipo, (n == 1 ? "Sentinela" : "Carcereiro") + " de " + nome,
                (int) Math.max(30, bossVida * f), Math.max(3, bossDano * f), 1.0, lacaioTipo, Math.max(1, lacaioQtd / 2));
    }

    public record Loot(String item, int qtd, double chance) {}

    static DungeonDef deJson(JsonObject o) {
        List<Loot> loot = new ArrayList<>();
        if (o.has("loot") && o.get("loot").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("loot")) {
                JsonObject l = e.getAsJsonObject();
                loot.add(new Loot(l.get("item").getAsString(), l.get("qtd").getAsInt(), l.get("chance").getAsDouble()));
            }
        }
        List<Guardiao> andares = new ArrayList<>();
        if (o.has("andares") && o.get("andares").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("andares")) {
                JsonObject g = e.getAsJsonObject();
                andares.add(new Guardiao(g.get("bossTipo").getAsString(), g.get("bossNome").getAsString(),
                        g.get("bossVida").getAsInt(), g.get("bossDano").getAsDouble(),
                        g.has("bossVelocidade") ? g.get("bossVelocidade").getAsDouble() : 1.0,
                        g.get("lacaioTipo").getAsString(), g.get("lacaioQtd").getAsInt()));
            }
        }
        return new DungeonDef(o.get("id").getAsString(), o.get("nome").getAsString(),
                o.has("descricao") ? o.get("descricao").getAsString() : "",
                o.get("distanciaMin").getAsInt(), o.get("distanciaMax").getAsInt(), o.get("nivelRecomendado").getAsInt(),
                o.get("bossTipo").getAsString(), o.get("bossNome").getAsString(), o.get("bossVida").getAsInt(),
                o.get("bossDano").getAsDouble(), o.get("bossVelocidade").getAsDouble(),
                o.get("lacaioTipo").getAsString(), o.get("lacaioQtd").getAsInt(),
                o.get("recompensaXp").getAsLong(), loot, o.get("recargaHoras").getAsInt(), andares);
    }
}
