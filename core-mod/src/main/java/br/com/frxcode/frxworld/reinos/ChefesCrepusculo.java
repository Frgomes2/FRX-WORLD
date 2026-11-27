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
 * renascem 1 dia (24 h reais) depois de mortos. No mod original, o bloco que invoca o chefe some depois da luta e ele nunca mais volta.
 *  - Na morte, guarda onde ficava o invocador do chefe (o "ponto de casa" que o próprio Twilight Forest dá a ele).
 *  - Passadas as 24 h, o servidor avisa e recoloca o invocador assim que o lugar estiver carregado; o chefe aparece quando
 *    alguém chegar perto, do jeito normal do mod. A construção também é reativada (volta a ter os monstros dela).
 *  - Mesma regra das masmorras dos reinos: cada volta é uma geração MAIS FORTE (até 10), com +25% de vida, +15% de dano e mais armadura.
 * Estado em <mundo>/frx_chefes_crepusculo.json. Admin: /frx crepusculo chefes (lista) e /frx crepusculo renascer (todos os pendentes, agora).
 */
public class ChefesCrepusculo {
    private static final long ESPERA = 24 * 3_600_000L;
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
    private static final long ENFRAQUECE = 3L * 24 * 3_600_000L;
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
        r.addProperty("matador", ChefesMasmorra.matador(ev));
        r.addProperty("duracao", ChefesMasmorra.duracao(e));
        r.addProperty("geracaoMorta", e.getPersistentData().getInt(GERACAO));
        dados(nv.getServer()).add(chave(g), r);
        salvar(nv.getServer());
        FrxWorld.LOG.info("Chefe do Crepúsculo {} morreu ({}); renasce em 24 h, geração {}.", tipo(e), chave(g), geracao);
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
            if ("colocado".equals(r.get("estado").getAsString())) {   // de volta há 3 dias sem ninguém derrotar: cai uma geração
                int g = r.get("geracao").getAsInt();
                long desde = r.has("colocadoEm") ? r.get("colocadoEm").getAsLong() : agora;
                if (!r.has("colocadoEm")) { r.addProperty("colocadoEm", agora); mudou = true; }
                if (g > 0 && agora - desde >= ENFRAQUECE) {
                    r.addProperty("geracao", g - 1); r.addProperty("colocadoEm", agora);
                    mudou = true;
                    ServerLevel nv = s.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(r.get("dim").getAsString())));
                    BlockPos p = new BlockPos(r.get("x").getAsInt(), r.get("y").getAsInt(), r.get("z").getAsInt());
                    net.minecraft.world.entity.EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(r.get("tipo").getAsString()));
                    if (nv != null && et != null && nv.isLoaded(p))
                        for (Entity x : nv.getEntities(et, new net.minecraft.world.phys.AABB(p).inflate(64), Entity::isAlive)) if (x instanceof Mob mx) ChefesMasmorra.reajustar(mx, g - 1);
                }
                continue;
            }
            if (!"morto".equals(r.get("estado").getAsString()) && !"pronto".equals(r.get("estado").getAsString())) continue;
            long falta = ESPERA - (agora - r.get("morreu").getAsLong());
            if (falta > 0 && falta <= 10 * 60_000L && !(r.has("avisou10") && r.get("avisou10").getAsBoolean())) {
                r.addProperty("avisou10", true);
                mudou = true;
                s.getPlayerList().broadcastSystemMessage(Component.literal("⏳ " + r.get("nome").getAsString() + " volta ao covil na Floresta do Crepúsculo em 10 minutos. Reúnam o grupo!")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
            if (agora - r.get("morreu").getAsLong() < ESPERA) continue;
            if ("morto".equals(r.get("estado").getAsString())) {
                r.addProperty("estado", "pronto");
                mudou = true;
                int ger = r.get("geracao").getAsInt();
                s.getPlayerList().broadcastSystemMessage(Component.literal("☠ " + r.get("nome").getAsString() + " voltou ao seu covil na Floresta do Crepúsculo"
                        + (ger > 0 ? " MAIS FORTE (✦" + ger + ": " + ChefesMasmorra.forca(ger) + ")" : "") + ". Quem vai encarar?").withStyle(ChatFormatting.DARK_PURPLE), false);
                br.com.frxcode.frxworld.acesso.Discord.avisar("☠️ **" + r.get("nome").getAsString() + " voltou** ao covil na Floresta do Crepúsculo" + (ger > 0 ? " mais forte (✦" + ger + ")" : "") + ".");
            }
            if (recolocar(s, r)) { r.addProperty("estado", "colocado"); r.addProperty("colocadoEm", agora); mudou = true; }
        }
        if (mudou) salvar(s);
    }

    /** Linhas do /chefes para a Floresta do Crepúsculo. */
    static java.util.List<Component> situacao(MinecraftServer s) {
        java.util.List<Component> linhas = new ArrayList<>();
        long agora = System.currentTimeMillis();
        for (String k : dados(s).keySet()) {
            JsonObject r = dados(s).getAsJsonObject(k);
            int g = r.get("geracao").getAsInt();
            boolean morto = !"colocado".equals(r.get("estado").getAsString());
            long falta = ESPERA - (agora - r.get("morreu").getAsLong());
            Component estado = morto ? Component.literal(falta > 0 ? "volta em " + (falta / 3_600_000L) + "h " + (falta / 60_000L % 60) + "min" : "voltando agora").withStyle(ChatFormatting.YELLOW)
                    : Component.literal("vivo").withStyle(ChatFormatting.GREEN);
            linhas.add(Component.literal((morto ? "  ☠ " : "  ⚔ ") + r.get("nome").getAsString() + " · ").withStyle(morto ? ChatFormatting.GRAY : ChatFormatting.RED)
                    .append(estado).append(Component.literal(g > 0 ? " (✦" + g + ")" : "").withStyle(ChatFormatting.GOLD)));
        }
        return linhas;
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
                        }))
                        // chefe morto antes desta regra existir: o admin fica onde era o invocador (o centro da sala do chefe) e registra
                        .then(Commands.literal("registrar").then(Commands.argument("chefe", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b2) -> net.minecraft.commands.SharedSuggestionProvider.suggest(INVOCADOR.keySet().stream().map(k -> k.substring(k.indexOf(':') + 1)), b2))
                                .executes(ctx -> {
                                    net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String tipo = "twilightforest:" + com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "chefe");
                                    if (!INVOCADOR.containsKey(tipo)) { ctx.getSource().sendFailure(Component.literal("Chefe desconhecido. Use: " + String.join(", ", INVOCADOR.keySet()))); return 0; }
                                    if (!p.level().dimension().location().toString().equals("twilightforest:twilight_forest")) { ctx.getSource().sendFailure(Component.literal("Fique na Floresta do Crepúsculo, no lugar onde o chefe nascia.")); return 0; }
                                    GlobalPos g = GlobalPos.of(p.level().dimension(), p.blockPosition());
                                    JsonObject r = new JsonObject();
                                    r.addProperty("tipo", tipo);
                                    net.minecraft.world.entity.EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(tipo));
                                    r.addProperty("nome", et == null ? tipo : et.getDescription().getString());
                                    r.addProperty("dim", g.dimension().location().toString());
                                    r.addProperty("x", p.getBlockX()); r.addProperty("y", p.getBlockY()); r.addProperty("z", p.getBlockZ());
                                    r.addProperty("geracao", 1);
                                    r.addProperty("morreu", 0L);
                                    r.addProperty("estado", "morto");
                                    dados(ctx.getSource().getServer()).add(chave(g), r);
                                    salvar(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Registrado: " + r.get("nome").getAsString() + " volta aqui (" + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ() + ") em instantes, já na geração ✦1."), true);
                                    return 1;
                                })))));
    }
}
