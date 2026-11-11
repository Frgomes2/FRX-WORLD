package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.cliente.ClienteHabilidades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: mana/fúria/energia atual e máxima. */
public record SyncRecurso(int atual, int maximo) {
    public void codificar(FriendlyByteBuf b) { b.writeVarInt(atual); b.writeVarInt(maximo); }

    public static SyncRecurso decodificar(FriendlyByteBuf b) { return new SyncRecurso(b.readVarInt(), b.readVarInt()); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClienteHabilidades.aplicar(this));
    }
}
