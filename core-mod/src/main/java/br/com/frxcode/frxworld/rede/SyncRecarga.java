package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.cliente.ClienteHabilidades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: uma habilidade entrou em recarga (ms restantes e total). */
public record SyncRecarga(String codigo, int restanteMs, int totalMs) {
    public void codificar(FriendlyByteBuf b) { b.writeUtf(codigo); b.writeVarInt(restanteMs); b.writeVarInt(totalMs); }

    public static SyncRecarga decodificar(FriendlyByteBuf b) { return new SyncRecarga(b.readUtf(), b.readVarInt(), b.readVarInt()); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClienteHabilidades.aplicar(this));
    }
}
