package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invasões às capitais: de tempos em tempos, quando há jogadores numa capital, hordas atacam em ondas.
 * Quem defende ganha moedas e reputação com aquele reino. Config na aba Progressão → "Invasões".
 */
public class Invasoes {
    public static final String TAG = "frx_invasor";
    private static final List<String> ONDA1 = List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:husk");
    private static final List<String> ONDA2 = List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:vindicator", "minecraft:pillager", "minecraft:witch");
    private static final List<String> ONDA3 = List.of("minecraft:vindicator", "minecraft:pillager", "minecraft:evoker", "minecraft:wither_skeleton");

    private enum Fase { NADA, PREPARO, ONDA, INTERVALO }

    private static Fase fase = Fase.NADA;
    private static Reino reino;
    private static BlockPos centro;
    private static int onda, totalOnda;
    private static long proximoEm, inicio, ultimaChance = System.currentTimeMillis();
    private static final Set<UUID> vivos = new HashSet<>();
    private static final Map<UUID, Integer> abates = new HashMap<>();
    private static final Set<UUID> participantes = new HashSet<>();
    private static final Map<String, Long> ultimaPorReino = new HashMap<>();
    private static ServerBossEvent barra;
    private static int ticks;

    // ------------------------------------------------------------------ config

    private static JsonObject cfg() { return ConfigJogo.secaoCrua("invasoes"); }

    private static double num(String k, double padrao) {
        JsonObject o = cfg();
        try { return o != null && o.has(k) ? o.get(k).getAsDouble() : padrao; } catch (Exception e) { return padrao; }
    }

    private static boolean ativo() {
        JsonObject o = cfg();
        return o == null || !o.has("ativo") || o.get("ativo").getAsBoolean();
    }

    private static List<String> lista(String k, List<String> padrao) {
        JsonObject o = cfg();
        if (o == null || !o.has(k) || !o.get(k).isJsonArray()) return padrao;
        List<String> l = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray(k)) if (ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.tryParse(e.getAsString().trim()))) l.add(e.getAsString().trim());
        return l.isEmpty() ? padrao : l;
    }

    private static String chefe() {
        JsonObject o = cfg();
        String c = o != null && o.has("chefe") ? o.get("chefe").getAsString().trim() : "minecraft:ravager";
        return ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.tryParse(c)) ? c : "minecraft:ravager";
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        MinecraftServer s = e.getServer();
        long agora = System.currentTimeMillis();
        switch (fase) {
            case NADA -> {
                if (!ativo() || agora - ultimaChance < num("intervaloMinutos", 60) * 60000) return;
                ultimaChance = agora;
                if (ThreadLocalRandom.current().nextDouble(100) >= num("chance", 25)) return;
                List<ServerPlayer> candidatos = new ArrayList<>();
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    if (p.level().dimension() != Level.OVERWORLD || p.isSpectator() || p.isCreative()) continue;
                    for (Reino r : Reinos.todos()) {
                        if (r.distancia(p.getBlockX(), p.getBlockZ()) == 0 && agora - ultimaPorReino.getOrDefault(r.id(), 0L) > 3 * 3_600_000L) candidatos.add(p);   // a mesma capital só de 3 em 3 horas
                    }
                }
                if (candidatos.isEmpty()) return;
                ServerPlayer alvo = candidatos.get(ThreadLocalRandom.current().nextInt(candidatos.size()));
                for (Reino r : Reinos.todos()) if (r.distancia(alvo.getBlockX(), alvo.getBlockZ()) == 0) { comecar(s, r, alvo.blockPosition()); break; }
            }
            case PREPARO, INTERVALO -> { if (agora >= proximoEm) proximaOnda(s); }
            case ONDA -> acompanhar(s, agora);
        }
    }

    /** Há uma invasão em andamento nesta capital? (a limpeza da cidade espera ela acabar) */
    static boolean emAndamento(Reino r) { return fase != Fase.NADA && reino != null && reino.id().equals(r.id()); }

    static boolean comecar(MinecraftServer s, Reino r, BlockPos onde) {
        if (fase != Fase.NADA) return false;
        reino = r;
        centro = onde;
        onda = 0;
        abates.clear();
        participantes.clear();
        vivos.clear();
        inicio = System.currentTimeMillis();
        proximoEm = inicio + 30_000;
        ultimaPorReino.put(r.id(), inicio);
        fase = Fase.PREPARO;
        barra = new ServerBossEvent(Component.literal("⚔ Invasão a " + r.nome() + " — prepare-se!"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (perto(p, 300)) {
                barra.addPlayer(p);
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("⚔ INVASÃO ⚔").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Monstros atacam " + r.nome() + " em 30 s!").withStyle(ChatFormatting.GOLD)));
                p.level().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 1f);
            }
        }
        s.getPlayerList().broadcastSystemMessage(Component.literal("⚔ " + r.nome() + " está sob ataque perto de " + centro.getX() + ", " + centro.getZ()
                + "! Defensores ganham moedas e reputação.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        return true;
    }

    private static boolean perto(Player p, double raio) {
        return centro != null && p.level().dimension() == Level.OVERWORLD && p.blockPosition().distSqr(centro) <= raio * raio;
    }

    private static int ondas() { return (int) Math.max(1, Math.min(10, num("ondas", 3))); }

    private static void proximaOnda(MinecraftServer s) {
        onda++;
        ServerLevel nivel = s.overworld();
        int jogadores = 0;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (perto(p, 100)) { jogadores++; participantes.add(p.getUUID()); }
        jogadores = Math.max(1, jogadores);
        int qtd = (int) Math.round((num("monstrosBase", 6) + num("monstrosPorJogador", 3) * jogadores) * (1 + 0.5 * (onda - 1)));
        qtd = Math.min(qtd, 60);
        List<String> tipos = onda == 1 ? lista("monstros1", ONDA1) : onda == 2 ? lista("monstros2", ONDA2) : lista("monstros3", ONDA3);
        vivos.clear();
        for (int i = 0; i < qtd; i++) gerar(nivel, tipos.get(ThreadLocalRandom.current().nextInt(tipos.size())), false);
        if (onda == ondas()) gerar(nivel, chefe(), true);
        totalOnda = Math.max(1, vivos.size());
        fase = Fase.ONDA;
        String titulo = "⚔ Invasão a " + reino.nome() + " — Onda " + onda + "/" + ondas() + (onda == ondas() ? " (comandante!)" : "");
        if (barra != null) { barra.setName(Component.literal(titulo)); barra.setColor(BossEvent.BossBarColor.RED); barra.setProgress(1f); }
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!perto(p, 300)) continue;
            if (barra != null) barra.addPlayer(p);
            p.displayClientMessage(Component.literal(titulo + ": " + totalOnda + " inimigos").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
        nivel.playSound(null, centro, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 0.8f);
    }

    /** Ponto de chão a 28–45 blocos do centro, perto da altura do centro (serve para capital subterrânea). */
    private static BlockPos lugar(ServerLevel nivel) {
        for (int t = 0; t < 20; t++) {
            double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2), d = 28 + ThreadLocalRandom.current().nextDouble(17);
            int x = centro.getX() + (int) (Math.cos(ang) * d), z = centro.getZ() + (int) (Math.sin(ang) * d);
            nivel.getChunk(x >> 4, z >> 4);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dy = 12; dy >= -12; dy--) {
                int y = centro.getY() + dy;
                BlockState chao = nivel.getBlockState(m.set(x, y - 1, z));
                if (!chao.isFaceSturdy(nivel, m, Direction.UP) || !chao.getFluidState().isEmpty()) continue;
                if (nivel.getBlockState(m.set(x, y, z)).isAir() && nivel.getBlockState(m.set(x, y + 1, z)).isAir()) return new BlockPos(x, y, z);
            }
        }
        return centro;
    }

    private static void gerar(ServerLevel nivel, String id, boolean comandante) {
        EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
        Entity ent = et == null ? null : et.create(nivel);
        if (!(ent instanceof Mob m)) return;
        BlockPos b = lugar(nivel);
        m.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0);
        m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(b), MobSpawnType.EVENT, null, null);
        if (m.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));   // não queimar no sol
            m.setDropChance(EquipmentSlot.HEAD, 0f);
        }
        m.setPersistenceRequired();
        m.setGlowingTag(true);
        m.addTag(TAG);
        if (comandante) {
            AttributeInstance vida = m.getAttribute(Attributes.MAX_HEALTH);
            if (vida != null) vida.addPermanentModifier(new AttributeModifier(UUID.randomUUID(), "FRX comandante", 2.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
            m.setHealth(m.getMaxHealth());
            m.setCustomName(Component.literal("Comandante da Horda").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            m.setCustomNameVisible(true);
        }
        nivel.addFreshEntity(m);
        vivos.add(m.getUUID());
    }

    private static void acompanhar(MinecraftServer s, long agora) {
        ServerLevel nivel = s.overworld();
        vivos.removeIf(u -> { Entity x = nivel.getEntity(u); return x == null || !x.isAlive(); });
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (perto(p, 80)) participantes.add(p.getUUID());
            if (barra != null && perto(p, 300) && !barra.getPlayers().contains(p)) barra.addPlayer(p);
        }
        if (barra != null) barra.setProgress(Math.max(0f, vivos.size() / (float) totalOnda));
        // monstros sem alvo marcham para o centro
        if (ticks % 100 == 0) {
            for (UUID u : vivos) {
                if (!(nivel.getEntity(u) instanceof Mob m)) continue;
                if (m.getTarget() == null || !m.getTarget().isAlive()) {
                    Player perto = nivel.getNearestPlayer(m, 32);
                    if (perto != null && !perto.isCreative() && !perto.isSpectator()) m.setTarget(perto);
                    else m.getNavigation().moveTo(centro.getX() + 0.5, centro.getY(), centro.getZ() + 0.5, 1.0);
                }
            }
        }
        if (agora - inicio > 15 * 60_000) { fim(s, false); return; }
        if (vivos.isEmpty()) {
            if (onda >= ondas()) fim(s, true);
            else {
                fase = Fase.INTERVALO;
                proximoEm = agora + 15_000;
                for (ServerPlayer p : s.getPlayerList().getPlayers())
                    if (perto(p, 300)) p.displayClientMessage(Component.literal("Onda " + onda + " derrotada! A próxima chega em 15 s.").withStyle(ChatFormatting.GOLD), true);
                if (barra != null) { barra.setName(Component.literal("⚔ Invasão a " + reino.nome() + " — próxima onda em 15 s")); barra.setColor(BossEvent.BossBarColor.YELLOW); }
            }
        }
    }

    private static void fim(MinecraftServer s, boolean vitoria) {
        ServerLevel nivel = s.overworld();
        for (UUID u : vivos) { Entity x = nivel.getEntity(u); if (x != null) x.discard(); }
        vivos.clear();
        if (vitoria) {
            s.getPlayerList().broadcastSystemMessage(Component.literal("🛡 " + reino.nome() + " resistiu à invasão! Obrigado, defensores:").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
            for (UUID u : participantes) {
                ServerPlayer p = s.getPlayerList().getPlayer(u);
                if (p == null) continue;
                int n = abates.getOrDefault(u, 0);
                long moedas = Math.round(num("moedasPorJogador", 150) + n * num("moedasPorAbate", 5));
                int rep = (int) Math.round(num("repVitoria", 100) + n * num("repPorAbate", 5));
                Reputacao.ganhar(p, reino.id(), rep, true);
                Campanhas.aoDefender(p, reino.id());
                br.com.frxcode.frxworld.progressao.Conquistas.somar(p, "invasoes", 1);
                s.getPlayerList().broadcastSystemMessage(Component.literal("  " + p.getGameProfile().getName() + " — " + n + " abates"
                        + " · +" + rep + " reputação").withStyle(ChatFormatting.WHITE), false);
                p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            }
        } else {
            s.getPlayerList().broadcastSystemMessage(Component.literal("A horda recuou de " + reino.nome() + " depois de 15 minutos. Sem recompensa desta vez.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (barra != null) { barra.removeAllPlayers(); barra = null; }
        fase = Fase.NADA;
        FrxWorld.LOG.info("Invasão a {} terminou ({}).", reino.id(), vitoria ? "vitória" : "fim do tempo");
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aoMatar(LivingDeathEvent e) {
        if (!e.getEntity().getTags().contains(TAG) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        abates.merge(p.getUUID(), 1, Integer::sum);
        participantes.add(p.getUUID());
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent e) {
        if (barra != null && e.getEntity() instanceof ServerPlayer p) barra.removePlayer(p);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("invasao").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (fase == Fase.NADA) p.sendSystemMessage(Component.literal("Nenhuma invasão agora. Elas acontecem quando há jogadores numa capital.").withStyle(ChatFormatting.GRAY));
            else p.sendSystemMessage(Component.literal("⚔ " + reino.nome() + " sob ataque em " + centro.getX() + ", " + centro.getY() + ", " + centro.getZ()
                    + " — onda " + Math.max(1, onda) + "/" + ondas() + ", " + vivos.size() + " inimigos").withStyle(ChatFormatting.RED));
            return 1;
        }));
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("invasao")
                        .then(Commands.literal("parar").executes(ctx -> {
                            if (fase != Fase.NADA) fim(ctx.getSource().getServer(), false);
                            return 1;
                        }))
                        .then(Commands.argument("reino", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Reinos.todos().stream().map(Reino::id), b))
                                .executes(ctx -> {
                                    Reino r = Reputacao.reino(StringArgumentType.getString(ctx, "reino"));
                                    if (r == null) { ctx.getSource().sendFailure(Component.literal("Reino desconhecido.")); return 0; }
                                    ServerPlayer p = ctx.getSource().getPlayer();
                                    BlockPos onde = p != null && r.distancia(p.getBlockX(), p.getBlockZ()) == 0 ? p.blockPosition()
                                            : new BlockPos(r.centroX(), r.chao() + 1, r.centroZ());
                                    if (!comecar(ctx.getSource().getServer(), r, onde)) { ctx.getSource().sendFailure(Component.literal("Já há uma invasão.")); return 0; }
                                    return 1;
                                }))));
    }
}
