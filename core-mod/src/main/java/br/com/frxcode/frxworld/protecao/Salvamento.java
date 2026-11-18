package br.com.frxcode.frxworld.protecao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Salvamento automático a cada 30 minutos em vez de 5.
 *
 * O jogo salva tudo quando o contador de ticks do servidor é múltiplo de 6000 (5 min), e isso segura um tick por ~0,2 s:
 * todo mundo sente a travada, inclusive em combate. O intervalo é fixo no código do jogo, então aqui o contador pula
 * os múltiplos de 6000 que não são múltiplos de 36000 (30 min). Nos pulados, só os jogadores são salvos (inventário e
 * progresso), que é a parte leve. Os pedaços do mundo continuam sendo gravados aos poucos, o tempo todo, como sempre.
 * Desligar e reiniciar o servidor continuam salvando tudo.
 *
 * O campo do contador é privado e tem outro nome fora do ambiente de desenvolvimento: ele é achado pelo valor
 * (o único int do servidor que acompanha getTickCount() em dois ticks seguidos). Se não achar, nada muda.
 */
public class Salvamento {
    private static final int DO_JOGO = 6000, INTERVALO = 36000;
    private static Field contador;
    private static List<Field> candidatos;
    private static boolean desistiu;

    public Salvamento() {}

    private static void achar(MinecraftServer s, int agora) {
        try {
            if (candidatos == null) {
                candidatos = new ArrayList<>();
                for (Field f : MinecraftServer.class.getDeclaredFields()) {
                    if (f.getType() != int.class || Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (f.getInt(s) == agora) candidatos.add(f);
                }
                return;   // confere de novo no próximo tick, com outro valor
            }
            List<Field> ainda = new ArrayList<>();
            for (Field f : candidatos) if (f.getInt(s) == agora) ainda.add(f);
            candidatos = ainda;
            if (candidatos.size() == 1) {
                contador = candidatos.get(0);
                FrxWorld.LOG.info("Salvamento automático: a cada 30 minutos (jogadores a cada 5).");
            } else if (candidatos.isEmpty()) {
                desistiu = true;
                FrxWorld.LOG.warn("Salvamento automático: não achei o contador do servidor; continua a cada 5 minutos.");
            }
        } catch (Throwable t) {
            desistiu = true;
            FrxWorld.LOG.warn("Salvamento automático: continua a cada 5 minutos ({}).", t.toString());
        }
    }

    @SubscribeEvent
    public void antesDoTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.START || desistiu) return;
        MinecraftServer s = ev.getServer();
        int agora = s.getTickCount();
        if (contador == null) { achar(s, agora); return; }
        int proximo = agora + 1;   // o servidor soma 1 logo depois deste evento e então confere o múltiplo de 6000
        if (proximo % DO_JOGO != 0 || proximo % INTERVALO == 0) return;
        try {
            contador.setInt(s, proximo);        // o tick vira proximo + 1: o salvamento completo desta vez não acontece
            s.getPlayerList().saveAll();        // mas inventário e progresso dos jogadores ficam salvos
        } catch (Throwable t) {
            desistiu = true;
            FrxWorld.LOG.warn("Salvamento automático: voltou a ser a cada 5 minutos ({}).", t.toString());
        }
    }
}
