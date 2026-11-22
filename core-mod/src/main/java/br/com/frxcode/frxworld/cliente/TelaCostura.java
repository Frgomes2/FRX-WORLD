package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoHabilidade;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.reinos.Costureira;
import br.com.frxcode.frxworld.reinos.MenuCostura;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.moedas;

/**
 * Costureira: o ateliê. Fundo de linho com uma costura tracejada na borda e abas de fita no topo: Bolsos, Passar bolsos,
 * Remendos, Bordados e Disfarce. À esquerda, o espaço da peça e o da segunda calça (só usado em Passar bolsos);
 * à direita, o que cada aba faz com elas, sempre com o preço antes de confirmar.
 */
public class TelaCostura extends AbstractContainerScreen<MenuCostura> {
    private static final int LINHO = 0xFFEFE3CC, LINHO_ESC = 0xFFD9C7A5, LINHA = 0xFF8B5E3C, TINTA = 0xFF3B2A1E, TINTA_CLARA = 0xFF8A7458,
            ROSA = 0xFFC0567A, VERDE_OK = 0xFF2E7D32, VERMELHO_OK = 0xFF9B2C2C, ESPACO = 0xFFCDB894;
    private static final String[] ABAS = {"Bolsos", "Passar", "Remendos", "Bordados", "Disfarce"};
    private static final int[] CORES_ABA = {0xFFC0567A, 0xFF7E57C2, 0xFF43A047, 0xFFF4A300, 0xFF455A64};
    private static final int CX = 52;   // início do conteúdo à direita

    private CompoundTag d;
    private int aba, padrao, material, cor = 1, tempo;
    private EditBox campoNome;
    private String rascunho = "";

    public TelaCostura(MenuCostura menu, Inventory inv, Component titulo) {
        super(menu, inv, titulo);
        this.d = menu.info;
        imageWidth = 300;
        imageHeight = 246;
        inventoryLabelX = MenuCostura.INV_X;
        inventoryLabelY = MenuCostura.INV_Y - 11;
    }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    private ItemStack peca() { return menu.getSlot(MenuCostura.PECA).getItem(); }
    private ItemStack segunda() { return menu.getSlot(MenuCostura.SEGUNDA).getItem(); }
    private long dinheiro() { return d.getLong("dinheiro"); }
    private ListTag padroes() { return d.getList("padroes", Tag.TAG_COMPOUND); }
    private ListTag materiais() { return d.getList("materiais", Tag.TAG_COMPOUND); }
    private Component nomeJson(CompoundTag t) {
        try { Component c = Component.Serializer.fromJson(t.getString("nome")); return c == null ? Component.literal(t.getString("id")) : c; }
        catch (Exception e) { return Component.literal(t.getString("id")); }
    }
    private boolean reliquia(ItemStack s) { return s.hasTag() && s.getTag().contains("frx_codex"); }
    private boolean aceitaAcabamento(ItemStack s) { return !s.isEmpty() && s.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR) && !reliquia(s); }
    private void enviar(int acao, long valor) { Rede.CANAL.sendToServer(new AcaoMenu("costureira", menu.npc, acao, valor)); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }

    // ------------------------------------------------------------------ botões de cada aba

    @Override
    protected void init() {
        super.init();
        if (campoNome != null) rascunho = campoNome.getValue();
        campoNome = null;
        int x = leftPos, y = topPos;
        for (int i = 0; i < ABAS.length; i++) {
            final int k = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(ABAS[i]), bt -> { aba = k; rebuildWidgets(); })
                    .bounds(x + 8 + i * 57, y + 18, 55, 16).build());
            b.active = aba != i;
        }
        int bx = x + CX, by = y + 112;
        switch (aba) {
            case 0 -> {
                addRenderableWidget(Button.builder(Component.literal("+1 bolso"), b -> enviar(Costureira.COSTURAR, 1)).bounds(bx, by, 80, 18).build());
                addRenderableWidget(Button.builder(Component.literal("Completar"), b -> enviar(Costureira.COSTURAR, d.getInt("maximo"))).bounds(bx + 84, by, 80, 18).build());
            }
            case 1 -> addRenderableWidget(Button.builder(Component.literal("Passar bolsos"), b -> enviar(Costureira.PASSAR, 0)).bounds(bx, by, 110, 18).build());
            case 2 -> addRenderableWidget(Button.builder(Component.literal("Remendar"), b -> enviar(Costureira.REMENDAR, 0)).bounds(bx, by, 110, 18).build());
            case 3 -> {
                int n = Math.max(1, padroes().size()), m = Math.max(1, materiais().size());
                addRenderableWidget(Button.builder(Component.literal("<"), b -> padrao = (padrao + n - 1) % n).bounds(bx, y + 50, 14, 14).build());
                addRenderableWidget(Button.builder(Component.literal(">"), b -> padrao = (padrao + 1) % n).bounds(bx + 150, y + 50, 14, 14).build());
                addRenderableWidget(Button.builder(Component.literal("<"), b -> material = (material + m - 1) % m).bounds(bx, y + 66, 14, 14).build());
                addRenderableWidget(Button.builder(Component.literal(">"), b -> material = (material + 1) % m).bounds(bx + 150, y + 66, 14, 14).build());
                addRenderableWidget(Button.builder(Component.literal("Bordar"), b -> enviar(Costureira.ACABAMENTO, padrao * 1000L + material)).bounds(bx + 168, y + 50, 44, 14).build());
                addRenderableWidget(Button.builder(Component.literal("Tirar"), b -> enviar(Costureira.ACABAMENTO, -1)).bounds(bx + 168, y + 66, 44, 14).build());
                campoNome = new EditBox(font, bx + 1, y + 101, 120, 14, Component.literal("Nome"));
                campoNome.setMaxLength(30);
                campoNome.setValue(rascunho);
                campoNome.setHint(Component.literal("nome da peça").withStyle(ChatFormatting.GRAY));
                addRenderableWidget(campoNome);
                addRenderableWidget(Button.builder(Component.literal("Cor"), b -> cor = (cor + 1) % Costureira.CORES.length).bounds(bx + 126, y + 101, 36, 14).build());
                addRenderableWidget(Button.builder(Component.literal("Bordar nome"), b -> Rede.CANAL.sendToServer(
                        new AcaoHabilidade(AcaoHabilidade.BORDAR_NOME, cor, campoNome.getValue()))).bounds(bx + 166, y + 101, 72, 14).build());
            }
            case 4 -> {
                Button b = addRenderableWidget(Button.builder(Component.literal("Vestir o manto"), bt -> enviar(Costureira.DISFARCE, 0)).bounds(bx, by, 110, 18).build());
                b.active = !d.getBoolean("inimigo");
            }
            default -> { }
        }
    }

    @Override
    protected void containerTick() {
        tempo++;
        if (campoNome != null) campoNome.tick();
    }

    @Override
    public boolean keyPressed(int tecla, int codigo, int mods) {
        if (campoNome != null && campoNome.isFocused()) {
            if (tecla == 256) { onClose(); return true; }            // Esc fecha
            return campoNome.keyPressed(tecla, codigo, mods) || true; // o "E" não fecha enquanto digita
        }
        return super.keyPressed(tecla, codigo, mods);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x0 = leftPos, y0 = topPos;
        g.fill(x0 - 3, y0 - 3, x0 + imageWidth + 3, y0 + imageHeight + 3, 0xFF5A3A22);
        g.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, LINHO);
        for (int k = 0; k < imageHeight; k += 4) if ((k / 4) % 2 == 0) g.fill(x0, y0 + k, x0 + imageWidth, y0 + k + 1, 0x0D5A3A22);   // trama do tecido
        costura(g, x0 + 3, y0 + 3, x0 + imageWidth - 3, y0 + imageHeight - 3);
        g.fill(x0 + 8, y0 + 34, x0 + imageWidth - 8, y0 + 36, CORES_ABA[aba]);       // fita da aba escolhida

        // espaços
        espaco(g, x0 + MenuCostura.PECA_X, y0 + MenuCostura.PECA_Y, ROSA);
        boolean usaSegunda = aba == 1;
        espaco(g, x0 + MenuCostura.SEGUNDA_X, y0 + MenuCostura.SEGUNDA_Y, usaSegunda ? 0xFF7E57C2 : LINHO_ESC);
        if (!usaSegunda && segunda().isEmpty()) g.fill(x0 + MenuCostura.SEGUNDA_X, y0 + MenuCostura.SEGUNDA_Y, x0 + MenuCostura.SEGUNDA_X + 16, y0 + MenuCostura.SEGUNDA_Y + 16, 0x60EFE3CC);
        for (int l = 0; l < 3; l++) for (int c = 0; c < 9; c++) espaco(g, x0 + MenuCostura.INV_X + c * 18, y0 + MenuCostura.INV_Y + l * 18, LINHA);
        for (int c = 0; c < 9; c++) espaco(g, x0 + MenuCostura.INV_X + c * 18, y0 + MenuCostura.INV_Y + 58, LINHA);
        g.fill(x0 + 8, y0 + 140, x0 + imageWidth - 8, y0 + 141, LINHO_ESC);

        int x = x0 + CX, y = y0 + 42;
        ItemStack p = peca();
        switch (aba) {
            case 0 -> {
                if (!Costureira.calca(p)) { texto(g, "Coloque uma calça no espaço da peça.", x, y, TINTA); texto(g, "Cada bolso custa " + ouro(d.getLong("precoBolso")) + ".", x, y + 12, TINTA_CLARA); break; }
                int tem = Costureira.bolsos(p), max = d.getInt("maximo");
                texto(g, "Esta calça tem " + tem + " de " + max + " bolsos.", x, y, TINTA);
                for (int i = 0; i < max; i++) {   // os bolsos, em duas fileiras
                    int bx = x + (i % 6) * 18, by = y + 14 + (i / 6) * 18;
                    g.fill(bx, by, bx + 15, by + 15, i < tem ? ROSA : LINHO_ESC);
                    g.fill(bx + 2, by + 2, bx + 13, by + 3, i < tem ? 0xFFF3C6D3 : 0xFFE6D8BB);   // aba do bolso
                }
                int falta = max - tem;
                if (falta > 0) {
                    texto(g, "+1: ", x + 116, y + 18, TINTA_CLARA); moedas(g, font, x + 116 + font.width("+1: "), y + 18, d.getLong("precoBolso"), TINTA);
                    texto(g, "Completar: ", x + 116, y + 32, TINTA_CLARA); moedas(g, font, x + 116 + font.width("Completar: "), y + 32, falta * d.getLong("precoBolso"), TINTA);
                }
                texto(g, "O que vai nos bolsos fica com você, não na calça.", x, y + 54, TINTA_CLARA);
            }
            case 1 -> {
                texto(g, "Peça: a calça velha.  2ª calça: a nova.", x, y, TINTA);
                int de = Costureira.bolsos(p), para = Costureira.bolsos(segunda()), qtd = Math.min(de, d.getInt("maximo") - para);
                if (!Costureira.calca(p) || !Costureira.calca(segunda())) { texto(g, "Coloque as duas calças para ver o preço.", x, y + 14, TINTA_CLARA); break; }
                texto(g, "Velha: " + de + " bolsos  →  Nova: " + para + " bolsos", x, y + 14, TINTA);
                if (qtd > 0) {
                    texto(g, "Passa " + qtd + (qtd == 1 ? " bolso por " : " bolsos por "), x, y + 28, TINTA);
                    moedas(g, font, x + font.width("Passa " + qtd + (qtd == 1 ? " bolso por " : " bolsos por ")), y + 28, qtd * d.getLong("precoPassar"), qtd * d.getLong("precoPassar") <= dinheiro() ? TINTA : VERMELHO_OK);
                } else texto(g, de == 0 ? "A calça velha não tem bolsos." : "A calça nova já está completa.", x, y + 28, VERMELHO_OK);
                texto(g, "Bem mais barato que costurar de novo.", x, y + 54, TINTA_CLARA);
            }
            case 2 -> {
                if (p.isEmpty()) { texto(g, "Coloque uma armadura ou élitros na peça.", x, y, TINTA); texto(g, "Armas ficam com o ferreiro.", x, y + 12, TINTA_CLARA); break; }
                if (Costureira.fatorRemendo(p) <= 0) { texto(g, "Eu só remendo armaduras e élitros.", x, y, VERMELHO_OK); break; }
                texto(g, corta(p.getHoverName().getString(), 230), x, y, TINTA);
                int max = Math.max(1, p.getMaxDamage()), resta = max - p.getDamageValue();
                g.fill(x, y + 14, x + 160, y + 22, 0xFF6B5A44);
                int w = (int) (158L * resta / max);
                g.fill(x + 1, y + 15, x + 1 + w, y + 21, resta * 3 < max ? 0xFFD84315 : resta * 3 < max * 2 ? 0xFFF9A825 : 0xFF43A047);
                texto(g, resta + "/" + max, x + 166, y + 14, TINTA_CLARA);
                if (!p.isDamaged()) texto(g, "Está inteira. Nada para remendar.", x, y + 30, VERDE_OK);
                else {
                    long c = Costureira.custoRemendo(p);
                    texto(g, "Remendo completo: ", x, y + 30, TINTA);
                    moedas(g, font, x + font.width("Remendo completo: "), y + 30, c, c <= dinheiro() ? TINTA : VERMELHO_OK);
                }
            }
            case 3 -> {
                texto(g, "Acabamento", x, y - 2, ROSA);
                if (!aceitaAcabamento(p)) texto(g, reliquia(p) ? "Relíquias têm a aparência delas." : "Coloque uma peça de armadura.", x + 64, y - 2, TINTA_CLARA);
                if (!padroes().isEmpty()) {
                    CompoundTag pa = padroes().getCompound(Math.min(padrao, padroes().size() - 1));
                    String np = corta(nomeJson(pa).getString(), 130);
                    texto(g, np, x + 82 - font.width(np) / 2, y + 11, TINTA);
                }
                if (!materiais().isEmpty()) {
                    CompoundTag ma = materiais().getCompound(Math.min(material, materiais().size() - 1));
                    Component nm = nomeJson(ma);
                    g.drawString(font, corta(nm.getString(), 130), x + 82 - Math.min(130, font.width(nm.getString())) / 2, y + 27, nm.getStyle().getColor() != null ? 0xFF000000 | nm.getStyle().getColor().getValue() : TINTA, false);
                }
                texto(g, "Acabamento: " + ouro(d.getLong("precoAcabamento")) + " · tirar: " + ouro(d.getLong("precoTirar")), x, y + 40, TINTA_CLARA);
                // prévia: a peça com o acabamento escolhido
                if (aceitaAcabamento(p) && !padroes().isEmpty() && !materiais().isEmpty()) {
                    ItemStack prev = p.copy();
                    CompoundTag trim = new CompoundTag();
                    trim.putString("pattern", padroes().getCompound(Math.min(padrao, padroes().size() - 1)).getString("id"));
                    trim.putString("material", materiais().getCompound(Math.min(material, materiais().size() - 1)).getString("id"));
                    prev.getOrCreateTag().put("Trim", trim);
                    espaco(g, x0 + imageWidth - 27, y0 + 57, ROSA);
                    g.renderItem(prev, x0 + imageWidth - 27, y0 + 57);
                }
                texto(g, "Nome bordado", x, y + 46, ROSA);
                String exemplo = campoNome != null && !campoNome.getValue().isBlank() ? campoNome.getValue() : "Exemplo";
                ChatFormatting c = Costureira.CORES[cor];
                g.drawString(font, corta(exemplo, 120), x + 76, y + 46, 0xFF000000 | (c.getColor() == null ? 0xFFFFFF : c.getColor()), true);
                texto(g, "Custa " + ouro(d.getLong("precoNome")) + ". Não vale para relíquias.", x, y + 76, TINTA_CLARA);
            }
            case 4 -> {
                long ms = d.getLong("disfarceMs");
                texto(g, "Manto de disfarce", x, y - 2, 0xFF455A64);
                texto(g, "Por 30 minutos, os guardas não reconhecem", x, y + 10, TINTA);
                texto(g, "quem é Hostil a um reino: dá para entrar,", x, y + 20, TINTA);
                texto(g, "comprar e sair em paz.", x, y + 30, TINTA);
                texto(g, "Não vale para Inimigo do reino. Atacar alguém", x, y + 42, TINTA_CLARA);
                texto(g, "dentro de uma cidade rasga o manto.", x, y + 52, TINTA_CLARA);
                texto(g, "Preço: ", x + 116, y + 72, TINTA);
                moedas(g, font, x + 116 + font.width("Preço: "), y + 72, d.getLong("precoDisfarce"), d.getLong("precoDisfarce") <= dinheiro() ? TINTA : VERMELHO_OK);
                if (d.getBoolean("inimigo")) texto(g, "Você é Inimigo deste reino.", x + 116, y + 84, VERMELHO_OK);
                else texto(g, ms > 0 ? "Disfarçado: mais " + Math.max(1, ms / 60000) + " min" : "Sem disfarce agora", x + 116, y + 84, ms > 0 ? VERDE_OK : TINTA_CLARA);
            }
            default -> { }
        }
    }

    private String ouro(long cobre) { return cobre % 100 == 0 ? (cobre / 100) + " ouro" : cobre + " cobre"; }
    private void texto(GuiGraphics g, String t, int x, int y, int cor) { g.drawString(font, t, x, y, cor, false); }

    private static void costura(GuiGraphics g, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x += 6) { g.fill(x, y1, x + 3, y1 + 1, LINHA); g.fill(x, y2 - 1, x + 3, y2, LINHA); }
        for (int y = y1; y < y2; y += 6) { g.fill(x1, y, x1 + 1, y + 3, LINHA); g.fill(x2 - 1, y, x2, y + 3, LINHA); }
    }

    private static void espaco(GuiGraphics g, int x, int y, int borda) {
        g.fill(x - 1, y - 1, x + 17, y + 17, borda);
        g.fill(x, y, x + 16, y + 16, ESPACO);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "✂ " + title.getString(), 8, 6, TINTA, false);
        String sub = "Bolsa e banco: ";
        int w = font.width(sub);
        g.drawString(font, sub, imageWidth - 92 - w, 6, TINTA_CLARA, false);
        moedas(g, font, imageWidth - 92, 6, dinheiro(), TINTA);
        g.drawString(font, "Peça", MenuCostura.PECA_X - 1, MenuCostura.PECA_Y - 10, TINTA_CLARA, false);
        g.drawString(font, "2ª calça", MenuCostura.SEGUNDA_X - 8, MenuCostura.SEGUNDA_Y - 10, aba == 1 ? TINTA_CLARA : 0x808A7458, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TINTA_CLARA, false);
        String msg = d.getString("msg");
        if (!msg.isEmpty()) g.drawString(font, corta(msg, imageWidth - 16), 8, 143, d.getBoolean("ok") ? VERDE_OK : VERMELHO_OK, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
