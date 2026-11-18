package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.moedas.Moedas;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;

/** Tabelas de preço das lojas das capitais (valores em cobre: 10 cobre = 1 prata, 100 = 1 ouro). */
public final class Lojas {
    private Lojas() {}

    /** Item da tabela vinda do painel: id do item, quantidade e preço em cobre. */
    private record Linha(String item, int qtd, int preco, int nivelMin) {}

    /** Nível do jogador que está abrindo a loja (só durante a montagem da tabela). */
    private static int nivelJogador = Integer.MAX_VALUE;

    private static volatile List<Linha> painelMercador = List.of(), painelComprador = List.of();

    /** Lê as tabelas da aba Economia (chamado ao ligar e a cada minuto). Se o painel estiver vazio, valem as tabelas padrão. */
    public static void recarregar() {
        br.com.frxcode.frxworld.acesso.PainelApi.lerLojas().whenComplete((json, erro) -> {
            if (erro != null) return;
            try {
                painelMercador = linhas(json.getAsJsonArray("mercador"));
                painelComprador = linhas(json.getAsJsonArray("comprador"));
            } catch (Exception e) {
                br.com.frxcode.frxworld.FrxWorld.LOG.warn("Lojas do painel inválidas: {}", e.toString());
            }
        });
    }

    private static List<Linha> linhas(com.google.gson.JsonArray arr) {
        List<Linha> l = new ArrayList<>();
        if (arr == null) return l;
        for (com.google.gson.JsonElement e : arr) {
            com.google.gson.JsonObject o = e.getAsJsonObject();
            l.add(new Linha(o.get("item").getAsString(), o.get("qtd").getAsInt(), o.get("preco").getAsInt(), o.has("nivelMin") ? o.get("nivelMin").getAsInt() : 1));
        }
        return l;
    }

    private static Item item(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        Item i = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        return i == null || i == Items.AIR ? null : i;
    }

    private static List<MerchantOffer> doPainel(List<Linha> linhas, boolean jogadorVende) {
        List<MerchantOffer> l = new ArrayList<>();
        for (Linha x : linhas) {
            // "minecraft:potion#healing" = poção com efeito
            String[] partes = x.item().split("#", 2);
            Item i = item(partes[0]);
            if (i == null || x.qtd() < 1 || x.preco() < 1) continue;
            if (!jogadorVende && x.nivelMin() > nivelJogador) continue;   // item liberado só a partir de certo nível
            ItemStack pilha = new ItemStack(i, Math.min(x.qtd(), i.getMaxStackSize()));
            if (partes.length == 2) {
                net.minecraft.world.item.alchemy.Potion pocao = net.minecraftforge.registries.ForgeRegistries.POTIONS.getValue(net.minecraft.resources.ResourceLocation.tryParse(partes[1]));
                if (pocao != null) PotionUtils.setPotion(pilha, pocao);
            }
            l.add(jogadorVende ? compra(i, pilha.getCount(), x.preco()) : venda(pilha, x.preco()));
        }
        return l;
    }

    /** Uma pilha de moeda (até 64): usa a maior moeda que representa o preço exato. */
    private static ItemStack preco(int cobre) {
        if (cobre >= 100 && cobre % 100 == 0 && cobre / 100 <= 64) return Moedas.ouro(cobre / 100);
        if (cobre >= 10 && cobre % 10 == 0 && cobre / 10 <= 64) return Moedas.prata(cobre / 10);
        if (cobre <= 64) return Moedas.cobre(cobre);
        return cobre / 10 <= 64 ? Moedas.prata(Math.max(1, Math.round(cobre / 10f))) : Moedas.ouro(Math.min(64, Math.max(1, Math.round(cobre / 100f))));
    }

    /** Ajuste de preço pela reputação (só durante a montagem da tabela; o servidor é uma thread). */
    private static double fatorVenda = 1, fatorCompra = 1;

    /** Tabela do mercador com desconto e os itens especiais da reputação daquele reino. */
    /** Cambista: troca de moedas sem taxa (1 ouro = 10 prata = 100 cobre). */
    public static List<MerchantOffer> cambio() {
        List<MerchantOffer> l = new java.util.ArrayList<>();
        l.add(new MerchantOffer(Moedas.ouro(1), Moedas.prata(10), 9999, 0, 0f));    // ouro  -> prata
        l.add(new MerchantOffer(Moedas.prata(10), Moedas.ouro(1), 9999, 0, 0f));    // prata -> ouro
        l.add(new MerchantOffer(Moedas.prata(1), Moedas.cobre(10), 9999, 0, 0f));   // prata -> cobre
        l.add(new MerchantOffer(Moedas.cobre(10), Moedas.prata(1), 9999, 0, 0f));   // cobre -> prata
        l.add(new MerchantOffer(Moedas.prata(5), Moedas.cobre(50), 9999, 0, 0f));   // de 5 em 5
        l.add(new MerchantOffer(Moedas.cobre(50), Moedas.prata(5), 9999, 0, 0f));
        return l;
    }

    public static List<MerchantOffer> mercador(double desconto, int nivelRep, String nomeReino, int nivel) {
        fatorVenda = 1 - desconto;
        nivelJogador = nivel;
        try {
            List<MerchantOffer> l = new ArrayList<>(mercador());
            l.addAll(especiais(nivelRep, nomeReino));
            return l;
        } finally { fatorVenda = 1; nivelJogador = Integer.MAX_VALUE; }
    }

    // ------------------------------------------------------------------ ferreiro e armeiro: loja pelo nível do jogador

    private static final int[] TIER_NIVEL = {1, 10, 20, 30, 40, 50};
    private static final String[] TIER_MATERIAL = {"iron", "iron", "diamond", "diamond", "netherite", "netherite"};
    private static final String[] TIER_NOME = {"ferro", "ferro Incomum +2", "diamante", "diamante Raro +3", "netherite", "netherite Épico +5"};
    private static final int[] TIER_RARIDADE = {0, 1, 0, 2, 0, 3}, TIER_MAIS = {0, 2, 0, 3, 0, 5};
    private static final double[] TIER_PRECO = {1, 2.5, 8, 16, 30, 48};
    private static final String[] PECAS_FERREIRO = {"sword", "axe", "pickaxe", "shovel"}, PECAS_ARMEIRO = {"helmet", "chestplate", "leggings", "boots"};
    private static final int[] BASE_FERREIRO = {60, 70, 70, 30}, BASE_ARMEIRO = {80, 130, 110, 70};

    public static int tier(int nivel) {
        int t = 0;
        for (int i = 0; i < TIER_NIVEL.length; i++) if (nivel >= TIER_NIVEL[i]) t = i;
        return t;
    }

    /** Mercador no menu novo: a lista do painel (pelo nível) mais o estoque do dia com utilidades dos mods. */
    public static List<MerchantOffer> mercadorMenu(double desconto, int nivelRep, String nomeReino, int nivel,
                                                   net.minecraft.server.MinecraftServer servidor, String semente) {
        List<MerchantOffer> l = new ArrayList<>(mercador(desconto, nivelRep, nomeReino, nivel));
        fatorVenda = 1 - desconto;
        try {
            if (servidor != null)
                for (Catalogo.Entrada e : Catalogo.escolher(Catalogo.utilidades(servidor), nivel, 1, 10, semente))
                    l.add(venda(new ItemStack(e.item(), e.qtd()), e.preco()));
        } finally { fatorVenda = 1; }
        return l;
    }

    /** Cozinheiro: só comida, de todos os mods. O que aparece depende do nível do jogador; o estoque muda a cada dia. */
    public static List<MerchantOffer> comida(int nivel, double desconto, net.minecraft.server.MinecraftServer servidor, String semente) {
        fatorVenda = 1 - desconto;
        try {
            List<MerchantOffer> l = new ArrayList<>();
            for (Catalogo.Entrada e : Catalogo.escolher(Catalogo.comidas(servidor), nivel, 1, 18, semente))
                l.add(venda(new ItemStack(e.item(), e.qtd()), e.preco()));
            return l;
        } finally { fatorVenda = 1; }
    }

    /** Loja do pedreiro: blocos essenciais de construção, sempre os mesmos, em pilhas. Preço em cobre. */
    private static final Object[][] BLOCOS = {
            {"minecraft:oak_log", 16, 20}, {"minecraft:spruce_log", 16, 20}, {"minecraft:birch_log", 16, 20}, {"minecraft:dark_oak_log", 16, 22},
            {"minecraft:oak_planks", 32, 12}, {"minecraft:spruce_planks", 32, 12}, {"minecraft:birch_planks", 32, 12},
            {"minecraft:cobblestone", 32, 10}, {"minecraft:stone", 32, 16}, {"minecraft:stone_bricks", 32, 24}, {"minecraft:cobbled_deepslate", 32, 16},
            {"minecraft:dirt", 32, 6}, {"minecraft:sand", 32, 14}, {"minecraft:gravel", 32, 10}, {"minecraft:clay", 16, 20},
            {"minecraft:glass", 16, 20}, {"minecraft:bricks", 16, 30}, {"minecraft:terracotta", 16, 20}, {"minecraft:white_concrete", 16, 30},
            {"minecraft:quartz_block", 16, 40}, {"minecraft:white_wool", 8, 16},
            {"minecraft:torch", 16, 10}, {"minecraft:ladder", 16, 10}, {"minecraft:oak_door", 2, 6}, {"minecraft:oak_fence", 16, 12},
            {"minecraft:chest", 2, 10}, {"minecraft:crafting_table", 1, 4}, {"minecraft:furnace", 1, 8}, {"minecraft:white_bed", 1, 12}};

    public static List<MerchantOffer> blocos(double desconto) {
        fatorVenda = 1 - desconto;
        try {
            List<MerchantOffer> l = new ArrayList<>();
            for (Object[] b : BLOCOS) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse((String) b[0]));
                if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
                l.add(venda(new ItemStack(item, (Integer) b[1]), (Integer) b[2]));
            }
            return l;
        } finally { fatorVenda = 1; }
    }

    /** Aviso do que chega no próximo patamar ("" no último). */
    public static String proximoTier(int nivel) {
        int t = tier(nivel);
        return t + 1 >= TIER_NIVEL.length ? "" : "No nível " + TIER_NIVEL[t + 1] + " chegam os itens de " + TIER_NOME[t + 1] + ".";
    }

    /** Loja do ferreiro (armas e ferramentas) ou do armeiro (armaduras): o patamar do nível do jogador e o anterior. */
    public static List<MerchantOffer> forja(boolean armeiro, int nivel, double desconto) { return forja(armeiro, nivel, desconto, null, ""); }

    /** Com o servidor: além dos itens fixos, entra o estoque do dia com armas/armaduras dos outros mods no nível do jogador. */
    public static List<MerchantOffer> forja(boolean armeiro, int nivel, double desconto, net.minecraft.server.MinecraftServer servidor, String semente) {
        fatorVenda = 1 - desconto;
        try {
            List<MerchantOffer> l = new ArrayList<>();
            int atual = tier(nivel);
            String[] pecas = armeiro ? PECAS_ARMEIRO : PECAS_FERREIRO;
            int[] base = armeiro ? BASE_ARMEIRO : BASE_FERREIRO;
            for (int t = atual; t >= Math.max(0, atual - 1); t--) {
                for (int i = 0; i < pecas.length; i++) {
                    Item it = item("minecraft:" + TIER_MATERIAL[t] + "_" + pecas[i]);
                    if (it == null) continue;
                    ItemStack s = new ItemStack(it);
                    if (TIER_RARIDADE[t] > 0) br.com.frxcode.frxworld.armas.Armas.definirRaridade(s, TIER_RARIDADE[t]);
                    if (TIER_MAIS[t] > 0) br.com.frxcode.frxworld.armas.Armas.definirNivel(s, TIER_MAIS[t]);
                    l.add(venda(s, (int) Math.max(10, Math.round(base[i] * TIER_PRECO[t] / 10.0) * 10)));
                }
            }
            if (servidor != null) {
                int piso = TIER_NIVEL[Math.max(0, atual - 1)];
                for (Catalogo.Entrada e : Catalogo.escolher(armeiro ? Catalogo.armaduras(servidor) : Catalogo.armas(servidor), nivel, piso, 12, semente))
                    l.add(venda(new ItemStack(e.item(), e.qtd()), e.preco()));
            }
            if (armeiro) l.add(venda(new ItemStack(Items.SHIELD), 50));
            else { l.add(venda(new ItemStack(Items.BOW), 50)); l.add(venda(new ItemStack(Items.ARROW, 16), 5)); }
            return l;
        } finally { fatorVenda = 1; }
    }

    /** Equipamento de classe F (pedra e couro) para quem ainda é Rank F da Guilda e não pode usar ferro. */
    public static List<MerchantOffer> basicos(boolean armeiro, double desconto) {
        fatorVenda = 1 - desconto;
        try {
            List<MerchantOffer> l = new ArrayList<>();
            if (armeiro) {
                l.add(venda(new ItemStack(Items.LEATHER_HELMET), 30)); l.add(venda(new ItemStack(Items.LEATHER_CHESTPLATE), 50));
                l.add(venda(new ItemStack(Items.LEATHER_LEGGINGS), 40)); l.add(venda(new ItemStack(Items.LEATHER_BOOTS), 25));
            } else {
                l.add(venda(new ItemStack(Items.STONE_SWORD), 20)); l.add(venda(new ItemStack(Items.STONE_AXE), 25));
            }
            return l;
        } finally { fatorVenda = 1; }
    }

    /** Uma oferta avulsa (armas do Códex), já com o desconto do jogador. */
    public static MerchantOffer ofertaAvulsa(ItemStack item, int cobre, double desconto) {
        fatorVenda = 1 - desconto;
        try { return venda(item, cobre); } finally { fatorVenda = 1; }
    }

    /** Tabela do comprador pagando a mais pela reputação. */
    public static List<MerchantOffer> comprador(double bonus) {
        fatorCompra = 1 + bonus;
        try { return comprador(); } finally { fatorCompra = 1; }
    }

    /** Itens liberados pela reputação: Honrado (2), Reverenciado (3), Exaltado (4), Herói (5). */
    private static List<MerchantOffer> especiais(int n, String reino) {
        List<MerchantOffer> l = new ArrayList<>();
        String nome = reino == null || reino.isBlank() ? "do Reino" : "de " + Reputacao.curto(reino);
        if (n >= 2) {
            l.add(venda(new ItemStack(Items.TOTEM_OF_UNDYING), 500));
            l.add(venda(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 800));
        }
        if (n >= 3) {
            l.add(venda(new ItemStack(Items.ELYTRA), 3000));
            l.add(venda(new ItemStack(Items.SHULKER_BOX), 800));
        }
        if (n >= 4) l.add(venda(armaDoReino(Items.NETHERITE_SWORD, "Lâmina " + nome, 3, 2), 5000));
        if (n >= 5) {
            l.add(venda(armaDoReino(Items.NETHERITE_SWORD, "Espada Real " + nome, 4, 5), 10000));
            l.add(venda(armaDoReino(Items.NETHERITE_CHESTPLATE, "Couraça Real " + nome, 4, 5), 10000));
        }
        return l;
    }

    private static ItemStack armaDoReino(Item item, String nome, int raridade, int nivel) {
        ItemStack s = new ItemStack(item);
        br.com.frxcode.frxworld.armas.Armas.definirRaridade(s, raridade);
        br.com.frxcode.frxworld.armas.Armas.definirNivel(s, nivel);
        s.setHoverName(net.minecraft.network.chat.Component.literal(nome));
        return s;
    }

    private static MerchantOffer compra(Item item, int qtd, int cobre) {
        cobre = (int) Math.max(1, Math.round(cobre * fatorCompra));
        return new MerchantOffer(new ItemStack(item, qtd), preco(cobre), 9999, 2, 0f);
    }

    private static MerchantOffer venda(ItemStack item, int cobre) {
        cobre = (int) Math.max(1, Math.round(cobre * fatorVenda));
        // preço quebrado (ex.: 75) vira duas pilhas: 7 prata + 5 cobre
        int ouro = cobre / 100, prata = (cobre % 100) / 10, cobres = cobre % 10;
        ItemStack[] partes = new ItemStack[3];
        int n = 0;
        if (ouro > 0) partes[n++] = Moedas.ouro(Math.min(64, ouro));
        if (prata > 0) partes[n++] = Moedas.prata(prata);
        if (cobres > 0) partes[n++] = Moedas.cobre(cobres);
        if (n == 1) return new MerchantOffer(partes[0], item, 9999, 2, 0f);
        if (n == 2) return new MerchantOffer(partes[0], partes[1], item, 9999, 2, 0f);
        if (cobre / 10 <= 64) return new MerchantOffer(Moedas.prata(cobre / 10), Moedas.cobre(cobres), item, 9999, 2, 0f);
        return new MerchantOffer(preco(cobre), item, 9999, 2, 0f);
    }

    /** O jogador vende o saque das aventuras. */
    static List<MerchantOffer> comprador() {
        if (!painelComprador.isEmpty()) return doPainel(painelComprador, true);
        List<MerchantOffer> l = new ArrayList<>();
        l.add(compra(Items.ROTTEN_FLESH, 16, 3));
        l.add(compra(Items.BONE, 8, 4));
        l.add(compra(Items.STRING, 8, 4));
        l.add(compra(Items.SPIDER_EYE, 4, 4));
        l.add(compra(Items.ARROW, 32, 3));
        l.add(compra(Items.GUNPOWDER, 4, 6));
        l.add(compra(Items.LEATHER, 6, 5));
        l.add(compra(Items.FEATHER, 16, 3));
        l.add(compra(Items.SLIME_BALL, 4, 6));
        l.add(compra(Items.PHANTOM_MEMBRANE, 2, 8));
        l.add(compra(Items.ENDER_PEARL, 1, 8));
        l.add(compra(Items.BLAZE_ROD, 1, 10));
        l.add(compra(Items.GHAST_TEAR, 1, 20));
        l.add(compra(Items.COAL, 16, 4));
        l.add(compra(Items.REDSTONE, 16, 5));
        l.add(compra(Items.LAPIS_LAZULI, 16, 6));
        l.add(compra(Items.IRON_INGOT, 4, 8));
        l.add(compra(Items.GOLD_INGOT, 3, 10));
        l.add(compra(Items.EMERALD, 1, 10));
        l.add(compra(Items.DIAMOND, 1, 30));
        l.add(compra(Items.NETHERITE_SCRAP, 1, 200));
        l.add(compra(Items.WHEAT, 20, 3));
        l.add(compra(Items.POTATO, 20, 3));
        l.add(compra(Items.CARROT, 20, 3));
        l.add(compra(Items.COD, 6, 4));
        return l;
    }

    /** Utilidades para aventureiros. */
    static List<MerchantOffer> mercador() {
        if (!painelMercador.isEmpty()) return doPainel(painelMercador, false);
        List<MerchantOffer> l = new ArrayList<>();
        l.add(venda(new ItemStack(Items.BREAD, 6), 3));
        l.add(venda(new ItemStack(Items.COOKED_BEEF, 4), 5));
        l.add(venda(new ItemStack(Items.TORCH, 16), 4));
        l.add(venda(new ItemStack(Items.LANTERN, 2), 6));
        l.add(venda(new ItemStack(Items.ARROW, 16), 5));
        l.add(venda(new ItemStack(Items.MAP), 5));
        l.add(venda(new ItemStack(Items.RED_BED), 10));
        l.add(venda(new ItemStack(Items.SHIELD), 10));
        l.add(venda(new ItemStack(Items.BOW), 10));
        l.add(venda(new ItemStack(Items.IRON_SWORD), 20));
        l.add(venda(new ItemStack(Items.IRON_PICKAXE), 20));
        l.add(venda(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING), 10));
        l.add(venda(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.STRONG_HEALING), 30));
        l.add(venda(new ItemStack(Items.FIREWORK_ROCKET, 8), 10));
        l.add(venda(new ItemStack(Items.EXPERIENCE_BOTTLE, 4), 10));
        l.add(venda(new ItemStack(Items.ENDER_PEARL), 20));
        l.add(venda(new ItemStack(Items.SADDLE), 40));
        l.add(venda(new ItemStack(Items.NAME_TAG), 30));
        l.add(venda(new ItemStack(Items.GOLDEN_APPLE), 80));
        return l;
    }

    static MerchantOffer infinita(MerchantOffer o) {
        return new MerchantOffer(o.getBaseCostA(), o.getCostB(), o.getResult(), 0, 9999, o.getXp(), o.getPriceMultiplier(), o.getDemand());
    }
}
