package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: dados da janela de status em JSON. */
public record SyncStatus(String json) {
    private static final int MAX = 262144;

    public void codificar(FriendlyByteBuf b) { b.writeUtf(json, MAX); }

    public static SyncStatus decodificar(FriendlyByteBuf b) { return new SyncStatus(b.readUtf(MAX)); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.ClienteStatus.receber(json));
    }
}
