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
 *
 * Rendimento: o cofre rende uma vez por dia (virada do dia no horário de Brasília). A taxa começa na inicial e sobe
 * a cada N dias sem sacar, até a máxima; sacar volta à inicial. Só rende sobre o saldo até o teto. Quem está offline
 * recebe ao voltar, contando no máximo os últimos dias de ausência do limite. Os valores vêm do painel (seção "banco").
 * O andamento é da conta (o saldo passa junto na troca de personagem), em <mundo>/frx_banco_rendimento.json.
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
        long antes = saldo(p);
        if (!Moedas.cobrar(p, valor)) { avisar(p, "Não consegui tirar as moedas da bolsa.", ChatFormatting.RED); return; }
        definir(p, antes + valor);
        if (antes <= 0) recomecar(p);   // cofre vazio: a contagem de dias começa agora
        br.com.frxcode.frxworld.FrxWorld.LOG.info("Banco: {} depositou {}; saldo {} -> {}", p.getGameProfile().getName(), valor, antes, saldo(p));
        avisar(p, "Depositado " + Moedas.texto(valor) + ". Saldo: " + Moedas.texto(saldo(p)) + ".", ChatFormatting.GREEN);
        br.com.frxcode.frxworld.progressao.Progressao.enviarAoPainel(p, false);
    }

    public static void sacar(ServerPlayer p, long valor) {
        long s = saldo(p);
        if (valor <= 0 || valor > s) { avisar(p, "Saldo insuficiente (" + Moedas.texto(s) + ").", ChatFormatting.RED); return; }
        // só sai do cofre o que couber na bolsa: com o inventário cheio, as moedas caíam no chão e sumiam
        long sobra = Moedas.darSemDerrubar(p, valor);
        long sacado = valor - sobra;
        definir(p, s - sacado);
        if (sacado > 0 && rendeAtivo()) {
            boolean perdeu = taxa(diasSemSacar(p)) > taxaInicial() + 1e-9;
            recomecar(p);
            if (perdeu) avisar(p, "Saque feito: o rendimento do cofre voltou para " + pct(taxaInicial()) + " ao dia.", ChatFormatting.GRAY);
        }
        br.com.frxcode.frxworld.FrxWorld.LOG.info("Banco: {} sacou {} (pediu {}, não coube {}); saldo {} -> {}", p.getGameProfile().getName(), sacado, valor, sobra, s, saldo(p));
        if (sacado <= 0) { avisar(p, "Sua bolsa está cheia. Libere espaço no inventário para sacar.", ChatFormatting.RED); return; }
        avisar(p, "Sacado " + Moedas.texto(sacado) + (sobra > 0 ? " (o resto não coube e continua no cofre)" : "") + ". Saldo: " + Moedas.texto(saldo(p)) + ".", ChatFormatting.GREEN);
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

    // ------------------------------------------------------------------ saldo definido pelo painel com o jogador offline

    private static java.nio.file.Path pendentes(net.minecraft.server.MinecraftServer s) {
        return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("frx_banco_pendente.txt");
    }

    private static java.util.Map<String, Long> lerPendentes(net.minecraft.server.MinecraftServer s) {
        java.util.Map<String, Long> m = new java.util.LinkedHashMap<>();
        try {
            if (java.nio.file.Files.exists(pendentes(s)))
                for (String l : java.nio.file.Files.readAllLines(pendentes(s))) {
                    String[] c = l.trim().split("=");
                    if (c.length == 2) m.put(c[0], Long.parseLong(c[1]));
                }
        } catch (Exception ignorado) { }
        return m;
    }

    private static void gravarPendentes(net.minecraft.server.MinecraftServer s, java.util.Map<String, Long> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        try { java.nio.file.Files.writeString(pendentes(s), sb.toString()); } catch (Exception ignorado) { }
    }

    /** O saldo do banco fica no arquivo do jogador: offline, o valor novo espera aqui e entra no próximo login. */
    @SubscribeEvent
    public void aoEntrar(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        java.util.Map<String, Long> m = lerPendentes(p.server);
        Long v = m.remove(p.getUUID().toString());
        if (v == null) return;
        gravarPendentes(p.server, m);
        definirSaldo(p, v);
        avisar(p, "A administração ajustou seu saldo no banco: " + Moedas.texto(saldo(p)) + ".", ChatFormatting.YELLOW);
    }

    @SubscribeEvent
    public void comandosAdmin(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("bancooffline").then(Commands.argument("uuid", net.minecraft.commands.arguments.UuidArgument.uuid())
                        .then(Commands.argument("cobre", IntegerArgumentType.integer(0)).executes(ctx -> {
                            java.util.UUID u = net.minecraft.commands.arguments.UuidArgument.getUuid(ctx, "uuid");
                            int v = IntegerArgumentType.getInteger(ctx, "cobre");
                            net.minecraft.server.MinecraftServer s = ctx.getSource().getServer();
                            ServerPlayer on = s.getPlayerList().getPlayer(u);
                            if (on != null) { definirSaldo(on, v); }
                            else { java.util.Map<String, Long> m = lerPendentes(s); m.put(u.toString(), (long) v); gravarPendentes(s, m); }
                            ctx.getSource().sendSuccess(() -> Component.literal("[painel] banco de " + u + " = " + Moedas.texto(v) + (on != null ? "" : " (entra no próximo login)")), true);
                            return 1;
                        })))));
    }

    // ------------------------------------------------------------------ rendimento

    private static final java.time.ZoneId FUSO = java.time.ZoneId.of("America/Sao_Paulo");
    private static com.google.gson.JsonObject rend;
    private static int ticks;

    private static com.google.gson.JsonObject cfg() { return br.com.frxcode.frxworld.progressao.ConfigJogo.secaoCrua("banco"); }
    private static double num(String k, double padrao) {
        try { com.google.gson.JsonObject c = cfg(); if (c != null && c.has(k)) return c.get(k).getAsDouble(); } catch (Exception ignorado) { }
        return padrao;
    }
    public static boolean rendeAtivo() {
        try { com.google.gson.JsonObject c = cfg(); if (c != null && c.has("ativo")) return c.get("ativo").getAsBoolean(); } catch (Exception ignorado) { }
        return true;
    }
    private static double taxaInicial() { return Math.max(0, num("taxaInicial", 0.5)); }
    private static double taxaMax() { return Math.max(taxaInicial(), num("taxaMax", 1.0)); }
    private static double aumento() { return Math.max(0, num("aumento", 0.1)); }
    private static int aCadaDias() { return Math.max(1, (int) num("aCadaDias", 7)); }
    private static long teto() { return Math.max(0, (long) num("teto", 50000)); }
    private static int diasOffline() { return Math.max(1, (int) num("diasOffline", 7)); }

    private static long hoje() { return java.time.LocalDate.now(FUSO).toEpochDay(); }

    /** Taxa (% ao dia) para quem está há tantos dias sem sacar. */
    public static double taxa(long diasSemSacar) {
        return Math.min(taxaMax(), taxaInicial() + aumento() * Math.floor(Math.max(0, diasSemSacar) / (double) aCadaDias()));
    }

    private static String pct(double t) { return String.format(new java.util.Locale("pt", "BR"), "%.2f", t).replaceAll("0$", "").replaceAll(",$", "") + "%"; }

    private static java.nio.file.Path arqRend(net.minecraft.server.MinecraftServer s) {
        return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("frx_banco_rendimento.json");
    }

    private static com.google.gson.JsonObject conta(ServerPlayer p) {
        if (rend == null) {
            rend = new com.google.gson.JsonObject();
            try {
                if (java.nio.file.Files.exists(arqRend(p.server)))
                    rend = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(arqRend(p.server))).getAsJsonObject();
            } catch (Exception e) { br.com.frxcode.frxworld.FrxWorld.LOG.warn("Banco: não li frx_banco_rendimento.json: {}", e.toString()); }
        }
        String id = p.getUUID().toString();
        if (!rend.has(id)) rend.add(id, new com.google.gson.JsonObject());
        return rend.getAsJsonObject(id);
    }

    private static void gravarRend(net.minecraft.server.MinecraftServer s) {
        try { java.nio.file.Files.writeString(arqRend(s), rend.toString()); }
        catch (Exception e) { br.com.frxcode.frxworld.FrxWorld.LOG.warn("Banco: não gravei o rendimento: {}", e.toString()); }
    }

    private static long diasSemSacar(ServerPlayer p) {
        com.google.gson.JsonObject c = conta(p);
        return c.has("desde") ? Math.max(0, hoje() - c.get("desde").getAsLong()) : 0;
    }

    /** Cofre esvaziado, saque ou primeiro depósito: a contagem de dias recomeça hoje. */
    private static void recomecar(ServerPlayer p) {
        com.google.gson.JsonObject c = conta(p);
        long h = hoje();
        c.addProperty("desde", h);
        if (!c.has("pago") || saldo(p) <= 0) c.addProperty("pago", h);
        gravarRend(p.server);
    }

    /** Paga os dias que faltam (no máximo os últimos dias de ausência do limite). */
    private static void render(ServerPlayer p) {
        com.google.gson.JsonObject c = conta(p);
        long h = hoje();
        long saldo = saldo(p);
        if (!c.has("pago") || !c.has("desde")) { c.addProperty("pago", h); c.addProperty("desde", h); gravarRend(p.server); return; }
        long pago = c.get("pago").getAsLong();
        if (pago >= h) return;
        if (saldo <= 0) { c.addProperty("pago", h); c.addProperty("desde", h); c.addProperty("resto", 0); gravarRend(p.server); return; }
        long desde = c.get("desde").getAsLong();
        double resto = c.has("resto") ? c.get("resto").getAsDouble() : 0;
        long inicio = Math.max(pago + 1, h - diasOffline() + 1), total = 0, s = saldo;
        for (long d = inicio; d <= h; d++) {
            double r = Math.min(s, teto()) * taxa(d - desde) / 100.0 + resto;
            long inteiro = (long) Math.floor(r);
            resto = r - inteiro;
            s += inteiro;
            total += inteiro;
        }
        definir(p, s);
        c.addProperty("pago", h);
        c.addProperty("resto", resto);
        gravarRend(p.server);
        if (total <= 0) return;
        long dias = h - inicio + 1;
        br.com.frxcode.frxworld.FrxWorld.LOG.info("Banco: {} rendeu {} em {} dia(s); saldo {} -> {}", p.getGameProfile().getName(), total, dias, saldo, s);
        avisar(p, "Seu cofre rendeu " + Moedas.texto(total) + (dias > 1 ? " (" + dias + " dias)" : " hoje") + ", a " + pct(taxa(h - desde)) + " ao dia. Saldo: " + Moedas.texto(s) + ".", ChatFormatting.GREEN);
        br.com.frxcode.frxworld.progressao.Progressao.enviarAoPainel(p, false);
    }

    /** Taxa atual, para a tela do banqueiro (ex.: "0,6%"). Vazio se o rendimento está desligado. */
    public static String taxaTexto(ServerPlayer p) { return rendeAtivo() ? pct(taxa(diasSemSacar(p) + 1)) : ""; }

    /** Uma linha sobre o rendimento: quanto rende amanhã e quando a taxa sobe. */
    public static String resumoRendimento(ServerPlayer p) {
        if (!rendeAtivo()) return "";
        long dias = diasSemSacar(p) + 1;   // o próximo pagamento já conta o dia de hoje
        double t = taxa(dias);
        long saldo = saldo(p);
        StringBuilder sb = new StringBuilder("Rende ").append(pct(t)).append(" ao dia");
        if (saldo > 0) sb.append(" · amanhã: +").append(Moedas.texto((long) Math.floor(Math.min(saldo, teto()) * t / 100.0)));
        if (t < taxaMax() - 1e-9) {
            long falta = aCadaDias() - (dias % aCadaDias());
            sb.append(" · sobe em ").append(falta).append(falta == 1 ? " dia" : " dias");
        }
        if (saldo > teto()) sb.append(" · acima de ").append(Moedas.texto(teto())).append(" não rende");
        return sb.toString();
    }

    @SubscribeEvent
    public void rendimentoTick(net.minecraftforge.event.TickEvent.ServerTickEvent ev) {
        if (ev.phase != net.minecraftforge.event.TickEvent.Phase.END || ++ticks % 400 != 123 || !rendeAtivo()) return;
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers())
            if (p.tickCount > 200) render(p);   // espera o login assentar (a troca de personagem põe o saldo no lugar)
    }

    @SubscribeEvent
    public void rendimentoParar(net.minecraftforge.event.server.ServerStoppingEvent ev) { rend = null; }
}
