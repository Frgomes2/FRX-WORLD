package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.reinos.Dragoes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/**
 * Criador de Dragões: o ninho. Oito ovos em dois andares, cada um num cartão escuro com um brilho na cor da raridade
 * que pulsa devagar; o ovo escolhido cresce e ganha a descrição, o preço e onde ele choca.
 */
public class TelaDragoes extends Screen {
    private static final int LARG = 360, ALT = 236, CARTAO_L = 78, CARTAO_A = 62;
    private static final int NOITE = 0xFF1B1622, NOITE_CLARA = 0xFF2A2233;
    /** Cor de cada raridade (1 a 5): verde, azul, roxo, laranja, vermelho. */
    private static final int[] COR = {0xFF8A7D6A, 0xFF4E9A51, 0xFF3F7FD0, 0xFF9B4FD0, 0xFFE0872B, 0xFFD23A3A};

    private CompoundTag d;
    private ItemStack[] ovos = new ItemStack[0];
    private int x0, y0, escolhido = -1, tempo;

    public TelaDragoes(CompoundTag d) {
        super(Component.literal("Criador de Dragões"));
        ler(d);
    }

    private void ler(CompoundTag novo) {
        this.d = novo;
        ListTag l = tipos();
        ovos = new ItemStack[l.size()];
        for (int i = 0; i < ovos.length; i++) ovos[i] = l.getCompound(i).contains("item") ? ItemStack.of(l.getCompound(i).getCompound("item")) : ItemStack.EMPTY;
    }

    public void atualizar(CompoundTag novo) { ler(novo); rebuildWidgets(); }

    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }
    private ListTag tipos() { return d.getList("tipos", Tag.TAG_COMPOUND); }
    private int cx(int i) { return x0 + 14 + (i % 4) * (CARTAO_L + 6); }
    private int cy(int i) { return y0 + 40 + (i / 4) * (CARTAO_A + 6); }
    private int cor(int raridade) { return COR[Math.max(0, Math.min(5, raridade))]; }
    private boolean pode(int i) { return d.getBoolean("temOvos") && i >= 0 && i < tipos().size() && d.getLong("bolsa") >= tipos().getCompound(i).getLong("preco"); }
    /** Mistura a cor com o fundo escuro: f de 0 (fundo) a 1 (cor cheia). */
    private static int misturar(int cor, float f) {
        int r = (int) (0x1B + (((cor >> 16) & 255) - 0x1B) * f), v = (int) (0x16 + (((cor >> 8) & 255) - 0x16) * f), a = (int) (0x22 + ((cor & 255) - 0x22) * f);
        return 0xFF000000 | (r << 16) | (v << 8) | a;
    }

    @Override
    public void tick() { tempo++; }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        if (escolhido >= tipos().size()) escolhido = -1;
        final int qual = escolhido;
        Button comprar = addRenderableWidget(Button.builder(Component.literal("Comprar o ovo"), b -> Rede.CANAL.sendToServer(new AcaoMenu("dragoes", d.getInt("npc"), Dragoes.COMPRAR, qual)))
                .bounds(x0 + LARG - 112, y0 + ALT - 44, 100, 20).build());
        comprar.active = pode(escolhido);
        comprar.visible = escolhido >= 0;   // sem ovo escolhido o botão ficava por cima do texto de ajuda
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (super.mouseClicked(mx, my, botao)) return true;
        for (int i = 0; i < tipos().size(); i++) {
            if (mx >= cx(i) && mx < cx(i) + CARTAO_L && my >= cy(i) && my < cy(i) + CARTAO_A) {
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
        g.drawString(font, "Na sua bolsa:", x0 + 14, y0 + 24, CINZA, false);
        moedas(g, font, x0 + 14 + font.width("Na sua bolsa: "), y0 + 24, d.getLong("bolsa"), TINTA);

        ListTag l = tipos();
        float pulso = 0.5f + 0.5f * (float) Math.sin((tempo + pt) / 9.0);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            int x = cx(i), y = cy(i), c = cor(t.getInt("raridade"));
            boolean sel = i == escolhido, em = mx >= x && mx < x + CARTAO_L && my >= y && my < y + CARTAO_A;
            g.fill(x, y, x + CARTAO_L, y + CARTAO_A, sel ? c : em ? misturar(c, 0.7f) : misturar(c, 0.35f));
            g.fill(x + 1, y + 1, x + CARTAO_L - 1, y + CARTAO_A - 1, NOITE);
            // brilho do ninho: três faixas na cor da raridade, mais fortes embaixo, pulsando
            float forca = (sel ? 0.55f : em ? 0.4f : 0.22f) * (0.7f + 0.3f * pulso);
            for (int f = 0; f < 3; f++) g.fill(x + 1, y + CARTAO_A - 1 - (f + 1) * 9, x + CARTAO_L - 1, y + CARTAO_A - 1 - f * 9, misturar(c, forca * (3 - f) / 3f));
            if (i < ovos.length && !ovos[i].isEmpty()) {
                float escala = sel ? 2.4f : 2f;
                g.pose().pushPose();
                g.pose().translate(x + CARTAO_L / 2f - 8 * escala, y + 6 + (sel ? -2 : 0), 0);
                g.pose().scale(escala, escala, 1f);
                g.renderItem(ovos[i], 0, 0);
                g.pose().popPose();
            } else {
                centro(g, "?", x + CARTAO_L / 2, y + 18, c);
            }
            centro(g, corta(t.getString("nome").replace("Dragão ", ""), CARTAO_L - 6), x + CARTAO_L / 2, y + CARTAO_A - 20, 0xFFF4EDDC);
            String estrelas = "★".repeat(Math.max(1, t.getInt("raridade")));
            centro(g, estrelas, x + CARTAO_L / 2, y + CARTAO_A - 10, c);
        }

        int y = y0 + ALT - 54;
        g.fill(x0 + 12, y, x0 + LARG - 12, y + 42, MOLDURA);
        g.fill(x0 + 13, y + 1, x0 + LARG - 13, y + 41, NOITE_CLARA);
        if (escolhido >= 0 && escolhido < l.size()) {
            CompoundTag t = l.getCompound(escolhido);
            int c = cor(t.getInt("raridade"));
            g.drawString(font, t.getString("nome"), x0 + 18, y + 5, 0xFFF4EDDC, false);
            g.drawString(font, t.getString("raridadeNome"), x0 + 24 + font.width(t.getString("nome")), y + 5, c, false);
            g.drawString(font, corta(t.getString("ninho"), 214), x0 + 18, y + 17, 0xFFB9AE9A, false);
            g.drawString(font, "Preço:", x0 + 18, y + 29, 0xFFB9AE9A, false);
            moedas(g, font, x0 + 18 + font.width("Preço: "), y + 29, t.getLong("preco"), pode(escolhido) ? 0xFFF4EDDC : 0xFFE06A6A);
        } else {
            g.drawString(font, "Escolha um ovo. Quanto mais estrelas, mais raro o dragão.", x0 + 18, y + 9, 0xFFF4EDDC, false);
            g.drawString(font, "Coloque o ovo no chão e espere: ele choca do tipo que você comprou.", x0 + 18, y + 23, 0xFFB9AE9A, false);
        }
        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, corta(msg, LARG - 130), x0 + (LARG - 112) / 2, y0 + ALT - 10, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
