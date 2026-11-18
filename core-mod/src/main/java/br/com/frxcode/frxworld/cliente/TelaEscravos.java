package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.reinos.Escravos;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.HashMap;
import java.util.Map;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Mercador de Escravos: um cartão por tipo de aldeão (a roupa muda com o bioma), com o boneco, o preço e a exigência de rank. */
public class TelaEscravos extends Screen {
    private static final int LARG = 372, ALT = 236, CARTAO_L = 48, CARTAO_A = 104;

    private CompoundTag d;
    private int x0, y0, escolhido = -1;
    private final Map<String, Villager> bonecos = new HashMap<>();

    public TelaEscravos(CompoundTag d) {
        super(Component.literal("Mercador de Escravos"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }
    private ListTag tipos() { return d.getList("tipos", Tag.TAG_COMPOUND); }
    private int cx(int i) { return x0 + 9 + i * (CARTAO_L + 3); }
    private int cy() { return y0 + 46; }
    private boolean cheio() { return d.getInt("tem") >= d.getInt("limite"); }
    private boolean pode() { return d.getBoolean("podeRank") && !cheio() && d.getLong("bolsa") >= d.getLong("preco"); }

    /** O aldeão de cada tipo, criado uma vez só para ser desenhado no cartão. */
    private Villager boneco(String tipo) {
        if (bonecos.containsKey(tipo)) return bonecos.get(tipo);
        Villager v = null;
        try {
            if (minecraft != null && minecraft.level != null) {
                v = EntityType.VILLAGER.create(minecraft.level);
                if (v != null) {
                    v.setVillagerData(v.getVillagerData().setType(BuiltInRegistries.VILLAGER_TYPE.get(new ResourceLocation(tipo))));
                    v.setNoAi(true);
                }
            }
        } catch (Throwable t) { v = null; }
        bonecos.put(tipo, v);
        return v;
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        if (escolhido >= tipos().size()) escolhido = -1;
        final int qual = escolhido;
        addRenderableWidget(Button.builder(Component.literal("Comprar"), b -> Rede.CANAL.sendToServer(new AcaoMenu("escravos", d.getInt("npc"), Escravos.COMPRAR, qual)))
                .bounds(x0 + LARG - 92, y0 + ALT - 44, 80, 20).build()).active = escolhido >= 0 && pode();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (super.mouseClicked(mx, my, botao)) return true;
        for (int i = 0; i < tipos().size(); i++) {
            if (mx >= cx(i) && mx < cx(i) + CARTAO_L && my >= cy() && my < cy() + CARTAO_A) {
                escolhido = i;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, d.getString("nome"), x0 + LARG / 2, y0 + 8, MOLDURA);
        g.drawString(font, "Na sua bolsa:", x0 + 12, y0 + 28, CINZA, false);
        moedas(g, font, x0 + 12 + font.width("Na sua bolsa: "), y0 + 28, d.getLong("bolsa"), TINTA);
        String exige = d.getBoolean("podeRank") ? "Seus aldeões: " + d.getInt("tem") + " de " + d.getInt("limite") : "Exige Rank " + d.getString("rank") + " na Guilda";
        g.drawString(font, exige, x0 + LARG - 30 - font.width(exige), y0 + 28, d.getBoolean("podeRank") && !cheio() ? CINZA : VERMELHO, false);
        ListTag l = tipos();
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            int x = cx(i), y = cy();
            boolean sel = i == escolhido, em = mx >= x && mx < x + CARTAO_L && my >= y && my < y + CARTAO_A;
            g.fill(x, y, x + CARTAO_L, y + CARTAO_A, sel ? OURO : em ? MOLDURA : PERGAMINHO_ESC);
            g.fill(x + 1, y + 1, x + CARTAO_L - 1, y + CARTAO_A - 1, CAIXA);
            Villager v = boneco(t.getString("tipo"));
            if (v != null) {
                try { InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + CARTAO_L / 2, y + 78, 30, (float) (x + CARTAO_L / 2) - mx, (float) (y + 30) - my, v); }
                catch (Throwable erro) { bonecos.put(t.getString("tipo"), null); }
            }
            centro(g, corta(t.getString("nome"), CARTAO_L - 4), x + CARTAO_L / 2, y + 90, MOLDURA);
        }
        int y = y0 + ALT - 76;
        cartao(g, x0 + 10, y, LARG - 20, 56);
        if (escolhido >= 0 && escolhido < l.size()) {
            g.drawString(font, "Aldeão de " + l.getCompound(escolhido).getString("nome") + ", sem profissão", x0 + 16, y + 5, MOLDURA, false);
            g.drawString(font, "Preço:", x0 + 16, y + 18, TINTA, false);
            moedas(g, font, x0 + 16 + font.width("Preço: "), y + 18, d.getLong("preco"), d.getLong("bolsa") >= d.getLong("preco") ? TINTA : VERMELHO);
            String aviso = !d.getBoolean("podeRank") ? "Você ainda não tem o rank exigido." : cheio() ? "Você já tem o máximo do seu rank. Suba de rank na Guilda para ter mais." : d.getLong("bolsa") < d.getLong("preco") ? "Faltam moedas na bolsa." : "Você recebe um contrato: use no chão para trazer o aldeão.";
            g.drawString(font, corta(aviso, 250), x0 + 16, y + 31, pode() ? CINZA : VERMELHO, false);
            g.drawString(font, corta("Com uma bancada de trabalho por perto, ele aprende o ofício.", 250), x0 + 16, y + 42, CINZA, false);
        } else {
            g.drawString(font, "Escolha um aldeão. Todos vêm sem profissão; muda só a roupa.", x0 + 16, y + 12, TINTA, false);
            g.drawString(font, "Depois, uma bancada de trabalho por perto dá o ofício a ele.", x0 + 16, y + 26, CINZA, false);
        }
        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, corta(msg, LARG - 20), x0 + LARG / 2, y0 + ALT - 14, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
