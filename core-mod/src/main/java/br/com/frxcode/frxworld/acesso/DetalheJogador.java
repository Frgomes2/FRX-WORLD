package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.armas.Armas;
import br.com.frxcode.frxworld.habilidades.HabilidadeDef;
import br.com.frxcode.frxworld.habilidades.Habilidades;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.JanelaStatus;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.reinos.Reputacao;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;

/**
 * Ficha completa do jogador para a aba Jogadores do painel: tudo da janela de status + inventário, armadura,
 * baú do End, habilidades, posição e estatísticas. Vai ao painel ao entrar, a cada minuto, ao sair e depois de
 * cada ação de admin. Comandos de admin pensados para o console (o painel manda por lá): /frx admin ...
 */
public class DetalheJogador {
    private int ticks;

    public DetalheJogador() {}

    // ------------------------------------------------------------------ montagem

    private static JsonObject item(ItemStack s, int slot) {
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        o.addProperty("id", id == null ? "?" : id.toString());
        o.addProperty("qtd", s.getCount());
        o.addProperty("nome", s.getHoverName().getString());
        if (s.hasCustomHoverName()) o.addProperty("renomeado", true);
        if (s.isDamageableItem()) { o.addProperty("dano", s.getDamageValue()); o.addProperty("danoMax", s.getMaxDamage()); }
        var enc = EnchantmentHelper.getEnchantments(s);
        if (!enc.isEmpty()) {
            JsonArray a = new JsonArray();
            enc.forEach((e, n) -> {
                JsonObject x = new JsonObject();
                x.addProperty("nome", e.getFullname(n).getString());
                a.add(x);
            });
            o.add("encantos", a);
        }
        if (s.hasTag()) {
            int r = Armas.raridade(s), n = Armas.nivel(s);
            if (r > 0 || s.getTag().contains(Armas.RARIDADE)) o.addProperty("raridade", r);
            if (n > 0) o.addProperty("forja", n);
        }
        if (s.getItem() instanceof ArmorItem a) {
            o.addProperty("material", a.getMaterial().getName());
            o.addProperty("parte", a.getEquipmentSlot().getName());
        }
        return o;
    }

    public static JsonObject montar(ServerPlayer p) {
        JsonObject o = JsonParser.parseString(JanelaStatus.montar(p)).getAsJsonObject();
        o.addProperty("uuid", p.getUUID().toString());
        o.addProperty("vaga", Personagens.vagaDe(p));   // de qual personagem da conta é esta ficha
        o.addProperty("online", !p.hasDisconnected());
        o.addProperty("x", p.getBlockX()); o.addProperty("y", p.getBlockY()); o.addProperty("z", p.getBlockZ());
        o.addProperty("dim", p.level().dimension().location().toString());
        o.addProperty("modo", p.gameMode.getGameModeForPlayer().getName());
        o.addProperty("fome", p.getFoodData().getFoodLevel());
        o.addProperty("nivelVanilla", p.experienceLevel);
        o.addProperty("moedasCobre", Moedas.contar(p));
        o.addProperty("bancoCobre", Banco.saldo(p));
        o.addProperty("bancoRende", Banco.resumoRendimento(p));
        o.addProperty("tempoJogoMin", p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 20 / 60);
        o.addProperty("mobsMortos", p.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)));
        o.addProperty("mortesVanilla", p.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)));
        o.addProperty("blocosAndados", p.getStats().getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) / 100);

        var inv = p.getInventory();
        JsonArray itens = new JsonArray();
        for (int i = 0; i < inv.items.size(); i++) if (!inv.items.get(i).isEmpty()) itens.add(item(inv.items.get(i), i));
        o.add("inventario", itens);
        JsonArray arm = new JsonArray();
        for (int i = 0; i < inv.armor.size(); i++) if (!inv.armor.get(i).isEmpty()) arm.add(item(inv.armor.get(i), i)); // 0 botas .. 3 capacete
        o.add("armadura", arm);
        if (!inv.offhand.get(0).isEmpty()) o.add("maoSecundaria", item(inv.offhand.get(0), 0));
        o.addProperty("slotSelecionado", inv.selected);
        JsonArray ender = new JsonArray();
        var ec = p.getEnderChestInventory();
        for (int i = 0; i < ec.getContainerSize(); i++) if (!ec.getItem(i).isEmpty()) ender.add(item(ec.getItem(i), i));
        o.add("bauEnder", ender);

        JsonArray efeitos = new JsonArray();
        for (MobEffectInstance e : p.getActiveEffects()) {
            JsonObject x = new JsonObject();
            x.addProperty("nome", e.getEffect().getDisplayName().getString());
            x.addProperty("nivel", e.getAmplifier() + 1);
            x.addProperty("seg", e.getDuration() / 20);
            efeitos.add(x);
        }
        o.add("efeitos", efeitos);
        if (br.com.frxcode.frxworld.habilidades.Unica.def(p) != null) {
            JsonObject u = new JsonObject();
            u.addProperty("nome", br.com.frxcode.frxworld.habilidades.Unica.def(p).nome());
            u.addProperty("descricao", br.com.frxcode.frxworld.habilidades.Unica.def(p).descricao());
            u.addProperty("nivel", br.com.frxcode.frxworld.habilidades.Unica.nivel(p));
            u.addProperty("efeito", br.com.frxcode.frxworld.habilidades.Unica.efeito(p));
            u.addProperty("abates", br.com.frxcode.frxworld.habilidades.Unica.abates(p));
            o.add("unica", u);
        }

        String racaP = br.com.frxcode.frxworld.racas.RacasJogador.principal(p), racaS = br.com.frxcode.frxworld.racas.RacasJogador.secundaria(p);
        br.com.frxcode.frxworld.racas.DefRacas.Raca racaDef = br.com.frxcode.frxworld.racas.DefRacas.de(racaP);
        if (br.com.frxcode.frxworld.racas.RacasJogador.escolheu(p) && racaDef != null) {
            JsonObject r = new JsonObject();
            r.addProperty("principal", racaDef.nome());
            r.addProperty("secundaria", br.com.frxcode.frxworld.racas.DefRacas.nome(racaS));
            r.addProperty("nivel", br.com.frxcode.frxworld.racas.DefRacas.NOME_NIVEL[Math.max(0, Math.min(3, racaDef.nivel()))]);
            r.addProperty("descricao", racaDef.descricao());
            JsonArray bonus = new JsonArray();
            for (String s : br.com.frxcode.frxworld.racas.DefRacas.resumo(br.com.frxcode.frxworld.racas.DefRacas.somar(racaP, racaS))) bonus.add(s);
            r.add("bonus", bonus);
            o.add("raca", r);
        }

        if (Progressao.temDados(p)) {
            DadosJogador d = Progressao.dados(p);
            o.addProperty("classeId", d.classe());
            JsonArray hab = new JsonArray();
            Habilidades.catalogo().values().stream()
                    .filter(h -> h.classe().equals(d.classe()))
                    .sorted(Comparator.comparing(HabilidadeDef::ultimate).thenComparingInt(HabilidadeDef::nivelReq))
                    .forEach(h -> {
                        DadosJogador.RegistroHabilidade r = d.habilidades().get(h.codigo());
                        JsonObject x = new JsonObject();
                        x.addProperty("codigo", h.codigo());
                        x.addProperty("nome", h.nome());
                        x.addProperty("descricao", h.descricao());
                        x.addProperty("nivelReq", h.nivelReq());
                        x.addProperty("ultimate", h.ultimate());
                        x.addProperty("porItem", h.porItem());
                        x.addProperty("aprendida", r != null);
                        if (r != null) { x.addProperty("nivel", r.nivel); x.addProperty("usos", r.usos); }
                        hab.add(x);
                    });
            o.add("habilidades", hab);
            JsonArray barra = new JsonArray();
            for (String c : d.barra()) barra.add(c);
            o.add("barra", barra);
            JsonObject rep = new JsonObject();
            d.reputacao().forEach(rep::addProperty);
            o.add("reputacaoPontos", rep);
            o.add("sombras", br.com.frxcode.frxworld.habilidades.Sombras.json(p));
            o.add("colecao", br.com.frxcode.frxworld.progressao.Colecao.json(p));
            o.add("contratos", br.com.frxcode.frxworld.missoes.Contratos.json(p));
        }
        return o;
    }

    public static void enviar(ServerPlayer p) {
        try {
            JsonObject o = montar(p);
            PainelApi.enviarDetalheJogador(o);
        } catch (Exception ex) {
            br.com.frxcode.frxworld.FrxWorld.LOG.debug("Ficha do jogador: {}", ex.toString());
        }
    }

    // ------------------------------------------------------------------ quando enviar

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 1200 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) enviar(p);
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) p.getServer().execute(() -> enviar(p));
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            JsonObject o = montar(p);
            o.addProperty("online", false);
            PainelApi.enviarDetalheJogador(o);
        }
    }

    // ------------------------------------------------------------------ comandos de admin (console)

    private static void ok(CommandSourceStack src, ServerPlayer p, String texto) {
        src.sendSuccess(() -> Component.literal("[painel] " + texto), true);
        enviar(p);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("admin").then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.literal("ficha").executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "jogador");
                            ok(ctx.getSource(), p, "ficha de " + p.getGameProfile().getName() + " enviada");
                            return 1;
                        }))
                        .then(Commands.literal("curar").executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "jogador");
                            p.setHealth(p.getMaxHealth());
                            p.getFoodData().setFoodLevel(20);
                            p.getFoodData().setSaturation(10);
                            p.removeAllEffects();
                            p.clearFire();
                            ok(ctx.getSource(), p, p.getGameProfile().getName() + " curado");
                            return 1;
                        }))
                        .then(Commands.literal("banco").then(Commands.argument("cobre", IntegerArgumentType.integer(0)).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "jogador");
                            Banco.definirSaldo(p, IntegerArgumentType.getInteger(ctx, "cobre"));
                            ok(ctx.getSource(), p, "banco de " + p.getGameProfile().getName() + " = " + Moedas.texto(Banco.saldo(p)));
                            return 1;
                        })))
                        // bolsa exata: troca todas as moedas do inventário pelo valor pedido (o painel edita ouro, prata e cobre)
                        .then(Commands.literal("bolsa").then(Commands.argument("cobre", IntegerArgumentType.integer(0)).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "jogador");
                            long v = IntegerArgumentType.getInteger(ctx, "cobre");
                            Moedas.cobrar(p, Moedas.contar(p));
                            Moedas.dar(p, v);
                            ok(ctx.getSource(), p, "bolsa de " + p.getGameProfile().getName() + " = " + Moedas.texto(Moedas.contar(p)));
                            return 1;
                        })))
                        .then(Commands.literal("tirarmoedas").then(Commands.argument("cobre", IntegerArgumentType.integer(1)).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "jogador");
                            long v = Math.min(Moedas.contar(p), IntegerArgumentType.getInteger(ctx, "cobre"));
                            Moedas.cobrar(p, v);
                            ok(ctx.getSource(), p, "tirou " + Moedas.texto(v) + " de " + p.getGameProfile().getName());
                            return 1;
                        })))
                        .then(Commands.literal("reputacao").then(Commands.argument("reino", StringArgumentType.word())
                                .then(Commands.argument("pontos", IntegerArgumentType.integer(-100000, 100000)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "jogador");
                                    String reino = StringArgumentType.getString(ctx, "reino");
                                    int alvo = IntegerArgumentType.getInteger(ctx, "pontos");
                                    int atual = Progressao.temDados(p) ? Progressao.dados(p).reputacao().getOrDefault(reino, 0) : 0;
                                    Reputacao.ganhar(p, reino, alvo - atual, true, false);
                                    ok(ctx.getSource(), p, "reputação de " + p.getGameProfile().getName() + " em " + reino + " = " + alvo);
                                    return 1;
                                }))))
                        .then(Commands.literal("talento").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("pontos", IntegerArgumentType.integer(0, 50)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "jogador");
                                    if (!Progressao.temDados(p)) return 0;
                                    String id = StringArgumentType.getString(ctx, "id");
                                    int n = IntegerArgumentType.getInteger(ctx, "pontos");
                                    if (n == 0) Progressao.dados(p).talentos().remove(id); else Progressao.dados(p).talentos().put(id, n);
                                    br.com.frxcode.frxworld.habilidades.Talentos.aplicar(p);
                                    ok(ctx.getSource(), p, "talento " + id + " = " + n + " · " + p.getGameProfile().getName());
                                    return 1;
                                }))))
                        .then(Commands.literal("habilidade")
                                .then(Commands.literal("aprender").then(Commands.argument("codigo", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Habilidades.catalogo().keySet(), b))
                                        .executes(ctx -> habilidade(ctx.getSource(), EntityArgument.getPlayer(ctx, "jogador"), StringArgumentType.getString(ctx, "codigo"), 1))))
                                .then(Commands.literal("esquecer").then(Commands.argument("codigo", StringArgumentType.word())
                                        .executes(ctx -> habilidade(ctx.getSource(), EntityArgument.getPlayer(ctx, "jogador"), StringArgumentType.getString(ctx, "codigo"), 0))))
                                .then(Commands.literal("nivel").then(Commands.argument("codigo", StringArgumentType.word())
                                        .then(Commands.argument("nivel", IntegerArgumentType.integer(1, br.com.frxcode.frxworld.habilidades.Habilidades.NIVEL_MAX))
                                                .executes(ctx -> habilidade(ctx.getSource(), EntityArgument.getPlayer(ctx, "jogador"),
                                                        StringArgumentType.getString(ctx, "codigo"), IntegerArgumentType.getInteger(ctx, "nivel"))))))
                                .then(Commands.literal("todas").executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "jogador");
                                    if (!Progressao.temDados(p)) return 0;
                                    DadosJogador d = Progressao.dados(p);
                                    List<HabilidadeDef> lista = Habilidades.catalogo().values().stream().filter(h -> h.classe().equals(d.classe())).toList();
                                    for (HabilidadeDef h : lista) d.habilidades().putIfAbsent(h.codigo(), new DadosJogador.RegistroHabilidade());
                                    Habilidades.sincronizar(p);
                                    ok(ctx.getSource(), p, p.getGameProfile().getName() + " aprendeu todas as " + lista.size() + " habilidades da classe");
                                    return 1;
                                }))))));
    }

    /** nivel 0 = esquecer; 1 = aprender (ou manter); >1 = definir nível. */
    private static int habilidade(CommandSourceStack src, ServerPlayer p, String codigo, int nivel) {
        if (!Progressao.temDados(p)) return 0;
        DadosJogador d = Progressao.dados(p);
        HabilidadeDef h = Habilidades.catalogo().get(codigo);
        if (h == null) { src.sendFailure(Component.literal("Habilidade não encontrada: " + codigo)); return 0; }
        if (nivel == 0) {
            d.habilidades().remove(codigo);
            String[] barra = d.barra();
            for (int i = 0; i < barra.length; i++) if (codigo.equals(barra[i])) barra[i] = "";
        } else {
            DadosJogador.RegistroHabilidade r = d.habilidades().computeIfAbsent(codigo, k -> new DadosJogador.RegistroHabilidade());
            if (nivel > 1) r.nivel = nivel;
            String[] barra = d.barra();
            boolean naBarra = false;
            for (String c : barra) if (codigo.equals(c)) naBarra = true;
            if (!naBarra) {
                if (h.ultimate()) { if (barra[4].isEmpty()) barra[4] = codigo; }
                else for (int i = 0; i < 4; i++) if (barra[i].isEmpty()) { barra[i] = codigo; break; }
            }
        }
        Habilidades.sincronizar(p);
        ok(src, p, (nivel == 0 ? "esqueceu " : "habilidade ") + h.nome() + (nivel > 1 ? " nível " + nivel : "") + " · " + p.getGameProfile().getName());
        return 1;
    }
}
