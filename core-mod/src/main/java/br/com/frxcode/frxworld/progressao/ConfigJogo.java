package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.Config;
import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.PainelApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração de jogo vinda do painel (aba Progressão). Enquanto o painel não responde,
 * valem os padrões do frxworld-common.toml e a tabela padrão do documento de design.
 */
public final class ConfigJogo {
    private static volatile JsonObject atual = null;

    public record FaixaMorte(int ate, double xpPct, boolean podePerderNivel, double durabilidadePct, double feridoMin) {}

    private static final String[] RANKS = {"F", "E", "D", "C", "B", "A", "S"};
    private static final int[] RANKS_PADRAO = {1, 10, 20, 35, 50, 70, 90};
    private static final FaixaMorte[] MORTE_PADRAO = {
            new FaixaMorte(10, 0, false, 0, 0),
            new FaixaMorte(30, 5, false, 0, 2),
            new FaixaMorte(60, 10, false, 10, 5),
            new FaixaMorte(90, 15, true, 15, 10),
            new FaixaMorte(1000, 20, true, 20, 15),
    };

    private ConfigJogo() {}

    /** Busca no painel (assíncrono). Erros só vão para o log: o jogo segue com a config anterior. */
    public static void recarregar() {
        PainelApi.lerConfig().whenComplete((json, erro) -> {
            if (erro != null) {
                FrxWorld.LOG.warn("Não consegui ler a configuração do painel: {}", erro.getMessage());
            } else {
                atual = json;
            }
        });
    }

    private static JsonObject secao(String nome) {
        JsonObject a = atual;
        return a != null && a.has(nome) && a.get(nome).isJsonObject() ? a.getAsJsonObject(nome) : null;
    }

    /** Seção crua da config do painel (ou null). */
    public static JsonObject secaoCrua(String nome) { return secao(nome); }

    private static double num(JsonObject o, String k, double padrao) {
        if (o == null || !o.has(k)) return padrao;
        try { return o.get(k).getAsDouble(); } catch (Exception e) { return padrao; }
    }

    // ------------------------------------------------------------------ progressão
    public static int nivelMaximo() { return (int) num(secao("progressao"), "nivelMaximo", Config.NIVEL_MAXIMO.get()); }
    public static double xpBase() { return num(secao("progressao"), "xpBase", Config.XP_BASE.get()); }
    public static double xpExpoente() { return num(secao("progressao"), "xpExpoente", Config.XP_EXPOENTE.get()); }
    public static double xpMultiplicador() { return num(secao("progressao"), "xpMultiplicador", Config.XP_MULTIPLICADOR.get()); }

    // ------------------------------------------------------------------ ranks
    public static String rankDoNivel(int nivel) {
        int[] minimos = RANKS_PADRAO.clone();
        JsonObject a = atual;
        if (a != null && a.has("ranks") && a.get("ranks").isJsonArray()) {
            JsonArray arr = a.getAsJsonArray("ranks");
            for (int i = 0; i < Math.min(arr.size(), RANKS.length); i++) {
                minimos[i] = (int) num(arr.get(i).getAsJsonObject(), "nivelMin", minimos[i]);
            }
        }
        String rank = RANKS[0];
        for (int i = 0; i < RANKS.length; i++) if (nivel >= minimos[i]) rank = RANKS[i];
        return rank;
    }

    // ------------------------------------------------------------------ morte
    public static FaixaMorte faixaMorte(int nivel) {
        FaixaMorte[] faixas = MORTE_PADRAO;
        JsonObject a = atual;
        if (a != null && a.has("morte") && a.get("morte").isJsonArray()) {
            JsonArray arr = a.getAsJsonArray("morte");
            faixas = new FaixaMorte[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                JsonElement perde = o.get("podePerderNivel");
                faixas[i] = new FaixaMorte((int) num(o, "ate", 1000), num(o, "xpPct", 0),
                        perde != null && perde.getAsBoolean(), num(o, "durabilidadePct", 0), num(o, "feridoMin", 0));
            }
        }
        for (FaixaMorte f : faixas) if (nivel <= f.ate()) return f;
        return faixas[faixas.length - 1];
    }

    // ------------------------------------------------------------------ taverneiro
    public static String taverneiroNome() {
        JsonObject t = secao("taverna");
        return t != null && t.has("nome") ? t.get("nome").getAsString() : "Taverneiro";
    }

    public static List<String> taverneiroFalas() {
        List<String> falas = new ArrayList<>();
        JsonObject t = secao("taverna");
        if (t != null && t.has("falas") && t.get("falas").isJsonArray()) {
            for (JsonElement e : t.getAsJsonArray("falas")) falas.add(e.getAsString());
        }
        if (falas.isEmpty()) falas.add("Bem-vindo, {jogador}! Dê uma olhada no quadro de missões.");
        return falas;
    }

    // ------------------------------------------------------------------ zonas de perigo
    public static int zonaBlocosPorNivel() { return Math.max(50, (int) num(secao("zonas"), "blocosPorNivel", 250)); }
    public static int zonaNivelMax() { return Math.max(1, (int) num(secao("zonas"), "nivelMax", 60)); }
    public static double zonaVidaPct() { return num(secao("zonas"), "vidaPct", 6); }
    public static double zonaDanoPct() { return num(secao("zonas"), "danoPct", 3.5); }
    public static double zonaArmaduraPorNivel() { return num(secao("zonas"), "armaduraPorNivel", 0.2); }
    public static double zonaMoedaChance() { return num(secao("zonas"), "moedaChance", 30); }
    /** Monstros com rank (na seção "zonas" do painel). Chances em %. */
    public static double rankEliteChance() { return num(secao("zonas"), "eliteChance", 5); }
    public static int rankEliteNivelMin() { return (int) num(secao("zonas"), "eliteNivelMin", 3); }
    public static double rankCampeaoChance() { return num(secao("zonas"), "campeaoChance", 0.5); }
    public static int rankCampeaoNivelMin() { return (int) num(secao("zonas"), "campeaoNivelMin", 10); }
    public static double zonaMoedaPorNivel() { return num(secao("zonas"), "moedaPorNivel", 1); }

    public static double feridoVidaPct() { return num(secao("ferido"), "vidaPct", 20); }
    public static double feridoDanoPct() { return num(secao("ferido"), "danoPct", 20); }
}
