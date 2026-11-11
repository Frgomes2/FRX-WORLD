package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.SyncNivel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Dados recebidos do servidor (só existe no cliente). */
public final class ClienteDados {
    private static final Map<UUID, String> ROTULOS = new ConcurrentHashMap<>();

    public static int meuNivel = 1;
    public static String meuRank = "F";
    public static long minhaXp = 0;
    public static long minhaXpProximo = 1;
    public static boolean recebido = false;

    private ClienteDados() {}

    public static void aplicar(SyncNivel msg) {
        Minecraft mc = Minecraft.getInstance();
        ROTULOS.put(msg.jogador(), msg.rank() + " · Nv " + msg.nivel());

        if (mc.player != null && mc.player.getUUID().equals(msg.jogador())) {
            meuNivel = msg.nivel();
            meuRank = msg.rank();
            minhaXp = msg.xp();
            minhaXpProximo = Math.max(1, msg.xpProximo());
            recebido = true;
        }

        if (mc.level != null) {
            Player p = mc.level.getPlayerByUUID(msg.jogador());
            if (p != null) p.refreshDisplayName();
        }
    }

    /** Ex.: "E · Nv 12" */
    public static String rotuloDe(UUID jogador) {
        return ROTULOS.get(jogador);
    }

    public static void limpar() {
        ROTULOS.clear();
        recebido = false;
    }
}
