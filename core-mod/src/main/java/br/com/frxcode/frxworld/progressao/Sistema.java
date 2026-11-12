package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.rede.AvisoSistema;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Janela de "Sistema": avisos importantes numa caixa no topo da tela (nível, missão, conquista, promoção). */
public final class Sistema {
    public static final int AZUL = 0, OURO = 1, VERDE = 2, VERMELHO = 3, ROXO = 4;

    private Sistema() {}

    public static void avisar(ServerPlayer p, String titulo, String texto, int cor) {
        String t = titulo.length() > 78 ? titulo.substring(0, 78) : titulo, x = texto.length() > 236 ? texto.substring(0, 236) : texto;
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AvisoSistema(t, x, cor));
    }
}
