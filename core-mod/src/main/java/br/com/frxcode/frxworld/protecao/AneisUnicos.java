package br.com.frxcode.frxworld.protecao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Anéis sem repetição: o jogador tem 15 espaços de anel (Curios), mas não pode usar dois anéis iguais ao mesmo tempo.
 * Barra na hora de equipar (evento do Curios) e, ao entrar no jogo, devolve à mochila os repetidos que já estavam postos.
 * O Curios é usado por reflexão: sem ele, ou com outra versão da API, nada é barrado e o resto continua igual.
 */
public class AneisUnicos {
    private static final String ESPACO = "ring";
    private static final Map<UUID, Long> AVISADO = new HashMap<>();

    public AneisUnicos() {}

    /** Liga a escuta do evento de equipar do Curios. Chamado uma vez, ao carregar o mod. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void ligar() {
        try {
            Class evento = Class.forName("top.theillusivec4.curios.api.event.CurioEquipEvent");
            Consumer ouvinte = e -> aoEquipar((LivingEvent) e);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, evento, ouvinte);
        } catch (Throwable t) {
            FrxWorld.LOG.warn("Anéis: sem o Curios, a trava de anéis repetidos fica desligada ({}).", t.toString());
        }
    }

    /** Os espaços de anel do jogador (null se o Curios não responder). */
    private static IItemHandlerModifiable aneis(LivingEntity quem) {
        try {
            Object talvez = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", LivingEntity.class).invoke(null, quem);
            Object inventario = ((LazyOptional<?>) talvez).resolve().orElse(null);
            if (inventario == null) return null;
            Object grupo = ((Optional<?>) inventario.getClass().getMethod("getStacksHandler", String.class).invoke(inventario, ESPACO)).orElse(null);
            if (grupo == null) return null;
            Object pilhas = grupo.getClass().getMethod("getStacks").invoke(grupo);
            return pilhas instanceof IItemHandlerModifiable h ? h : null;
        } catch (Throwable t) { return null; }
    }

    private static void aoEquipar(LivingEvent ev) {
        try {
            if (!(ev.getEntity() instanceof Player p)) return;
            Object contexto = ev.getClass().getMethod("getSlotContext").invoke(ev);
            if (!ESPACO.equals(contexto.getClass().getMethod("identifier").invoke(contexto))) return;
            int indice = (int) contexto.getClass().getMethod("index").invoke(contexto);
            ItemStack novo = (ItemStack) ev.getClass().getMethod("getStack").invoke(ev);
            IItemHandlerModifiable postos = aneis(p);
            if (novo.isEmpty() || postos == null) return;
            for (int i = 0; i < postos.getSlots(); i++) {
                if (i == indice || postos.getStackInSlot(i).isEmpty() || postos.getStackInSlot(i).getItem() != novo.getItem()) continue;
                ev.setResult(Event.Result.DENY);
                if (p instanceof ServerPlayer sp) {
                    long agora = System.currentTimeMillis();
                    if (agora - AVISADO.getOrDefault(sp.getUUID(), 0L) > 1500) {
                        AVISADO.put(sp.getUUID(), agora);
                        sp.displayClientMessage(Component.literal("Você já está usando um " + novo.getHoverName().getString() + ". Não dá para usar dois anéis iguais.").withStyle(ChatFormatting.RED), true);
                    }
                }
                return;
            }
        } catch (Throwable t) { /* API diferente: não barra */ }
    }

    /** Quem já estava com anéis repetidos fica só com um de cada; os outros voltam para a mochila. */
    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        p.server.execute(() -> {
            IItemHandlerModifiable postos = aneis(p);
            if (postos == null) return;
            Set<net.minecraft.world.item.Item> vistos = new HashSet<>();
            int tirados = 0;
            for (int i = 0; i < postos.getSlots(); i++) {
                ItemStack s = postos.getStackInSlot(i);
                if (s.isEmpty() || vistos.add(s.getItem())) continue;
                postos.setStackInSlot(i, ItemStack.EMPTY);
                if (!p.getInventory().add(s)) p.drop(s, false);
                tirados++;
            }
            if (tirados > 0) p.sendSystemMessage(Component.literal("Não dá mais para usar dois anéis iguais: " + tirados + (tirados == 1 ? " anel repetido voltou" : " anéis repetidos voltaram") + " para a sua mochila.").withStyle(ChatFormatting.YELLOW));
        });
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) { AVISADO.remove(ev.getEntity().getUUID()); }
}
