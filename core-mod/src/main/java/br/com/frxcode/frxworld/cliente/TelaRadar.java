package br.com.frxcode.frxworld.cliente;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * Tela do Radar do Dragão: visor redondo verde com grade; cada esfera desta dimensão é um ponto laranja.
 * Norte para cima, você no centro (a seta mostra para onde está olhando). Botões + e − trocam o alcance.
 */
public class TelaRadar extends Screen {
    private static final int R = 78;
    private static final int[] ALCANCES = {250, 1000, 4000, 16000};
    private static final int ARO = 0xFFD8DCE2, ARO_ESC = 0xFF8E949E, VISOR = 0xFF2E6B3A, GRADE = 0xFF7FBF7A, PONTO = 0xFFFFA63A, BRILHO = 0xFFFFE9A8;

    private final CompoundTag d;
    private int alcance;

    public TelaRadar(CompoundTag d) {
        super(Component.literal("Radar do Dragão"));
        this.d = d;
        // começa no menor alcance que mostra a esfera mais próxima
        double perto = Double.MAX_VALUE;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ListTag l = d.getList("pontos", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                double dx = l.getCompound(i).getDouble("x") - mc.player.getX(), dz = l.getCompound(i).getDouble("z") - mc.player.getZ();
                perto = Math.min(perto, Math.sqrt(dx * dx + dz * dz));
            }
        }
        alcance = ALCANCES.length - 1;
        for (int i = ALCANCES.length - 1; i >= 0; i--) if (perto <= ALCANCES[i]) alcance = i;
    }

    @Override
    protected void init() {
        int cx = width / 2 - 70, cy = height / 2;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> alcance = Math.min(ALCANCES.length - 1, alcance + 1)).bounds(cx - 34, cy + R + 14, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> alcance = Math.max(0, alcance - 1)).bounds(cx + 14, cy + R + 14, 20, 16).build());
    }

    private static void disco(GuiGraphics g, int cx, int cy, int r, int cor) {
        for (int dy = -r; dy <= r; dy++) {
            int m = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - m, cy + dy, cx + m + 1, cy + dy + 1, cor);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Minecraft mc = Minecraft.getInstance();
        int cx = width / 2 - 70, cy = height / 2;
        // botão de cima e aro
        g.fill(cx - 7, cy - R - 16, cx + 8, cy - R - 4, ARO_ESC);
        g.fill(cx - 5, cy - R - 20, cx + 6, cy - R - 16, ARO);
        disco(g, cx, cy, R + 8, ARO_ESC);
        disco(g, cx, cy, R + 6, ARO);
        disco(g, cx, cy, R, VISOR);
        // grade
        for (int o = -R + (R % 13); o < R; o += 13) {
            int m = (int) Math.sqrt(R * R - o * o);
            g.fill(cx + o, cy - m, cx + o + 1, cy + m, GRADE);
            g.fill(cx - m, cy + o, cx + m, cy + o + 1, GRADE);
        }
        // você, no centro: seta para onde olha
        if (mc.player != null) {
            double rad = Math.toRadians(mc.player.getYRot());
            int fx = cx + (int) Math.round(-Math.sin(rad) * 7), fy = cy + (int) Math.round(Math.cos(rad) * 7);
            g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
            g.fill(fx - 1, fy - 1, fx + 2, fy + 2, 0xFFFFFFFF);
        }
        // esferas
        double esc = (R - 6) / (double) ALCANCES[alcance];
        boolean pisca = (System.currentTimeMillis() / 400) % 2 == 0;
        ListTag l = d.getList("pontos", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size() && mc.player != null; i++) {
            CompoundTag c = l.getCompound(i);
            double dx = (c.getDouble("x") - mc.player.getX()) * esc, dz = (c.getDouble("z") - mc.player.getZ()) * esc;
            double dist = Math.sqrt(dx * dx + dz * dz);
            boolean fora = dist > R - 6;
            if (fora) { dx = dx / dist * (R - 6); dz = dz / dist * (R - 6); }
            int x = cx + (int) Math.round(dx), y = cy + (int) Math.round(dz);
            int tam = fora ? 2 : 3;
            if (!fora || pisca) {
                disco(g, x, y, tam + 1, 0x80FFA63A);
                disco(g, x, y, tam, PONTO);
                g.fill(x - 1, y - 1, x + 1, y + 1, BRILHO);
            }
            g.drawString(font, String.valueOf(c.getInt("n")), x + tam + 2, y - 4, 0xFFFFFFFF, true);
        }
        g.drawCenteredString(font, "N", cx, cy - R + 3, 0xFFE8FFE8);
        g.drawCenteredString(font, "alcance " + ALCANCES[alcance] + " blocos", cx, cy + R + 36, 0xFFCFE8CF);
        // lista ao lado
        int tx = cx + R + 22, ty = cy - 48;
        g.drawString(font, "Radar do Dragão", tx, ty - 16, 0xFF7CFF8C, true);
        ListTag linhas = d.getList("linhas", Tag.TAG_STRING);
        for (int i = 0; i < linhas.size(); i++) {
            for (net.minecraft.util.FormattedCharSequence s : font.split(Component.literal(linhas.getString(i)), Math.max(60, width - tx - 8))) {
                g.drawString(font, s, tx, ty, 0xFFFFFFFF, true);
                ty += 10;
            }
            ty += 2;
        }
        if (l.isEmpty()) g.drawCenteredString(font, "sem sinal por aqui", cx, cy + 14, 0xFFE8FFE8);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
