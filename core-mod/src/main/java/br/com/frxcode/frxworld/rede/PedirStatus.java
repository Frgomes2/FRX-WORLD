package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.progressao.JanelaStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Cliente → servidor: pede os dados da janela de status (tecla E). */
public record PedirStatus() {
    public void codificar(FriendlyByteBuf b) {}

    public static PedirStatus decodificar(FriendlyByteBuf b) { return new PedirStatus(); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new SyncStatus(JanelaStatus.montar(p)));
    }
}
