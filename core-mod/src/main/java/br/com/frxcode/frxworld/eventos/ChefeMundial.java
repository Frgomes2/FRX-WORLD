package br.com.frxcode.frxworld.eventos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.armas.Armas;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chefe mundial: em horários marcados no painel (aba Progressão → "Chefe mundial") um chefe fortíssimo
 * aparece num ponto do mapa longe das capitais. Aviso com antecedência, barra de vida para todos,
 * e recompensa repartida pelo dano causado (moedas, XP e arma Lendária/Épica para os 3 primeiros).
 */
public class ChefeMundial {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String TAG = "frx_chefe_mundial";
    private static final String[] DIAS = {"SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM"};
    private static final List<String> PADRAO_CHEFES = List.of("mowziesmobs:frostmaw", "mowziesmobs:ferrous_wroughtnaut",
            "cataclysm:netherite_monstrosity", "cataclysm:ignis", "minecraft:wither");

    private enum Fase { NADA, AVISADO, ATIVO }

    private static Fase fase = Fase.NADA;
    private static BlockPos pos;
    private static String tipo;
    private static long nascerEm, terminarEm;
    private static UUID chefe;
    private static ServerBossEvent barra;
    private static final Map<UUID, Float> dano = new HashMap<>();
    private static final Set<String> feitos = new HashSet<>();
    private static final Set<Integer> lembretes = new HashSet<>();
    private static int ticks;

    // ------------------------------------------------------------------ config (painel)

    private static JsonObject cfg() { return ConfigJogo.secaoCrua("chefeMundial"); }

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
        for (JsonElement e : o.getAsJsonArray(k)) l.add(e.getAsString().trim());
        return l.isEmpty() ? padrao : l;
    }

    /** "SAB 20:00" ou "TODO 21:30". Retorna o próximo horário a partir de agora (ou null). */
    private static LocalDateTime proximo(LocalDateTime agora, int avisoMin) {
        LocalDateTime melhor = null;
        for (String h : lista("horarios", List.of("SAB 20:00"))) {
            try {
                String[] p = h.toUpperCase().split("\\s+");
                String[] hm = p[1].split(":");
                int hora = Integer.parseInt(hm[0]), min = Integer.parseInt(hm[1]);
                for (int d = 0; d <= 7; d++) {
                    LocalDateTime dia = agora.toLocalDate().plusDays(d).atTime(hora, min);
                    DayOfWeek dw = dia.getDayOfWeek();
                    if (!p[0].equals("TODO") && !p[0].equals(DIAS[dw.getValue() - 1])) continue;
                    if (dia.isBefore(agora.minusMinutes(5))) continue;
                    if (melhor == null || dia.isBefore(melhor)) melhor = dia;
                    break;
                }
            } catch (Exception ignorado) {}
        }
        return melhor;
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        MinecraftServer s = e.getServer();
        long agora = System.currentTimeMillis();
        switch (fase) {
            case NADA -> {
                if (!ativo() || ticks % 200 != 0) return;
                int aviso = (int) num("avisoMinutos", 10);
                LocalDateTime agoraL = LocalDateTime.now(FUSO);
                LocalDateTime h = proximo(agoraL, aviso);
                if (h == null) return;
                String chave = h.toString();
                if (feitos.contains(chave) || agoraL.isBefore(h.minusMinutes(aviso))) return;
                feitos.add(chave);
                long ms = Math.max(60_000, java.time.Duration.between(agoraL, h).toMillis());
                anunciar(s, null, ms);
            }
            case AVISADO -> {
                long falta = nascerEm - agora;
                int min = (int) Math.ceil(falta / 60000.0);
                if ((min == 5 || min == 1) && lembretes.add(min))
                    todos(s, Component.literal("☠ O chefe mundial surge em " + min + (min == 1 ? " minuto" : " minutos") + " em " + texto(pos) + "!")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                if (falta <= 0) nascer(s);
            }
            case ATIVO -> {
                ServerLevel nivel = s.overworld();
                Entity ent = nivel.getEntity(chefe);
                if (ent instanceof LivingEntity v && barra != null) barra.setProgress(Math.max(0, v.getHealth() / v.getMaxHealth()));
                long resta = terminarEm - agora;
                if (barra != null) barra.setName(Component.literal("☠ " + nomeTipo(tipo) + " · " + relogio(resta) + " · " + texto(pos)));
                int minRest = (int) Math.ceil(resta / 60000.0);
                if ((minRest == 10 || minRest == 5 || minRest == 1) && lembretes.add(-minRest))
                    todos(s, Component.literal("⌛ Faltam " + minRest + (minRest == 1 ? " minuto" : " minutos") + " para o chefe mundial fugir! (" + texto(pos) + ")")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                if (agora >= terminarEm) {
                    if (ent != null) ent.discard();
                    todos(s, Component.literal("☠ O chefe mundial se retirou... ninguém conseguiu derrotá-lo.").withStyle(ChatFormatting.GRAY));
                    encerrar(s);
                }
            }
        }
    }

    /** Situação do chefe mundial para a Visão geral do painel (null se não há). */
    public static com.google.gson.JsonObject statusPainel(MinecraftServer s) {
        if (fase == Fase.NADA || pos == null) return null;
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("fase", fase == Fase.AVISADO ? "aviso" : "ativo");
        o.addProperty("nome", nomeTipo(tipo));
        o.addProperty("x", pos.getX());
        o.addProperty("z", pos.getZ());
        long agora = System.currentTimeMillis();
        if (fase == Fase.AVISADO) o.addProperty("surgeEmS", Math.max(0, (nascerEm - agora) / 1000));
        else {
            o.addProperty("fogeEmS", Math.max(0, (terminarEm - agora) / 1000));
            Entity ent = chefe == null ? null : s.overworld().getEntity(chefe);
            if (ent instanceof LivingEntity v) o.addProperty("vida", Math.round(100 * v.getHealth() / v.getMaxHealth()));
            o.addProperty("participantes", dano.size());
        }
        return o;
    }

    private static String relogio(long ms) {
        long seg = Math.max(0, ms / 1000);
        return seg >= 3600 ? String.format("%d:%02d:%02d", seg / 3600, seg / 60 % 60, seg % 60) : String.format("%02d:%02d", seg / 60, seg % 60);
    }

    private static String texto(BlockPos p) { return p.getX() + ", " + p.getY() + ", " + p.getZ(); }

    private static void todos(MinecraftServer s, Component c) { s.getPlayerList().broadcastSystemMessage(c, false); }

    /** Escolhe o local e o chefe e avisa todo mundo. */
    static boolean anunciar(MinecraftServer s, String forcarTipo, long emMs) {
        if (fase != Fase.NADA) return false;
        ServerLevel nivel = s.overworld();
        List<String> validos = new ArrayList<>();
        for (String t : lista("chefes", PADRAO_CHEFES)) if (ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.tryParse(t))) validos.add(t);
        if (forcarTipo != null) validos = List.of(forcarTipo);
        if (validos.isEmpty()) { FrxWorld.LOG.warn("Chefe mundial: nenhum chefe válido na lista."); return false; }
        tipo = validos.get(ThreadLocalRandom.current().nextInt(validos.size()));
        BlockPos spawn = nivel.getSharedSpawnPos();
        double min = num("distanciaMin", 400), max = Math.max(min + 50, num("distanciaMax", 1500));
        pos = null;
        for (int t = 0; t < 40 && pos == null; t++) {
            double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2), d = min + ThreadLocalRandom.current().nextDouble(max - min);
            int x = spawn.getX() + (int) (Math.cos(ang) * d), z = spawn.getZ() + (int) (Math.sin(ang) * d);
            boolean longe = true;
            for (Reino r : Reinos.todos()) if (r.distancia(x, z) < 300) longe = false;
            if (longe) pos = new BlockPos(x, 0, z);
        }
        if (pos == null) return false;
        nivel.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        pos = new BlockPos(pos.getX(), nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
        nascerEm = System.currentTimeMillis() + emMs;
        lembretes.clear();
        fase = Fase.AVISADO;
        String nome = nomeTipo(tipo);
        int minutos = (int) Math.max(1, Math.round(emMs / 60000.0));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("☠ Chefe Mundial ☠").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(nome + " surge em " + minutos + " min").withStyle(ChatFormatting.GOLD)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 0.8f);
        }
        todos(s, Component.literal("☠ " + nome + " vai surgir em " + minutos + " min em " + texto(pos)
                + ". Reúnam-se! Depois de surgir, ele foge em " + (int) num("duracaoMinutos", 60) + " min. A recompensa é repartida pelo dano causado.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        br.com.frxcode.frxworld.acesso.Discord.avisar("☠️ **Chefe mundial:** " + nome + " vai surgir em " + minutos + " min. Reúnam-se!");
        return true;
    }

    private static String nomeTipo(String t) {
        EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(t));
        return et != null ? et.getDescription().getString() : t;
    }

    private static void nascer(MinecraftServer s) {
        ServerLevel nivel = s.overworld();
        EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(tipo));
        nivel.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        Entity ent = et == null ? null : et.create(nivel);
        if (!(ent instanceof Mob m)) {
            todos(s, Component.literal("O chefe mundial não conseguiu surgir (" + tipo + ").").withStyle(ChatFormatting.GRAY));
            encerrar(s);
            return;
        }
        m.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
        m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        m.getPersistentData().putInt("frx_zona", 1);   // não somar o bônus das zonas
        multiplicar(m.getAttribute(Attributes.MAX_HEALTH), num("vidaMult", 4));
        multiplicar(m.getAttribute(Attributes.ATTACK_DAMAGE), num("danoMult", 1.5));
        m.setHealth(m.getMaxHealth());
        m.setCustomName(Component.literal("☠ " + nomeTipo(tipo)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        m.setCustomNameVisible(true);
        m.setGlowingTag(true);
        m.setPersistenceRequired();
        m.addTag(TAG);
        m.getPersistentData().putInt("frx_rank_nivel", br.com.frxcode.frxworld.progressao.ConfigJogo.nivelMaximo());
        nivel.addFreshEntity(m);
        chefe = m.getUUID();
        dano.clear();
        terminarEm = System.currentTimeMillis() + (long) (num("duracaoMinutos", 60) * 60000);
        barra = new ServerBossEvent(Component.literal("☠ Chefe Mundial: " + nomeTipo(tipo) + " (" + texto(pos) + ")"),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) barra.addPlayer(p);
        fase = Fase.ATIVO;
        nivel.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 0.7f);
        lembretes.clear();
        todos(s, Component.literal("☠ " + nomeTipo(tipo) + " surgiu em " + texto(pos) + "! Vocês têm " + relogio(terminarEm - System.currentTimeMillis())
                + " para derrotá-lo, senão ele foge.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    private static void multiplicar(AttributeInstance a, double fator) {
        if (a == null || fator <= 1) return;
        a.addPermanentModifier(new AttributeModifier(UUID.randomUUID(), "FRX chefe mundial", fator - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void encerrar(MinecraftServer s) {
        if (barra != null) { barra.removeAllPlayers(); barra = null; }
        if (pos != null) s.overworld().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
        fase = Fase.NADA;
        chefe = null;
        dano.clear();
    }

    // ------------------------------------------------------------------ combate e recompensa

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void aoFerir(LivingHurtEvent e) {
        if (fase != Fase.ATIVO || !e.getEntity().getUUID().equals(chefe)) return;
        if (e.getSource().getEntity() instanceof ServerPlayer p)
            dano.merge(p.getUUID(), Math.min(e.getAmount(), e.getEntity().getHealth()), Float::sum);
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent e) {
        if (fase != Fase.ATIVO || !e.getEntity().getUUID().equals(chefe)) return;
        MinecraftServer s = e.getEntity().getServer();
        if (s == null) return;
        float total = 0;
        for (float v : dano.values()) total += v;
        List<Map.Entry<UUID, Float>> ranking = new ArrayList<>(dano.entrySet());
        ranking.sort(Map.Entry.<UUID, Float>comparingByValue(Comparator.reverseOrder()));
        long pote = (long) num("moedas", 3000);
        long xp = Progressao.xpDoMob(e.getEntity()) / 2;
        todos(s, Component.literal("☠ " + nomeTipo(tipo) + " foi derrotado!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        int lugar = 0;
        for (Map.Entry<UUID, Float> en : ranking) {
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            if (p == null || total <= 0) continue;
            double parte = en.getValue() / total;
            if (parte < 0.01) continue;
            lugar++;
            Progressao.ganharXp(p, Math.max(100, Math.round(xp * Math.max(0.2, parte))));
            br.com.frxcode.frxworld.progressao.Conquistas.somar(p, "chefes", 1);
            br.com.frxcode.frxworld.racas.Evolucao.checar(p);
            String premio = "";
            if (lugar <= 3) {
                ItemStack arma = sortearArma();
                Armas.definirRaridade(arma, lugar == 1 ? 4 : 3);
                Armas.definirNivel(arma, lugar == 1 ? 3 : 1);
                if (!p.getInventory().add(arma)) p.drop(arma, false);
                premio = " + " + arma.getHoverName().getString() + (lugar == 1 ? " Lendária" : " Épica");
            }
            if (lugar <= 5) todos(s, Component.literal("  " + lugar + "º " + p.getGameProfile().getName() + " — " + Math.round(parte * 100) + "% do dano"
                    + premio).withStyle(lugar == 1 ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
        encerrar(s);
    }

    private static ItemStack sortearArma() {
        Item[] armas = {Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.BOW, Items.CROSSBOW, Items.DIAMOND_SWORD, Items.TRIDENT,
                Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET};
        return new ItemStack(armas[ThreadLocalRandom.current().nextInt(armas.length)]);
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (barra != null && e.getEntity() instanceof ServerPlayer p) barra.addPlayer(p);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent e) {
        if (barra != null && e.getEntity() instanceof ServerPlayer p) barra.removePlayer(p);
    }

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("chefe").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            switch (fase) {
                case AVISADO -> p.sendSystemMessage(Component.literal("☠ " + nomeTipo(tipo) + " surge em "
                        + Math.max(1, (nascerEm - System.currentTimeMillis()) / 60000) + " min em " + texto(pos)).withStyle(ChatFormatting.RED));
                case ATIVO -> p.sendSystemMessage(Component.literal("☠ " + nomeTipo(tipo) + " está em " + texto(pos) + " · foge em "
                        + relogio(terminarEm - System.currentTimeMillis())).withStyle(ChatFormatting.RED));
                default -> {
                    LocalDateTime h = ativo() ? proximo(LocalDateTime.now(FUSO), 0) : null;
                    p.sendSystemMessage(Component.literal(h == null ? "Nenhum chefe mundial marcado."
                            : "Próximo chefe mundial: " + DIAS[h.getDayOfWeek().getValue() - 1].toLowerCase() + " " + h.toLocalDate()
                            + " às " + String.format("%02d:%02d", h.getHour(), h.getMinute())).withStyle(ChatFormatting.GOLD));
                }
            }
            return 1;
        }));
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("chefe")
                        .then(Commands.literal("agora")
                                .executes(ctx -> anunciar(ctx.getSource().getServer(), null, 60_000) ? 1 : 0)
                                .then(Commands.argument("entidade", StringArgumentType.greedyString())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(lista("chefes", PADRAO_CHEFES), b))
                                        .executes(ctx -> anunciar(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "entidade").trim(), 60_000) ? 1 : 0)))
                        .then(Commands.literal("tempo").then(Commands.argument("minutos", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 240)).executes(ctx -> {
                            if (fase != Fase.ATIVO) { ctx.getSource().sendFailure(Component.literal("Nenhum chefe mundial ativo.")); return 0; }
                            terminarEm = System.currentTimeMillis() + com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "minutos") * 60000L;
                            lembretes.clear();
                            ctx.getSource().sendSuccess(() -> Component.literal("O chefe foge em " + relogio(terminarEm - System.currentTimeMillis()) + "."), true);
                            return 1;
                        })))
                        .then(Commands.literal("cancelar").executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            if (chefe != null) { Entity ent = s.overworld().getEntity(chefe); if (ent != null) ent.discard(); }
                            encerrar(s);
                            ctx.getSource().sendSuccess(() -> Component.literal("Chefe mundial cancelado."), true);
                            return 1;
                        }))));
    }
}
