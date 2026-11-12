package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import static br.com.frxcode.frxworld.cliente.ClienteMenus.*;

/** Guilda de Aventureiros: cartão do jogador (rank, classe, feitos) e a prova de promoção. */
public class TelaGuilda extends Screen {
    private static final int LARG = 320, ALT = 214;
    private static final int[] COR_RANK = {0xFF8A7D6A, 0xFF5E8C4A, 0xFF3F8FB5, 0xFF7B5BC4, 0xFFC0572B, 0xFFB8892A, 0xFFB3261E};

    private CompoundTag d;
    private int x0, y0;

    public TelaGuilda(CompoundTag d) {
        super(Component.literal("Guilda de Aventureiros"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) { this.d = novo; rebuildWidgets(); }

    private void enviar(int acao) { Rede.CANAL.sendToServer(new AcaoMenu("guilda", d.getInt("npc"), acao, 0)); }
    private void centro(GuiGraphics g, String t, int cx, int y, int cor) { g.drawString(font, t, cx - font.width(t) / 2, y, cor, false); }
    private boolean noBalcao() { return d.getInt("npc") >= 0; }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose()).bounds(x0 + LARG - 22, y0 + 4, 16, 16).build());
        if (!noBalcao()) return;
        String estado = d.getString("estado");
        int by = y0 + ALT - 30;
        if (estado.equals("registrar")) {
            addRenderableWidget(Button.builder(Component.literal("Registrar-se na Guilda"), b -> enviar(0)).bounds(x0 + 10, by, LARG - 20, 20).build());
        } else {
            boolean semCartao = !d.getBoolean("temCartao");
            int larg = semCartao ? 190 : LARG - 20;
            if (!estado.equals("max"))
                addRenderableWidget(Button.builder(Component.literal(estado.equals("andamento") ? "Prova em andamento..." : "Fazer a prova para o Rank " + d.getString("proximo")),
                        b -> enviar(1)).bounds(x0 + 10, by, larg, 20).build()).active = estado.equals("pronto");
            if (semCartao)
                addRenderableWidget(Button.builder(Component.literal("2ª via do cartão"), b -> enviar(2)).bounds(x0 + LARG - 114, by, 104, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 4, y0 - 4, x0 + LARG + 4, y0 + ALT + 4, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, PERGAMINHO);
        centro(g, noBalcao() ? d.getString("nome") : "Cartão de Aventureiro", x0 + LARG / 2, y0 + 8, MOLDURA);

        // selo do rank
        int idx = Math.max(0, Math.min(COR_RANK.length - 1, d.getInt("rankIdx")));
        cartao(g, x0 + 10, y0 + 24, 86, 96);
        g.fill(x0 + 23, y0 + 32, x0 + 83, y0 + 92, COR_RANK[idx]);
        g.fill(x0 + 26, y0 + 35, x0 + 80, y0 + 89, CAIXA);
        g.pose().pushPose();
        g.pose().translate(x0 + 53, y0 + 44, 0);
        g.pose().scale(5f, 5f, 1f);
        String r = d.getString("rank");
        g.drawString(font, r, -font.width(r) / 2f, 0, COR_RANK[idx], false);
        g.pose().popPose();
        centro(g, d.getBoolean("registrado") ? "Rank " + r : "Sem registro", x0 + 53, y0 + 100, TINTA);

        // identidade
        int x = x0 + 104, y = y0 + 24;
        cartao(g, x, y, LARG - 114, 96);
        g.drawString(font, d.getString("jogador"), x + 8, y + 7, MOLDURA, false);
        if (!d.getString("titulo").isEmpty()) g.drawString(font, "«" + d.getString("titulo") + "»", x + 14 + font.width(d.getString("jogador")), y + 7, 0xFF7B5BC4, false);
        String classe = d.getString("classe").isEmpty() ? "sem classe" : d.getString("classe");
        String raca = d.getString("raca").isEmpty() ? "" : "  ·  " + d.getString("raca");
        g.drawString(font, "Nível " + d.getInt("nivel") + "  ·  " + classe + raca, x + 8, y + 20, TINTA, false);
        g.fill(x + 8, y + 31, x + LARG - 122, y + 32, PERGAMINHO_ESC);
        if (!d.getString("unica").isEmpty()) {
            g.drawString(font, "✦ " + d.getString("unica"), x + 8, y + 36, 0xFF7B5BC4, false);
            String ef = d.getString("unicaEfeito");
            int resto = LARG - 130 - font.width("✦ " + d.getString("unica") + "  ");
            if (font.width(ef) > resto) ef = font.plainSubstrByWidth(ef, Math.max(0, resto - 6)) + "…";
            g.drawString(font, ef, x + 8 + font.width("✦ " + d.getString("unica") + "  "), y + 36, CINZA, false);
        } else g.drawString(font, "Habilidade única ainda não despertou", x + 8, y + 36, CINZA, false);
        String[][] feitos = {{"Abates", String.valueOf(d.getInt("abates"))}, {"Chefes", String.valueOf(d.getInt("chefes"))},
                {"Campanhas", String.valueOf(d.getInt("campanhas"))}, {"Invasões", String.valueOf(d.getInt("invasoes"))},
                {"Conquistas", String.valueOf(d.getInt("conquistas"))}, {"Mortes", String.valueOf(d.getInt("mortes"))}};
        for (int i = 0; i < feitos.length; i++) {
            int fx = x + 8 + (i % 3) * 66, fy = y + 52 + (i / 3) * 21;
            g.drawString(font, feitos[i][0], fx, fy, CINZA, false);
            g.drawString(font, feitos[i][1], fx, fy + 9, TINTA, false);
        }

        // promoção
        y = y0 + 126;
        cartao(g, x0 + 10, y, LARG - 20, 52);
        String estado = d.getString("estado");
        int tx = x0 + 18;
        switch (estado) {
            case "registrar" -> {
                g.drawString(font, "Entre para a Guilda de Aventureiros", tx, y + 7, MOLDURA, false);
                g.drawString(font, "Você recebe o Cartão de Aventureiro e começa no Rank F.", tx, y + 20, TINTA, false);
                g.drawString(font, "Cada rank novo exige nível e uma prova de promoção.", tx, y + 32, CINZA, false);
            }
            case "max" -> {
                g.drawString(font, "Rank S — o topo da Guilda", tx, y + 7, MOLDURA, false);
                g.drawString(font, "Não há prova acima desta. Seu nome já é lenda.", tx, y + 20, TINTA, false);
            }
            default -> {
                g.drawString(font, "Próximo: Rank " + d.getString("proximo"), tx, y + 7, MOLDURA, false);
                boolean temNivel = d.getInt("nivel") >= d.getInt("proximoNivel");
                g.drawString(font, "Requer nível " + d.getInt("proximoNivel") + (temNivel ? "  (ok)" : "  (você está no " + d.getInt("nivel") + ")"), tx, y + 20, temNivel ? VERDE : VERMELHO, false);
                String linha = switch (estado) {
                    case "espera" -> "Descanse: nova tentativa em " + (d.getInt("esperaSeg") / 60 + 1) + " min.";
                    case "andamento" -> "Derrote o Examinador! Você tem 3 minutos.";
                    default -> "Prova: derrotar o Examinador da Guilda em 3 min, sem morrer.";
                };
                g.drawString(font, linha, tx, y + 32, estado.equals("espera") ? VERMELHO : CINZA, false);
                g.drawString(font, "Prêmio:", x0 + LARG - 100, y + 7, CINZA, false);
                moedas(g, font, x0 + LARG - 100 + font.width("Prêmio: "), y + 7, d.getLong("premio"), TINTA);
            }
        }
        String msg = d.getString("msg");
        if (!msg.isEmpty()) centro(g, msg, x0 + LARG / 2, y0 + ALT - (noBalcao() ? 44 : 24), d.getBoolean("ok") ? VERDE : VERMELHO);
        else if (!noBalcao()) centro(g, "Fale com a recepcionista da Guilda numa capital para subir de rank.", x0 + LARG / 2, y0 + ALT - 24, CINZA);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
