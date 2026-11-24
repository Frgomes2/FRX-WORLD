package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.racas.RacasJogador;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: confirmar a escolha de raça, alternar o disfarce da lendária ou pedir os dados. */
public record AcaoRaca(int acao, String principal, String secundaria) {
    public static final int CONFIRMAR = 0, REVELAR = 1, PEDIR = 2, TRANSFORMAR = 3;

    public void codificar(FriendlyByteBuf b) { b.writeVarInt(acao); b.writeUtf(principal, 32); b.writeUtf(secundaria, 32); }

    public static AcaoRaca decodificar(FriendlyByteBuf b) { return new AcaoRaca(b.readVarInt(), b.readUtf(32), b.readUtf(32)); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        switch (acao) {
            case CONFIRMAR -> RacasJogador.escolher(p, principal, secundaria, false);
            case REVELAR -> RacasJogador.alternarRevelar(p);
            case TRANSFORMAR -> br.com.frxcode.frxworld.racas.Saiyajin.transformar(p);
            default -> RacasJogador.sincronizar(p, false);
        }
    }
}
