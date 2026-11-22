package br.com.frxcode.frxworld.cliente;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Janela de Status (estilo "sistema" de anime): abas no topo, abertas pelo inventário (E) ou pela tecla K.
 * Status · Talentos · Reinos · Missões · Conquistas · Grupo; Inventário e Habilidades abrem as telas próprias.
 * Ações usam os comandos do jogador (/talentos, /titulo, /grupo), e a janela pede os dados de novo logo depois.
 */
public class TelaStatus extends Screen {
    public static final String[][] ABAS = {
            {"INVENTARIO", "Inventário"}, {"STATUS", "Status"}, {"HABILIDADES", "Habilidades"}, {"TALENTOS", "Talentos"},
            {"REINOS", "Reinos"}};   // a aba Grupo saiu na 1.1.9: ficaram só os clãs

    // mesmas cores da tela de Habilidades (painel marrom-escuro, moldura marrom, títulos dourados)
    static final int FUNDO = 0xF017140F, MOLDURA = 0xFF5A3A1E, BORDA2 = 0xFF5A3A1E, TITULO = 0xFFD4A93A, TEXTO = 0xFFE9DFC8,
            CINZA = 0xFF8A7D6A, OURO = 0xFFD4A93A, VERDE = 0xFF7CD67C, VERMELHO = 0xFFE05A4A, ROXO = 0xFFC58CF0, BARRA_FUNDO = 0xFF0F0D0B;

    private String aba;
    private int x0, y0, larg, alt, rolagem = 0, maxRolagem = 0, contador = 0;
    private int tituloIdx = -1;
    private long confirmarReset = 0;
    private EditBox convite;

    public TelaStatus(String aba) {
        super(Component.literal("Janela de Status"));
        this.aba = aba;
    }

    // ------------------------------------------------------------------ abas (também usadas no inventário)

    private static net.minecraft.world.item.ItemStack icone(String id) {
        return new net.minecraft.world.item.ItemStack(switch (id) {
            case "INVENTARIO" -> net.minecraft.world.item.Items.CHEST;
            case "STATUS" -> net.minecraft.world.item.Items.PLAYER_HEAD;
            case "HABILIDADES" -> net.minecraft.world.item.Items.ENCHANTED_BOOK;
            case "TALENTOS" -> net.minecraft.world.item.Items.NETHER_STAR;
            case "REINOS" -> net.minecraft.world.item.Items.GOLDEN_HELMET;
            default -> net.minecraft.world.item.Items.SHIELD;
        });
    }

    /** Abas com ícone presas ao topo de um painel: x/y = canto de cima à esquerda do painel (as abas ficam logo acima). */
    public static void criarAbas(Consumer<AbaIcone> add, java.util.function.IntSupplier x, java.util.function.IntSupplier y, String atual, boolean escuro) {
        for (int i = 0; i < ABAS.length; i++) {
            final int k = i;
            String id = ABAS[i][0];
            add.accept(new AbaIcone(id, ABAS[i][1], icone(id), id.equals(atual), escuro,
                    () -> x.getAsInt() + k * AbaIcone.LARG, () -> y.getAsInt() - AbaIcone.ALT));
        }
    }

    public static void abrir(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        switch (id) {
            case "INVENTARIO" -> mc.setScreen(new InventoryScreen(mc.player));
            case "HABILIDADES" -> mc.setScreen(new TelaHabilidades());
            default -> {
                if (mc.screen instanceof TelaStatus t) { t.aba = id; t.rolagem = 0; t.rebuildWidgets(); }
                else mc.setScreen(new TelaStatus(id));
            }
        }
    }

    // ------------------------------------------------------------------ ciclo da tela

    public void atualizar() {
        String texto = convite != null ? convite.getValue() : "";
        rebuildWidgets();
        if (convite != null) convite.setValue(texto);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        if (contador == 0) ClienteStatus.pedir();
        larg = Math.min(380, width - 10);   // mesmo tamanho da tela de Habilidades
        alt = Math.min(230, height - 34);
        x0 = (width - larg) / 2;
        y0 = Math.max(28, (height - alt) / 2 + 10);
        criarAbas(this::addRenderableWidget, () -> x0 + 4, () -> y0 - 2, aba, true);
        convite = null;
        switch (aba) {
            case "STATUS" -> initStatus();
            case "TALENTOS" -> initTalentos();
            case "GRUPO" -> initGrupo();
            default -> {}
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (convite != null) convite.tick();
        if (++contador % 40 == 0) ClienteStatus.pedir();   // vida/distância do grupo, recurso etc.
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mod) {
        if (convite != null && convite.isFocused()) {
            if (tecla == 257 || tecla == 335) { convidar(); return true; }   // Enter
            return convite.keyPressed(tecla, scan, mod) || super.keyPressed(tecla, scan, mod);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyInventory.matches(tecla, scan)) { onClose(); return true; }
        return super.keyPressed(tecla, scan, mod);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        rolagem = Math.max(0, Math.min(maxRolagem, rolagem - (int) (delta * 14)));
        return true;
    }

    // ------------------------------------------------------------------ helpers de JSON

    static JsonObject d() { return ClienteStatus.dados; }

    static String s(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    static double n(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsDouble() : 0; } catch (Exception e) { return 0; }
    }

    static boolean b(JsonObject o, String k) {
        try { return o != null && o.has(k) && o.get(k).getAsBoolean(); } catch (Exception e) { return false; }
    }

    static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    // ------------------------------------------------------------------ widgets por aba

    private void initStatus() {
        Button raca = Button.builder(Component.literal("✦ Raça"), bt -> net.minecraft.client.Minecraft.getInstance().setScreen(new TelaRacas()))
                .bounds(x0 + larg - 156, y0 + 8, 56, 14).build();
        raca.setTooltip(Tooltip.create(Component.literal(ClienteRacas.escolheu ? "Ver ou trocar sua raça" : "Você ainda não escolheu uma raça!")));
        addRenderableWidget(raca);
        JsonArray titulos = arr(d(), "titulos");
        if (titulos.size() == 0) return;
        String atual = s(d(), "titulo");
        Button b = Button.builder(Component.literal("Trocar título"), bt -> {
            tituloIdx = (tituloIdx + 1) % (titulos.size() + 1);
            String t = tituloIdx == titulos.size() ? "nenhum" : titulos.get(tituloIdx).getAsString();
            ClienteStatus.comando("titulo usar " + t);
        }).bounds(x0 + larg - 96, y0 + 8, 88, 14).build();
        b.setTooltip(Tooltip.create(Component.literal("Atual: " + (atual.isEmpty() ? "nenhum" : atual) + "\nClique para passar para o próximo título liberado.")));
        addRenderableWidget(b);
    }

    private void initTalentos() {
        JsonObject t = obj(d(), "talentos");
        JsonArray ramos = arr(t, "ramos");
        int col = ramos.size() == 0 ? 1 : ramos.size();
        int cw = (larg - 16) / col;
        for (int r = 0; r < ramos.size(); r++) {
            JsonArray ts = arr(ramos.get(r).getAsJsonObject(), "talentos");
            for (int i = 0; i < ts.size(); i++) {
                JsonObject tal = ts.get(i).getAsJsonObject();
                int x = x0 + 8 + r * cw + cw - 22, y = y0 + 46 + i * 34;
                boolean pode = b(tal, "pode");
                String id = s(tal, "id");
                Button bt = Button.builder(Component.literal("+"), x2 -> ClienteStatus.comando("talentos aprender " + id)).bounds(x, y, 16, 16).build();
                bt.active = pode;
                StringBuilder dica = new StringBuilder(s(tal, "nome")).append("\n").append(s(tal, "porPonto"));
                if (!s(tal, "efeito").isEmpty()) dica.append("\nAtual: ").append(s(tal, "efeito"));
                if (!s(tal, "pede").isEmpty()) dica.append("\n").append(s(tal, "pede"));
                bt.setTooltip(Tooltip.create(Component.literal(dica.toString())));
                addRenderableWidget(bt);
            }
        }
        if (ramos.size() > 0) {
            boolean confirmando = System.currentTimeMillis() - confirmarReset < 5000;
            Button reset = Button.builder(Component.literal(confirmando ? "Confirmar?" : "Redistribuir"), bt -> {
                if (System.currentTimeMillis() - confirmarReset < 5000) { confirmarReset = 0; ClienteStatus.comando("talentos resetar"); }
                else { confirmarReset = System.currentTimeMillis(); rebuildWidgets(); }
            }).bounds(x0 + larg - 96, y0 + 8, 88, 14).build();
            reset.setTooltip(Tooltip.create(Component.literal("Devolve todos os pontos por " + s(t, "precoReset") + ". Clique duas vezes.")));
            addRenderableWidget(reset);
        }
    }

    private void initGrupo() {
        JsonObject g = obj(d(), "grupo");
        int y = y0 + alt - 26;
        convite = new EditBox(font, x0 + 10, y, 120, 16, Component.literal("Nick"));
        convite.setHint(Component.literal("nick do jogador"));
        convite.setMaxLength(16);
        addRenderableWidget(convite);
        addRenderableWidget(Button.builder(Component.literal("Convidar"), bt -> convidar()).bounds(x0 + 134, y, 62, 16).build());
        if (b(g, "convite")) {
            addRenderableWidget(Button.builder(Component.literal("Aceitar convite"), bt -> ClienteStatus.comando("grupo aceitar")).bounds(x0 + 204, y, 90, 16).build());
            addRenderableWidget(Button.builder(Component.literal("Recusar"), bt -> ClienteStatus.comando("grupo recusar")).bounds(x0 + 298, y, 56, 16).build());
        }
        if (b(g, "tem"))
            addRenderableWidget(Button.builder(Component.literal("Sair do grupo"), bt -> ClienteStatus.comando("grupo sair")).bounds(x0 + larg - 96, y0 + 8, 88, 14).build());
    }

    private void convidar() {
        if (convite == null) return;
        String nick = convite.getValue().trim();
        if (nick.isEmpty()) return;
        ClienteStatus.comando("grupo convidar " + nick);
        convite.setValue("");
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // mesmo painel da tela de Habilidades
        g.fill(x0 - 2, y0 - 2, x0 + larg + 2, y0 + alt + 2, MOLDURA);
        g.fill(x0, y0, x0 + larg, y0 + alt, FUNDO);

        if (!ClienteStatus.recebido) {
            g.drawCenteredString(font, "Carregando…", x0 + larg / 2, y0 + alt / 2, CINZA);
        } else {
            switch (aba) {
                case "STATUS" -> renderStatus(g);
                case "TALENTOS" -> renderTalentos(g);
                case "REINOS" -> renderReinos(g);
                case "GRUPO" -> renderGrupo(g);
                default -> {}
            }
        }
        super.render(g, mx, my, pt);
    }

    private void cabecalho(GuiGraphics g, String texto) {
        g.drawString(font, texto, x0 + 10, y0 + 10, TITULO, false);
        g.fill(x0 + 8, y0 + 22, x0 + larg - 8, y0 + 23, BORDA2);
    }

    private void barra(GuiGraphics g, int x, int y, int w, int h, double frac, int cor, String texto) {
        frac = Math.max(0, Math.min(1, frac));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDA2);
        g.fill(x, y, x + w, y + h, BARRA_FUNDO);
        g.fill(x, y, x + (int) (w * frac), y + h, cor);
        if (texto != null && !texto.isEmpty())
            g.drawCenteredString(font, texto, x + w / 2, y + (h - 8) / 2 + 1, TEXTO);
    }

    private void linha(GuiGraphics g, String rotulo, String valor, int x, int y, int w, int cor) {
        g.drawString(font, rotulo, x, y, CINZA, false);
        g.drawString(font, valor, x + w - font.width(valor), y, cor, false);
    }

    private void renderStatus(GuiGraphics g) {
        JsonObject o = d();
        String titulo = s(o, "titulo");
        g.drawString(font, s(o, "nome"), x0 + 10, y0 + 8, TEXTO, false);
        g.drawString(font, titulo.isEmpty() ? "sem título" : "«" + titulo + "»", x0 + 14 + font.width(s(o, "nome")), y0 + 8, titulo.isEmpty() ? CINZA : ROXO, false);
        g.fill(x0 + 8, y0 + 22, x0 + larg - 8, y0 + 23, BORDA2);

        int col = (larg - 30) / 2, xa = x0 + 10, xb = x0 + 20 + col, y = y0 + 30;
        // coluna A: nível, xp, classe, vida
        g.drawString(font, "NÍVEL " + (int) n(o, "nivel"), xa, y, OURO, false);
        String rank = "Rank " + s(o, "rank");
        g.drawString(font, rank, xa + col - font.width(rank), y, OURO, false);
        y += 12;
        double xp = n(o, "xp"), prox = Math.max(1, n(o, "xpProximo"));
        barra(g, xa, y, col, 9, n(o, "nivel") >= n(o, "nivelMax") ? 1 : xp / prox, 0xFF7CB342, "XP " + (long) xp + " / " + (long) prox);
        y += 16;
        JsonObject at = obj(o, "atributos");
        barra(g, xa, y, col, 9, n(at, "vida") / Math.max(1, n(at, "vidaMax")), 0xFFC93C3C, "Vida " + (int) n(at, "vida") + " / " + (int) n(at, "vidaMax"));
        y += 14;
        String classe = s(o, "classe");
        if (!classe.isEmpty()) {
            barra(g, xa, y, col, 9, n(o, "recursoAtual") / Math.max(1, n(o, "recursoMax")),
                    switch (s(o, "recurso")) { case "Fúria" -> 0xFFC0392B; case "Energia" -> 0xFFE5B91F; default -> 0xFF3B82F6; },
                    s(o, "recurso") + " " + (int) n(o, "recursoAtual") + " / " + (int) n(o, "recursoMax"));
            y += 16;
        }
        linha(g, "Classe", classe.isEmpty() ? "nenhuma (aba Habilidades)" : classe, xa, y, col, classe.isEmpty() ? VERMELHO : TEXTO); y += 11;
        {
            String rp = br.com.frxcode.frxworld.racas.DefRacas.nome(ClienteRacas.principal), rs = br.com.frxcode.frxworld.racas.DefRacas.nome(ClienteRacas.secundaria);
            String rt = !ClienteRacas.escolheu ? "nenhuma (botão ✦ Raça)" : rp + (rs.isEmpty() ? "" : ClienteRacas.secundaria.equals(ClienteRacas.principal) ? " puro" : "/" + rs);
            linha(g, "Raça", rt, xa, y, col, ClienteRacas.escolheu ? TEXTO : VERMELHO); y += 11;
        }
        linha(g, "Zona atual", "Nv " + (int) n(o, "zona"), xa, y, col, n(o, "zona") > n(o, "nivel") ? VERMELHO : VERDE); y += 11;
        linha(g, "Bolsa", s(o, "moedas"), xa, y, col, OURO); y += 11;
        linha(g, "Banco", s(o, "banco"), xa, y, col, OURO); y += 15;

        g.drawString(font, "ESTATÍSTICAS", xa, y, TITULO, false); y += 11;
        JsonObject es = obj(o, "estatisticas");
        linha(g, "Monstros abatidos", fmt(n(es, "matar_total")), xa, y, col, TEXTO); y += 10;
        linha(g, "Chefes derrotados", fmt(n(es, "chefes")), xa, y, col, TEXTO); y += 10;
        linha(g, "Invasões vencidas", fmt(n(es, "invasoes")), xa, y, col, TEXTO); y += 10;
        linha(g, "Campanhas concluídas", fmt(n(es, "campanhas")), xa, y, col, TEXTO); y += 10;
        linha(g, "Conquistas", fmt(n(es, "conquistas")), xa, y, col, TEXTO); y += 10;
        linha(g, "Mortes", fmt(n(es, "mortes")), xa, y, col, TEXTO);

        // coluna B: atributos
        y = y0 + 30;
        g.drawString(font, "ATRIBUTOS", xb, y, TITULO, false); y += 13;
        linha(g, "Ataque", fmt(n(at, "ataque")), xb, y, col, TEXTO); y += 11;
        linha(g, "Velocidade de ataque", fmt(n(at, "velAtaque")), xb, y, col, TEXTO); y += 11;
        linha(g, "Armadura", fmt(n(at, "armadura")), xb, y, col, TEXTO); y += 11;
        linha(g, "Resistência", fmt(n(at, "resistencia")), xb, y, col, TEXTO); y += 11;
        linha(g, "Velocidade", fmt(n(at, "velocidade")) + "%", xb, y, col, TEXTO); y += 11;
        linha(g, "Crítico", fmt(n(at, "critico")) + "%", xb, y, col, TEXTO); y += 11;
        linha(g, "Esquiva", fmt(n(at, "esquiva")) + "%", xb, y, col, TEXTO); y += 11;
        linha(g, "Roubo de vida", fmt(n(at, "roubo")) + "%", xb, y, col, TEXTO); y += 11;
        linha(g, "Sorte", fmt(n(at, "sorte")), xb, y, col, TEXTO); y += 15;
        JsonObject tal = obj(o, "talentos");
        if (n(tal, "livres") > 0) g.drawString(font, "★ " + (int) n(tal, "livres") + " ponto(s) de talento livre(s)!", xb, y, VERDE, false);
    }

    private void renderTalentos(GuiGraphics g) {
        JsonObject t = obj(d(), "talentos");
        cabecalho(g, "TALENTOS  ·  pontos livres: " + (int) n(t, "livres") + " de " + (int) n(t, "total"));
        JsonArray ramos = arr(t, "ramos");
        if (ramos.size() == 0) {
            g.drawCenteredString(font, "Escolha uma classe na aba Habilidades para liberar os talentos.", x0 + larg / 2, y0 + alt / 2, CINZA);
            return;
        }
        int cw = (larg - 16) / ramos.size();
        for (int r = 0; r < ramos.size(); r++) {
            JsonObject ramo = ramos.get(r).getAsJsonObject();
            int x = x0 + 8 + r * cw;
            g.drawString(font, s(ramo, "nome"), x + 2, y0 + 30, OURO, false);
            String pts = (int) n(ramo, "pontos") + " pts";
            g.drawString(font, pts, x + cw - 6 - font.width(pts), y0 + 30, CINZA, false);
            JsonArray ts = arr(ramo, "talentos");
            for (int i = 0; i < ts.size(); i++) {
                JsonObject tal = ts.get(i).getAsJsonObject();
                int y = y0 + 44 + i * 34;
                boolean lib = b(tal, "liberado");
                int atual = (int) n(tal, "atual"), max = (int) n(tal, "max");
                g.fill(x + 1, y, x + cw - 4, y + 30, atual >= max ? 0x5040A060 : lib ? 0x402A2218 : 0x60000000);
                g.drawString(font, s(tal, "nome"), x + 4, y + 3, lib ? TEXTO : CINZA, false);
                // pontinhos
                for (int k = 0; k < max; k++)
                    g.fill(x + 4 + k * 7, y + 14, x + 9 + k * 7, y + 19, k < atual ? OURO : 0xFF3A2E22);
                String ef = atual > 0 ? s(tal, "efeito") : s(tal, "porPonto");
                g.drawString(font, font.plainSubstrByWidth(ef, cw - 34), x + 4, y + 21, atual > 0 ? VERDE : CINZA, false);
            }
        }
    }

    private void renderReinos(GuiGraphics g) {
        cabecalho(g, "REINOS  ·  reputação: ajudar um reino desagrada o rival");
        JsonArray rs = arr(d(), "reinos");
        int y = y0 + 30 - rolagem, h = 26;
        maxRolagem = Math.max(0, rs.size() * h - (alt - 40));
        g.enableScissor(x0 + 2, y0 + 26, x0 + larg - 2, y0 + alt - 4);
        for (JsonElement e : rs) {
            JsonObject r = e.getAsJsonObject();
            int pts = (int) n(r, "pontos"), min = (int) n(r, "minimo"), prox = (int) n(r, "proximo"), idx = (int) n(r, "indice");
            int hostil = (int) n(r, "hostil");
            int cor = hostil > 0 ? new int[]{CINZA, 0xFFE0B040, 0xFFE0523F, 0xFFA01818}[Math.min(3, hostil)]
                    : new int[]{CINZA, VERDE, 0xFF5FC8E0, 0xFF6C8CFF, ROXO, OURO}[Math.max(0, Math.min(5, idx))];
            g.drawString(font, s(r, "nome"), x0 + 10, y, TEXTO, false);
            if (!s(r, "rival").isEmpty()) g.drawString(font, "⚔ rival de " + s(r, "rival"), x0 + 16 + font.width(s(r, "nome")), y, CINZA, false);
            String nv = s(r, "nivel") + ((int) n(r, "desconto") > 0 ? "  (−" + (int) n(r, "desconto") + "% nas lojas)"
                    : (int) n(r, "acrescimo") > 0 ? "  (+" + (int) n(r, "acrescimo") + "% nas lojas)" : "");
            g.drawString(font, nv, x0 + larg - 10 - font.width(nv), y, cor, false);
            boolean topo = b(r, "topo");
            double frac = topo ? 1 : (pts - min) / (double) Math.max(1, prox - min);
            barra(g, x0 + 10, y + 11, larg - 20, 7, frac, cor, null);
            String txt = topo ? pts + " pts (máximo)" : pts + " / " + prox + " pts";
            g.drawString(font, txt, x0 + larg - 12 - font.width(txt), y + 11, TEXTO, false);
            y += h;
        }
        g.disableScissor();
    }

    private void renderGrupo(GuiGraphics g) {
        JsonObject gr = obj(d(), "grupo");
        cabecalho(g, "GRUPO" + (b(gr, "tem") ? "  ·  até 6 jogadores, XP dividida com bônus" : ""));
        if (!b(gr, "tem")) {
            g.drawString(font, "Você não está em grupo. Convide alguém pelo nick abaixo.", x0 + 10, y0 + 32, CINZA, false);
            if (b(gr, "convite")) g.drawString(font, "Você tem um convite pendente!", x0 + 10, y0 + 46, OURO, false);
            return;
        }
        int y = y0 + 30;
        for (JsonElement e : arr(gr, "membros")) {
            JsonObject m = e.getAsJsonObject();
            boolean on = b(m, "online");
            String nome = (b(m, "lider") ? "♛ " : "") + s(m, "nome") + (on ? "  Nv " + (int) n(m, "nivel") : "");
            g.drawString(font, nome, x0 + 10, y, on ? TEXTO : CINZA, false);
            if (on) {
                double frac = n(m, "vida") / Math.max(1, n(m, "vidaMax"));
                barra(g, x0 + 150, y, 140, 8, frac, frac < .3 ? VERMELHO : 0xFF3CC95A, (int) n(m, "vida") + " / " + (int) n(m, "vidaMax"));
                int dist = (int) n(m, "dist");
                String ds = dist < 0 ? "outra dimensão" : dist == 0 ? "você" : dist + " blocos";
                g.drawString(font, ds, x0 + larg - 10 - font.width(ds), y, CINZA, false);
            }
            y += 16;
        }
    }
}
