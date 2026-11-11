package br.com.frxcode.frxworld.efeitos;

import br.com.frxcode.frxworld.progressao.ConfigJogo;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Ferido: depois de morrer, menos vida máxima e menos dano por alguns minutos (valores do painel). */
public class EfeitoFerido extends MobEffect {
    private static final String UUID_VIDA = "6f1d1c3e-6c7e-4a52-9a0c-1f2b3c4d5e01";
    private static final String UUID_DANO = "6f1d1c3e-6c7e-4a52-9a0c-1f2b3c4d5e02";

    public EfeitoFerido() {
        super(MobEffectCategory.HARMFUL, 0x8B1A1A);
        addAttributeModifier(Attributes.MAX_HEALTH, UUID_VIDA, -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, UUID_DANO, -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplificador, AttributeModifier modificador) {
        String id = modificador.getId().toString();
        if (UUID_VIDA.equals(id)) return -ConfigJogo.feridoVidaPct() / 100.0;
        if (UUID_DANO.equals(id)) return -ConfigJogo.feridoDanoPct() / 100.0;
        return super.getAttributeModifierValue(amplificador, modificador);
    }
}
