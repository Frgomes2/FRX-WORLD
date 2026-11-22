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
 * Quem tem o rank do portal (ou maior) atravessa e cai na dungeon temporária do portal, a mesma para todos e com lotação por rank (aventura/Fenda): câmaras e um chefão.
 * Derrotou o chefão, o portal fecha e o grupo ganha XP e chance de runa, tomo e mapa. Se ninguém fechar, o portal se rompe e uma horda ataca a capital.
 */
public class Portais {
    public static final String TAG = "frx_portal";
    private static final long ABERTO_MS = 20 * 60_000L, LUTA_MS = 8 * 60_000L;
    private static final int ONDAS = 3;

    /** Tempo entre tentativas de abrir portal (painel: Economia → Portais; padrão 15 min). */
    private static long intervaloMs() {
        try { JsonObject o = ConfigJogo.secaoCrua("portais"); if (o != null && o.has("intervaloMinutos")) return Math.max(1, o.get("intervaloMinutos").getAsLong()) * 60_000L; } catch (Exception ignorado) { }
        return 15 * 60_000L;
    }

    /** Chance (%) de cada tentativa abrir um portal (padrão 80). */
    private static int chance() {
        try { JsonObject o = ConfigJogo.secaoCrua("portais"); if (o != null && o.has("chance")) return Math.max(0, Math.min(100, o.get("chance").getAsInt())); } catch (Exception ignorado) { }
        return 80;
    }

    private enum Fase { NADA, ABERTO, LUTA }
    private static Fase fase = Fase.NADA;
    private static Reino reino;
    private static BlockPos pos;
    private static int rank, onda;
    private static long limite, proximaTentativa = System.currentTimeMillis() + 5 * 60_000L;
    private static final Set<UUID> vivos = new HashSet<>();
    private static final Map<UUID, Integer> entrando = new java.util.HashMap<>();
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

    /**
     * Linha de chat com o lugar do portal: distância e direção para cada jogador, e as coordenadas no formato
     * que o JourneyMap reconhece ([name:"…", x:…, y:…, z:…]): clicar cria o ponto no mapa.
     */
    private static void mostrarNoMapa(ServerPlayer p) {
        if (pos == null) return;
        String onde = "";
        if (p.level() == p.server.overworld()) {
            double dx = pos.getX() - p.getX(), dz = pos.getZ() - p.getZ();
            double ang = Math.toDegrees(Math.atan2(dx, -dz));
            if (ang < 0) ang += 360;
            String[] nomes = {"norte", "nordeste", "leste", "sudeste", "sul", "sudoeste", "oeste", "noroeste"};
            onde = " — a " + (int) Math.sqrt(dx * dx + dz * dz) + " blocos, na direção " + nomes[(int) Math.round(ang / 45) % 8];
        }
        p.sendSystemMessage(Component.literal("   📍 ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("[name:\"Portal Rank " + nomeRank() + "\", x:" + pos.getX() + ", y:" + pos.getY() + ", z:" + pos.getZ() + "]").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(onde + ". Clique nas coordenadas para marcar no mapa.").withStyle(ChatFormatting.GRAY)));
    }

    /** Põe (ou tira) o ponto do portal no mapa do JourneyMap de um jogador, sem clique. */
    private static void marcarNoMapa(ServerPlayer p, boolean remover) {
        net.minecraft.nbt.CompoundTag d = new net.minecraft.nbt.CompoundTag();
        if (remover || pos == null) d.putBoolean("remover", true);
        else {
            d.putString("nome", "Portal Rank " + nomeRank());
            d.putInt("x", pos.getX()); d.putInt("y", pos.getY()); d.putInt("z", pos.getZ());
        }
        br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new br.com.frxcode.frxworld.rede.AbrirMenu("marcador", d));
    }

    /**
     * Ponto global no JourneyMap do servidor (aparece no mapa e no minimapa de todos), pela API de servidor do mod.
     * Sempre apaga antes os pontos "Portal Rank ..." que tenham sobrado.
     */
    private static void pontoGlobal(boolean remover) {
        try {
            Class<?> classe = Class.forName("journeymap.api.server.ServerAPI");
            Object api = classe.getField("INSTANCE").get(null);
            Object lista = classe.getMethod("getGlobalWaypoints").invoke(api);
            if (lista instanceof java.util.Collection<?> pontos) {
                for (Object w : new ArrayList<>(pontos)) {
                    String nome = String.valueOf(w.getClass().getMethod("getName").invoke(w));
                    if (!nome.startsWith("Portal Rank ")) continue;
                    for (String getter : new String[]{"getGuid", "getId"}) {
                        try { classe.getMethod("deleteGlobalWaypoint", String.class).invoke(api, String.valueOf(w.getClass().getMethod(getter).invoke(w))); } catch (Throwable ignorado) { }
                    }
                }
            }
            if (remover || pos == null || fase == Fase.NADA) return;
            Object ponto = Class.forName("journeymap.api.v2.common.waypoint.WaypointFactory")
                    .getMethod("createWaypoint", String.class, BlockPos.class, String.class, String.class, boolean.class)
                    .invoke(null, FrxWorld.MOD_ID, pos, "Portal Rank " + nomeRank(), "minecraft:overworld", true);
            for (java.lang.reflect.Method m : ponto.getClass().getMethods())
                if (m.getName().equals("setColor") && m.getParameterCount() == 1) { try { m.invoke(ponto, 0xB040FF); } catch (Throwable ignorado) { } break; }
            for (java.lang.reflect.Method m : classe.getMethods())
                if (m.getName().equals("addGlobalWaypoint") && m.getParameterCount() == 1) { m.invoke(api, ponto); break; }
        } catch (Throwable t) {
            FrxWorld.LOG.warn("Portal: não marquei o ponto global no JourneyMap: {}", t.toString());
        }
    }

    /** Coluna de luz acima do portal, visível de longe (partículas forçadas), para achar a olho. */
    private static void pilarDeLuz(ServerLevel nivel) {
        for (ServerPlayer p : nivel.players()) {
            if (!p.blockPosition().closerThan(pos, 500)) continue;
            for (int i = 0; i < 20; i++)
                nivel.sendParticles(p, i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.DRAGON_BREATH, true,
                        pos.getX() + 1, pos.getY() + 6 + i * 4, pos.getZ() + 0.5, 3, 0.15, 1.6, 0.15, 0.0);
        }
    }

    /** Quem entra com um portal aberto também recebe o ponto. */
    @SubscribeEvent
    public void aoEntrar(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && fase != Fase.NADA) marcarNoMapa(p, false);
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
                + ". Leva a uma dungeon de nível " + nivelDoPortal() + " com um chefão no fim. Só Rank " + nomeRank() + " ou superior da Guilda entra, no máximo " + br.com.frxcode.frxworld.aventura.Fenda.lotacao(rank) + " aventureiros por vez. Fechem em " + ABERTO_MS / 60_000 + " minutos ou ele se rompe! (/portal)", Sistema.ROXO, ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer jogador : s.getPlayerList().getPlayers()) { mostrarNoMapa(jogador); marcarNoMapa(jogador, false); }
        pontoGlobal(false);
        return true;
    }

    private static void fechar(MinecraftServer s) {
        for (ServerLevel l : s.getAllLevels()) for (UUID u : vivos) { Entity e = l.getEntity(u); if (e != null) e.discard(); }
        vivos.clear();
        desmontar(s);
        for (ServerPlayer jogador : s.getPlayerList().getPlayers()) marcarNoMapa(jogador, true);
        fase = Fase.NADA;
        pontoGlobal(true);
        proximaTentativa = System.currentTimeMillis() + intervaloMs();
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

    /** Um grupo derrotou o chefão da dungeon aberta por um portal: paga a recompensa e fecha o portal, se ainda for o mesmo. */
    /** Moedas (cobre) para cada jogador que derrota o chefão, pelo rank do portal. Painel: Economia → Recompensa dos portais. */
    private static final long[] MOEDAS_PADRAO = {100, 200, 400, 800, 1500, 2500, 4000};

    static long moedasDoRank(int rank) {
        int r = Math.max(0, Math.min(MOEDAS_PADRAO.length - 1, rank));
        try {
            JsonObject o = ConfigJogo.secaoCrua("portais");
            if (o != null && o.has("moedasPorRank") && o.getAsJsonArray("moedasPorRank").size() > r) return Math.max(0, o.getAsJsonArray("moedasPorRank").get(r).getAsLong());
        } catch (Exception ignorado) { }
        return MOEDAS_PADRAO[r];
    }

    public static void aoVencerDungeon(MinecraftServer s, List<ServerPlayer> quem, int rankDungeon, int nivel, BlockPos portal) {
        long xp = 300L * Math.max(1, nivel), moedas = moedasDoRank(rankDungeon);
        StringBuilder nomes = new StringBuilder();
        for (ServerPlayer p : quem) {
            Progressao.ganharXp(p, xp);
            if (moedas > 0) Moedas.dar(p, moedas);
            br.com.frxcode.frxworld.progressao.Conquistas.somar(p, "portais", 1);
            br.com.frxcode.frxworld.progressao.Conquistas.somar(p, "chefes", 1);
            br.com.frxcode.frxworld.aventura.Tesouros.talvezDar(p, 20);
            br.com.frxcode.frxworld.aventura.Runas.talvezDar(p, 40, rankDungeon >= 4 ? 3 : rankDungeon >= 2 ? 2 : 1);
            if (p.getRandom().nextInt(100) < 30 + rankDungeon * 10) {
                ItemStack tomo = new ItemStack(br.com.frxcode.frxworld.blocos.Blocos.TOMO_ARCANO.get());
                if (!p.getInventory().add(tomo)) p.drop(tomo, false);
            }
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            nomes.append(nomes.length() > 0 ? ", " : "").append(p.getGameProfile().getName());
        }
        String r = ConfigJogo.nomeRank(rankDungeon);
        if (fase != Fase.NADA && pos != null && pos.equals(portal)) fechar(s);
        todos(s, "Portal Rank " + r + " fechado", (nomes.length() > 0 ? nomes + " derrotou o chefão da dungeon. " : "") + "Recompensa: " + xp + " XP" + (moedas > 0 ? " e " + Moedas.texto(moedas) : "") + " para cada um, com chance de runa, tomo e mapa do tesouro.",
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
            proximaTentativa = agora + intervaloMs();
            List<ServerPlayer> online = s.overworld().players();
            if (online.isEmpty() || ThreadLocalRandom.current().nextInt(100) >= chance()) return;
            // Um jogador ao acaso (dos que estão a até 800 blocos de uma capital) vira a referência: o portal abre perto
            // da capital mais próxima dele, com rank em volta do rank dele na Guilda. Assim cada um vê portais do seu nível,
            // e ainda saem portais acima como desafio: 25% um abaixo, 45% o mesmo, 20% um acima, 10% dois acima.
            List<ServerPlayer> perto = new java.util.ArrayList<>();
            for (ServerPlayer p : online) for (Reino r : Reinos.todos()) if (r.distancia(p.getBlockX(), p.getBlockZ()) < 800) { perto.add(p); break; }
            if (perto.isEmpty()) return;
            ServerPlayer alvo = perto.get(ThreadLocalRandom.current().nextInt(perto.size()));
            Reino melhor = null; int menor = Integer.MAX_VALUE;
            for (Reino r : Reinos.todos()) {
                int d = r.distancia(alvo.getBlockX(), alvo.getBlockZ());
                if (d < menor) { menor = d; melhor = r; }
            }
            if (melhor == null) return;
            int base = br.com.frxcode.frxworld.progressao.Guilda.indice(alvo), sorte = ThreadLocalRandom.current().nextInt(100);
            int r = sorte < 25 ? base - 1 : sorte < 70 ? base : sorte < 90 ? base + 1 : base + 2;
            abrir(s, melhor, null, Math.max(0, Math.min(ConfigJogo.totalRanks() - 1, r)));
            return;
        }
        ServerLevel nivel = s.overworld();
        nivel.sendParticles(ParticleTypes.PORTAL, pos.getX() + 1, pos.getY() + 2, pos.getZ() + 0.5, 40, 0.8, 1.2, 0.3, 0.6);
        pilarDeLuz(nivel);
        long resta = Math.max(0, (limite - agora) / 1000);
        String relogio = resta / 60 + ":" + String.format("%02d", resta % 60);
        if (fase == Fase.ABERTO) {
            if (agora > limite) {
                if (br.com.frxcode.frxworld.aventura.Fenda.emAndamento(pos)) limite = agora + 60_000;   // há grupo lá dentro: segura o portal
                else { romper(s); return; }
            }
            for (ServerPlayer p : perto(s, 40))
                p.displayClientMessage(Component.literal("Portal Rank " + nomeRank() + " · rompe em " + relogio + " · encoste no portal para entrar (Rank " + nomeRank() + " ou superior)").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            // atravessar: ficar 2 s encostado no portal. Só entra quem tem o rank do portal ou maior.
            entrando.keySet().removeIf(u -> { ServerPlayer q = s.getPlayerList().getPlayer(u); return q == null || !q.blockPosition().closerThan(pos.offset(1, 1, 0), 3); });
            for (ServerPlayer p : perto(s, 4)) {
                if (!p.blockPosition().closerThan(pos.offset(1, 1, 0), 3)) continue;
                int t = entrando.merge(p.getUUID(), 1, Integer::sum);
                if (t < 2) continue;
                entrando.remove(p.getUUID());
                if (br.com.frxcode.frxworld.progressao.Guilda.indice(p) < rank && !p.isCreative()) {
                    p.displayClientMessage(Component.literal("O portal te repele: só Rank " + nomeRank() + " ou superior da Guilda entra (você é Rank "
                            + br.com.frxcode.frxworld.progressao.Guilda.rank(p) + ").").withStyle(ChatFormatting.RED), true);
                    p.knockback(1.0, pos.getX() + 1 - p.getX(), pos.getZ() - p.getZ());
                    p.hurtMarked = true;
                    continue;
                }
                br.com.frxcode.frxworld.aventura.Fenda.entrar(p, rank, nivelDoPortal(), pos);
            }
            return;
        }
        // LUTA
        vivos.removeIf(u -> { Entity e = nivel.getEntity(u); return e == null || !e.isAlive(); });
        if (vivos.isEmpty()) {
            fechar(s);   // a fase de ondas no mundo aberto foi trocada pela dungeon (aventura/Fenda)
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
            if (fase != Fase.NADA) mostrarNoMapa(p);
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
