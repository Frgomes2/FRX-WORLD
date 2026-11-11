package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Servidor → todos: como a raça de um jogador aparece (nome, traços visuais e aura discreta). Texto vazio = sem raça. */
public record SyncRacaVisivel(UUID jogador, String texto, String visualPrincipal, String visualSecundaria, String aura) {
    public void codificar(FriendlyByteBuf b) {
        b.writeUUID(jogador); b.writeUtf(texto, 64); b.writeUtf(visualPrincipal, 32); b.writeUtf(visualSecundaria, 32); b.writeUtf(aura, 32);
    }

    public static SyncRacaVisivel decodificar(FriendlyByteBuf b) {
        return new SyncRacaVisivel(b.readUUID(), b.readUtf(64), b.readUtf(32), b.readUtf(32), b.readUtf(32));
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.ClienteRacas.aplicar(this));
    }
}
