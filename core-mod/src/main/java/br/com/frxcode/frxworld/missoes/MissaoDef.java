package br.com.frxcode.frxworld.missoes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Missão cadastrada no painel (aba Missões). */
public record MissaoDef(String id, String titulo, String descricao, String tipo, String alvo, int quantidade,
                        int nivelMin, long recompensaXp, List<ItemRecompensa> itens, boolean repetivel,
                        int intervaloHoras, String preRequisitoId, long recompensaMoedas, int rankMin) {

    public record ItemRecompensa(String item, int qtd) {}

    public boolean caca() { return "MATAR".equals(tipo); }

    static MissaoDef deJson(JsonObject o) {
        List<ItemRecompensa> itens = new ArrayList<>();
        if (o.has("recompensaItens") && o.get("recompensaItens").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("recompensaItens")) {
                JsonObject i = e.getAsJsonObject();
                itens.add(new ItemRecompensa(i.get("item").getAsString(), i.get("qtd").getAsInt()));
            }
        }
        JsonElement pre = o.get("preRequisitoId");
        return new MissaoDef(
                o.get("id").getAsString(),
                o.get("titulo").getAsString(),
                o.has("descricao") ? o.get("descricao").getAsString() : "",
                o.get("tipo").getAsString(),
                o.get("alvo").getAsString(),
                o.get("quantidade").getAsInt(),
                o.get("nivelMin").getAsInt(),
                o.get("recompensaXp").getAsLong(),
                itens,
                o.get("repetivel").getAsBoolean(),
                o.get("intervaloHoras").getAsInt(),
                pre == null || pre.isJsonNull() ? null : pre.getAsString(),
                o.has("recompensaMoedas") && !o.get("recompensaMoedas").isJsonNull() ? o.get("recompensaMoedas").getAsLong() : o.get("recompensaXp").getAsLong() / 5,
                o.has("rankMin") && !o.get("rankMin").isJsonNull() ? Math.max(0, Math.min(6, o.get("rankMin").getAsInt())) : 0);
    }
}
