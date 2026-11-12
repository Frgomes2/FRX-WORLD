package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.cliente.ClienteDados;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Servidor → cliente: nível de um jogador.
 * Para o próprio jogador vai também a XP (para a barra do HUD); para os outros só o nível (para o nome).
 */
public record SyncNivel(UUID jogador, int nivel, String rank, long xp, long xpProximo, String titulo) {

    public void codificar(FriendlyByteBuf buf) {
        buf.writeUUID(jogador);
        buf.writeVarInt(nivel);
        buf.writeUtf(rank, 4);
        buf.writeVarLong(xp);
        buf.writeVarLong(xpProximo);
        buf.writeUtf(titulo, 64);
    }

    public static SyncNivel decodificar(FriendlyByteBuf buf) {
        return new SyncNivel(buf.readUUID(), buf.readVarInt(), buf.readUtf(4), buf.readVarLong(), buf.readVarLong(), buf.readUtf(64));
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClienteDados.aplicar(this));
    }
}
