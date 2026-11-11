package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.racas.DefRacas;
import br.com.frxcode.frxworld.racas.RacasJogador;
import br.com.frxcode.frxworld.reinos.Campanhas;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Marcos de progressão do FRX virando conquistas ocultas (data/frxworld/advancements/frx/*.json, sem aviso na tela).
 * O FTB Quests usa essas conquistas como tarefas ("advancement"), então a campanha do livro acompanha
 * nível, classe, raça, reinos, campanhas dos reis, taverna, dungeons, Elites, Campeões e chefes.
 */
public class Marcos {
    public static final int[] NIVEIS = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 75, 100};

    public Marcos() {}

    /** Concede a conquista oculta frxworld:frx/<id> (se ainda não tiver). */
    public static void marcar(ServerPlayer p, String id) {
        try {
            Advancement a = p.server.getAdvancements().getAdvancement(new ResourceLocation(FrxWorld.MOD_ID, "frx/" + id));
            if (a == null) return;
            AdvancementProgress pr = p.getAdvancements().getOrStartProgress(a);
            if (pr.isDone()) return;
            for (String c : pr.getRemainingCriteria()) p.getAdvancements().award(a, c);
        } catch (Exception e) {
            FrxWorld.LOG.debug("Marco {} falhou: {}", id, e.toString());
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 100 != 7 || !Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        for (int n : NIVEIS) if (d.nivel() >= n) marcar(p, "nivel_" + n);
        if (!d.classe().isEmpty()) marcar(p, "classe");
        if (RacasJogador.escolheu(p)) {
            marcar(p, "raca");
            DefRacas.Raca r = DefRacas.de(RacasJogador.principal(p));
            if (r != null && r.nivel() >= DefRacas.ESPECIAL) marcar(p, "raca_especial");
            if (r != null && r.nivel() >= DefRacas.LENDARIA) marcar(p, "raca_lendaria");
        }
        for (Reino r : Reinos.todos()) {
            if (r.distancia(p.getBlockX(), p.getBlockZ()) == 0 && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) marcar(p, "capital_" + r.id());
            if (d.reputacao().getOrDefault(r.id(), 0) >= 500) marcar(p, "honrado_" + r.id());
            Campanhas.Campanha c = Campanhas.TODAS.get(r.id());
            if (c != null && d.campanhaEtapa().getOrDefault(r.id(), -1) >= c.etapas().size()) marcar(p, "campanha_" + r.id());
        }
        long missoes = d.missoes().values().stream().filter(m -> m.concluida).count();
        if (missoes >= 1) marcar(p, "taverna_1");
        if (missoes >= 10) marcar(p, "taverna_10");
        if (!d.dungeons().isEmpty()) marcar(p, "dungeon");
        if (d.contadores().getOrDefault("chefes", 0) > 0) marcar(p, "chefe_mundial");
    }
}
