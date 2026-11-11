package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Senhas das contas para servidor sem autenticação da Mojang (online-mode=false: TLauncher, Shiginima...).
 * Guardadas por nick em frxworld/senhas.json (fora do mundo: sobrevivem a um mundo novo), só o hash
 * PBKDF2-SHA256 com sal aleatório, nunca a senha.
 */
public final class SenhasContas {
    private static final int ITERACOES = 120_000;
    private static JsonObject cache;

    private SenhasContas() {}

    private static Path arquivo() { return FMLPaths.GAMEDIR.get().resolve("frxworld").resolve("senhas.json"); }

    private static synchronized JsonObject dados() {
        if (cache != null) return cache;
        try {
            Path p = arquivo();
            cache = Files.exists(p) ? JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            FrxWorld.LOG.error("Não li senhas.json", e);
            cache = new JsonObject();
        }
        return cache;
    }

    private static synchronized void salvar() {
        try {
            Path p = arquivo();
            Files.createDirectories(p.getParent());
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(p, g.toJson(dados()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            FrxWorld.LOG.error("Não salvei senhas.json", e);
        }
    }

    private static String chave(String nick) { return nick.toLowerCase(Locale.ROOT); }

    private static byte[] hash(String senha, byte[] sal) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, ITERACOES, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    public static synchronized boolean tem(String nick) { return dados().has(chave(nick)); }

    public static synchronized void definir(String nick, String senha) {
        try {
            byte[] sal = new byte[16];
            new SecureRandom().nextBytes(sal);
            JsonObject o = new JsonObject();
            o.addProperty("sal", Base64.getEncoder().encodeToString(sal));
            o.addProperty("hash", Base64.getEncoder().encodeToString(hash(senha, sal)));
            dados().add(chave(nick), o);
            salvar();
        } catch (Exception e) {
            FrxWorld.LOG.error("Falha ao gerar a senha", e);
        }
    }

    public static synchronized boolean confere(String nick, String senha) {
        try {
            JsonObject o = dados().getAsJsonObject(chave(nick));
            if (o == null) return false;
            byte[] sal = Base64.getDecoder().decode(o.get("sal").getAsString());
            byte[] esperado = Base64.getDecoder().decode(o.get("hash").getAsString());
            return MessageDigest.isEqual(esperado, hash(senha, sal));
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized boolean apagar(String nick) {
        boolean tinha = dados().remove(chave(nick)) != null;
        if (tinha) salvar();
        return tinha;
    }
}
