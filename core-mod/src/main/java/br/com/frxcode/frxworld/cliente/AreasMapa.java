package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Áreas pintadas no mapa do JourneyMap (terrenos comprados e a escolha em andamento), a pedido do servidor.
 * Usa a API de cliente do JourneyMap por reflexão: sem o mod, ou com outra versão da API, nada é pintado e o resto continua igual.
 */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AreasMapa {
    private static final String MOD = "frxworld";
    private static final Map<String, Object> DESENHOS = new HashMap<>();
    private static boolean avisou;

    private AreasMapa() {}

    private static Object api() throws Exception {
        return Class.forName("journeymap.api.client.impl.ClientAPI").getField("INSTANCE").get(null);
    }

    private static Method metodo(Object alvo, String nome, int parametros) throws Exception {
        for (Method m : alvo.getClass().getMethods()) if (m.getName().equals(nome) && m.getParameterCount() == parametros) return m;
        throw new NoSuchMethodException(nome);
    }

    private static Object chamar(Object alvo, String nome, Object... args) throws Exception {
        return metodo(alvo, nome, args.length).invoke(alvo, args);
    }

    /** show e remove da API, pelo tipo exato do parâmetro (a classe do JourneyMap tem outros métodos com o mesmo nome). */
    private static Method daApi(String nome) throws Exception {
        return Class.forName("journeymap.api.v2.client.IClientAPI").getMethod(nome, Class.forName("journeymap.api.v2.client.display.Displayable"));
    }

    private static void falhou(Throwable t) {
        if (avisou) return;
        avisou = true;
        com.mojang.logging.LogUtils.getLogger().warn("FRX: não pintei a área no JourneyMap: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
    }

    private static void tirar(Object api, String id) throws Exception {
        Object velho = DESENHOS.remove(id);
        if (velho != null) daApi("remove").invoke(api, velho);
    }

    /** Dados: {limpar: true} tira tudo; {id, remover: true} tira um; senão {id, x0, z0, x1, z1, cor, fundo, rotulo, titulo} pinta (trocando o anterior). */
    public static void tratar(CompoundTag d) {
        try {
            Object api = api();
            if (d.getBoolean("limpar")) {
                for (String id : List.copyOf(DESENHOS.keySet())) tirar(api, id);
                return;
            }
            String id = d.getString("id");
            tirar(api, id);
            if (d.getBoolean("remover")) return;
            int x0 = d.getInt("x0"), z0 = d.getInt("z0"), x1 = d.getInt("x1") + 1, z1 = d.getInt("z1") + 1, cor = d.getInt("cor");
            Class<?> propsC = Class.forName("journeymap.api.v2.client.model.ShapeProperties");
            Class<?> poliC = Class.forName("journeymap.api.v2.client.model.MapPolygon");
            Class<?> desenhoC = Class.forName("journeymap.api.v2.client.display.PolygonOverlay");
            Object props = propsC.getConstructor().newInstance();
            chamar(props, "setStrokeColor", cor);
            chamar(props, "setFillColor", cor);
            chamar(props, "setStrokeOpacity", 0.95f);
            chamar(props, "setFillOpacity", Math.max(0.05f, d.getFloat("fundo")));
            chamar(props, "setStrokeWidth", 2f);
            // sudoeste, sudeste, nordeste, noroeste: a ordem que o JourneyMap pede
            Object poli = poliC.getConstructor(List.class).newInstance(List.of(new BlockPos(x0, 70, z1), new BlockPos(x1, 70, z1), new BlockPos(x1, 70, z0), new BlockPos(x0, 70, z0)));
            Constructor<?> construtor = desenhoC.getConstructor(String.class, ResourceKey.class, propsC, poliC);
            ResourceKey<Level> dim = d.contains("dim")
                    ? ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(d.getString("dim")))
                    : Level.OVERWORLD;
            Object desenho = construtor.newInstance(MOD, dim, props, poli);
            chamar(desenho, "setOverlayGroupName", "Terrenos");
            if (!d.getString("rotulo").isEmpty()) chamar(desenho, "setLabel", d.getString("rotulo"));
            if (!d.getString("titulo").isEmpty()) chamar(desenho, "setTitle", d.getString("titulo"));
            daApi("show").invoke(api, desenho);
            DESENHOS.put(id, desenho);
        } catch (Throwable t) { falhou(t); }
    }

    /** Ao sair do servidor, o mapa esquece os desenhos (outro servidor ou outro mundo não tem os mesmos terrenos). */
    @SubscribeEvent
    public static void aoSair(ClientPlayerNetworkEvent.LoggingOut e) {
        try {
            Object api = api();
            for (String id : List.copyOf(DESENHOS.keySet())) tirar(api, id);
        } catch (Throwable t) { DESENHOS.clear(); }
    }
}
