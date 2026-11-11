package br.com.frxcode.frxworld.cliente;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Quadro no canto superior esquerdo: nível e barra de XP do FRX-WORLD. */
public class HudNivel implements IGuiOverlay {
    private static final int LARGURA = 110;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int largura, int altura) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClienteDados.recebido) return;

        Font fonte = mc.font;
        int x = 6, y = 6;
        float progresso = Math.min(1f, (float) ClienteDados.minhaXp / ClienteDados.minhaXpProximo);

        // fundo
        g.fill(x - 3, y - 3, x + LARGURA + 3, y + 22, 0xAA17140F);
        g.fill(x - 3, y - 3, x + LARGURA + 3, y - 2, 0xFFD4A93A);

        // texto
        g.drawString(fonte, "Nível " + ClienteDados.meuNivel, x, y, 0xFFD4A93A, true);
        int xr = x + fonte.width("Nível " + ClienteDados.meuNivel) + 5;
        g.fill(xr - 1, y - 1, xr + fonte.width(ClienteDados.meuRank) + 2, y + 9, 0xFF9B2C2C);
        g.drawString(fonte, ClienteDados.meuRank, xr + 1, y, 0xFFFFFFFF, false);
        String xpTexto = ClienteDados.minhaXp + "/" + ClienteDados.minhaXpProximo;
        g.drawString(fonte, xpTexto, x + LARGURA - fonte.width(xpTexto), y + 1, 0xFFE9DFC8, false);

        // barra
        int yb = y + 12;
        g.fill(x, yb, x + LARGURA, yb + 5, 0xFF332B22);
        g.fill(x, yb, x + Math.round(LARGURA * progresso), yb + 5, 0xFFD4A93A);
        g.fill(x, yb, x + Math.round(LARGURA * progresso), yb + 1, 0xFFF2D27A);

        // rastreador de missões
        var linhas = ClienteMissoes.rastreador;
        if (!linhas.isEmpty()) {
            int ym = y + 30;
            g.fill(x - 3, ym - 3, x + LARGURA + 3, ym + 11 + linhas.size() * 10, 0x8817140F);
            g.drawString(fonte, "Missões", x, ym, 0xFFD4A93A, true);
            ym += 11;
            for (var l : linhas) {
                String titulo = l.titulo().length() > 16 ? l.titulo().substring(0, 15) + "…" : l.titulo();
                String prog = l.pronta() ? "✔" : l.progresso() + "/" + l.quantidade();
                int cor = l.pronta() ? 0xFF7CD67C : 0xFFE9DFC8;
                g.drawString(fonte, titulo, x, ym, cor, false);
                g.drawString(fonte, prog, x + LARGURA - fonte.width(prog), ym, cor, false);
                ym += 10;
            }
        }
    }
}
