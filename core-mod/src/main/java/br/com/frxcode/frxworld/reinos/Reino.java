package br.com.frxcode.frxworld.reinos;

import java.nio.file.Path;
import java.util.Set;

/** Capital NPC: fatias por chunk numa pasta do servidor + posição no mapa. */
public record Reino(String id, String nome, String modo, int larguraChunks, int profundidadeChunks, int altura, int yBase,
                    int chao, Set<Long> fatias, Path pasta, int centroX, int centroZ) {

    public boolean subterraneo() { return "subterraneo".equals(modo); }

    public int chunkX0() { return (centroX >> 4) - larguraChunks / 2; }
    public int chunkZ0() { return (centroZ >> 4) - profundidadeChunks / 2; }
    public int chunkX1() { return chunkX0() + larguraChunks - 1; }
    public int chunkZ1() { return chunkZ0() + profundidadeChunks - 1; }

    public boolean contemChunk(int cx, int cz) {
        return cx >= chunkX0() && cx <= chunkX1() && cz >= chunkZ0() && cz <= chunkZ1();
    }

    /** Distância em blocos (Chebyshev) de uma coluna até a caixa do reino; 0 dentro. */
    public int distancia(int x, int z) {
        int x0 = chunkX0() << 4, z0 = chunkZ0() << 4, x1 = (chunkX1() << 4) + 15, z1 = (chunkZ1() << 4) + 15;
        int dx = Math.max(0, Math.max(x0 - x, x - x1)), dz = Math.max(0, Math.max(z0 - z, z - z1));
        return Math.max(dx, dz);
    }

    static long chave(int a, int b) { return ((long) a << 32) | (b & 0xffffffffL); }
}
