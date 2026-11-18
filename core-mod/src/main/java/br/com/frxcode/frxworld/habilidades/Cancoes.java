package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bardo: a classe de suporte. Canções que ficam tocando em volta dele e fortalecem o grupo.
 *
 *  - Inspiração (passiva): jogadores a até 8 blocos de um Bardo causam 8% a mais de dano (o próprio Bardo não conta).
 *  - Canções (frx:cancao_marcha, frx:cancao_cura, frx:cancao_guerra, frx:sinfonia): uma por vez; começar outra troca a que estava tocando.
 *  - Outras mecânicas: frx:ninar, frx:zombaria, frx:eco e frx:heroi.
 */
public class Cancoes {
    private record Cancao(UUID dono, ServerLevel nivel, String tipo, HabilidadeDef h, double mult, int fimTick) { }
    private record Bonus(long fimMs, double fracao) { }

    private static final Map<UUID, Cancao> cancoes = new ConcurrentHashMap<>();
    /** Jogador → dano a mais que ele causa. */
    private static final Map<UUID, Bonus> inspirados = new ConcurrentHashMap<>();
    /** Criatura → dano a mais que ela leva, de qualquer um. */
    private static final Map<UUID, Bonus> zombados = new ConcurrentHashMap<>();

    public Cancoes() {}

    private static void aviso(ServerPlayer p, String texto, ChatFormatting cor) { p.displayClientMessage(Component.literal(texto).withStyle(cor), true); }
    private static int ticksDe(HabilidadeDef h, int minimo) { return (int) Math.max(minimo, h.duracao() * 20); }
    private static boolean bardo(ServerPlayer p) { return Progressao.temDados(p) && "BARDO".equals(Progressao.dados(p).classe()); }

    private static List<ServerPlayer> grupo(ServerPlayer p, double raio, boolean comEle) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer o : p.serverLevel().players()) if (o.isAlive() && !o.isSpectator() && (comEle || o != p) && o.distanceTo(p) <= raio) l.add(o);
        return l;
    }

    private static void notas(ServerLevel n, double x, double y, double z, int quantas, double espalhar) {
        for (int i = 0; i < quantas; i++)
            n.sendParticles(ParticleTypes.NOTE, x + (Math.random() - 0.5) * espalhar, y + Math.random() * 0.8, z + (Math.random() - 0.5) * espalhar, 0, Math.random(), 0, 0, 1);
    }

    private static void tocar(ServerLevel n, ServerPlayer p, SoundEvent som, float tom) { n.playSound(null, p.blockPosition(), som, SoundSource.PLAYERS, 1.6f, tom); }

    /** Dá a um jogador um bônus de dano por um tempo (fica o maior entre o que ele já tinha e o novo). */
    public static void inspirar(ServerPlayer a, long ms, double fracao) {
        long agora = System.currentTimeMillis();
        Bonus antes = inspirados.get(a.getUUID());
        if (antes != null && antes.fimMs() > agora && antes.fracao() > fracao) return;
        inspirados.put(a.getUUID(), new Bonus(agora + ms, fracao));
    }

    // ------------------------------------------------------------------ canções que ficam tocando

    static boolean cancao(ServerPlayer p, HabilidadeDef h, double mult, String tipo) {
        int dur = ticksDe(h, 100);
        Cancao antes = cancoes.put(p.getUUID(), new Cancao(p.getUUID(), p.serverLevel(), tipo, h, mult, p.server.getTickCount() + dur));
        ServerLevel n = p.serverLevel();
        notas(n, p.getX(), p.getY() + 1.6, p.getZ(), 12, 2);
        switch (tipo) {
            case "marcha" -> tocar(n, p, SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.4f);
            case "cura" -> tocar(n, p, SoundEvents.NOTE_BLOCK_HARP.value(), 1.0f);
            case "guerra" -> tocar(n, p, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.8f);
            default -> { tocar(n, p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f); tocar(n, p, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f); }
        }
        aviso(p, h.nome() + " tocando por " + dur / 20 + " s" + (antes != null ? " (a canção anterior parou)." : "."), ChatFormatting.LIGHT_PURPLE);
        return true;
    }

    private static void tickCancao(MinecraftServer s, Cancao c) {
        ServerPlayer p = s.getPlayerList().getPlayer(c.dono());
        int agora = s.getTickCount();
        if (p == null || !p.isAlive() || p.serverLevel() != c.nivel() || agora >= c.fimTick()) { cancoes.remove(c.dono(), c); return; }
        ServerLevel n = c.nivel();
        double raio = Math.max(4, c.h().raio());
        if (agora % 6 == 0) {
            double ang = agora * 0.15;
            for (int i = 0; i < 4; i++) {
                double a = ang + Math.PI / 2 * i;
                n.sendParticles(ParticleTypes.NOTE, p.getX() + Math.cos(a) * raio, p.getY() + 0.6, p.getZ() + Math.sin(a) * raio, 0, (agora % 24) / 24.0, 0, 0, 1);
            }
            n.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2.2, p.getZ(), 0, (agora % 24) / 24.0, 0, 0, 1);
        }
        if ((c.fimTick() - agora) % 20 != 0) return;
        boolean tudo = "sinfonia".equals(c.tipo());
        int pot = Math.max(1, c.h().potencia());
        for (ServerPlayer a : grupo(p, raio, true)) {
            if (tudo || "marcha".equals(c.tipo())) {
                a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, tudo ? 1 : Math.max(0, pot - 1), true, true));
                a.addEffect(new MobEffectInstance(MobEffects.JUMP, 50, 0, true, false));
            }
            if (tudo) a.heal((float) (pot * Talentos.fator(p, Talentos.Atributo.CURA)));
            else if ("cura".equals(c.tipo())) a.heal((float) (Math.abs(c.h().dano()) * c.mult() * Talentos.fator(p, Talentos.Atributo.CURA)));
            if (tudo || "cura".equals(c.tipo())) n.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + 1.8, a.getZ(), 1, 0.2, 0.1, 0.2, 0);
            if (tudo) inspirar(a, 1500, 0.20);
            else if ("guerra".equals(c.tipo())) inspirar(a, 1500, pot * 0.05);
        }
        if (!tudo) return;
        for (LivingEntity e : n.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(raio, 4, raio), x -> x.isAlive() && Habilidades.inimigo(p, x) && x.distanceTo(p) <= raio + 0.5)) {
            Habilidades.ferir(p, e, c.h().dano() * c.mult(), c.h());
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0));
            notas(n, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 2, 0.6);
        }
        tocar(n, p, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f + ((c.fimTick() - agora) / 20 % 6) * 0.15f);
    }

    // ------------------------------------------------------------------ frx:ninar — faz dormir quem estiver em volta

    static boolean ninar(ServerPlayer p, HabilidadeDef h) {
        ServerLevel n = p.serverLevel();
        double raio = Math.max(3, h.raio());
        int dur = ticksDe(h, 40), quantos = 0;
        for (Mob m : n.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(raio, 3, raio), x -> x.isAlive() && Habilidades.inimigo(p, x) && x.distanceTo(p) <= raio + 0.5)) {
            int d = m.getType().is(Tags.EntityTypes.BOSSES) ? dur / 3 : dur;
            m.setTarget(null);
            m.getNavigation().stop();
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d, 9));
            m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 4));
            n.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
            notas(n, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 2, 0.5);
            quantos++;
        }
        tocar(n, p, SoundEvents.NOTE_BLOCK_HARP.value(), 0.6f);
        aviso(p, quantos == 0 ? "Ninguém por perto para ouvir a canção de ninar." : quantos + (quantos == 1 ? " inimigo adormeceu" : " inimigos adormeceram") + " por " + dur / 20 + " s.", ChatFormatting.LIGHT_PURPLE);
        return quantos > 0;
    }

    // ------------------------------------------------------------------ frx:zombaria — o alvo passa a levar mais dano de todos

    static boolean zombaria(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(6, h.alcance()), 0.9, false);
        if (alvo == null) { aviso(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        int dur = ticksDe(h, 80);
        double fracao = Math.max(0.05, Math.min(0.5, h.potencia() * 0.1));
        if (h.dano() > 0) Habilidades.ferir(p, alvo, h.dano() * mult, h);
        zombados.put(alvo.getUUID(), new Bonus(System.currentTimeMillis() + dur * 50L, fracao));
        alvo.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
        alvo.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 0));
        ServerLevel n = p.serverLevel();
        Habilidades.linha(n, p.getEyePosition(), alvo.getBoundingBox().getCenter(), ParticleTypes.NOTE);
        n.sendParticles(ParticleTypes.ANGRY_VILLAGER, alvo.getX(), alvo.getY() + alvo.getBbHeight() + 0.2, alvo.getZ(), 5, 0.3, 0.1, 0.3, 0);
        tocar(n, p, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.6f);
        aviso(p, "Zombaria: o alvo leva " + Math.round(fracao * 100) + "% a mais de dano de todos por " + dur / 20 + " s.", ChatFormatting.LIGHT_PURPLE);
        Habilidades.marcarCombate(p);
        return true;
    }

    // ------------------------------------------------------------------ frx:eco — encurta as recargas do grupo e devolve recurso

    static boolean eco(ServerPlayer p, HabilidadeDef h) {
        double fracao = Math.max(0.1, Math.min(0.8, h.potencia() * 0.1));
        List<ServerPlayer> alvos = grupo(p, Math.max(4, h.raio()), false);
        ServerLevel n = p.serverLevel();
        if (alvos.isEmpty()) {
            // sozinho: vale para o próprio Bardo, pela metade (menos esta habilidade, que ainda nem entrou em recarga)
            Habilidades.encurtarRecargas(p, fracao / 2);
            notas(n, p.getX(), p.getY() + 1.6, p.getZ(), 8, 1.5);
            tocar(n, p, SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f);
            aviso(p, "Sem aliados por perto: suas recargas encurtaram " + Math.round(fracao * 50) + "%.", ChatFormatting.LIGHT_PURPLE);
            return true;
        }
        for (ServerPlayer a : alvos) {
            Habilidades.encurtarRecargas(a, fracao);
            Habilidades.darRecurso(a, h.potencia() * 5);
            notas(n, a.getX(), a.getY() + 1.8, a.getZ(), 6, 1);
            aviso(a, p.getGameProfile().getName() + " tocou o Refrão: suas recargas encurtaram " + Math.round(fracao * 100) + "%.", ChatFormatting.LIGHT_PURPLE);
        }
        tocar(n, p, SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f);
        aviso(p, "Refrão: " + alvos.size() + (alvos.size() == 1 ? " aliado" : " aliados") + " com recargas " + Math.round(fracao * 100) + "% mais curtas.", ChatFormatting.LIGHT_PURPLE);
        return true;
    }

    // ------------------------------------------------------------------ frx:heroi — um aliado vira o herói da canção

    static boolean heroi(ServerPlayer p, HabilidadeDef h) {
        LivingEntity mira = Habilidades.mirar(p, Math.max(8, h.alcance()), 1.0, true);
        ServerPlayer a = mira instanceof ServerPlayer outro ? outro : p;
        int dur = ticksDe(h, 100);
        double fracao = Math.max(0.1, Math.min(0.6, h.potencia() * 0.1));
        inspirar(a, dur * 50L, fracao);
        a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1));
        a.addEffect(new MobEffectInstance(MobEffects.REGENERATION, dur, 1));
        a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, dur, 1));
        a.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false));
        ServerLevel n = p.serverLevel();
        if (a != p) Habilidades.linha(n, p.getEyePosition(), a.getBoundingBox().getCenter(), ParticleTypes.NOTE);
        notas(n, a.getX(), a.getY() + 1.8, a.getZ(), 14, 1.5);
        tocar(n, p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.3f);
        n.playSound(null, a.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.5f, 1.4f);
        if (a != p) aviso(a, p.getGameProfile().getName() + " canta os seus feitos: +" + Math.round(fracao * 100) + "% de dano por " + dur / 20 + " s.", ChatFormatting.GOLD);
        aviso(p, "Canção do Herói em " + (a == p ? "você" : a.getGameProfile().getName()) + ": +" + Math.round(fracao * 100) + "% de dano por " + dur / 20 + " s.", ChatFormatting.GOLD);
        return true;
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        for (Cancao c : cancoes.values()) {
            try { tickCancao(ev.getServer(), c); } catch (Exception e) { cancoes.remove(c.dono(), c); }
        }
        if (ev.getServer().getTickCount() % 200 == 0) {
            long agora = System.currentTimeMillis();
            inspirados.values().removeIf(b -> b.fimMs() < agora);
            zombados.values().removeIf(b -> b.fimMs() < agora);
        }
    }

    @SubscribeEvent
    public void aoFerir(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide() || ev.getAmount() <= 0) return;
        long agora = System.currentTimeMillis();
        double fator = 1;
        Bonus z = zombados.get(vitima.getUUID());
        if (z != null && z.fimMs() > agora) fator += z.fracao();
        if (ev.getSource().getEntity() instanceof ServerPlayer a && a != vitima) {
            Bonus b = inspirados.get(a.getUUID());
            if (b != null && b.fimMs() > agora) fator += b.fracao();
            // Inspiração: algum Bardo a até 8 blocos (que não o próprio atacante)
            for (ServerPlayer o : a.serverLevel().players()) {
                if (o != a && o.isAlive() && o.distanceTo(a) <= 8 && bardo(o)) { fator += 0.08; break; }
            }
        }
        if (fator != 1) ev.setAmount((float) (ev.getAmount() * fator));
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        UUID id = ev.getEntity().getUUID();
        cancoes.remove(id);
        inspirados.remove(id);
    }
}
