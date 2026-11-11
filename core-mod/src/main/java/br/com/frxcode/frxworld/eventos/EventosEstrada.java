package br.com.frxcode.frxworld.eventos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Eventos de estrada (aba do painel): enquanto o jogador viaja, sorteia pelo nível dele uma emboscada,
 * um chefe errante, um mercador raro ou um tesouro. Vencer o evento paga XP e loot a quem estiver perto.
 */
public class EventosEstrada {
    private static final double RAIO_PREMIO = 48, RAIO_FUGA = 96;
    private static final int TICKS_LIMITE = 20 * 60 * 3, TICKS_TESOURO = 20 * 60 * 10;
    public static final String TAG = "frx_evento";

    record Loot(String item, int qtd, double chance) {}
    record Def(String nome, String tipo, String mensagem, int peso, int nivelMin, int nivelMax, String mobTipo, int mobQtd,
               int vida, double dano, int preco, long xp, List<Loot> loot) {}
    record Cfg(boolean ativo, int checagemSegundos, double chance, int recargaMinutos, int distanciaSpawn, int andarMinimo) {}

    private static volatile Cfg cfg = new Cfg(true, 90, 25, 8, 150, 48);
    private static volatile List<Def> eventos = new ArrayList<>();

    /** Evento em andamento (um por jogador). */
    private static final class Ativo {
        final Def def;
        final ServerLevel nivel;
        final Vec3 origem;
        final Set<UUID> inimigos = new HashSet<>();
        UUID chefe;
        ServerBossEvent barra;
        BlockPos tesouro;
        int ticks = 0;

        Ativo(Def def, ServerLevel nivel, Vec3 origem) { this.def = def; this.nivel = nivel; this.origem = origem; }
    }

    private static final Map<UUID, Ativo> ativos = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> ultimaPosicao = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ultimoEvento = new ConcurrentHashMap<>();
    private static int ticks = 0;

    // ------------------------------------------------------------------ painel

    public static void recarregar() {
        PainelApi.lerEventos().whenComplete((json, erro) -> {
            if (erro != null) {
                FrxWorld.LOG.warn("Não consegui ler os eventos de estrada do painel: {}", erro.getMessage());
                return;
            }
            try {
                JsonObject c = json.getAsJsonObject("config");
                Cfg novaCfg = new Cfg(c.get("ativo").getAsBoolean(), Math.max(20, c.get("checagemSegundos").getAsInt()),
                        c.get("chance").getAsDouble(), c.get("recargaMinutos").getAsInt(), c.get("distanciaSpawn").getAsInt(),
                        c.get("andarMinimo").getAsInt());
                List<Def> lista = new ArrayList<>();
                for (JsonElement e : json.getAsJsonArray("eventos")) {
                    JsonObject o = e.getAsJsonObject();
                    List<Loot> loot = new ArrayList<>();
                    for (JsonElement l : o.getAsJsonArray("loot")) {
                        JsonObject x = l.getAsJsonObject();
                        loot.add(new Loot(x.get("item").getAsString(), x.get("qtd").getAsInt(), x.get("chance").getAsDouble()));
                    }
                    lista.add(new Def(o.get("nome").getAsString(), o.get("tipo").getAsString(), o.get("mensagem").getAsString(),
                            o.get("peso").getAsInt(), o.get("nivelMin").getAsInt(), o.get("nivelMax").getAsInt(),
                            o.get("mobTipo").getAsString(), o.get("mobQtd").getAsInt(), o.get("vida").getAsInt(),
                            o.get("dano").getAsDouble(), o.get("preco").getAsInt(), o.get("recompensaXp").getAsLong(), loot));
                }
                cfg = novaCfg;
                eventos = lista;
            } catch (Exception ex) {
                FrxWorld.LOG.warn("Resposta inválida de /api/servidor/eventos: {}", ex.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ sorteio

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END) return;
        MinecraftServer server = evento.getServer();
        ticks++;
        if (ticks % 20 == 0) manter();
        Cfg c = cfg;
        if (!c.ativo() || ticks % (c.checagemSegundos() * 20) != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try { checar(p, c); } catch (Exception ex) { FrxWorld.LOG.warn("Evento de estrada falhou para {}: {}", p.getScoreboardName(), ex.toString()); }
        }
    }

    private static void checar(ServerPlayer p, Cfg c) {
        Vec3 agora = p.position();
        Vec3 antes = ultimaPosicao.put(p.getUUID(), agora);
        if (antes == null || !p.isAlive() || p.isSpectator() || p.isCreative() || !Progressao.temDados(p)) return;
        if (p.level().dimension() != Level.OVERWORLD || ativos.containsKey(p.getUUID())) return;
        if (Math.hypot(agora.x - antes.x, agora.z - antes.z) < c.andarMinimo()) return;
        BlockPos spawn = p.serverLevel().getSharedSpawnPos();
        if (Math.hypot(agora.x - spawn.getX(), agora.z - spawn.getZ()) < c.distanciaSpawn()) return;
        Long ultimo = ultimoEvento.get(p.getUUID());
        if (ultimo != null && System.currentTimeMillis() - ultimo < c.recargaMinutos() * 60_000L) return;
        if (ThreadLocalRandom.current().nextDouble(100) >= c.chance()) return;
        Def d = sortear(p);
        if (d != null) iniciar(p, d);
    }

    private static Def sortear(ServerPlayer p) {
        int nivel = Progressao.dados(p).nivel();
        List<Def> possiveis = new ArrayList<>();
        int total = 0;
        for (Def d : eventos) {
            if (nivel < d.nivelMin() || nivel > d.nivelMax() || d.peso() <= 0) continue;
            possiveis.add(d);
            total += d.peso();
        }
        if (total == 0) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (Def d : possiveis) {
            r -= d.peso();
            if (r < 0) return d;
        }
        return null;
    }

    // ------------------------------------------------------------------ início

    private static boolean iniciar(ServerPlayer p, Def d) {
        ServerLevel nivel = p.serverLevel();
        Ativo a = new Ativo(d, nivel, p.position());
        boolean ok = switch (d.tipo()) {
            case "EMBOSCADA" -> emboscada(p, a);
            case "CHEFE" -> chefe(p, a);
            case "MERCADOR" -> mercador(p, a);
            case "TESOURO" -> tesouro(p, a);
            default -> false;
        };
        if (!ok) return false;
        ultimoEvento.put(p.getUUID(), System.currentTimeMillis());
        if (!d.tipo().equals("MERCADOR")) ativos.put(p.getUUID(), a);

        ChatFormatting cor = switch (d.tipo()) {
            case "MERCADOR" -> ChatFormatting.GREEN;
            case "TESOURO" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(d.nome()).withStyle(cor, ChatFormatting.BOLD)));
        if (!d.mensagem().isBlank()) {
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(d.mensagem()).withStyle(ChatFormatting.GRAY)));
            p.sendSystemMessage(Component.literal("[" + d.nome() + "] ").withStyle(cor).append(Component.literal(d.mensagem()).withStyle(ChatFormatting.GRAY)));
        }
        nivel.playSound(null, p.blockPosition(), d.tipo().equals("MERCADOR") || d.tipo().equals("TESOURO")
                ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2f, 1f);
        return true;
    }

    private static Mob criarMob(ServerLevel nivel, String tipo, BlockPos onde) {
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(tipo));
        if (t == null) return null;
        Entity e = t.create(nivel);
        if (!(e instanceof Mob m)) { if (e != null) e.discard(); return null; }
        m.moveTo(onde.getX() + 0.5, onde.getY(), onde.getZ() + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(onde), MobSpawnType.EVENT, null, null);
        m.setPersistenceRequired();
        m.addTag(TAG);
        if (m instanceof Enemy && m.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            m.setDropChance(EquipmentSlot.HEAD, 0f);
        }
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, TICKS_LIMITE, 0, false, false));
        return m;
    }

    private static void ajustar(Mob m, int vida, double dano) {
        if (vida > 0) {
            AttributeInstance a = m.getAttribute(Attributes.MAX_HEALTH);
            if (a != null) { a.setBaseValue(Math.min(1024, vida)); m.setHealth(m.getMaxHealth()); }
        }
        if (dano > 0) {
            AttributeInstance a = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (a != null) a.setBaseValue(dano);
        }
    }

    private static boolean emboscada(ServerPlayer p, Ativo a) {
        Def d = a.def;
        for (int i = 0; i < Math.max(1, d.mobQtd()); i++) {
            Mob m = criarMob(a.nivel, d.mobTipo(), lugar(a.nivel, p, 9, 15));
            if (m == null) break;
            ajustar(m, d.vida(), d.dano());
            m.setTarget(p);
            a.nivel.addFreshEntity(m);
            a.inimigos.add(m.getUUID());
        }
        return !a.inimigos.isEmpty();
    }

    private static boolean chefe(ServerPlayer p, Ativo a) {
        Def d = a.def;
        Mob boss = criarMob(a.nivel, d.mobTipo(), lugar(a.nivel, p, 10, 14));
        if (boss == null) return false;
        ajustar(boss, d.vida() > 0 ? d.vida() : 150, d.dano());
        AttributeInstance kb = boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.6);
        boss.setCustomName(Component.literal(d.nome()).withStyle(ChatFormatting.DARK_RED));
        boss.setCustomNameVisible(true);
        boss.setTarget(p);
        a.nivel.addFreshEntity(boss);
        a.chefe = boss.getUUID();
        a.inimigos.add(boss.getUUID());
        a.barra = new ServerBossEvent(Component.literal(d.nome()).withStyle(ChatFormatting.DARK_RED),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        for (int i = 0; i < d.mobQtd(); i++) {
            Mob m = criarMob(a.nivel, d.mobTipo(), lugar(a.nivel, p, 8, 14));
            if (m == null) break;
            m.setTarget(p);
            a.nivel.addFreshEntity(m);
            a.inimigos.add(m.getUUID());
        }
        return true;
    }

    private static boolean mercador(ServerPlayer p, Ativo a) {
        Def d = a.def;
        WanderingTrader t = EntityType.WANDERING_TRADER.create(a.nivel);
        if (t == null) return false;
        BlockPos onde = lugar(a.nivel, p, 4, 7);
        t.moveTo(onde.getX() + 0.5, onde.getY(), onde.getZ() + 0.5, 0f, 0f);
        t.setCustomName(Component.literal(d.nome()).withStyle(ChatFormatting.GREEN));
        t.setCustomNameVisible(true);
        t.setDespawnDelay(20 * 60 * 5);
        t.addTag(TAG);
        t.getOffers().clear();
        int preco = Math.max(1, Math.min(64, d.preco()));
        for (Loot l : d.loot()) {
            if (ThreadLocalRandom.current().nextDouble(100) >= l.chance()) continue;
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(l.item()));
            if (item == null) continue;
            t.getOffers().add(new MerchantOffer(new ItemStack(Items.EMERALD, preco),
                    new ItemStack(item, Math.min(l.qtd(), item.getMaxStackSize())), 3, 0, 0.05f));
        }
        if (t.getOffers().isEmpty()) { t.discard(); return false; }
        a.nivel.addFreshEntity(t);
        return true;
    }

    private static boolean tesouro(ServerPlayer p, Ativo a) {
        BlockPos onde = lugar(a.nivel, p, 18, 30);
        a.nivel.setBlock(onde, Blocks.CHEST.defaultBlockState(), 3);
        if (!(a.nivel.getBlockEntity(onde) instanceof ChestBlockEntity bau)) return false;
        int slot = 0;
        for (Loot l : a.def.loot()) {
            if (ThreadLocalRandom.current().nextDouble(100) >= l.chance()) continue;
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(l.item()));
            if (item == null || slot >= bau.getContainerSize()) continue;
            bau.setItem(slot, new ItemStack(item, Math.min(l.qtd(), item.getMaxStackSize())));
            slot += 1 + ThreadLocalRandom.current().nextInt(3);
        }
        LightningBolt raio = EntityType.LIGHTNING_BOLT.create(a.nivel);
        if (raio != null) {
            raio.moveTo(Vec3.atBottomCenterOf(onde));
            raio.setVisualOnly(true);
            a.nivel.addFreshEntity(raio);
        }
        a.tesouro = onde;
        p.sendSystemMessage(Component.literal("O tesouro está perto de " + onde.getX() + ", " + onde.getY() + ", " + onde.getZ() + ".").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** Ponto no chão a uma distância entre min e max do jogador (na superfície, ou na altura dele se estiver numa caverna). */
    private static BlockPos lugar(ServerLevel nivel, ServerPlayer p, int min, int max) {
        BlockPos base = p.blockPosition();
        boolean caverna = !nivel.canSeeSky(base) && nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ()) > base.getY() + 6;
        for (int i = 0; i < 12; i++) {
            double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            int dist = min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min + 1));
            int x = base.getX() + (int) Math.round(Math.cos(ang) * dist), z = base.getZ() + (int) Math.round(Math.sin(ang) * dist);
            if (!caverna) {
                BlockPos topo = nivel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (nivel.getFluidState(topo.below()).isEmpty() && Math.abs(topo.getY() - base.getY()) < 12) return topo;
            } else {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos c = new BlockPos(x, base.getY() + dy, z);
                    if (nivel.getBlockState(c).isAir() && nivel.getBlockState(c.above()).isAir() && nivel.getBlockState(c.below()).isSolid()) return c;
                }
            }
        }
        return base;
    }

    // ------------------------------------------------------------------ andamento

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent evento) {
        LivingEntity e = evento.getEntity();
        if (!e.getTags().contains(TAG)) return;
        for (Map.Entry<UUID, Ativo> ent : ativos.entrySet()) {
            Ativo a = ent.getValue();
            if (!a.inimigos.remove(e.getUUID())) continue;
            if (e.getUUID().equals(a.chefe) && a.barra != null) a.barra.setProgress(0);
            if (a.inimigos.isEmpty()) {
                premiar(a, "venceu");
                encerrar(ent.getKey(), a);
            }
            return;
        }
    }

    private static void manter() {
        for (Map.Entry<UUID, Ativo> ent : ativos.entrySet()) {
            Ativo a = ent.getValue();
            a.ticks += 20;
            ServerPlayer dono = a.nivel.getServer().getPlayerList().getPlayer(ent.getKey());

            if (a.tesouro != null) {
                boolean achou = false;
                for (ServerPlayer p : a.nivel.players()) {
                    if (p.blockPosition().distSqr(a.tesouro) <= 9) { achou = true; break; }
                }
                if (achou) { premiar(a, "achou"); encerrar(ent.getKey(), a); }
                else if (a.ticks > TICKS_TESOURO) encerrar(ent.getKey(), a);
                continue;
            }

            if (a.barra != null && a.chefe != null) {
                Entity b = a.nivel.getEntity(a.chefe);
                if (b instanceof LivingEntity vivo) a.barra.setProgress(Math.max(0f, vivo.getHealth() / vivo.getMaxHealth()));
                for (ServerPlayer p : a.nivel.players()) {
                    boolean perto = p.position().distanceToSqr(a.origem) < RAIO_FUGA * RAIO_FUGA;
                    if (perto) a.barra.addPlayer(p); else a.barra.removePlayer(p);
                }
            }
            boolean fugiu = dono == null || !dono.isAlive() || dono.level() != a.nivel || dono.position().distanceToSqr(a.origem) > RAIO_FUGA * RAIO_FUGA;
            if (fugiu || a.ticks > TICKS_LIMITE) {
                if (dono != null) dono.sendSystemMessage(Component.literal("[" + a.def.nome() + "] Os inimigos se dispersaram.").withStyle(ChatFormatting.GRAY));
                for (UUID id : a.inimigos) {
                    Entity m = a.nivel.getEntity(id);
                    if (m != null) m.discard();
                }
                encerrar(ent.getKey(), a);
            }
        }
    }

    private static void premiar(Ativo a, String como) {
        Def d = a.def;
        for (ServerPlayer p : a.nivel.players()) {
            if (!p.isAlive() || p.position().distanceToSqr(a.origem) > RAIO_PREMIO * RAIO_PREMIO * 4 || !Progressao.temDados(p)) continue;
            StringBuilder ganhou = new StringBuilder();
            if (d.xp() > 0) { Progressao.ganharXp(p, d.xp()); ganhou.append("+").append(d.xp()).append(" XP"); }
            if (!d.tipo().equals("TESOURO")) {
                for (Loot l : d.loot()) {
                    if (ThreadLocalRandom.current().nextDouble(100) >= l.chance()) continue;
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(l.item()));
                    if (item == null) continue;
                    ItemStack pilha = new ItemStack(item, Math.min(l.qtd(), item.getMaxStackSize()));
                    if (!p.getInventory().add(pilha)) p.drop(pilha, false);
                    if (!ganhou.isEmpty()) ganhou.append(" · ");
                    ganhou.append(l.qtd()).append("× ").append(new ItemStack(item).getHoverName().getString());
                }
            }
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(como.equals("achou") ? "Tesouro encontrado" : "Evento vencido").withStyle(ChatFormatting.GOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(d.nome()).withStyle(ChatFormatting.YELLOW)));
            if (!ganhou.isEmpty()) p.sendSystemMessage(Component.literal("[" + d.nome() + "] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(ganhou.toString()).withStyle(ChatFormatting.WHITE)));
            a.nivel.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    private static void encerrar(UUID dono, Ativo a) {
        if (a.barra != null) a.barra.removeAllPlayers();
        ativos.remove(dono);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent evento) {
        UUID id = evento.getEntity().getUUID();
        ultimaPosicao.remove(id);
    }

    // ------------------------------------------------------------------ comando de teste

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("evento")
                        .executes(ctx -> forcar(ctx.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                                .executes(ctx -> forcar(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nome"))))));
    }

    private static int forcar(ServerPlayer p, String nome) {
        if (ativos.containsKey(p.getUUID())) {
            p.sendSystemMessage(Component.literal("Você já está num evento.").withStyle(ChatFormatting.RED));
            return 0;
        }
        Def d = null;
        if (nome == null) d = sortear(p);
        else for (Def e : eventos) if (e.nome().equalsIgnoreCase(nome.trim())) { d = e; break; }
        if (d == null) {
            p.sendSystemMessage(Component.literal(nome == null ? "Nenhum evento para o seu nível (carregue as sugestões no painel)."
                    : "Evento não encontrado: " + nome).withStyle(ChatFormatting.RED));
            return 0;
        }
        return iniciar(p, d) ? 1 : 0;
    }
}
