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
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Mesa de costura da Costureira: um espaço para a peça (a calça dos bolsos, a peça a remendar ou bordar) e um para a
 * segunda calça (usado em "Passar bolsos"), mais o inventário. As abas da tela (cliente/TelaCostura) dizem o que fazer
 * com elas; o servidor faz tudo em Costureira.acao. Ao fechar, o que ficou nos espaços volta ao jogador.
 */
public class MenuCostura extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, FrxWorld.MOD_ID);
    public static final RegistryObject<MenuType<MenuCostura>> TIPO = MENUS.register("costura",
            () -> IForgeMenuType.create((id, inv, buf) -> new MenuCostura(id, inv, buf.readInt(), buf.readNbt())));

    public static final int PECA = 0, SEGUNDA = 1;
    /** Posições dos espaços na tela (a tela usa as mesmas). */
    public static final int PECA_X = 22, PECA_Y = 58, SEGUNDA_X = 22, SEGUNDA_Y = 108, INV_X = 69, INV_Y = 164;

    public final Container caixa = new SimpleContainer(2) {
        @Override public void setChanged() { super.setChanged(); slotsChanged(this); }
    };
    public final int npc;
    /** Preços, listas de acabamentos e dados do jogador para a tela (só no cliente; o servidor recalcula tudo). */
    public CompoundTag info;

    public static void registrar(IEventBus modBus) { MENUS.register(modBus); }

    public MenuCostura(int id, Inventory inv, int npc, CompoundTag info) {
        super(TIPO.get(), id);
        this.npc = npc;
        this.info = info == null ? new CompoundTag() : info;
        addSlot(new Slot(caixa, PECA, PECA_X, PECA_Y) {
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(caixa, SEGUNDA, SEGUNDA_X, SEGUNDA_Y) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack s) { return Costureira.calca(s); }
        });
        for (int l = 0; l < 3; l++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + l * 9 + 9, INV_X + c * 18, INV_Y + l * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
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

    /** Shift-clique: dos espaços para a mochila; da mochila, para o espaço da peça (ou o da segunda calça, se a peça já estiver ocupada). */
    @Override
    public ItemStack quickMoveStack(Player p, int indice) {
        Slot slot = slots.get(indice);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copia = s.copy();
        if (indice < 2) {
            if (!moveItemStackTo(s, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!slots.get(PECA).hasItem()) {
            if (!moveItemStackTo(s, PECA, PECA + 1, false)) return ItemStack.EMPTY;
        } else if (Costureira.calca(s) && !slots.get(SEGUNDA).hasItem()) {
            if (!moveItemStackTo(s, SEGUNDA, SEGUNDA + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (s.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copia;
    }
}
