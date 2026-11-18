package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.acesso.Personagens;
import br.com.frxcode.frxworld.rede.AcaoHabilidade;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/**
 * Escolha de personagem: três estandartes lado a lado, um por vaga. Aparece ao entrar no servidor e pela tecla de personagens.
 * O personagem em jogo mostra o boneco do jogador; os outros, o símbolo da classe. Trocar faz o jogo entrar de novo sozinho.
 */
public class TelaPersonagens extends Screen {
    private static final int FAIXA_L = 112, FAIXA_A = 178, VAO = 14;
    private static final int FUNDO_ALTO = 0xF0100C08, FUNDO_BAIXO = 0xF0241A10, SOMBRA = 0x66000000, BRASA = 0xFFD9A441;

    private CompoundTag d;
    private int x0, y0, digitando = -1, operacao;
    private String rascunho = "";
    private EditBox campo;

    public TelaPersonagens(CompoundTag d) {
        super(Component.literal("Personagens"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) {
        this.d = novo;
        if (novo.getBoolean("ok")) { digitando = -1; rascunho = ""; }
        rebuildWidgets();
    }

    private ListTag vagas() { return d.getList("vagas", Tag.TAG_COMPOUND); }
    private int fx(int i) { return x0 + i * (FAIXA_L + VAO); }
    private void centro(GuiGraphics g, Component t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private String corta(String t, int larg) { return font.width(t) <= larg ? t : font.plainSubstrByWidth(t, Math.max(0, larg - 6)) + "…"; }

    private void pedirNome(int vaga, int qual, String atual) {
        digitando = vaga; operacao = qual; rascunho = atual;
        rebuildWidgets();
    }

    private void confirmarNome() {
        String nome = campo == null ? "" : campo.getValue().trim();
        if (nome.length() < 3) return;
        Rede.CANAL.sendToServer(new AcaoHabilidade(AcaoHabilidade.PERSONAGEM, digitando * 10 + operacao, nome));
    }

    @Override
    protected void init() {
        int total = FAIXA_L * 3 + VAO * 2;
        x0 = (width - total) / 2;
        y0 = Math.max(34, (height - FAIXA_A - 60) / 2 + 10);
        campo = null;
        ListTag l = vagas();
        int ativo = d.getInt("ativo");
        for (int i = 0; i < l.size() && i < 3; i++) {
            CompoundTag t = l.getCompound(i);
            final int vaga = t.getInt("vaga");
            int x = fx(i), y = y0 + FAIXA_A + 6;
            if (!t.getBoolean("criado")) {
                addRenderableWidget(Button.builder(Component.literal("Criar personagem"), b -> pedirNome(vaga, Personagens.CRIAR, "")).bounds(x, y, FAIXA_L, 20).build());
            } else if (vaga == ativo) {
                addRenderableWidget(Button.builder(Component.literal(d.getBoolean("entrada") ? "Entrar" : "Continuar"), b -> onClose()).bounds(x, y, 70, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Nome"), b -> pedirNome(vaga, Personagens.RENOMEAR, t.getString("nome"))).bounds(x + 74, y, FAIXA_L - 74, 20).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("Jogar"), b -> Rede.CANAL.sendToServer(new AcaoMenu("personagens", 0, Personagens.JOGAR, vaga))).bounds(x, y, 70, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Nome"), b -> pedirNome(vaga, Personagens.RENOMEAR, t.getString("nome"))).bounds(x + 74, y, FAIXA_L - 74, 20).build());
            }
        }
        if (digitando > 0) {
            int cy = y0 + FAIXA_A + 34, cx = width / 2;
            campo = new EditBox(font, cx - 110, cy, 140, 18, Component.literal("Nome do personagem"));
            campo.setMaxLength(16);
            campo.setValue(rascunho);
            campo.setResponder(v -> rascunho = v);
            addRenderableWidget(campo);
            setInitialFocus(campo);
            addRenderableWidget(Button.builder(Component.literal(operacao == Personagens.CRIAR ? "Criar e jogar" : "Salvar"), b -> confirmarNome()).bounds(cx + 34, cy - 1, 76, 20).build());
            addRenderableWidget(Button.builder(Component.literal("x"), b -> { digitando = -1; rebuildWidgets(); }).bounds(cx + 114, cy - 1, 18, 20).build());
        }
    }

    @Override
    public boolean keyPressed(int tecla, int codigo, int mods) {
        if (campo != null && campo.isFocused() && (tecla == 257 || tecla == 335)) { confirmarNome(); return true; }   // Enter
        return super.keyPressed(tecla, codigo, mods);
    }

    /** Estandarte de uma vaga: haste em cima, pano de pergaminho e ponta em V embaixo. */
    private void estandarte(GuiGraphics g, int x, int y, boolean ativo, boolean em) {
        int borda = ativo ? BRASA : em ? OURO : MOLDURA;
        g.fill(x + 4, y + 6, x + FAIXA_L + 4, y + FAIXA_A + 4, SOMBRA);
        g.fill(x - 4, y - 4, x + FAIXA_L + 4, y, MOLDURA);                       // haste
        g.fill(x - 6, y - 6, x - 2, y + 2, borda); g.fill(x + FAIXA_L + 2, y - 6, x + FAIXA_L + 6, y + 2, borda);
        g.fill(x, y, x + FAIXA_L, y + FAIXA_A - 12, borda);
        g.fill(x + 2, y, x + FAIXA_L - 2, y + FAIXA_A - 14, PERGAMINHO);
        g.fill(x + 2, y, x + FAIXA_L - 2, y + 18, ativo ? BRASA : PERGAMINHO_ESC);   // faixa do nome
        for (int k = 0; k < 12; k++) {                                              // ponta em V
            int recuo = k * (FAIXA_L / 2 - 2) / 12;
            g.fill(x, y + FAIXA_A - 12 + k, x + FAIXA_L / 2 - recuo, y + FAIXA_A - 11 + k, borda);
            g.fill(x + FAIXA_L / 2 + recuo, y + FAIXA_A - 12 + k, x + FAIXA_L, y + FAIXA_A - 11 + k, borda);
            g.fill(x + 2, y + FAIXA_A - 14 + k, x + FAIXA_L / 2 - recuo - 2, y + FAIXA_A - 13 + k, PERGAMINHO);
            g.fill(x + FAIXA_L / 2 + recuo + 2, y + FAIXA_A - 14 + k, x + FAIXA_L - 2, y + FAIXA_A - 13 + k, PERGAMINHO);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, FUNDO_ALTO, FUNDO_BAIXO);
        centro(g, Component.literal("Quem vai viver esta aventura?").withStyle(ChatFormatting.BOLD), width / 2, y0 - 26, BRASA);
        centro(g, "Conta " + d.getString("conta") + " · até 3 personagens, cada um com a sua história", width / 2, y0 - 14, 0xFFB9A98C);
        ListTag l = vagas();
        int ativo = d.getInt("ativo");
        for (int i = 0; i < l.size() && i < 3; i++) {
            CompoundTag t = l.getCompound(i);
            int x = fx(i), y = y0, cx = x + FAIXA_L / 2;
            boolean eAtivo = t.getInt("vaga") == ativo, criado = t.getBoolean("criado");
            boolean em = mx >= x && mx < x + FAIXA_L && my >= y && my < y + FAIXA_A;
            estandarte(g, x, y, eAtivo, em);
            if (!criado) {
                centro(g, "Vaga livre", cx, y + 5, CINZA);
                g.pose().pushPose();
                g.pose().translate(cx, y + 70, 0);
                g.pose().scale(4f, 4f, 1f);
                g.drawString(font, "+", -font.width("+") / 2, -4, PERGAMINHO_ESC, false);
                g.pose().popPose();
                centro(g, "Um novo começo:", cx, y + 112, TINTA);
                centro(g, "outra raça, outra classe", cx, y + 123, CINZA);
                continue;
            }
            centro(g, Component.literal(corta(t.getString("nome"), FAIXA_L - 10)).withStyle(ChatFormatting.BOLD), cx, y + 5, eAtivo ? 0xFF2A1A08 : MOLDURA);
            if (eAtivo && minecraft != null && minecraft.player != null) {
                try { InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, y + 108, 36, (float) cx - mx, (float) (y + 50) - my, minecraft.player); }
                catch (Throwable erro) { /* sem boneco: fica só o texto */ }
            } else {
                g.pose().pushPose();
                g.pose().translate(cx - 24, y + 42, 0);
                g.pose().scale(3f, 3f, 1f);
                g.renderItem(ItemStack.of(t.getCompound("item")), 0, 0);
                g.pose().popPose();
            }
            String classe = t.getString("classe"), raca = t.getString("raca");
            centro(g, classe.isEmpty() ? "Ainda sem classe" : classe, cx, y + 116, MOLDURA);
            if (!raca.isEmpty()) centro(g, corta(raca, FAIXA_L - 8), cx, y + 127, TINTA);
            centro(g, "Nível " + Math.max(1, t.getInt("nivel")) + (t.getString("rank").isEmpty() ? "" : " · Rank " + t.getString("rank")), cx, y + 139, CINZA);
            if (eAtivo) centro(g, "em jogo", cx, y + 151, VERDE);
        }
        int yTexto = y0 + FAIXA_A + (digitando > 0 ? 58 : 34);
        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, msg, width / 2, yTexto, d.getBoolean("ok") ? 0xFF8FD18A : 0xFFE08A7A);
        else if (digitando > 0) centro(g, operacao == Personagens.CRIAR ? "Dê um nome ao personagem. Ao criar, o jogo entra de novo com ele." : "Novo nome do personagem (3 a 16 letras).", width / 2, yTexto, 0xFFB9A98C);
        else centro(g, "Banco e terrenos são da conta. Itens, nível, classe e clãs são de cada personagem.", width / 2, yTexto, 0xFFB9A98C);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
