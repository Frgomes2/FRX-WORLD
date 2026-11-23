package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.habilidades.Habilidades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: usar a habilidade de um espaço da barra, escolher classe ou mudar a barra. */
public record AcaoHabilidade(int acao, int numero, String texto) {
    public static final int USAR = 0, CLASSE = 1, DEFINIR_ESPACO = 2, DESPERTAR = 3, NOME_CLA = 4, PERSONAGEM = 5, FUNDAR_CLA = 6, BORDAR_NOME = 7, PVP = 8;

    public void codificar(FriendlyByteBuf b) { b.writeVarInt(acao); b.writeVarInt(numero); b.writeUtf(texto, 64); }

    public static AcaoHabilidade decodificar(FriendlyByteBuf b) { return new AcaoHabilidade(b.readVarInt(), b.readVarInt(), b.readUtf(64)); }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        switch (acao) {
            case USAR -> Habilidades.usar(p, numero);
            case CLASSE -> Habilidades.escolherClasse(p, texto, false);
            case DEFINIR_ESPACO -> Habilidades.definirEspaco(p, numero, texto);
            case PVP -> Habilidades.alternarPvp(p);
            case DESPERTAR -> br.com.frxcode.frxworld.habilidades.Despertar.acionar(p);
            case NOME_CLA -> br.com.frxcode.frxworld.clas.Clas.renomear(p, texto);
            case FUNDAR_CLA -> br.com.frxcode.frxworld.clas.Clas.fundar(p, texto);
            case BORDAR_NOME -> br.com.frxcode.frxworld.reinos.Costureira.bordarNome(p, numero, texto);
            case PERSONAGEM -> br.com.frxcode.frxworld.acesso.Personagens.nomear(p, numero, texto);
            default -> { }
        }
    }
}
