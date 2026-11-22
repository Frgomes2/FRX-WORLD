package br.com.frxcode.frxworld.protecao;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Terrenos: o jogador escolhe uma área retangular do mundo normal (dois cantos) e compra com moedas; o preço vem do tamanho.
 * No terreno só o dono — e quem ele liberar — coloca, quebra e usa blocos; monstros e explosões não destroem nada.
 * A escolha é feita no mapa do JourneyMap (botão direito → Terreno: marcar canto), e os terrenos comprados ficam pintados
 * no mapa de todos com o nome do dono. Sem o mapa, a janela /terreno marca os cantos onde o jogador está.
 * O rank da Guilda limita o lado, a área somada e a quantidade. Terras do reino não ficam à venda: capitais, Torre e ponto
 * de partida são barrados sozinhos e o admin barra outras áreas com /frx terreno proibir.
 */
public class Terrenos {
    private static final double COBRE_POR_BLOCO = 1.2;
    private static final int LADO_MIN = 5;
    /** Por rank da Guilda (F a S): lado máximo, área somada de todos os terrenos e quantos terrenos. */
    private static final int[] LADO_MAX = {24, 34, 46, 58, 68, 92, 114}, AREA_MAX = {512, 1200, 2200, 3600, 5600, 9000, 14000}, QTD_MAX = {1, 2, 3, 4, 5, 6, 7};

    private static final class Lote {
        int id, x0, z0, x1, z1;
        UUID dono;
        long pago;
        int largura() { return x1 - x0 + 1; }
        int comprimento() { return z1 - z0 + 1; }
        int area() { return largura() * comprimento(); }
        boolean contem(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
        String onde() { return "X " + x0 + " a " + x1 + ", Z " + z0 + " a " + z1; }
    }

    private static final class Dono {
        String nome = "?";
        boolean publico;
        final Map<UUID, String> amigos = new LinkedHashMap<>();
        /** O que cada liberado pode fazer: soma de COLOCAR, QUEBRAR e USAR. Quem não está aqui (cadastro antigo) pode tudo. */
        final Map<UUID, Integer> permissoes = new HashMap<>();
        int permissao(UUID quem) { return amigos.containsKey(quem) ? permissoes.getOrDefault(quem, TUDO) : 0; }
    }

    /** Permissões que o dono dá a cada jogador: colocar blocos, quebrar blocos e usar (baús, portas, alavancas, bichos). */
    public static final int COLOCAR = 1, QUEBRAR = 2, USAR = 4, TUDO = 7;

    private static final List<Lote> LOTES = new ArrayList<>();
    private static final Map<Long, List<Lote>> POR_CHUNK = new HashMap<>();
    private static final Map<UUID, Dono> DONOS = new HashMap<>();
    private static final Set<Long> PROIBIDOS = new HashSet<>();
    private static final Map<UUID, String> ONDE = new HashMap<>();
    private static final Map<UUID, Long> MOSTRAR_ATE = new HashMap<>(), AVISADO = new HashMap<>();
    private static final Map<UUID, int[]> CANTO = new HashMap<>(), SELECAO = new HashMap<>();
    private static final Map<UUID, Integer> PINTAR_EM = new HashMap<>();
    private static MinecraftServer servidor;
    private static boolean sujo;
    private static int proximoId = 1;

    public Terrenos() {}

    // ------------------------------------------------------------------ arquivo

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_terrenos.json"); }

    private static void indexar() {
        POR_CHUNK.clear();
        for (Lote l : LOTES)
            for (int cx = l.x0 >> 4; cx <= l.x1 >> 4; cx++) for (int cz = l.z0 >> 4; cz <= l.z1 >> 4; cz++)
                POR_CHUNK.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(l);
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        servidor = ev.getServer();
        LOTES.clear(); DONOS.clear(); PROIBIDOS.clear(); POR_CHUNK.clear();
        try {
            Path a = arquivo(servidor);
            if (!Files.exists(a)) return;
            JsonObject raiz = JsonParser.parseString(Files.readString(a, StandardCharsets.UTF_8)).getAsJsonObject();
            if (raiz.has("lotes") && raiz.get("lotes").isJsonArray()) for (JsonElement e : raiz.getAsJsonArray("lotes")) {
                JsonObject j = e.getAsJsonObject();
                Lote l = new Lote();
                l.id = j.get("id").getAsInt(); l.dono = UUID.fromString(j.get("dono").getAsString());
                l.x0 = j.get("x0").getAsInt(); l.z0 = j.get("z0").getAsInt(); l.x1 = j.get("x1").getAsInt(); l.z1 = j.get("z1").getAsInt();
                l.pago = j.has("pago") ? j.get("pago").getAsLong() : 0;
                LOTES.add(l);
                proximoId = Math.max(proximoId, l.id + 1);
            }
            if (raiz.has("donos")) for (Map.Entry<String, JsonElement> e : raiz.getAsJsonObject("donos").entrySet()) {
                JsonObject j = e.getValue().getAsJsonObject();
                Dono d = new Dono();
                d.nome = j.get("nome").getAsString();
                d.publico = j.has("publico") && j.get("publico").getAsBoolean();
                if (j.has("amigos")) for (Map.Entry<String, JsonElement> am : j.getAsJsonObject("amigos").entrySet()) d.amigos.put(UUID.fromString(am.getKey()), am.getValue().getAsString());
                if (j.has("permissoes")) for (Map.Entry<String, JsonElement> pm : j.getAsJsonObject("permissoes").entrySet()) d.permissoes.put(UUID.fromString(pm.getKey()), pm.getValue().getAsInt() & TUDO);
                DONOS.put(UUID.fromString(e.getKey()), d);
            }
            if (raiz.has("proibidos")) for (JsonElement e : raiz.getAsJsonArray("proibidos")) {
                String[] xz = e.getAsString().split(",");
                PROIBIDOS.add(ChunkPos.asLong(Integer.parseInt(xz[0]), Integer.parseInt(xz[1])));
            }
            indexar();
            FrxWorld.LOG.info("Terrenos: {} comprados, {} pedaços proibidos.", LOTES.size(), PROIBIDOS.size());
        } catch (Exception e) {
            FrxWorld.LOG.warn("Terrenos: não li frx_terrenos.json: {}", e.toString());
        }
    }

    private static void gravar() {
        if (servidor == null) return;
        sujo = false;
        JsonObject raiz = new JsonObject(), donos = new JsonObject();
        com.google.gson.JsonArray lotes = new com.google.gson.JsonArray(), proibidos = new com.google.gson.JsonArray();
        for (Lote l : LOTES) {
            JsonObject j = new JsonObject();
            j.addProperty("id", l.id); j.addProperty("dono", l.dono.toString());
            j.addProperty("x0", l.x0); j.addProperty("z0", l.z0); j.addProperty("x1", l.x1); j.addProperty("z1", l.z1); j.addProperty("pago", l.pago);
            lotes.add(j);
        }
        DONOS.forEach((id, d) -> {
            JsonObject j = new JsonObject(), am = new JsonObject(), pm = new JsonObject();
            j.addProperty("nome", d.nome);
            j.addProperty("publico", d.publico);
            d.amigos.forEach((a, nome) -> { am.addProperty(a.toString(), nome); pm.addProperty(a.toString(), d.permissao(a)); });
            j.add("amigos", am);
            j.add("permissoes", pm);
            donos.add(id.toString(), j);
        });
        for (long k : PROIBIDOS) proibidos.add(ChunkPos.getX(k) + "," + ChunkPos.getZ(k));
        raiz.add("lotes", lotes); raiz.add("donos", donos); raiz.add("proibidos", proibidos);
        try { Files.writeString(arquivo(servidor), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(raiz), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Terrenos: não gravei frx_terrenos.json: {}", e.toString()); }
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) { if (sujo) gravar(); }

    // ------------------------------------------------------------------ regras

    private static boolean mundoNormal(LevelAccessor n) { return n instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD; }
    private static boolean admin(Entity e) { return e instanceof ServerPlayer p && p.isCreative() && p.hasPermissions(2); }
    private static Dono dono(UUID id) { return DONOS.computeIfAbsent(id, k -> new Dono()); }

    private static Lote loteEm(int x, int z) {
        List<Lote> l = POR_CHUNK.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (l != null) for (Lote t : l) if (t.contem(x, z)) return t;
        return null;
    }

    /** Há terreno de alguém (ou terra do reino) neste ponto? */
    public static boolean temLote(int x, int z) { return loteEm(x, z) != null; }
    private static UUID donoDe(BlockPos pos) { Lote l = loteEm(pos.getX(), pos.getZ()); return l == null ? null : l.dono; }

    /** Terras do reino: a capital inteira, a Torre, o ponto de partida, as salas da Torre e das masmorras (longe, em X/Z ≥ 20000) e o que o admin marcou. */
    private static boolean proibido(int cx, int cz) {
        if (PROIBIDOS.contains(ChunkPos.asLong(cx, cz))) return true;
        if (Math.abs(cx) >= 1250 || Math.abs(cz) >= 1250) return true;
        int mx = (cx << 4) + 8, mz = (cz << 4) + 8;
        for (Reino r : Reinos.todos()) if (r.distancia(mx, mz) <= 8) return true;
        if (br.com.frxcode.frxworld.aventura.Torre.pertoDaBase(mx, mz, 24)) return true;
        if (servidor != null) {   // ponto de partida do mundo
            BlockPos inicio = servidor.overworld().getSharedSpawnPos();
            if (Math.abs(mx - inicio.getX()) <= 40 && Math.abs(mz - inicio.getZ()) <= 40) return true;
        }
        return false;
    }

    /** O jogador tem alguma liberação nos terrenos deste dono (para a cor no mapa e os avisos). */
    private static boolean liberado(UUID donoLote, Entity quem) { return liberado(donoLote, quem, TUDO); }

    /** O jogador pode fazer pelo menos uma das coisas pedidas (COLOCAR, QUEBRAR, USAR) nos terrenos deste dono. */
    private static boolean liberado(UUID donoLote, Entity quem, int oQue) {
        if (!(quem instanceof ServerPlayer p)) return false;
        if (p.getUUID().equals(donoLote) || admin(p)) return true;
        Dono d = DONOS.get(donoLote);
        return d != null && (d.publico || (d.permissao(p.getUUID()) & oQue) != 0);
    }

    /** true = este jogador (ou criatura) pode fazer isto (COLOCAR, QUEBRAR ou USAR) neste bloco. */
    private static boolean pode(LevelAccessor n, BlockPos pos, Entity quem, int oQue) {
        if (!mundoNormal(n)) return true;
        UUID d = donoDe(pos);
        return d == null || liberado(d, quem, oQue);
    }

    /** A conta tem algum terreno? (marco do Guia do Aventureiro) */
    public static boolean temTerreno(ServerPlayer p) { return !lotesDe(p.getUUID()).isEmpty(); }
    private static List<Lote> lotesDe(UUID id) { List<Lote> l = new ArrayList<>(); for (Lote t : LOTES) if (t.dono.equals(id)) l.add(t); return l; }
    /** Área somada de vários retângulos {x0, z0, x1, z1} (cantos inclusive), contando uma vez só o que se sobrepõe. */
    private static int uniao(List<int[]> rets) {
        if (rets.isEmpty()) return 0;
        java.util.TreeSet<Integer> xs = new java.util.TreeSet<>(), zs = new java.util.TreeSet<>();
        for (int[] q : rets) { xs.add(q[0]); xs.add(q[2] + 1); zs.add(q[1]); zs.add(q[3] + 1); }
        Integer[] ax = xs.toArray(new Integer[0]), az = zs.toArray(new Integer[0]);
        long total = 0;
        for (int i = 0; i + 1 < ax.length; i++) for (int j = 0; j + 1 < az.length; j++) {
            for (int[] q : rets) if (q[0] <= ax[i] && q[2] + 1 >= ax[i + 1] && q[1] <= az[j] && q[3] + 1 >= az[j + 1]) {
                total += (long) (ax[i + 1] - ax[i]) * (az[j + 1] - az[j]);
                break;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static List<int[]> retangulosDe(UUID id) {
        List<int[]> l = new ArrayList<>();
        for (Lote t : LOTES) if (t.dono.equals(id)) l.add(new int[]{t.x0, t.z0, t.x1, t.z1});
        return l;
    }

    /** Área que o jogador tem de verdade: terrenos dele que se sobrepõem contam uma vez só. */
    private static int areaDe(UUID id) { return uniao(retangulosDe(id)); }

    /** Quantos blocos desta área ainda não são do jogador (o que ele paga e o que conta no limite). */
    private static int blocosNovos(UUID id, int x0, int z0, int x1, int z1) {
        List<int[]> l = retangulosDe(id);
        int antes = uniao(l);
        l.add(new int[]{x0, z0, x1, z1});
        return uniao(l) - antes;
    }

    /** Quantos blocos deixam de ser do jogador se ele vender este terreno (o que outro terreno dele cobre continua dele). */
    private static int blocosSoDele(Lote l) {
        List<int[]> resto = new ArrayList<>();
        for (Lote t : LOTES) if (t != l && t.dono.equals(l.dono)) resto.add(new int[]{t.x0, t.z0, t.x1, t.z1});
        int sem = uniao(resto);
        resto.add(new int[]{l.x0, l.z0, l.x1, l.z1});
        return uniao(resto) - sem;
    }

    /** O que a venda devolve: metade do que foi pago, e só pela parte que deixa de ser do jogador. */
    private static long devolucao(Lote l) { return Math.min(l.pago / 2, preco(blocosSoDele(l)) / 2); }
    private static long preco(int area) { return (long) Math.ceil(area * COBRE_POR_BLOCO); }
    private static int rank(ServerPlayer p) { return Math.max(0, Math.min(6, Guilda.indice(p))); }

    /** Por que este jogador não pode comprar esta área (null = pode). */
    private static String recusa(ServerPlayer p, int x0, int z0, int x1, int z1) {
        int r = rank(p), larg = x1 - x0 + 1, comp = z1 - z0 + 1, area = blocosNovos(p.getUUID(), x0, z0, x1, z1);   // só o que ainda não é dele
        if (larg < LADO_MIN || comp < LADO_MIN) return "Muito pequeno: cada lado precisa de pelo menos " + LADO_MIN + " blocos.";
        if (larg > LADO_MAX[r] || comp > LADO_MAX[r]) return "Grande demais para o Rank " + Guilda.rank(p) + ": cada lado pode ter até " + LADO_MAX[r] + " blocos.";
        if (lotesDe(p.getUUID()).size() >= QTD_MAX[r]) return "Você já tem o máximo de terrenos do Rank " + Guilda.rank(p) + " (" + QTD_MAX[r] + ").";
        if (areaDe(p.getUUID()) + area > AREA_MAX[r]) return "Passa do seu limite: o Rank " + Guilda.rank(p) + " pode ter " + AREA_MAX[r] + " blocos de terreno somados e você já tem " + areaDe(p.getUUID()) + ".";
        for (Lote l : LOTES) if (!l.dono.equals(p.getUUID()) && x0 <= l.x1 && x1 >= l.x0 && z0 <= l.z1 && z1 >= l.z0) return "A área pega parte do terreno de " + dono(l.dono).nome + ".";
        if (area <= 0) return "Essa área inteira já é sua.";
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) if (proibido(cx, cz)) return "A área pega terras do reino, que não estão à venda.";
        if (Moedas.contar(p) < preco(area)) return "Faltam moedas na bolsa: custa " + Moedas.texto(preco(area)) + " e você tem " + Moedas.texto(Moedas.contar(p)) + ".";
        return null;
    }

    private static void negar(Entity quem, BlockPos pos) {
        if (!(quem instanceof ServerPlayer p)) return;
        long agora = System.currentTimeMillis();
        if (agora - AVISADO.getOrDefault(p.getUUID(), 0L) < 1500) return;
        AVISADO.put(p.getUUID(), agora);
        UUID d = donoDe(pos);
        p.displayClientMessage(Component.literal("⌂ Terreno de " + (d == null ? "?" : dono(d).nome) + ": só o dono e quem ele liberar mexem aqui.").withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ proteção

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoQuebrar(BlockEvent.BreakEvent ev) {
        if (AreasProtegidas.tumulo(ev.getState()) || pode(ev.getLevel(), ev.getPos(), ev.getPlayer(), QUEBRAR)) return;
        ev.setCanceled(true);
        negar(ev.getPlayer(), ev.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoColocar(BlockEvent.EntityPlaceEvent ev) {
        if (pode(ev.getLevel(), ev.getPos(), ev.getEntity(), COLOCAR)) return;
        ev.setCanceled(true);
        negar(ev.getEntity(), ev.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoPisarNaHorta(BlockEvent.FarmlandTrampleEvent ev) {
        if (!pode(ev.getLevel(), ev.getPos(), ev.getEntity(), TUDO)) ev.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoClicarNoBloco(PlayerInteractEvent.RightClickBlock ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        if (pode(ev.getLevel(), ev.getPos(), p, USAR)) return;   // abre baú, porta, alavanca; colocar bloco ainda passa por aoColocar
        if (pode(ev.getLevel(), ev.getPos(), p, COLOCAR)) {     // só pode colocar: o bloco clicado não reage, mas o item da mão é usado
            ev.setUseBlock(Event.Result.DENY);
            return;
        }
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.FAIL);
        negar(p, ev.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoUsarBalde(FillBucketEvent ev) {
        if (ev.getTarget() instanceof net.minecraft.world.phys.BlockHitResult alvo && !pode(ev.getLevel(), alvo.getBlockPos().relative(alvo.getDirection()), ev.getEntity(), COLOCAR | QUEBRAR)) {
            ev.setCanceled(true);
            negar(ev.getEntity(), alvo.getBlockPos());
        }
    }

    /** Bichos, moldura, suporte de armadura e barco dentro do terreno: estranho não bate. Monstros e jogadores ficam de fora. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoBater(AttackEntityEvent ev) {
        Entity alvo = ev.getTarget();
        if (alvo instanceof Enemy || alvo instanceof Player || pode(alvo.level(), alvo.blockPosition(), ev.getEntity(), QUEBRAR)) return;
        ev.setCanceled(true);
        negar(ev.getEntity(), alvo.blockPosition());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoMexerEmEntidade(PlayerInteractEvent.EntityInteract ev) {
        Entity alvo = ev.getTarget();
        if (alvo instanceof Enemy || alvo instanceof Player || alvo.getTags().contains("frx_npc") || pode(alvo.level(), alvo.blockPosition(), ev.getEntity(), USAR)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.FAIL);
        negar(ev.getEntity(), alvo.blockPosition());
    }

    /** Explosão (creeper, TNT, ghast, wither) não leva nenhum bloco de terreno comprado. */
    @SubscribeEvent
    public void aoExplodir(ExplosionEvent.Detonate ev) {
        if (LOTES.isEmpty() || !mundoNormal(ev.getLevel())) return;
        ev.getAffectedBlocks().removeIf(b -> donoDe(b) != null);
    }

    /** Monstro não pega bloco, não quebra porta nem pisoteia dentro de terreno comprado. */
    @SubscribeEvent
    public void aoMonstroMexer(EntityMobGriefingEvent ev) {
        Entity e = ev.getEntity();
        if (e instanceof Enemy && !LOTES.isEmpty() && mundoNormal(e.level()) && donoDe(e.blockPosition()) != null) ev.setResult(Event.Result.DENY);
    }

    // ------------------------------------------------------------------ mapa do JourneyMap

    private static void pintarLote(ServerPlayer p, Lote l) {
        boolean meu = l.dono.equals(p.getUUID()), lib = !meu && liberado(l.dono, p) && !admin(p);
        String nome = meu ? "Seu terreno" : "Terreno de " + dono(l.dono).nome;
        MapaJm.mostrar(p, "lote_" + l.id, l.x0, l.z0, l.x1, l.z1, meu ? 0x35C435 : lib ? 0xF09A1A : 0xD43A2F, 0.22f, nome,
                nome + " · " + l.largura() + "×" + l.comprimento() + " blocos" + (meu ? "" : lib ? " · liberado para você" : ""));
    }

    private static void pintarTudo(ServerPlayer p) { for (Lote l : LOTES) pintarLote(p, l); }

    private static void repintarParaTodos() {
        if (servidor == null) return;
        for (ServerPlayer p : servidor.getPlayerList().getPlayers()) pintarTudo(p);
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Dono d = DONOS.get(p.getUUID());
        if (d != null && !d.nome.equals(p.getGameProfile().getName())) { d.nome = p.getGameProfile().getName(); sujo = true; }
        // o mapa do jogador só aceita os desenhos depois de se apresentar ao servidor: pinta 3 s depois de entrar
        if (!LOTES.isEmpty()) PINTAR_EM.put(p.getUUID(), p.server.getTickCount() + 60);
    }

    // ------------------------------------------------------------------ o que o jogador vê no mundo

    private static final ParticleOptions COR_OUTRO = new DustParticleOptions(new Vector3f(0.95f, 0.2f, 0.15f), 1.3f),
            COR_AMIGO = new DustParticleOptions(new Vector3f(1f, 0.6f, 0.1f), 1.3f), COR_SELECAO = new DustParticleOptions(new Vector3f(1f, 0.9f, 0.2f), 1.5f);

    private static void ponto(ServerPlayer p, ServerLevel n, int x, int z, ParticleOptions cor) {
        if (Math.abs(x - p.getX()) > 48 || Math.abs(z - p.getZ()) > 48) return;
        int y = n.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - p.getY()) > 12) y = (int) p.getY();
        n.sendParticles(p, cor, true, x, y + 0.4, z, 1, 0, 0.15, 0, 0);
    }

    /** Contorno do retângulo no chão (x1 e z1 inclusive), só para este jogador. */
    private static void divisa(ServerPlayer p, ServerLevel n, int x0, int z0, int x1, int z1, ParticleOptions cor) {
        for (int x = x0; x <= x1 + 1; x += 2) { ponto(p, n, x, z0, cor); ponto(p, n, x, z1 + 1, cor); }
        for (int z = z0; z <= z1 + 1; z += 2) { ponto(p, n, x0, z, cor); ponto(p, n, x1 + 1, z, cor); }
    }

    private static void desenharDivisas(ServerPlayer p) {
        ServerLevel n = p.serverLevel();
        for (Lote l : LOTES) {
            if (l.x1 < p.getX() - 48 || l.x0 > p.getX() + 48 || l.z1 < p.getZ() - 48 || l.z0 > p.getZ() + 48) continue;
            divisa(p, n, l.x0, l.z0, l.x1, l.z1, l.dono.equals(p.getUUID()) ? ParticleTypes.HAPPY_VILLAGER : liberado(l.dono, p) ? COR_AMIGO : COR_OUTRO);
        }
        int[] s = SELECAO.get(p.getUUID());
        if (s != null) divisa(p, n, s[0], s[1], s[2], s[3], COR_SELECAO);
        else { int[] c = CANTO.get(p.getUUID()); if (c != null) divisa(p, n, c[0], c[1], c[0], c[1], COR_SELECAO); }
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        if (s.getTickCount() % 200 == 77 && sujo) gravar();
        if (s.getTickCount() % 10 != 3) return;
        long agora = System.currentTimeMillis();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Integer pintar = PINTAR_EM.get(p.getUUID());
            if (pintar != null && s.getTickCount() >= pintar) { PINTAR_EM.remove(p.getUUID()); pintarTudo(p); }
            if (!mundoNormal(p.level())) { ONDE.remove(p.getUUID()); continue; }
            UUID d = donoDe(p.blockPosition());
            String aqui = d == null ? "" : d.toString(), antes = ONDE.getOrDefault(p.getUUID(), "");
            if (!aqui.equals(antes)) {
                ONDE.put(p.getUUID(), aqui);
                if (d != null) p.displayClientMessage(Component.literal(d.equals(p.getUUID()) ? "⌂ Seu terreno" : "⌂ Terreno de " + dono(d).nome + (liberado(d, p) ? " (liberado para você)" : ""))
                        .withStyle(d.equals(p.getUUID()) ? ChatFormatting.GREEN : liberado(d, p) ? ChatFormatting.GOLD : ChatFormatting.RED), true);
                else p.displayClientMessage(Component.literal("Terras livres").withStyle(ChatFormatting.GRAY), true);
            }
            if (s.getTickCount() % 20 == 13 && MOSTRAR_ATE.getOrDefault(p.getUUID(), 0L) > agora) desenharDivisas(p);
        }
    }

    // ------------------------------------------------------------------ escolha da área (mapa ou janela)

    /** Vem do mapa do JourneyMap (botão direito): npc = X, valor = Z; ação 0 = 1º canto, 1 = 2º canto, 2 = meus terrenos. */
    public static void acao(ServerPlayer p, AcaoMenu a) {
        int x = a.npc(), z = (int) Math.max(-30_000_000L, Math.min(30_000_000L, a.valor()));
        if (Math.abs(x) > 30_000_000) return;
        switch (a.acao()) {
            case 0 -> canto1(p, x, z);
            case 1 -> canto2(p, x, z);
            default -> abrirPainel(p);
        }
    }

    private static void canto1(ServerPlayer p, int x, int z) {
        if (!mundoNormal(p.level())) { Sistema.avisar(p, "Terrenos", "Só há terrenos à venda no mundo normal.", Sistema.VERMELHO); return; }
        CANTO.put(p.getUUID(), new int[]{x, z});
        SELECAO.remove(p.getUUID());
        MapaJm.remover(p, "selecao");
        MapaJm.mostrar(p, "canto", x, z, x, z, 0xFFE033, 0.8f, "1º canto", "1º canto do terreno");
        MOSTRAR_ATE.put(p.getUUID(), System.currentTimeMillis() + 120_000);
        som(p, true);
        p.displayClientMessage(Component.literal("⌂ 1º canto marcado em X " + x + ", Z " + z + ". Agora marque o 2º canto, no lado oposto do terreno.").withStyle(ChatFormatting.YELLOW), false);
    }

    private static void canto2(ServerPlayer p, int x, int z) {
        int[] c = CANTO.get(p.getUUID());
        if (c == null) { som(p, false); p.displayClientMessage(Component.literal("⌂ Marque primeiro o 1º canto do terreno.").withStyle(ChatFormatting.RED), false); return; }
        int[] s = {Math.min(c[0], x), Math.min(c[1], z), Math.max(c[0], x), Math.max(c[1], z)};
        SELECAO.put(p.getUUID(), s);
        MapaJm.remover(p, "canto");
        int area = blocosNovos(p.getUUID(), s[0], s[1], s[2], s[3]);
        MapaJm.mostrar(p, "selecao", s[0], s[1], s[2], s[3], 0xFFE033, 0.3f, "Sua escolha", (s[2] - s[0] + 1) + "×" + (s[3] - s[1] + 1) + " blocos · " + Moedas.texto(preco(area)));
        MOSTRAR_ATE.put(p.getUUID(), System.currentTimeMillis() + 120_000);
        p.server.execute(() -> abrir(p, 0));
    }

    private static void limparEscolha(ServerPlayer p) {
        CANTO.remove(p.getUUID()); SELECAO.remove(p.getUUID());
        MapaJm.remover(p, "canto"); MapaJm.remover(p, "selecao");
    }

    // ------------------------------------------------------------------ tela (cliente/TelaTerrenos)

    public static final int ABA = 0, CANTO1 = 1, CANTO2 = 2, COMPRAR = 3, CANCELAR = 4, VENDER = 5, DIVISAS = 6, PUBLICO = 7, PESSOA = 8;
    /** Aba aberta de cada jogador (0 comprar, 1 meus terrenos, 2 quem pode mexer) e a lista de pessoas que a tela dele mostra. */
    private static final Map<UUID, Integer> ABA_DE = new HashMap<>();
    private static final Map<UUID, List<UUID>> PESSOAS = new HashMap<>();

    private static void som(ServerPlayer p, boolean ok) {
        p.level().playSound(null, p.blockPosition(), ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7f, ok ? 1.2f : 1f);
    }

    /** Abre a tela: na compra, se há uma área escolhida; senão, em Meus terrenos. */
    public static void abrirPainel(ServerPlayer p) { abrir(p, SELECAO.containsKey(p.getUUID()) || CANTO.containsKey(p.getUUID()) ? 0 : 1); }

    private static void abrir(ServerPlayer p, int aba) {
        ABA_DE.put(p.getUUID(), aba);
        enviar(p, "", true);
    }

    private static CompoundTag retangulo(int x0, int z0, int x1, int z1) {
        CompoundTag t = new CompoundTag();
        t.putInt("x0", x0); t.putInt("z0", z0); t.putInt("x1", x1); t.putInt("z1", z1);
        return t;
    }

    private static void enviar(ServerPlayer p, String msg, boolean ok) {
        UUID eu = p.getUUID();
        int r = rank(p);
        CompoundTag d = new CompoundTag();
        d.putInt("aba", ABA_DE.getOrDefault(eu, 1));
        d.putString("msg", msg); d.putBoolean("ok", ok);
        d.putLong("bolsa", Moedas.contar(p));
        d.putString("rank", Guilda.rank(p));
        d.putInt("ladoMax", LADO_MAX[r]); d.putInt("areaMax", AREA_MAX[r]); d.putInt("qtdMax", QTD_MAX[r]);
        d.putInt("areaTenho", areaDe(eu)); d.putInt("qtdTenho", lotesDe(eu).size());
        d.putLong("preco100", preco(100));
        d.putBoolean("publico", dono(eu).publico);
        int[] c = CANTO.get(eu), s = SELECAO.get(eu);
        if (c != null && s == null) { d.putInt("cx", c[0]); d.putInt("cz", c[1]); }
        if (s != null) {
            CompoundTag sel = retangulo(s[0], s[1], s[2], s[3]);
            int larg = s[2] - s[0] + 1, comp = s[3] - s[1] + 1;
            int novos = blocosNovos(eu, s[0], s[1], s[2], s[3]);
            sel.putInt("novos", novos);
            sel.putLong("preco", preco(novos));
            String motivo = recusa(p, s[0], s[1], s[2], s[3]);
            sel.putString("motivo", motivo == null ? "" : motivo);
            d.put("sel", sel);
            // terrenos em volta, para o desenho da área
            int m = Math.max(4, Math.max(larg, comp) / 3);
            ListTag viz = new ListTag();
            for (Lote l : LOTES) {
                if (l.x1 < s[0] - m || l.x0 > s[2] + m || l.z1 < s[1] - m || l.z0 > s[3] + m || viz.size() >= 40) continue;
                CompoundTag v = retangulo(l.x0, l.z0, l.x1, l.z1);
                v.putBoolean("meu", l.dono.equals(eu));
                viz.add(v);
            }
            d.put("vizinhos", viz);
        }
        ListTag lotes = new ListTag();
        for (Lote l : lotesDe(eu)) {
            CompoundTag t = retangulo(l.x0, l.z0, l.x1, l.z1);
            t.putInt("id", l.id); t.putLong("pago", l.pago); t.putLong("volta", devolucao(l));
            lotes.add(t);
        }
        d.put("lotes", lotes);
        // quem pode mexer: primeiro quem está no jogo, depois os liberados que estão fora
        Dono dn = dono(eu);
        List<UUID> ids = new ArrayList<>();
        ListTag pessoas = new ListTag();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p || ids.size() >= 60) continue;
            CompoundTag t = new CompoundTag();
            t.putString("nome", o.getGameProfile().getName()); t.putInt("perm", dn.permissao(o.getUUID())); t.putBoolean("online", true);
            pessoas.add(t); ids.add(o.getUUID());
        }
        for (Map.Entry<UUID, String> a : dn.amigos.entrySet()) {
            if (ids.contains(a.getKey()) || ids.size() >= 60) continue;
            CompoundTag t = new CompoundTag();
            t.putString("nome", a.getValue()); t.putInt("perm", dn.permissao(a.getKey())); t.putBoolean("online", false);
            pessoas.add(t); ids.add(a.getKey());
        }
        d.put("pessoas", pessoas);
        PESSOAS.put(eu, ids);
        br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new br.com.frxcode.frxworld.rede.AbrirMenu("terrenos", d));
    }

    /** Cliques da tela de terrenos. */
    public static void acaoTela(ServerPlayer p, AcaoMenu a) {
        UUID eu = p.getUUID();
        switch (a.acao()) {
            case ABA -> { ABA_DE.put(eu, (int) Math.max(0, Math.min(2, a.valor()))); enviar(p, "", true); }
            case CANTO1 -> {
                if (!mundoNormal(p.level())) { enviar(p, "Só há terrenos à venda no mundo normal.", false); return; }
                canto1(p, p.blockPosition().getX(), p.blockPosition().getZ());
                ABA_DE.put(eu, 0);
                enviar(p, "1º canto marcado onde você está. Vá até o canto oposto e marque o 2º.", true);
            }
            case CANTO2 -> {
                if (!CANTO.containsKey(eu)) { som(p, false); enviar(p, "Marque primeiro o 1º canto do terreno.", false); return; }
                canto2(p, p.blockPosition().getX(), p.blockPosition().getZ());
            }
            case COMPRAR -> {
                String erro = comprar(p);
                if (erro != null) { enviar(p, erro, false); return; }
                ABA_DE.put(eu, 1);
                enviar(p, "Terreno comprado. Ele já aparece no mapa de todos com o seu nome.", true);
            }
            case CANCELAR -> { limparEscolha(p); enviar(p, "Escolha apagada.", true); }
            case VENDER -> {
                for (Lote l : lotesDe(eu)) if (l.id == a.valor()) {
                    long volta = devolucao(l);
                    vender(p, l);
                    enviar(p, "Terreno vendido por " + Moedas.texto(volta) + ". O que está construído fica, mas sem proteção.", true);
                    return;
                }
                enviar(p, "Esse terreno não é mais seu.", false);
            }
            case DIVISAS -> {
                MOSTRAR_ATE.put(eu, System.currentTimeMillis() + 60_000);
                p.displayClientMessage(Component.literal("⌂ Divisas no chão por 1 minuto. Verde: seu · Laranja: liberado · Vermelho: de outro.").withStyle(ChatFormatting.AQUA), false);
            }
            case PUBLICO -> {
                Dono d = dono(eu);
                d.publico = !d.publico; d.nome = p.getGameProfile().getName(); sujo = true;
                som(p, true);
                repintarParaTodos();
                enviar(p, d.publico ? "Seus terrenos estão abertos: qualquer jogador constrói e quebra." : "Seus terrenos estão fechados: só você e quem você liberou.", true);
            }
            case PESSOA -> {   // valor = posição na lista × 8 + a permissão que troca (COLOCAR, QUEBRAR ou USAR)
                List<UUID> ids = PESSOAS.get(eu);
                int idx = (int) (a.valor() / 8), bit = (int) (a.valor() % 8);
                if (ids == null || a.valor() < 0 || idx >= ids.size() || (bit != COLOCAR && bit != QUEBRAR && bit != USAR)) return;
                UUID alvo = ids.get(idx);
                Dono d = dono(eu);
                int antes = d.permissao(alvo), depois = antes ^ bit;
                ServerPlayer o = p.server.getPlayerList().getPlayer(alvo);
                String nome = d.amigos.containsKey(alvo) ? d.amigos.get(alvo) : o != null ? o.getGameProfile().getName() : null;
                if (nome == null) { enviar(p, "Esse jogador saiu do jogo.", false); return; }
                if (depois == 0) { d.amigos.remove(alvo); d.permissoes.remove(alvo); }
                else { d.amigos.put(alvo, nome); d.permissoes.put(alvo, depois); }
                if (antes == 0 && o != null) Sistema.avisar(o, "Terreno liberado", p.getGameProfile().getName() + " liberou você nos terrenos dele.", Sistema.VERDE);
                String oQue = bit == COLOCAR ? "colocar blocos" : bit == QUEBRAR ? "quebrar blocos" : "abrir baús e portas";
                d.nome = p.getGameProfile().getName(); sujo = true;
                som(p, true);
                repintarParaTodos();
                enviar(p, nome + ((depois & bit) != 0 ? " agora pode " : " não pode mais ") + oQue + " nos seus terrenos.", true);
            }
            default -> { }
        }
    }

    /** Compra a área escolhida. Devolve o motivo da recusa, ou null se comprou. */
    private static String comprar(ServerPlayer p) {
        int[] s = SELECAO.get(p.getUUID());
        if (s == null) return "Escolha a área primeiro: marque os dois cantos.";
        String motivo = recusa(p, s[0], s[1], s[2], s[3]);
        if (motivo != null) { som(p, false); return motivo; }
        Lote l = new Lote();
        l.id = proximoId++; l.dono = p.getUUID(); l.x0 = s[0]; l.z0 = s[1]; l.x1 = s[2]; l.z1 = s[3];
        l.pago = preco(blocosNovos(p.getUUID(), l.x0, l.z0, l.x1, l.z1));   // a parte que já era dele não é cobrada de novo
        if (!Moedas.cobrar(p, l.pago)) return "Faltam moedas na bolsa.";
        LOTES.add(l);
        indexar();
        dono(p.getUUID()).nome = p.getGameProfile().getName();
        gravar();
        limparEscolha(p);
        repintarParaTodos();
        MOSTRAR_ATE.put(p.getUUID(), System.currentTimeMillis() + 30_000);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.2f);
        Sistema.avisar(p, "Terreno comprado", l.largura() + "×" + l.comprimento() + " blocos em " + l.onde() + ". Só você constrói aqui. Ele já aparece no mapa de todos com o seu nome.", Sistema.VERDE);
        return null;
    }

    private static void vender(ServerPlayer p, Lote l) {
        long volta = devolucao(l);
        if (!LOTES.remove(l)) return;
        indexar();
        Moedas.dar(p, volta);
        gravar();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) MapaJm.remover(o, "lote_" + l.id);
        som(p, true);
        Sistema.avisar(p, "Terreno vendido", "Você recebeu " + Moedas.texto(volta) + ". O que está construído fica, mas sem proteção.", Sistema.OURO);
    }

    // ------------------------------------------------------------------ comandos

    private static int marcar(CommandSourceStack fonte, int raio, boolean proibir) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = fonte.getPlayerOrException();
        int pcx = p.blockPosition().getX() >> 4, pcz = p.blockPosition().getZ() >> 4, n = 0, comDono = 0;
        for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) {
            long chave = ChunkPos.asLong(pcx + dx, pcz + dz);
            if (proibir && POR_CHUNK.containsKey(chave)) { comDono++; continue; }
            if (proibir ? PROIBIDOS.add(chave) : PROIBIDOS.remove(chave)) n++;
        }
        gravar();
        int x0 = (pcx - raio) << 4, z0 = (pcz - raio) << 4, x1 = ((pcx + raio) << 4) + 15, z1 = ((pcz + raio) << 4) + 15;
        if (proibir) MapaJm.mostrar(p, "admin_area", x0, z0, x1, z1, 0x808088, 0.3f, "Terras do reino", "Área que você acabou de proibir");
        else MapaJm.remover(p, "admin_area");
        int feitos = n, pulados = comDono, lado = (raio * 2 + 1) * 16;
        fonte.sendSuccess(() -> Component.literal("[terrenos] " + feitos + " pedaços " + (proibir ? "marcados como terras do reino" : "liberados para venda") + ": X " + x0 + " a " + x1 + ", Z " + z0 + " a " + z1
                + " (" + lado + "×" + lado + " blocos)" + (pulados > 0 ? ". " + pulados + " já têm terreno comprado e ficaram como estão" : "") + "."), true);
        return 1;
    }

    @SubscribeEvent
    public void aoRegistrarComandos(RegisterCommandsEvent ev) {
        // jogadores: reserva para quem não usa o mapa
        ev.getDispatcher().register(Commands.literal("terreno").executes(c -> { abrirPainel(c.getSource().getPlayerOrException()); return 1; }));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("terreno")
                        .then(Commands.literal("proibir").executes(c -> marcar(c.getSource(), 0, true))
                                .then(Commands.argument("raio", IntegerArgumentType.integer(0, 12)).executes(c -> marcar(c.getSource(), IntegerArgumentType.getInteger(c, "raio"), true))))
                        .then(Commands.literal("liberar").executes(c -> marcar(c.getSource(), 0, false))
                                .then(Commands.argument("raio", IntegerArgumentType.integer(0, 12)).executes(c -> marcar(c.getSource(), IntegerArgumentType.getInteger(c, "raio"), false))))
                        .then(Commands.literal("tomar").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Lote l = loteEm(p.blockPosition().getX(), p.blockPosition().getZ());
                            if (l == null) { c.getSource().sendFailure(Component.literal("Você não está em nenhum terreno comprado.")); return 0; }
                            LOTES.remove(l);
                            indexar();
                            gravar();
                            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) MapaJm.remover(o, "lote_" + l.id);
                            c.getSource().sendSuccess(() -> Component.literal("[terrenos] O terreno de " + dono(l.dono).nome + " (" + l.onde() + ") voltou a ser terra livre, sem reembolso."), true);
                            return 1;
                        }))
                        .then(Commands.literal("info").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int cx = p.blockPosition().getX() >> 4, cz = p.blockPosition().getZ() >> 4;
                            Lote l = loteEm(p.blockPosition().getX(), p.blockPosition().getZ());
                            String t = l != null ? "terreno de " + dono(l.dono).nome + " (" + l.onde() + ")" : proibido(cx, cz) ? "terra do reino" + (PROIBIDOS.contains(ChunkPos.asLong(cx, cz)) ? " (marcada por admin)" : " (capital, Torre ou ponto de partida)") : "livre";
                            c.getSource().sendSuccess(() -> Component.literal("[terrenos] Aqui: " + t + ". No mundo: " + LOTES.size() + " terrenos comprados, " + PROIBIDOS.size() + " pedaços proibidos."), false);
                            return 1;
                        }))));
    }
}
