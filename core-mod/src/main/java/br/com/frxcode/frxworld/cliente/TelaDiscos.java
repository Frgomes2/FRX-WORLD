package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.musica.Discos;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Mercador de Discos: prateleira de discos em cartões, com prévia da música antes de comprar. */
public class TelaDiscos extends Screen {
    private static final int LARG = 372, ALT = 236, COLUNAS = 4, LINHAS = 2, CARTAO_L = 86, CARTAO_A = 58;

    private CompoundTag d;
    private int x0, y0, pagina;
    private String tocando = "", estilo = "", escolhido = "";

    public TelaDiscos(CompoundTag d) {
        super(Component.literal("Mercador de Discos"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    /** Os discos do estilo escolhido ("" = todos). */
    private java.util.List<CompoundTag> discos() {
        ListTag l = d.getList("discos", Tag.TAG_COMPOUND);
        java.util.List<CompoundTag> r = new java.util.ArrayList<>();
        for (int i = 0; i < l.size(); i++) if (estilo.isEmpty() || l.getCompound(i).getString("estilo").equalsIgnoreCase(estilo)) r.add(l.getCompound(i));
        return r;
    }

    /** Estilos que existem na loja, em ordem alfabética, com "" (todos) na frente. */
    private java.util.List<String> estilos() {
        ListTag l = d.getList("discos", Tag.TAG_COMPOUND);
        java.util.TreeSet<String> nomes = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < l.size(); i++) if (!l.getCompound(i).getString("estilo").isEmpty()) nomes.add(l.getCompound(i).getString("estilo"));
        java.util.List<String> r = new java.util.ArrayList<>();
        r.add("");
        r.addAll(nomes);
        return r;
    }

    private CompoundTag marcado() {
        if (escolhido.isEmpty()) return null;
        for (CompoundTag t : discos()) if (t.getString("codigo").equals(escolhido)) return t;
        return null;
    }
    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }
    private int paginas() { return Math.max(1, (discos().size() + COLUNAS * LINHAS - 1) / (COLUNAS * LINHAS)); }
    private static String tempo(int s) { return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60; }
    private static ResourceLocation som(String codigo) { return new ResourceLocation("frxworld", "disco." + codigo); }

    private void pararPrevia() {
        if (tocando.isEmpty()) return;
        Minecraft.getInstance().getSoundManager().stop(som(tocando), SoundSource.MASTER);
        tocando = "";
    }

    private void previa(String codigo) {
        boolean era = tocando.equals(codigo);
        pararPrevia();
        if (!era) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(som(codigo)), 1f, 1f));
            tocando = codigo;
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        pagina = Math.max(0, Math.min(paginas() - 1, pagina));
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        if (paginas() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina--; rebuildWidgets(); }).bounds(x0 + LARG - 62, y0 + 24, 18, 16).build()).active = pagina > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); }).bounds(x0 + LARG - 28, y0 + 24, 18, 16).build()).active = pagina < paginas() - 1;
        }
        java.util.List<String> es = estilos();
        if (es.size() > 1) {
            if (!es.contains(estilo)) estilo = "";
            addRenderableWidget(Button.builder(Component.literal("Estilo: " + (estilo.isEmpty() ? "todos" : estilo)), b -> {
                java.util.List<String> lista = estilos();
                estilo = lista.get((Math.max(0, lista.indexOf(estilo)) + (hasShiftDown() ? lista.size() - 1 : 1)) % lista.size());
                pagina = 0;
                rebuildWidgets();
            }).bounds(x0 + LARG - 198, y0 + 24, 130, 16).build());
        }
        CompoundTag t = marcado();
        if (t != null) {
            final String codigo = t.getString("codigo");
            addRenderableWidget(Button.builder(Component.literal(tocando.equals(codigo) ? "■ Parar" : "▶ Ouvir"), b -> previa(codigo)).bounds(x0 + LARG - 170, y0 + ALT - 44, 74, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Comprar"), b -> enviar(codigo)).bounds(x0 + LARG - 92, y0 + ALT - 44, 80, 20).build())
                    .active = t.getBoolean("podeRank") && d.getLong("bolsa") >= t.getLong("preco") && t.getInt("restam") != 0;
        }
    }

    private void enviar(String codigo) { Rede.CANAL.sendToServer(new AcaoMenu("discos", d.getInt("npc"), Discos.COMPRAR, codigo.hashCode())); }

    private int cx(int i) { return x0 + 10 + (i % COLUNAS) * (CARTAO_L + 3); }
    private int cy(int i) { return y0 + 46 + (i / COLUNAS) * (CARTAO_A + 3); }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (super.mouseClicked(mx, my, botao)) return true;
        int ini = pagina * COLUNAS * LINHAS;
        java.util.List<CompoundTag> lista = discos();
        for (int i = 0; i < COLUNAS * LINHAS && ini + i < lista.size(); i++) {
            if (mx >= cx(i) && mx < cx(i) + CARTAO_L && my >= cy(i) && my < cy(i) + CARTAO_A) {
                escolhido = lista.get(ini + i).getString("codigo");
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
        java.util.List<CompoundTag> l = discos();
        if (l.isEmpty()) centro(g, "Nenhum disco à venda. O mercador ainda espera a carga.", x0 + LARG / 2, y0 + 100, TINTA);
        int ini = pagina * COLUNAS * LINHAS;
        for (int i = 0; i < COLUNAS * LINHAS && ini + i < l.size(); i++) {
            CompoundTag t = l.get(ini + i);
            ItemStack s = ItemStack.of(t.getCompound("item"));
            int x = cx(i), y = cy(i);
            boolean sel = t.getString("codigo").equals(escolhido), em = mx >= x && mx < x + CARTAO_L && my >= y && my < y + CARTAO_A;
            g.fill(x, y, x + CARTAO_L, y + CARTAO_A, sel ? OURO : em ? MOLDURA : PERGAMINHO_ESC);
            g.fill(x + 1, y + 1, x + CARTAO_L - 1, y + CARTAO_A - 1, CAIXA);
            g.pose().pushPose();
            g.pose().translate(x + CARTAO_L / 2f - 12, y + 3, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(s, 0, 0);
            g.pose().popPose();
            centro(g, corta(t.getString("nome"), CARTAO_L - 6), x + CARTAO_L / 2, y + 29, MOLDURA);
            moedas(g, font, x + 8, y + 42, t.getLong("preco"), t.getBoolean("podeRank") ? TINTA : CINZA);
            if (tocando.equals(t.getString("codigo"))) g.drawString(font, "♪", x + CARTAO_L - 12, y + 4, VERDE, false);
            if (!t.getString("rank").isEmpty()) g.drawString(font, t.getString("rank"), x + 5, y + 4, t.getBoolean("podeRank") ? CINZA : VERMELHO, false);
            if (t.getInt("limite") > 0) {   // edição limitada: quantas restam do total, ao lado do preço
                String r = t.getInt("restam") + "/" + t.getInt("limite");
                g.drawString(font, r, x + CARTAO_L - 5 - font.width(r), y + 42, t.getInt("restam") > 0 ? OURO : VERMELHO, false);
            }
        }
        // detalhes do disco escolhido
        int y = y0 + ALT - 66;
        cartao(g, x0 + 10, y, LARG - 20, 46);
        CompoundTag t = marcado();
        if (t != null) {
            String artista = t.getString("artista"), genero = t.getString("estilo");
            String linha = (artista.isEmpty() ? "" : artista + "  ·  ") + (genero.isEmpty() ? "" : genero + "  ·  ") + (t.getInt("duracao") > 0 ? "♪ " + tempo(t.getInt("duracao")) : "");
            g.drawString(font, corta(t.getString("nome"), 180), x0 + 16, y + 5, MOLDURA, false);
            g.drawString(font, corta(linha, 180), x0 + 16, y + 17, TINTA, false);
            String desc = t.getString("descricao");
            String aviso = t.getInt("restam") == 0 ? "Esgotado: as " + t.getInt("limite") + " unidades já foram vendidas" : !t.getBoolean("podeRank") ? "Requer Rank " + t.getString("rank") + " na Guilda" : d.getLong("bolsa") < t.getLong("preco") ? "Faltam moedas" : desc;
            g.drawString(font, corta(aviso, 180), x0 + 16, y + 30, t.getBoolean("podeRank") && d.getLong("bolsa") >= t.getLong("preco") && t.getInt("restam") != 0 ? CINZA : VERMELHO, false);
            if (t.getInt("limite") > 0 && t.getInt("restam") > 0) g.drawString(font, "Edição limitada: restam " + t.getInt("restam") + " de " + t.getInt("limite"), x0 + LARG - 170, y + 5, OURO, false);
        } else {
            g.drawString(font, "Clique num disco para ouvir um trecho e comprar.", x0 + 16, y + 10, TINTA, false);
            g.drawString(font, "Depois é só clicar numa jukebox com o disco na mão.", x0 + 16, y + 24, CINZA, false);
        }
        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, corta(msg, LARG - 20), x0 + LARG / 2, y0 + ALT - 14, d.getBoolean("ok") ? VERDE : VERMELHO);
        super.render(g, mx, my, pt);
    }

    @Override
    public void removed() {
        pararPrevia();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
