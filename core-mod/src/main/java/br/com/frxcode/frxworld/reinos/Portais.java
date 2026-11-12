package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Portais de masmorra: de tempos em tempos um portal com rank (F a S) abre perto de uma capital.
 * Chegar perto começa a incursão: 3 ondas de monstros do nível daquele rank, a última com o Guardião.
 * Fechou a tempo, todos por perto ganham XP e moedas. Se ninguém fechar, o portal se rompe e uma horda ataca a capital.
 */
public class Portais {
    public static final String TAG = "frx_portal";
    private static final long ABERTO_MS = 12 * 60_000L, LUTA_MS = 8 * 60_000L, INTERVALO_MS = 40 * 60_000L;
    private static final int CHANCE = 50, ONDAS = 3;

    private enum Fase { NADA, ABERTO, LUTA }
    private static Fase fase = Fase.NADA;
    private static Reino reino;
    private static BlockPos pos;
    private static int rank, onda;
    private static long limite, proximaTentativa = System.currentTimeMillis() + 15 * 60_000L;
    private static final Set<UUID> vivos = new HashSet<>();
    private static final Map<BlockPos, BlockState> salvos = new LinkedHashMap<>();

    public Portais() {}

    private static String nomeRank() { return ConfigJogo.nomeRank(rank); }
    private static int nivelDoPortal() { return Math.max(1, ConfigJogo.nivelMinDoRank(rank)); }
    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_portal.txt"); }

    private static void todos(MinecraftServer s, String titulo, String texto, int cor, ChatFormatting corChat) {
        s.getPlayerList().broadcastSystemMessage(Component.literal("◈ " + titulo + " ").withStyle(corChat, ChatFormatting.BOLD)
                .append(Component.literal(texto).withStyle(ChatFormatting.WHITE)), false);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) Sistema.avisar(p, titulo, texto, cor);
    }

    // ------------------------------------------------------------------ construção

    /** Moldura 4x5 de obsidiana chorosa com "véu" roxo no meio, virada para o eixo X. */
    private static List<BlockPos> moldura(BlockPos b) {
        List<BlockPos> l = new ArrayList<>();
        for (int dx = -1; dx <= 2; dx++) for (int dy = 0; dy <= 4; dy++) l.add(b.offset(dx, dy, 0));
        return l;
    }

    private static void construir(ServerLevel nivel) {
        salvos.clear();
        for (BlockPos b : moldura(pos)) {
            salvos.put(b, nivel.getBlockState(b));
            int dx = b.getX() - pos.getX(), dy = b.getY() - pos.getY();
            boolean borda = dx == -1 || dx == 2 || dy == 0 || dy == 4;
            nivel.setBlock(b, (borda ? Blocks.CRYING_OBSIDIAN : Blocks.PURPLE_STAINED_GLASS).defaultBlockState(), 3);
        }
        try { Files.writeString(arquivo(nivel.getServer()), pos.getX() + " " + pos.getY() + " " + pos.getZ()); } catch (Exception ignorado) { }
    }

    private static void desmontar(MinecraftServer s) {
        ServerLevel nivel = s.overworld();
        for (Map.Entry<BlockPos, BlockState> e : salvos.entrySet()) nivel.setBlock(e.getKey(), e.getValue(), 3);
        salvos.clear();
        try { Files.deleteIfExists(arquivo(s)); } catch (Exception ignorado) { }
    }

    /** Se o servidor caiu com um portal aberto, tira a moldura que ficou no mundo. */
    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        try {
            Path a = arquivo(ev.getServer());
            if (!Files.exists(a)) return;
            String[] p = Files.readString(a).trim().split(" ");
            BlockPos b = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            ServerLevel nivel = ev.getServer().overworld();
            for (BlockPos m : moldura(b)) {
                BlockState st = nivel.getBlockState(m);
                if (st.is(Blocks.CRYING_OBSIDIAN) || st.is(Blocks.PURPLE_STAINED_GLASS)) nivel.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
            }
            Files.deleteIfExists(a);
        } catch (Exception e) { FrxWorld.LOG.warn("Portal antigo não pôde ser limpo: {}", e.toString()); }
    }

    // ------------------------------------------------------------------ abrir / fechar

    private static BlockPos lugarPerto(ServerLevel nivel, Reino r) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int t = 0; t < 16; t++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            int dist = 70 + rnd.nextInt(80);
            int x = r.centroX() + (int) (Math.cos(ang) * (r.larguraChunks() * 8 + dist)), z = r.centroZ() + (int) (Math.sin(ang) * (r.profundidadeChunks() * 8 + dist));
            if (r.distancia(x, z) < 40) continue;
            boolean emOutro = false;
            for (Reino o : Reinos.todos()) if (o.distancia(x, z) == 0) emOutro = true;
            if (emOutro) continue;
            nivel.getChunk(x >> 4, z >> 4);
            int y = nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos chao = new BlockPos(x, y - 1, z);
            if (y < nivel.getSeaLevel() - 2 || !nivel.getFluidState(chao).isEmpty() || !nivel.getBlockState(chao).isSolid()) continue;
            return new BlockPos(x, y, z);
        }
        return null;
    }

    static boolean abrir(MinecraftServer s, Reino r, BlockPos onde, int rankPedido) {
        if (fase != Fase.NADA) return false;
        ServerLevel nivel = s.overworld();
        BlockPos b = onde != null ? onde : lugarPerto(nivel, r);
        if (b == null) return false;
        reino = r; pos = b; rank = Math.max(0, Math.min(ConfigJogo.totalRanks() - 1, rankPedido));
        onda = 0; vivos.clear();
        fase = Fase.ABERTO;
        limite = System.currentTimeMillis() + ABERTO_MS;
        construir(nivel);
        nivel.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 4f, 0.8f);
        todos(s, "Portal Rank " + nomeRank() + " aberto", "Perto de " + (r != null ? r.nome() : "você") + ", em " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + ". Monstros de nível " + nivelDoPortal() + ". Fechem em " + ABERTO_MS / 60_000 + " minutos ou ele se rompe! (/portal)", Sistema.ROXO, ChatFormatting.LIGHT_PURPLE);
        return true;
    }

    private static void fechar(MinecraftServer s) {
        for (ServerLevel l : s.getAllLevels()) for (UUID u : vivos) { Entity e = l.getEntity(u); if (e != null) e.discard(); }
        vivos.clear();
        desmontar(s);
        fase = Fase.NADA;
        proximaTentativa = System.currentTimeMillis() + INTERVALO_MS;
    }

    private static List<ServerPlayer> perto(MinecraftServer s, double raio) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : s.overworld().players()) if (p.isAlive() && !p.isSpectator() && p.blockPosition().closerThan(pos, raio)) l.add(p);
        return l;
    }

    private static void romper(MinecraftServer s) {
        Reino r = reino;
        String onde = r != null ? r.nome() : "a região";
        fechar(s);
        todos(s, "O portal se rompeu!", "Ninguém fechou o Portal Rank " + nomeRank() + ". Os monstros marcham sobre " + onde + "!", Sistema.VERMELHO, ChatFormatting.RED);
        if (r != null) Invasoes.comecar(s, r, new BlockPos(r.centroX(), r.chao() + 1, r.centroZ()));
    }

    private static void vitoria(MinecraftServer s) {
        List<ServerPlayer> quem = perto(s, 64);
        long moedas = 100L * (rank + 1) * (rank + 1), xp = 120L * nivelDoPortal();
        StringBuilder nomes = new StringBuilder();
        for (ServerPlayer p : quem) {
            Moedas.dar(p, moedas);
            Progressao.ganharXp(p, xp);
            br.com.frxcode.frxworld.progressao.Conquistas.somar(p, "portais", 1);
            if (p.getRandom().nextInt(100) < 10 + rank * 5) {
                ItemStack tomo = new ItemStack(br.com.frxcode.frxworld.blocos.Blocos.TOMO_ARCANO.get());
                if (!p.getInventory().add(tomo)) p.drop(tomo, false);
            }
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            nomes.append(nomes.length() > 0 ? ", " : "").append(p.getGameProfile().getName());
        }
        String r = nomeRank();
        fechar(s);
        todos(s, "Portal Rank " + r + " fechado", (nomes.length() > 0 ? nomes + " venceu a incursão. " : "") + "Recompensa: " + Moedas.texto(moedas) + " e " + xp + " XP para cada um.",
                Sistema.OURO, ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ ondas

    private static List<String> lista(String k, List<String> padrao) {
        JsonObject o = ConfigJogo.secaoCrua("invasoes");
        if (o == null || !o.has(k) || !o.get(k).isJsonArray()) return padrao;
        List<String> l = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray(k)) if (ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.tryParse(e.getAsString().trim()))) l.add(e.getAsString().trim());
        return l.isEmpty() ? padrao : l;
    }

    private static void gerar(ServerLevel nivel, String id, boolean guardiao) {
        EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
        Entity ent = et == null ? null : et.create(nivel);
        if (!(ent instanceof Mob m)) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double ang = rnd.nextDouble() * Math.PI * 2, d = 3 + rnd.nextDouble() * 5;
        int x = pos.getX() + (int) Math.round(Math.cos(ang) * d), z = pos.getZ() + 1 + (int) Math.round(Math.abs(Math.sin(ang)) * d) * (rnd.nextBoolean() ? 1 : -1);
        int y = nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - pos.getY()) > 8) y = pos.getY();
        BlockPos b = new BlockPos(x, y, z);
        m.moveTo(x + 0.5, y, z + 0.5, rnd.nextFloat() * 360f, 0);
        m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(b), MobSpawnType.EVENT, null, null);
        if (m.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));   // não queimar no sol
            m.setDropChance(EquipmentSlot.HEAD, 0f);
        }
        m.addTag(TAG);
        m.setPersistenceRequired();
        if (guardiao) {
            AttributeInstance vida = m.getAttribute(Attributes.MAX_HEALTH);
            if (vida != null) vida.addPermanentModifier(new AttributeModifier(UUID.randomUUID(), "FRX guardiao", 2.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
            m.setCustomName(Component.literal("Guardião do Portal · Rank " + nomeRank()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            m.setCustomNameVisible(true);
            m.getPersistentData().putString("frx_rank", br.com.frxcode.frxworld.progressao.Ranks.CHEFE);   // letreiro, apresentação e fases de chefe
        }
        Zonas.aplicarNivel(m, nivelDoPortal());
        m.setHealth(m.getMaxHealth());
        nivel.addFreshEntity(m);
        nivel.sendParticles(ParticleTypes.PORTAL, x + 0.5, y + 1, z + 0.5, 30, 0.4, 0.8, 0.4, 0.2);
        vivos.add(m.getUUID());
    }

    private static void proximaOnda(MinecraftServer s) {
        onda++;
        ServerLevel nivel = s.overworld();
        List<String> tipos = switch (onda) {
            case 1 -> lista("monstros1", List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:husk"));
            case 2 -> lista("monstros2", List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:vindicator", "minecraft:pillager", "minecraft:witch"));
            default -> lista("monstros3", List.of("minecraft:vindicator", "minecraft:pillager", "minecraft:evoker", "minecraft:wither_skeleton"));
        };
        int jogadores = Math.max(1, perto(s, 48).size());
        int qtd = Math.min(16, 3 + rank + 2 * jogadores);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < qtd; i++) gerar(nivel, tipos.get(rnd.nextInt(tipos.size())), false);
        if (onda == ONDAS) {
            JsonObject o = ConfigJogo.secaoCrua("invasoes");
            String chefe = o != null && o.has("chefe") ? o.get("chefe").getAsString().trim() : "minecraft:ravager";
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.tryParse(chefe))) chefe = "minecraft:ravager";
            gerar(nivel, chefe, true);
        }
        nivel.playSound(null, pos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 1f);
        for (ServerPlayer p : perto(s, 64))
            Sistema.avisar(p, "Incursão · onda " + onda + " de " + ONDAS, onda == ONDAS ? "O Guardião do Portal saiu. Derrote todos!" : vivos.size() + " monstros saíram do portal.", Sistema.ROXO);
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = ev.getServer();
        long agora = System.currentTimeMillis();
        if (fase == Fase.NADA) {
            if (agora < proximaTentativa) return;
            proximaTentativa = agora + INTERVALO_MS;
            List<ServerPlayer> online = s.overworld().players();
            if (online.isEmpty() || ThreadLocalRandom.current().nextInt(100) >= CHANCE) return;
            // capital mais próxima de algum jogador (até 800 blocos) e rank pelo jogador mais forte por ali
            Reino melhor = null; int menor = 800, nivelMax = 1;
            for (ServerPlayer p : online) {
                for (Reino r : Reinos.todos()) {
                    int d = r.distancia(p.getBlockX(), p.getBlockZ());
                    if (d < menor) { menor = d; melhor = r; }
                }
                if (Progressao.temDados(p)) nivelMax = Math.max(nivelMax, Progressao.dados(p).nivel());
            }
            if (melhor == null) return;
            int r = ConfigJogo.indiceDoNivel(nivelMax) - ThreadLocalRandom.current().nextInt(2);
            abrir(s, melhor, null, Math.max(0, r));
            return;
        }
        ServerLevel nivel = s.overworld();
        nivel.sendParticles(ParticleTypes.PORTAL, pos.getX() + 1, pos.getY() + 2, pos.getZ() + 0.5, 40, 0.8, 1.2, 0.3, 0.6);
        long resta = Math.max(0, (limite - agora) / 1000);
        String relogio = resta / 60 + ":" + String.format("%02d", resta % 60);
        if (fase == Fase.ABERTO) {
            if (agora > limite) { romper(s); return; }
            for (ServerPlayer p : perto(s, 40))
                p.displayClientMessage(Component.literal("Portal Rank " + nomeRank() + " · rompe em " + relogio + " · chegue perto para começar").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            if (!perto(s, 6).isEmpty()) {
                fase = Fase.LUTA;
                limite = agora + LUTA_MS;
                proximaOnda(s);
            }
            return;
        }
        // LUTA
        vivos.removeIf(u -> { Entity e = nivel.getEntity(u); return e == null || !e.isAlive(); });
        if (vivos.isEmpty()) {
            if (onda >= ONDAS) vitoria(s); else proximaOnda(s);
            return;
        }
        if (agora > limite) { romper(s); return; }
        for (ServerPlayer p : perto(s, 64))
            p.displayClientMessage(Component.literal("Incursão · onda " + onda + "/" + ONDAS + " · " + vivos.size() + " inimigos · " + relogio).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("portal").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (fase == Fase.NADA) p.sendSystemMessage(Component.literal("Nenhum portal aberto agora. Eles surgem perto das capitais de tempos em tempos.").withStyle(ChatFormatting.GRAY));
            else p.sendSystemMessage(Component.literal("◈ Portal Rank " + nomeRank() + " em " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                    + (fase == Fase.LUTA ? " — incursão na onda " + onda + "/" + ONDAS : " — aguardando aventureiros")).withStyle(ChatFormatting.LIGHT_PURPLE));
            return 1;
        }));
        List<String> ranks = List.of("F", "E", "D", "C", "B", "A", "S");
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("portal")
                        .then(Commands.literal("fechar").executes(ctx -> { if (fase != Fase.NADA) fechar(ctx.getSource().getServer()); return 1; }))
                        .then(Commands.literal("aqui")
                                .executes(ctx -> abrirAqui(ctx.getSource().getPlayerOrException(), 0))
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ranks, b))
                                        .executes(ctx -> abrirAqui(ctx.getSource().getPlayerOrException(),
                                                Math.max(0, ranks.indexOf(StringArgumentType.getString(ctx, "rank").toUpperCase(java.util.Locale.ROOT)))))))));
    }

    private static int abrirAqui(ServerPlayer p, int rankPedido) {
        if (p.level() != p.server.overworld()) { p.sendSystemMessage(Component.literal("Portais só abrem na superfície.").withStyle(ChatFormatting.RED)); return 0; }
        Reino perto = null; int menor = Integer.MAX_VALUE;
        for (Reino r : Reinos.todos()) { int d = r.distancia(p.getBlockX(), p.getBlockZ()); if (d < menor) { menor = d; perto = r; } }
        BlockPos b = p.blockPosition().relative(p.getDirection(), 8);
        b = new BlockPos(b.getX(), p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX(), b.getZ()), b.getZ());
        if (!abrir(p.server, perto, b, rankPedido)) { p.sendSystemMessage(Component.literal("Já existe um portal aberto (/frx portal fechar).").withStyle(ChatFormatting.RED)); return 0; }
        return 1;
    }
}
