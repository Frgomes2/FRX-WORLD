package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Moradores das capitais: rei, ferreiro, armeiro, mercador, sacerdote, sábio e guardas.
 * Nascem na primeira visita a cada capital (registro em <mundo>/frx_npcs.txt).
 * Posições: em volta do centro (ou da entrada, em Dwargon); um admin ajusta com /frx npc aqui <papel>.
 */
public class NpcsReino {
    public static final String TAG = "frx_npc";
    private static final String GUARDA_MOD = "guardvillagers:guard";

    /** papel → {nome padrão, profissão vanilla ou "" , dx, dz} */
    private record Papel(String id, String profissao, int dx, int dz, String[] falas) {}

    private static final List<Papel> PAPEIS = List.of(
            new Papel("rei", "", 0, 0, new String[]{"Seja bem-vindo ao meu reino, viajante.", "Meus súditos contam com heróis como você.", "Prove seu valor e terá a gratidão da coroa."}),
            new Papel("ferreiro", "weaponsmith", 7, 0, new String[]{"Lâminas afiadas, preço justo! Fale comigo e eu aprimoro a sua arma.", "Traga minério e moedas e eu levo sua arma até +10.", "Cuidado com a fornalha, está quente."}),
            new Papel("armeiro", "armorer", 7, 5, new String[]{"Uma boa armadura salva vidas.", "Fale comigo: eu reforjo a qualidade da sua arma ou armadura."}),
            new Papel("mercador", "toolsmith", -7, 0, new String[]{"Tudo o que um aventureiro precisa!", "Hoje tenho ofertas especiais."}),
            new Papel("sacerdote", "cleric", 0, 7, new String[]{"Que a luz guie seus passos.", "Poções e bênçãos para quem precisa."}),
            new Papel("sabio", "librarian", 0, -7, new String[]{"Conhecimento é a arma mais poderosa.", "Tenho livros que nem o rei leu."}),
            new Papel("cartografo", "cartographer", -7, 5, new String[]{"Mapas de terras distantes, quer ver?"}),
            new Papel("comprador", "toolsmith", 7, -5, new String[]{"Compro tudo que você trouxer das aventuras!", "Ossos, pólvora, diamantes... pago em moedas."}),
            new Papel("banqueiro", "nitwit", 0, 10, new String[]{"Moedas no banco não se perdem na morte.", "Guardo seu ouro com a minha vida."}),
            new Papel("cambista", "nitwit", -10, 0, new String[]{"Troco ouro, prata e cobre sem cobrar taxa!", "1 ouro vale 10 pratas; 1 prata vale 10 cobres."}),
            new Papel("recepcionista", "librarian", 10, -5, new String[]{"Bem-vindo à Guilda de Aventureiros!", "Suba de rank e o reino inteiro vai saber o seu nome."}),
            new Papel("pedreiro", "mason", 10, 5, new String[]{"Madeira, pedra, vidro: tudo para a sua obra!", "Quem constrói bem começa pelo material certo.", "Blocos em pilha, preço de atacado."}),
            new Papel("cozinheiro", "butcher", 0, -10, new String[]{"Comida quente para a viagem!", "Barriga cheia, espada firme.", "O cardápio muda todo dia, volte sempre."}),
            new Papel("taverneiro", "nitwit", -7, -5, new String[]{"Procurando trabalho? Veja o quadro de missões!", "Hoje a cerveja está por conta da casa... brincadeira."}),
            new Papel("clas", "librarian", -10, -5, new String[]{"Sozinho se vai rápido; em clã se vai longe.", "Todo clã forte começou com dois amigos e um cofre vazio.", "Quer fundar um clã ou entrar em um? É comigo."}),
            new Papel("leiloeiro", "nitwit", 10, -10, new String[]{"O que sobra para um falta para outro!", "Anuncie hoje, receba mesmo dormindo.", "Tudo o que os aventureiros vendem passa pelo meu balcão."}),
            new Papel("discos", "nitwit", -10, 10, new String[]{"Música boa é a que fica na cabeça depois da batalha.", "Tenho discos que você não acha em masmorra nenhuma.", "Uma jukebox e um bom disco: isso é lar."}),
            new Papel("escravos", "nitwit", 10, 10, new String[]{"Mão de obra boa e barata. Só para quem a Guilda respeita.", "Sem profissão, sem vício: você ensina o que quiser.", "Rank D para cima. Abaixo disso, nem conversa."})
    );
    private static final int GUARDAS = 6;

    private static final Map<String, Map<String, String>> NOMES = new LinkedHashMap<>();
    static {
        NOMES.put("tempest", Map.of("rei", "Lorde Rimuru Tempest", "ferreiro", "Kaijin, Mestre Ferreiro", "armeiro", "Kurobe, Forjador", "mercador", "Myourmile, Mercador",
                "sacerdote", "Shuna, Sacerdotisa", "sabio", "Rigurd, Conselheiro", "cartografo", "Gobta, Explorador", "taverneiro", "Taverneiro de Tempest", "comprador", "Gobzo, Comprador de Saques", "guarda", "Guarda de Tempest"));
        NOMES.put("dwargon", Map.of("rei", "Rei Gazel Dwargo", "ferreiro", "Mestre Ferreiro Anão", "armeiro", "Armeiro Real", "mercador", "Mercador da Montanha",
                "sacerdote", "Guardião das Runas", "sabio", "Ancião de Dwargon", "cartografo", "Mapeador dos Túneis", "taverneiro", "Taverneiro Barba-de-Ferro", "comprador", "Cambista Anão", "guarda", "Cavaleiro Pégaso"));
        NOMES.put("sarion", Map.of("rei", "Imperatriz Elmesia El-Ru Sarion", "ferreiro", "Ferreiro Élfico", "armeiro", "Tecelã de Mithril", "mercador", "Mercador Arcano",
                "sacerdote", "Sacerdotisa da Lua", "sabio", "Arquimago de Sarion", "cartografo", "Navegante das Ilhas", "taverneiro", "Taverneira Élfica", "comprador", "Avaliador Élfico", "guarda", "Guarda da Dinastia"));
        NOMES.put("leste", Map.of("rei", "Imperador Ludora", "ferreiro", "Mestre Forjador Imperial", "armeiro", "Armeiro Imperial", "mercador", "Mercador da Seda",
                "sacerdote", "Monge do Templo", "sabio", "Estrategista Imperial", "cartografo", "Cartógrafo do Império", "taverneiro", "Dono da Casa de Chá", "comprador", "Comprador Imperial", "guarda", "Soldado Imperial"));
        NOMES.put("leste-sakura", Map.of("rei", "Senhora das Cerejeiras", "ferreiro", "Ferreiro de Katanas", "armeiro", "Armeiro Samurai", "mercador", "Mercadora de Chá",
                "sacerdote", "Sacerdotisa do Santuário", "sabio", "Mestre do Jardim", "cartografo", "Viajante do Leste", "taverneiro", "Dona da Casa de Saquê", "comprador", "Mercador de Relíquias", "guarda", "Samurai do Distrito"));
        NOMES.put("oeste", nomes("rei", "Rei Edmaris de Farmus", "ferreiro", "Garm, Ferreiro de Blumund", "armeiro", "Dold, Artesão de Armaduras",
                "mercador", "Mercador da Rota Ocidental", "sacerdote", "Padre da Igreja Ocidental", "sabio", "Fuze, Mestre da Guilda",
                "cartografo", "Kabal, Aventureiro Veterano", "taverneiro", "Taverna do Javali Dourado", "comprador", "Elen, Compradora de Relíquias",
                "banqueiro", "Banco de Blumund", "guarda", "Cavaleiro de Farmus"));
        NOMES.put("lubelius", nomes("rei", "Luminus Valentine, Deusa de Lubelius", "ferreiro", "Ferreiro dos Cavaleiros Sagrados", "armeiro", "Armeiro do Templo",
                "mercador", "Mercador da Catedral", "sacerdote", "Sumo Sacerdote Louis", "sabio", "Hinata Sakaguchi, Capitã Sagrada",
                "cartografo", "Peregrino Sagrado", "taverneiro", "Hospedaria do Peregrino", "comprador", "Tesoureiro da Igreja",
                "banqueiro", "Cofre Sagrado", "guarda", "Cavaleiro Sagrado"));
        NOMES.put("demonios", nomes("rei", "Guy Crimson, Lorde Demônio Primordial", "ferreiro", "Forjador do Abismo", "armeiro", "Armeiro Infernal",
                "mercador", "Mercador de Almas", "sacerdote", "Sacerdote das Sombras", "sabio", "Misery, Serva Escarlate",
                "cartografo", "Batedor das Cinzas", "taverneiro", "Taverna do Caldeirão", "comprador", "Agiota Demoníaco",
                "banqueiro", "Cofre dos Lordes", "guarda", "Arquidemônio de Guarda"));
    }

    private static Map<String, String> nomes(String... pares) {
        Map<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) m.put(pares[i], pares[i + 1]);
        return m;
    }

    private static String nome(Reino r, String papel) {
        Map<String, String> m = NOMES.get(r.id());
        if (m != null && m.containsKey(papel)) return m.get(papel);
        return switch (papel) {
            case "rei" -> "Soberano de " + r.nome();
            case "guarda" -> "Guarda de " + r.nome();
            case "pedreiro" -> "Mestre de Obras";
            case "clas" -> "Mestre dos Clãs";
            case "leiloeiro" -> "Leiloeiro";
            case "discos" -> "Mercador de Discos";
            case "escravos" -> "Mercador de Escravos";
            default -> Character.toUpperCase(papel.charAt(0)) + papel.substring(1);
        };
    }

    // ------------------------------------------------------------------ registro por mundo

    private static Path marca(ServerLevel nivel) {
        return nivel.getServer().getWorldPath(LevelResource.ROOT).resolve("frx_npcs.txt");
    }

    private static Set<String> povoados(ServerLevel nivel) {
        try {
            Path p = marca(nivel);
            return Files.exists(p) ? new HashSet<>(Files.readAllLines(p)) : new HashSet<>();
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private static void marcar(ServerLevel nivel, Set<String> ids) {
        try { Files.write(marca(nivel), ids); } catch (Exception e) { FrxWorld.LOG.warn("Não salvei frx_npcs.txt: {}", e.toString()); }
    }

    private static Set<String> cache;
    private static Object servidorCache;

    /** Chamado a cada 2s enquanto o jogador está numa capital; povoa quando ele chega perto do centro. */
    public static void garantir(ServerPlayer jogador, Reino r) {
        ServerLevel nivel = jogador.serverLevel();
        if (cache == null || servidorCache != nivel.getServer()) { cache = povoados(nivel); servidorCache = nivel.getServer(); }
        if (cache.contains(r.id())) return;
        // povoa assim que o jogador entra em qualquer parte da capital (antes: só a 96 blocos do centro,
        // e na Dwargon grande ninguém achava o rei)
        int n = povoar(nivel, r, null);
        cache.add(r.id());
        marcar(nivel, cache);
        FrxWorld.LOG.info("Capital {} povoada com {} moradores.", r.id(), n);
    }

    private static void registrar(ServerLevel nivel, Reino r) {
        if (cache == null || servidorCache != nivel.getServer()) { cache = povoados(nivel); servidorCache = nivel.getServer(); }
        cache.add(r.id());
        marcar(nivel, cache);
    }

    // ------------------------------------------------------------------ posições

    private static JsonObject posicoesJson() {
        try {
            Path p = FMLPaths.GAMEDIR.get().resolve("frxworld").resolve("reinos").resolve("posicoes.json");
            return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static BlockPos centro(ServerLevel nivel, Reino r) {
        JsonObject pos = posicoesJson();
        if (pos.has(r.id()) && pos.getAsJsonObject(r.id()).has("praca")) {
            JsonArray a = pos.getAsJsonObject(r.id()).getAsJsonArray("praca");
            return new BlockPos(a.get(0).getAsInt(), r.subterraneo() ? r.yBase() + 30 : r.chao(), a.get(1).getAsInt());
        }
        if (r.subterraneo()) {
            int[] e = Entradas.de(r);
            if (e != null) return new BlockPos((r.chunkX0() << 4) + e[0], e[1], (r.chunkZ0() << 4) + e[2]);
        }
        return new BlockPos(r.centroX(), r.chao(), r.centroZ());
    }

    /** Procura chão firme com 2 de ar acima, perto da altura de referência. */
    private static BlockPos chao(ServerLevel nivel, int x, int z, int yRef, boolean subterraneo) {
        nivel.getChunk(x >> 4, z >> 4);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= 24; d++) {
            for (int s : new int[]{1, -1}) {
                int y = yRef + d * s;
                m.set(x, y, z);
                if (nivel.getBlockState(m).isAir() && nivel.getBlockState(m.above()).isAir()
                        && nivel.getBlockState(m.below()).isSolidRender(nivel, m.below())) return m.immutable();
                if (d == 0) break;
            }
        }
        if (subterraneo) return new BlockPos(x, yRef, z);
        return new BlockPos(x, nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static BlockPos posicao(ServerLevel nivel, Reino r, String papel, int dx, int dz, JsonObject sobrescritas) {
        if (sobrescritas != null && sobrescritas.has(papel)) {
            JsonArray a = sobrescritas.getAsJsonArray(papel);
            return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        }
        BlockPos c = centro(nivel, r);
        return chao(nivel, c.getX() + dx, c.getZ() + dz, c.getY(), r.subterraneo());
    }

    // ------------------------------------------------------------------ casinhas dos ofícios

    /** Centro do anel de casinhas: a praça; sem praça mas com posições fixas, o meio delas; senão o centro do reino. */
    private static BlockPos centroDasCasas(ServerLevel nivel, Reino r, JsonObject sobre) {
        JsonObject pos = posicoesJson();
        boolean temPraca = pos.has(r.id()) && pos.getAsJsonObject(r.id()).has("praca");
        if (!temPraca && sobre != null && sobre.size() > 1) {
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE, y = 0, qtd = 0;
            for (String k : sobre.keySet()) {
                if (k.equals("rei")) continue;
                JsonArray a = sobre.getAsJsonArray(k);
                x0 = Math.min(x0, a.get(0).getAsInt()); x1 = Math.max(x1, a.get(0).getAsInt());
                z0 = Math.min(z0, a.get(2).getAsInt()); z1 = Math.max(z1, a.get(2).getAsInt());
                y += a.get(1).getAsInt(); qtd++;
            }
            if (qtd > 0) return new BlockPos((x0 + x1) / 2, y / qtd, (z0 + z1) / 2);
        }
        return centro(nivel, r);
    }

    private static boolean livre(BlockPos b, List<BlockPos> usados, BlockPos centro) {
        if (Math.abs(b.getX() - centro.getX()) < 6 && Math.abs(b.getZ() - centro.getZ()) < 6) return false;   // fonte/trono do meio
        for (BlockPos u : usados) if (Math.abs(b.getX() - u.getX()) < 6 && Math.abs(b.getZ() - u.getZ()) < 6) return false;
        return true;
    }

    /**
     * Onde fica a casinha (e o NPC) de cada ofício. Usa as posições fixas do posicoes.json quando elas têm espaço
     * para uma casinha 5x5 cada; senão monta um anel em volta da praça, fugindo das ruas e das casinhas vizinhas.
     */
    private static Map<String, BlockPos> lugaresDasCasas(ServerLevel nivel, Reino r, JsonObject sobre, BlockPos c) {
        Map<String, BlockPos> fixos = new LinkedHashMap<>();
        if (sobre != null) for (String papel : Oficios.ORDEM) {
            if (!sobre.has(papel)) continue;
            JsonArray a = sobre.getAsJsonArray(papel);
            fixos.put(papel, new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
        }
        boolean cabem = true;
        List<BlockPos> lista = new ArrayList<>(fixos.values());
        for (int i = 0; i < lista.size() && cabem; i++)
            for (int j = i + 1; j < lista.size(); j++)
                if (Math.abs(lista.get(i).getX() - lista.get(j).getX()) < 6 && Math.abs(lista.get(i).getZ() - lista.get(j).getZ()) < 6) { cabem = false; break; }
        Map<String, BlockPos> casas = new LinkedHashMap<>();
        List<BlockPos> usados = new ArrayList<>();
        if (cabem) { casas.putAll(fixos); usados.addAll(fixos.values()); }
        for (String papel : Oficios.ORDEM) {
            if (casas.containsKey(papel)) continue;
            int[] xz = Oficios.noAnel(c, papel);
            BlockPos b = chao(nivel, xz[0], xz[1], c.getY(), r.subterraneo());
            // vaga ocupada: gira pelo anel (de 15 em 15 graus) até achar espaço
            for (int passo = 1; passo < 24 && !livre(b, usados, c); passo++) {
                double ang = Math.atan2(xz[1] - c.getZ(), xz[0] - c.getX()) + Math.toRadians(15 * passo);
                b = chao(nivel, c.getX() + (int) Math.round(Math.cos(ang) * Oficios.RAIO), c.getZ() + (int) Math.round(Math.sin(ang) * Oficios.RAIO), c.getY(), r.subterraneo());
            }
            casas.put(papel, b);
            usados.add(b);
        }
        return casas;
    }

    // ------------------------------------------------------------------ criação

    /** Cria os moradores (todos, ou só um papel). Retorna quantos nasceram. */
    static int povoar(ServerLevel nivel, Reino r, String soPapel) {
        JsonObject pos = posicoesJson();
        JsonObject sobre = pos.has(r.id()) && pos.getAsJsonObject(r.id()).has("npcs") ? pos.getAsJsonObject(r.id()).getAsJsonObject("npcs") : null;
        int n = 0;
        BlockPos centroCasas = centroDasCasas(nivel, r, sobre);
        Map<String, BlockPos> casas = lugaresDasCasas(nivel, r, sobre, centroCasas);
        for (Papel p : PAPEIS) {
            if (soPapel != null && !soPapel.equals(p.id())) continue;
            BlockPos casa = casas.get(p.id());
            BlockPos b;
            float yaw;
            if (casa != null) {
                // cada ofício na sua casinha, de frente para a praça (Oficios)
                Direction frente = Oficios.paraCentro(casa, centroCasas);
                Oficios.construir(nivel, r, p.id(), casa, frente);
                b = casa;
                yaw = frente.toYRot();
            } else {
                b = posicao(nivel, r, p.id(), p.dx(), p.dz(), sobre);
                yaw = p.id().equals("rei") ? 0f : ThreadLocalRandom.current().nextFloat() * 360f;
            }
            nivel.getChunk(b.getX() >> 4, b.getZ() >> 4);   // carrega o chunk do NPC (pode estar longe do jogador)
            Entity e = p.id().equals("rei") ? criarRei(nivel, r) : criarMorador(nivel, r, p, b);
            if (e == null) continue;
            e.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, yaw, 0);
            e.setYHeadRot(e.getYRot());
            marcarNpc(e, r, p.id());
            nivel.addFreshEntity(e);
            n++;
        }
        if (soPapel == null || soPapel.equals("guarda")) {
            for (int i = 0; i < GUARDAS; i++) {
                double ang = Math.PI * 2 * i / GUARDAS;
                String chave = "guarda" + (i + 1);
                BlockPos b = posicao(nivel, r, chave, (int) Math.round(Math.cos(ang) * 18), (int) Math.round(Math.sin(ang) * 18), sobre);
                nivel.getChunk(b.getX() >> 4, b.getZ() >> 4);
                Entity e = criarGuarda(nivel, r, b);
                if (e == null) continue;
                e.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, (float) Math.toDegrees(ang), 0);
                marcarNpc(e, r, "guarda");
                nivel.addFreshEntity(e);
                n++;
            }
        }
        return n;
    }

    static void marcarNpc(Entity e, Reino r, String papel) {
        e.addTag(TAG);
        e.addTag(TAG + "_" + r.id());
        e.getPersistentData().putString("frx_papel", papel);
        e.getPersistentData().putString("frx_reino", r.id());
        if (e instanceof Mob m) m.setPersistenceRequired();
    }

    private static Entity criarRei(ServerLevel nivel, Reino r) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(GUARDA_MOD));
        Entity e = (tipo != null ? tipo : EntityType.VILLAGER).create(nivel);
        if (!(e instanceof Mob m)) return null;
        if (e instanceof Villager v) v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        m.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
        m.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        for (EquipmentSlot s : EquipmentSlot.values()) m.setDropChance(s, 0f);
        m.setNoAi(true);
        m.setInvulnerable(true);
        m.setSilent(true);
        nomear(m, nome(r, "rei"), ChatFormatting.GOLD);
        return m;
    }

    private static Entity criarMorador(ServerLevel nivel, Reino r, Papel p, BlockPos b) {
        Villager v = EntityType.VILLAGER.create(nivel);
        if (v == null) return null;
        VillagerProfession prof = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(new ResourceLocation(p.profissao()));
        if (prof == null) prof = VillagerProfession.NITWIT;
        v.setVillagerData(v.getVillagerData().setType(Racas.de(r, nivel.getRandom())).setProfession(prof).setLevel(1));
        v.getOffers(); // gera as trocas do nível 1
        try {
            Method atualizar = ObfuscationReflectionHelper.findMethod(AbstractVillager.class, "m_7604_");
            for (int nivelTroca = 2; nivelTroca <= 5; nivelTroca++) {
                v.setVillagerData(v.getVillagerData().setLevel(nivelTroca));
                atualizar.invoke(v);
            }
        } catch (Exception e) {
            v.setVillagerData(v.getVillagerData().setLevel(5));
        }
        MerchantOffers ofertas = v.getOffers();
        if (p.id().equals("comprador")) { ofertas.clear(); ofertas.addAll(Lojas.comprador()); }
        else if (p.id().equals("mercador")) { ofertas.clear(); ofertas.addAll(Lojas.mercador()); }
        else if (p.id().equals("cambista")) { ofertas.clear(); ofertas.addAll(Lojas.cambio()); }
        // parado não reabastece na bancada: estoque praticamente infinito
        for (int i = 0; i < ofertas.size(); i++) ofertas.set(i, Lojas.infinita(ofertas.get(i)));
        v.setVillagerXp(10_000);
        if (p.id().equals("taverneiro")) v.addTag(br.com.frxcode.frxworld.taverna.Tavernas.TAG_TAVERNEIRO);
        v.setNoAi(true);
        v.setInvulnerable(true);
        nomear(v, nome(r, p.id()), ChatFormatting.YELLOW);
        return v;
    }

    static Entity criarGuarda(ServerLevel nivel, Reino r, BlockPos b) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(GUARDA_MOD));
        Entity e = (tipo != null ? tipo : EntityType.IRON_GOLEM).create(nivel);
        if (!(e instanceof Mob m)) return null;
        m.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
        m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(b), MobSpawnType.STRUCTURE, null, null);
        if (tipo != null) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            m.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            m.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            if (m.getMainHandItem().isEmpty()) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            for (EquipmentSlot s : EquipmentSlot.values()) m.setDropChance(s, 0f);
        }
        nomear(m, nome(r, "guarda"), ChatFormatting.GRAY);
        m.restrictTo(b, 16);
        return m;
    }

    private static void nomear(Entity e, String nome, ChatFormatting cor) {
        e.setCustomName(Component.literal(nome).withStyle(cor));
        e.setCustomNameVisible(true);
    }

    // ------------------------------------------------------------------ conversa e proteção

    /** Outros mods (ex.: Guard Villagers) não podem transformar os NPCs do reino ao clicar com arma na mão. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void protegerNpc(PlayerInteractEvent.EntityInteractSpecific evento) {
        if (evento.getTarget().getTags().contains(TAG) && armado(evento.getEntity())) {
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.PASS);
        }
    }

    private static boolean armado(net.minecraft.world.entity.player.Player p) {
        net.minecraft.world.item.Item i = p.getMainHandItem().getItem();
        return i instanceof net.minecraft.world.item.SwordItem || i instanceof net.minecraft.world.item.ProjectileWeaponItem
                || i instanceof net.minecraft.world.item.AxeItem || i instanceof net.minecraft.world.item.TridentItem;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void aoInteragir(PlayerInteractEvent.EntityInteract evento) {
        Entity e = evento.getTarget();
        if (!e.getTags().contains(TAG)) return;
        if (evento.getHand() != InteractionHand.MAIN_HAND) {
            if (armado(evento.getEntity())) { evento.setCanceled(true); evento.setCancellationResult(InteractionResult.FAIL); }
            return;
        }
        String papel = e.getPersistentData().getString("frx_papel");
        // ferreiro/armeiro: clique abre o menu (forja + loja); agachado abre a troca clássica do aldeão
        if ((papel.equals("ferreiro") || papel.equals("armeiro") || papel.equals("cozinheiro") || papel.equals("pedreiro")) && !evento.getEntity().isShiftKeyDown()) {
            if (evento.getEntity() instanceof ServerPlayer jogador) MenusNpc.abrirForja(jogador, e, "", true);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (papel.equals("escravos")) {
            if (evento.getEntity() instanceof ServerPlayer jogador) Escravos.abrir(jogador, e);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (papel.equals("discos")) {
            if (evento.getEntity() instanceof ServerPlayer jogador) br.com.frxcode.frxworld.musica.Discos.abrir(jogador, e);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (papel.equals("leiloeiro")) {
            if (evento.getEntity() instanceof ServerPlayer jogador) br.com.frxcode.frxworld.moedas.Mercado.abrir(jogador, e);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (papel.equals("clas")) {
            if (evento.getEntity() instanceof ServerPlayer jogador) br.com.frxcode.frxworld.clas.Clas.abrir(jogador, e);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (papel.equals("recepcionista")) {
            if (evento.getEntity() instanceof ServerPlayer jogador) br.com.frxcode.frxworld.progressao.Guilda.abrir(jogador, e, "", true);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // rei, taverneiro e lojistas: caixa de fala com retrato e opções
        if (Dialogos.temDialogo(papel) && !evento.getEntity().isShiftKeyDown()) {
            if (evento.getEntity() instanceof ServerPlayer jogador) Dialogos.abrir(jogador, e, "");
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (papel.equals("banqueiro") || papel.equals("cambista") && !evento.getEntity().isShiftKeyDown()) {
            if (evento.getEntity() instanceof ServerPlayer jogador) MenusNpc.abrirBanco(jogador, e, "", true);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // mercador: mesma loja do ferreiro (estoque do dia pelo nivel)
        if (papel.equals("mercador") && !evento.getEntity().isShiftKeyDown()) {
            if (evento.getEntity() instanceof ServerPlayer jogador) MenusNpc.abrirForja(jogador, e, "", true);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // comprador: menu proprio (aceita qualquer item, preco pela raridade)
        if (papel.equals("comprador") && !evento.getEntity().isShiftKeyDown()) {
            if (evento.getEntity() instanceof ServerPlayer jogador) MenusNpc.abrirVender(jogador, e, "", true);
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if ((papel.equals("mercador") || papel.equals("comprador")) && e instanceof Villager v && !evento.getLevel().isClientSide()) {
            // tabela sempre atual (aba Economia do painel)
            MerchantOffers ofertas = v.getOffers();
            ofertas.clear();
            String reinoId = e.getPersistentData().getString("frx_reino");
            if (evento.getEntity() instanceof ServerPlayer jogador) {
                double desc = Reputacao.desconto(jogador, reinoId);
                Reino rr = Reputacao.reino(reinoId);
                ofertas.addAll(papel.equals("mercador")
                        ? Lojas.mercador(desc, Reputacao.indice(Reputacao.pontos(jogador, reinoId)), rr != null ? rr.nome() : "", br.com.frxcode.frxworld.progressao.Progressao.dados(jogador).nivel())
                        : Lojas.comprador(desc));
            } else {
                ofertas.addAll(papel.equals("mercador") ? Lojas.mercador() : Lojas.comprador());
            }
        } else if (e instanceof Villager v && !evento.getLevel().isClientSide() && evento.getEntity() instanceof ServerPlayer jogador
                && !papel.equals("rei") && !papel.equals("guarda")) {
            // demais lojistas do reino: desconto da reputação no preço (volta ao normal ao fechar a troca)
            double desc = Reputacao.desconto(jogador, e.getPersistentData().getString("frx_reino"));
            if (papel.equals("ferreiro") || papel.equals("armeiro")) {
                // loja pelo nível do jogador (o desconto já entra no preço)
                v.getOffers().clear();
                v.getOffers().addAll(Lojas.forja(papel.equals("armeiro"), br.com.frxcode.frxworld.progressao.Progressao.dados(jogador).nivel(), desc,
                        jogador.server, e.getPersistentData().getString("frx_reino") + papel));
            } else if (papel.equals("pedreiro")) {
                v.getOffers().clear();
                v.getOffers().addAll(Lojas.blocos(desc));
            } else if (papel.equals("cozinheiro")) {
                v.getOffers().clear();
                v.getOffers().addAll(Lojas.comida(br.com.frxcode.frxworld.progressao.Progressao.dados(jogador).nivel(), desc,
                        jogador.server, e.getPersistentData().getString("frx_reino") + papel));
            } else if (desc > 0) for (net.minecraft.world.item.trading.MerchantOffer o : v.getOffers())
                o.addToSpecialPriceDiff(-(int) Math.floor(o.getBaseCostA().getCount() * desc));
        }
        if (evento.getEntity() instanceof ServerPlayer jogador) {
            String[] falas = PAPEIS.stream().filter(p -> p.id().equals(papel)).findFirst().map(Papel::falas)
                    .orElse(new String[]{"Mantenha a ordem no reino.", "Siga em frente, viajante."});
            String fala = falas[ThreadLocalRandom.current().nextInt(falas.length)];
            String reacao = Reacoes.fala(jogador, papel);
            if (!reacao.isEmpty() && ThreadLocalRandom.current().nextInt(100) < 65) fala = reacao;
            jogador.sendSystemMessage(Component.literal("[" + (e.getCustomName() != null ? e.getCustomName().getString() : "Morador") + "] ")
                    .withStyle(ChatFormatting.GOLD).append(Component.literal(fala).withStyle(ChatFormatting.WHITE)));
        }
        if (papel.equals("rei") || papel.equals("guarda") || armado(evento.getEntity()) && evento.getEntity().isShiftKeyDown()) {
            evento.setCanceled(true);
            evento.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Moradores (menos guardas) não apanham; para remover: criativo agachado. */
    @SubscribeEvent
    public void proteger(LivingAttackEvent evento) {
        Entity e = evento.getEntity();
        if (!e.getTags().contains(TAG) || "guarda".equals(e.getPersistentData().getString("frx_papel"))) return;
        if (evento.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player p && p.isCreative() && p.isShiftKeyDown()) return;
        evento.setCanceled(true);
    }

    // ------------------------------------------------------------------ comandos

    static Reino reinoDe(ServerPlayer p) {
        for (Reino r : Reinos.todos()) if (r.distancia(p.getBlockX(), p.getBlockZ()) == 0) return r;
        return null;
    }

    private static int remover(ServerLevel nivel, Reino r, String papel) {
        int raio = Math.max(r.larguraChunks(), r.profundidadeChunks()) * 16;
        AABB caixa = new AABB(r.centroX() - raio, nivel.getMinBuildHeight(), r.centroZ() - raio, r.centroX() + raio, nivel.getMaxBuildHeight(), r.centroZ() + raio);
        List<Entity> alvos = new ArrayList<>();
        for (Entity e : nivel.getEntities((Entity) null, caixa, x -> x.getTags().contains(TAG + "_" + r.id()))) {
            if (papel == null || papel.equals(e.getPersistentData().getString("frx_papel"))) alvos.add(e);
        }
        alvos.forEach(Entity::discard);
        return alvos.size();
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        // /rei: onde fica o rei da capital mais próxima
        evento.getDispatcher().register(Commands.literal("rei").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            Reino perto = null;
            int melhor = Integer.MAX_VALUE;
            for (Reino r : Reinos.todos()) {
                int d = r.distancia(p.getBlockX(), p.getBlockZ());
                if (d < melhor) { melhor = d; perto = r; }
            }
            if (perto == null) return 0;
            JsonObject pos = posicoesJson();
            JsonObject sobre = pos.has(perto.id()) && pos.getAsJsonObject(perto.id()).has("npcs") ? pos.getAsJsonObject(perto.id()).getAsJsonObject("npcs") : null;
            Papel rei = PAPEIS.stream().filter(x -> x.id().equals("rei")).findFirst().orElse(null);
            BlockPos b = rei == null ? centro(p.serverLevel(), perto) : posicao(p.serverLevel(), perto, "rei", rei.dx(), rei.dz(), sobre);
            int dx = b.getX() - p.getBlockX(), dz = b.getZ() - p.getBlockZ();
            String dir = Math.abs(dx) > Math.abs(dz) * 2 ? (dx > 0 ? "leste" : "oeste") : Math.abs(dz) > Math.abs(dx) * 2 ? (dz > 0 ? "sul" : "norte")
                    : (dz > 0 ? "sul" : "norte") + "-" + (dx > 0 ? "leste" : "oeste");
            int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
            String nomeRei = nome(perto, "rei");
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("♛ " + nomeRei + " (" + perto.nome() + ")").withStyle(net.minecraft.ChatFormatting.GOLD)
                    .append(net.minecraft.network.chat.Component.literal("\n  em " + b.getX() + ", " + b.getY() + ", " + b.getZ() + " · " + dist + " blocos ao " + dir)
                            .withStyle(net.minecraft.ChatFormatting.YELLOW)));
            return 1;
        }));
        List<String> papeis = new ArrayList<>();
        PAPEIS.forEach(p -> papeis.add(p.id()));
        for (int i = 1; i <= GUARDAS; i++) papeis.add("guarda" + i);
        evento.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("npc")
                        .then(Commands.literal("recriar").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Reino r = reinoDe(p);
                            if (r == null) { ctx.getSource().sendFailure(Component.literal("Entre numa capital primeiro.")); return 0; }
                            ServerLevel nivel = p.serverLevel();
                            int tirados = remover(nivel, r, null);
                            int criados = povoar(nivel, r, null);
                            registrar(nivel, r);
                            ctx.getSource().sendSuccess(() -> Component.literal(r.nome() + ": " + tirados + " removidos, " + criados + " criados."), true);
                            return criados;
                        }))
                        .then(Commands.literal("aqui").then(Commands.argument("papel", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(papeis, b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String papel = StringArgumentType.getString(ctx, "papel");
                                    Reino r = reinoDe(p);
                                    if (r == null) { ctx.getSource().sendFailure(Component.literal("Entre numa capital primeiro.")); return 0; }
                                    if (!papeis.contains(papel)) { ctx.getSource().sendFailure(Component.literal("Papel inválido.")); return 0; }
                                    salvarPosicao(r, papel, p.blockPosition());
                                    String base = papel.startsWith("guarda") ? "guarda" : papel;
                                    if (!base.equals("guarda")) {
                                        remover(p.serverLevel(), r, base);
                                        povoar(p.serverLevel(), r, base);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("Posição de " + papel + " salva em " + r.nome()
                                            + (base.equals("guarda") ? ". Use /frx npc recriar para reposicionar os guardas." : ".")), true);
                                    return 1;
                                })))));
    }

    private static void salvarPosicao(Reino r, String papel, BlockPos b) {
        try {
            Path arq = FMLPaths.GAMEDIR.get().resolve("frxworld").resolve("reinos").resolve("posicoes.json");
            JsonObject raiz = JsonParser.parseString(Files.readString(arq, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject reino = raiz.has(r.id()) ? raiz.getAsJsonObject(r.id()) : new JsonObject();
            JsonObject npcs = reino.has("npcs") ? reino.getAsJsonObject("npcs") : new JsonObject();
            JsonArray a = new JsonArray();
            a.add(b.getX()); a.add(b.getY()); a.add(b.getZ());
            npcs.add(papel, a);
            reino.add("npcs", npcs);
            raiz.add(r.id(), reino);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(arq, gson.toJson(raiz), StandardCharsets.UTF_8);
        } catch (Exception e) {
            FrxWorld.LOG.warn("Não salvei a posição do NPC: {}", e.toString());
        }
    }
}
