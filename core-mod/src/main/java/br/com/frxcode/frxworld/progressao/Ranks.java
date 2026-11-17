package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.SyncRotuloMob;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monstros com rank: Normal (só "Zumbi Lv. 3"), Elite (★1–5, um traço no nome: "Zumbi Berserker"),
 * Campeão (prefixo + título, barra própria) e Chefe / Chefe Mundial (dungeons, chefes de mods, evento).
 * Dados no NBT do mob; o cliente recebe um resumo para desenhar o letreiro (cliente/RotulosMob).
 */
public class Ranks {
    public static final String NORMAL = "normal", ELITE = "elite", CAMPEAO = "campeao", CHEFE = "chefe", MUNDIAL = "mundial";
    private static final String K_RANK = "frx_rank", K_EST = "frx_rank_estrelas", K_TRACOS = "frx_rank_tracos",
            K_PREF = "frx_rank_prefixo", K_TIT = "frx_rank_titulo", K_NIVEL = "frx_rank_nivel";

    /** Traços: id → nome que vai depois do nome do monstro. */
    public static final Map<String, String> TRACOS = Map.of(
            "berserker", "Berserker", "blindado", "Blindado", "veloz", "Veloz", "vampirico", "Vampírico",
            "peconhento", "Peçonhento", "incendiario", "Incendiário", "gelido", "Gélido");
    private static final String[] IDS_TRACOS = {"berserker", "blindado", "veloz", "vampirico", "peconhento", "incendiario", "gelido"};
    private static final String[] PREFIXOS = {"Ancestral", "Sombrio", "Colossal", "Profano", "Eterno", "Maldito", "Voraz"};
    private static final String[] TITULOS = {"Guardião da Cripta", "Terror da Estrada", "Devorador de Almas", "Aquele Que Não Dorme",
            "Flagelo dos Viajantes", "Senhor da Noite", "Carrasco Esquecido", "Sombra do Bosque", "Rei dos Ossos", "Pesadelo dos Reinos",
            "Último da Linhagem", "Coração de Pedra"};

    private static final Map<UUID, ServerBossEvent> BARRAS = new HashMap<>();

    public Ranks() {}

    private static UUID id(String n) { return UUID.nameUUIDFromBytes(("frxworld:rank_" + n).getBytes(StandardCharsets.UTF_8)); }

    public static String rank(LivingEntity e) {
        String r = e.getPersistentData().getString(K_RANK);
        return r.isEmpty() ? NORMAL : r;
    }

    public static int nivel(LivingEntity e) {
        CompoundTag d = e.getPersistentData();
        return Math.max(1, d.contains(K_NIVEL) ? d.getInt(K_NIVEL) : Zonas.nivelDo(e));
    }

    private static boolean temTraco(LivingEntity e, String t) {
        String s = e.getPersistentData().getString(K_TRACOS);
        return !s.isEmpty() && (("," + s + ",").contains("," + t + ","));
    }

    private static void mod(LivingEntity e, Attribute a, String nome, double v, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null || v == 0) return;
        inst.removeModifier(id(nome));
        inst.addPermanentModifier(new AttributeModifier(id(nome), "FRX rank " + nome, v, op));
    }

    // ------------------------------------------------------------------ nascer com rank

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void aoNascer(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !(ev.getEntity() instanceof Mob m)) return;
        CompoundTag d = m.getPersistentData();
        if (d.contains(K_RANK)) return;
        if (m.getTags().contains("frx_chefe_mundial")) { d.putString(K_RANK, MUNDIAL); return; }
        if (m.getTags().contains("frx_boss") || m.getType().is(Tags.EntityTypes.BOSSES)) { d.putString(K_RANK, CHEFE); return; }
        if (!(m instanceof Enemy) || m.getTags().contains("frx_invocado") || m.hasCustomName() || !d.contains("frx_zona")) return;
        int n = Zonas.nivelDo(m);
        for (Reino r : Reinos.todos()) if (r.distancia(m.getBlockX(), m.getBlockZ()) == 0) { d.putString(K_RANK, NORMAL); return; }
        RandomSource rnd = m.getRandom();
        int[] faixa = Zonas.faixa(m.level());
        boolean dimensao = m.level().dimension() != net.minecraft.world.level.Level.OVERWORLD;
        // jogador muito acima da região por perto: desafios especiais (mais chance e mais nível, sem passar da faixa + 10)
        ServerPlayer forte = null;
        if (m.level() instanceof ServerLevel nv)
            for (ServerPlayer p : nv.players())
                if (p.distanceToSqr(m) < 48 * 48 && Progressao.temDados(p) && Progressao.dados(p).nivel() >= n + 10
                        && (forte == null || Progressao.dados(p).nivel() > Progressao.dados(forte).nivel())) forte = p;
        double multElite = forte != null ? 1.5 : 1, multCampeao = forte != null ? 2 : 1;
        double sorte = rnd.nextDouble() * 100;
        double chC = ConfigJogo.rankCampeaoChance() * multCampeao, chE = ConfigJogo.rankEliteChance() * multElite;
        boolean podeCampeao = dimensao || n >= ConfigJogo.rankCampeaoNivelMin(), podeElite = dimensao || n >= ConfigJogo.rankEliteNivelMin();
        String rank = podeCampeao && sorte < chC ? CAMPEAO : podeElite && sorte < chC + chE ? ELITE : NORMAL;
        if (!NORMAL.equals(rank)) {
            // nível do rank dentro da faixa: Elite até +5, Campeão +5 a +10; com jogador forte perto, sobe até metade da diferença
            int alvo = n + (ELITE.equals(rank) ? 1 + rnd.nextInt(5) : 5 + rnd.nextInt(6));
            if (forte != null) alvo = Math.max(alvo, n + (Progressao.dados(forte).nivel() - n) / 3);
            alvo = Math.min(alvo, faixa[1] + (forte != null ? 5 : 0));
            if (alvo > n) Zonas.aplicarNivel(m, alvo);
        }
        if (CAMPEAO.equals(rank)) virarCampeao(m, rnd);
        else if (ELITE.equals(rank)) virarElite(m, rnd);
        else d.putString(K_RANK, NORMAL);
    }

    private static void aplicarTraco(Mob m, String t, double f) {
        switch (t) {
            case "berserker" -> mod(m, Attributes.ATTACK_DAMAGE, "berserker", 0.3 * f, AttributeModifier.Operation.MULTIPLY_TOTAL);
            case "blindado" -> {
                mod(m, Attributes.ARMOR, "blindado", 8 * f, AttributeModifier.Operation.ADDITION);
                mod(m, Attributes.ARMOR_TOUGHNESS, "blindado_t", 4 * f, AttributeModifier.Operation.ADDITION);
                mod(m, Attributes.KNOCKBACK_RESISTANCE, "blindado_k", 0.5, AttributeModifier.Operation.ADDITION);
            }
            case "veloz" -> mod(m, Attributes.MOVEMENT_SPEED, "veloz", 0.3 * f, AttributeModifier.Operation.MULTIPLY_TOTAL);
            case "incendiario" -> m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, true, false));
            default -> { }
        }
    }

    private static void virarElite(Mob m, RandomSource rnd) {
        double r = rnd.nextDouble();
        int est = r < 0.35 ? 1 : r < 0.63 ? 2 : r < 0.83 ? 3 : r < 0.95 ? 4 : 5;
        String t = IDS_TRACOS[rnd.nextInt(IDS_TRACOS.length)];
        CompoundTag d = m.getPersistentData();
        d.putString(K_RANK, ELITE);
        d.putInt(K_EST, est);
        d.putString(K_TRACOS, t);
        mod(m, Attributes.MAX_HEALTH, "vida", 0.5 + 0.2 * est, AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(m, Attributes.ATTACK_DAMAGE, "dano", 0.1 + 0.06 * est, AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(m, Attributes.ARMOR, "armadura", 2 * est, AttributeModifier.Operation.ADDITION);
        aplicarTraco(m, t, 0.6 + 0.1 * est);
        m.setHealth(m.getMaxHealth());
    }

    private static void virarCampeao(Mob m, RandomSource rnd) {
        String t1 = IDS_TRACOS[rnd.nextInt(IDS_TRACOS.length)], t2;
        do { t2 = IDS_TRACOS[rnd.nextInt(IDS_TRACOS.length)]; } while (t2.equals(t1));
        CompoundTag d = m.getPersistentData();
        d.putString(K_RANK, CAMPEAO);
        d.putString(K_TRACOS, t1 + "," + t2);
        d.putString(K_PREF, PREFIXOS[rnd.nextInt(PREFIXOS.length)]);
        d.putString(K_TIT, TITULOS[rnd.nextInt(TITULOS.length)]);
        mod(m, Attributes.MAX_HEALTH, "vida", 3.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(m, Attributes.ATTACK_DAMAGE, "dano", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(m, Attributes.ARMOR, "armadura", 10, AttributeModifier.Operation.ADDITION);
        mod(m, Attributes.KNOCKBACK_RESISTANCE, "empurrao", 0.6, AttributeModifier.Operation.ADDITION);
        aplicarTraco(m, t1, 1.2);
        aplicarTraco(m, t2, 1.2);
        m.setHealth(m.getMaxHealth());
        m.setPersistenceRequired();
        m.setGlowingTag(false);
        // avisa quem está por perto (na próxima volta, quando o mundo já tem o mob)
        if (m.level() instanceof ServerLevel nv) nv.getServer().execute(() -> {
            if (!m.isAlive()) return;
            for (ServerPlayer p : nv.players())
                if (p.distanceToSqr(m) < 64 * 64)
                    p.sendSystemMessage(Component.literal("⚔ Um Campeão surgiu por perto: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(nome(m).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                            .append(Component.literal(" — “" + d.getString(K_TIT) + "”, Lv. " + nivel(m)).withStyle(ChatFormatting.GRAY)));
        });
    }

    /** Nome com o traço/prefixo: "Zumbi Berserker", "Esqueleto Ancestral" (traduzido no cliente). */
    public static MutableComponent nome(LivingEntity e) {
        if (e.hasCustomName() && e.getCustomName() != null) return e.getCustomName().copy();
        MutableComponent n = e.getType().getDescription().copy();
        CompoundTag d = e.getPersistentData();
        switch (rank(e)) {
            case ELITE -> n.append(" " + TRACOS.getOrDefault(d.getString(K_TRACOS), ""));
            case CAMPEAO -> n.append(" " + d.getString(K_PREF));
            default -> { }
        }
        return n;
    }

    /** Linha da barra de ação ao bater: "[Elite] Zumbi Berserker · Lv. 17 ★★★". */
    public static MutableComponent rotulo(LivingEntity e) {
        int n = nivel(e);
        MutableComponent c = Component.empty();
        switch (rank(e)) {
            case ELITE -> c.append(Component.literal("[Elite] ").withStyle(ChatFormatting.GOLD));
            case CAMPEAO -> c.append(Component.literal("[Campeão] ").withStyle(ChatFormatting.LIGHT_PURPLE));
            case CHEFE -> c.append(Component.literal("[Chefe] ").withStyle(ChatFormatting.RED));
            case MUNDIAL -> c.append(Component.literal("[Chefe Mundial] ").withStyle(ChatFormatting.DARK_RED));
            default -> { }
        }
        ChatFormatting cor = n >= 40 ? ChatFormatting.DARK_RED : n >= 25 ? ChatFormatting.RED : n >= 10 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        c.append(nome(e).withStyle(cor)).append(Component.literal(" · Lv. " + n).withStyle(cor));
        int est = e.getPersistentData().getInt(K_EST);
        if (est > 0) c.append(Component.literal(" " + "★".repeat(est)).withStyle(ChatFormatting.YELLOW));
        return c;
    }

    // ------------------------------------------------------------------ letreiro no cliente

    private static SyncRotuloMob pacote(LivingEntity e) {
        CompoundTag d = e.getPersistentData();
        return new SyncRotuloMob(e.getId(), nivel(e), rank(e), d.getInt(K_EST), d.getString(K_TRACOS), d.getString(K_PREF), d.getString(K_TIT));
    }

    @SubscribeEvent
    public void aoVer(PlayerEvent.StartTracking ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !(ev.getTarget() instanceof LivingEntity e)) return;
        CompoundTag d = e.getPersistentData();
        if (!d.contains(K_RANK) && !d.contains("frx_zona")) return;
        if (e.getTags().contains("frx_invocado")) return;
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), pacote(e));
    }

    // ------------------------------------------------------------------ traços em combate

    @SubscribeEvent
    public void aoFerir(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide()) return;
        if (ev.getSource().getEntity() instanceof LivingEntity atk && ev.getSource().getDirectEntity() == atk && atk.getPersistentData().contains(K_TRACOS)) {
            if (temTraco(atk, "vampirico")) atk.heal(ev.getAmount() * 0.3f);
            if (temTraco(atk, "peconhento")) vitima.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            if (temTraco(atk, "incendiario")) vitima.setSecondsOnFire(4);
            if (temTraco(atk, "gelido")) vitima.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
        if (temTraco(vitima, "berserker") && vitima.getHealth() - ev.getAmount() < vitima.getMaxHealth() * 0.5f && !vitima.hasEffect(MobEffects.DAMAGE_BOOST)) {
            vitima.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
            vitima.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            if (vitima.level() instanceof ServerLevel nv)
                nv.sendParticles(ParticleTypes.ANGRY_VILLAGER, vitima.getX(), vitima.getY() + vitima.getBbHeight(), vitima.getZ(), 4, 0.3, 0.2, 0.3, 0);
        }
        if (temTraco(vitima, "incendiario") && ev.getSource().is(DamageTypeTags.IS_FIRE)) ev.setCanceled(true);
    }

    // ------------------------------------------------------------------ prêmios

    @SubscribeEvent
    public void premios(LivingDropsEvent ev) {
        LivingEntity e = ev.getEntity();
        if (!(ev.getSource().getEntity() instanceof ServerPlayer p)) return;
        String r = rank(e);
        if (!ELITE.equals(r) && !CAMPEAO.equals(r)) return;
        int n = nivel(e);
        RandomSource rnd = e.getRandom();
        List<ItemStack> extra = new ArrayList<>();
        if (ELITE.equals(r)) {
            int est = Math.max(1, e.getPersistentData().getInt(K_EST));
            if (rnd.nextDouble() < 0.3 + est * 0.1)   // repete um dos drops normais
                for (ItemEntity it : new ArrayList<>(ev.getDrops())) { extra.add(it.getItem().copy()); break; }
            if (est >= 4 && rnd.nextDouble() < 0.25) extra.add(new ItemStack(Items.EMERALD, 1 + rnd.nextInt(2)));
        } else {
            extra.add(new ItemStack(Items.DIAMOND, 1 + rnd.nextInt(2)));
            extra.add(new ItemStack(Items.EMERALD, 2 + rnd.nextInt(4)));
            extra.add(new ItemStack(rnd.nextDouble() < 0.1 ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE));
        }
        for (ItemStack s : extra) ev.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), s));
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        LivingEntity e = ev.getEntity();
        ServerBossEvent b = BARRAS.remove(e.getUUID());
        if (b != null) b.removeAllPlayers();
        if (ev.getSource().getEntity() instanceof ServerPlayer mp) {
            if (ELITE.equals(rank(e))) Marcos.marcar(mp, "elite");
            if (CAMPEAO.equals(rank(e))) Marcos.marcar(mp, "campeao");
        }
        if (CAMPEAO.equals(rank(e)) && ev.getSource().getEntity() instanceof ServerPlayer p && e.level() instanceof ServerLevel nv) {
            for (ServerPlayer o : nv.players())
                if (o.distanceToSqr(e) < 64 * 64)
                    o.sendSystemMessage(Component.literal("✦ " + p.getGameProfile().getName() + " derrotou o Campeão ").withStyle(ChatFormatting.GOLD)
                            .append(nome(e).withStyle(ChatFormatting.WHITE)).append(Component.literal(", “" + e.getPersistentData().getString(K_TIT) + "”!").withStyle(ChatFormatting.GOLD)));
            nv.playSound(null, e.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.HOSTILE, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------ barra dos campeões

    private int ticks;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        // procura campeões perto de jogadores
        for (ServerLevel nv : ev.getServer().getAllLevels()) {
            for (ServerPlayer p : nv.players()) {
                for (Mob m : nv.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), x -> x.isAlive() && CAMPEAO.equals(rank(x)))) {
                    ServerBossEvent b = BARRAS.computeIfAbsent(m.getUUID(), k -> new ServerBossEvent(
                            Component.literal("[Campeão] ").withStyle(ChatFormatting.LIGHT_PURPLE).append(nome(m).withStyle(ChatFormatting.WHITE))
                                    .append(Component.literal(" — “" + m.getPersistentData().getString(K_TIT) + "” · Lv. " + nivel(m)).withStyle(ChatFormatting.GRAY)),
                            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10));
                    b.addPlayer(p);
                }
            }
        }
        // atualiza e solta quem se afastou
        BARRAS.entrySet().removeIf(en -> {
            Entity e = null;
            for (ServerLevel nv : ev.getServer().getAllLevels()) { e = nv.getEntity(en.getKey()); if (e != null) break; }
            ServerBossEvent b = en.getValue();
            if (!(e instanceof LivingEntity le) || !le.isAlive()) { b.removeAllPlayers(); return true; }
            b.setProgress(Math.max(0, Math.min(1, le.getHealth() / le.getMaxHealth())));
            for (ServerPlayer p : new ArrayList<>(b.getPlayers())) if (p.level() != le.level() || p.distanceToSqr(le) > 48 * 48) b.removePlayer(p);
            if (b.getPlayers().isEmpty()) { return true; }
            return false;
        });
    }
}
