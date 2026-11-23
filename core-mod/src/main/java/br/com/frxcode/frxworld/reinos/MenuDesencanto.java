package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Bancada do sábio (estilo bigorna): um espaço para o item encantado e um para o livro em branco, mais o inventário.
 * O jogador escolhe na tela qual encantamento vai para o livro (Desencanto.acao). O livro encantado aparece no
 * espaço do livro (ou na mochila, se ainda sobrarem livros em branco ali). Ao fechar, o que ficou nos espaços volta ao jogador.
 */
public class MenuDesencanto extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, FrxWorld.MOD_ID);
    public static final RegistryObject<MenuType<MenuDesencanto>> TIPO = MENUS.register("desencanto",
            () -> IForgeMenuType.create((id, inv, buf) -> new MenuDesencanto(id, inv, buf.readInt(), buf.readNbt())));

    public static final int ITEM = 0, LIVRO = 1;
    /** Posições dos espaços na tela (a tela usa as mesmas). */
    public static final int ITEM_X = 27, ITEM_Y = 34, LIVRO_X = 27, LIVRO_Y = 92, INV_X = 69, INV_Y = 164;

    public final Container caixa = new SimpleContainer(2) {
        @Override public void setChanged() { super.setChanged(); slotsChanged(this); }
    };
    public final int npc;
    /** Preços e dados do jogador para a tela (só no cliente; o servidor recalcula tudo). */
    public CompoundTag info;

    public static void registrar(IEventBus modBus) { MENUS.register(modBus); }

    public MenuDesencanto(int id, Inventory inv, int npc, CompoundTag info) {
        super(TIPO.get(), id);
        this.npc = npc;
        this.info = info == null ? new CompoundTag() : info;
        addSlot(new Slot(caixa, ITEM, ITEM_X, ITEM_Y) {
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(caixa, LIVRO, LIVRO_X, LIVRO_Y) {
            @Override public boolean mayPlace(ItemStack s) { return livroBranco(s); }
        });
        for (int l = 0; l < 3; l++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + l * 9 + 9, INV_X + c * 18, INV_Y + l * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
    }

    /** Livro em branco ou livro encantado vazio (sem nenhum encantamento guardado). */
    public static boolean livroBranco(ItemStack s) {
        return s.is(Items.BOOK) || (s.is(Items.ENCHANTED_BOOK) && net.minecraft.world.item.EnchantedBookItem.getEnchantments(s).isEmpty());
    }

    @Override
    public boolean stillValid(Player p) {
        if (p.level().isClientSide()) return true;
        Entity e = p.level().getEntity(npc);
        return e != null && e.isAlive() && e.distanceToSqr(p) <= 144;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (!p.level().isClientSide()) clearContainer(p, caixa);
    }

    /** Shift-clique: dos espaços para a mochila; da mochila, livro em branco para o espaço do livro e o resto para o do item. */
    @Override
    public ItemStack quickMoveStack(Player p, int indice) {
        Slot slot = slots.get(indice);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copia = s.copy();
        if (indice < 2) {
            if (!moveItemStackTo(s, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (livroBranco(s)) {
            if (!moveItemStackTo(s, LIVRO, LIVRO + 1, false)) return ItemStack.EMPTY;
        } else {
            if (slots.get(ITEM).hasItem() || !moveItemStackTo(s, ITEM, ITEM + 1, false)) return ItemStack.EMPTY;
        }
        if (s.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copia;
    }
}
