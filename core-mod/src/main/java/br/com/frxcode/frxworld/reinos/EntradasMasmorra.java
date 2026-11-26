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
    /** Lugar do portal escolhido pelo admin (/frx masmorra portal <id>): posição dos pés. */
    private static final Map<String, BlockPos> MANUAIS = new HashMap<>();
    private static int espera = 0;
    /** Pórtico de cada masmorra dentro do reino dela (posição dos pés). SEM = não tem ou não coube. */
    private static final Map<String, BlockPos> PORTAL_REINO = new LinkedHashMap<>();
    private static final BlockPos SEM = new BlockPos(0, -9999, 0);
    /** Por qual pórtico o jogador entrou: é para lá que a saída devolve. */
    private static final Map<UUID, BlockPos> ORIGEM = new HashMap<>();
    /** Quem já recebeu as marcações do mapa nesta sessão. */
    private static final java.util.Set<UUID> NO_MAPA = new java.util.HashSet<>();

    public EntradasMasmorra() {}

    private static Path arquivoReino(ServerLevel nivel) {
        return nivel.getServer().getWorldPath(LevelResource.ROOT).resolve("frx_portais_reino.txt");
    }

    private static void lerReino(ServerLevel nivel) {
        PORTAL_REINO.clear();
        try {
            if (!Files.exists(arquivoReino(nivel))) return;
            for (String l : Files.readAllLines(arquivoReino(nivel), StandardCharsets.UTF_8)) {
                String[] p = l.trim().split(";");
                if (p.length >= 4 && Integer.parseInt(p[2]) > -1000) PORTAL_REINO.put(p[0], new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            }
        } catch (Exception e) {
            FrxWorld.LOG.warn("Portais de reino: não consegui ler o registro: {}", e.toString());
        }
    }

    private static void gravarReino(ServerLevel nivel) {
        StringBuilder sb = new StringBuilder();
        PORTAL_REINO.forEach((id, b) -> sb.append(id).append(';').append(b.getX()).append(';').append(b.getY()).append(';').append(b.getZ()).append('\n'));
        try { Files.writeString(arquivoReino(nivel), sb.toString(), StandardCharsets.UTF_8); } catch (Exception ignorado) { }
    }

    /** O reino a que a masmorra pertence, pelo começo do id (dwargon-ferro → dwargon; sakura-… → leste-sakura). */
    private static Reino reinoDe(String id) {
        String chave = id.startsWith("coliseu-tempest") ? "tempest" : id.startsWith("sakura-") ? "leste-sakura" : id.contains("-") ? id.substring(0, id.indexOf('-')) : id;
        for (Reino rr : Reinos.todos()) if (rr.id().equals(chave)) return rr;
        return null;
    }

    /** O pórtico que vale como "o do reino": o próprio, ou a entrada normal quando ela já fica dentro da capital. */
    private static BlockPos portalNoReino(Entrada e) {
        BlockPos k = PORTAL_REINO.get(e.id());
        return k == null || k.getY() < -1000 ? null : k;
    }

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
    private static StructureStart estrutura(ServerLevel nivel, JsonObject m, String estruturaColocada, int descer) {
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
                if (inicio.isValid()) {
                    if (descer != 0) inicio.getPieces().forEach(pc -> pc.move(0, descer, 0));   // mesmo ajuste feito ao colocar
                    return inicio;
                }
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
        int descer = 0;
        if (p.length >= 6) try { descer = Integer.parseInt(p[5].trim()); } catch (NumberFormatException ignorado) { }
        StructureStart inicio = estrutura(nivel, m, p[4], descer);
        if (inicio == null) return "não consegui recalcular a estrutura " + p[4];
        BoundingBox bb = inicio.getBoundingBox();
        BlockPos centro = bb.getCenter();
        int sx = centro.getX(), sz = centro.getZ();
        if (m.has("entrada")) { JsonArray e = m.getAsJsonArray("entrada"); sx = e.get(0).getAsInt(); sz = e.get(2).getAsInt(); }
        for (int cx = (bb.minX() >> 4) - 1; cx <= (bb.maxX() >> 4) + 1; cx++) for (int cz = (bb.minZ() >> 4) - 1; cz <= (bb.maxZ() >> 4) + 1; cz++) nivel.getChunk(cx, cz);
        nivel.getChunk(sx >> 4, sz >> 4);
        int chao = m.has("entrada") ? m.getAsJsonArray("entrada").get(1).getAsInt() - 1
                : ANTIGAS.containsKey(id) ? ANTIGAS.get(id).getY() - 1 : chaoNatural(nivel, sx, sz);
        boolean enterrada = bb.maxY() < chao - 3 || descer != 0;   // descida de propósito para caber sob a capital: é enterrada
        // nível próprio da masmorra (painel): vale em toda a caixa dela, com folga
        int nivelFixo = m.has("nivel") && !m.get("nivel").isJsonNull() ? m.get("nivel").getAsInt() : 0;
        // embaixo de uma capital e sem entrada marcada: o pórtico cairia no meio da praça. Espera o admin escolher o lugar.
        boolean naCapital = false;
        for (Reino rr : Reinos.todos()) if (rr.distancia(centro.getX(), centro.getZ()) == 0) naCapital = true;
        // masmorra embaixo de reino é fim de jogo: nunca abaixo do nível 50, mesmo sem nível no painel
        if (naCapital && enterrada) nivelFixo = Math.max(nivelFixo, br.com.frxcode.frxworld.progressao.Zonas.NIVEL_CHEFE_MASMORRA);
        br.com.frxcode.frxworld.progressao.AreasNivel.definir(nivel.getServer(), id, bb.inflatedBy(3), nivelFixo);
        if (enterrada && naCapital && !m.has("entrada") && !MANUAIS.containsKey(id)) {
            Reino dona = null;
            for (Reino rr : Reinos.todos()) if (rr.distancia(centro.getX(), centro.getZ()) == 0) dona = rr;
            BlockPos auto = dona == null ? null : lugarNoReino(nivel, dona);
            if (auto == null) return "embaixo de uma capital e sem lugar livre: escolha o do portal com /frx masmorra portal " + id + " (em pé no ponto)";
            MANUAIS.put(id, auto);
        }
        if (MANUAIS.containsKey(id)) { BlockPos mp = MANUAIS.get(id); sx = mp.getX(); sz = mp.getZ(); chao = mp.getY() - 1; }
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

    // ------------------------------------------------------------------ pórtico dentro do reino

    private static JsonObject posicoesDoReino(String reino) {
        try {
            Path p = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("frxworld").resolve("reinos").resolve("posicoes.json");
            JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has(reino) && o.get(reino).isJsonObject() ? o.getAsJsonObject(reino) : new JsonObject();
        } catch (Exception e) { return new JsonObject(); }
    }

    /**
     * Piso quadrado (lado 2×meio+1) com `livre` blocos de ar por cima; devolve o Y do piso ou Integer.MIN_VALUE.
     * aCeuAberto: usa o topo do terreno, aceita até 1 bloco de desnível e recusa o que estiver mais de 3 blocos acima do
     * ponto mais baixo em volta (telhados, muralhas). Senão, procura um piso em volta da altura de referência
     * (reino subterrâneo, ou rua coberta por toldo e pérgola).
     */
    private static int pisoLivre(ServerLevel n, BlockPos.MutableBlockPos m, int x, int z, int ref, boolean aCeuAberto, int meio, int livre, int faixa) {
        int de, ate;
        if (aCeuAberto) {
            int topo = Integer.MIN_VALUE, fundo = Integer.MAX_VALUE;
            for (int dx = -meio; dx <= meio; dx++) for (int dz = -meio; dz <= meio; dz++) {
                int h = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1;
                topo = Math.max(topo, h); fundo = Math.min(fundo, h);
            }
            if (topo - fundo > 1) return Integer.MIN_VALUE;
            int baixo = Integer.MAX_VALUE;
            for (int dx = -12; dx <= 12; dx += 4) for (int dz = -12; dz <= 12; dz += 4)
                baixo = Math.min(baixo, n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1);
            if (topo - baixo > 3) return Integer.MIN_VALUE;
            de = ate = topo;
        } else { de = ref + faixa; ate = ref - faixa; }
        for (int y = de; y >= ate; y--) {
            boolean ok = true;
            for (int dx = -meio; dx <= meio && ok; dx++) for (int dz = -meio; dz <= meio && ok; dz++) {
                BlockState piso = n.getBlockState(m.set(x + dx, y, z + dz));
                BlockState abaixo = n.getBlockState(m.set(x + dx, y - 1, z + dz));
                boolean firme = piso.isSolidRender(n, m) || (aCeuAberto && abaixo.isSolidRender(n, m) && piso.getFluidState().isEmpty());
                if (!firme || !piso.getFluidState().isEmpty() || piso.is(net.minecraft.tags.BlockTags.LEAVES) || piso.is(Blocks.FARMLAND)
                        || !abaixo.getFluidState().isEmpty()) { ok = false; break; }
                for (int dy = 1; dy <= livre; dy++) {
                    BlockState b = n.getBlockState(m.set(x + dx, y + dy, z + dz));
                    if (!b.isAir() && !(b.canBeReplaced() && b.getFluidState().isEmpty())) { ok = false; break; }
                }
            }
            if (ok) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Lugar achado para um pórtico: posição dos pés e o meio-lado do piso (3 = 7×7, 2 = 5×5, 1 = só o portal 3×3). */
    private record Lugar(BlockPos pe, int meio) {}

    /** Lugar para o pórtico grande (7×7) dentro da capital; null se não couber. */
    static BlockPos lugarNoReino(ServerLevel n, Reino rr) {
        Lugar l = lugar(n, rr, 3);
        return l == null ? null : l.pe();
    }

    /**
     * Lugar para o pórtico dentro da capital, sem ninguém escolher: parte da praça (ou do centro) e abre em espiral,
     * longe dos NPCs e dos outros pórticos. Tenta o pórtico grande a céu aberto; se a capital for apertada, o de 5×5 e,
     * por último, só o portal 3×3, primeiro a céu aberto e depois em rua coberta.
     */
    private static Lugar lugar(ServerLevel n, Reino rr, int meioMinimo) {
        JsonObject pos = posicoesDoReino(rr.id());
        int ax = rr.centroX(), az = rr.centroZ(), ref = rr.chao();
        if (pos.has("praca") && pos.get("praca").isJsonArray() && pos.getAsJsonArray("praca").size() >= 2) {
            ax = pos.getAsJsonArray("praca").get(0).getAsInt();
            az = pos.getAsJsonArray("praca").get(1).getAsInt();
        }
        List<BlockPos> evitar = new ArrayList<>();
        if (pos.has("npcs") && pos.get("npcs").isJsonObject())
            for (Map.Entry<String, JsonElement> e : pos.getAsJsonObject("npcs").entrySet()) {
                if (!e.getValue().isJsonArray() || e.getValue().getAsJsonArray().size() < 3) continue;
                JsonArray a = e.getValue().getAsJsonArray();
                evitar.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
                if (e.getKey().equals("rei")) ref = a.get(1).getAsInt() - 1;
            }
        List<BlockPos> porticos = new ArrayList<>();
        for (Entrada e : ENTRADAS.values()) if (e.fora().getY() > -1000) porticos.add(e.fora());
        for (BlockPos k : PORTAL_REINO.values()) if (k.getY() > -1000) porticos.add(k);
        int limite = Math.min(240, Math.max(rr.larguraChunks(), rr.profundidadeChunks()) * 8);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // {meio-lado, a céu aberto (1) ou coberto (0)}
        int[][] tentativas = rr.subterraneo() ? new int[][]{{3, 0}, {2, 0}, {1, 0}} : new int[][]{{3, 1}, {2, 1}, {1, 1}, {2, 0}, {1, 0}};
        for (int[] t : tentativas) {
            int meio = t[0];
            if (meio < meioMinimo) continue;
            boolean aberto = t[1] == 1;
            int passo = meio == 3 ? 3 : 2;
            for (int raio = 9; raio <= limite; raio += passo)
                for (int dx = -raio; dx <= raio; dx += passo) for (int dz = -raio; dz <= raio; dz += passo) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != raio) continue;
                    int x = ax + dx, z = az + dz;
                    if (rr.distancia(x - 5, z - 5) != 0 || rr.distancia(x + 5, z + 5) != 0) continue;
                    if (!rr.contemFatia(x >> 4, z >> 4)) continue;   // só onde a capital tem construção de verdade
                    boolean perto = false;
                    for (BlockPos q : evitar) if (Math.abs(q.getX() - x) <= 7 && Math.abs(q.getZ() - z) <= 7) perto = true;
                    for (BlockPos q : porticos) if (Math.abs(q.getX() - x) <= 12 && Math.abs(q.getZ() - z) <= 12) perto = true;
                    if (perto) continue;
                    int y = pisoLivre(n, m, x, z, ref, aberto, meio, meio == 3 ? 5 : meio == 2 ? 4 : 3, rr.subterraneo() ? 4 : 14);
                    if (y != Integer.MIN_VALUE) return new Lugar(new BlockPos(x, y + 1, z), meio);
                }
        }
        return null;
    }

    /** Pórtico para capital apertada: 5×5 com quatro pilares (meio = 2) ou só o portal 3×3 no chão (meio = 1). */
    private static void porticoMenor(ServerLevel n, BlockPos base, int meio) {
        BlockState tijolo = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), pilar = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        int alto = meio == 2 ? 4 : 3;
        for (int dx = -meio; dx <= meio; dx++) for (int dz = -meio; dz <= meio; dz++) {
            boolean borda = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            por(n, base.offset(dx, 0, dz), dx == 0 && dz == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                    : borda ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState());
            for (int dy = 1; dy <= alto; dy++) por(n, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
        }
        if (meio != 2) return;
        for (int i = -2; i <= 2; i++) {
            por(n, base.offset(i, 3, -2), tijolo); por(n, base.offset(i, 3, 2), tijolo);
            por(n, base.offset(-2, 3, i), tijolo); por(n, base.offset(2, 3, i), tijolo);
        }
        for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2}) {
            for (int dy = 1; dy <= 3; dy++) por(n, base.offset(dx, dy, dz), pilar);
            por(n, base.offset(dx, 4, dz), Blocks.SOUL_LANTERN.defaultBlockState());
        }
    }

    private static boolean anelLivre(ServerLevel n, BlockPos.MutableBlockPos m, int x, int y, int z, int raio) {
        for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != raio) continue;
            if (!n.getBlockState(m.set(x + dx, y - 1, z + dz)).isSolidRender(n, m)) return false;
            for (int dy = 0; dy <= 3; dy++) if (!n.getBlockState(m.set(x + dx, y + dy, z + dz)).isAir()) return false;
        }
        return true;
    }

    /** O maior salão coberto da construção (para o chefe): piso de construção e o maior quadrado livre em volta. */
    private static BlockPos salaGrande(ServerLevel n, StructureStart inicio) {
        BoundingBox bb = inicio.getBoundingBox();
        List<BoundingBox> caixas = new ArrayList<>();
        inicio.getPieces().forEach(pc -> caixas.add(pc.getBoundingBox()));
        if (caixas.isEmpty()) caixas.add(bb);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos centro = bb.getCenter(), melhor = null;
        double melhorNota = -1e18;
        for (BoundingBox c : caixas)
            for (int x = c.minX() + 2; x <= c.maxX() - 2; x += 3) for (int z = c.minZ() + 2; z <= c.maxZ() - 2; z += 3)
                for (int y = c.maxY() - 2; y >= c.minY() + 1; y--) {
                    if (!sala(n, m, x, y, z)) continue;
                    if (tetoConstruido(n, m, x, y, z)) {
                        int raio = 1;
                        while (raio < 9 && anelLivre(n, m, x, y, z, raio + 1)) raio++;
                        double dist = Math.sqrt(Math.pow(x - centro.getX(), 2) + Math.pow(z - centro.getZ(), 2));
                        double nota = raio * 100 - dist / 4;
                        if (raio >= 3 && nota > melhorNota) { melhorNota = nota; melhor = new BlockPos(x, y, z); }
                    }
                    y -= 4;   // pula o vão desta sala e procura o andar de baixo
                }
        return melhor;
    }

    /**
     * Garante o chefe da masmorra (nas que não têm um próprio) e o pórtico dentro do reino dela.
     * O pórtico leva à primeira sala (masmorra enterrada) ou ao marco ao lado da construção (de superfície); o marco vira portal de volta.
     */
    private static String ligarAoReino(ServerLevel nivel, String id, String linha) {
        Entrada e = ENTRADAS.get(id);
        if (e == null || e.fora().getY() < -1000) { PORTAL_REINO.put(id, SEM); return "sem entrada montada"; }
        String[] p = linha.split(";");
        JsonObject m = def(id);
        String nota = "";
        if (m != null && p.length >= 5 && ChefesMasmorra.precisaInicial(nivel.getServer(), id, p[4])) {
            int descer = 0;
            if (p.length >= 6) try { descer = Integer.parseInt(p[5].trim()); } catch (NumberFormatException ignorado) { }
            StructureStart inicio = estrutura(nivel, m, p[4], descer);
            if (inicio != null) {
                BoundingBox bb = inicio.getBoundingBox();
                for (int cx = (bb.minX() >> 4) - 1; cx <= (bb.maxX() >> 4) + 1; cx++) for (int cz = (bb.minZ() >> 4) - 1; cz <= (bb.maxZ() >> 4) + 1; cz++) nivel.getChunk(cx, cz);
                BlockPos salao = salaGrande(nivel, inicio);
                if (salao == null) salao = e.dentro();
                if (salao != null) nota = " · " + ChefesMasmorra.garantirInicial(nivel.getServer(), id, p[4], salao);
                else nota = " · sem salão para o chefe";
            }
        }
        Reino rr = reinoDe(id);
        if (rr == null) { PORTAL_REINO.put(id, SEM); return "sem reino" + nota; }
        if (rr.distancia(e.fora().getX(), e.fora().getZ()) == 0) { PORTAL_REINO.put(id, e.fora()); return "a entrada já fica no reino" + nota; }
        Lugar achado = lugar(nivel, rr, 1);
        if (achado == null) { PORTAL_REINO.put(id, SEM); return "não achei lugar livre em " + rr.id() + nota; }
        BlockPos lugar = achado.pe();
        if (achado.meio() == 3) portico(nivel, lugar.below(), true); else porticoMenor(nivel, lugar.below(), achado.meio());
        rotulo(nivel, lugar, "☠ " + nome(id), "Pise no portal para ir à masmorra");
        if (e.dentro() == null) {   // de superfície: o marco ao lado da construção vira o portal de volta
            nivel.getChunk(e.fora().getX() >> 4, e.fora().getZ() >> 4);
            portico(nivel, e.fora().below(), true);
            rotulo(nivel, e.fora(), "☠ " + nome(id), "Pise no portal para voltar ao reino");
        }
        PORTAL_REINO.put(id, lugar);
        NO_MAPA.clear();
        return "pórtico " + (achado.meio() == 3 ? "7×7" : achado.meio() == 2 ? "5×5" : "3×3") + " em " + rr.id() + ": " + lugar.getX() + ", " + lugar.getY() + ", " + lugar.getZ() + nota;
    }

    /**
     * Os reinos no mapa de todo jogador: o contorno da capital com o nome (pelo nosso desenho no JourneyMap, que aparece
     * para todos; os pontos globais do JourneyMap às vezes não chegavam aos outros jogadores).
     */
    private static void marcarReinos(ServerPlayer p) {
        for (Reino r : Reinos.todos()) {
            int x0 = r.chunkX0() << 4, z0 = r.chunkZ0() << 4;
            int x1 = x0 + r.larguraChunks() * 16 - 1, z1 = z0 + r.profundidadeChunks() * 16 - 1;
            br.com.frxcode.frxworld.protecao.MapaJm.mostrar(p, "reino_" + r.id(), x0, z0, x1, z1, 0xD4A93A, 0.08f, "👑 " + r.nome(), r.nome());
        }
    }

    /** Marca no mapa (JourneyMap) de um jogador os pórticos das masmorras que ficam nos reinos. */
    private static void marcarNoMapa(ServerPlayer p) {
        marcarReinos(p);
        for (Entrada e : ENTRADAS.values()) {
            BlockPos k = portalNoReino(e);
            if (k == null) continue;
            br.com.frxcode.frxworld.protecao.MapaJm.mostrar(p, "masmorra_" + e.id(), k.getX() - 5, k.getZ() - 5, k.getX() + 5, k.getZ() + 5, 0x8E24AA, 0.55f,
                    "☠ " + nome(e.id()), "Portal da masmorra: " + nome(e.id()));
        }
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aoSair(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent ev) {
        NO_MAPA.remove(ev.getEntity().getUUID());
        ORIGEM.remove(ev.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void aoLigar(ServerStartedEvent ev) {
        ServerLevel nivel = ev.getServer().getLevel(Level.OVERWORLD);
        if (nivel == null) return;
        ler(nivel);
        lerReino(nivel);
        NO_MAPA.clear();
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
            } else {
                // depois das entradas: o chefe e o pórtico dentro do reino, uma masmorra por vez
                String falta = null;
                for (String id : Masmorras.lidas(nivel).keySet()) if (ENTRADAS.containsKey(id) && !PORTAL_REINO.containsKey(id)) { falta = id; break; }
                if (falta != null) {
                    try {
                        FrxWorld.LOG.info("Masmorra {} no reino: {}", falta, ligarAoReino(nivel, falta, Masmorras.lidas(nivel).get(falta)));
                    } catch (Throwable t) {
                        FrxWorld.LOG.warn("Masmorra {} no reino falhou: {}", falta, t.toString());
                    }
                    if (!PORTAL_REINO.containsKey(falta)) PORTAL_REINO.put(falta, SEM);
                    gravarReino(nivel);
                    espera = 200;
                } else espera = 6000;   // confere de novo em 5 min (masmorras colocadas depois)
            }
        }

        // mapa: cada jogador recebe as marcações uma vez por sessão (e de novo quando surge um pórtico)
        if (s.getTickCount() % 100 == 0)
            for (ServerPlayer p : s.getPlayerList().getPlayers())
                if (p.tickCount > 100 && NO_MAPA.add(p.getUUID())) marcarNoMapa(p);

        // portais
        if (s.getTickCount() % 5 != 0 || ENTRADAS.isEmpty()) return;
        long agora = System.currentTimeMillis();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.level() != nivel || p.isSpectator()) continue;
            BlockPos pe = p.blockPosition();
            for (Entrada e : ENTRADAS.values()) {
                if (e.fora().getY() < -1000) continue;
                BlockPos reino = portalNoReino(e);
                if (reino != null && reino.equals(e.fora())) reino = null;   // a entrada normal já é a do reino
                boolean peloReino = reino != null && pe.equals(reino);
                boolean voltarAoReino = reino != null && e.dentro() == null && pe.equals(e.fora());   // marco de superfície
                boolean entrar = peloReino || (e.dentro() != null && pe.equals(e.fora())), sair = voltarAoReino || (e.dentro() != null && pe.equals(e.dentro()));
                if (!entrar && !sair) continue;
                if (RECARGA.getOrDefault(p.getUUID(), 0L) > agora) break;
                RECARGA.put(p.getUUID(), agora + 4000);
                BlockPos d;
                if (entrar) {
                    ORIGEM.put(p.getUUID(), peloReino ? reino : e.fora());
                    d = e.dentro() != null ? e.dentro() : e.fora().offset(0, 0, 2);
                } else if (voltarAoReino) d = reino.offset(0, 0, 2);
                else {
                    BlockPos veio = ORIGEM.remove(p.getUUID());
                    d = (veio != null && (veio.equals(reino) || veio.equals(e.fora())) ? veio : e.fora()).offset(0, 0, 2);
                }
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
                .then(Commands.literal("masmorra").then(Commands.literal("secar").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Masmorras.lidas(c.getSource().getServer().overworld()).keySet(), b))
                        .executes(ctx -> {
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                            ServerLevel nv = ctx.getSource().getServer().overworld();
                            String linha = Masmorras.lidas(nv).get(id);
                            JsonObject m = def(id);
                            if (linha == null || m == null) { ctx.getSource().sendFailure(Component.literal("Masmorra desconhecida ou ainda não colocada.")); return 0; }
                            String[] p = linha.split(";");
                            int descer = 0;
                            if (p.length >= 6) try { descer = Integer.parseInt(p[5].trim()); } catch (NumberFormatException ignorado) { }
                            StructureStart inicio = p.length >= 5 ? estrutura(nv, m, p[4], descer) : null;
                            if (inicio == null) { ctx.getSource().sendFailure(Component.literal("Não consegui recalcular a estrutura.")); return 0; }
                            int n = Masmorras.secar(nv, inicio);
                            ctx.getSource().sendSuccess(() -> Component.literal("Masmorra " + id + " seca: " + n + " blocos de água e caverna fechados.").withStyle(ChatFormatting.GOLD), true);
                            return n;
                        }))))
                .then(Commands.literal("masmorra").then(Commands.literal("portal").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Masmorras.lidas(c.getSource().getServer().overworld()).keySet(), b))
                        .executes(ctx -> {
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                            ServerPlayer adm = ctx.getSource().getPlayerOrException();
                            ServerLevel nv = ctx.getSource().getServer().overworld();
                            String linha = Masmorras.lidas(nv).get(id);
                            if (linha == null) { ctx.getSource().sendFailure(Component.literal("Essa masmorra ainda não foi colocada no mundo.")); return 0; }
                            Entrada velha = ENTRADAS.get(id);
                            if (velha != null && velha.dentro() != null) {
                                nv.getChunk(velha.dentro().getX() >> 4, velha.dentro().getZ() >> 4);
                                if (nv.getBlockState(velha.dentro().below()).is(Blocks.CRYING_OBSIDIAN)) por(nv, velha.dentro().below(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                                for (ArmorStand a : nv.getEntitiesOfClass(ArmorStand.class, new AABB(velha.dentro()).inflate(4), x -> x.getTags().contains(TAG))) a.discard();
                            }
                            MANUAIS.put(id, adm.blockPosition());
                            ANTIGAS.remove(id);
                            ENTRADAS.remove(id);
                            PORTAL_REINO.remove(id);
                            gravarReino(nv);
                            String r = montar(nv, id, linha);
                            gravar(nv);
                            ctx.getSource().sendSuccess(() -> Component.literal("Portal de " + nome(id) + ": " + r).withStyle(ChatFormatting.GOLD), true);
                            return 1;
                        }))).then(Commands.literal("entradas")
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
                                    PORTAL_REINO.remove(id);
                                    gravarReino(nv);
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
                            PORTAL_REINO.clear();
                            gravarReino(nv);
                            pendentes.clear();
                            espera = 20;
                            ctx.getSource().sendSuccess(() -> Component.literal("Refazendo as entradas das masmorras, uma a cada 10 s. Veja o resultado em /frx masmorra entradas."), true);
                            return 1;
                        })))));
    }
}
