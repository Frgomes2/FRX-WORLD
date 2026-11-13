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

/**
 * Abertura do primeiro login: tela preta, as falas do Sistema aparecendo uma a uma
 * e um botão só quando o texto termina. Não dá para fechar com Esc.
 */
public class TelaAbertura extends Screen {
    private static final long POR_LETRA = 42, ENTRE_LINHAS = 650;
    private static final int AZUL = 0xFF4FC3F7, AZUL_CLARO = 0xFFB3E5FC;

    private CompoundTag d;
    private long inicio;
    private Button botao;

    public TelaAbertura(CompoundTag d) {
        super(Component.literal("Sistema"));
        this.d = d;
        this.inicio = System.currentTimeMillis();
    }

    public void atualizar(CompoundTag novo) {
        this.d = novo;
        this.inicio = System.currentTimeMillis();
        rebuildWidgets();
    }

    private ListTag linhas() { return d.getList("linhas", Tag.TAG_STRING); }

    /** Quanto tempo o texto inteiro leva para aparecer. */
    private long duracaoTotal() {
        long t = 0;
        ListTag l = linhas();
        for (int i = 0; i < l.size(); i++) t += ENTRE_LINHAS + l.getString(i).length() * POR_LETRA;
        return t;
    }

    private boolean terminou() { return System.currentTimeMillis() - inicio >= duracaoTotal(); }

    @Override
    protected void init() {
        botao = addRenderableWidget(Button.builder(Component.literal(d.getString("botao")), b -> {
            switch (d.getString("fase")) {
                // o servidor abre a tela de raças por cima desta
                case "intro" -> Rede.CANAL.sendToServer(new AcaoMenu("abertura", -1, d.getBoolean("previa") ? 2 : 0, 0));
                case "classe" -> {
                    if (d.getBoolean("previa")) Rede.CANAL.sendToServer(new AcaoMenu("abertura", -1, 2, 0));
                    else minecraft.setScreen(new TelaHabilidades());
                }
                default -> { Rede.CANAL.sendToServer(new AcaoMenu("abertura", -1, 1, 0)); onClose(); }
            }
        }).bounds(width / 2 - 80, height - 56, 160, 20).build());
        botao.visible = false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF000000);
        long t = System.currentTimeMillis() - inicio;

        ListTag l = linhas();
        int total = l.size();
        int y = height / 2 - total * 9;
        long gasto = 0;
        for (int i = 0; i < total; i++) {
            String texto = l.getString(i);
            long comeca = gasto + ENTRE_LINHAS;
            gasto = comeca + texto.length() * POR_LETRA;
            if (t < comeca) break;
            int letras = (int) Math.min(texto.length(), (t - comeca) / POR_LETRA);
            String visivel = texto.substring(0, letras);
            boolean ultima = i == total - 1;
            int cor = ultima ? AZUL : AZUL_CLARO;
            g.drawString(font, visivel, (width - font.width(visivel)) / 2, y, cor, false);
            if (letras < texto.length() && (System.currentTimeMillis() / 400) % 2 == 0)
                g.drawString(font, "_", (width + font.width(visivel)) / 2 + 1, y, AZUL, false);
            y += 18;
        }

        if (terminou() && botao != null) botao.visible = true;
        else if (botao != null) {
            String pular = "clique para adiantar";
            g.drawString(font, pular, (width - font.width(pular)) / 2, height - 30, 0xFF3A3A3A, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (!terminou()) { inicio = System.currentTimeMillis() - duracaoTotal(); return true; }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean isPauseScreen() { return false; }
}
