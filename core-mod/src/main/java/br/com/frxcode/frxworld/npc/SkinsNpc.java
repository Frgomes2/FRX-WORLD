package br.com.frxcode.frxworld.npc;

import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.rede.MarcaNpc;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.SkinNpc;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins de NPC feitas no painel (aba NPCs): o servidor busca a cada 30 s e manda aos jogadores.
 * Cada NPC é "marcado" para o cliente com papel e reino quando entra no campo de visão; o cliente escolhe
 * a skin mais específica: reino:papel → papel:papel → skin da raça.
 */
public class SkinsNpc {
    public record Skin(boolean slim, byte[] png) {}

    private static final Map<String, Skin> SKINS = new ConcurrentHashMap<>();
    private static volatile String versao = "";
    private static volatile boolean buscando;
    private int ticks;

    public SkinsNpc() {}

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 600 != 40 || buscando) return;
        buscando = true;
        PainelApi.lerSkinsNpc(versao).whenComplete((o, erro) -> {
            buscando = false;
            if (erro != null || o == null || o.has("igual")) return;
            MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
            if (s != null) s.execute(() -> aplicar(s, o));
        });
    }

    private static void aplicar(MinecraftServer s, JsonObject o) {
        Set<String> vistas = new HashSet<>();
        for (JsonElement el : o.getAsJsonArray("skins")) {
            JsonObject j = el.getAsJsonObject();
            String chave = j.get("chave").getAsString();
            boolean slim = j.has("slim") && j.get("slim").getAsBoolean();
            byte[] png;
            try { png = Base64.getDecoder().decode(j.get("png").getAsString()); } catch (Exception ex) { continue; }
            vistas.add(chave);
            Skin antiga = SKINS.get(chave);
            if (antiga != null && antiga.slim() == slim && java.util.Arrays.equals(antiga.png(), png)) continue;
            SKINS.put(chave, new Skin(slim, png));
            Rede.CANAL.send(PacketDistributor.ALL.noArg(), new SkinNpc(chave, slim, png));
        }
        for (String chave : new HashSet<>(SKINS.keySet())) {
            if (vistas.contains(chave)) continue;
            SKINS.remove(chave);
            Rede.CANAL.send(PacketDistributor.ALL.noArg(), new SkinNpc(chave, false, new byte[0]));
        }
        versao = o.has("versao") ? o.get("versao").getAsString() : "";
        br.com.frxcode.frxworld.FrxWorld.LOG.info("Skins de NPC: {} do painel.", SKINS.size());
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        SKINS.forEach((chave, sk) -> Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new SkinNpc(chave, sk.slim(), sk.png())));
    }

    /** Papel do NPC: os do reino têm frx_papel; moradores e guardas da população também ganham skin própria. */
    public static String[] papelEReino(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof Player) return null;
        var tags = e.getTags();
        if (tags.contains(br.com.frxcode.frxworld.aventura.Criaturas.TAG)) return br.com.frxcode.frxworld.aventura.Criaturas.marca(e);   // chefe ou monstro do Códex com skin própria
        if (tags.contains("frx_npc")) {
            String papel = e.getPersistentData().getString("frx_papel");
            String reino = e.getPersistentData().getString("frx_reino");
            return papel.isEmpty() ? null : new String[]{papel, reino};
        }
        if (tags.contains("frx_craque")) return new String[]{"craque", ""};
        if (tags.contains("frx_ladrao")) return new String[]{"ladrao", ""};
        if (tags.contains("frx_cidadao")) {
            String reino = "";
            for (String t : tags) if (t.startsWith("frx_cidadao_")) reino = t.substring("frx_cidadao_".length());
            String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
            String papel = id.equals("minecraft:villager") ? "morador" : id.equals("guardvillagers:guard") ? "guarda" : "";
            return papel.isEmpty() ? null : new String[]{papel, reino};
        }
        return null;
    }

    @SubscribeEvent
    public void aoVer(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        String[] pr = papelEReino(e.getTarget());
        if (pr == null) return;
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new MarcaNpc(e.getTarget().getId(), pr[0], pr[1]));
    }
}
