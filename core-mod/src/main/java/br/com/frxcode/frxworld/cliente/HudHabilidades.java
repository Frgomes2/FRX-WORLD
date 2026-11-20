package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.SyncHabilidades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Barra de combate à direita da hotbar: recurso em cima, 4 habilidades + ultimate com recarga. */
public class HudHabilidades implements IGuiOverlay {
    private static final int TAM = 20, ESP = 2;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int largura, int altura) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator() || !ClienteHabilidades.recebido) return;
        Font fonte = mc.font;
        int x0 = largura / 2 + 91 + 8, y0 = altura - TAM - 2;

        if (ClienteHabilidades.classe.isEmpty()) {
            String dica = "K: escolher classe";
            g.fill(x0 - 2, y0 + 4, x0 + fonte.width(dica) + 3, y0 + 16, 0xAA17140F);
            g.drawString(fonte, dica, x0, y0 + 6, 0xFFD4A93A, true);
            return;
        }

        // Os espaços extras do rank da Guilda ficam na mesma linha, entre os 4 comuns e a ultimate.
        // Se a tela for estreita demais para todos, voltam para uma linha acima.
        int extras = ClienteHabilidades.extras;
        boolean naLinha = x0 + (5 + extras) * (TAM + ESP) + 4 <= largura - 2;
        int larguraBarra = (naLinha ? 5 + extras : 5) * TAM + (naLinha ? 4 + extras : 4) * ESP + 4;
        // recurso
        int yr = y0 - 7;
        float frac = Math.min(1f, (float) ClienteHabilidades.recursoAtual / ClienteHabilidades.recursoMax);
        g.fill(x0 - 1, yr - 1, x0 + larguraBarra + 1, yr + 5, 0xCC17140F);
        g.fill(x0, yr, x0 + Math.round(larguraBarra * frac), yr + 4, ClienteHabilidades.corRecurso());
        String txt = ClienteHabilidades.recursoAtual + "";
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        g.drawString(fonte, ClienteHabilidades.nomeRecurso() + " " + txt + "/" + ClienteHabilidades.recursoMax,
                (x0 + 1) * 2, (yr - 5) * 2, 0xFFE9DFC8, true);
        g.pose().popPose();

        // Despertar (poder especial da classe)
        if (ClienteHabilidades.despertarLiberado && !"INVOCADOR".equals(ClienteHabilidades.classe)) {
            int yd = yr - 9, cor = ClienteHabilidades.corDespertar(ClienteHabilidades.classe);
            boolean ativo = ClienteHabilidades.despertarAtivo > 0;
            float fd = ativo ? (float) ClienteHabilidades.despertarAtivo / ClienteHabilidades.despertarTotal : ClienteHabilidades.despertar / 100f;
            boolean pronto = !ativo && ClienteHabilidades.despertar >= 100;
            boolean pisca = pronto && (System.currentTimeMillis() / 300) % 2 == 0;
            g.fill(x0 - 1, yd - 1, x0 + larguraBarra + 1, yd + 3, pisca ? 0xFFFFFFFF : 0xCC17140F);
            g.fill(x0, yd, x0 + Math.round(larguraBarra * Math.min(1f, fd)), yd + 2, ativo ? 0xFFFFFFFF : cor);
            String nomeD = ClienteHabilidades.despertarInfo(ClienteHabilidades.classe)[0];
            String rot = ativo ? "✦ " + nomeD + (ClienteHabilidades.despertarExtra > 0 && "ASSASSINO".equals(ClienteHabilidades.classe) ? " · " + ClienteHabilidades.despertarExtra + "× [" + Teclas.rotuloDespertar() + "]" : "")
                    : pronto ? "✦ " + nomeD + " PRONTO [" + Teclas.rotuloDespertar() + "]" : "✦ " + ClienteHabilidades.despertar + "%";
            g.pose().pushPose();
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(fonte, rot, (x0 + larguraBarra + 1) * 2 - fonte.width(rot), (yd - 5) * 2, pronto || ativo ? cor : 0xFFB0A590, true);
            g.pose().popPose();
        }

        for (int i = 0; i < 5 + ClienteHabilidades.extras; i++) {
            int pos = i < 4 ? i : i == 4 ? (naLinha ? 4 + extras : 4) : (naLinha ? i - 1 : i - 5);   // ordem na linha: 4 comuns, extras, ultimate
            int x = x0 + pos * (TAM + ESP) + (i == 4 ? 4 : 0), y = i < 5 || naLinha ? y0 : y0 - TAM - 24;
            boolean ult = i == 4;
            g.fill(x - 1, y - 1, x + TAM + 1, y + TAM + 1, ult ? 0xFFB8892A : 0xFF5A3A1E);
            g.fill(x, y, x + TAM, y + TAM, 0xDD17140F);
            SyncHabilidades.Info h = ClienteHabilidades.info(ClienteHabilidades.barra[i]);
            if (h != null) {
                g.renderItem(ClienteHabilidades.icone(h.icone()), x + 2, y + 2);
                float rc = ClienteHabilidades.recargaFracao(h.codigo());
                if (rc > 0) {
                    int alturaSombra = Math.round(TAM * rc);
                    g.fill(x, y + TAM - alturaSombra, x + TAM, y + TAM, 0xB0000000);
                    long ms = ClienteHabilidades.recargaRestante(h.codigo());
                    String s = ms >= 10_000 ? (ms / 1000) + "" : String.format("%.1f", ms / 1000.0);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.drawCenteredString(fonte, s, x + TAM / 2, y + 6, 0xFFFFFFFF);
                    g.pose().popPose();
                } else if (ClienteHabilidades.recursoAtual < h.custo()) {
                    g.fill(x, y, x + TAM, y + TAM, 0x803B0A0A);
                }
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(fonte, Teclas.rotulo(i), (x + 1) * 2, (y + 1) * 2, 0xFFD4A93A, true);
            g.pose().popPose();
        }
    }
}
