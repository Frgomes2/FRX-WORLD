package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.Personagens;
import br.com.frxcode.frxworld.habilidades.Sombras;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * X1: duelo combinado entre dois jogadores, numa arena criada pelo admin. O desafio sai da aba X1 do inventário
 * (ou chega pelo chat, com botões de aceitar e recusar) e leva as regras: habilidades (com as invocações), itens do
 * personagem ou kit sorteado igual para os dois, poções, arena, luta única ou melhor de 3 e aposta (moedas ou um item).
 *
 * Ninguém morre de verdade: o golpe fatal é cancelado e a rodada acaba, então não há punição de morte nem túmulo.
 * Antes da luta o servidor guarda o inventário, a vida, a fome, os efeitos e o lugar de cada um no próprio arquivo do
 * jogador (frx_x1, nos dados persistentes); no fim devolve tudo. Como a foto e a aposta ficam no mesmo arquivo que o
 * inventário, se o servidor cair ou alguém sair no meio, o que foi guardado volta na próxima entrada, sem perda nem cópia.
 *
 * Arenas e placar em <mundo>/frx_x1.json. Arenas pelo admin com /frx arena.
 */
public class X1 {
    /** Ações da tela (AcaoMenu de tipo "x1"). DESAFIAR: npc = id da entidade do alvo, valor = regras empacotadas. ASSISTIR: npc = número da arena (1...). */
    public static final int PEDIR = 0, DESAFIAR = 1, ACEITAR = 2, RECUSAR = 3, CANCELAR = 4, ASSISTIR = 5, SAIR = 6;
    public static final int SEM_APOSTA = 0, APOSTA_MOEDAS = 1, APOSTA_ITEM = 2;
    public static final long APOSTA_MAX = 1_000_000;   // em cobre: 10 mil ouro

    private static final String CHAVE = "frx_x1";
    private static final long CONVITE_MS = 30_000, CONTAGEM_MS = 4_000, RODADA_MS = 5 * 60_000L, INTERVALO_MS = 4_000;
    private static final int RAIO_PADRAO = 48;
    private static final String[] KITS = {"Cavaleiro", "Arqueiro", "Bárbaro", "Paladino", "Besteiro"};

    /** Regras do duelo. arena: 0 = sorteada entre as livres; n = a n-ésima da lista (em ordem alfabética de id). */
    public record Regras(boolean hab, boolean kit, boolean pocoes, boolean md3, int aposta, long moedas, int arena, boolean vidaIgual) {
        public static Regras de(long v) {
            return new Regras((v & 1) != 0, (v & 2) != 0, (v & 4) != 0, (v & 8) != 0, (int) ((v >> 4) & 3), Math.max(0, Math.min(APOSTA_MAX, v >> 16)), (int) ((v >> 8) & 0xFF), (v & 64) != 0);
        }
    }

    /** O valor que a tela manda no DESAFIAR. */
    public static long empacotar(boolean hab, boolean kit, boolean pocoes, boolean md3, int aposta, int arena, long moedas, boolean vidaIgual) {
        return (vidaIgual ? 64 : 0) | (hab ? 1 : 0) | (kit ? 2 : 0) | (pocoes ? 4 : 0) | (md3 ? 8 : 0) | ((long) (aposta & 3) << 4) | ((long) (arena & 0xFF) << 8)
                | (Math.max(0, Math.min(APOSTA_MAX, moedas)) << 16);
    }

    private static final class Convite {
        final UUID de, para; final Regras r; final String arena; final long expira = System.currentTimeMillis() + CONVITE_MS;
        Convite(UUID de, UUID para, Regras r, String arena) { this.de = de; this.para = para; this.r = r; this.arena = arena; }
    }

    private enum Fase { CONTAGEM, LUTA, INTERVALO }

    private static final class Duelo {
        final UUID a, b; final String nomeA, nomeB; final Regras r; final String arena; final int kit;
        int vitA, vitB, rodada = 1, ultimoSeg = -1;
        Fase fase = Fase.CONTAGEM;
        long ate;
        /** Rodada decidida num evento de dano: o fecho fica para o próximo tick, fora do evento. */
        boolean resolver; UUID vencedorDaRodada;
        Duelo(UUID a, UUID b, String nomeA, String nomeB, Regras r, String arena, int kit) { this.a = a; this.b = b; this.nomeA = nomeA; this.nomeB = nomeB; this.r = r; this.arena = arena; this.kit = kit; }
        boolean tem(UUID u) { return a.equals(u) || b.equals(u); }
        UUID outro(UUID u) { return a.equals(u) ? b : a; }
    }

    private static JsonObject dados;
    private static boolean sujo;
    /** Convite pendente, pela conta de quem foi desafiado. */
    private static final Map<UUID, Convite> convites = new HashMap<>();
    private static final List<Duelo> duelos = new ArrayList<>();
    /** Quem está assistindo → arena. */
    private static final Map<UUID, String> plateia = new HashMap<>();
    /** Quem abriu um baú, mochila ou outro menu no meio do duelo: fecha no tick seguinte. */
    private static final Set<UUID> fechar = new HashSet<>();

    public X1() {}

    // ------------------------------------------------------------------ o que os outros sistemas perguntam

    private static Duelo duelo(UUID u) {
        for (Duelo d : duelos) if (d.tem(u)) return d;
        return null;
    }

    public static boolean emDuelo(Player p) { return duelo(p.getUUID()) != null; }

    /** Lutando com kit sorteado: as classes de equipamento por rank não valem (o kit é igual para os dois). */
    public static boolean comKit(Player p) {
        Duelo d = duelo(p.getUUID());
        return d != null && d.kit >= 0;
    }

    /** "Dano em jogadores" das habilidades e das invocações durante o duelo: vale a regra combinada. null = não está em duelo. */
    public static Boolean danoEmJogadores(Player p) {
        Duelo d = duelo(p.getUUID());
        return d == null ? null : d.r.hab();
    }

    /** Barra habilidade, Despertar, Suprema e invocações quando o duelo é sem habilidades ou a luta ainda não começou. */
    public static boolean semHabilidades(ServerPlayer p) {
        Duelo d = duelo(p.getUUID());
        if (d == null) return false;
        if (!d.r.hab()) { p.displayClientMessage(Component.literal("Este duelo é sem habilidades.").withStyle(ChatFormatting.RED), true); return true; }
        if (d.fase != Fase.LUTA) { p.displayClientMessage(Component.literal("Espere a luta começar.").withStyle(ChatFormatting.GRAY), true); return true; }
        return false;
    }

    // ------------------------------------------------------------------ arquivo (arenas e placar)

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_x1.json"); }

    private static JsonObject dados(MinecraftServer s) {
        if (dados != null) return dados;
        try { if (Files.exists(arquivo(s))) dados = new Gson().fromJson(Files.readString(arquivo(s), StandardCharsets.UTF_8), JsonObject.class); }
        catch (Exception e) { FrxWorld.LOG.warn("X1: não li frx_x1.json: {}", e.toString()); }
        if (dados == null) dados = new JsonObject();
        if (!dados.has("arenas")) dados.add("arenas", new JsonObject());
        if (!dados.has("placar")) dados.add("placar", new JsonObject());
        return dados;
    }

    private static void gravar(MinecraftServer s) {
        if (!sujo || dados == null) return;
        try { Files.writeString(arquivo(s), new GsonBuilder().setPrettyPrinting().create().toJson(dados), StandardCharsets.UTF_8); sujo = false; }
        catch (Exception e) { FrxWorld.LOG.warn("X1: não gravei frx_x1.json: {}", e.toString()); }
    }

    private static JsonObject arenas(MinecraftServer s) { return dados(s).getAsJsonObject("arenas"); }

    private static boolean pronta(JsonObject a) { return a.has("s1") && a.has("s2"); }

    /** Arenas com os dois pontos marcados, em ordem alfabética de id (é a ordem que a tela usa). */
    private static List<String> prontas(MinecraftServer s) {
        List<String> l = new ArrayList<>();
        for (String id : new TreeSet<>(arenas(s).keySet())) if (pronta(arenas(s).getAsJsonObject(id))) l.add(id);
        return l;
    }

    private static boolean ocupada(String id) {
        for (Duelo d : duelos) if (d.arena.equals(id)) return true;
        return false;
    }

    private static String nomeArena(MinecraftServer s, String id) {
        JsonObject a = arenas(s).has(id) ? arenas(s).getAsJsonObject(id) : null;
        return a != null && a.has("nome") ? a.get("nome").getAsString() : id;
    }

    /** Ponto guardado como [x, y, z, giro, inclinação]. */
    private static double[] ponto(JsonObject arena, String chave) {
        JsonArray a = arena.getAsJsonArray(chave);
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble(), a.get(4).getAsDouble()};
    }

    private static ServerLevel nivel(MinecraftServer s, String dim) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        ServerLevel n = id == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return n != null ? n : s.overworld();
    }

    private static ServerLevel nivelDa(MinecraftServer s, JsonObject arena) { return nivel(s, arena.has("dim") ? arena.get("dim").getAsString() : "minecraft:overworld"); }

    private static void levar(ServerPlayer p, ServerLevel n, double[] pt) {
        p.stopRiding();
        p.teleportTo(n, pt[0], pt[1], pt[2], (float) pt[3], (float) pt[4]);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------------ o que fica guardado no arquivo do jogador

    private static CompoundTag tagSeExiste(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return pers.contains(CHAVE) ? pers.getCompound(CHAVE) : null;
    }

    private static CompoundTag tag(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    /** Tira a marca do jogador quando não sobrou nada guardado. */
    private static void limpar(Player p) {
        CompoundTag t = tagSeExiste(p);
        if (t != null && t.isEmpty()) p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove(CHAVE);
    }

    /** Guarda de onde o jogador saiu (não troca o que já estava guardado). */
    private static void marcarVolta(ServerPlayer p) {
        CompoundTag t = tag(p);
        if (t.contains("dim")) return;
        t.putString("dim", p.level().dimension().location().toString());
        t.putDouble("x", p.getX()); t.putDouble("y", p.getY()); t.putDouble("z", p.getZ());
        t.putFloat("yr", p.getYRot()); t.putFloat("xr", p.getXRot());
    }

    /** Leva de volta para onde estava antes do duelo (ou de assistir). */
    private static void voltar(ServerPlayer p) {
        CompoundTag t = tagSeExiste(p);
        if (t == null || !t.contains("dim")) return;
        p.stopRiding();
        p.teleportTo(nivel(p.server, t.getString("dim")), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yr"), t.getFloat("xr"));
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        for (String k : new String[]{"dim", "x", "y", "z", "yr", "xr"}) t.remove(k);
        limpar(p);
    }

    /** Foto de antes da luta: inventário inteiro (armadura e mão secundária juntas), efeitos, vida, fome e lugar. */
    private static void fotografar(ServerPlayer p) {
        CompoundTag t = tag(p);
        t.put("inv", p.getInventory().save(new ListTag()));
        ListTag efeitos = new ListTag();
        for (MobEffectInstance e : p.getActiveEffects()) efeitos.add(e.save(new CompoundTag()));
        t.put("efeitos", efeitos);
        t.putFloat("vida", p.getHealth());
        t.putInt("fome", p.getFoodData().getFoodLevel());
        t.putFloat("sat", p.getFoodData().getSaturationLevel());
        marcarVolta(p);
    }

    // ------------------------------------------------------------------ acessórios (Curios) e vida igual

    private static final UUID VIDA_IGUAL = UUID.nameUUIDFromBytes("frxworld:x1:vida_igual".getBytes());

    /** Todos os espaços de acessório do jogador (null sem o Curios). Por reflexão, como em protecao/AneisUnicos. */
    private static net.minecraftforge.items.IItemHandlerModifiable acessorios(ServerPlayer p) {
        try {
            Object talvez = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, p);
            Object inventario = ((net.minecraftforge.common.util.LazyOptional<?>) talvez).resolve().orElse(null);
            if (inventario == null) return null;
            Object todos = inventario.getClass().getMethod("getEquippedCurios").invoke(inventario);
            return todos instanceof net.minecraftforge.items.IItemHandlerModifiable h ? h : null;
        } catch (Throwable e) { return null; }
    }

    /** Tira os acessórios e guarda na foto. */
    private static void guardarAcessorios(ServerPlayer p, CompoundTag t) {
        net.minecraftforge.items.IItemHandlerModifiable h = acessorios(p);
        if (h == null) return;
        ListTag l = t.contains("curios") ? t.getList("curios", Tag.TAG_COMPOUND) : new ListTag();   // rodada 2 em diante: soma ao que já está guardado
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty()) continue;
            CompoundTag c = s.save(new CompoundTag());
            c.putInt("frx_espaco", i);
            l.add(c);
            h.setStackInSlot(i, ItemStack.EMPTY);
        }
        t.put("curios", l);
    }

    private static void devolverAcessorios(ServerPlayer p, CompoundTag t) {
        if (!t.contains("curios")) return;
        net.minecraftforge.items.IItemHandlerModifiable h = acessorios(p);
        for (Tag e : t.getList("curios", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            ItemStack s = ItemStack.of(c);
            int i = c.getInt("frx_espaco");
            if (s.isEmpty()) continue;
            if (h != null && i < h.getSlots() && h.getStackInSlot(i).isEmpty()) h.setStackInSlot(i, s);
            else if (!p.getInventory().add(s)) p.drop(s, false);
        }
    }

    private static void tirarVidaIgual(ServerPlayer p) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance a = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (a != null) a.removeModifier(VIDA_IGUAL);
    }

    /** Regra "vida igual": com kit, 20 corações para os dois; com os itens do personagem, a vida do que tem menos. */
    private static void igualarVida(Duelo d, ServerPlayer a, ServerPlayer b) {
        tirarVidaIgual(a); tirarVidaIgual(b);
        double alvo = d.kit >= 0 ? 40 : Math.min(a.getMaxHealth(), b.getMaxHealth());
        for (ServerPlayer p : List.of(a, b)) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance at = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (at == null) continue;
            double atual = at.getValue();
            if (atual > 0 && Math.abs(atual - alvo) > 0.01)
                at.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(VIDA_IGUAL, "FRX X1 vida igual", alvo / atual - 1, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
            p.setHealth(p.getMaxHealth());
            avisar(p, "Vida igual: os dois lutam com " + Math.round(alvo / 2) + " corações.", ChatFormatting.WHITE);
        }
    }

    /** Devolve o que a foto guardou. Não mexe no lugar (isso é com voltar). */
    private static void restaurar(ServerPlayer p) {
        CompoundTag t = tagSeExiste(p);
        if (t == null || !t.contains("inv")) return;
        p.getInventory().clearContent();
        p.getInventory().load(t.getList("inv", Tag.TAG_COMPOUND));
        devolverAcessorios(p, t);
        tirarVidaIgual(p);
        p.removeAllEffects();
        for (Tag e : t.getList("efeitos", Tag.TAG_COMPOUND)) {
            MobEffectInstance ef = MobEffectInstance.load((CompoundTag) e);
            if (ef != null) p.addEffect(ef);
        }
        p.setHealth(Math.max(1f, Math.min(p.getMaxHealth(), t.getFloat("vida"))));
        p.getFoodData().setFoodLevel(t.getInt("fome"));
        p.getFoodData().setSaturation(t.getFloat("sat"));
        p.clearFire();
        for (String k : new String[]{"inv", "efeitos", "vida", "fome", "sat", "curios"}) t.remove(k);
        limpar(p);
        p.inventoryMenu.broadcastChanges();
    }

    private static void entregar(ServerPlayer p, ItemStack s) {
        if (s.isEmpty()) return;
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /** Tira a aposta que estava guardada com o jogador e entrega para quem vai ficar com ela (ele mesmo, ou o vencedor). */
    private static void passarAposta(ServerPlayer de, ServerPlayer para) {
        CompoundTag t = tagSeExiste(de);
        if (t == null) return;
        long moedas = t.getLong("moedas");
        ItemStack item = t.contains("item") ? ItemStack.of(t.getCompound("item")) : ItemStack.EMPTY;
        t.remove("moedas"); t.remove("item");
        limpar(de);
        Moedas.dar(para, moedas);
        entregar(para, item);
        para.inventoryMenu.broadcastChanges();
    }

    /** Cobra a aposta e deixa guardada com o próprio jogador. Retorna a razão de não ter dado certo, ou null. */
    private static String cobrarAposta(ServerPlayer p, Regras r) {
        switch (r.aposta()) {
            case APOSTA_MOEDAS -> {
                if (r.moedas() <= 0) return "escolha o valor da aposta";
                if (!Moedas.cobrar(p, r.moedas())) return "faltam moedas na bolsa para a aposta de " + Moedas.texto(r.moedas());
                tag(p).putLong("moedas", r.moedas());
            }
            case APOSTA_ITEM -> {
                ItemStack mao = p.getMainHandItem();
                if (mao.isEmpty()) return "segure na mão o item que vai apostar";
                tag(p).put("item", mao.save(new CompoundTag()));
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            default -> { }
        }
        p.inventoryMenu.broadcastChanges();
        return null;
    }

    private static String textoAposta(ServerPlayer dono, Regras r) {
        switch (r.aposta()) {
            case APOSTA_MOEDAS: return Moedas.texto(r.moedas()) + " de cada um";
            case APOSTA_ITEM: {
                CompoundTag t = tagSeExiste(dono);
                ItemStack item = t != null && t.contains("item") ? ItemStack.of(t.getCompound("item")) : ItemStack.EMPTY;
                return (item.isEmpty() ? "um item" : item.getCount() + "x " + item.getHoverName().getString()) + " contra o item que o outro estiver segurando";
            }
            default: return "";
        }
    }

    // ------------------------------------------------------------------ quem pode ser chamado

    /** Por que este jogador não pode entrar num X1 agora, ou null se pode. */
    private static String ocupado(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || !Progressao.temDados(p)) return "indisponível";
        if (p.isCreative()) return "no modo criativo";
        if (duelo(p.getUUID()) != null) return "em duelo";
        if (plateia.containsKey(p.getUUID())) return "assistindo um duelo";
        if (Personagens.escolhendo(p) || br.com.frxcode.frxworld.acesso.Abertura.emAndamento(p)) return "entrando no jogo";
        if (Torre.dentro(p)) return "na Torre Infinita";
        if (Fenda.dentro(p)) return "numa dungeon de portal";
        if (br.com.frxcode.frxworld.dungeon.Dungeons.dentro(p)) return "numa dungeon";
        if (p.level().dimension() == Level.OVERWORLD && br.com.frxcode.frxworld.progressao.AreasNivel.em(p.blockPosition()) > 0) return "numa masmorra";
        if (br.com.frxcode.frxworld.protecao.AreasProtegidas.noLabirinto(p.level(), p.blockPosition())) return "no labirinto";
        if (Corridas.correndo(p)) return "numa corrida";
        return null;
    }

    private static Convite conviteDe(UUID quem) {
        for (Convite c : convites.values()) if (c.de.equals(quem)) return c;
        return null;
    }

    private static ServerPlayer porIdDeEntidade(MinecraftServer s, int id) {
        for (ServerPlayer q : s.getPlayerList().getPlayers()) if (q.getId() == id) return q;
        return null;
    }

    private static void avisar(ServerPlayer p, String texto, ChatFormatting cor) {
        p.sendSystemMessage(Component.literal("⚔ ").withStyle(ChatFormatting.GOLD).append(Component.literal(texto).withStyle(cor)));
    }

    private static void titulo(ServerPlayer p, String t, String sub, ChatFormatting cor) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 22, 6));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t).withStyle(cor, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
    }

    private static MutableComponent botao(String texto, String comando, String dica, ChatFormatting cor) {
        return Component.literal(texto).withStyle(st -> st.withColor(cor).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, comando))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(dica))));
    }

    private static String regrasEmTexto(MinecraftServer s, Regras r, String arena) {
        return (r.hab() ? "com habilidades e invocações" : "sem habilidades") + " · " + (r.kit() ? "kit sorteado igual para os dois" : "itens do personagem")
                + " · " + (r.vidaIgual() ? "vida igual para os dois" : "vida de cada um") + " · " + (r.pocoes() ? "com poções" : "sem poções") + " · " + (r.md3() ? "melhor de 3" : "luta única")
                + " · arena " + (arena.isEmpty() ? "sorteada" : nomeArena(s, arena));
    }

    // ------------------------------------------------------------------ desafio

    private static void desafiar(ServerPlayer p, ServerPlayer alvo, Regras r) {
        MinecraftServer s = p.server;
        if (alvo == null || alvo == p) { avisar(p, "Esse jogador não está mais online.", ChatFormatting.RED); return; }
        String eu = ocupado(p), ele = ocupado(alvo);
        if (eu != null) { avisar(p, "Você não pode desafiar agora: está " + eu + ".", ChatFormatting.RED); return; }
        if (ele != null) { avisar(p, Personagens.nome(alvo) + " não pode ser desafiado agora: está " + ele + ".", ChatFormatting.RED); return; }
        if (conviteDe(p.getUUID()) != null || convites.containsKey(p.getUUID())) { avisar(p, "Você já tem um desafio em aberto.", ChatFormatting.RED); return; }
        if (convites.containsKey(alvo.getUUID()) || conviteDe(alvo.getUUID()) != null) { avisar(p, Personagens.nome(alvo) + " já tem um desafio em aberto.", ChatFormatting.RED); return; }
        List<String> prontas = prontas(s);
        if (prontas.isEmpty()) { avisar(p, "Ainda não há arena pronta. O admin cria com /frx arena.", ChatFormatting.RED); return; }
        String arena = "";
        if (r.arena() > 0) {
            if (r.arena() > prontas.size()) { avisar(p, "Essa arena não existe mais.", ChatFormatting.RED); return; }
            arena = prontas.get(r.arena() - 1);
            if (ocupada(arena)) { avisar(p, "A arena " + nomeArena(s, arena) + " está ocupada. Escolha outra ou deixe sortear.", ChatFormatting.RED); return; }
        }
        String erro = cobrarAposta(p, r);
        if (erro != null) { avisar(p, "Desafio não enviado: " + erro + ".", ChatFormatting.RED); return; }

        convites.put(alvo.getUUID(), new Convite(p.getUUID(), alvo.getUUID(), r, arena));
        String regras = regrasEmTexto(s, r, arena);
        alvo.sendSystemMessage(Component.literal("⚔ " + Personagens.nome(p) + " desafiou você para um X1!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        alvo.sendSystemMessage(Component.literal("   " + regras).withStyle(ChatFormatting.WHITE));
        if (r.aposta() != SEM_APOSTA) alvo.sendSystemMessage(Component.literal("   Aposta: " + textoAposta(p, r) + ".").withStyle(ChatFormatting.YELLOW));
        if (r.aposta() == APOSTA_ITEM) alvo.sendSystemMessage(Component.literal("   Para aceitar, segure na mão o item que você aposta.").withStyle(ChatFormatting.GRAY));
        alvo.sendSystemMessage(Component.literal("   ").append(botao("[Aceitar]", "/x1 aceitar", "Aceitar o duelo", ChatFormatting.GREEN)).append(Component.literal("  "))
                .append(botao("[Recusar]", "/x1 recusar", "Recusar o duelo", ChatFormatting.RED)).append(Component.literal("  (vale por 30 segundos)").withStyle(ChatFormatting.GRAY)));
        alvo.level().playSound(null, alvo.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.6f);
        avisar(p, "Desafio enviado para " + Personagens.nome(alvo) + " (" + regras + "). Ele tem 30 segundos para responder.", ChatFormatting.WHITE);
        enviar(alvo, false);
    }

    /** Desfaz um convite: a aposta de quem desafiou volta para ele. */
    private static void desfazer(MinecraftServer s, Convite c, String paraQuemDesafiou, String paraODesafiado) {
        convites.remove(c.para);
        ServerPlayer de = s.getPlayerList().getPlayer(c.de), para = s.getPlayerList().getPlayer(c.para);
        if (de != null) {
            passarAposta(de, de);
            if (paraQuemDesafiou != null) avisar(de, paraQuemDesafiou, ChatFormatting.GRAY);
            enviar(de, false);
        }
        if (para != null) {
            if (paraODesafiado != null) avisar(para, paraODesafiado, ChatFormatting.GRAY);
            enviar(para, false);
        }
    }

    private static void aceitar(ServerPlayer p) {
        MinecraftServer s = p.server;
        Convite c = convites.get(p.getUUID());
        if (c == null) { avisar(p, "Você não tem desafio em aberto.", ChatFormatting.GRAY); return; }
        ServerPlayer de = s.getPlayerList().getPlayer(c.de);
        if (de == null) { convites.remove(p.getUUID()); avisar(p, "Quem desafiou saiu do jogo.", ChatFormatting.GRAY); return; }
        String eu = ocupado(p), ele = ocupado(de);
        if (eu != null) { avisar(p, "Você não pode aceitar agora: está " + eu + ".", ChatFormatting.RED); return; }
        if (ele != null) { desfazer(s, c, "Desafio cancelado: você está " + ele + ".", Personagens.nome(de) + " não pode lutar agora (está " + ele + ")."); return; }
        String arena = c.arena;
        if (arena.isEmpty()) {
            List<String> livres = new ArrayList<>();
            for (String id : prontas(s)) if (!ocupada(id)) livres.add(id);
            if (livres.isEmpty()) { avisar(p, "Todas as arenas estão ocupadas. Tente de novo em instantes (o desafio continua valendo).", ChatFormatting.RED); return; }
            arena = livres.get(ThreadLocalRandom.current().nextInt(livres.size()));
        } else if (!arenas(s).has(arena) || !pronta(arenas(s).getAsJsonObject(arena)) || ocupada(arena)) {
            avisar(p, "A arena " + nomeArena(s, arena) + " está ocupada. Tente de novo em instantes (o desafio continua valendo).", ChatFormatting.RED);
            return;
        }
        String erro = cobrarAposta(p, c.r);
        if (erro != null) { avisar(p, "Para aceitar: " + erro + ".", ChatFormatting.RED); return; }
        convites.remove(p.getUUID());
        iniciar(s, c, de, p, arena);
    }

    // ------------------------------------------------------------------ duelo

    private static void iniciar(MinecraftServer s, Convite c, ServerPlayer a, ServerPlayer b, String arena) {
        int kit = c.r.kit() ? ThreadLocalRandom.current().nextInt(KITS.length) : -1;
        Duelo d = new Duelo(a.getUUID(), b.getUUID(), Personagens.nome(a), Personagens.nome(b), c.r, arena, kit);
        for (ServerPlayer p : List.of(a, b)) {
            p.closeContainer();
            p.stopRiding();
            if (!c.r.hab()) Sombras.recolherTodas(p);
            fotografar(p);
            CompoundTag fecha = new CompoundTag();   // a aba X1 some da frente de quem vai lutar
            fecha.putBoolean("fechar", true);
            Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("x1", fecha));
        }
        duelos.add(d);
        MutableComponent anuncio = Component.literal("⚔ X1: " + d.nomeA + " contra " + d.nomeB + " na arena " + nomeArena(s, arena) + " (" + (c.r.md3() ? "melhor de 3" : "luta única")
                + (c.r.aposta() != SEM_APOSTA ? ", valendo aposta" : "") + ").").withStyle(ChatFormatting.GOLD);
        if (arenas(s).getAsJsonObject(arena).has("esp"))
            anuncio.append(Component.literal(" ")).append(botao("[Assistir]", "/x1 assistir " + arena, "Ir para a arquibancada desta luta", ChatFormatting.AQUA));
        s.getPlayerList().broadcastSystemMessage(anuncio, false);
        prepararRodada(s, d, a, b);
    }

    /** Começo de cada rodada: equipamento, vida cheia, cada um no seu ponto e contagem. */
    private static void prepararRodada(MinecraftServer s, Duelo d, ServerPlayer a, ServerPlayer b) {
        JsonObject arena = arenas(s).getAsJsonObject(d.arena);
        ServerLevel n = nivelDa(s, arena);
        for (ServerPlayer p : List.of(a, b)) {
            equipar(d, p);
            p.removeAllEffects();
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5f);
            p.clearFire();
            levar(p, n, ponto(arena, p == a ? "s1" : "s2"));
            if (d.kit >= 0) avisar(p, "Kit desta luta: " + KITS[d.kit] + ". Os seus itens estão guardados e voltam no fim.", ChatFormatting.WHITE);
        }
        d.fase = Fase.CONTAGEM;
        d.ate = System.currentTimeMillis() + CONTAGEM_MS;
        d.ultimoSeg = -1;
    }

    /** Kit sorteado, ou os itens do personagem como estavam na foto (assim cada rodada começa igual). */
    private static void equipar(Duelo d, ServerPlayer p) {
        CompoundTag t = tagSeExiste(p);
        if (t == null || !t.contains("inv")) return;   // sem foto não se mexe no inventário
        p.getInventory().clearContent();
        if (d.kit >= 0) guardarAcessorios(p, t);   // kit sorteado: anéis e outros acessórios (Curios) também ficam de fora
        if (d.kit >= 0) darKit(p, d.kit, d.r.pocoes());
        else p.getInventory().load(t.getList("inv", Tag.TAG_COMPOUND));
        p.inventoryMenu.broadcastChanges();
    }

    private static void vestir(ServerPlayer p, Item cabeca, Item peito, Item pernas, Item pes) {
        if (cabeca != null) p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(cabeca));
        if (peito != null) p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(peito));
        if (pernas != null) p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(pernas));
        if (pes != null) p.setItemSlot(EquipmentSlot.FEET, new ItemStack(pes));
    }

    private static void darKit(ServerPlayer p, int kit, boolean pocoes) {
        switch (kit) {
            case 0 -> {   // Cavaleiro
                vestir(p, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                p.getInventory().add(new ItemStack(Items.IRON_SWORD));
                p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
            }
            case 1 -> {   // Arqueiro
                vestir(p, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
                p.getInventory().add(new ItemStack(Items.BOW));
                p.getInventory().add(new ItemStack(Items.STONE_SWORD));
                p.getInventory().add(new ItemStack(Items.ARROW, 48));
                p.getInventory().add(new ItemStack(Items.BREAD, 8));
            }
            case 2 -> {   // Bárbaro
                vestir(p, null, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
                p.getInventory().add(new ItemStack(Items.DIAMOND_AXE));
                p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
                p.getInventory().add(new ItemStack(Items.GOLDEN_APPLE));
            }
            case 3 -> {   // Paladino
                vestir(p, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                p.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
                p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 8));
            }
            default -> {   // Besteiro
                vestir(p, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
                p.getInventory().add(new ItemStack(Items.CROSSBOW));
                p.getInventory().add(new ItemStack(Items.IRON_SWORD));
                p.getInventory().add(new ItemStack(Items.ARROW, 32));
                p.getInventory().add(new ItemStack(Items.BREAD, 8));
            }
        }
        if (pocoes) {
            p.getInventory().add(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.STRONG_HEALING));
            p.getInventory().add(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.STRONG_HEALING));
            p.getInventory().add(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.SWIFTNESS));
        }
    }

    /** Fecha a rodada (chamado no tick, nunca de dentro de um evento de dano). vencedor null = empate. */
    private static void fimDaRodada(MinecraftServer s, Duelo d, UUID vencedor) {
        ServerPlayer a = s.getPlayerList().getPlayer(d.a), b = s.getPlayerList().getPlayer(d.b);
        if (a == null || b == null) { encerrar(s, d, a == null ? (b == null ? null : d.b) : d.a, null, true, "o adversário saiu do jogo"); return; }
        if (vencedor != null) { if (vencedor.equals(d.a)) d.vitA++; else d.vitB++; }
        for (ServerPlayer p : List.of(a, b)) { p.removeAllEffects(); p.setHealth(p.getMaxHealth()); p.clearFire(); }
        int precisa = d.r.md3() ? 2 : 1, maximo = d.r.md3() ? 3 : 1;
        if (d.vitA >= precisa || d.vitB >= precisa || d.rodada >= maximo) {
            encerrar(s, d, d.vitA > d.vitB ? d.a : d.vitB > d.vitA ? d.b : null, null, true, "");
            return;
        }
        String quem = vencedor == null ? "Rodada empatada" : "Rodada para " + (vencedor.equals(d.a) ? d.nomeA : d.nomeB);
        for (ServerPlayer p : List.of(a, b)) titulo(p, quem, d.nomeA + " " + d.vitA + " x " + d.vitB + " " + d.nomeB, ChatFormatting.GOLD);
        d.rodada++;
        d.fase = Fase.INTERVALO;
        d.ate = System.currentTimeMillis() + INTERVALO_MS;
    }

    /**
     * Fim do duelo. vencedor null = empate (cada um fica com a própria aposta). saindo = quem está deixando o jogo neste
     * instante: recebe o inventário de volta já, mas o lugar só na próxima entrada. teleportar false = servidor desligando.
     */
    private static void encerrar(MinecraftServer s, Duelo d, UUID vencedor, UUID saindo, boolean teleportar, String motivo) {
        if (!duelos.remove(d)) return;
        ServerPlayer a = s.getPlayerList().getPlayer(d.a), b = s.getPlayerList().getPlayer(d.b);
        for (ServerPlayer p : new ServerPlayer[]{a, b}) if (p != null) restaurar(p);
        // aposta: o vencedor leva a dele de volta e a do outro; no empate cada um fica com a sua
        ServerPlayer ganhou = vencedor == null ? null : vencedor.equals(d.a) ? a : b;
        for (ServerPlayer p : new ServerPlayer[]{a, b}) if (p != null) passarAposta(p, ganhou != null ? ganhou : p);
        for (ServerPlayer p : new ServerPlayer[]{a, b}) if (p != null && teleportar && !p.getUUID().equals(saindo)) voltar(p);

        if (teleportar) {
            String placar = d.r.md3() ? " por " + Math.max(d.vitA, d.vitB) + " a " + Math.min(d.vitA, d.vitB) : "";
            String texto = vencedor == null ? "⚔ X1: " + d.nomeA + " e " + d.nomeB + " empataram"
                    : "🏆 X1: " + (vencedor.equals(d.a) ? d.nomeA : d.nomeB) + " venceu " + (vencedor.equals(d.a) ? d.nomeB : d.nomeA) + placar;
            if (!motivo.isEmpty()) texto += " (" + motivo + ")";
            if (vencedor != null && d.r.aposta() == APOSTA_MOEDAS) texto += " e levou " + Moedas.texto(d.r.moedas() * 2);
            if (vencedor != null && d.r.aposta() == APOSTA_ITEM) texto += " e levou os itens apostados";
            s.getPlayerList().broadcastSystemMessage(Component.literal(texto + ".").withStyle(ChatFormatting.GOLD), false);
            for (ServerPlayer p : new ServerPlayer[]{a, b}) {
                if (p == null || p.getUUID().equals(saindo)) continue;
                boolean venceu = p.getUUID().equals(vencedor);
                titulo(p, vencedor == null ? "Empate" : venceu ? "Vitória!" : "Derrota", "Seus itens, vida e lugar foram devolvidos.", vencedor == null ? ChatFormatting.YELLOW : venceu ? ChatFormatting.GREEN : ChatFormatting.RED);
                if (venceu) p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
            }
        }
        if (teleportar) registrar(s, d, a, b, vencedor);   // desligamento do servidor não entra no placar
        // a plateia desta arena volta para onde estava
        for (Map.Entry<UUID, String> e : new ArrayList<>(plateia.entrySet())) {
            if (!e.getValue().equals(d.arena)) continue;
            plateia.remove(e.getKey());
            ServerPlayer q = s.getPlayerList().getPlayer(e.getKey());
            if (q != null && teleportar) voltar(q);
        }
        for (ServerPlayer p : new ServerPlayer[]{a, b}) if (p != null && teleportar && !p.getUUID().equals(saindo)) enviar(p, false);
    }

    /** Placar por personagem: vitórias, derrotas, empates, sequência atual e a melhor. */
    private static void registrar(MinecraftServer s, Duelo d, ServerPlayer a, ServerPlayer b, UUID vencedor) {
        JsonObject placar = dados(s).getAsJsonObject("placar");
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            if (p == null) continue;
            String id = Personagens.id(p).toString();
            JsonObject o = placar.has(id) ? placar.getAsJsonObject(id) : new JsonObject();
            int v = o.has("v") ? o.get("v").getAsInt() : 0, der = o.has("d") ? o.get("d").getAsInt() : 0, e = o.has("e") ? o.get("e").getAsInt() : 0;
            int seq = o.has("seq") ? o.get("seq").getAsInt() : 0, melhor = o.has("melhor") ? o.get("melhor").getAsInt() : 0;
            if (vencedor == null) e++;
            else if (p.getUUID().equals(vencedor)) { v++; seq++; melhor = Math.max(melhor, seq); }
            else { der++; seq = 0; }
            o.addProperty("nome", Personagens.nome(p));
            o.addProperty("v", v); o.addProperty("d", der); o.addProperty("e", e); o.addProperty("seq", seq); o.addProperty("melhor", melhor);
            placar.add(id, o);
        }
        // histórico dos últimos 60 duelos (o site mostra): quem lutou, quem venceu, placar, arena e regras
        if (!dados(s).has("historico")) dados(s).add("historico", new com.google.gson.JsonArray());
        com.google.gson.JsonArray hist = dados(s).getAsJsonArray("historico");
        JsonObject h = new JsonObject();
        h.addProperty("quando", System.currentTimeMillis());
        h.addProperty("a", d.nomeA); h.addProperty("b", d.nomeB);
        h.addProperty("vencedor", vencedor == null ? "" : vencedor.equals(d.a) ? d.nomeA : d.nomeB);
        h.addProperty("vitA", d.vitA); h.addProperty("vitB", d.vitB);
        h.addProperty("md3", d.r.md3()); h.addProperty("kit", d.r.kit()); h.addProperty("hab", d.r.hab()); h.addProperty("vidaIgual", d.r.vidaIgual());
        h.addProperty("arena", nomeArena(s, d.arena));
        hist.add(h);
        while (hist.size() > 60) hist.remove(0);
        sujo = true;
        gravar(s);
    }

    // ------------------------------------------------------------------ plateia

    private static void assistir(ServerPlayer p, String arena) {
        MinecraftServer s = p.server;
        if (arena.isEmpty()) {   // sem dizer qual: a única luta em andamento
            if (duelos.size() == 1) arena = duelos.get(0).arena;
            else { avisar(p, duelos.isEmpty() ? "Não há duelo acontecendo agora." : "Há mais de um duelo: escolha pela aba X1.", ChatFormatting.GRAY); return; }
        }
        if (!ocupada(arena)) { avisar(p, "Não há duelo nessa arena agora.", ChatFormatting.GRAY); return; }
        JsonObject a = arenas(s).getAsJsonObject(arena);
        if (a == null || !a.has("esp")) { avisar(p, "Essa arena não tem arquibancada marcada.", ChatFormatting.GRAY); return; }
        String motivo = ocupado(p);
        if (motivo != null) { avisar(p, "Você não pode assistir agora: está " + motivo + ".", ChatFormatting.RED); return; }
        if (convites.containsKey(p.getUUID()) || conviteDe(p.getUUID()) != null) { avisar(p, "Resolva o seu desafio em aberto primeiro.", ChatFormatting.RED); return; }
        p.closeContainer();
        marcarVolta(p);
        plateia.put(p.getUUID(), arena);
        levar(p, nivelDa(s, a), ponto(a, "esp"));
        avisar(p, "Você está na arquibancada. No fim da luta você volta sozinho; para sair antes, /x1 sair.", ChatFormatting.WHITE);
    }

    private static void sairDaPlateia(ServerPlayer p) {
        if (plateia.remove(p.getUUID()) == null) { avisar(p, "Você não está assistindo nenhum duelo.", ChatFormatting.GRAY); return; }
        voltar(p);
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public void aoTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        long agora = System.currentTimeMillis();
        if (!fechar.isEmpty()) {
            for (UUID u : fechar) { ServerPlayer p = s.getPlayerList().getPlayer(u); if (p != null) p.closeContainer(); }
            fechar.clear();
        }
        if (!convites.isEmpty() && s.getTickCount() % 10 == 0)
            for (Convite c : new ArrayList<>(convites.values()))
                if (agora > c.expira) desfazer(s, c, "Seu desafio não foi respondido a tempo.", "O desafio expirou.");
        for (Duelo d : new ArrayList<>(duelos)) {
            ServerPlayer a = s.getPlayerList().getPlayer(d.a), b = s.getPlayerList().getPlayer(d.b);
            if (a == null || b == null) { encerrar(s, d, a == null ? (b == null ? null : d.b) : d.a, null, true, "o adversário saiu do jogo"); continue; }
            if (d.resolver) { d.resolver = false; fimDaRodada(s, d, d.vencedorDaRodada); continue; }
            JsonObject arena = arenas(s).has(d.arena) ? arenas(s).getAsJsonObject(d.arena) : null;
            if (arena == null || !pronta(arena)) { encerrar(s, d, null, null, true, "a arena foi removida"); continue; }
            ServerLevel n = nivelDa(s, arena);
            double[] s1 = ponto(arena, "s1"), s2 = ponto(arena, "s2");
            switch (d.fase) {
                case CONTAGEM -> {
                    // parados no ponto até o "Lute!"
                    if (a.level() != n || a.distanceToSqr(s1[0], s1[1], s1[2]) > 0.6) levar(a, n, s1);
                    if (b.level() != n || b.distanceToSqr(s2[0], s2[1], s2[2]) > 0.6) levar(b, n, s2);
                    int seg = (int) Math.ceil((d.ate - agora) / 1000.0);
                    if (seg != d.ultimoSeg && seg >= 1 && seg <= 3) {
                        d.ultimoSeg = seg;
                        for (ServerPlayer p : List.of(a, b)) {
                            titulo(p, String.valueOf(seg), d.r.md3() ? "Rodada " + d.rodada + " · " + d.nomeA + " " + d.vitA + " x " + d.vitB + " " + d.nomeB : d.nomeA + " contra " + d.nomeB, ChatFormatting.YELLOW);
                            p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 0.8f);
                        }
                    }
                    if (agora >= d.ate) {
                        d.fase = Fase.LUTA;
                        d.ate = agora + RODADA_MS;
                        if (d.r.vidaIgual()) igualarVida(d, a, b);
                        else for (ServerPlayer p : List.of(a, b)) p.setHealth(p.getMaxHealth());   // sem os anéis a vida máxima pode ter mudado
                        for (ServerPlayer p : List.of(a, b)) {
                            titulo(p, "Lute!", "", ChatFormatting.RED);
                            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5f, 1.3f);
                        }
                    }
                }
                case LUTA -> {
                    // ninguém sai da arena
                    double cx = (s1[0] + s2[0]) / 2, cy = (s1[1] + s2[1]) / 2, cz = (s1[2] + s2[2]) / 2;
                    int raio = arena.has("raio") ? arena.get("raio").getAsInt() : RAIO_PADRAO;
                    if (a.level() != n || a.distanceToSqr(cx, cy, cz) > raio * raio) { levar(a, n, s1); a.displayClientMessage(Component.literal("Você saiu da arena e foi levado de volta.").withStyle(ChatFormatting.RED), true); }
                    if (b.level() != n || b.distanceToSqr(cx, cy, cz) > raio * raio) { levar(b, n, s2); b.displayClientMessage(Component.literal("Você saiu da arena e foi levado de volta.").withStyle(ChatFormatting.RED), true); }
                    if (agora >= d.ate) {   // tempo esgotado: leva a rodada quem tem mais vida (em proporção)
                        float fa = a.getHealth() / a.getMaxHealth(), fb = b.getHealth() / b.getMaxHealth();
                        d.fase = Fase.INTERVALO;
                        fimDaRodada(s, d, Math.abs(fa - fb) < 0.01f ? null : fa > fb ? d.a : d.b);
                    } else if (s.getTickCount() % 20 == 0) {
                        long resta = (d.ate - agora) / 1000;
                        String tempo = (d.r.md3() ? "Rodada " + d.rodada + " · " : "") + String.format("%d:%02d", resta / 60, resta % 60);
                        a.displayClientMessage(Component.literal(tempo + " · você " + (int) Math.ceil(a.getHealth()) + " ❤ · " + d.nomeB + " " + (int) Math.ceil(b.getHealth()) + " ❤").withStyle(ChatFormatting.GOLD), true);
                        b.displayClientMessage(Component.literal(tempo + " · você " + (int) Math.ceil(b.getHealth()) + " ❤ · " + d.nomeA + " " + (int) Math.ceil(a.getHealth()) + " ❤").withStyle(ChatFormatting.GOLD), true);
                    }
                }
                case INTERVALO -> {
                    if (agora >= d.ate) prepararRodada(s, d, a, b);
                }
            }
        }
        if (s.getTickCount() % 1200 == 91) gravar(s);
    }

    /** Quem é o jogador por trás de um ataque: ele mesmo, ou o dono da invocação, da sombra ou do bicho domado. */
    private static UUID responsavel(Entity e) {
        if (e == null) return null;
        if (e instanceof Player) return e.getUUID();
        CompoundTag d = e.getPersistentData();
        if (d.hasUUID("frx_dono")) return d.getUUID("frx_dono");
        if (d.contains("frx_dono", Tag.TAG_STRING)) { try { return UUID.fromString(d.getString("frx_dono")); } catch (Exception ignorado) { } }
        if (e instanceof OwnableEntity o) return o.getOwnerUUID();
        return null;
    }

    /** No duelo só vale dano entre os dois (e o do próprio ambiente); fora da luta, nenhum. Quem luta também não fere a plateia. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void filtrarDano(LivingAttackEvent ev) {
        Entity fonte = ev.getSource().getEntity();
        UUID autor = responsavel(fonte);
        if (ev.getEntity() instanceof ServerPlayer vitima) {
            Duelo d = duelo(vitima.getUUID());
            if (d != null) {
                if (d.fase != Fase.LUTA || d.resolver) { ev.setCanceled(true); return; }
                if (fonte == null) return;   // queda, fogo, lava: vale
                if (autor == null || !d.tem(autor)) { ev.setCanceled(true); return; }   // monstro solto ou gente de fora
                if (!(fonte instanceof Player) && !d.r.hab()) ev.setCanceled(true);     // invocação num duelo sem habilidades
                return;
            }
            if (plateia.containsKey(vitima.getUUID()) && autor != null && duelo(autor) != null) { ev.setCanceled(true); return; }
        }
        if (autor != null && ev.getEntity() instanceof Player outro) {
            Duelo d = duelo(autor);
            if (d != null && !d.tem(outro.getUUID())) ev.setCanceled(true);
        }
    }

    /** O golpe que mataria não mata: a rodada acaba ali. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void golpeFatal(LivingDamageEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Duelo d = duelo(p.getUUID());
        if (d == null) return;
        if (d.fase != Fase.LUTA || d.resolver) { ev.setCanceled(true); return; }
        if (ev.getAmount() < p.getHealth()) return;
        ev.setCanceled(true);
        decidirRodada(d, p);
    }

    /** Rede de segurança: se algum dano escapar do filtro acima, a morte é cancelada do mesmo jeito. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void semMorte(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Duelo d = duelo(p.getUUID());
        if (d == null) return;
        ev.setCanceled(true);
        p.setHealth(1f);
        if (d.fase == Fase.LUTA && !d.resolver) decidirRodada(d, p);
    }

    private static void decidirRodada(Duelo d, ServerPlayer perdeu) {
        d.resolver = true;
        d.vencedorDaRodada = d.outro(perdeu.getUUID());
        perdeu.setHealth(perdeu.getMaxHealth());
        perdeu.clearFire();
    }

    // ------------------------------------------------------------------ travas durante o duelo (para a foto do inventário continuar valendo)

    @SubscribeEvent
    public void semJogarItem(ItemTossEvent ev) {
        if (!emDuelo(ev.getPlayer())) return;
        ev.getPlayer().getInventory().add(ev.getEntity().getItem());   // o item já saiu do inventário: volta
        ev.setCanceled(true);
        ev.getPlayer().inventoryMenu.sendAllDataToRemote();
    }

    @SubscribeEvent
    public void semPegarItem(EntityItemPickupEvent ev) { if (emDuelo(ev.getEntity())) ev.setCanceled(true); }

    @SubscribeEvent
    public void semAbrirMenu(PlayerContainerEvent.Open ev) { if (emDuelo(ev.getEntity())) fechar.add(ev.getEntity().getUUID()); }

    @SubscribeEvent
    public void semColocarBloco(BlockEvent.EntityPlaceEvent ev) { if (ev.getEntity() instanceof Player p && emDuelo(p)) ev.setCanceled(true); }

    @SubscribeEvent
    public void semQuebrarBloco(BlockEvent.BreakEvent ev) { if (emDuelo(ev.getPlayer())) ev.setCanceled(true); }

    @SubscribeEvent
    public void semBalde(FillBucketEvent ev) { if (emDuelo(ev.getEntity())) ev.setCanceled(true); }

    private static boolean pocaoProibida(Player p, ItemStack s) {
        if (!(s.getItem() instanceof PotionItem)) return false;
        Duelo d = duelo(p.getUUID());
        return d != null && !d.r.pocoes();
    }

    @SubscribeEvent
    public void semPocaoNaMao(PlayerInteractEvent.RightClickItem ev) {
        if (!pocaoProibida(ev.getEntity(), ev.getItemStack())) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.FAIL);
        if (ev.getEntity() instanceof ServerPlayer p) {
            p.displayClientMessage(Component.literal("Este duelo é sem poções.").withStyle(ChatFormatting.RED), true);
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    @SubscribeEvent
    public void semBeberPocao(LivingEntityUseItemEvent.Start ev) {
        if (ev.getEntity() instanceof Player p && pocaoProibida(p, ev.getItem())) ev.setCanceled(true);
    }

    /** Lutando só valem os comandos do próprio X1 (banco, casa, teleporte e o resto esperam o fim). Admin fica livre. */
    @SubscribeEvent
    public void semComandos(CommandEvent ev) {
        if (!(ev.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p) || !emDuelo(p) || p.hasPermissions(2)) return;
        String cmd = ev.getParseResults().getReader().getString().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.split(" ")[0].equalsIgnoreCase("x1")) return;
        ev.setCanceled(true);
        p.sendSystemMessage(Component.literal("Você está num duelo. Para sair antes do fim, /x1 desistir.").withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------ entrada, saída e desligamento

    /** Sobrou algo guardado (o servidor caiu ou o jogador saiu no meio): devolve inventário, aposta e lugar. */
    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag t = tagSeExiste(p);
        if (t == null) return;
        if (t.isEmpty()) { limpar(p); return; }
        boolean interrompido = t.contains("inv") || t.contains("moedas") || t.contains("item");
        restaurar(p);
        passarAposta(p, p);
        voltar(p);
        limpar(p);
        if (interrompido) avisar(p, "Seu X1 foi interrompido: itens, aposta e lugar foram devolvidos.", ChatFormatting.YELLOW);
    }

    /** Sair no meio do duelo é derrota. O inventário volta na hora (é salvo junto); o lugar, na próxima entrada. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer s = p.server;
        Duelo d = duelo(p.getUUID());
        if (d != null) encerrar(s, d, d.outro(p.getUUID()), p.getUUID(), true, (p.getUUID().equals(d.a) ? d.nomeA : d.nomeB) + " saiu do jogo");
        Convite recebido = convites.get(p.getUUID()), enviado = conviteDe(p.getUUID());
        if (recebido != null) desfazer(s, recebido, "Desafio cancelado: o jogador saiu do jogo.", null);
        if (enviado != null) desfazer(s, enviado, null, "Desafio cancelado: quem desafiou saiu do jogo.");
        plateia.remove(p.getUUID());
        fechar.remove(p.getUUID());
    }

    /** Servidor desligando: tudo volta para o dono antes de os jogadores serem salvos. */
    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) {
        MinecraftServer s = ev.getServer();
        for (Duelo d : new ArrayList<>(duelos)) encerrar(s, d, null, null, false, "");
        for (Convite c : new ArrayList<>(convites.values())) desfazer(s, c, null, null);
        plateia.clear();
        fechar.clear();
        gravar(s);
        dados = null;
    }

    // ------------------------------------------------------------------ tela (aba X1 do inventário)

    public static void acao(ServerPlayer p, AcaoMenu m) {
        MinecraftServer s = p.server;
        switch (m.acao()) {
            case PEDIR -> { }
            case DESAFIAR -> desafiar(p, porIdDeEntidade(s, m.npc()), Regras.de(m.valor()));
            case ACEITAR -> aceitar(p);
            case RECUSAR -> recusar(p);
            case CANCELAR -> cancelar(p);
            case ASSISTIR -> {
                List<String> prontas = prontas(s);
                if (m.npc() >= 1 && m.npc() <= prontas.size()) assistir(p, prontas.get(m.npc() - 1));
            }
            case SAIR -> sairDaPlateia(p);
            default -> { }
        }
        enviar(p, false);   // só atualiza a aba se ela estiver aberta
    }

    private static void recusar(ServerPlayer p) {
        Convite c = convites.get(p.getUUID());
        if (c == null) { avisar(p, "Você não tem desafio em aberto.", ChatFormatting.GRAY); return; }
        desfazer(p.server, c, Personagens.nome(p) + " recusou o seu desafio.", "Desafio recusado.");
    }

    private static void cancelar(ServerPlayer p) {
        Convite c = conviteDe(p.getUUID());
        if (c == null) { avisar(p, "Você não tem desafio enviado.", ChatFormatting.GRAY); return; }
        desfazer(p.server, c, "Desafio cancelado.", Personagens.nome(p) + " cancelou o desafio.");
    }

    /** Manda os dados da aba. abrir = abre a tela mesmo se ela estiver fechada (comando /x1). */
    private static void enviar(ServerPlayer p, boolean abrir) {
        MinecraftServer s = p.server;
        CompoundTag t = new CompoundTag();
        t.putBoolean("abrir", abrir);
        t.putBoolean("pronto", true);
        ListTag jogadores = new ListTag();
        for (ServerPlayer q : s.getPlayerList().getPlayers()) {
            if (q == p || !Progressao.temDados(q)) continue;
            CompoundTag j = new CompoundTag();
            j.putInt("id", q.getId());
            j.putString("nome", Personagens.nome(q));
            j.putInt("nivel", Progressao.dados(q).nivel());
            String motivo = ocupado(q);
            if (motivo == null && (convites.containsKey(q.getUUID()) || conviteDe(q.getUUID()) != null)) motivo = "com desafio em aberto";
            j.putString("estado", motivo == null ? "" : motivo);
            jogadores.add(j);
        }
        t.put("jogadores", jogadores);
        ListTag ars = new ListTag();
        for (String id : prontas(s)) {
            JsonObject a = arenas(s).getAsJsonObject(id);
            CompoundTag at = new CompoundTag();
            at.putString("nome", nomeArena(s, id));
            at.putBoolean("ocupada", ocupada(id));
            at.putBoolean("plateia", a.has("esp"));
            String luta = "";
            for (Duelo d : duelos) if (d.arena.equals(id)) luta = d.nomeA + " x " + d.nomeB;
            at.putString("luta", luta);
            ars.add(at);
        }
        t.put("arenas", ars);
        // placar: os 8 com mais vitórias
        List<JsonObject> todos = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : dados(s).getAsJsonObject("placar").entrySet()) todos.add(e.getValue().getAsJsonObject());
        todos.sort((x, y) -> y.get("v").getAsInt() != x.get("v").getAsInt() ? y.get("v").getAsInt() - x.get("v").getAsInt() : x.get("d").getAsInt() - y.get("d").getAsInt());
        ListTag placar = new ListTag();
        for (int i = 0; i < Math.min(8, todos.size()); i++) placar.add(linhaPlacar(todos.get(i)));
        t.put("placar", placar);
        JsonObject meu = dados(s).getAsJsonObject("placar").has(Personagens.id(p).toString()) ? dados(s).getAsJsonObject("placar").getAsJsonObject(Personagens.id(p).toString()) : null;
        if (meu != null) t.put("meu", linhaPlacar(meu));
        Convite recebido = convites.get(p.getUUID()), enviado = conviteDe(p.getUUID());
        if (recebido != null) {
            ServerPlayer de = s.getPlayerList().getPlayer(recebido.de);
            CompoundTag c = new CompoundTag();
            c.putString("de", de == null ? "?" : Personagens.nome(de));
            c.putString("regras", regrasEmTexto(s, recebido.r, recebido.arena));
            c.putString("aposta", de == null ? "" : textoAposta(de, recebido.r));
            c.putInt("seg", (int) Math.max(0, (recebido.expira - System.currentTimeMillis()) / 1000));
            t.put("recebido", c);
        }
        if (enviado != null) {
            ServerPlayer para = s.getPlayerList().getPlayer(enviado.para);
            CompoundTag c = new CompoundTag();
            c.putString("para", para == null ? "?" : Personagens.nome(para));
            c.putInt("seg", (int) Math.max(0, (enviado.expira - System.currentTimeMillis()) / 1000));
            t.put("enviado", c);
        }
        t.putBoolean("assistindo", plateia.containsKey(p.getUUID()));
        t.putLong("bolsa", Moedas.contar(p));
        ItemStack mao = p.getMainHandItem();
        t.putString("mao", mao.isEmpty() ? "" : mao.getCount() + "x " + mao.getHoverName().getString());
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("x1", t));
    }

    private static CompoundTag linhaPlacar(JsonObject o) {
        CompoundTag l = new CompoundTag();
        l.putString("nome", o.has("nome") ? o.get("nome").getAsString() : "?");
        l.putInt("v", o.get("v").getAsInt());
        l.putInt("d", o.get("d").getAsInt());
        l.putInt("e", o.has("e") ? o.get("e").getAsInt() : 0);
        l.putInt("seq", o.has("seq") ? o.get("seq").getAsInt() : 0);
        l.putInt("melhor", o.has("melhor") ? o.get("melhor").getAsInt() : 0);
        return l;
    }

    // ------------------------------------------------------------------ comandos

    private static int responder(CommandSourceStack src, String t) { src.sendSuccess(() -> Component.literal("[arena] " + t), true); return 1; }

    private static JsonObject arenaOuErro(CommandSourceStack src, String id) {
        JsonObject todas = arenas(src.getServer());
        if (!todas.has(id)) { src.sendFailure(Component.literal("Arena não existe. Crie com /frx arena criar " + id + " <nome>.")); return null; }
        return todas.getAsJsonObject(id);
    }

    private static JsonArray aqui(ServerPlayer p) {
        JsonArray a = new JsonArray();
        a.add(Math.floor(p.getX()) + 0.5); a.add(p.getY()); a.add(Math.floor(p.getZ()) + 0.5); a.add(p.getYRot()); a.add(0);
        return a;
    }

    private static int marcar(CommandSourceStack src, String id, String chave, String oQue) {
        ServerPlayer p;
        try { p = src.getPlayerOrException(); } catch (Exception e) { return 0; }
        JsonObject a = arenaOuErro(src, id);
        if (a == null) return 0;
        if (ocupada(id)) { src.sendFailure(Component.literal("Há um duelo acontecendo nessa arena. Espere acabar.")); return 0; }
        String dim = p.level().dimension().location().toString();
        if (a.has("dim") && !a.get("dim").getAsString().equals(dim) && (a.has("s1") || a.has("s2") || a.has("esp")) && !chave.equals("s1")) {
            src.sendFailure(Component.literal("Os pontos da arena precisam ficar no mesmo mundo do ponto 1."));
            return 0;
        }
        if (chave.equals("s1")) a.addProperty("dim", dim);
        a.add(chave, aqui(p));
        sujo = true; gravar(src.getServer());
        String falta = !a.has("s1") ? " Falta o ponto 1." : !a.has("s2") ? " Falta o ponto 2." : !a.has("esp") ? " Arena pronta; falta só a arquibancada (/frx arena espectador " + id + "), que é opcional." : " Arena completa.";
        return responder(src, oQue + " de " + id + " marcado onde você está, olhando para onde você olha." + falta);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("x1")
                .executes(c -> { enviar(c.getSource().getPlayerOrException(), true); return 1; })
                .then(Commands.literal("aceitar").executes(c -> { aceitar(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("recusar").executes(c -> { recusar(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("cancelar").executes(c -> { cancelar(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("desistir").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Duelo d = duelo(p.getUUID());
                    if (d == null) { avisar(p, "Você não está em duelo.", ChatFormatting.GRAY); return 0; }
                    encerrar(p.server, d, d.outro(p.getUUID()), null, true, (p.getUUID().equals(d.a) ? d.nomeA : d.nomeB) + " desistiu");
                    return 1;
                }))
                .then(Commands.literal("assistir")
                        .executes(c -> { assistir(c.getSource().getPlayerOrException(), ""); return 1; })
                        .then(Commands.argument("arena", StringArgumentType.word()).executes(c -> { assistir(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "arena")); return 1; })))
                .then(Commands.literal("sair").executes(c -> { sairDaPlateia(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("placar").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    List<JsonObject> todos = new ArrayList<>();
                    for (Map.Entry<String, JsonElement> e : dados(p.server).getAsJsonObject("placar").entrySet()) todos.add(e.getValue().getAsJsonObject());
                    todos.sort((x, y) -> y.get("v").getAsInt() - x.get("v").getAsInt());
                    p.sendSystemMessage(Component.literal("⚔ Placar do X1").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    if (todos.isEmpty()) p.sendSystemMessage(Component.literal("  Nenhum duelo ainda.").withStyle(ChatFormatting.GRAY));
                    for (int i = 0; i < Math.min(10, todos.size()); i++) {
                        JsonObject o = todos.get(i);
                        p.sendSystemMessage(Component.literal("  " + (i + 1) + "º " + o.get("nome").getAsString() + " · " + o.get("v").getAsInt() + " V · " + o.get("d").getAsInt() + " D · sequência "
                                + (o.has("seq") ? o.get("seq").getAsInt() : 0)).withStyle(i < 3 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
                    }
                    return 1;
                })));

        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2)).then(Commands.literal("arena")
                .then(Commands.literal("criar").then(Commands.argument("id", StringArgumentType.word())
                        .executes(c -> criar(c.getSource(), StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "id")))
                        .then(Commands.argument("nome", StringArgumentType.greedyString()).executes(c -> criar(c.getSource(), StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "nome"))))))
                .then(Commands.literal("spawn").then(Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(arenas(c.getSource().getServer()).keySet(), b))
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, 2)).executes(c -> {
                            int n = IntegerArgumentType.getInteger(c, "numero");
                            return marcar(c.getSource(), StringArgumentType.getString(c, "id"), n == 1 ? "s1" : "s2", "Ponto " + n);
                        }))))
                .then(Commands.literal("espectador").then(Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(arenas(c.getSource().getServer()).keySet(), b))
                        .executes(c -> marcar(c.getSource(), StringArgumentType.getString(c, "id"), "esp", "Arquibancada"))))
                .then(Commands.literal("raio").then(Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(arenas(c.getSource().getServer()).keySet(), b))
                        .then(Commands.argument("blocos", IntegerArgumentType.integer(8, 200)).executes(c -> {
                            JsonObject a = arenaOuErro(c.getSource(), StringArgumentType.getString(c, "id"));
                            if (a == null) return 0;
                            a.addProperty("raio", IntegerArgumentType.getInteger(c, "blocos"));
                            sujo = true; gravar(c.getSource().getServer());
                            return responder(c.getSource(), "Quem passar de " + IntegerArgumentType.getInteger(c, "blocos") + " blocos do meio da arena é levado de volta ao ponto dele.");
                        }))))
                .then(Commands.literal("ir").then(Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(arenas(c.getSource().getServer()).keySet(), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            JsonObject a = arenaOuErro(c.getSource(), StringArgumentType.getString(c, "id"));
                            if (a == null) return 0;
                            if (!a.has("s1")) { c.getSource().sendFailure(Component.literal("Essa arena ainda não tem o ponto 1.")); return 0; }
                            levar(p, nivelDa(c.getSource().getServer(), a), ponto(a, "s1"));
                            return 1;
                        })))
                .then(Commands.literal("remover").then(Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(arenas(c.getSource().getServer()).keySet(), b))
                        .executes(c -> {
                            String id = StringArgumentType.getString(c, "id");
                            if (ocupada(id)) { c.getSource().sendFailure(Component.literal("Há um duelo acontecendo nessa arena. Espere acabar.")); return 0; }
                            if (arenas(c.getSource().getServer()).remove(id) == null) { c.getSource().sendFailure(Component.literal("Arena não existe.")); return 0; }
                            sujo = true; gravar(c.getSource().getServer());
                            return responder(c.getSource(), "Arena " + id + " removida.");
                        })))
                .then(Commands.literal("lista").executes(c -> {
                    JsonObject todas = arenas(c.getSource().getServer());
                    if (todas.size() == 0) return responder(c.getSource(), "Nenhuma arena. Crie com /frx arena criar <id> <nome>.");
                    for (String id : new TreeSet<>(todas.keySet())) {
                        JsonObject a = todas.getAsJsonObject(id);
                        responder(c.getSource(), id + " (" + nomeArena(c.getSource().getServer(), id) + "): " + (pronta(a) ? "pronta" : "faltam pontos") + (a.has("esp") ? ", com arquibancada" : ", sem arquibancada")
                                + (ocupada(id) ? ", em uso" : ""));
                    }
                    return 1;
                }))));
    }

    private static int criar(CommandSourceStack src, String id, String nome) {
        JsonObject todas = arenas(src.getServer());
        if (todas.has(id)) { src.sendFailure(Component.literal("Já existe uma arena com esse id.")); return 0; }
        JsonObject a = new JsonObject();
        a.addProperty("nome", nome);
        todas.add(id, a);
        sujo = true; gravar(src.getServer());
        return responder(src, "Arena " + id + " criada. Agora marque os lugares, em pé em cada um: /frx arena spawn " + id + " 1, /frx arena spawn " + id + " 2 e /frx arena espectador " + id + ".");
    }
}
