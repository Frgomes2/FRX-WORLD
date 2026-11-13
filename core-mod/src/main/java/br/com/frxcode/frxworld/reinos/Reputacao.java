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
    public static double desconto(ServerPlayer p, String reino) { return reino == null || reino.isEmpty() ? 0 : nivel(p, reino).desconto(); }

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

    public static void ganhar(ServerPlayer p, String reino, int quanto, boolean avisar) {
        if (!Progressao.temDados(p) || quanto == 0 || reino == null) return;
        if (quanto > 0) quanto = (int) Math.round(quanto * (1 + br.com.frxcode.frxworld.racas.RacasJogador.num(p, "reputacao") / 100));
        DadosJogador d = Progressao.dados(p);
        int antes = d.reputacao().getOrDefault(reino, 0);
        int depois = Math.max(-1000, antes + quanto);
        d.reputacao().put(reino, depois);
        Reino r = reino(reino);
        String nomeR = r != null ? r.nome() : reino;
        int ia = indice(antes), id = indice(depois);
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
            if (r.distancia(e.getEntity().getBlockX(), e.getEntity().getBlockZ()) <= 200) { ganhar(p, r.id(), 1, false); return; }
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
        }
        for (String t : br.com.frxcode.frxworld.progressao.Conquistas.titulos(p)) if (!l.contains(t)) l.add(t);
        for (String t : Campanhas.titulos(p)) if (!l.contains(t)) l.add(t);
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
            j.addProperty("nivel", NIVEIS.get(i).nome());
            j.addProperty("indice", i);
            j.addProperty("desconto", (int) Math.round(NIVEIS.get(i).desconto() * 100));
            j.addProperty("minimo", NIVEIS.get(i).minimo());
            j.addProperty("proximo", i + 1 < NIVEIS.size() ? NIVEIS.get(i + 1).minimo() : -1);
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
            Nivel n = NIVEIS.get(indice(pts));
            p.sendSystemMessage(Component.literal(r.nome() + ": ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(n.nome() + " ").withStyle(n.cor(), ChatFormatting.BOLD)).append(barra(pts)));
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
                                    ganhar(p, StringArgumentType.getString(ctx, "reino"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "pontos"), true);
                                    return 1;
                                })))));
    }
}
