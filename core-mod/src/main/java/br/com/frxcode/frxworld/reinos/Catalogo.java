package br.com.frxcode.frxworld.reinos;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Catálogo automático das lojas: lê as armas, armaduras e comidas de TODOS os mods instalados,
 * mede a força de cada item (dano, defesa, fome) e define o nível mínimo e o preço.
 * Só entram armas/armaduras que têm receita (drop de chefe não é vendido) e que cabem até o nível 60.
 */
public final class Catalogo {
    private Catalogo() {}

    public record Entrada(Item item, int qtd, int nivel, int preco) {}

    private static List<Entrada> armas, armaduras, comidas;

    private static int dez(double v) { return (int) Math.max(10, Math.round(v / 10.0) * 10); }

    /** Preço-base (cobre) de um equipamento do nível n: ferro 60, diamante ~310, netherite ~1700. */
    private static double precoNivel(int n) { return 60 * Math.pow(1.09, n - 1); }

    private static double soma(Item item, Attribute a) {
        double s = 0;
        for (AttributeModifier m : item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND).get(a))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) s += m.getAmount();
        return s;
    }

    private static synchronized void montar(MinecraftServer servidor) {
        if (armas != null) return;
        Set<Item> comReceita = new HashSet<>();
        for (Recipe<?> r : servidor.getRecipeManager().getRecipes()) {
            try {
                ItemStack s = r.getResultItem(servidor.registryAccess());
                if (!s.isEmpty()) comReceita.add(s.getItem());
            } catch (Throwable ignorado) { }
        }
        List<Entrada> la = new ArrayList<>(), ld = new ArrayList<>(), lc = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            try {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null) continue;
                boolean vanilla = id.getNamespace().equals("minecraft");

                if (item.isEdible()) {
                    FoodProperties fp = item.getFoodProperties();
                    if (fp == null || fp.getNutrition() <= 0 || item.getRarity(new ItemStack(item)) == Rarity.EPIC) continue;
                    boolean ruim = false;
                    for (var par : fp.getEffects()) if (par.getFirst().getEffect().getCategory() == MobEffectCategory.HARMFUL) ruim = true;
                    if (ruim) continue;
                    boolean especial = !fp.getEffects().isEmpty();
                    int n = fp.getNutrition();
                    int nivel = (n <= 4 ? 1 : n <= 6 ? 5 : n <= 8 ? 10 : 20) + (especial ? 15 : 0) + (item.getRarity(new ItemStack(item)) == Rarity.RARE ? 10 : 0);
                    int unidade = (int) Math.max(1, Math.round(n * (0.3 + fp.getSaturationModifier()) * 0.25));
                    lc.add(especial ? new Entrada(item, 1, nivel, Math.max(60, unidade * 40)) : new Entrada(item, 4, nivel, unidade * 4));
                    continue;
                }
                if (vanilla || !comReceita.contains(item)) continue;   // as vanilla já estão na lista fixa do ferreiro/armeiro

                if (item instanceof ArmorItem a) {
                    ArmorMaterial m = a.getMaterial();
                    double s = m.getDefenseForType(ArmorItem.Type.CHESTPLATE) + m.getToughness() + m.getKnockbackResistance() * 10;
                    if (s < 5) continue;
                    int nivel = (int) Math.round(s <= 6 ? 1 : s <= 10 ? 1 + (s - 6) * 4.75 : 20 + (s - 10) * 10);
                    if (nivel > 60) continue;
                    double f = switch (a.getType()) { case HELMET -> 1.3; case CHESTPLATE -> 2.2; case LEGGINGS -> 1.8; default -> 1.2; };
                    ld.add(new Entrada(item, 1, nivel, Math.min(6400, dez(precoNivel(nivel) * f))));
                } else if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TieredItem && !(item instanceof DiggerItem)) {
                    double dano = 1 + soma(item, Attributes.ATTACK_DAMAGE), vel = 4 + soma(item, Attributes.ATTACK_SPEED);
                    if (vel <= 0.1) continue;
                    double e = dano * Math.sqrt(vel / 1.6);
                    if (e < 5.5) continue;
                    int nivel = (int) Math.max(1, Math.round(1 + (e - 6) * 20));
                    if (nivel > 60) continue;
                    la.add(new Entrada(item, 1, nivel, Math.min(6400, dez(precoNivel(nivel) * (item instanceof AxeItem ? 1.15 : 1)))));
                }
            } catch (Throwable ignorado) { }
        }
        armas = la; armaduras = ld; comidas = lc;
        br.com.frxcode.frxworld.FrxWorld.LOG.info("Catálogo das lojas: {} armas, {} armaduras e {} comidas de mods.", la.size(), ld.size(), lc.size());
    }

    public static List<Entrada> armas(MinecraftServer s) { montar(s); return armas; }
    public static List<Entrada> armaduras(MinecraftServer s) { montar(s); return armaduras; }
    public static List<Entrada> comidas(MinecraftServer s) { montar(s); return comidas; }

    /** Estoque do dia: até `max` itens entre os níveis piso..nivel, sorteados pela semente (muda a cada dia), do melhor para o mais simples. */
    public static List<Entrada> escolher(List<Entrada> todas, int nivel, int piso, int max, String semente) {
        List<Entrada> l = new ArrayList<>();
        for (Entrada e : todas) if (e.nivel() <= nivel && e.nivel() >= piso) l.add(e);
        l.sort(Comparator.comparing(e -> String.valueOf(ForgeRegistries.ITEMS.getKey(e.item()))));
        Collections.shuffle(l, new Random(semente.hashCode() * 31L + System.currentTimeMillis() / 86_400_000L));
        if (l.size() > max) l = new ArrayList<>(l.subList(0, max));
        l.sort(Comparator.comparingInt(Entrada::nivel).reversed().thenComparing(Comparator.comparingInt(Entrada::preco).reversed()));
        return l;
    }
}
