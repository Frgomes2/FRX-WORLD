package br.com.frxcode.frxworld;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Reinício automático de madrugada (config manutencao.reinicioDiario, padrão 05:00, horário de Brasília; vazio = desligado).
 * Só pede o reinício com o servidor vazio, ligado há pelo menos 3 h e uma vez por dia; tenta da hora marcada até 2 h depois.
 * O pedido vai pelo agente do PC (controle/pedido.txt com "reiniciar"), que salva o mundo, faz o backup e liga de novo.
 * Limpa a memória que alguns mods vão segurando e coloca no ar as atualizações já prontas.
 */
public class ReinicioDiario {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final long TRES_HORAS = 3L * 3_600_000L;
    private final long ligouEm = System.currentTimeMillis();
    private int relogio;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++relogio % 1200 != 0) return;   // a cada minuto
        MinecraftServer s = ev.getServer();
        String hora = Config.REINICIO_DIARIO.get().trim();
        if (hora.isEmpty() || System.currentTimeMillis() - ligouEm < TRES_HORAS || !s.getPlayerList().getPlayers().isEmpty()) return;
        LocalTime alvo;
        try { alvo = LocalTime.parse(hora); } catch (Exception e) { return; }
        ZonedDateTime agora = ZonedDateTime.now(FUSO);
        LocalTime t = agora.toLocalTime();
        if (t.isBefore(alvo) || t.isAfter(alvo.plusHours(2))) return;
        Path marca = s.getWorldPath(LevelResource.ROOT).resolve("frx_reinicio_diario.txt");
        String hoje = LocalDate.now(FUSO).toString();
        try {
            if (Files.exists(marca) && Files.readString(marca, StandardCharsets.UTF_8).trim().equals(hoje)) return;
            Path pedido = Path.of("..", "controle", "pedido.txt").toAbsolutePath().normalize();
            if (!Files.isDirectory(pedido.getParent())) return;   // sem o agente do PC (outro lugar): não faz nada
            if (Files.exists(pedido) && !Files.readString(pedido, StandardCharsets.UTF_8).isBlank()) return;   // já tem um pedido esperando
            Files.writeString(marca, hoje, StandardCharsets.UTF_8);
            Files.writeString(pedido, "reiniciar\n", StandardCharsets.UTF_8);
            FrxWorld.LOG.info("Reinício diário: servidor vazio às {}, pedi o reinício ao agente.", t.withNano(0));
        } catch (Exception e) {
            FrxWorld.LOG.warn("Reinício diário: não consegui pedir: {}", e.toString());
        }
    }
}
