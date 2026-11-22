package br.com.frxcode.frxworld.dungeon;

import br.com.frxcode.frxworld.progressao.ConfigJogo;
import com.google.gson.JsonObject;

/** Moedas dos chefões de masmorra (catacumbas do FRX-WORLD e chefes de estruturas de outros mods), pelo nível do chefão. */
final class Recompensa {
    private Recompensa() {}

    /** Cobre por nível do chefão, para cada jogador (painel: Economia → Portais e masmorras; padrão 150 = 1,5 ouro). */
    static long moedasPorNivel() {
        try {
            JsonObject o = ConfigJogo.secaoCrua("masmorras");
            if (o != null && o.has("moedasPorNivel")) return Math.max(0, o.get("moedasPorNivel").getAsLong());
        } catch (Exception ignorado) { }
        return 40;
    }

    static long chefao(int nivel) { return moedasPorNivel() * Math.max(1, nivel); }

    /**
     * Chefe de outro mod (uma vez por recarga): pouco para os comuns, muito só para os chefes difíceis.
     * Nível < 40: 20 cobre por nível (Aptrgangr 30 = 6 ouro). 40 a 54: 50 por nível (Frostmaw 40 = 20 ouro).
     * 55 ou mais (os especiais): 3 × nível² (Ignis 60 = 108 ouro, Leviatã 75 = 168 ouro, Tremorzilla 80 = 192 ouro).
     */
    static long chefaoExterno(int nivelChefe) {
        int n = Math.max(1, nivelChefe);
        if (n >= 55) return 3L * n * n;
        if (n >= 40) return 50L * n;
        return 20L * n;
    }

    /** Guardião de andar das catacumbas: um quinto do chefão por andar. */
    static long guardiao(int nivel, int andar) { return Math.round(chefao(nivel) * 0.2 * Math.max(1, andar)); }
}
