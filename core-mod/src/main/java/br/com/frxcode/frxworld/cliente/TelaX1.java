package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.aventura.X1;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import static br.com.frxcode.frxworld.cliente.TelaStatus.BORDA2;
import static br.com.frxcode.frxworld.cliente.TelaStatus.CINZA;
import static br.com.frxcode.frxworld.cliente.TelaStatus.FUNDO;
import static br.com.frxcode.frxworld.cliente.TelaStatus.MOLDURA;
import static br.com.frxcode.frxworld.cliente.TelaStatus.OURO;
import static br.com.frxcode.frxworld.cliente.TelaStatus.TEXTO;
import static br.com.frxcode.frxworld.cliente.TelaStatus.TITULO;
import static br.com.frxcode.frxworld.cliente.TelaStatus.VERMELHO;

/**
 * Aba X1 do inventário: escolhe um jogador online, combina as regras (habilidades, itens, poções, arena, formato e
 * aposta) e manda o desafio. Também mostra o desafio recebido, as lutas em andamento (para assistir) e o placar.
 * As escolhas ficam lembradas enquanto o jogo está aberto.
 */
public class TelaX1 extends Screen {
    private static final int POR_PAGINA = 7;

    // regras escolhidas (lembradas entre uma abertura e outra)
    private static boolean habilidades = true, kit = false, pocoes = true, melhorDe3 = false, vidaIgual = false;
    private static int arena = 0, aposta = X1.SEM_APOSTA;
    private static String valorAposta = "";

    private CompoundTag d;
    private int x0, y0, larg, alt, pagina, alvo = -1, contador;
    private EditBox caixaAposta;
    private Button botaoDesafiar;

    public TelaX1(CompoundTag d) {
        super(Component.literal("X1"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) {
        this.d = novo;
        rebuildWidgets();
    }

    private void enviar(int acao, int numero, long valor) { Rede.CANAL.sendToServer(new AcaoMenu("x1", numero, acao, valor)); }
    private ListTag lista(String chave) { return d.getList(chave, Tag.TAG_COMPOUND); }
    private String corta(String t, int largura) { return font.width(t) <= largura ? t : font.plainSubstrByWidth(t, Math.max(0, largura - 6)) + "…"; }

    private long valorEmCobre() {
        try { return valorAposta.isEmpty() ? 0 : Math.min(X1.APOSTA_MAX, Long.parseLong(valorAposta)); } catch (Exception e) { return 0; }
    }

    /** O jogador escolhido na lista (ou null se saiu do jogo). */
    private CompoundTag escolhido() {
        for (Tag t : lista("jogadores")) if (((CompoundTag) t).getInt("id") == alvo) return (CompoundTag) t;
        return null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        if (contador == 0) enviar(X1.PEDIR, 0, 0);
        larg = Math.min(380, width - 10);   // mesmo painel das outras abas
        alt = Math.min(230, height - 34);
        x0 = (width - larg) / 2;
        y0 = Math.max(28, (height - alt) / 2 + 10);
        TelaStatus.criarAbas(this::addRenderableWidget, () -> x0 + 4, () -> y0 - 2, "X1", true);
        caixaAposta = null;
        botaoDesafiar = null;
        if (!d.getBoolean("pronto")) return;

        // ---- coluna da esquerda: quem está online
        ListTag jogadores = lista("jogadores");
        int paginas = Math.max(1, (jogadores.size() + POR_PAGINA - 1) / POR_PAGINA);
        pagina = Math.max(0, Math.min(paginas - 1, pagina));
        int colEsq = 150, y = y0 + 36;
        for (int i = pagina * POR_PAGINA; i < Math.min(jogadores.size(), (pagina + 1) * POR_PAGINA); i++) {
            CompoundTag j = jogadores.getCompound(i);
            int id = j.getInt("id");
            String estado = j.getString("estado");
            Button b = Button.builder(Component.literal((id == alvo ? "▶ " : "") + corta(j.getString("nome"), colEsq - 60) + "  Nv " + j.getInt("nivel")), bt -> { alvo = id; rebuildWidgets(); })
                    .bounds(x0 + 8, y, colEsq, 14).build();
            b.active = estado.isEmpty();
            b.setTooltip(Tooltip.create(Component.literal(estado.isEmpty() ? "Clique para escolher como adversário." : "Não pode ser desafiado agora: está " + estado + ".")));
            addRenderableWidget(b);
            y += 16;
        }
        if (paginas > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), bt -> { pagina--; rebuildWidgets(); }).bounds(x0 + 8 + colEsq - 38, y0 + 26, 18, 12).build()).active = pagina > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), bt -> { pagina++; rebuildWidgets(); }).bounds(x0 + 8 + colEsq - 18, y0 + 26, 18, 12).build()).active = pagina < paginas - 1;
        }

        // ---- coluna da direita: regras do duelo
        int xd = x0 + 16 + colEsq, ld = larg - colEsq - 24;
        ListTag arenas = lista("arenas");
        if (arena > arenas.size()) arena = 0;
        y = y0 + 34;
        opcao(xd, y, ld, "Habilidades: " + (habilidades ? "sim" : "não"), "Habilidades de classe, Despertar, Suprema e as invocações (sombras e monstros chamados).\nCom \"sim\", elas ferem o adversário mesmo que o seu \"Dano em jogadores\" esteja desligado.",
                () -> habilidades = !habilidades);
        y += 14;
        opcao(xd, y, ld, "Itens: " + (kit ? "kit sorteado" : "do personagem"), kit
                        ? "Os dois recebem o mesmo kit (armadura, arma, comida), sorteado na hora. Seus itens, anéis e acessórios ficam guardados e voltam no fim."
                        : "Cada um luta com o que tem no inventário. No fim tudo volta como estava: nada gasta, nada quebra.",
                () -> kit = !kit);
        y += 14;
        opcao(xd, y, ld, "Vida: " + (vidaIgual ? "igual para os dois" : "a de cada um"), vidaIgual
                        ? (kit ? "Os dois lutam com 20 corações." : "Os dois lutam com a mesma vida: a de quem tem menos.")
                        : "Cada um luta com a vida máxima que tem.",
                () -> vidaIgual = !vidaIgual);
        y += 14;
        opcao(xd, y, ld, "Poções: " + (pocoes ? "sim" : "não"), pocoes ? "Poções de beber e de arremessar liberadas (o kit sorteado vem com algumas)." : "Ninguém bebe nem arremessa poção.",
                () -> pocoes = !pocoes);
        y += 14;
        String nomeArena = arena == 0 ? "sorteada" : arenas.getCompound(arena - 1).getString("nome") + (arenas.getCompound(arena - 1).getBoolean("ocupada") ? " (ocupada)" : "");
        opcao(xd, y, ld, "Arena: " + nomeArena, arenas.isEmpty() ? "Ainda não há arena pronta. O admin cria com /frx arena." : "Clique para passar para a próxima arena. \"Sorteada\" escolhe uma livre na hora.",
                () -> arena = (arena + 1) % (arenas.size() + 1));
        y += 14;
        opcao(xd, y, ld, "Formato: " + (melhorDe3 ? "melhor de 3" : "luta única"), "Cada rodada dura até 5 minutos; no tempo esgotado, leva quem estiver com mais vida.",
                () -> melhorDe3 = !melhorDe3);
        y += 14;
        String nomeAposta = switch (aposta) {
            case X1.APOSTA_MOEDAS -> "moedas";
            case X1.APOSTA_ITEM -> "item na mão";
            default -> "nenhuma";
        };
        opcao(xd, y, ld, "Aposta: " + nomeAposta, "Moedas: os dois põem o mesmo valor e o vencedor leva tudo.\nItem: cada um aposta o item que estiver segurando; o vencedor leva os dois.\nA aposta fica guardada com o servidor até o fim da luta.",
                () -> aposta = (aposta + 1) % 3);
        y += 14;
        if (aposta == X1.APOSTA_MOEDAS) {
            caixaAposta = new EditBox(font, xd + 1, y + 1, 60, 12, Component.literal("Valor"));
            caixaAposta.setMaxLength(7);
            caixaAposta.setFilter(t -> t.matches("\\d{0,7}"));
            caixaAposta.setHint(Component.literal("em cobre"));
            caixaAposta.setValue(valorAposta);
            caixaAposta.setResponder(t -> valorAposta = t);
            addRenderableWidget(caixaAposta);
        }
        y += 14;
        CompoundTag e = escolhido();
        botaoDesafiar = Button.builder(Component.literal(e == null ? "Escolha um jogador" : "⚔ Desafiar " + corta(e.getString("nome"), ld - 70)), bt -> {
            if (podeDesafiar()) enviar(X1.DESAFIAR, alvo, X1.empacotar(habilidades, kit, pocoes, melhorDe3, aposta, arena, valorEmCobre(), vidaIgual));
        }).bounds(xd, y, ld, 16).build();
        botaoDesafiar.active = podeDesafiar();
        addRenderableWidget(botaoDesafiar);

        // ---- rodapé: desafio recebido, desafio enviado, ou as lutas para assistir
        int yr = y0 + alt - 22;
        if (d.contains("recebido")) {
            addRenderableWidget(Button.builder(Component.literal("Aceitar").withStyle(net.minecraft.ChatFormatting.GREEN), bt -> enviar(X1.ACEITAR, 0, 0)).bounds(x0 + larg - 128, yr, 58, 16).build());
            addRenderableWidget(Button.builder(Component.literal("Recusar").withStyle(net.minecraft.ChatFormatting.RED), bt -> enviar(X1.RECUSAR, 0, 0)).bounds(x0 + larg - 66, yr, 58, 16).build());
        } else if (d.contains("enviado")) {
            addRenderableWidget(Button.builder(Component.literal("Cancelar desafio"), bt -> enviar(X1.CANCELAR, 0, 0)).bounds(x0 + larg - 108, yr, 100, 16).build());
        } else if (d.getBoolean("assistindo")) {
            addRenderableWidget(Button.builder(Component.literal("Sair da arquibancada"), bt -> enviar(X1.SAIR, 0, 0)).bounds(x0 + larg - 128, yr, 120, 16).build());
        } else {
            int n = 0;
            for (int i = 0; i < arenas.size() && n < 2; i++) {
                CompoundTag a = arenas.getCompound(i);
                if (!a.getBoolean("ocupada") || !a.getBoolean("plateia")) continue;
                final int numero = i + 1;
                Button b = Button.builder(Component.literal("Assistir"), bt -> enviar(X1.ASSISTIR, numero, 0)).bounds(x0 + larg - 62, y0 + alt - 40 + n * 17, 54, 14).build();
                b.setTooltip(Tooltip.create(Component.literal("Vai para a arquibancada da arena " + a.getString("nome") + ". No fim da luta você volta sozinho.")));
                addRenderableWidget(b);
                n++;
            }
        }
    }

    /** Dá para mandar o desafio agora? (alvo livre, arena pronta, nenhum desafio em aberto e a aposta coberta) */
    private boolean podeDesafiar() {
        CompoundTag e = escolhido();
        if (e == null || !e.getString("estado").isEmpty() || d.contains("recebido") || d.contains("enviado") || d.getBoolean("assistindo") || lista("arenas").isEmpty()) return false;
        if (aposta == X1.APOSTA_MOEDAS) return valorEmCobre() > 0 && valorEmCobre() <= d.getLong("bolsa");
        if (aposta == X1.APOSTA_ITEM) return !d.getString("mao").isEmpty();
        return true;
    }

    /** Botão de regra: cada clique passa para a próxima escolha. */
    private void opcao(int x, int y, int largura, String texto, String dica, Runnable aoClicar) {
        Button b = Button.builder(Component.literal(texto), bt -> { aoClicar.run(); rebuildWidgets(); }).bounds(x, y, largura, 14).build();
        b.setTooltip(Tooltip.create(Component.literal(dica)));
        addRenderableWidget(b);
    }

    @Override
    public void tick() {
        super.tick();
        if (caixaAposta != null) caixaAposta.tick();
        if (botaoDesafiar != null) botaoDesafiar.active = podeDesafiar();   // o valor da aposta muda enquanto se digita
        if (++contador % 40 == 0 && (caixaAposta == null || !caixaAposta.isFocused())) enviar(X1.PEDIR, 0, 0);   // lista de online, lutas e tempo do convite
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mod) {
        if (caixaAposta != null && caixaAposta.isFocused()) return caixaAposta.keyPressed(tecla, scan, mod) || super.keyPressed(tecla, scan, mod);
        if (Minecraft.getInstance().options.keyInventory.matches(tecla, scan)) { onClose(); return true; }
        return super.keyPressed(tecla, scan, mod);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 2, y0 - 2, x0 + larg + 2, y0 + alt + 2, MOLDURA);
        g.fill(x0, y0, x0 + larg, y0 + alt, FUNDO);
        if (!d.getBoolean("pronto")) {
            g.drawCenteredString(font, "Carregando…", x0 + larg / 2, y0 + alt / 2, CINZA);
            super.render(g, mx, my, pt);
            return;
        }
        // cabeçalho: título e o meu retrospecto
        g.drawString(font, "X1  ·  duelo combinado, sem punição de morte", x0 + 10, y0 + 10, TITULO, false);
        if (d.contains("meu")) {
            CompoundTag m = d.getCompound("meu");
            String meu = m.getInt("v") + " V · " + m.getInt("d") + " D · sequência " + m.getInt("seq");
            g.drawString(font, meu, x0 + larg - 10 - font.width(meu), y0 + 10, TEXTO, false);
        }
        g.fill(x0 + 8, y0 + 22, x0 + larg - 8, y0 + 23, BORDA2);

        int colEsq = 150, xd = x0 + 16 + colEsq;
        g.drawString(font, "JOGADORES ONLINE", x0 + 8, y0 + 26, OURO, false);
        if (lista("jogadores").isEmpty()) g.drawString(font, "Ninguém mais online.", x0 + 10, y0 + 40, CINZA, false);
        g.drawString(font, "REGRAS", xd, y0 + 26, OURO, false);
        // linha da aposta, logo abaixo dos sete botões de regra
        int ya = y0 + 34 + 7 * 14 + 3;
        switch (aposta) {
            case X1.APOSTA_MOEDAS -> {
                long v = valorEmCobre();
                int x = xd + 66;
                if (v > 0) x += ClienteMenus.moedas(g, font, x, ya, v, v > d.getLong("bolsa") ? VERMELHO : TEXTO) + 4;
                g.drawString(font, v > d.getLong("bolsa") ? "falta na bolsa" : v == 0 ? "100 = 1 ouro" : "de cada um", x, ya, v > d.getLong("bolsa") ? VERMELHO : CINZA, false);
            }
            case X1.APOSTA_ITEM -> {
                String mao = d.getString("mao");
                g.drawString(font, mao.isEmpty() ? "Segure o item que vai apostar." : corta("Na mão: " + mao, larg - colEsq - 26), xd + 2, ya, mao.isEmpty() ? VERMELHO : TEXTO, false);
            }
            default -> g.drawString(font, "Sem aposta: só pela glória.", xd + 2, ya, CINZA, false);
        }

        // rodapé
        int yb = y0 + alt - 60;
        g.fill(x0 + 8, yb - 4, x0 + larg - 8, yb - 3, BORDA2);
        if (d.contains("recebido")) {
            CompoundTag c = d.getCompound("recebido");
            g.drawString(font, "⚔ " + c.getString("de") + " desafiou você! (" + c.getInt("seg") + " s)", x0 + 10, yb, OURO, false);
            g.drawString(font, corta(c.getString("regras"), larg - 20), x0 + 10, yb + 11, TEXTO, false);
            if (!c.getString("aposta").isEmpty()) g.drawString(font, corta("Aposta: " + c.getString("aposta"), larg - 20), x0 + 10, yb + 22, 0xFFE0B040, false);
        } else if (d.contains("enviado")) {
            CompoundTag c = d.getCompound("enviado");
            g.drawString(font, "Aguardando a resposta de " + c.getString("para") + " (" + c.getInt("seg") + " s)…", x0 + 10, yb, OURO, false);
            g.drawString(font, "Se não responder, a aposta volta para você.", x0 + 10, yb + 11, CINZA, false);
        } else {
            // placar à esquerda, lutas em andamento à direita
            g.drawString(font, "PLACAR", x0 + 10, yb, OURO, false);
            ListTag placar = lista("placar");
            if (placar.isEmpty()) g.drawString(font, "Nenhum duelo ainda.", x0 + 10, yb + 11, CINZA, false);
            for (int i = 0; i < Math.min(4, placar.size()); i++) {
                CompoundTag l = placar.getCompound(i);
                g.drawString(font, (i + 1) + "º " + corta(l.getString("nome"), 70) + "  " + l.getInt("v") + " V · " + l.getInt("d") + " D", x0 + 10, yb + 11 + i * 10, i == 0 ? TEXTO : CINZA, false);
            }
            g.drawString(font, "LUTAS AGORA", xd, yb, OURO, false);
            int n = 0;
            for (Tag t : lista("arenas")) {
                CompoundTag a = (CompoundTag) t;
                if (!a.getBoolean("ocupada")) continue;
                if (n < 2) g.drawString(font, corta(a.getString("luta") + " · " + a.getString("nome"), larg - colEsq - 90), xd, y0 + alt - 37 + n * 17, TEXTO, false);
                n++;
            }
            if (n == 0) g.drawString(font, "Nenhuma no momento.", xd, yb + 11, CINZA, false);
        }
        super.render(g, mx, my, pt);
    }
}
