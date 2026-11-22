package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alguns mods escrevem as frases direto no código, em inglês, sem passar pelo arquivo de idioma (ex.: The Rings of Power).
 * Aqui essas frases são trocadas pela tradução na hora de mostrar: nas dicas dos itens e nas mensagens do chat.
 * A lista fica em assets/frxworld/traducoes_fixas.json ("frase em inglês": "frase em português"); só troca frase idêntica.
 */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TraducaoFixa {
    private static Map<String, String> frases;

    private TraducaoFixa() {}

    private static Map<String, String> frases() {
        if (frases != null) return frases;
        Map<String, String> m = new HashMap<>();
        try (InputStream in = TraducaoFixa.class.getResourceAsStream("/assets/frxworld/traducoes_fixas.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception ex) {
            FrxWorld.LOG.warn("Traduções fixas não carregaram: {}", ex.toString());
        }
        frases = m;
        return m;
    }

    @SubscribeEvent
    public static void aoMostrarDica(ItemTooltipEvent ev) {
        Map<String, String> m = frases();
        if (m.isEmpty()) return;
        List<Component> linhas = ev.getToolTip();
        for (int i = 0; i < linhas.size(); i++) {
            Component c = linhas.get(i);
            String pt = m.get(c.getString().trim());
            if (pt != null) linhas.set(i, Component.literal(pt).withStyle(c.getStyle()));
        }
    }

    @SubscribeEvent
    public static void aoReceberMensagem(ClientChatReceivedEvent ev) {
        Map<String, String> m = frases();
        Component c = ev.getMessage();
        if (m.isEmpty() || c == null) return;
        String pt = m.get(c.getString().trim());
        if (pt != null) ev.setMessage(Component.literal(pt).withStyle(c.getStyle()));
    }
}
