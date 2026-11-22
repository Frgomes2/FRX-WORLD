package br.com.frxcode.frxworld.cliente;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Diário do Enigma: um livro aberto com capa de couro. Na página da esquerda, um marcador de fita colorida para cada
 * enigma, com o estado (aguardando, em andamento, desvendado) e seis contas de progresso; na da direita, o enigma
 * escolhido: contagem até o início, pistas já descobertas (a atual em destaque, as antigas apagadas) e quem venceu.
 * Só chegam aqui as pistas que o jogador já descobriu.
 */
public class TelaDiarioEnigma extends Screen {
    private static final int LARG = 340, ALT = 214, PAG_L = 156;
    private static final int COURO = 0xFF4A2316, COURO_CLARO = 0xFF6B3523, PAPEL = 0xFFF1E6C8, PAPEL_SOMBRA = 0xFFE2D3AE, TINTA = 0xFF2B1D14,
            TINTA_CLARA = 0xFF8A7458, ROXO = 0xFF6A3D9A, OURO = 0xFFB8892A, VERDE = 0xFF2E7D32, VERMELHO = 0xFF9B2C2C, BRASA = 0xFFC0562B;
    /** Cor da fita de cada marcador, na ordem. */
    private static final int[] FITAS = {0xFF7E57C2, 0xFF1E88E5, 0xFFD81B60, 0xFF43A047, 0xFFF4A300, 0xFF00897B};

    private final ListTag enigmas;
    private final long recebido = System.currentTimeMillis();
    private int x0, y0, escolhido, rolagem, tempo;

    public TelaDiarioEnigma(CompoundTag d) {
        super(Component.literal("Diário do Enigma"));
        enigmas = d.getList("enigmas", Tag.TAG_COMPOUND);
        // abre no primeiro enigma em andamento, se houver
        for (int i = 0; i < enigmas.size(); i++) if (enigmas.getCompound(i).getInt("estado") == 1) { escolhido = i; break; }
    }

    private boolean dentro(double mx, double my, int x, int y, int l, int a) { return mx >= x && mx < x + l && my >= y && my < y + a; }
    private int px() { return x0 + 12; }                    // página da esquerda
    private int dx() { return x0 + LARG / 2 + 6; }          // página da direita
    private int marcadorY(int i) { return y0 + 36 + i * 38; }

    private String falta(long ms) {
        long min = Math.max(1, (ms - (System.currentTimeMillis() - recebido) + 59_999) / 60_000);
        if (min < 60) return min + (min == 1 ? " minuto" : " minutos");
        long h = min / 60, m = min % 60;
        if (h >= 24) { long dias = h / 24; h %= 24; return dias + (dias == 1 ? " dia" : " dias") + (h > 0 ? " e " + h + "h" : ""); }
        return h + "h" + (m > 0 ? String.format("%02d", m) : "");
    }

    @Override
    public void tick() { tempo++; }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(x0 + LARG / 2 - 30, y0 + ALT + 6, 60, 18).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        for (int i = 0; i < enigmas.size(); i++)
            if (dentro(mx, my, px(), marcadorY(i), PAG_L - 6, 34)) { escolhido = i; rolagem = 0; return true; }
        return super.mouseClicked(mx, my, botao);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        rolagem = Math.max(0, rolagem - (int) Math.signum(delta) * 10);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // capa de couro e as duas páginas
        g.fill(x0 - 6, y0 - 6, x0 + LARG + 6, y0 + ALT + 4, COURO);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 2, COURO_CLARO);
        g.fill(x0, y0, x0 + LARG / 2 - 1, y0 + ALT, PAPEL);
        g.fill(x0 + LARG / 2 + 1, y0, x0 + LARG, y0 + ALT, PAPEL);
        for (int k = 0; k < 6; k++) {   // sombra da lombada
            int a = 0x30 - k * 6;
            g.fill(x0 + LARG / 2 - 1 - k * 2, y0, x0 + LARG / 2 - 1 - k * 2 + 2, y0 + ALT, (a << 24) | 0x5A3A1E);
            g.fill(x0 + LARG / 2 + 1 + k * 2, y0, x0 + LARG / 2 + 3 + k * 2, y0 + ALT, (a << 24) | 0x5A3A1E);
        }
        g.fill(x0 + LARG / 2 - 1, y0, x0 + LARG / 2 + 1, y0 + ALT, 0xFF8B6B45);

        // página da esquerda: título e marcadores
        String tit = "✦ Diário do Enigma ✦";
        g.drawString(font, tit, x0 + LARG / 4 - font.width(tit) / 2, y0 + 10, ROXO, false);
        g.fill(px(), y0 + 22, px() + PAG_L - 10, y0 + 23, PAPEL_SOMBRA);
        if (enigmas.isEmpty()) {
            g.drawString(font, "As páginas estão em branco.", px(), y0 + 40, TINTA_CLARA, false);
        }
        for (int i = 0; i < enigmas.size(); i++) {
            CompoundTag e = enigmas.getCompound(i);
            int y = marcadorY(i), fita = FITAS[i % FITAS.length], estado = e.getInt("estado");
            boolean sel = i == escolhido, em = dentro(mx, my, px(), y, PAG_L - 6, 34);
            if (sel || em) g.fill(px() - 2, y - 2, px() + PAG_L - 8, y + 34, sel ? 0x40B8892A : 0x20B8892A);
            g.fill(px(), y, px() + 4, y + 30, fita);                                  // fita
            g.fill(px() + 1, y + 30, px() + 3, y + 33, fita);                         // ponta da fita
            g.drawString(font, e.getString("nome"), px() + 9, y + 2, sel ? TINTA : 0xFF4A3A2A, false);
            String st = switch (estado) {
                case 0 -> "⌛ começa em " + falta(e.getLong("faltaMs"));
                case 2 -> "✔ desvendado por " + e.getString("vencedor");
                case 3 -> "✖ não responde mais a você";
                default -> e.getBoolean("guardiao") ? "☠ o guardião espera" : "✦ em andamento";
            };
            int cor = switch (estado) { case 0 -> TINTA_CLARA; case 2 -> OURO; case 3 -> VERMELHO; default -> e.getBoolean("guardiao") ? BRASA : VERDE; };
            g.drawString(font, font.plainSubstrByWidth(st, PAG_L - 20), px() + 9, y + 12, cor, false);
            // contas de progresso
            int total = Math.max(1, e.getInt("total")), feitos = estado == 2 ? total : e.getInt("atual");
            for (int k = 0; k < total; k++) {
                int cx = px() + 9 + k * 10, cy = y + 23;
                boolean cheio = k < feitos, atual = estado == 1 && k == feitos;
                g.fill(cx, cy, cx + 7, cy + 7, cheio ? fita : atual ? 0xFFE9D9A8 : 0xFFD8C9A3);
                if (atual && (tempo / 8) % 2 == 0) g.fill(cx + 2, cy + 2, cx + 5, cy + 5, fita);
                g.fill(cx, cy, cx + 7, cy + 1, 0x40000000);
            }
            String frac = feitos + "/" + total;
            g.drawString(font, frac, px() + 9 + total * 10 + 4, y + 23, TINTA_CLARA, false);
        }
        String rodape = "Só o primeiro a chegar ao fim";
        String rodape2 = "de cada enigma leva o tesouro.";
        g.drawString(font, rodape, x0 + LARG / 4 - font.width(rodape) / 2, y0 + ALT - 24, TINTA_CLARA, false);
        g.drawString(font, rodape2, x0 + LARG / 4 - font.width(rodape2) / 2, y0 + ALT - 14, TINTA_CLARA, false);

        // página da direita: o enigma escolhido
        if (escolhido >= 0 && escolhido < enigmas.size()) pagina(g, enigmas.getCompound(escolhido), FITAS[escolhido % FITAS.length]);
        super.render(g, mx, my, pt);
    }

    private void pagina(GuiGraphics g, CompoundTag e, int fita) {
        int x = dx(), w = PAG_L - 6, topo = y0 + 10, fim = y0 + ALT - 10;
        g.fill(x + w - 10, y0, x + w - 4, y0 + 18, fita);   // fita pendurada no canto
        g.drawString(font, e.getString("nome"), x, topo, TINTA, false);
        g.fill(x, topo + 12, x + w - 14, topo + 13, fita);
        int estado = e.getInt("estado");
        List<FormattedCharSequence> linhas = new ArrayList<>();
        List<Integer> cores = new ArrayList<>();
        java.util.function.BiConsumer<String, Integer> escrever = (t, c) -> {
            for (FormattedCharSequence l : font.split(Component.literal(t), w - 4)) { linhas.add(l); cores.add(c); }
        };
        switch (estado) {
            case 0 -> {
                escrever.accept("Desperta " + e.getString("quando") + ".", ROXO);
                escrever.accept("Faltam " + falta(e.getLong("faltaMs")) + ". Até lá, nada responde.", TINTA_CLARA);
                escrever.accept(" ", TINTA);
            }
            case 2 -> {
                escrever.accept("Desvendado por " + e.getString("vencedor") + ".", OURO);
                escrever.accept("O tesouro deste enigma já tem dono.", TINTA_CLARA);
                escrever.accept(" ", TINTA);
            }
            case 3 -> {
                escrever.accept("Você já carrega uma relíquia deste conjunto. Este enigma não responde mais a você.", VERMELHO);
                escrever.accept(" ", TINTA);
            }
            default -> {
                escrever.accept("Passo " + (e.getInt("atual") + 1) + " de " + e.getInt("total") + ".", VERDE);
                if (e.getBoolean("guardiao")) escrever.accept("No fim do caminho, um guardião espera. Só quem o derrotar verá o tesouro.", BRASA);
                escrever.accept(" ", TINTA);
            }
        }
        ListTag pistas = e.getList("pistas", Tag.TAG_STRING);
        for (int i = 0; i < pistas.size(); i++) {
            boolean atual = i == pistas.size() - 1 && estado != 2;
            escrever.accept((atual ? "➤ Pista " : "Pista ") + (i + 1), atual ? ROXO : TINTA_CLARA);
            escrever.accept(pistas.getString(i), atual ? TINTA : 0xFF9C8A70);
            escrever.accept(" ", TINTA);
        }
        int y = topo + 18, alturaMax = fim - y;
        int total = linhas.size() * 10;
        rolagem = Math.max(0, Math.min(rolagem, Math.max(0, total - alturaMax)));
        g.enableScissor(x, y, x + w, fim);
        for (int i = 0; i < linhas.size(); i++) {
            int ly = y + i * 10 - rolagem;
            if (ly > fim || ly < y - 10) continue;
            g.drawString(font, linhas.get(i), x, ly, cores.get(i), false);
        }
        g.disableScissor();
        if (total > alturaMax) {   // barrinha de rolagem
            int bh = Math.max(10, alturaMax * alturaMax / total), by = y + (alturaMax - bh) * rolagem / Math.max(1, total - alturaMax);
            g.fill(x + w - 2, by, x + w, by + bh, 0x60000000);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
