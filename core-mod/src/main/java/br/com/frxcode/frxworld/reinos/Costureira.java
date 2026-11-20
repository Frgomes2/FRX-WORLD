package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Costureira (NPC de papel "costureira"): costura bolsos na calça do jogador, um espaço por vez, até 12 por calça.
 * A calça guarda só QUANTOS bolsos tem (marca frx_bolsos). O que está dentro dos bolsos fica guardado no personagem:
 * vale enquanto ele veste uma calça com bolsos; tirando a calça (ou vestindo uma com menos bolsos), o que não cabe volta para a mochila.
 */
public class Costureira {
    public static final int COSTURAR = 0;
    public static final int MAXIMO = 12;
    /** Marca na calça: quantos bolsos ela tem. */
    public static final String TAG = "frx_bolsos";
    private static final long PRECO = 200;                  // por bolso: 2 de ouro, em cobre
    private static final String GUARDADO = "frx_bolso_itens";
    private static final String FECHO = "frx_fecho";        // marca das placas que tampam os espaços que não são bolso
    private static final int POR_LINHA = 6, LINHAS = 2, CASAS = LINHAS * 9;

    public Costureira() {}

    // ------------------------------------------------------------------ a calça

    public static boolean calca(ItemStack s) { return !s.isEmpty() && LivingEntity.getEquipmentSlotForItem(s) == EquipmentSlot.LEGS; }

    public static int bolsos(ItemStack s) { return calca(s) && s.hasTag() ? Math.max(0, Math.min(MAXIMO, s.getTag().getInt(TAG))) : 0; }

    /** Quantos bolsos o jogador tem agora: os da calça que está vestindo. */
    public static int capacidade(Player p) { return bolsos(p.getItemBySlot(EquipmentSlot.LEGS)); }

    /** A calça que a Costureira vai mexer: a que está na mão; se a mão não tem calça, a que está vestida. */
    private static ItemStack alvo(ServerPlayer p) {
        if (calca(p.getMainHandItem())) return p.getMainHandItem();
        ItemStack v = p.getItemBySlot(EquipmentSlot.LEGS);
        return calca(v) ? v : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ a tela da Costureira

    public static void abrir(ServerPlayer p, Entity npc) { enviar(p, npc.getId(), "", true); }

    private static void enviar(ServerPlayer p, int npcId, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        Entity npc = p.level().getEntity(npcId);
        ItemStack c = alvo(p);
        d.putInt("npc", npcId);
        d.putString("nome", npc != null && npc.getCustomName() != null ? npc.getCustomName().getString() : "Costureira");
        d.putString("msg", msg); d.putBoolean("ok", ok);
        d.putLong("bolsa", Moedas.contar(p));
        d.putLong("preco", PRECO);
        d.putInt("maximo", MAXIMO);
        d.putBoolean("temCalca", !c.isEmpty());
        d.putBoolean("naMao", !c.isEmpty() && c == p.getMainHandItem());
        d.putInt("bolsos", bolsos(c));
        if (!c.isEmpty()) d.put("item", c.save(new CompoundTag()));
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("costureira", d));
    }

    /** A tela manda COSTURAR com valor = quantos bolsos costurar de uma vez. */
    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (npc == null || npc.distanceToSqr(p) > 144 || !"costureira".equals(npc.getPersistentData().getString("frx_papel")) || a.acao() != COSTURAR) return;
        ItemStack c = alvo(p);
        String msg;
        boolean ok = false;
        int tem = bolsos(c);
        long qtd = Math.min(a.valor(), MAXIMO - tem);
        if (c.isEmpty()) msg = "Traga a calça na mão, ou venha vestindo uma.";
        else if (tem >= MAXIMO) msg = "Essa calça já tem os " + MAXIMO + " bolsos. Não cabe mais nenhum.";
        else if (qtd < 1) return;
        else if (!Moedas.cobrar(p, qtd * PRECO)) msg = "Faltam moedas: " + (qtd == 1 ? "o bolso custa " : "os " + qtd + " bolsos custam ") + Moedas.texto(qtd * PRECO) + ".";
        else {
            c.getOrCreateTag().putInt(TAG, tem + (int) qtd);
            msg = (qtd == 1 ? "Bolso costurado." : qtd + " bolsos costurados.") + " A calça agora tem " + (tem + qtd) + " de " + MAXIMO + ".";
            ok = true;
        }
        p.playNotifySound(ok ? SoundEvents.ARMOR_EQUIP_LEATHER : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, ok ? 1.2f : 1f);
        enviar(p, a.npc(), msg, ok);
    }

    // ------------------------------------------------------------------ o que está nos bolsos (guardado no personagem)

    private static CompoundTag pers(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static NonNullList<ItemStack> ler(Player p) {
        NonNullList<ItemStack> l = NonNullList.withSize(MAXIMO, ItemStack.EMPTY);
        CompoundTag pers = pers(p);
        if (pers.contains(GUARDADO)) ContainerHelper.loadAllItems(pers.getCompound(GUARDADO), l);
        return l;
    }

    private static void gravar(Player p, NonNullList<ItemStack> l) {
        boolean vazio = true;
        for (ItemStack s : l) if (!s.isEmpty()) { vazio = false; break; }
        if (vazio) pers(p).remove(GUARDADO);
        else pers(p).put(GUARDADO, ContainerHelper.saveAllItems(new CompoundTag(), l));
    }

    /** Casa da janela (2 linhas de 9) → número do bolso (0 a 11), ou -1 nas 3 colunas da direita, que não são bolso. */
    private static int bolsoDa(int casa) { return casa % 9 < POR_LINHA ? (casa / 9) * POR_LINHA + casa % 9 : -1; }

    private static ItemStack fecho(boolean bolsoQueFalta) {
        ItemStack s = new ItemStack(bolsoQueFalta ? Items.GRAY_STAINED_GLASS_PANE : Items.BLACK_STAINED_GLASS_PANE);
        s.getOrCreateTag().putBoolean(FECHO, true);
        s.setHoverName(Component.literal(bolsoQueFalta ? "Sem bolso aqui: a Costureira costura mais" : " ").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)));
        return s;
    }

    /** Coisas que já guardam coisas (caixas de shulker e parecidos) não entram no bolso. */
    private static boolean cabe(ItemStack s) { return s.getItem().canFitInsideContainerItems() && !(s.hasTag() && s.getTag().getBoolean(FECHO)); }

    /** Janela dos bolsos: um baú de 2 linhas em que só os bolsos costurados aceitam itens. */
    private static final class MenuBolsos extends ChestMenu {
        final SimpleContainer caixa;
        final ServerPlayer dono;
        final int abertos;

        MenuBolsos(int id, Inventory inv, SimpleContainer caixa, ServerPlayer dono, int abertos) {
            super(MenuType.GENERIC_9x2, id, inv, caixa, LINHAS);
            this.caixa = caixa; this.dono = dono; this.abertos = abertos;
            NonNullList<ItemStack> guardado = ler(dono);
            for (int i = 0; i < CASAS; i++) {
                final boolean livre = aberto(i);
                caixa.setItem(i, livre ? guardado.get(bolsoDa(i)) : fecho(bolsoDa(i) >= 0));
                Slot velho = slots.get(i);
                Slot novo = new Slot(caixa, i, velho.x, velho.y) {
                    @Override public boolean mayPlace(ItemStack s) { return livre && cabe(s); }
                    @Override public boolean mayPickup(Player j) { return livre; }
                };
                novo.index = i;
                slots.set(i, novo);
            }
            caixa.addListener(c -> salvar());
        }

        boolean aberto(int casa) { int b = bolsoDa(casa); return b >= 0 && b < abertos; }

        void salvar() {
            NonNullList<ItemStack> l = NonNullList.withSize(MAXIMO, ItemStack.EMPTY);
            for (int i = 0; i < CASAS; i++) if (aberto(i)) l.set(bolsoDa(i), caixa.getItem(i));
            gravar(dono, l);
        }

        @Override
        public void clicked(int casa, int botao, ClickType tipo, Player jogador) {
            if (casa >= 0 && casa < CASAS && !aberto(casa)) { sendAllDataToRemote(); return; }   // placa de fecho: nada acontece
            super.clicked(casa, botao, tipo, jogador);
        }

        @Override public void removed(Player jogador) { super.removed(jogador); salvar(); }
        /** Trocou ou tirou a calça com a janela aberta: fecha. */
        @Override public boolean stillValid(Player jogador) { return jogador.isAlive() && capacidade(jogador) == abertos; }
    }

    // ------------------------------------------------------------------ bolsos dentro do inventário (painel ao lado da mochila)

    /** Manda ao cliente quantos bolsos a calça tem e o que há em cada um (o painel do inventário desenha a partir disto). */
    public static void enviarBolsos(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        d.putInt("n", capacidade(p));
        d.put("itens", ContainerHelper.saveAllItems(new CompoundTag(), ler(p)));
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("bolsosdados", d));
    }

    /**
     * Clique num bolso do painel do inventário. modo: -1 só pede os dados; 0 botão esquerdo (pega, põe ou troca);
     * 1 botão direito (pega metade ou põe um); 2 com Shift (manda o item do bolso para a mochila).
     * Tudo é decidido aqui no servidor, com o item que está "na mão do mouse" (o carregado do inventário).
     */
    public static void clique(ServerPlayer p, int bolso, int modo) {
        if (modo < 0 || modo > 2 || p.isCreative() || p.isSpectator() || !p.isAlive() || p.containerMenu != p.inventoryMenu
                || bolso < 0 || bolso >= capacidade(p)) { enviarBolsos(p); return; }
        arrumar(p);
        NonNullList<ItemStack> l = ler(p);
        ItemStack dentro = l.get(bolso), mao = p.inventoryMenu.getCarried();
        if (modo == 2) {
            if (!dentro.isEmpty()) p.getInventory().add(dentro);
        } else if (mao.isEmpty()) {
            if (!dentro.isEmpty()) {
                if (modo == 1) p.inventoryMenu.setCarried(dentro.split((dentro.getCount() + 1) / 2));
                else { p.inventoryMenu.setCarried(dentro); dentro = ItemStack.EMPTY; }
            }
        } else if (cabe(mao)) {
            if (dentro.isEmpty()) {
                dentro = modo == 1 ? mao.split(1) : mao.split(mao.getCount());
            } else if (ItemStack.isSameItemSameTags(dentro, mao)) {
                int quanto = Math.min(modo == 1 ? 1 : mao.getCount(), dentro.getMaxStackSize() - dentro.getCount());
                if (quanto > 0) { dentro.grow(quanto); mao.shrink(quanto); }
            } else if (modo == 0) {
                p.inventoryMenu.setCarried(dentro);
                dentro = mao;
                mao = ItemStack.EMPTY;
            }
            if (mao.isEmpty() && p.inventoryMenu.getCarried() == mao) p.inventoryMenu.setCarried(ItemStack.EMPTY);
        }
        l.set(bolso, dentro.isEmpty() ? ItemStack.EMPTY : dentro);
        gravar(p, l);
        p.inventoryMenu.broadcastChanges();
        enviarBolsos(p);
    }

    /** Abre os bolsos (tecla ou botão do inventário). */
    public static void abrirBolsos(ServerPlayer p) {
        int n = capacidade(p);
        if (n <= 0) {
            p.displayClientMessage(Component.literal(p.getItemBySlot(EquipmentSlot.LEGS).isEmpty() ? "Você está sem calça. Os bolsos são costurados na calça, pela Costureira."
                    : "Sua calça não tem bolsos. Leve-a à Costureira.").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        arrumar(p);
        p.openMenu(new SimpleMenuProvider((id, inv, jogador) -> new MenuBolsos(id, inv, new SimpleContainer(CASAS), p, n),
                Component.literal("Bolsos da calça · " + n + " de " + MAXIMO)));
    }

    /** O que está em bolso que o jogador não tem mais (tirou a calça, vestiu uma com menos bolsos) volta para a mochila; o que não couber cai no chão. */
    private static void arrumar(ServerPlayer p) {
        if (!pers(p).contains(GUARDADO)) return;
        int n = capacidade(p);
        NonNullList<ItemStack> l = ler(p);
        boolean sobra = false;
        for (int i = n; i < MAXIMO; i++) if (!l.get(i).isEmpty()) { sobra = true; break; }
        if (!sobra) return;
        if (p.containerMenu instanceof MenuBolsos) { p.closeContainer(); l = ler(p); }
        for (int i = n; i < MAXIMO; i++) {
            ItemStack s = l.get(i);
            if (s.isEmpty()) continue;
            l.set(i, ItemStack.EMPTY);
            if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
        }
        gravar(p, l);
        p.displayClientMessage(Component.literal("O que estava nos bolsos voltou para a mochila.").withStyle(ChatFormatting.YELLOW), true);
    }

    @SubscribeEvent
    public void aoPassarTempo(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 20 != 0 || !p.isAlive()) return;
        arrumar(p);
    }

    /** Morrer derruba o que está nos bolsos junto com o resto da mochila (a não ser que o mundo guarde o inventário na morte). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoCairItens(LivingDropsEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !pers(p).contains(GUARDADO)) return;
        if (p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        if (p.containerMenu instanceof MenuBolsos) p.doCloseContainer();
        NonNullList<ItemStack> l = ler(p);
        for (ItemStack s : l) if (!s.isEmpty()) ev.getDrops().add(new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), s));
        pers(p).remove(GUARDADO);
    }
}
