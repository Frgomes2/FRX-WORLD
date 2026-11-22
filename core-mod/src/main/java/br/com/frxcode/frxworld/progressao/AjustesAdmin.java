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
 * Ajustes de admin que funcionam com o jogador offline: /frx ajustar <nick> nivel <n> | rank <F..S> | torre | moedas <cobre> |
 * item <id> <qtd> | chefe <nome> | aviso <texto>.
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
            case "moedas" -> {
                long cobre = Long.parseLong(valor);
                br.com.frxcode.frxworld.moedas.Moedas.dar(p, cobre);
                return br.com.frxcode.frxworld.moedas.Moedas.texto(cobre) + " em moedas";
            }
            case "item" -> {   // "id*qtd"
                String[] v = valor.split("\\*");
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse(v[0]));
                if (item == null || item == net.minecraft.world.item.Items.AIR) return "";
                int qtd = v.length > 1 ? Integer.parseInt(v[1]) : 1;
                net.minecraft.world.item.ItemStack pilha = new net.minecraft.world.item.ItemStack(item, qtd);
                String nome = pilha.getHoverName().getString();
                while (qtd > 0) {
                    int lote = Math.min(qtd, item.getMaxStackSize());
                    net.minecraft.world.item.ItemStack s2 = new net.minecraft.world.item.ItemStack(item, lote);
                    if (!p.getInventory().add(s2)) p.drop(s2, false);
                    qtd -= lote;
                }
                return (v.length > 1 ? v[1] : "1") + "× " + nome;
            }
            case "chefe" -> { Colecao.registrarChefe(p, valor); Progressao.enviarAoPainel(p, false); return valor + " na sua coleção de chefões"; }
            case "aviso" -> { p.sendSystemMessage(Component.literal("✉ " + valor).withStyle(ChatFormatting.GOLD)); return "-"; }
            default -> { return ""; }
        }
    }

    private static int pedir(CommandSourceStack src, String nick, String acao, String valor) {
        ServerPlayer p = src.getServer().getPlayerList().getPlayerByName(nick);
        if (p != null) {
            String feito = aplicar(p, acao, valor);
            if (!feito.isEmpty() && !feito.equals("-")) p.sendSystemMessage(Component.literal("A administração ajustou a sua conta: " + feito + ".").withStyle(ChatFormatting.YELLOW));
            src.sendSuccess(() -> Component.literal("[ajuste] " + nick + ": " + feito), true);
            return 1;
        }
        List<String> l = ler(src.getServer());
        String pre = nick.toLowerCase(Locale.ROOT) + ";" + acao + ";";
        if (acao.equals("moedas")) {
            long soma = Long.parseLong(valor);
            for (String x : l) if (x.toLowerCase(Locale.ROOT).startsWith(pre)) try { soma += Long.parseLong(x.substring(pre.length()).trim()); } catch (NumberFormatException ignorado) { }
            l.removeIf(x -> x.toLowerCase(Locale.ROOT).startsWith(pre));
            l.add(nick + ";moedas;" + soma);
        } else {
            if (!acao.equals("item") && !acao.equals("aviso") && !acao.equals("chefe")) l.removeIf(x -> x.toLowerCase(Locale.ROOT).startsWith(pre));
            l.add(nick + ";" + acao + ";" + valor.replace(";", ","));
        }
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
            try { String f = aplicar(p, c[1], c[2]); if (!f.isEmpty()) feitos.add(f); else resto.add(x); }
            catch (Exception e) { FrxWorld.LOG.warn("Ajuste pendente de {} falhou ({}): {}", nick, x, e.toString()); }
        }
        if (feitos.isEmpty()) return;
        gravar(p.server, resto);
        feitos.removeIf("-"::equals);
        if (feitos.isEmpty()) return;
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
                        .then(Commands.literal("torre").executes(c -> pedir(c.getSource(), StringArgumentType.getString(c, "nick"), "torre", "")))
                        .then(Commands.literal("moedas").then(Commands.argument("cobre", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                                .executes(c -> pedir(c.getSource(), StringArgumentType.getString(c, "nick"), "moedas", String.valueOf(com.mojang.brigadier.arguments.LongArgumentType.getLong(c, "cobre"))))))
                        .then(Commands.literal("item").then(Commands.argument("id", StringArgumentType.string()).then(Commands.argument("qtd", IntegerArgumentType.integer(1, 6400))
                                .executes(c -> pedir(c.getSource(), StringArgumentType.getString(c, "nick"), "item", StringArgumentType.getString(c, "id") + "*" + IntegerArgumentType.getInteger(c, "qtd"))))))
                        .then(Commands.literal("chefe").then(Commands.argument("nome", StringArgumentType.greedyString())
                                .executes(c -> pedir(c.getSource(), StringArgumentType.getString(c, "nick"), "chefe", StringArgumentType.getString(c, "nome").trim()))))
                        .then(Commands.literal("aviso").then(Commands.argument("texto", StringArgumentType.greedyString())
                                .executes(c -> pedir(c.getSource(), StringArgumentType.getString(c, "nick"), "aviso", StringArgumentType.getString(c, "texto").trim())))))));
    }
}
