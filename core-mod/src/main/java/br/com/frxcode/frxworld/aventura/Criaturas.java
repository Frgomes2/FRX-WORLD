package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Códex de Criaturas: chefes e monstros cadastrados no painel (aba Códex de Criaturas).
 * Os chefes entram no sorteio de {@link Chefes}; os monstros tomam o lugar de um comum nas masmorras de portal,
 * na Torre e nas emboscadas do Nether. Skin e tamanho viajam pelo mesmo caminho das skins de NPC (papel "bicho_código").
 */
public class Criaturas {
    public static final String TAG = "frx_criatura", CHAVE = "frx_criatura";
    private static final UUID VIDA = UUID.fromString("c3a1f0d2-7b44-4e19-9a5c-2f6d8e1b4a01"), DANO = UUID.fromString("c3a1f0d2-7b44-4e19-9a5c-2f6d8e1b4a02"),
            VELOCIDADE = UUID.fromString("c3a1f0d2-7b44-4e19-9a5c-2f6d8e1b4a03");

    public record Def(String codigo, String nome, boolean chefe, String entidade, int rankMin, int rankMax, double vida, double dano, double velocidade,
                      List<String> golpes, String lacaio, Set<String> locais, int peso, int xp, int moedas, String dropItem, double dropChance, int dropQtd,
                      double escala, boolean brilho, boolean skin) { }

    private static volatile Map<String, Def> DEFS = Map.of();
    private int ticks;

    public Criaturas() {}

    // ------------------------------------------------------------------ painel

    public static void recarregar() {
        PainelApi.lerCriaturas().thenAccept(resp -> {
            Map<String, Def> novo = new LinkedHashMap<>();
            JsonArray arr = resp.has("criaturas") && resp.get("criaturas").isJsonArray() ? resp.getAsJsonArray("criaturas") : new JsonArray();
            for (JsonElement el : arr) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    List<String> golpes = new ArrayList<>();
                    for (JsonElement x : o.getAsJsonArray("golpes")) golpes.add(x.getAsString().toUpperCase(Locale.ROOT));
                    Set<String> locais = new HashSet<>();
                    for (JsonElement x : o.getAsJsonArray("locais")) locais.add(x.getAsString());
                    Def d = new Def(o.get("codigo").getAsString(), o.get("nome").getAsString(), "chefe".equals(o.get("tipo").getAsString()), o.get("entidade").getAsString(),
                            o.get("rankMin").getAsInt(), o.get("rankMax").getAsInt(), o.get("vida").getAsDouble(), o.get("dano").getAsDouble(), o.get("velocidade").getAsDouble(),
                            golpes, o.get("lacaio").getAsString(), locais, Math.max(1, o.get("peso").getAsInt()), o.get("xp").getAsInt(), o.get("moedas").getAsInt(),
                            o.get("dropItem").getAsString(), o.get("dropChance").getAsDouble(), Math.max(1, o.get("dropQtd").getAsInt()),
                            o.get("escala").getAsDouble(), o.get("brilho").getAsBoolean(), o.get("skin").getAsBoolean());
                    if (d.codigo().length() > 30) continue;   // o papel "bicho_<código>" precisa caber no pacote de marcação
                    novo.put(d.codigo(), d);
                } catch (Exception e) { FrxWorld.LOG.warn("Criaturas: cadastro inválido vindo do painel: {}", e.toString()); }
            }
            DEFS = novo;
        }).exceptionally(e -> null);
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ++ticks % 1200 == 100) recarregar();
    }

    public static Def def(String codigo) { return DEFS.get(codigo); }

    private static boolean existe(String entidade) {
        ResourceLocation rl = ResourceLocation.tryParse(entidade);
        return rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl);
    }

    private static boolean serve(Def d, boolean chefe, String local, int rank) {
        return d.chefe() == chefe && d.locais().contains(local) && rank >= d.rankMin() && rank <= d.rankMax() && existe(d.entidade());
    }

    /** Sorteia (pelo peso) um chefe do Códex para o lugar e o rank; null se não houver nenhum. */
    public static Def sortearChefe(String local, int rank) {
        List<Def> opcoes = new ArrayList<>();
        int total = 0;
        for (Def d : DEFS.values()) if (serve(d, true, local, rank)) { opcoes.add(d); total += d.peso(); }
        if (opcoes.isEmpty()) return null;
        int sorte = ThreadLocalRandom.current().nextInt(total);
        for (Def d : opcoes) { sorte -= d.peso(); if (sorte < 0) return d; }
        return opcoes.get(0);
    }

    /** Para cada monstro comum que o lugar vai criar: um monstro do Códex pode tomar a vaga (chance de cada um); null = fica o comum. */
    public static Def sortearMonstro(String local, int rank) {
        List<Def> opcoes = new ArrayList<>();
        for (Def d : DEFS.values()) if (serve(d, false, local, rank)) opcoes.add(d);
        if (opcoes.isEmpty()) return null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        java.util.Collections.shuffle(opcoes, rnd);
        for (Def d : opcoes) if (rnd.nextInt(100) < d.peso()) return d;
        return null;
    }

    // ------------------------------------------------------------------ no mundo

    private static void multiplicar(Mob m, Attribute atributo, UUID id, double vezes) {
        var a = m.getAttribute(atributo);
        if (a == null || Math.abs(vezes - 1) < 1e-6) return;
        a.removeModifier(id);
        a.addPermanentModifier(new AttributeModifier(id, "FRX criatura", vezes - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Marca o mob como criatura do Códex e aplica força, nome e brilho. Chamar antes de colocá-lo no mundo. */
    public static void aplicar(Mob m, Def d) {
        m.addTag(TAG);
        m.getPersistentData().putString(CHAVE, d.codigo());
        multiplicar(m, Attributes.MAX_HEALTH, VIDA, d.vida());
        multiplicar(m, Attributes.ATTACK_DAMAGE, DANO, d.dano());
        multiplicar(m, Attributes.MOVEMENT_SPEED, VELOCIDADE, d.velocidade());
        m.setHealth(m.getMaxHealth());
        if (d.brilho()) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        if (!d.chefe()) m.setCustomName(Component.literal(d.nome()).withStyle(ChatFormatting.RED));
    }

    /** Cria um monstro do Códex (não chefe) já com o nível do lugar. */
    public static Mob criarMonstro(ServerLevel n, Vec3 pos, Def d, int nivel) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(d.entidade()));
        Entity e = tipo == null ? null : tipo.create(n);
        if (!(e instanceof Mob m)) return null;
        m.moveTo(pos.x, pos.y, pos.z, ThreadLocalRandom.current().nextFloat() * 360f, 0);
        m.finalizeSpawn(n, n.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        Zonas.aplicarNivel(m, Math.max(1, nivel));
        aplicar(m, d);
        n.addFreshEntity(m);
        return m;
    }

    /** Papel e "reino" que o cliente recebe para desenhar a criatura: skin "papel:bicho_código" e o tamanho no segundo campo ("x1.5"). */
    public static String[] marca(Entity e) {
        Def d = DEFS.get(e.getPersistentData().getString(CHAVE));
        if (d == null || !d.skin()) return null;
        return new String[]{"bicho_" + d.codigo(), "x" + Math.max(0.4, Math.min(3, d.escala()))};
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof Mob m) || !(m.level() instanceof ServerLevel n) || !m.getTags().contains(TAG)) return;
        Def d = DEFS.get(m.getPersistentData().getString(CHAVE));
        if (d == null) return;
        List<ServerPlayer> gente = new ArrayList<>();
        for (ServerPlayer p : n.players()) if (p.isAlive() && !p.isSpectator() && p.distanceTo(m) <= 32) gente.add(p);
        ServerPlayer matador = ev.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        if (matador == null && !gente.isEmpty()) {
            matador = gente.get(0);
            for (ServerPlayer p : gente) if (p.distanceTo(m) < matador.distanceTo(m)) matador = p;
        }
        if (matador != null && !gente.contains(matador)) gente.add(matador);
        if (gente.isEmpty()) return;
        for (ServerPlayer p : gente) {
            if (d.xp() > 0) Progressao.ganharXp(p, d.xp());
            if (d.moedas() > 0) Moedas.dar(p, d.moedas());
            if (d.chefe() && (d.xp() > 0 || d.moedas() > 0))
                Sistema.avisar(p, d.nome() + " caiu", "Recompensa: " + (d.xp() > 0 ? "+" + d.xp() + " XP" : "") + (d.xp() > 0 && d.moedas() > 0 ? " e " : "") + (d.moedas() > 0 ? Moedas.texto(d.moedas()) : "") + ".", Sistema.OURO);
        }
        if (matador != null && !d.dropItem().isEmpty() && ThreadLocalRandom.current().nextDouble(100) < d.dropChance()) {
            if (d.dropItem().startsWith("codex:")) {
                br.com.frxcode.frxworld.armas.Codex.darPorCodigo(matador, d.dropItem().substring(6));
            } else {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(d.dropItem()));
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    ItemStack s = new ItemStack(item, Math.min(d.dropQtd(), 64));
                    Component nome = s.getHoverName();
                    if (!matador.getInventory().add(s)) matador.drop(s, false);
                    Sistema.avisar(matador, "Espólio raro", d.nome() + " deixou " + d.dropQtd() + "× " + nome.getString() + " para você.", Sistema.ROXO);
                }
            }
        }
        PainelApi.enviarDerrotaCriatura(d.codigo());
    }

    // ------------------------------------------------------------------ comandos de admin (o painel usa "invocar" e "recarregar")

    @SubscribeEvent
    public void aoRegistrarComandos(RegisterCommandsEvent ev) {
        com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.CommandSourceStack> codigos = (c, b) -> SharedSuggestionProvider.suggest(DEFS.keySet(), b);
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("criaturas")
                        .then(Commands.literal("recarregar").executes(c -> { recarregar(); c.getSource().sendSuccess(() -> Component.literal("Criaturas: lendo do painel."), true); return 1; }))
                        .then(Commands.literal("lista").executes(c -> {
                            if (DEFS.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Nenhuma criatura no Códex (crie no painel)."), false);
                            for (Def d : DEFS.values())
                                c.getSource().sendSuccess(() -> Component.literal(d.codigo() + " — " + d.nome() + (d.chefe() ? " (chefe)" : " (monstro)") + " · " + d.entidade()), false);
                            return 1;
                        }))
                        .then(Commands.literal("invocar").then(Commands.argument("codigo", StringArgumentType.word()).suggests(codigos)
                                .then(Commands.argument("jogador", EntityArgument.player()).executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "jogador");
                                    Def d = DEFS.get(StringArgumentType.getString(c, "codigo"));
                                    if (d == null) { c.getSource().sendFailure(Component.literal("Criatura desconhecida (ou inativa).")); return 0; }
                                    ServerLevel n = p.serverLevel();
                                    Vec3 olhar = p.getLookAngle().multiply(1, 0, 1);
                                    olhar = olhar.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : olhar.normalize();
                                    Vec3 pos = p.position().add(olhar.scale(4));
                                    int nivel = Math.max(1, Zonas.nivel(n, net.minecraft.core.BlockPos.containing(pos)));
                                    Mob m = d.chefe() ? Chefes.criarDe(n, pos, d, Guilda.indice(p), nivel, 0) : criarMonstro(n, pos, d, nivel);
                                    if (m == null) { c.getSource().sendFailure(Component.literal("Não consegui criar " + d.entidade() + ".")); return 0; }
                                    m.setTarget(p);
                                    c.getSource().sendSuccess(() -> Component.literal("[criaturas] " + d.nome() + " invocado perto de " + p.getGameProfile().getName() + " (nível " + nivel + ")"), true);
                                    return 1;
                                }))))));
    }
}
