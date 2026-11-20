package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Troca de personagem: o servidor avisa que vai desconectar o jogador para trocar; quando a tela de "desconectado"
 * apareceria, entra no lugar uma tela de espera que conecta de novo no mesmo servidor depois de 2 segundos.
 * Se algo falhar, fica a tela normal de desconectado, com o recado para entrar de novo.
 */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Reconexao {
    private static ServerData servidor;
    private static String nome = "";

    private Reconexao() {}

    /** O servidor avisou: a próxima desconexão é troca de personagem. */
    public static void marcar(String personagem) {
        servidor = Minecraft.getInstance().getCurrentServer();
        nome = personagem == null ? "" : personagem;
    }

    @SubscribeEvent
    public static void aoAbrirTela(ScreenEvent.Opening e) {
        // Propaganda do Enhanced Celestials 2 (aparece a cada entrada no mundo): não abre.
        if (e.getNewScreen() != null && e.getNewScreen().getClass().getName().endsWith("EC2AnnouncementScreen")) {
            e.setCanceled(true);
            return;
        }
        if (servidor == null || !(e.getNewScreen() instanceof DisconnectedScreen)) return;
        ServerData destino = servidor;
        servidor = null;
        e.setNewScreen(new Espera(destino, nome));
    }

    private static final class Espera extends Screen {
        private final ServerData destino;
        private final String personagem;
        private int ticks;

        Espera(ServerData destino, String personagem) {
            super(Component.literal("Trocando de personagem"));
            this.destino = destino;
            this.personagem = personagem;
        }

        @Override
        public void tick() {
            if (++ticks != 40) return;   // 2 s: tempo de o servidor guardar um personagem e pôr o outro
            Minecraft mc = Minecraft.getInstance();
            try { ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(destino.ip), destino, false); }
            catch (Throwable t) { mc.setScreen(new JoinMultiplayerScreen(new TitleScreen())); }
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fillGradient(0, 0, width, height, 0xF0100C08, 0xF0241A10);
            String linha = personagem.isEmpty() ? "Preparando o seu personagem..." : "Preparando " + personagem + "...";
            g.drawString(font, linha, (width - font.width(linha)) / 2, height / 2 - 10, 0xFFD9A441, false);
            String baixo = "O jogo entra de novo sozinho em instantes.";
            g.drawString(font, baixo, (width - font.width(baixo)) / 2, height / 2 + 6, 0xFFB9A98C, false);
            super.render(g, mx, my, pt);
        }

        @Override
        public boolean shouldCloseOnEsc() { return false; }
    }
}
