package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Áreas com nível fixo: dentro delas os monstros nascem naquele nível, qualquer que seja a distância do spawn.
 * Serve para uma masmorra difícil poder ficar embaixo da cidade inicial. Registro por mundo em frx_areas.txt:
 * "id;minX;minY;minZ;maxX;maxY;maxZ;nivel".
 */
public class AreasNivel {
    private record Area(BoundingBox caixa, int nivel) {}
    private static volatile Map<String, Area> AREAS = new LinkedHashMap<>();

    public AreasNivel() {}

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_areas.txt"); }

    /** Nível fixo no ponto, ou 0 se o ponto não está em nenhuma área. */
    public static int em(BlockPos p) {
        for (Area a : AREAS.values()) if (a.caixa().isInside(p)) return a.nivel();
        return 0;
    }

    public static void definir(MinecraftServer s, String id, BoundingBox caixa, int nivel) {
        Map<String, Area> novo = new LinkedHashMap<>(AREAS);
        if (nivel > 0) novo.put(id, new Area(caixa, nivel)); else novo.remove(id);
        AREAS = novo;
        StringBuilder sb = new StringBuilder();
        novo.forEach((k, a) -> sb.append(k).append(';').append(a.caixa().minX()).append(';').append(a.caixa().minY()).append(';').append(a.caixa().minZ()).append(';')
                .append(a.caixa().maxX()).append(';').append(a.caixa().maxY()).append(';').append(a.caixa().maxZ()).append(';').append(a.nivel()).append('\n'));
        try { Files.writeString(arquivo(s), sb.toString(), StandardCharsets.UTF_8); } catch (Exception ignorado) { }
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        Map<String, Area> novo = new LinkedHashMap<>();
        try {
            Path a = arquivo(ev.getServer());
            if (Files.exists(a))
                for (String l : Files.readAllLines(a, StandardCharsets.UTF_8)) {
                    String[] p = l.trim().split(";");
                    if (p.length < 8) continue;
                    novo.put(p[0], new Area(new BoundingBox(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                            Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6])), Integer.parseInt(p[7])));
                }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Áreas de nível: não li frx_areas.txt: {}", e.toString());
        }
        AREAS = novo;
    }
}
