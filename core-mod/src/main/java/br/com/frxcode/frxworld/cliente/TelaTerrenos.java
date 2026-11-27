package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.protecao.Terrenos;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Terrenos: comprar a área escolhida (com o desenho dela e os limites do rank), meus terrenos e quem pode mexer. */
public class TelaTerrenos extends Screen {
    private static final int LARG = 372, ALT = 236, POR_PAGINA = 5, PESSOAS_PAGINA = 6;
    private static final int COR_ESCOLHA = 0xFFE6C55A, COR_MEU = 0xFF7DB86A, COR_OUTRO = 0xFFC86A5A, COR_TRILHO = 0xFFCFC2A4;

    private CompoundTag d;
    private int x0, y0, pagina, escolhido = -1, confirmar = -1;

    public TelaTerrenos(CompoundTag d) {
        super(Component.literal("Terrenos"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) {
        if (novo.getInt("aba") != d.getInt("aba")) pagina = 0;
        this.d = novo;
        confirmar = -1;
        rebuildWidgets();
    }

    private void enviar(int acao, long valor) { Rede.CANAL.sendToServer(new AcaoMenu("terrenos", 0, acao, valor)); }
    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }
    private ListTag lotes() { return d.getList("lotes", Tag.TAG_COMPOUND); }
    private ListTag pessoas() { return d.getList("pessoas", Tag.TAG_COMPOUND); }
    private boolean temEscolha() { return d.contains("sel"); }

    private int quebrar(GuiGraphics g, String t, int x, int y, int larg, int cor, int maxLinhas) {
        List<FormattedCharSequence> linhas = font.split(Component.literal(t), larg);
        for (int i = 0; i < linhas.size() && i < maxLinhas; i++) g.drawString(font, linhas.get(i), x, y + i * 10, cor, false);
        return Math.min(linhas.size(), maxLinhas) * 10;
    }

    private CompoundTag marcado() {
        ListTag l = lotes();
        for (int i = 0; i < l.size(); i++) if (l.getCompound(i).getInt("id") == escolhido) return l.getCompound(i);
        return null;
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        int aba = d.getInt("aba");
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        String[] abas = {"Comprar", "Meus terrenos (" + lotes().size() + ")", "Quem pode mexer"};
        for (int i = 0; i < 3; i++) {
            final int qual = i;
            addRenderableWidget(Button.builder(Component.literal(abas[i]), b -> enviar(Terrenos.ABA, qual)).bounds(x0 + 10 + i * 118, y0 + 22, 114, 16).build()).active = aba != i;
        }
        int yb = y0 + ALT - 44;
        if (aba == 0) {
            boolean pode = temEscolha() && d.getCompound("sel").getString("motivo").isEmpty();
            addRenderableWidget(Button.builder(Component.literal("1º canto aqui"), b -> enviar(Terrenos.CANTO1, 0)).bounds(x0 + 10, yb, 104, 20).build());
            addRenderableWidget(Button.builder(Component.literal("2º canto aqui"), b -> enviar(Terrenos.CANTO2, 0)).bounds(x0 + 118, yb, 104, 20).build()).active = d.contains("cx") || temEscolha();
            addRenderableWidget(Button.builder(Component.literal("Limpar"), b -> enviar(Terrenos.CANCELAR, 0)).bounds(x0 + 226, yb, 58, 20).build()).active = d.contains("cx") || temEscolha();
            addRenderableWidget(Button.builder(Component.literal("Comprar"), b -> enviar(Terrenos.COMPRAR, 0)).bounds(x0 + 288, yb, 74, 20).build()).active = pode;
        } else if (aba == 1) {
            int paginas = Math.max(1, (lotes().size() + POR_PAGINA - 1) / POR_PAGINA);
            pagina = Math.max(0, Math.min(paginas - 1, pagina));
            if (paginas > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina--; rebuildWidgets(); }).bounds(x0 + LARG - 62, y0 + 4, 16, 16).build()).active = pagina > 0;
                addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); }).bounds(x0 + LARG - 42, y0 + 4, 16, 16).build()).active = pagina < paginas - 1;
            }
            CompoundTag t = marcado();
            final int id = t == null ? -1 : t.getInt("id");
            addRenderableWidget(Button.builder(Component.literal(t != null && confirmar == id ? "Confirmar?" : "Vender"), b -> {
                if (confirmar == id) { enviar(Terrenos.VENDER, id); escolhido = -1; confirmar = -1; }
                else { confirmar = id; rebuildWidgets(); }
            }).bounds(x0 + 10, yb, 70, 20).build()).active = t != null;
            addRenderableWidget(Button.builder(Component.literal("Ver divisas"), b -> { enviar(Terrenos.DIVISAS, 0); onClose(); }).bounds(x0 + 84, yb, 76, 20).build());
            addRenderableWidget(Button.builder(Component.literal(d.getBoolean("publico") ? "Fechar terrenos" : "Abrir a todos"), b -> enviar(Terrenos.PUBLICO, 0)).bounds(x0 + 164, yb, 94, 20).build());
            addRenderableWidget(Button.builder(Component.literal(d.getBoolean("semMonstros") ? "Liberar monstros" : "Barrar monstros"), b -> enviar(Terrenos.MONSTROS, 0)).bounds(x0 + 262, yb, 100, 20).build())
                    .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(d.getBoolean("semMonstros")
                            ? "Hoje monstros não nascem nos seus terrenos e os que entram somem. Clique para deixar nascer de novo."
                            : "Monstros param de nascer nos seus terrenos (noite, patrulhas, hordas) e os que entrarem andando somem.")));
        } else {
            ListTag l = pessoas();
            int paginas = Math.max(1, (l.size() + PESSOAS_PAGINA - 1) / PESSOAS_PAGINA);
            pagina = Math.max(0, Math.min(paginas - 1, pagina));
            if (paginas > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina--; rebuildWidgets(); }).bounds(x0 + LARG - 62, y0 + 4, 16, 16).build()).active = pagina > 0;
                addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); }).bounds(x0 + LARG - 42, y0 + 4, 16, 16).build()).active = pagina < paginas - 1;
            }
            String[] nomes = {"Colocar", "Quebrar", "Baús e portas"};
            int[] bits = {Terrenos.COLOCAR, Terrenos.QUEBRAR, Terrenos.USAR}, largs = {62, 62, 88};
            for (int i = 0; i < PESSOAS_PAGINA && pagina * PESSOAS_PAGINA + i < l.size(); i++) {
                final int idx = pagina * PESSOAS_PAGINA + i;
                int perm = l.getCompound(idx).getInt("perm"), x = x0 + 142;
                for (int k = 0; k < 3; k++) {
                    final int bit = bits[k];
                    addRenderableWidget(Button.builder(Component.literal(((perm & bit) != 0 ? "✔ " : "") + nomes[k]), b -> enviar(Terrenos.PESSOA, idx * 8L + bit)).bounds(x, y0 + 74 + i * 20, largs[k], 18).build());
                    x += largs[k] + 4;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (super.mouseClicked(mx, my, botao)) return true;
        if (d.getInt("aba") != 1) return false;
        ListTag l = lotes();
        for (int i = 0; i < POR_PAGINA && pagina * POR_PAGINA + i < l.size(); i++) {
            int y = y0 + 46 + i * 26;
            if (mx >= x0 + 10 && mx < x0 + LARG - 10 && my >= y && my < y + 24) {
                escolhido = l.getCompound(pagina * POR_PAGINA + i).getInt("id");
                confirmar = -1;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    /** Barra de limite: verde enquanto cabe, vermelha quando passa. */
    private void limite(GuiGraphics g, int x, int y, String rotulo, int valor, int max) {
        boolean passa = valor > max;
        g.drawString(font, rotulo + ": " + valor + " de " + max, x, y, passa ? VERMELHO : TINTA, false);
        int bx = x + 112, larg = 78;
        g.fill(bx, y + 2, bx + larg, y + 7, COR_TRILHO);
        g.fill(bx, y + 2, bx + Math.max(0, Math.min(larg, max <= 0 ? 0 : (int) ((long) larg * valor / max))), y + 7, passa ? VERMELHO : VERDE);
    }

    /** Desenho da área vista de cima: a escolha em dourado e os terrenos vizinhos em volta. */
    private void desenhoDaArea(GuiGraphics g, int x, int y, int l, int a) {
        cartao(g, x, y, l, a);
        if (!temEscolha()) {
            int ty = y + 14;
            if (d.contains("cx")) {
                centro(g, "1º canto marcado", x + l / 2, ty, MOLDURA);
                centro(g, "X " + d.getInt("cx") + ", Z " + d.getInt("cz"), x + l / 2, ty + 12, TINTA);
                quebrar(g, "Vá até o canto oposto e clique em \"2º canto aqui\", ou marque pelo mapa.", x + 8, ty + 32, l - 16, CINZA, 5);
            } else {
                centro(g, "Nenhuma área escolhida", x + l / 2, ty, MOLDURA);
                quebrar(g, "No mapa (tecla J), clique com o botão direito e marque os dois cantos. Ou ande até cada canto e use os botões abaixo.", x + 8, ty + 18, l - 16, CINZA, 7);
            }
            return;
        }
        CompoundTag s = d.getCompound("sel");
        int sx0 = s.getInt("x0"), sz0 = s.getInt("z0"), sx1 = s.getInt("x1"), sz1 = s.getInt("z1"), larg = sx1 - sx0 + 1, comp = sz1 - sz0 + 1;
        int margem = Math.max(4, Math.max(larg, comp) / 3), mx0 = sx0 - margem, mz0 = sz0 - margem, ml = larg + margem * 2, mc = comp + margem * 2;
        int areaL = l - 30, areaA = a - 30;
        float escala = Math.min(areaL / (float) ml, areaA / (float) mc);
        int dl = Math.round(ml * escala), da = Math.round(mc * escala), ox = x + 8 + (areaL - dl) / 2, oy = y + 16 + (areaA - da) / 2;
        g.fill(ox, oy, ox + dl, oy + da, PERGAMINHO);
        g.enableScissor(ox, oy, ox + dl, oy + da);
        ListTag viz = d.getList("vizinhos", Tag.TAG_COMPOUND);
        for (int i = 0; i < viz.size(); i++) {
            CompoundTag v = viz.getCompound(i);
            g.fill(ox + Math.round((v.getInt("x0") - mx0) * escala), oy + Math.round((v.getInt("z0") - mz0) * escala),
                    ox + Math.round((v.getInt("x1") + 1 - mx0) * escala), oy + Math.round((v.getInt("z1") + 1 - mz0) * escala), v.getBoolean("meu") ? COR_MEU : COR_OUTRO);
        }
        g.disableScissor();
        int rx0 = ox + Math.round(margem * escala), rz0 = oy + Math.round(margem * escala), rx1 = ox + Math.round((margem + larg) * escala), rz1 = oy + Math.round((margem + comp) * escala);
        boolean pode = s.getString("motivo").isEmpty();
        g.fill(rx0 - 1, rz0 - 1, rx1 + 1, rz1 + 1, pode ? MOLDURA : VERMELHO);
        g.fill(rx0, rz0, rx1, rz1, COR_ESCOLHA);
        centro(g, larg + " blocos", (rx0 + rx1) / 2, y + 5, TINTA);
        g.drawString(font, String.valueOf(comp), x + l - 20, (rz0 + rz1) / 2 - 4, TINTA, false);
        g.drawString(font, "N↑", x + 4, y + a - 11, CINZA, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, "Terrenos", x0 + LARG / 2, y0 + 8, MOLDURA);
        int aba = d.getInt("aba");
        if (aba == 0) {
            desenhoDaArea(g, x0 + 10, y0 + 44, 150, 120);
            int cx = x0 + 170, y = y0 + 46;
            int lado = 0, area = 0;
            if (temEscolha()) {
                CompoundTag s = d.getCompound("sel");
                int larg = s.getInt("x1") - s.getInt("x0") + 1, comp = s.getInt("z1") - s.getInt("z0") + 1;
                lado = Math.max(larg, comp); area = s.contains("novos") ? s.getInt("novos") : larg * comp;   // só o que ainda não é do jogador
                g.drawString(font, "Terreno de " + larg + " × " + comp + " blocos", cx, y, MOLDURA, false);
                g.drawString(font, "X " + s.getInt("x0") + " a " + s.getInt("x1") + ", Z " + s.getInt("z0") + " a " + s.getInt("z1"), cx, y + 12, CINZA, false);
                g.drawString(font, area < larg * comp ? "Área nova: " + area + " blocos (" + (larg * comp - area) + " já são seus)" : "Área: " + area + " blocos, do fundo ao céu", cx, y + 24, TINTA, false);
                g.drawString(font, "Preço:", cx, y + 38, TINTA, false);
                moedas(g, font, cx + 52, y + 38, s.getLong("preco"), s.getLong("preco") <= d.getLong("bolsa") ? TINTA : VERMELHO);
            } else {
                g.drawString(font, "Escolha a área do terreno", cx, y, MOLDURA, false);
                g.drawString(font, "A cada 100 blocos de área:", cx, y + 12, CINZA, false);
                moedas(g, font, cx, y + 24, d.getLong("preco100"), TINTA);
            }
            g.drawString(font, "Na bolsa:", cx, y + 50, TINTA, false);
            moedas(g, font, cx + 52, y + 50, d.getLong("bolsa"), TINTA);
            g.drawString(font, "Limites do Rank " + d.getString("rank"), cx, y + 66, MOLDURA, false);
            limite(g, cx, y + 78, "Lado", lado, d.getInt("ladoMax"));
            limite(g, cx, y + 90, "Área", d.getInt("areaTenho") + area, d.getInt("areaMax"));
            limite(g, cx, y + 102, "Terrenos", d.getInt("qtdTenho") + (temEscolha() ? 1 : 0), d.getInt("qtdMax"));
            String motivo = temEscolha() ? d.getCompound("sel").getString("motivo") : "";
            if (!motivo.isEmpty()) quebrar(g, motivo, x0 + 10, y0 + 168, LARG - 20, VERMELHO, 2);
            else quebrar(g, temEscolha() ? "Só você e quem você liberar constroem e quebram aqui. Subir de rank na Guilda aumenta os limites."
                    : "O terreno vai do fundo ao céu. Subir de rank na Guilda aumenta os limites.", x0 + 10, y0 + 168, LARG - 20, CINZA, 2);
        } else if (aba == 1) {
            ListTag l = lotes();
            if (l.isEmpty()) {
                centro(g, "Você ainda não tem terreno", x0 + LARG / 2, y0 + 80, MOLDURA);
                quebrar(g, "Abra o mapa (tecla J), clique com o botão direito e marque os dois cantos. Ou vá na aba Comprar e marque os cantos onde você está.", x0 + 40, y0 + 96, LARG - 80, CINZA, 4);
            }
            for (int i = 0; i < POR_PAGINA && pagina * POR_PAGINA + i < l.size(); i++) {
                CompoundTag t = l.getCompound(pagina * POR_PAGINA + i);
                int x = x0 + 10, y = y0 + 46 + i * 26, larg = t.getInt("x1") - t.getInt("x0") + 1, comp = t.getInt("z1") - t.getInt("z0") + 1;
                boolean sel = t.getInt("id") == escolhido, em = mx >= x && mx < x0 + LARG - 10 && my >= y && my < y + 24;
                g.fill(x, y, x0 + LARG - 10, y + 24, sel ? OURO : em ? MOLDURA : PERGAMINHO_ESC);
                g.fill(x + 1, y + 1, x0 + LARG - 11, y + 23, CAIXA);
                g.fill(x + 5, y + 5, x + 19, y + 19, COR_MEU);
                g.drawString(font, "Terreno " + (pagina * POR_PAGINA + i + 1) + " · " + larg + " × " + comp + " (" + larg * comp + " blocos)", x + 26, y + 3, MOLDURA, false);
                g.drawString(font, "X " + t.getInt("x0") + " a " + t.getInt("x1") + ", Z " + t.getInt("z0") + " a " + t.getInt("z1"), x + 26, y + 13, CINZA, false);
                g.drawString(font, "pago", x0 + LARG - 120, y + 8, CINZA, false);
                moedas(g, font, x0 + LARG - 94, y + 8, t.getLong("pago"), TINTA);
            }
            CompoundTag t = marcado();
            if (t != null) {
                g.drawString(font, "Vender devolve metade:", x0 + 10, y0 + 178, TINTA, false);
                moedas(g, font, x0 + 14 + font.width("Vender devolve metade:"), y0 + 178, t.contains("volta") ? t.getLong("volta") : t.getLong("pago") / 2, TINTA);
            } else if (!l.isEmpty()) g.drawString(font, "Clique num terreno para vender. " + (d.getBoolean("publico") ? "Qualquer um mexe." : "Só você e os liberados mexem.") + (d.getBoolean("semMonstros") ? " Sem monstros." : ""), x0 + 10, y0 + 178, CINZA, false);
        } else {
            quebrar(g, "Escolha o que cada jogador pode fazer nos seus terrenos. O ✔ marca o que está liberado; clique para ligar ou desligar.", x0 + 10, y0 + 46, LARG - 20, TINTA, 2);
            ListTag l = pessoas();
            if (l.isEmpty()) centro(g, "Ninguém mais está no jogo agora.", x0 + LARG / 2, y0 + 110, CINZA);
            for (int i = 0; i < PESSOAS_PAGINA && pagina * PESSOAS_PAGINA + i < l.size(); i++) {
                CompoundTag t = l.getCompound(pagina * PESSOAS_PAGINA + i);
                int y = y0 + 74 + i * 20;
                g.drawString(font, corta(t.getString("nome"), 126), x0 + 12, y + 1, t.getInt("perm") != 0 ? VERDE : TINTA, false);
                g.drawString(font, t.getBoolean("online") ? "no jogo" : "fora do jogo", x0 + 12, y + 10, CINZA, false);
            }
        }
        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, corta(msg, LARG - 20), x0 + LARG / 2, y0 + ALT - 14, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
