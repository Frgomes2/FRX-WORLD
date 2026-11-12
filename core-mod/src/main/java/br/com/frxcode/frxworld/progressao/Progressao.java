package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** Regras de progressão: curva de XP, XP por abate, subida de nível e rank. */
public final class Progressao {
    private Progressao() {}

    /** XP necessária para sair de um nível e ir ao próximo. */
    public static long xpParaProximo(int nivel) {
        return Math.max(1, Math.round(ConfigJogo.xpBase() * Math.pow(nivel, ConfigJogo.xpExpoente())));
    }

    /** XP que um mob dá ao morrer. */
    public static long xpDoMob(LivingEntity mob) {
        EntityType<?> tipo = mob.getType();
        double xp;
        if (tipo == EntityType.ENDER_DRAGON) xp = 3000;
        else if (tipo == EntityType.WITHER) xp = 1500;
        else if (tipo == EntityType.WARDEN) xp = 800;
        else if (tipo == EntityType.ELDER_GUARDIAN) xp = 400;
        else {
            double vida = mob.getMaxHealth();
            xp = mob instanceof Enemy ? vida : vida * 0.25; // monstro vale mais que animal
        }
        return Math.max(1, Math.round(xp * ConfigJogo.xpMultiplicador()));
    }

    /**
     * Dados do jogador. Enquanto ele está na tela de morte o Forge invalida as capabilities;
     * nesse intervalo devolve um objeto vazio (descartado) em vez de derrubar o servidor.
     */
    public static DadosJogador dados(ServerPlayer jogador) {
        return jogador.getCapability(ProvedorDados.DADOS).orElseGet(DadosJogador::new);
    }

    public static boolean temDados(ServerPlayer jogador) {
        return jogador.isAlive() && jogador.getCapability(ProvedorDados.DADOS).isPresent();
    }

    public static String rank(ServerPlayer jogador) {
        return Guilda.rank(jogador);
    }

    /** Soma XP, sobe quantos níveis couberem, avisa o jogador e sincroniza. */
    public static void ganharXp(ServerPlayer jogador, long quantidade) {
        if (quantidade <= 0) return;
        double bonusRaca = br.com.frxcode.frxworld.racas.RacasJogador.num(jogador, "xp");
        if (bonusRaca != 0) quantidade = Math.max(1, Math.round(quantidade * (1 + bonusRaca / 100)));
        double bonusUnica = br.com.frxcode.frxworld.habilidades.Unica.bonusXp(jogador);
        if (bonusUnica > 0) quantidade = Math.max(1, Math.round(quantidade * (1 + bonusUnica)));
        DadosJogador d = dados(jogador);
        int maximo = ConfigJogo.nivelMaximo();
        int nivel = d.nivel();
        long xp = d.xp() + quantidade;
        int nivelAntes = nivel;
        String rankAntes = ConfigJogo.rankDoNivel(nivelAntes);

        while (nivel < maximo && xp >= xpParaProximo(nivel)) {
            xp -= xpParaProximo(nivel);
            nivel++;
        }
        if (nivel >= maximo) xp = 0;

        d.definir(nivel, xp);
        if (nivel > nivelAntes) {
            String rankNovo = ConfigJogo.rankDoNivel(nivel);
            aoSubirNivel(jogador, nivel, rankNovo.equals(rankAntes) ? null : rankNovo);
            br.com.frxcode.frxworld.habilidades.Habilidades.aprenderPorNivel(jogador);
            enviarAoPainel(jogador, false);
        }
        Rede.sincronizar(jogador);
    }

    /** Define nível direto (comando de admin). */
    public static void definirNivel(ServerPlayer jogador, int nivel) {
        dados(jogador).definir(Math.min(nivel, ConfigJogo.nivelMaximo()), 0);
        br.com.frxcode.frxworld.habilidades.Habilidades.aprenderPorNivel(jogador);
        atualizarNome(jogador);
        Rede.sincronizar(jogador);
        enviarAoPainel(jogador, false);
    }

    public static void enviarAoPainel(ServerPlayer jogador, boolean morreu) {
        DadosJogador d = dados(jogador);
        PainelApi.enviarProgresso(jogador.getUUID(), jogador.getGameProfile().getName(),
                d.nivel(), d.xp(), Guilda.rank(jogador), morreu, d.classe(),
                br.com.frxcode.frxworld.moedas.Moedas.contar(jogador), br.com.frxcode.frxworld.moedas.Banco.saldo(jogador),
                d.titulo(), d.conquistas().size(), br.com.frxcode.frxworld.reinos.Campanhas.resumoPainel(d));
    }

    public static void atualizarNome(ServerPlayer jogador) {
        jogador.refreshDisplayName();
        jogador.refreshTabListName();
    }

    private static void aoSubirNivel(ServerPlayer jogador, int nivel, String rankNovo) {
        Sistema.avisar(jogador, "Você subiu de nível!", "Nível " + nivel + ". Você ficou mais forte.", Sistema.OURO);
        jogador.level().playSound(null, jogador.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
        // o nível liberou um rank novo: o rank só muda depois da prova na Guilda
        if (rankNovo != null && ConfigJogo.indiceDoNivel(nivel) > Guilda.indice(jogador))
            Sistema.avisar(jogador, "Prova de promoção liberada", "Procure a recepcionista da Guilda numa capital para tentar o Rank " + rankNovo + ".", Sistema.AZUL);
        atualizarNome(jogador);
    }
}
