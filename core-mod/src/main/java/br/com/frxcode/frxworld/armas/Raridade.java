package br.com.frxcode.frxworld.armas;

import net.minecraft.ChatFormatting;

/** Raridade de armas, ferramentas e armaduras: dano extra (fração), e por peça de armadura: armadura, resistência e vida. */
public enum Raridade {
    COMUM("Comum", ChatFormatting.WHITE, 0.00, 0.0, 0.0, 0.0),
    INCOMUM("Incomum", ChatFormatting.GREEN, 0.08, 0.5, 0.0, 0.0),
    RARO("Raro", ChatFormatting.AQUA, 0.15, 1.0, 0.5, 0.0),
    EPICO("Épico", ChatFormatting.LIGHT_PURPLE, 0.25, 1.5, 1.0, 1.0),
    LENDARIO("Lendário", ChatFormatting.GOLD, 0.40, 2.0, 1.5, 2.0);

    public final String nome;
    public final ChatFormatting cor;
    public final double dano, armadura, resistencia, vida;

    Raridade(String nome, ChatFormatting cor, double dano, double armadura, double resistencia, double vida) {
        this.nome = nome; this.cor = cor; this.dano = dano; this.armadura = armadura; this.resistencia = resistencia; this.vida = vida;
    }

    public static Raridade de(int i) {
        Raridade[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
