package br.com.frxcode.frxworld.cliente;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;

/**
 * Marca um ponto no mapa do JourneyMap (tela cheia e minimapa) sem o jogador clicar em nada.
 * Usa a API do JourneyMap por reflexão: se o mod não estiver instalado ou a API mudar, simplesmente não marca
 * (as coordenadas clicáveis no chat continuam valendo). Os pontos são do grupo "frxworld" e não ficam salvos.
 */
public final class MarcadorMapa {
    private static final String MOD = "frxworld";

    private MarcadorMapa() {}

    private static Object api() throws Exception {
        return Class.forName("journeymap.api.client.impl.ClientAPI").getField("INSTANCE").get(null);
    }

    private static void chamar(Object alvo, String nome, Object... args) throws Exception {
        for (Method m : alvo.getClass().getMethods()) {
            if (!m.getName().equals(nome) || m.getParameterCount() != args.length) continue;
            m.invoke(alvo, args);
            return;
        }
    }

    /** Dados: {remover: true} tira os pontos do FRX; senão {nome, x, y, z} põe (trocando o anterior). */
    public static void tratar(CompoundTag d) {
        try {
            Object api = api();
            chamar(api, "removeAllWaypoints", MOD);
            if (d.getBoolean("remover")) return;
            BlockPos pos = new BlockPos(d.getInt("x"), d.getInt("y"), d.getInt("z"));
            Class<?> fabrica = Class.forName("journeymap.api.v2.common.waypoint.WaypointFactory");
            Object ponto = fabrica.getMethod("createWaypoint", String.class, BlockPos.class, String.class, String.class, boolean.class)
                    .invoke(null, MOD, pos, d.getString("nome"), "minecraft:overworld", false);
            try { chamar(ponto, "setColor", 0xB040FF); } catch (Throwable ignorado) { }
            for (Method m : api.getClass().getMethods()) {
                if (m.getName().equals("addWaypoint") && m.getParameterCount() == 2) { m.invoke(api, MOD, ponto); break; }
            }
        } catch (Throwable erro) {
            // sem JourneyMap ou com outra versão da API: fica só o aviso no chat
            com.mojang.logging.LogUtils.getLogger().warn("FRX: não marquei o ponto no JourneyMap: {}", String.valueOf(erro.getCause() != null ? erro.getCause() : erro));
        }
    }
}
