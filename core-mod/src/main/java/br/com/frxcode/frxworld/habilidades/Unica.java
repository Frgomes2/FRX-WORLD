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
    private static final UUID MOD_TITAN = UUID.fromString("6f0e4c1a-51b7-4c0e-9a55-0b8f7d1c2a11"), MOD_SORTE = UUID.fromString("6f0e4c1a-51b7-4c0e-9a55-0b8f7d1c2a12");
    /** Momentos que não precisam sobreviver a um reinício: último dano recebido, vingança armada, escudo pronto de novo. */
    private static final Map<UUID, Long> FERIDO = new java.util.concurrent.ConcurrentHashMap<>(), VINGANCA = new java.util.concurrent.ConcurrentHashMap<>(),
            ESCUDO = new java.util.concurrent.ConcurrentHashMap<>();

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
        // 18/11: mais dez, para os jogadores não repetirem tanto
        def("titan", "Sangue de Titã", "Seu corpo aguenta mais: corações extras permanentes.");
        def("espinhos", "Pele de Espinhos", "Quem bate em você de perto leva parte do dano de volta.");
        def("vingador", "Olho por Olho", "Depois de apanhar, o seu próximo golpe sai muito mais forte.");
        def("sorte", "Estrela da Sorte", "A sorte anda com você: os baús e os monstros rendem mais.");
        def("gelo", "Toque Gélido", "Seus golpes têm chance de congelar o passo do inimigo.");
        def("chama", "Coração de Brasa", "Seus golpes têm chance de incendiar, e o fogo fere você menos.");
        def("berserk", "Sede de Batalha", "Cada monstro abatido deixa você mais rápido e mais forte por alguns segundos.");
        def("regen", "Sangue de Troll", "Fora de combate, suas feridas se fecham sozinhas.");
        def("eco", "Golpe em Eco", "Chance de o seu golpe acertar de novo, como um eco.");
        def("guardiao", "Escudo do Guardião", "De tempos em tempos, um escudo invisível anula por completo o próximo golpe que você receberia.");
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

    /** 1 a 5 pelos abates; 6 depois que vira Habilidade Suprema (um degrau a mais no efeito passivo). */
    public static int nivel(Player p) {
        int a = abates(p), n = 1;
        for (int i = 0; i < DEGRAUS.length; i++) if (a >= DEGRAUS[i]) n = i + 1;
        return n >= 5 && dados(p).getBoolean("suprema") ? 6 : n;
    }

    static CompoundTag dadosDe(Player p) { return dados(p); }

    static void tornarSuprema(ServerPlayer p) {
        dados(p).putBoolean("suprema", true);
        aplicarAtributos(p);
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
            case "titan" -> "+" + (2 + (n - 1)) + " corações";
            case "espinhos" -> (15 + 5 * (n - 1)) + "% do dano de perto volta no agressor";
            case "vingador" -> "+" + (30 + 10 * (n - 1)) + "% no golpe seguinte a um dano recebido (até 5 s)";
            case "sorte" -> "+" + n + " de sorte";
            case "gelo" -> (15 + 5 * (n - 1)) + "% de chance de deixar o alvo lento por 3 s";
            case "chama" -> (15 + 5 * (n - 1)) + "% de chance de incendiar, -" + (20 + 5 * (n - 1)) + "% de dano de fogo";
            case "berserk" -> "Velocidade e Força por " + (4 + (n - 1)) + " s a cada abate";
            case "regen" -> "cura " + (1 + (n - 1)) / 2.0 + " coração(ões) a cada 5 s fora de combate";
            case "eco" -> (8 + 3 * (n - 1)) + "% de chance de um segundo golpe (60% do dano)";
            case "guardiao" -> "anula um golpe a cada " + (30 - 3 * (n - 1)) + " s";
            default -> "";
        };
    }

    /** "Grande Sábio Nv 2" (ou "" se ainda não despertou). */
    public static String resumo(Player p) {
        Def d = def(p);
        if (d == null) return "";
        Suprema.Def s = Suprema.def(p);
        return s != null ? s.nome() + " (Suprema)" : d.nome() + " Nv " + nivel(p);
    }

    public static double bonusXp(Player p) { return v(p, "sabio", 0.08, 0.04); }

    private static void aplicarAtributos(ServerPlayer p) {
        AttributeInstance vel = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (vel == null) return;
        vel.removeModifier(MOD_VENTO);
        double b = v(p, "vento", 0.06, 0.03);
        if (b > 0) vel.addTransientModifier(new AttributeModifier(MOD_VENTO, "FRX habilidade unica", b, AttributeModifier.Operation.MULTIPLY_TOTAL));
        AttributeInstance vida = p.getAttribute(Attributes.MAX_HEALTH), sorte = p.getAttribute(Attributes.LUCK);
        if (vida != null) {
            vida.removeModifier(MOD_TITAN);
            double c = v(p, "titan", 4, 2);   // em pontos de vida: 2 corações, +1 por nível
            if (c > 0) vida.addTransientModifier(new AttributeModifier(MOD_TITAN, "FRX habilidade unica", c, AttributeModifier.Operation.ADDITION));
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }
        if (sorte != null) {
            sorte.removeModifier(MOD_SORTE);
            double c = v(p, "sorte", 1, 1);
            if (c > 0) sorte.addTransientModifier(new AttributeModifier(MOD_SORTE, "FRX habilidade unica", c, AttributeModifier.Operation.ADDITION));
        }
    }

    public static void definir(ServerPlayer p, String id, boolean anunciar) {
        Def d = TODAS.get(id);
        if (d == null) return;
        dados(p).putString("id", id);
        registrar(p, id);
        dados(p).putInt("abates", 0);
        dados(p).remove("suprema");
        aplicarAtributos(p);
        if (anunciar) {
            Sistema.avisar(p, "Habilidade Única despertada: " + d.nome(), d.descricao() + " Ela evolui conforme você luta. Veja em /unica.", Sistema.ROXO);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.8f);
        }
    }

    private static void sortear(ServerPlayer p) { definir(p, menosUsada(p), true); }

    // ------------------------------------------------------------------ quem tem o quê (para o sorteio espalhar as habilidades)

    /** personagem → habilidade, em <mundo>/frx_unicas.json. */
    private static com.google.gson.JsonObject registro;
    private static java.nio.file.Path arquivo(net.minecraft.server.MinecraftServer s) { return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("frx_unicas.json"); }

    private static com.google.gson.JsonObject registro(net.minecraft.server.MinecraftServer s) {
        if (registro == null) {
            try { registro = java.nio.file.Files.exists(arquivo(s)) ? com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(arquivo(s), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject() : new com.google.gson.JsonObject(); }
            catch (Exception e) { registro = new com.google.gson.JsonObject(); }
        }
        return registro;
    }

    private static void registrar(ServerPlayer p, String id) {
        com.google.gson.JsonObject r = registro(p.server);
        String quem = br.com.frxcode.frxworld.acesso.Personagens.id(p).toString();
        if (r.has(quem) && id.equals(r.get(quem).getAsString())) return;
        r.addProperty(quem, id);
        try { java.nio.file.Files.writeString(arquivo(p.server), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(r), java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { br.com.frxcode.frxworld.FrxWorld.LOG.warn("Unica: não gravei frx_unicas.json: {}", e.toString()); }
    }

    /** Sorteia entre as habilidades que menos personagens têm, para o servidor ficar variado. */
    private static String menosUsada(ServerPlayer p) {
        Map<String, Integer> conta = new LinkedHashMap<>();
        for (String id : TODAS.keySet()) conta.put(id, 0);
        String eu = br.com.frxcode.frxworld.acesso.Personagens.id(p).toString();
        for (var e : registro(p.server).entrySet()) {
            String id = e.getValue().getAsString();
            if (!e.getKey().equals(eu) && conta.containsKey(id)) conta.merge(id, 1, Integer::sum);
        }
        int menor = java.util.Collections.min(conta.values());
        List<String> ids = new ArrayList<>();
        for (var e : conta.entrySet()) if (e.getValue() == menor) ids.add(e.getKey());
        return ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
    }

    private static void mostrar(ServerPlayer p) {
        Def d = def(p);
        if (d == null) { p.sendSystemMessage(Component.literal("Você ainda não despertou uma Habilidade Única.").withStyle(ChatFormatting.GRAY)); return; }
        int n = nivel(p), a = abates(p);
        if (Suprema.tem(p)) { p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "suprema"); return; }
        p.sendSystemMessage(Component.literal("✦ " + d.nome() + " · Nível " + n + "/5").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("  " + d.descricao()).withStyle(ChatFormatting.WHITE));
        p.sendSystemMessage(Component.literal("  Agora: " + efeito(p)).withStyle(ChatFormatting.AQUA));
        p.sendSystemMessage(Component.literal(n >= 5 ? "  Nível máximo. A prova da Habilidade Suprema está aberta: /suprema" : "  Evolução: " + a + "/" + DEGRAUS[n] + " monstros abatidos").withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        if (def(p) != null) { registrar(p, id(p)); aplicarAtributos(p); return; }
        // quem ainda vai ver a abertura descobre a habilidade lá, não no chat
        boolean calado = !br.com.frxcode.frxworld.racas.RacasJogador.escolheu(p);
        definir(p, menosUsada(p), !calado);
    }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) aplicarAtributos(p);
    }

    @SubscribeEvent
    public void esquiva(LivingAttackEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.getSource().getEntity() == null || ev.getAmount() <= 0) return;
        if ("guardiao".equals(id(p)) && !ev.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            long agora = System.currentTimeMillis();
            if (ESCUDO.getOrDefault(p.getUUID(), 0L) <= agora) {
                ESCUDO.put(p.getUUID(), agora + (30 - 3 * (nivel(p) - 1)) * 1000L);
                ev.setCanceled(true);
                p.displayClientMessage(Component.literal("Escudo do Guardião: golpe anulado!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                p.level().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9f, 1.4f);
                return;
            }
        }
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
            double vinganca = v(a, "vingador", 0.30, 0.10);
            if (vinganca > 0 && VINGANCA.getOrDefault(a.getUUID(), 0L) > System.currentTimeMillis()) {
                VINGANCA.remove(a.getUUID());
                mult += vinganca;
                a.displayClientMessage(Component.literal("Olho por Olho!").withStyle(ChatFormatting.RED), true);
            }
            double eco = v(a, "eco", 0.08, 0.03);
            if (eco > 0 && a.getRandom().nextDouble() < eco) {
                mult += 0.6;
                a.level().playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.5f);
                a.displayClientMessage(Component.literal("Golpe em Eco!").withStyle(ChatFormatting.AQUA), true);
            }
            double gelo = v(a, "gelo", 0.15, 0.05);
            if (gelo > 0 && a.getRandom().nextDouble() < gelo) alvo.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            double chama = v(a, "chama", 0.15, 0.05);
            if (chama > 0 && a.getRandom().nextDouble() < chama) alvo.setSecondsOnFire(4);
            q = (float) (q * mult);
            double sangue = v(a, "sangue", 0.04, 0.02);
            if (sangue > 0) a.heal((float) (q * sangue));
        }
        if (ev.getEntity() instanceof ServerPlayer p) {
            double aco = v(p, "aco", 0.06, 0.03);
            if (aco > 0) q = (float) (q * (1 - aco));
            long agora = System.currentTimeMillis();
            FERIDO.put(p.getUUID(), agora);
            if ("vingador".equals(id(p))) VINGANCA.put(p.getUUID(), agora + 5000);
            double fogo = v(p, "chama", 0.20, 0.05);
            if (fogo > 0 && ev.getSource().is(DamageTypeTags.IS_FIRE)) q = (float) (q * (1 - fogo));
            double espinhos = v(p, "espinhos", 0.15, 0.05);
            if (espinhos > 0 && ev.getSource().getEntity() instanceof LivingEntity agressor && agressor != p && ev.getSource().getDirectEntity() == agressor
                    && !ev.getSource().is(net.minecraft.world.damagesource.DamageTypes.THORNS))
                agressor.hurt(p.damageSources().thorns(p), (float) (q * espinhos));
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
        if ("berserk".equals(id(p))) {
            int t = 20 * (4 + (nivel(p) - 1));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, t, 0, false, false));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, t, 0, false, false));
        }
        if (nivel(p) > antes) {
            aplicarAtributos(p);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
            Sistema.avisar(p, def(p).nome() + " evoluiu para o nível " + nivel(p), "Agora: " + efeito(p) + "."
                    + (nivel(p) == 5 ? " Ela pode se tornar uma Habilidade Suprema: use /suprema." : ""), Sistema.ROXO);
        }
    }

    /** Sangue de Troll: a cada 5 s sem levar dano há 6 s, fecha um pouco das feridas. */
    @SubscribeEvent
    public void aCadaTick(net.minecraftforge.event.TickEvent.PlayerTickEvent ev) {
        if (ev.phase != net.minecraftforge.event.TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 100 != 0 || !"regen".equals(id(p)) || !p.isAlive()) return;
        if (System.currentTimeMillis() - FERIDO.getOrDefault(p.getUUID(), 0L) < 6000 || p.getHealth() >= p.getMaxHealth()) return;
        p.heal((float) v(p, "regen", 1, 1));
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
