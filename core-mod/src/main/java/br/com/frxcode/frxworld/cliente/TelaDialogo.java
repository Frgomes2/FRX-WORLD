package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Caixa de fala do NPC: retrato à esquerda, texto no meio e as respostas embaixo. */
public class TelaDialogo extends Screen {
    private static final int LARG = 340, ALT = 150, RETRATO = 76;

    private CompoundTag d;
    private int x0, y0;
    private long aberto;

    public TelaDialogo(CompoundTag d) {
        super(Component.literal("Conversa"));
        this.d = d;
        this.aberto = System.currentTimeMillis();
    }

    public void atualizar(CompoundTag novo) {
        this.d = novo;
        this.aberto = System.currentTimeMillis();
        rebuildWidgets();
    }

    private ListTag opcoes() { return d.getList("opcoes", Tag.TAG_COMPOUND); }
    private void enviar(int i) { Rede.CANAL.sendToServer(new AcaoMenu("dialogo", d.getInt("npc"), 0, i)); }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = height - ALT - 24;
        ListTag o = opcoes();
        int y = y0 + 68;
        for (int i = 0; i < o.size(); i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(o.getCompound(i).getString("texto")), b -> {
                if (o.getCompound(idx).getString("id").equals("sair")) onClose(); else enviar(idx);
            }).bounds(x0 + RETRATO + 18, y, LARG - RETRATO - 28, 18).build());
            y += 21;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // sem escurecer a tela: a conversa acontece "no mundo"
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);

        // retrato: o próprio NPC, olhando para o cursor
        int rx = x0 + 10, ry = y0 + 10;
        g.fill(rx, ry, rx + RETRATO, ry + RETRATO, PERGAMINHO_ESC);
        g.fill(rx + 1, ry + 1, rx + RETRATO - 1, ry + RETRATO - 1, CAIXA);
        Minecraft mc = Minecraft.getInstance();
        Entity npc = mc.level == null ? null : mc.level.getEntity(d.getInt("npc"));
        if (npc instanceof LivingEntity le) {
            g.enableScissor(rx + 1, ry + 1, rx + RETRATO - 1, ry + RETRATO - 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, rx + RETRATO / 2, ry + RETRATO - 8, 34,
                    (rx + RETRATO / 2f) - mx, (ry + RETRATO / 2f - 16) - my, le);
            g.disableScissor();
        } else {
            g.drawString(font, "?", rx + RETRATO / 2 - 3, ry + RETRATO / 2 - 4, CINZA, false);
        }

        int tx = x0 + RETRATO + 18, larg = LARG - RETRATO - 28;
        g.drawString(font, d.getString("nome"), tx, y0 + 10, MOLDURA, false);
        if (!d.getString("reino").isEmpty())
            g.drawString(font, d.getString("reino"), x0 + LARG - 10 - font.width(d.getString("reino")), y0 + 10, CINZA, false);
        g.fill(tx, y0 + 21, x0 + LARG - 10, y0 + 22, PERGAMINHO_ESC);

        // texto que aparece letra a letra
        String texto = d.getString("texto");
        int visiveis = (int) Math.min(texto.length(), (System.currentTimeMillis() - aberto) / 18);
        List<FormattedCharSequence> linhas = font.split(Component.literal(texto.substring(0, visiveis)), larg);
        int y = y0 + 27;
        for (FormattedCharSequence l : linhas) {
            g.drawString(font, l, tx, y, TINTA, false);
            y += 10;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        // clicar no texto completa a digitação
        String texto = d.getString("texto");
        if ((System.currentTimeMillis() - aberto) / 18 < texto.length()) { aberto = 0; return true; }
        return super.mouseClicked(mx, my, botao);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
