package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AbrirQuadro;
import br.com.frxcode.frxworld.rede.SyncRastreador;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Estado das missões no cliente: tela do quadro e rastreador do HUD. */
public final class ClienteMissoes {
    public static volatile List<SyncRastreador.Linha> rastreador = List.of();

    private ClienteMissoes() {}

    public static void abrirQuadro(AbrirQuadro msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TelaQuadro tela) tela.atualizar(msg);
        else mc.setScreen(new TelaQuadro(msg));
    }
}
