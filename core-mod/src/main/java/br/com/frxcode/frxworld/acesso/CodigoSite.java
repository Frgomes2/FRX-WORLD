package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.Rede;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conta do site: o jogador aperta a tecla (F9) e o jogo mostra um código de 6 números, que vale por 10 minutos.
 * No site (Minha conta → Primeiro acesso) ele digita o nick, o código e escolhe a senha. O código vai ao painel
 * pela rota local /api/servidor/codigo-site; o painel guarda só o resumo dele.
 */
public final class CodigoSite {
    private static final int MINUTOS = 10;
    private static final long ESPERA_MS = 20_000;       // um código novo a cada 20 s, no máximo
    private static final SecureRandom SORTE = new SecureRandom();
    private static final Map<UUID, Long> ULTIMO = new ConcurrentHashMap<>();

    private CodigoSite() {}

    public static void abrir(ServerPlayer p) {
        long agora = System.currentTimeMillis();
        Long antes = ULTIMO.get(p.getUUID());
        CompoundTag d = new CompoundTag();
        d.putString("nick", p.getGameProfile().getName());
        d.putInt("minutos", MINUTOS);
        d.putString("site", "craft.frgomes.com.br");
        if (antes != null && agora - antes < ESPERA_MS) {
            d.putString("codigo", "");
            d.putInt("espera", (int) Math.ceil((ESPERA_MS - (agora - antes)) / 1000.0));
        } else {
            ULTIMO.put(p.getUUID(), agora);
            String codigo = String.format("%06d", SORTE.nextInt(1_000_000));
            JsonObject corpo = new JsonObject();
            corpo.addProperty("uuid", p.getUUID().toString());
            corpo.addProperty("codigo", codigo);
            corpo.addProperty("minutos", MINUTOS);
            PainelApi.enviarCodigoSite(corpo);
            d.putString("codigo", codigo);
        }
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("codigosite", d));
    }
}
