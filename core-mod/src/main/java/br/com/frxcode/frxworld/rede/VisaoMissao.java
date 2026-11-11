package br.com.frxcode.frxworld.rede;

import net.minecraft.network.FriendlyByteBuf;

/** Como uma missão aparece no quadro para um jogador específico. */
public record VisaoMissao(String id, String titulo, String descricao, String objetivo, int nivelMin,
                          String recompensa, int estado, int progresso, int quantidade, String nota) {

    public static final int PRONTA = 0, ATIVA = 1, DISPONIVEL = 2, BLOQUEADA = 3, CONCLUIDA = 4;

    public void escrever(FriendlyByteBuf b) {
        b.writeUtf(id); b.writeUtf(titulo); b.writeUtf(descricao); b.writeUtf(objetivo);
        b.writeVarInt(nivelMin); b.writeUtf(recompensa); b.writeVarInt(estado);
        b.writeVarInt(progresso); b.writeVarInt(quantidade); b.writeUtf(nota);
    }

    public static VisaoMissao ler(FriendlyByteBuf b) {
        return new VisaoMissao(b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readVarInt(),
                b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf());
    }
}
