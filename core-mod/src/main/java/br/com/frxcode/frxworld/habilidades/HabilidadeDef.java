package br.com.frxcode.frxworld.habilidades;

import com.google.gson.JsonObject;

/** Habilidade cadastrada no painel (aba Habilidades). */
public record HabilidadeDef(String codigo, String nome, String descricao, String classe, String tipo, String elemento, String icone,
                            int custo, double recarga, double dano, double alcance, double raio, double duracao,
                            String efeito, int potencia, int nivelReq, boolean ultimate, boolean porItem) {

    static HabilidadeDef deJson(JsonObject o) {
        return new HabilidadeDef(o.get("codigo").getAsString(), o.get("nome").getAsString(), o.get("descricao").getAsString(),
                o.get("classe").getAsString(), o.get("tipo").getAsString(), o.get("elemento").getAsString(), o.get("icone").getAsString(),
                o.get("custo").getAsInt(), o.get("cooldown").getAsDouble(), o.get("dano").getAsDouble(), o.get("alcance").getAsDouble(),
                o.get("raio").getAsDouble(), o.get("duracao").getAsDouble(), o.get("efeito").getAsString(), o.get("potencia").getAsInt(),
                o.get("nivelReq").getAsInt(), o.get("ultimate").getAsBoolean(), "ITEM".equals(o.get("aprendizado").getAsString()));
    }
}
