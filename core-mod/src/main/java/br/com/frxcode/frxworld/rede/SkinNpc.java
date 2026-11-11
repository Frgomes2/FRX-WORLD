package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: skin personalizada de um tipo de NPC (feita no painel). png vazio = remover. */
public record SkinNpc(String chave, boolean slim, byte[] png) {
    private static final int MAX = 200_000;

    public void codificar(FriendlyByteBuf b) {
        b.writeUtf(chave, 120);
        b.writeBoolean(slim);
        b.writeByteArray(png);
    }

    public static SkinNpc decodificar(FriendlyByteBuf b) {
        return new SkinNpc(b.readUtf(120), b.readBoolean(), b.readByteArray(MAX));
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.SkinsNpcCliente.receber(chave, slim, png));
    }
}
