package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: o exército de sombras do Invocador (tela de Habilidades → Exército). */
public record SyncSombras(int vagas, int ativas, int ativasMax, List<Info> lista, List<String> atalhos) {
    /** estado: 0 = guardada, 1 = ao seu lado, 2 = se recompondo (descansa = segundos). */
    public record Info(String id, String tipo, String nome, int nivel, int abates, int forca, boolean chefe,
                       int estado, int descansa, int custo, float vida, float vidaMax) {}

    public void codificar(FriendlyByteBuf b) {
        b.writeVarInt(vagas); b.writeVarInt(ativas); b.writeVarInt(ativasMax);
        b.writeVarInt(lista.size());
        for (Info i : lista) {
            b.writeUtf(i.id(), 16); b.writeUtf(i.tipo(), 128); b.writeUtf(i.nome(), 64);
            b.writeVarInt(i.nivel()); b.writeVarInt(i.abates()); b.writeVarInt(i.forca()); b.writeBoolean(i.chefe());
            b.writeVarInt(i.estado()); b.writeVarInt(i.descansa()); b.writeVarInt(i.custo()); b.writeFloat(i.vida()); b.writeFloat(i.vidaMax());
        }
        b.writeVarInt(atalhos.size());
        for (String a : atalhos) b.writeUtf(a, 16);
    }

    public static SyncSombras decodificar(FriendlyByteBuf b) {
        int vagas = b.readVarInt(), ativas = b.readVarInt(), max = b.readVarInt();
        int n = b.readVarInt();
        List<Info> l = new ArrayList<>();
        for (int k = 0; k < n; k++)
            l.add(new Info(b.readUtf(16), b.readUtf(128), b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat()));
        int na = b.readVarInt();
        List<String> at = new ArrayList<>();
        for (int k = 0; k < na; k++) at.add(b.readUtf(16));
        return new SyncSombras(vagas, ativas, max, l, at);
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> br.com.frxcode.frxworld.cliente.ClienteSombras.aplicar(this));
    }
}
