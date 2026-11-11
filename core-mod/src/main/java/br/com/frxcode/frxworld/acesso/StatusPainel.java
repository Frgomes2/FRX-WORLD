package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.eventos.ChefeMundial;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * A cada 10 s manda ao painel um retrato do servidor para a Visão geral: TPS, memória, hora e clima do mundo,
 * jogadores com posição, contagem de criaturas por tipo, reinos e chefe mundial.
 */
public class StatusPainel {
    private int ticks;

    public StatusPainel() {}

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 200 != 0) return;
        try {
            JsonObject o = montar(e.getServer());
            PainelApi.enviarStatus(o);
        } catch (Exception ex) {
            br.com.frxcode.frxworld.FrxWorld.LOG.debug("Status do painel: {}", ex.toString());
        }
    }

    private static JsonObject montar(MinecraftServer s) {
        JsonObject o = new JsonObject();
        double mspt = s.getAverageTickTime();
        o.addProperty("mspt", Math.round(mspt * 10) / 10.0);
        o.addProperty("tps", Math.round(Math.min(20.0, mspt <= 0 ? 20 : 1000.0 / mspt) * 10) / 10.0);
        Runtime rt = Runtime.getRuntime();
        o.addProperty("memUsadaMb", (rt.totalMemory() - rt.freeMemory()) / 1048576);
        o.addProperty("memMaxMb", rt.maxMemory() / 1048576);
        o.addProperty("versaoMod", ModList.get().getModContainerById("frxworld").map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
        o.addProperty("mods", ModList.get().size());
        o.addProperty("maxJogadores", s.getMaxPlayers());

        ServerLevel mundo = s.overworld();
        long dia = mundo.getDayTime();
        o.addProperty("dia", dia / 24000 + 1);
        o.addProperty("hora", dia % 24000);
        o.addProperty("clima", mundo.isThundering() ? "tempestade" : mundo.isRaining() ? "chuva" : "limpo");
        BlockPos spawn = mundo.getSharedSpawnPos();
        JsonArray sp = new JsonArray(); sp.add(spawn.getX()); sp.add(spawn.getZ());
        o.add("spawn", sp);

        // jogadores
        JsonArray jog = new JsonArray();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            JsonObject j = new JsonObject();
            j.addProperty("nick", p.getGameProfile().getName());
            j.addProperty("uuid", p.getUUID().toString());
            j.addProperty("x", p.getBlockX()); j.addProperty("y", p.getBlockY()); j.addProperty("z", p.getBlockZ());
            j.addProperty("dim", p.level().dimension().location().getPath());
            j.addProperty("vida", Math.round(p.getHealth()));
            j.addProperty("vidaMax", Math.round(p.getMaxHealth()));
            j.addProperty("ping", p.latency);
            if (Progressao.temDados(p)) {
                DadosJogador d = Progressao.dados(p);
                j.addProperty("nivel", d.nivel());
                j.addProperty("classe", d.classe());
            }
            for (Reino r : Reinos.todos()) if (r.distancia(p.getBlockX(), p.getBlockZ()) == 0 && p.level() == mundo) { j.addProperty("reino", r.nome()); break; }
            jog.add(j);
        }
        o.add("jogadores", jog);

        // criaturas
        int total = 0, monstros = 0, animais = 0, aldeoes = 0, itens = 0, outros = 0;
        Map<String, Integer> porTipo = new HashMap<>();
        JsonObject porDim = new JsonObject();
        int chunks = 0;
        for (ServerLevel nivel : s.getAllLevels()) {
            int n = 0;
            for (Entity ent : nivel.getAllEntities()) {
                if (ent instanceof Player) continue;
                n++;
                if (ent instanceof ItemEntity) { itens++; continue; }
                MobCategory cat = ent.getType().getCategory();
                if (ent instanceof AbstractVillager || ent.getTags().contains("frx_npc") || ent.getTags().contains("frx_cidadao")) aldeoes++;
                else if (cat == MobCategory.MONSTER) monstros++;
                else if (cat == MobCategory.CREATURE || cat == MobCategory.AMBIENT || cat == MobCategory.WATER_CREATURE
                        || cat == MobCategory.WATER_AMBIENT || cat == MobCategory.UNDERGROUND_WATER_CREATURE || cat == MobCategory.AXOLOTLS) animais++;
                else { outros++; continue; }
                ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(ent.getType());
                if (id != null) porTipo.merge(id.toString(), 1, Integer::sum);
            }
            total += n;
            porDim.addProperty(nivel.dimension().location().getPath(), n);
            chunks += nivel.getChunkSource().getLoadedChunksCount();
        }
        JsonObject ent = new JsonObject();
        ent.addProperty("total", total);
        ent.addProperty("monstros", monstros);
        ent.addProperty("animais", animais);
        ent.addProperty("aldeoes", aldeoes);
        ent.addProperty("itens", itens);
        ent.addProperty("outros", outros);
        ent.add("porDimensao", porDim);
        JsonArray top = new JsonArray();
        porTipo.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12).forEach(en -> {
            JsonObject t = new JsonObject();
            t.addProperty("id", en.getKey());
            t.addProperty("n", en.getValue());
            top.add(t);
        });
        ent.add("porTipo", top);
        o.add("entidades", ent);
        o.addProperty("chunksCarregados", chunks);

        // reinos
        JsonArray reinos = new JsonArray();
        for (Reino r : Reinos.todos()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", r.id());
            j.addProperty("nome", r.nome());
            j.addProperty("x0", r.chunkX0() * 16);
            j.addProperty("z0", r.chunkZ0() * 16);
            j.addProperty("x1", (r.chunkX1() + 1) * 16);
            j.addProperty("z1", (r.chunkZ1() + 1) * 16);
            j.addProperty("subterraneo", r.subterraneo());
            reinos.add(j);
        }
        o.add("reinos", reinos);

        JsonObject chefe = ChefeMundial.statusPainel(s);
        if (chefe != null) o.add("chefe", chefe);
        return o;
    }
}
