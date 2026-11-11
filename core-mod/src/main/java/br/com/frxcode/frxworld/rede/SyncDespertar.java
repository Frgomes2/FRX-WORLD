package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: barra do Despertar (0-100), poder ativo (ticks restantes/total), cargas extras e se já liberou. */
public record SyncDespertar(int carga, int ativo, int total, int extra, boolean liberado) {
    public void codificar(FriendlyByteBuf b) { b.writeVarInt(carga); b.writeVarInt(ativo); b.writeVarInt(total); b.writeVarInt(extra); b.writeBoolean(liberado); }

    public static SyncDespertar decodificar(FriendlyByteBuf b) { return new SyncDespertar(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.ClienteHabilidades.aplicar(this));
    }
}
