package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AvisoSistema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Janela de "Sistema": caixa azul no topo da tela com os avisos importantes, um de cada vez. */
public class HudSistema implements IGuiOverlay {
    private static final long DURACAO = 5200, ENTRADA = 250, SAIDA = 400;
    private static final int[] CORES = {0xFF4FC3F7, 0xFFFFD54F, 0xFF81C784, 0xFFE57373, 0xFFBA68C8};

    private static final Deque<AvisoSistema> FILA = new ArrayDeque<>();
    private static AvisoSistema atual;
    private static long inicio;

    public static void adicionar(AvisoSistema a) {
        synchronized (FILA) { if (FILA.size() < 8) FILA.add(a); }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int largura, int altura) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long agora = System.currentTimeMillis();
        if (atual == null || agora - inicio > DURACAO) {
            synchronized (FILA) { atual = FILA.poll(); }
            if (atual == null) return;
            inicio = agora;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 1f));
        }
        if (mc.options.hideGui) return;
        long t = agora - inicio;
        float f = t < ENTRADA ? t / (float) ENTRADA : t > DURACAO - SAIDA ? Math.max(0f, (DURACAO - t) / (float) SAIDA) : 1f;

        Font font = mc.font;
        int cor = CORES[Math.max(0, Math.min(CORES.length - 1, atual.cor()))];
        int larg = Math.min(260, largura - 40);
        List<FormattedCharSequence> linhas = font.split(Component.literal(atual.texto()), larg - 20);
        int alt = 34 + linhas.size() * 10;
        int x = (largura - larg) / 2, y = Math.round(-alt + (alt + 26) * f);
        int a = Math.round(0xE0 * f) << 24, aTexto = Math.max(4, Math.round(0xFF * f)) << 24;

        g.fill(x, y, x + larg, y + alt, a | 0x0B1626);
        g.fill(x, y, x + larg, y + 1, aTexto | (cor & 0xFFFFFF));
        g.fill(x, y + alt - 1, x + larg, y + alt, aTexto | (cor & 0xFFFFFF));
        g.fill(x, y, x + 1, y + alt, aTexto | (cor & 0xFFFFFF));
        g.fill(x + larg - 1, y, x + larg, y + alt, aTexto | (cor & 0xFFFFFF));
        String cab = "[ SISTEMA ]";
        g.drawString(font, cab, x + (larg - font.width(cab)) / 2, y + 5, aTexto | (cor & 0xFFFFFF), false);
        g.drawString(font, atual.titulo(), x + (larg - font.width(atual.titulo())) / 2, y + 17, aTexto | 0xFFFFFF, true);
        int ly = y + 30;
        for (FormattedCharSequence l : linhas) {
            g.drawString(font, l, x + (larg - font.width(l)) / 2, ly, aTexto | 0xB3E5FC, false);
            ly += 10;
        }
    }
}
