package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Conquistas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mural de procurados: de tempos em tempos um monstro raro, com nome próprio, aparece perto de um jogador.
 * A Guilda anuncia o contrato; quem estiver por perto quando ele cair divide a glória (cada um leva o prêmio).
 */
public class Procurados {
    private static final String TAG = "frx_procurado";
    private static final long DURACAO_MS = 25 * 60_000L, INTERVALO_MS = 30 * 60_000L;
    private static final int CHANCE = 60;
    private static final String[] TIPOS = {"minecraft:zombie", "minecraft:husk", "minecraft:skeleton", "minecraft:stray", "minecraft:vindicator", "minecraft:wither_skeleton"};
    private static final String[] NOMES = {"Grukk", "Morvath", "Zarok", "Ulgrim", "Vexa", "Draven", "Kargath", "Nyx", "Bront", "Sylas", "Orlok", "Thessa"};
    private static final String[] ALCUNHAS = {"o Devorador", "a Sombra", "Quebra-Ossos", "o Sem-Rosto", "Lâmina Negra", "o Carniceiro", "Olho de Sangue", "o Imortal", "Mão de Ferro"};

    private static UUID alvo;
    private static String nome = "";
    private static BlockPos pos;
    private static int nivel;
    private static long limite, proxima = System.currentTimeMillis() + 15 * 60_000L;

    private static long moedas() { return 300 + nivel * 40L; }
    private static long xp() { return 250L * nivel; }

    private static boolean emCapital(int x, int z) {
        for (Reino r : Reinos.todos()) if (r.distancia(x, z) < 32) return true;
        return false;
    }

    static boolean criar(MinecraftServer s, ServerPlayer perto, boolean aqui) {
        if (alvo != null) return false;
        ServerLevel mundo = s.overworld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        BlockPos b = null;
        for (int t = 0; t < 16 && b == null; t++) {
            double ang = rnd.nextDouble(Math.PI * 2), dist = aqui ? 6 : rnd.nextDouble(70, 120);
            int x = perto.getBlockX() + (int) (Math.cos(ang) * dist), z = perto.getBlockZ() + (int) (Math.sin(ang) * dist);
            if (emCapital(x, z) || !mundo.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos c = new BlockPos(x, mundo.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!mundo.getFluidState(c.below()).isEmpty() || c.getY() <= mundo.getMinBuildHeight() + 1) continue;
            b = c;
        }
        if (b == null) return false;
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(TIPOS[rnd.nextInt(TIPOS.length)]));
        Entity e = tipo == null ? null : tipo.create(mundo);
        if (!(e instanceof Mob m)) return false;
        m.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, rnd.nextFloat() * 360f, 0);
        m.finalizeSpawn(mundo, mundo.getCurrentDifficultyAt(b), MobSpawnType.EVENT, null, null);
        nivel = Math.max(3, Math.min(ConfigJogo.zonaNivelMax(), Math.max(Zonas.nivel(mundo, b), Progressao.temDados(perto) ? Progressao.dados(perto).nivel() : 1) + 3));
        Zonas.aplicarNivel(m, nivel);
        var vida = m.getAttribute(Attributes.MAX_HEALTH);
        if (vida != null) vida.addPermanentModifier(new AttributeModifier(UUID.fromString("6f0f6f3e-2c1d-4b53-9a51-0f5d7c1e9a01"), "FRX procurado", 3.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        var dano = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dano != null) dano.addPermanentModifier(new AttributeModifier(UUID.fromString("6f0f6f3e-2c1d-4b53-9a51-0f5d7c1e9a02"), "FRX procurado", 0.4, AttributeModifier.Operation.MULTIPLY_TOTAL));
        m.setHealth(m.getMaxHealth());
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));   // não queima no sol
        m.setDropChance(EquipmentSlot.HEAD, 0f);
        m.setPersistenceRequired();
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        nome = NOMES[rnd.nextInt(NOMES.length)] + ", " + ALCUNHAS[rnd.nextInt(ALCUNHAS.length)];
        m.setCustomName(Component.literal("☠ " + nome).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        m.setCustomNameVisible(true);
        m.addTag(TAG);
        alvo = m.getUUID();   // antes de entrar no mundo: aoEntrarNoMundo só deixa passar o alvo atual
        mundo.addFreshEntity(m);
        pos = b;
        limite = System.currentTimeMillis() + DURACAO_MS;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Sistema.avisar(p, "PROCURADO: " + nome, "Visto em " + b.getX() + ", " + b.getY() + ", " + b.getZ() + " (nível " + nivel + "). Recompensa: "
                    + xp() + " XP, com chance de runa e mapa do tesouro. A Guilda dá " + DURACAO_MS / 60_000 + " minutos. (/procurados)", Sistema.VERMELHO);
            anunciar(p);
        }
        return true;
    }

    private static void anunciar(ServerPlayer p) {
        if (alvo == null) { p.sendSystemMessage(Component.literal("O mural da Guilda está vazio no momento.").withStyle(ChatFormatting.GRAY)); return; }
        long resta = Math.max(0, (limite - System.currentTimeMillis()) / 60_000);
        p.sendSystemMessage(Component.literal("☠ PROCURADO: " + nome + " (nível " + nivel + ") — recompensa " + xp() + " XP, runa e mapa. Restam " + resta + " min.")
                .withStyle(ChatFormatting.RED));
        p.sendSystemMessage(Component.literal("   📍 ").withStyle(ChatFormatting.RED)
                .append(Component.literal("[name:\"Procurado: " + nome + "\", x:" + pos.getX() + ", y:" + pos.getY() + ", z:" + pos.getZ() + "]").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  visto por último ali. Clique para marcar no mapa.").withStyle(ChatFormatting.GRAY)));
    }

    private static void encerrar(MinecraftServer s, boolean remover) {
        if (remover && alvo != null) { Entity e = s.overworld().getEntity(alvo); if (e != null) e.discard(); }
        alvo = null;
        proxima = System.currentTimeMillis() + INTERVALO_MS;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 20 != 7) return;
        MinecraftServer s = ev.getServer();
        long agora = System.currentTimeMillis();
        if (alvo == null) {
            if (agora < proxima) return;
            proxima = agora + INTERVALO_MS;
            List<ServerPlayer> online = s.overworld().players().stream().filter(p -> p.isAlive() && !p.isSpectator() && !emCapital(p.getBlockX(), p.getBlockZ())).toList();
            if (online.isEmpty()) { proxima = agora + 5 * 60_000L; return; }
            if (ThreadLocalRandom.current().nextInt(100) >= CHANCE) return;
            criar(s, online.get(ThreadLocalRandom.current().nextInt(online.size())), false);
            return;
        }
        if (agora > limite) {
            s.getPlayerList().broadcastSystemMessage(Component.literal("☠ " + nome + " escapou. O contrato da Guilda expirou.").withStyle(ChatFormatting.GRAY), false);
            encerrar(s, true);
            return;
        }
        Entity e = s.overworld().getEntity(alvo);
        if (e == null) return;   // chunk descarregado: continua valendo
        pos = e.blockPosition();
        ServerLevel mundo = s.overworld();
        for (ServerPlayer p : mundo.players()) {
            if (!p.blockPosition().closerThan(pos, 300)) continue;
            for (int i = 0; i < 12; i++)
                mundo.sendParticles(p, ParticleTypes.FLAME, true, e.getX(), e.getY() + 4 + i * 4, e.getZ(), 2, 0.1, 1.6, 0.1, 0.0);
        }
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        Entity e = ev.getEntity();
        if (alvo == null || e.level().isClientSide() || !e.getUUID().equals(alvo) || !(e.level() instanceof ServerLevel mundo)) return;
        MinecraftServer s = mundo.getServer();
        StringBuilder nomes = new StringBuilder();
        for (ServerPlayer p : mundo.players()) {
            boolean matou = ev.getSource().getEntity() == p;
            if (!matou && !(p.isAlive() && p.blockPosition().closerThan(e.blockPosition(), 40))) continue;
            Progressao.ganharXp(p, xp());
            Conquistas.somar(p, "procurados", 1);
            Tesouros.talvezDar(p, 25);
            Runas.talvezDar(p, 35, 2);
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            nomes.append(nomes.length() > 0 ? ", " : "").append(p.getGameProfile().getName());
        }
        s.getPlayerList().broadcastSystemMessage(Component.literal("☠ " + nome + " foi abatido" + (nomes.length() > 0 ? " por " + nomes : "") + "! Contrato pago: "
                + xp() + " XP.").withStyle(ChatFormatting.GOLD), false);
        encerrar(s, false);
    }

    /** Sobra de uma queda do servidor some ao carregar. */
    @SubscribeEvent
    public void aoEntrarNoMundo(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || !e.getTags().contains(TAG)) return;
        if (alvo == null || !e.getUUID().equals(alvo)) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) { encerrar(ev.getServer(), true); }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("procurados").executes(c -> { anunciar(c.getSource().getPlayerOrException()); return 1; }));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("procurado")
                        .executes(c -> {
                            boolean ok = criar(c.getSource().getServer(), c.getSource().getPlayerOrException(), true);
                            c.getSource().sendSuccess(() -> Component.literal(ok ? "Procurado criado ao seu lado." : "Não deu: já existe um, ou não achei lugar."), false);
                            return ok ? 1 : 0;
                        })
                        .then(Commands.literal("encerrar").executes(c -> { encerrar(c.getSource().getServer(), true); return 1; }))));
    }
}
