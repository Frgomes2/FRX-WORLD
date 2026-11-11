package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.PedirStatus;
import br.com.frxcode.frxworld.rede.Rede;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

/** Dados da janela de status (tecla E) recebidos do servidor. */
public final class ClienteStatus {
    public static JsonObject dados = new JsonObject();
    public static boolean recebido = false;
    private static int pedirEm = -1;

    private ClienteStatus() {}

    public static void receber(String json) {
        try {
            dados = JsonParser.parseString(json).getAsJsonObject();
            recebido = true;
        } catch (Exception e) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TelaStatus t) t.atualizar();
    }

    public static void pedir() {
        Rede.CANAL.sendToServer(new PedirStatus());
    }

    /** Executa um comando do jogador (ex.: "talentos aprender x") e atualiza a janela logo depois. */
    public static void comando(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.sendCommand(cmd);
        pedirEm = 6;
    }

    /** Chamado a cada tick do cliente. */
    public static void tick() {
        if (pedirEm > 0 && --pedirEm == 0) { pedirEm = -1; pedir(); }
    }

    public static void limpar() {
        dados = new JsonObject();
        recebido = false;
    }
}
