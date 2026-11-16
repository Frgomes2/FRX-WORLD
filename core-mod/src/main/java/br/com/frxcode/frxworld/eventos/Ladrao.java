package br.com.frxcode.frxworld.eventos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Conquistas;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
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
 * Evento raro: um ladrão aparece atrás do jogador, leva uma moeda e sai correndo.
 * Quem alcançar (encostar ou bater) em 15 segundos recebe dez vezes o que foi levado.
 * Chance por mil, por minuto e por jogador, em frx_ladrao.txt (padrão 5). Skin: painel → NPCs → "Ladrão" (papel "ladrao").
 */
public class Ladrao {
    private static final String TAG = "frx_ladrao";
    private static final int DURACAO = 300;             // ticks de fuga
    private static final double PASSO = 0.25;           // blocos por tick: mais que andar, menos que correr
    private static int porMil = 5;

    private static final class Fuga {
        final Entity npc;
        final long levou;
        double x, y, z;
        int t;
        Fuga(Entity npc, long levou) { this.npc = npc; this.levou = levou; x = npc.getX(); y = npc.getY(); z = npc.getZ(); }
    }
    private static final Map<UUID, Fuga> fugas = new ConcurrentHashMap<>();

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_ladrao.txt"); }

    @SubscribeEvent
    public void aoIniciar(ServerStartedEvent ev) {
        try {
            Path a = arquivo(ev.getServer());
            if (Files.exists(a)) porMil = Math.max(0, Math.min(1000, Integer.parseInt(Files.readString(a).trim())));
        } catch (Exception e) {
            FrxWorld.LOG.warn("Ladrão: não li frx_ladrao.txt: {}", e.toString());
        }
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        for (Fuga f : fugas.values()) f.npc.discard();
        fugas.clear();
    }

    @SubscribeEvent
    public void aoEntrarNoMundo(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || !e.getTags().contains(TAG)) return;
        for (Fuga f : fugas.values()) if (f.npc == e) return;
        ev.setCanceled(true);
    }

    private static boolean passavel(ServerLevel n, BlockPos b) {
        return n.getBlockState(b).getCollisionShape(n, b).isEmpty() && n.getFluidState(b).isEmpty();
    }

    /** Altura em que dá para pisar na coluna (x, z), perto de y; null se não der. */
    private static Double chao(ServerLevel n, double x, double y, double z) {
        for (int dy : new int[]{0, 1, -1, -2, -3}) {
            BlockPos b = BlockPos.containing(x, y + dy + 0.1, z);
            if (passavel(n, b) && passavel(n, b.above()) && !n.getBlockState(b.below()).getCollisionShape(n, b.below()).isEmpty()) return (double) b.getY();
        }
        return null;
    }

    private static boolean livre(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger() && p.onGround() && !p.isInWater()
                && p.tickCount - p.getLastHurtByMobTimestamp() > 200 && !fugas.containsKey(p.getUUID());
    }

    static boolean comecar(ServerPlayer p) {
        if (fugas.containsKey(p.getUUID())) return false;
        long bolsa = Moedas.contar(p);
        long levou = bolsa >= 10 ? 10 : bolsa >= 1 ? 1 : 0;   // uma moeda: de prata se tiver, senão de cobre
        if (levou == 0) return false;
        ServerLevel nivel = p.serverLevel();
        Vec3 atras = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
        Double y = chao(nivel, atras.x, p.getY(), atras.z);
        if (y == null) return false;
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse("guardvillagers:guard"));
        Entity npc = (tipo != null ? tipo : EntityType.VILLAGER).create(nivel);
        if (npc == null || !Moedas.cobrar(p, levou)) return false;
        npc.moveTo(atras.x, y, atras.z, p.getYRot() + 180, 0);
        if (npc instanceof Mob m) {
            m.setNoAi(true);
            for (EquipmentSlot s : EquipmentSlot.values()) { m.setItemSlot(s, ItemStack.EMPTY); m.setDropChance(s, 0f); }
            m.setItemSlot(EquipmentSlot.MAINHAND, levou >= 10 ? Moedas.prata(1) : Moedas.cobre(1));
        }
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.addTag(TAG);
        npc.setCustomName(Component.literal("Ladrão").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        npc.setCustomNameVisible(true);
        fugas.put(p.getUUID(), new Fuga(npc, levou));
        nivel.addFreshEntity(npc);
        nivel.sendParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + 1, npc.getZ(), 15, 0.3, 0.6, 0.3, 0.05);
        nivel.playSound(null, p.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f, 1.4f);
        p.sendSystemMessage(Component.literal("🪙 Um ladrão levou " + Moedas.texto(levou) + " da sua bolsa! Corra atrás dele: se pegar em 15 s, ele devolve dez vezes.").withStyle(ChatFormatting.RED));
        p.displayClientMessage(Component.literal("PEGA LADRÃO!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return true;
    }

    private static void fim(ServerPlayer p, Fuga f, boolean pegou) {
        fugas.values().remove(f);
        if (f.npc.level() instanceof ServerLevel nivel) {
            nivel.sendParticles(ParticleTypes.POOF, f.npc.getX(), f.npc.getY() + 1, f.npc.getZ(), 25, 0.3, 0.6, 0.3, 0.05);
            nivel.playSound(null, f.npc.blockPosition(), pegou ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1f, pegou ? 1.4f : 0.6f);
        }
        f.npc.discard();
        if (p == null) return;
        if (pegou) {
            Moedas.dar(p, f.levou * 10);
            Conquistas.somar(p, "ladroes", 1);
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("🪙 " + p.getGameProfile().getName() + " pegou o ladrão e recebeu " + Moedas.texto(f.levou * 10) + "!").withStyle(ChatFormatting.GOLD), false);
        } else {
            p.sendSystemMessage(Component.literal("🪙 O ladrão sumiu com a sua moeda. Da próxima vez, corra mais.").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void quadro(ServerPlayer p, Fuga f) {
        ServerLevel nivel = p.serverLevel();
        f.t++;
        double dist = Math.sqrt(Math.pow(f.x - p.getX(), 2) + Math.pow(f.z - p.getZ(), 2));
        if (dist < 1.4 && Math.abs(f.y - p.getY()) < 2.5) { fim(p, f, true); return; }
        if (f.t > DURACAO || dist > 60) { fim(p, f, false); return; }
        if (f.t < 12) return;   // meio segundo parado, para o jogador ver quem foi
        double fuga = Math.atan2(f.z - p.getZ(), f.x - p.getX()) + Math.sin(f.t * 0.2) * 0.7;
        for (double desvio : new double[]{0, 0.7, -0.7, 1.4, -1.4, 2.2, -2.2, Math.PI}) {
            double ang = fuga + desvio, nx = f.x + Math.cos(ang) * PASSO, nz = f.z + Math.sin(ang) * PASSO;
            Double ny = chao(nivel, nx, f.y, nz);
            if (ny == null) continue;
            f.x = nx; f.z = nz; f.y = ny;
            float yaw = (float) Math.toDegrees(ang) - 90;
            f.npc.moveTo(f.x, f.y, f.z, yaw, 0);
            f.npc.setYHeadRot(yaw);
            f.npc.setYBodyRot(yaw);
            break;
        }
        if (f.t % 5 == 0) nivel.sendParticles(ParticleTypes.CLOUD, f.x, f.y + 0.1, f.z, 2, 0.1, 0.02, 0.1, 0.01);
        if (f.t % 60 == 0) p.displayClientMessage(Component.literal("Ladrão fugindo! " + (DURACAO - f.t) / 20 + " s").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public void aoBater(AttackEntityEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !ev.getTarget().getTags().contains(TAG)) return;
        ev.setCanceled(true);
        for (Map.Entry<UUID, Fuga> e : fugas.entrySet()) {
            if (e.getValue().npc != ev.getTarget()) continue;
            ServerPlayer dono = p.server.getPlayerList().getPlayer(e.getKey());
            fim(dono != null ? dono : p, e.getValue(), true);   // amigo que pega devolve para o dono
            return;
        }
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ev.getServer();
        for (Iterator<Map.Entry<UUID, Fuga>> it = fugas.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Fuga> ent = it.next();
            Fuga f = ent.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(ent.getKey());
            try {
                if (p == null || !p.isAlive() || p.level() != f.npc.level()) fim(null, f, false);
                else quadro(p, f);
            } catch (Exception e) {
                FrxWorld.LOG.warn("Ladrão: fuga falhou: {}", e.toString());
                try { fim(null, f, false); } catch (Exception ignorado) { fugas.values().remove(f); }
            }
        }
        if (porMil > 0 && server.getTickCount() % 1200 == 900) {
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (ThreadLocalRandom.current().nextInt(1000) < porMil && livre(p)) comecar(p);
        }
    }

    private static int chamar(CommandSourceStack fonte, ServerPlayer p) {
        boolean ok = comecar(p);
        fonte.sendSuccess(() -> Component.literal(ok ? "Ladrão solto em " + p.getGameProfile().getName() + "." : "Não deu: o jogador não tem moedas na bolsa, não há espaço atrás dele, ou já há um ladrão."), false);
        return ok ? 1 : 0;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("ladrao")
                        .executes(c -> chamar(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.literal("chance").then(Commands.argument("porMil", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                            porMil = IntegerArgumentType.getInteger(c, "porMil");
                            try { Files.writeString(arquivo(c.getSource().getServer()), String.valueOf(porMil)); } catch (Exception ignorado) { }
                            c.getSource().sendSuccess(() -> Component.literal("Chance do ladrão: " + porMil + " em 1000 por minuto, por jogador."), true);
                            return 1;
                        })))
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(c -> chamar(c.getSource(), EntityArgument.getPlayer(c, "jogador"))))));
    }
}
