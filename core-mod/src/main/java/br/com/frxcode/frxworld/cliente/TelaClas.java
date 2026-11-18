package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.clas.Clas;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AcaoHabilidade;
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
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Mestre dos Clãs: meus clãs, ranking de todos e a página do clã (cofre, membros, pedidos e gestão). */
public class TelaClas extends Screen {
    private static final int LARG = 360, ALT = 236, POR_PAGINA = 7;
    private static final String[] NOMES = {"Ouro", "Prata", "Cobre"};
    private static final long[] VALOR = {100, 10, 1};

    private CompoundTag d;
    private int x0, y0, pagina;
    private boolean sacando;
    private final long[] qtd = new long[3];
    private String confirmar = "";
    private EditBox nome;

    public TelaClas(CompoundTag d) {
        super(Component.literal("Mestre dos Clãs"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) {
        boolean mesmaTela = novo.getString("tela").equals(d.getString("tela")) && novo.getInt("aba") == d.getInt("aba");
        this.d = novo;
        if (!mesmaTela) pagina = 0;
        qtd[0] = qtd[1] = qtd[2] = 0;
        confirmar = "";
        rebuildWidgets();
    }

    private void enviar(int acao, long valor) { Rede.CANAL.sendToServer(new AcaoMenu("clas", d.getInt("npc"), acao, valor)); }
    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }
    private String tela() { return d.getString("tela"); }
    private CompoundTag cla() { return d.getCompound("cla"); }
    private ListTag lista(CompoundTag de, String chave) { return de.getList(chave, Tag.TAG_COMPOUND); }
    private long total() { return qtd[0] * 100 + qtd[1] * 10 + qtd[2]; }
    private long limite() { return sacando ? cla().getLong("cofre") : d.getLong("bolsa"); }

    private void somar(int i, long n) {
        long novo = Math.max(0, qtd[i] + n);
        long resto = limite() - (total() - qtd[i] * VALOR[i]);
        qtd[i] = Math.min(novo, Math.max(0, resto / VALOR[i]));
        rebuildWidgets();
    }

    /** Botão que pede um segundo clique antes de agir (sair, expulsar, passar a liderança, fundar). */
    private Button perigoso(String chave, String texto, int x, int y, int larg, int alt, Runnable acao) {
        boolean armado = confirmar.equals(chave);
        return Button.builder(Component.literal(armado ? "Confirmar?" : texto), b -> {
            if (armado) { confirmar = ""; acao.run(); }
            else { confirmar = chave; rebuildWidgets(); }
        }).bounds(x, y, larg, alt).build();
    }

    private void paginas(int quantos, int y) {
        int total = Math.max(1, (quantos + POR_PAGINA - 1) / POR_PAGINA);
        pagina = Math.max(0, Math.min(total - 1, pagina));
        if (total <= 1) return;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina--; rebuildWidgets(); }).bounds(x0 + LARG - 62, y, 18, 16).build()).active = pagina > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); }).bounds(x0 + LARG - 28, y, 18, 16).build()).active = pagina < total - 1;
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        nome = null;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        String tela = tela();
        addRenderableWidget(Button.builder(Component.literal("Meus clãs (" + d.getInt("meusQtd") + "/" + d.getInt("max") + ")"), b -> enviar(Clas.VER_MEUS, 0))
                .bounds(x0 + 10, y0 + 22, 110, 18).build()).active = !tela.equals("meus");
        addRenderableWidget(Button.builder(Component.literal("Todos os clãs (" + d.getInt("total") + ")"), b -> enviar(Clas.VER_TODOS, 0))
                .bounds(x0 + 124, y0 + 22, 120, 18).build()).active = !tela.equals("todos");
        if (tela.equals("meus")) initMeus();
        else if (tela.equals("todos")) initTodos();
        else initCla();
    }

    private void initMeus() {
        ListTag clas = lista(d, "clas");
        for (int i = 0; i < clas.size() && i < 3; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal("Abrir"), b -> enviar(Clas.ABRIR_CLA, idx)).bounds(x0 + LARG - 70, y0 + 62 + i * 46, 56, 18).build());
        }
        Button fundar = perigoso("fundar", "Fundar um clã novo", x0 + 10, y0 + ALT - 26, 150, 16, () -> enviar(Clas.FUNDAR, 0));
        fundar.active = d.getInt("meusQtd") < d.getInt("max");
        addRenderableWidget(fundar);
    }

    private void initTodos() {
        ListTag clas = lista(d, "clas");
        paginas(clas.size(), y0 + ALT - 24);
        for (int i = pagina * POR_PAGINA; i < Math.min(clas.size(), (pagina + 1) * POR_PAGINA); i++) {
            final int idx = i;
            int estado = clas.getCompound(i).getInt("estado");
            if (estado > 1) continue;
            addRenderableWidget(Button.builder(Component.literal(estado == 1 ? "Cancelar" : "Pedir entrada"), b -> enviar(Clas.PEDIR, idx))
                    .bounds(x0 + LARG - 92, y0 + 62 + (i - pagina * POR_PAGINA) * 21, 80, 16).build());
        }
    }

    private void initCla() {
        CompoundTag c = cla();
        int aba = d.getInt("aba");
        boolean lider = c.getBoolean("souLider");
        String[] abas = {"Cofre", "Membros (" + c.getInt("membros") + ")", "Pedidos (" + lista(c, "pedidosLista").size() + ")", "Gerir"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(abas[i]), b -> enviar(Clas.ABA, idx)).bounds(x0 + 10 + i * 86, y0 + 84, 82, 18).build()).active = aba != i;
        }
        int y = y0 + 108;
        if (aba == 0) {
            addRenderableWidget(Button.builder(Component.literal("Depositar"), b -> { sacando = false; qtd[0] = qtd[1] = qtd[2] = 0; rebuildWidgets(); }).bounds(x0 + 10, y, 72, 16).build()).active = sacando;
            addRenderableWidget(Button.builder(Component.literal("Sacar"), b -> { sacando = true; qtd[0] = qtd[1] = qtd[2] = 0; rebuildWidgets(); }).bounds(x0 + 86, y, 72, 16).build())
                    .active = !sacando && c.getBoolean("podeSacar");
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                int ly = y + 22 + i * 20;
                addRenderableWidget(Button.builder(Component.literal("-"), b -> somar(idx, hasShiftDown() ? -10 : -1)).bounds(x0 + 70, ly, 16, 16).build()).active = qtd[i] > 0;
                addRenderableWidget(Button.builder(Component.literal("+"), b -> somar(idx, hasShiftDown() ? 10 : 1)).bounds(x0 + 122, ly, 16, 16).build()).active = total() + VALOR[i] <= limite();
                addRenderableWidget(Button.builder(Component.literal("máx"), b -> somar(idx, 1_000_000)).bounds(x0 + 140, ly, 28, 16).build()).active = total() + VALOR[i] <= limite();
            }
            String verbo = sacando ? "Sacar" : "Depositar";
            addRenderableWidget(Button.builder(Component.literal(verbo), b -> enviar(sacando ? Clas.SACAR : Clas.DEPOSITAR, total())).bounds(x0 + 10, y0 + ALT - 40, 76, 18).build()).active = total() > 0;
            addRenderableWidget(Button.builder(Component.literal(verbo + " tudo"), b -> enviar(sacando ? Clas.SACAR : Clas.DEPOSITAR, limite())).bounds(x0 + 90, y0 + ALT - 40, 78, 18).build())
                    .active = limite() > 0 && (!sacando || c.getBoolean("podeSacar"));
        } else if (aba == 1) {
            ListTag ms = lista(c, "lista");
            paginas(ms.size(), y0 + ALT - 24);
            for (int i = pagina * POR_PAGINA; i < Math.min(ms.size(), (pagina + 1) * POR_PAGINA); i++) {
                final int idx = i;
                CompoundTag m = ms.getCompound(i);
                if (!lider || m.getBoolean("eu")) continue;
                int ly = y + (i - pagina * POR_PAGINA) * 16;
                addRenderableWidget(Button.builder(Component.literal(m.getString("cargo").equals("oficial") ? "Tirar oficial" : "Tornar oficial"), b -> enviar(Clas.OFICIAL, idx)).bounds(x0 + 180, ly, 72, 14).build());
                addRenderableWidget(perigoso("lider" + i, "Líder", x0 + 254, ly, 44, 14, () -> enviar(Clas.LIDERANCA, idx)));
                addRenderableWidget(perigoso("expulsar" + i, "Expulsar", x0 + 300, ly, 52, 14, () -> enviar(Clas.EXPULSAR, idx)));
            }
        } else if (aba == 2) {
            ListTag ps = lista(c, "pedidosLista");
            paginas(ps.size(), y0 + ALT - 24);
            for (int i = pagina * POR_PAGINA; i < Math.min(ps.size(), (pagina + 1) * POR_PAGINA); i++) {
                final int idx = i;
                int ly = y + (i - pagina * POR_PAGINA) * 16;
                addRenderableWidget(Button.builder(Component.literal("Aceitar"), b -> enviar(Clas.ACEITAR, idx)).bounds(x0 + 220, ly, 60, 14).build());
                addRenderableWidget(Button.builder(Component.literal("Recusar"), b -> enviar(Clas.RECUSAR, idx)).bounds(x0 + 284, ly, 60, 14).build());
            }
        } else {
            if (lider) {
                nome = new EditBox(font, x0 + 14, y + 12, 170, 16, Component.literal("Nome do clã"));
                nome.setMaxLength(24);
                nome.setValue(c.getString("nome"));
                addRenderableWidget(nome);
                addRenderableWidget(Button.builder(Component.literal("Mudar o nome"), b -> Rede.CANAL.sendToServer(new AcaoHabilidade(AcaoHabilidade.NOME_CLA, 0, nome.getValue())))
                        .bounds(x0 + 190, y + 12, 90, 16).build());
                ListTag cores = lista(c, "cores");
                for (int i = 0; i < cores.size() && i < 16; i++) {
                    final int idx = i;
                    final int rgb = cores.getCompound(i).getInt("rgb");
                    addRenderableWidget(Button.builder(Component.literal(c.getInt("corIdx") == i ? "✔" : "■").withStyle(s -> s.withColor(rgb)), b -> enviar(Clas.COR, idx))
                            .bounds(x0 + 14 + i * 20, y + 46, 18, 16).build());
                }
                addRenderableWidget(Button.builder(Component.literal(c.getBoolean("saqueLideres") ? "Saques: só líder e oficiais" : "Saques: qualquer membro"), b -> enviar(Clas.REGRA_SAQUE, 0))
                        .bounds(x0 + 14, y + 80, 170, 16).build());
            }
            addRenderableWidget(perigoso("sair", "Sair do clã", x0 + LARG - 104, y0 + ALT - 40, 90, 16, () -> enviar(Clas.SAIR, 0)));
        }
    }

    /** Faixa com a cor do clã, o nome e os números. */
    private void faixa(GuiGraphics g, CompoundTag c, int x, int y, int larg, int alt) {
        cartao(g, x, y, larg, alt);
        g.fill(x + 1, y + 1, x + 7, y + alt - 1, 0xFF000000 | c.getInt("cor"));
        g.drawString(font, corta("⚑ " + c.getString("nome"), larg - 90), x + 12, y + 5, MOLDURA, false);
        String cargo = c.getString("cargo");
        String linha = (cargo.isEmpty() ? "líder: " + c.getString("lider") : "você: " + cargo) + "  ·  " + c.getInt("membros") + (c.getInt("membros") == 1 ? " membro" : " membros") + "  ·  poder " + c.getInt("poder");
        g.drawString(font, corta(linha, larg - 20), x + 12, y + 17, TINTA, false);
        g.drawString(font, "Riqueza:", x + 12, y + 29, CINZA, false);
        moedas(g, font, x + 12 + font.width("Riqueza: "), y + 29, c.getLong("riqueza"), TINTA);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, d.getString("nome"), x0 + LARG / 2, y0 + 8, MOLDURA);
        String tela = tela();
        if (tela.equals("meus")) renderMeus(g);
        else if (tela.equals("todos")) renderTodos(g);
        else renderCla(g);
        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, corta(msg, LARG - 20), x0 + LARG / 2, y0 + ALT - 12, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
    }

    private void renderMeus(GuiGraphics g) {
        ListTag clas = lista(d, "clas");
        if (clas.isEmpty()) {
            cartao(g, x0 + 10, y0 + 56, LARG - 20, 70);
            centro(g, "Você ainda não faz parte de nenhum clã.", x0 + LARG / 2, y0 + 70, MOLDURA);
            centro(g, "Um clã é um grupo fixo de aventureiros, com cofre próprio", x0 + LARG / 2, y0 + 86, TINTA);
            centro(g, "e lugar no ranking do site. Funde o seu ou peça entrada em um.", x0 + LARG / 2, y0 + 98, TINTA);
        }
        for (int i = 0; i < clas.size() && i < 3; i++) {
            CompoundTag c = clas.getCompound(i);
            int y = y0 + 50 + i * 46;
            faixa(g, c, x0 + 10, y, LARG - 20, 42);
            if (c.getInt("pedidos") > 0) g.drawString(font, c.getInt("pedidos") + " pedido(s)", x0 + LARG - 150, y + 29, VERDE, false);
        }
        g.drawString(font, "Você pode participar de até " + d.getInt("max") + " clãs.", x0 + 170, y0 + ALT - 22, CINZA, false);
    }

    private void renderTodos(GuiGraphics g) {
        ListTag clas = lista(d, "clas");
        g.drawString(font, "#", x0 + 14, y0 + 48, CINZA, false);
        g.drawString(font, "Clã", x0 + 40, y0 + 48, CINZA, false);
        g.drawString(font, "Membros", x0 + 150, y0 + 48, CINZA, false);
        g.drawString(font, "Poder", x0 + 196, y0 + 48, CINZA, false);
        g.drawString(font, "Riqueza", x0 + 230, y0 + 48, CINZA, false);
        if (clas.isEmpty()) centro(g, "Nenhum clã ainda. Funde o primeiro em “Meus clãs”.", x0 + LARG / 2, y0 + 90, TINTA);
        for (int i = pagina * POR_PAGINA; i < Math.min(clas.size(), (pagina + 1) * POR_PAGINA); i++) {
            CompoundTag c = clas.getCompound(i);
            int y = y0 + 60 + (i - pagina * POR_PAGINA) * 21;
            cartao(g, x0 + 10, y, LARG - 20, 19);
            g.fill(x0 + 28, y + 3, x0 + 36, y + 16, 0xFF000000 | c.getInt("cor"));
            g.drawString(font, (i + 1) + "º", x0 + 13, y + 6, i < 3 ? OURO : CINZA, false);
            g.drawString(font, corta(c.getString("nome"), 106), x0 + 40, y + 6, MOLDURA, false);
            g.drawString(font, String.valueOf(c.getInt("membros")), x0 + 160, y + 6, TINTA, false);
            g.drawString(font, String.valueOf(c.getInt("poder")), x0 + 198, y + 6, TINTA, false);
            int estado = c.getInt("estado");
            if (estado > 1) g.drawString(font, estado == 2 ? "você é membro" : estado == 3 ? "cheio" : "seu limite", x0 + LARG - 90, y + 6, estado == 2 ? VERDE : CINZA, false);
            else moedas(g, font, x0 + 230, y + 6, c.getLong("riqueza") / 100 * 100, TINTA);
        }
    }

    private void renderCla(GuiGraphics g) {
        CompoundTag c = cla();
        faixa(g, c, x0 + 10, y0 + 42, LARG - 20, 40);
        int aba = d.getInt("aba"), y = y0 + 108;
        if (aba == 0) {
            ItemStack[] icones = {new ItemStack(Moedas.OURO.get()), new ItemStack(Moedas.PRATA.get()), new ItemStack(Moedas.COBRE.get())};
            for (int i = 0; i < 3; i++) {
                int ly = y + 22 + i * 20;
                g.renderItem(icones[i], x0 + 12, ly);
                g.drawString(font, NOMES[i], x0 + 32, ly + 4, TINTA, false);
                g.fill(x0 + 88, ly, x0 + 120, ly + 16, PERGAMINHO_ESC);
                centro(g, String.valueOf(qtd[i]), x0 + 104, ly + 4, TINTA);
            }
            // saldos e registro, à direita
            int x = x0 + 178;
            cartao(g, x, y, LARG - 188, 34);
            g.drawString(font, "No cofre do clã", x + 6, y + 4, CINZA, false);
            moedas(g, font, x + 6, y + 16, c.getLong("cofre"), TINTA);
            g.drawString(font, "Na sua bolsa:", x + 6, y + 40, CINZA, false);
            moedas(g, font, x + 6 + font.width("Na sua bolsa: "), y + 40, d.getLong("bolsa"), TINTA);
            g.drawString(font, "Últimos movimentos", x + 6, y + 54, MOLDURA, false);
            ListTag reg = c.getList("registro", Tag.TAG_STRING);
            if (reg.isEmpty()) g.drawString(font, "nenhum ainda", x + 6, y + 66, CINZA, false);
            for (int i = 0; i < reg.size() && i < 4; i++) g.drawString(font, corta(reg.getString(i), LARG - 200), x + 6, y + 66 + i * 10, TINTA, false);
            g.drawString(font, c.getBoolean("saqueLideres") ? "Saques: só líder e oficiais" : "Saques: qualquer membro", x0 + 12, y + 84, CINZA, false);
        } else if (aba == 1) {
            ListTag ms = lista(c, "lista");
            for (int i = pagina * POR_PAGINA; i < Math.min(ms.size(), (pagina + 1) * POR_PAGINA); i++) {
                CompoundTag m = ms.getCompound(i);
                int ly = y + (i - pagina * POR_PAGINA) * 16;
                g.fill(x0 + 12, ly + 4, x0 + 17, ly + 9, m.getBoolean("online") ? VERDE : CINZA);
                g.drawString(font, corta(m.getString("nick"), 74), x0 + 22, ly + 3, m.getBoolean("eu") ? OURO : MOLDURA, false);
                g.drawString(font, m.getString("cargo") + " · nv " + m.getInt("nivel"), x0 + 98, ly + 3, TINTA, false);
                if (!c.getBoolean("souLider") || m.getBoolean("eu")) moedas(g, font, x0 + 190, ly + 3, m.getLong("dinheiro") / 100 * 100, CINZA);
            }
        } else if (aba == 2) {
            ListTag ps = lista(c, "pedidosLista");
            if (!c.getBoolean("lideranca")) centro(g, "Só o líder e os oficiais veem e aceitam os pedidos.", x0 + LARG / 2, y + 20, TINTA);
            else if (ps.isEmpty()) centro(g, "Nenhum pedido no momento.", x0 + LARG / 2, y + 20, TINTA);
            for (int i = pagina * POR_PAGINA; i < Math.min(ps.size(), (pagina + 1) * POR_PAGINA); i++) {
                CompoundTag p = ps.getCompound(i);
                int ly = y + (i - pagina * POR_PAGINA) * 16;
                g.drawString(font, corta(p.getString("nick"), 110), x0 + 14, ly + 3, MOLDURA, false);
                g.drawString(font, "nível " + p.getInt("nivel"), x0 + 130, ly + 3, TINTA, false);
            }
        } else {
            if (c.getBoolean("souLider")) {
                g.drawString(font, "Nome do clã (3 a 24 letras)", x0 + 14, y + 1, CINZA, false);
                g.drawString(font, "Cor do clã (aparece aqui e no site)", x0 + 14, y + 35, CINZA, false);
                g.drawString(font, "Quem pode sacar do cofre", x0 + 14, y + 69, CINZA, false);
            } else {
                g.drawString(font, "Só o líder muda o nome, a cor e a regra de saque.", x0 + 14, y + 6, TINTA, false);
            }
            g.drawString(font, c.getBoolean("souLider") && c.getInt("membros") > 1 ? "Líder: passe a liderança antes de sair." : "O último a sair leva o cofre.", x0 + 14, y0 + ALT - 36, CINZA, false);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
