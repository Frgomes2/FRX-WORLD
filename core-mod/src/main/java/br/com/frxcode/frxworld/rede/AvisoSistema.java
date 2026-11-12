package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: aviso na janela de "Sistema" (caixa no topo da tela). */
public record AvisoSistema(String titulo, String texto, int cor) {

    public void codificar(FriendlyByteBuf b) { b.writeUtf(titulo, 80); b.writeUtf(texto, 240); b.writeVarInt(cor); }

    public static AvisoSistema decodificar(FriendlyByteBuf b) { return new AvisoSistema(b.readUtf(80), b.readUtf(240), b.readVarInt()); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.HudSistema.adicionar(this));
    }
}
