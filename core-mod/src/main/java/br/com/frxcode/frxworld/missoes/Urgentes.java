package br.com.frxcode.frxworld.missoes;

import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import com.google.gson.JsonObject;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Missão urgente da Guilda: de tempos em tempos, uma caçada do quadro vira urgente por alguns minutos.
 * Quem entregar dentro do prazo recebe a recompensa multiplicada. O Sistema avisa quem tem rank para ela.
 * Os números vêm do painel (Missões → Missões urgentes).
 */
public class Urgentes {
    private static volatile String id = "";
    private static volatile long fim = 0, proxima = System.currentTimeMillis() + 5 * 60_000L;
    private static boolean avisouFim = false;

    public Urgentes() {}

    private static JsonObject cfg() { return ConfigJogo.secaoCrua("urgentes"); }
    private static double num(String chave, double padrao) {
        try { JsonObject o = cfg(); return o != null && o.has(chave) ? o.get(chave).getAsDouble() : padrao; } catch (Exception e) { return padrao; }
    }
    private static boolean ligado() {
        try { JsonObject o = cfg(); return o == null || !o.has("ativo") || o.get("ativo").getAsBoolean(); } catch (Exception e) { return true; }
    }
    private static long intervaloMs() { return (long) (Math.max(5, num("intervaloMin", 45)) * 60_000L); }
    private static long duracaoMs() { return (long) (Math.max(3, num("duracaoMin", 20)) * 60_000L); }

    public static boolean ativa(String missao) { return !id.isEmpty() && id.equals(missao) && System.currentTimeMillis() < fim; }
    public static double multiplicador() { return Math.max(1, Math.min(10, num("multiplicador", 2))); }
    public static long restaMin() { return Math.max(1, (fim - System.currentTimeMillis() + 59_999) / 60_000); }

    private static boolean podePegar(ServerPlayer p, MissaoDef m) {
        return Progressao.temDados(p) && Progressao.dados(p).nivel() >= m.nivelMin() && Guilda.indice(p) >= m.rankMin() && (m.rankMin() == 0 || Guilda.registrado(p));
    }

    private static String vezes() {
        double x = multiplicador();
        return x == 2 ? "em dobro" : x == 3 ? "em triplo" : "×" + (x == Math.floor(x) ? String.valueOf((int) x) : String.valueOf(x));
    }

    /** Sorteia e anuncia uma missão urgente. Devolve o título, ou "" se ninguém online pode pegar nenhuma. */
    static String comecar(MinecraftServer s) {
        List<ServerPlayer> online = s.getPlayerList().getPlayers();
        List<MissaoDef> opcoes = new ArrayList<>();
        for (MissaoDef m : Missoes.todas()) {
            if (!m.caca()) continue;
            for (ServerPlayer p : online) if (podePegar(p, m)) { opcoes.add(m); break; }
        }
        if (opcoes.isEmpty()) return "";
        // prefere as de rank mais alto que alguém online alcança
        int topo = opcoes.stream().mapToInt(MissaoDef::rankMin).max().orElse(0);
        List<MissaoDef> altas = opcoes.stream().filter(m -> m.rankMin() >= topo - 1).toList();
        MissaoDef m = altas.get(ThreadLocalRandom.current().nextInt(altas.size()));
        id = m.id();
        fim = System.currentTimeMillis() + duracaoMs();
        avisouFim = false;
        for (ServerPlayer p : online) {
            if (!podePegar(p, m)) continue;
            Sistema.avisar(p, "Missão urgente · Rank " + ConfigJogo.nomeRank(m.rankMin()),
                    m.titulo() + ": entregue em até " + restaMin() + " min e receba a recompensa " + vezes() + ". Aceite no quadro do taverneiro.", Sistema.VERMELHO);
            p.level().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1f, 1.2f);
            // fica também no chat, para poder reler depois que a janela do Sistema some
            p.sendSystemMessage(Component.literal("⚠ [Sistema] Missão urgente · Rank " + ConfigJogo.nomeRank(m.rankMin()) + ": ").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)
                    .append(Component.literal(m.titulo()).withStyle(net.minecraft.ChatFormatting.WHITE)));
            if (!m.descricao().isEmpty()) p.sendSystemMessage(Component.literal("   " + m.descricao()).withStyle(net.minecraft.ChatFormatting.GRAY));
            p.sendSystemMessage(Component.literal("   Prazo: " + restaMin() + " min · recompensa " + vezes() + " · aceite e entregue no quadro do taverneiro.").withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        return m.titulo();
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 40 != 0) return;
        MinecraftServer s = ev.getServer();
        long agora = System.currentTimeMillis();
        if (!id.isEmpty()) {
            MissaoDef m = Missoes.porId(id);
            if (m == null || agora >= fim) {
                if (m != null) for (ServerPlayer p : s.getPlayerList().getPlayers())
                    if (Missoes.emAndamento(p, id)) {
                        Sistema.avisar(p, "Missão urgente encerrada", m.titulo() + ": o prazo acabou. A missão continua valendo, com a recompensa normal.", Sistema.VERMELHO);
                        p.sendSystemMessage(Component.literal("⚠ [Sistema] Missão urgente encerrada: " + m.titulo() + ". O prazo acabou; ela continua valendo, com a recompensa normal.").withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                id = "";
                proxima = agora + intervaloMs();
                return;
            }
            if (!avisouFim && fim - agora <= 5 * 60_000L && fim - agora > 4 * 60_000L) {
                avisouFim = true;
                for (ServerPlayer p : s.getPlayerList().getPlayers())
                    if (Missoes.emAndamento(p, id)) {
                        Sistema.avisar(p, "Missão urgente: 5 minutos", "Entregue " + m.titulo() + " ao taverneiro antes do fim do prazo.", Sistema.VERMELHO);
                        p.sendSystemMessage(Component.literal("⚠ [Sistema] Faltam 5 minutos para a missão urgente: " + m.titulo() + ".").withStyle(net.minecraft.ChatFormatting.YELLOW));
                    }
            }
            return;
        }
        if (!ligado() || agora < proxima || s.getPlayerList().getPlayerCount() == 0) return;
        if (comecar(s).isEmpty()) proxima = agora + 5 * 60_000L;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("urgente")
                        .executes(ctx -> {
                            String t = comecar(ctx.getSource().getServer());
                            if (t.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Nenhuma caçada do quadro serve para quem está online (nível e rank).")); return 0; }
                            ctx.getSource().sendSuccess(() -> Component.literal("Missão urgente: " + t), true);
                            return 1;
                        })
                        .then(Commands.literal("encerrar").executes(ctx -> { fim = 0; return 1; }))));
    }
}
