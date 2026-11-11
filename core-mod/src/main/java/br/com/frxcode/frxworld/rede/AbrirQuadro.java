package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.cliente.ClienteMissoes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: abre (ou atualiza) a tela do Quadro de Missões. */
public record AbrirQuadro(List<VisaoMissao> missoes, String mensagem) {

    public void codificar(FriendlyByteBuf b) {
        b.writeCollection(missoes, (buf, v) -> v.escrever(buf));
        b.writeUtf(mensagem);
    }

    public static AbrirQuadro decodificar(FriendlyByteBuf b) {
        return new AbrirQuadro(b.readList(VisaoMissao::ler), b.readUtf());
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClienteMissoes.abrirQuadro(this));
    }
}
