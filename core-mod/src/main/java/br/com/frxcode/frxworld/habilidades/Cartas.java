package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cartomante (original do FRX-WORLD): a classe que joga com a sorte.
 * O jogador tem uma mão de 3 cartas sorteadas. Toda habilidade de dano gasta a carta da vez (a primeira da mão)
 * e ganha o efeito do naipe; uma carta nova entra no fim da mão.
 *   Espadas: +40% de dano · Copas: cura 20% do dano causado · Ouros: devolve 60% da mana gasta ·
 *   Paus: prende o alvo por 2 s · Coringa (raro): os quatro efeitos de uma vez.
 * A sorte decide o que vem; a habilidade está em escolher com que golpe gastar cada carta, e em embaralhar na hora certa.
 */
public class Cartas {
    public enum Naipe {
        ESPADAS("♠", "Espadas", ChatFormatting.RED), COPAS("♥", "Copas", ChatFormatting.LIGHT_PURPLE),
        OUROS("♦", "Ouros", ChatFormatting.GOLD), PAUS("♣", "Paus", ChatFormatting.GREEN), CORINGA("★", "Coringa", ChatFormatting.AQUA);
        final String simbolo, nome; final ChatFormatting cor;
        Naipe(String s, String n, ChatFormatting c) { simbolo = s; nome = n; cor = c; }
    }

    private static final Map<UUID, Deque<Naipe>> MAOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Naipe> ATUAL = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> REEMBOLSO = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SORTE = new ConcurrentHashMap<>();   // Maré de Sorte ativa até (ms)

    public static boolean cartomante(ServerPlayer p) { return Progressao.temDados(p) && "CARTOMANTE".equals(Progressao.dados(p).classe()); }

    private static Naipe sortear(ServerPlayer p) {
        if (Despertar.maoPerfeita(p)) return Naipe.CORINGA;
        boolean mare = SORTE.getOrDefault(p.getUUID(), 0L) > System.currentTimeMillis();
        if (ThreadLocalRandom.current().nextDouble() < (mare ? 0.35 : 0.08)) return Naipe.CORINGA;
        return Naipe.values()[ThreadLocalRandom.current().nextInt(4)];
    }

    private static Deque<Naipe> mao(ServerPlayer p) {
        Deque<Naipe> m = MAOS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        while (m.size() < 3) m.addLast(sortear(p));
        return m;
    }

    /** A habilidade gasta a carta da vez? (as de dano ou cura; reforços, deslocamentos sem dano e as de mexer no baralho não gastam) */
    private static boolean gasta(HabilidadeDef h) {
        return "CARTOMANTE".equals(h.classe()) && h.dano() != 0 && !"BUFF".equals(h.tipo()) && !h.efeito().equals("frx:carta_mao");
    }

    /** Antes de executar: separa a carta da vez. A "Casa Cheia" vale como Coringa sem gastar a mão. */
    static void antes(ServerPlayer p, HabilidadeDef h) {
        if (!"CARTOMANTE".equals(h.classe())) return;
        if (h.efeito().equals("frx:carta_final")) { ATUAL.put(p.getUUID(), Naipe.CORINGA); return; }
        if (gasta(h)) ATUAL.put(p.getUUID(), mao(p).peekFirst());
    }

    /** Depois de executar: se deu certo, a carta sai da mão e entra uma nova no fim. */
    static void depois(ServerPlayer p, HabilidadeDef h, boolean ok) {
        Naipe c = ATUAL.remove(p.getUUID());
        if (c == null || !ok) return;
        if (c == Naipe.OUROS || c == Naipe.CORINGA) REEMBOLSO.put(p.getUUID(), 0.6);
        if (!h.efeito().equals("frx:carta_final")) {
            Deque<Naipe> m = mao(p);
            m.pollFirst();
            m.addLast(sortear(p));
        }
        // o efeito da carta gasta aparece na barra de ação; a mão volta a aparecer sozinha em até 2 s
        p.displayClientMessage(Component.literal(c.simbolo + " " + c.nome + ": " + efeito(c)).withStyle(c.cor, ChatFormatting.BOLD), true);
    }

    private static String efeito(Naipe c) {
        return switch (c) {
            case ESPADAS -> "+40% de dano";
            case COPAS -> "o golpe curou você";
            case OUROS -> "60% da mana voltou";
            case PAUS -> "o alvo ficou preso";
            case CORINGA -> "os quatro naipes de uma vez!";
        };
    }

    /** Chamado depois de a mana ser descontada: Ouros e Coringa devolvem parte. */
    static void aposCusto(ServerPlayer p, double custo) {
        Double f = REEMBOLSO.remove(p.getUUID());
        if (f == null || custo <= 0 || !Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        d.definirRecurso(d.recurso() + custo * f);
    }

    /** Multiplicador de dano da carta da vez (Espadas e Coringa). */
    static double fatorDano(ServerPlayer p) {
        Naipe c = ATUAL.get(p.getUUID());
        return c == Naipe.ESPADAS || c == Naipe.CORINGA ? 1.4 : 1;
    }

    /** Efeitos no acerto: Copas cura quem lançou, Paus prende o alvo. */
    static void aoFerir(ServerPlayer p, LivingEntity alvo, double dano) {
        Naipe c = ATUAL.get(p.getUUID());
        if (c == null) return;
        if (c == Naipe.COPAS || c == Naipe.CORINGA) p.heal((float) Math.max(1, dano * 0.2));
        if (c == Naipe.PAUS || c == Naipe.CORINGA) {
            alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6, false, true));
            alvo.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, true));
        }
    }

    /** A mão na barra de ação: a carta da vez vem entre colchetes. */
    static void mostrar(ServerPlayer p) {
        MutableComponent t = Component.literal("Mão: ").withStyle(ChatFormatting.GRAY);
        int i = 0;
        for (Naipe c : mao(p)) {
            t.append(Component.literal(i == 0 ? "[" + c.simbolo + " " + c.nome + "]" : c.simbolo + " " + c.nome).withStyle(i == 0 ? new ChatFormatting[]{c.cor, ChatFormatting.BOLD} : new ChatFormatting[]{c.cor}));
            if (i++ < 2) t.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
        }
        p.displayClientMessage(t, true);
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 40 != 0 || !p.isAlive() || !cartomante(p)) return;
        mostrar(p);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        UUID id = ev.getEntity().getUUID();
        MAOS.remove(id); ATUAL.remove(id); REEMBOLSO.remove(id); SORTE.remove(id);
    }

    // ------------------------------------------------------------------ habilidades próprias (frx:carta_*)

    /** Embaralhar: troca a mão inteira por três cartas novas. */
    static boolean embaralhar(ServerPlayer p, HabilidadeDef h) {
        Deque<Naipe> m = MAOS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        m.clear();
        mao(p);
        Efeitos.aura(p, "ARCANO");
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2f, 1.3f);
        mostrar(p);
        return true;
    }

    /** Maré de Sorte: por alguns segundos o Coringa sai muito mais (35% em vez de 8%). */
    static boolean mare(ServerPlayer p, HabilidadeDef h) {
        SORTE.put(p.getUUID(), System.currentTimeMillis() + (long) (Math.max(3, h.duracao()) * 1000));
        Efeitos.aura(p, "SAGRADO");
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.8f);
        p.displayClientMessage(Component.literal("★ Maré de Sorte: o Coringa aparece muito mais por " + (int) Math.max(3, h.duracao()) + " s.").withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    /** Cara ou Coroa: cara, o alvo leva o dobro; coroa, leva metade, você se fere um pouco e ganha força e velocidade. */
    static boolean moeda(ServerPlayer p, HabilidadeDef h, double mult) {
        LivingEntity alvo = Habilidades.mirar(p, Math.max(1, h.alcance()), 0.9, false);
        if (alvo == null) { p.displayClientMessage(Component.literal("Nenhum alvo na mira.").withStyle(ChatFormatting.GRAY), true); return false; }
        boolean cara = ThreadLocalRandom.current().nextBoolean();
        Efeitos.conjurar(p, "SAGRADO");
        Efeitos.golpe(alvo, cara ? "SAGRADO" : "SOMBRA");
        if (cara) {
            Habilidades.ferir(p, alvo, h.dano() * mult * 2, h);
            p.displayClientMessage(Component.literal("Cara! O golpe saiu em dobro.").withStyle(ChatFormatting.GOLD), false);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.9f);
        } else {
            Habilidades.ferir(p, alvo, h.dano() * mult * 0.5, h);
            p.hurt(p.damageSources().magic(), 2f);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 1));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1));
            p.displayClientMessage(Component.literal("Coroa! O azar mordeu, mas você ficou mais forte por 6 s.").withStyle(ChatFormatting.DARK_PURPLE), false);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.7f);
        }
        return true;
    }

    /** Mão Cheia: joga as três cartas de uma vez, cada uma num inimigo à frente (repete o alvo se houver menos de três), cada qual com o seu naipe. */
    static boolean maoCheia(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel n = p.serverLevel();
        double alcance = Math.max(4, h.alcance());
        Vec3 olho = p.getEyePosition(), dir = p.getLookAngle();
        List<LivingEntity> alvos = new ArrayList<>(n.getEntitiesOfClass(LivingEntity.class, new AABB(olho, olho).inflate(alcance),
                e -> e != p && e.isAlive() && Habilidades.inimigo(p, e) && e.distanceTo(p) <= alcance
                        && e.getBoundingBox().getCenter().subtract(olho).normalize().dot(dir) > 0.5 && p.hasLineOfSight(e)));
        if (alvos.isEmpty()) { p.displayClientMessage(Component.literal("Nenhum inimigo à frente.").withStyle(ChatFormatting.GRAY), true); return false; }
        alvos.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        Efeitos.conjurar(p, "ARCANO");
        List<Naipe> cartas = new ArrayList<>(mao(p));
        try {
            for (int i = 0; i < cartas.size(); i++) {
                LivingEntity alvo = alvos.get(i % alvos.size());
                if (!alvo.isAlive()) continue;
                Naipe c = cartas.get(i);
                ATUAL.put(p.getUUID(), c);
                Efeitos.projetil(n, olho.add(dir.scale(0.8)), alvo.getBoundingBox().getCenter(), elemento(c));
                Habilidades.ferir(p, alvo, h.dano() * mult, h);
                if (c == Naipe.OUROS || c == Naipe.CORINGA) REEMBOLSO.merge(p.getUUID(), 0.2, Double::sum);
            }
        } finally { ATUAL.remove(p.getUUID()); }
        Deque<Naipe> m = MAOS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        m.clear();
        mao(p);
        Efeitos.somImpacto(n, alvos.get(0).position(), "ARCANO", false);
        mostrar(p);
        return true;
    }

    private static String elemento(Naipe c) {
        return switch (c) { case ESPADAS -> "FOGO"; case COPAS -> "SAGRADO"; case OUROS -> "RAIO"; case PAUS -> "NATUREZA"; case CORINGA -> "ARCANO"; };
    }
}
