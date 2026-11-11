package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.Config;
import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acesso por convite (senha só no primeiro login).
 *
 * Ao entrar, o servidor pergunta ao painel se a conta já está liberada.
 * Se não estiver, o jogador fica parado na chegada, sem interagir e sem levar dano,
 * até digitar /convite CÓDIGO. Código certo = conta liberada para sempre.
 */
public class ControleAcesso {

    /** Jogador aguardando liberação: posição em que deve ficar parado e ticks restantes. */
    private static final class Pendente {
        final double x, y, z;
        int ticksRestantes;
        boolean conferindo = true; // ainda esperando a primeira resposta do painel

        Pendente(ServerPlayer p, int segundos) {
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
            this.ticksRestantes = segundos * 20;
        }
    }

    private final Map<UUID, Pendente> pendentes = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ login / logout

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent evento) {
        if (!(evento.getEntity() instanceof ServerPlayer jogador)) return;

        if (!Config.ACESSO_ATIVO.get()) {
            boasVindas(jogador);
            return;
        }

        UUID uuid = jogador.getUUID();
        String nick = jogador.getGameProfile().getName();
        MinecraftServer server = jogador.getServer();
        pendentes.put(uuid, new Pendente(jogador, Config.SEGUNDOS_PARA_CONVITE.get()));

        PainelApi.acessoPermitido(uuid, nick).whenComplete((permitido, erro) -> server.execute(() -> {
            Pendente pendente = pendentes.get(uuid);
            ServerPlayer atual = server.getPlayerList().getPlayer(uuid);
            if (pendente == null || atual == null) return; // saiu antes da resposta

            if (erro != null) {
                FrxWorld.LOG.error("Não foi possível falar com o painel em {}", Config.PAINEL_URL.get(), erro);
                pendentes.remove(uuid);
                atual.connection.disconnect(Component.literal(
                        "O painel do FRX-WORLD está fora do ar. Tente de novo em instantes."));
                return;
            }

            if (Boolean.TRUE.equals(permitido)) {
                pendentes.remove(uuid);
                boasVindas(atual);
            } else {
                pendente.conferindo = false;
                pedirConvite(atual);
            }
        }));
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent evento) {
        pendentes.remove(evento.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ comando /convite

    @SubscribeEvent
    public void registrarComandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("convite")
                .then(Commands.argument("codigo", StringArgumentType.word())
                        .executes(ctx -> usarConvite(ctx.getSource(), StringArgumentType.getString(ctx, "codigo")))));
    }

    private int usarConvite(CommandSourceStack fonte, String codigo) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer jogador = fonte.getPlayerOrException();
        UUID uuid = jogador.getUUID();

        if (!pendentes.containsKey(uuid)) {
            jogador.sendSystemMessage(Component.literal("Sua conta já está liberada.").withStyle(ChatFormatting.GRAY));
            return 1;
        }

        jogador.sendSystemMessage(Component.literal("Conferindo convite...").withStyle(ChatFormatting.GRAY));
        MinecraftServer server = jogador.getServer();

        PainelApi.usarConvite(uuid, jogador.getGameProfile().getName(), codigo)
                .whenComplete((resultado, erro) -> server.execute(() -> {
                    ServerPlayer atual = server.getPlayerList().getPlayer(uuid);
                    if (atual == null) return;

                    if (erro != null) {
                        FrxWorld.LOG.error("Erro ao validar convite", erro);
                        atual.sendSystemMessage(Component.literal("O painel não respondeu. Tente de novo.")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }

                    if (resultado.ok()) {
                        pendentes.remove(uuid);
                        atual.sendSystemMessage(Component.literal(resultado.motivo()).withStyle(ChatFormatting.GREEN));
                        boasVindas(atual);
                    } else {
                        atual.sendSystemMessage(Component.literal(resultado.motivo()).withStyle(ChatFormatting.RED));
                    }
                }));
        return 1;
    }

    // ------------------------------------------------------------------ jogador pendente: parado e protegido

    @SubscribeEvent
    public void aCadaTick(TickEvent.PlayerTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END || evento.side != LogicalSide.SERVER) return;
        if (!(evento.player instanceof ServerPlayer jogador)) return;

        Pendente pendente = pendentes.get(jogador.getUUID());
        if (pendente == null) return;

        // Mantém na posição de chegada
        if (jogador.distanceToSqr(pendente.x, pendente.y, pendente.z) > 0.25) {
            jogador.teleportTo(pendente.x, pendente.y, pendente.z);
        }

        if (pendente.conferindo) return;

        pendente.ticksRestantes--;
        if (pendente.ticksRestantes <= 0) {
            pendentes.remove(jogador.getUUID());
            jogador.connection.disconnect(Component.literal(
                    "Tempo esgotado. Peça um código de convite ao administrador do FRX-WORLD."));
        } else if (pendente.ticksRestantes % (20 * 30) == 0) {
            pedirConvite(jogador);
        }
    }

    private boolean pendente(Player p) {
        return p != null && pendentes.containsKey(p.getUUID());
    }

    @SubscribeEvent
    public void bloquearQuebra(BlockEvent.BreakEvent e) {
        if (pendente(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void bloquearColocar(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player p && pendente(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void bloquearCliqueBloco(PlayerInteractEvent.RightClickBlock e) {
        if (pendente(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void bloquearUsarItem(PlayerInteractEvent.RightClickItem e) {
        if (pendente(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void bloquearInteragirEntidade(PlayerInteractEvent.EntityInteract e) {
        if (pendente(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void bloquearBaterBloco(PlayerInteractEvent.LeftClickBlock e) {
        if (pendente(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void bloquearAtacar(AttackEntityEvent e) {
        if (pendente(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void protegerPendente(LivingAttackEvent e) {
        if (e.getEntity() instanceof Player p && pendente(p)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ mensagens

    private static void pedirConvite(ServerPlayer jogador) {
        jogador.sendSystemMessage(Component.literal("Bem-vindo ao FRX-WORLD! ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Este é um servidor privado. Digite ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("/convite SEU-CÓDIGO").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" para liberar sua conta.").withStyle(ChatFormatting.WHITE)));
    }

    private static void boasVindas(ServerPlayer jogador) {
        jogador.sendSystemMessage(Component.literal("Bem-vindo ao FRX-WORLD, ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(jogador.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("!").withStyle(ChatFormatting.GOLD)));
    }
}
