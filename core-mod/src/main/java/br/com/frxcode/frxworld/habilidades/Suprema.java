package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Habilidade Suprema: no nível 5, a Habilidade Única pode passar por uma prova (/suprema).
 * Quem vence o Eco da própria habilidade ganha um nome novo, um degrau a mais no efeito passivo
 * e um efeito ativo, disparado com Agachar + F (ou /suprema usar).
 */
public class Suprema {
    private static final String TAG = "frx_eco", PRESA = "frx_presa";
    private static final long DURACAO_MS = 240_000, ESPERA_MS = 600_000;

    public record Def(String nome, String ativo, int recargaSeg) {}

    public static final Map<String, Def> TODAS = new LinkedHashMap<>();
    static {
        TODAS.put("sabio", new Def("Senhor da Sabedoria", "Análise Total: revela os inimigos a 24 blocos e dá Força II por 15 s.", 90));
        TODAS.put("devorador", new Def("Senhor da Gula", "Banquete: arranca vida de todos os inimigos a 8 blocos e cura você com ela.", 75));
        TODAS.put("furia", new Def("Ira do Dragão Verdadeiro", "Rugido: onda que fere e arremessa os inimigos a 8 blocos.", 75));
        TODAS.put("aco", new Def("Fortaleza Absoluta", "Muralha: Resistência IV e corações extras por 8 s.", 90));
        TODAS.put("sombra", new Def("Soberano das Sombras", "Véu: invisível e veloz por 10 s; os monstros perdem você de vista.", 90));
        TODAS.put("critico", new Def("Olho Divino", "Mira Perfeita: todos os golpes são críticos por 10 s.", 90));
        TODAS.put("sangue", new Def("Monarca Carmesim", "Sede: por 10 s, 30% do dano causado volta como vida.", 90));
        TODAS.put("vento", new Def("Deus do Vento", "Rajada: avanço de 12 blocos e Velocidade III por 10 s.", 45));
        TODAS.put("imortal", new Def("Fênix Eterna", "Renascer: cura toda a vida e limpa os efeitos ruins.", 180));
        TODAS.put("titan", new Def("Colosso Primordial", "Colosso: corações extras e Resistência II por 10 s.", 90));
        TODAS.put("espinhos", new Def("Rei dos Espinhos", "Estouro: espinhos ferem e deixam lentos todos os inimigos a 8 blocos.", 75));
        TODAS.put("vingador", new Def("Juiz Implacável", "Sentença: Força III por 8 s.", 90));
        TODAS.put("sorte", new Def("Favorito do Destino", "Fortuna: muita sorte por 60 s (baús e monstros rendem bem mais).", 180));
        TODAS.put("gelo", new Def("Senhor do Inverno", "Nevasca: fere e prende no gelo por 5 s os inimigos a 8 blocos.", 75));
        TODAS.put("chama", new Def("Coração do Vulcão", "Erupção: fere e incendeia os inimigos a 8 blocos; você fica imune a fogo por 20 s.", 75));
        TODAS.put("berserk", new Def("Deus da Guerra", "Frenesi: Força II, Velocidade II e Pressa II por 12 s.", 90));
        TODAS.put("regen", new Def("Fonte da Vida", "Nascente: Regeneração forte por 8 s em você e nos jogadores a 8 blocos.", 90));
        TODAS.put("eco", new Def("Mil Ecos", "Ressonância: Pressa III e Força I por 10 s.", 90));
        TODAS.put("guardiao", new Def("Guardião Eterno", "Domo: quase nenhum dano passa por 4 s.", 120));
        TODAS.put("cacador", new Def("Predador Ápice", "Marca: o inimigo mais forte a 24 blocos fica marcado e sofre 40% a mais de dano por 12 s.", 75));
    }

    private record Prova(UUID eco, long fim) {}
    private static final Map<UUID, Prova> PROVAS = new HashMap<>();

    public Suprema() {}

    public static boolean tem(net.minecraft.world.entity.player.Player p) { return Unica.dadosDe(p).getBoolean("suprema"); }
    public static Def def(net.minecraft.world.entity.player.Player p) { return tem(p) ? TODAS.get(Unica.id(p)) : null; }
    private static boolean ativa(net.minecraft.world.entity.player.Player p, String qual) {
        return qual.equals(Unica.id(p)) && tem(p) && Unica.dadosDe(p).getLong("ativoAte") > System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ prova

    private static void base(Mob e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private static void iniciar(ServerPlayer p) {
        Unica.Def d = Unica.def(p);
        CompoundTag dados = Unica.dadosDe(p);
        if (d == null) { erro(p, "Você ainda não despertou uma Habilidade Única."); return; }
        if (tem(p)) { erro(p, "Sua habilidade já é Suprema. Use com Agachar + F."); return; }
        if (Unica.nivel(p) < 5) { erro(p, d.nome() + " precisa chegar ao nível 5 antes da prova (veja em /unica)."); return; }
        if (PROVAS.containsKey(p.getUUID())) { erro(p, "Sua prova já está em andamento."); return; }
        long espera = dados.getLong("esperaSuprema") - System.currentTimeMillis();
        if (espera > 0) { erro(p, "Sua alma ainda está se recompondo. Tente de novo em " + (espera / 60_000 + 1) + " min."); return; }

        ServerLevel nivel = p.serverLevel();
        WitherSkeleton eco = EntityType.WITHER_SKELETON.create(nivel);
        if (eco == null) { erro(p, "O Eco não respondeu. Tente em outro lugar."); return; }
        Vec3 olhar = p.getLookAngle();
        Vec3 dir = new Vec3(olhar.x, 0, olhar.z);
        dir = dir.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : dir.normalize();
        eco.moveTo(p.getX() + dir.x * 5, p.getY(), p.getZ() + dir.z * 5, p.getYRot() + 180, 0);
        eco.addTag(TAG);
        eco.getPersistentData().putString("frx_dono", p.getUUID().toString());
        eco.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        eco.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        eco.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        for (EquipmentSlot s : EquipmentSlot.values()) eco.setDropChance(s, 0f);
        int nv = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
        base(eco, Attributes.MAX_HEALTH, 160);
        base(eco, Attributes.ATTACK_DAMAGE, 7);
        base(eco, Attributes.MOVEMENT_SPEED, 0.31);
        base(eco, Attributes.KNOCKBACK_RESISTANCE, 0.6);
        base(eco, Attributes.FOLLOW_RANGE, 48);
        Zonas.aplicarNivel(eco, nv);
        eco.setHealth(eco.getMaxHealth());
        eco.setCustomName(Component.literal("Eco de " + d.nome()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        eco.setCustomNameVisible(true);
        eco.setPersistenceRequired();
        eco.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 300, 0, false, false));
        eco.setTarget(p);
        nivel.addFreshEntity(eco);
        nivel.sendParticles(ParticleTypes.REVERSE_PORTAL, eco.getX(), eco.getY() + 1, eco.getZ(), 150, 0.5, 1, 0.5, 0.1);
        nivel.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);

        PROVAS.put(p.getUUID(), new Prova(eco.getUUID(), System.currentTimeMillis() + DURACAO_MS));
        Sistema.avisar(p, "Prova da Habilidade Suprema", "A sua própria habilidade tomou forma. Derrote o Eco de " + d.nome() + " em 4 minutos, sem morrer.", Sistema.ROXO);
    }

    private static void erro(ServerPlayer p, String t) { p.sendSystemMessage(Component.literal(t).withStyle(ChatFormatting.GRAY)); }

    private static Entity eco(MinecraftServer s, Prova pr) {
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(pr.eco());
            if (e != null) return e;
        }
        return null;
    }

    private static void encerrar(MinecraftServer s, UUID jogador) {
        Prova pr = PROVAS.remove(jogador);
        if (pr == null) return;
        Entity e = eco(s, pr);
        if (e != null && e.isAlive()) e.discard();
    }

    private static void falhar(ServerPlayer p, String motivo) {
        encerrar(p.server, p.getUUID());
        Unica.dadosDe(p).putLong("esperaSuprema", System.currentTimeMillis() + ESPERA_MS);
        Sistema.avisar(p, "A prova falhou", motivo + " Tente de novo em 10 minutos.", Sistema.VERMELHO);
    }

    private static void vencer(ServerPlayer p) {
        PROVAS.remove(p.getUUID());
        Unica.Def antiga = Unica.def(p);
        Unica.tornarSuprema(p);
        Def d = TODAS.get(Unica.id(p));
        if (d == null || antiga == null) return;
        ServerLevel nivel = p.serverLevel();
        nivel.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 200, 0.8, 1.4, 0.8, 0.15);
        nivel.sendParticles(ParticleTypes.DRAGON_BREATH, p.getX(), p.getY() + 0.5, p.getZ(), 120, 1.2, 0.6, 1.2, 0.03);
        nivel.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 0.8f);
        nivel.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.6f);
        p.setHealth(p.getMaxHealth());
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 90, 30));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(d.nome().toUpperCase()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(antiga.nome() + " → Habilidade Suprema").withStyle(ChatFormatting.WHITE)));
        Sistema.avisar(p, "Habilidade Suprema: " + d.nome(), d.ativo() + " Use com Agachar + F. Recarga: " + d.recargaSeg() + " s.", Sistema.ROXO);
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("✦ A habilidade de " + p.getGameProfile().getName() + " evoluiu: " + antiga.nome() + " tornou-se a Habilidade Suprema ")
                .withStyle(ChatFormatting.LIGHT_PURPLE).append(Component.literal(d.nome()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("!").withStyle(ChatFormatting.LIGHT_PURPLE)), false);
        Progressao.enviarAoPainel(p, false);
    }

    // ------------------------------------------------------------------ efeito ativo

    private static List<LivingEntity> inimigos(ServerPlayer p, double raio) {
        List<LivingEntity> l = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(raio)))
            if (e instanceof Enemy && e.isAlive() && e.distanceToSqr(p) <= raio * raio) l.add(e);
        return l;
    }

    /** Dano de uma fatia da vida do alvo, com piso e teto (para não apagar chefes de uma vez). */
    private static float fatia(LivingEntity e, double parte, float piso, float teto) {
        return (float) Math.max(piso, Math.min(teto, e.getMaxHealth() * parte));
    }

    public static void usar(ServerPlayer p) {
        Def d = def(p);
        if (d == null) return;
        CompoundTag dados = Unica.dadosDe(p);
        long agora = System.currentTimeMillis(), falta = dados.getLong("recargaAtiva") - agora;
        if (falta > 0) {
            p.displayClientMessage(Component.literal(d.nome() + " recarregando: " + (falta / 1000 + 1) + " s").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ServerLevel nivel = p.serverLevel();
        String id = Unica.id(p);
        switch (id) {
            case "sabio" -> {
                for (LivingEntity e : inimigos(p, 24)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 1, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
            }
            case "devorador" -> {
                float total = 0;
                for (LivingEntity e : inimigos(p, 8)) {
                    float q = fatia(e, 0.15, 6, 60);
                    if (e.hurt(p.damageSources().magic(), q)) total += q;
                    nivel.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                }
                p.heal(total * 0.6f + 4);
            }
            case "furia" -> {
                for (LivingEntity e : inimigos(p, 8)) {
                    e.hurt(p.damageSources().playerAttack(p), fatia(e, 0.20, 8, 80));
                    Vec3 dir = e.position().subtract(p.position());
                    dir = dir.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : dir.normalize();
                    e.push(dir.x * 1.4, 0.6, dir.z * 1.4);
                    e.hurtMarked = true;
                }
                nivel.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 1, p.getZ(), 12, 3, 0.5, 3, 0);
                nivel.playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2f, 1f);
            }
            case "aco" -> {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 3, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 3, false, true));
            }
            case "sombra" -> {
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false));
                for (LivingEntity e : inimigos(p, 32)) if (e instanceof Mob m && m.getTarget() == p) m.setTarget(null);
                nivel.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.02);
            }
            case "critico", "sangue" -> dados.putLong("ativoAte", agora + 10_000);
            case "vento" -> {
                Vec3 o = p.getLookAngle();
                p.push(o.x * 2.6, Math.max(0.25, o.y * 1.2 + 0.25), o.z * 2.6);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 2, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
                nivel.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 40, 0.5, 0.3, 0.5, 0.1);
            }
            case "imortal" -> {
                List<MobEffect> ruins = new ArrayList<>();
                for (MobEffectInstance e : p.getActiveEffects()) if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL) ruins.add(e.getEffect());
                for (MobEffect e : ruins) p.removeEffect(e);
                p.setHealth(p.getMaxHealth());
                p.clearFire();
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, true));
                nivel.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 80, 0.5, 1, 0.5, 0.08);
            }
            case "titan" -> {
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1, false, true));
            }
            case "espinhos" -> {
                for (LivingEntity e : inimigos(p, 8)) {
                    e.hurt(p.damageSources().thorns(p), fatia(e, 0.18, 7, 70));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                    nivel.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 14, 0.3, 0.5, 0.3, 0.2);
                }
            }
            case "vingador" -> p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 2, false, true));
            case "sorte" -> p.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 4, false, true));
            case "gelo" -> {
                for (LivingEntity e : inimigos(p, 8)) {
                    e.hurt(p.damageSources().freeze(), fatia(e, 0.15, 6, 60));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
                    e.setTicksFrozen(200);
                    nivel.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                }
            }
            case "chama" -> {
                for (LivingEntity e : inimigos(p, 8)) {
                    e.hurt(p.damageSources().onFire(), fatia(e, 0.15, 6, 60));
                    e.setSecondsOnFire(8);
                }
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0, false, true));
                nivel.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1, p.getZ(), 40, 3, 0.5, 3, 0);
            }
            case "berserk" -> {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 1, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 1, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 240, 1, false, true));
            }
            case "regen" -> {
                for (ServerPlayer j : nivel.players()) if (j.distanceToSqr(p) <= 64) j.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 3, false, true));
                nivel.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 16, 2, 0.5, 2, 0);
            }
            case "eco" -> {
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 2, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0, false, true));
            }
            case "guardiao" -> p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 4, false, true));
            case "cacador" -> {
                LivingEntity alvo = null;
                for (LivingEntity e : inimigos(p, 24)) if (alvo == null || e.getMaxHealth() > alvo.getMaxHealth()) alvo = e;
                if (alvo == null) {
                    p.displayClientMessage(Component.literal("Nenhuma presa por perto.").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                alvo.getPersistentData().putLong(PRESA, agora + 12_000);
                alvo.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false));
                alvo.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 240, 0, false, true));
            }
            default -> { return; }
        }
        dados.putLong("recargaAtiva", agora + d.recargaSeg() * 1000L);
        nivel.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1f, 1.2f);
        nivel.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        p.displayClientMessage(Component.literal("✦ " + d.nome()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
    }

    private static void mostrar(ServerPlayer p) {
        Unica.Def u = Unica.def(p);
        if (u == null) { erro(p, "Você ainda não despertou uma Habilidade Única."); return; }
        Def d = TODAS.get(Unica.id(p));
        if (d == null) return;
        if (tem(p)) {
            p.sendSystemMessage(Component.literal("✦ " + d.nome() + " · Habilidade Suprema").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.literal("  Ativo (Agachar + F): " + d.ativo()).withStyle(ChatFormatting.WHITE));
            p.sendSystemMessage(Component.literal("  Recarga: " + d.recargaSeg() + " s · Passivo: " + Unica.efeito(p)).withStyle(ChatFormatting.AQUA));
        } else if (Unica.nivel(p) >= 5) {
            p.sendSystemMessage(Component.literal(u.nome() + " está pronta para a prova. Vencendo, ela se torna " + d.nome() + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
            p.sendSystemMessage(Component.literal("  Use /suprema prova em um lugar aberto. Você enfrenta o Eco da sua habilidade.").withStyle(ChatFormatting.WHITE));
        } else {
            erro(p, u.nome() + " está no nível " + Unica.nivel(p) + "/5. A prova da Habilidade Suprema abre no nível 5.");
        }
    }

    // ------------------------------------------------------------------ eventos

    private static ServerPlayer dono(Entity eco) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return null;
        try { return s.getPlayerList().getPlayer(UUID.fromString(eco.getPersistentData().getString("frx_dono"))); }
        catch (Exception e) { return null; }
    }

    /** Agachar + F (trocar de mão) dispara o efeito ativo no lugar da troca. */
    @SubscribeEvent
    public void trocarMao(LivingSwapItemsEvent.Hands ev) {
        if (ev.getEntity() instanceof ServerPlayer p && p.isShiftKeyDown() && tem(p)) {
            ev.setCanceled(true);
            usar(p);
        }
    }

    @SubscribeEvent
    public void soODono(LivingAttackEvent ev) {
        if (!ev.getEntity().getTags().contains(TAG)) return;
        Entity quem = ev.getSource().getEntity();
        if (!(quem instanceof ServerPlayer p) || !p.getUUID().toString().equals(ev.getEntity().getPersistentData().getString("frx_dono"))) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void alvo(LivingChangeTargetEvent ev) {
        if (!ev.getEntity().getTags().contains(TAG)) return;
        ServerPlayer d = dono(ev.getEntity());
        if (ev.getNewTarget() != d) ev.setNewTarget(d);
    }

    @SubscribeEvent
    public void semDrops(LivingDropsEvent ev) {
        if (ev.getEntity().getTags().contains(TAG)) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void dano(LivingHurtEvent ev) {
        if (!(ev.getSource().getEntity() instanceof ServerPlayer a) || ev.getEntity() == a) return;
        float q = ev.getAmount();
        if (ativa(a, "critico")) q *= 1.5f;
        if (ev.getEntity().getPersistentData().getLong(PRESA) > System.currentTimeMillis()) q *= 1.4f;
        if (ativa(a, "sangue")) a.heal(q * 0.3f);
        if (q != ev.getAmount()) ev.setAmount(q);
    }

    @SubscribeEvent
    public void morte(LivingDeathEvent ev) {
        if (ev.getEntity().level().isClientSide()) return;
        if (ev.getEntity().getTags().contains(TAG)) {
            ServerPlayer p = dono(ev.getEntity());
            Prova pr = p == null ? null : PROVAS.get(p.getUUID());
            if (pr != null && pr.eco().equals(ev.getEntity().getUUID())) vencer(p);
        } else if (ev.getEntity() instanceof ServerPlayer p && PROVAS.containsKey(p.getUUID())) {
            falhar(p, "Você caiu diante do próprio Eco.");
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || PROVAS.isEmpty() || ev.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = ev.getServer();
        long agora = System.currentTimeMillis();
        for (Map.Entry<UUID, Prova> x : new HashMap<>(PROVAS).entrySet()) {
            ServerPlayer p = s.getPlayerList().getPlayer(x.getKey());
            Entity e = eco(s, x.getValue());
            if (p == null) { encerrar(s, x.getKey()); continue; }
            if (e == null || !e.isAlive()) { if (PROVAS.containsKey(x.getKey())) falhar(p, "O Eco se desfez."); continue; }
            if (agora > x.getValue().fim()) { falhar(p, "O tempo acabou."); continue; }
            if (e.level() != p.level() || e.distanceToSqr(p) > 48 * 48) { falhar(p, "Você abandonou a prova."); continue; }
            long resta = (x.getValue().fim() - agora) / 1000;
            p.displayClientMessage(Component.literal("Prova da Habilidade Suprema · " + resta / 60 + ":" + String.format("%02d", resta % 60)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("suprema")
                .executes(ctx -> { mostrar(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("prova").executes(ctx -> { iniciar(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("usar").executes(ctx -> { usar(ctx.getSource().getPlayerOrException()); return 1; })));
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("suprema")
                        .then(Commands.literal("dar").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (Unica.def(p) == null) return 0;
                            Unica.dadosDe(p).putInt("abates", Math.max(Unica.abates(p), 1500));
                            vencer(p);
                            return 1;
                        }))
                        .then(Commands.literal("nivel5").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Unica.dadosDe(p).putInt("abates", Math.max(Unica.abates(p), 1500));
                            Unica.dadosDe(p).remove("esperaSuprema");
                            ctx.getSource().sendSuccess(() -> Component.literal("Habilidade Única no nível 5. Use /suprema prova."), false);
                            return 1;
                        }))
                        .then(Commands.literal("tirar").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Unica.dadosDe(p).remove("suprema");
                            Unica.dadosDe(p).remove("recargaAtiva");
                            ctx.getSource().sendSuccess(() -> Component.literal("Habilidade voltou a ser Única."), false);
                            return 1;
                        }))));
    }
}
