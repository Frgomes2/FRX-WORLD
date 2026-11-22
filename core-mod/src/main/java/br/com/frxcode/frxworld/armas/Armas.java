package br.com.frxcode.frxworld.armas;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Armas e forja: raridade (Comum → Lendário) e aprimoramento (+1 a +10) guardados no NBT do item
 * ("frx_raridade", "frx_nivel"). Os bônus entram como modificadores de atributo calculados na hora.
 */
public class Armas {
    public static final String RARIDADE = "frx_raridade", NIVEL = "frx_nivel";
    public static final int NIVEL_MAX = 10;
    public static final double DANO_POR_NIVEL = 0.06, ARMADURA_POR_NIVEL = 0.3;

    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> GLM =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, FrxWorld.MOD_ID);

    static { GLM.register("raridade", RaridadeLoot.CODEC); }

    public static void registrar(IEventBus modBus) { GLM.register(modBus); }

    // ------------------------------------------------------------------ dados do item

    public static boolean forjavel(ItemStack s) {
        if (s.isEmpty() || s.getMaxStackSize() != 1 || s.getMaxDamage() <= 0) return false;
        if (s.getItem() instanceof ArmorItem || s.getItem() instanceof ProjectileWeaponItem || s.getItem() instanceof ShieldItem) return true;
        return !s.getItem().getDefaultAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty();
    }

    public static int raridade(ItemStack s) { return s.hasTag() ? s.getTag().getInt(RARIDADE) : 0; }
    public static int nivel(ItemStack s) { return s.hasTag() ? s.getTag().getInt(NIVEL) : 0; }
    public static void definirRaridade(ItemStack s, int r) { s.getOrCreateTag().putInt(RARIDADE, Math.max(0, Math.min(4, r))); atualizarDescricao(s); }
    public static void definirNivel(ItemStack s, int n) { s.getOrCreateTag().putInt(NIVEL, Math.max(0, Math.min(NIVEL_MAX, n))); atualizarDescricao(s); }

    /** Linha de descrição (Lore) gravada no próprio item: aparece para todos, sem depender do cliente. */
    public static void atualizarDescricao(ItemStack s) {
        if (s.isEmpty()) return;
        net.minecraft.nbt.CompoundTag disp = s.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag lore = disp.getList("Lore", net.minecraft.nbt.Tag.TAG_STRING);
        if (s.getTag().getBoolean("frx_lore") && !lore.isEmpty()) lore.remove(0);
        int r = raridade(s), n = nivel(s);
        Raridade rar = Raridade.de(r);
        StringBuilder t = new StringBuilder("★ ").append(rar.nome);
        if (n > 0) t.append(" · Aprimoramento +").append(n);
        if (s.getItem() instanceof ArmorItem) {
            double arm = rar.armadura + ARMADURA_POR_NIVEL * n;
            if (arm > 0) t.append(" · +").append(String.format(java.util.Locale.ROOT, "%.1f", arm)).append(" de armadura");
        } else {
            int pct = (int) Math.round(bonusDano(s) * 100);
            if (pct > 0) t.append(" · +").append(pct).append("% de dano");
        }
        Component linha = Component.literal(t.toString()).withStyle(st -> st.withColor(rar.cor).withItalic(false));
        lore.add(0, net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(linha)));
        disp.put("Lore", lore);
        s.getTag().putBoolean("frx_lore", true);
    }

    /** Pesos Comum..Lendário. */
    public static final int[] BAU = {40, 30, 18, 9, 3}, MOB = {55, 28, 12, 4, 1}, CRAFT = {70, 22, 7, 1, 0};

    public static int sortear(int[] pesos, RandomSource rnd) {
        int total = 0;
        for (int p : pesos) total += p;
        int x = rnd.nextInt(total);
        for (int i = 0; i < pesos.length; i++) { x -= pesos[i]; if (x < 0) return i; }
        return 0;
    }

    public static double bonusDano(ItemStack s) {
        return Raridade.de(raridade(s)).dano + DANO_POR_NIVEL * nivel(s);
    }

    private static UUID id(String nome) { return UUID.nameUUIDFromBytes(("frxworld:" + nome).getBytes(StandardCharsets.UTF_8)); }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void atributos(ItemAttributeModifierEvent e) {
        ItemStack s = e.getItemStack();
        if (!s.hasTag()) return;
        int r = raridade(s), n = nivel(s);
        if (r == 0 && n == 0) return;
        Raridade rar = Raridade.de(r);
        EquipmentSlot slot = e.getSlotType();
        if (slot == EquipmentSlot.MAINHAND) {
            double base = 0;
            for (AttributeModifier m : e.getOriginalModifiers().get(Attributes.ATTACK_DAMAGE))
                if (m.getOperation() == AttributeModifier.Operation.ADDITION) base += m.getAmount();
            if (base > 0) somar(e, Attributes.ATTACK_DAMAGE, "dano_mao", (base + 1) * (rar.dano + DANO_POR_NIVEL * n));
            apoth(e, s, r, n);
        }
        if (s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot) {
            String k = slot.getName();
            somar(e, Attributes.ARMOR, "armadura_" + k, rar.armadura + ARMADURA_POR_NIVEL * n);
            somar(e, Attributes.ARMOR_TOUGHNESS, "resistencia_" + k, rar.resistencia + (n >= 5 ? 0.5 : 0) + (n >= 10 ? 0.5 : 0));
            somar(e, Attributes.MAX_HEALTH, "vida_" + k, rar.vida + (n >= 8 ? 1 : 0));
            somarApoth(e, br.com.frxcode.frxworld.atributos.Apoth.DODGE, "esquiva_" + k, (r >= 4 ? 0.02 : r >= 3 ? 0.01 : 0) + (n >= 10 ? 0.01 : 0));
        }
    }

    /**
     * Bônus da Forja em atributos do Apothic Attributes (só se o mod estiver instalado).
     * Armas: chance e dano crítico pela raridade e +N, roubo de vida no Lendário, perfuração de armadura a partir do +7.
     * Arcos: puxar mais rápido no Épico/Lendário. Ferramentas: minerar mais rápido.
     */
    private static void apoth(ItemAttributeModifierEvent e, ItemStack s, int r, int n) {
        if (!br.com.frxcode.frxworld.atributos.Apoth.ativo()) return;
        double[] crit = {0, 0.01, 0.03, 0.05, 0.08}, critDano = {0, 0, 0.05, 0.10, 0.25};
        if (s.getItem() instanceof ProjectileWeaponItem) {
            somarApoth(e, br.com.frxcode.frxworld.atributos.Apoth.DRAW_SPEED, "puxar", r >= 4 ? 0.2 : r >= 3 ? 0.1 : 0);
            somarApoth(e, br.com.frxcode.frxworld.atributos.Apoth.CRIT_CHANCE, "critico_arco", crit[Math.min(4, r)]);
            return;
        }
        somarApoth(e, br.com.frxcode.frxworld.atributos.Apoth.CRIT_CHANCE, "critico", crit[Math.min(4, r)] + (n >= 5 ? 0.02 : 0) + (n >= 10 ? 0.03 : 0));
        somarApoth(e, br.com.frxcode.frxworld.atributos.Apoth.CRIT_DAMAGE, "critico_dano", critDano[Math.min(4, r)] + 0.01 * n);
        somarApoth(e, br.com.frxcode.frxworld.atributos.Apoth.LIFE_STEAL, "roubo", r >= 4 ? 0.03 : 0);
        somarApoth(e, br.com.frxcode.frxworld.atributos.Apoth.ARMOR_PIERCE, "perfuracao", n >= 7 ? n - 6 : 0);
        if (s.getItem() instanceof net.minecraft.world.item.DiggerItem)
            somarApoth(e, br.com.frxcode.frxworld.atributos.Apoth.MINING, "mineracao", 0.05 * r + 0.04 * n);
    }

    private static void somarApoth(ItemAttributeModifierEvent e, String atributo, String nome, double valor) {
        Attribute a = br.com.frxcode.frxworld.atributos.Apoth.attr(atributo);
        if (a == null || valor <= 0) return;
        e.addModifier(a, new AttributeModifier(id("apoth_" + nome), "FRX " + nome, valor, AttributeModifier.Operation.ADDITION));
    }

    private static void somar(ItemAttributeModifierEvent e, Attribute atributo, String nome, double valor) {
        if (valor <= 0) return;
        e.addModifier(atributo, new AttributeModifier(id(nome), "FRX " + nome, valor, AttributeModifier.Operation.ADDITION));
    }

    /** Arcos e bestas: a flecha sai com o dano da raridade/aprimoramento. */
    @SubscribeEvent
    public void flechas(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractArrow f) || !(f.getOwner() instanceof Player p)) return;
        if (f.getPersistentData().getBoolean("frx_flecha")) return;
        f.getPersistentData().putBoolean("frx_flecha", true);
        ItemStack arma = p.getMainHandItem().getItem() instanceof ProjectileWeaponItem ? p.getMainHandItem() : p.getOffhandItem();
        if (!(arma.getItem() instanceof ProjectileWeaponItem)) return;
        double b = bonusDano(arma);
        if (b > 0) f.setBaseDamage(f.getBaseDamage() * (1 + b));
    }

    /** Itens fabricados também podem sair melhores (raro). */
    @SubscribeEvent
    public void fabricado(PlayerEvent.ItemCraftedEvent e) {
        ItemStack s = e.getCrafting();
        if (e.getEntity().level().isClientSide() || !forjavel(s) || s.hasTag() && s.getTag().contains(RARIDADE)) return;
        definirRaridade(s, sortear(CRAFT, e.getEntity().getRandom()));
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("raridade").then(Commands.argument("valor", IntegerArgumentType.integer(0, 4)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ItemStack s = p.getMainHandItem();
                    if (!forjavel(s)) { ctx.getSource().sendFailure(Component.literal("Segure uma arma, ferramenta ou armadura.")); return 0; }
                    definirRaridade(s, IntegerArgumentType.getInteger(ctx, "valor"));
                    ctx.getSource().sendSuccess(() -> Component.literal("Raridade: " + Raridade.de(raridade(s)).nome).withStyle(Raridade.de(raridade(s)).cor), false);
                    return 1;
                })))
                .then(Commands.literal("forja").then(Commands.argument("nivel", IntegerArgumentType.integer(0, NIVEL_MAX)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ItemStack s = p.getMainHandItem();
                    if (!forjavel(s)) { ctx.getSource().sendFailure(Component.literal("Segure uma arma, ferramenta ou armadura.")); return 0; }
                    definirNivel(s, IntegerArgumentType.getInteger(ctx, "nivel"));
                    ctx.getSource().sendSuccess(() -> Component.literal("Aprimoramento: +" + nivel(s)).withStyle(ChatFormatting.YELLOW), false);
                    return 1;
                }))));
    }
}
