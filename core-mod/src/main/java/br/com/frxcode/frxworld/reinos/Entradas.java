package br.com.frxcode.frxworld.reinos;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lê o ponto de entrada (x,y,z relativos à caixa) de reinos subterrâneos, campo "entrada" do reino.json. */
final class Entradas {
    private static final Map<String, int[]> CACHE = new ConcurrentHashMap<>();
    private static final int[] NADA = new int[0];

    private Entradas() {}

    static int[] de(Reino r) {
        int[] v = CACHE.computeIfAbsent(r.id(), id -> {
            try {
                Path man = r.pasta().resolve("reino.json");
                JsonObject o = JsonParser.parseString(Files.readString(man, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!o.has("entrada")) return NADA;
                JsonArray a = o.getAsJsonArray("entrada");
                return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
            } catch (Exception e) {
                return NADA;
            }
        });
        return v.length == 3 ? v : null;
    }
}
