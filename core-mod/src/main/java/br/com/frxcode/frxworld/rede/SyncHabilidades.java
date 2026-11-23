package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.cliente.ClienteHabilidades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: classe, habilidades da classe (aprendidas ou não) e a barra de combate. */
public record SyncHabilidades(String classe, String recurso, List<Info> habilidades, List<String> barra, int extras, boolean pvp) {

    public record Info(String codigo, String nome, String descricao, String tipo, String elemento, String icone,
                       int custo, float recarga, int nivelReq, boolean ultimate, boolean porItem,
                       boolean aprendida, int nivel, int usos) {}

    public void codificar(FriendlyByteBuf b) {
        b.writeUtf(classe);
        b.writeUtf(recurso);
        b.writeVarInt(habilidades.size());
        for (Info i : habilidades) {
            b.writeUtf(i.codigo()); b.writeUtf(i.nome()); b.writeUtf(i.descricao()); b.writeUtf(i.tipo()); b.writeUtf(i.elemento());
            b.writeUtf(i.icone()); b.writeVarInt(i.custo()); b.writeFloat(i.recarga()); b.writeVarInt(i.nivelReq());
            b.writeBoolean(i.ultimate()); b.writeBoolean(i.porItem()); b.writeBoolean(i.aprendida()); b.writeVarInt(i.nivel()); b.writeVarInt(i.usos());
        }
        b.writeVarInt(barra.size());
        for (String s : barra) b.writeUtf(s);
        b.writeVarInt(extras);
        b.writeBoolean(pvp);
    }

    public static SyncHabilidades decodificar(FriendlyByteBuf b) {
        String classe = b.readUtf(), recurso = b.readUtf();
        int n = b.readVarInt();
        List<Info> lista = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            lista.add(new Info(b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readVarInt(),
                    b.readFloat(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt()));
        }
        int m = b.readVarInt();
        List<String> barra = new ArrayList<>(m);
        for (int k = 0; k < m; k++) barra.add(b.readUtf());
        int extras = b.readVarInt();
        return new SyncHabilidades(classe, recurso, lista, barra, extras, b.readBoolean());
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClienteHabilidades.aplicar(this));
    }
}
