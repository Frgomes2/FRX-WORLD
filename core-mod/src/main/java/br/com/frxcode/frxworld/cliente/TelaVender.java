package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Comprador: vende qualquer item da mochila pelo preço da raridade, respeitando o teto de moedas do dia. */
public class TelaVender extends Screen {
    private static final int LARG = 320, ALT = 220, POR_PAGINA = 6, LINHA = 24;

    private CompoundTag d;
    private int pagina = 0, x0, y0;
    /** "Vender tudo" pede um segundo clique: evita vender a arma boa sem querer. */
    private boolean confirmarTudo = false;

    public TelaVender(CompoundTag d) {
        super(Component.literal("Comprador"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) { this.d = novo; this.confirmarTudo = false; rebuildWidgets(); }

    private ListTag itens() { return d.getList("itens", Tag.TAG_COMPOUND); }
    private void enviar(int acao, long valor) { Rede.CANAL.sendToServer(new AcaoMenu("vender", d.getInt("npc"), acao, valor)); }
    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        ListTag lista = itens();
        boolean temTeto = d.getLong("resta") > 0;
        pagina = Math.max(0, Math.min(pagina, Math.max(0, (lista.size() - 1) / POR_PAGINA)));
        int inicio = pagina * POR_PAGINA;
        for (int i = inicio; i < Math.min(lista.size(), inicio + POR_PAGINA); i++) {
            CompoundTag o = lista.getCompound(i);
            int y = y0 + 64 + (i - inicio) * LINHA;
            addRenderableWidget(Button.builder(Component.literal("Vender"), b -> enviar(AcaoMenu.VENDER, o.getInt("slot")))
                    .bounds(x0 + LARG - 68, y + 2, 58, 18).build()).active = temTeto;
        }
        if (lista.size() > POR_PAGINA) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina--; rebuildWidgets(); })
                    .bounds(x0 + 10, y0 + ALT - 26, 20, 16).build()).active = pagina > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); })
                    .bounds(x0 + 34, y0 + ALT - 26, 20, 16).build()).active = (pagina + 1) * POR_PAGINA < lista.size();
        }
        addRenderableWidget(Button.builder(Component.literal(confirmarTudo ? "Confirmar: vender tudo" : "Vender tudo"), b -> {
            if (confirmarTudo) { confirmarTudo = false; enviar(AcaoMenu.VENDER_TUDO, 0); }
            else { confirmarTudo = true; rebuildWidgets(); }
        }).bounds(x0 + LARG - 146, y0 + ALT - 28, 136, 20).build()).active = temTeto && !lista.isEmpty();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, d.getString("nome"), x0 + LARG / 2, y0 + 8, MOLDURA);

        // teto do dia
        cartao(g, x0 + 10, y0 + 22, LARG - 20, 36);
        long limite = d.getLong("limite"), recebido = d.getLong("recebido"), resta = d.getLong("resta");
        g.drawString(font, "Limite de hoje (sobe com o seu nível)", x0 + 16, y0 + 27, CINZA, false);
        int barra = LARG - 32, cheio = limite <= 0 ? 0 : (int) Math.min(barra, barra * recebido / limite);
        g.fill(x0 + 16, y0 + 38, x0 + 16 + barra, y0 + 44, PERGAMINHO_ESC);
        g.fill(x0 + 16, y0 + 38, x0 + 16 + cheio, y0 + 44, resta > 0 ? VERDE : VERMELHO);
        g.drawString(font, "Ainda pode receber:", x0 + 16, y0 + 47, CINZA, false);
        int xv = x0 + 16 + font.width("Ainda pode receber: ");
        moedas(g, font, xv, y0 + 47, resta, resta > 0 ? TINTA : VERMELHO);
        String renova = "renova em " + d.getString("renova");
        g.drawString(font, renova, x0 + LARG - 16 - font.width(renova), y0 + 47, CINZA, false);

        ItemStack dica = ItemStack.EMPTY;
        ListTag lista = itens();
        if (lista.isEmpty()) {
            centro(g, "Nada na mochila que eu possa comprar.", x0 + LARG / 2, y0 + 100, CINZA);
        } else {
            int inicio = pagina * POR_PAGINA;
            for (int i = inicio; i < Math.min(lista.size(), inicio + POR_PAGINA); i++) {
                CompoundTag o = lista.getCompound(i);
                int y = y0 + 64 + (i - inicio) * LINHA;
                cartao(g, x0 + 10, y, LARG - 20, LINHA - 2);
                ItemStack s = ItemStack.of(o.getCompound("item"));
                g.renderItem(s, x0 + 14, y + 3);
                g.renderItemDecorations(font, s, x0 + 14, y + 3);
                if (mx >= x0 + 14 && mx < x0 + 30 && my >= y + 3 && my < y + 19) dica = s;
                String nome = s.getHoverName().getString();
                if (font.width(nome) > 108) nome = font.plainSubstrByWidth(nome, 102) + "…";
                g.drawString(font, nome, x0 + 36, y + 7, TINTA, false);
                moedas(g, font, x0 + 152, y + 7, o.getLong("total"), o.getLong("total") <= d.getLong("resta") ? TINTA : VERMELHO);
            }
            if (lista.size() > POR_PAGINA)
                g.drawString(font, (pagina + 1) + "/" + ((lista.size() - 1) / POR_PAGINA + 1), x0 + 60, y0 + ALT - 22, CINZA, false);
        }

        if (confirmarTudo) {
            g.drawString(font, "Vende TUDO da mochila, inclusive armas e ferramentas.", x0 + 10, y0 + ALT - 42, VERMELHO, false);
        } else {
        String msg = d.getString("msg");
        if (!msg.isEmpty()) g.drawString(font, msg, x0 + 10, y0 + ALT - 42, d.getBoolean("ok") ? VERDE : VERMELHO, false);
        else if (!lista.isEmpty()) {
            g.drawString(font, "Tudo:", x0 + 10, y0 + ALT - 42, CINZA, false);
            moedas(g, font, x0 + 10 + font.width("Tudo: "), y0 + ALT - 42, d.getLong("totalTudo"), TINTA);
        }
        }
        super.render(g, mx, my, pt);
        if (!dica.isEmpty()) g.renderTooltip(font, dica, mx, my);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
