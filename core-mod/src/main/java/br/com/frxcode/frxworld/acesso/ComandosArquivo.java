package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Canal de comandos por arquivo: o painel escreve em server/frx_comandos.txt (uma linha por comando) quando não é
 * "dono" do processo do servidor (o painel anterior caiu e o jogo continuou no ar). A cada meio segundo o servidor lê,
 * apaga o arquivo e executa as linhas como se fossem digitadas no console. O desligar-frx.bat usa o mesmo caminho
 * para mandar "stop" (salvando o mundo) antes de qualquer encerramento à força.
 */
public class ComandosArquivo {
    private static int relogio;

    /** /frx painel reiniciar: roda o reiniciar-painel.bat (fecha e sobe de novo só o painel; o jogo continua). */
    @SubscribeEvent
    public void comandos(net.minecraftforge.event.RegisterCommandsEvent ev) {
        ev.getDispatcher().register(net.minecraft.commands.Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("painel").then(net.minecraft.commands.Commands.literal("reiniciar").executes(ctx -> {
                    Path bat = FMLPaths.GAMEDIR.get().toAbsolutePath().getParent().resolve("reiniciar-painel.bat");
                    if (!Files.exists(bat)) { ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Não achei o reiniciar-painel.bat.")); return 0; }
                    try {
                        new ProcessBuilder("cmd.exe", "/c", bat.toString()).directory(bat.getParent().toFile())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Não consegui rodar o reiniciar-painel.bat: " + e.getMessage()));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Reiniciando só o painel; o jogo continua no ar."), true);
                    return 1;
                }))));
    }

    private static Path arquivo() { return FMLPaths.GAMEDIR.get().resolve("frx_comandos.txt"); }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++relogio % 10 != 0) return;
        Path a = arquivo();
        if (!Files.exists(a)) return;
        List<String> linhas;
        try {
            linhas = Files.readAllLines(a, StandardCharsets.UTF_8);
            Files.deleteIfExists(a);
        } catch (Exception e) {
            return;   // o painel ainda está escrevendo: tenta no próximo
        }
        MinecraftServer s = ev.getServer();
        for (String l : linhas) {
            String cmd = l.replace("﻿", "").trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (cmd.isEmpty()) continue;
            FrxWorld.LOG.info("[painel] > {}", cmd);
            try { s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), cmd); }
            catch (Exception e) { FrxWorld.LOG.warn("Comando do painel falhou ({}): {}", cmd, e.toString()); }
        }
    }
}
