package br.com.frxcode.frxworld.rede;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal de rede do FRX-WORLD. Cliente e servidor precisam do mod na mesma versão de protocolo. */
public final class Rede {
    private static final String PROTOCOLO = "13";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FrxWorld.MOD_ID, "principal"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private Rede() {}

    public static void registrar() {
        int id = 0;
        CANAL.messageBuilder(SyncNivel.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncNivel::codificar)
                .decoder(SyncNivel::decodificar)
                .consumerMainThread(SyncNivel::tratar)
                .add();
        CANAL.messageBuilder(AbrirQuadro.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AbrirQuadro::codificar)
                .decoder(AbrirQuadro::decodificar)
                .consumerMainThread(AbrirQuadro::tratar)
                .add();
        CANAL.messageBuilder(SyncRastreador.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRastreador::codificar)
                .decoder(SyncRastreador::decodificar)
                .consumerMainThread(SyncRastreador::tratar)
                .add();
        CANAL.messageBuilder(AcaoMissao.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AcaoMissao::codificar)
                .decoder(AcaoMissao::decodificar)
                .consumerMainThread(AcaoMissao::tratar)
                .add();
        CANAL.messageBuilder(SyncHabilidades.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncHabilidades::codificar)
                .decoder(SyncHabilidades::decodificar)
                .consumerMainThread(SyncHabilidades::tratar)
                .add();
        CANAL.messageBuilder(SyncRecurso.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRecurso::codificar)
                .decoder(SyncRecurso::decodificar)
                .consumerMainThread(SyncRecurso::tratar)
                .add();
        CANAL.messageBuilder(SyncRecarga.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRecarga::codificar)
                .decoder(SyncRecarga::decodificar)
                .consumerMainThread(SyncRecarga::tratar)
                .add();
        CANAL.messageBuilder(AcaoHabilidade.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AcaoHabilidade::codificar)
                .decoder(AcaoHabilidade::decodificar)
                .consumerMainThread(AcaoHabilidade::tratar)
                .add();
        CANAL.messageBuilder(PedirStatus.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PedirStatus::codificar)
                .decoder(PedirStatus::decodificar)
                .consumerMainThread(PedirStatus::tratar)
                .add();
        CANAL.messageBuilder(SyncStatus.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncStatus::codificar)
                .decoder(SyncStatus::decodificar)
                .consumerMainThread(SyncStatus::tratar)
                .add();
        CANAL.messageBuilder(SkinNpc.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkinNpc::codificar)
                .decoder(SkinNpc::decodificar)
                .consumerMainThread(SkinNpc::tratar)
                .add();
        CANAL.messageBuilder(MarcaNpc.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MarcaNpc::codificar)
                .decoder(MarcaNpc::decodificar)
                .consumerMainThread(MarcaNpc::tratar)
                .add();
        CANAL.messageBuilder(SyncSombras.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncSombras::codificar)
                .decoder(SyncSombras::decodificar)
                .consumerMainThread(SyncSombras::tratar)
                .add();
        CANAL.messageBuilder(AcaoSombra.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AcaoSombra::codificar)
                .decoder(AcaoSombra::decodificar)
                .consumerMainThread(AcaoSombra::tratar)
                .add();
        CANAL.messageBuilder(SyncDespertar.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDespertar::codificar)
                .decoder(SyncDespertar::decodificar)
                .consumerMainThread(SyncDespertar::tratar)
                .add();
        CANAL.messageBuilder(SyncRaca.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRaca::codificar).decoder(SyncRaca::decodificar).consumerMainThread(SyncRaca::tratar).add();
        CANAL.messageBuilder(SyncRacaVisivel.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRacaVisivel::codificar).decoder(SyncRacaVisivel::decodificar).consumerMainThread(SyncRacaVisivel::tratar).add();
        CANAL.messageBuilder(SyncRotuloMob.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRotuloMob::codificar).decoder(SyncRotuloMob::decodificar).consumerMainThread(SyncRotuloMob::tratar).add();
        CANAL.messageBuilder(AcaoRaca.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AcaoRaca::codificar).decoder(AcaoRaca::decodificar).consumerMainThread(AcaoRaca::tratar).add();
        CANAL.messageBuilder(NumeroDano.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NumeroDano::codificar).decoder(NumeroDano::decodificar).consumerMainThread(NumeroDano::tratar).add();
        CANAL.messageBuilder(AvisoSistema.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AvisoSistema::codificar).decoder(AvisoSistema::decodificar).consumerMainThread(AvisoSistema::tratar).add();
        CANAL.messageBuilder(AbrirMenu.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AbrirMenu::codificar).decoder(AbrirMenu::decodificar).consumerMainThread(AbrirMenu::tratar).add();
        CANAL.messageBuilder(AcaoMenu.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AcaoMenu::codificar).decoder(AcaoMenu::decodificar).consumerMainThread(AcaoMenu::tratar).add();
    }

    private static String titulo(DadosJogador d) { String t = d.titulo(); return t == null ? "" : t.length() > 60 ? t.substring(0, 60) : t; }

    /** Manda os dados completos para o próprio jogador e o nível dele para todos os outros. */
    public static void sincronizar(ServerPlayer jogador) {
        DadosJogador d = Progressao.dados(jogador);
        CANAL.send(PacketDistributor.PLAYER.with(() -> jogador),
                new SyncNivel(jogador.getUUID(), d.nivel(), Progressao.rank(jogador), d.xp(), Progressao.xpParaProximo(d.nivel()), titulo(d)));
        SyncNivel publico = new SyncNivel(jogador.getUUID(), d.nivel(), Progressao.rank(jogador), 0, 0, titulo(d));
        for (ServerPlayer outro : jogador.server.getPlayerList().getPlayers()) {
            if (outro != jogador) CANAL.send(PacketDistributor.PLAYER.with(() -> outro), publico);
        }
    }

    /** Ao entrar: o jogador recebe o nível de quem já está online. */
    public static void enviarTodosPara(ServerPlayer jogador) {
        for (ServerPlayer outro : jogador.server.getPlayerList().getPlayers()) {
            if (outro == jogador) continue;
            DadosJogador d = Progressao.dados(outro);
            CANAL.send(PacketDistributor.PLAYER.with(() -> jogador), new SyncNivel(outro.getUUID(), d.nivel(), Progressao.rank(outro), 0, 0, titulo(d)));
        }
    }
}
