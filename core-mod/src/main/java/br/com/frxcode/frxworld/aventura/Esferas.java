package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.armas.Armas;
import br.com.frxcode.frxworld.clas.Clas;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.racas.DefRacas;
import br.com.frxcode.frxworld.racas.RacasJogador;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.Rede;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * As 7 Esferas do Dragão: um conjunto só no servidor.
 *  - 1 a 4 estrelas: enterradas no mundo normal (aparecem para quem chega a 3 blocos do ponto, em qualquer altura);
 *    5: no Nether; 6: com o Dragão do Fim; 7: com a Rainha da Neve da Floresta do Crepúsculo.
 *  - Radar do Dragão (mercador, 50 ouro): clique para ver onde está cada esfera, inclusive com qual jogador.
 *  - Quem morre carregando uma esfera deixa ela cair no chão (não vai para o túmulo). No chão ela brilha e não some.
 *  - Esfera sem sinal (baú, jogador offline, perdida) por 3 dias volta a se esconder; a antiga vira pó.
 *  - Com as 7 no inventário, ao ar livre no mundo normal: clique com uma esfera para chamar Shenlong, que realiza 1 de 8 desejos.
 *  - Depois do desejo as esferas viram pedra por 7 dias reais e então se espalham de novo.
 * Estado em <mundo>/frx_esferas.json.
 */
public class Esferas {
    private static final DeferredRegister<Item> ITENS = DeferredRegister.create(ForgeRegistries.ITEMS, FrxWorld.MOD_ID);
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item>[] ESFERA = new RegistryObject[8];
    static { for (int i = 1; i <= 7; i++) { final int n = i; ESFERA[i] = ITENS.register("esfera_" + i, () -> new ItemEsfera(n)); } }
    public static final RegistryObject<Item> RADAR = ITENS.register("radar_dragao", ItemRadar::new);

    public static void registrar(IEventBus modBus) { ITENS.register(modBus); }

    private static final long DIA = 24 * 3_600_000L, PEDRA = 7 * DIA, SEM_SINAL = 3 * DIA, MES = 30 * DIA;
    private static final String SERIE = "frx_serie", TAG_SHEN = "frx_shenlong";
    private static final ResourceKey<Level> CREPUSCULO = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation("twilightforest", "twilight_forest"));
    private static final ResourceLocation RAINHA = new ResourceLocation("twilightforest", "snow_queen");

    public static final class ItemEsfera extends Item {
        final int n;
        ItemEsfera(int n) { super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()); this.n = n; }
        @Override public boolean isFoil(ItemStack s) { return true; }
        @Override public InteractionResultHolder<ItemStack> use(Level nv, Player p, InteractionHand m) {
            if (p instanceof ServerPlayer sp) invocar(sp);
            return InteractionResultHolder.success(p.getItemInHand(m));
        }
        @Override public void appendHoverText(ItemStack s, Level nv, List<Component> l, TooltipFlag f) {
            l.add(Component.literal("★".repeat(n)).withStyle(ChatFormatting.RED));
            l.add(Component.literal("Junte as 7 e clique com uma delas ao ar livre.").withStyle(ChatFormatting.GRAY));
            l.add(Component.literal("Quem morre com ela deixa cair.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static final class ItemRadar extends Item {
        ItemRadar() { super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)); }
        @Override public InteractionResultHolder<ItemStack> use(Level nv, Player p, InteractionHand m) {
            if (p instanceof ServerPlayer sp) { radar(sp); p.getCooldowns().addCooldown(this, 40); }
            return InteractionResultHolder.success(p.getItemInHand(m));
        }
        @Override public void appendHoverText(ItemStack s, Level nv, List<Component> l, TooltipFlag f) {
            l.add(Component.literal("Clique para localizar as Esferas do Dragão.").withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ estado

    /** estado: "oculta" (dim, x, z), "chefe" (espera o chefão), "solta" (o item existe por aí). */
    private static final class Esf { String estado = "oculta", dim = "minecraft:overworld"; int x, z, serie = 1; long visto; }

    private static final Esf[] E = new Esf[8];
    private static long pedraAte;
    private static final Map<String, Long> CLAS_XP = new HashMap<>();
    private static boolean carregado, sujo;
    private static int ticks;

    /** Onde cada esfera solta foi vista agora (não é salvo). */
    private static final UUID[] DONO = new UUID[8];
    private static final ItemEntity[] NO_CHAO = new ItemEntity[8];

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_esferas.json"); }

    private static void carregar(MinecraftServer s) {
        carregado = true;
        boolean novo = true;
        try {
            if (Files.exists(arquivo(s))) {
                JsonObject o = JsonParser.parseString(Files.readString(arquivo(s), StandardCharsets.UTF_8)).getAsJsonObject();
                pedraAte = o.get("pedraAte").getAsLong();
                JsonObject es = o.getAsJsonObject("esferas");
                for (int i = 1; i <= 7; i++) {
                    JsonObject j = es.getAsJsonObject(String.valueOf(i));
                    Esf e = new Esf();
                    e.estado = j.get("estado").getAsString(); e.dim = j.get("dim").getAsString();
                    e.x = j.get("x").getAsInt(); e.z = j.get("z").getAsInt(); e.serie = j.get("serie").getAsInt(); e.visto = j.get("visto").getAsLong();
                    E[i] = e;
                }
                if (o.has("clas")) for (String k : o.getAsJsonObject("clas").keySet()) CLAS_XP.put(k, o.getAsJsonObject("clas").get(k).getAsLong());
                novo = false;
            }
        } catch (Exception ex) { FrxWorld.LOG.warn("Esferas: não li frx_esferas.json: {}", ex.toString()); }
        if (novo) { for (int i = 1; i <= 7; i++) { E[i] = new Esf(); esconder(s, i); } salvar(s); }
    }

    private static void salvar(MinecraftServer s) {
        sujo = false;
        JsonObject o = new JsonObject(), es = new JsonObject(), cl = new JsonObject();
        o.addProperty("pedraAte", pedraAte);
        for (int i = 1; i <= 7; i++) {
            JsonObject j = new JsonObject();
            j.addProperty("estado", E[i].estado); j.addProperty("dim", E[i].dim); j.addProperty("x", E[i].x); j.addProperty("z", E[i].z);
            j.addProperty("serie", E[i].serie); j.addProperty("visto", E[i].visto);
            es.add(String.valueOf(i), j);
        }
        CLAS_XP.forEach(cl::addProperty);
        o.add("esferas", es); o.add("clas", cl);
        try { Files.writeString(arquivo(s), new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8); }
        catch (Exception ex) { FrxWorld.LOG.warn("Esferas: não gravei frx_esferas.json: {}", ex.toString()); }
    }

    /** Esconde de novo a esfera n (a série sobe: o item antigo, se existir, vira pó). */
    private static void esconder(MinecraftServer s, int n) {
        Esf e = E[n];
        e.serie++;
        e.visto = System.currentTimeMillis();
        DONO[n] = null; NO_CHAO[n] = null;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble(Math.PI * 2);
        if (n <= 4) {
            BlockPos sp = s.overworld().getSharedSpawnPos();
            double d = r.nextDouble(1500, 5000);
            e.estado = "oculta"; e.dim = "minecraft:overworld"; e.x = sp.getX() + (int) (Math.cos(ang) * d); e.z = sp.getZ() + (int) (Math.sin(ang) * d);
        } else if (n == 5) {
            double d = r.nextDouble(200, 1200);
            e.estado = "oculta"; e.dim = "minecraft:the_nether"; e.x = (int) (Math.cos(ang) * d); e.z = (int) (Math.sin(ang) * d);
        } else { e.estado = "chefe"; e.dim = n == 6 ? "minecraft:the_end" : CREPUSCULO.location().toString(); e.x = 0; e.z = 0; }
        sujo = true;
    }

    private static boolean emPedra() { return System.currentTimeMillis() < pedraAte; }

    private static ItemStack criar(int n) {
        ItemStack s = new ItemStack(ESFERA[n].get());
        s.getOrCreateTag().putInt(SERIE, E[n].serie);
        return s;
    }

    private static int numero(ItemStack s) { return s.getItem() instanceof ItemEsfera e ? e.n : 0; }
    private static boolean valida(ItemStack s) { int n = numero(s); return n > 0 && carregado && s.hasTag() && s.getTag().getInt(SERIE) == E[n].serie && "solta".equals(E[n].estado); }

    private static void entregar(ServerPlayer p, int n, String como) {
        E[n].estado = "solta"; E[n].visto = System.currentTimeMillis(); sujo = true;
        ItemStack s = criar(n);
        if (!p.getInventory().add(s)) p.drop(s, false);
        ServerLevel nv = p.serverLevel();
        nv.sendParticles(LARANJA, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 0.8, 0.6, 0.1);
        nv.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1.2f);
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + p.getGameProfile().getName() + " " + como + " a Esfera de " + n + (n == 1 ? " estrela" : " estrelas") + "! ✦")
                .withStyle(ChatFormatting.GOLD), false);
        // título e som para o servidor inteiro: ninguém perde a notícia
        Component t1 = Component.literal("★".repeat(n)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component t2 = Component.literal(p.getGameProfile().getName() + " " + como + " a Esfera de " + n + (n == 1 ? " estrela" : " estrelas")).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            o.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));
            o.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(t1));
            o.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(t2));
            if (o != p) o.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.7f, 1.2f);
        }
    }

    private static final DustParticleOptions LARANJA = new DustParticleOptions(new Vector3f(1f, 0.6f, 0.1f), 1.4f), VERDE = new DustParticleOptions(new Vector3f(0.2f, 0.9f, 0.3f), 2f);

    // ------------------------------------------------------------------ varredura

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        if (!carregado) carregar(s);
        if (ritual != null) tickRitual(s);
        if (++ticks % 20 != 0) return;
        long agora = System.currentTimeMillis();
        if (pedraAte > 0 && agora >= pedraAte) {
            pedraAte = 0;
            for (int i = 1; i <= 7; i++) esconder(s, i);
            s.getPlayerList().broadcastSystemMessage(Component.literal("✦ As Esferas do Dragão despertaram e se espalharam pelo mundo outra vez! ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        }
        for (int i = 1; i <= 7; i++) { DONO[i] = null; if (NO_CHAO[i] != null && !NO_CHAO[i].isAlive()) NO_CHAO[i] = null; }
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            // esferas no inventário: confere a série e anota o dono
            for (int k = 0; k < p.getInventory().getContainerSize(); k++) {
                ItemStack it = p.getInventory().getItem(k);
                int n = numero(it);
                if (n == 0) continue;
                if (!valida(it) || emPedra()) { p.getInventory().setItem(k, ItemStack.EMPTY); p.displayClientMessage(Component.literal("A esfera virou pó nas suas mãos.").withStyle(ChatFormatting.GRAY), false); continue; }
                DONO[n] = p.getUUID(); E[n].visto = agora;
            }
            if (emPedra() || p.isSpectator() || !p.isAlive()) continue;
            // esferas enterradas: aparecem para quem passa em cima
            String dim = p.level().dimension().location().toString();
            for (int n = 1; n <= 5; n++) {
                Esf e = E[n];
                if (!"oculta".equals(e.estado) || !e.dim.equals(dim)) continue;
                if (Math.abs(p.getX() - e.x) <= 3 && Math.abs(p.getZ() - e.z) <= 3) entregar(p, n, "encontrou");
            }
            bencao(p);
        }
        for (int n = 1; n <= 7; n++) {
            if (NO_CHAO[n] != null) E[n].visto = agora;
            if ("solta".equals(E[n].estado) && agora - E[n].visto > SEM_SINAL && ritual == null) {
                esconder(s, n);
                s.getPlayerList().broadcastSystemMessage(Component.literal("✦ A Esfera de " + n + (n == 1 ? " estrela" : " estrelas") + " ficou tempo demais sem sinal e voltou a se esconder pelo mundo.").withStyle(ChatFormatting.GOLD), false);
            }
        }
        if (sujo || ticks % 6000 == 0) salvar(s);
    }

    /** Esfera no chão: brilha, não some e não queima. A de série velha vira pó. */
    @SubscribeEvent
    public void noChao(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide()) return;
        Entity e = ev.getEntity();
        if (e.getTags().contains(TAG_SHEN) && ritual == null && !criando) { ev.setCanceled(true); return; }
        if (!(e instanceof ItemEntity it)) return;
        int n = numero(it.getItem());
        if (n == 0) return;
        if (!carregado) carregar(ev.getLevel().getServer());
        if (!valida(it.getItem()) || emPedra()) { ev.setCanceled(true); return; }
        it.setUnlimitedLifetime();
        it.setGlowingTag(true);
        it.setInvulnerable(true);
        NO_CHAO[n] = it;
    }

    /** Quem morre com esferas deixa elas no chão (fora do túmulo); com Imortalidade, o resto dos itens volta ao renascer. */
    private static final Map<UUID, List<ItemStack>> GUARDADOS = new HashMap<>();
    private static final Map<UUID, int[]> XP = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoCair(LivingDropsEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        ServerLevel nv = p.serverLevel();
        for (Iterator<ItemEntity> it = ev.getDrops().iterator(); it.hasNext(); ) {
            ItemEntity d = it.next();
            int n = numero(d.getItem());
            if (n == 0) continue;
            it.remove();
            nv.addFreshEntity(d);
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("☄ " + p.getGameProfile().getName() + " caiu carregando a Esfera de " + n + (n == 1 ? " estrela" : " estrelas")
                    + "! Ela ficou no chão perto de " + p.getBlockX() + ", " + p.getBlockZ() + ".").withStyle(ChatFormatting.GOLD), false);
        }
        if (!imortal(p)) return;
        List<ItemStack> l = GUARDADOS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        for (ItemEntity d : ev.getDrops()) l.add(d.getItem().copy());
        ev.getDrops().clear();
        ev.setCanceled(true);
    }

    @SubscribeEvent
    public void semPerderXp(LivingExperienceDropEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && imortal(p)) {
            XP.put(p.getUUID(), new int[]{p.experienceLevel, p.totalExperience, Float.floatToIntBits(p.experienceProgress)});
            ev.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> l = GUARDADOS.remove(p.getUUID());
        if (l != null) {
            for (ItemStack s : l) if (!p.getInventory().add(s)) p.drop(s, false);
            if (!DeathNote.semPena(p)) p.displayClientMessage(Component.literal("✦ A Imortalidade de Shenlong devolveu tudo o que você carregava.").withStyle(ChatFormatting.GREEN), false);
        }
        int[] x = XP.remove(p.getUUID());
        if (x != null) { p.experienceLevel = x[0]; p.totalExperience = x[1]; p.experienceProgress = Float.intBitsToFloat(x[2]); p.giveExperiencePoints(0); }
    }

    /** Chefões que guardam as esferas 6 e 7. */
    @SubscribeEvent
    public void chefe(LivingDeathEvent ev) {
        if (!carregado || emPedra() || ev.getEntity().level().isClientSide()) return;
        Entity m = ev.getEntity();
        int n = 0;
        if (m instanceof EnderDragon && m.level().dimension() == Level.END) n = 6;
        else if (RAINHA.equals(ForgeRegistries.ENTITY_TYPES.getKey(m.getType())) && m.level().dimension() == CREPUSCULO) n = 7;
        if (n == 0 || !"chefe".equals(E[n].estado)) return;
        ServerPlayer p = ev.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        if (p == null && m.level() instanceof ServerLevel nv) p = (ServerPlayer) nv.getNearestPlayer(m, 160);
        if (p != null) entregar(p, n, n == 6 ? "arrancou do Dragão do Fim" : "arrancou da Rainha da Neve");
    }

    // ------------------------------------------------------------------ radar

    private static String rumo(double dx, double dz) {
        String[] r = {"sul", "sudoeste", "oeste", "noroeste", "norte", "nordeste", "leste", "sudeste"};
        double a = Math.toDegrees(Math.atan2(-dx, dz));
        return r[(int) Math.floor(((a + 360 + 22.5) % 360) / 45)];
    }

    private static String nomeDim(String d) {
        return switch (d) { case "minecraft:overworld" -> "mundo normal"; case "minecraft:the_nether" -> "Nether"; case "minecraft:the_end" -> "End";
            case "twilightforest:twilight_forest" -> "Floresta do Crepúsculo"; default -> d; };
    }

    private static String onde(ServerPlayer p, String dim, double x, double z) {
        if (!p.level().dimension().location().toString().equals(dim)) return "em outra dimensão (" + nomeDim(dim) + ")";
        double dx = x - p.getX(), dz = z - p.getZ();
        int d = (int) Math.sqrt(dx * dx + dz * dz);
        return d <= 5 ? "bem aqui!" : d + " blocos a " + rumo(dx, dz);
    }

    private static void radar(ServerPlayer p) {
        if (!carregado) carregar(p.server);
        p.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
        p.sendSystemMessage(Component.literal("═══ Radar do Dragão ═══").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        if (emPedra()) {
            long falta = pedraAte - System.currentTimeMillis();
            p.sendSystemMessage(Component.literal("Nenhum sinal: as esferas viraram pedra. Despertam em " + (falta / DIA) + " d " + (falta % DIA / 3_600_000L) + " h.").withStyle(ChatFormatting.GRAY));
            return;
        }
        ListTag pontos = new ListTag(), linhas = new ListTag();
        String aqui = p.level().dimension().location().toString();
        for (int n = 1; n <= 7; n++) {
            Esf e = E[n];
            String t;
            ChatFormatting cor = ChatFormatting.WHITE;
            // ponto na tela do radar: só o que está nesta dimensão e tem lugar conhecido
            double rx = Double.NaN, rz = 0;
            if ("oculta".equals(e.estado) && e.dim.equals(aqui)) { rx = e.x; rz = e.z; }
            else if ("solta".equals(e.estado) && DONO[n] != null) {
                ServerPlayer d = p.server.getPlayerList().getPlayer(DONO[n]);
                if (d != null && d != p && d.level() == p.level()) { rx = d.getX(); rz = d.getZ(); }
            } else if ("solta".equals(e.estado) && NO_CHAO[n] != null && NO_CHAO[n].level() == p.level()) { rx = NO_CHAO[n].getX(); rz = NO_CHAO[n].getZ(); }
            if (!Double.isNaN(rx)) { CompoundTag pt = new CompoundTag(); pt.putInt("n", n); pt.putDouble("x", rx); pt.putDouble("z", rz); pontos.add(pt); }
            if ("oculta".equals(e.estado)) t = onde(p, e.dim, e.x, e.z);
            else if ("chefe".equals(e.estado)) { t = n == 6 ? "com o Dragão do Fim" : "com a Rainha da Neve, na Floresta do Crepúsculo"; cor = ChatFormatting.LIGHT_PURPLE; }
            else if (DONO[n] != null) {
                ServerPlayer d = p.server.getPlayerList().getPlayer(DONO[n]);
                if (d == p) { t = "com você"; cor = ChatFormatting.GREEN; }
                else if (d != null) { t = "com " + d.getGameProfile().getName() + ", " + onde(p, d.level().dimension().location().toString(), d.getX(), d.getZ()); cor = ChatFormatting.RED; }
                else t = "sinal perdido";
            } else if (NO_CHAO[n] != null) { t = "caída no chão, " + onde(p, NO_CHAO[n].level().dimension().location().toString(), NO_CHAO[n].getX(), NO_CHAO[n].getZ()); cor = ChatFormatting.YELLOW; }
            else { t = "sinal perdido (guardada ou com alguém offline)"; cor = ChatFormatting.GRAY; }
            p.sendSystemMessage(Component.literal("★" + n + " ").withStyle(ChatFormatting.GOLD).append(Component.literal(t).withStyle(cor)));
            linhas.add(StringTag.valueOf("★" + n + " " + t));
        }
        CompoundTag d = new CompoundTag();
        d.put("pontos", pontos);
        d.put("linhas", linhas);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("radar", d));
    }

    // ------------------------------------------------------------------ Shenlong

    private static final class Ritual {
        UUID jogador; Vec3 centro; int t, fim = -1, pagina; boolean chuvaAntes, trovaoAntes; boolean menuAberto;
        final List<Entity> corpo = new ArrayList<>();
        final List<Vec3> pontos = new ArrayList<>(); Vec3 cabeca; float giro;
    }

    private static Ritual ritual;
    private static boolean criando;
    private static final int SEGMENTOS = 64;

    private static int quantas(ServerPlayer p) {
        boolean[] tem = new boolean[8];
        for (int k = 0; k < p.getInventory().getContainerSize(); k++) { ItemStack it = p.getInventory().getItem(k); if (valida(it)) tem[numero(it)] = true; }
        int q = 0;
        for (int n = 1; n <= 7; n++) if (tem[n]) q++;
        return q;
    }

    private static void invocar(ServerPlayer p) {
        if (!carregado) carregar(p.server);
        if (ritual != null) {   // fechou a tela sem querer: clicar de novo reabre os desejos
            if (p.getUUID().equals(ritual.jogador) && ritual.menuAberto && ritual.fim < 0) abrirMenu(p, 0, "");
            return;
        }
        int q = quantas(p);
        if (q < 7) { p.displayClientMessage(Component.literal("Você tem " + q + " de 7 esferas. O dragão só atende quem reúne todas.").withStyle(ChatFormatting.GOLD), true); return; }
        ServerLevel nv = p.serverLevel();
        if (nv.dimension() != Level.OVERWORLD || !nv.canSeeSky(p.blockPosition().above())) {
            p.displayClientMessage(Component.literal("Shenlong só aparece no mundo normal, a céu aberto.").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        Ritual r = new Ritual();
        r.jogador = p.getUUID();
        Vec3 frente = p.getLookAngle().multiply(1, 0, 1);
        if (frente.lengthSqr() < 0.01) frente = new Vec3(0, 0, 1);
        frente = frente.normalize();
        r.centro = p.position().add(frente.scale(14));
        r.chuvaAntes = nv.isRaining(); r.trovaoAntes = nv.isThundering();
        // caminho do corpo: espiral que sobe; a cabeça se inclina para o jogador
        for (int i = 0; i < SEGMENTOS; i++) {
            double s = i / (double) (SEGMENTOS - 1), ang = s * Math.PI * 3.5, raio = 5 - 1.5 * s;
            r.pontos.add(r.centro.add(Math.cos(ang) * raio, s * 30, Math.sin(ang) * raio));
        }
        Vec3 topo = r.pontos.get(SEGMENTOS - 1);
        r.cabeca = topo.add(frente.scale(-4)).add(0, -1.5, 0);
        r.giro = (float) Math.toDegrees(Math.atan2(-(p.getX() - r.cabeca.x), p.getZ() - r.cabeca.z));
        ritual = r;
        nv.setWeatherParameters(0, 6000, true, true);
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("⚡ O céu escurece... " + p.getGameProfile().getName() + " reuniu as 7 Esferas do Dragão! ⚡").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        nv.playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2f, 0.6f);
    }

    private static ListTag fl(float... v) { ListTag l = new ListTag(); for (float f : v) l.add(FloatTag.valueOf(f)); return l; }

    private static void bloco(ServerLevel nv, Ritual r, Vec3 pos, String bloco, float sx, float sy, float sz, float giro) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        CompoundTag bs = new CompoundTag();
        bs.putString("Name", bloco);
        tag.put("block_state", bs);
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", fl(0, 0, 0, 1)); tr.put("right_rotation", fl(0, 0, 0, 1));
        tr.put("translation", fl(-sx / 2, -sy / 2, -sz / 2)); tr.put("scale", fl(sx, sy, sz));
        tag.put("transformation", tr);
        CompoundTag br = new CompoundTag();
        br.putInt("sky", 15); br.putInt("block", 15);
        tag.put("brightness", br);
        tag.putFloat("view_range", 4f);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG_SHEN));
        tag.put("Tags", tags);
        criando = true;
        try {
            Entity e = EntityType.loadEntityRecursive(tag, nv, en -> { en.moveTo(pos.x, pos.y, pos.z, giro, 0f); return en; });
            if (e != null && nv.addFreshEntity(e)) r.corpo.add(e);
        } finally { criando = false; }
    }

    private static void raio(ServerLevel nv, Vec3 v) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(nv);
        if (b == null) return;
        b.moveTo(v.x, v.y, v.z); b.setVisualOnly(true); nv.addFreshEntity(b);
    }

    private static void fala(ServerLevel nv, Ritual r, String t) {
        Component c = Component.literal("[Shenlong] ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD).append(Component.literal(t).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer o : nv.players()) if (o.position().distanceTo(r.centro) < 96) o.sendSystemMessage(c);
    }

    private static void tickRitual(MinecraftServer s) {
        Ritual r = ritual;
        ServerLevel nv = s.overworld();
        ServerPlayer p = s.getPlayerList().getPlayer(r.jogador);
        int t = r.t++;
        if (r.fim < 0 && (p == null || !p.isAlive() || p.level() != nv || p.position().distanceTo(r.centro) > 60 || quantas(p) < 7 || t > 110 + 3600)) {
            fala(nv, r, "Não tenho a eternidade para esperar. Chamem-me quando souberem o que querem.");
            if (p != null) fecharMenu(p);
            r.fim = t + 40;
        }
        if (t < 100 && t % 20 == 0) { raio(nv, r.centro.add(ThreadLocalRandom.current().nextDouble(-8, 8), 0, ThreadLocalRandom.current().nextDouble(-8, 8))); }
        // o corpo sobe do chão, um segmento por tick
        int i = t - 20;
        if (i >= 0 && i < SEGMENTOS) {
            Vec3 pt = r.pontos.get(i);
            float tam = 1.6f + 1.2f * Math.min(1f, i / 12f);
            Vec3 prox = r.pontos.get(Math.min(SEGMENTOS - 1, i + 1));
            float giro = (float) Math.toDegrees(Math.atan2(-(prox.x - pt.x), prox.z - pt.z));
            bloco(nv, r, pt, i % 4 == 0 ? "minecraft:lime_concrete" : "minecraft:emerald_block", tam, tam, tam, giro);
            if (i % 2 == 0) bloco(nv, r, pt.add(0, -tam * 0.45, 0), "minecraft:yellow_terracotta", tam * 0.7f, tam * 0.35f, tam * 0.7f, giro);
            nv.sendParticles(VERDE, pt.x, pt.y, pt.z, 10, 1, 1, 1, 0.05);
            if (i % 8 == 0) nv.playSound(null, BlockPos.containing(pt), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3f, 0.5f);
        }
        if (i == SEGMENTOS) {
            // cabeça virada para quem chamou
            float g = r.giro;
            double rad = Math.toRadians(g);
            Vec3 f = new Vec3(-Math.sin(rad), 0, Math.cos(rad)), lado = new Vec3(Math.cos(rad), 0, Math.sin(rad));
            Vec3 c = r.cabeca;
            bloco(nv, r, c, "minecraft:emerald_block", 4f, 3.4f, 4.6f, g);
            bloco(nv, r, c.add(f.scale(3.4)).add(0, -0.5, 0), "minecraft:emerald_block", 2.8f, 2.2f, 2.6f, g);          // focinho
            bloco(nv, r, c.add(f.scale(3.6)).add(0, -1.7, 0), "minecraft:yellow_terracotta", 2.4f, 0.5f, 2.4f, g);      // mandíbula
            for (int k = -1; k <= 1; k += 2) {
                bloco(nv, r, c.add(f.scale(2.2)).add(lado.scale(1.5 * k)).add(0, 0.9, 0), "minecraft:redstone_block", 0.9f, 0.7f, 0.5f, g);   // olhos
                bloco(nv, r, c.add(f.scale(-1.2)).add(lado.scale(1.3 * k)).add(0, 3.2, 0), "minecraft:bone_block", 0.6f, 3.4f, 0.6f, g);      // chifres
                bloco(nv, r, c.add(f.scale(-2.6)).add(lado.scale(1.6 * k)).add(0, 4.6, 0), "minecraft:bone_block", 0.5f, 1.6f, 1.6f, g);
                bloco(nv, r, c.add(f.scale(4.2)).add(lado.scale(2.6 * k)).add(0, -0.9, 0), "minecraft:lime_concrete", 3.0f, 0.3f, 0.3f, g);   // bigodes
            }
            for (int k = 0; k < 4; k++) bloco(nv, r, c.add(f.scale(-1.5 - k * 1.3)).add(0, 2.1 - k * 0.2, 0), "minecraft:lime_concrete", 1.2f, 1.6f, 1.2f, g);   // juba
            raio(nv, r.centro);
            nv.playSound(null, BlockPos.containing(c), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6f, 0.5f);
        }
        if (t == 20 + SEGMENTOS + 20 && r.fim < 0) fala(nv, r, "Eu sou Shenlong. Vocês reuniram as sete esferas. Digam o seu desejo: realizarei apenas um.");
        if (t == 20 + SEGMENTOS + 50 && r.fim < 0 && p != null) { r.menuAberto = true; abrirMenu(p, 0, ""); }
        if (t % 5 == 0 && r.cabeca != null && i > SEGMENTOS) nv.sendParticles(VERDE, r.cabeca.x, r.cabeca.y, r.cabeca.z, 4, 2, 1.5, 2, 0.02);
        if (r.fim >= 0 && t >= r.fim) encerrar(nv);
    }

    private static void encerrar(ServerLevel nv) {
        Ritual r = ritual;
        if (r == null) return;
        for (Entity e : r.corpo) { nv.sendParticles(VERDE, e.getX(), e.getY(), e.getZ(), 6, 0.8, 0.8, 0.8, 0.1); e.discard(); }
        nv.playSound(null, BlockPos.containing(r.centro), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4f, 0.4f);
        if (!r.chuvaAntes) nv.setWeatherParameters(6000, 0, false, false);
        else nv.setWeatherParameters(0, 6000, true, r.trovaoAntes);
        ritual = null;
    }

    // ------------------------------------------------------------------ desejos

    private static final String[][] DESEJOS = {
            {"Poder", "+50% do seu nível atual (no mínimo +20 níveis)."},
            {"Riqueza", "1.000 moedas de ouro, direto no seu banco."},
            {"Tesouro", "Um item qualquer do servidor, à sua escolha."},
            {"Arsenal lendário", "Um conjunto completo de armadura lendária, já forjado em +10."},
            {"Divindade", "Libera uma raça lendária à sua escolha, até mesmo a raça Deus."},
            {"Bênção do Dragão", "+5 corações e +10% de dano, para sempre."},
            {"Imortalidade", "Por 30 dias, morrer não tira itens, XP nem nível."},
            {"Glória ao clã", "XP em dobro para todos dos seus clãs por 30 dias."}};
    private static final String[] RACAS = {"anjo", "rei_demonio", "dragao", "celestial", "saiyajin", "deus"};
    private static final Set<String> PROIBIDOS = Set.of("minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:command_block_minecart",
            "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "minecraft:barrier", "minecraft:bedrock", "minecraft:debug_stick", "minecraft:light",
            "minecraft:spawner", "minecraft:end_portal_frame", "minecraft:knowledge_book");

    private static void abrirMenu(ServerPlayer p, int pagina, String msg) {
        if (ritual != null) ritual.pagina = pagina;
        CompoundTag d = new CompoundTag();
        d.putInt("pagina", pagina);
        d.putString("msg", msg);
        ListTag l = new ListTag();
        if (pagina == 0) for (int i = 0; i < DESEJOS.length; i++) { CompoundTag o = new CompoundTag(); o.putInt("n", i + 1); o.putString("nome", DESEJOS[i][0]); o.putString("desc", DESEJOS[i][1]); o.putString("texto", ""); l.add(o); }
        if (pagina == 4) for (int i = 0; i < 3; i++) {
            String c = br.com.frxcode.frxworld.armas.Lendarias.CONJUNTOS[i];
            CompoundTag o = new CompoundTag(); o.putInt("n", 4); o.putString("texto", c);
            o.putString("nome", "Armadura " + Character.toUpperCase(c.charAt(0)) + c.substring(1)); o.putString("desc", "Elmo, peitoral, perneiras e botas em +10."); l.add(o);
        }
        if (pagina == 5) for (String id : RACAS) {
            CompoundTag o = new CompoundTag(); o.putInt("n", 5); o.putString("texto", id); o.putString("nome", DefRacas.nome(id));
            o.putString("desc", RacasJogador.liberada(p, id) ? "Você já tem esta raça liberada." : DefRacas.de(id).descricao()); o.putBoolean("travado", RacasJogador.liberada(p, id)); l.add(o);
        }
        d.put("opcoes", l);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("shenlong", d));
    }

    private static void fecharMenu(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        d.putBoolean("fechar", true);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("shenlong", d));
    }

    private static boolean proibido(Item i) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(i);
        if (id == null || i == net.minecraft.world.item.Items.AIR || PROIBIDOS.contains(id.toString()) || i instanceof SpawnEggItem || i instanceof ItemEsfera) return true;
        if (id.getNamespace().equals(FrxWorld.MOD_ID) && id.getPath().startsWith("alvorecer_")) return true;   // relíquias únicas dos enigmas
        // armas absurdas (ex.: o machado vermelho de milhares de dano): nada com 100 ou mais de dano base
        double dano = 0;
        for (AttributeModifier m : new ItemStack(i).getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) dano += m.getAmount();
        return dano >= 100;
    }

    /** Pedido da tela: numero 0 = voltar à lista; 1..8 = desejo (texto = escolha da subpágina ou id do item). */
    public static void desejo(ServerPlayer p, int numero, String texto) {
        Ritual r = ritual;
        if (r == null || !p.getUUID().equals(r.jogador) || !r.menuAberto || r.fim >= 0 || quantas(p) < 7) return;
        if (numero == 0) { abrirMenu(p, 0, ""); return; }
        if (numero < 1 || numero > 8) return;
        ServerLevel nv = p.serverLevel();
        String feito;
        long agora = System.currentTimeMillis();
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        switch (numero) {
            case 1 -> {
                if (!Progressao.temDados(p)) return;
                int n = Progressao.dados(p).nivel(), ganho = Math.max(20, (n + 1) / 2);
                Progressao.definirNivel(p, n + ganho);
                Rede.sincronizar(p);
                feito = "Poder (do nível " + n + " para o " + Progressao.dados(p).nivel() + ")";
            }
            case 2 -> { Banco.definirSaldo(p, Banco.saldo(p) + 100_000); feito = "Riqueza (1.000 moedas de ouro)"; }
            case 3 -> {
                if (texto.isEmpty()) { abrirMenu(p, 3, ""); return; }
                ResourceLocation id = ResourceLocation.tryParse(texto);
                Item i = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                if (i == null || proibido(i)) { abrirMenu(p, 3, "Esse item está além até do meu poder. Escolha outro."); return; }
                ItemStack s = new ItemStack(i);
                String nome = s.getHoverName().getString();
                if (!p.getInventory().add(s)) p.drop(s, false);
                feito = "Tesouro (" + nome + ")";
            }
            case 4 -> {
                if (!List.of(br.com.frxcode.frxworld.armas.Lendarias.CONJUNTOS).contains(texto)) { abrirMenu(p, 4, ""); return; }
                for (String peca : br.com.frxcode.frxworld.armas.Lendarias.PECAS) {
                    Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(FrxWorld.MOD_ID, texto + "_" + peca));
                    if (i == null) continue;
                    ItemStack s = new ItemStack(i);
                    Armas.definirNivel(s, 10);
                    if (!p.getInventory().add(s)) p.drop(s, false);
                }
                feito = "Arsenal lendário (armadura " + texto + " +10)";
            }
            case 5 -> {
                if (!List.of(RACAS).contains(texto)) { abrirMenu(p, 5, ""); return; }
                if (!RacasJogador.liberarRaca(p, texto)) { abrirMenu(p, 5, "Essa raça você já tem. Escolha outra."); return; }
                feito = "Divindade (raça " + DefRacas.nome(texto) + ")";
            }
            case 6 -> { pers.putInt("frx_bencao", pers.getInt("frx_bencao") + 1); feito = "Bênção do Dragão"; }
            case 7 -> { pers.putLong("frx_imortal_ate", Math.max(agora, pers.getLong("frx_imortal_ate")) + MES); feito = "Imortalidade (30 dias)"; }
            default -> {
                List<UUID> clas = Clas.idsDe(p);
                if (clas.isEmpty()) { abrirMenu(p, 0, "Você não pertence a nenhum clã. Escolha outro desejo."); return; }
                for (UUID c : clas) CLAS_XP.put(c.toString(), Math.max(agora, CLAS_XP.getOrDefault(c.toString(), 0L)) + MES);
                feito = "Glória ao clã (XP em dobro por 30 dias)";
            }
        }
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        bencao(p);
        // as esferas somem e viram pedra
        for (int k = 0; k < p.getInventory().getContainerSize(); k++) if (numero(p.getInventory().getItem(k)) > 0) p.getInventory().setItem(k, ItemStack.EMPTY);
        pedraAte = agora + PEDRA;
        for (int n = 1; n <= 7; n++) { E[n].serie++; DONO[n] = null; NO_CHAO[n] = null; }
        salvar(p.server);
        fecharMenu(p);
        r.menuAberto = false;
        r.fim = r.t + 80;
        fala(nv, r, "Seu desejo foi realizado. Adeus.");
        raio(nv, r.centro);
        nv.sendParticles(LARANJA, p.getX(), p.getY() + 1, p.getZ(), 150, 1.5, 2, 1.5, 0.4);
        nv.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.5f, 0.8f);
        MutableComponent c = Component.literal("✦ Shenlong realizou o desejo de " + p.getGameProfile().getName() + ": " + feito + ". As esferas viraram pedra por 7 dias. ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        p.server.getPlayerList().broadcastSystemMessage(c, false);
        FrxWorld.LOG.info("Shenlong: {} pediu {}.", p.getGameProfile().getName(), feito);
    }

    // ------------------------------------------------------------------ efeitos dos desejos

    public static boolean imortal(Player p) { return DeathNote.semPena(p) || p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong("frx_imortal_ate") > System.currentTimeMillis(); }

    public static boolean xpEmDobro(ServerPlayer p) {
        if (CLAS_XP.isEmpty()) return false;
        long agora = System.currentTimeMillis();
        for (UUID c : Clas.idsDe(p)) if (CLAS_XP.getOrDefault(c.toString(), 0L) > agora) return true;
        return false;
    }

    private static final UUID B_VIDA = UUID.nameUUIDFromBytes("frxworld:shenlong:vida".getBytes()), B_DANO = UUID.nameUUIDFromBytes("frxworld:shenlong:dano".getBytes());

    private static void bencao(ServerPlayer p) {
        int n = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt("frx_bencao");
        AttributeInstance vida = p.getAttribute(Attributes.MAX_HEALTH), dano = p.getAttribute(Attributes.ATTACK_DAMAGE);
        if (vida != null && (vida.getModifier(B_VIDA) == null ? 0 : vida.getModifier(B_VIDA).getAmount()) != n * 10.0) {
            vida.removeModifier(B_VIDA);
            if (n > 0) vida.addTransientModifier(new AttributeModifier(B_VIDA, "FRX Bênção do Dragão", n * 10.0, AttributeModifier.Operation.ADDITION));
        }
        if (dano != null && (dano.getModifier(B_DANO) == null ? 0 : dano.getModifier(B_DANO).getAmount()) != n * 0.10) {
            dano.removeModifier(B_DANO);
            if (n > 0) dano.addTransientModifier(new AttributeModifier(B_DANO, "FRX Bênção do Dragão", n * 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ------------------------------------------------------------------ admin

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("esferas")
                        .then(Commands.literal("ver").executes(ctx -> {
                            if (!carregado) carregar(ctx.getSource().getServer());
                            for (int n = 1; n <= 7; n++) {
                                final int k = n;
                                ctx.getSource().sendSuccess(() -> Component.literal("★" + k + " " + E[k].estado + " " + nomeDim(E[k].dim) + " " + E[k].x + ", " + E[k].z), false);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("espalhar").executes(ctx -> {
                            if (!carregado) carregar(ctx.getSource().getServer());
                            pedraAte = 0;
                            for (int n = 1; n <= 7; n++) esconder(ctx.getSource().getServer(), n);
                            salvar(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("As 7 esferas foram escondidas de novo."), true);
                            return 1;
                        }))));
    }
}
