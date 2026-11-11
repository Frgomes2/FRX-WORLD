package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Raças dos moradores das capitais. São "tipos de aldeão" do mod (sincronizados com o cliente),
 * e o cliente desenha esses aldeões com corpo de gente e uma skin da raça (cliente/RenderRacas).
 */
public final class Racas {
    private Racas() {}

    private static final DeferredRegister<VillagerType> TIPOS = DeferredRegister.create(Registries.VILLAGER_TYPE, FrxWorld.MOD_ID);
    public static final RegistryObject<VillagerType> HUMANO = TIPOS.register("humano", () -> new VillagerType("humano"));
    public static final RegistryObject<VillagerType> ANAO = TIPOS.register("anao", () -> new VillagerType("anao"));
    public static final RegistryObject<VillagerType> ELFO = TIPOS.register("elfo", () -> new VillagerType("elfo"));
    public static final RegistryObject<VillagerType> HOBGOBLIN = TIPOS.register("hobgoblin", () -> new VillagerType("hobgoblin"));
    public static final RegistryObject<VillagerType> ORIENTAL = TIPOS.register("oriental", () -> new VillagerType("oriental"));
    public static final RegistryObject<VillagerType> SAGRADO = TIPOS.register("sagrado", () -> new VillagerType("sagrado"));
    public static final RegistryObject<VillagerType> DEMONIO = TIPOS.register("demonio", () -> new VillagerType("demonio"));
    public static final RegistryObject<VillagerType> BEASTKIN = TIPOS.register("beastkin", () -> new VillagerType("beastkin"));
    public static final RegistryObject<VillagerType> HALFLING = TIPOS.register("halfling", () -> new VillagerType("halfling"));
    public static final RegistryObject<VillagerType> ONI = TIPOS.register("oni", () -> new VillagerType("oni"));
    public static final RegistryObject<VillagerType> VAMPIRO = TIPOS.register("vampiro", () -> new VillagerType("vampiro"));
    public static final RegistryObject<VillagerType> DRAGONOIDE = TIPOS.register("dragonoide", () -> new VillagerType("dragonoide"));
    public static final RegistryObject<VillagerType> ESPIRITO = TIPOS.register("espirito", () -> new VillagerType("espirito"));
    public static final RegistryObject<VillagerType> GIGANTE = TIPOS.register("gigante", () -> new VillagerType("gigante"));

    public static void registrar(IEventBus modBus) {
        TIPOS.register(modBus);
    }

    /**
     * Raça de um morador nascido nesta capital, por pesos (soma 100).
     * Tempest é a federação de todos os povos; os outros reinos têm uma raça dominante e algumas minorias.
     */
    public static VillagerType de(Reino r, RandomSource rnd) {
        Object[] pesos = switch (r.id()) {
            case "tempest" -> new Object[]{HOBGOBLIN, 28, BEASTKIN, 16, HUMANO, 12, ONI, 10, ANAO, 8, ELFO, 8, DRAGONOIDE, 6, HALFLING, 5, ESPIRITO, 4, DEMONIO, 3};
            case "dwargon" -> new Object[]{ANAO, 82, GIGANTE, 8, HUMANO, 6, HALFLING, 4};
            case "sarion" -> new Object[]{ELFO, 82, ESPIRITO, 10, HUMANO, 8};
            case "leste" -> new Object[]{ORIENTAL, 78, ONI, 14, BEASTKIN, 8};
            case "leste-sakura" -> new Object[]{ORIENTAL, 72, ONI, 18, ESPIRITO, 10};
            case "lubelius" -> new Object[]{SAGRADO, 72, VAMPIRO, 15, HUMANO, 13};
            case "demonios" -> new Object[]{DEMONIO, 62, VAMPIRO, 15, ONI, 12, DRAGONOIDE, 11};
            case "oeste" -> new Object[]{HUMANO, 70, HALFLING, 12, BEASTKIN, 8, ANAO, 6, ELFO, 4};
            default -> new Object[]{HUMANO, 100};
        };
        int total = 0;
        for (int i = 1; i < pesos.length; i += 2) total += (Integer) pesos[i];
        int sorteio = rnd.nextInt(Math.max(1, total));
        for (int i = 0; i < pesos.length; i += 2) {
            sorteio -= (Integer) pesos[i + 1];
            @SuppressWarnings("unchecked") RegistryObject<VillagerType> t = (RegistryObject<VillagerType>) pesos[i];
            if (sorteio < 0) return t.get();
        }
        return HUMANO.get();
    }

    public static boolean ehRaca(ResourceLocation tipo) {
        return tipo != null && tipo.getNamespace().equals(FrxWorld.MOD_ID);
    }
}
