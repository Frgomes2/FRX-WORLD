package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Terrenos pelo mapa do JourneyMap: o botão direito no mapa em tela cheia ganha as opções de marcar os dois cantos do terreno
 * e de abrir "Meus terrenos". O clique só manda a posição ao servidor (AcaoMenu "terreno"); preço, limites e compra são de lá.
 * API do JourneyMap por reflexão: sem o mod, ou com outra versão, as opções não aparecem e fica o comando /terreno.
 */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MapaTerrenos {
    private static boolean feito;

    private MapaTerrenos() {}

    @SubscribeEvent
    public static void aoEntrar(ClientPlayerNetworkEvent.LoggingIn e) {
        if (feito) return;
        feito = true;
        try {
            Object evento = Class.forName("journeymap.api.v2.common.event.FullscreenEventRegistry").getField("FULLSCREEN_POPUP_MENU_EVENT").get(null);
            Method assinar = Class.forName("journeymap.api.v2.common.event.impl.Event").getMethod("subscribe", String.class, Consumer.class);
            Consumer<Object> ouvinte = MapaTerrenos::montarMenu;
            assinar.invoke(evento, FrxWorld.MOD_ID, ouvinte);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("FRX: sem menu de terrenos no JourneyMap: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
        }
    }

    private static void montarMenu(Object evento) {
        try {
            Object menu = Class.forName("journeymap.api.v2.client.event.PopupMenuEvent").getMethod("getPopupMenu").invoke(evento);
            Class<?> acaoC = Class.forName("journeymap.api.v2.client.fullscreen.ModPopupMenu$Action");
            Method item = Class.forName("journeymap.api.v2.client.fullscreen.ModPopupMenu").getMethod("addMenuItem", String.class, acaoC);
            item.invoke(menu, "Terreno: marcar o 1º canto aqui", acao(acaoC, 0));
            item.invoke(menu, "Terreno: marcar o 2º canto aqui e ver o preço", acao(acaoC, 1));
            item.invoke(menu, "Terreno: meus terrenos e permissões", acao(acaoC, 2));
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("FRX: menu de terrenos no JourneyMap falhou: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
        }
    }

    private static Object acao(Class<?> acaoC, int qual) {
        return Proxy.newProxyInstance(acaoC.getClassLoader(), new Class<?>[]{acaoC}, (proxy, metodo, args) -> {
            switch (metodo.getName()) {
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                case "toString": return "FRX terreno " + qual;
                default:
                    if (args != null && args.length == 1 && args[0] instanceof BlockPos pos) Rede.CANAL.sendToServer(new AcaoMenu("terreno", pos.getX(), qual, pos.getZ()));
                    return null;
            }
        });
    }
}
