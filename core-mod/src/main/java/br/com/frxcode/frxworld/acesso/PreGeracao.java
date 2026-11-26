package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Pré-geração do mapa (Chunky) só com o servidor vazio: quando alguém entra, a geração pausa na hora para não
 * travar o jogo; quando o último sai e o servidor fica 2 minutos vazio, ela continua de onde parou.
 * Sem tarefa do Chunky, os comandos só deixam uma linha no log ("No tasks...").
 */
public class PreGeracao {
    private static final int VAZIO_PARA_CONTINUAR = 20 * 120;
    private static boolean pausado;
    private static int vazio;
    private static int relogio;

    private static void rodar(MinecraftServer s, String cmd) {
        try { s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), cmd); }
        catch (Exception e) { FrxWorld.LOG.warn("Pré-geração: {} falhou: {}", cmd, e.toString()); }
    }

    private static void pausar(MinecraftServer s) {
        if (pausado) return;
        pausado = true;
        FrxWorld.LOG.info("[pregeracao] jogador online: pausando o Chunky");
        rodar(s, "chunky pause");
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity().getServer() != null) pausar(ev.getEntity().getServer());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++relogio % 100 != 0) return;
        MinecraftServer s = ev.getServer();
        if (s.getPlayerCount() > 0) { vazio = 0; pausar(s); return; }
        vazio += 100;
        if (pausado && vazio >= VAZIO_PARA_CONTINUAR) {
            pausado = false;
            FrxWorld.LOG.info("[pregeracao] servidor vazio: continuando o Chunky");
            rodar(s, "chunky continue");
        }
    }
}
