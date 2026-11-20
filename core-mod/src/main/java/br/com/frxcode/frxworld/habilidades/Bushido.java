package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Samurai: o caminho da katana (original do FRX-WORLD).
 * - Saque: depois de 3 s sem golpear, a katana volta à bainha e o próximo golpe sai 75% mais forte.
 * - Katana: todo golpe com uma katana na mão causa 10% a mais. Sem katana não há Saque.
 * - Lâmina das Três Formas (Despertar): todo golpe é um Saque e alterna Brasa (queima), Maré (lentidão) e Relâmpago (dano extra).
 */
public class Bushido {
    private static final int EMBAINHAR = 60;                       // ticks sem golpear para a katana voltar à bainha
    /** jogador → {tick do último golpe, golpes dados no Despertar, já avisou que embainhou (1/0)}. */
    private static final Map<UUID, long[]> ESTADO = new ConcurrentHashMap<>();
    private static final String[] LAMINAS = {"katana", "odachi", "nodachi", "wakizashi", "tachi", "uchigatana"};

    /** Marca da Katana de Aprendiz: uma espada de pedra (classe F) que vale como katana. */
    private static final String APRENDIZ = "frx_katana";

    public Bushido() {}

    /** Quem escolhe Samurai recebe, uma vez por personagem, a Katana de Aprendiz: dá para treinar o Saque desde o Rank F. */
    public static void katanaDeAprendiz(ServerPlayer p) {
        var raiz = p.getPersistentData();
        if (!raiz.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) raiz.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        var pers = raiz.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (pers.getBoolean("frx_katana_dada")) return;
        pers.putBoolean("frx_katana_dada", true);
        ItemStack s = new ItemStack(net.minecraft.world.item.Items.STONE_SWORD);
        s.getOrCreateTag().putBoolean(APRENDIZ, true);
        s.setHoverName(Component.literal("Katana de Aprendiz").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.RED)));
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    static boolean samurai(ServerPlayer p) { return Progressao.temDados(p) && "SAMURAI".equals(Progressao.dados(p).classe()); }

    /** Katana = qualquer item cujo nome de registro tenha "katana" (ou outra lâmina japonesa), de qualquer mod. */
    public static boolean katana(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.hasTag() && s.getTag().getBoolean(APRENDIZ)) return true;
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null) return false;
        if (id.getPath().contains("handle")) return false;   // cabo de katana é peça de receita, não arma
        for (String l : LAMINAS) if (id.getPath().contains(l)) return true;
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoFerir(LivingHurtEvent ev) {
        LivingEntity alvo = ev.getEntity();
        if (alvo.level().isClientSide() || ev.getAmount() <= 0) return;
        if (!(ev.getSource().getEntity() instanceof ServerPlayer p) || ev.getSource().getDirectEntity() != p || alvo == p || !samurai(p) || !katana(p.getMainHandItem())) return;
        long agora = p.server.getTickCount();
        long[] e = ESTADO.computeIfAbsent(p.getUUID(), k -> new long[]{-EMBAINHAR, 0, 1});
        boolean formas = Despertar.formasAtivas(p), saque = formas || agora - e[0] >= EMBAINHAR;
        e[0] = agora; e[2] = 0;
        float dano = ev.getAmount() * 1.1f;
        ServerLevel nv = p.serverLevel();
        double x = alvo.getX(), y = alvo.getY() + alvo.getBbHeight() * 0.55, z = alvo.getZ();
        if (saque) {
            dano *= formas ? 1.5f : 1.75f;
            nv.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 2, 0.3, 0.2, 0.3, 0);
            nv.sendParticles(ParticleTypes.CRIT, x, y, z, 12, 0.3, 0.4, 0.3, 0.3);
            nv.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.7f);
            if (!formas) p.displayClientMessage(Component.literal("Saque!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
        if (formas) {
            switch ((int) (e[1]++ % 3)) {
                case 0 -> { alvo.setSecondsOnFire(4); nv.sendParticles(ParticleTypes.FLAME, x, y, z, 14, 0.3, 0.4, 0.3, 0.04); }                       // Brasa
                case 1 -> { alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1)); nv.sendParticles(ParticleTypes.SPLASH, x, y, z, 24, 0.4, 0.4, 0.4, 0.2); }   // Maré
                default -> { dano *= 1.3f; nv.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 18, 0.3, 0.5, 0.3, 0.3);                               // Relâmpago
                    nv.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.25f, 1.8f); }
            }
        } else e[1] = 0;
        ev.setAmount(dano);
    }

    /** Avisa, uma vez, quando a katana volta à bainha e o Saque está pronto de novo. */
    @SubscribeEvent
    public void aCadaTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        long[] e = ESTADO.get(p.getUUID());
        if (e == null || e[2] == 1 || p.server.getTickCount() - e[0] < EMBAINHAR) return;
        e[2] = 1;
        if (!samurai(p) || !katana(p.getMainHandItem())) return;
        p.displayClientMessage(Component.literal("Katana na bainha: o próximo golpe é um Saque.").withStyle(ChatFormatting.GRAY), true);
        p.playNotifySound(SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.35f, 1.9f);
    }
}
