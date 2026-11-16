package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Eventos do céu.
 * Lua de Sangue: algumas noites os monstros nascem mais rápidos, mas dão XP e loot em dobro.
 * Chuva de meteoros: de vez em quando meteoros caem perto dos jogadores e deixam minério raro onde batem.
 */
public class Ceu {
    private static final int CHANCE_LUA = 15, CHANCE_METEOROS = 35;
    private static final long INTERVALO_METEOROS_MS = 75 * 60_000L;

    private static boolean lua;
    private static long noiteChecada = -1;
    private static long proximaChuva = System.currentTimeMillis() + 25 * 60_000L;

    private record Meteoro(BlockPos alvo, int cai) { }          // tick em que toca o chão
    private record Cratera(BlockPos pos, int fim) { }
    private static final List<Meteoro> caindo = new ArrayList<>();
    private static final List<Cratera> crateras = new ArrayList<>();
    private static int restantes, proximoTick;

    /** Multiplicador de XP dos monstros (2 durante a Lua de Sangue). */
    public static double multXp() { return lua ? 2.0 : 1.0; }
    public static boolean luaDeSangue() { return lua; }

    private static void todos(MinecraftServer s, String titulo, String texto, int cor, ChatFormatting chat) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) Sistema.avisar(p, titulo, texto, cor);
        s.getPlayerList().broadcastSystemMessage(Component.literal(titulo + " — " + texto).withStyle(chat), false);
    }

    private static boolean emCapital(int x, int z) {
        for (Reino r : Reinos.todos()) if (r.distancia(x, z) < 40) return true;
        return false;
    }

    // ------------------------------------------------------------------ lua de sangue

    private static void comecarLua(MinecraftServer s) {
        lua = true;
        todos(s, "🌑 Lua de Sangue", "Esta noite os monstros estão mais rápidos e furiosos — e dão XP e loot em dobro. Até o amanhecer.", Sistema.VERMELHO, ChatFormatting.DARK_RED);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.AMBIENT, 0.6f, 0.5f);
    }

    private static void acabarLua(MinecraftServer s) {
        lua = false;
        s.getPlayerList().broadcastSystemMessage(Component.literal("🌅 A Lua de Sangue se pôs. A noite voltou ao normal.").withStyle(ChatFormatting.GRAY), false);
    }

    @SubscribeEvent
    public void monstrosDaLua(MobSpawnEvent.FinalizeSpawn ev) {
        if (!lua || ev.getSpawnType() != MobSpawnType.NATURAL || !(ev.getEntity() instanceof Enemy)) return;
        if (ev.getEntity().level().dimension() != Level.OVERWORLD) return;
        ev.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 10, 0, false, false));
    }

    @SubscribeEvent
    public void lootEmDobro(LivingDropsEvent ev) {
        if (!lua || !(ev.getEntity() instanceof Enemy) || !(ev.getSource().getEntity() instanceof ServerPlayer)) return;
        if (ev.getEntity().level().dimension() != Level.OVERWORLD) return;
        List<ItemEntity> copia = new ArrayList<>();
        for (ItemEntity i : ev.getDrops()) {
            if (i.getItem().getMaxStackSize() == 1) continue;   // equipamento não duplica
            copia.add(new ItemEntity(i.level(), i.getX(), i.getY(), i.getZ(), i.getItem().copy()));
        }
        ev.getDrops().addAll(copia);
    }

    // ------------------------------------------------------------------ meteoros

    private static void comecarChuva(MinecraftServer s) {
        restantes = 4 + ThreadLocalRandom.current().nextInt(3);
        proximoTick = s.getTickCount() + 100;
        todos(s, "☄ Chuva de meteoros", "Meteoros estão cruzando o céu! Eles deixam minério raro onde caem — siga as colunas de fogo.", Sistema.OURO, ChatFormatting.GOLD);
    }

    private static void lancar(MinecraftServer s) {
        ServerLevel n = s.overworld();
        List<ServerPlayer> gente = n.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
        if (gente.isEmpty()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ServerPlayer p = gente.get(rnd.nextInt(gente.size()));
        for (int t = 0; t < 12; t++) {
            double ang = rnd.nextDouble(Math.PI * 2), dist = rnd.nextDouble(60, 130);
            int x = p.getBlockX() + (int) (Math.cos(ang) * dist), z = p.getBlockZ() + (int) (Math.sin(ang) * dist);
            if (emCapital(x, z) || !n.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos b = new BlockPos(x, n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!n.getFluidState(b.below()).isEmpty()) continue;
            boolean pertoDeAlguem = false;
            for (ServerPlayer o : gente) if (o.blockPosition().closerThan(b, 30)) pertoDeAlguem = true;
            if (pertoDeAlguem) continue;
            caindo.add(new Meteoro(b, s.getTickCount() + 60));
            return;
        }
    }

    private static void impacto(MinecraftServer s, Meteoro m) {
        ServerLevel n = s.overworld();
        BlockPos b = m.alvo();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        n.explode(null, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 3.5f, Level.ExplosionInteraction.NONE);
        int sorte = rnd.nextInt(100);
        Block nucleo = sorte < 30 ? Blocks.RAW_IRON_BLOCK : sorte < 55 ? Blocks.RAW_GOLD_BLOCK : sorte < 70 ? Blocks.LAPIS_BLOCK : sorte < 85 ? Blocks.DEEPSLATE_DIAMOND_ORE
                : sorte < 92 ? Blocks.EMERALD_BLOCK : Blocks.ANCIENT_DEBRIS;
        n.setBlock(b, nucleo.defaultBlockState(), 3);
        for (BlockPos v : new BlockPos[]{b.north(), b.south(), b.east(), b.west()})
            if (rnd.nextBoolean() && n.getBlockState(v).canBeReplaced()) n.setBlock(v, (rnd.nextBoolean() ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE).defaultBlockState(), 3);
        n.sendParticles(ParticleTypes.LAVA, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 40, 1, 0.5, 1, 0);
        crateras.add(new Cratera(b, s.getTickCount() + 20 * 60 * 4));
        for (ServerPlayer p : n.players()) {
            if (!p.blockPosition().closerThan(b, 400)) continue;
            p.sendSystemMessage(Component.literal("☄ Um meteoro caiu! ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("[name:\"Meteoro\", x:" + b.getX() + ", y:" + b.getY() + ", z:" + b.getZ() + "]").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  a " + (int) Math.sqrt(p.blockPosition().distSqr(b)) + " blocos. Quem chegar primeiro leva o minério.").withStyle(ChatFormatting.GRAY)));
        }
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        ServerLevel n = s.overworld();
        int agora = s.getTickCount();
        // rastro dos meteoros caindo (a cada 2 ticks) e impacto
        if (!caindo.isEmpty() && agora % 2 == 0) {
            for (Meteoro m : new ArrayList<>(caindo)) {
                int falta = m.cai() - agora;
                if (falta <= 0) { caindo.remove(m); try { impacto(s, m); } catch (Exception ignorado) { } continue; }
                double x = m.alvo().getX() + 0.5 + falta * 0.7, y = m.alvo().getY() + falta * 1.6, z = m.alvo().getZ() + 0.5 + falta * 0.4;
                for (ServerPlayer p : n.players()) {
                    if (!p.blockPosition().closerThan(m.alvo(), 400)) continue;
                    n.sendParticles(p, ParticleTypes.FLAME, true, x, y, z, 12, 0.4, 0.4, 0.4, 0.02);
                    n.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, x + 1, y + 2, z + 0.6, 4, 0.3, 0.3, 0.3, 0.01);
                }
                if (falta == 40) n.playSound(null, m.alvo(), SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 6f, 0.5f);
            }
        }
        if (agora % 20 != 9) return;
        // colunas de fogo sobre as crateras
        for (Cratera c : new ArrayList<>(crateras)) {
            if (agora > c.fim() || n.getBlockState(c.pos()).isAir()) { crateras.remove(c); continue; }
            for (ServerPlayer p : n.players()) {
                if (!p.blockPosition().closerThan(c.pos(), 400)) continue;
                for (int i = 0; i < 12; i++) n.sendParticles(p, ParticleTypes.FLAME, true, c.pos().getX() + 0.5, c.pos().getY() + 3 + i * 4, c.pos().getZ() + 0.5, 2, 0.1, 1.6, 0.1, 0);
            }
        }
        if (restantes > 0 && agora >= proximoTick) {
            restantes--;
            proximoTick = agora + 300 + ThreadLocalRandom.current().nextInt(300);
            lancar(s);
        }
        long relogio = n.getDayTime() % 24000, dia = n.getDayTime() / 24000;
        boolean noite = relogio >= 13000 && relogio < 23000;
        if (lua && !noite) acabarLua(s);
        if (noite && dia != noiteChecada) {
            noiteChecada = dia;
            if (!lua && !s.getPlayerList().getPlayers().isEmpty() && ThreadLocalRandom.current().nextInt(100) < CHANCE_LUA) comecarLua(s);
        }
        long ms = System.currentTimeMillis();
        if (restantes == 0 && ms > proximaChuva) {
            proximaChuva = ms + INTERVALO_METEOROS_MS;
            if (!n.players().isEmpty() && ThreadLocalRandom.current().nextInt(100) < CHANCE_METEOROS) comecarChuva(s);
        }
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("ceu")
                        .then(Commands.literal("lua").executes(c -> { if (!lua) comecarLua(c.getSource().getServer()); return 1; }))
                        .then(Commands.literal("meteoros").executes(c -> { comecarChuva(c.getSource().getServer()); return 1; }))
                        .then(Commands.literal("parar").executes(c -> {
                            if (lua) acabarLua(c.getSource().getServer());
                            restantes = 0; caindo.clear();
                            return 1;
                        }))));
    }
}
