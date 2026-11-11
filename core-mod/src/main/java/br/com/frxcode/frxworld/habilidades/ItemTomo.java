package br.com.frxcode.frxworld.habilidades;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Tomo Arcano: ensina uma habilidade "por item" da classe do jogador (aleatória, ou a gravada no NBT frx_habilidade). */
public class ItemTomo extends Item {
    public static final String NBT = "frx_habilidade";

    public ItemTomo() {
        super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level nivel, Player jogador, InteractionHand mao) {
        ItemStack pilha = jogador.getItemInHand(mao);
        if (!nivel.isClientSide && jogador instanceof ServerPlayer sp && Habilidades.lerTomo(sp, pilha)) {
            if (!sp.getAbilities().instabuild) pilha.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(pilha, nivel.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack pilha) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack pilha, @Nullable Level nivel, List<Component> linhas, TooltipFlag flag) {
        String nome = pilha.hasTag() ? pilha.getTag().getString("frx_nome") : "";
        linhas.add(Component.literal(nome.isEmpty() ? "Ensina uma habilidade rara da sua classe" : "Ensina: " + nome)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        linhas.add(Component.literal("Clique com o botão direito para ler").withStyle(ChatFormatting.GRAY));
    }
}
