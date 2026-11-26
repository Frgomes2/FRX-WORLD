package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Árvore de talentos: cada classe tem 3 ramos com 5 talentos. 1 ponto a cada 2 níveis.
 * O 2º talento de um ramo pede 5 pontos naquele ramo, o 3º pede 10, o 4º 15 e o último (capstone) 20.
 * Interface no chat com cliques: /talentos.
 */
public class Talentos {

    /** O que um ponto de talento melhora. valor = por ponto. */
    public enum Atributo {
        DANO_HAB("% de dano das habilidades", 100),
        DANO_FISICO("% de dano corpo a corpo", 100),
        DANO_DISTANCIA("% de dano de flechas e projéteis", 100),
        VIDA(" de vida", 1),
        ARMADURA(" de armadura", 1),
        RESISTENCIA(" de resistência de armadura", 1),
        VELOCIDADE("% de velocidade", 100),
        RECARGA("% menos recarga", 100),
        CUSTO("% menos custo de recurso", 100),
        RECURSO_MAX(" de recurso máximo", 1),
        REGEN("% de regeneração de recurso", 100),
        CRITICO("% de chance de crítico (×1,5)", 100),
        ROUBO_VIDA("% de roubo de vida", 100),
        CURA("% de cura das habilidades", 100),
        INVOCACAO("% de força e duração das invocações", 100),
        ESQUIVA("% de chance de esquiva", 100);

        final String texto;
        final int escala;   // 100 = mostrar como porcentagem

        Atributo(String texto, int escala) { this.texto = texto; this.escala = escala; }

        String descrever(double v) {
            double x = v * escala;
            String n = x == Math.rint(x) ? String.valueOf((long) x) : String.format("%.1f", x);
            return "+" + n + texto;
        }
    }

    public record Talento(String id, String nome, Atributo atributo, double porPonto, int maximo, int linha, String ramo) {}
    public record Ramo(String id, String nome, List<Talento> talentos) {}

    private static final Map<String, List<Ramo>> ARVORES = new LinkedHashMap<>();
    private static final Map<String, Talento> TODOS = new HashMap<>();
    private static final int[] MAXIMOS = {5, 5, 5, 3, 1};

    private static void ramo(String classe, String id, String nome, Object... t) {
        List<Talento> lista = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String nomeT = (String) t[i * 3];
            Atributo a = (Atributo) t[i * 3 + 1];
            double v = ((Number) t[i * 3 + 2]).doubleValue();
            Talento tal = new Talento(classe.toLowerCase() + "." + id + "." + i, nomeT, a, v, MAXIMOS[i], i, id);
            lista.add(tal);
            TODOS.put(tal.id(), tal);
        }
        ARVORES.computeIfAbsent(classe, k -> new ArrayList<>()).add(new Ramo(id, nome, lista));
    }

    static {
        Atributo DH = Atributo.DANO_HAB, DF = Atributo.DANO_FISICO, DD = Atributo.DANO_DISTANCIA, VI = Atributo.VIDA, AR = Atributo.ARMADURA,
                RE = Atributo.RESISTENCIA, VE = Atributo.VELOCIDADE, RC = Atributo.RECARGA, CU = Atributo.CUSTO, RM = Atributo.RECURSO_MAX,
                RG = Atributo.REGEN, CR = Atributo.CRITICO, RV = Atributo.ROUBO_VIDA, CA = Atributo.CURA, IN = Atributo.INVOCACAO, ES = Atributo.ESQUIVA;

        ramo("GUERREIRO", "furia", "Fúria",
                "Golpes Brutais", DF, .03, "Sede de Sangue", RV, .015, "Habilidades Ferozes", DH, .03, "Golpe Certeiro", CR, .03, "Avatar da Guerra", DF, .15);
        ramo("GUERREIRO", "baluarte", "Baluarte",
                "Pele de Ferro", AR, 1, "Vigor", VI, 2, "Couraça", RE, .5, "Inabalável", ES, .02, "Muralha Viva", VI, 10);
        ramo("GUERREIRO", "comandante", "Senhor da Guerra",
                "Grito de Guerra", RM, 6, "Fôlego", CU, .03, "Tática", RC, .03, "Marcha", VE, .02, "Comandante Lendário", DH, .15);

        ramo("ARQUEIRO", "atirador", "Atirador",
                "Flechas Afiadas", DD, .04, "Olho de Águia", CR, .03, "Disparo Perfurante", DH, .03, "Mira Mortal", CR, .03, "Tiro do Destino", DD, .20);
        ramo("ARQUEIRO", "patrulheiro", "Patrulheiro",
                "Passos Leves", VE, .02, "Reflexos", ES, .02, "Resistência da Trilha", VI, 2, "Sobrevivente", AR, 1, "Vento Selvagem", VE, .08);
        ramo("ARQUEIRO", "cacador", "Caçador",
                "Preparação", RC, .03, "Economia", CU, .03, "Fôlego de Caçador", RG, .08, "Instinto", DH, .04, "Mestre Caçador", RC, .12);

        ramo("MAGO", "arcano", "Arcano",
                "Foco Arcano", DH, .04, "Feitiço Crítico", CR, .02, "Potência", DH, .03, "Ruptura", CR, .03, "Arquimago", DH, .20);
        ramo("MAGO", "elementalista", "Elementalista",
                "Fluxo", RC, .03, "Canalização", CU, .03, "Afinidade", DH, .02, "Torrente", RC, .03, "Tempestade Eterna", CU, .15);
        ramo("MAGO", "sabio", "Sábio",
                "Mente Vasta", RM, 15, "Meditação", RG, .08, "Barreira Mística", AR, 1, "Vitalidade", VI, 2, "Fonte Inesgotável", RG, .40);

        ramo("CLERIGO", "luz", "Luz",
                "Toque Sagrado", CA, .05, "Prece", RG, .06, "Bênção", CA, .05, "Graça", RM, 10, "Milagre", CA, .30);
        ramo("CLERIGO", "templario", "Templário",
                "Martelo da Fé", DF, .03, "Escudo da Fé", AR, 1, "Couraça Sagrada", RE, .5, "Fervor", VI, 2, "Cruzado", DH, .15);
        ramo("CLERIGO", "oraculo", "Oráculo",
                "Visão", RC, .03, "Devoção", CU, .03, "Sabedoria", RM, 8, "Harmonia", RG, .08, "Profeta", RC, .12);

        ramo("LADINO", "assassino", "Assassino",
                "Lâmina Oculta", DF, .03, "Ponto Fraco", CR, .03, "Veneno", DH, .03, "Execução", CR, .03, "Sombra da Morte", DF, .20);
        ramo("LADINO", "sombra", "Sombra",
                "Passo Silencioso", VE, .02, "Evasão", ES, .02, "Agilidade", VE, .02, "Ilusão", ES, .02, "Intangível", ES, .08);
        ramo("LADINO", "trapaceiro", "Trapaceiro",
                "Truques", RC, .03, "Sangria", RV, .015, "Oportunista", CU, .03, "Rapina", RV, .02, "Mestre dos Truques", RC, .15);

        ramo("ASSASSINO", "letalidade", "Letalidade",
                "Fio da Lâmina", DF, .04, "Golpe Preciso", CR, .03, "Artéria", DH, .04, "Instinto Assassino", CR, .04, "Lâmina do Fim", DH, .22);
        ramo("ASSASSINO", "sangria", "Sangria",
                "Sede", RV, .015, "Corte Fundo", DF, .03, "Banquete", RV, .02, "Frenesi", RC, .04, "Rio de Sangue", RV, .06);
        ramo("ASSASSINO", "fantasma", "Fantasma",
                "Pés de Vento", VE, .025, "Esquiva", ES, .02, "Fôlego Curto", CU, .04, "Reflexo", ES, .02, "Ninguém Me Vê", VE, .10);

        ramo("INVOCADOR", "convocacao", "Convocação",
                "Elo", IN, .06, "Vínculo", IN, .06, "Chamado Rápido", RC, .03, "Legião", IN, .08, "Senhor das Hordas", IN, .40);
        ramo("INVOCADOR", "pacto", "Pacto",
                "Sangue Antigo", DH, .03, "Pacto de Vida", VI, 2, "Dreno", RV, .015, "Pele Sombria", AR, 1, "Pacto Proibido", DH, .15);
        ramo("INVOCADOR", "ritualista", "Ritualista",
                "Círculo", RM, 12, "Cântico", RG, .06, "Oferenda", CU, .03, "Selo", RC, .03, "Grande Ritual", RG, .35);

        ramo("PALADINO", "baluarte_sagrado", "Baluarte",
                "Fé de Aço", AR, 1, "Coração Firme", VI, 2, "Manto da Aurora", RE, .5, "Inquebrável", VI, 3, "Muralha da Fé", RE, 2);
        ramo("PALADINO", "justica", "Justiça",
                "Lâmina Abençoada", DF, .03, "Veredito", DH, .03, "Golpe Justo", CR, .02, "Ira Santa", DH, .04, "Mão do Juiz", DF, .15);
        ramo("PALADINO", "devocao", "Devoção",
                "Mãos que Curam", CA, .05, "Oração", RG, .06, "Reserva de Fé", RM, 10, "Compaixão", CA, .06, "Santo Protetor", CA, .25);

        ramo("BARDO", "maestro", "Maestro",
                "Voz Afinada", CA, .05, "Fôlego Longo", RG, .06, "Repertório", RM, 10, "Harmonia", CA, .05, "Grande Maestro", RG, .35);
        ramo("BARDO", "virtuose", "Virtuose",
                "Nota Afiada", DH, .03, "Improviso", RC, .03, "Agudo", CR, .03, "Solo", DH, .04, "Virtuose Lendário", RC, .12);
        ramo("BARDO", "andarilho", "Andarilho",
                "Pé na Estrada", VE, .02, "Jogo de Cintura", ES, .02, "Casca Grossa", VI, 2, "Economia de Palco", CU, .03, "Lenda das Tavernas", VE, .08);

        ramo("MONGE", "punho", "Punho de Ferro",
                "Juntas Calejadas", DF, .04, "Golpe Seco", CR, .03, "Peso do Corpo", DF, .04, "Ponto Vital", CR, .03, "Punho que Racha Pedra", DF, .20);
        ramo("MONGE", "vento", "Vento",
                "Passo Leve", VE, .02, "Fluir", ES, .02, "Corrente de Ar", VE, .02, "Folha ao Vento", ES, .02, "Intocável", ES, .08);
        ramo("MONGE", "espirito", "Espírito",
                "Respiração", RM, 8, "Corpo Temperado", VI, 2, "Disciplina", CU, .04, "Mente Calma", RC, .03, "Mestre do Chi", RM, 30);

        ramo("SAMURAI", "fio", "Fio da Lâmina",
                "Fio Perfeito", DF, .03, "Corte Limpo", CR, .02, "Mão Firme", DF, .03, "Golpe Certeiro", CR, .03, "Um Corte, Uma Vida", DF, .15);
        ramo("SAMURAI", "formas", "Três Formas",
                "Brasa Viva", DH, .03, "Maré Cheia", DH, .03, "Trovão Distante", CR, .02, "Fluxo", DH, .04, "Mestre das Formas", DH, .15);
        ramo("SAMURAI", "honra", "Honra",
                "Postura", AR, 1, "Sangue Frio", VI, 2, "Pele de Aço", RE, .5, "Vontade", VI, 3, "Inabalável", RE, 2);
        ramo("CARTOMANTE", "naipes", "Naipes",
                "Fio da Carta", DH, .03, "Mão Rápida", DH, .03, "Carta Afiada", CR, .02, "Baralho Viciado", DH, .04, "Rei de Espadas", DH, .15);
        ramo("CARTOMANTE", "baralho", "Baralho",
                "Embaralhar Ligeiro", RC, .03, "Manga Larga", CU, .04, "Dedos Leves", RC, .03, "Carta na Manga", CU, .04, "Mestre do Baralho", RC, .10);
        ramo("CARTOMANTE", "sorte", "Sorte",
                "Pé de Coelho", VI, 2, "Trevo", CR, .02, "Pele de Jogador", RE, .5, "Sangue Frio", VI, 3, "Nascido com Sorte", CR, .08);
        ramo("DRUIDA", "garra", "Garra",
                "Unhas Afiadas", DF, .03, "Faro", CR, .03, "Patas Ligeiras", VE, .02, "Instinto de Caça", DF, .04, "Alfa da Matilha", DF, .15);
        ramo("DRUIDA", "guardiao_selvagem", "Guardião",
                "Couro Grosso", AR, 1, "Fôlego de Urso", VI, 2, "Pelagem Densa", RE, .5, "Raízes Fundas", VI, 3, "Ancião da Mata", VI, 8);
        ramo("DRUIDA", "bosque", "Bosque",
                "Seiva", CA, .05, "Orvalho", RG, .06, "Reserva Verde", RM, 10, "Florescer", CA, .05, "Voz da Floresta", RG, .30);
    }

    // ------------------------------------------------------------------ cálculo

    public static int pontosTotais(DadosJogador d) { return Math.max(0, d.nivel() / 2); }

    public static int gastos(DadosJogador d) {
        int s = 0;
        for (int v : d.talentos().values()) s += v;
        return s;
    }

    private static int noRamo(DadosJogador d, String classe, String ramo) {
        int s = 0;
        for (Map.Entry<String, Integer> e : d.talentos().entrySet()) {
            Talento t = TODOS.get(e.getKey());
            if (t != null && t.ramo().equals(ramo) && e.getKey().startsWith(classe.toLowerCase() + ".")) s += e.getValue();
        }
        return s;
    }

    private static final Map<UUID, EnumMap<Atributo, Double>> CACHE = new HashMap<>();

    /** Bônus total do jogador num atributo (ex.: 0,12 = +12%). */
    public static double bonus(ServerPlayer p, Atributo a) {
        EnumMap<Atributo, Double> m = CACHE.get(p.getUUID());
        if (m == null) m = recalcular(p);
        return m.getOrDefault(a, 0.0) + br.com.frxcode.frxworld.reinos.Bencao.bonus(p, a);   // armadura abençoada pela Hinata
    }

    /** 1 + bônus (para multiplicar). */
    public static double fator(ServerPlayer p, Atributo a) { return 1 + bonus(p, a); }

    private static EnumMap<Atributo, Double> recalcular(ServerPlayer p) {
        EnumMap<Atributo, Double> m = new EnumMap<>(Atributo.class);
        if (Progressao.temDados(p)) {
            DadosJogador d = Progressao.dados(p);
            String prefixo = d.classe().toLowerCase() + ".";
            for (Map.Entry<String, Integer> e : d.talentos().entrySet()) {
                Talento t = TODOS.get(e.getKey());
                if (t == null || !e.getKey().startsWith(prefixo)) continue;
                m.merge(t.atributo(), t.porPonto() * Math.min(e.getValue(), t.maximo()), Double::sum);
            }
        }
        CACHE.put(p.getUUID(), m);
        return m;
    }

    // ------------------------------------------------------------------ atributos do jogador

    private static UUID id(String n) { return UUID.nameUUIDFromBytes(("frxworld:talento_" + n).getBytes(StandardCharsets.UTF_8)); }

    private static void mod(ServerPlayer p, Attribute a, String nome, double valor, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        UUID u = id(nome);
        inst.removeModifier(u);
        if (valor != 0) inst.addTransientModifier(new AttributeModifier(u, "FRX talento " + nome, valor, op));
    }

    public static void aplicar(ServerPlayer p) {
        recalcular(p);
        mod(p, Attributes.MAX_HEALTH, "vida", bonus(p, Atributo.VIDA), AttributeModifier.Operation.ADDITION);
        // Nível do personagem: +0,4 de vida por nível acima do 1 (nível 30 = +11,6 = quase 6 corações; teto de +40)
        int lvl = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
        mod(p, Attributes.MAX_HEALTH, "nivel_vida", Math.min(40, 0.4 * (lvl - 1)), AttributeModifier.Operation.ADDITION);
        mod(p, Attributes.ARMOR, "armadura", bonus(p, Atributo.ARMADURA), AttributeModifier.Operation.ADDITION);
        mod(p, Attributes.ARMOR_TOUGHNESS, "resistencia", bonus(p, Atributo.RESISTENCIA), AttributeModifier.Operation.ADDITION);
        mod(p, Attributes.MOVEMENT_SPEED, "velocidade", bonus(p, Atributo.VELOCIDADE), AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(p, Attributes.ATTACK_DAMAGE, "fisico", bonus(p, Atributo.DANO_FISICO), AttributeModifier.Operation.MULTIPLY_TOTAL);
        // com o Apothic Attributes, crítico / roubo de vida / esquiva / dano à distância viram atributos reais
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.CRIT_CHANCE, "talento_critico", bonus(p, Atributo.CRITICO));
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.LIFE_STEAL, "talento_roubo", bonus(p, Atributo.ROUBO_VIDA));
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.DODGE, "talento_esquiva", Math.min(0.5, bonus(p, Atributo.ESQUIVA)));
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.ARROW_DAMAGE, "talento_distancia", bonus(p, Atributo.DANO_DISTANCIA));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ------------------------------------------------------------------ ações

    private static void avisar(ServerPlayer p, String texto, ChatFormatting cor) {
        p.sendSystemMessage(Component.literal(texto).withStyle(cor));
    }

    static void aprender(ServerPlayer p, String id) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        Talento t = TODOS.get(id);
        if (d.classe().isEmpty()) { avisar(p, "Escolha uma classe primeiro (tecla K).", ChatFormatting.YELLOW); return; }
        if (t == null || !id.startsWith(d.classe().toLowerCase() + ".")) { avisar(p, "Esse talento não é da sua classe.", ChatFormatting.RED); return; }
        int livres = pontosTotais(d) - gastos(d);
        if (livres <= 0) { avisar(p, "Sem pontos livres. Você ganha 1 ponto a cada 2 níveis.", ChatFormatting.RED); return; }
        int atual = d.talentos().getOrDefault(id, 0);
        if (atual >= t.maximo()) { avisar(p, t.nome() + " já está no máximo.", ChatFormatting.YELLOW); return; }
        int precisa = t.linha() * 5;
        if (noRamo(d, d.classe(), t.ramo()) < precisa) {
            avisar(p, t.nome() + " pede " + precisa + " pontos no ramo.", ChatFormatting.RED);
            return;
        }
        d.talentos().put(id, atual + 1);
        aplicar(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
        mostrar(p);
    }

    static void resetar(ServerPlayer p, boolean gratis) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        int gastos = gastos(d);
        if (gastos == 0) { avisar(p, "Você não tem talentos para redistribuir.", ChatFormatting.YELLOW); return; }
        long preco = 10L * d.nivel();
        if (!gratis && !Moedas.cobrar(p, preco)) {
            avisar(p, "Redistribuir custa " + Moedas.texto(preco) + " (10 cobre por nível).", ChatFormatting.RED);
            return;
        }
        d.talentos().clear();
        aplicar(p);
        avisar(p, "Talentos zerados: " + gastos + " pontos devolvidos" + (gratis ? "." : " por " + Moedas.texto(preco) + "."), ChatFormatting.GREEN);
        mostrar(p);
    }

    // ------------------------------------------------------------------ janela de status (tecla E)

    public static com.google.gson.JsonObject json(ServerPlayer p) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        if (!Progressao.temDados(p)) return o;
        DadosJogador d = Progressao.dados(p);
        int livres = pontosTotais(d) - gastos(d);
        o.addProperty("livres", livres);
        o.addProperty("total", pontosTotais(d));
        o.addProperty("precoReset", Moedas.texto(10L * d.nivel()));
        com.google.gson.JsonArray ramos = new com.google.gson.JsonArray();
        for (Ramo r : ARVORES.getOrDefault(d.classe(), List.of())) {
            int noR = noRamo(d, d.classe(), r.id());
            com.google.gson.JsonObject jr = new com.google.gson.JsonObject();
            jr.addProperty("nome", r.nome());
            jr.addProperty("pontos", noR);
            com.google.gson.JsonArray ts = new com.google.gson.JsonArray();
            for (Talento t : r.talentos()) {
                int atual = d.talentos().getOrDefault(t.id(), 0);
                boolean liberado = noR >= t.linha() * 5;
                com.google.gson.JsonObject jt = new com.google.gson.JsonObject();
                jt.addProperty("id", t.id());
                jt.addProperty("nome", t.nome());
                jt.addProperty("atual", atual);
                jt.addProperty("max", t.maximo());
                jt.addProperty("liberado", liberado);
                jt.addProperty("pode", liberado && livres > 0 && atual < t.maximo());
                jt.addProperty("porPonto", t.atributo().descrever(t.porPonto()) + " por ponto");
                jt.addProperty("efeito", atual > 0 ? t.atributo().descrever(t.porPonto() * atual) : "");
                jt.addProperty("pede", liberado ? "" : "Pede " + t.linha() * 5 + " pontos em " + r.nome());
                ts.add(jt);
            }
            jr.add("talentos", ts);
            ramos.add(jr);
        }
        o.add("ramos", ramos);
        return o;
    }

    // ------------------------------------------------------------------ interface no chat

    public static void mostrar(ServerPlayer p) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        if (d.classe().isEmpty()) { avisar(p, "Escolha uma classe primeiro (tecla K).", ChatFormatting.YELLOW); return; }
        List<Ramo> ramos = ARVORES.getOrDefault(d.classe(), List.of());
        int livres = pontosTotais(d) - gastos(d);
        p.sendSystemMessage(Component.literal("══════ Talentos de " + Habilidades.nomeClasse(d.classe()) + " ══════").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("Pontos livres: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(livres)).withStyle(livres > 0 ? ChatFormatting.GREEN : ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("  (1 a cada 2 níveis · clique no [+] para gastar)").withStyle(ChatFormatting.DARK_GRAY)));
        for (Ramo r : ramos) {
            int noR = noRamo(d, d.classe(), r.id());
            p.sendSystemMessage(Component.literal("◆ " + r.nome() + " ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                    .append(Component.literal("(" + noR + " pontos)").withStyle(ChatFormatting.GRAY)));
            for (Talento t : r.talentos()) {
                int atual = d.talentos().getOrDefault(t.id(), 0);
                boolean liberado = noR >= t.linha() * 5;
                boolean pode = liberado && livres > 0 && atual < t.maximo();
                MutableComponent botao = Component.literal(pode ? "[+]" : atual >= t.maximo() ? "[✔]" : "[ ]")
                        .withStyle(pode ? ChatFormatting.GREEN : atual >= t.maximo() ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
                MutableComponent dica = Component.literal(t.nome()).withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("\n" + t.atributo().descrever(t.porPonto()) + " por ponto").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("\nAtual: " + t.atributo().descrever(t.porPonto() * atual)).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(liberado ? "" : "\nPede " + t.linha() * 5 + " pontos em " + r.nome()).withStyle(ChatFormatting.RED));
                Style estilo = Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, dica));
                if (pode) estilo = estilo.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/talentos aprender " + t.id()));
                MutableComponent linha = Component.literal("   ").append(botao.withStyle(estilo)).append(Component.literal(" "))
                        .append(Component.literal(t.nome()).withStyle(estilo.withColor(liberado ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)))
                        .append(Component.literal(" " + atual + "/" + t.maximo()).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  " + t.atributo().descrever(t.porPonto() * Math.max(1, atual))).withStyle(ChatFormatting.DARK_AQUA));
                p.sendSystemMessage(linha);
            }
        }
        long preco = 10L * d.nivel();
        p.sendSystemMessage(Component.literal("[Redistribuir pontos: " + Moedas.texto(preco) + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/talentos resetar"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Devolve todos os pontos. Confirme apertando Enter.")))));
    }

    private static final Map<UUID, Integer> AVISADO = new HashMap<>();

    /** Chamado ao subir de nível / entrar: avisa se há pontos livres (uma vez por nível). */
    public static void avisarPontos(ServerPlayer p) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        if (d.classe().isEmpty()) return;
        int livres = pontosTotais(d) - gastos(d);
        Integer antes = AVISADO.put(p.getUUID(), d.nivel());
        if (livres <= 0 || (antes != null && antes == d.nivel())) return;
        p.sendSystemMessage(Component.literal("✦ Você tem " + livres + (livres == 1 ? " ponto" : " pontos") + " de talento. ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("[Abrir talentos]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/talentos")))));
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { aplicar(p); avisarPontos(p); }
    }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) aplicar(p);
    }

    @SubscribeEvent
    public void aoMudarDimensao(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) aplicar(p);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent e) {
        CACHE.remove(e.getEntity().getUUID());
    }

    /** Crítico, roubo de vida e dano de projéteis. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoFerir(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        if (br.com.frxcode.frxworld.atributos.Apoth.ativo()) return;   // o Apothic Attributes cuida disso
        float dano = e.getAmount();
        if (e.getSource().getDirectEntity() instanceof Projectile) dano *= (float) fator(p, Atributo.DANO_DISTANCIA);
        if (ThreadLocalRandom.current().nextDouble() < bonus(p, Atributo.CRITICO)) {
            dano *= 1.5f;
            LivingEntity alvo = e.getEntity();
            p.serverLevel().sendParticles(ParticleTypes.CRIT, alvo.getX(), alvo.getY() + alvo.getBbHeight() * 0.6, alvo.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
            p.level().playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1f);
        }
        e.setAmount(dano);
        double roubo = bonus(p, Atributo.ROUBO_VIDA);
        if (roubo > 0) p.heal((float) (dano * roubo));
    }

    /** Esquiva: chance de ignorar o golpe. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoSerAtacado(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSource().getEntity() == null) return;
        if (br.com.frxcode.frxworld.atributos.Apoth.ativo()) return;
        double esq = Math.min(0.5, bonus(p, Atributo.ESQUIVA));
        if (esq > 0 && ThreadLocalRandom.current().nextDouble() < esq) {
            e.setCanceled(true);
            p.displayClientMessage(Component.literal("Esquivou!").withStyle(ChatFormatting.AQUA), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8f, 1.5f);
        }
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("talentos")
                .executes(ctx -> { mostrar(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("aprender").then(Commands.argument("id", StringArgumentType.string())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TODOS.keySet(), b))
                        .executes(ctx -> { aprender(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")); return 1; })))
                .then(Commands.literal("resetar").executes(ctx -> { resetar(ctx.getSource().getPlayerOrException(), false); return 1; })));
        evento.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("talentos").then(Commands.literal("resetar").then(Commands.argument("jogador", EntityArgument.player())
                        .executes(ctx -> { resetar(EntityArgument.getPlayer(ctx, "jogador"), true); return 1; })))));
    }
}
