package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Banco: saldo do cofre e da bolsa, depositar, sacar e câmbio de moedas. */
public class TelaBanco extends Screen {
    private static final int LARG = 300, ALT = 220;
    private static final String[] NOMES = {"Ouro", "Prata", "Cobre"};
    private static final long[] VALOR = {100, 10, 1};

    private CompoundTag d;
    private int aba = 0;                 // 0 depositar, 1 sacar, 2 câmbio
    private final long[] qtd = new long[3];
    private int x0, y0;

    public TelaBanco(CompoundTag d) {
        super(Component.literal("Banco"));
        this.d = d;
        if (d.getBoolean("soCambio")) aba = 2;
    }

    public void atualizar(CompoundTag novo) {
        this.d = novo;
        qtd[0] = qtd[1] = qtd[2] = 0;
        rebuildWidgets();
    }

    private long total() { return qtd[0] * 100 + qtd[1] * 10 + qtd[2]; }
    private long limite() { return aba == 0 ? d.getLong("bolsa") : d.getLong("saldo"); }

    private void enviar(int acao, long valor) { Rede.CANAL.sendToServer(new AcaoMenu("banco", d.getInt("npc"), acao, valor)); }

    private void somar(int i, long n) {
        long novo = Math.max(0, qtd[i] + n);
        long resto = limite() - (total() - qtd[i] * VALOR[i]);
        qtd[i] = Math.min(novo, Math.max(0, resto / VALOR[i]));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        boolean soCambio = d.getBoolean("soCambio");
        String[] abas = {"Depositar", "Sacar", "Câmbio"};
        if (!soCambio) for (int i = 0; i < 3; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(abas[i]), b -> { aba = idx; qtd[0] = qtd[1] = qtd[2] = 0; rebuildWidgets(); })
                    .bounds(x0 + 10 + i * 94, y0 + 66, 90, 18).build()).active = aba != i;
        }
        int y = y0 + 92;
        if (aba < 2) {
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                int ly = y + i * 22;
                addRenderableWidget(Button.builder(Component.literal("-"), b -> somar(idx, hasShiftDown() ? -10 : -1)).bounds(x0 + 150, ly, 18, 18).build()).active = qtd[i] > 0;
                addRenderableWidget(Button.builder(Component.literal("+"), b -> somar(idx, hasShiftDown() ? 10 : 1)).bounds(x0 + 212, ly, 18, 18).build())
                        .active = total() + VALOR[i] <= limite();
                addRenderableWidget(Button.builder(Component.literal("máx"), b -> somar(idx, 1_000_000)).bounds(x0 + 234, ly, 30, 18).build())
                        .active = total() + VALOR[i] <= limite();
            }
            String verbo = aba == 0 ? "Depositar" : "Sacar";
            addRenderableWidget(Button.builder(Component.literal(verbo), b -> enviar(aba == 0 ? AcaoMenu.DEPOSITAR : AcaoMenu.SACAR, total()))
                    .bounds(x0 + 10, y0 + ALT - 30, 136, 20).build()).active = total() > 0;
            addRenderableWidget(Button.builder(Component.literal(verbo + " tudo"), b -> enviar(aba == 0 ? AcaoMenu.DEPOSITAR : AcaoMenu.SACAR, limite()))
                    .bounds(x0 + 154, y0 + ALT - 30, 136, 20).build()).active = limite() > 0;
        } else {
            int[] tem = {d.getInt("ouro"), d.getInt("prata"), d.getInt("cobre")};
            String[] rot = {"1 ouro  →  10 prata", "10 prata  →  1 ouro", "1 prata  →  10 cobre", "10 cobre  →  1 prata"};
            int[] de = {0, 1, 1, 2}, precisa = {1, 10, 1, 10};
            for (int i = 0; i < 4; i++) {
                final int tipo = i;
                int bx = x0 + 10 + (i % 2) * 142, by = y + (i / 2) * 24;
                addRenderableWidget(Button.builder(Component.literal(rot[i]), b -> enviar(AcaoMenu.CAMBIO, tipo | ((hasShiftDown() ? 10L : 1L) << 4)))
                        .bounds(bx, by, 138, 20).build()).active = tem[de[i]] >= precisa[i];
            }
            addRenderableWidget(Button.builder(Component.literal("Juntar tudo nas moedas maiores"), b -> enviar(AcaoMenu.JUNTAR, 0))
                    .bounds(x0 + 10, y + 52, 280, 20).build()).active = d.getLong("bolsa") > 0;
        }
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, d.getString("nome"), x0 + LARG / 2, y0 + 8, MOLDURA);

        // cartões de saldo
        cartao(g, x0 + 10, y0 + 24, 136, 36);
        cartao(g, x0 + 154, y0 + 24, 136, 36);
        g.drawString(font, "No cofre", x0 + 16, y0 + 29, CINZA, false);
        g.drawString(font, "Na bolsa", x0 + 160, y0 + 29, CINZA, false);
        moedas(g, font, x0 + 16, y0 + 43, d.getLong("saldo"), TINTA);
        moedas(g, font, x0 + 160, y0 + 43, d.getLong("bolsa"), TINTA);

        int y = y0 + 92;
        if (aba < 2) {
            ItemStack[] icones = {new ItemStack(Moedas.OURO.get()), new ItemStack(Moedas.PRATA.get()), new ItemStack(Moedas.COBRE.get())};
            for (int i = 0; i < 3; i++) {
                int ly = y + i * 22;
                g.renderItem(icones[i], x0 + 14, ly + 1);
                g.drawString(font, NOMES[i], x0 + 36, ly + 5, TINTA, false);
                g.fill(x0 + 170, ly, x0 + 210, ly + 18, PERGAMINHO_ESC);
                centro(g, String.valueOf(qtd[i]), x0 + 190, ly + 5, TINTA);
            }
            g.drawString(font, (aba == 0 ? "Vai para o cofre: " : "Vai para a bolsa: "), x0 + 14, y + 70, CINZA, false);
            moedas(g, font, x0 + 14 + font.width(aba == 0 ? "Vai para o cofre: " : "Vai para a bolsa: "), y + 70, total(), TINTA);
            g.drawString(font, "Shift = de 10 em 10", x0 + LARG - 14 - font.width("Shift = de 10 em 10"), y + 70, CINZA, false);
        } else {
            g.drawString(font, "Sem taxa. Segure Shift para trocar 10 de uma vez.", x0 + 14, y + 80, CINZA, false);
        }
        String msg = d.getString("msg");
        int ym = y0 + ALT - (aba == 2 ? 18 : 44);
        if (!msg.isEmpty()) centro(g, msg, x0 + LARG / 2, ym, d.getBoolean("ok") ? VERDE : VERMELHO);
        else if (aba < 2) centro(g, "Moedas no cofre não se perdem na morte.", x0 + LARG / 2, ym, CINZA);
        super.render(g, mx, my, pt);
    }

    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }

    @Override
    public boolean isPauseScreen() { return false; }
}
