package br.com.frxcode.frxworld.racas;

import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raça Saiyajin: transformações Super Saiyajin (tecla U) e Zenkai.
 *  - Forma 1 no nível 20, forma 2 no 40, forma 3 no 60. Cada aperto sobe uma forma; agachado + U volta ao normal.
 *  - Transformar leva 2 s parado (aura subindo, chão rachando, raio no fim) e empurra quem estiver perto.
 *  - Transformado: mais dano, velocidade e armadura; a fome cai mais rápido (com fome demais, volta ao normal).
 *  - Forma 3 dura no máximo 60 s e deixa cansado; só pode repetir 30 s depois.
 *  - Zenkai: escapar de uma luta com menos de 20% de vida dá +1% de dano permanente (até +15%, uma vez a cada 10 min).
 */
public class Saiyajin {
    public static final int[] NIVEL_FORMA = {0, 20, 40, 60};
    private static final double[] DANO = {0, 0.25, 0.45, 0.70}, VELOC = {0, 0.12, 0.18, 0.25}, ARMADURA = {0, 2, 4, 6};
    private static final float[] FOME_SEG = {0, 0.2f, 0.4f, 0.9f};
    private static final int ANIMACAO = 40, MAX_FORMA3 = 60_000, ESPERA_FORMA3 = 30_000;
    private static final int ZENKAI_MAX = 15;
    private static final long ZENKAI_INTERVALO = 10 * 60_000L;

    private static final class Estado {
        int forma, alvo, animando;
        long inicioForma3, fimCansaco, zenkaiPendente;
    }

    private static final Map<UUID, Estado> ESTADOS = new ConcurrentHashMap<>();

    private static Estado est(Player p) { return ESTADOS.computeIfAbsent(p.getUUID(), k -> new Estado()); }

    /** Forma atual (0 = normal). Usado pelo visual da raça. */
    public static int forma(Player p) {
        Estado e = ESTADOS.get(p.getUUID());
        return e == null ? 0 : e.forma;
    }

    public static boolean ehSaiyajin(Player p) { return "saiyajin".equals(RacasJogador.principal(p)) && RacasJogador.escolheu(p); }

    private static void avisar(ServerPlayer p, String t, ChatFormatting cor) { p.displayClientMessage(Component.literal(t).withStyle(cor), true); }

    // ------------------------------------------------------------------ tecla U

    public static void transformar(ServerPlayer p) {
        if (!ehSaiyajin(p)) { avisar(p, "Só quem tem sangue Saiyajin consegue se transformar.", ChatFormatting.GRAY); return; }
        Estado e = est(p);
        if (e.animando > 0) return;
        if (p.isShiftKeyDown() && e.forma > 0) { voltar(p, true); return; }
        int nivel = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
        int proxima = e.forma + 1;
        if (proxima > 3 || nivel < NIVEL_FORMA[proxima]) {
            if (e.forma > 0) { voltar(p, true); return; }
            avisar(p, "Seu poder ainda não basta: a primeira transformação vem no nível " + NIVEL_FORMA[1] + ".", ChatFormatting.GRAY);
            return;
        }
        if (p.getFoodData().getFoodLevel() < 6) { avisar(p, "Com fome desse jeito, não dá para liberar o poder.", ChatFormatting.RED); return; }
        if (proxima == 3 && System.currentTimeMillis() < e.fimCansaco + ESPERA_FORMA3) {
            avisar(p, "O corpo ainda não se recuperou da última forma 3.", ChatFormatting.RED);
            return;
        }
        e.alvo = proxima;
        e.animando = ANIMACAO;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ANIMACAO + 5, 10, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ANIMACAO + 5, 4, false, false, false));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, 0.5f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** Volta ao normal (tira os bônus). */
    public static void voltar(ServerPlayer p, boolean comEfeito) {
        Estado e = est(p);
        int era = e.forma;
        e.forma = 0;
        e.animando = 0;
        aplicarBonus(p, 0);
        if (era == 3) {
            e.fimCansaco = System.currentTimeMillis();
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1));
            if (comEfeito) avisar(p, "A forma 3 cobra o preço: o corpo pesa.", ChatFormatting.GOLD);
        }
        if (comEfeito && era > 0 && p.level() instanceof ServerLevel nv) {
            nv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            nv.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        if (era > 0) RacasJogador.atualizarVisual(p);
    }

    private static UUID idMod(String k) { return UUID.nameUUIDFromBytes(("frxworld:saiyajin:" + k).getBytes()); }

    private static void mod(ServerPlayer p, String k, Attribute a, AttributeModifier.Operation op, double v) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        inst.removeModifier(idMod(k));
        if (Math.abs(v) > 1e-6) inst.addTransientModifier(new AttributeModifier(idMod(k), "FRX Saiyajin " + k, v, op));
    }

    private static void aplicarBonus(ServerPlayer p, int forma) {
        mod(p, "dano", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.MULTIPLY_TOTAL, DANO[forma]);
        mod(p, "velocidade", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, VELOC[forma]);
        mod(p, "armadura", Attributes.ARMOR, AttributeModifier.Operation.ADDITION, ARMADURA[forma]);
    }

    // ------------------------------------------------------------------ animação e aura

    private static DustParticleOptions dourado(float tam) { return new DustParticleOptions(new Vector3f(1f, 0.85f, 0.2f), tam); }

    private static void animar(ServerPlayer p, Estado e) {
        ServerLevel nv = p.serverLevel();
        int t = ANIMACAO - e.animando;   // 0 .. 39
        double x = p.getX(), y = p.getY(), z = p.getZ();
        float tam = 1.2f + e.alvo * 0.4f;
        // coluna de energia subindo e anel no chão
        nv.sendParticles(dourado(tam), x, y + 1, z, 6 + e.alvo * 3, 0.35, 1.0, 0.35, 0.05);
        double raio = 0.6 + t * 0.06;
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + t * 0.2;
            nv.sendParticles(dourado(1f), x + Math.cos(a) * raio, y + 0.1, z + Math.sin(a) * raio, 1, 0, 0.05, 0, 0);
        }
        if (e.alvo >= 2) nv.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1, z, 6, 0.5, 0.9, 0.5, 0.1);
        BlockState chao = nv.getBlockState(p.blockPosition().below());
        if (!chao.isAir() && t % 4 == 0) nv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chao), x, y + 0.1, z, 12, 1.2, 0.1, 1.2, 0.15);
        if (t % 10 == 0) p.level().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 0.6f + t * 0.02f);
        if (t == 20) p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.5f, 1.3f);
        if (--e.animando > 0) return;
        // explosão final
        e.forma = e.alvo;
        if (e.forma == 3) e.inicioForma3 = System.currentTimeMillis();
        aplicarBonus(p, e.forma);
        LightningBolt raio2 = EntityType.LIGHTNING_BOLT.create(nv);
        if (raio2 != null) { raio2.moveTo(x, y, z); raio2.setVisualOnly(true); nv.addFreshEntity(raio2); }
        nv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0, 0, 0, 0);
        nv.sendParticles(dourado(2.5f), x, y + 1, z, 120, 1.5, 1.5, 1.5, 0.3);
        nv.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 1.4f);
        for (LivingEntity o : nv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4), o -> o != p && o.isAlive())) {
            Vec3 d = o.position().subtract(p.position()).multiply(1, 0, 1);
            if (d.lengthSqr() < 0.01) d = new Vec3(1, 0, 0);
            d = d.normalize().scale(1.2);
            o.push(d.x, 0.35, d.z);
            o.hurtMarked = true;
        }
        String nome = e.forma == 1 ? "SUPER SAIYAJIN" : "SUPER SAIYAJIN " + e.forma;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(nome).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(switch (e.forma) {
            case 1 -> "O poder adormecido despertou";
            case 2 -> "Relâmpagos correm pelo corpo";
            default -> "O limite foi ultrapassado... por pouco tempo";
        }).withStyle(ChatFormatting.YELLOW)));
        for (ServerPlayer o : nv.players())
            if (o != p && o.distanceTo(p) < 48)
                o.displayClientMessage(Component.literal("⚡ " + p.getGameProfile().getName() + " virou " + nome + "!").withStyle(ChatFormatting.GOLD), true);
        RacasJogador.atualizarVisual(p);
    }

    private static void aura(ServerPlayer p, int forma) {
        ServerLevel nv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        nv.sendParticles(dourado(0.9f + forma * 0.3f), x, y + 0.9, z, 2 + forma * 2, 0.35, 0.8, 0.35, 0.02);
        if (forma >= 2 && p.getRandom().nextInt(3) == 0) nv.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.2, z, 3, 0.4, 0.7, 0.4, 0.05);
        if (forma == 3) nv.sendParticles(ParticleTypes.END_ROD, x, y + 0.2, z, 1, 0.3, 0.05, 0.3, 0.03);
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p)) return;
        Estado e = ESTADOS.get(p.getUUID());
        if (e != null && e.animando > 0) {
            if (!ehSaiyajin(p) || !p.isAlive()) { e.animando = 0; return; }
            animar(p, e);
            return;
        }
        if (e != null && e.forma > 0) {
            if (!ehSaiyajin(p) || !p.isAlive()) { voltar(p, false); return; }
            if (p.tickCount % 3 == 0) aura(p, e.forma);
            if (p.tickCount % 20 == 0) {
                if (!p.isCreative()) p.causeFoodExhaustion(FOME_SEG[e.forma]);
                if (p.getFoodData().getFoodLevel() <= 3) { avisar(p, "A fome venceu: você volta ao normal.", ChatFormatting.RED); voltar(p, true); return; }
                if (e.forma == 3 && System.currentTimeMillis() - e.inicioForma3 > MAX_FORMA3) { voltar(p, true); return; }
            }
        }
        if (p.tickCount % 20 == 7 && ehSaiyajin(p)) zenkai(p);
    }

    /** Golpe corpo a corpo de quem está na forma 2 ou 3 dá choque. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void choque(LivingHurtEvent ev) {
        if (!(ev.getSource().getEntity() instanceof ServerPlayer p) || ev.getSource().getDirectEntity() != p || forma(p) < 2) return;
        ev.setAmount(ev.getAmount() * 1.15f);
        LivingEntity alvo = ev.getEntity();
        alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
        p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, alvo.getX(), alvo.getY() + alvo.getBbHeight() / 2, alvo.getZ(), 10, 0.3, 0.4, 0.3, 0.15);
    }

    // ------------------------------------------------------------------ Zenkai

    private static CompoundTag dadosZenkai(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains("frx_zenkai")) pers.put("frx_zenkai", new CompoundTag());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        return pers.getCompound("frx_zenkai");
    }

    public static int zenkaiTotal(Player p) { return dadosZenkai(p).getInt("total"); }

    /** Marca quando o Saiyajin fica com menos de 20% de vida e não morre. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void quaseMorte(LivingDamageEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !ehSaiyajin(p)) return;
        float depois = p.getHealth() - ev.getAmount();
        if (depois > 0 && depois < p.getMaxHealth() * 0.2f) est(p).zenkaiPendente = System.currentTimeMillis();
    }

    private static void zenkai(ServerPlayer p) {
        Estado e = est(p);
        CompoundTag z = dadosZenkai(p);
        int total = z.getInt("total");
        mod(p, "zenkai", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.MULTIPLY_TOTAL, total * 0.01);
        if (e.zenkaiPendente == 0) return;
        long agora = System.currentTimeMillis();
        if (agora - e.zenkaiPendente < 8_000 || p.getHealth() < p.getMaxHealth() * 0.4f) return;
        e.zenkaiPendente = 0;
        if (total >= ZENKAI_MAX || agora - z.getLong("ultimo") < ZENKAI_INTERVALO) return;
        z.putInt("total", total + 1);
        z.putLong("ultimo", agora);
        mod(p, "zenkai", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.MULTIPLY_TOTAL, (total + 1) * 0.01);
        p.sendSystemMessage(Component.literal("⚡ Zenkai! Você escapou por um fio e voltou mais forte: +1% de dano (total +" + (total + 1) + "%).").withStyle(ChatFormatting.GOLD));
        p.serverLevel().sendParticles(dourado(1.5f), p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    // ------------------------------------------------------------------ limpeza

    @SubscribeEvent
    public void morreu(LivingDeathEvent ev) { if (ev.getEntity() instanceof ServerPlayer p && forma(p) > 0) voltar(p, false); }

    @SubscribeEvent
    public void saiu(PlayerEvent.PlayerLoggedOutEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) { if (forma(p) > 0) voltar(p, false); ESTADOS.remove(p.getUUID()); }
    }

    @SubscribeEvent
    public void trocouMundo(PlayerEvent.PlayerChangedDimensionEvent ev) { if (ev.getEntity() instanceof ServerPlayer p && forma(p) > 0) voltar(p, true); }
}
