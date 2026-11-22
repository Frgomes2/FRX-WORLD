package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.armas.Codex;
import br.com.frxcode.frxworld.progressao.Sistema;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conjuntos de relíquias: quando o jogador veste ao mesmo tempo todas as peças do Códex de um mesmo conjunto
 * (definido nos enigmas, campo "conjunto"), recupera vida sozinho enquanto estiver com elas.
 *
 * A recuperação acompanha o nível do personagem: % da vida máxima por segundo = 0,5 + 9,5 × (nível/100)²
 * (nível 1: 0,5%/s; 50: 2,9%/s; 100 ou mais: 10%/s, vida cheia em ~10 s). Nos 2 segundos depois de levar dano
 * cai pela metade: dano forte e seguido ainda mata. Conferido a cada segundo; ao completar o conjunto, aparece um aviso.
 */
public class Conjuntos {
    private static final Map<UUID, Set<String>> ativos = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> feridoEm = new ConcurrentHashMap<>();   // tick do último dano recebido

    /** % da vida máxima recuperada por segundo no nível dado. */
    public static double porSegundo(int nivel) {
        double n = Math.max(1, Math.min(100, nivel)) / 100.0;
        return 0.5 + 9.5 * n * n;
    }

    private static int nivel(ServerPlayer p) {
        return br.com.frxcode.frxworld.progressao.Progressao.temDados(p) ? br.com.frxcode.frxworld.progressao.Progressao.dados(p).nivel() : 1;
    }

    @SubscribeEvent
    public void aoSerFerido(net.minecraftforge.event.entity.living.LivingDamageEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && ev.getAmount() > 0) feridoEm.put(p.getUUID(), p.server.getTickCount());
    }
    private int ticks;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++ticks % 20 != 17) return;
        var conjuntos = Enigma.conjuntos();
        if (conjuntos.isEmpty()) return;
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) {
            Set<String> vestidos = new HashSet<>();
            for (ItemStack s : p.getInventory().armor) {
                String c = Codex.codigoDe(s);
                if (c != null) vestidos.add(c);
            }
            Set<String> antes = ativos.getOrDefault(p.getUUID(), Set.of()), agora = new HashSet<>();
            for (var e : conjuntos.entrySet()) {
                Set<String> pecas = e.getValue().getValue();
                if (pecas.size() < 2 || !vestidos.containsAll(pecas)) continue;
                agora.add(e.getKey());
                double pct = porSegundo(nivel(p));
                if (p.isAlive() && p.getHealth() < p.getMaxHealth()) {
                    boolean emCombate = p.server.getTickCount() - feridoEm.getOrDefault(p.getUUID(), -1000) < 40;
                    p.heal((float) (p.getMaxHealth() * pct / 100.0 * (emCombate ? 0.5 : 1)));
                }
                if (!antes.contains(e.getKey())) {
                    String nome = e.getValue().getKey();
                    String ritmo = String.format(new java.util.Locale("pt", "BR"), "%.1f%%", pct);
                    Sistema.avisar(p, "Conjunto " + nome + " completo", "Sua vida se recupera sozinha: " + ritmo + " por segundo.", 0xFFD166);
                    p.sendSystemMessage(Component.literal("✦ O conjunto " + nome + " desperta: sua vida se recupera sozinha (" + ritmo
                            + " da vida por segundo no seu nível; cresce até o nível 100, e cai pela metade logo depois de levar dano).").withStyle(ChatFormatting.GOLD));
                    p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.4f);
                }
            }
            if (agora.isEmpty()) ativos.remove(p.getUUID()); else ativos.put(p.getUUID(), agora);
        }
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) { ativos.remove(ev.getEntity().getUUID()); feridoEm.remove(ev.getEntity().getUUID()); }
}
