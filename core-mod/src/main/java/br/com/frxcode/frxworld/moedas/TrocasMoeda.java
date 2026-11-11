package br.com.frxcode.frxworld.moedas;

import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/** Todo comércio com aldeões e mercadores passa a usar moeda de prata no lugar da esmeralda (1 esmeralda = 1 prata). */
public class TrocasMoeda {

    @SubscribeEvent
    public void aldeoes(VillagerTradesEvent e) {
        for (List<VillagerTrades.ItemListing> lista : e.getTrades().values()) lista.replaceAll(TrocasMoeda::embrulhar);
    }

    @SubscribeEvent
    public void andarilho(WandererTradesEvent e) {
        e.getGenericTrades().replaceAll(TrocasMoeda::embrulhar);
        e.getRareTrades().replaceAll(TrocasMoeda::embrulhar);
    }

    private static VillagerTrades.ItemListing embrulhar(VillagerTrades.ItemListing original) {
        return (ent, rnd) -> {
            MerchantOffer o = original.getOffer(ent, rnd);
            return o == null ? null : converter(o);
        };
    }

    public static MerchantOffer converter(MerchantOffer o) {
        return new MerchantOffer(troca(o.getBaseCostA()), troca(o.getCostB()), troca(o.getResult()),
                o.getUses(), o.getMaxUses(), o.getXp(), o.getPriceMultiplier(), o.getDemand());
    }

    private static ItemStack troca(ItemStack s) {
        if (s.is(Items.EMERALD)) return Moedas.prata(s.getCount());
        if (s.is(Items.EMERALD_BLOCK)) return Moedas.ouro(Math.max(1, s.getCount()));
        return s;
    }
}
