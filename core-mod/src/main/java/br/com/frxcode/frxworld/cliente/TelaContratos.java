package br.com.frxcode.frxworld.cliente;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Quadro de contratos da Guilda: os três do dia, o extra por fechar os três, o da semana e a coleção. Só mostra: os contratos contam sozinhos. */
public class TelaContratos extends Screen {
    private static final int LARG = 372, ALT = 244, COR_TRILHO = 0xFFCFC2A4;

    private CompoundTag d;
    private int x0, y0;

    public TelaContratos(CompoundTag d) {
        super(Component.literal("Contratos da Guilda"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private void direita(GuiGraphics g, String t, int xFim, int y, int cor) { g.drawString(font, t, xFim - font.width(t), y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }

    private static String falta(int minutos) {
        if (minutos >= 1440) return minutos / 1440 + " d " + (minutos % 1440) / 60 + " h";
        if (minutos >= 60) return minutos / 60 + " h " + minutos % 60 + " min";
        return minutos + " min";
    }

    private void barra(GuiGraphics g, int x, int y, int larg, int prog, int alvo, boolean pago) {
        g.fill(x, y, x + larg, y + 6, COR_TRILHO);
        int cheio = alvo <= 0 ? 0 : Math.max(0, Math.min(larg, (int) ((long) larg * prog / alvo)));
        g.fill(x, y, x + cheio, y + 6, pago ? VERDE : OURO);
    }

    private void icone(GuiGraphics g, CompoundTag c, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.renderItem(ItemStack.of(c.getCompound("item")), 0, 0);
        g.pose().popPose();
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, "Contratos da Guilda · Rank " + d.getString("rank"), x0 + LARG / 2, y0 + 8, MOLDURA);
        centro(g, "Contam sozinhos enquanto você joga. A recompensa cai na hora.", x0 + LARG / 2, y0 + 20, CINZA);

        // os três do dia
        g.drawString(font, "Hoje", x0 + 10, y0 + 36, MOLDURA, false);
        direita(g, "renovam em " + falta(d.getInt("faltaDia")), x0 + LARG - 10, y0 + 36, CINZA);
        ListTag dia = d.getList("dia", Tag.TAG_COMPOUND);
        for (int i = 0; i < dia.size() && i < 3; i++) {
            CompoundTag c = dia.getCompound(i);
            int x = x0 + 10 + i * 119, y = y0 + 48, l = 114;
            boolean pago = c.getBoolean("pago");
            g.fill(x, y, x + l, y + 84, pago ? VERDE : PERGAMINHO_ESC);
            g.fill(x + 1, y + 1, x + l - 1, y + 83, CAIXA);
            icone(g, c, x + 6, y + 5);
            direita(g, pago ? "✔ cumprido" : c.getInt("prog") + " / " + c.getInt("alvo"), x + l - 6, y + 12, pago ? VERDE : TINTA);
            List<FormattedCharSequence> linhas = font.split(Component.literal(c.getString("texto")), l - 12);
            for (int k = 0; k < linhas.size() && k < 2; k++) g.drawString(font, linhas.get(k), x + 6, y + 32 + k * 10, MOLDURA, false);
            barra(g, x + 6, y + 56, l - 12, c.getInt("prog"), c.getInt("alvo"), pago);
            moedas(g, font, x + 6, y + 69, c.getLong("moedas"), pago ? CINZA : TINTA);
            direita(g, "+" + c.getLong("xp") + " XP", x + l - 6, y + 69, pago ? CINZA : TINTA);
        }

        // extra por fechar os três
        boolean bonus = d.getBoolean("bonus");
        int yb = y0 + 138;
        g.fill(x0 + 10, yb, x0 + LARG - 10, yb + 22, bonus ? VERDE : PERGAMINHO_ESC);
        g.fill(x0 + 11, yb + 1, x0 + LARG - 11, yb + 21, CAIXA);
        g.drawString(font, bonus ? "✔ Extra do dia pago" : "Extra por fechar os três: " + d.getInt("feitos") + " de 3", x0 + 16, yb + 7, bonus ? VERDE : MOLDURA, false);
        direita(g, "+" + d.getLong("bonusXp") + " XP", x0 + LARG - 16, yb + 7, bonus ? CINZA : TINTA);
        moedas(g, font, x0 + LARG - 150, yb + 7, d.getLong("bonusMoedas"), bonus ? CINZA : TINTA);

        // o da semana
        g.drawString(font, "Esta semana", x0 + 10, y0 + 168, MOLDURA, false);
        direita(g, "renova em " + falta(d.getInt("faltaSemana")), x0 + LARG - 10, y0 + 168, CINZA);
        CompoundTag s = d.getCompound("semana");
        boolean pago = s.getBoolean("pago");
        int ys = y0 + 180;
        g.fill(x0 + 10, ys, x0 + LARG - 10, ys + 34, pago ? VERDE : OURO);
        g.fill(x0 + 11, ys + 1, x0 + LARG - 11, ys + 33, CAIXA);
        icone(g, s, x0 + 16, ys + 5);
        g.drawString(font, corta(s.getString("texto"), 170), x0 + 46, ys + 5, MOLDURA, false);
        direita(g, pago ? "✔ cumprido" : s.getInt("prog") + " / " + s.getInt("alvo"), x0 + LARG - 16, ys + 5, pago ? VERDE : TINTA);
        barra(g, x0 + 46, ys + 19, 170, s.getInt("prog"), s.getInt("alvo"), pago);
        moedas(g, font, x0 + 224, ys + 18, s.getLong("moedas"), pago ? CINZA : TINTA);
        direita(g, "+" + s.getLong("xp") + " XP", x0 + LARG - 16, ys + 18, pago ? CINZA : TINTA);

        // coleção
        ItemStack colecao = ItemStack.of(d.getCompound("colecao"));
        int yc = y0 + ALT - 22;
        boolean em = mx >= x0 + 10 && mx < x0 + LARG - 10 && my >= yc - 2 && my < yc + 18;
        g.renderItem(colecao, x0 + 12, yc);
        g.drawString(font, corta(colecao.getHoverName().getString(), LARG - 150), x0 + 34, yc + 4, TINTA, false);
        direita(g, "passe o mouse para ver", x0 + LARG - 12, yc + 4, CINZA);
        super.render(g, mx, my, pt);
        if (em && !colecao.isEmpty()) g.renderTooltip(font, colecao, mx, my);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
