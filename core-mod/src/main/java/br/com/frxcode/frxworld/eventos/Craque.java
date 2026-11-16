package br.com.frxcode.frxworld.eventos;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evento raro e sem motivo nenhum: o Ronaldinho aparece do nada, dribla o jogador com a bola e some.
 * Uma vez por minuto cada jogador tem "chance por mil" de ser o escolhido (frx_ronaldinho.txt, padrão 4 = 0,4%).
 * A skin vem do painel (NPCs → "Ronaldinho"), papel "craque".
 */
public class Craque {
    private static final String TAG = "frx_craque";
    private static final int CHEGADA = 25, DRIBLE = 45, FUGA = 22;
    private static int porMil = 4;

    private static final class Cena {
        final Entity npc, bola;
        final Vec3 origem;      // onde ele apareceu, relativo ao jogador
        final double angulo;    // ângulo de chegada
        int t = 0;
        Cena(Entity npc, Entity bola, Vec3 origem, double angulo) { this.npc = npc; this.bola = bola; this.origem = origem; this.angulo = angulo; }
    }
    private static final Map<UUID, Cena> cenas = new ConcurrentHashMap<>();

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_ronaldinho.txt"); }

    @SubscribeEvent
    public void aoIniciar(ServerStartedEvent ev) {
        try {
            Path a = arquivo(ev.getServer());
            if (Files.exists(a)) porMil = Math.max(0, Math.min(1000, Integer.parseInt(Files.readString(a).trim())));
        } catch (Exception e) {
            FrxWorld.LOG.warn("Ronaldinho: não li frx_ronaldinho.txt: {}", e.toString());
        }
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        for (Cena c : cenas.values()) { c.npc.discard(); c.bola.discard(); }
        cenas.clear();
    }

    /** Sobrou de uma queda do servidor: some ao carregar. */
    @SubscribeEvent
    public void aoEntrarNoMundo(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || !e.getTags().contains(TAG)) return;
        for (Cena c : cenas.values()) if (c.npc == e || c.bola == e) return;
        ev.setCanceled(true);
    }

    // ------------------------------------------------------------------ começo

    private static boolean livre(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger() && p.onGround() && !p.isInWater()
                && p.tickCount - p.getLastHurtByMobTimestamp() > 200 && !cenas.containsKey(p.getUUID());
    }

    static boolean comecar(ServerPlayer p) {
        if (cenas.containsKey(p.getUUID())) return false;
        ServerLevel nivel = p.serverLevel();
        // procura um lugar livre a ~7 blocos, de preferência na frente do jogador
        double base = Math.toRadians(p.getYRot() + 90);
        Vec3 rel = null; double angulo = base;
        for (int i = 0; i < 8 && rel == null; i++) {
            double ang = base + (i % 2 == 0 ? 1 : -1) * (i / 2) * Math.PI / 4;
            for (double dist = 7; dist >= 4 && rel == null; dist -= 1.5) {
                Vec3 r = new Vec3(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                BlockPos b = BlockPos.containing(p.getX() + r.x, p.getY() + 0.2, p.getZ() + r.z);
                if (nivel.getBlockState(b).getCollisionShape(nivel, b).isEmpty() && nivel.getBlockState(b.above()).getCollisionShape(nivel, b.above()).isEmpty()
                        && !nivel.getBlockState(b.below()).getCollisionShape(nivel, b.below()).isEmpty()) { rel = r; angulo = ang; }
            }
        }
        if (rel == null) return false;

        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse("guardvillagers:guard"));
        Entity npc = (tipo != null ? tipo : EntityType.VILLAGER).create(nivel);
        Entity bola = EntityType.ITEM_DISPLAY.create(nivel);
        if (npc == null || bola == null) return false;
        npc.moveTo(p.getX() + rel.x, p.getY(), p.getZ() + rel.z, 0, 0);
        if (npc instanceof Mob m) {
            m.setNoAi(true);
            for (EquipmentSlot s : EquipmentSlot.values()) { m.setItemSlot(s, ItemStack.EMPTY); m.setDropChance(s, 0f); }
        }
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.addTag(TAG);
        npc.getPersistentData().putString("frx_papel", "craque");
        npc.getPersistentData().putString("frx_reino", "");
        npc.setCustomName(Component.literal("Ronaldinho").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        npc.setCustomNameVisible(true);

        CompoundTag nbt = new CompoundTag();
        bola.saveWithoutId(nbt);
        nbt.put("item", new ItemStack(Items.SNOWBALL).save(new CompoundTag()));
        nbt.putString("billboard", "center");
        bola.load(nbt);
        bola.addTag(TAG);
        bola.moveTo(npc.getX(), npc.getY() + 0.3, npc.getZ());

        Cena c = new Cena(npc, bola, rel, angulo);
        cenas.put(p.getUUID(), c);
        nivel.addFreshEntity(npc);
        nivel.addFreshEntity(bola);
        nivel.sendParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + 1, npc.getZ(), 25, 0.3, 0.6, 0.3, 0.05);
        nivel.playSound(null, npc.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1f, 1.2f);
        p.displayClientMessage(Component.literal("⚽ Peraí... aquele ali é o Ronaldinho?!").withStyle(ChatFormatting.YELLOW), true);
        return true;
    }

    // ------------------------------------------------------------------ cena

    private static void quadro(ServerPlayer p, Cena c) {
        ServerLevel nivel = p.serverLevel();
        int t = c.t++;
        Vec3 centro = p.position();
        Vec3 pos; double olhar; // olhar = ângulo para onde ele corre
        if (t < CHEGADA) {
            double f = (double) t / CHEGADA;
            double dist = c.origem.length() * (1 - f) + 1.7 * f;
            pos = centro.add(Math.cos(c.angulo) * dist, 0, Math.sin(c.angulo) * dist);
            olhar = c.angulo + Math.PI;
        } else if (t < CHEGADA + DRIBLE) {
            double f = (double) (t - CHEGADA) / DRIBLE;
            double ang = c.angulo + f * Math.PI * 2.5; // uma volta e meia: termina nas costas
            double raio = 1.7 - 0.5 * Math.sin(f * Math.PI * 4);
            pos = centro.add(Math.cos(ang) * raio, 0, Math.sin(ang) * raio);
            olhar = ang + Math.PI / 2;
        } else {
            double f = (double) (t - CHEGADA - DRIBLE) / FUGA;
            double ang = c.angulo + Math.PI * 2.5;
            double dist = 1.7 + f * 9;
            pos = centro.add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
            olhar = ang;
        }
        float yaw = (float) Math.toDegrees(olhar) - 90;
        c.npc.moveTo(pos.x, centro.y, pos.z, yaw, 0);
        c.npc.setYHeadRot(yaw);
        c.npc.setYBodyRot(yaw);
        double toque = 0.75 + 0.25 * Math.sin(t * 0.9);
        c.bola.moveTo(pos.x + Math.cos(olhar) * toque, centro.y + 0.3 + 0.12 * Math.abs(Math.sin(t * 0.9)), pos.z + Math.sin(olhar) * toque);
        if (t % 6 == 0) nivel.playSound(null, c.npc.blockPosition(), SoundEvents.WOOL_STEP, SoundSource.NEUTRAL, 1f, 0.8f);

        if (t == CHEGADA) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DRIBLE + 10, 5, false, false));
            nivel.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.NEUTRAL, 1f, 1.6f);
        }
        if (t == CHEGADA + DRIBLE / 2) {
            // a caneta: o jogador gira procurando a bola
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, p.getXRot());
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("OLÉ!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("A bola passou no meio das suas pernas").withStyle(ChatFormatting.YELLOW)));
            nivel.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.8f);
        }
        if (t >= CHEGADA + DRIBLE + FUGA) fim(p, c, true);
    }

    private static void fim(ServerPlayer p, Cena c, boolean completo) {
        cenas.values().remove(c);
        if (c.npc.level() instanceof ServerLevel nivel) {
            nivel.sendParticles(ParticleTypes.POOF, c.npc.getX(), c.npc.getY() + 1, c.npc.getZ(), 30, 0.3, 0.6, 0.3, 0.05);
            nivel.sendParticles(ParticleTypes.FIREWORK, c.npc.getX(), c.npc.getY() + 1, c.npc.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
            nivel.playSound(null, c.npc.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1f, 1f);
        }
        c.npc.discard();
        c.bola.discard();
        if (!completo || p == null) return;
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("⚽ " + p.getGameProfile().getName()
                + " foi driblado pelo Ronaldinho e está até agora procurando a bola.").withStyle(ChatFormatting.YELLOW), false);
        ItemStack lembranca = new ItemStack(Items.SNOWBALL);
        lembranca.setHoverName(Component.literal("Bola do Bruxo").withStyle(ChatFormatting.GOLD));
        if (!p.getInventory().add(lembranca)) p.drop(lembranca, false);
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ev.getServer();
        for (Iterator<Map.Entry<UUID, Cena>> it = cenas.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Cena> ent = it.next();
            Cena c = ent.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(ent.getKey());
            try {
                if (p == null || !p.isAlive() || p.level() != c.npc.level() || c.t > 200) fim(null, c, false);
                else quadro(p, c);
            } catch (Exception e) {
                FrxWorld.LOG.warn("Ronaldinho: cena falhou: {}", e.toString());
                try { fim(null, c, false); } catch (Exception ignorado) { cenas.values().remove(c); }
            }
        }
        if (porMil > 0 && server.getTickCount() % 1200 == 600) {
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (ThreadLocalRandom.current().nextInt(1000) < porMil && livre(p)) comecar(p);
        }
    }

    // ------------------------------------------------------------------ comandos

    private static int chamar(CommandSourceStack fonte, ServerPlayer p) {
        boolean ok = comecar(p);
        fonte.sendSuccess(() -> Component.literal(ok ? "Ronaldinho a caminho de " + p.getGameProfile().getName() + "."
                : "Não deu: sem espaço livre em volta ou ele já está lá."), false);
        return ok ? 1 : 0;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("ronaldinho")
                        .executes(c -> chamar(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.literal("chance")
                                .executes(c -> {
                                    c.getSource().sendSuccess(() -> Component.literal("Ronaldinho: " + porMil + " em 1000 por minuto, por jogador"
                                            + (porMil > 0 ? " (em média 1 vez a cada " + Math.round(1000.0 / porMil / 60 * 10) / 10.0 + " h de jogo)." : " (desligado).")), false);
                                    return 1;
                                })
                                .then(Commands.argument("porMil", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                                    porMil = IntegerArgumentType.getInteger(c, "porMil");
                                    try { Files.writeString(arquivo(c.getSource().getServer()), String.valueOf(porMil)); } catch (Exception e) { FrxWorld.LOG.warn("Ronaldinho: não gravei a chance: {}", e.toString()); }
                                    c.getSource().sendSuccess(() -> Component.literal("Chance do Ronaldinho: " + porMil + " em 1000 por minuto, por jogador."), true);
                                    return 1;
                                })))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(c -> chamar(c.getSource(), EntityArgument.getPlayer(c, "jogador"))))));
    }
}
