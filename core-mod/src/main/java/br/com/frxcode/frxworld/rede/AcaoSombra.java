package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.habilidades.Sombras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: comandos do exército de sombras pela tela ou pelas teclas rápidas. */
public record AcaoSombra(int acao, int numero, String id) {
    public static final int PEDIR = 0, INVOCAR = 1, GUARDAR = 2, INVOCAR_TODAS = 3, GUARDAR_TODAS = 4, LIBERTAR = 5,
            DEFINIR_ATALHO = 6, USAR_ATALHO = 7;

    public void codificar(FriendlyByteBuf b) { b.writeVarInt(acao); b.writeVarInt(numero); b.writeUtf(id, 16); }

    public static AcaoSombra decodificar(FriendlyByteBuf b) { return new AcaoSombra(b.readVarInt(), b.readVarInt(), b.readUtf(16)); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) Sombras.acao(p, acao, numero, id);
    }
}
