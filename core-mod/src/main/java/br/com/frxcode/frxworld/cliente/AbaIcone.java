package br.com.frxcode.frxworld.cliente;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntSupplier;

/**
 * Aba com ícone (estilo das abas do inventário criativo), presa ao topo de um painel.
 * A posição vem de suppliers, para acompanhar o painel quando ele se move (ex.: livro de receitas no inventário).
 */
public class AbaIcone extends AbstractButton {
    public static final int LARG = 22, ALT = 24;

    private final String id;
    private final ItemStack icone;
    private final boolean atual, escuro;
    private final IntSupplier px, py;

    public AbaIcone(String id, String nome, ItemStack icone, boolean atual, boolean escuro, IntSupplier px, IntSupplier py) {
        super(px.getAsInt(), py.getAsInt(), LARG, ALT, Component.literal(nome));
        this.id = id;
        this.icone = icone;
        this.atual = atual;
        this.escuro = escuro;
        this.px = px;
        this.py = py;
        setTooltip(Tooltip.create(Component.literal(nome)));
    }

    @Override
    public void onPress() {
        if (!atual) TelaStatus.abrir(id);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        setX(px.getAsInt());
        setY(py.getAsInt());
        int x = getX(), y = getY(), w = width, h = ALT + (atual ? 3 : 0);
        int top = atual ? y : y + 3;   // abas de trás ficam 3 px mais baixas
        int fundo, claro, escuroBorda;
        if (escuro) {   // tema das telas FRX (marrom e dourado, igual à tela de Habilidades)
            fundo = atual ? 0xFF17140F : isHovered ? 0xFF2E2419 : 0xFF221B14;
            claro = atual ? 0xFFD4A93A : 0xFF5A3A1E;
            escuroBorda = 0xFF0F0D0B;
        } else {
            fundo = atual ? 0xFFC6C6C6 : isHovered ? 0xFFB0B0B0 : 0xFF8B8B8B;
            claro = 0xFFFFFFFF;
            escuroBorda = 0xFF373737;
        }
        int base = y + h;
        g.fill(x, top, x + w, base, escuroBorda);                 // contorno
        g.fill(x + 1, top + 1, x + w - 1, base, claro);           // luz em cima/esquerda
        g.fill(x + 2, top + 2, x + w - 1, base, fundo);           // miolo
        g.fill(x + w - 2, top + 2, x + w - 1, base - (atual ? 0 : 1), escuro ? 0xFF3A2614 : 0xFF555555);   // sombra à direita
        if (atual) g.fill(x + 1, base - 3, x + w - 1, base, fundo);   // emenda com o painel
        g.renderItem(icone, x + 3, top + 5);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput saida) {
        defaultButtonNarrationText(saida);
    }
}
