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
        boolean senha = false;     // já passou do convite; falta /login ou /registrar (servidor sem autenticação da Mojang)
        int erros = 0;

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
            // sem convite: no servidor offline ainda pede a senha da conta
            Pendente p = new Pendente(jogador, Config.SEGUNDOS_PARA_CONVITE.get());
            pendentes.put(jogador.getUUID(), p);
            liberarOuPedirSenha(jogador, p);
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
                liberarOuPedirSenha(atual, pendente);
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
        // senha da conta (só vale quando o servidor roda sem autenticação da Mojang: TLauncher, Shiginima...)
        evento.getDispatcher().register(Commands.literal("registrar")
                .then(Commands.argument("senha", StringArgumentType.word())
                        .then(Commands.argument("confirmacao", StringArgumentType.word())
                                .executes(ctx -> registrar(ctx.getSource(), StringArgumentType.getString(ctx, "senha"),
                                        StringArgumentType.getString(ctx, "confirmacao"))))));
        evento.getDispatcher().register(Commands.literal("login")
                .then(Commands.argument("senha", StringArgumentType.word())
                        .executes(ctx -> entrar(ctx.getSource(), StringArgumentType.getString(ctx, "senha")))));
        evento.getDispatcher().register(Commands.literal("frx").requires(f -> f.hasPermission(2))
                .then(Commands.literal("senha").then(Commands.argument("nick", StringArgumentType.word())
                        .then(Commands.literal("resetar").executes(ctx -> {
                            String nick = StringArgumentType.getString(ctx, "nick");
                            boolean tinha = SenhasContas.apagar(nick);
                            ctx.getSource().sendSuccess(() -> Component.literal(tinha
                                    ? "Senha de " + nick + " apagada: no próximo login ele cria outra com /registrar."
                                    : nick + " não tinha senha.").withStyle(ChatFormatting.GOLD), true);
                            return 1;
                        })))));
    }

    // ------------------------------------------------------------------ senha (servidor offline)

    /** Convite ok: no servidor original (Mojang) já entra; no offline ainda pede a senha da conta. */
    private void liberarOuPedirSenha(ServerPlayer jogador, Pendente pendente) {
        MinecraftServer server = jogador.getServer();
        if (server == null || server.usesAuthentication()) {
            pendentes.remove(jogador.getUUID());
            boasVindas(jogador);
            return;
        }
        pendente.senha = true;
        pendente.conferindo = false;
        pendente.ticksRestantes = Math.max(pendente.ticksRestantes, 120 * 20);
        pedirSenha(jogador);
    }

    private int registrar(CommandSourceStack fonte, String senha, String confirmacao) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer jogador = fonte.getPlayerOrException();
        Pendente pd = pendentes.get(jogador.getUUID());
        String nick = jogador.getGameProfile().getName();
        if (pd == null || !pd.senha) {
            jogador.sendSystemMessage(Component.literal(pd == null ? "Você já está logado." : "Primeiro use o convite: /convite CÓDIGO").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (SenhasContas.tem(nick)) {
            jogador.sendSystemMessage(Component.literal("Esta conta já tem senha. Use /login SUA-SENHA").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!senha.equals(confirmacao)) {
            jogador.sendSystemMessage(Component.literal("As duas senhas não são iguais. Digite /registrar SENHA SENHA").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (senha.length() < 6) {
            jogador.sendSystemMessage(Component.literal("A senha precisa ter pelo menos 6 caracteres.").withStyle(ChatFormatting.RED));
            return 0;
        }
        SenhasContas.definir(nick, senha);
        pendentes.remove(jogador.getUUID());
        jogador.sendSystemMessage(Component.literal("Senha criada! Nas próximas vezes entre com /login SUA-SENHA").withStyle(ChatFormatting.GREEN));
        boasVindas(jogador);
        return 1;
    }

    private int entrar(CommandSourceStack fonte, String senha) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer jogador = fonte.getPlayerOrException();
        Pendente pd = pendentes.get(jogador.getUUID());
        String nick = jogador.getGameProfile().getName();
        if (pd == null || !pd.senha) {
            jogador.sendSystemMessage(Component.literal(pd == null ? "Você já está logado." : "Primeiro use o convite: /convite CÓDIGO").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (!SenhasContas.tem(nick)) {
            jogador.sendSystemMessage(Component.literal("Esta conta ainda não tem senha. Crie com /registrar SENHA SENHA").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!SenhasContas.confere(nick, senha)) {
            pd.erros++;
            if (pd.erros >= 5) {
                pendentes.remove(jogador.getUUID());
                FrxWorld.LOG.warn("Login: 5 senhas erradas para {} ({})", nick, jogador.getIpAddress());
                jogador.connection.disconnect(Component.literal("Senha errada 5 vezes. Se esqueceu, peça ao administrador para resetar."));
            } else {
                jogador.sendSystemMessage(Component.literal("Senha errada (" + pd.erros + "/5).").withStyle(ChatFormatting.RED));
            }
            return 0;
        }
        pendentes.remove(jogador.getUUID());
        boasVindas(jogador);
        return 1;
    }

    /** Enquanto não entra: nada de comandos (só convite/login/registrar) nem chat. */
    @SubscribeEvent
    public void bloquearComandos(net.minecraftforge.event.CommandEvent e) {
        if (!(e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p) || !pendente(p)) return;
        String cmd = e.getParseResults().getReader().getString().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        String raiz = cmd.split(" ")[0].toLowerCase();
        if (raiz.equals("convite") || raiz.equals("login") || raiz.equals("registrar")) return;
        e.setCanceled(true);
        p.sendSystemMessage(Component.literal("Entre na sua conta primeiro.").withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public void bloquearChat(net.minecraftforge.event.ServerChatEvent e) {
        if (pendente(e.getPlayer())) {
            e.setCanceled(true);
            e.getPlayer().sendSystemMessage(Component.literal("Entre na sua conta primeiro.").withStyle(ChatFormatting.RED));
        }
    }

    private int usarConvite(CommandSourceStack fonte, String codigo) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer jogador = fonte.getPlayerOrException();
        UUID uuid = jogador.getUUID();

        if (!pendentes.containsKey(uuid) || pendentes.get(uuid).senha) {
            jogador.sendSystemMessage(Component.literal("Seu convite já foi aceito.").withStyle(ChatFormatting.GRAY));
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
                        atual.sendSystemMessage(Component.literal(resultado.motivo()).withStyle(ChatFormatting.GREEN));
                        Pendente pd = pendentes.get(uuid);
                        if (pd != null) liberarOuPedirSenha(atual, pd);
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
            jogador.connection.disconnect(Component.literal(pendente.senha
                    ? "Tempo esgotado. Entre de novo e use /login SUA-SENHA."
                    : "Tempo esgotado. Peça um código de convite ao administrador do FRX-WORLD."));
        } else if (pendente.ticksRestantes % (20 * 30) == 0) {
            if (pendente.senha) pedirSenha(jogador); else pedirConvite(jogador);
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

    private static void pedirSenha(ServerPlayer jogador) {
        boolean tem = SenhasContas.tem(jogador.getGameProfile().getName());
        jogador.sendSystemMessage(Component.literal("🔒 ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(tem ? "Digite " : "Crie a senha da sua conta: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(tem ? "/login SUA-SENHA" : "/registrar SENHA SENHA").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(tem ? " para entrar." : " (ela protege seu nick: ninguém mais entra com ele).").withStyle(ChatFormatting.WHITE)));
    }

    private static void boasVindas(ServerPlayer jogador) {
        jogador.sendSystemMessage(Component.literal("Bem-vindo ao FRX-WORLD, ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(jogador.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("!").withStyle(ChatFormatting.GOLD)));
    }
}
