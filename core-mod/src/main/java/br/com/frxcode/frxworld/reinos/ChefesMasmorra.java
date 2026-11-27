package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.Zonas;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chefes das masmorras fixas (as que ficam embaixo dos reinos e do coliseu) renascem 7 dias reais depois de mortos.
 *  - Vale para chefe (marca forge:bosses, mais o Ferrous Wroughtnaut e o Lich) que morre a até 110 blocos de uma masmorra do frx_masmorras.txt.
 *  - Guarda o tipo, o nível e o lugar onde ele estava quando apareceu pela primeira vez (ou onde morreu, se não se sabe).
 *  - Passados os 7 dias, o servidor avisa e o chefe volta assim que alguém chegar perto (o pedaço do mundo precisa estar carregado).
 *  - Um chefe de cada tipo por masmorra: se já houver um vivo ali (invocado por jogador), não nasce outro.
 *  - Enquanto o chefe está morto, quem está dentro da masmorra vê uma barra no alto da tela com o nome dele e o contador até a volta.
 *  - Cada vez que renasce, volta MAIS FORTE (geração, até 10): +25% de vida, +15% de dano, +2 de armadura e +5% de resistência a
 *    empurrão por geração, e o nome ganha a marca da geração (ex.: "Ignis ✦3"). A geração passa de uma morte para a outra.
 * Estado em <mundo>/frx_chefes_masmorra.json. Admin: /frx masmorra chefes (lista) e /frx masmorra renascer (todos os pendentes, agora).
 */
public class ChefesMasmorra {
    private static final long SEMANA = 7L * 24 * 3_600_000L;
    private static final int RAIO = 110;
    private static final String ORIGEM = "frx_origem", GERACAO = "frx_geracao";
    private static final int GERACAO_MAX = 10;
    private static final java.util.UUID MOD_VIDA = java.util.UUID.nameUUIDFromBytes("frxworld:chefe_masmorra:vida".getBytes(StandardCharsets.UTF_8)),
            MOD_DANO = java.util.UUID.nameUUIDFromBytes("frxworld:chefe_masmorra:dano".getBytes(StandardCharsets.UTF_8)),
            MOD_ARMADURA = java.util.UUID.nameUUIDFromBytes("frxworld:chefe_masmorra:armadura".getBytes(StandardCharsets.UTF_8)),
            MOD_FIRME = java.util.UUID.nameUUIDFromBytes("frxworld:chefe_masmorra:firme".getBytes(StandardCharsets.UTF_8));
    private static final Set<String> EXTRAS = Set.of("mowziesmobs:ferrous_wroughtnaut", "graveyard:lich");

    /** Chefe de cada estrutura (para as masmorras novas, colocadas embaixo de reino que não tinha nenhuma). */
    private static final Map<String, String> POR_ESTRUTURA = Map.of(
            "cataclysm:ruined_citadel", "cataclysm:ender_guardian", "cataclysm:soul_black_smith", "cataclysm:netherite_monstrosity",
            "cataclysm:ancient_factory", "cataclysm:the_harbinger", "cataclysm:cursed_pyramid", "cataclysm:ancient_remnant",
            "cataclysm:acropolis", "cataclysm:scylla", "cataclysm:burning_arena", "cataclysm:ignis", "cataclysm:frosted_prison", "cataclysm:maledictus");
    /** Chefe das masmorras cuja estrutura não traz nenhum. */
    private static final Map<String, String> POR_MASMORRA = Map.of("leste-mosteiro", "cataclysm:ancient_remnant", "sakura-gorgona", "graveyard:lich");
    private static final String INICIAIS = "_iniciais";

    private static JsonObject dados;
    private static int relogio;

    private static String chefeInicial(String id, String estrutura) {
        if (POR_MASMORRA.containsKey(id)) return POR_MASMORRA.get(id);
        return id.startsWith("oeste-") || id.startsWith("demonios-") ? POR_ESTRUTURA.get(estrutura) : null;
    }

    /** Esta masmorra ainda precisa receber o primeiro chefe? */
    static boolean precisaInicial(MinecraftServer s, String id, String estrutura) {
        JsonObject d = dados(s);
        return chefeInicial(id, estrutura) != null && !(d.has(INICIAIS) && d.getAsJsonObject(INICIAIS).has(id));
    }

    /** Marca o primeiro chefe para nascer no salão (uma vez só por masmorra). Devolve um texto para o log. */
    static String garantirInicial(MinecraftServer s, String id, String estrutura, BlockPos salao) {
        String tipo = chefeInicial(id, estrutura);
        if (tipo == null) return "sem chefe previsto";
        JsonObject d = dados(s);
        if (!d.has(INICIAIS)) d.add(INICIAIS, new JsonObject());
        d.getAsJsonObject(INICIAIS).addProperty(id, tipo);
        ResourceLocation rl = new ResourceLocation(tipo);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) { salvar(s); return "chefe " + tipo + " não existe (mod fora?)"; }
        JsonObject reg = new JsonObject();
        reg.addProperty("masmorra", id);
        reg.addProperty("tipo", tipo);
        reg.addProperty("nome", ForgeRegistries.ENTITY_TYPES.getValue(rl).getDescription().getString());
        reg.addProperty("x", salao.getX()); reg.addProperty("y", salao.getY()); reg.addProperty("z", salao.getZ());
        reg.addProperty("nivel", 0);
        reg.addProperty("morreu", 0L);
        reg.addProperty("avisou", true);
        d.add(id + "|" + tipo, reg);
        salvar(s);
        return "chefe " + tipo + " marcado em " + salao.getX() + ", " + salao.getY() + ", " + salao.getZ();
    }

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_chefes_masmorra.json"); }

    private static JsonObject dados(MinecraftServer s) {
        if (dados != null) return dados;
        try {
            Path a = arquivo(s);
            dados = Files.exists(a) ? JsonParser.parseString(Files.readString(a, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        } catch (Exception e) { dados = new JsonObject(); }
        return dados;
    }

    private static void salvar(MinecraftServer s) {
        try { Files.writeString(arquivo(s), new GsonBuilder().setPrettyPrinting().create().toJson(dados(s)), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Chefes de masmorra: não gravei: {}", e.toString()); }
    }

    private static boolean ehChefe(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id == null || id.getNamespace().equals(FrxWorld.MOD_ID)) return false;
        for (String t : e.getTags()) if (t.startsWith("frx_")) return false;   // chefes de evento do próprio mod não contam
        return e.getType().is(Tags.EntityTypes.BOSSES) || EXTRAS.contains(id.toString());
    }

    private static Map<String, String> lidasCache;
    private static long lidasAte;

    private static Map<String, String> lidas(ServerLevel nv) {
        long agora = System.currentTimeMillis();
        if (lidasCache == null || agora > lidasAte) { lidasCache = Masmorras.lidas(nv); lidasAte = agora + 30_000; }
        return lidasCache;
    }

    /** Id da masmorra fixa em volta deste ponto do mundo normal, ou null. */
    private static String masmorra(ServerLevel nv, double x, double z) {
        if (nv.dimension() != Level.OVERWORLD) return null;
        for (Map.Entry<String, String> m : lidas(nv).entrySet()) {
            String[] c = m.getValue().split(";");
            if (c.length < 4) continue;
            try {
                if (Math.abs(x - Integer.parseInt(c[1])) <= RAIO && Math.abs(z - Integer.parseInt(c[3])) <= RAIO) return m.getKey();
            } catch (NumberFormatException ignorado) { }
        }
        return null;
    }

    /** Anota onde o chefe estava quando apareceu: é ali que ele renasce. */
    @SubscribeEvent
    public void aoAparecer(EntityJoinLevelEvent ev) {
        if (!(ev.getLevel() instanceof ServerLevel nv) || !(ev.getEntity() instanceof LivingEntity e) || e.getPersistentData().contains(ORIGEM) || !ehChefe(e)) return;
        if (masmorra(nv, e.getX(), e.getZ()) == null) return;
        e.getPersistentData().putIntArray(ORIGEM, new int[]{e.getBlockX(), e.getBlockY(), e.getBlockZ()});
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        LivingEntity e = ev.getEntity();
        if (!(e.level() instanceof ServerLevel nv) || !ehChefe(e)) return;
        String id = masmorra(nv, e.getX(), e.getZ());
        if (id == null) return;
        String tipo = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
        int[] o = e.getPersistentData().contains(ORIGEM) ? e.getPersistentData().getIntArray(ORIGEM) : new int[]{e.getBlockX(), e.getBlockY(), e.getBlockZ()};
        JsonObject r = new JsonObject();
        r.addProperty("masmorra", id);
        r.addProperty("tipo", tipo);
        r.addProperty("nome", e.getType().getDescription().getString());
        r.addProperty("x", o[0]); r.addProperty("y", o[1]); r.addProperty("z", o[2]);
        r.addProperty("nivel", Zonas.nivelDo(e));
        int geracao = Math.min(GERACAO_MAX, e.getPersistentData().getInt(GERACAO) + 1);
        r.addProperty("geracao", geracao);
        r.addProperty("morreu", System.currentTimeMillis());
        r.addProperty("avisou", false);
        dados(nv.getServer()).add(id + "|" + tipo, r);
        salvar(nv.getServer());
        FrxWorld.LOG.info("Chefe de masmorra {} morreu em {}; renasce em 7 dias, geração {}.", tipo, id, geracao);
    }

    private static boolean renascer(ServerLevel nv, JsonObject r) {
        BlockPos p = new BlockPos(r.get("x").getAsInt(), r.get("y").getAsInt(), r.get("z").getAsInt());
        if (!nv.isLoaded(p) || !nv.isPositionEntityTicking(p)) return false;
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(r.get("tipo").getAsString()));
        if (tipo == null || !ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(r.get("tipo").getAsString()))) return true;   // mod saiu: esquece
        if (!nv.getEntities(tipo, new AABB(p).inflate(RAIO), Entity::isAlive).isEmpty()) return true;   // já tem um vivo ali
        Entity e = tipo.create(nv);
        if (!(e instanceof Mob m)) return true;
        m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, nv.random.nextFloat() * 360, 0);
        m.getPersistentData().putIntArray(ORIGEM, new int[]{p.getX(), p.getY(), p.getZ()});
        try { m.finalizeSpawn(nv, nv.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null, null); } catch (Exception ex) { FrxWorld.LOG.warn("Chefe de masmorra: {}", ex.toString()); }
        m.setPersistenceRequired();
        int nivel = r.get("nivel").getAsInt();
        if (nivel > 0) Zonas.aplicarNivel(m, nivel);
        int geracao = geracaoDe(r);
        if (geracao > 0) fortalecer(m, geracao);
        nv.addFreshEntity(m);
        nv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 80, 1, 1.5, 1, 0.05);
        nv.playSound(null, p, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1f, 0.8f);
        FrxWorld.LOG.info("Chefe de masmorra {} renasceu em {}.", r.get("tipo").getAsString(), r.get("masmorra").getAsString());
        return true;
    }

    /** Chefe que renasce volta mais forte a cada geração. */
    static void fortalecer(Mob m, int geracao) {
        m.getPersistentData().putInt(GERACAO, geracao);
        modificar(m, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, MOD_VIDA, 0.25 * geracao, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
        modificar(m, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, MOD_DANO, 0.15 * geracao, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
        modificar(m, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, MOD_ARMADURA, 2.0 * geracao, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        modificar(m, net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, MOD_FIRME, 0.05 * geracao, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        m.setHealth(m.getMaxHealth());
        Component nome = m.hasCustomName() ? m.getCustomName() : m.getType().getDescription();
        m.setCustomName(Component.empty().append(nome.copy().withStyle(ChatFormatting.DARK_RED)).append(Component.literal(" ✦" + geracao).withStyle(ChatFormatting.GOLD)));
    }

    private static void modificar(Mob m, net.minecraft.world.entity.ai.attributes.Attribute atributo, java.util.UUID id, double valor,
                                  net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance a = m.getAttribute(atributo);
        if (a == null) return;
        a.removeModifier(id);
        a.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "FRX chefe renascido", valor, op));
    }

    /** Geração com que o chefe volta. Registro antigo (de antes das gerações) de chefe que já morreu conta como a 1ª. */
    private static int geracaoDe(JsonObject r) {
        if (r.has("geracao")) return r.get("geracao").getAsInt();
        return r.has("morreu") && r.get("morreu").getAsLong() > 0 ? 1 : 0;
    }

    /** Texto do quanto a próxima volta será mais forte. */
    static String forca(int geracao) {
        return "+" + (25 * geracao) + "% de vida e +" + (15 * geracao) + "% de dano";
    }

    // ------------------------------------------------------------------ contador na tela de quem está na masmorra

    private static final Map<String, net.minecraft.server.level.ServerBossEvent> BARRAS = new java.util.HashMap<>();

    private static String falta(long ms) {
        long seg = Math.max(0, ms / 1000);
        long d = seg / 86_400, h = seg / 3600 % 24, m = seg / 60 % 60, sg = seg % 60;
        return (d > 0 ? d + "d " : "") + String.format("%02dh %02dmin %02ds", h, m, sg);
    }

    /** Chefe morto: quem está dentro da masmorra vê, no alto da tela, o nome dele e quanto falta para voltar. */
    private static void barras(MinecraftServer s) {
        JsonObject d = dados(s);
        ServerLevel nv = s.overworld();
        long agora = System.currentTimeMillis();
        java.util.Set<String> vivos = new java.util.HashSet<>();
        for (String k : d.keySet()) {
            if (k.startsWith("_")) continue;
            JsonObject r = d.getAsJsonObject(k);
            long resta = SEMANA - (agora - r.get("morreu").getAsLong());
            if (resta <= 0) continue;
            vivos.add(k);
            net.minecraft.server.level.ServerBossEvent b = BARRAS.computeIfAbsent(k, x -> new net.minecraft.server.level.ServerBossEvent(Component.empty(),
                    net.minecraft.world.BossEvent.BossBarColor.PURPLE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS));
            int ger = geracaoDe(r);
            b.setName(Component.literal("☠ " + r.get("nome").getAsString() + " foi derrotado · volta em " + falta(resta) + (ger > 0 ? " · mais forte (✦" + ger + ")" : "")).withStyle(ChatFormatting.LIGHT_PURPLE));
            b.setProgress(Math.max(0f, Math.min(1f, 1f - resta / (float) SEMANA)));
            String id = r.get("masmorra").getAsString();
            int rx = r.get("x").getAsInt(), ry = r.get("y").getAsInt(), rz = r.get("z").getAsInt();
            for (ServerPlayer p : new ArrayList<>(b.getPlayers())) if (!naMasmorra(nv, p, id, rx, ry, rz)) b.removePlayer(p);
            for (ServerPlayer p : nv.players()) if (naMasmorra(nv, p, id, rx, ry, rz)) b.addPlayer(p);
        }
        for (String k : new ArrayList<>(BARRAS.keySet())) if (!vivos.contains(k)) BARRAS.remove(k).removeAllPlayers();
    }

    private static boolean naMasmorra(ServerLevel nv, ServerPlayer p, String id, int rx, int ry, int rz) {
        if (p.level() != nv || !p.isAlive() || Math.abs(p.getY() - ry) > 45) return false;
        return (Math.abs(p.getX() - rx) <= 90 && Math.abs(p.getZ() - rz) <= 90) || id.equals(masmorra(nv, p.getX(), p.getZ()));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        relogio++;
        if (relogio % 20 == 0) barras(ev.getServer());
        if (relogio % 200 != 0) return;
        MinecraftServer s = ev.getServer();
        JsonObject d = dados(s);
        if (d.size() == 0) return;
        long agora = System.currentTimeMillis();
        boolean mudou = false;
        for (String k : new ArrayList<>(d.keySet())) {
            if (k.startsWith("_")) continue;
            JsonObject r = d.getAsJsonObject(k);
            if (agora - r.get("morreu").getAsLong() < SEMANA) continue;
            if (!r.get("avisou").getAsBoolean()) {
                r.addProperty("avisou", true);
                mudou = true;
                int ger = geracaoDe(r);
                s.getPlayerList().broadcastSystemMessage(Component.literal("☠ " + r.get("nome").getAsString() + " voltou à masmorra " + r.get("masmorra").getAsString()
                        + (ger > 0 ? " MAIS FORTE (✦" + ger + ": " + forca(ger) + ")" : "") + ". Quem vai encarar?").withStyle(ChatFormatting.DARK_PURPLE), false);
            }
            if (renascer(s.overworld(), r)) { d.remove(k); mudou = true; }
        }
        if (mudou) salvar(s);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("masmorra")
                        .then(Commands.literal("chefes").executes(ctx -> {
                            JsonObject d = dados(ctx.getSource().getServer());
                            if (d.size() == 0) ctx.getSource().sendSuccess(() -> Component.literal("Nenhum chefe de masmorra esperando para renascer."), false);
                            long agora = System.currentTimeMillis();
                            for (String k : d.keySet()) {
                                if (k.startsWith("_")) continue;
                                JsonObject r = d.getAsJsonObject(k);
                                long falta = SEMANA - (agora - r.get("morreu").getAsLong());
                                String quando = falta <= 0 ? "pronto: nasce quando alguém chegar perto" : "renasce em " + (falta / 86_400_000L) + " d " + (falta / 3_600_000L % 24) + " h";
                                ctx.getSource().sendSuccess(() -> Component.literal(r.get("nome").getAsString() + " · " + r.get("masmorra").getAsString() + " · " + quando), false);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("renascer").executes(ctx -> {
                            JsonObject d = dados(ctx.getSource().getServer());
                            for (String k : d.keySet()) if (!k.startsWith("_")) d.getAsJsonObject(k).addProperty("morreu", 0L);
                            salvar(ctx.getSource().getServer());
                            int n = d.size();
                            ctx.getSource().sendSuccess(() -> Component.literal(n + " chefe(s) liberado(s): nascem quando alguém chegar perto da masmorra."), true);
                            return 1;
                        }))
                        .then(Commands.literal("chefe").then(Commands.argument("tipo", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(
                                        ForgeRegistries.ENTITY_TYPES.getValues().stream().filter(t -> t.is(Tags.EntityTypes.BOSSES) || EXTRAS.contains(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(t))))
                                                .map(ForgeRegistries.ENTITY_TYPES::getKey), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ResourceLocation tipo = net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "tipo");
                                    String id = masmorra(p.serverLevel(), p.getX(), p.getZ());
                                    if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(tipo)) {
                                        ctx.getSource().sendFailure(Component.literal("Fique dentro de uma masmorra fixa e escolha um tipo de chefe que exista."));
                                        return 0;
                                    }
                                    JsonObject r = new JsonObject();
                                    r.addProperty("masmorra", id);
                                    r.addProperty("tipo", tipo.toString());
                                    r.addProperty("nome", ForgeRegistries.ENTITY_TYPES.getValue(tipo).getDescription().getString());
                                    r.addProperty("x", p.getBlockX()); r.addProperty("y", p.getBlockY()); r.addProperty("z", p.getBlockZ());
                                    r.addProperty("nivel", 0);
                                    r.addProperty("morreu", 0L);
                                    r.addProperty("avisou", true);
                                    dados(ctx.getSource().getServer()).add(id + "|" + tipo, r);
                                    salvar(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Chefe " + tipo + " marcado para nascer aqui (" + id + ") em instantes."), true);
                                    return 1;
                                })))));
    }
}
