package br.com.frxcode.frxworld.armas;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Nome colorido pela raridade, "+N" e uma linha com a qualidade na dica do item. */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClienteArmas {
    private ClienteArmas() {}

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void dica(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        if (!s.hasTag()) return;
        int r = Armas.raridade(s), n = Armas.nivel(s);
        if (r == 0 && n == 0) return;
        Raridade rar = Raridade.de(r);
        List<Component> t = e.getToolTip();
        if (t.isEmpty()) return;
        MutableComponent nome = t.get(0).copy().withStyle(rar.cor);
        if (n > 0) nome.append(Component.literal(" +" + n).withStyle(ChatFormatting.YELLOW));
        t.set(0, nome);
        if (s.getTag().getBoolean("frx_lore")) return; // a descrição já está gravada no item
        StringBuilder linha = new StringBuilder("★ ").append(rar.nome);
        int pct = (int) Math.round(Armas.bonusDano(s) * 100);
        if (pct > 0) linha.append(" · +").append(pct).append("% de dano");
        t.add(1, Component.literal(linha.toString()).withStyle(rar.cor));
    }
}
