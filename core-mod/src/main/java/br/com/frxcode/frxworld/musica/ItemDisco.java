package br.com.frxcode.frxworld.musica;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Disco de música do painel: um item só; a música, o nome e a aparência vêm no NBT (ver {@link Discos#criar}). */
public class ItemDisco extends Item {
    public ItemDisco() { super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)); }

    @Override
    public Component getName(ItemStack s) {
        String nome = s.hasTag() ? s.getTag().getString(Discos.NBT_NOME) : "";
        return nome.isEmpty() ? super.getName(s) : Component.literal("Disco: " + nome);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level nivel, List<Component> linhas, TooltipFlag flag) {
        if (!s.hasTag()) return;
        String artista = s.getTag().getString(Discos.NBT_ARTISTA), desc = s.getTag().getString(Discos.NBT_DESC);
        int dur = s.getTag().getInt(Discos.NBT_DUR);
        if (!artista.isEmpty()) linhas.add(Component.literal(artista).withStyle(ChatFormatting.GRAY));
        if (!desc.isEmpty()) linhas.add(Component.literal(desc).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (s.getTag().getInt("frx_total") > 0) linhas.add(Component.literal("Edição limitada: nº " + s.getTag().getInt("frx_num") + " de " + s.getTag().getInt("frx_total")).withStyle(ChatFormatting.GOLD));
        if (dur > 0) linhas.add(Component.literal("♪ " + dur / 60 + ":" + String.format("%02d", dur % 60)).withStyle(ChatFormatting.AQUA));
        linhas.add(Component.literal("Clique numa jukebox para tocar").withStyle(ChatFormatting.DARK_GRAY));
    }
}
