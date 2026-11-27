package br.com.frxcode.frxworld.protecao;

import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Desenha retângulos no mapa do JourneyMap dos jogadores (tela cheia e minimapa).
 * O servidor só manda o pedido (pacote AbrirMenu de tipo "area"); quem desenha é o jogo do jogador, em cliente/AreasMapa,
 * pela API de cliente do JourneyMap — a mesma dos pontos do mapa, que já funciona. A API de servidor do JourneyMap
 * (ServerOverlayAPI) aceitava o pedido sem erro e não desenhava nada no 6.0.7, por isso saiu.
 */
public final class MapaJm {
    private MapaJm() {}

    private static void mandar(ServerPlayer p, CompoundTag d) {
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("area", d));
    }

    /** Mostra (ou troca) o retângulo com este id; x1 e z1 inclusive. cor = 0xRRGGBB; preenchimento de 0 a 1. */
    public static void mostrar(ServerPlayer p, String id, int x0, int z0, int x1, int z1, int cor, float preenchimento, String rotulo, String titulo) {
        CompoundTag d = new CompoundTag();
        d.putString("id", id);
        d.putInt("x0", x0); d.putInt("z0", z0); d.putInt("x1", x1); d.putInt("z1", z1);
        d.putInt("cor", cor); d.putFloat("fundo", preenchimento);
        d.putString("rotulo", rotulo == null ? "" : rotulo); d.putString("titulo", titulo == null ? "" : titulo);
        mandar(p, d);
    }

    /** Igual ao mostrar, mas em outra dimensão (ex.: "frxworld:expedicao"). */
    public static void mostrarEm(ServerPlayer p, String dim, String id, int x0, int z0, int x1, int z1, int cor, float preenchimento, String rotulo, String titulo) {
        CompoundTag d = new CompoundTag();
        d.putString("id", id);
        d.putString("dim", dim);
        d.putInt("x0", x0); d.putInt("z0", z0); d.putInt("x1", x1); d.putInt("z1", z1);
        d.putInt("cor", cor); d.putFloat("fundo", preenchimento);
        d.putString("rotulo", rotulo == null ? "" : rotulo); d.putString("titulo", titulo == null ? "" : titulo);
        mandar(p, d);
    }

    public static void remover(ServerPlayer p, String id) {
        CompoundTag d = new CompoundTag();
        d.putString("id", id);
        d.putBoolean("remover", true);
        mandar(p, d);
    }

    public static void limpar(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        d.putBoolean("limpar", true);
        mandar(p, d);
    }
}
