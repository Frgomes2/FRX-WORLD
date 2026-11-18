package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Druida: as formas de animal (lobo, urso e ave).
 *
 *  - A forma muda os números (vida, velocidade, dano recebido), troca a arma pelas garras e TROCA A BARRA de habilidades:
 *    cada forma tem a sua, guardada no jogador. Habilidades com código "lobo_", "urso_" ou "ave_" só funcionam naquela forma;
 *    as outras só na forma humana (as de transformação e a suprema valem em qualquer uma).
 *  - Garras: na forma de animal o golpe corpo a corpo ignora a arma da mão e vale um dano fixo que cresce com o nível.
 *  - O visual vem do mod Woodwalkers, por reflexão. Sem ele a forma funciona igual, só não muda o corpo do jogador.
 *    Com ele, o FRX tranca o Woodwalkers em "só por código": ninguém se transforma por conta própria.
 *  - Tempo: a forma dura a "duração" da habilidade. Trocar de uma forma para outra não renova o relógio, e ao voltar
 *    a humano ele passa 15 s sem poder se transformar. Usar a habilidade da forma atual volta na hora, sem custo.
 *  - Mecânicas: frx:forma (potência 1 = lobo, 2 = urso, 3 = ave) e frx:floresta.
 */
public class Formas {
    private static final UUID ID_VIDA = UUID.fromString("6f0d2c1e-51a7-4c0a-9a7e-0d6b1f2a3c01");
    private static final UUID ID_VEL = UUID.fromString("6f0d2c1e-51a7-4c0a-9a7e-0d6b1f2a3c02");
    private static final UUID ID_EMPURRAO = UUID.fromString("6f0d2c1e-51a7-4c0a-9a7e-0d6b1f2a3c03");
    private static final String RAIZ = "frx_druida";

    private record Floresta(UUID dono, ServerLevel nivel, HabilidadeDef h, double mult, int fimTick) { }
    /** Depois de voltar à forma humana, o Druida fica este tempo sem poder se transformar de novo. */
    private static final int DESCANSO_TICKS = 20 * 15;
    private static final Map<UUID, Integer> fimDaForma = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> descanso = new ConcurrentHashMap<>();
    private static final List<Floresta> florestas = new CopyOnWriteArrayList<>();
    private static boolean avisouSemVisual;

    public Formas() {}

    private static void aviso(ServerPlayer p, String texto, ChatFormatting cor) { p.displayClientMessage(Component.literal(texto).withStyle(cor), true); }
    private static boolean druida(ServerPlayer p) { return Progressao.temDados(p) && "DRUIDA".equals(Progressao.dados(p).classe()); }

    private static CompoundTag guardado(ServerPlayer p) {
        CompoundTag raiz = p.getPersistentData();
        CompoundTag fica = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        raiz.put(Player.PERSISTED_NBT_TAG, fica);
        CompoundTag d = fica.getCompound(RAIZ);
        fica.put(RAIZ, d);
        return d;
    }

    /** "" = humano; "lobo", "urso" ou "ave". */
    public static String forma(ServerPlayer p) {
        CompoundTag fica = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return fica.getCompound(RAIZ).getString("forma");
    }

    private static String nome(String forma) {
        return switch (forma) { case "lobo" -> "Lobo"; case "urso" -> "Urso"; case "ave" -> "Ave"; default -> "Humano"; };
    }

    /** De qual forma é a habilidade? "" = humana, "*" = qualquer uma. */
    private static String formaDa(HabilidadeDef h) {
        if ("frx:forma".equals(h.efeito()) || h.ultimate()) return "*";
        for (String f : List.of("lobo", "urso", "ave")) if (h.codigo().startsWith(f + "_")) return f;
        return "";
    }

    /** Chamado antes de usar uma habilidade: o Druida só usa a que é da forma em que está. */
    static boolean podeUsar(ServerPlayer p, HabilidadeDef h) {
        if (!"DRUIDA".equals(h.classe())) return true;
        String precisa = formaDa(h), atual = forma(p);
        if (precisa.equals("*") || precisa.equals(atual)) return true;
        aviso(p, h.nome() + " só funciona na forma de " + nome(precisa).toLowerCase() + ".", ChatFormatting.YELLOW);
        return false;
    }

    // ------------------------------------------------------------------ números de cada forma

    /** Dano do golpe corpo a corpo na forma de animal (substitui a arma). */
    private static double garras(ServerPlayer p, String forma) {
        int n = Progressao.dados(p).nivel();
        double base = switch (forma) { case "lobo" -> 3 + n * 0.28; case "urso" -> 2.5 + n * 0.20; default -> 1; };
        return base * Talentos.fator(p, Talentos.Atributo.DANO_FISICO) * (Despertar.florestaAtiva(p) ? 1.5 : 1);
    }

    /** Quanto do dano recebido passa em cada forma. */
    private static double danoRecebido(String forma) {
        return switch (forma) { case "lobo" -> 1.10; case "urso" -> 0.85; case "ave" -> 1.25; default -> 1; };
    }

    private static void mod(ServerPlayer p, Attribute a, UUID id, double valor, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        inst.removeModifier(id);
        if (valor != 0) inst.addTransientModifier(new AttributeModifier(id, "FRX forma", valor, op));
    }

    private static void atributos(ServerPlayer p, String forma) {
        double vida = "urso".equals(forma) ? 0.25 : 0;
        double vel = switch (forma) { case "lobo" -> 0.25; case "urso" -> -0.10; default -> 0; };
        mod(p, Attributes.MAX_HEALTH, ID_VIDA, vida, AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(p, Attributes.MOVEMENT_SPEED, ID_VEL, vel, AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(p, Attributes.KNOCKBACK_RESISTANCE, ID_EMPURRAO, "urso".equals(forma) ? 0.5 : 0, AttributeModifier.Operation.ADDITION);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ------------------------------------------------------------------ barras por forma

    private static void guardarBarra(ServerPlayer p, String forma) {
        CompoundTag d = guardado(p);
        String[] barra = Progressao.dados(p).barra();
        CompoundTag b = new CompoundTag();
        for (int i = 0; i < barra.length; i++) b.putString("e" + i, barra[i]);
        d.put("barra_" + (forma.isEmpty() ? "humano" : forma), b);
    }

    private static void porBarra(ServerPlayer p, String forma, String codigoDaForma) {
        CompoundTag d = guardado(p);
        DadosJogador dj = Progressao.dados(p);
        String[] barra = dj.barra();
        String chave = "barra_" + (forma.isEmpty() ? "humano" : forma);
        String suprema = barra[4];
        boolean nova = !d.contains(chave);
        CompoundTag b = d.getCompound(chave);
        for (int i = 0; i < barra.length; i++) barra[i] = nova ? "" : b.getString("e" + i);
        if (forma.isEmpty()) return;
        // barra de animal: o 1º espaço volta à forma humana e os vazios recebem o que ele já sabe daquela forma
        if (nova) { barra[0] = codigoDaForma; barra[4] = suprema; }
        List<HabilidadeDef> sabe = Habilidades.catalogo().values().stream()
                .filter(h -> "DRUIDA".equals(h.classe()) && !h.ultimate() && h.codigo().startsWith(forma + "_") && dj.habilidades().containsKey(h.codigo()))
                .sorted(Comparator.comparingInt(HabilidadeDef::nivelReq)).toList();
        for (HabilidadeDef h : sabe) {
            boolean jaEsta = false;
            for (int i = 0; i < 4; i++) if (barra[i].equals(h.codigo())) jaEsta = true;
            if (jaEsta) continue;
            for (int i = 0; i < 4; i++) if (barra[i].isEmpty()) { barra[i] = h.codigo(); break; }
        }
        // limpa o que não existe mais ou foi desaprendido
        for (int i = 0; i < barra.length; i++) if (!barra[i].isEmpty() && !dj.habilidades().containsKey(barra[i])) barra[i] = "";
    }

    // ------------------------------------------------------------------ transformar

    static boolean transformar(ServerPlayer p, HabilidadeDef h) {
        String alvo = switch (h.potencia()) { case 2 -> "urso"; case 3 -> "ave"; default -> "lobo"; };
        String atual = forma(p);
        if (atual.equals(alvo)) { voltar(p, true); return false; }
        int agora = p.server.getTickCount();
        Integer cansado = descanso.get(p.getUUID());
        if (atual.isEmpty() && cansado != null && cansado > agora) {
            aviso(p, "Seu corpo ainda se recupera da última forma: " + (cansado - agora + 19) / 20 + " s.", ChatFormatting.YELLOW);
            return false;
        }
        guardarBarra(p, atual);
        guardado(p).putString("forma", alvo);
        porBarra(p, alvo, h.codigo());
        atributos(p, alvo);
        // tempo transformado: trocar de uma forma para outra não renova o relógio (só pode encurtar, como na ave)
        int novoFim = agora + (int) (Math.max(5, h.duracao()) * 20);
        Integer fimAntes = atual.isEmpty() ? null : fimDaForma.get(p.getUUID());
        fimDaForma.put(p.getUUID(), fimAntes == null ? novoFim : Math.min(fimAntes, novoFim));
        int resta = (fimDaForma.get(p.getUUID()) - agora) / 20;
        visual(p, alvo);
        ServerLevel n = p.serverLevel();
        n.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
        n.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 14, 0.4, 0.6, 0.4, 0.03);
        n.playSound(null, p.blockPosition(), switch (alvo) { case "urso" -> SoundEvents.POLAR_BEAR_WARNING; case "ave" -> SoundEvents.PARROT_FLY; default -> SoundEvents.WOLF_GROWL; }, SoundSource.PLAYERS, 1.2f, 1f);
        if ("ave".equals(alvo)) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(100, resta * 20), 0, false, false));
        aviso(p, switch (alvo) {
            case "urso" -> "Forma de urso por " + resta + " s: +25% de vida, 15% menos dano recebido, mais lento.";
            case "ave" -> "Forma de ave por " + resta + " s: você voa, mas quase não fere e apanha mais.";
            default -> "Forma de lobo por " + resta + " s: +25% de velocidade e garras fortes, mas você apanha 10% mais.";
        }, ChatFormatting.GREEN);
        Habilidades.sincronizar(p);
        return true;
    }

    /** A habilidade é a da forma em que ele já está? Então só volta a humano: sem custo e mesmo com a habilidade em recarga. */
    static boolean voltarSeForaMesma(ServerPlayer p, HabilidadeDef h) {
        if (!"frx:forma".equals(h.efeito())) return false;
        String alvo = switch (h.potencia()) { case 2 -> "urso"; case 3 -> "ave"; default -> "lobo"; };
        if (!forma(p).equals(alvo)) return false;
        voltar(p, true);
        return true;
    }

    /** Volta à forma humana (não faz nada se já estiver). */
    public static void voltar(ServerPlayer p, boolean avisar) {
        String atual = forma(p);
        fimDaForma.remove(p.getUUID());
        atributos(p, "");
        if (atual.isEmpty()) return;
        descanso.put(p.getUUID(), p.server.getTickCount() + DESCANSO_TICKS);
        boolean temDados = Progressao.temDados(p);
        if (temDados) guardarBarra(p, atual);
        guardado(p).putString("forma", "");
        if (temDados) porBarra(p, "", "");
        visual(p, "");
        if ("ave".equals(atual)) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 14, 0.4, 0.6, 0.4, 0.03);
        if (avisar) aviso(p, "Você voltou à forma humana. Nova transformação em " + DESCANSO_TICKS / 20 + " s.", ChatFormatting.GREEN);
        if (temDados) Habilidades.sincronizar(p);
    }

    // ------------------------------------------------------------------ ponte para o Woodwalkers (visual)

    private static Boolean temWoodwalkers;

    private static boolean woodwalkers() {
        if (temWoodwalkers == null) {
            try { Class.forName("tocraft.walkers.api.PlayerShape"); temWoodwalkers = true; }
            catch (Throwable t) { temWoodwalkers = false; }
        }
        return temWoodwalkers;
    }

    private static void visual(ServerPlayer p, String forma) {
        if (!woodwalkers()) {
            if (!forma.isEmpty() && !avisouSemVisual) { avisouSemVisual = true; FrxWorld.LOG.warn("Druida: o mod Woodwalkers não está no pack; as formas funcionam sem mudar o corpo do jogador."); }
            return;
        }
        try {
            LivingEntity corpo = null;
            if (!forma.isEmpty()) {
                EntityType<? extends LivingEntity> tipo = switch (forma) { case "urso" -> EntityType.POLAR_BEAR; case "ave" -> EntityType.PARROT; default -> EntityType.WOLF; };
                corpo = tipo.create(p.serverLevel());
            }
            Class.forName("tocraft.walkers.api.PlayerShape").getMethod("updateShapes", ServerPlayer.class, LivingEntity.class).invoke(null, p, corpo);
        } catch (Throwable t) {
            FrxWorld.LOG.warn("Druida: o Woodwalkers recusou a troca de forma: {}", t.toString());
        }
    }

    /** Tranca o Woodwalkers: só o FRX transforma, sem vida/armadura copiadas do bicho e sem monstros ignorando o jogador. */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void aoLigar(ServerStartedEvent ev) {
        if (!woodwalkers()) return;
        try {
            Object cfg = Class.forName("tocraft.walkers.Walkers").getField("CONFIG").get(null);
            for (Object[] par : new Object[][]{{"apiLevel", 0}, {"scalingHealth", false}, {"scalingAmor", false}, {"hostilesIgnoreHostileShapedPlayer", false},
                    {"hostilesIgnoreNotHostileShapedPlayer", false}, {"showPlayerNametag", true}, {"revoke2ndShapeOnDeath", true}, {"villagersRunFrom2ndShapes", false}}) {
                try { cfg.getClass().getField((String) par[0]).set(cfg, par[1]); }
                catch (Throwable t) { FrxWorld.LOG.warn("Druida: o Woodwalkers não tem a opção {} ({})", par[0], t.toString()); }
            }
            Class nivel = Class.forName("tocraft.walkers.api.platform.ApiLevel");
            nivel.getMethod("setApiLevel", nivel).invoke(null, Enum.valueOf(nivel, "API_ONLY"));
            FrxWorld.LOG.info("Druida: Woodwalkers trancado em 'só por código'.");
        } catch (Throwable t) {
            FrxWorld.LOG.warn("Druida: não consegui trancar o Woodwalkers ({}). Jogadores podem conseguir se transformar sozinhos.", t.toString());
        }
    }

    // ------------------------------------------------------------------ frx:floresta — a mata cresce em volta do Druida

    static boolean floresta(ServerPlayer p, HabilidadeDef h, double mult) {
        int dur = (int) Math.max(100, h.duracao() * 20);
        florestas.removeIf(f -> f.dono().equals(p.getUUID()));
        florestas.add(new Floresta(p.getUUID(), p.serverLevel(), h, mult, p.server.getTickCount() + dur));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 2f, 0.5f);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1f, 0.7f);
        aviso(p, "Ira da Floresta: raízes e espinhos em volta de você por " + dur / 20 + " s.", ChatFormatting.DARK_GREEN);
        return true;
    }

    private static void tickFloresta(MinecraftServer s, Floresta f) {
        ServerPlayer p = s.getPlayerList().getPlayer(f.dono());
        int agora = s.getTickCount();
        if (p == null || !p.isAlive() || p.serverLevel() != f.nivel() || agora >= f.fimTick()) { florestas.remove(f); return; }
        ServerLevel n = f.nivel();
        double raio = Math.max(4, f.h().raio());
        if (agora % 5 == 0) {
            for (int i = 0; i < 24; i++) {
                double ang = Math.PI * 2 * i / 24 + agora * 0.05;
                n.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + Math.cos(ang) * raio, p.getY() + 0.2, p.getZ() + Math.sin(ang) * raio, 1, 0, 0.1, 0, 0);
            }
            n.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.getX(), p.getY() + 2.5, p.getZ(), 10, raio / 2, 0.3, raio / 2, 0);
        }
        if ((f.fimTick() - agora) % 20 != 0) return;
        for (LivingEntity e : n.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(raio, 4, raio), x -> x.isAlive() && x.distanceTo(p) <= raio + 0.5)) {
            if (e instanceof ServerPlayer a) {
                a.heal((float) (Math.max(1, f.h().potencia()) * Talentos.fator(p, Talentos.Atributo.CURA)));
                n.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + 1.8, a.getZ(), 1, 0.2, 0.1, 0.2, 0);
            } else if (Habilidades.inimigo(p, e)) {
                Habilidades.ferir(p, e, f.h().dano() * f.mult(), f.h());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
                n.sendParticles(ParticleTypes.COMPOSTER, e.getX(), e.getY() + 0.3, e.getZ(), 8, 0.3, 0.2, 0.3, 0);
            }
        }
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoFerir(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide() || ev.getAmount() <= 0) return;
        // garras: na forma de animal o golpe corpo a corpo não usa a arma
        if (ev.getSource().getEntity() instanceof ServerPlayer p && p != vitima && !Habilidades.emHabilidade && ev.getSource().getDirectEntity() == p) {
            String f = forma(p);
            if (!f.isEmpty() && druida(p)) ev.setAmount((float) garras(p, f));
        }
        if (vitima instanceof ServerPlayer d) {
            String f = forma(d);
            if (!f.isEmpty() && druida(d)) {
                double passa = danoRecebido(f);
                if (passa > 1 && Despertar.florestaAtiva(d)) passa = 1;   // Coração da Floresta tira as fraquezas das formas
                ev.setAmount((float) (ev.getAmount() * passa));
            }
        }
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        int agora = s.getTickCount();
        for (Floresta f : florestas) { try { tickFloresta(s, f); } catch (Exception e) { florestas.remove(f); } }
        if (agora % 10 != 0) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            String f = forma(p);
            if (f.isEmpty()) continue;
            Integer fim = fimDaForma.get(p.getUUID());
            if (!druida(p) || !p.isAlive()) voltar(p, false);
            else if (fim == null || agora >= fim) { voltar(p, false); aviso(p, "O tempo da forma acabou: você voltou à forma humana. Nova transformação em " + DESCANSO_TICKS / 20 + " s.", ChatFormatting.YELLOW); }
            else if (fim - agora <= 100 && fim - agora > 90) aviso(p, "A forma acaba em 5 s.", ChatFormatting.YELLOW);
        }
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) { if (ev.getEntity() instanceof ServerPlayer p) voltar(p, false); }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent ev) { if (ev.getEntity() instanceof ServerPlayer p) voltar(p, false); }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) { try { voltar(p, false); } catch (Exception e) { /* os dados dele já podem ter sido soltos */ } }
        florestas.removeIf(f -> f.dono().equals(ev.getEntity().getUUID()));
    }
}
