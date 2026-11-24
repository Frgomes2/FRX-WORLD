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
 * Peças de armadura próprias do servidor, para servir de item base de relíquias do Códex com visual único quando vestidas.
 * Não têm receita nem aparecem nas abas do criativo: só chegam ao jogo pelo Códex (prêmios de enigma, missões...).
 * O nome e o poder vêm do Códex; a textura vestida fica em assets/frxworld/textures/models/armor/<material>_layer_1/2.png.
 */
public class Reliquias {
    private static final DeferredRegister<Item> ITENS = DeferredRegister.create(ForgeRegistries.ITEMS, FrxWorld.MOD_ID);

    /** Cristal do Alvorecer: proteção de diamante e resistência de netherite, inquebrável: não tem durabilidade. */
    public static final ArmorMaterial ALVORECER = new ArmorMaterial() {
        private final int[] defesa = {3, 6, 8, 3};   // botas, perneiras, peitoral, elmo
        /** 0 = sem durabilidade: a relíquia nunca desgasta (peças já gastas voltam inteiras, porque deixam de ter barra). */
        @Override public int getDurabilityForType(ArmorItem.Type t) { return 0; }
        @Override public int getDefenseForType(ArmorItem.Type t) {
            return switch (t) { case HELMET -> defesa[3]; case CHESTPLATE -> defesa[2]; case LEGGINGS -> defesa[1]; case BOOTS -> defesa[0]; };
        }
        @Override public int getEnchantmentValue() { return 18; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.AMETHYST_BLOCK_CHIME; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return FrxWorld.MOD_ID + ":alvorecer"; }
        @Override public float getToughness() { return 2.5f; }
        @Override public float getKnockbackResistance() { return 0.05f; }
    };

    private static RegistryObject<Item> peca(String nome, ArmorItem.Type tipo) {
        return ITENS.register(nome, () -> new ArmorItem(ALVORECER, tipo, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    }

    public static final RegistryObject<Item> ELMO = peca("alvorecer_elmo", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> PEITORAL = peca("alvorecer_peitoral", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> PERNEIRAS = peca("alvorecer_perneiras", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> BOTAS = peca("alvorecer_botas", ArmorItem.Type.BOOTS);

    private Reliquias() {}

    public static void registrar(IEventBus modBus) { ITENS.register(modBus); }
}
