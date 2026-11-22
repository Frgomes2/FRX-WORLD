package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Reputação com cada capital. Sobe com missões (no reino mais próximo), abates perto da capital e defesa de invasões.
 * Níveis dão desconto nas lojas e na forja, liberam itens especiais no mercador e títulos (/titulo).
 */
public class Reputacao {

    public record Nivel(String nome, int minimo, double desconto, ChatFormatting cor) {}

    public static final List<Nivel> NIVEIS = List.of(
            new Nivel("Neutro", 0, 0.00, ChatFormatting.GRAY),
            new Nivel("Amigável", 100, 0.03, ChatFormatting.GREEN),
            new Nivel("Honrado", 500, 0.06, ChatFormatting.AQUA),
            new Nivel("Reverenciado", 1500, 0.10, ChatFormatting.BLUE),
            new Nivel("Exaltado", 4000, 0.15, ChatFormatting.LIGHT_PURPLE),
            new Nivel("Herói", 10000, 0.20, ChatFormatting.GOLD));

    /** Lado negativo: a partir destes pontos o reino trata o jogador como Malvisto, Hostil e Inimigo. */
    public static final int MALVISTO = -100, HOSTIL = -500, INIMIGO = -1500, PISO = -3000;
    private static final String[] NOME_HOSTIL = {"", "Malvisto", "Hostil", "Inimigo"};
    private static final ChatFormatting[] COR_HOSTIL = {ChatFormatting.GRAY, ChatFormatting.YELLOW, ChatFormatting.RED, ChatFormatting.DARK_RED};
    /** Acréscimo no preço das lojas por grau de hostilidade. */
    private static final double[] ACRESCIMO = {0, 0.15, 0.30, 0.30};

    /** Reinos rivais: ajudar um custa um quarto do ganho no outro. Dwargon e Sakura são neutros. */
    private static final java.util.Map<String, String> RIVAIS = java.util.Map.of(
            "lubelius", "demonios", "demonios", "lubelius",
            "oeste", "tempest", "tempest", "oeste",
            "leste", "sarion", "sarion", "leste");

    public static String rival(String reino) { return reino == null ? null : RIVAIS.get(reino); }

    /** 0 = normal, 1 = Malvisto, 2 = Hostil, 3 = Inimigo. */
    public static int hostilidade(int pontos) { return pontos <= INIMIGO ? 3 : pontos <= HOSTIL ? 2 : pontos <= MALVISTO ? 1 : 0; }
    public static int hostilidade(ServerPlayer p, String reino) { return reino == null || reino.isEmpty() ? 0 : hostilidade(pontos(p, reino)); }

    /** O maior grau de hostilidade do jogador com qualquer reino. */
    public static int piorHostilidade(ServerPlayer p) {
        int pior = 0;
        for (Reino r : Reinos.todos()) pior = Math.max(pior, hostilidade(pontos(p, r.id())));
        return pior;
    }

    public static String nomeNivel(int pontos) { int h = hostilidade(pontos); return h > 0 ? NOME_HOSTIL[h] : NIVEIS.get(indice(pontos)).nome(); }
    public static ChatFormatting corNivel(int pontos) { int h = hostilidade(pontos); return h > 0 ? COR_HOSTIL[h] : NIVEIS.get(indice(pontos)).cor(); }

    public static int pontos(ServerPlayer p, String reino) {
        return Progressao.temDados(p) ? Progressao.dados(p).reputacao().getOrDefault(reino, 0) : 0;
    }

    public static int indice(int pontos) {
        int i = 0;
        for (int k = 0; k < NIVEIS.size(); k++) if (pontos >= NIVEIS.get(k).minimo()) i = k;
        return i;
    }

    public static Nivel nivel(ServerPlayer p, String reino) { return NIVEIS.get(indice(pontos(p, reino))); }

    /** Desconto nas lojas e na forja daquele reino (0..0,20). */
    /** Desconto da reputação no reino somado ao do rank da Guilda (teto de 50%). */
    public static double desconto(ServerPlayer p, String reino) {
        if (reino == null || reino.isEmpty()) return Math.min(0.5, br.com.frxcode.frxworld.progressao.Guilda.desconto(p));
        int h = hostilidade(p, reino);
        if (h > 0) return -ACRESCIMO[h];   // malvisto paga mais caro, e o rank da Guilda não ajuda
        return Math.min(0.5, nivel(p, reino).desconto() + br.com.frxcode.frxworld.progressao.Guilda.desconto(p));
    }

    public static Reino reino(String id) {
        for (Reino r : Reinos.todos()) if (r.id().equals(id)) return r;
        return null;
    }

    /** Capital onde o jogador está ou, fora delas, a mais próxima (até 3000 blocos). */
    public static Reino maisProximo(ServerPlayer p) {
        Reino melhor = null;
        int menor = 3000;
        for (Reino r : Reinos.todos()) {
            int d = r.distancia(p.getBlockX(), p.getBlockZ());
            if (d < menor) { menor = d; melhor = r; }
        }
        return melhor;
    }

    public static void ganhar(ServerPlayer p, String reino, int quanto, boolean avisar) { ganhar(p, reino, quanto, avisar, true); }

    /** Tira reputação (crime, sabotagem). Não mexe no rival. */
    public static void perder(ServerPlayer p, String reino, int quanto) { ganhar(p, reino, -Math.abs(quanto), true, false); }

    /** comRival: ganhos de 5 pontos ou mais custam um quarto no reino rival. */
    public static void ganhar(ServerPlayer p, String reino, int quanto, boolean avisar, boolean comRival) {
        if (!Progressao.temDados(p) || quanto == 0 || reino == null || reino.isEmpty()) return;
        if (quanto > 0) quanto = (int) Math.round(quanto * (1 + br.com.frxcode.frxworld.racas.RacasJogador.num(p, "reputacao") / 100));
        DadosJogador d = Progressao.dados(p);
        int antes = d.reputacao().getOrDefault(reino, 0);
        int depois = Math.max(PISO, antes + quanto);
        d.reputacao().put(reino, depois);
        Reino r = reino(reino);
        String nomeR = r != null ? r.nome() : reino;
        int ia = indice(antes), id = indice(depois);
        int ha = hostilidade(antes), hd = hostilidade(depois);
        if (hd != ha) {
            if (hd > ha) {
                String efeito = hd == 1 ? "Os preços sobem 15% por lá." : hd == 2 ? "Os lojistas não atendem mais você e os guardas atacam dentro da capital."
                        : "Há uma recompensa pela sua cabeça.";
                p.sendSystemMessage(Component.literal("☠ " + nomeR + " agora vê você como ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(NOME_HOSTIL[hd]).withStyle(COR_HOSTIL[hd], ChatFormatting.BOLD))
                        .append(Component.literal(". " + efeito + " O rei aceita uma multa para limpar seu nome.").withStyle(ChatFormatting.WHITE)));
                p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.25f, 1.4f);
            } else {
                p.sendSystemMessage(Component.literal("✦ " + nomeR + ": você voltou a ser ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(nomeNivel(depois)).withStyle(corNivel(depois), ChatFormatting.BOLD)).append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
            }
            Hostilidade.mudou(p, reino, ha, hd);
        } else if (quanto < 0 && avisar) {
            p.displayClientMessage(Component.literal(quanto + " reputação com " + nomeR).withStyle(ChatFormatting.RED), true);
        }
        String rival = comRival && quanto >= 5 ? RIVAIS.get(reino) : null;
        if (rival != null) ganhar(p, rival, -Math.max(1, quanto / 4), false, false);
        if (quanto < 0) return;
        if (id > ia) {
            Nivel n = NIVEIS.get(id);
            p.sendSystemMessage(Component.literal("✦ Reputação com " + nomeR + ": ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(n.nome()).withStyle(n.cor(), ChatFormatting.BOLD))
                    .append(Component.literal(" — " + Math.round(n.desconto() * 100) + "% de desconto" + (id >= 2 ? " e itens novos no mercador" : "")
                            + (id >= 4 ? ". Novo título liberado: /titulo" : "")).withStyle(ChatFormatting.WHITE)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1.2f);
            br.com.frxcode.frxworld.racas.Evolucao.checar(p);   // honra com um reino pode liberar a evolução
        } else if (avisar) {
            p.displayClientMessage(Component.literal("+" + quanto + " reputação com " + nomeR).withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** Missão entregue: reputação com a capital mais próxima. */
    public static void porMissao(ServerPlayer p, long xp) {
        Reino r = maisProximo(p);
        if (r != null) ganhar(p, r.id(), (int) Math.max(5, Math.min(200, xp / 10)), true);
    }

    /** Abates de monstros perto de uma capital (até 200 blocos da caixa): +1. */
    @SubscribeEvent
    public void aoMatar(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Enemy) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (e.getEntity().getTags().contains(Invasoes.TAG)) return;   // invasores contam na invasão
        for (Reino r : Reinos.todos()) {
            if (r.distancia(e.getEntity().getBlockX(), e.getEntity().getBlockZ()) <= 200) { ganhar(p, r.id(), 1, false); FavorReal.abate(p, r.id()); return; }
        }
    }

    // ------------------------------------------------------------------ títulos

    /** Títulos liberados: Exaltado = "Campeão de X", Herói = "Herói de X". */
    public static List<String> titulos(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        for (Reino r : Reinos.todos()) {
            int i = indice(pontos(p, r.id()));
            if (i >= 4) l.add("Campeão de " + curto(r.nome()));
            if (i >= 5) l.add("Herói de " + curto(r.nome()));
            if (hostilidade(pontos(p, r.id())) >= 3) l.add("Inimigo de " + curto(r.nome()));
        }
        if (piorHostilidade(p) >= 2) l.add("Fora da Lei");
        for (String t : br.com.frxcode.frxworld.progressao.Conquistas.titulos(p)) if (!l.contains(t)) l.add(t);
        for (String t : Campanhas.titulos(p)) if (!l.contains(t)) l.add(t);
        if (!Nomeacao.titulo(p).isEmpty()) l.add(Nomeacao.titulo(p));
        for (String t : br.com.frxcode.frxworld.aventura.Historia.titulos(p)) if (!l.contains(t)) l.add(t);
        for (String t : br.com.frxcode.frxworld.aventura.Torre.titulos(p)) if (!l.contains(t)) l.add(t);
        for (String t : br.com.frxcode.frxworld.aventura.Temporadas.titulos(p)) if (!l.contains(t)) l.add(t);
        return l;
    }

    /** Para a janela de status (tecla E). */
    public static com.google.gson.JsonArray json(ServerPlayer p) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (Reino r : Reinos.todos()) {
            int pts = pontos(p, r.id()), i = indice(pts);
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("id", r.id());
            j.addProperty("nome", r.nome());
            j.addProperty("pontos", pts);
            int h = hostilidade(pts);
            j.addProperty("nivel", nomeNivel(pts));
            j.addProperty("indice", i);
            j.addProperty("hostil", h);
            j.addProperty("desconto", h > 0 ? 0 : (int) Math.round(NIVEIS.get(i).desconto() * 100));
            j.addProperty("acrescimo", (int) Math.round(ACRESCIMO[h] * 100));
            // no lado negativo a barra anda de um degrau ruim até o seguinte, em direção ao zero
            int[] degraus = {PISO, INIMIGO, HOSTIL, MALVISTO};
            j.addProperty("minimo", h > 0 ? degraus[3 - h] : pts < 0 ? MALVISTO : NIVEIS.get(i).minimo());
            j.addProperty("proximo", h > 0 ? degraus[4 - h] : i + 1 < NIVEIS.size() ? NIVEIS.get(i + 1).minimo() : -1);
            j.addProperty("topo", h == 0 && i + 1 >= NIVEIS.size());
            Reino rv = reino(RIVAIS.getOrDefault(r.id(), ""));
            j.addProperty("rival", rv != null ? curto(rv.nome()) : "");
            a.add(j);
        }
        return a;
    }

    /** "Federação de Jura Tempest" → "Tempest"; "Império do Leste — Distrito Sakura" → "Sakura". */
    static String curto(String nome) {
        String[] p = nome.split("\\s+");
        return p[p.length - 1];
    }

    // ------------------------------------------------------------------ comandos

    private static MutableComponent barra(int pontos) {
        int i = indice(pontos);
        if (pontos < 0) return Component.literal(pontos + " pts").withStyle(corNivel(pontos));
        if (i >= NIVEIS.size() - 1) return Component.literal("██████████ máx").withStyle(ChatFormatting.GOLD);
        Nivel a = NIVEIS.get(i), b = NIVEIS.get(i + 1);
        double f = Math.max(0, Math.min(1, (pontos - a.minimo()) / (double) (b.minimo() - a.minimo())));
        int cheio = (int) Math.round(f * 10);
        return Component.literal("█".repeat(cheio)).withStyle(a.cor()).append(Component.literal("░".repeat(10 - cheio)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" " + pontos + "/" + b.minimo()).withStyle(ChatFormatting.GRAY));
    }

    static void mostrar(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("══════ Reputação com os reinos ══════").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (Reino r : Reinos.todos()) {
            int pts = pontos(p, r.id());
            p.sendSystemMessage(Component.literal(r.nome() + ": ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(nomeNivel(pts) + " ").withStyle(corNivel(pts), ChatFormatting.BOLD)).append(barra(pts)));
        }
        p.sendSystemMessage(Component.literal("Amigável 3% · Honrado 6% e itens especiais · Reverenciado 10% · Exaltado 15% e título · Herói 20% e título. "
                + "Sobe com missões, abates perto da capital e defendendo invasões.").withStyle(ChatFormatting.DARK_GRAY));
    }

    static void listarTitulos(ServerPlayer p) {
        List<String> l = titulos(p);
        String atual = Progressao.dados(p).titulo();
        if (l.isEmpty()) {
            p.sendSystemMessage(Component.literal("Nenhum título ainda. Ganhe com conquistas (/conquistas), missões épicas dos reis (/campanha) ou reputação Exaltado (/reputacao).").withStyle(ChatFormatting.GRAY));
            return;
        }
        p.sendSystemMessage(Component.literal("Seus títulos (clique para usar):").withStyle(ChatFormatting.GOLD));
        for (String t : l) {
            p.sendSystemMessage(Component.literal((t.equals(atual) ? " ✔ " : "   ") + t).withStyle(Style.EMPTY
                    .withColor(t.equals(atual) ? ChatFormatting.GOLD : ChatFormatting.WHITE)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/titulo usar " + t))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Usar este título")))));
        }
        p.sendSystemMessage(Component.literal("   [Sem título]").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/titulo usar nenhum"))));
    }

    static void usarTitulo(ServerPlayer p, String t) {
        if (t.equalsIgnoreCase("nenhum")) Progressao.dados(p).definirTitulo("");
        else if (titulos(p).contains(t)) Progressao.dados(p).definirTitulo(t);
        else { p.sendSystemMessage(Component.literal("Você não tem esse título.").withStyle(ChatFormatting.RED)); return; }
        br.com.frxcode.frxworld.rede.Rede.sincronizar(p);   // o título aparece em cima da cabeça para os outros
        Progressao.atualizarNome(p);
        Progressao.enviarAoPainel(p, false);
        p.sendSystemMessage(Component.literal(t.equalsIgnoreCase("nenhum") ? "Título removido." : "Agora você é \"" + t + "\".").withStyle(ChatFormatting.GREEN));
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("reputacao").executes(ctx -> { mostrar(ctx.getSource().getPlayerOrException()); return 1; }));
        evento.getDispatcher().register(Commands.literal("titulo")
                .executes(ctx -> { listarTitulos(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("usar").then(Commands.argument("titulo", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titulos(c.getSource().getPlayerOrException()), b))
                        .executes(ctx -> { usarTitulo(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "titulo")); return 1; }))));
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("reputacao").then(Commands.argument("reino", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Reinos.todos().stream().map(Reino::id), b))
                        .then(Commands.argument("pontos", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-100000, 100000))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ganhar(p, StringArgumentType.getString(ctx, "reino"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "pontos"), true, false);
                                    return 1;
                                })))));
    }
}
