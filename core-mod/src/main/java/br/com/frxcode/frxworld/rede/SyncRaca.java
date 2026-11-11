package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: a raça do próprio jogador, as liberadas e o custo da próxima troca. abrir = abre a tela. */
public record SyncRaca(String principal, String secundaria, boolean escolheu, int trocas, long custo, boolean revelar,
                       List<String> liberadas, boolean abrir) {
    public void codificar(FriendlyByteBuf b) {
        b.writeUtf(principal, 32); b.writeUtf(secundaria, 32); b.writeBoolean(escolheu); b.writeVarInt(trocas); b.writeVarLong(custo);
        b.writeBoolean(revelar); b.writeVarInt(liberadas.size());
        for (String s : liberadas) b.writeUtf(s, 32);
        b.writeBoolean(abrir);
    }

    public static SyncRaca decodificar(FriendlyByteBuf b) {
        String p = b.readUtf(32), s = b.readUtf(32);
        boolean esc = b.readBoolean();
        int t = b.readVarInt();
        long c = b.readVarLong();
        boolean rev = b.readBoolean();
        int n = b.readVarInt();
        List<String> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(b.readUtf(32));
        return new SyncRaca(p, s, esc, t, c, rev, l, b.readBoolean());
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.ClienteRacas.aplicar(this));
    }
}
