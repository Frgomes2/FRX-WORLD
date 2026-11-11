package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.cliente.ClienteMissoes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: missões ativas para o rastreador do HUD. */
public record SyncRastreador(List<Linha> linhas) {
    public record Linha(String titulo, int progresso, int quantidade, boolean pronta) {}

    public void codificar(FriendlyByteBuf b) {
        b.writeCollection(linhas, (buf, l) -> {
            buf.writeUtf(l.titulo()); buf.writeVarInt(l.progresso()); buf.writeVarInt(l.quantidade()); buf.writeBoolean(l.pronta());
        });
    }

    public static SyncRastreador decodificar(FriendlyByteBuf b) {
        return new SyncRastreador(b.readList(buf -> new Linha(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean())));
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClienteMissoes.rastreador = linhas);
    }
}
