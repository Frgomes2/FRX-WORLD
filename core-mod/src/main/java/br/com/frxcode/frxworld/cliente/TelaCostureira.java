package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.reinos.Costureira;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Costureira: mostra a calça, os 12 lugares de bolso (os já costurados em ouro) e o preço para costurar mais. */
public class TelaCostureira extends Screen {
    private static final int LARG = 300, ALT = 196, CASA = 22;

    private CompoundTag d;
    private ItemStack calca = ItemStack.EMPTY;
    private int x0, y0;

    public TelaCostureira(CompoundTag d) {
        super(Component.literal("Costureira"));
        ler(d);
    }

    private void ler(CompoundTag novo) {
        this.d = novo;
        this.calca = novo.contains("item") ? ItemStack.of(novo.getCompound("item")) : ItemStack.EMPTY;
    }

    public void atualizar(CompoundTag novo) { ler(novo); rebuildWidgets(); }

    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }
    private int tem() { return d.getInt("bolsos"); }
    private int faltam() { return d.getBoolean("temCalca") ? Math.max(0, d.getInt("maximo") - tem()) : 0; }
    private long preco() { return d.getLong("preco"); }
    /** Quantos bolsos o dinheiro da bolsa paga, até completar a calça. */
    private int paga() { return preco() <= 0 ? faltam() : (int) Math.min(faltam(), d.getLong("bolsa") / preco()); }

    private void costurar(int qtd) { Rede.CANAL.sendToServer(new AcaoMenu("costureira", d.getInt("npc"), Costureira.COSTURAR, qtd)); }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Costurar 1 bolso"), b -> costurar(1))
                .bounds(x0 + 12, y0 + ALT - 44, 134, 20).build()).active = paga() >= 1;
        final int todos = paga();
        addRenderableWidget(Button.builder(Component.literal(todos > 1 ? "Costurar " + todos + " bolsos" : "Costurar todos"), b -> costurar(todos))
                .bounds(x0 + LARG - 146, y0 + ALT - 44, 134, 20).build()).active = todos > 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, d.getString("nome"), x0 + LARG / 2, y0 + 8, MOLDURA);
        g.drawString(font, "Na sua bolsa:", x0 + 12, y0 + 26, CINZA, false);
        moedas(g, font, x0 + 12 + font.width("Na sua bolsa: "), y0 + 26, d.getLong("bolsa"), TINTA);

        // a calça, grande, num cartão à esquerda
        int cy = y0 + 42;
        cartao(g, x0 + 12, cy, 76, 92);
        if (!calca.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x0 + 12 + 14, cy + 10, 0);
            g.pose().scale(3f, 3f, 1f);
            g.renderItem(calca, 0, 0);
            g.pose().popPose();
            centro(g, corta(calca.getHoverName().getString(), 70), x0 + 50, cy + 64, MOLDURA);
            centro(g, d.getBoolean("naMao") ? "na sua mão" : "vestida", x0 + 50, cy + 76, CINZA);
        } else {
            centro(g, "Sem calça", x0 + 50, cy + 36, VERMELHO);
        }

        // os 12 lugares de bolso
        int bx = x0 + 100, by = cy;
        cartao(g, bx, by, LARG - 112, 92);
        if (d.getBoolean("temCalca")) {
            g.drawString(font, "Bolsos: " + tem() + " de " + d.getInt("maximo"), bx + 8, by + 6, MOLDURA, false);
            int gx = bx + 8, gy = by + 20;
            for (int i = 0; i < d.getInt("maximo"); i++) {
                int x = gx + (i % 6) * (CASA + 4), y = gy + (i / 6) * (CASA + 4);
                boolean feito = i < tem(), proximo = i == tem();
                g.fill(x, y, x + CASA, y + CASA, feito ? OURO : proximo ? MOLDURA : PERGAMINHO_ESC);
                g.fill(x + 1, y + 1, x + CASA - 1, y + CASA - 1, feito ? 0xFFF3E2B0 : CAIXA);
                if (feito) {                                   // o bolso: uma aba e a costura
                    g.fill(x + 5, y + 6, x + CASA - 5, y + 8, OURO);
                    g.fill(x + 5, y + 8, x + 6, y + CASA - 5, OURO);
                    g.fill(x + CASA - 6, y + 8, x + CASA - 5, y + CASA - 5, OURO);
                    g.fill(x + 5, y + CASA - 6, x + CASA - 5, y + CASA - 5, OURO);
                } else {
                    centro(g, "+", x + CASA / 2 + 1, y + 7, proximo ? MOLDURA : PERGAMINHO_ESC);
                }
            }
            g.drawString(font, "Cada bolso:", bx + 8, by + 78, TINTA, false);
            moedas(g, font, bx + 8 + font.width("Cada bolso: "), by + 78, preco(), TINTA);
        } else {
            g.drawString(font, "Traga uma calça para eu costurar.", bx + 8, by + 22, TINTA, false);
            g.drawString(font, "Segure a calça na mão,", bx + 8, by + 40, CINZA, false);
            g.drawString(font, "ou venha vestindo uma.", bx + 8, by + 51, CINZA, false);
        }

        String dica;
        int cor = CINZA;
        if (!d.getBoolean("temCalca")) dica = "Cada bolso é um espaço a mais para guardar itens.";
        else if (faltam() == 0) dica = "Essa calça já tem todos os bolsos.";
        else if (paga() == 0) { dica = "Faltam moedas na bolsa para mais um bolso."; cor = VERMELHO; }
        else dica = "Vestindo a calça, abra os bolsos pelo inventário ou pela tecla dos bolsos.";
        centro(g, corta(dica, LARG - 20), x0 + LARG / 2, y0 + 140, cor);

        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, corta(msg, LARG - 20), x0 + LARG / 2, y0 + ALT - 16, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
