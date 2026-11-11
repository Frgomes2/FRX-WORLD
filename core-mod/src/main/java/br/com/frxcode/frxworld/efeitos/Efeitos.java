package br.com.frxcode.frxworld.efeitos;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Efeitos {
    private static final DeferredRegister<MobEffect> REGISTRO = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, FrxWorld.MOD_ID);

    public static final RegistryObject<MobEffect> FERIDO = REGISTRO.register("ferido", EfeitoFerido::new);

    private Efeitos() {}

    public static void registrar(IEventBus modBus) {
        REGISTRO.register(modBus);
    }
}
