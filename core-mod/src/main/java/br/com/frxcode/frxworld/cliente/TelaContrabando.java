package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.reinos.Contrabando;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/**
 * Contrabandista: o beco. Fundo quase preto com uma lanterna vermelha que oscila no canto.
 * À esquerda, três serviços do dia em bilhetes rasgados; à direita, a mercadoria num caixote de nove espaços,
 * com cadeado no que a má fama do jogador ainda não alcança.
 */
public class TelaContrabando extends Screen {
    private static final int LARG = 372, ALT = 232, BILHETE_L = 168, BILHETE_A = 46, CAIXA = 46;
    private static final int BECO = 0xFF120E12, BECO_CLARO = 0xFF1E171C, BRASA = 0xFFB3261E, PAPEL = 0xFFCDBF9F, PAPEL_ESC = 0xFF9E9175,
            CLARO = 0xFFEDE3CF, APAGADO = 0xFF7A6F68;

    private CompoundTag d;
    private ItemStack[] itens = new ItemStack[0];
    private int x0, y0, servico = -1, mercadoria = -1, tempo;

    public TelaContrabando(CompoundTag d) {
        super(Component.literal("Contrabandista"));
        ler(d);
    }

    private void ler(CompoundTag novo) {
        this.d = novo;
        ListTag l = loja();
        itens = new ItemStack[l.size()];
        for (int i = 0; i < itens.length; i++) itens[i] = ItemStack.of(l.getCompound(i).getCompound("item"));
    }

    public void atualizar(CompoundTag novo) { ler(novo); rebuildWidgets(); }

    private ListTag servicos() { return d.getList("servicos", Tag.TAG_COMPOUND); }
    private ListTag loja() { return d.getList("loja", Tag.TAG_COMPOUND); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }
    private int bx() { return x0 + 12; }
    private int by(int i) { return y0 + 44 + i * (BILHETE_A + 5); }
    private int mx0(int i) { return x0 + 196 + (i % 3) * (CAIXA + 6); }
    private int my0(int i) { return y0 + 44 + (i / 3) * (CAIXA + 5); }
    private boolean liberada(int i) { return i >= 0 && i < loja().size() && d.getInt("grau") >= loja().getCompound(i).getInt("grau"); }
    private boolean podeComprar(int i) { return liberada(i) && d.getLong("bolsa") >= loja().getCompound(i).getLong("preco"); }
    private boolean dentro(double mx, double my, int x, int y, int l, int a) { return mx >= x && mx < x + l && my >= y && my < y + a; }

    @Override
    public void tick() { tempo++; }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        if (servico >= servicos().size()) servico = -1;
        if (mercadoria >= loja().size()) mercadoria = -1;
        final int s = servico, m = mercadoria;
        if (s >= 0) {
            int estado = servicos().getCompound(s).getInt("estado");
            Button b = addRenderableWidget(Button.builder(Component.literal(estado == 2 ? "Pago" : "Receber"),
                    x -> Rede.CANAL.sendToServer(new AcaoMenu("contrabando", d.getInt("npc"), Contrabando.SERVICO, s)))
                    .bounds(x0 + LARG - 92, y0 + ALT - 30, 80, 20).build());
            b.active = estado == 1;
        } else if (m >= 0) {
            Button b = addRenderableWidget(Button.builder(Component.literal("Comprar"),
                    x -> Rede.CANAL.sendToServer(new AcaoMenu("contrabando", d.getInt("npc"), Contrabando.COMPRAR, m)))
                    .bounds(x0 + LARG - 92, y0 + ALT - 30, 80, 20).build());
            b.active = podeComprar(m);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (super.mouseClicked(mx, my, botao)) return true;
        for (int i = 0; i < servicos().size(); i++) {
            if (dentro(mx, my, bx(), by(i), BILHETE_L, BILHETE_A)) { servico = i; mercadoria = -1; rebuildWidgets(); return true; }
        }
        for (int i = 0; i < loja().size(); i++) {
            if (dentro(mx, my, mx0(i), my0(i), CAIXA, CAIXA)) { mercadoria = i; servico = -1; rebuildWidgets(); return true; }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 3, y0 - 3, x0 + LARG + 3, y0 + ALT + 3, 0xFF000000);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, BECO);
        // lanterna vermelha: brilho que oscila no canto de cima
        float luz = 0.6f + 0.25f * (float) Math.sin((tempo + pt) / 7.0) + 0.15f * (float) Math.sin((tempo + pt) / 2.3);
        for (int f = 0; f < 6; f++) {
            int a = (int) (38 * luz * (6 - f) / 6f);
            g.fill(x0, y0 + f * 6, x0 + LARG, y0 + f * 6 + 6, (a << 24) | 0xB3261E);
        }
        g.drawString(font, "☠ Contrabandista", x0 + 12, y0 + 9, BRASA, false);
        String fama = d.getString("fama");
        g.drawString(font, fama, x0 + 20 + font.width("☠ Contrabandista"), y0 + 9, APAGADO, false);
        g.drawString(font, "Bolsa:", x0 + 12, y0 + 24, APAGADO, false);
        moedas(g, font, x0 + 12 + font.width("Bolsa: "), y0 + 24, d.getLong("bolsa"), CLARO);
        g.drawString(font, "SERVIÇOS DE HOJE", x0 + 12, y0 + 34, APAGADO, false);
        g.drawString(font, "MERCADORIA", x0 + 196, y0 + 34, APAGADO, false);

        ListTag ss = servicos();
        for (int i = 0; i < ss.size(); i++) {
            CompoundTag s = ss.getCompound(i);
            int x = bx(), y = by(i), estado = s.getInt("estado");
            boolean sel = i == servico, em = dentro(mx, my, x, y, BILHETE_L, BILHETE_A);
            g.fill(x - 1, y - 1, x + BILHETE_L + 1, y + BILHETE_A + 1, sel ? BRASA : em ? PAPEL_ESC : 0xFF000000);
            g.fill(x, y, x + BILHETE_L, y + BILHETE_A, estado == 2 ? PAPEL_ESC : PAPEL);
            for (int k = 0; k < BILHETE_L; k += 8) g.fill(x + k, y + BILHETE_A - 2, x + k + 4, y + BILHETE_A, BECO);   // borda rasgada
            g.drawString(font, s.getString("titulo"), x + 5, y + 4, 0xFF3A1512, false);
            String st = estado == 2 ? "pago" : estado == 1 ? "pronto" : s.getInt("prog") + "/" + s.getInt("meta");
            g.drawString(font, st, x + BILHETE_L - 5 - font.width(st), y + 4, estado == 1 ? VERDE : 0xFF5A4A3A, false);
            g.drawString(font, corta(s.getString("texto"), BILHETE_L - 10), x + 5, y + 16, TINTA, false);
            g.drawString(font, corta(s.getString("efeito"), BILHETE_L - 10), x + 5, y + 28, VERMELHO, false);
        }

        ListTag ms = loja();
        for (int i = 0; i < ms.size(); i++) {
            int x = mx0(i), y = my0(i);
            boolean sel = i == mercadoria, em = dentro(mx, my, x, y, CAIXA, CAIXA), aberta = liberada(i);
            g.fill(x - 1, y - 1, x + CAIXA + 1, y + CAIXA + 1, sel ? BRASA : em ? 0xFF5A4038 : 0xFF2C2126);
            g.fill(x, y, x + CAIXA, y + CAIXA, BECO_CLARO);
            g.fill(x, y + CAIXA / 2, x + CAIXA, y + CAIXA / 2 + 1, 0xFF2C2126);   // ripa do caixote
            if (i < itens.length && !itens[i].isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x + CAIXA / 2f - 12, y + 6, 0);
                g.pose().scale(1.5f, 1.5f, 1f);
                g.renderItem(itens[i], 0, 0);
                g.pose().popPose();
            }
            if (!aberta) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(x, y, x + CAIXA, y + CAIXA, 0xC0120E12);
                String cad = "✖";
                g.drawString(font, cad, x + CAIXA / 2 - font.width(cad) / 2, y + 12, BRASA, false);
                String gn = ms.getCompound(i).getString("grauNome");
                g.drawString(font, gn, x + CAIXA / 2 - font.width(gn) / 2, y + CAIXA - 13, APAGADO, false);
                g.pose().popPose();
            }
        }

        int y = y0 + ALT - 36;
        g.fill(x0 + 10, y, x0 + LARG - 10, y + 1, 0xFF2C2126);
        if (mercadoria >= 0 && mercadoria < ms.size()) {
            CompoundTag m = ms.getCompound(mercadoria);
            g.drawString(font, corta(m.getString("nome"), 180), x0 + 12, y + 6, CLARO, false);
            int px = x0 + 200;
            g.drawString(font, "Preço:", px, y + 6, APAGADO, false);
            moedas(g, font, px + font.width("Preço: "), y + 6, m.getLong("preco"), podeComprar(mercadoria) ? CLARO : 0xFFE06A6A);
            g.drawString(font, corta(liberada(mercadoria) ? m.getString("nota") : "Só para quem já é " + m.getString("grauNome") + " de algum reino.", LARG - 116),
                    x0 + 12, y + 19, APAGADO, false);
        } else if (servico >= 0 && servico < ss.size()) {
            CompoundTag s = ss.getCompound(servico);
            g.drawString(font, "Pagamento:", x0 + 12, y + 6, APAGADO, false);
            moedas(g, font, x0 + 12 + font.width("Pagamento: "), y + 6, s.getLong("premio"), CLARO);
            g.drawString(font, corta(s.getString("efeito"), LARG - 116), x0 + 12, y + 19, 0xFFE06A6A, false);
        } else {
            g.drawString(font, "Escolha um serviço ou uma mercadoria.", x0 + 12, y + 6, CLARO, false);
            g.drawString(font, "Cada serviço custa reputação com um reino e agrada o rival dele.", x0 + 12, y + 19, APAGADO, false);
        }
        String msg = d.getString("msg");
        if (!msg.isEmpty()) g.drawString(font, corta(msg, LARG - 24), x0 + 12, y0 + ALT + 6, d.getBoolean("ok") ? 0xFF7BC67E : 0xFFE06A6A, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
