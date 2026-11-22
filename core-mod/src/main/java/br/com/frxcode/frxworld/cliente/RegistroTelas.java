package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.reinos.MenuDesencanto;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Liga as telas com espaços de inventário (menus próprios do mod) às suas telas no cliente. */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RegistroTelas {
    private RegistroTelas() {}

    @SubscribeEvent
    public static void aoPreparar(FMLClientSetupEvent ev) {
        ev.enqueueWork(() -> {
            MenuScreens.register(MenuDesencanto.TIPO.get(), TelaDesencanto::new);
            MenuScreens.register(br.com.frxcode.frxworld.reinos.MenuCostura.TIPO.get(), TelaCostura::new);
        });
    }
}
