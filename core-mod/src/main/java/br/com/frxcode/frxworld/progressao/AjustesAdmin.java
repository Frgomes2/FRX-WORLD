package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ajustes de admin que funcionam com o jogador offline: /frx ajustar <nick> nivel <n> | rank <F..S> | torre.
 * Online, aplica na hora. Offline, o pedido espera em <mundo>/frx_ajustes.txt ("nick;acao;valor") e entra no próximo login.
 */
public class AjustesAdmin {
    private static final List<String> LETRAS = List.of("F", "E", "D", "C", "B", "A", "S");

    public AjustesAdmin() {}

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_ajustes.txt"); }

    private static List<String> ler(MinecraftServer s) {
        try { return Files.exists(arquivo(s)) ? new ArrayList<>(Files.readAllLines(arquivo(s), StandardCharsets.UTF_8)) : new ArrayList<>(); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private static void gravar(MinecraftServer s, List<String> linhas) {
        try { Files.writeString(arquivo(s), String.join("\n", linhas) + (linhas.isEmpty() ? "" : "\n"), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Ajustes: não gravei frx_ajustes.txt: {}", e.toString()); }
    }

    private static String aplicar(ServerPlayer p, String acao, String valor) {
        switch (acao) {
            case "nivel" -> { Progressao.definirNivel(p, Integer.parseInt(valor)); return "nível " + valor; }
            case "rank" -> { Guilda.definirRank(p, Math.max(0, LETRAS.indexOf(valor))); return "Rank " + valor + " da Guilda"; }
            case "torre" -> { br.com.frxcode.frxworld.aventura.Torre.resetar(p); return "progresso da Torre zerado"; }
            default -> { return ""; }
        }
    }

    private static int pedir(CommandSourceStack src, String nick, String acao, String valor) {
        ServerPlayer p = src.getServer().getPlayerList().getPlayerByName(nick);
        if (p != null) {
            String feito = aplicar(p, acao, valor);
            p.sendSystemMessage(Component.literal("A administração ajustou a sua conta: " + feito + ".").withStyle(ChatFormatting.YELLOW));
            src.sendSuccess(() -> Component.literal("[ajuste] " + nick + ": " + feito), true);
            return 1;
        }
        List<String> l = ler(src.getServer());
        l.removeIf(x -> x.toLowerCase(Locale.ROOT).startsWith(nick.toLowerCase(Locale.ROOT) + ";" + acao + ";"));
        l.add(nick + ";" + acao + ";" + valor);
        gravar(src.getServer(), l);
        src.sendSuccess(() -> Component.literal("[ajuste] " + nick + " está offline: " + acao + (valor.isEmpty() ? "" : " " + valor) + " entra no próximo login."), true);
        return 1;
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        List<String> l = ler(p.server), resto = new ArrayList<>(), feitos = new ArrayList<>();
        String nick = p.getGameProfile().getName();
        for (String x : l) {
            String[] c = x.split(";", -1);
            if (c.length < 3 || !c[0].equalsIgnoreCase(nick)) { if (!x.isBlank()) resto.add(x); continue; }
            try { String f = aplicar(p, c[1], c[2]); if (!f.isEmpty()) feitos.add(f); }
            catch (Exception e) { FrxWorld.LOG.warn("Ajuste pendente de {} falhou ({}): {}", nick, x, e.toString()); }
        }
        if (feitos.isEmpty()) return;
        gravar(p.server, resto);
        p.sendSystemMessage(Component.literal("A administração ajustou a sua conta: " + String.join(", ", feitos) + ".").withStyle(ChatFormatting.YELLOW));
        FrxWorld.LOG.info("Ajustes pendentes aplicados a {}: {}", nick, feitos);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("ajustar").then(Commands.argument("nick", StringArgumentType.word())
                        .then(Commands.literal("nivel").then(Commands.argument("n", IntegerArgumentType.integer(1, 999))
                                .executes(c -> pedir(c.getSource(), StringArgumentType.getString(c, "nick"), "nivel", String.valueOf(IntegerArgumentType.getInteger(c, "n"))))))
                        .then(Commands.literal("rank").then(Commands.argument("letra", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(LETRAS, b))
                                .executes(c -> {
                                    String letra = StringArgumentType.getString(c, "letra").toUpperCase(Locale.ROOT);
                                    if (!LETRAS.contains(letra)) { c.getSource().sendFailure(Component.literal("Rank inválido: use F, E, D, C, B, A ou S.")); return 0; }
                                    return pedir(c.getSource(), StringArgumentType.getString(c, "nick"), "rank", letra);
                                })))
                        .then(Commands.literal("torre").executes(c -> pedir(c.getSource(), StringArgumentType.getString(c, "nick"), "torre", ""))))));
    }
}
