package br.com.frxcode.frxworld.progressao;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * /ping (todos): o seu ping até o servidor e como o servidor está rodando.
 * /frx ping (admin): o ping de todos os jogadores online.
 * O ping é o que o servidor mede pelo "keep alive" (média dos últimos segundos); logo depois de entrar ele ainda aparece como 0.
 */
public class Ping {
    public Ping() {}

    private static ChatFormatting cor(int ms) { return ms <= 0 ? ChatFormatting.GRAY : ms < 80 ? ChatFormatting.GREEN : ms < 150 ? ChatFormatting.YELLOW : ms < 250 ? ChatFormatting.GOLD : ChatFormatting.RED; }
    private static String nota(int ms) { return ms <= 0 ? "medindo" : ms < 80 ? "ótimo" : ms < 150 ? "bom" : ms < 250 ? "regular" : "ruim"; }

    private static MutableComponent linhaServidor(MinecraftServer s) {
        double mspt = s.getAverageTickTime(), tps = Math.min(20.0, 1000.0 / Math.max(50.0, mspt));
        ChatFormatting c = tps >= 19 ? ChatFormatting.GREEN : tps >= 15 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal("Servidor: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(java.util.Locale.ROOT, "%.1f TPS", tps)).withStyle(c))
                .append(Component.literal(String.format(java.util.Locale.ROOT, " · %.1f ms por tick (o limite é 50)", mspt)).withStyle(ChatFormatting.GRAY));
    }

    private static int meu(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int ms = p.latency;
        p.sendSystemMessage(Component.literal("📶 Seu ping: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ms <= 0 ? "medindo..." : ms + " ms").withStyle(cor(ms), ChatFormatting.BOLD))
                .append(Component.literal(ms <= 0 ? " (tente de novo em alguns segundos)" : " (" + nota(ms) + ")").withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(linhaServidor(p.server));
        if (ms >= 250) p.sendSystemMessage(Component.literal("Ping alto costuma ser da sua internet ou da rota até o servidor, não do servidor em si.").withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int todos(CommandSourceStack src) {
        MinecraftServer s = src.getServer();
        src.sendSuccess(() -> linhaServidor(s), false);
        if (s.getPlayerList().getPlayers().isEmpty()) { src.sendSuccess(() -> Component.literal("Ninguém online."), false); return 1; }
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            int ms = p.latency;
            src.sendSuccess(() -> Component.literal("  " + p.getGameProfile().getName() + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(ms <= 0 ? "medindo" : ms + " ms (" + nota(ms) + ")").withStyle(cor(ms))), false);
        }
        return 1;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("ping").executes(c -> meu(c.getSource())));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("ping").executes(c -> todos(c.getSource()))));
    }
}
