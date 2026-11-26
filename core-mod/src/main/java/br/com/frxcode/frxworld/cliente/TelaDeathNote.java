package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoHabilidade;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Páginas do Death Note: lista dos jogadores online e a confirmação antes de escrever o nome. */
public class TelaDeathNote extends Screen {
    private static final int LARG = 300, ALT = 220;
    private static final int FUNDO = 0xF4080808, MOLDURA = 0xFF5A0000, BRANCO = 0xFFEDEDED, CINZA = 0xFF8C8C8C, VERMELHO = 0xFFD03030;

    private final CompoundTag d;
    private int x0, y0;
    private String escolhido = "";

    public TelaDeathNote(CompoundTag d) {
        super(Component.literal("Death Note"));
        this.d = d;
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        if (!escolhido.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("✎ Escrever o nome"), b -> {
                Rede.CANAL.sendToServer(new AcaoHabilidade(AcaoHabilidade.DEATH_NOTE, 0, escolhido));
                onClose();
            }).bounds(x0 + LARG / 2 - 124, y0 + ALT - 50, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Voltar"), b -> { escolhido = ""; rebuildWidgets(); }).bounds(x0 + LARG / 2 + 4, y0 + ALT - 50, 120, 20).build());
            return;
        }
        ListTag nomes = d.getList("nomes", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(14, nomes.size()); i++) {
            final String nome = nomes.getString(i);
            addRenderableWidget(Button.builder(Component.literal(nome), b -> { escolhido = nome; rebuildWidgets(); })
                    .bounds(x0 + 20 + (i % 2) * 134, y0 + 58 + (i / 2) * 20, 126, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Fechar o caderno"), b -> onClose()).bounds(x0 + LARG / 2 - 60, y0 + ALT - 26, 120, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(x0 - 2, y0 - 2, x0 + LARG + 2, y0 + ALT + 2, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, FUNDO);
        g.drawCenteredString(font, "D E A T H   N O T E", x0 + LARG / 2, y0 + 10, BRANCO);
        g.fill(x0 + 20, y0 + 22, x0 + LARG - 20, y0 + 23, MOLDURA);
        if (!escolhido.isEmpty()) {
            g.drawCenteredString(font, "Você vai escrever:", x0 + LARG / 2, y0 + 60, CINZA);
            g.drawCenteredString(font, escolhido, x0 + LARG / 2, y0 + 78, VERMELHO);
            g.drawCenteredString(font, "A Morte vem buscar essa pessoa e só para", x0 + LARG / 2, y0 + 98, BRANCO);
            g.drawCenteredString(font, "quando levar ela ou for derrotada.", x0 + LARG / 2, y0 + 108, BRANCO);
            g.drawCenteredString(font, "O caderno vira cinzas. Ninguém saberá quem escreveu.", x0 + LARG / 2, y0 + 124, CINZA);
        } else {
            g.drawCenteredString(font, "Escolha de quem é o nome. Só cabe um.", x0 + LARG / 2, y0 + 30, CINZA);
            String msg = d.getString("msg");
            if (!msg.isEmpty()) g.drawCenteredString(font, msg, x0 + LARG / 2, y0 + 43, VERMELHO);
            if (d.getList("nomes", Tag.TAG_STRING).isEmpty()) g.drawCenteredString(font, "Não há mais ninguém online.", x0 + LARG / 2, y0 + 90, CINZA);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
