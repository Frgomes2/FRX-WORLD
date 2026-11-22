package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.reinos.Desencanto;
import br.com.frxcode.frxworld.reinos.MenuDesencanto;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.moedas;

/**
 * Sábio: bancada arcana no jeito da bigorna. À esquerda, o espaço do item (com um círculo de runas girando em volta)
 * e o do livro em branco; à direita, cada encantamento do item numa ficha de pergaminho com a chance. Escolhe-se a
 * ficha e aperta "Extrair": o livro encantado aparece no espaço do livro. Fundo azul-noite com estrelas.
 */
public class TelaDesencanto extends AbstractContainerScreen<MenuDesencanto> {
    private static final int FICHA_A = 18, VISIVEIS = 5, LISTA_X = 70, LISTA_Y = 20, LISTA_L = 222;
    private static final int NOITE = 0xFF0E1230, OURO = 0xFFE9C46A, OURO_ESC = 0xFF9C7A2E, PERG = 0xFFE8DCC0, PERG_ESC = 0xFFC9B892,
            TINTA_AZUL = 0xFF1F2547, CINZA_AZUL = 0xFF8C93B8, ROXO = 0xFFB18CFF, VERDE_OK = 0xFF7BC67E, VERMELHO_OK = 0xFFE06A6A, ESPACO = 0xFF20264E;
    private static final String RUNAS = "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗ";

    private record Enc(Enchantment e, int nivel, long moedas, int xp, int chance) {}

    private CompoundTag d;
    private int escolhido = -1, rolagem, tempo, aba;   // aba 0 = encantamentos, 1 = runas
    private ItemStack visto = ItemStack.EMPTY;
    private Button extrair;

    public TelaDesencanto(MenuDesencanto menu, Inventory inv, Component titulo) {
        super(menu, inv, titulo);
        this.d = menu.info;
        imageWidth = 300;
        imageHeight = 246;
        inventoryLabelX = MenuDesencanto.INV_X;
        inventoryLabelY = MenuDesencanto.INV_Y - 11;
    }

    /** Resposta do servidor depois de extrair (mensagem e moedas/níveis atualizados). */
    public void atualizar(CompoundTag novo) { this.d = novo; }

    // ------------------------------------------------------------------ preços (mesma conta do servidor)

    private double peso(Enchantment e) {
        double r = switch (e.getRarity()) {
            case COMMON -> d.getDouble("multComum");
            case UNCOMMON -> d.getDouble("multIncomum");
            case RARE -> d.getDouble("multRaro");
            case VERY_RARE -> d.getDouble("multMuitoRaro");
        };
        return e.isTreasureOnly() ? r * d.getDouble("multTesouro") : r;
    }

    private List<Enc> encs() {
        List<Enc> l = new ArrayList<>();
        ItemStack s = menu.getSlot(MenuDesencanto.ITEM).getItem();
        if (s.isEmpty()) return l;
        for (Map.Entry<Enchantment, Integer> en : EnchantmentHelper.getEnchantments(s).entrySet()) {
            Enchantment e = en.getKey();
            int nv = en.getValue();
            double m = peso(e);
            long moedas = Math.round(d.getDouble("moedasPorNivel") * m * nv);
            int xp = (int) Math.min(d.getDouble("xpMax"), Math.round(d.getDouble("xpBase") + d.getDouble("xpPorNivel") * nv * m));
            int chance = nv >= 5 ? (int) d.getDouble("chance5") : nv == 4 ? (int) d.getDouble("chance4") : 100;
            l.add(new Enc(e, nv, moedas, xp, chance));
        }
        return l;
    }

    private boolean reliquia() {
        ItemStack s = menu.getSlot(MenuDesencanto.ITEM).getItem();
        return s.hasTag() && s.getTag().contains("frx_codex");
    }

    private boolean pode(Enc e) {
        return d.getBoolean("ativo") && !reliquia() && !e.e().isCurse() && menu.getSlot(MenuDesencanto.LIVRO).getItem().is(Items.BOOK)
                && (d.getBoolean("criativo") || d.getInt("xp") >= e.xp()) && d.getLong("bolsa") + d.getLong("banco") >= e.moedas();
    }

    /** Runas encaixadas no item da bancada: [tipo, nível]. */
    private List<Map.Entry<String, Integer>> runas() {
        return new ArrayList<>(br.com.frxcode.frxworld.aventura.Runas.doItem(menu.getSlot(MenuDesencanto.ITEM).getItem()).entrySet());
    }

    private long precoRuna(int nivel) { return d.getLong("runa" + Math.max(1, Math.min(3, nivel))); }

    private int tamanhoLista() { return aba == 1 ? runas().size() : encs().size(); }

    private boolean dentro(double mx, double my, int x, int y, int l, int a) { return mx >= x && mx < x + l && my >= y && my < y + a; }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }

    // ------------------------------------------------------------------ tela

    @Override
    protected void init() {
        super.init();
        extrair = addRenderableWidget(Button.builder(Component.literal(aba == 1 ? "Tirar runa" : "Extrair"),
                b -> Rede.CANAL.sendToServer(new AcaoMenu("desencanto", menu.npc, aba == 1 ? Desencanto.TIRAR_RUNA : Desencanto.EXTRAIR, escolhido)))
                .bounds(leftPos + imageWidth - 74, topPos + 124, 66, 18).build());
        String[] abas = {"Encantamentos", "Runas"};
        for (int i = 0; i < 2; i++) {
            final int k = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(abas[i]), bt -> { aba = k; escolhido = -1; rolagem = 0; rebuildWidgets(); })
                    .bounds(leftPos + imageWidth - 170 + i * 84, topPos + 3, i == 0 ? 82 : 60, 13).build());
            b.active = aba != i;
        }
    }

    @Override
    protected void containerTick() {
        tempo++;
        ItemStack s = menu.getSlot(MenuDesencanto.ITEM).getItem();
        if (!ItemStack.matches(s, visto)) { visto = s.copy(); escolhido = -1; rolagem = 0; }   // item trocado: escolha de novo
        if (aba == 1) {
            List<Map.Entry<String, Integer>> r = runas();
            extrair.active = d.getBoolean("ativo") && escolhido >= 0 && escolhido < r.size() && d.getLong("bolsa") + d.getLong("banco") >= precoRuna(r.get(escolhido).getValue());
            return;
        }
        List<Enc> l = encs();
        extrair.active = escolhido >= 0 && escolhido < l.size() && pode(l.get(escolhido));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        int tamanho = tamanhoLista();
        for (int i = rolagem; i < Math.min(tamanho, rolagem + VISIVEIS); i++)
            if (dentro(mx, my, leftPos + LISTA_X, topPos + LISTA_Y + (i - rolagem) * (FICHA_A + 2), LISTA_L, FICHA_A)) { escolhido = i; return true; }
        return super.mouseClicked(mx, my, botao);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, tamanhoLista() - VISIVEIS);
        rolagem = Math.max(0, Math.min(max, rolagem - (int) Math.signum(delta)));
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x0 = leftPos, y0 = topPos;
        float t = tempo + pt;
        g.fill(x0 - 3, y0 - 3, x0 + imageWidth + 3, y0 + imageHeight + 3, OURO_ESC);
        g.fill(x0 - 2, y0 - 2, x0 + imageWidth + 2, y0 + imageHeight + 2, 0xFF05060F);
        g.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, NOITE);
        for (int k = 0; k < 36; k++) {   // estrelas que piscam
            int sx = x0 + 4 + (k * 97) % (imageWidth - 8), sy = y0 + 4 + (k * 53) % (imageHeight - 8);
            int a = (int) (90 + 80 * Math.sin(t / 9.0 + k));
            g.fill(sx, sy, sx + 1, sy + 1, (Math.max(0, Math.min(255, a)) << 24) | 0xC8D0FF);
        }
        // círculo de runas em volta do espaço do item
        int cx = x0 + MenuDesencanto.ITEM_X + 8, cy = y0 + MenuDesencanto.ITEM_Y + 8, raio = 22;
        for (int k = 0; k < 14; k++) {
            double ang = t / 50.0 + k * Math.PI * 2 / 14;
            String r = String.valueOf(RUNAS.charAt(k % RUNAS.length()));
            int brilho = (int) (150 + 100 * Math.sin(t / 12.0 + k * 0.7));
            g.drawString(font, r, cx + (int) (Math.cos(ang) * raio) - font.width(r) / 2, cy + (int) (Math.sin(ang) * raio) - 4,
                    (Math.max(60, Math.min(255, brilho)) << 24) | 0xB18CFF, false);
        }
        // espaços (item, livro e mochila)
        espaco(g, x0 + MenuDesencanto.ITEM_X, y0 + MenuDesencanto.ITEM_Y, ROXO);
        espaco(g, x0 + MenuDesencanto.LIVRO_X, y0 + MenuDesencanto.LIVRO_Y, OURO);
        if (!menu.getSlot(MenuDesencanto.LIVRO).hasItem()) {   // fantasma do livro, para mostrar o que vai ali
            g.renderFakeItem(new ItemStack(Items.BOOK), x0 + MenuDesencanto.LIVRO_X, y0 + MenuDesencanto.LIVRO_Y);
            g.fill(x0 + MenuDesencanto.LIVRO_X, y0 + MenuDesencanto.LIVRO_Y, x0 + MenuDesencanto.LIVRO_X + 16, y0 + MenuDesencanto.LIVRO_Y + 16, 0xA020264E);
        }
        // seta mágica do item para o livro
        for (int k = 0; k < 4; k++) {
            int yy = y0 + MenuDesencanto.ITEM_Y + 30 + k * 6 + (int) (t / 4) % 6;
            if (yy < y0 + MenuDesencanto.LIVRO_Y - 6) g.drawString(font, "▼", cx - font.width("▼") / 2, yy, 0x80B18CFF, false);
        }
        for (int l = 0; l < 3; l++) for (int c = 0; c < 9; c++) espaco(g, x0 + MenuDesencanto.INV_X + c * 18, y0 + MenuDesencanto.INV_Y + l * 18, 0xFF3A4170);
        for (int c = 0; c < 9; c++) espaco(g, x0 + MenuDesencanto.INV_X + c * 18, y0 + MenuDesencanto.INV_Y + 58, 0xFF3A4170);

        if (aba == 1) { abaRunas(g, x0, y0, mx, my); return; }
        // fichas de pergaminho
        List<Enc> l = encs();
        int lx = x0 + LISTA_X;
        if (menu.getSlot(MenuDesencanto.ITEM).getItem().isEmpty()) {
            g.drawString(font, "Coloque à esquerda o item encantado", lx, y0 + LISTA_Y + 14, PERG_ESC, false);
            g.drawString(font, "e, embaixo dele, um livro em branco.", lx, y0 + LISTA_Y + 26, PERG_ESC, false);
        } else if (reliquia()) {
            g.drawString(font, "Relíquias do Códex não se separam", lx, y0 + LISTA_Y + 14, VERMELHO_OK, false);
            g.drawString(font, "dos seus encantamentos.", lx, y0 + LISTA_Y + 26, VERMELHO_OK, false);
        } else if (l.isEmpty()) {
            g.drawString(font, "Esse item não tem encantamento.", lx, y0 + LISTA_Y + 14, PERG_ESC, false);
        }
        if (!reliquia()) for (int i = rolagem; i < Math.min(l.size(), rolagem + VISIVEIS); i++) {
            Enc e = l.get(i);
            int y = y0 + LISTA_Y + (i - rolagem) * (FICHA_A + 2);
            boolean sel = i == escolhido, em = dentro(mx, my, lx, y, LISTA_L, FICHA_A), mal = e.e().isCurse();
            g.fill(lx - 1, y - 1, lx + LISTA_L + 1, y + FICHA_A + 1, sel ? OURO : em ? PERG_ESC : 0xFF3A3420);
            g.fill(lx, y, lx + LISTA_L, y + FICHA_A, mal ? 0xFFD9B8B0 : PERG);
            g.fill(lx, y, lx + 3, y + FICHA_A, mal ? 0xFF8E2C2C : ROXO);
            g.drawString(font, corta(e.e().getFullname(e.nivel()).getString(), LISTA_L - 60), lx + 7, y + 5, mal ? 0xFF8E2C2C : TINTA_AZUL, false);
            String ch = mal ? "maldição" : e.chance() + "%";
            g.drawString(font, ch, lx + LISTA_L - 6 - font.width(ch), y + 5, mal ? 0xFF8E2C2C : e.chance() < 100 ? 0xFFB5651D : 0xFF2E7D32, false);
        }
        if (l.size() > VISIVEIS && !reliquia())
            g.drawString(font, (rolagem + 1) + "–" + Math.min(l.size(), rolagem + VISIVEIS) + " de " + l.size() + " (role)", lx, y0 + LISTA_Y + VISIVEIS * (FICHA_A + 2), CINZA_AZUL, false);

        // custo do escolhido
        int y = y0 + 122;
        g.fill(x0 + 8, y - 4, x0 + imageWidth - 8, y - 3, OURO_ESC);
        if (escolhido >= 0 && escolhido < l.size() && !reliquia()) {
            Enc e = l.get(escolhido);
            long total = d.getLong("bolsa") + d.getLong("banco");
            g.drawString(font, "Custo:", x0 + 8, y + 1, CINZA_AZUL, false);
            moedas(g, font, x0 + 8 + font.width("Custo: "), y + 1, e.moedas(), total >= e.moedas() ? PERG : VERMELHO_OK);
            String xp = e.xp() + " níveis (você tem " + d.getInt("xp") + ")";
            g.drawString(font, xp, x0 + 8, y + 12, d.getBoolean("criativo") || d.getInt("xp") >= e.xp() ? VERDE_OK : VERMELHO_OK, false);
            boolean livro = menu.getSlot(MenuDesencanto.LIVRO).getItem().is(Items.BOOK);
            g.drawString(font, livro ? "+ 1 livro" : "falta o livro", x0 + 14 + font.width(xp), y + 12, livro ? VERDE_OK : VERMELHO_OK, false);
        } else {
            g.drawString(font, "Escolha o encantamento que vai para o livro.", x0 + 8, y + 1, PERG, false);
            g.drawString(font, "Bolsa e banco: ", x0 + 8, y + 12, CINZA_AZUL, false);
            moedas(g, font, x0 + 8 + font.width("Bolsa e banco: "), y + 12, d.getLong("bolsa") + d.getLong("banco"), PERG);
        }
    }

    /** Aba Runas: as runas encaixadas no item, cada uma numa ficha com o preço para tirar. */
    private void abaRunas(GuiGraphics g, int x0, int y0, int mx, int my) {
        List<Map.Entry<String, Integer>> l = runas();
        int lx = x0 + LISTA_X;
        if (menu.getSlot(MenuDesencanto.ITEM).getItem().isEmpty()) {
            g.drawString(font, "Coloque à esquerda a arma ou a armadura", lx, y0 + LISTA_Y + 14, PERG_ESC, false);
            g.drawString(font, "com runas. O livro não é usado aqui.", lx, y0 + LISTA_Y + 26, PERG_ESC, false);
        } else if (l.isEmpty()) g.drawString(font, "Esse item não tem runa encaixada.", lx, y0 + LISTA_Y + 14, PERG_ESC, false);
        for (int i = rolagem; i < Math.min(l.size(), rolagem + VISIVEIS); i++) {
            Map.Entry<String, Integer> r = l.get(i);
            int y = y0 + LISTA_Y + (i - rolagem) * (FICHA_A + 2);
            boolean sel = i == escolhido, em = dentro(mx, my, lx, y, LISTA_L, FICHA_A);
            int cor = br.com.frxcode.frxworld.aventura.Runas.corRuna(r.getKey());
            g.fill(lx - 1, y - 1, lx + LISTA_L + 1, y + FICHA_A + 1, sel ? OURO : em ? PERG_ESC : 0xFF3A3420);
            g.fill(lx, y, lx + LISTA_L, y + FICHA_A, PERG);
            g.fill(lx, y, lx + 3, y + FICHA_A, cor);
            g.drawString(font, "◆ " + br.com.frxcode.frxworld.aventura.Runas.nomeRuna(r.getKey(), r.getValue()), lx + 7, y + 5, TINTA_AZUL, false);
            long preco = precoRuna(r.getValue());
            moedas(g, font, lx + LISTA_L - 6 - 60, y + 5, preco, TINTA_AZUL);
        }
        int y = y0 + 122;
        g.fill(x0 + 8, y - 4, x0 + imageWidth - 8, y - 3, OURO_ESC);
        if (escolhido >= 0 && escolhido < l.size()) {
            long preco = precoRuna(l.get(escolhido).getValue()), total = d.getLong("bolsa") + d.getLong("banco");
            g.drawString(font, "Custo:", x0 + 8, y + 1, CINZA_AZUL, false);
            moedas(g, font, x0 + 8 + font.width("Custo: "), y + 1, preco, total >= preco ? PERG : VERMELHO_OK);
            g.drawString(font, "A runa sai inteira e vai para a mochila.", x0 + 8, y + 12, VERDE_OK, false);
        } else {
            g.drawString(font, "Escolha a runa que vai sair do item.", x0 + 8, y + 1, PERG, false);
            g.drawString(font, "Bolsa e banco: ", x0 + 8, y + 12, CINZA_AZUL, false);
            moedas(g, font, x0 + 8 + font.width("Bolsa e banco: "), y + 12, d.getLong("bolsa") + d.getLong("banco"), PERG);
        }
    }

    private static void espaco(GuiGraphics g, int x, int y, int borda) {
        g.fill(x - 1, y - 1, x + 17, y + 17, borda);
        g.fill(x, y, x + 16, y + 16, ESPACO);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, corta("☾ " + title.getString(), imageWidth - 182), 8, 6, OURO, false);
        g.drawString(font, "Livro", MenuDesencanto.LIVRO_X - 3, MenuDesencanto.LIVRO_Y + 19, CINZA_AZUL, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, CINZA_AZUL, false);
        String msg = d.getString("msg");
        if (!msg.isEmpty()) g.drawString(font, corta(msg, imageWidth - 16), 8, 146, d.getBoolean("ok") ? VERDE_OK : VERMELHO_OK, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
