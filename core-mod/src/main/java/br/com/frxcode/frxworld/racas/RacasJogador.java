package br.com.frxcode.frxworld.racas;

import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.racas.DefRacas.Raca;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.SyncRaca;
import br.com.frxcode.frxworld.rede.SyncRacaVisivel;
import br.com.frxcode.frxworld.reinos.Campanhas;
import br.com.frxcode.frxworld.reinos.Reputacao;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Raças dos jogadores: principal (100%) + secundária (35–60%, pela compatibilidade) ou puro-sangue (130%).
 * Comuns na criação · especiais no nível 20 ou com honra num reino · lendárias por feitos · Deus só por evento/admin.
 * Dados no NBT persistente do jogador (frx_raca). Trocar de raça não mexe em conquistas, habilidades nem progresso.
 */
public class RacasJogador {
    private static final String CHAVE = "frx_raca";
    public static final int NIVEL_ESPECIAL = 20, REP_ESPECIAL = 500, NIVEL_VER_LENDARIA = 50;

    private static final Map<UUID, Map<String, Double>> NUM = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> EFE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COMBATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SOPRO = new ConcurrentHashMap<>();
    private static final Map<String, Long> RECONHECIDO = new ConcurrentHashMap<>();

    public RacasJogador() {}

    // ------------------------------------------------------------------ dados

    private static CompoundTag dados(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        return pers.getCompound(CHAVE);
    }

    public static String principal(Player p) { return dados(p).getString("principal"); }
    public static String secundaria(Player p) { return dados(p).getString("secundaria"); }
    public static boolean escolheu(Player p) { return dados(p).getBoolean("escolheu"); }

    private static Set<String> lista(CompoundTag d, String k) {
        Set<String> s = new HashSet<>();
        for (Tag t : d.getList(k, Tag.TAG_STRING)) s.add(t.getAsString());
        return s;
    }

    private static void guardarLista(CompoundTag d, String k, Set<String> s) {
        ListTag l = new ListTag();
        for (String x : s) l.add(StringTag.valueOf(x));
        d.put(k, l);
    }

    /** Número já somado da combinação do jogador (0 se não tiver). Usado por XP, reputação, recurso etc. */
    public static double num(Player p, String chave) {
        Map<String, Double> m = NUM.get(p.getUUID());
        return m == null ? 0 : m.getOrDefault(chave, 0.0);
    }

    public static boolean tem(Player p, String efeito) {
        Set<String> s = EFE.get(p.getUUID());
        return s != null && s.contains(efeito);
    }

    // ------------------------------------------------------------------ liberação

    private static boolean campanhaConcluida(ServerPlayer p, String reino) {
        Campanhas.Campanha c = Campanhas.TODAS.get(reino);
        return c != null && Progressao.dados(p).campanhaEtapa().getOrDefault(reino, -1) >= c.etapas().size();
    }

    public static boolean liberada(ServerPlayer p, String id) {
        Raca r = DefRacas.de(id);
        if (r == null) return false;
        if (lista(dados(p), "liberadas").contains(id)) return true;
        if (!Progressao.temDados(p)) return r.nivel() == DefRacas.COMUM;
        DadosJogador d = Progressao.dados(p);
        return switch (r.nivel()) {
            case DefRacas.COMUM -> true;
            case DefRacas.ESPECIAL -> d.nivel() >= NIVEL_ESPECIAL || (!r.reinoMissao().isEmpty() && Reputacao.pontos(p, r.reinoMissao()) >= REP_ESPECIAL);
            case DefRacas.LENDARIA -> switch (id) {
                case "anjo" -> campanhaConcluida(p, "lubelius");
                case "rei_demonio" -> campanhaConcluida(p, "demonios");
                case "dragao" -> d.contadores().getOrDefault("c:dragoes", 0) > 0 || d.contadores().getOrDefault("c:dragao_end", 0) > 0;
                case "celestial" -> d.nivel() >= 100;
                default -> false;
            };
            default -> false;   // Deus: só pela lista (evento/admin)
        };
    }

    /** Custo (cobre) da próxima troca: escolha inicial e primeira troca grátis, depois sobe. */
    public static long custo(Player p) {
        CompoundTag d = dados(p);
        if (!d.getBoolean("escolheu")) return 0;
        int t = d.getInt("trocas");
        return switch (t) { case 0 -> 0; case 1 -> 1_000; case 2 -> 5_000; default -> 20_000L * (t - 2); };
    }

    // ------------------------------------------------------------------ escolher

    public static void escolher(ServerPlayer p, String principal, String secundaria, boolean admin) {
        if (secundaria == null) secundaria = "";
        Raca rp = DefRacas.de(principal), rs = secundaria.isEmpty() ? null : DefRacas.de(secundaria);
        if (rp == null || (!secundaria.isEmpty() && rs == null)) { avisar(p, "Raça desconhecida.", ChatFormatting.RED); return; }
        if (!admin) {
            if (!liberada(p, principal)) { avisar(p, rp.nome() + " ainda está bloqueada: " + rp.liberar() + ".", ChatFormatting.RED); return; }
            if (rs != null && !liberada(p, secundaria)) { avisar(p, rs.nome() + " ainda está bloqueada: " + rs.liberar() + ".", ChatFormatting.RED); return; }
        }
        DefRacas.Compat c = DefRacas.compat(principal, secundaria);
        if (c == DefRacas.Compat.PROIBIDA) { avisar(p, "Essa combinação não é possível (" + rp.nome() + " + " + (rs == null ? "" : rs.nome()) + ").", ChatFormatting.RED); return; }
        CompoundTag d = dados(p);
        if (principal.equals(d.getString("principal")) && secundaria.equals(d.getString("secundaria")) && d.getBoolean("escolheu")) {
            sincronizar(p, false);
            return;
        }
        long custo = admin ? 0 : custo(p);
        if (custo > 0) {
            boolean pago = Moedas.cobrar(p, custo);
            if (!pago && Banco.saldo(p) >= custo) { Banco.definirSaldo(p, Banco.saldo(p) - custo); pago = true; }
            if (!pago) { avisar(p, "A troca de raça custa " + Moedas.texto(custo) + " (bolsa ou banco).", ChatFormatting.RED); sincronizar(p, false); return; }
        }
        boolean primeira = !d.getBoolean("escolheu");
        if (!primeira && !admin) d.putInt("trocas", d.getInt("trocas") + 1);
        d.putBoolean("escolheu", true);
        d.putString("principal", principal);
        d.putString("secundaria", secundaria);
        aplicar(p);
        String nome = rp.nome() + (rs != null && !secundaria.equals(principal) ? " / " + rs.nome() : "") + (secundaria.equals(principal) ? " (puro-sangue)" : "");
        p.sendSystemMessage(Component.literal("✦ Sua raça agora é ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(nome).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(rs != null && !secundaria.equals(principal) ? "  · compatibilidade " + c.nome : "").withStyle(ChatFormatting.GRAY)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1f);
        sincronizar(p, false);
    }

    public static void alternarRevelar(ServerPlayer p) {
        Raca r = DefRacas.de(principal(p));
        if (r == null || !r.lendaria()) return;
        CompoundTag d = dados(p);
        d.putBoolean("revelar", !d.getBoolean("revelar"));
        avisar(p, d.getBoolean("revelar") ? "Você revelou sua verdadeira natureza: " + r.nome() + "." : "Você voltou a se disfarçar.", ChatFormatting.LIGHT_PURPLE);
        aplicar(p);
        sincronizar(p, false);
    }

    // ------------------------------------------------------------------ aplicar atributos

    private static final Object[][] ATRIBUTOS = {
            {"vida", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADDITION, 1.0},
            {"velocidade", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, 0.01},
            {"dano", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.MULTIPLY_TOTAL, 0.01},
            {"armadura", Attributes.ARMOR, AttributeModifier.Operation.ADDITION, 1.0},
            {"resistencia", Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION, 1.0},
            {"sorte", Attributes.LUCK, AttributeModifier.Operation.ADDITION, 1.0},
            {"empurrao", Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADDITION, 1.0},
    };

    private static UUID idMod(String k) { return UUID.nameUUIDFromBytes(("frxworld:raca:" + k).getBytes()); }

    private static void modificar(ServerPlayer p, String chave, Attribute a, AttributeModifier.Operation op, double valor) {
        if (a == null) return;
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        inst.removeModifier(idMod(chave));
        if (Math.abs(valor) > 1e-6) inst.addTransientModifier(new AttributeModifier(idMod(chave), "FRX raça " + chave, valor, op));
    }

    public static void aplicar(ServerPlayer p) {
        String pr = principal(p), se = secundaria(p);
        Map<String, Double> t = escolheu(p) ? DefRacas.somar(pr, se) : Map.of();
        Set<String> e = escolheu(p) ? DefRacas.efeitos(pr, se) : Set.of();
        NUM.put(p.getUUID(), t);
        EFE.put(p.getUUID(), e);
        for (Object[] a : ATRIBUTOS)
            modificar(p, (String) a[0], (Attribute) a[1], (AttributeModifier.Operation) a[2], t.getOrDefault((String) a[0], 0.0) * (double) a[3]);
        double alcance = t.getOrDefault("alcance", 0.0);
        modificar(p, "alcance_e", ForgeMod.ENTITY_REACH.get(), AttributeModifier.Operation.ADDITION, alcance);
        modificar(p, "alcance_b", ForgeMod.BLOCK_REACH.get(), AttributeModifier.Operation.ADDITION, alcance);
        // com o Apothic Attributes: esquiva, roubo de vida, cura recebida, mineração e dano com arco viram atributos reais
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.DODGE, "raca_esquiva", t.getOrDefault("esquiva", 0.0) / 100);
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.LIFE_STEAL, "raca_roubo", t.getOrDefault("rouboVida", 0.0) / 100);
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.HEALING, "raca_cura", t.getOrDefault("curaRecebida", 0.0) / 100);
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.MINING, "raca_mineracao", t.getOrDefault("mineracao", 0.0) / 100);
        br.com.frxcode.frxworld.atributos.Apoth.definir(p, br.com.frxcode.frxworld.atributos.Apoth.ARROW_DAMAGE, "raca_arco", t.getOrDefault("danoArco", 0.0) / 100);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        p.refreshDisplayName();
        p.refreshTabListName();
        Rede.CANAL.send(PacketDistributor.ALL.noArg(), visivel(p));
    }

    // ------------------------------------------------------------------ aparência

    private static boolean disfarcada(Player p) {
        Raca r = DefRacas.de(principal(p));
        return r != null && r.lendaria() && !dados(p).getBoolean("revelar");
    }

    /** "Elfo/Oni"; lendária disfarçada mostra só a secundária (ou Humano). */
    public static String textoVisivel(Player p) {
        if (!escolheu(p)) return "";
        String pr = principal(p), se = secundaria(p);
        if (disfarcada(p)) return se.isEmpty() || se.equals(pr) ? "Humano" : DefRacas.nome(se);
        if (se.isEmpty()) return DefRacas.nome(pr);
        if (se.equals(pr)) return DefRacas.nome(pr) + " puro";
        return DefRacas.nome(pr) + "/" + DefRacas.nome(se);
    }

    private static SyncRacaVisivel visivel(Player p) {
        if (!escolheu(p)) return new SyncRacaVisivel(p.getUUID(), "", "", "", "");
        String pr = principal(p), se = secundaria(p);
        Raca r = DefRacas.de(pr);
        String vp = pr, vs = se.equals(pr) ? "" : se;
        if (disfarcada(p)) { vp = se.isEmpty() || se.equals(pr) ? "humano" : se; vs = ""; }
        return new SyncRacaVisivel(p.getUUID(), textoVisivel(p), vp, vs, r != null && r.lendaria() ? pr : "");
    }

    public static void sincronizar(ServerPlayer p, boolean abrir) {
        CompoundTag d = dados(p);
        List<String> lib = new ArrayList<>();
        for (String id : DefRacas.TODAS.keySet()) if (liberada(p, id)) lib.add(id);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new SyncRaca(d.getString("principal"), d.getString("secundaria"),
                d.getBoolean("escolheu"), d.getInt("trocas"), custo(p), d.getBoolean("revelar"), lib, abrir));
    }

    private static void avisar(ServerPlayer p, String t, ChatFormatting cor) {
        p.displayClientMessage(Component.literal(t).withStyle(cor), true);
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        aplicar(p);
        sincronizar(p, false);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p) Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), visivel(o));
        if (!escolheu(p))
            p.sendSystemMessage(Component.literal("✦ Você ainda não escolheu sua raça. ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("[Escolher raça]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/raca"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Abre a tela de raças"))))));
    }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) aplicar(p);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        UUID id = ev.getEntity().getUUID();
        NUM.remove(id); EFE.remove(id); COMBATE.remove(id); SOPRO.remove(id);
    }

    /** Raça depois do nome no chat. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void nome(PlayerEvent.NameFormat ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        String t = textoVisivel(p);
        if (!t.isEmpty()) ev.setDisplayname(ev.getDisplayname().copy().append(Component.literal(" · " + t).withStyle(ChatFormatting.GRAY)));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void nomeTab(PlayerEvent.TabListNameFormat ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        String t = textoVisivel(p);
        if (t.isEmpty()) return;
        Component base = ev.getDisplayName() != null ? ev.getDisplayName() : Component.literal(p.getGameProfile().getName());
        ev.setDisplayName(base.copy().append(Component.literal(" · " + t).withStyle(ChatFormatting.GRAY)));
    }

    private static boolean aliado(Entity e) {
        return e instanceof Player || e instanceof AbstractVillager || (e instanceof TamableAnimal t && t.isTame()) || e.getTags().contains("frx_invocado");
    }

    @SubscribeEvent
    public void aoFerir(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide()) return;
        float dano = ev.getAmount();
        long agora = System.currentTimeMillis();

        // quem bateu
        if (ev.getSource().getEntity() instanceof ServerPlayer p && p != vitima) {
            COMBATE.put(p.getUUID(), agora);
            Entity direto = ev.getSource().getDirectEntity();
            boolean apoth = br.com.frxcode.frxworld.atributos.Apoth.ativo();
            if (direto instanceof AbstractArrow && !apoth) dano *= 1 + num(p, "danoArco") / 100f;
            if (ev.getSource().is(DamageTypes.INDIRECT_MAGIC) || ev.getSource().is(DamageTypes.MAGIC)) dano *= 1 + num(p, "danoMagico") / 100f;
            double roubo = num(p, "rouboVida");
            if (roubo > 0 && !apoth && !aliado(vitima)) p.heal((float) (dano * roubo / 100));
            if (direto == p && tem(p, DefRacas.SOPRO) && agora - SOPRO.getOrDefault(p.getUUID(), 0L) > 30_000) {
                SOPRO.put(p.getUUID(), agora);
                sopro(p);
            }
        }

        // quem apanhou
        if (vitima instanceof ServerPlayer p) {
            COMBATE.put(p.getUUID(), agora);
            if (ev.getSource().is(DamageTypeTags.IS_FALL) && tem(p, DefRacas.SEM_QUEDA)) { ev.setCanceled(true); return; }
            double fogo = num(p, "fogo");
            if (fogo > 0 && ev.getSource().is(DamageTypeTags.IS_FIRE)) {
                if (fogo >= 100) { ev.setCanceled(true); p.clearFire(); return; }
                dano *= (float) (1 - fogo / 100);
            }
            if (ev.getSource().is(DamageTypes.INDIRECT_MAGIC) || ev.getSource().is(DamageTypes.MAGIC)) dano *= 1 + num(p, "danoMagicoRecebido") / 100f;
            double esq = num(p, "esquiva");
            if (esq > 0 && !br.com.frxcode.frxworld.atributos.Apoth.ativo() && ev.getSource().getEntity() instanceof LivingEntity && !ev.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                    && ThreadLocalRandom.current().nextDouble() * 100 < esq) {
                ev.setCanceled(true);
                p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 5, 0.3, 0.4, 0.3, 0.01);
                p.displayClientMessage(Component.literal("Esquivou!").withStyle(ChatFormatting.GREEN), true);
                return;
            }
        }
        ev.setAmount(dano);
    }

    /** Dragão: cone de fogo à frente (a cada 30 s, no golpe). */
    private static void sopro(ServerPlayer p) {
        ServerLevel nv = p.serverLevel();
        Vec3 olhar = p.getLookAngle();
        Vec3 olho = p.getEyePosition();
        int lvl = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
        for (LivingEntity e : nv.getEntitiesOfClass(LivingEntity.class, new AABB(olho, olho).inflate(5), x -> x != p && x.isAlive() && !aliado(x))) {
            Vec3 para = e.getBoundingBox().getCenter().subtract(olho);
            if (para.length() > 5 || para.normalize().dot(olhar) < 0.6) continue;
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().indirectMagic(p, p), (float) (4 + lvl * 0.15));
            e.setSecondsOnFire(4);
        }
        for (int i = 1; i <= 10; i++) {
            Vec3 pt = olho.add(olhar.scale(i * 0.5));
            nv.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 4, 0.1 * i / 3, 0.1 * i / 3, 0.1 * i / 3, 0.02);
        }
        nv.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 0.7f);
    }

    @SubscribeEvent
    public void aoCurar(LivingHealEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && !br.com.frxcode.frxworld.atributos.Apoth.ativo()) {
            double c = num(p, "curaRecebida");
            if (c != 0) ev.setAmount((float) Math.max(0, ev.getAmount() * (1 + c / 100)));
        }
    }

    @SubscribeEvent
    public void minerar(PlayerEvent.BreakSpeed ev) {
        if (br.com.frxcode.frxworld.atributos.Apoth.ativo()) return;
        double m = num(ev.getEntity(), "mineracao");
        if (m != 0) ev.setNewSpeed((float) (ev.getNewSpeed() * (1 + m / 100)));
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || !EFE.containsKey(p.getUUID())) return;
        // queda lenta precisa reagir rápido
        if (p.tickCount % 2 == 0 && tem(p, DefRacas.QUEDA_LENTA) && p.fallDistance > 2.5f && !p.isShiftKeyDown() && !p.getAbilities().flying)
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20, 0, true, false, false));
        if (p.tickCount % 20 != 0) return;
        long agora = System.currentTimeMillis();
        if (tem(p, DefRacas.VISAO_NOTURNA)) {
            MobEffectInstance v = p.getEffect(MobEffects.NIGHT_VISION);
            if (v == null || v.getDuration() < 240) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, false));
        }
        if (tem(p, DefRacas.FRACO_SOL) && p.level().isDay() && !p.level().isRaining() && p.level().canSeeSky(p.blockPosition().above()))
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, true, true));
        if (num(p, "fogo") >= 100 && p.isOnFire()) p.clearFire();
        double fome = num(p, "fome");
        if (fome > 0 && !p.isCreative() && !p.isSpectator()) p.causeFoodExhaustion((float) (0.05 * fome / 100));
        if (tem(p, DefRacas.REGEN_TOTAL) && agora - COMBATE.getOrDefault(p.getUUID(), 0L) > 6_000 && p.getHealth() < p.getMaxHealth())
            p.heal(p.getMaxHealth() * 0.05f);
        if (tem(p, DefRacas.AURA)) {
            for (LivingEntity m : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6), x -> x instanceof Enemy && x.isAlive())) {
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, true, false));
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0, true, false));
            }
        }
        if (p.tickCount % 1200 == 0) avisarLiberadas(p);
    }

    /** Avisa (uma vez) quando uma raça especial ou lendária fica disponível. */
    private static void avisarLiberadas(ServerPlayer p) {
        CompoundTag d = dados(p);
        Set<String> avisadas = lista(d, "avisadas");
        boolean mudou = false;
        for (Raca r : DefRacas.TODAS.values()) {
            if (r.nivel() == DefRacas.COMUM || avisadas.contains(r.id()) || !liberada(p, r.id())) continue;
            avisadas.add(r.id());
            mudou = true;
            boolean lend = r.lendaria();
            p.sendSystemMessage(Component.literal(lend ? "✦ Algo desperta dentro de você... raça lendária liberada: " : "✦ Nova raça liberada: ")
                    .withStyle(lend ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD)
                    .append(Component.literal(r.nome()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal("  [ver raças]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/raca")))));
            if (lend) p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.8f);
        }
        if (mudou) guardarLista(d, "avisadas", avisadas);
    }

    /** NPCs dos reinos reconhecem um lendário, mesmo disfarçado. */
    @SubscribeEvent
    public void falarComNpc(PlayerInteractEvent.EntityInteract ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !(ev.getTarget() instanceof AbstractVillager v)) return;
        Raca r = DefRacas.de(principal(p));
        if (r == null || !r.lendaria() || !escolheu(p)) return;
        String chave = p.getUUID() + "|" + v.getUUID();
        long agora = System.currentTimeMillis();
        if (agora - RECONHECIDO.getOrDefault(chave, 0L) < 300_000) return;
        RECONHECIDO.put(chave, agora);
        String fala = switch (r.id()) {
            case "anjo" -> "abaixa a voz: \"Sinto uma luz em você que não é deste mundo...\"";
            case "rei_demonio" -> "estremece: \"P-perdão, senhor. Não vou atrapalhar seu caminho.\"";
            case "dragao" -> "recua um passo: \"Esses olhos... dizem que os antigos dragões andam entre nós.\"";
            case "celestial" -> "sorri, sem saber por quê: \"Estar perto de você me acalma.\"";
            case "deus" -> "se ajoelha: \"Os sábios diziam que um dia você viria.\"";
            default -> "";
        };
        if (!fala.isEmpty())
            p.sendSystemMessage(Component.literal("[" + v.getDisplayName().getString() + "] ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(fala).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)));
    }

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("raca")
                .executes(ctx -> { sincronizar(ctx.getSource().getPlayerOrException(), true); return 1; })
                .then(Commands.literal("revelar").executes(ctx -> { alternarRevelar(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("ver").then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer quem = ctx.getSource().getPlayerOrException(), alvo = EntityArgument.getPlayer(ctx, "jogador");
                    boolean experiente = ctx.getSource().hasPermission(2) || (Progressao.temDados(quem) && Progressao.dados(quem).nivel() >= NIVEL_VER_LENDARIA);
                    String t = textoVisivel(alvo);
                    String verdade = escolheu(alvo) ? DefRacas.nome(principal(alvo)) + (secundaria(alvo).isEmpty() ? "" : "/" + DefRacas.nome(secundaria(alvo))) : "";
                    String msg = alvo.getGameProfile().getName() + ": " + (t.isEmpty() ? "sem raça" : t);
                    if (experiente && disfarcada(alvo)) msg += "  — seu olhar experiente percebe: " + verdade + "!";
                    String fm = msg;
                    ctx.getSource().sendSuccess(() -> Component.literal(fm).withStyle(ChatFormatting.GOLD), false);
                    return 1;
                }))));

        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("raca").then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.literal("definir").then(Commands.argument("principal", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(DefRacas.TODAS.keySet(), b))
                                .executes(ctx -> { escolher(EntityArgument.getPlayer(ctx, "jogador"), StringArgumentType.getString(ctx, "principal"), "", true); return 1; })
                                .then(Commands.argument("secundaria", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(DefRacas.TODAS.keySet(), b))
                                        .executes(ctx -> {
                                            escolher(EntityArgument.getPlayer(ctx, "jogador"), StringArgumentType.getString(ctx, "principal"), StringArgumentType.getString(ctx, "secundaria"), true);
                                            return 1;
                                        }))))
                        .then(Commands.literal("liberar").then(Commands.argument("raca", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(DefRacas.TODAS.keySet(), b))
                                .executes(ctx -> {
                                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                    String id = StringArgumentType.getString(ctx, "raca");
                                    if (DefRacas.de(id) == null) { ctx.getSource().sendFailure(Component.literal("Raça desconhecida.")); return 0; }
                                    CompoundTag d = dados(alvo);
                                    Set<String> l = lista(d, "liberadas");
                                    l.add(id);
                                    guardarLista(d, "liberadas", l);
                                    avisarLiberadas(alvo);
                                    sincronizar(alvo, false);
                                    ctx.getSource().sendSuccess(() -> Component.literal(DefRacas.nome(id) + " liberada para " + alvo.getGameProfile().getName()), true);
                                    return 1;
                                })))
                        .then(Commands.literal("limpar").executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            CompoundTag d = dados(alvo);
                            d.putBoolean("escolheu", false); d.putString("principal", ""); d.putString("secundaria", ""); d.putInt("trocas", 0);
                            aplicar(alvo);
                            sincronizar(alvo, false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Raça de " + alvo.getGameProfile().getName() + " apagada."), true);
                            return 1;
                        })))));
    }
}
