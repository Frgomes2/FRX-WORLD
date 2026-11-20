package br.com.frxcode.frxworld.protecao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ao morrer, guarda o que o jogador estava vestindo (armadura, mão secundária e os espaços do Curios: anéis, amuletos...).
 * Quando ele quebra o túmulo e os itens voltam para a mochila, cada peça volta sozinha para o lugar onde estava,
 * desde que o lugar esteja vazio e a peça esteja na mochila. Nada é criado: os itens só mudam de lugar.
 * O retrato fica no personagem (frx_reequipar) e vale por 2 horas; a tentativa dura 10 s depois de quebrar o túmulo.
 */
public class Reequipar {
    private static final String CHAVE = "frx_reequipar";
    private static final long VALIDADE_MS = 2 * 60 * 60 * 1000L;
    private static final EquipmentSlot[] VESTE = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    private static CompoundTag pers(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Os espaços do Curios do jogador: nome do grupo (ring, necklace...) → pilhas. Vazio se o Curios não responder. */
    @SuppressWarnings("unchecked")
    private static Map<String, IItemHandlerModifiable> curios(LivingEntity quem) {
        Map<String, IItemHandlerModifiable> saida = new LinkedHashMap<>();
        try {
            Object talvez = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", LivingEntity.class).invoke(null, quem);
            Object inventario = ((LazyOptional<?>) talvez).resolve().orElse(null);
            if (inventario == null) return saida;
            Map<String, ?> grupos = (Map<String, ?>) Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getCurios").invoke(inventario);
            java.lang.reflect.Method pilhas = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler").getMethod("getStacks");
            for (Map.Entry<String, ?> g : grupos.entrySet())
                if (pilhas.invoke(g.getValue()) instanceof IItemHandlerModifiable h) saida.put(g.getKey(), h);
        } catch (Throwable t) { /* sem Curios, ou API diferente: só a armadura volta */ }
        return saida;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoMorrer(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        ListTag lista = new ListTag();
        for (EquipmentSlot s : VESTE) {
            ItemStack item = p.getItemBySlot(s);
            if (item.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putString("veste", s.getName());
            t.put("item", item.save(new CompoundTag()));
            lista.add(t);
        }
        for (Map.Entry<String, IItemHandlerModifiable> g : curios(p).entrySet()) {
            for (int i = 0; i < g.getValue().getSlots(); i++) {
                ItemStack item = g.getValue().getStackInSlot(i);
                if (item.isEmpty()) continue;
                CompoundTag t = new CompoundTag();
                t.putString("curio", g.getKey());
                t.putInt("indice", i);
                t.put("item", item.save(new CompoundTag()));
                lista.add(t);
            }
        }
        if (lista.isEmpty()) { pers(p).remove(CHAVE); return; }
        CompoundTag d = new CompoundTag();
        d.put("pecas", lista);
        d.putLong("quando", System.currentTimeMillis());
        pers(p).put(CHAVE, d);
    }

    /** Quebrou um túmulo (o evento não foi cancelado por nenhuma proteção): por 10 s tenta devolver cada peça ao lugar. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void aoQuebrarTumulo(BlockEvent.BreakEvent ev) {
        if (!(ev.getPlayer() instanceof ServerPlayer p) || !AreasProtegidas.tumulo(ev.getState()) || !pers(p).contains(CHAVE)) return;
        CompoundTag d = pers(p).getCompound(CHAVE);
        if (System.currentTimeMillis() - d.getLong("quando") > VALIDADE_MS) { pers(p).remove(CHAVE); return; }
        d.putInt("tentar", 10);
    }

    /** Tira da mochila uma pilha igual à guardada (mesmo item e mesmos dados) e devolve; vazio se não achar. */
    private static ItemStack tirar(Inventory inv, ItemStack igual) {
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, igual)) return inv.removeItem(i, Math.min(s.getCount(), igual.getCount()));
        }
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public void aCadaSegundo(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 20 != 0 || !p.isAlive()) return;
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG) || !raiz.getCompound(Player.PERSISTED_NBT_TAG).contains(CHAVE)) return;
        CompoundTag d = pers(p).getCompound(CHAVE);
        int resta = d.getInt("tentar");
        if (resta <= 0) return;
        ListTag lista = d.getList("pecas", Tag.TAG_COMPOUND), sobra = new ListTag();
        Map<String, IItemHandlerModifiable> curios = null;
        int voltaram = 0;
        try {
            for (int n = 0; n < lista.size(); n++) {
                CompoundTag t = lista.getCompound(n);
                ItemStack igual = ItemStack.of(t.getCompound("item"));
                if (igual.isEmpty()) continue;
                if (t.contains("veste")) {
                    EquipmentSlot lugar = EquipmentSlot.byName(t.getString("veste"));
                    if (!p.getItemBySlot(lugar).isEmpty()) continue;            // já tem algo ali (ou o túmulo já devolveu no lugar)
                    ItemStack peca = tirar(p.getInventory(), igual);
                    if (peca.isEmpty()) { sobra.add(t); continue; }
                    p.setItemSlot(lugar, peca);
                    voltaram++;
                } else {
                    if (curios == null) curios = curios(p);
                    IItemHandlerModifiable h = curios.get(t.getString("curio"));
                    int i = t.getInt("indice");
                    if (h == null || i >= h.getSlots() || !h.getStackInSlot(i).isEmpty()) continue;
                    ItemStack peca = tirar(p.getInventory(), igual);
                    if (peca.isEmpty()) { sobra.add(t); continue; }
                    h.setStackInSlot(i, peca);
                    voltaram++;
                }
            }
        } catch (Throwable t) {
            FrxWorld.LOG.warn("Reequipar: falhou para {}: {}", p.getGameProfile().getName(), t.toString());
            pers(p).remove(CHAVE);
            return;
        }
        if (voltaram > 0) {
            p.inventoryMenu.broadcastChanges();
            p.displayClientMessage(Component.literal(voltaram == 1 ? "1 peça voltou para o lugar onde estava." : voltaram + " peças voltaram para o lugar onde estavam.").withStyle(ChatFormatting.GREEN), true);
        }
        if (sobra.isEmpty() || resta <= 1) pers(p).remove(CHAVE);
        else { d.put("pecas", sobra); d.putInt("tentar", resta - 1); }
    }
}
