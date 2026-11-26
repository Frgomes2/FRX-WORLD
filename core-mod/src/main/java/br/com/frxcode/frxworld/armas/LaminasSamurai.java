package br.com.frxcode.frxworld.armas;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Katanas do Samurai Dynasty (Epic Samurais) mais fortes: no mod original elas batem como uma espada de netherite,
 * fracas para o FRX-WORLD. Soma dano fixo na mão principal (aparece na descrição do item). A raridade e a forja
 * continuam calculadas sobre o dano original da arma.
 */
public class LaminasSamurai {
    /** Dano a mais por arma, somado ao do mod original (material de aço ou netherite com +5 de dano). */
    private static final Map<String, Double> BONUS = Map.ofEntries(
            Map.entry("katana", 3.0),
            Map.entry("katana_netherite", 4.0),
            Map.entry("katana_oni", 5.0),
            Map.entry("katana_kitsune", 5.0),
            Map.entry("katana_kitsune_blue", 5.0),
            Map.entry("katana_akaname", 5.0),
            Map.entry("katana_jorogumo", 5.0),
            Map.entry("wakizashi", 2.0),
            Map.entry("wakizashi_netherite", 3.0),
            Map.entry("odachi", 3.0),
            Map.entry("odachi_netherite", 4.0));
    private static final UUID ID = UUID.nameUUIDFromBytes("frxworld:lamina_samurai".getBytes(StandardCharsets.UTF_8));

    /**
     * Armaduras do Samurai Dynasty: bonitas, mas com proteção de ferro a diamante. Bônus por peça, pelo material:
     * {armadura, resistência, vida (meio coração = 1), resistência a repulsão}.
     */
    private static final double[] COMUM = {1, 0.5, 0, 0};        // ferro, ouro, aço, chapéu de palha
    private static final double[] JOIA = {2, 1.5, 1, 0};         // diamante e as de pedra preciosa
    private static final double[] SUPREMA = {3, 2.5, 2, 0.05};   // netherite, Battlemage e Warden
    private static final Map<String, double[]> MATERIAL = Map.ofEntries(
            Map.entry("iron", COMUM), Map.entry("gold", COMUM), Map.entry("steel", COMUM), Map.entry("straw", COMUM),
            Map.entry("diamond", JOIA), Map.entry("red", JOIA), Map.entry("green", JOIA), Map.entry("blue", JOIA),
            Map.entry("white", JOIA), Map.entry("gray", JOIA), Map.entry("amethyst", JOIA), Map.entry("quartz", JOIA),
            Map.entry("netherite", SUPREMA), Map.entry("mage", SUPREMA), Map.entry("sculk", SUPREMA));

    private static UUID id(String nome, EquipmentSlot slot) {
        return UUID.nameUUIDFromBytes(("frxworld:armadura_samurai_" + nome + "_" + slot.getName()).getBytes(StandardCharsets.UTF_8));
    }

    @SubscribeEvent
    public void armaduras(ItemAttributeModifierEvent e) {
        if (!(e.getItemStack().getItem() instanceof ArmorItem a) || a.getEquipmentSlot() != e.getSlotType()) return;
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(a);
        if (item == null || !item.getNamespace().equals("samurai_dynasty")) return;
        String nome = item.getPath().startsWith("mask_") ? item.getPath().substring(5) : item.getPath();
        double[] b = MATERIAL.get(nome.split("_")[0]);
        if (b == null) return;
        EquipmentSlot slot = e.getSlotType();
        if (b[0] > 0) e.addModifier(Attributes.ARMOR, new AttributeModifier(id("armadura", slot), "FRX armadura samurai", b[0], AttributeModifier.Operation.ADDITION));
        if (b[1] > 0) e.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id("resistencia", slot), "FRX armadura samurai", b[1], AttributeModifier.Operation.ADDITION));
        if (b[2] > 0) e.addModifier(Attributes.MAX_HEALTH, new AttributeModifier(id("vida", slot), "FRX armadura samurai", b[2], AttributeModifier.Operation.ADDITION));
        if (b[3] > 0) e.addModifier(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id("repulsao", slot), "FRX armadura samurai", b[3], AttributeModifier.Operation.ADDITION));
    }

    @SubscribeEvent
    public void atributos(ItemAttributeModifierEvent e) {
        if (e.getSlotType() != EquipmentSlot.MAINHAND) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem());
        if (id == null || !id.getNamespace().equals("samurai_dynasty")) return;
        Double bonus = BONUS.get(id.getPath());
        if (bonus == null) return;
        e.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(ID, "FRX lâmina samurai", bonus, AttributeModifier.Operation.ADDITION));
    }
}
