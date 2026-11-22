package br.com.frxcode.frxworld.eventos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.reinos.Reputacao;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evento raro do Papai Noel, no mesmo sorteio do Ronaldinho: uma vez por minuto cada jogador tem "chance por mil"
 * de ser o escolhido (frx_noel.txt, padrão 4 = 0,4%). Duas versões, sorteadas:
 * - Passagem do trenó (70%): o trenó com as renas passa rasante no céu, dá a volta e pousa perto do jogador; o Noel confere
 *   a lista (Hostil ou procurado em algum reino = carvão; senão, Caixa de Presente), decola e deixa cair presentes em volta.
 * - Noel perdido (30%): as renas fugiram; o jogador tem 5 minutos para encostar nas 3 renas espalhadas por perto.
 *   Achou todas: 3 presentes e 20 ouro.
 * A skin vem do painel (NPCs → "Papai Noel"), papel "noel". As renas são cervos do Twilight Forest (ou cabras).
 */
public class Noel {
    static final String TAG = "frx_noel";
    private static final int VOO = 70, POUSO = 50, CONVERSA = 70, PARTIDA = 60;
    private static final long PERDIDO_MS = 5 * 60_000L;
    private static int porMil = 4;

    private static final class Cena {
        final boolean perdido;
        final Entity noel;
        Entity treno;
        final List<Entity> renas = new ArrayList<>();
        Vec3 inicio, fim, pouso;
        int t, achadas;
        long limite;
        Cena(boolean perdido, Entity noel) { this.perdido = perdido; this.noel = noel; }
        List<Entity> todos() { List<Entity> l = new ArrayList<>(renas); l.add(noel); if (treno != null) l.add(treno); return l; }
    }
    private static final Map<UUID, Cena> cenas = new ConcurrentHashMap<>();

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_noel.txt"); }

    @SubscribeEvent
    public void aoIniciar(ServerStartedEvent ev) {
        try {
            Path a = arquivo(ev.getServer());
            if (Files.exists(a)) porMil = Math.max(0, Math.min(1000, Integer.parseInt(Files.readString(a).trim())));
        } catch (Exception e) { FrxWorld.LOG.warn("Noel: não li frx_noel.txt: {}", e.toString()); }
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        for (Cena c : cenas.values()) for (Entity e : c.todos()) e.discard();
        cenas.clear();
    }

    /** Sobrou de uma queda do servidor: some ao carregar. */
    @SubscribeEvent
    public void aoEntrarNoMundo(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || !e.getTags().contains(TAG)) return;
        for (Cena c : cenas.values()) if (c.todos().contains(e)) return;
        ev.setCanceled(true);
    }

    // ------------------------------------------------------------------ peças da cena

    private static Entity noel(ServerLevel n) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse("guardvillagers:guard"));
        Entity e = (tipo != null ? tipo : EntityType.VILLAGER).create(n);
        if (e == null) return null;
        if (e instanceof Mob m) {
            m.setNoAi(true);
            for (EquipmentSlot s : EquipmentSlot.values()) { m.setItemSlot(s, ItemStack.EMPTY); m.setDropChance(s, 0f); }
        }
        e.setInvulnerable(true);
        e.setSilent(true);
        e.addTag(TAG);
        e.getPersistentData().putString("frx_papel", "noel");
        e.getPersistentData().putString("frx_reino", "");
        e.setCustomName(Component.literal("Papai Noel").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        e.setCustomNameVisible(true);
        return e;
    }

    private static Entity rena(ServerLevel n, boolean voa) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse("twilightforest:deer"));
        Entity e = (tipo != null && tipo != EntityType.PIG ? tipo : EntityType.GOAT).create(n);
        if (e == null) return null;
        if (e instanceof Mob m) { m.setNoAi(voa); m.setPersistenceRequired(); }
        e.setInvulnerable(true);
        e.setNoGravity(voa);
        e.setSilent(true);
        e.addTag(TAG);
        e.setCustomName(Component.literal("Rena do Noel").withStyle(ChatFormatting.GOLD));
        e.setCustomNameVisible(!voa);
        return e;
    }

    private static boolean livre(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger() && p.onGround() && !p.isInWater()
                && p.level() == p.server.overworld() && p.level().canSeeSky(p.blockPosition().above())
                && p.tickCount - p.getLastHurtByMobTimestamp() > 200 && !cenas.containsKey(p.getUUID());
    }

    static boolean comecar(ServerPlayer p, Boolean perdido) {
        if (cenas.containsKey(p.getUUID())) return false;
        boolean versao = perdido != null ? perdido : ThreadLocalRandom.current().nextInt(100) < 30;
        return versao ? comecarPerdido(p) : comecarTreno(p);
    }

    private static boolean comecarTreno(ServerPlayer p) {
        ServerLevel n = p.serverLevel();
        double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
        Entity noel = noel(n);
        Entity treno = EntityType.MINECART.create(n);
        if (noel == null || treno == null) return false;
        Cena c = new Cena(false, noel);
        c.inicio = p.position().add(dir.scale(-45)).add(0, 16, 0);
        c.fim = p.position().add(dir.scale(30)).add(0, 12, 0);
        // pouso: um lugar no chão a ~5 blocos do jogador
        double a2 = ang + Math.PI / 2;
        int lx = (int) Math.floor(p.getX() + Math.cos(a2) * 5), lz = (int) Math.floor(p.getZ() + Math.sin(a2) * 5);
        int ly = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
        if (Math.abs(ly - p.getBlockY()) > 4) { lx = p.getBlockX(); lz = p.getBlockZ() + 3; ly = p.getBlockY(); }
        c.pouso = new Vec3(lx + 0.5, ly, lz + 0.5);
        treno.setNoGravity(true);
        treno.setInvulnerable(true);
        treno.addTag(TAG);
        treno.moveTo(c.inicio.x, c.inicio.y, c.inicio.z, 0, 0);
        noel.moveTo(c.inicio.x, c.inicio.y, c.inicio.z, 0, 0);
        n.addFreshEntity(treno);
        n.addFreshEntity(noel);
        noel.startRiding(treno, true);
        c.treno = treno;
        for (int i = 0; i < 4; i++) {
            Entity r = rena(n, true);
            if (r == null) continue;
            r.moveTo(c.inicio.x, c.inicio.y, c.inicio.z, 0, 0);
            n.addFreshEntity(r);
            c.renas.add(r);
        }
        cenas.put(p.getUUID(), c);
        p.displayClientMessage(Component.literal("🔔 Sinos no céu... é um trenó?!").withStyle(ChatFormatting.RED), true);
        n.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.NEUTRAL, 1.5f, 1.2f);
        return true;
    }

    private static boolean comecarPerdido(ServerPlayer p) {
        ServerLevel n = p.serverLevel();
        double ang = Math.toRadians(p.getYRot() + 90);
        int x = (int) Math.floor(p.getX() + Math.cos(ang) * 4), z = (int) Math.floor(p.getZ() + Math.sin(ang) * 4);
        int y = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - p.getBlockY()) > 3) return false;
        Entity noel = noel(n);
        if (noel == null) return false;
        Cena c = new Cena(true, noel);
        noel.moveTo(x + 0.5, y, z + 0.5, 0, 0);
        n.addFreshEntity(noel);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            Entity r = rena(n, false);
            if (r == null) continue;
            for (int tentativa = 0; tentativa < 12; tentativa++) {
                double a = rnd.nextDouble(Math.PI * 2), d = 15 + rnd.nextDouble(20);
                int rx = (int) Math.floor(p.getX() + Math.cos(a) * d), rz = (int) Math.floor(p.getZ() + Math.sin(a) * d);
                int ry = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rx, rz);
                if (Math.abs(ry - p.getBlockY()) <= 12 && !n.getBlockState(new BlockPos(rx, ry - 1, rz)).liquid()) { r.moveTo(rx + 0.5, ry, rz + 0.5, 0, 0); break; }
            }
            if (r.position().equals(Vec3.ZERO)) r.moveTo(p.getX() + 15, p.getY(), p.getZ(), 0, 0);
            if (r instanceof net.minecraft.world.entity.LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (PERDIDO_MS / 50) + 200, 0, false, false));
            n.addFreshEntity(r);
            c.renas.add(r);
        }
        c.limite = System.currentTimeMillis() + PERDIDO_MS;
        cenas.put(p.getUUID(), c);
        n.sendParticles(ParticleTypes.SNOWFLAKE, noel.getX(), noel.getY() + 1, noel.getZ(), 40, 0.4, 0.8, 0.4, 0.02);
        n.playSound(null, noel.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.NEUTRAL, 1.2f, 0.9f);
        dizer(p, "Ho ho... ai, ai. Minhas renas fugiram! Você me ajuda a achar as 3 antes de 5 minutos? Elas estão brilhando por aqui perto.");
        return true;
    }

    private static void dizer(ServerPlayer p, String fala) {
        p.sendSystemMessage(Component.literal("🎅 Papai Noel: ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(fala).withStyle(ChatFormatting.WHITE)));
    }

    // ------------------------------------------------------------------ passagem do trenó

    private static void posicionarTreno(Cena c, Vec3 pos, Vec3 frente, ServerLevel n) {
        float yaw = (float) Math.toDegrees(Math.atan2(frente.z, frente.x)) - 90;
        c.treno.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        for (int i = 0; i < c.renas.size(); i++) {
            int fila = i / 2, lado = i % 2 == 0 ? 1 : -1;
            Vec3 lateral = new Vec3(-frente.z, 0, frente.x).scale(0.7 * lado);
            Vec3 rp = pos.add(frente.scale(2.2 + fila * 1.8)).add(lateral).add(0, 0.2, 0);
            Entity r = c.renas.get(i);
            r.moveTo(rp.x, rp.y, rp.z, yaw, 0);
            r.setYHeadRot(yaw);
            r.setYBodyRot(yaw);
        }
        if (c.t % 2 == 0) n.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.5, pos.z, 6, 0.6, 0.2, 0.6, 0.01);
        if (c.t % 4 == 0) n.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 2, 0.3, 0.1, 0.3, 0.02);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double f) { return a.add(b.subtract(a).scale(f)); }

    private static void quadroTreno(ServerPlayer p, Cena c) {
        ServerLevel n = p.serverLevel();
        int t = c.t++;
        if (t < VOO) {   // passagem rasante
            double f = (double) t / VOO;
            Vec3 pos = lerp(c.inicio, c.fim, f).add(0, -Math.sin(f * Math.PI) * 6, 0);
            posicionarTreno(c, pos, c.fim.subtract(c.inicio).normalize(), n);
            if (t % 10 == 0) n.playSound(null, BlockPos.containing(pos), SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.NEUTRAL, 2f, 1f + (t % 20 == 0 ? 0.2f : 0f));
            if (t == VOO / 2) p.server.getPlayerList().broadcastSystemMessage(Component.literal("🎅 Ho ho ho! O trenó do Papai Noel está passando por cima de " + p.getGameProfile().getName() + "!").withStyle(ChatFormatting.RED), false);
        } else if (t < VOO + POUSO) {   // dá a volta e pousa
            double f = (double) (t - VOO) / POUSO;
            Vec3 alto = c.pouso.add(0, 8, 0);
            Vec3 pos = f < 0.5 ? lerp(c.fim, alto, f * 2) : lerp(alto, c.pouso, (f - 0.5) * 2);
            Vec3 frente = (f < 0.5 ? alto.subtract(c.fim) : p.position().subtract(c.pouso)).multiply(1, 0, 1);
            posicionarTreno(c, pos, frente.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : frente.normalize(), n);
        } else if (t < VOO + POUSO + CONVERSA) {   // conversa no chão
            int k = t - VOO - POUSO;
            if (k == 0) {
                c.noel.stopRiding();
                c.noel.moveTo(c.pouso.x + 1, c.pouso.y, c.pouso.z, 0, 0);
                n.sendParticles(ParticleTypes.SNOWFLAKE, c.pouso.x, c.pouso.y + 1, c.pouso.z, 40, 0.6, 0.6, 0.6, 0.02);
                dizer(p, "Ho ho ho, " + p.getGameProfile().getName() + "! Deixa eu ver a minha lista...");
            }
            olhar(c.noel, p);
            if (k == 30) {
                if (Reputacao.piorHostilidade(p) >= 2) {
                    ItemStack carvao = new ItemStack(Items.COAL);
                    carvao.setHoverName(Component.literal("Carvão do Papai Noel").withStyle(ChatFormatting.DARK_GRAY));
                    dar(p, carvao);
                    dizer(p, "Ho ho... não. Tem cartaz com a sua cara por aí. Este ano é carvão, " + p.getGameProfile().getName() + ".");
                    n.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 0.6f);
                } else {
                    dar(p, ItensNatal.presente());
                    dizer(p, "Você está na lista dos bonzinhos! Feliz Natal! Clique no presente com ele na mão para abrir.");
                    n.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1.4f);
                }
            }
            if (k == CONVERSA - 5) { c.noel.startRiding(c.treno, true); dizer(p, "Tenho muitas casas para visitar. Ho ho ho!"); }
        } else if (t < VOO + POUSO + CONVERSA + PARTIDA) {   // decola, deixando presentes caírem
            int k = t - VOO - POUSO - CONVERSA;
            double f = (double) k / PARTIDA;
            Vec3 saida = c.pouso.add(c.fim.subtract(c.inicio).normalize().scale(60)).add(0, 40, 0);
            Vec3 pos = lerp(c.pouso, saida, f * f);
            posicionarTreno(c, pos, saida.subtract(c.pouso).multiply(1, 0, 1).normalize(), n);
            if (k > 5 && k < 40 && k % 7 == 0) {   // chuva de presentes
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                double a = rnd.nextDouble(Math.PI * 2), d = 2 + rnd.nextDouble(7);
                ItemEntity ie = new ItemEntity(n, p.getX() + Math.cos(a) * d, p.getY() + 8, p.getZ() + Math.sin(a) * d, ItensNatal.presente());
                ie.setDefaultPickUpDelay();
                n.addFreshEntity(ie);
            }
        } else fim(p, c, true);
    }

    private static void olhar(Entity e, ServerPlayer p) {
        double dx = p.getX() - e.getX(), dz = p.getZ() - e.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        if (e instanceof net.minecraft.world.entity.LivingEntity le) le.setYBodyRot(yaw);
    }

    private static void dar(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    // ------------------------------------------------------------------ Noel perdido

    private static void quadroPerdido(ServerPlayer p, Cena c) {
        ServerLevel n = p.serverLevel();
        c.t++;
        olhar(c.noel, p);
        for (Entity r : List.copyOf(c.renas)) {
            if (!r.isAlive()) { c.renas.remove(r); continue; }
            if (r.distanceToSqr(p) > 2.5 * 2.5) continue;
            c.renas.remove(r);
            c.achadas++;
            n.sendParticles(ParticleTypes.SNOWFLAKE, r.getX(), r.getY() + 1, r.getZ(), 30, 0.4, 0.5, 0.4, 0.02);
            n.playSound(null, r.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.NEUTRAL, 1.2f, 1.2f + c.achadas * 0.1f);
            r.discard();
            p.displayClientMessage(Component.literal("🦌 Rena encontrada! " + c.achadas + " de 3").withStyle(ChatFormatting.GOLD), true);
        }
        if (c.achadas >= 3) {
            dizer(p, "Ho ho ho! Todas de volta! Muito obrigado, " + p.getGameProfile().getName() + ". Isto é para você.");
            for (int i = 0; i < 3; i++) dar(p, ItensNatal.presente());
            Moedas.dar(p, 2000);
            n.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("🎅 " + p.getGameProfile().getName() + " achou as renas fujonas do Papai Noel e ganhou presentes!").withStyle(ChatFormatting.RED), false);
            fim(p, c, true);
            return;
        }
        if (System.currentTimeMillis() > c.limite || p.distanceToSqr(c.noel) > 90 * 90) {
            dizer(p, "Bom... vou procurar sozinho. Ho ho... ho.");
            fim(p, c, false);
            return;
        }
        if (c.t % 1200 == 0) {
            long resta = Math.max(0, (c.limite - System.currentTimeMillis()) / 60_000 + 1);
            p.displayClientMessage(Component.literal("🦌 Renas: " + c.achadas + " de 3 · ~" + resta + " min").withStyle(ChatFormatting.GOLD), true);
        }
    }

    // ------------------------------------------------------------------ fim

    private static void fim(ServerPlayer p, Cena c, boolean completo) {
        cenas.values().remove(c);
        for (Entity e : c.todos()) {
            if (e.level() instanceof ServerLevel n) n.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.8, e.getZ(), 12, 0.3, 0.4, 0.3, 0.03);
            e.discard();
        }
        if (completo && p != null && !c.perdido)
            FrxWorld.LOG.info("Noel: passagem do trenó para {}.", p.getGameProfile().getName());
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
                if (p == null || !p.isAlive() || p.level() != c.noel.level()) fim(null, c, false);
                else if (c.perdido) quadroPerdido(p, c);
                else quadroTreno(p, c);
            } catch (Exception e) {
                FrxWorld.LOG.warn("Noel: cena falhou: {}", e.toString());
                try { fim(null, c, false); } catch (Exception ignorado) { cenas.values().remove(c); }
            }
        }
        if (porMil > 0 && server.getTickCount() % 1200 == 300) {
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (ThreadLocalRandom.current().nextInt(1000) < porMil && livre(p)) comecar(p, null);
        }
    }

    // ------------------------------------------------------------------ comandos

    private static int chamar(CommandSourceStack fonte, ServerPlayer p, Boolean perdido) {
        boolean ok = comecar(p, perdido);
        fonte.sendSuccess(() -> Component.literal(ok ? "Papai Noel a caminho de " + p.getGameProfile().getName() + "."
                : "Não deu: o jogador precisa estar ao ar livre, no chão, sem outra cena do Noel."), false);
        return ok ? 1 : 0;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("noel")
                        .executes(c -> chamar(c.getSource(), c.getSource().getPlayerOrException(), null))
                        .then(Commands.literal("treno").executes(c -> chamar(c.getSource(), c.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("perdido").executes(c -> chamar(c.getSource(), c.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("chance")
                                .executes(c -> {
                                    c.getSource().sendSuccess(() -> Component.literal("Papai Noel: " + porMil + " em 1000 por minuto, por jogador."), false);
                                    return 1;
                                })
                                .then(Commands.argument("porMil", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                                    porMil = IntegerArgumentType.getInteger(c, "porMil");
                                    try { Files.writeString(arquivo(c.getSource().getServer()), String.valueOf(porMil)); } catch (Exception e) { FrxWorld.LOG.warn("Noel: não gravei a chance: {}", e.toString()); }
                                    c.getSource().sendSuccess(() -> Component.literal("Chance do Papai Noel: " + porMil + " em 1000 por minuto, por jogador."), true);
                                    return 1;
                                })))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(c -> chamar(c.getSource(), EntityArgument.getPlayer(c, "jogador"), null)))));
    }
}
