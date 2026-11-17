package br.com.frxcode.frxworld.armas;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.function.Supplier;

/** Equipamento achado em baús e derrubado por monstros ganha uma raridade sorteada. */
public class RaridadeLoot extends LootModifier {
    public static final Supplier<Codec<RaridadeLoot>> CODEC =
            Suppliers.memoize(() -> RecordCodecBuilder.create(i -> codecStart(i).apply(i, RaridadeLoot::new)));

    public RaridadeLoot(LootItemCondition[] condicoes) { super(condicoes); }

    /** Mods cujos baús são de masmorra/estrutura perigosa: raridade melhor e prêmios extras. */
    private static final java.util.Set<String> MASMORRA_MODS = java.util.Set.of("cataclysm", "dungeons_arise", "dungeons_enhanced", "betterdungeons",
            "dungeonnowloading", "idas", "graveyard", "iceandfire", "aquamirae", "mowziesmobs", "betterfortresses", "betterstrongholds",
            "betterdeserttemples", "betterjungletemples", "betteroceanmonuments", "born_in_chaos_v1", "alexscaves", "mns", "mes");
    /** Baús vanilla de lugares perigosos. */
    private static final String[] MASMORRA_VANILLA = {"chests/simple_dungeon", "chests/stronghold", "chests/ancient_city", "chests/bastion",
            "chests/end_city", "chests/woodland_mansion", "chests/nether_bridge", "chests/buried_treasure", "chests/abandoned_mineshaft"};

    public static final int[] MASMORRA = {12, 25, 33, 22, 8};
    /** Raridade nos baús das capitais, por nível do reino (1 a 5). */
    private static final int[][] REINO = {{50, 30, 15, 5, 0}, {40, 32, 20, 7, 1}, {30, 32, 25, 11, 2}, {20, 30, 30, 16, 4}, {10, 25, 35, 22, 8}};

    static boolean deMasmorra(net.minecraft.resources.ResourceLocation id) {
        if (id == null || !id.getPath().startsWith("chests/")) return false;
        if (MASMORRA_MODS.contains(id.getNamespace())) return true;
        if (id.getNamespace().equals("frxworld") && id.getPath().startsWith("chests/masmorra")) return true;
        if (id.getNamespace().equals("minecraft")) for (String v : MASMORRA_VANILLA) if (id.getPath().startsWith(v)) return true;
        return false;
    }

    private static void extra(ObjectArrayList<ItemStack> loot, net.minecraft.util.RandomSource r) {
        if (r.nextFloat() < 0.12f) loot.add(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 1 + r.nextInt(3)));
        if (r.nextFloat() < 0.10f) loot.add(new ItemStack(br.com.frxcode.frxworld.blocos.Blocos.TOMO_ARCANO.get()));
        if (r.nextFloat() < 0.06f) loot.add(new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP));
        if (r.nextFloat() < 0.10f) loot.add(new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 4 + r.nextInt(8)));
        if (r.nextFloat() < 0.03f) loot.add(new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE));
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        net.minecraft.resources.ResourceLocation id = ctx.getQueriedLootTableId();
        String tabela = id == null ? "" : id.getPath();
        if (tabela.startsWith("blocks/")) return loot;
        int[] pesos = tabela.startsWith("entities/") ? Armas.MOB : Armas.BAU;
        boolean masmorra = deMasmorra(id);
        if (masmorra) {
            pesos = MASMORRA;
            extra(loot, ctx.getRandom());
        } else if (id != null && id.getNamespace().equals("frxworld") && tabela.startsWith("chests/reino_")) {
            try { pesos = REINO[Math.max(0, Math.min(4, Integer.parseInt(tabela.substring(13)) - 1))]; } catch (Exception ignorado) {}
        }
        for (ItemStack s : loot) {
            if (Armas.forjavel(s) && !(s.hasTag() && s.getTag().contains(Armas.RARIDADE)))
                Armas.definirRaridade(s, Armas.sortear(pesos, ctx.getRandom()));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() { return CODEC.get(); }
}
