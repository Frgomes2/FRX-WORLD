package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.armas.Armas;
import br.com.frxcode.frxworld.grupos.Grupos;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Conquistas;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Missões épicas dos reis: cada rei tem uma corrente de 5 etapas com história.
 * Fale com o rei (clique) para aceitar, acompanhar e entregar. Final: arma Lendária +5, título "Lenda de X",
 * muita reputação e moedas. /campanha mostra o andamento.
 */
public class Campanhas {

    /** tipo: MATAR (alvo = ids separados por |, ou * para qualquer monstro), COLETAR (item), VISITAR (id de reino),
     *  DIMENSAO (minecraft:the_nether / the_end), DEFENDER (vencer invasão no reino da campanha). */
    public record Etapa(String titulo, String pedido, String tipo, String alvo, int qtd, String agradecimento) {}

    public record Campanha(String reino, String rei, String nome, int nivelMin, List<Etapa> etapas, Item premio, String nomePremio) {
        String titulo() { return "Lenda de " + Reputacao.curto(nomeReino()); }
        String nomeReino() { Reino r = Reputacao.reino(reino); return r != null ? r.nome() : reino; }
    }

    public static final Map<String, Campanha> TODAS = new LinkedHashMap<>();

    private static void campanha(String reino, String rei, String nome, int nivel, Item premio, String nomePremio, Etapa... etapas) {
        TODAS.put(reino, new Campanha(reino, rei, nome, nivel, List.of(etapas), premio, nomePremio));
    }

    private static Etapa e(String titulo, String pedido, String tipo, String alvo, int qtd, String agradecimento) {
        return new Etapa(titulo, pedido, tipo, alvo, qtd, agradecimento);
    }

    static {
        campanha("tempest", "Lorde Rimuru Tempest", "A Federação em Perigo", 5, Items.NETHERITE_SWORD, "Lâmina do Grande Sábio",
                e("Sombras na Floresta de Jura", "Monstros rondam as fronteiras da Federação. Derrote 30 deles para acalmar os goblins.",
                        "MATAR", "*", 30, "Os goblins já dormem tranquilos. Você tem talento, aventureiro."),
                e("Ferro para Kaijin", "Kaijin precisa de metal para armar nossos guardas. Traga-me 32 lingotes de ferro.",
                        "COLETAR", "minecraft:iron_ingot", 32, "Kaijin já está martelando. Tempest fica mais forte graças a você."),
                e("Saqueadores na Estrada", "Bandidos atacam as caravanas que vêm para Tempest. Elimine 15 saqueadores ou vindicadores.",
                        "MATAR", "minecraft:pillager|minecraft:vindicator", 15, "As caravanas voltaram a chegar. Os mercadores agradecem."),
                e("A Defesa da Capital", "Uma horda se aproxima. Defenda Tempest de uma invasão até o fim.",
                        "DEFENDER", "", 1, "Você lutou ao lado dos nossos. Agora é um de nós."),
                e("O Tirano do Gelo", "Uma fera colossal ameaça o norte. Derrote o Frostmaw — ou, se não o achar, um Devastador.",
                        "MATAR", "mowziesmobs:frostmaw|minecraft:ravager", 1, "Uma lenda nasceu hoje. Receba esta lâmina, herói de Tempest."));

        campanha("dwargon", "Rei Gazel Dwargo", "O Juramento da Montanha", 10, Items.NETHERITE_AXE, "Machado do Rei Anão",
                e("Profundezas Infestadas", "Criaturas sobem das minas. Derrote 40 monstros para limpar os túneis.",
                        "MATAR", "*", 40, "Os mineiros voltaram ao trabalho. Bom serviço."),
                e("Tributo de Diamantes", "A forja real precisa de pedras puras. Traga-me 8 diamantes.",
                        "COLETAR", "minecraft:diamond", 8, "Diamantes dignos da coroa de Dwargon."),
                e("Carta a Tempest", "Leve a palavra de Dwargon até a Federação de Jura Tempest.",
                        "VISITAR", "tempest", 1, "Rimuru recebeu a carta. A aliança está selada."),
                e("O Fogo do Submundo", "Os esqueletos wither guardam um metal que precisamos. Vá ao Nether e derrote 15 deles.",
                        "MATAR", "minecraft:wither_skeleton", 15, "Poucos voltam do Nether com tantas vitórias."),
                e("A Besta de Netherite", "Uma monstruosidade de netherite desperta. Derrube-a — ou o próprio Wither.",
                        "MATAR", "cataclysm:netherite_monstrosity|minecraft:wither", 1, "Dwargon jamais esquecerá. Este machado é seu."));

        campanha("sarion", "Imperatriz Elmesia El-Ru Sarion", "A Profecia Élfica", 15, Items.BOW, "Arco da Imperatriz",
                e("Cristais Antigos", "A magia de Sarion se alimenta de ametista. Traga-me 32 fragmentos.",
                        "COLETAR", "minecraft:amethyst_shard", 32, "Os cristais cantam outra vez."),
                e("Pesadelos no Céu", "Phantoms atormentam o sono do império. Derrote 15 deles.",
                        "MATAR", "minecraft:phantom", 15, "As noites voltaram a ser calmas."),
                e("Além do Portal", "A profecia fala de um mundo de fogo. Pise no Nether e volte para contar.",
                        "DIMENSAO", "minecraft:the_nether", 1, "Você sobreviveu ao calor do submundo."),
                e("O Vazio", "Agora o fim de tudo: chegue ao End.",
                        "DIMENSAO", "minecraft:the_end", 1, "Poucos mortais viram o vazio e voltaram."),
                e("O Guardião das Chamas", "Ignis desperta. Derrote-o — ou o Dragão do Fim.",
                        "MATAR", "cataclysm:ignis|minecraft:ender_dragon", 1, "A profecia se cumpriu. Este arco é seu, campeão."));

        campanha("leste", "Imperador Ludora", "A Vontade do Império", 15, Items.NETHERITE_SWORD, "Lâmina Imperial",
                e("Ordem nas Fronteiras", "Illagers rondam o império. Derrote 25 saqueadores, vindicadores ou evocadores.",
                        "MATAR", "minecraft:pillager|minecraft:vindicator|minecraft:evoker", 25, "As fronteiras estão seguras."),
                e("Ouro do Tesouro", "O tesouro imperial precisa de reforço. Traga-me 32 lingotes de ouro.",
                        "COLETAR", "minecraft:gold_ingot", 32, "O tesouro agradece sua lealdade."),
                e("Defenda o Trono", "Vença uma invasão ao Império do Leste.",
                        "DEFENDER", "", 1, "Você honrou o trono."),
                e("O Templo do Mar", "Guardiões protegem segredos no fundo do oceano. Derrote 20 guardiões.",
                        "MATAR", "minecraft:guardian|minecraft:elder_guardian", 20, "O mar respeita você agora."),
                e("O Cavaleiro de Ferro", "O Ferrous Wroughtnaut desafia o império. Derrote-o — ou um Guardião Ancião.",
                        "MATAR", "mowziesmobs:ferrous_wroughtnaut|minecraft:elder_guardian", 1, "O império curva-se a você. Receba a lâmina imperial."));

        campanha("leste-sakura", "Senhora das Cerejeiras", "O Florescer de Sakura", 8, Items.NETHERITE_SWORD, "Katana das Cerejeiras",
                e("Pragas no Jardim", "Aranhas invadem o jardim sagrado. Derrote 25.",
                        "MATAR", "minecraft:spider|minecraft:cave_spider", 25, "As flores respiram aliviadas."),
                e("Madeira Sagrada", "Para o novo santuário, traga 64 toras de cerejeira.",
                        "COLETAR", "minecraft:cherry_log", 64, "O santuário será lindo."),
                e("Visita ao Imperador", "Leve nossas flores ao Império do Leste.",
                        "VISITAR", "leste", 1, "O imperador sorriu ao ver as flores."),
                e("Os Mortos Inquietos", "Mortos-vivos perturbam os ancestrais. Derrote 40 zumbis ou esqueletos.",
                        "MATAR", "minecraft:zombie|minecraft:skeleton|minecraft:husk|minecraft:stray", 40, "Os ancestrais descansam."),
                e("O Guardião Ancestral", "Um Guardião Ancião guarda a relíquia do clã no fundo do mar. Derrote-o.",
                        "MATAR", "minecraft:elder_guardian", 1, "A relíquia volta ao clã. Esta katana agora é sua."));

        campanha("oeste", "Rei Edmaris de Farmus", "A Coroa do Oeste", 12, Items.CROSSBOW, "Besta Real do Oeste",
                e("Mortos nas Plantações", "Os mortos se levantam nos campos de trigo. Derrote 35 zumbis antes da colheita.",
                        "MATAR", "minecraft:zombie|minecraft:husk|minecraft:zombie_villager|born_in_chaos_v1:decaying_zombie", 35,
                        "Os camponeses voltaram aos campos. Farmus agradece."),
                e("Celeiros Vazios", "O inverno se aproxima e os celeiros estão vazios. Traga-me 64 trigos.",
                        "COLETAR", "minecraft:wheat", 64, "Ninguém passará fome este ano."),
                e("A Cripta Profanada", "Algo despertou nos cemitérios do reino. Derrote 15 criaturas da cripta: ghouls, revenants ou illagers corrompidos.",
                        "MATAR", "graveyard:ghoul|graveyard:revenant|graveyard:corrupted_vindicator|graveyard:corrupted_pillager|graveyard:acolyte", 15,
                        "Os mortos voltaram ao silêncio. Mas algo maior os comandava..."),
                e("Tratado com os Anões", "Leve a proposta de aliança do Oeste ao Rei Gazel, em Dwargon.",
                        "VISITAR", "dwargon", 1, "Gazel assinou. O Oeste não está mais sozinho."),
                e("O Lich do Cemitério", "O mestre dos mortos se revelou. Derrote o Lich — ou, se não o encontrar, um Evocador.",
                        "MATAR", "graveyard:lich|minecraft:evoker", 1, "O Oeste está livre. Esta besta real é sua, campeão de Farmus."));

        campanha("lubelius", "Luminus Valentine, Deusa de Lubelius", "A Luz de Luminus", 20, Items.TRIDENT, "Lança Sagrada de Lubelius",
                e("Purificar os Mortos", "Os mortos-vivos são uma afronta à Luz. Destrua 50 deles.",
                        "MATAR", "minecraft:zombie|minecraft:skeleton|minecraft:stray|minecraft:husk|minecraft:drowned|born_in_chaos_v1:decrepit_skeleton|graveyard:ghoul", 50,
                        "A Luz agradece. Você tem fé nas mãos."),
                e("Luz para a Catedral", "As lâmpadas da catedral se apagam. Traga 64 pós de pedra luminosa.",
                        "COLETAR", "minecraft:glowstone_dust", 64, "A catedral brilha outra vez."),
                e("Espíritos Inquietos", "Almas perdidas vagam pelo reino. Derrote 20 espíritos, espectros ou vexes.",
                        "MATAR", "born_in_chaos_v1:restless_spirit|born_in_chaos_v1:seared_spirit|graveyard:wraith|graveyard:nightmare|minecraft:vex", 20,
                        "As almas descansam. Hinata ficou impressionada."),
                e("Cruzada ao Submundo", "Toda cruzada começa no fogo. Pise no Nether e volte com vida.",
                        "DIMENSAO", "minecraft:the_nether", 1, "Você caminhou pelo inferno e voltou puro."),
                e("O Arauto da Maldição", "Um ser amaldiçoado desafia a Deusa. Derrote o Maledictus — ou o Wither.",
                        "MATAR", "cataclysm:maledictus|minecraft:wither", 1, "Luminus sorri — raramente o faz. Esta lança sagrada é sua."));

        campanha("demonios", "Guy Crimson, Lorde Demônio Primordial", "O Banquete dos Lordes", 30, Items.NETHERITE_SWORD, "Lâmina Carmesim de Guy",
                e("Prova de Força", "Humanos não entram aqui sem provar o valor. Derrote 30 blazes, esqueletos wither ou brutos piglin.",
                        "MATAR", "minecraft:blaze|minecraft:wither_skeleton|minecraft:piglin_brute", 30, "Hm. Você não é tão frágil quanto parece."),
                e("Tributo ao Lorde", "Todo convidado traz um presente. Quatro fragmentos de netherite serão suficientes.",
                        "COLETAR", "minecraft:netherite_scrap", 4, "Um tributo aceitável."),
                e("Sobreviva ao Cerco", "Meus inimigos testam as muralhas. Vença uma invasão à cidade dos Lordes.",
                        "DEFENDER", "", 1, "Você defendeu a casa de um demônio. Curioso."),
                e("Os Que Vigiam", "Os vigias do vazio me espionam. Derrote 30 endermen.",
                        "MATAR", "minecraft:enderman", 30, "Agora ninguém me observa. Ótimo."),
                e("O Desafio do Lorde", "Último teste: derrube um verdadeiro titã — o Harbinger, o Ignis ou o Wither.",
                        "MATAR", "cataclysm:the_harbinger|cataclysm:ignis|minecraft:wither", 1,
                        "Hahaha! Há séculos ninguém me divertia assim. Leve minha lâmina — e não a perca."));
    }

    // ------------------------------------------------------------------ utilidades

    private static Campanha de(String reino) { return TODAS.get(reino); }

    /** Para a janela de status (tecla E). */
    public static com.google.gson.JsonArray json(ServerPlayer p) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        if (!Progressao.temDados(p)) return a;
        DadosJogador d = Progressao.dados(p);
        for (Campanha c : TODAS.values()) {
            int i = etapa(d, c.reino());
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("reino", c.nomeReino());
            j.addProperty("nome", c.nome());
            j.addProperty("rei", c.rei());
            j.addProperty("nivelMin", c.nivelMin());
            j.addProperty("etapa", i);
            j.addProperty("total", c.etapas().size());
            if (i >= 0 && i < c.etapas().size()) {
                Etapa e = c.etapas().get(i);
                j.addProperty("etapaTitulo", e.titulo());
                j.addProperty("pedido", e.pedido());
                j.addProperty("progresso", Math.min(e.qtd(), progresso(d, c.reino())));
                j.addProperty("qtd", e.qtd());
            }
            a.add(j);
        }
        return a;
    }

    /** reputacao {reino: pontos} e campanhas {reino: {nome, etapa, total, progresso, qtd, etapaTitulo}} para a aba Reinos do painel. */
    public static com.google.gson.JsonObject resumoPainel(DadosJogador d) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        com.google.gson.JsonObject rep = new com.google.gson.JsonObject();
        d.reputacao().forEach(rep::addProperty);
        o.add("reputacao", rep);
        com.google.gson.JsonObject cs = new com.google.gson.JsonObject();
        for (Campanha c : TODAS.values()) {
            int i = etapa(d, c.reino());
            if (i < 0) continue;
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("nome", c.nome());
            j.addProperty("etapa", i);
            j.addProperty("total", c.etapas().size());
            if (i < c.etapas().size()) {
                Etapa e = c.etapas().get(i);
                j.addProperty("etapaTitulo", e.titulo().length() > 120 ? e.titulo().substring(0, 120) : e.titulo());
                j.addProperty("qtd", e.qtd());
                j.addProperty("progresso", progresso(d, c.reino()));
            }
            cs.add(c.reino(), j);
        }
        o.add("campanhas", cs);
        return o;
    }

    private static int etapa(DadosJogador d, String reino) { return d.campanhaEtapa().getOrDefault(reino, -1); }

    private static int progresso(DadosJogador d, String reino) { return d.campanhaProgresso().getOrDefault(reino, 0); }

    private static String nomeAlvo(Etapa e) {
        return switch (e.tipo()) {
            case "MATAR" -> {
                if (e.alvo().equals("*")) yield "monstros";
                for (String id : e.alvo().split("\\|")) {
                    EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
                    if (t != null && ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.tryParse(id))) yield t.getDescription().getString();
                }
                yield e.alvo();
            }
            case "COLETAR" -> {
                Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(e.alvo()));
                yield i != null ? new ItemStack(i).getHoverName().getString() : e.alvo();
            }
            case "VISITAR" -> { Reino r = Reputacao.reino(e.alvo()); yield r != null ? r.nome() : e.alvo(); }
            case "DIMENSAO" -> e.alvo().endsWith("the_end") ? "o End" : "o Nether";
            default -> "uma invasão";
        };
    }

    private static void falaRei(ServerPlayer p, Campanha c, String texto) {
        p.sendSystemMessage(Component.literal("[" + c.rei() + "] ").withStyle(ChatFormatting.GOLD).append(Component.literal(texto).withStyle(ChatFormatting.WHITE)));
    }

    private static MutableComponent objetivo(Campanha c, int i, int prog) {
        Etapa e = c.etapas().get(i);
        boolean pronto = prog >= e.qtd() && !"COLETAR".equals(e.tipo());
        return Component.literal("  ➤ " + (i + 1) + "/" + c.etapas().size() + " " + e.titulo() + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(pronto ? "concluída — volte ao rei!" : switch (e.tipo()) {
                    case "MATAR" -> "derrote " + nomeAlvo(e) + " (" + Math.min(prog, e.qtd()) + "/" + e.qtd() + ")";
                    case "COLETAR" -> "entregue " + e.qtd() + "× " + nomeAlvo(e) + " ao rei";
                    case "VISITAR" -> "vá até " + nomeAlvo(e);
                    case "DIMENSAO" -> "chegue a " + nomeAlvo(e);
                    default -> "vença uma invasão em " + c.nomeReino();
                }).withStyle(pronto ? ChatFormatting.GREEN : ChatFormatting.WHITE));
    }

    // ------------------------------------------------------------------ conversa com o rei

    /** Chamado ao clicar no rei. */
    public static void falar(ServerPlayer p, String reino) {
        Campanha c = de(reino);
        if (c == null || !Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        int i = etapa(d, reino);
        if (i < 0) {
            if (d.nivel() < c.nivelMin()) { falaRei(p, c, "Você ainda é jovem demais para o que preciso. Volte no nível " + c.nivelMin() + "."); return; }
            falaRei(p, c, "Viajante, " + c.nomeReino() + " precisa de um herói. Aceita a missão \"" + c.nome() + "\"?");
            p.sendSystemMessage(Component.literal("   [Aceitar a missão do rei]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/campanha aceitar " + reino))));
            return;
        }
        if (i >= c.etapas().size()) { falaRei(p, c, "Salve, " + c.titulo() + "! " + c.nomeReino() + " jamais esquecerá o que você fez."); return; }
        Etapa e = c.etapas().get(i);
        int prog = progresso(d, reino);
        if ("COLETAR".equals(e.tipo())) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(e.alvo()));
            int tem = 0;
            for (ItemStack s : p.getInventory().items) if (item != null && s.is(item)) tem += s.getCount();
            if (item == null || tem < e.qtd()) {
                falaRei(p, c, e.pedido() + " (você tem " + tem + "/" + e.qtd() + ")");
                return;
            }
            int falta = e.qtd();
            for (ItemStack s : p.getInventory().items) {
                if (falta <= 0) break;
                if (s.is(item)) { int t = Math.min(falta, s.getCount()); s.shrink(t); falta -= t; }
            }
            prog = e.qtd();
        }
        if (prog < e.qtd()) {
            falaRei(p, c, e.pedido());
            p.sendSystemMessage(objetivo(c, i, prog));
            return;
        }
        avancar(p, c, d, i);
    }

    static void aceitar(ServerPlayer p, String reino) {
        Campanha c = de(reino);
        if (c == null || !Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        if (etapa(d, reino) >= 0) { p.sendSystemMessage(Component.literal("Você já aceitou essa missão (/campanha).").withStyle(ChatFormatting.GRAY)); return; }
        if (d.nivel() < c.nivelMin()) { p.sendSystemMessage(Component.literal("Precisa do nível " + c.nivelMin() + ".").withStyle(ChatFormatting.RED)); return; }
        d.campanhaEtapa().put(reino, 0);
        d.campanhaProgresso().put(reino, 0);
        Progressao.enviarAoPainel(p, false);
        Etapa e = c.etapas().get(0);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(c.nome()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Missão épica de " + c.rei()).withStyle(ChatFormatting.YELLOW)));
        falaRei(p, c, e.pedido());
        p.sendSystemMessage(objetivo(c, 0, 0));
    }

    private static void avancar(ServerPlayer p, Campanha c, DadosJogador d, int i) {
        Etapa e = c.etapas().get(i);
        falaRei(p, c, e.agradecimento());
        Moedas.dar(p, 100L * (i + 1));
        Progressao.ganharXp(p, 250L * (i + 1));
        Reputacao.ganhar(p, c.reino(), 100, true);
        int prox = i + 1;
        d.campanhaEtapa().put(c.reino(), prox);
        d.campanhaProgresso().put(c.reino(), 0);
        Progressao.enviarAoPainel(p, false);
        br.com.frxcode.frxworld.racas.Evolucao.checar(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.1f);
        if (prox < c.etapas().size()) {
            Etapa n = c.etapas().get(prox);
            falaRei(p, c, n.pedido());
            p.sendSystemMessage(objetivo(c, prox, 0));
            return;
        }
        // final
        ItemStack premio = new ItemStack(c.premio());
        Armas.definirRaridade(premio, 4);
        Armas.definirNivel(premio, 5);
        premio.setHoverName(Component.literal(c.nomePremio()).withStyle(ChatFormatting.GOLD));
        if (!p.getInventory().add(premio)) p.drop(premio, false);
        Moedas.dar(p, 2000);
        Reputacao.ganhar(p, c.reino(), 1000, true);
        Conquistas.somar(p, "campanhas", 1);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(c.titulo()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(c.nomePremio() + " · 20 ouro · título novo").withStyle(ChatFormatting.YELLOW)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        if (p.getServer() != null) p.getServer().getPlayerList().broadcastSystemMessage(Component.literal("👑 " + p.getGameProfile().getName()
                + " completou a missão épica \"" + c.nome() + "\" e agora é " + c.titulo() + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    /** Títulos "Lenda de X" das campanhas concluídas. */
    public static List<String> titulos(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        if (!Progressao.temDados(p)) return l;
        DadosJogador d = Progressao.dados(p);
        for (Campanha c : TODAS.values()) if (etapa(d, c.reino()) >= c.etapas().size()) l.add(c.titulo());
        return l;
    }

    // ------------------------------------------------------------------ progresso

    private static void marcar(ServerPlayer p, Campanha c, int prog) {
        DadosJogador d = Progressao.dados(p);
        d.campanhaProgresso().put(c.reino(), prog);
        Etapa e = c.etapas().get(etapa(d, c.reino()));
        if (prog == e.qtd()) {
            p.displayClientMessage(Component.literal("✦ " + e.titulo() + " concluída! Volte a " + c.rei() + ".").withStyle(ChatFormatting.GREEN), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1f, 1.2f);
        } else if (prog < e.qtd()) {
            p.displayClientMessage(Component.literal(e.titulo() + ": " + prog + "/" + e.qtd()).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    @SubscribeEvent
    public void aoMatar(LivingDeathEvent ev) {
        LivingEntity morto = ev.getEntity();
        if (!(ev.getSource().getEntity() instanceof ServerPlayer matador)) return;
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(morto.getType());
        String id = rl == null ? "" : rl.toString();
        for (ServerPlayer p : Grupos.perto(matador)) {
            if (!Progressao.temDados(p)) continue;
            DadosJogador d = Progressao.dados(p);
            for (Campanha c : TODAS.values()) {
                int i = etapa(d, c.reino());
                if (i < 0 || i >= c.etapas().size()) continue;
                Etapa e = c.etapas().get(i);
                if (!"MATAR".equals(e.tipo())) continue;
                boolean conta = e.alvo().equals("*") ? morto instanceof Enemy : List.of(e.alvo().split("\\|")).contains(id);
                int prog = progresso(d, c.reino());
                if (conta && prog < e.qtd()) marcar(p, c, prog + 1);
            }
        }
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 40 != 0 || !Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        for (Campanha c : TODAS.values()) {
            int i = etapa(d, c.reino());
            if (i < 0 || i >= c.etapas().size()) continue;
            Etapa e = c.etapas().get(i);
            if (progresso(d, c.reino()) >= e.qtd()) continue;
            boolean ok = switch (e.tipo()) {
                case "VISITAR" -> {
                    Reino r = Reputacao.reino(e.alvo());
                    yield r != null && p.level().dimension() == Level.OVERWORLD && r.distancia(p.getBlockX(), p.getBlockZ()) == 0;
                }
                case "DIMENSAO" -> p.level().dimension().location().toString().equals(e.alvo());
                default -> false;
            };
            if (ok) marcar(p, c, e.qtd());
        }
    }

    /** Invasão vencida: conta para a etapa DEFENDER da campanha daquele reino. */
    public static void aoDefender(ServerPlayer p, String reino) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        Campanha c = de(reino);
        if (c == null) return;
        int i = etapa(d, reino);
        if (i < 0 || i >= c.etapas().size()) return;
        Etapa e = c.etapas().get(i);
        if ("DEFENDER".equals(e.tipo()) && progresso(d, reino) < e.qtd()) marcar(p, c, e.qtd());
    }

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("campanha")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    DadosJogador d = Progressao.dados(p);
                    p.sendSystemMessage(Component.literal("══════ Missões épicas dos reis ══════").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    for (Campanha c : TODAS.values()) {
                        int i = etapa(d, c.reino());
                        MutableComponent l = Component.literal(c.nome() + " — " + c.rei()).withStyle(ChatFormatting.WHITE);
                        if (i < 0) l.append(Component.literal("  (fale com o rei de " + c.nomeReino() + ", nível " + c.nivelMin() + "+)").withStyle(ChatFormatting.DARK_GRAY));
                        else if (i >= c.etapas().size()) l.append(Component.literal("  ✔ concluída").withStyle(ChatFormatting.GREEN));
                        p.sendSystemMessage(l);
                        if (i >= 0 && i < c.etapas().size()) p.sendSystemMessage(objetivo(c, i, progresso(d, c.reino())));
                    }
                    return 1;
                })
                .then(Commands.literal("aceitar").then(Commands.argument("reino", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TODAS.keySet(), b))
                        .executes(ctx -> { aceitar(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "reino")); return 1; }))));
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("campanha").then(Commands.literal("pular").then(Commands.argument("reino", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TODAS.keySet(), b))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Campanha c = de(StringArgumentType.getString(ctx, "reino"));
                            if (c == null) return 0;
                            DadosJogador d = Progressao.dados(p);
                            int i = etapa(d, c.reino());
                            if (i < 0) { d.campanhaEtapa().put(c.reino(), 0); d.campanhaProgresso().put(c.reino(), 0); i = 0; }
                            if (i < c.etapas().size()) d.campanhaProgresso().put(c.reino(), c.etapas().get(i).qtd());
                            ctx.getSource().sendSuccess(() -> Component.literal("Etapa marcada como feita; fale com o rei."), false);
                            return 1;
                        })))));
    }
}
