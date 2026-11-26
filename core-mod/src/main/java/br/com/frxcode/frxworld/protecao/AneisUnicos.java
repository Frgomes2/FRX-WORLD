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
 * Anéis sem repetição: não dá para usar dois anéis iguais ao mesmo tempo.
 * Espaços de anel pelo rank da Guilda (F 2, E 4, D 7, C 11, B 15, A 20, S 27): no rank S cabem os 27 anéis do
 * Rings of Ascension. O Curios define 27 (kubejs/data/frxworld/curios/slots/ring.json) e cada jogador recebe um
 * modificador permanente negativo (27 − espaços do rank); ao diminuir, os anéis que sobram voltam para a mochila.
 * Barra na hora de equipar (evento do Curios) e, ao entrar no jogo, devolve à mochila os repetidos que já estavam postos.
 * O Curios é usado por reflexão: sem ele, ou com outra versão da API, nada é barrado e o resto continua igual.
 */
public class AneisUnicos {
    private static final String ESPACO = "ring";
    /** Espaços de anel por rank (F, E, D, C, B, A, S). O último precisa ser igual ao total do ring.json. */
    private static final int[] ESPACOS_POR_RANK = {2, 4, 7, 11, 15, 20, 27};
    private static final int TOTAL = 27;
    private static final UUID MOD_RANK = UUID.nameUUIDFromBytes("frxworld:aneis_por_rank".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static final Map<UUID, Integer> APLICADO = new HashMap<>();

    private static void atualizar(Object inv) {
        try {
            Object grupo = ((Optional<?>) inv.getClass().getMethod("getStacksHandler", String.class).invoke(inv, ESPACO)).orElse(null);
            if (grupo != null) grupo.getClass().getMethod("update").invoke(grupo);
        } catch (Throwable t) { /* o Curios atualiza sozinho */ }
    }

    /**
     * Anel de Vida pelo rank: o mod original dá corações fixos (ringHealthHearts, zerado no config); aqui o anel dá
     * mais corações quanto maior o rank da Guilda de quem usa. F 2, E 3, D 4, C 5, B 6, A 8, S 10.
     */
    private static final int[] CORACOES_POR_RANK = {2, 3, 4, 5, 6, 8, 10};
    private static final net.minecraft.resources.ResourceLocation VIDA = new net.minecraft.resources.ResourceLocation("ringsofascension", "ring_health");
    private static final UUID MOD_VIDA = UUID.nameUUIDFromBytes("frxworld:anel_vida_rank".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static void vidaDoAnel(ServerPlayer p) {
        IItemHandlerModifiable postos = aneis(p);
        boolean usa = false;
        if (postos != null) for (int i = 0; i < postos.getSlots() && !usa; i++)
            usa = VIDA.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(postos.getStackInSlot(i).getItem()));
        var at = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (at == null) return;
        int i = br.com.frxcode.frxworld.progressao.Guilda.indice(p);
        double quer = usa ? CORACOES_POR_RANK[Math.max(0, Math.min(CORACOES_POR_RANK.length - 1, i))] * 2.0 : 0;
        var atual = at.getModifier(MOD_VIDA);
        if (atual == null && quer == 0) return;
        if (atual != null && atual.getAmount() == quer) return;
        at.removeModifier(MOD_VIDA);
        if (quer > 0) at.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(MOD_VIDA, "FRX anel de vida", quer, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** Quantos anéis o jogador pode usar agora (admin no criativo: todos). */
    public static int espacosDe(Player p) {
        if (p.isCreative() && p.hasPermissions(2)) return TOTAL;
        int i = br.com.frxcode.frxworld.progressao.Guilda.indice(p);
        return ESPACOS_POR_RANK[Math.max(0, Math.min(ESPACOS_POR_RANK.length - 1, i))];
    }

    /** O inventário do Curios do jogador (ICuriosItemHandler), ou null. */
    private static Object inventarioCurios(LivingEntity quem) {
        try {
            Object talvez = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", LivingEntity.class).invoke(null, quem);
            return ((LazyOptional<?>) talvez).resolve().orElse(null);
        } catch (Throwable t) { return null; }
    }

    /**
     * Ajusta os espaços de anel ao rank. Ao diminuir, primeiro devolve à mochila os anéis dos espaços que vão sumir
     * (assim o Curios nunca joga nada no chão). Retorna true se mudou.
     */
    private static boolean ajustarEspacos(ServerPlayer p, boolean avisar) {
        Object inv = inventarioCurios(p);
        IItemHandlerModifiable postos = aneis(p);
        if (inv == null || postos == null) return false;
        int quer = espacosDe(p), tem = postos.getSlots();
        if (quer == tem) { APLICADO.put(p.getUUID(), quer); return false; }
        if (Integer.valueOf(quer).equals(APLICADO.get(p.getUUID()))) { atualizar(inv); return false; }   // já pedido: o Curios aplica no próximo tick
        int devolvidos = 0;
        if (quer < tem) {
            // junta os anéis nos primeiros espaços e devolve o que não couber
            java.util.List<ItemStack> todos = new java.util.ArrayList<>();
            for (int i = 0; i < tem; i++) { ItemStack s = postos.getStackInSlot(i); if (!s.isEmpty()) todos.add(s); postos.setStackInSlot(i, ItemStack.EMPTY); }
            for (int i = 0; i < todos.size(); i++) {
                if (i < quer) postos.setStackInSlot(i, todos.get(i));
                else { if (!p.getInventory().add(todos.get(i))) p.drop(todos.get(i), false); devolvidos++; }
            }
        }
        try {
            inv.getClass().getMethod("removeSlotModifier", String.class, UUID.class).invoke(inv, ESPACO, MOD_RANK);
            if (quer != TOTAL)
                inv.getClass().getMethod("addPermanentSlotModifier", String.class, UUID.class, String.class, double.class, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.class)
                        .invoke(inv, ESPACO, MOD_RANK, "FRX anéis por rank", (double) (quer - TOTAL), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        } catch (Throwable t) {
            FrxWorld.LOG.warn("Anéis: não consegui ajustar os espaços de {} ({}).", p.getGameProfile().getName(), t.toString());
            return false;
        }
        APLICADO.put(p.getUUID(), quer);
        atualizar(inv);
        if (avisar || devolvidos > 0) {
            String rank = br.com.frxcode.frxworld.progressao.Guilda.rank(p);
            p.sendSystemMessage(Component.literal("💍 Rank " + rank + ": você pode usar " + quer + (quer == 1 ? " anel" : " anéis") + " ao mesmo tempo."
                    + (devolvidos > 0 ? " " + devolvidos + (devolvidos == 1 ? " anel voltou" : " anéis voltaram") + " para a sua mochila." : "")
                    + (quer < TOTAL ? " Subir de rank na Guilda libera mais." : "")).withStyle(quer > tem ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
        return true;
    }
    /** Anel de Voo (Rings of Ascension): voa como no criativo. Só a partir do rank B da Guilda. */
    private static final net.minecraft.resources.ResourceLocation VOO = new net.minecraft.resources.ResourceLocation("ringsofascension", "ring_flight");
    private static int relogio;

    private static int rankB() {
        for (int i = 0; i < br.com.frxcode.frxworld.progressao.ConfigJogo.totalRanks(); i++)
            if (br.com.frxcode.frxworld.progressao.ConfigJogo.nomeRank(i).equalsIgnoreCase("B")) return i;
        return 4;
    }

    static boolean ehVoo(ItemStack s) { return !s.isEmpty() && VOO.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem())); }

    private static boolean semRankParaVoar(Player p) {
        return !p.isCreative() && !p.isSpectator() && br.com.frxcode.frxworld.progressao.Guilda.indice(p) < rankB();
    }

    private static void avisarVoo(ServerPlayer sp) {
        long agora = System.currentTimeMillis();
        if (agora - AVISADO.getOrDefault(sp.getUUID(), 0L) < 1500) return;
        AVISADO.put(sp.getUUID(), agora);
        sp.displayClientMessage(Component.literal("O Anel de Voo só funciona a partir do rank B da Guilda. O seu é "
                + br.com.frxcode.frxworld.progressao.Guilda.rank(sp) + ".").withStyle(ChatFormatting.RED), true);
    }
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
    public static IItemHandlerModifiable aneis(LivingEntity quem) {
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
            if (ehVoo(novo) && semRankParaVoar(p)) {
                ev.setResult(Event.Result.DENY);
                if (p instanceof ServerPlayer sp) avisarVoo(sp);
                return;
            }
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
            ajustarEspacos(p, false);
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

    /** Quem já estava com o Anel de Voo posto e não tem rank B: o anel volta para a mochila e o voo acaba (a cada 2 s). */
    @SubscribeEvent
    public void conferirVoo(net.minecraftforge.event.TickEvent.ServerTickEvent ev) {
        if (ev.phase != net.minecraftforge.event.TickEvent.Phase.END || ++relogio % 40 != 0) return;
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) if (p.tickCount > 60) { ajustarEspacos(p, true); vidaDoAnel(p); }
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) {
            if (!semRankParaVoar(p)) continue;
            IItemHandlerModifiable postos = aneis(p);
            if (postos == null) continue;
            boolean tirou = false;
            for (int i = 0; i < postos.getSlots(); i++) {
                ItemStack s = postos.getStackInSlot(i);
                if (!ehVoo(s)) continue;
                postos.setStackInSlot(i, ItemStack.EMPTY);
                if (!p.getInventory().add(s)) p.drop(s, false);
                tirou = true;
            }
            if (!tirou) continue;
            p.getAbilities().flying = false;
            p.getAbilities().mayfly = false;
            p.onUpdateAbilities();
            p.fallDistance = 0;
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 200, 0, false, false));
            p.sendSystemMessage(Component.literal("O Anel de Voo voltou para a sua mochila: ele só funciona a partir do rank B da Guilda (o seu é "
                    + br.com.frxcode.frxworld.progressao.Guilda.rank(p) + ").").withStyle(ChatFormatting.YELLOW));
        }
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) { AVISADO.remove(ev.getEntity().getUUID()); APLICADO.remove(ev.getEntity().getUUID()); }
}
