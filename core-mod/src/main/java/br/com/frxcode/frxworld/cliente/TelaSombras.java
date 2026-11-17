package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoSombra;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.SyncSombras;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Habilidades → Exército: as sombras do Invocador, com invocar/recolher uma ou todas e as teclas rápidas. */
public class TelaSombras extends Screen {
    private static final int LARG = 380, ALT = 230, LINHA = 22, POR_PAGINA = 6, TOPO_LISTA = 34;
    private static final int FUNDO = 0xF017140F, MOLDURA = 0xFF5A3A1E, OURO = 0xFFD4A93A, TEXTO = 0xFFE9DFC8,
            CINZA = 0xFF8A7D6A, ROXO = 0xFFC58CF0, ROXO_ESCURO = 0xFF2A1838, VERDE = 0xFF7CD67C, VERMELHO = 0xFFE05A4A;
    private static final String[] NOME_TECLA = {"1", "2", "3", "Todas"};

    private int x0, y0, pagina = 0, contador = 0;
    private String selecionada = "";
    private String confirmarLibertar = "";
    /** Tecla esperando a nova (0..2 = atalhos, 3 = todas; -1 = nenhuma). */
    private int esperandoTecla = -1;
    private final Map<String, LivingEntity> modelos = new HashMap<>();

    public TelaSombras() {
        super(Component.literal("Exército de Sombras"));
    }

    private static void enviar(int acao, int numero, String id) {
        Rede.CANAL.sendToServer(new AcaoSombra(acao, numero, id));
    }

    public void atualizar() {
        rebuildWidgets();
    }

    private List<SyncSombras.Info> lista() { return ClienteSombras.lista; }

    private SyncSombras.Info sel() {
        SyncSombras.Info s = ClienteSombras.porId(selecionada);
        if (s == null && !lista().isEmpty()) { s = lista().get(0); selecionada = s.id(); }
        return s;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = Math.max(28, (height - ALT) / 2 + 10);
        if (contador == 0) enviar(AcaoSombra.PEDIR, 0, "");
        TelaStatus.criarAbas(this::addRenderableWidget, () -> x0 + 4, () -> y0 - 2, "HABILIDADES", true);
        addRenderableWidget(Button.builder(Component.literal("← Habilidades"), b -> Minecraft.getInstance().setScreen(new TelaHabilidades()))
                .bounds(x0 + LARG - 86, y0 + 5, 80, 14).build());

        List<SyncSombras.Info> l = lista();
        int paginas = Math.max(1, (l.size() + POR_PAGINA - 1) / POR_PAGINA);
        pagina = Math.min(pagina, paginas - 1);
        if (l.size() > POR_PAGINA) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { pagina--; rebuildWidgets(); })
                    .bounds(x0 + 8, y0 + TOPO_LISTA + POR_PAGINA * LINHA + 2, 20, 14).build()).active = pagina > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { pagina++; rebuildWidgets(); })
                    .bounds(x0 + 158, y0 + TOPO_LISTA + POR_PAGINA * LINHA + 2, 20, 14).build()).active = pagina < paginas - 1;
        }

        // detalhe da sombra escolhida
        SyncSombras.Info s = sel();
        int dx = x0 + 188, dy = y0 + TOPO_LISTA;
        if (s != null) {
            Button acao;
            if (s.estado() == 1) acao = Button.builder(Component.literal("⚫ Recolher"), b -> enviar(AcaoSombra.GUARDAR, 0, s.id())).bounds(dx, dy + 80, 90, 18).build();
            else {
                acao = Button.builder(Component.literal("⚫ Invocar · " + s.custo()), b -> enviar(AcaoSombra.INVOCAR, 0, s.id())).bounds(dx, dy + 80, 90, 18).build();
                acao.active = s.estado() == 0 && ClienteSombras.ativas < ClienteSombras.ativasMax;
                if (s.estado() == 2) acao.setTooltip(Tooltip.create(Component.literal("Se recompondo, volta em " + s.descansa() + " s.")));
                else if (!acao.active) acao.setTooltip(Tooltip.create(Component.literal("Você já mantém o máximo de sombras ao seu lado (" + ClienteSombras.ativasMax + ").")));
                else acao.setTooltip(Tooltip.create(Component.literal("Custa " + s.custo() + " de mana.")));
            }
            addRenderableWidget(acao);
            boolean conf = confirmarLibertar.equals(s.id());
            Button lib = Button.builder(conf ? Component.literal("Confirmar?").withStyle(ChatFormatting.RED) : Component.literal("Libertar"), b -> {
                if (confirmarLibertar.equals(s.id())) { enviar(AcaoSombra.LIBERTAR, 0, s.id()); confirmarLibertar = ""; }
                else { confirmarLibertar = s.id(); rebuildWidgets(); }
            }).bounds(dx + 94, dy + 80, 86, 18).build();
            lib.setTooltip(Tooltip.create(Component.literal("Remove esta sombra do exército para sempre (abre uma vaga).")));
            addRenderableWidget(lib);

            int atual = ClienteSombras.atalhoDe(s.id());
            for (int i = 0; i < 3; i++) {
                final int k = i;
                String tecla = rotulo(Teclas.SOMBRAS[i]);
                Component rot = Component.literal((atual == i ? "✔ " : "") + (i + 1)).withStyle(atual == i ? ChatFormatting.GREEN : ChatFormatting.WHITE);
                Button b = Button.builder(rot, bt -> enviar(AcaoSombra.DEFINIR_ATALHO, k, atual == k ? "" : s.id())).bounds(dx + 52 + i * 44, dy + 104, 40, 16).build();
                SyncSombras.Info ocupante = ClienteSombras.porId(ClienteSombras.atalhos[i]);
                b.setTooltip(Tooltip.create(Component.literal(atual == i ? "Clique para tirar do atalho " + (i + 1) + "."
                        : "Pôr no atalho " + (i + 1) + " (tecla " + tecla + ")." + (ocupante != null ? "\nHoje está: " + ocupante.nome() : ""))));
                addRenderableWidget(b);
            }
        }

        // todas
        int by = y0 + ALT - 46;
        Button todas = Button.builder(Component.literal("⚫ Invocar todas"), b -> enviar(AcaoSombra.INVOCAR_TODAS, 0, "")).bounds(x0 + 8, by, 110, 18).build();
        todas.active = !l.isEmpty() && ClienteSombras.ativas < ClienteSombras.ativasMax;
        todas.setTooltip(Tooltip.create(Component.literal("Chama as sombras guardadas até o limite que você consegue manter.")));
        addRenderableWidget(todas);
        Button recolher = Button.builder(Component.literal("Recolher todas"), b -> enviar(AcaoSombra.GUARDAR_TODAS, 0, "")).bounds(x0 + 122, by, 110, 18).build();
        recolher.active = ClienteSombras.ativas > 0;
        addRenderableWidget(recolher);

        // teclas rápidas: clique e aperte a tecla nova
        int tx = x0 + 52, ty = y0 + ALT - 20;
        int[] ordem = {3, 0, 1, 2};
        for (int k : ordem) {
            int lw = font.width(NOME_TECLA[k] + ":") + 3;
            addRenderableWidget(botaoTecla(k, tx + lw, ty, 36));
            tx += lw + 36 + 10;
        }
    }

    private static String rotulo(KeyMapping k) {
        if (k.isUnbound()) return "—";
        String s = k.getTranslatedKeyMessage().getString();
        return s.length() > 5 ? s.substring(0, 5) : s;
    }

    private Button botaoTecla(int i, int x, int y, int w) {
        KeyMapping k = Teclas.SOMBRAS[i];
        String outra = null;
        if (!k.isUnbound())
            for (KeyMapping m : Minecraft.getInstance().options.keyMappings)
                if (m != k && m.same(k)) { outra = Component.translatable(m.getName()).getString(); break; }
        Component rot = esperandoTecla == i ? Component.literal("> ? <").withStyle(ChatFormatting.YELLOW)
                : outra != null ? Component.literal(rotulo(k)).withStyle(ChatFormatting.RED) : Component.literal(rotulo(k));
        Button b = Button.builder(rot, bt -> { esperandoTecla = esperandoTecla == i ? -1 : i; rebuildWidgets(); }).bounds(x, y, w, 14).build();
        String alvo;
        if (i == 3) alvo = "Chama todas as sombras; se alguma já estiver fora, recolhe todas.";
        else {
            SyncSombras.Info s = ClienteSombras.porId(ClienteSombras.atalhos[i]);
            alvo = s == null ? "Atalho " + (i + 1) + " vazio: escolha a sombra e clique em " + (i + 1) + "."
                    : "Atalho " + (i + 1) + ": " + s.nome() + ". Aperte para invocar; de novo para recolher.";
        }
        b.setTooltip(Tooltip.create(Component.literal(alvo + "\nClique e aperte a tecla nova (Esc cancela, Delete limpa)."
                + (outra != null ? "\nAtenção: já é usada por \"" + outra + "\"." : ""))));
        return b;
    }

    private void gravarTecla(InputConstants.Key tecla) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setKey(Teclas.SOMBRAS[esperandoTecla], tecla);
        KeyMapping.resetMapping();
        mc.options.save();
        esperandoTecla = -1;
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mod) {
        if (esperandoTecla >= 0) {
            if (tecla == 256) { esperandoTecla = -1; rebuildWidgets(); }        // Esc cancela
            else if (tecla == 261) gravarTecla(InputConstants.UNKNOWN);          // Delete limpa
            else gravarTecla(InputConstants.getKey(tecla, scan));
            return true;
        }
        return super.keyPressed(tecla, scan, mod);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (esperandoTecla >= 0 && botao >= 2) {
            gravarTecla(InputConstants.Type.MOUSE.getOrCreate(botao));
            return true;
        }
        if (super.mouseClicked(mx, my, botao)) return true;
        // clique numa linha da lista
        if (botao == 0 && mx >= x0 + 8 && mx < x0 + 178 && my >= y0 + TOPO_LISTA && my < y0 + TOPO_LISTA + POR_PAGINA * LINHA) {
            int idx = pagina * POR_PAGINA + (int) ((my - y0 - TOPO_LISTA) / LINHA);
            if (idx < lista().size()) {
                selecionada = lista().get(idx).id();
                confirmarLibertar = "";
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (++contador % 20 == 0) enviar(AcaoSombra.PEDIR, 0, "");   // vida e tempo de descanso ao vivo
    }

    // ------------------------------------------------------------------ desenho

    private static ItemStack ovo(String tipo) {
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(tipo));
        SpawnEggItem ovo = t == null ? null : ForgeSpawnEggItem.fromEntityType(t);
        return new ItemStack(ovo != null ? ovo : Items.WITHER_SKELETON_SKULL);
    }

    private LivingEntity modelo(String tipo) {
        if (modelos.containsKey(tipo)) return modelos.get(tipo);
        LivingEntity le = null;
        try {
            EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(tipo));
            Entity e = t == null || Minecraft.getInstance().level == null ? null : t.create(Minecraft.getInstance().level);
            if (e instanceof LivingEntity l) le = l;
        } catch (Throwable ignorar) { le = null; }
        modelos.put(tipo, le);
        return le;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0 - 2, y0 - 2, x0 + LARG + 2, y0 + ALT + 2, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, FUNDO);

        g.drawString(font, "⚫ Exército de Sombras", x0 + 8, y0 + 8, ROXO, false);
        g.drawString(font, "Exército " + lista().size() + "/" + ClienteSombras.vagas + "  ·  Ao seu lado " + ClienteSombras.ativas + "/" + ClienteSombras.ativasMax
                + "  ·  " + ClienteHabilidades.nomeRecurso() + " " + ClienteHabilidades.recursoAtual + "/" + ClienteHabilidades.recursoMax, x0 + 8, y0 + 20, CINZA, false);

        List<SyncSombras.Info> l = lista();
        if (l.isEmpty()) {
            String msg = ClienteSombras.recebido ? "Seu exército está vazio. Mate uma criatura e, em até 20 s, use /levantese (ou clique em [Levante-se!] no chat) para erguer a sombra dela."
                    : "Carregando...";
            int y = y0 + TOPO_LISTA + 4;
            for (var linha : font.split(Component.literal(msg), LARG - 20)) { g.drawString(font, linha, x0 + 10, y, TEXTO, false); y += 10; }
        }

        // lista
        int inicio = pagina * POR_PAGINA;
        for (int i = inicio; i < Math.min(l.size(), inicio + POR_PAGINA); i++) {
            SyncSombras.Info s = l.get(i);
            int y = y0 + TOPO_LISTA + (i - inicio) * LINHA, x = x0 + 8;
            boolean marcada = s.id().equals(selecionada), sobre = mx >= x && mx < x + 170 && my >= y && my < y + LINHA - 2;
            g.fill(x, y, x + 170, y + LINHA - 2, marcada ? 0xFF3A2650 : sobre ? 0xFF2A2018 : 0xFF1E1914);
            if (marcada) g.fill(x - 2, y, x - 1, y + LINHA - 2, ROXO);
            g.renderItem(ovo(s.tipo()), x + 2, y + 2);
            String nome = (s.chefe() ? "☠ " : "") + s.nome();
            if (font.width(nome) > 98) nome = font.plainSubstrByWidth(nome, 94) + "…";
            g.drawString(font, nome, x + 22, y + 2, s.estado() == 1 ? ROXO : s.estado() == 2 ? CINZA : TEXTO, false);
            g.drawString(font, "Nv " + s.nivel(), x + 22, y + 11, OURO, false);
            int nvL = s.nivel(), baseL = 3 * nvL * nvL, proxL = 3 * (nvL + 1) * (nvL + 1);
            float fracL = nvL >= 10 ? 1f : Math.max(0f, Math.min(1f, (s.abates() - baseL) / (float) Math.max(1, proxL - baseL)));
            g.fill(x + 50, y + 14, x + 100, y + 16, 0xFF0F0D0B);
            g.fill(x + 50, y + 14, x + 50 + (int) (50 * fracL), y + 16, ROXO);
            String estado = s.estado() == 1 ? "● fora" : s.estado() == 2 ? s.descansa() + "s" : "guardada";
            g.drawString(font, estado, x + 166 - font.width(estado), y + 11, s.estado() == 1 ? VERDE : CINZA, false);
            int at = ClienteSombras.atalhoDe(s.id());
            if (at >= 0) {
                String t = "[" + (at + 1) + "]";
                g.drawString(font, t, x + 166 - font.width(t), y + 2, OURO, false);
            }
        }
        if (l.size() > POR_PAGINA) {
            String pg = (pagina + 1) + "/" + ((l.size() + POR_PAGINA - 1) / POR_PAGINA);
            g.drawCenteredString(font, pg, x0 + 93, y0 + TOPO_LISTA + POR_PAGINA * LINHA + 5, CINZA);
        }

        // detalhe
        SyncSombras.Info s = sel();
        int dx = x0 + 188, dy = y0 + TOPO_LISTA;
        if (s != null) {
            g.fill(dx, dy, dx + 56, dy + 74, ROXO_ESCURO);
            g.fill(dx, dy + 73, dx + 56, dy + 74, ROXO);
            LivingEntity m = modelo(s.tipo());
            if (m != null) {
                float h = Math.max(m.getBbHeight(), m.getBbWidth());
                int escala = (int) Math.max(6, Math.min(30, 56 / Math.max(0.6f, h)));
                try {
                    RenderSystem.setShaderColor(0.45f, 0.28f, 0.7f, 1f);   // tom sombrio
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, dx + 28, dy + 68, escala, dx + 28 - mx, dy + 30 - my, m);
                } catch (Throwable ignorar) {
                    modelos.put(s.tipo(), null);
                } finally {
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }
            } else {
                g.pose().pushPose();
                g.pose().translate(dx + 12, dy + 21, 0);
                g.pose().scale(2f, 2f, 1f);
                g.renderItem(ovo(s.tipo()), 0, 0);
                g.pose().popPose();
            }

            int tx = dx + 62, ty = dy + 2;
            String nome = s.nome();
            if (font.width(nome) > 120) nome = font.plainSubstrByWidth(nome, 116) + "…";
            g.drawString(font, nome, tx, ty, ROXO, false);
            ty += 11;
            if (s.chefe()) { g.drawString(font, "☠ Chefe", tx, ty, VERMELHO, false); ty += 10; }
            // experiência da sombra: o nível n pede 3 × n² abates (os seus abates com ela em campo também contam)
            int nvS = s.nivel(), baseXp = 3 * nvS * nvS, proxXp = 3 * (nvS + 1) * (nvS + 1);
            int feitoXp = Math.max(0, s.abates() - baseXp), faltaXp = Math.max(1, proxXp - baseXp);
            float fracXp = nvS >= 10 ? 1f : Math.min(1f, feitoXp / (float) faltaXp);
            g.drawString(font, nvS >= 10 ? "Nível 10/10 · máximo" : "Nível " + nvS + "/10 · " + feitoXp + "/" + faltaXp + " abates", tx, ty, OURO, false);
            g.fill(tx, ty + 9, tx + 116, ty + 12, 0xFF0F0D0B);
            g.fill(tx, ty + 9, tx + (int) (116 * fracXp), ty + 12, ROXO);
            ty += 14;
            g.drawString(font, "Força " + s.forca() + "% do original", tx, ty, TEXTO, false);
            ty += 10;
            g.drawString(font, "Invocar: " + s.custo() + " " + ClienteHabilidades.nomeRecurso().toLowerCase(), tx, ty, CINZA, false);
            ty += 12;
            switch (s.estado()) {
                case 1 -> {
                    g.drawString(font, "Ao seu lado", tx, ty, VERDE, false);
                    int bw = 116, bx = tx, bby = ty + 10;
                    float f = s.vidaMax() > 0 ? Math.max(0, Math.min(1, s.vida() / s.vidaMax())) : 0;
                    g.fill(bx, bby, bx + bw, bby + 5, 0xFF0F0D0B);
                    g.fill(bx, bby, bx + (int) (bw * f), bby + 5, f > 0.5f ? 0xFF5BC25B : f > 0.25f ? 0xFFE0B040 : VERMELHO);
                    g.drawString(font, Math.round(s.vida()) + "/" + Math.round(s.vidaMax()) + " ❤", bx + bw - 40, bby + 7, CINZA, false);
                }
                case 2 -> g.drawString(font, "Se recompondo: " + s.descansa() + " s", tx, ty, CINZA, false);
                default -> g.drawString(font, "Guardada nas sombras", tx, ty, CINZA, false);
            }
            g.drawString(font, "Atalho:", dx, dy + 108, CINZA, false);
        }

        // rodapé
        g.fill(x0 + 6, y0 + ALT - 52, x0 + LARG - 6, y0 + ALT - 51, MOLDURA);
        g.drawString(font, "Teclas:", x0 + 8, y0 + ALT - 17, OURO, false);
        int tx = x0 + 52;
        int[] ordem = {3, 0, 1, 2};
        for (int k : ordem) {
            g.drawString(font, NOME_TECLA[k] + ":", tx, y0 + ALT - 17, CINZA, false);
            tx += font.width(NOME_TECLA[k] + ":") + 3 + 36 + 10;
        }
        g.drawString(font, "1ª vez: invoca · 2ª: recolhe", x0 + 240, y0 + ALT - 41, CINZA, false);

        super.render(g, mx, my, pt);
    }
}
