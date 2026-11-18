package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.moedas.Mercado;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Leiloeiro: vitrine com busca e cartões, venda escolhendo o item na bolsa, meus anúncios e o que há para receber. */
public class TelaMercado extends Screen {
    private static final int LARG = 372, ALT = 240, COLUNAS = 4, LINHAS = 2, CARTAO_L = 86, CARTAO_A = 62;
    private static final String[] NOMES = {"Ouro", "Prata", "Cobre"};
    private static final long[] VALOR = {100, 10, 1};
    private static final String[] ORDENS = {"Mais novos", "Mais baratos", "Mais caros"};

    /** Um anúncio já pronto para desenhar. */
    private record Oferta(int id, ItemStack item, long preco, String vendedor, boolean meu, String resta, String busca) { }

    private CompoundTag d;
    private int x0, y0, aba, pagina, ordem, selecionado = -1, espaco = -1;
    private String textoBusca = "";
    private final long[] preco = new long[3];
    private final List<Oferta> todas = new ArrayList<>();
    private EditBox busca;

    public TelaMercado(CompoundTag d) {
        super(Component.literal("Leiloeiro"));
        this.d = d;
        ler();
    }

    public void atualizar(CompoundTag novo) {
        this.d = novo;
        ler();
        selecionado = -1;
        if (novo.getBoolean("ok") && !novo.getString("msg").isEmpty() && aba == 1) { espaco = -1; preco[0] = preco[1] = preco[2] = 0; }
        rebuildWidgets();
    }

    private void ler() {
        todas.clear();
        ListTag l = d.getList("anuncios", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            ItemStack s = ItemStack.of(t.getCompound("item"));
            if (s.isEmpty()) continue;
            todas.add(new Oferta(t.getInt("id"), s, t.getLong("preco"), t.getString("vendedor"), t.getBoolean("meu"), t.getString("resta"),
                    (s.getHoverName().getString() + " " + t.getString("vendedor")).toLowerCase(Locale.ROOT)));
        }
    }

    private void enviar(int acao, long valor) { Rede.CANAL.sendToServer(new AcaoMenu("mercado", d.getInt("npc"), acao, valor)); }
    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }
    private long total() { return preco[0] * 100 + preco[1] * 10 + preco[2]; }

    /** O que aparece na grade da aba atual, já filtrado e ordenado. */
    private List<Oferta> visiveis() {
        List<Oferta> l = new ArrayList<>();
        String filtro = textoBusca.trim().toLowerCase(Locale.ROOT);
        for (Oferta o : todas) {
            if (aba == 2 ? !o.meu() : o.meu()) continue;
            if (aba == 0 && !filtro.isEmpty() && !o.busca().contains(filtro)) continue;
            l.add(o);
        }
        if (aba == 0 && ordem == 1) l.sort((a, b) -> Long.compare(a.preco(), b.preco()));
        if (aba == 0 && ordem == 2) l.sort((a, b) -> Long.compare(b.preco(), a.preco()));
        return l;
    }

    private int paginas(int quantos) { return Math.max(1, (quantos + COLUNAS * LINHAS - 1) / (COLUNAS * LINHAS)); }

    private Oferta escolhida() {
        for (Oferta o : todas) if (o.id() == selecionado) return o;
        return null;
    }

    private ItemStack naBolsa(int i) {
        return minecraft == null || minecraft.player == null || i < 0 || i >= 36 ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(i);
    }

    private void somar(int i, long n) {
        preco[i] = Math.max(0, Math.min(i == 0 ? 99_999 : 9, preco[i] + n));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        busca = null;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        long receber = d.getLong("receberMoedas");
        int itens = d.getInt("receberItens");
        String[] abas = {"Comprar", "Vender", "Meus anúncios (" + d.getInt("meusQtd") + "/" + d.getInt("limite") + ")", receber > 0 || itens > 0 ? "Receber ✦" : "Receber"};
        int[] larg = {70, 70, 130, 76};
        int x = x0 + 10;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(abas[i]), b -> { aba = idx; pagina = 0; selecionado = -1; rebuildWidgets(); }).bounds(x, y0 + 22, larg[i], 18).build()).active = aba != i;
            x += larg[i] + 4;
        }
        if (aba == 0 || aba == 2) {
            if (aba == 0) {
                busca = new EditBox(font, x0 + 12, y0 + 47, 150, 14, Component.literal("Buscar"));
                busca.setMaxLength(32);
                busca.setHint(Component.literal("buscar item ou vendedor"));
                busca.setValue(textoBusca);
                busca.setResponder(t -> { textoBusca = t; pagina = 0; });
                addRenderableWidget(busca);
                addRenderableWidget(Button.builder(Component.literal(ORDENS[ordem]), b -> { ordem = (ordem + 1) % ORDENS.length; pagina = 0; rebuildWidgets(); }).bounds(x0 + 168, y0 + 46, 84, 16).build());
            }
            addRenderableWidget(Button.builder(Component.literal("<"), b -> pagina = Math.max(0, pagina - 1)).bounds(x0 + LARG - 62, y0 + 46, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> pagina = Math.min(paginas(visiveis().size()) - 1, pagina + 1)).bounds(x0 + LARG - 28, y0 + 46, 18, 16).build());
            Oferta o = escolhida();
            if (o != null && aba == 0)
                addRenderableWidget(Button.builder(Component.literal("Comprar"), b -> enviar(Mercado.COMPRAR, o.id())).bounds(x0 + LARG - 100, y0 + ALT - 40, 88, 20).build()).active = d.getLong("bolsa") >= o.preco();
            if (o != null && aba == 2)
                addRenderableWidget(Button.builder(Component.literal("Tirar do mercado"), b -> enviar(Mercado.CANCELAR, o.id())).bounds(x0 + LARG - 120, y0 + ALT - 40, 108, 20).build());
        } else if (aba == 1) {
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                int ly = y0 + 106 + i * 20;
                addRenderableWidget(Button.builder(Component.literal("-"), b -> somar(idx, hasShiftDown() ? -10 : -1)).bounds(x0 + 250, ly, 16, 16).build()).active = preco[i] > 0;
                addRenderableWidget(Button.builder(Component.literal("+"), b -> somar(idx, hasShiftDown() ? 10 : 1)).bounds(x0 + 304, ly, 16, 16).build());
            }
            ItemStack s = naBolsa(espaco);
            addRenderableWidget(Button.builder(Component.literal("Anunciar"), b -> {
                ItemStack agora = naBolsa(espaco);
                if (!agora.isEmpty()) enviar(Mercado.ANUNCIAR, ((long) Item.getId(agora.getItem()) << 46) | ((long) espaco << 40) | total());
            }).bounds(x0 + 196, y0 + ALT - 40, 164, 20).build()).active = !s.isEmpty() && total() > 0 && d.getInt("meusQtd") < d.getInt("limite");
        } else {
            addRenderableWidget(Button.builder(Component.literal("Receber tudo"), b -> enviar(Mercado.RECEBER, 0)).bounds(x0 + LARG / 2 - 70, y0 + 150, 140, 20).build()).active = receber > 0 || itens > 0;
        }
    }

    // posição do cartão i (0 a 7) da página
    private int cx(int i) { return x0 + 10 + (i % COLUNAS) * (CARTAO_L + 3); }
    private int cy(int i) { return y0 + 68 + (i / COLUNAS) * (CARTAO_A + 3); }
    // posição do espaço i da bolsa (0 a 8 = barra de baixo; 9 a 35 = as três linhas de cima)
    private int bx(int i) { return x0 + 12 + (i % 9) * 19; }
    private int by(int i) { return i < 9 ? y0 + 146 : y0 + 80 + (i / 9 - 1) * 19; }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (super.mouseClicked(mx, my, botao)) return true;
        if (aba == 0 || aba == 2) {
            List<Oferta> l = visiveis();
            int ini = Math.min(pagina, paginas(l.size()) - 1) * COLUNAS * LINHAS;
            for (int i = 0; i < COLUNAS * LINHAS && ini + i < l.size(); i++) {
                if (mx >= cx(i) && mx < cx(i) + CARTAO_L && my >= cy(i) && my < cy(i) + CARTAO_A) {
                    selecionado = l.get(ini + i).id();
                    rebuildWidgets();
                    return true;
                }
            }
        } else if (aba == 1) {
            for (int i = 0; i < 36; i++) {
                if (mx >= bx(i) && mx < bx(i) + 18 && my >= by(i) && my < by(i) + 18 && !naBolsa(i).isEmpty()) {
                    espaco = i;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (aba == 0 || aba == 2) {
            pagina = Math.max(0, Math.min(paginas(visiveis().size()) - 1, pagina + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, d.getString("nome") + " · Mercado dos aventureiros", x0 + LARG / 2, y0 + 8, MOLDURA);
        ItemStack dica = ItemStack.EMPTY;
        if (aba == 0 || aba == 2) dica = renderGrade(g, mx, my);
        else if (aba == 1) dica = renderVenda(g, mx, my);
        else renderReceber(g);
        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, corta(msg, LARG - 20), x0 + LARG / 2, y0 + ALT - 12, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
        if (!dica.isEmpty()) g.renderTooltip(font, dica, mx, my);
    }

    private ItemStack renderGrade(GuiGraphics g, int mx, int my) {
        List<Oferta> l = visiveis();
        int total = paginas(l.size());
        pagina = Math.max(0, Math.min(total - 1, pagina));
        centro(g, (pagina + 1) + "/" + total, x0 + LARG - 36, y0 + 50, CINZA);
        if (aba == 2) g.drawString(font, "Seus itens à venda. Clique num cartão para tirá-lo do mercado.", x0 + 12, y0 + 50, CINZA, false);
        ItemStack dica = ItemStack.EMPTY;
        if (l.isEmpty()) centro(g, aba == 2 ? "Você não tem nada anunciado." : textoBusca.isBlank() ? "Nenhum item à venda agora. Seja o primeiro: aba Vender." : "Nada encontrado para “" + textoBusca.trim() + "”.", x0 + LARG / 2, y0 + 110, TINTA);
        int ini = pagina * COLUNAS * LINHAS;
        for (int i = 0; i < COLUNAS * LINHAS && ini + i < l.size(); i++) {
            Oferta o = l.get(ini + i);
            int x = cx(i), y = cy(i);
            boolean sel = o.id() == selecionado, em = mx >= x && mx < x + CARTAO_L && my >= y && my < y + CARTAO_A;
            g.fill(x, y, x + CARTAO_L, y + CARTAO_A, sel ? OURO : em ? MOLDURA : PERGAMINHO_ESC);
            g.fill(x + 1, y + 1, x + CARTAO_L - 1, y + CARTAO_A - 1, CAIXA);
            g.fill(x + CARTAO_L / 2 - 11, y + 4, x + CARTAO_L / 2 + 11, y + 26, PERGAMINHO_ESC);
            g.renderItem(o.item(), x + CARTAO_L / 2 - 8, y + 7);
            g.renderItemDecorations(font, o.item(), x + CARTAO_L / 2 - 8, y + 7);
            centro(g, corta(o.item().getHoverName().getString(), CARTAO_L - 6), x + CARTAO_L / 2, y + 29, MOLDURA);
            int larg = font.width(String.valueOf(o.preco() / 100)) + 60;
            moedas(g, font, x + Math.max(4, (CARTAO_L - Math.min(larg, CARTAO_L - 8)) / 2), y + 40, o.preco(), TINTA);
            centro(g, corta(aba == 2 ? "vence em " + o.resta() : "de " + o.vendedor(), CARTAO_L - 6), x + CARTAO_L / 2, y + 51, CINZA);
            if (em) dica = o.item();
        }
        Oferta o = escolhida();
        int y = y0 + ALT - 40;
        if (o != null && (aba == 2) == o.meu()) {
            g.renderItem(o.item(), x0 + 12, y + 2);
            g.drawString(font, corta(o.item().getCount() + "× " + o.item().getHoverName().getString(), 150), x0 + 34, y + 1, MOLDURA, false);
            moedas(g, font, x0 + 34, y + 12, o.preco(), TINTA);
            if (aba == 0 && d.getLong("bolsa") < o.preco()) g.drawString(font, "faltam moedas", x0 + 150, y + 12, VERMELHO, false);
        } else {
            g.drawString(font, "Na sua bolsa:", x0 + 12, y + 6, CINZA, false);
            moedas(g, font, x0 + 12 + font.width("Na sua bolsa: "), y + 6, d.getLong("bolsa"), TINTA);
        }
        return dica;
    }

    private ItemStack renderVenda(GuiGraphics g, int mx, int my) {
        g.drawString(font, "1. Clique no item da sua bolsa que você quer vender", x0 + 12, y0 + 48, MOLDURA, false);
        g.drawString(font, "A pilha inteira vai para o anúncio.", x0 + 12, y0 + 60, CINZA, false);
        ItemStack dica = ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) {
            int x = bx(i), y = by(i);
            ItemStack s = naBolsa(i);
            boolean em = mx >= x && mx < x + 18 && my >= y && my < y + 18;
            g.fill(x, y, x + 18, y + 18, i == espaco ? OURO : em && !s.isEmpty() ? MOLDURA : PERGAMINHO_ESC);
            g.fill(x + 1, y + 1, x + 17, y + 17, CAIXA);
            if (s.isEmpty()) continue;
            g.renderItem(s, x + 1, y + 1);
            g.renderItemDecorations(font, s, x + 1, y + 1);
            if (em) dica = s;
        }
        // à direita: o item escolhido e o preço
        int x = x0 + 196;
        cartao(g, x, y0 + 46, LARG - 208, 44);
        ItemStack s = naBolsa(espaco);
        if (s.isEmpty()) {
            g.drawString(font, "Nenhum item escolhido", x + 8, y0 + 64, CINZA, false);
        } else {
            g.renderItem(s, x + 8, y0 + 52);
            g.renderItemDecorations(font, s, x + 8, y0 + 52);
            g.drawString(font, corta(s.getHoverName().getString(), LARG - 246), x + 30, y0 + 52, MOLDURA, false);
            g.drawString(font, s.getCount() + (s.getCount() == 1 ? " unidade" : " unidades"), x + 30, y0 + 64, TINTA, false);
            g.drawString(font, "Fica " + d.getInt("dias") + " dias à venda", x + 30, y0 + 76, CINZA, false);
        }
        g.drawString(font, "2. Preço (Shift = de 10 em 10)", x, y0 + 94, MOLDURA, false);
        ItemStack[] icones = {new ItemStack(Moedas.OURO.get()), new ItemStack(Moedas.PRATA.get()), new ItemStack(Moedas.COBRE.get())};
        for (int i = 0; i < 3; i++) {
            int ly = y0 + 106 + i * 20;
            g.renderItem(icones[i], x, ly);
            g.drawString(font, NOMES[i], x + 20, ly + 4, TINTA, false);
            g.fill(x0 + 268, ly, x0 + 302, ly + 16, PERGAMINHO_ESC);
            centro(g, String.valueOf(preco[i]), x0 + 285, ly + 4, TINTA);
        }
        long t = total(), taxa = t <= 0 ? 0 : Math.max(1, t * d.getInt("taxa") / 100);
        g.drawString(font, "Você recebe:", x, y0 + 170, CINZA, false);
        moedas(g, font, x + font.width("Você recebe: "), y0 + 170, Math.max(0, t - taxa), TINTA);
        g.drawString(font, "taxa de " + d.getInt("taxa") + "% só se vender", x, y0 + 182, CINZA, false);
        if (d.getInt("meusQtd") >= d.getInt("limite")) g.drawString(font, "Limite de anúncios atingido", x0 + 12, y0 + ALT - 34, VERMELHO, false);
        else g.drawString(font, "Anúncios: " + d.getInt("meusQtd") + " de " + d.getInt("limite") + " (cresce com o rank)", x0 + 12, y0 + ALT - 34, CINZA, false);
        return dica;
    }

    private void renderReceber(GuiGraphics g) {
        long receber = d.getLong("receberMoedas");
        int itens = d.getInt("receberItens");
        cartao(g, x0 + 40, y0 + 60, LARG - 80, 76);
        centro(g, "O que espera você no balcão", x0 + LARG / 2, y0 + 68, MOLDURA);
        g.drawString(font, "Moedas das suas vendas:", x0 + 56, y0 + 88, TINTA, false);
        moedas(g, font, x0 + 56 + font.width("Moedas das suas vendas: "), y0 + 88, receber, TINTA);
        g.drawString(font, "Itens devolvidos (vencidos ou sem espaço): " + itens, x0 + 56, y0 + 104, TINTA, false);
        g.drawString(font, receber <= 0 && itens <= 0 ? "Nada por enquanto. O dinheiro das vendas aparece aqui." : "Vendas pagam mesmo com você fora do jogo.", x0 + 56, y0 + 120, CINZA, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
