package br.com.frxcode.frxworld.protecao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Áreas protegidas da superfície (labirinto, arenas, construções do admin): dentro delas ninguém quebra nem
 * coloca bloco, e explosão não estraga nada. Só admin no criativo mexe. Cada área pode ter um "refúgio":
 * uma caixa menor de onde os monstros são retirados (sala segura).
 * Registro por mundo em frx_protegidas.txt, uma área por linha:
 * "id;minX;minY;minZ;maxX;maxY;maxZ" e, opcional, mais seis números com a caixa do refúgio.
 */
public class AreasProtegidas {
    private record Area(String id, BoundingBox caixa, BoundingBox refugio) {}
    private static volatile List<Area> AREAS = List.of();
    private int ticks;

    private static boolean admin(Player p) { return p.isCreative() && p.hasPermissions(2); }

    /** Túmulo (mod Gravestone): toda proteção do servidor deixa quebrar, para o jogador recuperar os itens de onde morreu. */
    public static boolean tumulo(net.minecraft.world.level.block.state.BlockState estado) {
        net.minecraft.resources.ResourceLocation bloco = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(estado.getBlock());
        return bloco != null && bloco.getNamespace().equals("gravestone");
    }

    public static boolean protegido(Level n, BlockPos p) {
        if (n.dimension() != Level.OVERWORLD) return false;
        for (Area a : AREAS) if (a.caixa().isInside(p)) return true;
        return false;
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        List<Area> novo = new ArrayList<>();
        try {
            Path arq = ev.getServer().getWorldPath(LevelResource.ROOT).resolve("frx_protegidas.txt");
            if (Files.exists(arq))
                for (String l : Files.readAllLines(arq, StandardCharsets.UTF_8)) {
                    String[] p = l.trim().split(";");
                    if (p.length < 7) continue;
                    int[] v = new int[p.length - 1];
                    for (int i = 1; i < p.length; i++) v[i - 1] = Integer.parseInt(p[i].trim());
                    novo.add(new Area(p[0], new BoundingBox(v[0], v[1], v[2], v[3], v[4], v[5]),
                            v.length >= 12 ? new BoundingBox(v[6], v[7], v[8], v[9], v[10], v[11]) : null));
                }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Áreas protegidas: não li frx_protegidas.txt: {}", e.toString());
        }
        AREAS = List.copyOf(novo);
        FrxWorld.LOG.info("Áreas protegidas: {}.", novo.size());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoQuebrar(BlockEvent.BreakEvent ev) {
        if (!(ev.getLevel() instanceof Level n) || n.isClientSide() || !protegido(n, ev.getPos())) return;
        Player p = ev.getPlayer();
        if (p != null && admin(p)) return;
        // túmulo de quem morreu aqui dentro: pode ser quebrado para recuperar os itens (o próprio mod decide quem pode)
        if (tumulo(ev.getState())) return;
        ev.setCanceled(true);
        if (p != null) p.displayClientMessage(Component.literal("Este lugar é protegido: nada aqui pode ser quebrado.").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoColocar(BlockEvent.EntityPlaceEvent ev) {
        if (!(ev.getLevel() instanceof Level n) || n.isClientSide() || !protegido(n, ev.getPos())) return;
        if (ev.getEntity() instanceof Player p && admin(p)) return;
        ev.setCanceled(true);
        if (ev.getEntity() instanceof Player p) p.displayClientMessage(Component.literal("Este lugar é protegido: não dá para colocar blocos aqui.").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public void naExplosao(ExplosionEvent.Detonate ev) {
        Level n = ev.getLevel();
        if (n.isClientSide() || AREAS.isEmpty()) return;
        ev.getAffectedBlocks().removeIf(b -> protegido(n, b));
    }

    // ------------------------------------------------------------------ quem já chegou ao refúgio (Mural dos Primeiros)

    private static com.google.gson.JsonObject chegadas;

    private static Path arquivoChegadas(ServerLevel n) { return n.getServer().getWorldPath(LevelResource.ROOT).resolve("frx_chegadas.json"); }

    /**
     * Jogador dentro do refúgio de uma área: na primeira vez de cada personagem, fica registrado em
     * <mundo>/frx_chegadas.json ({ área: { primeiro: {nome, quando}, todos: { id: {nome, quando} } } }).
     * O primeiro de todos ganha o anúncio no chat e vai para o Mural dos Primeiros do site.
     * Quem está no criativo, de espectador ou é operador não conta (conhece o caminho ou chegou voando).
     */
    private static void chegou(ServerLevel nivel, String area, net.minecraft.server.level.ServerPlayer p) {
        if (!p.isAlive() || p.isCreative() || p.isSpectator() || p.hasPermissions(2)) return;
        if (chegadas == null) {
            chegadas = new com.google.gson.JsonObject();
            try {
                Path arq = arquivoChegadas(nivel);
                if (Files.exists(arq)) chegadas = com.google.gson.JsonParser.parseString(Files.readString(arq, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) { FrxWorld.LOG.warn("Chegadas: não li frx_chegadas.json: {}", e.toString()); }
        }
        if (!chegadas.has(area)) chegadas.add(area, new com.google.gson.JsonObject());
        com.google.gson.JsonObject a = chegadas.getAsJsonObject(area);
        if (!a.has("todos")) a.add("todos", new com.google.gson.JsonObject());
        com.google.gson.JsonObject todos = a.getAsJsonObject("todos");
        String id = br.com.frxcode.frxworld.acesso.Personagens.id(p).toString();
        if (todos.has(id)) return;
        String nome = br.com.frxcode.frxworld.acesso.Personagens.nome(p);
        com.google.gson.JsonObject eu = new com.google.gson.JsonObject();
        eu.addProperty("nome", nome);
        eu.addProperty("quando", System.currentTimeMillis());
        todos.add(id, eu);
        boolean primeiro = !a.has("primeiro");
        if (primeiro) a.add("primeiro", eu.deepCopy());
        try { Files.writeString(arquivoChegadas(nivel), chegadas.toString(), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Chegadas: não gravei frx_chegadas.json: {}", e.toString()); }
        FrxWorld.LOG.info("Chegadas: {} chegou ao refúgio de {}{}.", nome, area, primeiro ? " (primeiro do servidor)" : "");
        if (primeiro) {
            nivel.getServer().getPlayerList().broadcastSystemMessage(Component.literal("✦ " + nome + " foi o primeiro a chegar ao coração do Labirinto do Dragão! ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            nivel.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        } else {
            p.displayClientMessage(Component.literal("Você chegou ao coração do labirinto. Aqui dentro nenhum monstro entra.").withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** A cada segundo tira os monstros que entraram num refúgio. */
    @SubscribeEvent
    public void aoTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++ticks % 20 != 0 || AREAS.isEmpty()) return;
        ServerLevel nivel = ev.getServer().overworld();
        for (Area a : AREAS) {
            BoundingBox r = a.refugio();
            if (r == null || !nivel.hasChunkAt(r.getCenter())) continue;
            AABB caixa = new AABB(r.minX(), r.minY(), r.minZ(), r.maxX() + 1, r.maxY() + 1, r.maxZ() + 1);
            for (Mob m : nivel.getEntitiesOfClass(Mob.class, caixa, x -> x instanceof Enemy && !x.getTags().contains("frx_npc"))) m.discard();
            for (net.minecraft.server.level.ServerPlayer p : nivel.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, caixa)) chegou(nivel, a.id(), p);
        }
    }
}
