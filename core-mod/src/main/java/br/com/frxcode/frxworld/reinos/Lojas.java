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
    private record Linha(String item, int qtd, int preco) {}

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
            l.add(new Linha(o.get("item").getAsString(), o.get("qtd").getAsInt(), o.get("preco").getAsInt()));
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
    public static List<MerchantOffer> mercador(double desconto, int nivelRep, String nomeReino) {
        fatorVenda = 1 - desconto;
        try {
            List<MerchantOffer> l = new ArrayList<>(mercador());
            l.addAll(especiais(nivelRep, nomeReino));
            return l;
        } finally { fatorVenda = 1; }
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
