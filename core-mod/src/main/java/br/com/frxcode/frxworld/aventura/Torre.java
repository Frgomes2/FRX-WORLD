package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.blocos.Blocos;
import br.com.frxcode.frxworld.grupos.Grupos;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Conquistas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Torre Infinita: 100 andares de combate. Por fora é uma torre maciça e indestrutível, com uma única porta;
 * o saguão tem um portal que leva às salas dos andares (salas fechadas, longe de tudo, uma por grupo).
 * A cada 10 andares há um chefe e, depois dele, um andar seguro que salva o progresso: quem cair recomeça de lá.
 * Dentro da torre não se coloca nem quebra bloco (logo, nem cama nem waystone).
 */
public class Torre {
    public static final int ANDARES = 100;
    private static final String CHAVE = "frx_torre", TAG = "frx_torre";
    private static final int MEIO = 6, ALTURA = 48;                 // torre de fora: 13x13, 48 de altura
    private static final int AX0 = 24000, AZ0 = 24000, AY = 200, PASSO = 64, VAGAS = 8, RMAX = 15, HMAX = 13;   // salas dos andares
    private static final long TEMPO_ANDAR_MS = 6 * 60_000L, TEMPO_CHEFE_MS = 10 * 60_000L, PAUSA_MS = 15_000L;
    private static final String TAG_NPC = "frx_torre_npc";

    private static final String[][] MONSTROS = {
            {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider"},                              // Masmorra
            {"minecraft:husk", "minecraft:skeleton", "minecraft:cave_spider"},                          // Deserto
            {"minecraft:spider", "minecraft:witch", "minecraft:zombie", "minecraft:vindicator"},        // Floresta
            {"minecraft:wither_skeleton", "minecraft:blaze", "minecraft:zoglin"},                       // Fornalha
            {"minecraft:stray", "minecraft:vindicator", "minecraft:pillager"},                          // Geleira
            {"minecraft:wither_skeleton", "minecraft:ravager", "minecraft:husk", "minecraft:witch"}};   // Abismo
    private static final String[] CHEFES = {"minecraft:ravager", "minecraft:wither_skeleton", "minecraft:illusioner", "minecraft:zoglin", "minecraft:vindicator",
            "minecraft:ravager", "minecraft:wither_skeleton", "minecraft:illusioner", "minecraft:zoglin", "minecraft:wither"};
    private static final String[] NOMES_CHEFE = {"Besta do Primeiro Selo", "Cavaleiro Sombrio", "Mestre das Ilusões", "Javali do Abismo", "O Carrasco",
            "Devastador Ancião", "Senhor dos Ossos", "Arquimago Cego", "Fúria do Abismo", "Senhor da Torre"};
    /** Tema de cada bloco de 10 andares: tamanho da sala, materiais e decoração. */
    private record Tema(String nome, int raio, int alto, Block piso, Block piso2, Block parede, Block parede2, Block luz) { }
    private static final Tema[] TEMAS = {
            new Tema("Masmorra", 10, 7, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.SEA_LANTERN),
            new Tema("Deserto", 14, 10, Blocks.SAND, Blocks.SAND, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.GLOWSTONE),
            new Tema("Floresta", 14, 11, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.MOSSY_COBBLESTONE, Blocks.MOSS_BLOCK, Blocks.SHROOMLIGHT),
            new Tema("Fornalha", 10, 7, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.GLOWSTONE),
            new Tema("Geleira", 14, 10, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SEA_LANTERN),
            new Tema("Abismo", 10, 7, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.SEA_LANTERN)};

    private static int indiceTema(int andar) { return (Math.min(ANDARES, Math.max(1, andar)) - 1) / 10 % TEMAS.length; }
    private static Tema tema(int andar) { return TEMAS[indiceTema(andar)]; }

    private enum Fase { PAUSA, LUTA, SEGURO }

    private static final class Corrida {
        final int vaga;
        final Set<UUID> membros = new HashSet<>();
        int andar;
        Fase fase = Fase.PAUSA;
        long ate;          // fim da pausa ou limite do andar
        int pisando;
        Corrida(int vaga) { this.vaga = vaga; }
        BlockPos centro() { return new BlockPos(AX0 + vaga * PASSO, AY, AZ0); }
        net.minecraft.server.level.ServerBossEvent barra;
        int total = 1;     // monstros no começo do andar
        AABB caixa() { BlockPos c = centro(); return new AABB(c.getX() - RMAX - 1, AY - 1, c.getZ() - RMAX - 1, c.getX() + RMAX + 2, AY + HMAX + 2, c.getZ() + RMAX + 2); }
    }

    private static final Map<Integer, Corrida> corridas = new HashMap<>();
    private static final Map<UUID, Integer> noSaguao = new HashMap<>();
    private static final Map<BlockPos, BlockState> desfazer = new LinkedHashMap<>();
    private static BlockPos base;          // centro do saguão (chão)
    private static Direction porta = Direction.SOUTH;

    // ------------------------------------------------------------------ dados do jogador

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    public static int recorde(Player p) { return dados(p).getInt("recorde"); }
    /** Último andar seguro alcançado (0, 10, 20...). */
    public static int seguro(Player p) { return dados(p).getInt("seguro"); }

    public static List<String> titulos(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        if (dados(p).getBoolean("mercenario")) l.add("Mercenário da Torre");
        if (recorde(p) >= 50) l.add("Escalador da Torre");
        if (recorde(p) >= ANDARES) l.add("Conquistador da Torre");
        return l;
    }

    // ------------------------------------------------------------------ fichas, baú do andar seguro e loja

    private static final String TAG_LOJA = "frx_torre_loja";

    public static int fichas(Player p) { return dados(p).getInt("fichas"); }

    private static void darFichas(ServerPlayer p, int n) {
        if (n > 0) dados(p).putInt("fichas", fichas(p) + n);
    }

    private static ItemStack nomeado(ItemStack s, String nome, String linha) {
        s.setHoverName(Component.literal(nome).withStyle(st -> st.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        for (String l : new String[]{linha, "Só existe na Torre Infinita."})
            lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(Component.literal(l).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)))));
        s.getOrCreateTagElement("display").put("Lore", lore);
        return s;
    }

    private record Oferta(String id, String nome, int preco, java.util.function.Function<ServerPlayer, ItemStack> item) { }

    private static final List<Oferta> LOJA = List.of(
            new Oferta("cura", "Poção de Cura II (arremessável)", 4, p -> net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(net.minecraft.world.item.Items.SPLASH_POTION), net.minecraft.world.item.alchemy.Potions.STRONG_HEALING)),
            new Oferta("forca", "Poção de Força II", 6, p -> net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(net.minecraft.world.item.Items.POTION), net.minecraft.world.item.alchemy.Potions.STRONG_STRENGTH)),
            new Oferta("xp", "16 Frascos de Experiência", 8, p -> new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 16)),
            new Oferta("tomo", "Tomo Arcano", 15, p -> new ItemStack(Blocos.TOMO_ARCANO.get())),
            new Oferta("mapa", "Mapa do Tesouro", 20, Tesouros::criar),
            new Oferta("runa", "Runa aleatória (nível I ou II)", 30, p -> Runas.aleatoria(2)),
            new Oferta("maca", "Maçã Dourada Encantada", 40, p -> new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE)),
            new Oferta("netherita", "Lingote de Netherita", 45, p -> new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT)),
            new Oferta("totem", "Totem da Imortalidade", 50, p -> new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)),
            new Oferta("raposa", "Raposa da Torre (ovo de pet)", 80, p -> nomeado(new ItemStack(net.minecraft.world.item.Items.FOX_SPAWN_EGG), "Raposa da Torre", "Um filhote que nasceu entre os andares.")),
            new Oferta("titulo", "Título «Mercenário da Torre»", 100, p -> ItemStack.EMPTY),
            new Oferta("arco", "Arco do Vigia", 120, p -> {
                ItemStack s = new ItemStack(net.minecraft.world.item.Items.BOW);
                s.enchant(net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS, 5);
                s.enchant(net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS, 1);
                s.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                return nomeado(s, "Arco do Vigia", "Do alto da Torre nada escapa.");
            }),
            new Oferta("lamina", "Lâmina da Torre", 150, p -> {
                ItemStack s = new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD);
                s.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 5);
                s.enchant(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING, 3);
                s.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                return nomeado(s, "Lâmina da Torre", "Forjada com o que sobrou de cem andares.");
            }),
            new Oferta("asas", "Asas do Escalador", 250, p -> {
                ItemStack s = new ItemStack(net.minecraft.world.item.Items.ELYTRA);
                s.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                s.enchant(net.minecraft.world.item.enchantment.Enchantments.MENDING, 1);
                return nomeado(s, "Asas do Escalador", "Quem subiu tanto merece voar.");
            }));

    /** A loja só atende no andar seguro ou no saguão da torre. */
    private static boolean podeComprar(ServerPlayer p) {
        Corrida c = corridaDe(p);
        if (c != null) return c.fase == Fase.SEGURO;
        return base != null && p.level() == p.server.overworld() && p.blockPosition().closerThan(base, 7);
    }

    private static void mostrarLoja(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("🗼 Loja da Torre — você tem " + fichas(p) + " fichas. Fichas só caem dentro da Torre.").withStyle(ChatFormatting.LIGHT_PURPLE));
        boolean aqui = podeComprar(p);
        for (Oferta o : LOJA) {
            if (o.id().equals("titulo") && dados(p).getBoolean("mercenario")) continue;
            boolean da = fichas(p) >= o.preco();
            net.minecraft.network.chat.MutableComponent linha = Component.literal("   ");
            if (aqui) linha.append(Component.literal("[Comprar]").withStyle(st -> st.withColor(da ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY).withBold(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/torre comprar " + o.id()))));
            linha.append(Component.literal(" " + o.nome() + " — " + o.preco() + " fichas").withStyle(da ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            p.sendSystemMessage(linha);
        }
        if (!aqui) p.sendSystemMessage(Component.literal("   Para comprar, fale com o Mercador num andar seguro ou use /torre loja no saguão da torre.").withStyle(ChatFormatting.GRAY));
    }

    private static int comprar(ServerPlayer p, String id) {
        if (!podeComprar(p)) { p.sendSystemMessage(Component.literal("A loja só atende no andar seguro ou no saguão da torre.").withStyle(ChatFormatting.RED)); return 0; }
        for (Oferta o : LOJA) {
            if (!o.id().equals(id)) continue;
            if (fichas(p) < o.preco()) { p.sendSystemMessage(Component.literal("Faltam " + (o.preco() - fichas(p)) + " fichas para " + o.nome() + ".").withStyle(ChatFormatting.RED)); return 0; }
            if (o.id().equals("titulo")) {
                if (dados(p).getBoolean("mercenario")) return 0;
                dados(p).putBoolean("mercenario", true);
            } else {
                ItemStack s = o.item().apply(p);
                if (!p.getInventory().add(s)) p.drop(s, false);
            }
            dados(p).putInt("fichas", fichas(p) - o.preco());
            p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 0.8f);
            p.sendSystemMessage(Component.literal("🗼 Comprado: " + o.nome() + ". Restam " + fichas(p) + " fichas." + (o.id().equals("titulo") ? " Use /titulo." : "")).withStyle(ChatFormatting.GREEN));
            return 1;
        }
        return 0;
    }

    /** Baú (de ender, só de enfeite) e Mercador do andar seguro. */
    private static void montarAndarSeguro(MinecraftServer s, ServerLevel n, Corrida c) {
        BlockPos bau = c.centro().offset(-4, 1, 0);
        n.setBlock(bau.above(), Blocks.AIR.defaultBlockState(), 2);
        n.setBlock(bau, Blocks.ENDER_CHEST.defaultBlockState(), 2);
        net.minecraft.world.entity.npc.Villager v = EntityType.VILLAGER.create(n);
        if (v == null) return;
        BlockPos pe = c.centro().offset(4, 1, 3);
        n.setBlock(pe, Blocks.AIR.defaultBlockState(), 2);
        n.setBlock(pe.above(), Blocks.AIR.defaultBlockState(), 2);
        v.moveTo(pe.getX() + 0.5, pe.getY(), pe.getZ() + 0.5, 90, 0);
        v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.CARTOGRAPHER).setLevel(5));
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.setCustomName(Component.literal("Mercador da Torre").withStyle(ChatFormatting.LIGHT_PURPLE));
        v.setCustomNameVisible(true);
        v.addTag(TAG_NPC);
        v.addTag(TAG_LOJA);
        n.addFreshEntity(v);
    }

    @SubscribeEvent
    public void aoFalarComMercador(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract ev) {
        if (!ev.getTarget().getTags().contains(TAG_LOJA)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (ev.getEntity() instanceof ServerPlayer p && ev.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) mostrarLoja(p);
    }

    /** O baú do andar seguro abre uma vez por jogador em cada andar seguro. */
    @SubscribeEvent
    public void aoAbrirBau(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !naSala(ev.getPos().getX(), ev.getPos().getZ()) || p.level() != p.server.overworld()) return;
        if (!p.level().getBlockState(ev.getPos()).is(Blocks.ENDER_CHEST)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (ev.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Corrida c = corridaDe(p);
        if (c == null || c.fase != Fase.SEGURO) return;
        int andar = c.andar - 1;
        CompoundTag d = dados(p);
        if (!d.contains("baus")) d.put("baus", new CompoundTag());
        if (d.getCompound("baus").getBoolean(String.valueOf(andar))) {
            p.displayClientMessage(Component.literal("Você já abriu o baú do andar " + andar + ".").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        d.getCompound("baus").putBoolean(String.valueOf(andar), true);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int fichasGanhas = 5 + andar / 5;
        long moedas = 100L + andar * 30L;
        darFichas(p, fichasGanhas);
        Moedas.dar(p, moedas);
        List<ItemStack> itens = new ArrayList<>();
        itens.add(new ItemStack(net.minecraft.world.item.Items.GOLDEN_CARROT, 8));
        itens.add(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 1 + andar / 20 + rnd.nextInt(2)));
        if (rnd.nextInt(100) < 35) itens.add(new ItemStack(Blocos.TOMO_ARCANO.get()));
        if (rnd.nextInt(100) < 20) itens.add(Tesouros.criar(p));
        if (rnd.nextInt(100) < 5 + andar / 10) itens.add(LOJA.get(6 + rnd.nextInt(3)).item().apply(p));   // maçã encantada, netherita ou totem
        for (ItemStack s : itens) if (!p.getInventory().add(s)) p.drop(s, false);
        p.level().playSound(null, ev.getPos(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 1f, 1f);
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, ev.getPos().getX() + 0.5, ev.getPos().getY() + 1, ev.getPos().getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.3);
        p.sendSystemMessage(Component.literal("🗼 Baú do andar " + andar + ": " + Moedas.texto(moedas) + ", " + fichasGanhas + " fichas e " + itens.size() + " itens.").withStyle(ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------ placar (quadros do lado de fora)

    private static final String TAG_PLACAR = "frx_torre_placar";
    private static com.google.gson.JsonObject placar = new com.google.gson.JsonObject();
    private static boolean placarSujo, quadrosVelhos = true;
    private static final List<UUID> quadros = new ArrayList<>();

    private static Path arquivoPlacar(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_torre_placar.json"); }

    private static com.google.gson.JsonObject linhaPlacar(ServerPlayer p) {
        String id = p.getUUID().toString();
        if (!placar.has(id)) placar.add(id, new com.google.gson.JsonObject());
        com.google.gson.JsonObject j = placar.getAsJsonObject(id);
        j.addProperty("nome", p.getGameProfile().getName());
        return j;
    }

    /** Guarda o recorde do jogador no placar (também para quem está offline aparecer no quadro). */
    private static void anotarRecorde(ServerPlayer p) {
        com.google.gson.JsonObject j = linhaPlacar(p);
        int r = recorde(p);
        if (!j.has("recorde") || j.get("recorde").getAsInt() < r) { j.addProperty("recorde", r); placarSujo = true; quadrosVelhos = true; }
    }

    private static void anotarAbate(ServerPlayer p) {
        com.google.gson.JsonObject j = linhaPlacar(p);
        j.addProperty("abates", (j.has("abates") ? j.get("abates").getAsInt() : 0) + 1);
        placarSujo = true;
    }

    private static List<String[]> melhores(String campo, int quantos) {
        List<String[]> l = new ArrayList<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : placar.entrySet()) {
            com.google.gson.JsonObject j = e.getValue().getAsJsonObject();
            int v = j.has(campo) ? j.get(campo).getAsInt() : 0;
            if (v > 0) l.add(new String[]{j.has("nome") ? j.get("nome").getAsString() : "?", String.valueOf(v)});
        }
        l.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));
        return l.size() > quantos ? l.subList(0, quantos) : l;
    }

    private static Component textoQuadro(String titulo, String campo, String antes, String depois) {
        net.minecraft.network.chat.MutableComponent t = Component.literal(titulo + "\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        List<String[]> l = melhores(campo, 10);
        if (l.isEmpty()) t.append(Component.literal("\nNinguém ainda.\nSeja o primeiro!").withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)));
        for (int i = 0; i < l.size(); i++) {
            ChatFormatting cor = i == 0 ? ChatFormatting.YELLOW : i < 3 ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            t.append(Component.literal("\n" + (i + 1) + "º " + l.get(i)[0] + " — " + antes + l.get(i)[1] + depois).withStyle(s -> s.withColor(cor).withBold(false)));
        }
        return t;
    }

    private static void refazerQuadros(ServerLevel n) {
        for (UUID u : quadros) { Entity e = n.getEntity(u); if (e != null) e.discard(); }
        quadros.clear();
        if (base == null) return;
        Direction lado = porta.getClockWise();
        Component[] textos = {textoQuadro("🗼 MAIS LONGE", "recorde", "andar ", ""), textoQuadro("☠ MAIS ABATES", "abates", "", " monstros")};
        for (int i = 0; i < 2; i++) {
            Entity e = EntityType.TEXT_DISPLAY.create(n);
            if (e == null) continue;
            CompoundTag t = new CompoundTag();
            e.saveWithoutId(t);
            t.putString("text", Component.Serializer.toJson(textos[i]));
            t.putString("billboard", "fixed");
            t.putInt("line_width", 220);
            t.putInt("background", 0xC0101018);
            t.putBoolean("shadow", true);
            e.load(t);
            int desvio = i == 0 ? -4 : 4;
            e.moveTo(base.getX() + 0.5 + porta.getStepX() * (MEIO + 0.62) + lado.getStepX() * desvio, base.getY() + 1.6,
                    base.getZ() + 0.5 + porta.getStepZ() * (MEIO + 0.62) + lado.getStepZ() * desvio, porta.toYRot(), 0);
            e.addTag(TAG_PLACAR);
            quadros.add(e.getUUID());
            n.addFreshEntity(e);
        }
        quadrosVelhos = false;
    }

    /** Quadros antigos gravados no mundo não voltam: são sempre refeitos com o placar atual. */
    @SubscribeEvent
    public void semQuadroVelho(net.minecraftforge.event.entity.EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !ev.getEntity().getTags().contains(TAG_PLACAR)) return;
        if (!quadros.contains(ev.getEntity().getUUID())) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void abateNaTorre(LivingDeathEvent ev) {
        if (ev.getEntity() instanceof Player || ev.getEntity().level().isClientSide()) return;
        if (!(ev.getSource().getEntity() instanceof ServerPlayer p) || corridaDe(p) == null) return;
        if (!naSala(ev.getEntity().getBlockX(), ev.getEntity().getBlockZ())) return;
        anotarAbate(p);
        if (ThreadLocalRandom.current().nextInt(100) < 12) { darFichas(p, 1); p.displayClientMessage(Component.literal("+1 ficha da Torre (" + fichas(p) + ")").withStyle(ChatFormatting.LIGHT_PURPLE), true); }
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && recorde(p) > 0) anotarRecorde(p);
    }

    private static void mostrarPlacar(ServerPlayer p) {
        for (String[] q : new String[][]{{"🗼 Mais longe na Torre", "recorde", "andar ", ""}, {"☠ Mais abates na Torre", "abates", "", " monstros"}}) {
            p.sendSystemMessage(Component.literal(q[0]).withStyle(ChatFormatting.GOLD));
            List<String[]> l = melhores(q[1], 10);
            if (l.isEmpty()) p.sendSystemMessage(Component.literal("   Ninguém ainda.").withStyle(ChatFormatting.GRAY));
            for (int i = 0; i < l.size(); i++)
                p.sendSystemMessage(Component.literal("   " + (i + 1) + "º " + l.get(i)[0] + " — " + q[2] + l.get(i)[1] + q[3]).withStyle(i < 3 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ áreas protegidas

    private static boolean naSala(int x, int z) {
        return x >= AX0 - 48 && x <= AX0 + VAGAS * PASSO + 48 && z >= AZ0 - 48 && z <= AZ0 + 48;
    }

    private static boolean naTorre(BlockPos b) {
        return base != null && Math.abs(b.getX() - base.getX()) <= MEIO + 1 && Math.abs(b.getZ() - base.getZ()) <= MEIO + 1
                && b.getY() >= base.getY() - 3 && b.getY() <= base.getY() + ALTURA + 6;
    }

    private static boolean protegido(Level nivel, BlockPos b) {
        return nivel instanceof ServerLevel sl && sl == sl.getServer().overworld() && (naSala(b.getX(), b.getZ()) || naTorre(b));
    }

    private static boolean admin(Entity e) { return e instanceof ServerPlayer p && p.isCreative() && p.hasPermissions(2); }

    @SubscribeEvent
    public void aoQuebrar(BlockEvent.BreakEvent ev) {
        if (ev.getLevel() instanceof Level l && protegido(l, ev.getPos()) && !admin(ev.getPlayer())) {
            ev.setCanceled(true);
            ev.getPlayer().displayClientMessage(Component.literal("A Torre não pode ser quebrada.").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void aoColocar(BlockEvent.EntityPlaceEvent ev) {
        if (ev.getLevel() instanceof Level l && protegido(l, ev.getPos()) && !admin(ev.getEntity())) {
            ev.setCanceled(true);
            if (ev.getEntity() instanceof ServerPlayer p) p.displayClientMessage(Component.literal("Nada pode ser colocado na Torre (nem cama, nem waystone).").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void aoUsarBalde(FillBucketEvent ev) {
        if (ev.getTarget() instanceof net.minecraft.world.phys.BlockHitResult h && protegido(ev.getLevel(), h.getBlockPos()) && !admin(ev.getEntity())) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoExplodir(ExplosionEvent.Detonate ev) {
        ev.getAffectedBlocks().removeIf(b -> protegido(ev.getLevel(), b));
    }

    @SubscribeEvent
    public void aoDestruirPorMob(LivingDestroyBlockEvent ev) {
        if (protegido(ev.getEntity().level(), ev.getPos())) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void semNascimento(MobSpawnEvent.FinalizeSpawn ev) {
        if (ev.getSpawnType() != MobSpawnType.NATURAL && ev.getSpawnType() != MobSpawnType.PATROL) return;
        BlockPos b = BlockPos.containing(ev.getX(), ev.getY(), ev.getZ());
        if (protegido(ev.getEntity().level(), b)) ev.setSpawnCancelled(true);
    }

    // ------------------------------------------------------------------ torre de fora

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_torre.txt"); }

    @SubscribeEvent
    public void aoIniciar(ServerStartedEvent ev) {
        base = null;
        corridas.clear();
        quadros.clear();
        quadrosVelhos = true;
        placar = new com.google.gson.JsonObject();
        try {
            Path pl = arquivoPlacar(ev.getServer());
            if (Files.exists(pl)) placar = new com.google.gson.Gson().fromJson(Files.readString(pl, java.nio.charset.StandardCharsets.UTF_8), com.google.gson.JsonObject.class);
            if (placar == null) placar = new com.google.gson.JsonObject();
        } catch (Exception e) {
            FrxWorld.LOG.warn("Torre: não li o placar: {}", e.toString());
        }
        try {
            Path a = arquivo(ev.getServer());
            if (!Files.exists(a)) return;
            String[] c = Files.readString(a).trim().split(" ");
            base = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            porta = Direction.byName(c[3]) == null ? Direction.SOUTH : Direction.byName(c[3]);
        } catch (Exception e) {
            FrxWorld.LOG.warn("Torre: não li frx_torre.txt: {}", e.toString());
        }
    }

    private static void por(ServerLevel n, BlockPos b, BlockState s, boolean guardar) {
        if (guardar) desfazer.putIfAbsent(b.immutable(), n.getBlockState(b));
        n.setBlock(b, s, 2);
    }

    private static int construir(ServerPlayer p) {
        ServerLevel n = p.server.overworld();
        if (p.level() != n) return 0;
        BlockPos c = p.blockPosition();
        Direction d = p.getDirection();
        desfazer.clear();
        BlockState parede = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), pilar = Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                vidro = Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), luz = Blocks.SEA_LANTERN.defaultBlockState();
        for (int y = -3; y <= ALTURA; y++) for (int x = -MEIO; x <= MEIO; x++) for (int z = -MEIO; z <= MEIO; z++) {
            BlockPos b = c.offset(x, y, z);
            boolean canto = Math.abs(x) == MEIO && Math.abs(z) == MEIO, casca = Math.abs(x) == MEIO || Math.abs(z) == MEIO;
            boolean saguao = y >= 0 && y <= 5 && Math.abs(x) <= MEIO - 2 && Math.abs(z) <= MEIO - 2;
            BlockState s;
            if (saguao) s = Blocks.AIR.defaultBlockState();
            else if (canto || y % 12 == 11) s = pilar;
            else if (casca && y > 8 && y % 6 == 3 && (x == 0 || z == 0 || Math.abs(x) == 3 || Math.abs(z) == 3)) s = vidro;
            else s = parede;
            por(n, b, s, true);
        }
        // ameias e farol no topo
        for (int x = -MEIO; x <= MEIO; x++) for (int z = -MEIO; z <= MEIO; z++) {
            boolean casca = Math.abs(x) == MEIO || Math.abs(z) == MEIO;
            if (casca && (x + z) % 2 == 0) por(n, c.offset(x, ALTURA + 1, z), pilar, true);
        }
        por(n, c.offset(0, ALTURA + 1, 0), luz, true);
        por(n, c.offset(0, ALTURA + 2, 0), vidro, true);
        // saguão: piso, luzes e o portal no centro
        for (int x = -(MEIO - 2); x <= MEIO - 2; x++) for (int z = -(MEIO - 2); z <= MEIO - 2; z++)
            por(n, c.offset(x, -1, z), Math.abs(x) <= 1 && Math.abs(z) <= 1 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), true);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) por(n, c.offset(sx * (MEIO - 2), 5, sz * (MEIO - 2)), luz, true);
        por(n, c.offset(0, 6, 0), luz, true);
        // a única porta: 3 de largura, 4 de altura, atravessando a parede (2 de espessura)
        Direction lado = d.getClockWise();
        for (int fundo = MEIO - 1; fundo <= MEIO; fundo++) for (int l = -1; l <= 1; l++) for (int y = 0; y <= 3; y++)
            por(n, c.relative(d, fundo).relative(lado, l).above(y), Blocks.AIR.defaultBlockState(), true);
        for (int l = -1; l <= 1; l++) por(n, c.relative(d, MEIO).relative(lado, l).above(4), pilar, true);
        base = c.immutable();
        porta = d;
        quadrosVelhos = true;
        try { Files.writeString(arquivo(p.server), c.getX() + " " + c.getY() + " " + c.getZ() + " " + d.getName()); } catch (Exception ignorado) { }
        p.teleportTo(n, c.getX() + 0.5 + d.getStepX() * (MEIO + 3), c.getY(), c.getZ() + 0.5 + d.getStepZ() * (MEIO + 3), p.getYRot() + 180, 0);
        return desfazer.size();
    }

    private static void sair(ServerPlayer p) {
        ServerLevel n = p.server.overworld();
        if (base != null) p.teleportTo(n, base.getX() + 0.5 + porta.getStepX() * (MEIO + 3), base.getY(), base.getZ() + 0.5 + porta.getStepZ() * (MEIO + 3), porta.getOpposite().toYRot(), 0);
        else { BlockPos s = n.getSharedSpawnPos(); p.teleportTo(n, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0); }
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------------ salas

    private static void montarSala(ServerLevel n, Corrida c) {
        BlockPos o = c.centro();
        Tema t = tema(c.andar);
        int r = t.raio() + 1, h = t.alto() + 1;
        BlockState ar = Blocks.AIR.defaultBlockState();
        for (int x = -RMAX - 1; x <= RMAX + 1; x++) for (int z = -RMAX - 1; z <= RMAX + 1; z++) for (int y = -1; y <= HMAX; y++) {
            boolean dentro = Math.abs(x) <= r && Math.abs(z) <= r && y <= h;
            boolean lateral = Math.abs(x) == r || Math.abs(z) == r;
            BlockState s;
            if (!dentro) s = ar;
            else if (y == -1) s = t.parede().defaultBlockState();
            else if (lateral) s = (y % 4 == 3 ? t.parede2() : t.parede()).defaultBlockState();
            else if (y == 0) s = ((Math.abs(x * 7 + z * 13) % 6 == 0) ? t.piso2() : t.piso()).defaultBlockState();
            else if (y == h) s = (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? t.luz() : t.parede2()).defaultBlockState();
            else s = ar;
            BlockPos b = o.offset(x, y, z);
            if (n.getBlockState(b) != s) n.setBlock(b, s, 2);
        }
        decorar(n, o, t, new java.util.Random(c.vaga * 31L + indiceTema(c.andar)));
    }

    private static void coluna(ServerLevel n, BlockPos pe, Block bloco, int altura) {
        for (int y = 0; y < altura; y++) n.setBlock(pe.above(y), bloco.defaultBlockState(), 2);
    }

    /** Decoração do tema. Fica fora do centro e do corredor do meio (onde o grupo chega e o chefe nasce). */
    private static void decorar(ServerLevel n, BlockPos o, Tema t, java.util.Random rnd) {
        int r = t.raio() - 1;
        List<BlockPos> grade = new ArrayList<>();
        for (int x = -r + 1; x <= r - 1; x += 4) for (int z = -r + 1; z <= r - 1; z += 4)
            if (Math.abs(x) > 2 && Math.max(Math.abs(x), Math.abs(z)) > 4) grade.add(o.offset(x, 1, z));
        java.util.Collections.shuffle(grade, rnd);
        java.util.function.Supplier<BlockPos> solto = () -> {
            for (int i = 0; i < 20; i++) {
                int x = rnd.nextInt(2 * r + 1) - r, z = rnd.nextInt(2 * r + 1) - r;
                BlockPos b = o.offset(x, 1, z);
                if (Math.max(Math.abs(x), Math.abs(z)) > 3 && n.getBlockState(b).isAir()) return b;
            }
            return null;
        };
        switch (t.nome()) {
            case "Deserto" -> {
                for (int i = 0; i < Math.min(12, grade.size()); i++) coluna(n, grade.get(i), Blocks.CACTUS, 1 + rnd.nextInt(3));
                for (int i = 0; i < 25; i++) { BlockPos b = solto.get(); if (b != null && n.getBlockState(b.below()).is(Blocks.SAND) && livreEmVolta(n, b)) n.setBlock(b, Blocks.DEAD_BUSH.defaultBlockState(), 2); }
            }
            case "Floresta" -> {
                BlockState folha = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
                for (int i = 0; i < Math.min(11, grade.size()); i++) {
                    BlockPos pe = grade.get(i);
                    int alt = 4 + rnd.nextInt(2);
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) {
                        if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                        if (dy == 2 && (Math.abs(dx) > 1 || Math.abs(dz) > 1)) continue;
                        BlockPos f = pe.offset(dx, alt - 1 + dy, dz);
                        if (n.getBlockState(f).isAir()) n.setBlock(f, folha, 2);
                    }
                    coluna(n, pe, Blocks.OAK_LOG, alt);
                }
                Block[] plantas = {Blocks.GRASS, Blocks.GRASS, Blocks.FERN, Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET};
                for (int i = 0; i < 70; i++) { BlockPos b = solto.get(); if (b != null) n.setBlock(b, plantas[rnd.nextInt(plantas.length)].defaultBlockState(), 2); }
            }
            case "Geleira" -> {
                for (int i = 0; i < Math.min(12, grade.size()); i++) coluna(n, grade.get(i), rnd.nextInt(4) == 0 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE, 2 + rnd.nextInt(4));
                for (int i = 0; i < 30; i++) { BlockPos b = solto.get(); if (b != null) n.setBlock(b.below(), Blocks.BLUE_ICE.defaultBlockState(), 2); }
            }
            case "Fornalha" -> {
                for (int i = 0; i < Math.min(6, grade.size()); i++) coluna(n, grade.get(i), Blocks.BASALT, 2 + rnd.nextInt(3));
                for (int i = 0; i < 14; i++) { BlockPos b = solto.get(); if (b != null) n.setBlock(b.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2); }
            }
            case "Abismo" -> {
                for (int i = 0; i < Math.min(8, grade.size()); i++) coluna(n, grade.get(i), Blocks.POLISHED_BLACKSTONE, 3 + rnd.nextInt(3));
            }
            default -> {
                for (int i = 0; i < Math.min(6, grade.size()); i++) coluna(n, grade.get(i), Blocks.CHISELED_STONE_BRICKS, 2);
                for (int i = 0; i < 8; i++) { BlockPos b = solto.get(); if (b != null) n.setBlock(b, Blocks.COBWEB.defaultBlockState(), 2); }
            }
        }
    }

    private static boolean livreEmVolta(ServerLevel n, BlockPos b) {
        return n.getBlockState(b.north()).isAir() && n.getBlockState(b.south()).isAir() && n.getBlockState(b.east()).isAir() && n.getBlockState(b.west()).isAir();
    }

    /** Barra no topo da tela: andar, tema, monstros que faltam e tempo. */
    private static void atualizarBarra(MinecraftServer s, Corrida c, int vivos, long agora) {
        boolean chefe = c.andar % 10 == 0;
        String texto = "Torre Infinita — Andar " + c.andar + " de " + ANDARES + " · " + tema(c.andar).nome();
        float progresso = 1f;
        switch (c.fase) {
            case LUTA -> {
                long resta = Math.max(0, (c.ate - agora) / 1000);
                texto += (chefe ? " · CHEFE" : "") + " · faltam " + vivos + " · " + resta / 60 + ":" + String.format("%02d", resta % 60);
                progresso = Math.max(0f, Math.min(1f, vivos / (float) Math.max(1, c.total)));
            }
            case SEGURO -> texto = "Torre Infinita — Andar seguro (" + (c.andar - 1) + " salvo) · próximo: " + c.andar + " · " + tema(c.andar).nome();
            case PAUSA -> texto += " · prepare-se";
        }
        if (c.barra == null) c.barra = new net.minecraft.server.level.ServerBossEvent(Component.literal(texto), net.minecraft.world.BossEvent.BossBarColor.PURPLE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
        c.barra.setName(Component.literal(texto).withStyle(c.fase == Fase.SEGURO ? ChatFormatting.AQUA : chefe ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        c.barra.setColor(c.fase == Fase.SEGURO ? net.minecraft.world.BossEvent.BossBarColor.GREEN : chefe ? net.minecraft.world.BossEvent.BossBarColor.RED : net.minecraft.world.BossEvent.BossBarColor.PURPLE);
        c.barra.setProgress(progresso);
        List<ServerPlayer> gente = membros(s, c);
        for (ServerPlayer p : new ArrayList<>(c.barra.getPlayers())) if (!gente.contains(p)) c.barra.removePlayer(p);
        for (ServerPlayer p : gente) c.barra.addPlayer(p);
    }

    private static void limparSala(ServerLevel n, Corrida c) {
        for (Entity e : n.getEntities((Entity) null, c.caixa(), e -> !(e instanceof Player))) e.discard();
    }

    private static List<ServerPlayer> membros(MinecraftServer s, Corrida c) {
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID u : c.membros) { ServerPlayer p = s.getPlayerList().getPlayer(u); if (p != null) l.add(p); }
        return l;
    }

    private static Corrida corridaDe(Player p) {
        for (Corrida c : corridas.values()) if (c.membros.contains(p.getUUID())) return c;
        return null;
    }

    private static void falar(MinecraftServer s, Corrida c, String texto, ChatFormatting cor) {
        for (ServerPlayer p : membros(s, c)) p.sendSystemMessage(Component.literal("🗼 " + texto).withStyle(cor));
    }

    private static void iniciar(ServerPlayer lider) {
        MinecraftServer s = lider.server;
        ServerLevel n = s.overworld();
        List<ServerPlayer> grupo = new ArrayList<>();
        for (ServerPlayer m : Grupos.perto(lider)) if (corridaDe(m) == null && m.isAlive() && m.distanceTo(lider) <= 10) grupo.add(m);
        if (!grupo.contains(lider)) grupo.add(lider);
        int vaga = -1;
        for (int i = 0; i < VAGAS; i++) if (!corridas.containsKey(i)) { vaga = i; break; }
        if (vaga < 0) { lider.displayClientMessage(Component.literal("A Torre está cheia agora. Tente em instantes.").withStyle(ChatFormatting.RED), true); return; }
        int inicio = ANDARES;
        for (ServerPlayer m : grupo) inicio = Math.min(inicio, seguro(m));
        Corrida c = new Corrida(vaga);
        c.andar = Math.min(ANDARES, inicio + 1);
        corridas.put(vaga, c);
        montarSala(n, c);
        limparSala(n, c);
        BlockPos o = c.centro();
        for (ServerPlayer m : grupo) {
            c.membros.add(m.getUUID());
            m.teleportTo(n, o.getX() + 0.5, AY + 1, o.getZ() + 0.5, m.getYRot(), 0);
            m.fallDistance = 0;
            Sistema.avisar(m, "Torre Infinita", "Você entra no andar " + c.andar + ". A cada 10 andares há um chefe e, depois dele, um andar seguro que salva o progresso. /torre sair para desistir.", Sistema.ROXO);
        }
        c.fase = Fase.PAUSA;
        c.ate = System.currentTimeMillis() + 5000;
    }

    private static void comecarAndar(MinecraftServer s, Corrida c) {
        ServerLevel n = s.overworld();
        List<ServerPlayer> gente = membros(s, c);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        BlockPos o = c.centro();
        boolean chefe = c.andar % 10 == 0;
        int tema = (c.andar - 1) / 10 % MONSTROS.length;
        int nivel = Math.max(1, Math.min(ConfigJogo.zonaNivelMax(), 2 + c.andar));
        double extra = c.andar > 55 ? (c.andar - 55) * 0.04 : 0;     // acima do nível máximo, a vida continua subindo
        int qtd = chefe ? 2 + gente.size() : Math.min(14, 3 + c.andar / 8 + gente.size());
        for (int i = 0; i < qtd + (chefe ? 1 : 0); i++) {
            boolean oChefe = chefe && i == 0;
            String id = oChefe ? CHEFES[(c.andar / 10 - 1) % CHEFES.length] : MONSTROS[tema][rnd.nextInt(MONSTROS[tema].length)];
            EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
            Entity e = tipo == null ? null : tipo.create(n);
            if (!(e instanceof Mob m)) continue;
            int raio = tema(c.andar).raio();
            double x = o.getX() + 0.5, z = o.getZ() + 0.5 - 6;
            for (int tent = 0; tent < 12 && !oChefe; tent++) {
                int dx = rnd.nextInt(-raio + 2, raio - 1), dz = rnd.nextInt(-raio + 2, raio - 1);
                BlockPos pe = o.offset(dx, 1, dz);
                if (Math.max(Math.abs(dx), Math.abs(dz)) < 4 || !n.getBlockState(pe).getCollisionShape(n, pe).isEmpty() || !n.getBlockState(pe.above()).getCollisionShape(n, pe.above()).isEmpty()) continue;
                x = o.getX() + 0.5 + dx; z = o.getZ() + 0.5 + dz;
                break;
            }
            m.moveTo(x, AY + 1, z, rnd.nextFloat() * 360f, 0);
            m.finalizeSpawn(n, n.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            Zonas.aplicarNivel(m, nivel);
            double vezes = extra + (oChefe ? 2.0 + c.andar / 25.0 + (gente.size() - 1) * 0.6 : (gente.size() - 1) * 0.25);
            var vida = m.getAttribute(Attributes.MAX_HEALTH);
            if (vida != null && vezes > 0) vida.addPermanentModifier(new AttributeModifier(UUID.fromString("7a1e4c55-0b6d-4c11-8f3a-5d2e9b7c1a01"), "FRX torre", vezes, AttributeModifier.Operation.MULTIPLY_TOTAL));
            m.setHealth(m.getMaxHealth());
            m.setPersistenceRequired();
            m.addTag(TAG);
            if (oChefe) {
                m.setCustomName(Component.literal("☠ " + NOMES_CHEFE[(c.andar / 10 - 1) % NOMES_CHEFE.length]).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
                m.setCustomNameVisible(true);
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
            }
            n.addFreshEntity(m);
            if (!gente.isEmpty()) m.setTarget(gente.get(rnd.nextInt(gente.size())));
            n.sendParticles(ParticleTypes.POOF, x, AY + 1.5, z, 8, 0.3, 0.5, 0.3, 0.02);
        }
        c.fase = Fase.LUTA;
        c.total = Math.max(1, qtd + (chefe ? 1 : 0));
        c.ate = System.currentTimeMillis() + (chefe ? TEMPO_CHEFE_MS : TEMPO_ANDAR_MS);
        n.playSound(null, o.above(), chefe ? SoundEvents.WITHER_SPAWN : SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, chefe ? 0.6f : 1.5f, chefe ? 1.2f : 0.7f);
        for (ServerPlayer p : gente) p.displayClientMessage(Component.literal(chefe ? "Andar " + c.andar + " — CHEFE!" : "Andar " + c.andar).withStyle(chefe ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD), true);
    }

    private static void andarLimpo(MinecraftServer s, Corrida c) {
        ServerLevel n = s.overworld();
        boolean chefe = c.andar % 10 == 0;
        for (ServerPlayer p : membros(s, c)) {
            boolean novo = c.andar > recorde(p);
            long moedas = (10 + c.andar * 4L) * (chefe ? 5 : 1), xp = c.andar * 20L * (chefe ? 5 : 1);
            if (!novo) { moedas /= 4; xp /= 4; }
            Moedas.dar(p, moedas);
            Progressao.ganharXp(p, xp);
            int ganhas = (chefe ? 10 : 1) + (novo ? 2 : 0);
            darFichas(p, ganhas);
            if (novo) {
                dados(p).putInt("recorde", c.andar);
                anotarRecorde(p);
                Conquistas.maximo(p, "torre", c.andar);
                Temporadas.pontuar(p, "torre", 1);
            }
            if (chefe) {
                dados(p).putInt("seguro", Math.max(seguro(p), c.andar));
                p.heal(p.getMaxHealth());
                if (novo) Tesouros.talvezDar(p, 20);
                if (novo) Runas.talvezDar(p, 35, c.andar >= 60 ? 3 : c.andar >= 30 ? 2 : 1);
                if (novo && ThreadLocalRandom.current().nextInt(100) < 40) {
                    ItemStack tomo = new ItemStack(Blocos.TOMO_ARCANO.get());
                    if (!p.getInventory().add(tomo)) p.drop(tomo, false);
                }
            }
            p.displayClientMessage(Component.literal("Andar " + c.andar + " vencido! +" + Moedas.texto(moedas) + " e " + xp + " XP" + " e " + ganhas + (ganhas == 1 ? " ficha" : " fichas") + (novo ? "  (novo recorde)" : "")).withStyle(ChatFormatting.GREEN), true);
        }
        n.playSound(null, c.centro().above(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
        if (c.andar >= ANDARES) {
            for (ServerPlayer p : membros(s, c)) {
                s.getPlayerList().broadcastSystemMessage(Component.literal("🗼 " + p.getGameProfile().getName() + " venceu o andar " + ANDARES + " e conquistou a Torre Infinita!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                Sistema.avisar(p, "Torre conquistada", "Você derrotou o Senhor da Torre. Novo título: «Conquistador da Torre» (/titulo).", Sistema.OURO);
            }
            encerrar(s, c, true);
            return;
        }
        if (chefe) {
            c.fase = Fase.SEGURO;
            c.pisando = 0;
            limparSala(n, c);
            c.andar++;
            montarSala(n, c);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) n.setBlock(c.centro().offset(x, 0, z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
            if ((c.andar - 1) % 50 == 0)
                for (ServerPlayer p : membros(s, c)) s.getPlayerList().broadcastSystemMessage(Component.literal("🗼 " + p.getGameProfile().getName() + " chegou ao andar " + (c.andar - 1) + " da Torre Infinita!").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            cozinheiro(s, n, c);
            montarAndarSeguro(s, n, c);
            falar(s, c, "Andar seguro. Progresso salvo no andar " + (c.andar - 1) + ": se cair ou sair, você recomeça daqui. Aqui há um baú, o Cozinheiro (comida) e o Mercador (fichas).", ChatFormatting.AQUA);
            escolha(s, c, "Descanse o quanto quiser. Para subir, fique no centro (obsidiana) ou clique:");
        } else {
            c.andar++;
            c.fase = Fase.PAUSA;
            c.ate = System.currentTimeMillis() + PAUSA_MS;
            escolha(s, c, "Andar " + (c.andar - 1) + " vencido. O andar " + c.andar + " começa em " + PAUSA_MS / 1000 + " s.");
        }
    }

    /** Botões no chat: continuar agora ou sair (quem sai volta depois ao último andar seguro). */
    private static void escolha(MinecraftServer s, Corrida c, String texto) {
        for (ServerPlayer p : membros(s, c)) {
            p.sendSystemMessage(Component.literal("🗼 " + texto + "  ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("[Continuar agora]").withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/torre continuar"))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Começar o próximo andar já")))))
                    .append(Component.literal("  "))
                    .append(Component.literal("[Sair da Torre]").withStyle(st -> st.withColor(ChatFormatting.RED).withBold(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/torre sair"))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Sair agora. Ao voltar, você recomeça do andar " + (seguro(p) + 1) + " (último andar seguro)."))))));
        }
    }

    /** Vendedor de comida do andar seguro (mesma loja de comida dos reinos, paga em moedas). */
    private static void cozinheiro(MinecraftServer s, ServerLevel n, Corrida c) {
        net.minecraft.world.entity.npc.Villager v = EntityType.VILLAGER.create(n);
        if (v == null) return;
        BlockPos pe = c.centro().offset(4, 1, 0);
        n.setBlock(pe, Blocks.AIR.defaultBlockState(), 2);
        n.setBlock(pe.above(), Blocks.AIR.defaultBlockState(), 2);
        v.moveTo(pe.getX() + 0.5, pe.getY(), pe.getZ() + 0.5, 90, 0);
        v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.BUTCHER).setLevel(5));
        v.setVillagerXp(10_000);
        int nivel = 1;
        for (ServerPlayer p : membros(s, c)) if (Progressao.temDados(p)) nivel = Math.max(nivel, Progressao.dados(p).nivel());
        v.getOffers().clear();
        try { v.getOffers().addAll(br.com.frxcode.frxworld.reinos.Lojas.comida(nivel, 0, s, "torre" + c.andar)); }
        catch (Exception e) { FrxWorld.LOG.warn("Torre: loja do cozinheiro falhou: {}", e.toString()); }
        if (v.getOffers().isEmpty()) {
            v.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(Moedas.cobre(5), new ItemStack(net.minecraft.world.item.Items.BREAD, 8), 9999, 0, 0f));
            v.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(Moedas.prata(1), new ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 8), 9999, 0, 0f));
            v.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(Moedas.prata(3), new ItemStack(net.minecraft.world.item.Items.GOLDEN_CARROT, 8), 9999, 0, 0f));
        }
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.setCustomName(Component.literal("Cozinheiro da Torre").withStyle(ChatFormatting.YELLOW));
        v.setCustomNameVisible(true);
        v.addTag(TAG_NPC);
        n.addFreshEntity(v);
    }

    private static void encerrar(MinecraftServer s, Corrida c, boolean tirarJogadores) {
        if (c.barra != null) { c.barra.removeAllPlayers(); c.barra = null; }
        if (tirarJogadores) for (ServerPlayer p : membros(s, c)) sair(p);
        limparSala(s.overworld(), c);
        corridas.remove(c.vaga);
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 10 != 4) return;
        MinecraftServer s = ev.getServer();
        ServerLevel n = s.overworld();
        long agora = System.currentTimeMillis();
        // entrada: ficar 1,5 s em cima do portal do saguão
        if (base != null) {
            for (ServerPlayer p : n.players()) {
                boolean em = p.isAlive() && !p.isSpectator() && Math.abs(p.getX() - (base.getX() + 0.5)) <= 1.5 && Math.abs(p.getZ() - (base.getZ() + 0.5)) <= 1.5
                        && p.getY() >= base.getY() - 0.5 && p.getY() <= base.getY() + 2 && corridaDe(p) == null;
                if (!em) { noSaguao.remove(p.getUUID()); continue; }
                int t = noSaguao.merge(p.getUUID(), 1, Integer::sum);
                n.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.8, 0.4, 0.5);
                if (t >= 3) { noSaguao.remove(p.getUUID()); iniciar(p); }
            }
            if (s.getTickCount() % 40 == 4) n.sendParticles(ParticleTypes.REVERSE_PORTAL, base.getX() + 0.5, base.getY() + 1.2, base.getZ() + 0.5, 30, 0.8, 1, 0.8, 0.02);
        }
        for (Corrida c : new ArrayList<>(corridas.values())) {
            try { tickCorrida(s, n, c, agora); }
            catch (Exception e) { FrxWorld.LOG.warn("Torre: corrida {} falhou: {}", c.vaga, e.toString()); encerrar(s, c, true); }
        }
        // quadros do placar ao lado da porta: refeitos quando o placar muda (no máximo a cada 15 s) e quando o lugar carrega
        if (s.getTickCount() % 300 == 4) {
            if (placarSujo) {
                try { Files.writeString(arquivoPlacar(s), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(placar), java.nio.charset.StandardCharsets.UTF_8); placarSujo = false; }
                catch (Exception e) { FrxWorld.LOG.warn("Torre: não gravei o placar: {}", e.toString()); }
                quadrosVelhos = true;
            }
            if (base != null && n.isPositionEntityTicking(base)) {
                boolean faltando = quadros.size() < 2;
                for (UUID u : quadros) if (n.getEntity(u) == null) faltando = true;
                if (quadrosVelhos || faltando) refazerQuadros(n);
            }
        }
        // quem ficou numa sala sem corrida (queda do servidor, login) volta para a porta
        if (s.getTickCount() % 100 == 4)
            for (ServerPlayer p : n.players())
                if (naSala(p.getBlockX(), p.getBlockZ()) && corridaDe(p) == null && !admin(p)) sair(p);
    }

    private static void tickCorrida(MinecraftServer s, ServerLevel n, Corrida c, long agora) {
        AABB caixa = c.caixa();
        c.membros.removeIf(u -> {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            return p == null || !p.isAlive() || p.level() != n || !caixa.inflate(4).contains(p.position());
        });
        if (c.membros.isEmpty()) { encerrar(s, c, false); return; }
        int vivosAgora = c.fase == Fase.LUTA ? n.getEntitiesOfClass(Mob.class, caixa, m -> m.isAlive() && (m instanceof Enemy || m.getTags().contains(TAG))).size() : 0;
        atualizarBarra(s, c, vivosAgora, agora);
        switch (c.fase) {
            case PAUSA -> { if (agora >= c.ate) comecarAndar(s, c); }
            case LUTA -> {
                List<Mob> vivos = n.getEntitiesOfClass(Mob.class, caixa, m -> m.isAlive() && (m instanceof Enemy || m.getTags().contains(TAG)));
                if (vivos.isEmpty()) { andarLimpo(s, c); return; }
                if (agora > c.ate) {
                    falar(s, c, "O tempo acabou no andar " + c.andar + ". A Torre expulsa você.", ChatFormatting.RED);
                    encerrar(s, c, true);
                    return;
                }
                List<ServerPlayer> gente = membros(s, c);
                for (Mob m : vivos) if (m.getTarget() == null && !gente.isEmpty()) m.setTarget(gente.get(n.random.nextInt(gente.size())));
                long resta = (c.ate - agora) / 1000;
                if (resta == 60 || resta == 30) for (ServerPlayer p : gente) p.displayClientMessage(Component.literal("Restam " + resta + " s neste andar!").withStyle(ChatFormatting.RED), true);
            }
            case SEGURO -> {
                BlockPos o = c.centro();
                boolean alguem = false;
                for (ServerPlayer p : membros(s, c)) if (Math.abs(p.getX() - (o.getX() + 0.5)) <= 1.5 && Math.abs(p.getZ() - (o.getZ() + 0.5)) <= 1.5) alguem = true;
                c.pisando = c.pisando >= 99 ? 99 : alguem ? c.pisando + 1 : 0;
                n.sendParticles(ParticleTypes.END_ROD, o.getX() + 0.5, AY + 1.5, o.getZ() + 0.5, 6, 0.8, 0.8, 0.8, 0.01);
                if (c.pisando >= 4) {
                    for (Entity e : n.getEntities((Entity) null, caixa, e -> e.getTags().contains(TAG_NPC))) e.discard();
                    n.setBlock(o.offset(-4, 1, 0), Blocks.AIR.defaultBlockState(), 2);
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) n.setBlock(o.offset(x, 0, z), tema(c.andar).piso().defaultBlockState(), 2);   // tira a obsidiana do centro
                    c.fase = Fase.PAUSA;
                    c.ate = agora + 3000;
                }
            }
        }
    }

    // ------------------------------------------------------------------ eventos de jogador

    /** Na Torre ninguém morre de verdade: quem cai é expulso com vida, sem perder itens. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void aoMorrer(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Corrida c = corridaDe(p);
        if (c == null) return;
        ev.setCanceled(true);
        p.setHealth(p.getMaxHealth() * 0.5f);
        p.removeAllEffects();
        p.clearFire();
        c.membros.remove(p.getUUID());
        sair(p);
        Sistema.avisar(p, "Você caiu no andar " + c.andar, "A Torre poupa a sua vida, mas expulsa você. Ao voltar, recomeça do andar " + (seguro(p) + 1) + ".", Sistema.VERMELHO);
        if (c.membros.isEmpty()) encerrar(p.server, c, false);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        noSaguao.remove(ev.getEntity().getUUID());
        Corrida c = corridaDe(ev.getEntity());
        if (c != null && ev.getEntity() instanceof ServerPlayer p) {
            c.membros.remove(p.getUUID());
            sair(p);
            if (c.membros.isEmpty()) encerrar(p.server, c, false);
        }
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        for (Corrida c : new ArrayList<>(corridas.values())) encerrar(ev.getServer(), c, true);
    }

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("torre")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.sendSystemMessage(Component.literal("🗼 Torre Infinita — recorde: andar " + recorde(p) + " de " + ANDARES + " · você recomeça do andar " + Math.min(ANDARES, seguro(p) + 1) + " · " + fichas(p) + " fichas (/torre loja).").withStyle(ChatFormatting.LIGHT_PURPLE));
                    p.sendSystemMessage(Component.literal(base == null ? "   A torre ainda não foi erguida neste mundo."
                            : "   Entrada em " + base.getX() + ", " + base.getY() + ", " + base.getZ() + ". Fique sobre o portal do saguão para subir (grupo perto entra junto).").withStyle(ChatFormatting.GRAY));
                    return 1;
                })
                .then(Commands.literal("continuar").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Corrida co = corridaDe(p);
                    if (co == null) { p.sendSystemMessage(Component.literal("Você não está na Torre.").withStyle(ChatFormatting.GRAY)); return 0; }
                    if (co.fase == Fase.PAUSA) co.ate = System.currentTimeMillis();
                    else if (co.fase == Fase.SEGURO) co.pisando = 99;
                    return 1;
                }))
                .then(Commands.literal("loja").executes(c -> { mostrarLoja(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("comprar").then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(c -> comprar(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "item")))))
                .then(Commands.literal("placar").executes(c -> { mostrarPlacar(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("sair").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Corrida co = corridaDe(p);
                    if (co == null) { p.sendSystemMessage(Component.literal("Você não está na Torre.").withStyle(ChatFormatting.GRAY)); return 0; }
                    co.membros.remove(p.getUUID());
                    sair(p);
                    if (co.membros.isEmpty()) encerrar(p.server, co, false);
                    return 1;
                })));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("torre")
                        .then(Commands.literal("aqui").executes(c -> {
                            int n = construir(c.getSource().getPlayerOrException());
                            c.getSource().sendSuccess(() -> Component.literal(n > 0 ? "Torre erguida (" + n + " blocos). A porta ficou na direção para onde você olhava. /frx torre desfazer volta atrás (até reiniciar)."
                                    : "Só no mundo principal."), true);
                            return n;
                        }))
                        .then(Commands.literal("desfazer").executes(c -> {
                            ServerLevel n = c.getSource().getServer().overworld();
                            int q = desfazer.size();
                            for (Map.Entry<BlockPos, BlockState> e : desfazer.entrySet()) n.setBlock(e.getKey(), e.getValue(), 2);
                            desfazer.clear();
                            for (UUID u : quadros) { Entity quadro = n.getEntity(u); if (quadro != null) quadro.discard(); }
                            quadros.clear();
                            base = null;
                            try { Files.deleteIfExists(arquivo(c.getSource().getServer())); } catch (Exception ignorado) { }
                            c.getSource().sendSuccess(() -> Component.literal(q > 0 ? "Torre removida." : "Nada para desfazer (só vale até reiniciar)."), true);
                            return q;
                        }))
                        .then(Commands.literal("andar").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, ANDARES)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int v = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n") / 10 * 10;
                            dados(p).putInt("seguro", v);
                            c.getSource().sendSuccess(() -> Component.literal("Seu andar seguro agora é o " + v + "."), false);
                            return 1;
                        })))));
    }
}
