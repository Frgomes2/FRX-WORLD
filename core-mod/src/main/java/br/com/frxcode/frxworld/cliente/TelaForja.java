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

/** Ferreiro (aprimorar +1..+10) e armeiro (reforjar a raridade), com a loja do NPC na segunda aba. */
public class TelaForja extends Screen {
    private static final int LARG = 340, ALT = 226, POR_PAGINA = 6, LINHA = 24;

    private CompoundTag d;
    private int aba = 0, pagina = 0;     // 0 forja, 1 loja
    private int x0, y0;

    public TelaForja(CompoundTag d) {
        super(Component.literal("Forja"));
        this.d = d;
        if (d.getBoolean("soLoja")) aba = 1;
    }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    private ListTag ofertas() { return d.getList("ofertas", Tag.TAG_COMPOUND); }
    private ListTag barra() { return d.getList("barra", Tag.TAG_COMPOUND); }
    private boolean reforjar() { return d.getBoolean("reforjar"); }
    private void enviar(int acao, long valor) { Rede.CANAL.sendToServer(new AcaoMenu("forja", d.getInt("npc"), acao, valor)); }
    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }

    private boolean podeForjar() {
        return d.getString("estado").equals("ok") && d.getLong("bolsa") >= d.getLong("moedas") && d.getInt("temMaterial") >= d.getInt("materialQtd");
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        String[] abas = {reforjar() ? "⚒ Reforjar" : "⚒ Aprimorar", "Loja (" + ofertas().size() + ")"};
        for (int i = 0; i < 2 && !d.getBoolean("soLoja"); i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(abas[i]), b -> { aba = idx; rebuildWidgets(); })
                    .bounds(x0 + 10 + i * 104, y0 + 22, 100, 18).build()).active = aba != i;
        }
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());

        if (aba == 0) {
            addRenderableWidget(Button.builder(Component.literal(reforjar() ? "Reforjar" : "Aprimorar"), b -> enviar(AcaoMenu.FORJAR, 0))
                    .bounds(x0 + 118, y0 + 140, 212, 20).build()).active = podeForjar();
        } else {
            ListTag of = ofertas();
            pagina = Math.max(0, Math.min(pagina, (of.size() - 1) / POR_PAGINA));
            int inicio = pagina * POR_PAGINA;
            for (int i = inicio; i < Math.min(of.size(), inicio + POR_PAGINA); i++) {
                final int idx = i;
                CompoundTag o = of.getCompound(i);
                int y = y0 + 46 + (i - inicio) * LINHA;
                addRenderableWidget(Button.builder(Component.literal(o.getBoolean("fora") ? "Esgotado" : "Comprar"), b -> enviar(AcaoMenu.COMPRAR, idx | ((long) net.minecraft.world.item.Item.getId(ItemStack.of(o.getCompound("r")).getItem()) << 16)))
                        .bounds(x0 + LARG - 70, y + 2, 60, 18).build()).active = o.getBoolean("pode");
            }
            if (of.size() > POR_PAGINA) {
                addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina--; rebuildWidgets(); })
                        .bounds(x0 + 10, y0 + ALT - 24, 20, 16).build()).active = pagina > 0;
                addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); })
                        .bounds(x0 + 34, y0 + ALT - 24, 20, 16).build()).active = (pagina + 1) * POR_PAGINA < of.size();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (aba == 0 && botao == 0) {
            // barra de atalhos: clicar num item passa a segurar ele
            for (int i = 0; i < 9; i++) {
                int sx = x0 + 10 + i * 20, sy = y0 + 178;
                if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) { enviar(AcaoMenu.SEGURAR, i); return true; }
            }
        }
        return super.mouseClicked(mx, my, botao);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, d.getString("nome"), x0 + LARG / 2, y0 + 8, MOLDURA);
        // bolsa (canto direito das abas)
        g.drawString(font, "Bolsa:", x0 + 226, y0 + 27, CINZA, false);
        moedas(g, font, x0 + 226 + font.width("Bolsa: "), y0 + 27, d.getLong("bolsa"), TINTA);

        ItemStack dica = ItemStack.EMPTY;
        if (aba == 0) dica = renderForja(g, mx, my); else dica = renderLoja(g, mx, my);

        String msg = d.getString("msg");
        if (msg.isEmpty() && aba == 1 && !d.getString("dicaLoja").isEmpty()) centro(g, d.getString("dicaLoja"), x0 + LARG / 2 + 30, y0 + ALT - 20, CINZA);
        if (!msg.isEmpty()) centro(g, msg, x0 + LARG / 2 + (aba == 1 ? 30 : 0), y0 + ALT - (aba == 0 ? 26 : 20), d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
        if (!dica.isEmpty()) g.renderTooltip(font, dica, mx, my);
    }

    private ItemStack renderForja(GuiGraphics g, int mx, int my) {
        ItemStack dica = ItemStack.EMPTY;
        String estado = d.getString("estado");
        // item grande
        cartao(g, x0 + 10, y0 + 46, 100, 114);
        if (d.contains("item")) {
            ItemStack item = ItemStack.of(d.getCompound("item"));
            g.pose().pushPose();
            g.pose().translate(x0 + 28, y0 + 54, 0);
            g.pose().scale(4f, 4f, 1f);
            g.renderItem(item, 0, 0);
            g.pose().popPose();
            String rar = d.getString("raridade");
            g.fill(x0 + 60 - font.width(rar) / 2 - 4, y0 + 121, x0 + 60 + font.width(rar) / 2 + 4, y0 + 134, MOLDURA);
            centro(g, rar, x0 + 60, y0 + 124, 0xFF000000 | (d.getInt("raridadeCor") & 0xFFFFFF));
            centro(g, d.getInt("nivel") > 0 ? "+" + d.getInt("nivel") : "sem aprimoramento", x0 + 60, y0 + 138, CINZA);
            if (mx >= x0 + 10 && mx < x0 + 110 && my >= y0 + 46 && my < y0 + 124) dica = item;
        } else {
            centro(g, "Nenhum item", x0 + 60, y0 + 92, CINZA);
            centro(g, "forjável na mão", x0 + 60, y0 + 104, CINZA);
        }

        int x = x0 + 118, y = y0 + 48;
        cartao(g, x, y - 2, 212, 88);
        if (estado.equals("ok")) {
            g.drawString(font, reforjar() ? "Reforjar a qualidade" : "Aprimorar o item", x + 6, y + 4, MOLDURA, false);
            g.drawString(font, d.getString("linha"), x + 6, y + 17, TINTA, false);
            boolean temMoedas = d.getLong("bolsa") >= d.getLong("moedas");
            g.drawString(font, "Custa:", x + 6, y + 34, CINZA, false);
            moedas(g, font, x + 44, y + 34, d.getLong("moedas"), temMoedas ? TINTA : VERMELHO);
            ItemStack mat = ItemStack.of(d.getCompound("material"));
            int precisa = d.getInt("materialQtd"), tem = d.getInt("temMaterial");
            if (precisa > 0) {
                g.renderItem(mat, x + 42, y + 46);
                g.drawString(font, precisa + "× " + mat.getHoverName().getString(), x + 62, y + 50, tem >= precisa ? TINTA : VERMELHO, false);
                g.drawString(font, "você tem " + tem, x + 62, y + 61, tem >= precisa ? VERDE : VERMELHO, false);
                if (mx >= x + 42 && mx < x + 58 && my >= y + 46 && my < y + 62) dica = mat;
            }
            int chance = d.getInt("chance");
            if (!reforjar()) {
                String t = "Chance: " + chance + "%";
                g.drawString(font, t, x + 206 - font.width(t), y + 4, chance >= 80 ? VERDE : chance >= 50 ? OURO : VERMELHO, false);
                if (chance < 100) g.drawString(font, "Se falhar, perde só o material.", x + 6, y + 74, CINZA, false);
            } else g.drawString(font, "Comum → Incomum → Raro → Épico → Lendário", x + 6, y + 74, CINZA, false);
        } else if (estado.equals("max")) {
            g.drawString(font, reforjar() ? "Esse item já é Lendário." : "Esse item já está em +10.", x + 6, y + 30, VERDE, false);
            g.drawString(font, "Não há mais o que melhorar nele aqui.", x + 6, y + 44, CINZA, false);
        } else {
            g.drawString(font, "Escolha na sua barra (abaixo) a arma,", x + 6, y + 24, TINTA, false);
            g.drawString(font, "ferramenta ou armadura que quer", x + 6, y + 36, TINTA, false);
            g.drawString(font, reforjar() ? "reforjar." : "aprimorar.", x + 6, y + 48, TINTA, false);
        }

        // barra de atalhos
        g.drawString(font, "Sua barra — clique para escolher o item:", x0 + 10, y0 + 166, CINZA, false);
        ListTag barra = barra();
        for (int i = 0; i < 9 && i < barra.size(); i++) {
            int sx = x0 + 10 + i * 20, sy = y0 + 178;
            boolean sel = i == d.getInt("slot");
            g.fill(sx, sy, sx + 18, sy + 18, sel ? OURO : PERGAMINHO_ESC);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, CAIXA);
            ItemStack s = ItemStack.of(barra.getCompound(i));
            if (!s.isEmpty()) {
                g.renderItem(s, sx + 1, sy + 1);
                g.renderItemDecorations(font, s, sx + 1, sy + 1);
                if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) dica = s;
            }
        }
        g.drawString(font, "Agachado + clique no NPC abre a troca antiga.", x0 + 10, y0 + ALT - 14, CINZA, false);
        return dica;
    }

    private ItemStack renderLoja(GuiGraphics g, int mx, int my) {
        ItemStack dica = ItemStack.EMPTY;
        ListTag of = ofertas();
        if (of.isEmpty()) { centro(g, "Este NPC não tem nada à venda agora.", x0 + LARG / 2, y0 + 90, CINZA); return dica; }
        int inicio = pagina * POR_PAGINA;
        for (int i = inicio; i < Math.min(of.size(), inicio + POR_PAGINA); i++) {
            CompoundTag o = of.getCompound(i);
            int y = y0 + 46 + (i - inicio) * LINHA;
            cartao(g, x0 + 10, y, LARG - 20, LINHA - 2);
            ItemStack r = ItemStack.of(o.getCompound("r")), a = ItemStack.of(o.getCompound("a")), b = ItemStack.of(o.getCompound("b"));
            int x = x0 + 14;
            g.renderItem(r, x, y + 3);
            g.renderItemDecorations(font, r, x, y + 3);
            if (mx >= x && mx < x + 16 && my >= y + 3 && my < y + 19) dica = r;
            String nome = r.getHoverName().getString();
            if (font.width(nome) > 120) nome = font.plainSubstrByWidth(nome, 114) + "…";
            g.drawString(font, nome, x + 22, y + 7, o.getBoolean("fora") ? CINZA : TINTA, false);
            int cx = x0 + 166;
            g.drawString(font, "por", cx, y + 7, CINZA, false);
            cx += font.width("por") + 6;
            for (ItemStack c : new ItemStack[]{a, b}) {
                if (c.isEmpty()) continue;
                g.renderItem(c, cx, y + 3);
                g.renderItemDecorations(font, c, cx, y + 3);
                if (mx >= cx && mx < cx + 16 && my >= y + 3 && my < y + 19) dica = c;
                cx += 22;
            }
        }
        if (of.size() > POR_PAGINA) g.drawString(font, (pagina + 1) + "/" + ((of.size() - 1) / POR_PAGINA + 1), x0 + 60, y0 + ALT - 20, CINZA, false);
        return dica;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
