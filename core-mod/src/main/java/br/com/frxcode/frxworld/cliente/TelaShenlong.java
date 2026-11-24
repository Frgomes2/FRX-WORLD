package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoHabilidade;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Os desejos de Shenlong. Página 0: os 8 desejos; 4: conjuntos de armadura; 5: raças; 3: busca de item (feita aqui, com os nomes
 * no idioma do jogador). Tudo pede confirmação antes de enviar: o desejo não tem volta.
 */
public class TelaShenlong extends Screen {
    private static final int LARG = 360, ALT = 226;
    private static final int FUNDO = 0xF0101812, MOLDURA = 0xFF2FA84F, OURO = 0xFFF2C94C, TEXTO = 0xFFE8F0E8, CINZA = 0xFF9AA89A, VERMELHO = 0xFFE06060;

    private CompoundTag d;
    private int x0, y0;
    private int pendN = -1;
    private String pendTexto = "", pendNome = "", busca = "";
    private final List<ItemStack> achados = new ArrayList<>();
    private EditBox campo;

    public TelaShenlong(CompoundTag d) {
        super(Component.literal("Shenlong"));
        this.d = d;
    }

    public void atualizar(CompoundTag novo) {
        this.d = novo;
        pendN = -1;
        rebuildWidgets();
    }

    private int pagina() { return d.getInt("pagina"); }

    private void enviar(int n, String t) { Rede.CANAL.sendToServer(new AcaoHabilidade(AcaoHabilidade.SHENLONG, n, t)); }

    private void escolher(int n, String texto, String nome) { pendN = n; pendTexto = texto; pendNome = nome; rebuildWidgets(); }

    private void procurar() {
        achados.clear();
        String q = busca.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) return;
        for (Item i : ForgeRegistries.ITEMS) {
            if (i == Items.AIR) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(i);
            if (id == null || id.toString().length() > 60) continue;
            ItemStack s = new ItemStack(i);
            String nome;
            try { nome = s.getHoverName().getString().toLowerCase(Locale.ROOT); } catch (Exception e) { continue; }
            if (nome.contains(q) || id.getPath().contains(q)) achados.add(s);
            if (achados.size() >= 14) break;
        }
    }

    @Override
    protected void init() {
        x0 = (width - LARG) / 2;
        y0 = (height - ALT) / 2;
        if (pendN >= 0) {
            addRenderableWidget(Button.builder(Component.literal("✦ Confirmar o desejo ✦"), b -> { int n = pendN; String t = pendTexto; pendN = -1; enviar(n, t); })
                    .bounds(x0 + LARG / 2 - 150, y0 + ALT - 60, 146, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Voltar"), b -> { pendN = -1; rebuildWidgets(); }).bounds(x0 + LARG / 2 + 4, y0 + ALT - 60, 146, 20).build());
            return;
        }
        int p = pagina();
        if (p == 3) {
            campo = new EditBox(font, x0 + 14, y0 + 44, LARG - 28, 16, Component.literal("Nome do item"));
            campo.setMaxLength(40);
            campo.setHint(Component.literal("Digite o nome do item (ex.: élitro)"));
            campo.setValue(busca);
            campo.setResponder(t -> { if (!t.equals(busca)) { busca = t; procurar(); rebuildWidgets(); } });
            addRenderableWidget(campo);
            setInitialFocus(campo);
            for (int i = 0; i < achados.size(); i++) {
                final ItemStack s = achados.get(i);
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                int col = i % 2, lin = i / 2;
                Button b = Button.builder(s.getHoverName(), bt -> escolher(3, id.toString(), s.getHoverName().getString()))
                        .bounds(x0 + 34 + col * 172, y0 + 66 + lin * 20, 146, 18).build();
                b.setTooltip(Tooltip.create(Component.literal(id.toString())));
                addRenderableWidget(b);
            }
            addRenderableWidget(Button.builder(Component.literal("← Outros desejos"), b -> enviar(0, "")).bounds(x0 + 14, y0 + ALT - 26, 120, 18).build());
            return;
        }
        ListTag o = d.getList("opcoes", Tag.TAG_COMPOUND);
        for (int i = 0; i < o.size(); i++) {
            CompoundTag c = o.getCompound(i);
            final int n = c.getInt("n");
            final String texto = c.getString("texto"), nome = c.getString("nome");
            boolean direto = p != 0 || (n != 3 && n != 4 && n != 5);
            Button b = Button.builder(Component.literal(nome), bt -> { if (direto) escolher(n, texto, nome); else enviar(n, ""); })
                    .bounds(x0 + 14, y0 + 44 + i * 21, 130, 18).build();
            b.active = !c.getBoolean("travado");
            addRenderableWidget(b);
        }
        if (p != 0) addRenderableWidget(Button.builder(Component.literal("← Outros desejos"), b -> enviar(0, "")).bounds(x0 + 14, y0 + ALT - 26, 120, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(x0 - 2, y0 - 2, x0 + LARG + 2, y0 + ALT + 2, MOLDURA);
        g.fill(x0, y0, x0 + LARG, y0 + ALT, FUNDO);
        g.drawCenteredString(font, "✦ SHENLONG ✦", x0 + LARG / 2, y0 + 8, OURO);
        int p = pagina();
        String sub = pendN >= 0 ? "Este desejo não tem volta." : p == 0 ? "Diga o seu desejo. Realizarei apenas um."
                : p == 3 ? "Qual tesouro você deseja?" : p == 4 ? "Qual armadura você deseja?" : "Qual sangue você deseja?";
        g.drawCenteredString(font, sub, x0 + LARG / 2, y0 + 22, CINZA);
        String msg = d.getString("msg");
        if (!msg.isEmpty() && pendN < 0) g.drawCenteredString(font, msg, x0 + LARG / 2, y0 + 33, VERMELHO);
        if (pendN >= 0) {
            g.drawCenteredString(font, "Você deseja:", x0 + LARG / 2, y0 + 70, TEXTO);
            g.drawCenteredString(font, pendNome, x0 + LARG / 2, y0 + 88, OURO);
            g.drawCenteredString(font, "As 7 esferas viram pedra por 7 dias.", x0 + LARG / 2, y0 + 112, CINZA);
        } else if (p == 3) {
            for (int i = 0; i < achados.size(); i++) g.renderItem(achados.get(i), x0 + 15 + (i % 2) * 172, y0 + 67 + (i / 2) * 20);
            if (achados.isEmpty()) g.drawCenteredString(font, busca.trim().length() < 2 ? "Digite pelo menos 2 letras." : "Nada com esse nome.", x0 + LARG / 2, y0 + 90, CINZA);
        } else {
            ListTag o = d.getList("opcoes", Tag.TAG_COMPOUND);
            for (int i = 0; i < o.size(); i++) {
                List<net.minecraft.util.FormattedCharSequence> l = font.split(Component.literal(o.getCompound(i).getString("desc")), LARG - 166);
                int y = y0 + 44 + i * 21 + (l.size() > 1 ? 0 : 5);
                for (int k = 0; k < Math.min(2, l.size()); k++) g.drawString(font, l.get(k), x0 + 152, y + k * 9, TEXTO, false);
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
