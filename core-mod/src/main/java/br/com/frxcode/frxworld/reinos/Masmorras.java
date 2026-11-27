package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Masmorras fixas de outros mods em pontos escolhidos (ex.: Fábrica Ancestral do Cataclysm embaixo do coliseu de Tempest).
 * Configuração em posicoes.json → "masmorras": [{id, estrutura, x, z, entrada:[x,y,z]}].
 * Cada uma é colocada uma vez por mundo ao ligar o servidor (registro em <mundo>/frx_masmorras.txt),
 * com um poço de escada da entrada até a estrutura.
 */
public class Masmorras {

    private static Path marca(ServerLevel nivel) {
        return nivel.getServer().getWorldPath(LevelResource.ROOT).resolve("frx_masmorras.txt");
    }

    /** Lista lida do painel no último início (null = painel fora: usa o posicoes.json). */
    private static volatile JsonArray doPainel = null;

    static JsonArray config() {
        JsonArray p = doPainel;
        if (p != null) return p;
        return doArquivo();
    }

    private static JsonArray doArquivo() {
        try {
            Path p = FMLPaths.GAMEDIR.get().resolve("frxworld").resolve("reinos").resolve("posicoes.json");
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("masmorras") ? o.getAsJsonArray("masmorras") : new JsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }

    /** Registro por mundo: uma linha por masmorra colocada, "id;x;y;z;estrutura". */
    static Map<String, String> lidas(ServerLevel nivel) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            if (Files.exists(marca(nivel)))
                for (String l : Files.readAllLines(marca(nivel))) if (!l.isBlank()) m.put(l.split(";")[0], l);
        } catch (Exception ignorado) {}
        return m;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoLigar(ServerStartedEvent evento) {
        ServerLevel nivel = evento.getServer().getLevel(Level.OVERWORLD);
        if (nivel == null) return;
        try {
            doPainel = br.com.frxcode.frxworld.acesso.PainelApi.lerMasmorras().get(10, java.util.concurrent.TimeUnit.SECONDS);
            FrxWorld.LOG.info("Masmorras: {} vindas do painel", doPainel.size());
        } catch (Exception e) {
            doPainel = null;
            FrxWorld.LOG.warn("Masmorras: painel não respondeu ({}), usando posicoes.json", e.getMessage());
        }
        // uma masmorra por vez, com folga entre elas: gerar tudo no mesmo tick derrubava o servidor (vigia de 60 s)
        fila.clear();
        preparando = null;
        esperandoVazio = 0;
        feitasFila = lidas(nivel);
        novasFila = new HashSet<>();
        for (JsonElement el : config()) {
            JsonObject m = el.getAsJsonObject();
            String id = m.get("id").getAsString();
            boolean refazer = m.has("refazer") && m.get("refazer").getAsBoolean();
            if (!feitasFila.containsKey(id) || refazer) fila.add(m);
        }
        espera = 100;   // 5 s depois de ligar
        if (fila.isEmpty()) avisarPainel();
        else FrxWorld.LOG.info("Masmorras: {} na fila para gerar (uma a cada ~10 s)", fila.size());
    }

    private static final java.util.ArrayDeque<JsonObject> fila = new java.util.ArrayDeque<>();
    private static Map<String, String> feitasFila = new LinkedHashMap<>();
    private static Set<String> novasFila = new HashSet<>();
    private static int espera = 0, esperandoVazio = 0;
    private static java.util.List<java.util.concurrent.CompletableFuture<?>> preparando = null;

    @SubscribeEvent
    public void aoTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || fila.isEmpty()) return;
        if (--espera > 0) return;
        ServerLevel nivel = e.getServer().getLevel(Level.OVERWORLD);
        if (nivel == null) return;
        JsonObject m = fila.peek();
        // 1) terreno em volta gerado em segundo plano (threads de geração), sem travar o servidor
        if (preparando == null) {
            int x0 = m.get("x").getAsInt(), z0 = m.get("z").getAsInt();
            preparando = new java.util.ArrayList<>();
            for (int cx = (x0 >> 4) - 6; cx <= (x0 >> 4) + 6; cx++)
                for (int cz = (z0 >> 4) - 6; cz <= (z0 >> 4) + 6; cz++)
                    preparando.add(nivel.getChunkSource().getChunkFuture(cx, cz, net.minecraft.world.level.chunk.ChunkStatus.FULL, true));
            espera = 20;
            return;
        }
        if (!preparando.stream().allMatch(java.util.concurrent.CompletableFuture::isDone)) { espera = 20; return; }
        // 2) colar a estrutura trava o servidor alguns segundos: espera ninguém estar online (no máx. 20 min)
        boolean gente = !e.getServer().getPlayerList().getPlayers().isEmpty();
        if (gente && esperandoVazio++ < 60) {
            if (esperandoVazio == 1) FrxWorld.LOG.info("Masmorra {}: aguardando o servidor ficar vazio para colar", m.get("id").getAsString());
            espera = 400;   // tenta de novo em ~20 s
            return;
        }
        esperandoVazio = 0;
        preparando = null;
        fila.poll();
        String id = m.get("id").getAsString();
        boolean refazer = m.has("refazer") && m.get("refazer").getAsBoolean();
        long t0 = System.currentTimeMillis();
        String r;
        try { r = colocar(nivel, m); } catch (Exception ex) { r = "erro: " + ex; }
        FrxWorld.LOG.info("Masmorra {}{}: {} ({} s)", id, refazer ? " (recolocar)" : "", r, (System.currentTimeMillis() - t0) / 1000);
        if (r.startsWith("ok;")) {
            feitasFila.put(id, id + r.substring(2));
            novasFila.add(id);
            try { Files.write(marca(nivel), feitasFila.values()); } catch (Exception ignorado) {}
        }
        espera = 200;   // ~10 s até a próxima
        if (fila.isEmpty()) avisarPainel();
    }

    /** Avisa o painel onde cada masmorra está (as novas limpam o "recolocar"). */
    private static void avisarPainel() {
        JsonArray colocadas = new JsonArray();
        for (String linha : feitasFila.values()) {
            String[] p = linha.split(";");
            if (p.length < 5) continue;
            try {
                JsonObject c = new JsonObject();
                c.addProperty("id", p[0]);
                c.addProperty("x", Integer.parseInt(p[1]));
                c.addProperty("y", Integer.parseInt(p[2]));
                c.addProperty("z", Integer.parseInt(p[3]));
                c.addProperty("estrutura", p[4]);
                if (novasFila.contains(p[0])) c.addProperty("nova", true);
                colocadas.add(c);
            } catch (NumberFormatException ignorado) {}
        }
        if (doPainel != null && colocadas.size() > 0) br.com.frxcode.frxworld.acesso.PainelApi.enviarMasmorras(colocadas);
    }

    /**
     * Coloca a masmorra. "estrutura" aceita alternativas separadas por | (a primeira que gerar vale).
     * Tenta o ponto pedido e até 6 pontos vizinhos (algumas estruturas exigem terreno certo).
     * Retorna "ok;x;y;z;estrutura" ou o motivo da falha.
     */
    static String colocar(ServerLevel nivel, JsonObject m) {
        int x0 = m.get("x").getAsInt(), z0 = m.get("z").getAsInt();
        ChunkGenerator gerador = nivel.getChunkSource().getGenerator();
        String motivo = "nenhuma estrutura da lista existe (mods instalados?)";
        for (String nome : m.get("estrutura").getAsString().split("\\|")) {
            ResourceLocation rl = ResourceLocation.tryParse(nome.trim());
            Structure estrutura = rl == null ? null : nivel.registryAccess().registryOrThrow(Registries.STRUCTURE).get(rl);
            if (estrutura == null) continue;
            for (int t = 0; t < 7; t++) {
                int x = t == 0 ? x0 : x0 + (int) (Math.cos(t) * 48 * ((t + 1) / 2)), z = t == 0 ? z0 : z0 + (int) (Math.sin(t) * 48 * ((t + 1) / 2));
                // gera o terreno da área antes, para a capital não colar por cima depois
                int r0 = t == 0 ? 6 : 4;
                for (int cx = (x >> 4) - r0; cx <= (x >> 4) + r0; cx++) for (int cz = (z >> 4) - r0; cz <= (z >> 4) + r0; cz++) nivel.getChunk(cx, cz);
                StructureStart inicio;
                try {
                    inicio = estrutura.generate(nivel.registryAccess(), gerador, gerador.getBiomeSource(), nivel.getChunkSource().randomState(),
                            nivel.getStructureManager(), nivel.getSeed(), new ChunkPos(new BlockPos(x, 0, z)), 0, nivel, b -> true);
                } catch (Exception ex) {
                    motivo = rl + " falhou: " + ex;
                    break;
                }
                if (!inicio.isValid()) { motivo = "não foi possível gerar " + rl; continue; }
                // dentro de uma capital a masmorra tem que caber inteira embaixo do chão (senão destruiria a cidade):
                // se a estrutura nasceria alta demais, o mod a desce antes de colocar
                BoundingBox bruta = caixaDasPecas(inicio);
                if (bruta == null) { motivo = rl + " veio sem peças"; continue; }
                BlockPos cb = bruta.getCenter();
                boolean naCapital = false;
                for (Reino rr : Reinos.todos()) if (rr.distancia(cb.getX(), cb.getZ()) == 0) naCapital = true;
                int descer = 0;
                if (naCapital) {
                    int solo = nivel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cb.getX(), cb.getZ());
                    if (bruta.maxY() > solo - 12) descer = (solo - 14) - bruta.maxY();
                    FrxWorld.LOG.info("Masmorra {}: {} iria de y {} a {} (chão em y {}); descendo {} blocos", m.get("id").getAsString(), rl, bruta.minY(), bruta.maxY(), solo, -descer);
                    if (bruta.minY() + descer < nivel.getMinBuildHeight() + 4) {
                        motivo = rl + " é alta demais para caber embaixo da capital (" + (bruta.maxY() - bruta.minY() + 1) + " blocos de altura)";
                        break;
                    }
                }
                if (descer != 0) { final int dy = descer; inicio.getPieces().forEach(pc -> pc.move(0, dy, 0)); }
                BoundingBox bb = inicio.getBoundingBox();
                ChunkPos c0 = new ChunkPos(SectionPos.blockToSectionCoord(bb.minX()), SectionPos.blockToSectionCoord(bb.minZ()));
                ChunkPos c1 = new ChunkPos(SectionPos.blockToSectionCoord(bb.maxX()), SectionPos.blockToSectionCoord(bb.maxZ()));
                ChunkPos.rangeClosed(c0, c1).forEach(cp -> {
                    nivel.getChunk(cp.x, cp.z);
                    inicio.placeInChunk(nivel, nivel.structureManager(), gerador, nivel.getRandom(),
                            new BoundingBox(cp.getMinBlockX(), nivel.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), nivel.getMaxBuildHeight(), cp.getMaxBlockZ()), cp);
                });
                if (m.has("entrada")) {
                    JsonArray e = m.getAsJsonArray("entrada");
                    poco(nivel, new BlockPos(e.get(0).getAsInt(), e.get(1).getAsInt(), e.get(2).getAsInt()), bb);
                }
                // enterrada de propósito (descida para caber sob a capital): seca a água dos aquíferos e fecha a casca
                if (descer != 0) FrxWorld.LOG.info("Masmorra {}: {} blocos de água/caverna fechados", m.get("id").getAsString(), secar(nivel, inicio));
                // tesouros: baús do Lootr (cada jogador abre o seu) espalhados pelas salas da estrutura
                java.util.List<BoundingBox> salas = new java.util.ArrayList<>();
                inicio.getPieces().forEach(pc -> salas.add(pc.getBoundingBox()));
                if (salas.isEmpty()) salas.add(bb);
                int baus = colocarBaus(nivel, salas, 5, TABELA, m.has("entrada") ? m.getAsJsonArray("entrada") : null);
                FrxWorld.LOG.info("Masmorra {}: {} baús de tesouro", m.get("id").getAsString(), baus);
                BlockPos c = bb.getCenter();
                int y = m.has("entrada") ? m.getAsJsonArray("entrada").get(1).getAsInt()
                        : nivel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                // com entrada definida, o ponto mostrado em /masmorras e no painel é a entrada (não o centro da estrutura)
                String sufixo = descer != 0 ? ";" + descer : "";   // quanto a estrutura foi descida (a entrada precisa do mesmo ajuste)
                if (m.has("entrada")) {
                    JsonArray e = m.getAsJsonArray("entrada");
                    return "ok;" + e.get(0).getAsInt() + ";" + y + ";" + e.get(2).getAsInt() + ";" + rl + sufixo;
                }
                return "ok;" + c.getX() + ";" + y + ";" + c.getZ() + ";" + rl + sufixo;
            }
        }
        return motivo;
    }

    /**
     * Seca uma masmorra enterrada: tira a água de dentro das peças, troca por pedra a água que ficou em volta
     * e fecha com pedra a casca de 1 bloco ao redor da caixa (cavernas e aquíferos que cruzavam a parede).
     * A lava fica como está. Devolve quantos blocos mudou.
     */
    static int secar(ServerLevel nivel, StructureStart inicio) {
        BoundingBox bb = inicio.getBoundingBox();
        java.util.List<BoundingBox> pecas = new java.util.ArrayList<>();
        inicio.getPieces().forEach(pc -> pecas.add(pc.getBoundingBox()));
        for (int cx = (bb.minX() - 1) >> 4; cx <= (bb.maxX() + 1) >> 4; cx++) for (int cz = (bb.minZ() - 1) >> 4; cz <= (bb.maxZ() + 1) >> 4; cz++) nivel.getChunk(cx, cz);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int mudou = 0;
        int y0 = Math.max(nivel.getMinBuildHeight() + 1, bb.minY() - 1), y1 = bb.maxY() + 1;
        for (int x = bb.minX() - 1; x <= bb.maxX() + 1; x++) for (int z = bb.minZ() - 1; z <= bb.maxZ() + 1; z++) for (int y = y0; y <= y1; y++) {
            m.set(x, y, z);
            BlockState b = nivel.getBlockState(m);
            boolean agua = b.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
            boolean casca = x < bb.minX() || x > bb.maxX() || z < bb.minZ() || z > bb.maxZ() || y < bb.minY() || y > bb.maxY();
            if (!agua && !(casca && b.isAir())) continue;
            boolean naPeca = false;
            if (!casca) for (BoundingBox c : pecas) if (c.isInside(m)) { naPeca = true; break; }
            BlockState pedra = (y < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
            BlockState novo;
            if (agua && b.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))
                novo = b.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
            else novo = naPeca ? Blocks.AIR.defaultBlockState() : pedra;
            nivel.setBlock(m, novo, 2 | 16);   // sem avisar os vizinhos: não dispara a água em volta
            mudou++;
        }
        return mudou;
    }

    /** Caixa que envolve todas as peças, calculada na hora (o getBoundingBox da estrutura guarda o primeiro valor). */
    static BoundingBox caixaDasPecas(StructureStart inicio) {
        java.util.List<BoundingBox> l = new java.util.ArrayList<>();
        inicio.getPieces().forEach(pc -> l.add(pc.getBoundingBox()));
        return BoundingBox.encapsulatingBoxes(l).orElse(null);
    }

    static final ResourceLocation TABELA = new ResourceLocation(FrxWorld.MOD_ID, "chests/masmorra");
    static final ResourceLocation TABELA_CHEFE = new ResourceLocation(FrxWorld.MOD_ID, "chests/masmorra_chefe");

    /** Lugar de baú: ar com ar em cima, chão firme, coberto (dentro da estrutura) e sem outro baú colado. */
    private static boolean lugarDeBau(ServerLevel nivel, BlockPos p) {
        return nivel.getBlockState(p).isAir() && nivel.getBlockState(p.above()).isAir()
                && nivel.getBlockState(p.below()).isFaceSturdy(nivel, p.below(), Direction.UP)
                && !nivel.canSeeSky(p)
                && Direction.Plane.HORIZONTAL.stream().noneMatch(d -> BausReino.ehBau(nivel.getBlockState(p.relative(d)))
                        || nivel.getBlockState(p.relative(d)).getBlock().getDescriptionId().contains("lootr"));
    }

    static void porBau(ServerLevel nivel, BlockPos p, ResourceLocation tabela, net.minecraft.util.RandomSource rnd) {
        BlockState bau = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,
                Direction.Plane.HORIZONTAL.getRandomDirection(rnd));
        BlockState lootr = BausReino.trocar(bau);
        nivel.setBlock(p, lootr != null ? lootr : bau, 3);
        if (nivel.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity c) {
            c.setLootTable(tabela, rnd.nextLong());
            c.setChanged();
        }
    }

    /** Espalha `qtd` baús pelas salas (caixas), longe do poço de entrada. Retorna quantos colocou. */
    static int colocarBaus(ServerLevel nivel, java.util.List<BoundingBox> salas, int qtd, ResourceLocation tabela, JsonArray entrada) {
        net.minecraft.util.RandomSource rnd = nivel.getRandom();
        java.util.List<BlockPos> postos = new java.util.ArrayList<>();
        for (int t = 0; t < 600 && postos.size() < qtd; t++) {
            BoundingBox s = salas.get(rnd.nextInt(salas.size()));
            int x = s.minX() + rnd.nextInt(Math.max(1, s.getXSpan())), z = s.minZ() + rnd.nextInt(Math.max(1, s.getZSpan()));
            if (entrada != null && Math.abs(x - entrada.get(0).getAsInt()) < 6 && Math.abs(z - entrada.get(2).getAsInt()) < 6) continue;
            boolean perto = false;
            for (BlockPos o : postos) if (o.distManhattan(new BlockPos(x, o.getY(), z)) < 10) { perto = true; break; }
            if (perto) continue;
            nivel.getChunk(x >> 4, z >> 4);
            for (int y = s.minY() + 1; y < s.maxY() - 1; y++) {
                BlockPos p = new BlockPos(x, y, z);
                if (lugarDeBau(nivel, p)) { porBau(nivel, p, tabela, rnd); postos.add(p); break; }
            }
        }
        return postos.size();
    }

    /** Chefe de masmorra derrotado: aparece um baú de prêmio onde ele caiu. */
    @SubscribeEvent
    public void chefeDerrotado(net.minecraftforge.event.entity.living.LivingDeathEvent ev) {
        if (!(ev.getEntity().level() instanceof ServerLevel nivel) || nivel.dimension() != Level.OVERWORLD) return;
        if (!ev.getEntity().getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)) return;
        BlockPos b = ev.getEntity().blockPosition();
        boolean naMasmorra = false;
        for (String linha : feitasFila.values()) {
            String[] p = linha.split(";");
            if (p.length < 4) continue;
            try {
                if (Math.abs(Integer.parseInt(p[1]) - b.getX()) < 128 && Math.abs(Integer.parseInt(p[3]) - b.getZ()) < 128) { naMasmorra = true; break; }
            } catch (NumberFormatException ignorado) {}
        }
        if (!naMasmorra) return;
        // alguns chefes explodem no fim da animação de morte: o baú só aparece depois que o corpo some (e fica protegido de explosão)
        BAUS_PENDENTES.add(new BauPendente(ev.getEntity(), b.immutable(), System.currentTimeMillis()));
    }

    /** Baú de chefe esperando o corpo sumir. */
    private static final class BauPendente {
        final net.minecraft.world.entity.Entity chefe; final BlockPos onde; final long morreu; long sumiu;
        BauPendente(net.minecraft.world.entity.Entity c, BlockPos o, long m) { chefe = c; onde = o; morreu = m; }
    }
    private static final java.util.List<BauPendente> BAUS_PENDENTES = new java.util.ArrayList<>();
    /** Baús de chefe recém-colocados, protegidos de explosão por 2 minutos: lugar -> até quando. */
    private static final java.util.Map<BlockPos, Long> BAUS_PROTEGIDOS = new java.util.HashMap<>();

    @SubscribeEvent
    public void bausDeChefe(net.minecraftforge.event.TickEvent.ServerTickEvent ev) {
        if (ev.phase != net.minecraftforge.event.TickEvent.Phase.END || BAUS_PENDENTES.isEmpty() && BAUS_PROTEGIDOS.isEmpty()) return;
        long agora = System.currentTimeMillis();
        BAUS_PROTEGIDOS.values().removeIf(ate -> ate < agora);
        for (java.util.Iterator<BauPendente> it = BAUS_PENDENTES.iterator(); it.hasNext(); ) {
            BauPendente bp = it.next();
            if (bp.sumiu == 0 && bp.chefe.isRemoved()) bp.sumiu = agora;
            // espera o corpo sumir e mais 3 s (a explosão vem no fim da animação de morte); no máximo 30 s
            boolean pronto = bp.sumiu > 0 && agora - bp.sumiu >= 3_000 || agora - bp.morreu >= 30_000;
            if (!pronto) continue;
            it.remove();
            if (bp.chefe.level() instanceof ServerLevel nivel) colocarBauDoChefe(nivel, bp.onde);
        }
    }

    /** Explosão perto de um baú de chefe recém-colocado não quebra o baú. */
    @SubscribeEvent
    public void protegerBau(net.minecraftforge.event.level.ExplosionEvent.Detonate ev) {
        if (BAUS_PROTEGIDOS.isEmpty()) return;
        ev.getAffectedBlocks().removeIf(BAUS_PROTEGIDOS::containsKey);
    }

    private static void colocarBauDoChefe(ServerLevel nivel, BlockPos b) {
        for (int r = 0; r <= 4; r++)
            for (int dy = 0; dy <= 3; dy++)
                for (BlockPos p : new BlockPos[]{b.offset(r, dy, 0), b.offset(-r, dy, 0), b.offset(0, dy, r), b.offset(0, dy, -r)}) {
                    if (nivel.getBlockState(p).isAir() && nivel.getBlockState(p.below()).isFaceSturdy(nivel, p.below(), Direction.UP)) {
                        porBau(nivel, p, TABELA_CHEFE, nivel.getRandom());
                        BAUS_PROTEGIDOS.put(p.immutable(), System.currentTimeMillis() + 120_000);
                        nivel.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 30, 0.4, 0.5, 0.4, 0.2);
                        for (net.minecraft.server.level.ServerPlayer j : nivel.players())
                            if (j.distanceToSqr(b.getX(), b.getY(), b.getZ()) < 64 * 64)
                                j.sendSystemMessage(Component.literal("☠ O chefe caiu! Um baú de tesouro apareceu onde ele tombou.").withStyle(ChatFormatting.GOLD));
                        return;
                    }
                }
    }

    /** Poço 5x5 com escada da entrada (piso) até achar uma sala vazia dentro da estrutura. */
    private static void poco(ServerLevel nivel, BlockPos topo, BoundingBox bb) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int x = topo.getX(), z = topo.getZ();
        int fundo = bb.maxY() - 3;
        for (int y = Math.min(bb.maxY(), topo.getY() - 1); y > bb.minY() + 2; y--) {
            if (nivel.getBlockState(m.set(x, y, z)).isAir() && nivel.getBlockState(m.set(x, y - 1, z)).isAir()
                    && nivel.getBlockState(m.set(x, y - 2, z)).isAir()) { fundo = y; break; }
        }
        BlockState parede = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int y = topo.getY(); y >= fundo; y--) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean anel = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                m.set(x + dx, y, z + dz);
                if (anel) { if (y < topo.getY() - 1 && y > bb.maxY()) nivel.setBlock(m, parede, 2); }
                else nivel.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            }
            nivel.setBlock(m.set(x, y, z - 2), parede, 2);
            nivel.setBlock(m.set(x, y, z - 1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 2);
            if ((topo.getY() - y) % 8 == 4)
                nivel.setBlock(m.set(x + 1, y, z + 1 + 0), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 2);
        }
        // moldura na arena: bordas de pedra e lanternas das almas
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (Math.abs(dx) < 3 && Math.abs(dz) < 3) continue;
            nivel.setBlock(m.set(x + dx, topo.getY() - 1, z + dz), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
        }
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3}) {
            nivel.setBlock(m.set(x + dx, topo.getY(), z + dz), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 2);
            nivel.setBlock(m.set(x + dx, topo.getY() + 1, z + dz), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        }
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("masmorras").executes(ctx -> {
            ServerLevel nivel = ctx.getSource().getServer().overworld();
            Map<String, String> feitas = lidas(nivel);
            ctx.getSource().sendSuccess(() -> Component.literal("══════ Masmorras dos reinos ══════").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            for (JsonElement el : config()) {
                JsonObject m = el.getAsJsonObject();
                String linha = feitas.get(m.get("id").getAsString());
                String nome = m.has("nome") ? m.get("nome").getAsString() : m.get("id").getAsString();
                String chefe = m.has("chefe") ? " — chefe: " + m.get("chefe").getAsString() : "";
                String onde;
                if (linha == null) onde = "ainda não surgiu";
                else { String[] p = linha.split(";"); onde = p.length >= 4 ? p[1] + ", " + p[2] + ", " + p[3] : "?"; }
                ctx.getSource().sendSuccess(() -> Component.literal("☠ " + nome).withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(chefe).withStyle(ChatFormatting.RED))
                        .append(Component.literal("  (" + onde + ")").withStyle(ChatFormatting.GRAY)), false);
            }
            return 1;
        }));
        evento.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("masmorra").then(Commands.literal("baus").executes(ctx -> {
                    // põe baús de tesouro nas salas em volta de quem rodou (para masmorras de mundos já gerados)
                    net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayerOrException();
                    BlockPos b = p.blockPosition();
                    int n = colocarBaus(p.serverLevel(), java.util.List.of(new BoundingBox(b.getX() - 32, b.getY() - 12, b.getZ() - 32, b.getX() + 32, b.getY() + 12, b.getZ() + 32)), 5, TABELA, null);
                    ctx.getSource().sendSuccess(() -> Component.literal(n + " baús de tesouro colocados nas salas em volta.").withStyle(ChatFormatting.GOLD), true);
                    return n;
                })).executes(ctx -> {
                    StringBuilder sb = new StringBuilder();
                    for (JsonElement el : config()) {
                        JsonObject m = el.getAsJsonObject();
                        sb.append("\n").append(m.get("id").getAsString()).append(" → ").append(m.get("estrutura").getAsString())
                                .append(" em ").append(m.get("x").getAsInt()).append(", ").append(m.get("z").getAsInt());
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("Masmorras fixas:" + (sb.length() == 0 ? " nenhuma" : sb)).withStyle(ChatFormatting.GOLD), false);
                    return 1;
                })));
    }
}
