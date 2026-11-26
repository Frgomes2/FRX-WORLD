package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.criaturas.Pokebola;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Balcão do Treinador de Criaturas: quantas pokébolas o jogador já comprou, o preço da próxima e o botão de comprar. */
public class TelaPokebola extends Screen {
    private static final int LARG = 280, ALT = 170;
    private static final int FUNDO = 0xF01C2A22, MOLDURA = 0xFF6FBF73, TITULO = 0xFFFFD866, TEXTO = 0xFFEAF3EA, CINZA = 0xFF9FB3A2,
            VERDE = 0xFF7EE07E, VERMELHO = 0xFFFF7A7A;

    private CompoundTag d;
    private int x0, y0;

    public TelaPokebola(CompoundTag d) { super(Component.literal("Treinador de Criaturas")); this.d = d; }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        int n = d.getInt("compradas"), max = d.getInt("maximo");
        long preco = d.getLong("preco");
        Button b = Button.builder(Component.literal(n >= max ? "Limite atingido" : "Comprar a " + (n + 1) + "ª · " + ouro(preco)),
                bt -> Rede.CANAL.sendToServer(new AcaoMenu("pokebola", d.getInt("npc"), Pokebola.COMPRAR, 0))).bounds(x0 + LARG / 2 - 90, y0 + ALT - 52, 180, 20).build();
        b.active = n < max && d.getLong("dinheiro") >= preco;
        addRenderableWidget(b);
        addRenderableWidget(Button.builder(Component.literal("Fechar"), bt -> onClose()).bounds(x0 + LARG / 2 - 40, y0 + ALT - 26, 80, 16).build());
    }

    private static String ouro(long cobre) { return (cobre / 100) + " ouro"; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 2, y0 - 2, x0 + LARG + 2, y0 + ALT + 2, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, FUNDO);
        g.drawCenteredString(font, "Treinador de Criaturas", x0 + LARG / 2, y0 + 8, TITULO);
        g.renderItem(new ItemStack(Pokebola.POKEBOLA.get()), x0 + LARG / 2 - 8, y0 + 22);
        int n = d.getInt("compradas"), max = d.getInt("maximo");
        g.drawCenteredString(font, "Suas pokébolas: " + n + " de " + max, x0 + LARG / 2, y0 + 44, TEXTO);
        g.drawCenteredString(font, "Captura bichos pacíficos e os bichos que você domou.", x0 + LARG / 2, y0 + 58, CINZA);
        g.drawCenteredString(font, "Cada uma fica presa a você e custa mais que a anterior.", x0 + LARG / 2, y0 + 70, CINZA);
        g.drawCenteredString(font, "Moedas (bolsa + banco): " + ouro(d.getLong("dinheiro")), x0 + LARG / 2, y0 + 84, CINZA);
        String msg = d.getString("msg");
        if (!msg.isEmpty()) g.drawCenteredString(font, msg, x0 + LARG / 2, y0 + 100, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
