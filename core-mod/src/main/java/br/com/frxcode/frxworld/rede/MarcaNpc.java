package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: esta entidade é um NPC do reino (papel e reino), para o cliente escolher a skin. */
public record MarcaNpc(int id, String papel, String reino) {
    public void codificar(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeUtf(papel, 40);
        b.writeUtf(reino, 40);
    }

    public static MarcaNpc decodificar(FriendlyByteBuf b) {
        return new MarcaNpc(b.readVarInt(), b.readUtf(40), b.readUtf(40));
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.SkinsNpcCliente.marcar(id, papel, reino));
    }
}
