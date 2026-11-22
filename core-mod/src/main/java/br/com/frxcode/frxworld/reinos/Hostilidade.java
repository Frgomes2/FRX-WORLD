package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.clas.Clas;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O lado ruim da reputação. Malvisto: preços mais caros. Hostil: lojistas não atendem e os guardas atacam
 * dentro da capital (banco, Guilda, clãs e o rei continuam atendendo). Inimigo: recompensa pela cabeça, paga
 * a quem derrotar o jogador (só com PvP ligado, nunca entre membros do mesmo clã).
 * A volta: multa paga ao rei, missões e abates perto da capital, e 2 pontos a cada 5 minutos de jogo.
 */
public class Hostilidade {
    /** Cobre cobrado por ponto negativo na multa do rei. */
    private static final int MULTA_POR_PONTO = 30;
    private static final Map<UUID, Long> AVISO_GUARDA = new HashMap<>(), AVISO_CAPITAL = new HashMap<>(), GOLPE = new HashMap<>(), ALERTA = new HashMap<>();

    private static boolean livre(ServerPlayer p) { return p.isCreative() || p.isSpectator(); }
    static boolean guarda(Entity e) {
        return e.getTags().contains(NpcsReino.TAG) && e.getPersistentData().getString("frx_papel").startsWith("guarda");
    }
    private static String nomeReino(String id) { Reino r = Reputacao.reino(id); return r != null ? r.nome() : id; }

    public static long recompensa(ServerPlayer p) { return 3000 + (Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1) * 150L; }
    public static long multa(ServerPlayer p, String reino) { return Math.max(0, -Reputacao.pontos(p, reino)) * (long) MULTA_POR_PONTO; }

    /** Chamado pela reputação quando o grau de hostilidade muda. */
    static void mudou(ServerPlayer p, String reino, int antes, int depois) {
        if (depois == 3 && antes < 3 && p.server.isPvpAllowed()) {
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("☠ PROCURADO: " + p.getGameProfile().getName() + " é inimigo de " + nomeReino(reino)
                    + ". O reino paga " + Moedas.texto(recompensa(p)) + " a quem o derrotar.").withStyle(ChatFormatting.RED), false);
        }
        Progressao.enviarAoPainel(p, false);
    }

    /** Lojistas de um reino hostil não atendem. Banco, câmbio, Guilda, clãs e o rei continuam. */
    static boolean recusa(ServerPlayer p, Entity npc, String papel) {
        String reino = npc.getPersistentData().getString("frx_reino");
        if (livre(p) || Reputacao.hostilidade(p, reino) < 2) return false;
        if (Costureira.disfarcado(p) && Reputacao.hostilidade(p, reino) < 3) return false;   // de manto, ninguém reconhece
        switch (papel) {
            case "rei", "banqueiro", "cambista", "recepcionista", "clas", "guarda" -> { return false; }
            default -> { }
        }
        p.sendSystemMessage(Component.literal("\"Você não é bem-vindo aqui. Saia antes que eu chame os guardas.\"").withStyle(ChatFormatting.RED));
        p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 0.8f);
        return true;
    }

    /** Multa paga ao rei: a reputação com o reino volta a zero. */
    static String pagarMulta(ServerPlayer p, String reino) {
        int pts = Reputacao.pontos(p, reino);
        if (pts >= 0) return "Seu nome está limpo neste reino.";
        long custo = multa(p, reino);
        if (!Moedas.cobrar(p, custo)) return "A multa é de " + Moedas.texto(custo) + ". Volte quando tiver as moedas.";
        Reputacao.ganhar(p, reino, -pts, false, false);
        return "Multa paga. Seu nome está limpo em " + nomeReino(reino) + ". Não me faça me arrepender.";
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        int t = s.getTickCount();
        if (t % 6000 == 11) {   // a cada 5 minutos o tempo apaga um pouco da má fama
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (!Progressao.temDados(p)) continue;
                for (Reino r : Reinos.todos()) if (Reputacao.pontos(p, r.id()) < 0) Reputacao.ganhar(p, r.id(), 2, false, false);
            }
        }
        if (t % 18000 == 13 && s.isPvpAllowed()) {   // a cada 15 minutos: onde os procurados foram vistos
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (livre(p) || !Progressao.temDados(p) || Reputacao.piorHostilidade(p) < 3 || Costureira.disfarcado(p)) continue;
                s.getPlayerList().broadcastSystemMessage(Component.literal("☠ " + p.getGameProfile().getName() + ", procurado, foi visto perto de "
                        + Math.round(p.getBlockX() / 100.0) * 100 + ", " + Math.round(p.getBlockZ() / 100.0) * 100 + ". Recompensa: " + Moedas.texto(recompensa(p)) + ".")
                        .withStyle(ChatFormatting.RED), false);
            }
        }
        if (t % 20 != 3) return;
        long agora = System.currentTimeMillis();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (livre(p) || !p.isAlive() || !Progressao.temDados(p)) continue;
            Reino r = NpcsReino.reinoDe(p);
            if (r == null) continue;
            if (Math.abs(p.getBlockX() - r.centroX()) <= 30 && Math.abs(p.getBlockZ() - r.centroZ()) <= 30) Contrabando.infiltrou(p, r.id());
            int h = Reputacao.hostilidade(p, r.id());
            if (h == 0) continue;
            if (Costureira.disfarcado(p) && h < 3) continue;   // manto da Costureira: os guardas não reconhecem
            if (h == 1) {
                if (agora - AVISO_CAPITAL.getOrDefault(p.getUUID(), 0L) > 300_000L) {
                    AVISO_CAPITAL.put(p.getUUID(), agora);
                    p.sendSystemMessage(Component.literal("Os moradores de " + r.nome() + " olham torto para você. Aqui tudo custa mais caro.").withStyle(ChatFormatting.YELLOW));
                }
                continue;
            }
            for (Mob g : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), Hostilidade::guarda))
                if (g.getTarget() == null || !g.getTarget().isAlive()) g.setTarget(p);
            if (agora - AVISO_GUARDA.getOrDefault(p.getUUID(), 0L) > 30_000L) {
                AVISO_GUARDA.put(p.getUUID(), agora);
                p.sendSystemMessage(Component.literal("Guarda: \"Você não é bem-vindo em " + r.nome() + ". Saia da cidade!\"").withStyle(ChatFormatting.RED));
                p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 0.8f, 0.7f);
            }
        }
    }

    /** Bater num guarda sem ter sido atacado: primeiro um aviso, depois reputação perdida. */
    @SubscribeEvent
    public void aoAtacar(LivingAttackEvent ev) {
        Entity e = ev.getEntity();
        if (e.level().isClientSide() || !guarda(e) || !(ev.getSource().getEntity() instanceof ServerPlayer p) || livre(p)) return;
        if (e instanceof Mob m && m.getTarget() == p) return;   // legítima defesa
        String reino = e.getPersistentData().getString("frx_reino");
        if (reino.isEmpty()) return;
        long agora = System.currentTimeMillis();
        if (agora - ALERTA.getOrDefault(p.getUUID(), 0L) > 60_000L) {
            ALERTA.put(p.getUUID(), agora);
            GOLPE.put(p.getUUID(), agora);
            p.sendSystemMessage(Component.literal("Guarda: \"Cuidado com essa arma! Mais um golpe e o rei fica sabendo.\"").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (agora - GOLPE.getOrDefault(p.getUUID(), 0L) < 3000L) return;
        GOLPE.put(p.getUUID(), agora);
        ALERTA.put(p.getUUID(), agora);
        Reputacao.perder(p, reino, 10);
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        Entity e = ev.getEntity();
        if (e.level().isClientSide() || !(ev.getSource().getEntity() instanceof ServerPlayer matador)) return;
        if (guarda(e)) {
            String reino = e.getPersistentData().getString("frx_reino");
            if (reino.isEmpty() || livre(matador)) return;
            Reputacao.perder(matador, reino, 30);
            Contrabando.guardaMorto(matador, reino);
            return;
        }
        if (!(e instanceof ServerPlayer vitima) || vitima == matador || !Progressao.temDados(vitima) || Clas.mesmoCla(vitima, matador)) return;
        long premio = recompensa(vitima);
        for (Reino r : Reinos.todos()) {
            int pts = Reputacao.pontos(vitima, r.id());
            if (Reputacao.hostilidade(pts) < 3) continue;
            Moedas.dar(matador, premio);
            Reputacao.ganhar(matador, r.id(), 100, true);
            Reputacao.ganhar(vitima, r.id(), Reputacao.HOSTIL - pts, false, false);   // pena cumprida: volta a Hostil
            matador.server.getPlayerList().broadcastSystemMessage(Component.literal("☠ " + matador.getGameProfile().getName() + " derrotou " + vitima.getGameProfile().getName()
                    + ", inimigo de " + r.nome() + ", e recebeu " + Moedas.texto(premio) + ".").withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** Para o mural (/procurados): jogadores com a cabeça a prêmio. Devolve se há algum. */
    public static boolean listar(ServerPlayer para) {
        boolean tem = false;
        if (!para.server.isPvpAllowed()) return false;
        for (ServerPlayer p : para.server.getPlayerList().getPlayers()) {
            if (livre(p) || !Progressao.temDados(p)) continue;
            for (Reino r : Reinos.todos()) {
                if (Reputacao.hostilidade(Reputacao.pontos(p, r.id())) < 3) continue;
                para.sendSystemMessage(Component.literal("☠ PROCURADO: " + p.getGameProfile().getName() + ", inimigo de " + r.nome() + " — recompensa "
                        + Moedas.texto(recompensa(p)) + ". Visto perto de " + Math.round(p.getBlockX() / 100.0) * 100 + ", " + Math.round(p.getBlockZ() / 100.0) * 100 + ".")
                        .withStyle(ChatFormatting.RED));
                tem = true;
            }
        }
        return tem;
    }
}
