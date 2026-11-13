package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: ação num menu de NPC (banco/forja). npc = id da entidade do NPC. */
public record AcaoMenu(String tipo, int npc, int acao, long valor) {
    // banco
    public static final int DEPOSITAR = 0, SACAR = 1, CAMBIO = 2, JUNTAR = 3;
    public static final int OURO_PRATA = 0, PRATA_OURO = 1, PRATA_COBRE = 2, COBRE_PRATA = 3;
    // forja
    public static final int FORJAR = 0, COMPRAR = 1, SEGURAR = 2;
    // comprador
    public static final int VENDER = 0, VENDER_TUDO = 1;

    public void codificar(FriendlyByteBuf b) { b.writeUtf(tipo, 16); b.writeVarInt(npc); b.writeVarInt(acao); b.writeVarLong(valor); }

    public static AcaoMenu decodificar(FriendlyByteBuf b) { return new AcaoMenu(b.readUtf(16), b.readVarInt(), b.readVarInt(), b.readVarLong()); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        if (tipo.equals("abertura")) {
            if (acao == 0) br.com.frxcode.frxworld.acesso.Abertura.acao(p, this);
            else if (acao == 2) br.com.frxcode.frxworld.acesso.Abertura.avancarPrevia(p);
            else br.com.frxcode.frxworld.acesso.Abertura.nascer(p);
        }
        else if (tipo.equals("guilda")) br.com.frxcode.frxworld.progressao.Guilda.acao(p, this);
        else br.com.frxcode.frxworld.reinos.MenusNpc.acao(p, this);
    }
}
