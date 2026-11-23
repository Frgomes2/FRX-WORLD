package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.armas.Lendarias;
import br.com.frxcode.frxworld.progressao.Sistema;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bônus de conjunto das armaduras lendárias (armas/Lendarias), com as 4 peças do mesmo conjunto vestidas:
 * Draconiana: imune a fogo e quem bate em você pega fogo por 4 s.
 * Abissal: +4 corações e 12% de chance de esquivar de um golpe.
 * Celestial: sem dano de queda, efeitos ruins somem e regeneração constante.
 */
public class ConjuntosLendarios {
    private static final UUID VIDA = UUID.nameUUIDFromBytes("frxworld:abissal_vida".getBytes(StandardCharsets.UTF_8));
    private static final Map<UUID, String> ativo = new ConcurrentHashMap<>();
    private int ticks;

    public ConjuntosLendarios() {}

    /** Conjunto completo vestido, ou null. */
    public static String completo(LivingEntity e) {
        String c = null;
        int n = 0;
        for (ItemStack s : e.getArmorSlots()) {
            String x = Lendarias.conjuntoDe(s);
            if (x == null || (c != null && !c.equals(x))) return null;
            c = x;
            n++;
        }
        return n == 4 ? c : null;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++ticks % 20 != 9) return;
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) {
            String c = completo(p);
            String antes = ativo.get(p.getUUID());
            AttributeInstance vida = p.getAttribute(Attributes.MAX_HEALTH);
            if (vida != null) {
                boolean quer = "abissal".equals(c);
                if (quer && vida.getModifier(VIDA) == null) vida.addTransientModifier(new AttributeModifier(VIDA, "FRX conjunto abissal", 8, AttributeModifier.Operation.ADDITION));
                if (!quer && vida.getModifier(VIDA) != null) { vida.removeModifier(VIDA); if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth()); }
            }
            if (c == null) { ativo.remove(p.getUUID()); continue; }
            switch (c) {
                case "draconiana" -> p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false, true));
                case "celestial" -> {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false, true));
                    List<net.minecraft.world.effect.MobEffect> ruins = new ArrayList<>();
                    for (MobEffectInstance i : p.getActiveEffects()) if (i.getEffect().getCategory() == MobEffectCategory.HARMFUL) ruins.add(i.getEffect());
                    for (var e : ruins) p.removeEffect(e);
                }
                default -> { }
            }
            if (!c.equals(antes)) {
                ativo.put(p.getUUID(), c);
                String[] t = switch (c) {
                    case "draconiana" -> new String[]{"Draconiana", "Imune a fogo, e quem bate em você pega fogo."};
                    case "abissal" -> new String[]{"Abissal", "+4 corações e 12% de chance de esquivar de um golpe."};
                    default -> new String[]{"Celestial", "Sem dano de queda, efeitos ruins somem e a vida se recupera sem parar."};
                };
                Sistema.avisar(p, "Conjunto " + t[0] + " completo", t[1], Sistema.OURO);
                p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.2f);
            }
        }
    }

    /** Entrega materiais das armaduras lendárias (na mochila; sem espaço, cai no chão aos pés). */
    public static void darMaterial(ServerPlayer p, net.minecraft.world.item.Item item, int qtd) {
        if (qtd <= 0) return;
        ItemStack s = new ItemStack(item, qtd);
        String nome = s.getHoverName().getString();
        if (!p.getInventory().add(s)) p.drop(s, false);
        p.sendSystemMessage(Component.literal("✦ +" + qtd + "× " + nome + " (material de armadura lendária: mesa de ferraria)").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Dragões do Ice and Fire (adultos, 100+ de vida) soltam Escama Draconiana para quem estava na luta. */
    @SubscribeEvent
    public void dragao(net.minecraftforge.event.entity.living.LivingDeathEvent ev) {
        LivingEntity e = ev.getEntity();
        if (e.level().isClientSide() || e.getMaxHealth() < 100) return;
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id == null || !id.getNamespace().equals("iceandfire") || !id.getPath().endsWith("_dragon")) return;
        for (ServerPlayer p : ((net.minecraft.server.level.ServerLevel) e.level()).players())
            if (p.isAlive() && p.distanceToSqr(e) < 48 * 48) darMaterial(p, Lendarias.ESCAMA.get(), 2 + ThreadLocalRandom.current().nextInt(3));
    }

    @SubscribeEvent
    public void esquiva(LivingAttackEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.getSource().getEntity() == null || !"abissal".equals(ativo.get(p.getUUID()))) return;
        if (ThreadLocalRandom.current().nextInt(100) >= 12) return;
        ev.setCanceled(true);
        p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.3);
        p.displayClientMessage(Component.literal("Esquivou!").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    @SubscribeEvent
    public void espinhosDeFogo(LivingHurtEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !"draconiana".equals(ativo.get(p.getUUID()))) return;
        if (ev.getSource().getDirectEntity() instanceof LivingEntity atacante && atacante != p) atacante.setSecondsOnFire(4);
    }

    /** Descrição: bônus do conjunto nas peças e de onde vem cada material. */
    @SubscribeEvent
    public void descricao(net.minecraftforge.event.entity.player.ItemTooltipEvent ev) {
        ItemStack s = ev.getItemStack();
        String c = Lendarias.conjuntoDe(s);
        var l = ev.getToolTip();
        if (c != null) {
            l.add(Component.literal("Conjunto (4 peças): " + switch (c) {
                case "draconiana" -> "imune a fogo; quem bate em você pega fogo";
                case "abissal" -> "+4 corações e 12% de esquiva";
                default -> "sem queda, sem efeitos ruins, regeneração";
            }).withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (s.is(Lendarias.ESCAMA.get())) {
            l.add(Component.literal("Dragões adultos, chefes de nível 40 a 54 e chefes da Torre (andar 30+).").withStyle(ChatFormatting.GRAY));
            l.add(Component.literal("Mesa de ferraria: netherite → Draconiana (Rank B), 2 por peça.").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (s.is(Lendarias.ESSENCIA.get())) {
            l.add(Component.literal("Chefes especiais (nível 55+) e chefes da Torre (andar 50+).").withStyle(ChatFormatting.GRAY));
            l.add(Component.literal("Mesa de ferraria: Draconiana → Abissal (Rank A), 2 por peça. Forja +11 a +13.").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (s.is(Lendarias.FRAGMENTO.get())) {
            l.add(Component.literal("Dragão do Fim e chefes da Torre (andar 80+).").withStyle(ChatFormatting.GRAY));
            l.add(Component.literal("Mesa de ferraria: Abissal → Celestial (Rank S), 2 por peça. Forja +14 e +15.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @SubscribeEvent
    public void semQueda(LivingFallEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && "celestial".equals(ativo.get(p.getUUID()))) ev.setDamageMultiplier(0);
    }
}
