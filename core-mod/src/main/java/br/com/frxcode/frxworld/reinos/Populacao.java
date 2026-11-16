package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import java.util.ArrayList;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gente nas ruas: ao chegar perto, cada chunk de capital ganha (uma vez por mundo) moradores andando,
 * crianças, guardas em patrulha e gatos. Monstros não nascem naturalmente dentro das capitais.
 */
public class Populacao {
    private static final int RAIO = 4;            // chunks em volta do jogador
    private static final int POR_VEZ = 6;         // chunks povoados por jogador a cada 2s
    private static final String TAG = "frx_cidadao";

    private static final List<VillagerProfession> PROFISSOES = List.of(
            VillagerProfession.FARMER, VillagerProfession.FISHERMAN, VillagerProfession.SHEPHERD, VillagerProfession.FLETCHER,
            VillagerProfession.LIBRARIAN, VillagerProfession.CARTOGRAPHER, VillagerProfession.CLERIC, VillagerProfession.ARMORER,
            VillagerProfession.WEAPONSMITH, VillagerProfession.TOOLSMITH, VillagerProfession.BUTCHER, VillagerProfession.LEATHERWORKER,
            VillagerProfession.MASON, VillagerProfession.NITWIT);
    private static final List<VillagerType> TIPOS = List.of(VillagerType.PLAINS, VillagerType.TAIGA, VillagerType.SAVANNA,
            VillagerType.DESERT, VillagerType.SNOW, VillagerType.JUNGLE, VillagerType.SWAMP);

    private static final String[] NOMES = {"Aldric", "Bruna", "Caio", "Dália", "Edgar", "Fiona", "Gustavo", "Helena", "Ivo", "Joana",
            "Kael", "Lia", "Marcos", "Nina", "Otto", "Paula", "Quim", "Rosa", "Sven", "Tália", "Ulric", "Vera", "Wagner", "Yara",
            "Zeca", "Aurora", "Bento", "Clara", "Dimas", "Elisa", "Fábio", "Gaia", "Hugo", "Iris", "Jonas", "Lara", "Milo", "Nara",
            "Orlando", "Pietra", "Rafa", "Sara", "Tomás", "Úrsula", "Vicente", "Wanda", "Ximena", "Yuri", "Zoe", "Gobzo", "Rigur",
            "Haruna", "Kenji", "Sakura", "Akira", "Mei", "Thorin", "Brokk", "Elwen", "Lirien"};

    private static Set<Long> feitos;
    private static Object servidor;

    // ------------------------------------------------------------------ registro por mundo

    private static Path arquivo(ServerLevel nivel) {
        return nivel.getServer().getWorldPath(LevelResource.ROOT).resolve("frx_populacao.txt");
    }

    private static void carregar(ServerLevel nivel) {
        if (feitos != null && servidor == nivel.getServer()) return;
        feitos = new HashSet<>();
        servidor = nivel.getServer();
        try {
            Path p = arquivo(nivel);
            if (Files.exists(p)) for (String l : Files.readAllLines(p)) if (!l.isBlank()) feitos.add(Long.parseLong(l.trim()));
        } catch (Exception e) {
            FrxWorld.LOG.warn("frx_populacao.txt ilegível: {}", e.toString());
        }
    }

    private static void anotar(ServerLevel nivel, long chave) {
        try {
            Files.writeString(arquivo(nivel), chave + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignorado) {}
    }

    /** Chamado a cada 2s enquanto um jogador está dentro de uma capital. */
    static void aoRedor(ServerPlayer p, Reino r) {
        ServerLevel nivel = p.serverLevel();
        carregar(nivel);
        ChunkPos c = p.chunkPosition();
        int feitosAgora = 0;
        for (int d = 0; d <= RAIO && feitosAgora < POR_VEZ; d++) {
            for (int dx = -d; dx <= d && feitosAgora < POR_VEZ; dx++) for (int dz = -d; dz <= d && feitosAgora < POR_VEZ; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                int cx = c.x + dx, cz = c.z + dz;
                if (!r.contemChunk(cx, cz)) continue;
                long chave = ChunkPos.asLong(cx, cz);
                if (feitos.contains(chave)) continue;
                if (!nivel.isPositionEntityTicking(new BlockPos((cx << 4) + 8, p.getBlockY(), (cz << 4) + 8))) continue;
                feitos.add(chave);
                anotar(nivel, chave);
                povoar(nivel, r, cx, cz, RandomSource.create(chave ^ nivel.getSeed()));
                feitosAgora++;
            }
        }
    }

    // ------------------------------------------------------------------ criação

    private static void povoar(ServerLevel nivel, Reino r, int cx, int cz, RandomSource rnd) {
        int moradores = rnd.nextFloat() < 0.16f ? (rnd.nextFloat() < 0.15f ? 2 : 1) : 0;
        for (int i = 0; i < moradores; i++) {
            BlockPos b = lugar(nivel, r, cx, cz, rnd);
            if (b != null) morador(nivel, r, b, rnd);
        }
        if (rnd.nextFloat() < 0.035f) {
            BlockPos b = lugar(nivel, r, cx, cz, rnd);
            if (b != null) guarda(nivel, r, b);
        }
        if (rnd.nextFloat() < 0.02f) {
            BlockPos b = lugar(nivel, r, cx, cz, rnd);
            if (b != null) {
                Entity gato = (r.subterraneo() ? EntityType.CAT : rnd.nextBoolean() ? EntityType.CAT : EntityType.CHICKEN).create(nivel);
                if (gato instanceof Mob m) {
                    m.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, rnd.nextFloat() * 360f, 0);
                    m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(b), MobSpawnType.STRUCTURE, null, null);
                    m.setPersistenceRequired();
                    m.addTag(TAG);
                    nivel.addFreshEntity(m);
                }
            }
        }
    }

    private static boolean livre(BlockState b) {
        return b.isAir() || (!b.blocksMotion() && b.getFluidState().isEmpty() && !b.is(BlockTags.LEAVES));
    }

    /** Um ponto de chão firme com 2 de altura livre. De baixo para cima: rua ou térreo, não telhado. */
    private static BlockPos lugar(ServerLevel nivel, Reino r, int cx, int cz, RandomSource rnd) {
        int y0, y1;
        if (r.subterraneo()) { y0 = r.yBase() + 2; y1 = r.yBase() + r.altura() - 3; }
        else { y0 = r.chao() - 4; y1 = r.chao() + 30; }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int t = 0; t < 12; t++) {
            int x = (cx << 4) + rnd.nextInt(16), z = (cz << 4) + rnd.nextInt(16);
            for (int y = y0; y <= y1; y++) {
                m.set(x, y - 1, z);
                BlockState chao = nivel.getBlockState(m);
                if (!chao.isFaceSturdy(nivel, m, Direction.UP) || chao.is(BlockTags.LEAVES) || chao.is(BlockTags.FENCES)
                        || chao.is(BlockTags.WALLS) || !chao.getFluidState().isEmpty()) continue;
                if (!livre(nivel.getBlockState(m.set(x, y, z))) || !livre(nivel.getBlockState(m.set(x, y + 1, z)))) continue;
                if (!nivel.getBlockState(m.set(x, y, z)).getFluidState().isEmpty()) continue;
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    private static void morador(ServerLevel nivel, Reino r, BlockPos b, RandomSource rnd) {
        Villager v = EntityType.VILLAGER.create(nivel);
        if (v == null) return;
        v.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, rnd.nextFloat() * 360f, 0);
        VillagerType tipo = Racas.de(r, rnd);
        if (rnd.nextFloat() < 0.15f) {
            v.setAge(-24000);
            v.setVillagerData(v.getVillagerData().setType(tipo));
        } else {
            VillagerProfession prof = PROFISSOES.get(rnd.nextInt(PROFISSOES.size()));
            v.setVillagerData(v.getVillagerData().setType(tipo).setProfession(prof).setLevel(1 + rnd.nextInt(3)));
            v.setVillagerXp(1);                 // não perde a profissão sem bancada
            v.getOffers();
        }
        v.setCustomName(Component.literal(NOMES[rnd.nextInt(NOMES.length)]));
        v.setCustomNameVisible(false);
        v.restrictTo(b, 32);
        v.setPersistenceRequired();
        v.addTag(TAG);
        v.addTag(TAG + "_" + r.id());
        nivel.addFreshEntity(v);
    }

    private static void guarda(ServerLevel nivel, Reino r, BlockPos b) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("guardvillagers", "guard"));
        Entity e = (tipo != null ? tipo : EntityType.IRON_GOLEM).create(nivel);
        if (!(e instanceof Mob m)) return;
        m.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, nivel.random.nextFloat() * 360f, 0);
        m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(b), MobSpawnType.STRUCTURE, null, null);
        if (tipo != null) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            if (m.getMainHandItem().isEmpty()) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            for (EquipmentSlot s : EquipmentSlot.values()) m.setDropChance(s, 0f);
            m.setCustomName(Component.literal("Guarda de " + r.nome()));
            m.setCustomNameVisible(false);
        }
        m.restrictTo(b, 40);
        m.setPersistenceRequired();
        m.addTag(TAG);
        m.addTag(TAG + "_" + r.id());
        nivel.addFreshEntity(m);
    }

    // ------------------------------------------------------------------ proteção e reposição

    private record Repor(int x, int y, int z, boolean guarda, long quando) { }
    private static final List<Repor> repor = new ArrayList<>();
    private static Object servidorRepor;
    private static final long ESPERA_REPOR_MS = 10 * 60_000L;

    private static Path arquivoRepor(net.minecraft.server.MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("frx_repor.txt");
    }

    private static void lerRepor(net.minecraft.server.MinecraftServer s) {
        if (servidorRepor == s) return;
        servidorRepor = s;
        repor.clear();
        try {
            Path p = arquivoRepor(s);
            if (Files.exists(p)) for (String l : Files.readAllLines(p)) {
                String[] c = l.trim().split(";");
                if (c.length >= 5) repor.add(new Repor(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]), c[3].equals("guarda"), Long.parseLong(c[4])));
            }
        } catch (Exception e) {
            FrxWorld.LOG.warn("frx_repor.txt ilegível: {}", e.toString());
        }
    }

    private static void gravarRepor(net.minecraft.server.MinecraftServer s) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Repor r : repor) sb.append(r.x()).append(';').append(r.y()).append(';').append(r.z()).append(';').append(r.guarda() ? "guarda" : "morador").append(';').append(r.quando()).append('\n');
            Files.writeString(arquivoRepor(s), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignorado) {}
    }

    /** Morador das ruas não morre para monstro, dragão, parede nem invasão: só jogador (e o vazio) fere. */
    @SubscribeEvent
    public void moradorProtegido(net.minecraftforge.event.entity.living.LivingAttackEvent evento) {
        if (!(evento.getEntity() instanceof Villager v) || v.level().isClientSide() || !v.getTags().contains(TAG)) return;
        var fonte = evento.getSource();
        if (fonte.getEntity() instanceof net.minecraft.world.entity.player.Player) return;
        if (fonte.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        evento.setCanceled(true);
    }

    /** Morador ou guarda que morre volta depois de 10 minutos, perto de onde caiu. */
    @SubscribeEvent
    public void aoMorrer(net.minecraftforge.event.entity.living.LivingDeathEvent evento) {
        Entity e = evento.getEntity();
        if (e.level().isClientSide() || !e.getTags().contains(TAG) || !(e.level() instanceof ServerLevel nivel)) return;
        boolean morador = e instanceof Villager;
        if (!morador && !(e instanceof Mob && e.getCustomName() != null && e.getCustomName().getString().startsWith("Guarda de "))) return;
        lerRepor(nivel.getServer());
        repor.add(new Repor(e.getBlockX(), e.getBlockY(), e.getBlockZ(), !morador, System.currentTimeMillis() + ESPERA_REPOR_MS));
        gravarRepor(nivel.getServer());
    }

    @SubscribeEvent
    public void reporMoradores(net.minecraftforge.event.TickEvent.ServerTickEvent evento) {
        if (evento.phase != net.minecraftforge.event.TickEvent.Phase.END || evento.getServer().getTickCount() % 100 != 50) return;
        net.minecraft.server.MinecraftServer s = evento.getServer();
        lerRepor(s);
        if (repor.isEmpty()) return;
        ServerLevel nivel = s.overworld();
        long agora = System.currentTimeMillis();
        int feitosAgora = 0;
        boolean mudou = false;
        for (int i = 0; i < repor.size() && feitosAgora < 4; i++) {
            Repor p = repor.get(i);
            if (p.quando() > agora) continue;
            Reino reino = null;
            for (Reino r : Reinos.todos()) if (r.distancia(p.x(), p.z()) == 0) { reino = r; break; }
            if (reino == null) { repor.remove(i--); mudou = true; continue; }   // morreu fora de capital: não é da cidade
            if (!nivel.isPositionEntityTicking(new BlockPos(p.x(), p.y(), p.z()))) continue;
            BlockPos b = lugar(nivel, reino, p.x() >> 4, p.z() >> 4, nivel.random);
            if (b == null) { repor.set(i, new Repor(p.x(), p.y(), p.z(), p.guarda(), agora + 5 * 60_000L)); mudou = true; continue; }
            if (p.guarda()) guarda(nivel, reino, b); else morador(nivel, reino, b, nivel.random);
            repor.remove(i--);
            mudou = true;
            feitosAgora++;
        }
        if (mudou) gravarRepor(s);
    }

    // ------------------------------------------------------------------ capitais seguras

    @SubscribeEvent
    public void semMonstros(MobSpawnEvent.FinalizeSpawn evento) {
        MobSpawnType tipo = evento.getSpawnType();
        if (tipo != MobSpawnType.NATURAL && tipo != MobSpawnType.PATROL && tipo != MobSpawnType.CHUNK_GENERATION) return;
        if (evento.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        int x = (int) Math.floor(evento.getX()), z = (int) Math.floor(evento.getZ());
        for (Reino r : Reinos.todos()) {
            if (r.distancia(x, z) == 0) { evento.setSpawnCancelled(true); return; }
        }
    }

    /** Dragões e outras feras do Ice and Fire (ninhos e cavernas geradas no mundo) não ficam dentro das capitais. */
    @SubscribeEvent
    public void semDragoes(net.minecraftforge.event.entity.EntityJoinLevelEvent evento) {
        if (evento.getLevel().isClientSide()) return;
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(evento.getEntity().getType());
        if (id == null || !id.getNamespace().equals("iceandfire")) return;
        if (!(evento.getEntity() instanceof net.minecraft.world.entity.Mob)) return;
        int x = evento.getEntity().getBlockX(), z = evento.getEntity().getBlockZ();
        for (Reino r : Reinos.todos()) {
            if (r.distancia(x, z) == 0) { evento.setCanceled(true); return; }
        }
    }

    /**
     * Dentro das capitais só ficam os moradores do FRX:
     * - aldeões e golens de vilas geradas por outros mods (ficam presos em paredes e telhados quando a cidade é colada por cima)
     *   são removidos; filhotes nascidos na cidade ganham a marca "frx_nascido" e ficam;
     * - illagers (vindicators, pillagers, ravagers, evokers...) de patrulhas, saques e postos não entram; os da invasão do FRX entram.
     */
    @SubscribeEvent
    public void soMoradoresDoReino(net.minecraftforge.event.entity.EntityJoinLevelEvent evento) {
        if (evento.getLevel().isClientSide()) return;
        Entity e = evento.getEntity();
        boolean aldeao = e instanceof Villager || e instanceof net.minecraft.world.entity.animal.IronGolem
                || e instanceof net.minecraft.world.entity.npc.WanderingTrader;
        boolean saqueador = e instanceof net.minecraft.world.entity.raid.Raider;
        if (!aldeao && !saqueador) return;
        if (e.getTags().contains(TAG) || e.getTags().contains(NpcsReino.TAG) || e.getTags().contains("frx_nascido")
                || e.getTags().contains(Invasoes.TAG)) return;
        if (e.getTags().stream().anyMatch(t -> t.startsWith("frx_"))) return;
        int x = e.getBlockX(), z = e.getBlockZ();
        Reino dentro = null;
        for (Reino r : Reinos.todos()) if (r.distancia(x, z) == 0) { dentro = r; break; }
        if (dentro == null) return;
        if (aldeao && e instanceof Villager v && v.isBaby() && !evento.loadedFromDisk()) { e.addTag("frx_nascido"); return; }
        evento.setCanceled(true);
    }

    /** /frx populacao refazer: remove os moradores gerados (carregados) e deixa os chunks serem povoados de novo. */
    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("populacao").then(Commands.literal("refazer").executes(ctx -> {
                    ServerLevel nivel = ctx.getSource().getLevel();
                    List<Entity> alvos = new ArrayList<>();
                    for (Entity e : nivel.getAllEntities()) if (e.getTags().contains(TAG)) alvos.add(e);
                    alvos.forEach(Entity::discard);
                    carregar(nivel);
                    feitos.clear();
                    try { Files.deleteIfExists(arquivo(nivel)); } catch (Exception ignorado) {}
                    int n = alvos.size();
                    ctx.getSource().sendSuccess(() -> Component.literal(n + " moradores removidos; as ruas serão povoadas de novo."), true);
                    return n;
                }))));
    }
}
