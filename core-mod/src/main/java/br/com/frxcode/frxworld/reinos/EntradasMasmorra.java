package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entrada das masmorras fixas. Toda masmorra enterrada ganha um pórtico na superfície, com o nome flutuando,
 * e um portal (bloco de obsidiana chorosa) que leva à primeira sala; lá dentro, outro portal traz de volta.
 * As masmorras de superfície ganham só o marco com o nome, porque a própria construção já é a entrada.
 * Registro por mundo em frx_entradas.txt: "id;sx;sy;sz;dx;dy;dz" (ou "id;sx;sy;sz" sem portal).
 */
public class EntradasMasmorra {
    private static final String TAG = "frx_entrada";

    private record Entrada(String id, BlockPos fora, BlockPos dentro) {}

    private static final Map<String, Entrada> ENTRADAS = new LinkedHashMap<>();
    private static final Map<UUID, Long> RECARGA = new HashMap<>();
    private static final List<String> pendentes = new ArrayList<>();
    /** Onde cada portal estava antes de um "refazer", para montar de novo no mesmo lugar. */
    private static final Map<String, BlockPos> ANTIGAS = new HashMap<>();
    private static int espera = 0;

    public EntradasMasmorra() {}

    private static Path arquivo(ServerLevel nivel) {
        return nivel.getServer().getWorldPath(LevelResource.ROOT).resolve("frx_entradas.txt");
    }

    private static void ler(ServerLevel nivel) {
        ENTRADAS.clear();
        try {
            if (!Files.exists(arquivo(nivel))) return;
            for (String l : Files.readAllLines(arquivo(nivel), StandardCharsets.UTF_8)) {
                String[] p = l.trim().split(";");
                if (p.length < 4) continue;
                BlockPos fora = new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                BlockPos dentro = p.length >= 7 ? new BlockPos(Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6])) : null;
                ENTRADAS.put(p[0], new Entrada(p[0], fora, dentro));
            }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Entradas de masmorra: não consegui ler o registro: {}", e.toString());
        }
    }

    private static void gravar(ServerLevel nivel) {
        StringBuilder sb = new StringBuilder();
        for (Entrada e : ENTRADAS.values()) {
            sb.append(e.id()).append(';').append(e.fora().getX()).append(';').append(e.fora().getY()).append(';').append(e.fora().getZ());
            if (e.dentro() != null) sb.append(';').append(e.dentro().getX()).append(';').append(e.dentro().getY()).append(';').append(e.dentro().getZ());
            sb.append('\n');
        }
        try { Files.writeString(arquivo(nivel), sb.toString(), StandardCharsets.UTF_8); } catch (Exception ignorado) { }
    }

    private static JsonObject def(String id) {
        for (JsonElement el : Masmorras.config()) if (el.getAsJsonObject().get("id").getAsString().equals(id)) return el.getAsJsonObject();
        return null;
    }

    private static String nome(String id) {
        JsonObject m = def(id);
        return m != null && m.has("nome") ? m.get("nome").getAsString() : id;
    }

    // ------------------------------------------------------------------ onde fica a estrutura

    /** Refaz o cálculo da estrutura (sem colocar blocos) para saber a caixa dela. A geração é a mesma do dia em que foi colocada. */
    private static StructureStart estrutura(ServerLevel nivel, JsonObject m, String estruturaColocada) {
        ResourceLocation rl = ResourceLocation.tryParse(estruturaColocada);
        Structure estrutura = rl == null ? null : nivel.registryAccess().registryOrThrow(Registries.STRUCTURE).get(rl);
        if (estrutura == null) return null;
        int x0 = m.get("x").getAsInt(), z0 = m.get("z").getAsInt();
        ChunkGenerator gerador = nivel.getChunkSource().getGenerator();
        for (int t = 0; t < 7; t++) {
            int x = t == 0 ? x0 : x0 + (int) (Math.cos(t) * 48 * ((t + 1) / 2)), z = t == 0 ? z0 : z0 + (int) (Math.sin(t) * 48 * ((t + 1) / 2));
            try {
                StructureStart inicio = estrutura.generate(nivel.registryAccess(), gerador, gerador.getBiomeSource(), nivel.getChunkSource().randomState(),
                        nivel.getStructureManager(), nivel.getSeed(), new ChunkPos(new BlockPos(x, 0, z)), 0, nivel, b -> true);
                if (inicio.isValid()) return inicio;
            } catch (Exception ignorado) { return null; }
        }
        return null;
    }

    private static boolean ar(ServerLevel n, BlockPos.MutableBlockPos m, int x, int y, int z) {
        BlockState b = n.getBlockState(m.set(x, y, z));
        return b.isAir() || b.getCollisionShape(n, m).isEmpty() && b.getFluidState().isEmpty();
    }

    /** Sala: piso firme e 3×3×3 livre, sem líquido. */
    private static boolean sala(ServerLevel n, BlockPos.MutableBlockPos m, int x, int y, int z) {
        BlockState piso = n.getBlockState(m.set(x, y - 1, z));
        if (!piso.isSolidRender(n, m) || !piso.getFluidState().isEmpty()) return false;
        // piso de pedra ou terra natural é caverna, não sala da masmorra
        if (piso.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || piso.is(net.minecraft.tags.BlockTags.DIRT)
                || piso.is(Blocks.GRAVEL) || piso.is(Blocks.SAND) || piso.is(Blocks.TUFF) || piso.is(Blocks.CALCITE)
                || piso.is(Blocks.DRIPSTONE_BLOCK) || piso.is(Blocks.CLAY) || piso.is(Blocks.MOSS_BLOCK)
                || piso.is(net.minecraft.tags.BlockTags.COAL_ORES) || piso.is(net.minecraft.tags.BlockTags.IRON_ORES) || piso.is(net.minecraft.tags.BlockTags.COPPER_ORES)) return false;
        // o piso em volta também precisa ser da construção
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockState v = n.getBlockState(m.set(x + dx, y - 1, z + dz));
            if (!v.isSolidRender(n, m) || v.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || v.is(net.minecraft.tags.BlockTags.DIRT)) return false;
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++) {
            BlockState b = n.getBlockState(m.set(x + dx, y + dy, z + dz));
            if (!b.isAir()) return false;
        }
        return true;
    }

    private static boolean natural(BlockState b) {
        return b.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || b.is(net.minecraft.tags.BlockTags.DIRT) || b.is(Blocks.GRAVEL)
                || b.is(Blocks.SAND) || b.is(Blocks.TUFF) || b.is(Blocks.CALCITE) || b.is(Blocks.DRIPSTONE_BLOCK) || b.is(Blocks.CLAY);
    }

    /** Tem teto de construção por cima (não é caverna aberta nem céu). */
    private static boolean tetoConstruido(ServerLevel n, BlockPos.MutableBlockPos m, int x, int y, int z) {
        for (int dy = 3; dy <= 24; dy++) {
            BlockState b = n.getBlockState(m.set(x, y + dy, z));
            if (b.isAir()) continue;
            return !natural(b) && b.getFluidState().isEmpty();
        }
        return false;
    }

    /** Paredes de construção em pelo menos dois lados, a até 12 blocos: separa sala de masmorra de caverna encostada nela. */
    private static boolean paredesConstruidas(ServerLevel n, BlockPos.MutableBlockPos m, int x, int y, int z) {
        int ok = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs)
            for (int i = 2; i <= 12; i++) {
                BlockState b = n.getBlockState(m.set(x + d[0] * i, y + 1, z + d[1] * i));
                if (b.isAir()) continue;
                if (!natural(b) && b.getFluidState().isEmpty()) ok++;
                break;
            }
        return ok >= 3;
    }

    /**
     * Sala de dentro da construção: procura nas peças da estrutura, exige piso, teto e paredes que não sejam pedra natural,
     * e prefere a mais alta (a entrada das masmorras enterradas costuma ficar em cima), desempatando pela mais central.
     */
    private static BlockPos salaDeDentro(ServerLevel n, StructureStart inicio) {
        BoundingBox bb = inicio.getBoundingBox();
        List<BoundingBox> caixas = new ArrayList<>();
        inicio.getPieces().forEach(pc -> caixas.add(pc.getBoundingBox()));
        if (caixas.isEmpty()) caixas.add(bb);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos centro = bb.getCenter(), melhor = null;
        double melhorNota = -1e18;
        for (BoundingBox c : caixas)
            for (int x = c.minX() + 2; x <= c.maxX() - 2; x += 2) for (int z = c.minZ() + 2; z <= c.maxZ() - 2; z += 2)
                for (int y = c.maxY() - 2; y >= c.minY() + 1; y--) {
                    if (!sala(n, m, x, y, z)) continue;
                    if (tetoConstruido(n, m, x, y, z) && paredesConstruidas(n, m, x, y, z)) {
                        double dist = Math.sqrt(Math.pow(x - centro.getX(), 2) + Math.pow(z - centro.getZ(), 2));
                        double nota = y * 4 - dist;
                        if (nota > melhorNota) { melhorNota = nota; melhor = new BlockPos(x, y, z); }
                    }
                    break;   // só a sala mais alta de cada coluna
                }
        return melhor;
    }

    /** Procura uma sala dentro da caixa, começando perto de (cx, cz) e de cima para baixo. */
    private static BlockPos acharSala(ServerLevel n, BoundingBox bb, int cx, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= 40; r += 2)
            for (int dx = -r; dx <= r; dx += 2) for (int dz = -r; dz <= r; dz += 2) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                int x = cx + dx, z = cz + dz;
                if (x < bb.minX() + 1 || x > bb.maxX() - 1 || z < bb.minZ() + 1 || z > bb.maxZ() - 1) continue;
                for (int y = bb.maxY() - 2; y >= bb.minY() + 1; y--) if (sala(n, m, x, y, z)) return new BlockPos(x, y, z);
            }
        return null;
    }

    private static boolean nosso(BlockState b) {
        return b.isAir() || b.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || b.is(Blocks.POLISHED_BLACKSTONE) || b.is(Blocks.CHISELED_POLISHED_BLACKSTONE)
                || b.is(Blocks.SOUL_LANTERN) || b.is(Blocks.BLACKSTONE) || b.is(Blocks.CRYING_OBSIDIAN) || b.is(Blocks.GILDED_BLACKSTONE)
                || b.is(Blocks.POLISHED_BLACKSTONE_WALL);
    }

    /** Y do bloco de chão natural na coluna, ignorando um pórtico nosso que já esteja ali (para refazer no mesmo lugar). */
    private static int chaoNatural(ServerLevel n, int x, int z) {
        int y = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (y > n.getMinBuildHeight() + 2 && nosso(n.getBlockState(m.set(x, y, z)))) y--;
        return y;
    }

    /** Fundo do poço antigo: desce pela coluna livre até o primeiro piso. */
    private static BlockPos fundoDoPoco(ServerLevel n, BoundingBox bb, int x, int topo, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int y = Math.min(topo, bb.maxY() + 8);
        // passa pelo tampo do pórtico e pela parte fechada, até entrar na coluna de ar
        while (y > bb.minY() && !n.getBlockState(m.set(x, y, z)).isAir()) y--;
        while (y > bb.minY() && n.getBlockState(m.set(x, y - 1, z)).isAir()) y--;
        return y > bb.minY() && y <= bb.maxY() + 2 ? new BlockPos(x, y, z) : null;
    }

    // ------------------------------------------------------------------ construção

    private static void por(ServerLevel n, BlockPos p, BlockState b) { n.setBlock(p, b, 2); }

    private static void rotulo(ServerLevel n, BlockPos p, String linha1, String linha2) {
        for (ArmorStand a : n.getEntitiesOfClass(ArmorStand.class, new AABB(p).inflate(4), e -> e.getTags().contains(TAG))) a.discard();
        String[] linhas = {linha1, linha2};
        for (int i = 0; i < linhas.length; i++) {
            if (linhas[i] == null || linhas[i].isEmpty()) continue;
            ArmorStand a = EntityType.ARMOR_STAND.create(n);
            if (a == null) continue;
            a.moveTo(p.getX() + 0.5, p.getY() + 2.6 - i * 0.3, p.getZ() + 0.5, 0, 0);
            a.setInvisible(true);
            a.setNoGravity(true);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.getPersistentData().putBoolean("frx_fixo", true);
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            a.saveWithoutId(t);
            t.putBoolean("Marker", true);
            a.load(t);
            a.addTag(TAG);
            a.setCustomName(Component.literal(linhas[i]).withStyle(i == 0 ? ChatFormatting.DARK_RED : ChatFormatting.GRAY, i == 0 ? ChatFormatting.BOLD : ChatFormatting.ITALIC));
            a.setCustomNameVisible(true);
            n.addFreshEntity(a);
        }
    }

    /** Pórtico 7×7 na superfície, com o portal no centro. `base` é o bloco do piso (o jogador pisa em base.above()). */
    private static void portico(ServerLevel n, BlockPos base, boolean comPortal) {
        BlockState tijolo = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), pilar = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            por(n, base.offset(dx, 0, dz), Math.abs(dx) == 3 || Math.abs(dz) == 3 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : tijolo);
            for (int dy = 1; dy <= 6; dy++) por(n, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            // apoio embaixo, para o piso não ficar flutuando em terreno inclinado
            for (int dy = 1; dy <= 4; dy++) {
                BlockPos baixo = base.offset(dx, -dy, dz);
                if (n.getBlockState(baixo).isAir() || !n.getBlockState(baixo).getFluidState().isEmpty()) por(n, baixo, Blocks.BLACKSTONE.defaultBlockState()); else break;
            }
        }
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3}) {
            for (int dy = 1; dy <= 4; dy++) por(n, base.offset(dx, dy, dz), pilar);
            por(n, base.offset(dx, 5, dz), Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // vigas do topo
        for (int i = -3; i <= 3; i++) {
            por(n, base.offset(i, 4, -3), tijolo); por(n, base.offset(i, 4, 3), tijolo);
            por(n, base.offset(-3, 4, i), tijolo); por(n, base.offset(3, 4, i), tijolo);
        }
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3}) por(n, base.offset(dx, 4, dz), pilar);
        if (comPortal) {
            por(n, base, Blocks.CRYING_OBSIDIAN.defaultBlockState());
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) por(n, base.offset(dx, 0, dz), Blocks.GILDED_BLACKSTONE.defaultBlockState());
        } else {
            por(n, base.above(), pilar);
            por(n, base.above(2), Blocks.SOUL_LANTERN.defaultBlockState());
        }
    }

    private static void portalDeVolta(ServerLevel n, BlockPos pe) {
        por(n, pe.below(), Blocks.CRYING_OBSIDIAN.defaultBlockState());
        for (int dy = 0; dy <= 2; dy++) por(n, pe.above(dy), Blocks.AIR.defaultBlockState());
    }

    /** Monta (ou refaz) a entrada de uma masmorra já colocada. Devolve um texto para o log. */
    private static String montar(ServerLevel nivel, String id, String linha) {
        JsonObject m = def(id);
        String[] p = linha.split(";");
        if (m == null || p.length < 5) return "sem configuração";
        StructureStart inicio = estrutura(nivel, m, p[4]);
        if (inicio == null) return "não consegui recalcular a estrutura " + p[4];
        BoundingBox bb = inicio.getBoundingBox();
        BlockPos centro = bb.getCenter();
        int sx = centro.getX(), sz = centro.getZ();
        if (m.has("entrada")) { JsonArray e = m.getAsJsonArray("entrada"); sx = e.get(0).getAsInt(); sz = e.get(2).getAsInt(); }
        for (int cx = (bb.minX() >> 4) - 1; cx <= (bb.maxX() >> 4) + 1; cx++) for (int cz = (bb.minZ() >> 4) - 1; cz <= (bb.maxZ() >> 4) + 1; cz++) nivel.getChunk(cx, cz);
        nivel.getChunk(sx >> 4, sz >> 4);
        int chao = m.has("entrada") ? m.getAsJsonArray("entrada").get(1).getAsInt() - 1
                : ANTIGAS.containsKey(id) ? ANTIGAS.get(id).getY() - 1 : chaoNatural(nivel, sx, sz);
        boolean enterrada = bb.maxY() < chao - 3;
        if (!enterrada) {
            // de superfície: só o marco, ao lado da construção (do lado de fora da caixa)
            int mx = bb.minX() - 5, mz = centro.getZ();
            nivel.getChunk(mx >> 4, mz >> 4);
            BlockPos base = new BlockPos(mx, chaoNatural(nivel, mx, mz), mz);
            portico(nivel, base, false);
            rotulo(nivel, base.above(2), "☠ " + nome(id), "A construção ao lado é a masmorra");
            ENTRADAS.put(id, new Entrada(id, base.above(), null));
            return "marco de superfície em " + base.getX() + ", " + (base.getY() + 1) + ", " + base.getZ();
        }
        BlockPos sala = salaDeDentro(nivel, inicio);
        if (sala == null) sala = acharSala(nivel, bb, Math.max(bb.minX() + 2, Math.min(bb.maxX() - 2, sx)), Math.max(bb.minZ() + 2, Math.min(bb.maxZ() - 2, sz)));
        if (sala == null) return "enterrada, mas não achei sala livre dentro dela";
        BlockPos base = new BlockPos(sx, chao, sz);
        // fecha o poço antigo logo abaixo do piso (a escada continua lá, só não é mais o caminho)
        if (m.has("entrada"))
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) por(nivel, base.offset(dx, -1, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        portico(nivel, base, true);
        portalDeVolta(nivel, sala);
        rotulo(nivel, base.above(), "☠ " + nome(id), "Pise no portal para entrar");
        rotulo(nivel, sala, "Saída", "Pise no portal para voltar");
        ENTRADAS.put(id, new Entrada(id, base.above(), sala));
        return "portal em " + base.getX() + ", " + (base.getY() + 1) + ", " + base.getZ() + " → sala em " + sala.getX() + ", " + sala.getY() + ", " + sala.getZ();
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void aoLigar(ServerStartedEvent ev) {
        ServerLevel nivel = ev.getServer().getLevel(Level.OVERWORLD);
        if (nivel == null) return;
        ler(nivel);
        pendentes.clear();
        espera = 600;   // 30 s depois de ligar (as masmorras novas são colocadas antes)
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        ServerLevel nivel = s.getLevel(Level.OVERWORLD);
        if (nivel == null) return;

        // monta as entradas que faltam, uma por vez
        if (espera > 0 && --espera == 0) {
            if (pendentes.isEmpty()) for (String id : Masmorras.lidas(nivel).keySet()) if (!ENTRADAS.containsKey(id)) pendentes.add(id);
            if (!pendentes.isEmpty()) {
                String id = pendentes.remove(0);
                String linha = Masmorras.lidas(nivel).get(id);
                try {
                    String r = linha == null ? "sem registro" : montar(nivel, id, linha);
                    FrxWorld.LOG.info("Entrada da masmorra {}: {}", id, r);
                    if (!ENTRADAS.containsKey(id)) ENTRADAS.put(id, new Entrada(id, new BlockPos(0, -9999, 0), null));   // não tenta de novo a cada início
                    gravar(nivel);
                } catch (Throwable t) {
                    FrxWorld.LOG.warn("Entrada da masmorra {} falhou: {}", id, t.toString());
                }
                espera = 200;
            } else espera = 6000;   // confere de novo em 5 min (masmorras colocadas depois)
        }

        // portais
        if (s.getTickCount() % 5 != 0 || ENTRADAS.isEmpty()) return;
        long agora = System.currentTimeMillis();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.level() != nivel || p.isSpectator()) continue;
            BlockPos pe = p.blockPosition();
            for (Entrada e : ENTRADAS.values()) {
                if (e.dentro() == null) continue;
                boolean entrar = pe.equals(e.fora()), sair = pe.equals(e.dentro());
                if (!entrar && !sair) continue;
                if (RECARGA.getOrDefault(p.getUUID(), 0L) > agora) break;
                RECARGA.put(p.getUUID(), agora + 4000);
                BlockPos d = entrar ? e.dentro() : e.fora().offset(0, 0, 2);
                nivel.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.4, 0.8, 0.4, 0.05);
                p.teleportTo(nivel, d.getX() + 0.5, d.getY(), d.getZ() + 0.5, p.getYRot(), p.getXRot());
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
                nivel.playSound(null, d, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
                p.displayClientMessage(Component.literal(entrar ? "☠ " + nome(e.id()) : "Você voltou à superfície").withStyle(entrar ? ChatFormatting.DARK_RED : ChatFormatting.GRAY), true);
                break;
            }
        }
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("masmorra").then(Commands.literal("entradas")
                        .executes(ctx -> {
                            StringBuilder sb = new StringBuilder("Entradas das masmorras:");
                            for (Entrada e : ENTRADAS.values())
                                sb.append("\n").append(nome(e.id())).append(": ").append(e.fora().getY() < -1000 ? "sem entrada montada"
                                        : e.fora().getX() + ", " + e.fora().getY() + ", " + e.fora().getZ() + (e.dentro() != null ? " (portal)" : " (marco)"));
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()).withStyle(ChatFormatting.GOLD), false);
                            return 1;
                        })
                        .then(Commands.literal("refazer").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ENTRADAS.keySet(), b))
                                .executes(ctx -> {
                                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                                    Entrada e = ENTRADAS.get(id);
                                    if (e == null) { ctx.getSource().sendFailure(Component.literal("Masmorra sem entrada registrada.")); return 0; }
                                    ServerLevel nv = ctx.getSource().getServer().overworld();
                                    ANTIGAS.clear();
                                    if (e.dentro() != null) {
                                        ANTIGAS.put(id, e.fora());
                                        nv.getChunk(e.dentro().getX() >> 4, e.dentro().getZ() >> 4);
                                        if (nv.getBlockState(e.dentro().below()).is(Blocks.CRYING_OBSIDIAN)) por(nv, e.dentro().below(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                                        for (ArmorStand a : nv.getEntitiesOfClass(ArmorStand.class, new AABB(e.dentro()).inflate(4), x -> x.getTags().contains(TAG))) a.discard();
                                    }
                                    ENTRADAS.remove(id);
                                    pendentes.clear();
                                    espera = 20;
                                    ctx.getSource().sendSuccess(() -> Component.literal("Refazendo a entrada de " + id + "."), true);
                                    return 1;
                                })).executes(ctx -> {
                            ServerLevel nv = ctx.getSource().getServer().overworld();
                            ANTIGAS.clear();
                            for (Entrada e : ENTRADAS.values()) {
                                if (e.dentro() == null) continue;
                                ANTIGAS.put(e.id(), e.fora());
                                nv.getChunk(e.dentro().getX() >> 4, e.dentro().getZ() >> 4);
                                if (nv.getBlockState(e.dentro().below()).is(Blocks.CRYING_OBSIDIAN)) por(nv, e.dentro().below(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                                for (ArmorStand a : nv.getEntitiesOfClass(ArmorStand.class, new AABB(e.dentro()).inflate(4), x -> x.getTags().contains(TAG))) a.discard();
                            }
                            ENTRADAS.clear();
                            pendentes.clear();
                            espera = 20;
                            ctx.getSource().sendSuccess(() -> Component.literal("Refazendo as entradas das masmorras, uma a cada 10 s. Veja o resultado em /frx masmorra entradas."), true);
                            return 1;
                        })))));
    }
}
