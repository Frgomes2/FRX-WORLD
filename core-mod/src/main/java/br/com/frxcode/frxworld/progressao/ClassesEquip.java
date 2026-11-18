package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classes de equipamento: cada arma e armadura pertence a um rank da Guilda (F a S) conforme a força do item,
 * inclusive os dos outros mods. Quem não tem o rank guarda o item normalmente, mas não consegue vestir nem bater com ele.
 * Armadura: defesa (ajustada pela peça) + resistência. Arma: bônus de dano do material, ou o dano do item.
 * Arcos, bestas e outras armas de longe: tabela DISTANCIA (ou a raridade, para o que não está nela).
 * Exceções item a item em config/frx_classes.txt (id=F..S ou id=livre), também por /frx classe definir.
 */
public class ClassesEquip {
    private static final List<String> LETRAS = List.of("F", "E", "D", "C", "B", "A", "S");
    private static final Map<String, Integer> excecoes = new TreeMap<>();
    private static final Map<UUID, Long> avisado = new ConcurrentHashMap<>();

    /** Armas de longe não têm material: a classe vem desta tabela (0 = F ... 6 = S). O que não está aqui vai pela raridade. */
    private static final Map<String, Integer> DISTANCIA = Map.ofEntries(
            Map.entry("minecraft:bow", 0), Map.entry("supplementaries:slingshot", 0), Map.entry("undergarden:slingshot", 0),
            Map.entry("minecraft:crossbow", 1), Map.entry("mowziesmobs:blowgun", 1),
            Map.entry("twilightforest:ender_bow", 2),
            Map.entry("twilightforest:ice_bow", 3), Map.entry("twilightforest:seeker_bow", 3), Map.entry("aether:phoenix_bow", 3),
            Map.entry("twilightforest:triple_bow", 4), Map.entry("iceandfire:dragonbone_bow", 4), Map.entry("unusualend:ancient_shortbow", 4),
            Map.entry("alexscaves:dreadbow", 5), Map.entry("cataclysm:cursed_bow", 5), Map.entry("alexscaves:raygun", 5));

    private static boolean deLonge(ItemStack s) {
        if (s.getItem() instanceof ProjectileWeaponItem) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && DISTANCIA.containsKey(id.toString());
    }

    private static Path arquivo() { return FMLPaths.CONFIGDIR.get().resolve("frx_classes.txt"); }

    private static void ler() {
        excecoes.clear();
        try {
            if (!Files.exists(arquivo())) return;
            for (String l : Files.readAllLines(arquivo(), StandardCharsets.UTF_8)) {
                String[] c = l.trim().split("=");
                if (c.length != 2 || l.startsWith("#")) continue;
                int i = LETRAS.indexOf(c[1].trim().toUpperCase(java.util.Locale.ROOT));
                excecoes.put(c[0].trim(), c[1].trim().equalsIgnoreCase("livre") ? -1 : Math.max(0, i));
            }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Classes de equipamento: não li frx_classes.txt: {}", e.toString());
        }
    }

    private static void gravar() {
        try {
            StringBuilder sb = new StringBuilder("# item=classe (F, E, D, C, B, A, S ou livre)\n");
            excecoes.forEach((k, v) -> sb.append(k).append('=').append(v < 0 ? "livre" : LETRAS.get(v)).append('\n'));
            Files.writeString(arquivo(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            FrxWorld.LOG.warn("Classes de equipamento: não gravei: {}", e.toString());
        }
    }

    private static int porLimites(double valor, double... limites) {
        for (int i = 0; i < limites.length; i++) if (valor < limites[i]) return i;
        return limites.length;
    }

    /** Rank mínimo (0 = F ... 6 = S) para usar o item, ou -1 se ele não é arma nem armadura. */
    public static int classe(ItemStack s) {
        if (s.isEmpty()) return -1;
        if (s.hasTag() && s.getTag().contains(br.com.frxcode.frxworld.armas.Codex.RANK)) return s.getTag().getInt(br.com.frxcode.frxworld.armas.Codex.RANK);   // arma do Códex: rank definido no painel
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id != null && excecoes.containsKey(id.toString())) return excecoes.get(id.toString());
        if (s.getItem() instanceof ArmorItem a) {
            double ref = switch (a.getType()) { case HELMET, BOOTS -> 3; case LEGGINGS -> 6; default -> 8; };
            double pontos = a.getDefense() * 8.0 / ref + a.getToughness();
            return Math.min(6, porLimites(pontos, 4.5, 8, 10.5, 11.5, 13.5, 17));
        }
        if (id != null && DISTANCIA.containsKey(id.toString())) return DISTANCIA.get(id.toString());
        if (s.getItem() instanceof ProjectileWeaponItem) {   // arco ou besta de mod fora da tabela: pela raridade
            return switch (s.getItem().getRarity(new ItemStack(s.getItem()))) { case COMMON -> 2; case UNCOMMON -> 3; case RARE -> 4; default -> 5; };
        }
        if (s.getItem() instanceof TridentItem) return 2;
        boolean ferramenta = s.getItem() instanceof DiggerItem && !(s.getItem() instanceof AxeItem);
        if (ferramenta) return -1;
        if (s.getItem() instanceof TieredItem t && (s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem || danoDe(s) >= 4))
            return Math.min(6, porLimites(t.getTier().getAttackDamageBonus(), 1.5, 2.5, 3.5, 4.5, 6.5, 9.5));
        double dano = danoDe(s);
        if (dano < 5) return -1;
        return Math.min(6, porLimites(dano, 6, 7, 8.5, 10, 13, 17));
    }

    private static double danoDe(ItemStack s) {
        double dano = 1;
        for (AttributeModifier m : s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) dano += m.getAmount();
        return dano;
    }

    private static boolean livre(ServerPlayer p) { return p.isCreative() || p.isSpectator(); }

    private static void avisar(ServerPlayer p, ItemStack s, int classe) {
        long agora = System.currentTimeMillis();
        if (agora - avisado.getOrDefault(p.getUUID(), 0L) < 2500) return;
        avisado.put(p.getUUID(), agora);
        p.displayClientMessage(Component.literal(s.getHoverName().getString() + " é de classe " + LETRAS.get(classe) + ": você precisa do Rank " + LETRAS.get(classe)
                + " da Guilda para usar (você é Rank " + Guilda.rank(p) + ").").withStyle(ChatFormatting.RED), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.5f, 0.6f);
    }

    /** Tira a peça do corpo e guarda na mochila ou, sem espaço, no baú de ender. Nunca derruba. false = não coube em lugar nenhum. */
    private static boolean guardar(ServerPlayer p, EquipmentSlot sl) {
        ItemStack s = p.getItemBySlot(sl);
        if (s.isEmpty()) return true;
        ItemStack copia = s.copy();
        if (p.getInventory().getFreeSlot() >= 0) {
            p.setItemSlot(sl, ItemStack.EMPTY);
            p.getInventory().add(copia);
            return true;
        }
        if (p.getEnderChestInventory().canAddItem(copia)) {
            p.setItemSlot(sl, ItemStack.EMPTY);
            p.getEnderChestInventory().addItem(copia);
            p.sendSystemMessage(Component.literal(copia.getHoverName().getString() + " foi para o seu baú de ender (mochila cheia).").withStyle(ChatFormatting.YELLOW));
            return true;
        }
        return false;
    }

    /** Na primeira entrada depois da regra das classes, toda a armadura vai para a mochila: ninguém perde peça proibida. */
    @SubscribeEvent
    public void primeiraEntrada(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        net.minecraft.nbt.CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) raiz.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.CompoundTag pers = raiz.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (pers.getBoolean("frx_classes_v1")) return;
        pers.putBoolean("frx_classes_v1", true);
        int tiradas = 0;
        for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (!p.getItemBySlot(sl).isEmpty() && guardar(p, sl)) tiradas++;
        p.sendSystemMessage(Component.literal("⚔ Novidade: armas e armaduras agora têm classe (F a S) e exigem o rank da Guilda. "
                + (tiradas > 0 ? "Sua armadura foi guardada na mochila; vista de novo o que o seu rank permite. " : "") + "A classe aparece na descrição de cada item.").withStyle(ChatFormatting.GOLD));
    }

    /** Armadura acima do rank sai do corpo e vai para a mochila (ou baú de ender); nunca cai no chão. */
    @SubscribeEvent
    public void aCadaMeioSegundo(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 10 != 3 || livre(p)) return;
        int rank = Guilda.indice(p);
        for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getItemBySlot(sl);
            int c = classe(s);
            if (c <= rank) continue;
            ItemStack fora = s.copy();
            if (!guardar(p, sl)) {   // sem espaço em lugar nenhum: fica no corpo até abrir vaga, nunca cai no chão
                long agora = System.currentTimeMillis();
                if (agora - avisado.getOrDefault(p.getUUID(), 0L) > 5000) {
                    avisado.put(p.getUUID(), agora);
                    p.displayClientMessage(Component.literal("Libere um espaço na mochila: " + fora.getHoverName().getString() + " é de classe " + LETRAS.get(c) + " e precisa sair do corpo.").withStyle(ChatFormatting.RED), true);
                }
                continue;
            }
            avisado.remove(p.getUUID());
            avisar(p, fora, c);
        }
    }

    /** Arma acima do rank não acerta ninguém. */
    @SubscribeEvent
    public void aoBater(AttackEntityEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || livre(p)) return;
        ItemStack s = p.getMainHandItem();
        int c = classe(s);
        if (c <= Guilda.indice(p)) return;
        ev.setCanceled(true);
        avisar(p, s, c);
    }

    /** Arco, besta e outras armas de longe acima do rank: não puxa nem dispara. */
    @SubscribeEvent
    public void aoUsarArmaDeLonge(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || livre(p)) return;
        ItemStack s = ev.getItemStack();
        if (!deLonge(s)) return;
        int c = classe(s);
        if (c <= Guilda.indice(p)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        p.stopUsingItem();
        p.inventoryMenu.sendAllDataToRemote();
        avisar(p, s, c);
    }

    /** Rede de segurança: se o arco chegou a ser puxado, a flecha não sai. */
    @SubscribeEvent
    public void aoSoltarFlecha(net.minecraftforge.event.entity.player.ArrowLooseEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || livre(p)) return;
        int c = classe(ev.getBow());
        if (c <= Guilda.indice(p)) return;
        ev.setCanceled(true);
        avisar(p, ev.getBow(), c);
    }

    /** Linha na descrição do item (só acontece no jogo do jogador). */
    @SubscribeEvent
    public void naDescricao(ItemTooltipEvent ev) {
        int c = classe(ev.getItemStack());
        if (c < 0) return;
        ev.getToolTip().add(Component.literal("Classe " + LETRAS.get(c) + " — exige Rank " + LETRAS.get(c) + " da Guilda").withStyle(c == 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public void aoIniciar(ServerStartedEvent ev) { ler(); }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("classe")
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack s = p.getMainHandItem();
                            int cl = classe(s);
                            c.getSource().sendSuccess(() -> Component.literal(s.isEmpty() ? "Segure o item na mão." : s.getHoverName().getString() + ": "
                                    + (cl < 0 ? "livre (sem classe)" : "classe " + LETRAS.get(cl))), false);
                            return 1;
                        })
                        .then(Commands.literal("definir").then(Commands.argument("classe", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("F", "E", "D", "C", "B", "A", "S", "livre", "automatica"), b))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
                                    if (p.getMainHandItem().isEmpty() || id == null) return 0;
                                    String v = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "classe");
                                    if (v.equalsIgnoreCase("automatica")) excecoes.remove(id.toString());
                                    else if (v.equalsIgnoreCase("livre")) excecoes.put(id.toString(), -1);
                                    else {
                                        int i = LETRAS.indexOf(v.toUpperCase(java.util.Locale.ROOT));
                                        if (i < 0) return 0;
                                        excecoes.put(id.toString(), i);
                                    }
                                    gravar();
                                    c.getSource().sendSuccess(() -> Component.literal(id + " → " + v + ". Vale no servidor agora; na descrição do item, depois de reiniciar o jogo."), true);
                                    return 1;
                                })))));
    }
}
