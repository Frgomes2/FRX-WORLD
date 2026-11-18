package br.com.frxcode.frxworld.protecao;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tranca de baú: agachado, clique com um gancho de armadilha num baú (ou barril, fornalha, mochila no chão...) para trancá-lo.
 * Só o dono abre e quebra; explosões não o destroem e ninguém mais põe funil embaixo. Agachado + gancho de novo destranca e devolve o gancho.
 * O dono fica gravado no próprio bloco (ForgeData do BlockEntity), então some quando o baú é quebrado. Admin em criativo passa por tudo.
 */
public class Trancas {
    private static final String DONO = "frx_tranca_dono", NOME = "frx_tranca_nome";
    private static final Map<UUID, Long> avisado = new ConcurrentHashMap<>();

    public Trancas() {}

    private static boolean trancavel(BlockEntity be) {
        if (be == null) return false;
        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
        String ns = id == null ? "" : id.getNamespace();
        if (ns.equals("lootr") || ns.equals("gravestone")) return false;   // baú de saque é de cada um; a lápide já tem dono
        return be instanceof Container || be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    /** A outra metade de um baú duplo, ou null. */
    private static BlockPos outraMetade(Level n, BlockPos pos) {
        BlockState s = n.getBlockState(pos);
        if (!(s.getBlock() instanceof ChestBlock) || !s.hasProperty(ChestBlock.TYPE) || s.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        return pos.relative(ChestBlock.getConnectedDirection(s));
    }

    private static CompoundTag marca(Level n, BlockPos pos) {
        BlockEntity be = n.getBlockEntity(pos);
        if (be != null && be.getPersistentData().hasUUID(DONO)) return be.getPersistentData();
        BlockPos o = outraMetade(n, pos);
        BlockEntity b2 = o == null ? null : n.getBlockEntity(o);
        return b2 != null && b2.getPersistentData().hasUUID(DONO) ? b2.getPersistentData() : null;
    }

    private static void gravar(Level n, BlockPos pos, Player dono) {
        for (BlockPos p : new BlockPos[]{pos, outraMetade(n, pos)}) {
            BlockEntity be = p == null ? null : n.getBlockEntity(p);
            if (be == null) continue;
            if (dono == null) { be.getPersistentData().remove(DONO); be.getPersistentData().remove(NOME); }
            else { be.getPersistentData().putUUID(DONO, dono.getUUID()); be.getPersistentData().putString(NOME, dono.getGameProfile().getName()); }
            be.setChanged();
        }
    }

    private static boolean admin(Player p) { return p.isCreative() && p.hasPermissions(2); }

    private static void avisar(Player p, String texto, ChatFormatting cor) {
        long agora = System.currentTimeMillis();
        if (cor == ChatFormatting.RED && agora - avisado.getOrDefault(p.getUUID(), 0L) < 1500) return;
        avisado.put(p.getUUID(), agora);
        p.displayClientMessage(Component.literal(texto).withStyle(cor), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoClicar(PlayerInteractEvent.RightClickBlock ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Level n = ev.getLevel();
        BlockPos pos = ev.getPos();
        if (!trancavel(n.getBlockEntity(pos))) return;
        CompoundTag m = marca(n, pos);
        boolean dele = m != null && m.getUUID(DONO).equals(p.getUUID());
        ItemStack mao = p.getMainHandItem();

        if (p.isShiftKeyDown() && mao.is(Items.TRIPWIRE_HOOK)) {
            ev.setCanceled(true);
            ev.setCancellationResult(InteractionResult.SUCCESS);
            if (ev.getHand() != InteractionHand.MAIN_HAND) return;
            if (m == null) {
                gravar(n, pos, p);
                if (!p.isCreative()) mao.shrink(1);
                n.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.4f);
                avisar(p, "🔒 Trancado. Só você abre e quebra este baú. Agachado + gancho de novo para destrancar.", ChatFormatting.GREEN);
            } else if (dele || admin(p)) {
                gravar(n, pos, null);
                if (!p.isCreative() && !p.getInventory().add(new ItemStack(Items.TRIPWIRE_HOOK))) p.drop(new ItemStack(Items.TRIPWIRE_HOOK), false);
                n.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 1.4f);
                avisar(p, "🔓 Destrancado. Qualquer um pode abrir agora.", ChatFormatting.YELLOW);
            } else {
                avisar(p, "🔒 Este baú já é de " + m.getString(NOME) + ".", ChatFormatting.RED);
            }
            return;
        }
        if (m == null || dele || admin(p)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.FAIL);
        if (ev.getHand() == InteractionHand.MAIN_HAND) {
            n.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8f, 1f);
            avisar(p, "🔒 Trancado por " + m.getString(NOME) + ".", ChatFormatting.RED);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoQuebrar(BlockEvent.BreakEvent ev) {
        if (!(ev.getLevel() instanceof Level n) || n.isClientSide()) return;
        CompoundTag m = marca(n, ev.getPos());
        if (m == null) return;
        Player p = ev.getPlayer();
        if (p != null && (m.getUUID(DONO).equals(p.getUUID()) || admin(p))) return;
        ev.setCanceled(true);
        if (p != null) avisar(p, "🔒 Trancado por " + m.getString(NOME) + ": só o dono quebra.", ChatFormatting.RED);
    }

    /** Explosão não leva baú trancado. */
    @SubscribeEvent
    public void naExplosao(ExplosionEvent.Detonate ev) {
        Level n = ev.getLevel();
        if (n.isClientSide()) return;
        ev.getAffectedBlocks().removeIf(b -> n.getBlockEntity(b) != null && marca(n, b) != null);
    }

    /** Ninguém além do dono encosta funil embaixo de um baú trancado para puxar os itens. */
    @SubscribeEvent
    public void aoColocar(BlockEvent.EntityPlaceEvent ev) {
        if (!(ev.getLevel() instanceof Level n) || n.isClientSide() || !(ev.getPlacedBlock().getBlock() instanceof HopperBlock)) return;
        CompoundTag m = marca(n, ev.getPos().above());
        if (m == null) return;
        if (ev.getEntity() instanceof Player p && (m.getUUID(DONO).equals(p.getUUID()) || admin(p))) return;
        ev.setCanceled(true);
        if (ev.getEntity() instanceof Player p) avisar(p, "🔒 O baú de cima é de " + m.getString(NOME) + ": funil aqui não.", ChatFormatting.RED);
    }
}
