package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Duração do dia e da noite. No Minecraft cada metade dura 10 minutos; aqui o admin escolhe quantos minutos
 * reais dura o dia e quantos dura a noite (/frx ciclo <dia> <noite>). O mod desliga o avanço automático do
 * relógio (doDaylightCycle) e anda o horário ele mesmo, mais devagar. Dormir continua pulando a noite.
 * Registro por mundo em frx_ciclo.txt.
 */
public class CicloDia {
    private static int diaMin = 20, noiteMin = 10;
    private static double resto = 0;

    public CicloDia() {}

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_ciclo.txt"); }

    private static boolean padrao() { return diaMin == 10 && noiteMin == 10; }

    private static void aplicarRegra(MinecraftServer s) {
        // com o ciclo normal (10/10) o jogo cuida do relógio; fora disso, quem anda o horário é o mod
        s.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(padrao(), s);
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        try {
            Path a = arquivo(ev.getServer());
            if (Files.exists(a)) {
                String[] t = Files.readString(a).trim().split("\\s+");
                diaMin = Math.max(1, Math.min(240, Integer.parseInt(t[0])));
                noiteMin = Math.max(1, Math.min(240, Integer.parseInt(t[1])));
            }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Ciclo do dia: não li frx_ciclo.txt: {}", e.toString());
        }
        aplicarRegra(ev.getServer());
        FrxWorld.LOG.info("Ciclo do dia: dia de {} min, noite de {} min", diaMin, noiteMin);
    }

    /**
     * Dormir pula a noite. Com o relógio do jogo desligado (doDaylightCycle = false) o Minecraft acorda os jogadores
     * mas não muda a hora, então o mod faz isso: no começo do tick, antes de o jogo acordar todo mundo.
     */
    private static void pularNoite(ServerLevel n) {
        int total = 0, dormindo = 0;
        for (net.minecraft.server.level.ServerPlayer p : n.players()) {
            if (p.isSpectator()) continue;
            total++;
            if (p.isSleepingLongEnough()) dormindo++;
        }
        if (total == 0 || dormindo == 0) return;
        int pct = n.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int precisa = Math.max(1, (int) Math.ceil(total * pct / 100.0));
        if (dormindo < precisa) return;
        long t = n.getDayTime();
        n.setDayTime(t + 24000L - Math.floorMod(t, 24000L));   // manhã seguinte
        resto = 0;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (padrao()) return;
        if (ev.phase == TickEvent.Phase.START) {
            ServerLevel mundo = ev.getServer().overworld();
            if (!mundo.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) pularNoite(mundo);
            return;
        }
        ServerLevel n = ev.getServer().overworld();
        if (n.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;   // alguém religou o relógio do jogo à mão: não soma por cima
        long t = n.getDayTime();
        boolean dia = Math.floorMod(t, 24000L) < 12000;
        resto += 10.0 / (dia ? diaMin : noiteMin);
        long passos = (long) resto;
        if (passos > 0) {
            resto -= passos;
            n.setDayTime(t + passos);
        }
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("ciclo")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("Ciclo: dia de " + diaMin + " min e noite de " + noiteMin
                                    + " min (o normal do jogo é 10 e 10). Mude com /frx ciclo <dia> <noite>.").withStyle(ChatFormatting.GOLD), false);
                            return 1;
                        })
                        .then(Commands.argument("dia", IntegerArgumentType.integer(1, 240)).then(Commands.argument("noite", IntegerArgumentType.integer(1, 240))
                                .executes(ctx -> {
                                    diaMin = IntegerArgumentType.getInteger(ctx, "dia");
                                    noiteMin = IntegerArgumentType.getInteger(ctx, "noite");
                                    MinecraftServer s = ctx.getSource().getServer();
                                    try { Files.writeString(arquivo(s), diaMin + " " + noiteMin); } catch (Exception ignorado) { }
                                    aplicarRegra(s);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Ciclo ajustado: dia de " + diaMin + " min, noite de " + noiteMin + " min.").withStyle(ChatFormatting.GOLD), true);
                                    return 1;
                                })))));
    }
}
