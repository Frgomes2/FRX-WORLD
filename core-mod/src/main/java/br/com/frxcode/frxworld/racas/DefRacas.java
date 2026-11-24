package br.com.frxcode.frxworld.racas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Definições das raças jogáveis (servidor e cliente usam a mesma tabela).
 * Números estão em 100%: a raça principal aplica 100%, a secundária 35–60% (pela compatibilidade) e o puro-sangue 130%.
 * Efeitos "liga/desliga": vantagens valem a partir de 50%; desvantagens só na raça principal.
 */
public final class DefRacas {
    private DefRacas() {}

    public static final int COMUM = 0, ESPECIAL = 1, LENDARIA = 2, DIVINA = 3;
    public static final String[] NOME_NIVEL = {"Comum", "Especial", "Lendária", "Divina"};

    /** Efeitos que não são números. */
    public static final String VISAO_NOTURNA = "visaoNoturna", QUEDA_LENTA = "quedaLenta", SEM_QUEDA = "semDanoQueda",
            FRACO_SOL = "fracoSol", AURA = "aura", SOPRO = "sopro", REGEN_TOTAL = "regenTotal", SAIYAJIN = "saiyajin";
    public static final Set<String> DESVANTAGENS_BINARIAS = Set.of(FRACO_SOL);

    public record Raca(String id, String nome, int nivel, String familia, String descricao,
                       Map<String, Double> numeros, Set<String> efeitos, List<String> vantagens, List<String> desvantagens,
                       String liberar, String reinoMissao) {
        public boolean lendaria() { return nivel >= LENDARIA; }
    }

    public static final Map<String, Raca> TODAS = new LinkedHashMap<>();

    private static Map<String, Double> n(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        return m;
    }

    private static void r(String id, String nome, int nivel, String familia, String desc, Map<String, Double> num, Set<String> ef,
                          List<String> mais, List<String> menos, String liberar, String reino) {
        TODAS.put(id, new Raca(id, nome, nivel, familia, desc, num, ef, mais, menos, liberar, reino));
    }

    static {
        // ---- comuns (livres na criação)
        r("humano", "Humano", COMUM, "neutro", "Adaptáveis e ambiciosos, aprendem rápido e fazem amigos em todo reino.",
                n("xp", 10, "reputacao", 10), Set.of(), List.of("+10% de XP", "+10% de reputação ganha"), List.of(), "Livre na criação", "");
        r("elfo", "Elfo", COMUM, "luz", "Longevos e ligados à magia, mestres do arco e das florestas.",
                n("recursoMax", 15, "danoArco", 15, "vida", -2), Set.of(), List.of("+15% de recurso máximo", "+15% de dano com arco"), List.of("−1 coração de vida"), "Livre na criação", "");
        r("anao", "Anão", COMUM, "terra", "Teimosos e resistentes, nascidos entre forjas e minas.",
                n("armadura", 3, "resistencia", 1, "mineracao", 20, "velocidade", -5), Set.of(), List.of("+3 de armadura e +1 de resistência", "+20% de velocidade de mineração"), List.of("−5% de velocidade"), "Livre na criação", "");
        r("beastkin", "Beastkin", COMUM, "fera", "Povo-fera de instintos aguçados: orelhas atentas e passos rápidos.",
                n("velocidade", 10, "recursoMax", -10), Set.of(VISAO_NOTURNA), List.of("+10% de velocidade", "Enxerga no escuro"), List.of("−10% de recurso máximo"), "Livre na criação", "");
        r("halfling", "Halfling", COMUM, "terra", "Pequenos, sortudos e difíceis de acertar.",
                n("sorte", 2, "esquiva", 10, "dano", -10), Set.of(), List.of("+2 de sorte (saques melhores)", "10% de chance de esquivar golpes"), List.of("−10% de dano corpo a corpo"), "Livre na criação", "");
        // ---- especiais (nível 20 ou honra com um reino)
        r("demonio", "Demônio", ESPECIAL, "trevas", "Filhos do abismo: o fogo não os fere e a magia sombria flui fácil.",
                n("fogo", 100, "danoMagico", 15, "danoMagicoRecebido", 15), Set.of(), List.of("Imune a fogo e lava", "+15% de dano mágico"), List.of("+15% de dano mágico recebido"), "Nível 20, ou Honrado com os Lordes Demônios", "demonios");
        r("vampiro", "Vampiro", ESPECIAL, "trevas", "Nobres da noite que se alimentam da vida dos inimigos.",
                n("rouboVida", 8), Set.of(VISAO_NOTURNA, FRACO_SOL), List.of("Recupera 8% do dano causado", "Enxerga no escuro"), List.of("Fraco sob o sol (fraqueza de dia ao ar livre)"), "Nível 20, ou Honrado com Lubelius", "lubelius");
        r("dragonoide", "Dragonoide", ESPECIAL, "draco", "Sangue de dragão nas veias: escamas duras e calor por dentro.",
                n("vida", 6, "fogo", 50, "fome", 25), Set.of(), List.of("+3 corações de vida", "Metade do dano de fogo"), List.of("Sente fome 25% mais rápido"), "Nível 20, ou Honrado com Tempest", "tempest");
        r("oni", "Oni", ESPECIAL, "fera", "Guerreiros chifrudos do Leste, de força bruta assustadora.",
                n("dano", 12, "vida", 2, "recursoMax", -15), Set.of(), List.of("+12% de dano corpo a corpo", "+1 coração de vida"), List.of("−15% de recurso máximo"), "Nível 20, ou Honrado com o Império do Leste", "leste");
        r("espirito", "Espírito", ESPECIAL, "luz", "Meio presos a este mundo, leves como a brisa e cheios de energia.",
                n("regenRecurso", 25, "vida", -4), Set.of(QUEDA_LENTA), List.of("+25% de regeneração de recurso", "Cai devagar (agache para cair normal)"), List.of("−2 corações de vida"), "Nível 20, ou Honrado com Sarion", "sarion");
        r("gigante", "Gigante", ESPECIAL, "terra", "Descendentes dos antigos titãs: alcance longo e corpo que não cede.",
                n("vida", 8, "alcance", 1, "empurrao", 0.5, "velocidade", -8), Set.of(), List.of("+4 corações de vida", "+1 bloco de alcance", "Resiste a empurrões"), List.of("−8% de velocidade"), "Nível 20, ou Honrado com Dwargon", "dwargon");
        // ---- lendárias (por feitos; fortes, mas com um preço)
        r("anjo", "Anjo", LENDARIA, "luz", "Mensageiro da luz. Não cai: plana. A cura o encontra com mais facilidade.",
                n("curaRecebida", 30, "dano", -10), Set.of(QUEDA_LENTA, SEM_QUEDA), List.of("Plana e não sofre dano de queda", "+30% de cura recebida"), List.of("−10% de dano corpo a corpo"), "Conclua a campanha de Lubelius", "");
        r("rei_demonio", "Rei Demônio", LENDARIA, "trevas", "Senhor do abismo. Monstros por perto perdem a força só de estar ao seu lado.",
                n("dano", 15, "fogo", 100, "curaRecebida", -25), Set.of(AURA), List.of("Aura: monstros a 6 blocos ficam fracos e lentos", "+15% de dano e imune a fogo"), List.of("−25% de cura recebida"), "Conclua a campanha dos Lordes Demônios", "");
        r("dragao", "Dragão", LENDARIA, "draco", "Um dragão em forma humana. De tempos em tempos, o fogo escapa pela boca.",
                n("vida", 10, "fogo", 100, "velocidade", -5, "fome", 50), Set.of(SOPRO), List.of("Sopro de fogo a cada 30 s no próximo golpe", "+5 corações e imune a fogo"), List.of("−5% de velocidade", "Sente fome 50% mais rápido"), "Derrote um dragão", "");
        r("celestial", "Ser Celestial", LENDARIA, "luz", "Feito de luz antiga: o corpo se refaz sozinho quando a luta acaba.",
                n("regenRecurso", 30, "dano", -10), Set.of(REGEN_TOTAL, SEM_QUEDA), List.of("Fora de combate, regenera a vida inteira", "+30% de regeneração de recurso e sem dano de queda"), List.of("−10% de dano corpo a corpo"), "Chegue ao nível 100", "");
        r("saiyajin", "Saiyajin", LENDARIA, "fera", "Guerreiros de uma linhagem das estrelas: quanto mais apanham, mais fortes voltam. Quando a raiva explode, o cabelo vira ouro.",
                n("dano", 15, "vida", 4, "fome", 50), Set.of(SAIYAJIN),
                List.of("+15% de dano corpo a corpo e +2 corações", "Zenkai: escapar de uma luta com menos de 20% de vida deixa você mais forte (+1% de dano, até +15%)",
                        "Transformação Super Saiyajin (tecla U): forma 1 no nível 20, forma 2 no 40 e forma 3 no 60"),
                List.of("Sente fome 50% mais rápido", "Transformado, a fome cai ainda mais rápido"), "Derrote o Príncipe Vegeta no topo do Templo Karin", "");
        r("deus", "Deus", DIVINA, "divino", "Uma divindade entre os mortais. Só nasce de eventos do servidor.",
                n("vida", 6, "dano", 10, "velocidade", 5, "xp", 10, "recursoMax", 10), Set.of(), List.of("Um pouco de tudo: vida, dano, velocidade, XP e recurso"), List.of("Só por evento ou admin"), "Somente por evento ou admin", "");
    }

    public static Raca de(String id) { return id == null ? null : TODAS.get(id); }

    public static String nome(String id) {
        Raca r = de(id);
        return r == null ? "" : r.nome();
    }

    // ------------------------------------------------------------------ compatibilidade

    public enum Compat {
        PURO("Puro-sangue", 1.3), HARMONICA("Harmônica", 0.6), ESTAVEL("Estável", 0.5), INSTAVEL("Instável", 0.35), PROIBIDA("Proibida", 0);
        public final String nome; public final double fator;
        Compat(String nome, double fator) { this.nome = nome; this.fator = fator; }
    }

    private static boolean opostos(String a, String b) {
        return ("luz".equals(a) && "trevas".equals(b)) || ("trevas".equals(a) && "luz".equals(b));
    }

    /** Compatibilidade da combinação; secundária vazia = só a principal. */
    public static Compat compat(String principal, String secundaria) {
        Raca p = de(principal), s = de(secundaria);
        if (p == null) return Compat.PROIBIDA;
        if (s == null || principal.equals(secundaria)) return Compat.PURO;
        if (s.nivel() >= LENDARIA) return Compat.PROIBIDA;               // lendária nunca é secundária
        Set<String> par = Set.of(principal, secundaria);
        if (par.equals(Set.of("gigante", "halfling"))) return Compat.PROIBIDA;
        if (opostos(p.familia(), s.familia())) return p.lendaria() ? Compat.PROIBIDA : Compat.INSTAVEL;
        if (par.equals(Set.of("espirito", "gigante")) || par.equals(Set.of("espirito", "dragonoide"))) return Compat.INSTAVEL;
        if (p.familia().equals(s.familia())) return Compat.HARMONICA;
        if (par.contains("humano") || "divino".equals(p.familia())) return Compat.ESTAVEL;
        if (Set.of(p.familia(), s.familia()).equals(Set.of("draco", "fera"))) return Compat.HARMONICA;
        return Compat.ESTAVEL;
    }

    /** Fator da secundária (ou do puro-sangue, que vale para a principal). */
    public static double fatorPrincipal(String principal, String secundaria) {
        return compat(principal, secundaria) == Compat.PURO && principal.equals(secundaria) ? 1.3 : 1.0;
    }

    public static double fatorSecundaria(String principal, String secundaria) {
        if (secundaria == null || secundaria.isEmpty() || secundaria.equals(principal)) return 0;
        return compat(principal, secundaria).fator;
    }

    /** Soma dos números das duas raças já com os fatores. */
    public static Map<String, Double> somar(String principal, String secundaria) {
        Map<String, Double> t = new LinkedHashMap<>();
        Raca p = de(principal), s = de(secundaria);
        double fp = p == null ? 0 : fatorPrincipal(principal, secundaria), fs = s == null ? 0 : fatorSecundaria(principal, secundaria);
        if (p != null) p.numeros().forEach((k, v) -> t.merge(k, v * fp, Double::sum));
        if (s != null && fs > 0) s.numeros().forEach((k, v) -> t.merge(k, v * fs, Double::sum));
        return t;
    }

    /** Efeitos liga/desliga ativos na combinação. */
    public static Set<String> efeitos(String principal, String secundaria) {
        Set<String> e = new java.util.HashSet<>();
        Raca p = de(principal), s = de(secundaria);
        if (p != null) e.addAll(p.efeitos());
        if (s != null && fatorSecundaria(principal, secundaria) >= 0.5)
            for (String x : s.efeitos()) if (!DESVANTAGENS_BINARIAS.contains(x)) e.add(x);
        return e;
    }

    /** Texto curto para a tela: "+12% dano", "−2 vida"... */
    public static List<String> resumo(Map<String, Double> t) {
        List<String> l = new ArrayList<>();
        t.forEach((k, v) -> {
            if (Math.abs(v) < 0.05) return;
            String sinal = v > 0 ? "+" : "−", num = fmt(Math.abs(v));
            String txt = switch (k) {
                case "vida" -> sinal + fmt(Math.abs(v) / 2) + " coração(ões)";
                case "velocidade" -> sinal + num + "% velocidade";
                case "dano" -> sinal + num + "% dano corpo a corpo";
                case "armadura" -> sinal + num + " armadura";
                case "resistencia" -> sinal + num + " resistência";
                case "sorte" -> sinal + num + " sorte";
                case "alcance" -> sinal + num + " bloco(s) de alcance";
                case "empurrao" -> sinal + fmt(Math.abs(v) * 100) + "% resistência a empurrão";
                case "xp" -> sinal + num + "% XP";
                case "reputacao" -> sinal + num + "% reputação";
                case "recursoMax" -> sinal + num + "% recurso máximo";
                case "regenRecurso" -> sinal + num + "% regeneração de recurso";
                case "danoArco" -> sinal + num + "% dano com arco";
                case "danoMagico" -> sinal + num + "% dano mágico";
                case "mineracao" -> sinal + num + "% mineração";
                case "fogo" -> (v >= 100 ? "Imune a fogo" : "−" + num + "% dano de fogo");
                case "rouboVida" -> sinal + num + "% roubo de vida";
                case "esquiva" -> sinal + num + "% esquiva";
                case "curaRecebida" -> sinal + num + "% cura recebida";
                case "danoMagicoRecebido" -> sinal + num + "% dano mágico recebido";
                case "fome" -> sinal + num + "% fome";
                default -> k + " " + sinal + num;
            };
            l.add(txt);
        });
        return l;
    }

    /** Se o número é bom para o jogador (para colorir). */
    public static boolean bom(String k, double v) {
        boolean inverso = k.equals("danoMagicoRecebido") || k.equals("fome");
        return inverso ? v < 0 : v > 0;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    public static String nomeEfeito(String e) {
        return switch (e) {
            case VISAO_NOTURNA -> "Enxerga no escuro";
            case QUEDA_LENTA -> "Cai devagar";
            case SEM_QUEDA -> "Sem dano de queda";
            case FRACO_SOL -> "Fraco sob o sol";
            case AURA -> "Aura do Rei Demônio";
            case SOPRO -> "Sopro de fogo";
            case REGEN_TOTAL -> "Regeneração fora de combate";
            default -> e;
        };
    }
}
