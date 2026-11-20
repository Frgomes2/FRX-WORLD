package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.reinos.Costureira;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Bolsos da calça no cliente: a linha "Bolsos: n de 12" na dica da calça e o painel de bolsos colado
 * à esquerda do inventário (2 colunas, um espaço por bolso). O painel só desenha e avisa o clique:
 * quem mexe nos itens é o servidor (Costureira.clique), que devolve o conteúdo novo.
 */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BolsosCliente {
    private static final int COLUNAS = 2, CASA = 18, BORDA = 7, TITULO = 11;
    private static NonNullList<ItemStack> itens = NonNullList.withSize(Costureira.MAXIMO, ItemStack.EMPTY);
    private static int abertos;

    private BolsosCliente() {}

    /** Tecla dos bolsos fora do inventário: abre a janela antiga (continua valendo). */
    public static void abrir() { Rede.CANAL.sendToServer(new AcaoMenu("bolsos", 0, 0, 0)); }

    /** Chegou do servidor o conteúdo dos bolsos. */
    public static void receber(CompoundTag d) {
        NonNullList<ItemStack> novo = NonNullList.withSize(Costureira.MAXIMO, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(d.getCompound("itens"), novo);
        itens = novo;
        abertos = Math.max(0, Math.min(Costureira.MAXIMO, d.getInt("n")));
    }

    @SubscribeEvent
    public static void dica(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        if (!s.hasTag() || !s.getTag().contains(Costureira.TAG)) return;
        int n = Costureira.bolsos(s);
        if (n <= 0 || e.getToolTip().isEmpty()) return;
        e.getToolTip().add(1, Component.literal("Bolsos: " + n + " de " + Costureira.MAXIMO).withStyle(ChatFormatting.GOLD));
    }

    /** Quantos bolsos mostrar agora: os da calça vestida (0 = sem painel). */
    private static int visiveis() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return 0;
        return Math.min(abertos, Costureira.capacidade(mc.player));
    }

    private static int linhas(int n) { return (n + COLUNAS - 1) / COLUNAS; }
    private static int largura() { return COLUNAS * CASA + BORDA * 2; }
    private static int altura(int n) { return linhas(n) * CASA + BORDA * 2 + TITULO; }

    /** Canto do painel: encostado à esquerda da janela, alinhado por baixo; com o livro de receitas aberto, vai para a esquerda dele. */
    private static int x0(InventoryScreen inv) {
        int x = inv.getGuiLeft() - largura() - 2;
        if (inv.getRecipeBookComponent().isVisible()) x -= 150;
        return Math.max(2, x);
    }
    private static int y0(InventoryScreen inv, int n) { return inv.getGuiTop() + inv.getYSize() - altura(n); }

    /** Bolso debaixo do mouse: 0 a n-1; -1 = dentro do painel mas fora de um bolso; -2 = fora do painel. */
    private static int bolsoEm(InventoryScreen inv, double mx, double my) {
        int n = visiveis();
        if (n <= 0) return -2;
        int x = x0(inv), y = y0(inv, n);
        if (mx < x || my < y || mx >= x + largura() || my >= y + altura(n)) return -2;
        int cx = (int) Math.floor((mx - x - BORDA) / CASA), cy = (int) Math.floor((my - y - BORDA - TITULO) / CASA);
        if (mx < x + BORDA || my < y + BORDA + TITULO || cx < 0 || cx >= COLUNAS || cy < 0) return -1;
        int i = cy * COLUNAS + cx;
        return i < n ? i : -1;
    }

    /** Ao abrir o inventário, pede ao servidor o que está nos bolsos. */
    @SubscribeEvent
    public static void aoAbrirInventario(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof InventoryScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Costureira.capacidade(mc.player) <= 0) return;
        Rede.CANAL.sendToServer(new AcaoMenu("bolso", 0, -1, 0));
    }

    @SubscribeEvent
    public static void aoDesenhar(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof InventoryScreen inv)) return;
        int n = visiveis();
        if (n <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        int x = x0(inv), y = y0(inv, n), l = largura(), a = altura(n);
        // moldura no estilo do inventário
        g.fill(x, y, x + l, y + a, 0xFF000000);
        g.fill(x + 1, y + 1, x + l - 1, y + a - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + l - 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + a - 2, 0xFFFFFFFF);
        g.fill(x + 2, y + a - 2, x + l - 1, y + a - 1, 0xFF555555);
        g.fill(x + l - 2, y + 2, x + l - 1, y + a - 1, 0xFF555555);
        g.drawString(mc.font, "Bolsos", x + BORDA, y + 6, 0xFF404040, false);
        int sobre = bolsoEm(inv, e.getMouseX(), e.getMouseY());
        for (int i = 0; i < n; i++) {
            int sx = x + BORDA + (i % COLUNAS) * CASA, sy = y + BORDA + TITULO + (i / COLUNAS) * CASA;
            g.fill(sx, sy, sx + CASA, sy + CASA, 0xFF8B8B8B);
            g.fill(sx, sy, sx + CASA - 1, sy + 1, 0xFF373737);
            g.fill(sx, sy, sx + 1, sy + CASA - 1, 0xFF373737);
            g.fill(sx + 1, sy + CASA - 1, sx + CASA, sy + CASA, 0xFFFFFFFF);
            g.fill(sx + CASA - 1, sy + 1, sx + CASA, sy + CASA, 0xFFFFFFFF);
            ItemStack s = itens.get(i);
            if (!s.isEmpty()) {
                g.renderItem(s, sx + 1, sy + 1);
                g.renderItemDecorations(mc.font, s, sx + 1, sy + 1);
            }
            if (i == sobre) g.fill(sx + 1, sy + 1, sx + CASA - 1, sy + CASA - 1, 0x80FFFFFF);
        }
        ItemStack mao = inv.getMenu().getCarried();
        if (sobre >= -1 && !mao.isEmpty()) {
            // o painel é desenhado depois da janela: redesenha por cima o item que está preso ao mouse
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 300f);
            g.renderItem(mao, e.getMouseX() - 8, e.getMouseY() - 8);
            g.renderItemDecorations(mc.font, mao, e.getMouseX() - 8, e.getMouseY() - 8);
            g.pose().popPose();
        } else if (sobre >= 0 && !itens.get(sobre).isEmpty()) {
            g.renderTooltip(mc.font, itens.get(sobre), e.getMouseX(), e.getMouseY());
        }
    }

    /** Clique no painel: vai para o servidor e não chega à janela (que entenderia como "clicou fora" e largaria o item no chão). */
    @SubscribeEvent
    public static void aoClicar(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen inv)) return;
        int i = bolsoEm(inv, e.getMouseX(), e.getMouseY());
        if (i == -2) return;
        e.setCanceled(true);
        if (i < 0 || e.getButton() > 1) return;
        Rede.CANAL.sendToServer(new AcaoMenu("bolso", 0, Screen.hasShiftDown() ? 2 : e.getButton(), i));
    }

    @SubscribeEvent
    public static void aoSoltar(ScreenEvent.MouseButtonReleased.Pre e) {
        if (e.getScreen() instanceof InventoryScreen inv && bolsoEm(inv, e.getMouseX(), e.getMouseY()) != -2) e.setCanceled(true);
    }
}
