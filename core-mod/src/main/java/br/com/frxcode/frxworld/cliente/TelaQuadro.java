package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AbrirQuadro;
import br.com.frxcode.frxworld.rede.AcaoMissao;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.VisaoMissao;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Tela do Quadro de Missões: lista à esquerda, detalhes e ações à direita. */
public class TelaQuadro extends Screen {
    private static final int LARG = 360, ALT = 220, LINHA = 22, POR_PAGINA = 8;
    private static final int PERGAMINHO = 0xFFE9DFC8, PERGAMINHO_ESC = 0xFFD8CBAE, TINTA = 0xFF3A2A1A,
            MOLDURA = 0xFF5A3A1E, OURO = 0xFFB8892A, CINZA = 0xFF8A7D6A, VERDE = 0xFF2E7D32, VERMELHO = 0xFF9B2C2C;

    private List<VisaoMissao> missoes;
    private String mensagem;
    private int pagina = 0, selecionada = 0;
    private int x0, y0;

    public TelaQuadro(AbrirQuadro msg) {
        super(Component.literal("Quadro de Missões"));
        this.missoes = msg.missoes();
        this.mensagem = msg.mensagem();
    }

    public void atualizar(AbrirQuadro msg) {
        String idSel = sel() != null ? sel().id() : null;
        this.missoes = msg.missoes();
        this.mensagem = msg.mensagem();
        selecionada = 0;
        for (int i = 0; i < missoes.size(); i++) if (missoes.get(i).id().equals(idSel)) selecionada = i;
        pagina = Math.min(pagina, Math.max(0, (missoes.size() - 1) / POR_PAGINA));
        rebuildWidgets();
    }

    private VisaoMissao sel() {
        return selecionada >= 0 && selecionada < missoes.size() ? missoes.get(selecionada) : null;
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;

        int inicio = pagina * POR_PAGINA;
        for (int i = inicio; i < Math.min(missoes.size(), inicio + POR_PAGINA); i++) {
            final int idx = i;
            VisaoMissao m = missoes.get(i);
            int y = y0 + 26 + (i - inicio) * LINHA;
            addRenderableWidget(Button.builder(Component.literal(rotuloLista(m)), b -> { selecionada = idx; rebuildWidgets(); })
                    .bounds(x0 + 8, y, 150, LINHA - 3).build());
        }

        if (missoes.size() > POR_PAGINA) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina = Math.max(0, pagina - 1); rebuildWidgets(); })
                    .bounds(x0 + 8, y0 + ALT - 22, 20, 16).build()).active = pagina > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); })
                    .bounds(x0 + 138, y0 + ALT - 22, 20, 16).build()).active = (pagina + 1) * POR_PAGINA < missoes.size();
        }

        VisaoMissao m = sel();
        if (m != null) {
            int bx = x0 + 172, by = y0 + ALT - 28;
            switch (m.estado()) {
                case VisaoMissao.DISPONIVEL -> addRenderableWidget(Button.builder(Component.literal("Aceitar missão"),
                        b -> enviar(m, AcaoMissao.ACEITAR)).bounds(bx, by, 178, 20).build());
                case VisaoMissao.PRONTA -> addRenderableWidget(Button.builder(Component.literal("Entregar e receber"),
                        b -> enviar(m, AcaoMissao.ENTREGAR)).bounds(bx, by, 178, 20).build());
                case VisaoMissao.ATIVA -> addRenderableWidget(Button.builder(Component.literal("Abandonar"),
                        b -> enviar(m, AcaoMissao.ABANDONAR)).bounds(bx, by, 178, 20).build());
                default -> { }
            }
        }
    }

    private static String rotuloLista(VisaoMissao m) {
        String prefixo = switch (m.estado()) {
            case VisaoMissao.PRONTA -> "★ ";
            case VisaoMissao.ATIVA -> "» ";
            case VisaoMissao.BLOQUEADA -> "✖ ";
            case VisaoMissao.CONCLUIDA -> "✔ ";
            default -> "";
        };
        String t = prefixo + m.titulo();
        return t.length() > 22 ? t.substring(0, 21) + "…" : t;
    }

    private void enviar(VisaoMissao m, int acao) {
        Rede.CANAL.sendToServer(new AcaoMissao(m.id(), acao));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // moldura e pergaminho
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        g.fill(x0 + 164, y0 + 22, x0 + 165, y0 + ALT - 6, PERGAMINHO_ESC);
        g.drawCenteredString(font, "Quadro de Missões", x0 + LARG / 2, y0 + 8, OURO);

        VisaoMissao m = sel();
        int dx = x0 + 172, dy = y0 + 26, larg = LARG - 180;
        if (m == null) {
            g.drawString(font, mensagem.isEmpty() ? "Nenhuma missão." : mensagem, dx, dy, TINTA, false);
        } else {
            g.drawString(font, m.titulo(), dx, dy, TINTA, false);
            dy += 12;
            g.drawString(font, "Nível " + m.nivelMin() + "+", dx, dy, OURO, false);
            dy += 12;
            for (FormattedCharSequence l : font.split(Component.literal(m.descricao()), larg)) {
                g.drawString(font, l, dx, dy, CINZA, false);
                dy += 10;
            }
            dy += 4;
            for (FormattedCharSequence l : font.split(Component.literal("Objetivo: " + m.objetivo()), larg)) {
                g.drawString(font, l, dx, dy, TINTA, false);
                dy += 10;
            }
            if (m.estado() == VisaoMissao.ATIVA || m.estado() == VisaoMissao.PRONTA) {
                int barra = larg - 40;
                float f = Math.min(1f, (float) m.progresso() / Math.max(1, m.quantidade()));
                g.fill(dx, dy + 2, dx + barra, dy + 7, PERGAMINHO_ESC);
                g.fill(dx, dy + 2, dx + Math.round(barra * f), dy + 7, m.estado() == VisaoMissao.PRONTA ? VERDE : OURO);
                g.drawString(font, m.progresso() + "/" + m.quantidade(), dx + barra + 4, dy, TINTA, false);
                dy += 12;
            }
            dy += 2;
            for (FormattedCharSequence l : font.split(Component.literal("Recompensa: " + m.recompensa()), larg)) {
                g.drawString(font, l, dx, dy, OURO, false);
                dy += 10;
            }
            if (!m.nota().isEmpty()) {
                dy += 2;
                g.drawString(font, m.nota(), dx, dy, m.estado() == VisaoMissao.BLOQUEADA ? VERMELHO : CINZA, false);
            }
            if (!mensagem.isEmpty()) {
                g.drawString(font, mensagem, dx, y0 + ALT - 40, VERDE, false);
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
