package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: número de dano que salta de uma criatura. tipo: 0 normal, 1 golpe forte, 2 dano num jogador. */
public record NumeroDano(int entidade, float valor, int tipo) {
    public void codificar(FriendlyByteBuf b) { b.writeVarInt(entidade); b.writeFloat(valor); b.writeVarInt(tipo); }

    public static NumeroDano decodificar(FriendlyByteBuf b) { return new NumeroDano(b.readVarInt(), b.readFloat(), b.readVarInt()); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.DanoFlutuante.adicionar(this));
    }
}
