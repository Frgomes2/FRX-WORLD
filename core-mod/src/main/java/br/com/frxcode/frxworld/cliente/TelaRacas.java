package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.racas.DefRacas;
import br.com.frxcode.frxworld.racas.DefRacas.Raca;
import br.com.frxcode.frxworld.rede.AcaoRaca;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Escolha de raça: principal + secundária, compatibilidade e prévia dos atributos. */
public class TelaRacas extends Screen {
    private static final int LARG = 400, ALT = 270, CW = 62, CH = 16;
    private static final int FUNDO = 0xF017140F, MOLDURA = 0xFF5A3A1E, OURO = 0xFFD4A93A, TEXTO = 0xFFE9DFC8, CINZA = 0xFF8A7D6A,
            VERDE = 0xFF7CD67C, VERMELHO = 0xFFE05A4A, ROXO = 0xFFC58CF0, PRATA = 0xFFC8D0D8, AZUL = 0xFF5DADE2;
    private static final int[] COR_NIVEL = {0xFFB8B0A0, 0xFF5DADE2, 0xFFC58CF0, 0xFFFFD84A};

    private int x0, y0;
    private String selP, selS, foco;
    private final List<int[]> chips = new ArrayList<>();      // x, y, índice
    private final List<String> ids = new ArrayList<>(DefRacas.TODAS.keySet());

    public TelaRacas() {
        super(Component.literal("Raças"));
        selP = ClienteRacas.principal;
        selS = ClienteRacas.secundaria;
        foco = selP.isEmpty() ? "humano" : selP;
    }

    public void atualizar() {
        if (selP.isEmpty()) selP = ClienteRacas.principal;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static boolean livre(String id) { return ClienteRacas.liberadas.contains(id); }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = Math.max(10, (height - ALT) / 2);
        if (!ClienteRacas.recebido) Rede.CANAL.sendToServer(new AcaoRaca(AcaoRaca.PEDIR, "", ""));

        // chips por nível
        chips.clear();
        int y = y0 + 34;
        for (int nivel = 0; nivel <= 3; nivel++) {
            int k = 0;
            boolean algum = false;
            for (int i = 0; i < ids.size(); i++) {
                Raca r = DefRacas.de(ids.get(i));
                int nv = Math.min(r.nivel(), DefRacas.LENDARIA);
                if (nv != nivel || (nivel == 3)) continue;
                if (!algum) { y += 11; algum = true; }
                chips.add(new int[]{x0 + 8 + (k % 3) * (CW + 2), y + (k / 3) * (CH + 2), i});
                k++;
            }
            if (algum) y += ((k + 2) / 3) * (CH + 2) + 2;
        }

        // ações da raça em foco
        Raca f = DefRacas.de(foco);
        int bx = x0 + 212, by = y0 + 150;
        if (f != null) {
            Button bp = Button.builder(Component.literal("Usar como principal"), b -> { selP = foco; if (selS.equals(selP)) selS = ""; rebuildWidgets(); })
                    .bounds(bx, by, 88, 16).build();
            bp.active = livre(foco) && !foco.equals(selP);
            if (!livre(foco)) bp.setTooltip(Tooltip.create(Component.literal("Bloqueada: " + f.liberar())));
            addRenderableWidget(bp);
            Button bs = Button.builder(Component.literal(foco.equals(selP) ? "Puro-sangue" : "Usar como secundária"),
                    b -> { selS = foco; rebuildWidgets(); }).bounds(bx + 92, by, 88, 16).build();
            bs.active = livre(foco) && !selP.isEmpty() && !foco.equals(selS) && !f.lendaria()
                    && DefRacas.compat(selP, foco) != DefRacas.Compat.PROIBIDA;
            String dica = !livre(foco) ? "Bloqueada: " + f.liberar()
                    : f.lendaria() ? "Raças lendárias só podem ser a principal."
                    : selP.isEmpty() ? "Escolha a principal primeiro."
                    : DefRacas.compat(selP, foco) == DefRacas.Compat.PROIBIDA ? "Combinação proibida com " + DefRacas.nome(selP) + "."
                    : foco.equals(selP) ? "As duas iguais: puro-sangue (130% da raça)." : "Secundária vale " + Math.round(DefRacas.fatorSecundaria(selP, foco) * 100) + "% com " + DefRacas.nome(selP) + ".";
            bs.setTooltip(Tooltip.create(Component.literal(dica)));
            addRenderableWidget(bs);
        }
        if (!selS.isEmpty())
            addRenderableWidget(Button.builder(Component.literal("Tirar secundária"), b -> { selS = ""; rebuildWidgets(); })
                    .bounds(x0 + 120, y0 + ALT - 68, 74, 14).build());

        // confirmar
        boolean mudou = !selP.equals(ClienteRacas.principal) || !selS.equals(ClienteRacas.secundaria) || !ClienteRacas.escolheu;
        boolean valido = !selP.isEmpty() && DefRacas.compat(selP, selS) != DefRacas.Compat.PROIBIDA;
        String rot = !ClienteRacas.escolheu ? "Confirmar (grátis)" : ClienteRacas.custo == 0 ? "Trocar (troca grátis)" : "Trocar · " + Moedas.texto(ClienteRacas.custo);
        Button conf = Button.builder(Component.literal(rot), b -> Rede.CANAL.sendToServer(new AcaoRaca(AcaoRaca.CONFIRMAR, selP, selS)))
                .bounds(x0 + LARG - 150, y0 + ALT - 24, 142, 18).build();
        conf.active = mudou && valido;
        conf.setTooltip(Tooltip.create(Component.literal(!ClienteRacas.escolheu ? "A primeira escolha é grátis, e a primeira troca também."
                : "Trocar de raça não apaga conquistas, habilidades nem progresso.\nTrocas feitas: " + ClienteRacas.trocas)));
        addRenderableWidget(conf);

        DefRacas.Raca atual = DefRacas.de(ClienteRacas.principal);
        if (atual != null && atual.lendaria() && ClienteRacas.escolheu) {
            Button rev = Button.builder(Component.literal(ClienteRacas.revelar ? "Disfarçar" : "Revelar"),
                    b -> Rede.CANAL.sendToServer(new AcaoRaca(AcaoRaca.REVELAR, "", ""))).bounds(x0 + 8, y0 + ALT - 24, 70, 18).build();
            rev.setTooltip(Tooltip.create(Component.literal(ClienteRacas.revelar ? "Voltar a aparecer como " + (ClienteRacas.secundaria.isEmpty() ? "Humano" : DefRacas.nome(ClienteRacas.secundaria)) + "."
                    : "Mostrar a todos que você é " + atual.nome() + ". Disfarçado, só NPCs e jogadores nível 50+ percebem.")));
            addRenderableWidget(rev);
        }
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(x0 + LARG - 204, y0 + ALT - 24, 50, 18).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (super.mouseClicked(mx, my, botao)) return true;
        for (int[] c : chips) {
            if (mx >= c[0] && mx < c[0] + CW && my >= c[1] && my < c[1] + CH) {
                String id = ids.get(c[2]);
                if (foco.equals(id) && botao == 0 && livre(id) && selP.isEmpty()) selP = id;   // duplo uso: escolhe direto
                foco = id;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 2, y0 - 2, x0 + LARG + 2, y0 + ALT + 2, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, FUNDO);
        g.drawString(font, "✦ Raças", x0 + 8, y0 + 8, OURO, false);
        String atual = !ClienteRacas.escolheu ? "Você ainda não tem raça" : "Atual: " + DefRacas.nome(ClienteRacas.principal)
                + (ClienteRacas.secundaria.isEmpty() ? "" : ClienteRacas.secundaria.equals(ClienteRacas.principal) ? " (puro-sangue)" : " / " + DefRacas.nome(ClienteRacas.secundaria));
        g.drawString(font, atual, x0 + LARG - 8 - font.width(atual), y0 + 8, ClienteRacas.escolheu ? TEXTO : VERMELHO, false);
        g.drawString(font, "Principal 100% · secundária 35–60% (compatibilidade) · as duas iguais = puro-sangue 130%", x0 + 8, y0 + 20, CINZA, false);

        // títulos dos níveis + chips
        int ultimoNivel = -1;
        for (int[] c : chips) {
            String id = ids.get(c[2]);
            Raca r = DefRacas.de(id);
            if (r.nivel() != ultimoNivel && Math.min(r.nivel(), 2) != Math.min(ultimoNivel, 2)) {
                ultimoNivel = r.nivel();
                String t = switch (Math.min(r.nivel(), 2)) {
                    case 0 -> "COMUNS · livres";
                    case 1 -> "ESPECIAIS · nível 20 ou honra num reino";
                    default -> "LENDÁRIAS · por feitos";
                };
                g.drawString(font, t, x0 + 8, c[1] - 10, COR_NIVEL[Math.min(r.nivel(), 2)], false);
            }
            boolean ehP = id.equals(selP), ehS = id.equals(selS) && !id.equals(selP), focado = id.equals(foco), liv = livre(id);
            boolean sobre = mx >= c[0] && mx < c[0] + CW && my >= c[1] && my < c[1] + CH;
            int borda = ehP ? OURO : ehS ? PRATA : focado ? TEXTO : 0xFF3A3128;
            g.fill(c[0] - 1, c[1] - 1, c[0] + CW + 1, c[1] + CH + 1, borda);
            g.fill(c[0], c[1], c[0] + CW, c[1] + CH, sobre ? 0xFF2E261C : liv ? 0xFF221C15 : 0xFF151210);
            String nome = (liv ? "" : "⊘ ") + r.nome();
            if (font.width(nome) > CW - 6) nome = font.plainSubstrByWidth(nome, CW - 8) + "…";
            g.drawString(font, nome, c[0] + 3, c[1] + 4, liv ? COR_NIVEL[r.nivel()] : 0xFF5A5248, false);
            if (ehP || ehS) g.drawString(font, ehP ? "1" : "2", c[0] + CW - 7, c[1] + 4, ehP ? OURO : PRATA, false);
        }

        // detalhe da raça em foco
        Raca f = DefRacas.de(foco);
        int dx = x0 + 212, dy = y0 + 34, dw = LARG - 220;
        g.fill(dx - 4, dy - 4, x0 + LARG - 6, y0 + 170, 0xFF1C1712);
        if (f != null) {
            g.drawString(font, f.nome(), dx, dy, COR_NIVEL[f.nivel()], false);
            String nv = DefRacas.NOME_NIVEL[f.nivel()];
            g.drawString(font, nv, dx + dw - font.width(nv), dy, CINZA, false);
            int y = dy + 12;
            for (FormattedCharSequence l : font.split(Component.literal(f.descricao()), dw)) { g.drawString(font, l, dx, y, TEXTO, false); y += 9; }
            y += 3;
            for (String v : f.vantagens()) { for (FormattedCharSequence l : font.split(Component.literal("+ " + v), dw)) { g.drawString(font, l, dx, y, VERDE, false); y += 9; } }
            for (String v : f.desvantagens()) { for (FormattedCharSequence l : font.split(Component.literal("− " + v), dw)) { g.drawString(font, l, dx, y, VERMELHO, false); y += 9; } }
            if (!livre(foco)) {
                y += 2;
                for (FormattedCharSequence l : font.split(Component.literal("⊘ Como liberar: " + f.liberar()), dw)) { g.drawString(font, l, dx, y, OURO, false); y += 9; }
            }
        }

        // combinação escolhida
        int cy = y0 + ALT - 84;
        g.fill(x0 + 6, cy - 4, x0 + LARG - 6, cy - 3, MOLDURA);
        g.drawString(font, "Principal:", x0 + 8, cy, CINZA, false);
        g.drawString(font, selP.isEmpty() ? "—" : DefRacas.nome(selP), x0 + 58, cy, OURO, false);
        g.drawString(font, "Secundária:", x0 + 8, cy + 20, CINZA, false);
        g.drawString(font, selS.isEmpty() ? "—" : selS.equals(selP) ? "(puro-sangue)" : DefRacas.nome(selS), x0 + 66, cy + 20, PRATA, false);
        if (!selP.isEmpty()) {
            DefRacas.Compat c = DefRacas.compat(selP, selS);
            int cor = switch (c) { case PURO, HARMONICA -> VERDE; case ESTAVEL -> AZUL; case INSTAVEL -> OURO; default -> VERMELHO; };
            String ct = "Compatibilidade: " + c.nome + (c == DefRacas.Compat.PROIBIDA ? "" : selS.isEmpty() ? "" :
                    c == DefRacas.Compat.PURO ? " (130%)" : " (secundária " + Math.round(c.fator * 100) + "%)");
            g.drawString(font, ct, x0 + 200, cy, cor, false);
            // atributos somados
            Map<String, Double> t = DefRacas.somar(selP, selS);
            List<String> linhas = new ArrayList<>(DefRacas.resumo(t));
            for (String e : DefRacas.efeitos(selP, selS)) linhas.add(DefRacas.nomeEfeito(e));
            int y = cy + 12;
            int x = x0 + 200;
            StringBuilder sb = new StringBuilder();
            for (String l : linhas) { if (sb.length() > 0) sb.append(" · "); sb.append(l); }
            int n = 0;
            for (FormattedCharSequence l : font.split(Component.literal(sb.toString()), LARG - 210)) {
                if (n++ >= 4) break;
                g.drawString(font, l, x, y, TEXTO, false);
                y += 9;
            }
        }
        super.render(g, mx, my, pt);
    }
}
