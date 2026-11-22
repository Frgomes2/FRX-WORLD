package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Teto da armadura: o Minecraft limita o atributo a 30. Com a fórmula do Apothic Attributes (dano que passa = a / (a + armadura),
 * a = 10 em golpes até 20 de dano), cada ponto acima de 30 ainda protege um pouco, com retorno cada vez menor.
 * O teto sobe para 100, no servidor e no cliente (para a tela de atributos mostrar o valor certo).
 */
public final class TetoAtributos {
    public static final double ARMADURA_MAX = 100;

    private TetoAtributos() {}

    public static void aplicar() {
        subir(Attributes.ARMOR, 30, ARMADURA_MAX);
    }

    /** Troca o máximo de um atributo com limite (o campo é o double que hoje vale o máximo antigo). */
    private static void subir(Attribute a, double antigo, double novo) {
        if (!(a instanceof RangedAttribute r)) return;
        try {
            for (Field f : RangedAttribute.class.getDeclaredFields()) {
                if (f.getType() != double.class || Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                if (f.getDouble(r) != antigo) continue;
                f.setDouble(r, novo);
                FrxWorld.LOG.info("Teto de {} subiu de {} para {}.", a.getDescriptionId(), antigo, novo);
                return;
            }
            FrxWorld.LOG.warn("Teto de {}: não achei o campo do máximo.", a.getDescriptionId());
        } catch (Exception e) {
            FrxWorld.LOG.warn("Teto de {}: não consegui mudar ({}).", a.getDescriptionId(), e.toString());
        }
    }
}
