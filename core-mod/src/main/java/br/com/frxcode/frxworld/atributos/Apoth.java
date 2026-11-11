package br.com.frxcode.frxworld.atributos;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Ponte com o Apothic Attributes (id "attributeslib" no 1.20.1). Sem o mod, tudo aqui devolve "inativo"
 * e os sistemas do FRX usam a lógica própria (crítico, esquiva, roubo de vida...) como antes.
 * Com o mod, viram atributos reais: aparecem na tela de atributos e na dica dos itens, e somam com o que outros mods derem.
 */
public final class Apoth {
    private Apoth() {}

    public static final String CRIT_CHANCE = "crit_chance", CRIT_DAMAGE = "crit_damage", LIFE_STEAL = "life_steal",
            DODGE = "dodge_chance", ARMOR_PIERCE = "armor_pierce", ARROW_DAMAGE = "arrow_damage", DRAW_SPEED = "draw_speed",
            HEALING = "healing_received", MINING = "mining_speed";

    private static Boolean ativo;

    public static boolean ativo() {
        if (ativo == null) ativo = ModList.get() != null && ModList.get().isLoaded("attributeslib");
        return ativo;
    }

    public static Attribute attr(String nome) {
        if (!ativo()) return null;
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("attributeslib", nome));
    }

    public static UUID id(String nome) {
        return UUID.nameUUIDFromBytes(("frxworld:apoth:" + nome).getBytes(StandardCharsets.UTF_8));
    }

    /** Define (ou tira, se valor = 0) um modificador temporário num atributo do Apothic. */
    public static void definir(LivingEntity e, String atributo, String chave, double valor) {
        Attribute a = attr(atributo);
        if (a == null) return;
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null) return;
        UUID u = id(chave);
        inst.removeModifier(u);
        if (Math.abs(valor) > 1e-6) inst.addTransientModifier(new AttributeModifier(u, "FRX " + chave, valor, AttributeModifier.Operation.ADDITION));
    }
}
