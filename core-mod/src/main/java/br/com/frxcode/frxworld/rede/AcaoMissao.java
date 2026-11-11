package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.missoes.Missoes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: aceitar, entregar ou abandonar uma missão no quadro. */
public record AcaoMissao(String missaoId, int acao) {
    public static final int ACEITAR = 0, ENTREGAR = 1, ABANDONAR = 2;

    public void codificar(FriendlyByteBuf b) {
        b.writeUtf(missaoId);
        b.writeVarInt(acao);
    }

    public static AcaoMissao decodificar(FriendlyByteBuf b) {
        return new AcaoMissao(b.readUtf(), b.readVarInt());
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer jogador = ctx.get().getSender();
        if (jogador != null) Missoes.executar(jogador, missaoId, acao);
    }
}
