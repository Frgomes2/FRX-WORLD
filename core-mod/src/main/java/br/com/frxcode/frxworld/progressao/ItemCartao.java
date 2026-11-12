package br.com.frxcode.frxworld.progressao;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Cartão de Aventureiro da Guilda: mostra rank, classe e feitos. Clique direito abre o cartão. */
public class ItemCartao extends Item {
    public ItemCartao() { super(new Item.Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level nivel, Player p, InteractionHand mao) {
        if (p instanceof ServerPlayer sp) Guilda.abrir(sp, null, "", true);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(mao), nivel.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack s, Level nivel, List<Component> linhas, TooltipFlag flag) {
        CompoundTag t = s.getTag();
        if (t == null || !t.contains("frx_dono")) {
            linhas.add(Component.literal("Registre-se com a recepcionista da Guilda.").withStyle(ChatFormatting.GRAY));
            return;
        }
        linhas.add(Component.literal(t.getString("frx_dono")).withStyle(ChatFormatting.WHITE));
        linhas.add(Component.literal("Rank " + t.getString("frx_rank")).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  ·  Nível " + t.getInt("frx_nivel")).withStyle(ChatFormatting.YELLOW)));
        if (!t.getString("frx_classe").isEmpty()) linhas.add(Component.literal(t.getString("frx_classe")).withStyle(ChatFormatting.AQUA));
        linhas.add(Component.literal("Clique direito para abrir.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
