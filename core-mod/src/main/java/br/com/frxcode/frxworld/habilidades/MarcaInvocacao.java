package br.com.frxcode.frxworld.habilidades;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Invocações do Invocador (sombras, golens, lobos...) não podem ser confundidas com monstros:
 * - entram no time "frx_invocacoes" (roxo). O time chega ao jogo de todos: o nome fica roxo e o cliente as desenha
 *   escurecidas, como sombras (cliente/SombraInvocacao);
 * - soltam uma aura de fumaça roxa;
 * - dano entre invocações e jogadores segue a escolha do dono ("Dano em jogadores", desligado por padrão): numa dungeon,
 *   ninguém mata invocação por engano e as invocações não ferem os colegas.
 */
public class MarcaInvocacao {
    public static final String TIME = "frx_invocacoes";
    private int ticks;

    public MarcaInvocacao() {}

    private static PlayerTeam time(Scoreboard sb) {
        PlayerTeam t = sb.getPlayerTeam(TIME);
        if (t == null) {
            t = sb.addPlayerTeam(TIME);
            t.setColor(ChatFormatting.DARK_PURPLE);
            t.setSeeFriendlyInvisibles(false);
        }
        return t;
    }

    private static boolean invocacao(Entity e) { return e.getTags().contains(Habilidades.TAG_INVOCADO); }

    @SubscribeEvent
    public void aoEntrar(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || ev.isCanceled() || !invocacao(ev.getEntity())) return;
        Scoreboard sb = ev.getLevel().getScoreboard();
        sb.addPlayerToTeam(ev.getEntity().getStringUUID(), time(sb));
    }

    /** Ao sair do mundo (morte, sumiço, chunk descarregado) sai do time, para o placar não acumular lixo; ao voltar, entra de novo. */
    @SubscribeEvent
    public void aoSair(EntityLeaveLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !invocacao(ev.getEntity())) return;
        Scoreboard sb = ev.getLevel().getScoreboard();
        PlayerTeam t = sb.getPlayerTeam(TIME);
        if (t != null && t.getPlayers().contains(ev.getEntity().getStringUUID())) sb.removePlayerFromTeam(ev.getEntity().getStringUUID(), t);
    }

    /** Dono da invocação (online), ou null. */
    private static ServerPlayer dono(Entity e) {
        if (!e.getPersistentData().hasUUID("frx_dono") || e.getServer() == null) return null;
        return e.getServer().getPlayerList().getPlayer(e.getPersistentData().getUUID("frx_dono"));
    }

    /** O dono deixou o dano em jogadores ligado (menu do Exército). Dono offline conta como desligado. */
    private static boolean pvpLigado(Entity invocacao) {
        ServerPlayer d = dono(invocacao);
        return d != null && Sombras.pvp(d);
    }

    /**
     * Dano entre invocações e jogadores segue a escolha do dono ("Dano em jogadores" no menu do Exército):
     * desligado (padrão) = a invocação não fere jogadores e nenhum jogador fere a invocação; ligado = vale nos dois sentidos.
     * O próprio dono nunca fere as suas invocações, nem é ferido por elas.
     */
    @SubscribeEvent
    public void danoEntreInvocacaoEJogador(LivingAttackEvent ev) {
        Entity atacante = ev.getSource().getEntity();
        if (invocacao(ev.getEntity()) && atacante instanceof Player p) {
            boolean doDono = ev.getEntity().getPersistentData().hasUUID("frx_dono") && ev.getEntity().getPersistentData().getUUID("frx_dono").equals(p.getUUID());
            if (doDono || !pvpLigado(ev.getEntity())) ev.setCanceled(true);
            return;
        }
        if (ev.getEntity() instanceof Player vitima && atacante != null && invocacao(atacante)) {
            boolean doDono = atacante.getPersistentData().hasUUID("frx_dono") && atacante.getPersistentData().getUUID("frx_dono").equals(vitima.getUUID());
            if (doDono || !pvpLigado(atacante)) ev.setCanceled(true);
        }
    }

    /** Aura: fumaça escura com faíscas roxas em volta de cada invocação. */
    @SubscribeEvent
    public void aura(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++ticks % 10 != 3) return;
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) {
            for (UUID id : Habilidades.invocadosDe(p.getUUID())) {
                for (ServerLevel n : ev.getServer().getAllLevels()) {
                    Entity e = n.getEntity(id);
                    if (e == null || !e.isAlive()) continue;
                    double l = e.getBbWidth() * 0.6, a = e.getBbHeight();
                    n.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY() + a * 0.5, e.getZ(), 2, l, a * 0.4, l, 0.01);
                    n.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + a * 0.3, e.getZ(), 1, l, a * 0.3, l, 0.005);
                    break;
                }
            }
        }
    }
}
