package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/**
 * Chefes das construções do Twilight Forest (Naga, Lich, Minoshroom, Hydra, Cavaleiros Fantasmas, Ur-Ghast, Yeti Alfa e Rainha da Neve)
 * renascem 3 dias reais depois de mortos. No mod original, o bloco que invoca o chefe some depois da luta e ele nunca mais volta.
 *  - Na morte, guarda onde ficava o invocador do chefe (o "ponto de casa" que o próprio Twilight Forest dá a ele).
 *  - Passados os 3 dias, o servidor avisa e recoloca o invocador assim que o lugar estiver carregado; o chefe aparece quando
 *    alguém chegar perto, do jeito normal do mod. A construção também é reativada (volta a ter os monstros dela).
 *  - Mesma regra das masmorras dos reinos: cada volta é uma geração MAIS FORTE (até 10), com +25% de vida, +15% de dano e mais armadura.
 * Estado em <mundo>/frx_chefes_crepusculo.json. Admin: /frx crepusculo chefes (lista) e /frx crepusculo renascer (todos os pendentes, agora).
 */
public class ChefesCrepusculo {
    private static final long ESPERA = 3L * 24 * 3_600_000L;
    private static final int GERACAO_MAX = 10;
    private static final String GERACAO = "frx_geracao";
    /** chefe do Twilight Forest -> bloco que o invoca */
    private static final Map<String, String> INVOCADOR = Map.of(
            "twilightforest:naga", "twilightforest:naga_boss_spawner",
            "twilightforest:lich", "twilightforest:lich_boss_spawner",
            "twilightforest:minoshroom", "twilightforest:minoshroom_boss_spawner",
            "twilightforest:hydra", "twilightforest:hydra_boss_spawner",
            "twilightforest:knight_phantom", "twilightforest:knight_phantom_boss_spawner",
            "twilightforest:ur_ghast", "twilightforest:ur_ghast_boss_spawner",
            "twilightforest:alpha_yeti", "twilightforest:alpha_yeti_boss_spawner",
            "twilightforest:snow_queen", "twilightforest:snow_queen_boss_spawner");

    private static JsonObject dados;
    private static int relogio;

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_chefes_crepusculo.json"); }

    private static JsonObject dados(MinecraftServer s) {
        if (dados == null) {
            try { Path p = arquivo(s); dados = Files.exists(p) ? JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject(); }
            catch (Exception e) { FrxWorld.LOG.warn("Chefes do Crepúsculo: não li o arquivo: {}", e.toString()); dados = new JsonObject(); }
        }
        return dados;
    }

    private static void salvar(MinecraftServer s) {
        try { Files.writeString(arquivo(s), new GsonBuilder().setPrettyPrinting().create().toJson(dados(s)), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Chefes do Crepúsculo: não gravei: {}", e.toString()); }
    }

    private static String tipo(Entity e) { return String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())); }

    /** Onde ficava o invocador deste chefe (o Twilight Forest guarda isso no próprio chefe), ou null. */
    private static GlobalPos casa(Entity e) {
        try {
            Object g = e.getClass().getMethod("getRestrictionPoint").invoke(e);
            return g instanceof GlobalPos gp ? gp : null;
        } catch (Exception ignorado) { return null; }
    }

    private static String chave(GlobalPos g) {
        BlockPos p = g.pos();
        return g.dimension().location() + "|" + p.getX() + "," + p.getY() + "," + p.getZ();
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        LivingEntity e = ev.getEntity();
        if (!(e.level() instanceof ServerLevel nv) || !INVOCADOR.containsKey(tipo(e))) return;
        for (String t : e.getTags()) if (t.startsWith("frx_")) return;   // chefes de evento do próprio mod não contam
        try { if (Boolean.TRUE.equals(e.getClass().getMethod("isShadowClone").invoke(e))) return; } catch (Exception ignorado) { }   // cópias do Lich
        GlobalPos g = casa(e);
        if (g == null) return;   // invocado à mão (ovo, comando): não tem casa, não renasce
        BlockPos p = g.pos();
        JsonObject r = new JsonObject();
        r.addProperty("tipo", tipo(e));
        r.addProperty("nome", e.getType().getDescription().getString());
        r.addProperty("dim", g.dimension().location().toString());
        r.addProperty("x", p.getX()); r.addProperty("y", p.getY()); r.addProperty("z", p.getZ());
        int geracao = Math.min(GERACAO_MAX, e.getPersistentData().getInt(GERACAO) + 1);
        r.addProperty("geracao", geracao);
        r.addProperty("morreu", System.currentTimeMillis());
        r.addProperty("estado", "morto");
        dados(nv.getServer()).add(chave(g), r);
        salvar(nv.getServer());
        FrxWorld.LOG.info("Chefe do Crepúsculo {} morreu ({}); renasce em 3 dias, geração {}.", tipo(e), chave(g), geracao);
    }

    /** Chefe que o invocador recolocado acabou de criar: volta com a força da geração dele. */
    @SubscribeEvent
    public void aoAparecer(EntityJoinLevelEvent ev) {
        if (!(ev.getLevel() instanceof ServerLevel nv) || !(ev.getEntity() instanceof Mob m) || ev.loadedFromDisk() || !INVOCADOR.containsKey(tipo(m))) return;
        if (m.getPersistentData().contains(GERACAO)) return;
        GlobalPos g = casa(m);
        if (g == null) return;
        JsonObject r = dados(nv.getServer()).getAsJsonObject(chave(g));
        if (r == null || !"colocado".equals(r.get("estado").getAsString())) return;
        int geracao = r.get("geracao").getAsInt();
        if (geracao > 0) ChefesMasmorra.fortalecer(m, geracao);
    }

    /** Recoloca o invocador no lugar. Devolve false se o lugar ainda não está carregado (tenta de novo depois). */
    private static boolean recolocar(MinecraftServer s, JsonObject r) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(r.get("dim").getAsString()));
        ServerLevel nv = s.getLevel(dim);
        Block bloco = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(INVOCADOR.getOrDefault(r.get("tipo").getAsString(), "minecraft:air")));
        if (nv == null || bloco == null || bloco == net.minecraft.world.level.block.Blocks.AIR) return true;   // mundo ou mod saiu: esquece
        BlockPos p = new BlockPos(r.get("x").getAsInt(), r.get("y").getAsInt(), r.get("z").getAsInt());
        if (!nv.isLoaded(p) || !nv.isPositionEntityTicking(p)) return false;
        BlockState atual = nv.getBlockState(p);
        if (!atual.is(bloco)) {
            if (!atual.isAir() && !atual.canBeReplaced()) nv.destroyBlock(p, true);   // alguém pôs um bloco ali: devolve o item
            nv.setBlock(p, bloco.defaultBlockState(), 3);
        }
        // a construção volta a ficar "ativa" (o mod para de gerar os monstros dela depois que o chefe morre)
        try {
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    "execute in " + r.get("dim").getAsString() + " positioned " + p.getX() + " " + p.getY() + " " + p.getZ() + " run tffeature conquer reactivate");
        } catch (Exception ex) { FrxWorld.LOG.warn("Chefes do Crepúsculo: não reativei a construção: {}", ex.toString()); }
        nv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 60, 1, 1.5, 1, 0.05);
        nv.playSound(null, p, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1f, 0.8f);
        FrxWorld.LOG.info("Chefe do Crepúsculo {}: invocador recolocado em {}.", r.get("tipo").getAsString(), p.toShortString());
        return true;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++relogio % 200 != 0) return;
        MinecraftServer s = ev.getServer();
        JsonObject d = dados(s);
        if (d.size() == 0) return;
        long agora = System.currentTimeMillis();
        boolean mudou = false;
        for (String k : new ArrayList<>(d.keySet())) {
            JsonObject r = d.getAsJsonObject(k);
            if (!"morto".equals(r.get("estado").getAsString()) && !"pronto".equals(r.get("estado").getAsString())) continue;
            if (agora - r.get("morreu").getAsLong() < ESPERA) continue;
            if ("morto".equals(r.get("estado").getAsString())) {
                r.addProperty("estado", "pronto");
                mudou = true;
                int ger = r.get("geracao").getAsInt();
                s.getPlayerList().broadcastSystemMessage(Component.literal("☠ " + r.get("nome").getAsString() + " voltou ao seu covil na Floresta do Crepúsculo"
                        + (ger > 0 ? " MAIS FORTE (✦" + ger + ": " + ChefesMasmorra.forca(ger) + ")" : "") + ". Quem vai encarar?").withStyle(ChatFormatting.DARK_PURPLE), false);
            }
            if (recolocar(s, r)) { r.addProperty("estado", "colocado"); mudou = true; }
        }
        if (mudou) salvar(s);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("crepusculo")
                        .then(Commands.literal("chefes").executes(ctx -> {
                            JsonObject d = dados(ctx.getSource().getServer());
                            long agora = System.currentTimeMillis();
                            int n = 0;
                            for (String k : d.keySet()) {
                                JsonObject r = d.getAsJsonObject(k);
                                String estado = r.get("estado").getAsString();
                                long falta = ESPERA - (agora - r.get("morreu").getAsLong());
                                String quando = "colocado".equals(estado) ? "de volta (✦" + r.get("geracao").getAsInt() + ")"
                                        : falta <= 0 ? "pronto: volta quando o lugar carregar" : "renasce em " + (falta / 86_400_000L) + " d " + (falta / 3_600_000L % 24) + " h";
                                String linha = r.get("nome").getAsString() + " · " + r.get("x").getAsInt() + " " + r.get("y").getAsInt() + " " + r.get("z").getAsInt() + " · " + quando;
                                ctx.getSource().sendSuccess(() -> Component.literal(linha), false);
                                n++;
                            }
                            if (n == 0) ctx.getSource().sendSuccess(() -> Component.literal("Nenhum chefe do Crepúsculo derrotado ainda."), false);
                            return 1;
                        }))
                        .then(Commands.literal("renascer").executes(ctx -> {
                            JsonObject d = dados(ctx.getSource().getServer());
                            int n = 0;
                            for (String k : d.keySet()) {
                                JsonObject r = d.getAsJsonObject(k);
                                if ("morto".equals(r.get("estado").getAsString())) { r.addProperty("morreu", 0L); n++; }
                            }
                            salvar(ctx.getSource().getServer());
                            int total = n;
                            ctx.getSource().sendSuccess(() -> Component.literal(total + " chefe(s) do Crepúsculo liberado(s): voltam assim que o lugar carregar."), true);
                            return 1;
                        }))));
    }
}
