package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Bênção da Capitã Hinata: as quatro peças vestidas, o grau de cada uma e o botão para tentar o próximo. */
public class TelaBencao extends Screen {
    private static final int LARG = 340, ALT = 210;
    private static final int FUNDO = 0xF0F4F1E6, MOLDURA = 0xFFC9A646, TITULO = 0xFF8A6A12, TEXTO = 0xFF2B2B2B, CINZA = 0xFF6E6A60,
            VERDE = 0xFF2E8B3E, VERMELHO = 0xFFB03030, AZUL = 0xFF2A6FA8;
    private static final String[] PECA = {"Elmo", "Peitoral", "Calças", "Botas"};

    private CompoundTag d;
    private int x0, y0;

    public TelaBencao(CompoundTag d) { super(Component.literal("Bênção de Hinata")); this.d = d; }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        ListTag pecas = d.getList("pecas", Tag.TAG_COMPOUND);
        for (int i = 0; i < pecas.size(); i++) {
            CompoundTag e = pecas.getCompound(i);
            if (e.getBoolean("vazio") || !e.contains("alvo")) continue;
            final int slot = i;
            String rotulo = "Grau " + romano(e.getInt("alvo")) + " · " + (e.getLong("preco") / 100) + " ouro · " + e.getInt("chance") + "%";
            Button b = Button.builder(Component.literal(rotulo), bt -> Rede.CANAL.sendToServer(new AcaoMenu("bencao", d.getInt("npc"), 1, slot)))
                    .bounds(x0 + LARG - 150, y0 + 52 + i * 34, 140, 18).build();
            String dica = "Próximo: " + e.getString("proximo") + "\nChance: " + e.getInt("chance") + "%. Se falhar, perde o pagamento e a peça fica como está."
                    + (e.getString("pede").isEmpty() ? "" : "\nPede também: " + e.getString("pede"));
            b.setTooltip(Tooltip.create(Component.literal(dica)));
            b.active = e.getBoolean("pode");
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(x0 + LARG / 2 - 40, y0 + ALT - 24, 80, 16).build());
    }

    private static String romano(int n) { return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; default -> "V"; }; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 2, y0 - 2, x0 + LARG + 2, y0 + ALT + 2, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, FUNDO);
        g.drawCenteredString(font, "✦ Bênção da Capitã Hinata ✦", x0 + LARG / 2, y0 + 8, TITULO);
        String classe = d.getString("classe");
        g.drawCenteredString(font, classe.isEmpty() ? "Escolha uma classe para receber a bênção." : "Classe " + classe + ": " + d.getString("efeito"), x0 + LARG / 2, y0 + 22, CINZA);
        g.drawString(font, "Moedas (bolsa + banco): " + (d.getLong("dinheiro") / 100) + " ouro", x0 + 10, y0 + 36, CINZA, false);
        ListTag pecas = d.getList("pecas", Tag.TAG_COMPOUND);
        for (int i = 0; i < pecas.size(); i++) {
            CompoundTag e = pecas.getCompound(i);
            int y = y0 + 50 + i * 34;
            g.fill(x0 + 8, y - 2, x0 + LARG - 8, y + 30, 0x22000000);
            if (e.getBoolean("vazio")) { g.drawString(font, PECA[i] + ": nada vestido", x0 + 32, y + 9, CINZA, false); continue; }
            g.renderItem(ItemStack.of(e.getCompound("item")), x0 + 12, y + 6);
            g.drawString(font, corta(e.getString("nome"), 150), x0 + 32, y + 3, TEXTO, false);
            g.drawString(font, corta(e.getString("estado"), 150), x0 + 32, y + 15, e.getInt("grau") > 0 ? AZUL : CINZA, false);
            if (!e.contains("alvo")) g.drawString(font, "Grau máximo", x0 + LARG - 110, y + 9, VERDE, false);
        }
        String msg = d.getString("msg");
        if (!msg.isEmpty()) g.drawCenteredString(font, msg, x0 + LARG / 2, y0 + ALT - 38, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
    }

    private String corta(String s, int max) {
        if (font.width(s) <= max) return s;
        while (s.length() > 1 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
