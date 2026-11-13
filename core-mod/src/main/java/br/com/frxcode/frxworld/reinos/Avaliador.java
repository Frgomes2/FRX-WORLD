package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.armas.Armas;
import br.com.frxcode.frxworld.moedas.ItemMoeda;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Avaliação automática de qualquer item para o comprador: o preço sai da raridade do item
 * (raridade do jogo, raridade/forja do FRX, encantamentos e desgaste), sem lista fixa.
 * Cada jogador tem um teto diário de moedas que pode receber, que cresce com o nível.
 */
public final class Avaliador {
    private static final String CHAVE = "frx_venda";

    private Avaliador() {}

    private static JsonObject cfg() { return ConfigJogo.secaoCrua("comprador"); }

    private static double num(String k, double padrao) {
        JsonObject o = cfg();
        try { return o != null && o.has(k) ? o.get(k).getAsDouble() : padrao; } catch (Exception e) { return padrao; }
    }

    /** Preço-base (cobre) de uma unidade pela raridade do próprio jogo. */
    private static double base(Rarity r) {
        return switch (r) {
            case UNCOMMON -> num("precoIncomum", 10);
            case RARE -> num("precoRaro", 40);
            case EPIC -> num("precoEpico", 150);
            default -> num("precoComum", 2);
        };
    }

    // ------------------------------------------------------------------ catálogo (armas, armaduras e comidas dos mods)

    private static Map<Item, Double> catalogo;

    private static synchronized Map<Item, Double> catalogo(MinecraftServer s) {
        if (catalogo != null) return catalogo;
        Map<Item, Double> m = new HashMap<>();
        for (java.util.List<Catalogo.Entrada> lista : java.util.List.of(Catalogo.armas(s), Catalogo.armaduras(s), Catalogo.comidas(s)))
            for (Catalogo.Entrada e : lista) m.put(e.item(), (double) e.preco() / Math.max(1, e.qtd()));
        catalogo = m;
        return m;
    }

    // ------------------------------------------------------------------ preço

    /** O comprador não aceita moedas, o cartão da Guilda nem itens marcados pelo FRX. */
    public static boolean recusa(ItemStack s) {
        if (s.isEmpty() || s.getItem() instanceof ItemMoeda) return true;
        if (s.is(br.com.frxcode.frxworld.blocos.Blocos.CARTAO.get())) return true;
        CompoundTag t = s.getTag();
        return t != null && (t.contains("frx_missao") || t.contains("frx_dungeon"));
    }

    /** Quanto o comprador paga por UMA unidade deste item (cobre). 0 = não compra. */
    public static long precoUnidade(MinecraftServer s, ItemStack pilha) {
        if (recusa(pilha)) return 0;
        double preco = base(pilha.getRarity());
        Double doCatalogo = catalogo(s).get(pilha.getItem());
        if (doCatalogo != null) preco = Math.max(preco, doCatalogo * num("fracaoVenda", 0.30));

        int rf = Armas.raridade(pilha);
        if (rf > 0) preco *= new double[]{1, 1.6, 2.5, 4, 7}[Math.min(4, rf)];
        int nf = Armas.nivel(pilha);
        if (nf > 0) preco *= 1 + 0.10 * nf;

        int enc = 0;
        for (int v : EnchantmentHelper.getEnchantments(pilha).values()) enc += v;
        if (enc > 0) preco *= 1 + num("bonusEncanto", 0.15) * enc;

        if (pilha.isDamageableItem() && pilha.getMaxDamage() > 0)
            preco *= Math.max(0.1, 1 - (double) pilha.getDamageValue() / pilha.getMaxDamage());

        return Math.max(1, Math.round(preco * num("multiplicador", 1.0)));
    }

    public static long precoPilha(MinecraftServer s, ItemStack pilha) {
        return precoUnidade(s, pilha) * pilha.getCount();
    }

    // ------------------------------------------------------------------ teto diário

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    private static long hoje() { return System.currentTimeMillis() / 86_400_000L; }

    /** Teto de moedas que o jogador pode receber por dia (cobre): cresce com o nível. */
    public static long limiteDiario(ServerPlayer p) {
        int nivel = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
        double ouro = num("limiteBaseOuro", 20) + num("limitePorNivelOuro", 3) * (nivel - 1);
        return Math.max(0, Math.round(ouro * 100));
    }

    public static long recebidoHoje(ServerPlayer p) {
        CompoundTag d = dados(p);
        return d.getLong("dia") == hoje() ? d.getLong("recebido") : 0;
    }

    public static long restaHoje(ServerPlayer p) {
        return Math.max(0, limiteDiario(p) - recebidoHoje(p));
    }

    public static void somarRecebido(ServerPlayer p, long quanto) {
        CompoundTag d = dados(p);
        if (d.getLong("dia") != hoje()) { d.putLong("dia", hoje()); d.putLong("recebido", 0); }
        d.putLong("recebido", d.getLong("recebido") + quanto);
    }

    /** Quanto falta para o teto zerar, em texto curto ("4 h 12 min"). */
    public static String quandoRenova() {
        long ms = (hoje() + 1) * 86_400_000L - System.currentTimeMillis();
        long h = ms / 3_600_000L, min = ms % 3_600_000L / 60_000L;
        return h > 0 ? h + " h " + min + " min" : min + " min";
    }
}
