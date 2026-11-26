package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Zonas;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evento raro "Death Note": um caderno negro cai do céu com um raio, perto de um jogador, e o servidor inteiro é avisado.
 *  - No chão ele brilha e some em 20 minutos. Depois de pego, vira cinzas em 30 minutos. Um uso só.
 *  - Clique com ele na mão e escolha o nome de um jogador online: a Morte é invocada e passa a perseguir esse jogador.
 *  - A Morte (Lifestealer do Born in Chaos) tem 2.000 de vida em duas fases de 1.000: com o manto e depois a forma verdadeira.
 *    Só persegue o alvo, vai atrás dele em qualquer lugar e só para quando mata o alvo ou é derrotada. Outros jogadores podem ajudar.
 *  - Quem morre enquanto é caçado não perde XP, nível nem itens. Quem escreveu o nome fica anônimo.
 *  - Não dá para escrever o nome de quem está em duelo X1, no criativo ou no espectador.
 * Sorteio: a cada hora, 15% de chance, com 2 ou mais jogadores no mundo normal. Admin: /frx deathnote soltar | dar.
 */
public class DeathNote {
    private static final DeferredRegister<Item> ITENS = DeferredRegister.create(ForgeRegistries.ITEMS, FrxWorld.MOD_ID);
    public static final RegistryObject<Item> CADERNO = ITENS.register("death_note", ItemCaderno::new);

    public static void registrar(IEventBus modBus) { ITENS.register(modBus); }

    private static final String ATE = "frx_dn_ate", TAG = "frx_morte";
    private static final long DURA = 30 * 60_000L;
    private static final int SORTEIO = 72_000, CHANCE = 15;
    private static final float FASE = 1000f;   // vida de cada uma das duas fases
    private static final ResourceLocation MANTO = new ResourceLocation("born_in_chaos_v1", "lifestealer"),
            VERDADEIRA = new ResourceLocation("born_in_chaos_v1", "lifestealer_true_form");

    private static final class Cacada {
        final UUID alvo; final String nome;
        UUID mob; int fase = 1; float vida = FASE; int espera;
        ServerLevel nivel; Vec3 ultimo;
        final ServerBossEvent barra;
        Cacada(ServerPlayer p) {
            alvo = p.getUUID(); nome = p.getGameProfile().getName();
            barra = new ServerBossEvent(Component.literal("☠ A Morte — veio por " + nome).withStyle(ChatFormatting.DARK_RED), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            barra.setDarkenScreen(true);
        }
    }

    /** Alvo → a caçada dele. */
    private static final Map<UUID, Cacada> CACADAS = new HashMap<>();
    /** Quem morreu caçado e ainda não renasceu: a morte não custa nada. */
    private static final Set<UUID> LIVRES = new HashSet<>();
    private static int relogio;

    public static boolean semPena(Player p) { return LIVRES.contains(p.getUUID()); }

    public static final class ItemCaderno extends Item {
        ItemCaderno() { super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()); }
        @Override public boolean isFoil(ItemStack s) { return true; }
        @Override public InteractionResultHolder<ItemStack> use(Level nv, Player p, InteractionHand m) {
            if (p instanceof ServerPlayer sp) abrir(sp, "");
            return InteractionResultHolder.success(p.getItemInHand(m));
        }
        @Override public void inventoryTick(ItemStack s, Level nv, Entity e, int espaco, boolean naMao) {
            if (nv.isClientSide() || !(e instanceof ServerPlayer p) || (p.tickCount + espaco) % 20 != 0) return;
            long agora = System.currentTimeMillis(), ate = s.getOrCreateTag().getLong(ATE);
            if (ate == 0) {
                s.getOrCreateTag().putLong(ATE, agora + DURA);
                p.displayClientMessage(Component.literal("📓 Você pegou o Death Note. Ele vira cinzas em 30 minutos: clique com ele na mão para escrever um nome.").withStyle(ChatFormatting.DARK_RED), false);
            } else if (agora > ate) {
                s.shrink(1);
                p.displayClientMessage(Component.literal("📓 O Death Note virou cinzas nas suas mãos.").withStyle(ChatFormatting.GRAY), false);
                p.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
            }
        }
        @Override public void appendHoverText(ItemStack s, Level nv, List<Component> l, TooltipFlag f) {
            l.add(Component.literal("Escreva um nome e a Morte vem buscar essa pessoa.").withStyle(ChatFormatting.DARK_RED));
            l.add(Component.literal("Um uso só. Vira cinzas 30 minutos depois de pego.").withStyle(ChatFormatting.GRAY));
            l.add(Component.literal("Quem cai para a Morte não perde itens nem XP.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ------------------------------------------------------------------ escrever um nome

    private static ItemStack naMao(ServerPlayer p) {
        if (p.getMainHandItem().is(CADERNO.get())) return p.getMainHandItem();
        if (p.getOffhandItem().is(CADERNO.get())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    private static void abrir(ServerPlayer p, String msg) {
        CompoundTag d = new CompoundTag();
        ListTag nomes = new ListTag();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p) nomes.add(StringTag.valueOf(o.getGameProfile().getName()));
        d.put("nomes", nomes);
        d.putString("msg", msg);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("deathnote", d));
    }

    private static void fechar(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        d.putBoolean("fechar", true);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("deathnote", d));
    }

    /** Chamado pela tela do caderno (rede/AcaoHabilidade). */
    public static void escrever(ServerPlayer p, String nome) {
        ItemStack caderno = naMao(p);
        if (caderno.isEmpty()) { fechar(p); return; }
        ServerPlayer alvo = p.server.getPlayerList().getPlayerByName(nome);
        if (alvo == null) { abrir(p, nome + " não está mais online."); return; }
        if (alvo == p) { abrir(p, "Você não pode escrever o próprio nome."); return; }
        if (alvo.isCreative() || alvo.isSpectator() || !alvo.isAlive()) { abrir(p, "A tinta não pega nesse nome agora."); return; }
        if (X1.emDuelo(alvo)) { abrir(p, nome + " está em um duelo X1. Espere acabar."); return; }
        if (CACADAS.containsKey(alvo.getUUID())) { abrir(p, "A Morte já está atrás desse nome."); return; }
        caderno.shrink(1);
        fechar(p);
        p.displayClientMessage(Component.literal("📓 Você escreveu o nome de " + nome + ". O caderno virou cinzas.").withStyle(ChatFormatting.DARK_RED), false);
        cacar(alvo);
    }

    /** Começa a caçada: a Morte aparece perto do alvo. */
    public static void cacar(ServerPlayer alvo) {
        String nome = alvo.getGameProfile().getName();
        Cacada c = new Cacada(alvo);
        CACADAS.put(alvo.getUUID(), c);
        alvo.server.getPlayerList().broadcastSystemMessage(Component.literal("☠ Um nome foi escrito no Death Note: a Morte veio buscar " + nome
                + ". Ela só para quando levar " + nome + " ou for derrotada (2.000 de vida). Quem quiser pode ajudar.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        alvo.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 70, 20));
        alvo.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("Alguém escreveu o seu nome. Lute ou corra.").withStyle(ChatFormatting.GRAY)));
        alvo.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("☠ A Morte veio por você").withStyle(ChatFormatting.DARK_RED)));
        alvo.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1f, 0.6f);
        invocar(c, alvo);
    }

    // ------------------------------------------------------------------ a Morte

    private static EntityType<?> tipo(int fase) {
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(fase == 1 ? MANTO : VERDADEIRA);
        if (t == null || ForgeRegistries.ENTITY_TYPES.getKey(t) == null || !ForgeRegistries.ENTITY_TYPES.containsKey(fase == 1 ? MANTO : VERDADEIRA)) return EntityType.WITHER_SKELETON;
        return t;
    }

    private static boolean daFamilia(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return MANTO.equals(id) || VERDADEIRA.equals(id);
    }

    private static void vestir(Cacada c, Mob m, ServerPlayer alvo) {
        m.addTag(TAG);
        Zonas.marcarNivel(m, Math.max(1, Progressao.dados(alvo).nivel()));
        m.setPersistenceRequired();
        m.setCustomName(Component.literal("☠ A Morte").withStyle(ChatFormatting.DARK_RED));
        m.setCustomNameVisible(true);
        vida(m);
        c.mob = m.getUUID();
        c.nivel = (ServerLevel) m.level();
        c.ultimo = m.position();
    }

    private static void vida(Mob m) {
        AttributeInstance a = m.getAttribute(Attributes.MAX_HEALTH);
        if (a != null && Math.abs(a.getValue() - FASE) > 0.5) { a.removeModifiers(); a.setBaseValue(FASE); }
    }

    /** Faz a Morte aparecer a uns 10 blocos do alvo, com a vida que ela tinha. */
    private static void invocar(Cacada c, ServerPlayer alvo) {
        ServerLevel nv = alvo.serverLevel();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double x = alvo.getX(), y = alvo.getY(), z = alvo.getZ();
        for (int t = 0; t < 16; t++) {
            double a = r.nextDouble(Math.PI * 2), tx = alvo.getX() + Math.cos(a) * 10, tz = alvo.getZ() + Math.sin(a) * 10;
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos pe = BlockPos.containing(tx, alvo.getY() + dy, tz);
                if (nv.getBlockState(pe).getCollisionShape(nv, pe).isEmpty() && nv.getBlockState(pe.above()).getCollisionShape(nv, pe.above()).isEmpty()
                        && nv.getBlockState(pe.above(2)).getCollisionShape(nv, pe.above(2)).isEmpty() && !nv.getBlockState(pe.below()).getCollisionShape(nv, pe.below()).isEmpty()) {
                    x = tx; y = pe.getY(); z = tz; t = 99; break;
                }
            }
        }
        Entity e = tipo(c.fase).create(nv);
        if (!(e instanceof Mob m)) return;
        m.moveTo(x, y, z, r.nextFloat() * 360, 0);
        try { m.finalizeSpawn(nv, nv.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null); } catch (Exception ex) { FrxWorld.LOG.warn("Death Note: {}", ex.toString()); }
        vestir(c, m, alvo);
        nv.addFreshEntity(m);
        m.setHealth(Math.max(1f, Math.min(FASE, c.vida)));
        m.setTarget(alvo);
        LightningBolt raio = EntityType.LIGHTNING_BOLT.create(nv);
        if (raio != null) { raio.moveTo(x, y, z); raio.setVisualOnly(true); nv.addFreshEntity(raio); }
        nv.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1, z, 40, 0.5, 1, 0.5, 0.02);
    }

    private static Mob achar(MinecraftServer srv, UUID id) {
        if (id == null) return null;
        for (ServerLevel nv : srv.getAllLevels()) if (nv.getEntity(id) instanceof Mob m) return m;
        return null;
    }

    /** A Morte trocou de forma por conta própria (o mod dela cria outra entidade): assume a nova. */
    private static boolean adotar(Cacada c, ServerPlayer alvo) {
        if (c.nivel == null || c.ultimo == null) return false;
        for (Mob m : c.nivel.getEntitiesOfClass(Mob.class, new AABB(c.ultimo, c.ultimo).inflate(14), e -> e.isAlive() && daFamilia(e) && !e.getTags().contains(TAG))) {
            boolean verdadeira = VERDADEIRA.equals(ForgeRegistries.ENTITY_TYPES.getKey(m.getType()));
            if (verdadeira && c.fase == 1) { c.fase = 2; c.vida = FASE; }
            vestir(c, m, alvo);
            m.setHealth(Math.max(1f, Math.min(FASE, c.vida)));
            return true;
        }
        return false;
    }

    private static void encerrar(Cacada c, MinecraftServer srv) {
        CACADAS.remove(c.alvo);
        c.barra.removeAllPlayers();
        Mob m = achar(srv, c.mob);
        if (m != null && m.isAlive()) {
            ((ServerLevel) m.level()).sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 1, m.getZ(), 60, 0.5, 1, 0.5, 0.03);
            m.discard();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoMorrer(LivingDeathEvent ev) {
        if (ev.getEntity().level().isClientSide()) return;
        MinecraftServer srv = ev.getEntity().getServer();
        if (srv == null) return;
        if (ev.getEntity() instanceof ServerPlayer p) {
            Cacada c = CACADAS.get(p.getUUID());
            if (c == null) return;
            LIVRES.add(p.getUUID());
            encerrar(c, srv);
            srv.getPlayerList().broadcastSystemMessage(Component.literal("☠ A Morte levou " + c.nome + ".").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            return;
        }
        if (!ev.getEntity().getTags().contains(TAG)) return;
        for (Cacada c : new ArrayList<>(CACADAS.values())) {
            if (!ev.getEntity().getUUID().equals(c.mob)) continue;
            c.ultimo = ev.getEntity().position();
            c.mob = null;
            if (c.fase == 1) {   // caiu o manto: vem a forma verdadeira
                c.fase = 2; c.vida = FASE; c.espera = 60;
                for (ServerPlayer o : c.barra.getPlayers()) o.displayClientMessage(Component.literal("☠ O manto caiu. A Morte mostra a forma verdadeira!").withStyle(ChatFormatting.DARK_RED), false);
            } else {
                encerrar(c, srv);
                String quem = ev.getSource().getEntity() instanceof ServerPlayer k ? k.getGameProfile().getName() : null;
                srv.getPlayerList().broadcastSystemMessage(Component.literal("✦ A Morte foi derrotada" + (quem != null ? " por " + quem : "") + "! " + c.nome + " escapou do Death Note.")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                for (ServerPlayer o : srv.getPlayerList().getPlayers()) o.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent ev) {
        if (LIVRES.remove(ev.getEntity().getUUID()))
            ev.getEntity().displayClientMessage(Component.literal("📓 A Morte levou só o susto: você não perdeu nada.").withStyle(ChatFormatting.GRAY), false);
    }

    /** Cada golpe da Morte no alvo tira pelo menos 12% da vida máxima dele. */
    @SubscribeEvent
    public void golpe(LivingHurtEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.getSource().getEntity() == null || !ev.getSource().getEntity().getTags().contains(TAG)) return;
        Cacada c = CACADAS.get(p.getUUID());
        if (c != null && ev.getSource().getEntity().getUUID().equals(c.mob)) ev.setAmount(Math.max(ev.getAmount(), p.getMaxHealth() * 0.12f));
    }

    /** Morte que sobrou de uma caçada antiga (o servidor reiniciou, por exemplo) não entra no mundo. */
    @SubscribeEvent
    public void sobra(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !ev.getEntity().getTags().contains(TAG)) return;
        for (Cacada c : CACADAS.values()) if (ev.getEntity().getUUID().equals(c.mob)) return;
        ev.setCanceled(true);
    }

    // ------------------------------------------------------------------ relógio e sorteio

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = ev.getServer();
        relogio++;
        if (relogio % 10 == 0 && !CACADAS.isEmpty()) for (Cacada c : new ArrayList<>(CACADAS.values())) perseguir(c, srv);
        if (relogio % SORTEIO != 0) return;
        List<ServerPlayer> aptos = aptos(srv);
        if (aptos.size() < 2 || ThreadLocalRandom.current().nextInt(100) >= CHANCE) return;
        soltar(aptos.get(ThreadLocalRandom.current().nextInt(aptos.size())));
    }

    private static void guardar(Cacada c, Mob m) {
        if (m == null) return;
        if (m.isAlive()) c.vida = m.getHealth();
        m.discard();
        c.mob = null;
    }

    private static void perseguir(Cacada c, MinecraftServer srv) {
        ServerPlayer alvo = srv.getPlayerList().getPlayer(c.alvo);
        Mob m = achar(srv, c.mob);
        // alvo fora do jogo, em duelo ou fora de alcance das regras: a Morte some e espera
        if (alvo == null || alvo.isCreative() || alvo.isSpectator() || X1.emDuelo(alvo)) { guardar(c, m); c.barra.removeAllPlayers(); return; }
        if (!alvo.isAlive()) return;
        if (c.espera > 0) {   // entre as duas fases
            c.espera -= 10;
            if (!adotar(c, alvo) && c.espera <= 0) invocar(c, alvo);
            return;
        }
        if (m == null || !m.isAlive()) {
            if (!adotar(c, alvo)) invocar(c, alvo);
            m = achar(srv, c.mob);
            if (m == null) return;
        } else if (m.level() != alvo.level() || m.distanceToSqr(alvo) > 48 * 48) {   // o alvo fugiu para longe: ela vai atrás
            guardar(c, m);
            invocar(c, alvo);
            alvo.displayClientMessage(Component.literal("☠ Não adianta fugir: a Morte está logo atrás de você.").withStyle(ChatFormatting.DARK_RED), true);
            m = achar(srv, c.mob);
            if (m == null) return;
        }
        vida(m);
        if (m.getTarget() != alvo) m.setTarget(alvo);
        c.vida = m.getHealth();
        c.ultimo = m.position();
        c.nivel = (ServerLevel) m.level();
        c.barra.setProgress(Math.max(0f, Math.min(1f, (c.vida + (c.fase == 1 ? FASE : 0)) / (FASE * 2))));
        for (ServerPlayer o : new ArrayList<>(c.barra.getPlayers()))
            if (o != alvo && (o.level() != m.level() || o.distanceToSqr(m) > 64 * 64)) c.barra.removePlayer(o);
        c.barra.addPlayer(alvo);
        for (ServerPlayer o : ((ServerLevel) m.level()).players()) if (o.distanceToSqr(m) <= 48 * 48) c.barra.addPlayer(o);
    }

    private static List<ServerPlayer> aptos(MinecraftServer srv) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.level().dimension() != Level.OVERWORLD || p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            if (Math.abs(p.getX()) > 15000 || Math.abs(p.getZ()) > 15000) continue;   // torre, fendas e salas distantes
            if (X1.emDuelo(p) || Karin.naArea(p.level(), p.getX(), p.getZ())) continue;
            l.add(p);
        }
        return l;
    }

    /** Derruba o caderno a 30–60 blocos do jogador, com raio e aviso geral. */
    public static void soltar(ServerPlayer perto) {
        ServerLevel nv = perto.serverLevel();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2), dist = 30 + r.nextDouble(30);
        int x = (int) Math.floor(perto.getX() + Math.cos(a) * dist), z = (int) Math.floor(perto.getZ() + Math.sin(a) * dist);
        int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        ItemEntity it = new ItemEntity(nv, x + 0.5, y + 1.2, z + 0.5, new ItemStack(CADERNO.get()));
        it.setDeltaMovement(0, 0, 0);
        it.setPickUpDelay(60);
        it.lifespan = 24_000;
        it.setGlowingTag(true);
        it.setInvulnerable(true);
        nv.addFreshEntity(it);
        LightningBolt raio = EntityType.LIGHTNING_BOLT.create(nv);
        if (raio != null) {
            raio.moveTo(x + 0.5, y, z + 0.5);
            raio.setVisualOnly(true);
            nv.addFreshEntity(raio);
        }
        nv.getServer().getPlayerList().broadcastSystemMessage(Component.literal("📓 Um caderno negro caiu do céu perto de " + x + ", " + z
                + ". Quem pegar pode escrever um nome... Ele some em 20 minutos.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        for (ServerPlayer o : nv.getServer().getPlayerList().getPlayers()) o.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.8f, 0.6f);
        FrxWorld.LOG.info("Death Note caiu em {} {} {} (perto de {}).", x, y, z, perto.getGameProfile().getName());
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("deathnote")
                        .then(Commands.literal("soltar").executes(ctx -> {
                            soltar(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("dar").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (!p.getInventory().add(new ItemStack(CADERNO.get()))) p.drop(new ItemStack(CADERNO.get()), false);
                            return 1;
                        }))));
    }
}
