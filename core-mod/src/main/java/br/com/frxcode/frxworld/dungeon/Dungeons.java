package br.com.frxcode.frxworld.dungeon;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.blocos.Blocos;
import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.google.gson.JsonElement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dungeons: o Núcleo de cada cripta escolhe a dungeon do painel pela distância do spawn,
 * desperta o guardião (boss com barra) e lacaios, e paga o prêmio final a quem estiver perto,
 * respeitando o tempo de recarga por jogador.
 */
public class Dungeons {
    public static final String TAG_BOSS = "frx_boss";
    private static final double RAIO_PARTICIPAR = 48, RAIO_BARRA = 56;
    private static final int TICKS_ABANDONO = 20 * 120;

    private static volatile List<DungeonDef> catalogo = new ArrayList<>();

    /** Dungeon em andamento num núcleo. */
    private static final class Ativa {
        final String chave;
        final ServerLevel nivel;
        final BlockPos nucleo;
        final DungeonDef def;
        final int andar;            // 0 = guardião final
        final UUID boss;
        final List<UUID> lacaios = new ArrayList<>();
        final ServerBossEvent barra;
        int ticksSemJogador = 0;

        Ativa(String chave, ServerLevel nivel, BlockPos nucleo, DungeonDef def, int andar, UUID boss, ServerBossEvent barra) {
            this.chave = chave; this.nivel = nivel; this.nucleo = nucleo; this.def = def; this.andar = andar; this.boss = boss; this.barra = barra;
        }
    }

    private static final Map<String, Ativa> ativas = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ catálogo

    public static void recarregar() {
        PainelApi.lerDungeons().whenComplete((arr, erro) -> {
            if (erro != null) {
                FrxWorld.LOG.warn("Não consegui ler as dungeons do painel: {}", erro.getMessage());
                return;
            }
            List<DungeonDef> novo = new ArrayList<>();
            for (JsonElement e : arr) {
                try { novo.add(DungeonDef.deJson(e.getAsJsonObject())); }
                catch (Exception ex) { FrxWorld.LOG.warn("Dungeon inválida ignorada: {}", e); }
            }
            catalogo = novo;
        });
    }

    private static String chave(ServerLevel nivel, BlockPos pos) {
        return nivel.dimension().location() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static DungeonDef escolher(ServerLevel nivel, BlockPos pos) {
        List<DungeonDef> lista = catalogo;
        if (lista.isEmpty()) return null;
        BlockPos spawn = nivel.getSharedSpawnPos();
        double dist = Math.sqrt(Math.pow(pos.getX() - spawn.getX(), 2) + Math.pow(pos.getZ() - spawn.getZ(), 2));
        for (DungeonDef d : lista) if (dist >= d.distanciaMin() && dist < d.distanciaMax()) return d;
        return lista.get(lista.size() - 1);
    }

    private static String itemNome(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        return item != null ? new ItemStack(item).getHoverName().getString() : id;
    }

    private static long recargaRestante(ServerPlayer p, String chave, DungeonDef d) {
        Long quando = Progressao.dados(p).dungeons().get(chave);
        if (quando == null) return 0;
        return Math.max(0, d.recargaHoras() * 3_600_000L - (System.currentTimeMillis() - quando));
    }

    private static String tempo(long ms) {
        long h = ms / 3_600_000L, m = (ms % 3_600_000L) / 60_000L;
        return (h > 0 ? h + "h " : "") + m + "min";
    }

    // ------------------------------------------------------------------ núcleo

    public static void interagir(ServerPlayer p, BlockPos pos, int andar, boolean despertar) {
        ServerLevel nivel = p.serverLevel();
        String chave = chave(nivel, pos);
        DungeonDef d = escolher(nivel, pos);
        if (d == null) {
            p.sendSystemMessage(Component.literal("Esta cripta está adormecida (nenhuma dungeon cadastrada no painel).").withStyle(ChatFormatting.GRAY));
            return;
        }
        DungeonDef.Guardiao g = andar == 0 ? d.finalBoss() : d.andar(andar);
        if (ativas.containsKey(chave)) {
            p.sendSystemMessage(Component.literal("O guardião já despertou! Derrote " + g.nome() + ".").withStyle(ChatFormatting.RED));
            return;
        }

        if (!despertar && andar > 0) {
            p.sendSystemMessage(Component.literal("━━ " + d.nome() + " · Camada " + andar + " ━━").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.literal("Guardião do andar: " + g.nome() + ". Derrote-o para ganhar a Chave da Camada " + andar
                    + " e abrir o Portão Selado.").withStyle(ChatFormatting.GOLD));
            p.sendSystemMessage(Component.literal("Agache e clique no núcleo para despertar o guardião.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        if (!despertar) {
            long moedasPremio = Recompensa.chefao(d.nivelRecomendado());
            StringBuilder premio = new StringBuilder(d.recompensaXp() + " XP" + (moedasPremio > 0 ? " · " + br.com.frxcode.frxworld.moedas.Moedas.texto(moedasPremio) : ""));
            for (DungeonDef.Loot l : d.loot()) {
                premio.append(" · ").append(l.qtd()).append("× ").append(itemNome(l.item()));
                if (l.chance() < 100) premio.append(" (").append((int) l.chance()).append("%)");
            }
            p.sendSystemMessage(Component.literal("━━ " + d.nome() + " ━━").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            if (!d.descricao().isEmpty()) p.sendSystemMessage(Component.literal(d.descricao()).withStyle(ChatFormatting.GRAY));
            int nivelJogador = Progressao.dados(p).nivel();
            p.sendSystemMessage(Component.literal("Nível recomendado: " + d.nivelRecomendado() + "+")
                    .withStyle(nivelJogador >= d.nivelRecomendado() ? ChatFormatting.GREEN : ChatFormatting.RED));
            p.sendSystemMessage(Component.literal("Guardião: " + d.bossNome() + " · Prêmio: " + premio).withStyle(ChatFormatting.GOLD));
            long resta = recargaRestante(p, chave, d);
            if (resta > 0) p.sendSystemMessage(Component.literal("Você já recebeu este prêmio. Disponível de novo em " + tempo(resta) + ".").withStyle(ChatFormatting.YELLOW));
            p.sendSystemMessage(Component.literal("Agache e clique no núcleo para despertar o guardião.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        despertarGuardiao(p, nivel, pos, chave, d, andar, g);
    }

    private static void despertarGuardiao(ServerPlayer quem, ServerLevel nivel, BlockPos nucleo, String chave, DungeonDef d,
                                          int andar, DungeonDef.Guardiao g) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(g.tipo()));
        Entity e = tipo != null ? tipo.create(nivel) : null;
        if (!(e instanceof Mob boss)) {
            quem.sendSystemMessage(Component.literal("Guardião inválido no painel: " + g.tipo()).withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos onde = acharLugar(nivel, nucleo, 3);
        boss.moveTo(onde.getX() + 0.5, onde.getY(), onde.getZ() + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        boss.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(onde), MobSpawnType.EVENT, null, null);
        definir(boss.getAttribute(Attributes.MAX_HEALTH), Math.min(1024, g.vida()));
        definir(boss.getAttribute(Attributes.ATTACK_DAMAGE), g.dano());
        AttributeInstance vel = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (vel != null) vel.setBaseValue(vel.getBaseValue() * g.velocidade());
        definir(boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE), 0.6);
        definir(boss.getAttribute(Attributes.FOLLOW_RANGE), 40);
        boss.setHealth(boss.getMaxHealth());
        boss.setCustomName(Component.literal(g.nome()).withStyle(andar == 0 ? ChatFormatting.DARK_RED : ChatFormatting.RED, ChatFormatting.BOLD));
        boss.setCustomNameVisible(true);
        boss.setPersistenceRequired();
        boss.addTag(TAG_BOSS);
        boss.getPersistentData().putInt("frx_rank_nivel", d.nivelRecomendado() + (andar == 0 ? 5 : andar * 2));
        protegerDoSol(boss);
        boss.setTarget(quem);
        nivel.addFreshEntity(boss);

        ServerBossEvent barra = new ServerBossEvent(Component.literal(g.nome()).withStyle(ChatFormatting.DARK_RED),
                andar == 0 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        Ativa a = new Ativa(chave, nivel, nucleo, d, andar, boss.getUUID(), barra);

        EntityType<?> tipoLacaio = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(g.lacaioTipo()));
        for (int i = 0; i < g.lacaioQtd() && tipoLacaio != null; i++) {
            Entity le = tipoLacaio.create(nivel);
            if (!(le instanceof Mob lacaio)) break;
            BlockPos lp = acharLugar(nivel, nucleo, 5);
            lacaio.moveTo(lp.getX() + 0.5, lp.getY(), lp.getZ() + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
            lacaio.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(lp), MobSpawnType.EVENT, null, null);
            lacaio.setPersistenceRequired();
            protegerDoSol(lacaio);
            nivel.addFreshEntity(lacaio);
            a.lacaios.add(lacaio.getUUID());
        }
        ativas.put(chave, a);

        for (ServerPlayer p : jogadoresPerto(a, RAIO_PARTICIPAR)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(g.nome()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(d.nome() + (andar > 0 ? " · Camada " + andar : " · Guardião final")).withStyle(ChatFormatting.GRAY)));
        }
        nivel.playSound(null, nucleo, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.2f);
    }

    private static void definir(AttributeInstance a, double valor) {
        if (a != null) a.setBaseValue(valor);
    }

    /** Capacete para mortos-vivos não queimarem de dia. */
    private static void protegerDoSol(Mob m) {
        if (m instanceof Enemy && m.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            m.setDropChance(EquipmentSlot.HEAD, 0f);
        }
    }

    /** Procura um ponto livre perto do núcleo (dentro da cripta). */
    private static BlockPos acharLugar(ServerLevel nivel, BlockPos centro, int raio) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int t = 0; t < 20; t++) {
            BlockPos p = centro.offset(r.nextInt(-raio, raio + 1), -1, r.nextInt(-raio, raio + 1));
            if (nivel.getBlockState(p).isAir() && nivel.getBlockState(p.above()).isAir() && !nivel.getBlockState(p.below()).isAir()) return p;
        }
        return centro.offset(2, -1, 0);
    }

    private static List<ServerPlayer> jogadoresPerto(Ativa a, double raio) {
        List<ServerPlayer> lista = new ArrayList<>();
        for (ServerPlayer p : a.nivel.players()) {
            if (p.distanceToSqr(a.nucleo.getX() + 0.5, a.nucleo.getY(), a.nucleo.getZ() + 0.5) <= raio * raio) lista.add(p);
        }
        return lista;
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END || ativas.isEmpty()) return;
        if (evento.getServer().getTickCount() % 10 != 0) return;

        for (Ativa a : List.copyOf(ativas.values())) {
            Entity e = a.nivel.getEntity(a.boss);
            if (e instanceof Mob boss && boss.isAlive()) {
                a.barra.setProgress(Math.max(0f, boss.getHealth() / boss.getMaxHealth()));
            }
            List<ServerPlayer> perto = jogadoresPerto(a, RAIO_BARRA);
            for (ServerPlayer p : perto) if (!a.barra.getPlayers().contains(p)) a.barra.addPlayer(p);
            for (ServerPlayer p : List.copyOf(a.barra.getPlayers())) if (!perto.contains(p)) a.barra.removePlayer(p);

            if (perto.isEmpty()) {
                a.ticksSemJogador += 10;
                if (a.ticksSemJogador >= TICKS_ABANDONO) encerrar(a, true);
            } else {
                a.ticksSemJogador = 0;
            }
        }
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent evento) {
        if (!evento.getEntity().getTags().contains(TAG_BOSS)) return;
        UUID id = evento.getEntity().getUUID();
        for (Ativa a : List.copyOf(ativas.values())) {
            if (a.boss.equals(id)) {
                premiar(a);
                encerrar(a, false);
                return;
            }
        }
    }

    private static void premiar(Ativa a) {
        if (a.andar > 0) {
            premiarAndar(a);
            return;
        }
        DungeonDef d = a.def;
        for (ServerPlayer p : jogadoresPerto(a, RAIO_PARTICIPAR)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Dungeon concluída").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(d.nome()).withStyle(ChatFormatting.YELLOW)));

            long resta = recargaRestante(p, a.chave, d);
            if (resta > 0) {
                p.sendSystemMessage(Component.literal("Você já recebeu o prêmio desta dungeon. Disponível de novo em " + tempo(resta) + ".").withStyle(ChatFormatting.YELLOW));
                continue;
            }
            long moedas = Recompensa.chefao(d.nivelRecomendado());
            if (moedas > 0) br.com.frxcode.frxworld.moedas.Moedas.dar(p, moedas);
            StringBuilder ganhou = new StringBuilder("+" + d.recompensaXp() + " XP" + (moedas > 0 ? " · " + br.com.frxcode.frxworld.moedas.Moedas.texto(moedas) : ""));
            for (DungeonDef.Loot l : d.loot()) {
                if (ThreadLocalRandom.current().nextDouble(100) >= l.chance()) continue;
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(l.item()));
                if (item == null) continue;
                int resto = l.qtd();
                while (resto > 0) {
                    int lote = Math.min(resto, item.getMaxStackSize());
                    ItemStack pilha = new ItemStack(item, lote);
                    if (!p.getInventory().add(pilha)) p.drop(pilha, false);
                    resto -= lote;
                }
                ganhou.append(" · ").append(l.qtd()).append("× ").append(itemNome(l.item()));
            }
            DadosJogador dados = Progressao.dados(p);
            dados.dungeons().put(a.chave, System.currentTimeMillis());
            Progressao.ganharXp(p, d.recompensaXp());
            p.sendSystemMessage(Component.literal("Prêmio de " + d.nome() + ": ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(ganhou.toString()).withStyle(ChatFormatting.WHITE)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    /** Guardião do andar: cada jogador perto ganha a Chave da Camada (válida só nesta catacumba) e um pouco de XP. */
    private static void premiarAndar(Ativa a) {
        long xp = Math.max(1, Math.round(a.def.recompensaXp() * 0.2 * a.andar));
        for (ServerPlayer p : jogadoresPerto(a, RAIO_PARTICIPAR)) {
            ItemStack chave = new ItemStack(Blocos.CHAVE_CAMADA.get());
            CompoundTag tag = chave.getOrCreateTagElement("frx_chave");
            tag.putInt("camada", a.andar);
            tag.putString("dim", a.nivel.dimension().location().toString());
            tag.putInt("x", a.nucleo.getX());
            tag.putInt("y", a.nucleo.getY());
            tag.putInt("z", a.nucleo.getZ());
            chave.setHoverName(Component.literal("Chave da Camada " + a.andar).withStyle(ChatFormatting.GOLD));
            if (!p.getInventory().add(chave)) p.drop(chave, false);
            Progressao.ganharXp(p, xp);
            long moedas = Recompensa.guardiao(a.def.nivelRecomendado(), a.andar);
            if (moedas > 0) {
                br.com.frxcode.frxworld.moedas.Moedas.dar(p, moedas);
                p.sendSystemMessage(Component.literal("Guardião da Camada " + a.andar + " vencido: +" + xp + " XP · " + br.com.frxcode.frxworld.moedas.Moedas.texto(moedas)).withStyle(ChatFormatting.GOLD));
            }
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Chave da Camada " + a.andar).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("O Portão Selado pode ser aberto").withStyle(ChatFormatting.YELLOW)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1.3f);
        }
    }

    /** Portão Selado: com a chave certa, leva o jogador para o outro lado. Voltar não precisa de chave. */
    public static void atravessarPortao(ServerPlayer p, BlockPos portao, int camada) {
        ServerLevel nivel = p.serverLevel();
        // direção do jogador para o portão (eixo dominante)
        double dx = portao.getX() + 0.5 - p.getX(), dz = portao.getZ() + 0.5 - p.getZ();
        Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);

        BlockPos destinoBase = portao.relative(dir, 2);
        if (!temChave(p, camada, portao) && !voltando(nivel, portao, dir)) {
            p.sendSystemMessage(Component.literal("O portão está selado. Derrote o guardião deste andar para conseguir a Chave da Camada " + camada + ".")
                    .withStyle(ChatFormatting.RED));
            nivel.playSound(null, portao, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
            return;
        }
        BlockPos alvo = acharChao(nivel, destinoBase);
        p.teleportTo(alvo.getX() + 0.5, alvo.getY(), alvo.getZ() + 0.5);
        nivel.playSound(null, alvo, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    /** Lado da escadaria fica mais baixo: quem está nele está voltando e passa sem chave. */
    private static boolean voltando(ServerLevel nivel, BlockPos portao, Direction dir) {
        BlockPos atras = portao.relative(dir.getOpposite(), 2);
        BlockPos frente = portao.relative(dir, 2);
        return acharChao(nivel, frente).getY() > acharChao(nivel, atras).getY();
    }

    private static boolean temChave(ServerPlayer p, int camada, BlockPos portao) {
        String dim = p.serverLevel().dimension().location().toString();
        for (ItemStack s : p.getInventory().items) {
            if (!s.is(Blocos.CHAVE_CAMADA.get())) continue;
            CompoundTag t = s.getTagElement("frx_chave");
            if (t == null || t.getInt("camada") != camada || !dim.equals(t.getString("dim"))) continue;
            BlockPos origem = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            if (origem.distSqr(portao) <= 32 * 32) return true;
        }
        return false;
    }

    /** Primeiro ponto com chão e 2 blocos de ar, procurando para baixo e para cima. */
    private static BlockPos acharChao(ServerLevel nivel, BlockPos base) {
        for (int dy = 2; dy >= -12; dy--) {
            BlockPos p = base.offset(0, dy, 0);
            if (nivel.getBlockState(p).isAir() && nivel.getBlockState(p.above()).isAir() && !nivel.getBlockState(p.below()).isAir()) return p;
        }
        return base;
    }

    private static void encerrar(Ativa a, boolean abandonada) {
        ativas.remove(a.chave);
        a.barra.removeAllPlayers();
        if (abandonada) {
            Entity boss = a.nivel.getEntity(a.boss);
            if (boss != null) boss.discard();
            for (UUID l : a.lacaios) {
                Entity e = a.nivel.getEntity(l);
                if (e != null) e.discard();
            }
            FrxWorld.LOG.info("Dungeon {} abandonada; guardião removido.", a.def.nome());
        }
    }

    /** Limpa o tempo de recarga das dungeons de um jogador (comando de admin). */
    public static void resetar(ServerPlayer p) {
        Progressao.dados(p).dungeons().clear();
    }
}
