package br.com.frxcode.frxworld.habilidades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Efeitos visuais das habilidades: em vez de um punhado de partículas soltas, pequenas animações de alguns ticks
 * (círculo rúnico no chão, onda de choque, projétil com rastro em espiral, convergência no alvo, aura que sobe).
 * Tudo com partículas e sons do próprio jogo, coloridas pelo elemento da habilidade; o servidor desenha e
 * qualquer jogador por perto vê. Os desenhos são originais do FRX-WORLD.
 */
public class Efeitos {
    /** Cores e partícula de destaque de um elemento. */
    private record Paleta(DustParticleOptions forte, DustParticleOptions clara, ParticleOptions brilho, ParticleOptions fumaca) {}

    private static DustParticleOptions po(float r, float g, float b, float tam) { return new DustParticleOptions(new Vector3f(r, g, b), tam); }

    private static Paleta paleta(String elemento) {
        return switch (elemento == null ? "" : elemento) {
            case "FOGO" -> new Paleta(po(1f, 0.42f, 0.08f, 1.5f), po(1f, 0.85f, 0.3f, 1.1f), ParticleTypes.FLAME, ParticleTypes.LAVA);
            case "GELO" -> new Paleta(po(0.45f, 0.8f, 1f, 1.5f), po(0.92f, 0.98f, 1f, 1.1f), ParticleTypes.SNOWFLAKE, ParticleTypes.CLOUD);
            case "RAIO" -> new Paleta(po(1f, 0.93f, 0.35f, 1.4f), po(0.7f, 0.82f, 1f, 1.0f), ParticleTypes.ELECTRIC_SPARK, ParticleTypes.FIREWORK);
            case "SAGRADO" -> new Paleta(po(1f, 0.86f, 0.4f, 1.5f), po(1f, 1f, 0.85f, 1.1f), ParticleTypes.END_ROD, ParticleTypes.GLOW);
            case "SOMBRA" -> new Paleta(po(0.42f, 0.12f, 0.62f, 1.5f), po(0.12f, 0.05f, 0.18f, 1.3f), ParticleTypes.SOUL, ParticleTypes.SMOKE);
            case "NATUREZA" -> new Paleta(po(0.25f, 0.8f, 0.28f, 1.5f), po(0.72f, 1f, 0.4f, 1.1f), ParticleTypes.HAPPY_VILLAGER, ParticleTypes.COMPOSTER);
            case "ARCANO" -> new Paleta(po(0.72f, 0.3f, 1f, 1.5f), po(0.45f, 0.65f, 1f, 1.1f), ParticleTypes.WITCH, ParticleTypes.ENCHANT);
            default -> new Paleta(po(0.92f, 0.92f, 0.95f, 1.3f), po(0.65f, 0.68f, 0.72f, 1.0f), ParticleTypes.CRIT, ParticleTypes.POOF);
        };
    }

    // ------------------------------------------------------------------ agenda (animações de alguns ticks)

    private static final class Tarefa { int espera; final Runnable acao; Tarefa(int e, Runnable a) { espera = e; acao = a; } }
    private static final List<Tarefa> AGENDA = new ArrayList<>();
    private static final int LIMITE = 4000;   // trava de segurança: nunca mais que isto na fila

    private static void em(int ticks, Runnable acao) {
        if (ticks <= 0) { acao.run(); return; }
        if (AGENDA.size() < LIMITE) AGENDA.add(new Tarefa(ticks, acao));
    }

    @SubscribeEvent
    public void aoTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || AGENDA.isEmpty()) return;
        List<Runnable> agora = new ArrayList<>();
        for (Iterator<Tarefa> it = AGENDA.iterator(); it.hasNext(); ) {
            Tarefa t = it.next();
            if (--t.espera <= 0) { agora.add(t.acao); it.remove(); }
        }
        for (Runnable r : agora) { try { r.run(); } catch (Throwable ignorado) { /* um efeito nunca derruba o servidor */ } }
    }

    // ------------------------------------------------------------------ peças

    private static void ponto(ServerLevel n, ParticleOptions p, double x, double y, double z) { n.sendParticles(p, x, y, z, 1, 0, 0, 0, 0); }
    private static void nuvem(ServerLevel n, ParticleOptions p, Vec3 c, int qtd, double espalha, double vel) { n.sendParticles(p, c.x, c.y, c.z, qtd, espalha, espalha, espalha, vel); }
    private static void som(ServerLevel n, Vec3 c, SoundEvent s, float vol, float tom) { n.playSound(null, BlockPos.containing(c), s, SoundSource.PLAYERS, vol, tom); }

    /** Anel deitado no chão. */
    private static void anel(ServerLevel n, ParticleOptions p, Vec3 c, double raio, int pontos, double fase) {
        for (int i = 0; i < pontos; i++) {
            double a = fase + Math.PI * 2 * i / pontos;
            ponto(n, p, c.x + Math.cos(a) * raio, c.y, c.z + Math.sin(a) * raio);
        }
    }

    /** Segmento de reta. */
    private static void traco(ServerLevel n, ParticleOptions p, Vec3 a, Vec3 b, double passo) {
        Vec3 d = b.subtract(a);
        int k = Math.max(1, (int) (d.length() / passo));
        for (int i = 0; i <= k; i++) { Vec3 q = a.add(d.scale(i / (double) k)); ponto(n, p, q.x, q.y, q.z); }
    }

    /** Estrela de 'pontas' pontas inscrita no círculo (liga cada vértice ao segundo seguinte). */
    private static void estrela(ServerLevel n, ParticleOptions p, Vec3 c, double raio, int pontas, double fase) {
        Vec3[] v = new Vec3[pontas];
        for (int i = 0; i < pontas; i++) { double a = fase + Math.PI * 2 * i / pontas; v[i] = new Vec3(c.x + Math.cos(a) * raio, c.y, c.z + Math.sin(a) * raio); }
        for (int i = 0; i < pontas; i++) traco(n, p, v[i], v[(i + 2) % pontas], 0.45);
    }

    /** Onda de choque: anel que se abre do centro até o raio. */
    private static void onda(ServerLevel n, Paleta pal, Vec3 c, double raio, int atraso) {
        int passos = Math.max(3, (int) Math.min(8, raio * 1.2));
        for (int s = 1; s <= passos; s++) {
            final double r = raio * s / passos; final int qual = s;
            em(atraso + s, () -> { anel(n, qual % 2 == 0 ? pal.clara() : pal.forte(), c.add(0, 0.15, 0), r, (int) Math.max(10, r * 7), qual * 0.4); });
        }
    }

    /** Coluna que sobe num ponto. */
    private static void coluna(ServerLevel n, Paleta pal, Vec3 base, double altura, int atraso) {
        int passos = (int) Math.max(3, altura * 2);
        for (int s = 0; s < passos; s++) {
            final double y = altura * s / passos;
            em(atraso + s / 2, () -> { ponto(n, pal.forte(), base.x, base.y + y, base.z); ponto(n, pal.brilho(), base.x, base.y + y + 0.1, base.z); });
        }
    }

    // ------------------------------------------------------------------ efeitos por tipo de habilidade

    /** Gesto de conjurar: anel aos pés e duas fitas subindo em espiral em volta de quem lança. */
    public static void conjurar(Entity quem, String elemento) {
        if (!(quem.level() instanceof ServerLevel n)) return;
        Paleta pal = paleta(elemento);
        Vec3 pe = quem.position();
        anel(n, pal.forte(), pe.add(0, 0.1, 0), 0.9, 14, 0);
        for (int t = 0; t < 6; t++) {
            final int k = t;
            em(t, () -> {
                Vec3 c = quem.position();
                for (int fita = 0; fita < 2; fita++) {
                    double a = k * 0.9 + fita * Math.PI;
                    ponto(n, fita == 0 ? pal.forte() : pal.clara(), c.x + Math.cos(a) * 0.75, c.y + 0.2 + k * 0.32, c.z + Math.sin(a) * 0.75);
                }
            });
        }
    }

    /** Projétil: uma cabeça brilhante corre de 'de' até 'ate' deixando um rastro em espiral, e estoura na chegada. */
    public static void projetil(ServerLevel n, Vec3 de, Vec3 ate, String elemento) {
        Paleta pal = paleta(elemento);
        Vec3 d = ate.subtract(de);
        double dist = d.length();
        if (dist < 0.3) { estouro(n, ate, elemento, 0.8); return; }
        Vec3 dir = d.normalize();
        // dois vetores perpendiculares ao caminho, para a espiral
        Vec3 lado = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 cima = dir.cross(lado).normalize();
        double porTick = 4.0;                       // blocos por tick: chega em menos de um quarto de segundo na maioria dos alcances
        int ticks = (int) Math.ceil(dist / porTick);
        for (int t = 0; t < ticks; t++) {
            final double ini = t * porTick, fim = Math.min(dist, (t + 1) * porTick);
            em(t, () -> {
                for (double s = ini; s < fim; s += 0.35) {
                    Vec3 q = de.add(dir.scale(s));
                    ponto(n, pal.forte(), q.x, q.y, q.z);
                    double a = s * 2.4;
                    Vec3 e = q.add(lado.scale(Math.cos(a) * 0.28)).add(cima.scale(Math.sin(a) * 0.28));
                    ponto(n, pal.clara(), e.x, e.y, e.z);
                }
                Vec3 cabeca = de.add(dir.scale(fim));
                nuvem(n, pal.brilho(), cabeca, 3, 0.08, 0.01);
            });
        }
        em(ticks, () -> estouro(n, ate, elemento, 1.0));
    }

    /** Estouro num ponto: clarão, faíscas para fora e um anel curto. */
    public static void estouro(ServerLevel n, Vec3 c, String elemento, double tamanho) {
        Paleta pal = paleta(elemento);
        nuvem(n, pal.brilho(), c, (int) (14 * tamanho), 0.15 * tamanho, 0.12);
        nuvem(n, pal.forte(), c, (int) (10 * tamanho), 0.35 * tamanho, 0);
        nuvem(n, pal.fumaca(), c, (int) (6 * tamanho), 0.25 * tamanho, 0.03);
        em(1, () -> anel(n, pal.clara(), c, 0.7 * tamanho, 12, 0));
        em(2, () -> anel(n, pal.forte(), c, 1.2 * tamanho, 16, 0.3));
    }

    /** Golpe em alvo único: partículas convergem para o alvo, depois estouro e uma coluna curta. */
    public static void golpe(Entity alvo, String elemento) {
        if (!(alvo.level() instanceof ServerLevel n)) return;
        Paleta pal = paleta(elemento);
        double meio = alvo.getBbHeight() * 0.55;
        for (int t = 0; t < 4; t++) {
            final double r = 2.2 - t * 0.55; final int k = t;
            em(t, () -> {
                Vec3 c = alvo.position().add(0, meio, 0);
                for (int i = 0; i < 8; i++) {
                    double a = k * 0.5 + Math.PI * 2 * i / 8;
                    ponto(n, i % 2 == 0 ? pal.forte() : pal.clara(), c.x + Math.cos(a) * r, c.y + Math.sin(a * 2 + k) * 0.5, c.z + Math.sin(a) * r);
                }
            });
        }
        em(4, () -> { Vec3 c = alvo.position().add(0, meio, 0); estouro(n, c, elemento, 1.0); coluna(n, pal, alvo.position(), alvo.getBbHeight() + 1.2, 0); });
    }

    /** Área: círculo rúnico no chão (anel duplo e estrela girando), onda de choque, colunas na borda e estouro no centro. */
    public static void area(ServerLevel n, Vec3 centro, double raio, String elemento, boolean cura) {
        Paleta pal = paleta(elemento);
        Vec3 chao = centro.add(0, 0.12, 0);
        int pontosAnel = (int) Math.max(16, raio * 9);
        anel(n, pal.forte(), chao, raio, pontosAnel, 0);
        anel(n, pal.clara(), chao, raio * 0.82, (int) (pontosAnel * 0.8), 0.2);
        int pontas = raio >= 4 ? 6 : 5;
        estrela(n, pal.clara(), chao, raio * 0.82, pontas, 0);
        em(2, () -> estrela(n, pal.forte(), chao, raio * 0.82, pontas, Math.PI / pontas));
        onda(n, pal, centro, raio, 2);
        for (int i = 0; i < pontas; i++) {
            double a = Math.PI * 2 * i / pontas;
            coluna(n, pal, new Vec3(centro.x + Math.cos(a) * raio, centro.y, centro.z + Math.sin(a) * raio), cura ? 1.6 : 2.4, 3);
        }
        em(4, () -> {
            nuvem(n, pal.brilho(), centro.add(0, 0.8, 0), (int) Math.min(60, 12 + raio * 8), raio * 0.45, cura ? 0.02 : 0.08);
            if (cura) nuvem(n, ParticleTypes.HEART, centro.add(0, 1.2, 0), (int) Math.min(12, 3 + raio), raio * 0.4, 0.02);
            else nuvem(n, pal.fumaca(), centro.add(0, 0.4, 0), (int) Math.min(30, 6 + raio * 4), raio * 0.4, 0.04);
        });
        em(7, () -> anel(n, pal.clara(), chao, raio, pontosAnel, 0.1));
    }

    /** Deslocamento: anel na partida, rastro de imagens ao longo do caminho e anel na chegada. */
    public static void rastro(ServerLevel n, Vec3 de, Vec3 ate, String elemento) {
        Paleta pal = paleta(elemento);
        anel(n, pal.forte(), de.add(0, 0.1, 0), 0.8, 12, 0);
        nuvem(n, pal.fumaca(), de.add(0, 0.9, 0), 10, 0.3, 0.02);
        Vec3 d = ate.subtract(de);
        int k = (int) Math.max(2, Math.min(10, d.length() / 1.2));
        for (int i = 1; i <= k; i++) {
            final Vec3 q = de.add(d.scale(i / (double) k));
            em(i / 2, () -> {
                for (double y = 0.2; y <= 1.8; y += 0.4) ponto(n, pal.clara(), q.x, q.y + y, q.z);
                ponto(n, pal.brilho(), q.x, q.y + 1, q.z);
            });
        }
        em(k / 2 + 1, () -> { anel(n, pal.forte(), ate.add(0, 0.1, 0), 0.9, 14, 0.2); nuvem(n, pal.brilho(), ate.add(0, 1, 0), 12, 0.3, 0.06); });
    }

    /** Aura de bênção ou reforço: anel aos pés e três fitas subindo em espiral por um segundo. */
    public static void aura(Entity quem, String elemento) {
        if (!(quem.level() instanceof ServerLevel n)) return;
        Paleta pal = paleta(elemento);
        anel(n, pal.forte(), quem.position().add(0, 0.1, 0), 1.0, 16, 0);
        double alt = quem.getBbHeight() + 0.6;
        for (int t = 0; t < 12; t++) {
            final int k = t;
            em(t, () -> {
                Vec3 c = quem.position();
                for (int fita = 0; fita < 3; fita++) {
                    double a = k * 0.55 + fita * Math.PI * 2 / 3;
                    ponto(n, fita == 0 ? pal.forte() : pal.clara(), c.x + Math.cos(a) * 0.8, c.y + alt * k / 12, c.z + Math.sin(a) * 0.8);
                }
                if (k == 11) nuvem(n, pal.brilho(), c.add(0, alt, 0), 10, 0.25, 0.04);
            });
        }
    }

    /** Invocação: círculo no chão, coluna de luz e a criatura surge no estouro. */
    public static void invocacao(ServerLevel n, Vec3 pos, String elemento) {
        Paleta pal = paleta(elemento);
        Vec3 chao = pos.add(0, 0.1, 0);
        anel(n, pal.forte(), chao, 1.1, 16, 0);
        estrela(n, pal.clara(), chao, 1.1, 5, 0);
        coluna(n, pal, pos, 2.6, 1);
        em(3, () -> { nuvem(n, pal.brilho(), pos.add(0, 1, 0), 16, 0.35, 0.05); nuvem(n, pal.fumaca(), pos.add(0, 0.5, 0), 8, 0.3, 0.02); });
    }

    /** Som em camadas na chegada de um efeito (grave do impacto e o timbre do elemento por cima). */
    public static void somImpacto(ServerLevel n, Vec3 c, String elemento, boolean grande) {
        som(n, c, grande ? SoundEvents.GENERIC_EXPLODE : SoundEvents.PLAYER_ATTACK_STRONG, grande ? 0.55f : 0.8f, grande ? 1.25f : 0.9f);
        SoundEvent timbre = switch (elemento == null ? "" : elemento) {
            case "FOGO" -> SoundEvents.FIRECHARGE_USE;
            case "GELO" -> SoundEvents.GLASS_BREAK;
            case "RAIO" -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case "SAGRADO" -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case "SOMBRA" -> SoundEvents.SOUL_ESCAPE;
            case "NATUREZA" -> SoundEvents.AZALEA_LEAVES_BREAK;
            case "ARCANO" -> SoundEvents.ILLUSIONER_CAST_SPELL;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
        som(n, c, timbre, 0.9f, 1.1f);
    }
}
