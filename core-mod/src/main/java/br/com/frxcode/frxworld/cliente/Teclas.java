package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.AcaoHabilidade;
import br.com.frxcode.frxworld.rede.Rede;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Teclas do FRX-WORLD (reconfiguráveis em Opções → Controles → FRX-WORLD). */
public final class Teclas {
    public static final String CATEGORIA = "key.categories.frxworld";
    public static final KeyMapping[] BARRA = {
            tecla("hab1", GLFW.GLFW_KEY_R), tecla("hab2", GLFW.GLFW_KEY_G), tecla("hab3", GLFW.GLFW_KEY_V),
            tecla("hab4", GLFW.GLFW_KEY_C), tecla("ultimate", GLFW.GLFW_KEY_X)
    };
    public static final KeyMapping MENU = tecla("habilidades", GLFW.GLFW_KEY_K);
    public static final KeyMapping PERSONAGENS = tecla("personagens", GLFW.GLFW_KEY_F8);
    /** Despertar: poder especial da classe (Invocador: Levante-se!). */
    public static final KeyMapping DESPERTAR = tecla("despertar", GLFW.GLFW_KEY_B);
    /** Exército de sombras (Invocador): 3 atalhos para uma sombra escolhida + chamar/recolher todas. */
    public static final KeyMapping[] SOMBRAS = {
            tecla("sombra1", InputConstants.UNKNOWN.getValue()), tecla("sombra2", InputConstants.UNKNOWN.getValue()),
            tecla("sombra3", InputConstants.UNKNOWN.getValue()), tecla("sombras_todas", GLFW.GLFW_KEY_Z)
    };

    /** Invocador: marca a criatura na mira como alvo das sombras (padrão: botão do meio do mouse). */
    public static final KeyMapping ALVO_SOMBRAS = new KeyMapping("key.frxworld.alvo_sombras", KeyConflictContext.IN_GAME, InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CATEGORIA);

    private Teclas() {}

    private static KeyMapping tecla(String nome, int codigo) {
        return new KeyMapping("key.frxworld." + nome, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, codigo, CATEGORIA);
    }

    /** Chamado a cada tick do cliente. */
    public static void processar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (int i = 0; i < BARRA.length; i++) {
            while (BARRA[i].consumeClick()) {
                if (mc.screen == null) Rede.CANAL.sendToServer(new AcaoHabilidade(AcaoHabilidade.USAR, i, ""));
            }
        }
        while (DESPERTAR.consumeClick()) {
            if (mc.screen == null && !ClienteHabilidades.classe.isEmpty()) Rede.CANAL.sendToServer(new AcaoHabilidade(AcaoHabilidade.DESPERTAR, 0, ""));
        }
        for (int i = 0; i < SOMBRAS.length; i++) {
            while (SOMBRAS[i].consumeClick()) {
                if (mc.screen == null && "INVOCADOR".equals(ClienteHabilidades.classe))
                    Rede.CANAL.sendToServer(new br.com.frxcode.frxworld.rede.AcaoSombra(br.com.frxcode.frxworld.rede.AcaoSombra.USAR_ATALHO, i, ""));
            }
        }
        while (ALVO_SOMBRAS.consumeClick()) {
            if (mc.screen == null && "INVOCADOR".equals(ClienteHabilidades.classe))
                Rede.CANAL.sendToServer(new br.com.frxcode.frxworld.rede.AcaoSombra(br.com.frxcode.frxworld.rede.AcaoSombra.MARCAR_ALVO, 0, ""));
        }
        while (PERSONAGENS.consumeClick()) {
            if (mc.screen == null) Rede.CANAL.sendToServer(new br.com.frxcode.frxworld.rede.AcaoMenu("personagens", 0, br.com.frxcode.frxworld.acesso.Personagens.ABRIR, 0));
        }
        while (MENU.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new TelaStatus("STATUS"));
        }
    }

    public static String rotuloDespertar() {
        String s = DESPERTAR.getTranslatedKeyMessage().getString();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    /** Rótulo curto da tecla atual (ex.: "R"). */
    public static String rotulo(int espaco) {
        String s = BARRA[espaco].getTranslatedKeyMessage().getString();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }
}
