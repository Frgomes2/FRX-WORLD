package br.com.frxcode.frxworld.cliente;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Conta do site: um pergaminho com o código de 6 números, grande, e os três passos para usar no site. */
public class TelaCodigoSite extends Screen {
    private static final int LARG = 280, ALT = 190;

    private CompoundTag d;
    private int x0, y0;

    public TelaCodigoSite(CompoundTag d) {
        super(Component.literal("Conta do site"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(x0 + LARG / 2 - 50, y0 + ALT - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, "Conta do site", x0 + LARG / 2, y0 + 8, MOLDURA);
        centro(g, d.getString("site"), x0 + LARG / 2, y0 + 20, CINZA);
        String codigo = d.getString("codigo");
        cartao(g, x0 + 30, y0 + 36, LARG - 60, 50);
        if (codigo.isEmpty()) {
            centro(g, "Você acabou de pedir um código.", x0 + LARG / 2, y0 + 48, TINTA);
            centro(g, "Espere " + d.getInt("espera") + " s e aperte a tecla de novo.", x0 + LARG / 2, y0 + 62, VERMELHO);
        } else {
            String espacado = String.join(" ", codigo.split(""));
            g.pose().pushPose();
            g.pose().translate(x0 + LARG / 2f, y0 + 46, 0);
            g.pose().scale(3f, 3f, 1f);
            g.drawString(font, espacado, -font.width(espacado) / 2, 0, OURO, false);
            g.pose().popPose();
            centro(g, "vale por " + d.getInt("minutos") + " minutos, uma vez só", x0 + LARG / 2, y0 + 74, CINZA);
        }
        int y = y0 + 96;
        g.drawString(font, "1. Abra o site e clique em Minha conta.", x0 + 16, y, TINTA, false);
        g.drawString(font, "2. Escolha Primeiro acesso.", x0 + 16, y + 12, TINTA, false);
        g.drawString(font, "3. Digite o nick " + d.getString("nick") + ", este código", x0 + 16, y + 24, TINTA, false);
        g.drawString(font, "   e a senha que você quer usar.", x0 + 16, y + 36, TINTA, false);
        centro(g, "Não passe este código para ninguém.", x0 + LARG / 2, y + 52, VERMELHO);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
