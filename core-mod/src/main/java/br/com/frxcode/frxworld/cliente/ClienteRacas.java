package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.SyncRaca;
import br.com.frxcode.frxworld.rede.SyncRacaVisivel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Raças no cliente: a minha (para a tela) e a aparência de cada jogador (nome, traços e aura). */
public final class ClienteRacas {
    public record Visivel(String texto, String principal, String secundaria, String aura) {}

    private static final Map<UUID, Visivel> VISIVEIS = new ConcurrentHashMap<>();

    public static String principal = "", secundaria = "";
    public static boolean escolheu = false, revelar = false, recebido = false;
    public static int trocas = 0;
    public static long custo = 0;
    public static Set<String> liberadas = new HashSet<>();

    private ClienteRacas() {}

    public static void aplicar(SyncRaca m) {
        principal = m.principal();
        secundaria = m.secundaria();
        escolheu = m.escolheu();
        trocas = m.trocas();
        custo = m.custo();
        revelar = m.revelar();
        liberadas = new HashSet<>(m.liberadas());
        recebido = true;
        Minecraft mc = Minecraft.getInstance();
        if (m.abrir()) mc.setScreen(new TelaRacas());
        else if (mc.screen instanceof TelaRacas t) t.atualizar();
    }

    public static void aplicar(SyncRacaVisivel m) {
        if (m.texto().isEmpty()) VISIVEIS.remove(m.jogador());
        else VISIVEIS.put(m.jogador(), new Visivel(m.texto(), m.visualPrincipal(), m.visualSecundaria(), m.aura()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Player p = mc.level.getPlayerByUUID(m.jogador());
            if (p != null) p.refreshDisplayName();
        }
    }

    public static Visivel de(UUID id) { return VISIVEIS.get(id); }

    /** Partículas bem discretas das lendárias (chamado a cada tick do cliente). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        var rnd = mc.level.getRandom();
        for (Player p : mc.level.players()) {
            Visivel v = VISIVEIS.get(p.getUUID());
            if (v == null || v.aura().isEmpty() || p.isInvisible()) continue;
            if (rnd.nextInt(40) != 0) continue;   // ~1 a cada 2 s
            ParticleOptions part = switch (v.aura()) {
                case "anjo", "celestial" -> ParticleTypes.END_ROD;
                case "rei_demonio" -> ParticleTypes.SMOKE;
                case "dragao" -> ParticleTypes.SMALL_FLAME;
                case "deus" -> ParticleTypes.WAX_ON;
                default -> null;
            };
            if (part == null) continue;
            double x = p.getX() + (rnd.nextDouble() - 0.5) * 0.8, y = p.getY() + rnd.nextDouble() * 1.8, z = p.getZ() + (rnd.nextDouble() - 0.5) * 0.8;
            mc.level.addParticle(part, x, y, z, 0, 0.01, 0);
        }
    }

    public static void limpar() {
        VISIVEIS.clear();
        principal = secundaria = "";
        escolheu = revelar = recebido = false;
        trocas = 0;
        custo = 0;
        liberadas = new HashSet<>();
    }
}
