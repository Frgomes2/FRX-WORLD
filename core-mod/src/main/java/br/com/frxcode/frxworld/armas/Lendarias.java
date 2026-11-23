package br.com.frxcode.frxworld.armas;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Armaduras depois do netherite, cada uma para um rank da Guilda (a classe sai da defesa e da resistência):
 * Draconiana (B) ← netherite + Escama Draconiana; Abissal (A) ← Draconiana + Essência Abissal; Celestial (S) ← Abissal + Fragmento Celestial.
 * Feitas na mesa de ferraria (material como molde e como acréscimo). O bônus de conjunto fica em aventura/ConjuntosLendarios.
 */
public class Lendarias {
    private static final DeferredRegister<Item> ITENS = DeferredRegister.create(ForgeRegistries.ITEMS, FrxWorld.MOD_ID);

    /** botas, perneiras, peitoral, elmo */
    private static ArmorMaterial material(String nome, int[] defesa, float resistencia, float empurrao, int encantar, SoundEvent som) {
        return new ArmorMaterial() {
            @Override public int getDurabilityForType(ArmorItem.Type t) {
                return 45 * switch (t) { case HELMET -> 11; case CHESTPLATE -> 16; case LEGGINGS -> 15; case BOOTS -> 13; };
            }
            @Override public int getDefenseForType(ArmorItem.Type t) {
                return switch (t) { case HELMET -> defesa[3]; case CHESTPLATE -> defesa[2]; case LEGGINGS -> defesa[1]; case BOOTS -> defesa[0]; };
            }
            @Override public int getEnchantmentValue() { return encantar; }
            @Override public SoundEvent getEquipSound() { return som; }
            @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
            @Override public String getName() { return FrxWorld.MOD_ID + ":" + nome; }
            @Override public float getToughness() { return resistencia; }
            @Override public float getKnockbackResistance() { return empurrao; }
        };
    }

    public static final ArmorMaterial DRACONIANA = material("draconiana", new int[]{3, 6, 8, 3}, 4f, 0.15f, 16, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final ArmorMaterial ABISSAL = material("abissal", new int[]{4, 7, 9, 4}, 5f, 0.2f, 18, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final ArmorMaterial CELESTIAL = material("celestial", new int[]{5, 9, 11, 5}, 6f, 0.3f, 22, SoundEvents.AMETHYST_BLOCK_CHIME);

    private static RegistryObject<Item> peca(String nome, ArmorMaterial m, ArmorItem.Type tipo, Rarity r) {
        return ITENS.register(nome, () -> new ArmorItem(m, tipo, new Item.Properties().rarity(r).fireResistant()));
    }

    public static final RegistryObject<Item> ESCAMA = ITENS.register("escama_draconiana", () -> new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> ESSENCIA = ITENS.register("essencia_abissal", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> FRAGMENTO = ITENS.register("fragmento_celestial", () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final String[] CONJUNTOS = {"draconiana", "abissal", "celestial"};
    public static final String[] PECAS = {"elmo", "peitoral", "perneiras", "botas"};

    public static final RegistryObject<Item> DRA_ELMO = peca("draconiana_elmo", DRACONIANA, ArmorItem.Type.HELMET, Rarity.RARE);
    public static final RegistryObject<Item> DRA_PEITORAL = peca("draconiana_peitoral", DRACONIANA, ArmorItem.Type.CHESTPLATE, Rarity.RARE);
    public static final RegistryObject<Item> DRA_PERNEIRAS = peca("draconiana_perneiras", DRACONIANA, ArmorItem.Type.LEGGINGS, Rarity.RARE);
    public static final RegistryObject<Item> DRA_BOTAS = peca("draconiana_botas", DRACONIANA, ArmorItem.Type.BOOTS, Rarity.RARE);
    public static final RegistryObject<Item> ABI_ELMO = peca("abissal_elmo", ABISSAL, ArmorItem.Type.HELMET, Rarity.EPIC);
    public static final RegistryObject<Item> ABI_PEITORAL = peca("abissal_peitoral", ABISSAL, ArmorItem.Type.CHESTPLATE, Rarity.EPIC);
    public static final RegistryObject<Item> ABI_PERNEIRAS = peca("abissal_perneiras", ABISSAL, ArmorItem.Type.LEGGINGS, Rarity.EPIC);
    public static final RegistryObject<Item> ABI_BOTAS = peca("abissal_botas", ABISSAL, ArmorItem.Type.BOOTS, Rarity.EPIC);
    public static final RegistryObject<Item> CEL_ELMO = peca("celestial_elmo", CELESTIAL, ArmorItem.Type.HELMET, Rarity.EPIC);
    public static final RegistryObject<Item> CEL_PEITORAL = peca("celestial_peitoral", CELESTIAL, ArmorItem.Type.CHESTPLATE, Rarity.EPIC);
    public static final RegistryObject<Item> CEL_PERNEIRAS = peca("celestial_perneiras", CELESTIAL, ArmorItem.Type.LEGGINGS, Rarity.EPIC);
    public static final RegistryObject<Item> CEL_BOTAS = peca("celestial_botas", CELESTIAL, ArmorItem.Type.BOOTS, Rarity.EPIC);

    /** Conjunto ("draconiana", "abissal", "celestial") da peça, ou null. */
    public static String conjuntoDe(net.minecraft.world.item.ItemStack s) {
        if (!(s.getItem() instanceof ArmorItem a)) return null;
        ArmorMaterial m = a.getMaterial();
        return m == DRACONIANA ? "draconiana" : m == ABISSAL ? "abissal" : m == CELESTIAL ? "celestial" : null;
    }

    private Lendarias() {}

    public static void registrar(IEventBus modBus) { ITENS.register(modBus); }
}
