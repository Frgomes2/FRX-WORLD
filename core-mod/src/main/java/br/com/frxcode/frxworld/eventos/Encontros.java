package br.com.frxcode.frxworld.eventos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.aventura.Tesouros;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.protecao.AreasProtegidas;
import br.com.frxcode.frxworld.protecao.Terrenos;
import br.com.frxcode.frxworld.reinos.Lojas;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import br.com.frxcode.frxworld.reinos.Reputacao;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encontros: pequenos acontecimentos raros na superfície, fora das capitais, sem combate obrigatório.
 * Um por jogador de cada vez, no máximo um a cada 40 minutos de jogo:
 *  - viajante: um ferido pede uma poção de cura e paga com um Mapa do Tesouro;
 *  - mercador: um mercador perdido vende um item raro, uma única vez, e vai embora;
 *  - estrela: uma estrela cai a 60–120 blocos e fica marcada por 10 minutos; quem chegar primeiro leva;
 *  - bau: um baú que pula para longe quando alguém chega perto, até cansar;
 *  - carta: um mensageiro confunde o jogador com outra pessoa e entrega uma carta; abrir ou levar ao destinatário.
 */
public class Encontros {
    private static final String TAG = "frx_encontro", CARTA = "frx_carta";
    private static final String[] TIPOS = {"viajante", "mercador", "estrela", "bau", "carta"};
    private static final long RECARGA_MS = 40 * 60_000L;
    private static final int CHANCE = 10, ANDAR_MIN = 40;

    private static final class Ativo {
        String tipo;
        ServerLevel nivel;
        UUID entidade;
        BlockPos pos;
        long fim;
        int pulos;
        boolean cansado;
        String reino = "";
    }

    private static final Map<UUID, Ativo> ATIVOS = new HashMap<>();
    private static final Set<UUID> VIVOS = new HashSet<>();
    private static final Map<UUID, Long> ULTIMO = new HashMap<>();
    private static final Map<UUID, BlockPos> POSICAO = new HashMap<>();

    private static ThreadLocalRandom rnd() { return ThreadLocalRandom.current(); }
    private static int nivel(ServerPlayer p) { return Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1; }
    private static void dizer(ServerPlayer p, String texto, ChatFormatting cor) { p.sendSystemMessage(Component.literal(texto).withStyle(cor)); }

    private static boolean lugarProibido(ServerLevel n, int x, int z, BlockPos b) {
        for (Reino r : Reinos.todos()) if (r.distancia(x, z) < 64) return true;
        return Terrenos.temLote(x, z) || AreasProtegidas.protegido(n, b);
    }

    /** Ponto livre na superfície, entre min e max blocos do jogador. */
    private static BlockPos lugar(ServerLevel n, ServerPlayer p, int min, int max) {
        for (int t = 0; t < 16; t++) {
            double ang = rnd().nextDouble(Math.PI * 2);
            int dist = min + rnd().nextInt(Math.max(1, max - min + 1));
            int x = p.getBlockX() + (int) Math.round(Math.cos(ang) * dist), z = p.getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
            if (!n.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos topo = n.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!n.getFluidState(topo.below()).isEmpty() || !n.getBlockState(topo).isAir() || !n.getBlockState(topo.above()).isAir()) continue;
            if (max <= 16 && Math.abs(topo.getY() - p.getBlockY()) > 8) continue;
            if (lugarProibido(n, x, z, topo)) continue;
            return topo;
        }
        return null;
    }

    private static Reino reinoQualquer() {
        List<Reino> l = new ArrayList<>(Reinos.todos());
        return l.isEmpty() ? null : l.get(rnd().nextInt(l.size()));
    }

    // ------------------------------------------------------------------ começar

    private static boolean iniciar(ServerPlayer p, String tipo) {
        if (!(p.level() instanceof ServerLevel n)) return false;
        Ativo a = new Ativo();
        a.tipo = tipo;
        a.nivel = n;
        a.fim = System.currentTimeMillis() + (tipo.equals("estrela") ? 10 : 5) * 60_000L;
        boolean ok = switch (tipo) {
            case "viajante" -> viajante(p, a);
            case "mercador" -> mercador(p, a);
            case "estrela" -> estrela(p, a);
            case "bau" -> bau(p, a);
            case "carta" -> carta(p, a);
            default -> false;
        };
        if (!ok) return false;
        ATIVOS.put(p.getUUID(), a);
        ULTIMO.put(p.getUUID(), System.currentTimeMillis());
        FrxWorld.LOG.info("Encontro {} para {}", tipo, p.getGameProfile().getName());
        return true;
    }

    private static boolean soltar(Ativo a, Mob m, BlockPos b, ServerPlayer p, String nome, ChatFormatting cor) {
        m.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, (float) Math.toDegrees(Math.atan2(-(p.getX() - b.getX() - 0.5), p.getZ() - b.getZ() - 0.5)), 0);
        m.setYHeadRot(m.getYRot());
        m.setNoAi(true);
        m.setInvulnerable(true);
        m.setCustomName(Component.literal(nome).withStyle(cor));
        m.setCustomNameVisible(true);
        m.addTag(TAG);
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0, false, false));
        a.entidade = m.getUUID();
        a.pos = b;
        VIVOS.add(m.getUUID());   // antes de entrar no mundo: aoEntrar só deixa passar os de agora
        a.nivel.addFreshEntity(m);
        return true;
    }

    private static boolean viajante(ServerPlayer p, Ativo a) {
        BlockPos b = lugar(a.nivel, p, 6, 10);
        Villager v = b == null ? null : EntityType.VILLAGER.create(a.nivel);
        Reino r = reinoQualquer();
        if (v == null) return false;
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));
        v.setHealth(4f);
        a.reino = r != null ? r.id() : "";
        dizer(p, "Alguém geme perto da estrada. Um viajante ferido pede ajuda.", ChatFormatting.YELLOW);
        return soltar(a, v, b, p, "Viajante Ferido" + (r != null ? " de " + r.nome() : ""), ChatFormatting.YELLOW);
    }

    /** Item raro, preço em cobre, nível mínimo do jogador. */
    private record Raro(Item item, int qtd, int preco, int nivelMin, String nome) {}
    private static final Raro[] RAROS = {
            new Raro(Items.TOTEM_OF_UNDYING, 1, 300, 1, ""), new Raro(Items.ENCHANTED_GOLDEN_APPLE, 1, 500, 1, ""),
            new Raro(Items.ENDER_PEARL, 16, 350, 1, ""), new Raro(Items.NETHERITE_INGOT, 1, 600, 10, ""),
            new Raro(Items.HEART_OF_THE_SEA, 1, 400, 5, ""), new Raro(Items.ELYTRA, 1, 2000, 20, ""),
            new Raro(Items.NETHERITE_SWORD, 1, 3000, 15, "Lâmina do Viajante"),
    };

    private static boolean mercador(ServerPlayer p, Ativo a) {
        BlockPos b = lugar(a.nivel, p, 6, 10);
        WanderingTrader t = b == null ? null : EntityType.WANDERING_TRADER.create(a.nivel);
        if (t == null) return false;
        List<Raro> pode = new ArrayList<>();
        for (Raro r : RAROS) if (nivel(p) >= r.nivelMin()) pode.add(r);
        Raro r = pode.get(rnd().nextInt(pode.size()));
        ItemStack s = new ItemStack(r.item(), r.qtd());
        if (!r.nome().isEmpty()) {
            br.com.frxcode.frxworld.armas.Armas.definirRaridade(s, 3);
            br.com.frxcode.frxworld.armas.Armas.definirNivel(s, 2);
            s.setHoverName(Component.literal(r.nome()));
        }
        MerchantOffer base = Lojas.ofertaAvulsa(s, r.preco(), 0);
        t.getOffers().clear();
        t.getOffers().add(new MerchantOffer(base.getBaseCostA(), base.getCostB(), base.getResult(), 1, 0, 0f));
        t.setDespawnDelay(20 * 60 * 6);
        dizer(p, "Um mercador perdido acena para você. Ele tem uma única coisa para vender, e só hoje.", ChatFormatting.GREEN);
        return soltar(a, t, b, p, "Mercador Perdido", ChatFormatting.GREEN);
    }

    private static boolean estrela(ServerPlayer p, Ativo a) {
        BlockPos b = lugar(a.nivel, p, 60, 120);
        if (b == null) return false;
        a.pos = b;
        a.nivel.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, SoundSource.AMBIENT, 2f, 0.6f);
        dizer(p, "✦ Uma estrela riscou o céu e caiu não muito longe. Ela brilha por 10 minutos.", ChatFormatting.AQUA);
        p.sendSystemMessage(Component.literal("   📍 ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("[name:\"Estrela caída\", x:" + b.getX() + ", y:" + b.getY() + ", z:" + b.getZ() + "]").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  clique para marcar no mapa.").withStyle(ChatFormatting.GRAY)));
        return true;
    }

    private static boolean porBau(ServerLevel n, BlockPos b) { return n.setBlock(b, Blocks.CHEST.defaultBlockState(), 3); }

    private static boolean bau(ServerPlayer p, Ativo a) {
        BlockPos b = lugar(a.nivel, p, 7, 11);
        if (b == null || !porBau(a.nivel, b)) return false;
        a.pos = b;
        a.pulos = 4 + rnd().nextInt(3);
        dizer(p, "Tem um baú ali... e ele acabou de se mexer?", ChatFormatting.GOLD);
        return true;
    }

    private static boolean carta(ServerPlayer p, Ativo a) {
        BlockPos b = lugar(a.nivel, p, 5, 8);
        Villager v = b == null ? null : EntityType.VILLAGER.create(a.nivel);
        Reino r = reinoQualquer();
        if (v == null || r == null) return false;
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER));
        a.reino = r.id();
        dizer(p, "Um mensageiro vem correndo na sua direção, sem fôlego.", ChatFormatting.LIGHT_PURPLE);
        return soltar(a, v, b, p, "Mensageiro Apressado", ChatFormatting.LIGHT_PURPLE);
    }

    private static ItemStack criarCarta(Reino r) {
        ItemStack s = new ItemStack(Items.PAPER);
        s.getOrCreateTag().putString(CARTA, r.id());
        s.setHoverName(Component.literal("Carta Lacrada").withStyle(x -> x.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
        ListTag lore = new ListTag();
        for (String l : new String[]{"Para o taverneiro de " + r.nome() + ".", "Entregue em mãos e ele paga pelo favor.", "Ou abra: clique com ela na mão, olhando para o céu."})
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(l).withStyle(x -> x.withColor(ChatFormatting.GRAY).withItalic(false)))));
        s.getOrCreateTagElement("display").put("Lore", lore);
        return s;
    }

    // ------------------------------------------------------------------ andamento

    private static void sumir(Ativo a, boolean fumaca) {
        if (a.entidade != null) {
            Entity e = a.nivel.getEntity(a.entidade);
            if (e != null) {
                if (fumaca) a.nivel.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
                e.discard();
            }
            VIVOS.remove(a.entidade);
        }
        if (a.tipo.equals("bau") && a.pos != null && a.nivel.getBlockState(a.pos).is(Blocks.CHEST)) {
            if (!a.cansado && a.nivel.getBlockEntity(a.pos) instanceof ChestBlockEntity c) c.clearContent();
            a.nivel.sendParticles(ParticleTypes.POOF, a.pos.getX() + 0.5, a.pos.getY() + 0.5, a.pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
            if (!a.cansado) a.nivel.setBlock(a.pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void encherBau(ServerPlayer p, ChestBlockEntity c) {
        int n = nivel(p);
        c.setItem(4, new ItemStack(Items.GOLD_INGOT, 4 + rnd().nextInt(5)));
        c.setItem(11, new ItemStack(Items.EMERALD, 3 + rnd().nextInt(4)));
        c.setItem(13, new ItemStack(Items.DIAMOND, 1 + rnd().nextInt(n >= 20 ? 3 : 2)));
        c.setItem(15, new ItemStack(Items.GOLDEN_APPLE, 1 + rnd().nextInt(2)));
        c.setItem(22, Moedas.prata(5 + Math.min(40, n)));
        if (rnd().nextInt(100) < 20) c.setItem(20, Tesouros.criar(p));
    }

    private static void tickBau(MinecraftServer s, UUID dono, Ativo a) {
        ServerPlayer p = s.getPlayerList().getPlayer(dono);
        if (a.cansado || p == null || p.level() != a.nivel) return;
        if (!a.nivel.getBlockState(a.pos).is(Blocks.CHEST)) { a.fim = 0; return; }   // alguém quebrou
        if (p.blockPosition().distSqr(a.pos) > 3.2 * 3.2) return;
        if (a.pulos <= 0) {
            a.cansado = true;
            a.fim = System.currentTimeMillis() + 3 * 60_000L;
            if (a.nivel.getBlockEntity(a.pos) instanceof ChestBlockEntity c) encherBau(p, c);
            a.nivel.playSound(null, a.pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1f, 0.7f);
            dizer(p, "O baú cansou de fugir. É seu.", ChatFormatting.GOLD);
            s.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + p.getGameProfile().getName() + " alcançou um baú fujão.").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        // pula para o lado oposto ao jogador
        BlockPos novo = null;
        double ang = Math.atan2(a.pos.getZ() - p.getZ(), a.pos.getX() - p.getX());
        for (int t = 0; t < 10 && novo == null; t++) {
            double g = ang + rnd().nextDouble(-1.0, 1.0);
            int dist = 6 + rnd().nextInt(4);
            int x = a.pos.getX() + (int) Math.round(Math.cos(g) * dist), z = a.pos.getZ() + (int) Math.round(Math.sin(g) * dist);
            if (!a.nivel.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos topo = a.nivel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!a.nivel.getBlockState(topo).isAir() || !a.nivel.getFluidState(topo.below()).isEmpty() || Math.abs(topo.getY() - a.pos.getY()) > 6
                    || lugarProibido(a.nivel, x, z, topo)) continue;
            novo = topo;
        }
        a.pulos--;
        if (novo == null) return;
        a.nivel.sendParticles(ParticleTypes.POOF, a.pos.getX() + 0.5, a.pos.getY() + 0.5, a.pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        a.nivel.setBlock(a.pos, Blocks.AIR.defaultBlockState(), 3);
        porBau(a.nivel, novo);
        a.pos = novo;
        a.nivel.playSound(null, novo, SoundEvents.RABBIT_JUMP, SoundSource.BLOCKS, 1.5f, 0.6f);
        a.nivel.sendParticles(ParticleTypes.CLOUD, novo.getX() + 0.5, novo.getY() + 0.2, novo.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.01);
    }

    private static void tickEstrela(MinecraftServer s, Ativo a) {
        double x = a.pos.getX() + 0.5, y = a.pos.getY(), z = a.pos.getZ() + 0.5;
        for (ServerPlayer p : a.nivel.players()) {
            if (p.blockPosition().distSqr(a.pos) > 200 * 200) continue;
            for (int i = 0; i < 10; i++) a.nivel.sendParticles(p, ParticleTypes.END_ROD, true, x, y + 2 + i * 4, z, 2, 0.1, 1.6, 0.1, 0.0);
            a.nivel.sendParticles(p, ParticleTypes.FIREWORK, true, x, y + 0.6, z, 4, 0.5, 0.3, 0.5, 0.02);
        }
        for (ServerPlayer p : a.nivel.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive() || p.blockPosition().distSqr(a.pos) > 16) continue;
            int n = nivel(p);
            long moedas = 200 + n * 20L;
            Moedas.dar(p, moedas);
            Progressao.ganharXp(p, n * 40L);
            int sorte = rnd().nextInt(100);
            ItemStack premio = sorte < 5 ? new ItemStack(Items.NETHER_STAR) : sorte < 40 ? new ItemStack(Items.DIAMOND, 2)
                    : sorte < 70 ? new ItemStack(Items.AMETHYST_SHARD, 8) : new ItemStack(Items.GLOWSTONE, 8);
            if (!p.getInventory().add(premio)) p.drop(premio, false);
            a.nivel.playSound(null, a.pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2f, 0.8f);
            dizer(p, "✦ Você tocou a estrela caída: " + Moedas.texto(moedas) + " e um fragmento do céu.", ChatFormatting.AQUA);
            s.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + p.getGameProfile().getName() + " alcançou uma estrela caída"
                    + (sorte < 5 ? " e encontrou uma Estrela do Nether!" : ".")).withStyle(ChatFormatting.GRAY), false);
            a.fim = 0;
            return;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        int t = s.getTickCount();
        if (t % 10 == 4 && !ATIVOS.isEmpty()) {
            long agora = System.currentTimeMillis();
            for (var it = ATIVOS.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                Ativo a = en.getValue();
                try {
                    if (a.tipo.equals("bau")) tickBau(s, en.getKey(), a);
                    else if (a.tipo.equals("estrela") && t % 20 == 4) tickEstrela(s, a);
                    else if (a.tipo.equals("mercador") && a.nivel.getEntity(a.entidade) instanceof WanderingTrader w
                            && !w.getOffers().isEmpty() && w.getOffers().get(0).isOutOfStock() && w.getTradingPlayer() == null) {
                        ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
                        if (p != null) dizer(p, "\"Negócio feito. Agora, se me dá licença, vou me perder em outro lugar.\"", ChatFormatting.GREEN);
                        a.fim = 0;
                    }
                } catch (Exception ex) { FrxWorld.LOG.warn("Encontro {} falhou: {}", a.tipo, ex.toString()); a.fim = 0; }
                if (agora > a.fim) { sumir(a, true); it.remove(); }
            }
        }
        if (t % 1200 != 37) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            BlockPos agora = p.blockPosition(), antes = POSICAO.put(p.getUUID(), agora);
            if (antes == null || !p.isAlive() || p.isSpectator() || p.isCreative() || !Progressao.temDados(p)) continue;
            if (p.level().dimension() != Level.OVERWORLD || ATIVOS.containsKey(p.getUUID())) continue;
            if (antes.distSqr(agora) < ANDAR_MIN * ANDAR_MIN) continue;
            if (System.currentTimeMillis() - ULTIMO.getOrDefault(p.getUUID(), 0L) < RECARGA_MS) continue;
            ServerLevel n = p.serverLevel();
            if (n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, agora.getX(), agora.getZ()) > agora.getY() + 6) continue;   // em caverna ou construção
            if (lugarProibido(n, agora.getX(), agora.getZ(), agora) || rnd().nextInt(100) >= CHANCE) continue;
            try { iniciar(p, TIPOS[rnd().nextInt(TIPOS.length)]); }
            catch (Exception ex) { FrxWorld.LOG.warn("Encontro falhou para {}: {}", p.getGameProfile().getName(), ex.toString()); }
        }
    }

    // ------------------------------------------------------------------ cliques

    private static boolean pocaoDeCura(ItemStack s) {
        if (!(s.getItem() instanceof PotionItem)) return false;
        for (MobEffectInstance e : PotionUtils.getMobEffects(s)) if (e.getEffect() == MobEffects.HEAL || e.getEffect() == MobEffects.REGENERATION) return true;
        return false;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void aoClicarEspecifico(PlayerInteractEvent.EntityInteractSpecific ev) {
        if (ev.getTarget().getTags().contains(TAG) && !(ev.getTarget() instanceof WanderingTrader)) {
            ev.setCanceled(true);
            ev.setCancellationResult(InteractionResult.PASS);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void aoClicar(PlayerInteractEvent.EntityInteract ev) {
        Entity e = ev.getTarget();
        if (!e.getTags().contains(TAG) || e instanceof WanderingTrader) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.SUCCESS);
        if (ev.getHand() != InteractionHand.MAIN_HAND || !(ev.getEntity() instanceof ServerPlayer p)) return;
        Ativo a = null;
        for (Ativo x : ATIVOS.values()) if (e.getUUID().equals(x.entidade)) a = x;
        if (a == null) return;
        if (a.tipo.equals("viajante")) {
            ItemStack mao = p.getMainHandItem();
            if (!pocaoDeCura(mao)) { dizer(p, "\"Uma poção de cura ou de regeneração... por favor. Pago com o que me resta: um mapa.\"", ChatFormatting.YELLOW); return; }
            mao.shrink(1);
            ItemStack mapa = Tesouros.criar(p);
            if (!p.getInventory().add(mapa)) p.drop(mapa, false);
            if (!a.reino.isEmpty()) Reputacao.ganhar(p, a.reino, 20, true);
            a.nivel.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + 1.6, e.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
            a.nivel.playSound(null, e.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
            dizer(p, "\"Você salvou a minha vida. Fique com o mapa: eu já não tenho pernas para ir atrás dele. Meu povo vai saber o que você fez.\"", ChatFormatting.YELLOW);
            a.fim = System.currentTimeMillis() + 4000L;
        } else if (a.tipo.equals("carta")) {
            Reino r = Reputacao.reino(a.reino);
            if (r == null) { a.fim = 0; return; }
            ItemStack carta = criarCarta(r);
            if (!p.getInventory().add(carta)) p.drop(carta, false);
            a.nivel.playSound(null, e.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.2f, 1f);
            dizer(p, "\"Finalmente achei você! Carta para o taverneiro de " + r.nome() + ", como combinado. ...Você é o entregador, não é? Deve ser. Tenho pressa!\"", ChatFormatting.LIGHT_PURPLE);
            a.fim = System.currentTimeMillis() + 3000L;
        }
    }

    /** Abrir a carta: clique com ela na mão, olhando para cima. */
    @SubscribeEvent
    public void aoUsarItem(PlayerInteractEvent.RightClickItem ev) {
        ItemStack s = ev.getItemStack();
        if (!(ev.getEntity() instanceof ServerPlayer p) || !s.is(Items.PAPER) || !s.hasTag() || !s.getTag().contains(CARTA)) return;
        if (p.getXRot() > -45) { p.displayClientMessage(Component.literal("Para abrir a carta, olhe para o céu e clique. Para entregar, leve ao taverneiro.").withStyle(ChatFormatting.GRAY), true); return; }
        s.shrink(1);
        int sorte = rnd().nextInt(100);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2f, 0.7f);
        if (sorte < 30) {
            ItemStack mapa = Tesouros.criar(p);
            if (!p.getInventory().add(mapa)) p.drop(mapa, false);
            dizer(p, "Você rompeu o lacre. Dentro da carta havia um Mapa do Tesouro.", ChatFormatting.LIGHT_PURPLE);
        } else if (sorte < 75) {
            long moedas = 200 + rnd().nextInt(400);
            Moedas.dar(p, moedas);
            dizer(p, "Você rompeu o lacre. A carta trazia um pagamento: " + Moedas.texto(moedas) + ".", ChatFormatting.LIGHT_PURPLE);
        } else {
            dizer(p, "Você rompeu o lacre. \"A cerveja acabou de novo. Mande mais barris.\" Só isso.", ChatFormatting.LIGHT_PURPLE);
        }
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Entrega da carta ao taverneiro do reino certo. Devolve true se a carta foi entregue. */
    public static boolean entregarCarta(ServerPlayer p, Entity npc, String papel) {
        ItemStack s = p.getMainHandItem();
        if (!s.is(Items.PAPER) || !s.hasTag() || !s.getTag().contains(CARTA) || !papel.equals("taverneiro")) return false;
        String reino = s.getTag().getString(CARTA);
        if (!reino.equals(npc.getPersistentData().getString("frx_reino"))) {
            Reino r = Reputacao.reino(reino);
            dizer(p, "\"Essa carta não é para mim. O lacre é de " + (r != null ? r.nome() : "outro reino") + ".\"", ChatFormatting.GRAY);
            return true;
        }
        s.shrink(1);
        int n = nivel(p);
        long moedas = 400 + n * 30L;
        Moedas.dar(p, moedas);
        Progressao.ganharXp(p, n * 50L);
        Reputacao.ganhar(p, reino, 60, true);
        p.level().playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
        dizer(p, "\"Com o lacre inteiro! Pouca gente resiste. Tome " + Moedas.texto(moedas) + " pelo caminho, e o reino fica sabendo.\"", ChatFormatting.LIGHT_PURPLE);
        return true;
    }

    // ------------------------------------------------------------------ limpeza e teste

    /** Sobra de uma queda do servidor some ao carregar. */
    @SubscribeEvent
    public void aoEntrar(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || !e.getTags().contains(TAG)) return;
        if (!VIVOS.contains(e.getUUID())) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        for (Ativo a : ATIVOS.values()) sumir(a, false);
        ATIVOS.clear();
        VIVOS.clear();
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("encontro").then(Commands.argument("tipo", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TIPOS, b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String tipo = StringArgumentType.getString(c, "tipo");
                            Ativo velho = ATIVOS.remove(p.getUUID());
                            if (velho != null) sumir(velho, false);
                            boolean ok = java.util.Arrays.asList(TIPOS).contains(tipo) && iniciar(p, tipo);
                            c.getSource().sendSuccess(() -> Component.literal(ok ? "Encontro criado: " + tipo + "." : "Não deu: tipo inválido ou sem lugar livre por perto."), false);
                            return ok ? 1 : 0;
                        }))));
    }
}
