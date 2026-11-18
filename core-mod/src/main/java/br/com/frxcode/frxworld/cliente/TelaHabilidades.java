package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoHabilidade;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.SyncHabilidades;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Tecla K: escolher a classe (primeira vez) ou ver as habilidades e montar a barra de combate. */
public class TelaHabilidades extends Screen {
    private static final int LARG = 380, ALT = 230, LINHA = 20, POR_PAGINA = 8;
    private static final int FUNDO = 0xF017140F, MOLDURA = 0xFF5A3A1E, OURO = 0xFFD4A93A, TEXTO = 0xFFE9DFC8,
            CINZA = 0xFF8A7D6A, ROXO = 0xFFC58CF0, VERMELHO = 0xFFE05A4A;

    private static final String[][] CLASSES = {
            {"GUERREIRO", "Guerreiro", "Fúria", "Corpo a corpo, vida e armadura altas. A fúria enche ao bater e ao apanhar."},
            {"ARQUEIRO", "Arqueiro", "Energia", "Dano à distância e mobilidade. Energia baixa que volta rápido."},
            {"MAGO", "Mago", "Mana", "Magia de dano em área. A mana cresce com o nível."},
            {"CLERIGO", "Clérigo", "Mana", "Cura e magia sagrada, forte contra mortos-vivos."},
            {"LADINO", "Ladino", "Energia", "Furtividade e golpes pelas costas com dano dobrado."},
            {"ASSASSINO", "Assassino", "Energia", "Dano explosivo: +50% pelas costas e +50% em alvos quase mortos."},
            {"INVOCADOR", "Invocador", "Mana", "Invoca criaturas aliadas que lutam por você."},
            {"PALADINO", "Paladino", "Mana", "Protetor sagrado: toma o dano dos aliados, cura e pune os mortos-vivos."},
            {"BARDO", "Bardo", "Mana", "Suporte: canções que deixam o grupo mais rápido, mais forte e mais vivo, e encurtam as recargas dos aliados. Quem luta perto de um Bardo causa 8% a mais de dano."},
            {"DRUIDA", "Druida", "Mana", "Metamorfo: vira lobo (rápido, garras), urso (aguenta dano) e ave (voa) por tempo limitado. Cada forma tem a sua barra de habilidades; na forma humana cura aos poucos e prende inimigos com raízes."},
            {"MONGE", "Monge", "Chi", "Luta de mão vazia: socos em combo (o 4º é o golpe final) geram chi, que paga as habilidades. Quanto menos armadura, mais esquiva e menos dano recebido."},
    };

    private int x0, y0, pagina = 0, selecionada = 0, classeEscolhida = -1;
    /** Espaço da barra esperando a tecla nova (0..4 = barra, 5 = abrir a janela; -1 = nenhum). */
    private int esperandoTecla = -1;

    public TelaHabilidades() {
        super(Component.literal("Habilidades"));
    }

    public void atualizar() {
        rebuildWidgets();
    }

    private List<SyncHabilidades.Info> lista() {
        return ClienteHabilidades.habilidades;
    }

    private SyncHabilidades.Info sel() {
        List<SyncHabilidades.Info> l = lista();
        return selecionada >= 0 && selecionada < l.size() ? l.get(selecionada) : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = Math.max(28, (height - ALT) / 2 + 10);
        TelaStatus.criarAbas(this::addRenderableWidget, () -> x0 + 4, () -> y0 - 2, "HABILIDADES", true);
        if (ClienteHabilidades.classe.isEmpty()) initClasses();
        else initHabilidades();
    }

    private void initClasses() {
        for (int i = 0; i < CLASSES.length; i++) {
            final int idx = i;
            int col = i % 3, lin = i / 3;
            addRenderableWidget(Button.builder(Component.literal(CLASSES[i][1] + " · " + CLASSES[i][2]), b -> { classeEscolhida = idx; rebuildWidgets(); })
                    .bounds(x0 + 8 + col * 122, y0 + 26 + lin * 22, 118, 18).build());
        }
        Button confirmar = Button.builder(Component.literal(classeEscolhida >= 0 ? "Ser " + CLASSES[classeEscolhida][1] : "Escolha uma classe"), b -> {
            Rede.CANAL.sendToServer(new AcaoHabilidade(AcaoHabilidade.CLASSE, 0, CLASSES[classeEscolhida][0]));
        }).bounds(x0 + LARG / 2 - 80, y0 + ALT - 28, 160, 20).build();
        confirmar.active = classeEscolhida >= 0;
        addRenderableWidget(confirmar);
    }

    private void initHabilidades() {
        List<SyncHabilidades.Info> l = lista();
        int inicio = pagina * POR_PAGINA;
        for (int i = inicio; i < Math.min(l.size(), inicio + POR_PAGINA); i++) {
            final int idx = i;
            SyncHabilidades.Info h = l.get(i);
            String rot = (h.ultimate() ? "★ " : "") + h.nome() + (h.aprendida() ? "  Nv " + h.nivel() : "");
            addRenderableWidget(Button.builder(Component.literal(rot), b -> { selecionada = idx; rebuildWidgets(); })
                    .bounds(x0 + 8, y0 + 24 + (i - inicio) * LINHA, 150, LINHA - 2).build());
        }
        if (l.size() > POR_PAGINA) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina = Math.max(0, pagina - 1); rebuildWidgets(); })
                    .bounds(x0 + 8, y0 + 24 + POR_PAGINA * LINHA, 20, 14).build()).active = pagina > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); })
                    .bounds(x0 + 138, y0 + 24 + POR_PAGINA * LINHA, 20, 14).build()).active = (pagina + 1) * POR_PAGINA < l.size();
        }

        SyncHabilidades.Info h = sel();
        int bx = x0 + 168, by = y0 + 132;
        if (h != null && h.aprendida()) {
            if (h.ultimate()) {
                addRenderableWidget(Button.builder(Component.literal("Pôr na ultimate (" + Teclas.rotulo(4) + ")"),
                        b -> definir(4, h.codigo())).bounds(bx, by, 204, 18).build());
            } else {
                for (int i = 0; i < 4; i++) {
                    final int espaco = i;
                    addRenderableWidget(Button.builder(Component.literal(Teclas.rotulo(i)), b -> definir(espaco, h.codigo()))
                            .bounds(bx + 52 + i * 38, by, 34, 18).build());
                }
            }
        }
        // teclas de cada espaço: clique e aperte a nova
        for (int i = 0; i < 5; i++) addRenderableWidget(botaoTecla(i, xEspaco(i) - 2, y0 + ALT - 55, 26));
        addRenderableWidget(botaoTecla(5, x0 + LARG - 40, y0 + 6, 32));
        {
            String[] info = ClienteHabilidades.despertarInfo(ClienteHabilidades.classe);
            boolean invocador = "INVOCADOR".equals(ClienteHabilidades.classe);
            Button ex = Button.builder(Component.literal(invocador ? "⚫ Exército" : "✦ Despertar"),
                    b -> { if (invocador) net.minecraft.client.Minecraft.getInstance().setScreen(new TelaSombras()); }).bounds(x0 + 168, y0 + 5, 78, 14).build();
            String trava = !invocador && !ClienteHabilidades.despertarLiberado ? "\n\n🔒 Libera no nível 10." : "";
            ex.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "✦ " + info[0] + "  (inspirado em " + info[1] + ")\n\nComo enche: " + info[2] + "\n\n" + info[3]
                            + (invocador ? "\n\nClique para abrir o exército." : "") + trava)));
            addRenderableWidget(ex);
            addRenderableWidget(botaoTecla(6, x0 + 248, y0 + 5, 22));
        }
        // barra: tirar
        for (int i = 0; i < 5; i++) {
            final int espaco = i;
            if (ClienteHabilidades.barra[i].isEmpty()) continue;
            addRenderableWidget(Button.builder(Component.literal("x"), b -> definir(espaco, ""))
                    .bounds(xEspaco(i) + 4, y0 + ALT - 18, 14, 12).build());
        }
    }

    private net.minecraft.client.KeyMapping mapa(int i) {
        return i < 5 ? Teclas.BARRA[i] : i == 6 ? Teclas.DESPERTAR : Teclas.MENU;
    }

    /** Outra tecla do jogo que usa a mesma (null = nenhuma). */
    private String conflito(net.minecraft.client.KeyMapping k) {
        if (k.isUnbound()) return null;
        for (net.minecraft.client.KeyMapping m : net.minecraft.client.Minecraft.getInstance().options.keyMappings)
            if (m != k && m.same(k)) return Component.translatable(m.getName()).getString();
        return null;
    }

    private Button botaoTecla(int i, int x, int y, int w) {
        net.minecraft.client.KeyMapping k = mapa(i);
        String nome = k.getTranslatedKeyMessage().getString();
        if (nome.length() > 4) nome = nome.substring(0, 4);
        String outra = conflito(k);
        Component rot = esperandoTecla == i ? Component.literal("> ? <").withStyle(net.minecraft.ChatFormatting.YELLOW)
                : outra != null ? Component.literal(nome).withStyle(net.minecraft.ChatFormatting.RED) : Component.literal(nome);
        Button b = Button.builder(rot, bt -> { esperandoTecla = esperandoTecla == i ? -1 : i; rebuildWidgets(); }).bounds(x, y, w, i == 6 ? 14 : 12).build();
        String dica = (i == 4 ? "Tecla da ultimate" : i == 5 ? "Tecla que abre a Janela de Status" : i == 6 ? "Tecla do Despertar (" + ClienteHabilidades.despertarInfo(ClienteHabilidades.classe)[0] + ")" : "Tecla do espaço " + (i + 1))
                + "\nClique e aperte a tecla nova (Esc cancela)."
                + (outra != null ? "\nAtenção: já é usada por \"" + outra + "\"." : "");
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(dica)));
        return b;
    }

    private void gravarTecla(com.mojang.blaze3d.platform.InputConstants.Key tecla) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.options.setKey(mapa(esperandoTecla), tecla);
        net.minecraft.client.KeyMapping.resetMapping();
        mc.options.save();
        esperandoTecla = -1;
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mod) {
        if (esperandoTecla >= 0) {
            if (tecla == 256) { esperandoTecla = -1; rebuildWidgets(); }   // Esc cancela
            else gravarTecla(com.mojang.blaze3d.platform.InputConstants.getKey(tecla, scan));
            return true;
        }
        return super.keyPressed(tecla, scan, mod);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (esperandoTecla >= 0 && botao >= 2) {   // botão do meio e laterais do mouse também valem
            gravarTecla(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(botao));
            return true;
        }
        return super.mouseClicked(mx, my, botao);
    }

    private int xEspaco(int i) {
        return x0 + 168 + i * 40 + (i == 4 ? 6 : 0);
    }

    private void definir(int espaco, String codigo) {
        Rede.CANAL.sendToServer(new AcaoHabilidade(AcaoHabilidade.DEFINIR_ESPACO, espaco, codigo));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 2, y0 - 2, x0 + LARG + 2, y0 + ALT + 2, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, FUNDO);
        if (ClienteHabilidades.classe.isEmpty()) renderClasses(g);
        else renderHabilidades(g);
        super.render(g, mx, my, pt);
    }

    private void renderClasses(GuiGraphics g) {
        g.drawCenteredString(font, "Escolha sua classe", x0 + LARG / 2, y0 + 10, OURO);
        int yTexto = y0 + 30 + ((CLASSES.length + 2) / 3) * 22;
        if (classeEscolhida < 0) {
            g.drawCenteredString(font, "Clique numa classe para ver como ela joga.", x0 + LARG / 2, yTexto + 10, CINZA);
        } else {
            g.drawString(font, CLASSES[classeEscolhida][1] + " · " + CLASSES[classeEscolhida][2], x0 + 12, yTexto, OURO, false);
            List<FormattedCharSequence> linhas = font.split(Component.literal(CLASSES[classeEscolhida][3]), LARG - 24);
            for (int k = 0; k < Math.min(5, linhas.size()); k++)
                g.drawString(font, linhas.get(k), x0 + 12, yTexto + 13 + k * 10, TEXTO, false);
        }
        g.drawCenteredString(font, "A escolha é permanente (só um administrador troca).", x0 + LARG / 2, y0 + ALT - 42, VERMELHO);
    }

    private void renderHabilidades(GuiGraphics g) {
        String classe = switch (ClienteHabilidades.classe) {
            case "GUERREIRO" -> "Guerreiro"; case "ARQUEIRO" -> "Arqueiro"; case "MAGO" -> "Mago";
            case "CLERIGO" -> "Clérigo"; case "LADINO" -> "Ladino"; case "ASSASSINO" -> "Assassino"; case "INVOCADOR" -> "Invocador"; case "PALADINO" -> "Paladino"; case "BARDO" -> "Bardo"; case "MONGE" -> "Monge"; case "DRUIDA" -> "Druida"; default -> ClienteHabilidades.classe;
        };
        g.drawString(font, classe + " · " + ClienteHabilidades.nomeRecurso() + " " + ClienteHabilidades.recursoAtual + "/" + ClienteHabilidades.recursoMax,
                x0 + 8, y0 + 8, OURO, false);
        if (lista().isEmpty()) g.drawString(font, "Nenhuma habilidade cadastrada no painel.", x0 + 8, y0 + 30, CINZA, false);

        // marca as não aprendidas
        int inicio = pagina * POR_PAGINA;
        for (int i = inicio; i < Math.min(lista().size(), inicio + POR_PAGINA); i++) {
            SyncHabilidades.Info h = lista().get(i);
            int y = y0 + 24 + (i - inicio) * LINHA;
            if (!h.aprendida()) g.fill(x0 + 8, y, x0 + 158, y + LINHA - 2, 0x90000000);
            if (i == selecionada) g.fill(x0 + 6, y, x0 + 7, y + LINHA - 2, OURO);
        }

        SyncHabilidades.Info h = sel();
        int dx = x0 + 168, dy = y0 + 24;
        if (h != null) {
            g.renderItem(ClienteHabilidades.icone(h.icone()), dx, dy);
            g.drawString(font, h.nome(), dx + 20, dy, h.ultimate() ? ROXO : TEXTO, false);
            g.drawString(font, (h.ultimate() ? "ULTIMATE · " : "") + h.tipo() + " · " + h.elemento(), dx + 20, dy + 10, CINZA, false);
            int y = dy + 24;
            g.drawString(font, "Custo " + h.custo() + " · Recarga " + fmt(h.recarga()) + "s", dx, y, TEXTO, false);
            y += 11;
            if (h.aprendida()) {
                g.drawString(font, "Nível " + h.nivel() + "/10 · usada " + h.usos() + "x", dx, y, 0xFF7CD67C, false);
            } else {
                g.drawString(font, h.porItem() ? "Aprende com um Tomo Arcano (nível " + h.nivelReq() + "+)" : "Libera no nível " + h.nivelReq(),
                        dx, y, VERMELHO, false);
            }
            y += 14;
            for (FormattedCharSequence linha : font.split(Component.literal(h.descricao()), 204)) {
                g.drawString(font, linha, dx, y, TEXTO, false);
                y += 10;
                if (y > y0 + 126) break;
            }
            if (h.aprendida() && !h.ultimate()) g.drawString(font, "Pôr em:", dx, y0 + 137, CINZA, false);
        }

        // barra atual
        g.drawString(font, "Sua barra", dx, y0 + ALT - 68, OURO, false);
        g.drawString(font, "Abrir janela:", x0 + LARG - 44 - font.width("Abrir janela:"), y0 + 8, CINZA, false);
        for (int i = 0; i < 5; i++) {
            int x = xEspaco(i), y = y0 + ALT - 40;
            g.fill(x - 1, y - 1, x + 23, y + 23, i == 4 ? 0xFFB8892A : MOLDURA);
            g.fill(x, y, x + 22, y + 22, 0xFF0F0D0B);
            SyncHabilidades.Info b = ClienteHabilidades.info(ClienteHabilidades.barra[i]);
            if (b != null) g.renderItem(ClienteHabilidades.icone(b.icone()), x + 3, y + 3);
        }
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }
}
