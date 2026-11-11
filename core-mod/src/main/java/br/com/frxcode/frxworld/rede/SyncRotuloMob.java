package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: nível e rank de um monstro visível (para o letreiro em cima dele). */
public record SyncRotuloMob(int entidade, int nivel, String rank, int estrelas, String tracos, String prefixo, String titulo) {
    public void codificar(FriendlyByteBuf b) {
        b.writeVarInt(entidade); b.writeVarInt(nivel); b.writeUtf(rank, 16); b.writeVarInt(estrelas);
        b.writeUtf(tracos, 64); b.writeUtf(prefixo, 32); b.writeUtf(titulo, 64);
    }

    public static SyncRotuloMob decodificar(FriendlyByteBuf b) {
        return new SyncRotuloMob(b.readVarInt(), b.readVarInt(), b.readUtf(16), b.readVarInt(), b.readUtf(64), b.readUtf(32), b.readUtf(64));
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.RotulosMob.aplicar(this));
    }
}
