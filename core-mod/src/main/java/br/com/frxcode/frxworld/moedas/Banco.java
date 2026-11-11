package br.com.frxcode.frxworld.moedas;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Banco dos reinos: moedas guardadas não caem na morte. Saldo no NBT persistente do jogador.
 * Banqueiro (NPC das capitais): clique = ver saldo; agachado + clique = depositar tudo.
 * /banco mostra o saldo em qualquer lugar; /banco sacar|depositar <cobre> só perto de um banqueiro.
 */
public class Banco {
    private static final String CHAVE = "frx_banco";

    public Banco() {}

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static long saldo(Player p) { return dados(p).getLong(CHAVE); }

    private static void definir(Player p, long v) { dados(p).putLong(CHAVE, Math.max(0, v)); }

    /** Admin (painel): define o saldo guardado. */
    public static void definirSaldo(ServerPlayer p, long v) {
        definir(p, v);
        br.com.frxcode.frxworld.progressao.Progressao.enviarAoPainel(p, false);
    }

    private static void avisar(ServerPlayer p, String texto, ChatFormatting cor) {
        p.sendSystemMessage(Component.literal("[Banco] ").withStyle(ChatFormatting.GOLD).append(Component.literal(texto).withStyle(cor)));
    }

    public static void mostrar(ServerPlayer p) {
        avisar(p, "Guardado: " + Moedas.texto(saldo(p)) + " · Na bolsa: " + Moedas.texto(Moedas.contar(p)) + ".", ChatFormatting.YELLOW);
        net.minecraft.network.chat.MutableComponent l = Component.literal("  ");
        l.append(botao("[Sacar 10 prata]", "/banco sacar 10 prata", ChatFormatting.AQUA)).append(" ");
        l.append(botao("[Sacar 1 ouro]", "/banco sacar 1 ouro", ChatFormatting.GOLD)).append(" ");
        l.append(botao("[Sacar tudo]", "/banco sacar tudo", ChatFormatting.GREEN)).append(" ");
        l.append(botao("[Depositar tudo]", "/banco depositar", ChatFormatting.YELLOW));
        p.sendSystemMessage(l);
        p.sendSystemMessage(Component.literal("  Ou digite: /banco sacar <quantidade> [cobre|prata|ouro]").withStyle(ChatFormatting.GRAY));
    }

    private static Component botao(String texto, String cmd, ChatFormatting cor) {
        return Component.literal(texto).withStyle(st -> st.withColor(cor).withBold(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(cmd))));
    }

    private static long unidade(String u) {
        return switch (u.toLowerCase(java.util.Locale.ROOT)) {
            case "ouro", "o" -> 100;
            case "prata", "p" -> 10;
            default -> 1;
        };
    }

    public static void depositar(ServerPlayer p, long valor) {
        long naBolsa = Moedas.contar(p);
        if (valor <= 0 || valor > naBolsa) valor = naBolsa;
        if (valor <= 0) { avisar(p, "Você não tem moedas na bolsa.", ChatFormatting.GRAY); return; }
        Moedas.cobrar(p, valor);
        definir(p, saldo(p) + valor);
        avisar(p, "Depositado " + Moedas.texto(valor) + ". Saldo: " + Moedas.texto(saldo(p)) + ".", ChatFormatting.GREEN);
        br.com.frxcode.frxworld.progressao.Progressao.enviarAoPainel(p, false);
    }

    public static void sacar(ServerPlayer p, long valor) {
        long s = saldo(p);
        if (valor <= 0 || valor > s) { avisar(p, "Saldo insuficiente (" + Moedas.texto(s) + ").", ChatFormatting.RED); return; }
        definir(p, s - valor);
        Moedas.dar(p, valor);
        avisar(p, "Sacado " + Moedas.texto(valor) + ". Saldo: " + Moedas.texto(saldo(p)) + ".", ChatFormatting.GREEN);
        br.com.frxcode.frxworld.progressao.Progressao.enviarAoPainel(p, false);
    }

    private static boolean pertoDoBanqueiro(ServerPlayer p) {
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(10)))
            if (e.getTags().contains("frx_npc") && "banqueiro".equals(e.getPersistentData().getString("frx_papel"))) return true;
        return false;
    }

    private static int comSacar(ServerPlayer p, long valor) {
        if (!pertoDoBanqueiro(p)) { avisar(p, "Vá até o banqueiro de uma capital para sacar.", ChatFormatting.RED); return 0; }
        sacar(p, valor);
        return 1;
    }

    private static int comDepositar(ServerPlayer p, long valor) {
        if (!pertoDoBanqueiro(p)) { avisar(p, "Vá até o banqueiro de uma capital para depositar.", ChatFormatting.RED); return 0; }
        depositar(p, valor);
        return 1;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.CommandSourceStack> unidades =
                (c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"cobre", "prata", "ouro"}, b);
        evento.getDispatcher().register(Commands.literal("banco")
                .executes(ctx -> { mostrar(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("sacar")
                        .executes(ctx -> comSacar(ctx.getSource().getPlayerOrException(), saldo(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("tudo").executes(ctx -> comSacar(ctx.getSource().getPlayerOrException(), saldo(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                .executes(ctx -> comSacar(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "quantidade")))
                                .then(Commands.argument("moeda", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests(unidades)
                                        .executes(ctx -> comSacar(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "quantidade")
                                                * unidade(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "moeda")))))))
                .then(Commands.literal("depositar")
                        .executes(ctx -> comDepositar(ctx.getSource().getPlayerOrException(), 0))
                        .then(Commands.literal("tudo").executes(ctx -> comDepositar(ctx.getSource().getPlayerOrException(), 0)))
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                .executes(ctx -> comDepositar(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "quantidade")))
                                .then(Commands.argument("moeda", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests(unidades)
                                        .executes(ctx -> comDepositar(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "quantidade")
                                                * unidade(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "moeda"))))))));
    }
}
