package br.com.frxcode.frxworld.grupos;

import br.com.frxcode.frxworld.missoes.Missoes;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grupos (party) de até 6 jogadores: XP dos abates dividida com bônus entre quem está perto,
 * progresso de missões de caça compartilhado, sem dano entre membros, chat do grupo (/g),
 * nome colorido e alertas de vida baixa e morte.
 */
public class Grupos {
    public static final int MAXIMO = 6;
    public static final double DISTANCIA = 48;
    private static final ChatFormatting[] CORES = {ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.YELLOW, ChatFormatting.BLUE, ChatFormatting.RED, ChatFormatting.DARK_GREEN, ChatFormatting.GOLD};

    public static final class Grupo {
        final int id;
        UUID lider;
        final Set<UUID> membros = new LinkedHashSet<>();
        final ChatFormatting cor;

        Grupo(int id, UUID lider) {
            this.id = id;
            this.lider = lider;
            this.cor = CORES[id % CORES.length];
            membros.add(lider);
        }
    }

    private static final Map<UUID, Grupo> DE = new HashMap<>();
    private static final Map<UUID, UUID> CONVITES = new HashMap<>();   // convidado → quem convidou
    private static final Map<UUID, Long> ALERTA = new HashMap<>();
    private static int proximo = 1;

    public static Grupo de(Player p) { return DE.get(p.getUUID()); }

    public static boolean mesmoGrupo(Player a, Player b) {
        Grupo g = DE.get(a.getUUID());
        return g != null && g.membros.contains(b.getUUID());
    }

    private static MinecraftServer servidor() { return ServerLifecycleHooks.getCurrentServer(); }

    private static List<ServerPlayer> online(Grupo g) {
        List<ServerPlayer> l = new ArrayList<>();
        MinecraftServer s = servidor();
        if (s == null) return l;
        for (UUID u : g.membros) {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p != null) l.add(p);
        }
        return l;
    }

    /** Membros online perto (mesma dimensão, até 48 blocos), incluindo o próprio. */
    public static List<ServerPlayer> perto(ServerPlayer p) {
        Grupo g = de(p);
        if (g == null) return List.of(p);
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer m : online(g))
            if (m == p || (m.level() == p.level() && m.distanceTo(p) <= DISTANCIA && m.isAlive())) l.add(m);
        return l;
    }

    private static String nome(UUID u) {
        MinecraftServer s = servidor();
        ServerPlayer p = s == null ? null : s.getPlayerList().getPlayer(u);
        if (p != null) return p.getGameProfile().getName();
        return s != null && s.getProfileCache() != null ? s.getProfileCache().get(u).map(gp -> gp.getName()).orElse("?") : "?";
    }

    private static void avisarGrupo(Grupo g, Component msg) {
        for (ServerPlayer m : online(g)) m.sendSystemMessage(msg);
    }

    private static MutableComponent prefixo(Grupo g) {
        return Component.literal("[Grupo] ").withStyle(g.cor, ChatFormatting.BOLD);
    }

    private static void info(ServerPlayer p, String texto, ChatFormatting cor) {
        p.sendSystemMessage(Component.literal("[Grupo] ").withStyle(ChatFormatting.GOLD).append(Component.literal(texto).withStyle(cor)));
    }

    // ------------------------------------------------------------------ time do placar (cor do nome)

    private static void atualizarTime(Grupo g) {
        MinecraftServer s = servidor();
        if (s == null) return;
        Scoreboard placar = s.getScoreboard();
        String nomeTime = "frx_g" + g.id;
        PlayerTeam time = placar.getPlayerTeam(nomeTime);
        if (g.membros.isEmpty()) {
            if (time != null) placar.removePlayerTeam(time);
            return;
        }
        if (time == null) {
            time = placar.addPlayerTeam(nomeTime);
            time.setColor(g.cor);
            time.setAllowFriendlyFire(false);
            time.setSeeFriendlyInvisibles(true);
        }
        for (String n : new ArrayList<>(time.getPlayers())) {
            boolean membro = false;
            for (UUID u : g.membros) if (nome(u).equals(n)) membro = true;
            if (!membro) placar.removePlayerFromTeam(n, time);
        }
        for (UUID u : g.membros) {
            String n = nome(u);
            if (!"?".equals(n) && placar.getPlayersTeam(n) != time) placar.addPlayerToTeam(n, time);
        }
    }

    private static void tirarDoTime(Grupo g, UUID u) {
        MinecraftServer s = servidor();
        if (s == null) return;
        PlayerTeam time = s.getScoreboard().getPlayerTeam("frx_g" + g.id);
        String n = nome(u);
        if (time != null && time.getPlayers().contains(n)) s.getScoreboard().removePlayerFromTeam(n, time);
    }

    // ------------------------------------------------------------------ ações

    static void convidar(ServerPlayer p, ServerPlayer alvo) {
        if (alvo == p) { info(p, "Você não pode se convidar.", ChatFormatting.RED); return; }
        Grupo g = de(p);
        if (g != null && !g.lider.equals(p.getUUID())) { info(p, "Só o líder convida.", ChatFormatting.RED); return; }
        if (g != null && g.membros.size() >= MAXIMO) { info(p, "Grupo cheio (" + MAXIMO + ").", ChatFormatting.RED); return; }
        if (de(alvo) != null) { info(p, alvo.getGameProfile().getName() + " já está em um grupo.", ChatFormatting.RED); return; }
        CONVITES.put(alvo.getUUID(), p.getUUID());
        info(p, "Convite enviado para " + alvo.getGameProfile().getName() + ".", ChatFormatting.GREEN);
        alvo.sendSystemMessage(Component.literal("[Grupo] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(p.getGameProfile().getName() + " convidou você para o grupo. ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("[Aceitar]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/grupo aceitar"))))
                .append(Component.literal(" "))
                .append(Component.literal("[Recusar]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/grupo recusar")))));
        alvo.level().playSound(null, alvo.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 1.5f);
    }

    static void aceitar(ServerPlayer p) {
        UUID de = CONVITES.remove(p.getUUID());
        MinecraftServer s = servidor();
        ServerPlayer lider = de == null || s == null ? null : s.getPlayerList().getPlayer(de);
        if (lider == null) { info(p, "Nenhum convite válido.", ChatFormatting.RED); return; }
        if (de(p) != null) { info(p, "Saia do seu grupo antes (/grupo sair).", ChatFormatting.RED); return; }
        Grupo g = de(lider);
        if (g == null) {
            g = new Grupo(proximo++, lider.getUUID());
            DE.put(lider.getUUID(), g);
        }
        if (g.membros.size() >= MAXIMO) { info(p, "O grupo está cheio.", ChatFormatting.RED); return; }
        g.membros.add(p.getUUID());
        DE.put(p.getUUID(), g);
        atualizarTime(g);
        avisarGrupo(g, prefixo(g).append(Component.literal(p.getGameProfile().getName() + " entrou no grupo.").withStyle(ChatFormatting.GREEN)));
    }

    static void sair(ServerPlayer p) {
        Grupo g = de(p);
        if (g == null) { info(p, "Você não está em um grupo.", ChatFormatting.GRAY); return; }
        remover(g, p.getUUID());
        info(p, "Você saiu do grupo.", ChatFormatting.YELLOW);
        avisarGrupo(g, prefixo(g).append(Component.literal(p.getGameProfile().getName() + " saiu do grupo.").withStyle(ChatFormatting.YELLOW)));
    }

    private static void remover(Grupo g, UUID u) {
        tirarDoTime(g, u);
        g.membros.remove(u);
        DE.remove(u);
        if (g.membros.size() <= 1) {
            for (UUID resto : new ArrayList<>(g.membros)) {
                tirarDoTime(g, resto);
                DE.remove(resto);
                MinecraftServer s = servidor();
                ServerPlayer rp = s == null ? null : s.getPlayerList().getPlayer(resto);
                if (rp != null) info(rp, "O grupo foi desfeito.", ChatFormatting.GRAY);
            }
            g.membros.clear();
            atualizarTime(g);
            return;
        }
        if (g.lider.equals(u)) {
            g.lider = g.membros.iterator().next();
            avisarGrupo(g, prefixo(g).append(Component.literal(nome(g.lider) + " agora é o líder.").withStyle(ChatFormatting.GOLD)));
        }
    }

    static void expulsar(ServerPlayer p, String nomeAlvo) {
        Grupo g = de(p);
        if (g == null || !g.lider.equals(p.getUUID())) { info(p, "Só o líder expulsa.", ChatFormatting.RED); return; }
        for (UUID u : new ArrayList<>(g.membros)) {
            if (nome(u).equalsIgnoreCase(nomeAlvo) && !u.equals(p.getUUID())) {
                remover(g, u);
                avisarGrupo(g, prefixo(g).append(Component.literal(nomeAlvo + " foi removido do grupo.").withStyle(ChatFormatting.YELLOW)));
                MinecraftServer s = servidor();
                ServerPlayer ex = s == null ? null : s.getPlayerList().getPlayer(u);
                if (ex != null) info(ex, "Você foi removido do grupo.", ChatFormatting.YELLOW);
                return;
            }
        }
        info(p, nomeAlvo + " não está no seu grupo.", ChatFormatting.RED);
    }

    static void mostrar(ServerPlayer p) {
        Grupo g = de(p);
        if (g == null) {
            info(p, "Você não está em grupo. Use /grupo convidar <nick>.", ChatFormatting.GRAY);
            return;
        }
        p.sendSystemMessage(prefixo(g).append(Component.literal(g.membros.size() + "/" + MAXIMO + " membros · XP dividida com +10% por membro perto").withStyle(ChatFormatting.GRAY)));
        MinecraftServer s = servidor();
        for (UUID u : g.membros) {
            ServerPlayer m = s == null ? null : s.getPlayerList().getPlayer(u);
            MutableComponent l = Component.literal((u.equals(g.lider) ? " ★ " : "   ") + nome(u)).withStyle(g.cor);
            if (m == null) l.append(Component.literal("  (offline)").withStyle(ChatFormatting.DARK_GRAY));
            else {
                l.append(Component.literal("  Nv " + Progressao.dados(m).nivel()).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  ❤ " + Math.round(m.getHealth()) + "/" + Math.round(m.getMaxHealth())).withStyle(ChatFormatting.RED));
                if (m != p) {
                    String onde = m.level() != p.level() ? "outra dimensão" : Math.round(m.distanceTo(p)) + " blocos";
                    l.append(Component.literal("  · " + onde + " (" + m.getBlockX() + ", " + m.getBlockY() + ", " + m.getBlockZ() + ")").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            p.sendSystemMessage(l);
        }
    }

    static void chat(ServerPlayer p, String texto) {
        Grupo g = de(p);
        if (g == null) { info(p, "Você não está em grupo.", ChatFormatting.GRAY); return; }
        avisarGrupo(g, prefixo(g).append(Component.literal(p.getGameProfile().getName() + ": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(texto).withStyle(ChatFormatting.GRAY)));
    }

    // ------------------------------------------------------------------ XP e missões

    /** Reparte a XP de um abate entre os membros perto; cada um ganha (total × (1 + 0,1 × extras)) / n. */
    public static void distribuirXp(ServerPlayer matador, long xp, LivingEntity morto) {
        List<ServerPlayer> l = perto(matador);
        if (l.size() <= 1) {
            Progressao.ganharXp(matador, xp);
            Missoes.aoMatar(matador, morto);
            return;
        }
        long cada = Math.max(1, Math.round(xp * (1 + 0.1 * (l.size() - 1)) / l.size()));
        for (ServerPlayer m : l) {
            Progressao.ganharXp(m, cada);
            Missoes.aoMatar(m, morto);
        }
    }

    // ------------------------------------------------------------------ eventos

    /** Sem dano entre membros do grupo. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void semFogoAmigo(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer alvo && e.getSource().getEntity() instanceof ServerPlayer atacante
                && alvo != atacante && mesmoGrupo(alvo, atacante)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Grupo g = de(p);
        if (g == null) return;
        avisarGrupo(g, prefixo(g).append(Component.literal("☠ " + p.getGameProfile().getName() + " caiu em " + p.getBlockX() + ", "
                + p.getBlockY() + ", " + p.getBlockZ() + "!").withStyle(ChatFormatting.RED)));
    }

    /** Alerta de vida baixa para o grupo (no máx. a cada 15 s por jogador). */
    @SubscribeEvent
    public void vidaBaixa(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        Grupo g = de(p);
        if (g == null || !p.isAlive() || p.getHealth() > p.getMaxHealth() * 0.3f) return;
        long agora = System.currentTimeMillis();
        if (agora - ALERTA.getOrDefault(p.getUUID(), 0L) < 15000) return;
        ALERTA.put(p.getUUID(), agora);
        for (ServerPlayer m : online(g)) {
            if (m == p) continue;
            m.displayClientMessage(Component.literal("⚠ " + p.getGameProfile().getName() + " com pouca vida (" + Math.round(p.getHealth()) + "❤)")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Grupo g = de(p);
        if (g != null) {
            atualizarTime(g);
            avisarGrupo(g, prefixo(g).append(Component.literal(p.getGameProfile().getName() + " voltou.").withStyle(ChatFormatting.GREEN)));
        }
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent e) {
        CONVITES.remove(e.getEntity().getUUID());
        Grupo g = de(e.getEntity());
        if (g != null) avisarGrupo(g, prefixo(g).append(Component.literal(e.getEntity().getGameProfile().getName() + " saiu do jogo.").withStyle(ChatFormatting.GRAY)));
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("grupo")
                .executes(ctx -> { mostrar(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("convidar").then(Commands.argument("jogador", EntityArgument.player())
                        .executes(ctx -> { convidar(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "jogador")); return 1; })))
                .then(Commands.literal("aceitar").executes(ctx -> { aceitar(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("recusar").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    CONVITES.remove(p.getUUID());
                    info(p, "Convite recusado.", ChatFormatting.GRAY);
                    return 1;
                }))
                .then(Commands.literal("sair").executes(ctx -> { sair(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("expulsar").then(Commands.argument("nick", StringArgumentType.word())
                        .executes(ctx -> { expulsar(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nick")); return 1; })))
                .then(Commands.literal("chat").then(Commands.argument("mensagem", StringArgumentType.greedyString())
                        .executes(ctx -> { chat(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mensagem")); return 1; }))));
        evento.getDispatcher().register(Commands.literal("g").then(Commands.argument("mensagem", StringArgumentType.greedyString())
                .executes(ctx -> { chat(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mensagem")); return 1; })));
    }

    /** Para a janela de status (tecla E). */
    public static com.google.gson.JsonObject json(ServerPlayer p) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        Grupo g = de(p);
        o.addProperty("tem", g != null);
        o.addProperty("convite", CONVITES.containsKey(p.getUUID()));
        if (g == null || p.getServer() == null) return o;
        o.addProperty("lider", g.lider.equals(p.getUUID()));
        com.google.gson.JsonArray ms = new com.google.gson.JsonArray();
        for (UUID id : g.membros) {
            ServerPlayer m = p.getServer().getPlayerList().getPlayer(id);
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            if (m == null) { j.addProperty("nome", "(offline)"); j.addProperty("online", false); ms.add(j); continue; }
            j.addProperty("nome", m.getGameProfile().getName());
            j.addProperty("online", true);
            j.addProperty("lider", id.equals(g.lider));
            j.addProperty("nivel", Progressao.temDados(m) ? Progressao.dados(m).nivel() : 1);
            j.addProperty("vida", (int) Math.ceil(m.getHealth()));
            j.addProperty("vidaMax", (int) Math.ceil(m.getMaxHealth()));
            j.addProperty("dist", m.level() == p.level() ? (int) m.distanceTo(p) : -1);
            ms.add(j);
        }
        o.add("membros", ms);
        return o;
    }
}
