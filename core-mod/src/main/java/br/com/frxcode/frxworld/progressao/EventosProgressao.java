package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.missoes.Missoes;
import br.com.frxcode.frxworld.rede.Rede;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Eventos de servidor da progressão (barramento do Forge). */
public class EventosProgressao {

    /** Barramento do mod: registra o tipo de capability. */
    public static void registrarCapabilities(RegisterCapabilitiesEvent evento) {
        evento.register(DadosJogador.class);
    }

    private int ticks = 0;

    /** Lê a configuração do painel ao ligar e depois a cada minuto. */
    @SubscribeEvent
    public void aoLigar(ServerStartedEvent evento) {
        ConfigJogo.recarregar();
        Missoes.recarregar();
        br.com.frxcode.frxworld.reinos.Lojas.recarregar();
        br.com.frxcode.frxworld.dungeon.Dungeons.recarregar();
        br.com.frxcode.frxworld.dungeon.Externos.recarregar();
        br.com.frxcode.frxworld.eventos.EventosEstrada.recarregar();
        br.com.frxcode.frxworld.habilidades.Habilidades.recarregar();
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks % 40 == 0) { // a cada 2 s: progresso das missões de coleta no HUD
            for (ServerPlayer p : evento.getServer().getPlayerList().getPlayers()) {
                if (Progressao.temDados(p)) Missoes.sincronizarRastreador(p);
            }
        }
        if (ticks >= 20 * 60) {
            ticks = 0;
            ConfigJogo.recarregar();
            Missoes.recarregar();
        br.com.frxcode.frxworld.reinos.Lojas.recarregar();
            br.com.frxcode.frxworld.dungeon.Dungeons.recarregar();
            br.com.frxcode.frxworld.dungeon.Externos.recarregar();
            br.com.frxcode.frxworld.eventos.EventosEstrada.recarregar();
            br.com.frxcode.frxworld.habilidades.Habilidades.recarregar();
        }
    }

    @SubscribeEvent
    public void anexar(AttachCapabilitiesEvent<Entity> evento) {
        if (evento.getObject() instanceof Player) {
            evento.addCapability(new ResourceLocation(FrxWorld.MOD_ID, "dados_jogador"), new ProvedorDados());
        }
    }

    /** Morte ou volta do End: copia os dados para a nova entidade do jogador. */
    @SubscribeEvent
    public void clonar(PlayerEvent.Clone evento) {
        Player antigo = evento.getOriginal();
        antigo.reviveCaps();
        antigo.getCapability(ProvedorDados.DADOS).ifPresent(velho ->
                evento.getEntity().getCapability(ProvedorDados.DADOS).ifPresent(novo -> novo.copiarDe(velho)));
        antigo.invalidateCaps();
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent evento) {
        if (evento.getEntity() instanceof ServerPlayer p) {
            Rede.sincronizar(p);
            Rede.enviarTodosPara(p);
            Missoes.sincronizarRastreador(p);
            p.refreshDisplayName();
            p.refreshTabListName();
        }
    }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent evento) {
        if (evento.getEntity() instanceof ServerPlayer p) Rede.sincronizar(p);
    }

    @SubscribeEvent
    public void aoMudarDimensao(PlayerEvent.PlayerChangedDimensionEvent evento) {
        if (evento.getEntity() instanceof ServerPlayer p) Rede.sincronizar(p);
    }

    /** XP por abate de mob (jogadores não dão XP aqui; PvP entra com o sistema de guerra). */
    @SubscribeEvent
    public void aoMatar(LivingDeathEvent evento) {
        if (evento.getEntity() instanceof Player) return;
        if (evento.getSource().getEntity() instanceof ServerPlayer matador) {
            // XP e missões de caça repartidas com o grupo (se houver)
            long xpAbate = br.com.frxcode.frxworld.aventura.Torre.semXpDeAbate(evento.getEntity()) ? 0 : Math.max(1, Math.round(Progressao.xpDoMob(evento.getEntity()) * Zonas.fatorXp(evento.getEntity(), matador)));
            br.com.frxcode.frxworld.grupos.Grupos.distribuirXp(matador, xpAbate, evento.getEntity());
        }
    }

    /** Nível antes do nome no chat (lado do servidor). */
    @SubscribeEvent
    public void nome(PlayerEvent.NameFormat evento) {
        if (evento.getEntity() instanceof ServerPlayer p) {
            p.getCapability(ProvedorDados.DADOS).ifPresent(d -> evento.setDisplayname(
                    Component.literal("[" + Guilda.rank(p) + " · Nv " + d.nivel() + "] ").withStyle(ChatFormatting.GOLD)
                            .append(evento.getDisplayname())
                            .append(d.titulo().isEmpty() ? Component.empty() : Component.literal(" «" + d.titulo() + "»").withStyle(ChatFormatting.LIGHT_PURPLE))));
        }
    }

    /** Nível na lista de jogadores (Tab). */
    @SubscribeEvent
    public void nomeTab(PlayerEvent.TabListNameFormat evento) {
        if (evento.getEntity() instanceof ServerPlayer p) {
            p.getCapability(ProvedorDados.DADOS).ifPresent(d -> evento.setDisplayName(
                    Component.literal("[" + Guilda.rank(p) + " · Nv " + d.nivel() + "] ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(p.getGameProfile().getName()))
                            .append(d.titulo().isEmpty() ? Component.empty() : Component.literal(" «" + d.titulo() + "»").withStyle(ChatFormatting.LIGHT_PURPLE))));
        }
    }

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        // /nivel — qualquer jogador vê o próprio progresso
        evento.getDispatcher().register(Commands.literal("nivel").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            DadosJogador d = Progressao.dados(p);
            p.sendSystemMessage(Component.literal("Rank " + Guilda.rank(p) + " · Nível " + d.nivel()).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("  " + d.xp() + " / " + Progressao.xpParaProximo(d.nivel()) + " XP")
                            .withStyle(ChatFormatting.GRAY)));
            return 1;
        }));

        // /frx xp <jogador> <quantidade>  e  /frx nivel <jogador> <nivel>  — só admin (op 2)
        evento.getDispatcher().register(Commands.literal("frx")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("recarregar").executes(ctx -> {
                    ConfigJogo.recarregar();
                    Missoes.recarregar();
        br.com.frxcode.frxworld.reinos.Lojas.recarregar();
                    br.com.frxcode.frxworld.dungeon.Dungeons.recarregar();
                    br.com.frxcode.frxworld.dungeon.Externos.recarregar();
                    br.com.frxcode.frxworld.eventos.EventosEstrada.recarregar();
                    br.com.frxcode.frxworld.habilidades.Habilidades.recarregar();
                    responder(ctx.getSource(), "Configuração, missões, dungeons, chefes, eventos e habilidades do painel recarregados.");
                    return 1;
                }))
                .then(Commands.literal("taverna").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    boolean ok = br.com.frxcode.frxworld.taverna.Tavernas.construir(p);
                    responder(ctx.getSource(), ok ? "Taverna construída." : "Modelo da taverna não encontrado.");
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("dungeon")
                        .executes(ctx -> {
                            // catacumba completa (peças de jigsaw), gerada a partir da posição do jogador
                            ctx.getSource().getServer().getCommands().performPrefixedCommand(ctx.getSource(), "place structure frxworld:catacumba");
                            return 1;
                        })
                        .then(Commands.literal("cripta").executes(ctx -> {
                            boolean ok = br.com.frxcode.frxworld.dungeon.Construir.cripta(ctx.getSource().getPlayerOrException());
                            responder(ctx.getSource(), ok ? "Cripta construída." : "Modelo da cripta não encontrado.");
                            return ok ? 1 : 0;
                        }))
                        .then(Commands.literal("resetar").then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            br.com.frxcode.frxworld.dungeon.Dungeons.resetar(alvo);
                            responder(ctx.getSource(), "Recarga de dungeons zerada para " + alvo.getGameProfile().getName());
                            return 1;
                        }))))
                .then(Commands.literal("quadro")
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                            Missoes.abrirQuadro(EntityArgument.getPlayer(ctx, "jogador"));
                            return 1;
                        })))
                .then(Commands.literal("xp")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("quantidade", LongArgumentType.longArg(1))
                                        .executes(ctx -> {
                                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                            long q = LongArgumentType.getLong(ctx, "quantidade");
                                            Progressao.ganharXp(alvo, q);
                                            responder(ctx.getSource(), "+" + q + " XP para " + alvo.getGameProfile().getName());
                                            return 1;
                                        }))))
                .then(Commands.literal("nivel")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("nivel", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                            int n = IntegerArgumentType.getInteger(ctx, "nivel");
                                            Progressao.definirNivel(alvo, n);
                                            responder(ctx.getSource(), alvo.getGameProfile().getName() + " agora é nível " + Progressao.dados(alvo).nivel());
                                            return 1;
                                        })))));
    }

    private static void responder(CommandSourceStack fonte, String texto) {
        fonte.sendSuccess(() -> Component.literal(texto).withStyle(ChatFormatting.GREEN), true);
    }
}
