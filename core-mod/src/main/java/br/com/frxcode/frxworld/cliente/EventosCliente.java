package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos que só existem no cliente. */
public final class EventosCliente {
    private EventosCliente() {}

    @Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod_ {
        @SubscribeEvent
        public static void registrarHud(RegisterGuiOverlaysEvent evento) {
            evento.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "nivel", new HudNivel());
            evento.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "habilidades", new HudHabilidades());
            evento.registerAboveAll("chefe", new HudChefe());
            evento.registerAboveAll("sistema", new HudSistema());
        }

        /** Traços de raça (orelhas, chifres, cauda...) nos dois modelos de jogador. */
        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void camadas(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers evento) {
            for (String skin : evento.getSkins()) {
                var r = evento.getSkin(skin);
                if (r instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) pr.addLayer(new CamadaRaca(pr));
            }
        }

        @SubscribeEvent
        public static void registrarTeclas(net.minecraftforge.client.event.RegisterKeyMappingsEvent evento) {
            for (var t : Teclas.BARRA) evento.register(t);
            evento.register(Teclas.MENU);
            evento.register(Teclas.PERSONAGENS);
            evento.register(Teclas.DESPERTAR);
            for (var t : Teclas.SOMBRAS) evento.register(t);
            evento.register(Teclas.ALVO_SOMBRAS);
        }
    }

    @Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Forge_ {
        /** Nível antes do nome (acima da cabeça) para os outros jogadores. */
        @SubscribeEvent
        public static void nome(PlayerEvent.NameFormat evento) {
            if (!evento.getEntity().level().isClientSide) return;
            String rotulo = ClienteDados.rotuloDe(evento.getEntity().getUUID());
            ClienteRacas.Visivel raca = ClienteRacas.de(evento.getEntity().getUUID());
            Component nome = evento.getDisplayname();
            if (raca != null) nome = nome.copy().append(Component.literal(" · " + raca.texto()).withStyle(ChatFormatting.GRAY));
            if (rotulo == null) { if (raca != null) evento.setDisplayname(nome); return; }
            evento.setDisplayname(Component.literal("[" + rotulo + "] ").withStyle(ChatFormatting.GOLD).append(nome));
        }

        /** Inventário (E): abas da Janela de Status no topo. */
        @SubscribeEvent
        public static void telaInit(net.minecraftforge.client.event.ScreenEvent.Init.Post evento) {
            if (!(evento.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen inv)) return;
            // abas com ícone coladas no topo do inventário (8 × 22 px = largura do inventário); acompanham o livro de receitas
            TelaStatus.criarAbas(evento::addListener, inv::getGuiLeft, () -> inv.getGuiTop() + 1, "INVENTARIO", false);
        }

        @SubscribeEvent
        public static void aoSair(ClientPlayerNetworkEvent.LoggingOut evento) {
            ClienteDados.limpar();
            ClienteHabilidades.limpar();
            ClienteStatus.limpar();
            ClienteSombras.limpar();
            ClienteRacas.limpar();
            HudChefe.limpar();
        }

        @SubscribeEvent
        public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent evento) {
            if (evento.phase == net.minecraftforge.event.TickEvent.Phase.END) { Teclas.processar(); ClienteStatus.tick(); ClienteRacas.tick(); HudChefe.tick(); }
        }
    }
}
