package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.progressao.Sistema;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Habilidade Única: cada jogador desperta UMA, sorteada no primeiro login, e ela é só dele.
 * É passiva e evolui com o uso: os monstros abatidos levam do nível 1 ao 5.
 */
public class Unica {
    private static final String CHAVE = "frx_unica";
    private static final int[] DEGRAUS = {0, 50, 200, 600, 1500};
    private static final UUID MOD_VENTO = UUID.fromString("6f0e4c1a-51b7-4c0e-9a55-0b8f7d1c2a10");

    public record Def(String id, String nome, String descricao) {}

    public static final Map<String, Def> TODAS = new LinkedHashMap<>();
    private static void def(String id, String nome, String desc) { TODAS.put(id, new Def(id, nome, desc)); }
    static {
        def("sabio", "Grande Sábio", "Você aprende mais rápido: ganha XP a mais em tudo.");
        def("devorador", "Devorador", "Cada monstro abatido devolve parte da sua vida.");
        def("furia", "Fúria do Dragão", "Quanto mais ferido, mais forte você bate.");
        def("aco", "Corpo de Aço", "Todo dano que você recebe é reduzido.");
        def("sombra", "Passo Sombrio", "Chance de desviar por completo de um ataque.");
        def("critico", "Olho do Falcão", "Chance de acerto crítico, com dano 50% maior.");
        def("sangue", "Sangue Real", "Parte do dano que você causa volta como vida.");
        def("vento", "Filho do Vento", "Você anda mais rápido; no nível 3, não sofre dano de queda.");
        def("imortal", "Segunda Chance", "Um golpe fatal deixa você vivo com 30% da vida (tem recarga).");
        def("cacador", "Instinto do Caçador", "Dano extra contra Elites, Campeões e chefes.");
    }

    public Unica() {}

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    public static String id(Player p) { return dados(p).getString("id"); }
    public static Def def(Player p) { return TODAS.get(id(p)); }
    public static int abates(Player p) { return dados(p).getInt("abates"); }

    public static int nivel(Player p) {
        int a = abates(p), n = 1;
        for (int i = 0; i < DEGRAUS.length; i++) if (a >= DEGRAUS[i]) n = i + 1;
        return n;
    }

    /** Valor do efeito se a habilidade do jogador for `qual` (senão 0): base no nível 1, mais `porNivel` a cada nível. */
    private static double v(Player p, String qual, double base, double porNivel) {
        return qual.equals(id(p)) ? base + porNivel * (nivel(p) - 1) : 0;
    }

    /** O que a habilidade faz no nível atual, em números. */
    public static String efeito(Player p) {
        int n = nivel(p);
        return switch (id(p)) {
            case "sabio" -> "+" + (8 + 4 * (n - 1)) + "% de XP";
            case "devorador" -> "cura " + (2 + (n - 1)) / 2.0 + " coração(ões) por abate";
            case "furia" -> "até +" + (15 + 5 * (n - 1)) + "% de dano quando ferido";
            case "aco" -> "-" + (6 + 3 * (n - 1)) + "% de dano recebido";
            case "sombra" -> (8 + 3 * (n - 1)) + "% de chance de esquiva";
            case "critico" -> (6 + 3 * (n - 1)) + "% de chance de crítico";
            case "sangue" -> "rouba " + (4 + 2 * (n - 1)) + "% do dano como vida";
            case "vento" -> "+" + (6 + 3 * (n - 1)) + "% de velocidade" + (n >= 3 ? ", sem dano de queda" : "");
            case "imortal" -> "recarga de " + (10 - (n - 1)) + " min";
            case "cacador" -> "+" + (10 + 5 * (n - 1)) + "% de dano em inimigos fortes";
            default -> "";
        };
    }

    /** "Grande Sábio Nv 2" (ou "" se ainda não despertou). */
    public static String resumo(Player p) {
        Def d = def(p);
        return d == null ? "" : d.nome() + " Nv " + nivel(p);
    }

    public static double bonusXp(Player p) { return v(p, "sabio", 0.08, 0.04); }

    private static void aplicarAtributos(ServerPlayer p) {
        AttributeInstance vel = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (vel == null) return;
        vel.removeModifier(MOD_VENTO);
        double b = v(p, "vento", 0.06, 0.03);
        if (b > 0) vel.addTransientModifier(new AttributeModifier(MOD_VENTO, "FRX habilidade unica", b, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    public static void definir(ServerPlayer p, String id, boolean anunciar) {
        Def d = TODAS.get(id);
        if (d == null) return;
        dados(p).putString("id", id);
        dados(p).putInt("abates", 0);
        aplicarAtributos(p);
        if (anunciar) {
            Sistema.avisar(p, "Habilidade Única despertada: " + d.nome(), d.descricao() + " Ela evolui conforme você luta. Veja em /unica.", Sistema.ROXO);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.8f);
        }
    }

    private static void sortear(ServerPlayer p) {
        List<String> ids = new ArrayList<>(TODAS.keySet());
        definir(p, ids.get(ThreadLocalRandom.current().nextInt(ids.size())), true);
    }

    private static void mostrar(ServerPlayer p) {
        Def d = def(p);
        if (d == null) { p.sendSystemMessage(Component.literal("Você ainda não despertou uma Habilidade Única.").withStyle(ChatFormatting.GRAY)); return; }
        int n = nivel(p), a = abates(p);
        p.sendSystemMessage(Component.literal("✦ " + d.nome() + " · Nível " + n + "/5").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("  " + d.descricao()).withStyle(ChatFormatting.WHITE));
        p.sendSystemMessage(Component.literal("  Agora: " + efeito(p)).withStyle(ChatFormatting.AQUA));
        p.sendSystemMessage(Component.literal(n >= 5 ? "  Nível máximo." : "  Evolução: " + a + "/" + DEGRAUS[n] + " monstros abatidos").withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        if (def(p) != null) { aplicarAtributos(p); return; }
        // quem ainda vai ver a abertura descobre a habilidade lá, não no chat
        boolean calado = !br.com.frxcode.frxworld.racas.RacasJogador.escolheu(p);
        List<String> ids = new ArrayList<>(TODAS.keySet());
        definir(p, ids.get(ThreadLocalRandom.current().nextInt(ids.size())), !calado);
    }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) aplicarAtributos(p);
    }

    @SubscribeEvent
    public void esquiva(LivingAttackEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.getSource().getEntity() == null || ev.getAmount() <= 0) return;
        double c = v(p, "sombra", 0.08, 0.03);
        if (c > 0 && p.getRandom().nextDouble() < c) {
            ev.setCanceled(true);
            p.displayClientMessage(Component.literal("Passo Sombrio: você desviou!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
    }

    @SubscribeEvent
    public void dano(LivingHurtEvent ev) {
        float q = ev.getAmount();
        if (ev.getSource().getEntity() instanceof ServerPlayer a && ev.getEntity() != a) {
            LivingEntity alvo = ev.getEntity();
            double mult = 1;
            double furia = v(a, "furia", 0.15, 0.05);
            if (furia > 0) mult += furia * (1 - a.getHealth() / Math.max(1f, a.getMaxHealth()));
            double cacador = v(a, "cacador", 0.10, 0.05);
            if (cacador > 0 && (alvo.hasCustomName() || alvo.getMaxHealth() >= 80)) mult += cacador;
            double crit = v(a, "critico", 0.06, 0.03);
            if (crit > 0 && a.getRandom().nextDouble() < crit) {
                mult *= 1.5;
                a.level().playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1.2f);
                a.displayClientMessage(Component.literal("Olho do Falcão: crítico!").withStyle(ChatFormatting.GOLD), true);
            }
            q = (float) (q * mult);
            double sangue = v(a, "sangue", 0.04, 0.02);
            if (sangue > 0) a.heal((float) (q * sangue));
        }
        if (ev.getEntity() instanceof ServerPlayer p) {
            double aco = v(p, "aco", 0.06, 0.03);
            if (aco > 0) q = (float) (q * (1 - aco));
        }
        if (q != ev.getAmount()) ev.setAmount(q);
    }

    @SubscribeEvent
    public void queda(LivingFallEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && "vento".equals(id(p)) && nivel(p) >= 3) ev.setCanceled(true);
    }

    /** Segunda Chance: antes de qualquer outro tratar a morte. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void golpeFatal(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !"imortal".equals(id(p))) return;
        if (ev.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        long agora = System.currentTimeMillis();
        if (dados(p).getLong("recarga") > agora) return;
        dados(p).putLong("recarga", agora + (10 - (nivel(p) - 1)) * 60_000L);
        ev.setCanceled(true);
        p.setHealth(Math.max(1f, p.getMaxHealth() * 0.3f));
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1f);
        Sistema.avisar(p, "Segunda Chance", "Você escapou da morte. A habilidade volta em " + (10 - (nivel(p) - 1)) + " minutos.", Sistema.ROXO);
    }

    @SubscribeEvent
    public void abate(LivingDeathEvent ev) {
        if (!(ev.getSource().getEntity() instanceof ServerPlayer p) || !(ev.getEntity() instanceof Enemy) || def(p) == null) return;
        int antes = nivel(p);
        dados(p).putInt("abates", abates(p) + 1);
        double cura = v(p, "devorador", 2, 1);
        if (cura > 0) p.heal((float) cura);
        if (nivel(p) > antes) {
            aplicarAtributos(p);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
            Sistema.avisar(p, def(p).nome() + " evoluiu para o nível " + nivel(p), "Agora: " + efeito(p) + ".", Sistema.ROXO);
        }
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("unica").executes(ctx -> { mostrar(ctx.getSource().getPlayerOrException()); return 1; }));
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("unica")
                        .then(Commands.literal("sortear").executes(ctx -> { sortear(ctx.getSource().getPlayerOrException()); return 1; }))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(TODAS.keySet(), b))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (!TODAS.containsKey(id)) { ctx.getSource().sendFailure(Component.literal("Habilidade desconhecida.")); return 0; }
                                    definir(ctx.getSource().getPlayerOrException(), id, true);
                                    return 1;
                                }))));
    }
}
