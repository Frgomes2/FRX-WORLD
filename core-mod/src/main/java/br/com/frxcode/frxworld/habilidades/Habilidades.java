package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.blocos.Blocos;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.SyncHabilidades;
import br.com.frxcode.frxworld.rede.SyncRecarga;
import br.com.frxcode.frxworld.rede.SyncRecurso;
import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Classes, recursos (mana/fúria/energia) e habilidades no estilo LoL: 4 ativas + 1 ultimate na barra,
 * custo, recarga e nível da habilidade que sobe com o uso. Os números vêm da aba Habilidades do painel.
 */
public class Habilidades {
    public static final List<String> CLASSES = List.of("GUERREIRO", "ARQUEIRO", "MAGO", "CLERIGO", "LADINO", "ASSASSINO", "INVOCADOR", "PALADINO", "BARDO", "MONGE", "DRUIDA");
    private static final Map<String, String> NOMES = Map.ofEntries(Map.entry("GUERREIRO", "Guerreiro"), Map.entry("ARQUEIRO", "Arqueiro"), Map.entry("MAGO", "Mago"),
            Map.entry("CLERIGO", "Clérigo"), Map.entry("LADINO", "Ladino"), Map.entry("ASSASSINO", "Assassino"), Map.entry("INVOCADOR", "Invocador"),
            Map.entry("PALADINO", "Paladino"), Map.entry("BARDO", "Bardo"), Map.entry("MONGE", "Monge"), Map.entry("DRUIDA", "Druida"));
    public static final String TAG_INVOCADO = "frx_invocado";
    private static final long COMBATE_MS = 5000;

    private static volatile Map<String, HabilidadeDef> catalogo = new LinkedHashMap<>();
    private static final Map<UUID, Map<String, Long>> recargas = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ultimoCombate = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ultimoEnviado = new ConcurrentHashMap<>();
    /** Invocações vivas: dono → entidades. */
    private static final Map<UUID, Set<UUID>> invocados = new ConcurrentHashMap<>();
    private static int ticks = 0;

    // ------------------------------------------------------------------ painel

    public static void recarregar() {
        PainelApi.lerHabilidades().whenComplete((arr, erro) -> {
            if (erro != null) {
                FrxWorld.LOG.warn("Não consegui ler as habilidades do painel: {}", erro.getMessage());
                return;
            }
            Map<String, HabilidadeDef> novo = new LinkedHashMap<>();
            for (JsonElement e : arr) {
                try {
                    HabilidadeDef h = HabilidadeDef.deJson(e.getAsJsonObject());
                    novo.put(h.codigo(), h);
                } catch (Exception ex) {
                    FrxWorld.LOG.warn("Habilidade inválida ignorada: {}", e);
                }
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) { catalogo = novo; return; }
            server.execute(() -> {
                boolean mudou = !novo.equals(catalogo);
                catalogo = novo;
                if (mudou) for (ServerPlayer p : server.getPlayerList().getPlayers()) aprenderPorNivel(p);
            });
        });
    }

    public static String nomeClasse(String c) {
        return NOMES.getOrDefault(c, "Sem classe");
    }

    public static String recursoDa(String classe) {
        return switch (classe) {
            case "" -> "";
            case "GUERREIRO" -> "FURIA";
            case "ARQUEIRO", "LADINO", "ASSASSINO" -> "ENERGIA";
            case "MONGE" -> "CHI";
            default -> "MANA";
        };
    }

    public static String nomeRecurso(String r) {
        return switch (r) { case "FURIA" -> "Fúria"; case "ENERGIA" -> "Energia"; case "CHI" -> "Chi"; default -> "Mana"; };
    }

    public static int maximo(ServerPlayer p) {
        DadosJogador d = Progressao.dados(p);
        int bonus = (int) Math.round(Talentos.bonus(p, Talentos.Atributo.RECURSO_MAX));
        int base = ("MANA".equals(recursoDa(d.classe())) ? Math.min(500, 100 + 4 * (d.nivel() - 1)) : 100) + bonus;
        return Math.max(20, (int) Math.round(base * (1 + br.com.frxcode.frxworld.racas.RacasJogador.num(p, "recursoMax") / 100)));
    }

    // ------------------------------------------------------------------ sincronização

    public static void sincronizar(ServerPlayer p) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        List<SyncHabilidades.Info> lista = new ArrayList<>();
        catalogo.values().stream()
                .filter(h -> h.classe().equals(d.classe()))
                .sorted(Comparator.comparing(HabilidadeDef::ultimate).thenComparingInt(HabilidadeDef::nivelReq).thenComparing(HabilidadeDef::nome))
                .forEach(h -> {
                    DadosJogador.RegistroHabilidade r = d.habilidades().get(h.codigo());
                    lista.add(new SyncHabilidades.Info(h.codigo(), h.nome(), h.descricao(), h.tipo(), h.elemento(), h.icone(), h.custo(),
                            (float) h.recarga(), h.nivelReq(), h.ultimate(), h.porItem(), r != null, r != null ? r.nivel : 0, r != null ? r.usos : 0));
                });
        Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new SyncHabilidades(d.classe(), recursoDa(d.classe()), lista, List.of(d.barra())));
        enviarRecurso(p, true);
        long agora = System.currentTimeMillis();
        recargas.getOrDefault(p.getUUID(), Map.of()).forEach((cod, fim) -> {
            if (fim > agora) enviar(p, new SyncRecarga(cod, (int) (fim - agora), (int) (fim - agora)));
        });
    }

    private static void enviar(ServerPlayer p, Object msg) {
        Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** Atualiza a barra de recurso (mana/fúria/energia) do jogador depois de gastar ou ganhar fora daqui. */
    public static void atualizarRecurso(ServerPlayer p) { enviarRecurso(p, true); }

    /** Invocações e sombras vivas do jogador (ids das entidades). */
    static Set<UUID> invocadosDe(UUID dono) { return invocados.getOrDefault(dono, Set.of()); }

    /** Coloca uma entidade na lista de invocações do jogador (ela passa a atacar o que ele ataca e a defendê-lo). */
    public static void registrarInvocado(ServerPlayer p, Entity e) {
        invocados.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(e.getUUID());
    }

    private static void enviarRecurso(ServerPlayer p, boolean sempre) {
        DadosJogador d = Progressao.dados(p);
        int atual = (int) Math.floor(d.recurso()), max = maximo(p);
        long chave = atual * 100_000L + max;
        Long antes = ultimoEnviado.put(p.getUUID(), chave);
        if (sempre || antes == null || antes != chave) enviar(p, new SyncRecurso(atual, max));
    }

    private static void avisar(ServerPlayer p, String texto, ChatFormatting cor) {
        p.displayClientMessage(Component.literal(texto).withStyle(cor), true);
    }

    // ------------------------------------------------------------------ classe, aprendizado e barra

    public static void escolherClasse(ServerPlayer p, String classe, boolean admin) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        if (!classe.isEmpty() && !CLASSES.contains(classe)) return;
        if (!admin && !d.classe().isEmpty()) {
            p.sendSystemMessage(Component.literal("Você já é " + nomeClasse(d.classe()) + ". Trocar de classe só com um administrador, por enquanto.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        Formas.voltar(p, false);
        d.definirClasse(classe);
        d.habilidades().clear();
        d.talentos().clear();
        for (int i = 0; i < d.barra().length; i++) d.barra()[i] = "";
        d.definirRecurso("FURIA".equals(recursoDa(classe)) || "CHI".equals(recursoDa(classe)) ? 0 : maximo(p));
        recargas.remove(p.getUUID());
        if (!classe.isEmpty()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(nomeClasse(classe)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Sua jornada começa. Teclas R, G, V, C e X.").withStyle(ChatFormatting.YELLOW)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
        aprenderPorNivel(p);
        Progressao.enviarAoPainel(p, false);
        if (!classe.isEmpty()) br.com.frxcode.frxworld.acesso.Abertura.aposClasse(p);
    }

    /** Aprende as habilidades "por nível" já liberadas e coloca na barra se houver espaço. Sempre sincroniza. */
    public static void aprenderPorNivel(ServerPlayer p) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        if (!d.classe().isEmpty()) {
            for (HabilidadeDef h : catalogo.values()) {
                if (!h.classe().equals(d.classe()) || h.porItem() || h.nivelReq() > d.nivel() || d.habilidades().containsKey(h.codigo())) continue;
                aprender(p, d, h);
            }
        }
        sincronizar(p);
        Talentos.aplicar(p);
        Talentos.avisarPontos(p);
    }

    private static void aprender(ServerPlayer p, DadosJogador d, HabilidadeDef h) {
        d.habilidades().put(h.codigo(), new DadosJogador.RegistroHabilidade());
        String[] barra = d.barra();
        if (h.ultimate()) {
            if (barra[4].isEmpty()) barra[4] = h.codigo();
        } else {
            for (int i = 0; i < 4; i++) if (barra[i].isEmpty()) { barra[i] = h.codigo(); break; }
        }
        p.sendSystemMessage(Component.literal("Nova habilidade: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(h.nome()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(h.ultimate() ? "  (ultimate · tecla X)" : "  (K para organizar a barra)").withStyle(ChatFormatting.GRAY)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
    }

    public static boolean lerTomo(ServerPlayer p, ItemStack tomo) {
        if (!Progressao.temDados(p)) return false;
        DadosJogador d = Progressao.dados(p);
        if (d.classe().isEmpty()) {
            avisar(p, "Escolha sua classe antes (tecla K).", ChatFormatting.YELLOW);
            return false;
        }
        String fixo = tomo.hasTag() ? tomo.getTag().getString(ItemTomo.NBT) : "";
        List<HabilidadeDef> possiveis = new ArrayList<>();
        int menorNivel = Integer.MAX_VALUE;
        for (HabilidadeDef h : catalogo.values()) {
            if (!h.classe().equals(d.classe()) || d.habilidades().containsKey(h.codigo())) continue;
            if (!fixo.isEmpty() ? !h.codigo().equals(fixo) : !h.porItem()) continue;
            if (h.nivelReq() > d.nivel()) { menorNivel = Math.min(menorNivel, h.nivelReq()); continue; }
            possiveis.add(h);
        }
        if (possiveis.isEmpty()) {
            if (menorNivel != Integer.MAX_VALUE) avisar(p, "Você ainda não entende este tomo. Volte no nível " + menorNivel + ".", ChatFormatting.YELLOW);
            else if (!fixo.isEmpty() && catalogo.containsKey(fixo) && !catalogo.get(fixo).classe().equals(d.classe()))
                avisar(p, "Este tomo é de outra classe (" + nomeClasse(catalogo.get(fixo).classe()) + ").", ChatFormatting.YELLOW);
            else avisar(p, "Este tomo não tem nada novo para você.", ChatFormatting.YELLOW);
            return false;
        }
        HabilidadeDef h = possiveis.get(ThreadLocalRandom.current().nextInt(possiveis.size()));
        aprender(p, d, h);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(h.nome()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("O tomo revelou uma habilidade").withStyle(ChatFormatting.GRAY)));
        sincronizar(p);
        return true;
    }

    public static void definirEspaco(ServerPlayer p, int espaco, String codigo) {
        if (!Progressao.temDados(p) || espaco < 0 || espaco > 4) return;
        DadosJogador d = Progressao.dados(p);
        String[] barra = d.barra();
        if (!codigo.isEmpty()) {
            HabilidadeDef h = catalogo.get(codigo);
            if (h == null || !d.habilidades().containsKey(codigo)) return;
            if (h.ultimate() != (espaco == 4)) {
                avisar(p, h.ultimate() ? "Ultimate só vai no último espaço (X)." : "O último espaço é só da ultimate.", ChatFormatting.YELLOW);
                return;
            }
            for (int i = 0; i < barra.length; i++) if (barra[i].equals(codigo)) barra[i] = "";
        }
        barra[espaco] = codigo;
        sincronizar(p);
    }

    // ------------------------------------------------------------------ uso

    public static void usar(ServerPlayer p, int espaco) {
        if (!Progressao.temDados(p) || espaco < 0 || espaco > 4 || p.isSpectator()) return;
        DadosJogador d = Progressao.dados(p);
        if (d.classe().isEmpty()) { avisar(p, "Escolha sua classe primeiro (tecla K).", ChatFormatting.YELLOW); return; }
        String cod = d.barra()[espaco];
        if (cod.isEmpty()) { avisar(p, "Espaço vazio. Aperte K para organizar a barra.", ChatFormatting.GRAY); return; }
        HabilidadeDef h = catalogo.get(cod);
        DadosJogador.RegistroHabilidade reg = d.habilidades().get(cod);
        if (h == null || reg == null || !h.classe().equals(d.classe())) return;

        if (Formas.voltarSeForaMesma(p, h) || !Formas.podeUsar(p, h)) return;

        long agora = System.currentTimeMillis();
        Map<String, Long> rc = recargas.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
        Long fim = rc.get(cod);
        if (fim != null && fim > agora) { avisar(p, h.nome() + " em recarga: " + String.format("%.1f", (fim - agora) / 1000.0) + "s", ChatFormatting.RED); return; }
        double custo = h.custo() * Math.max(0.4, 1 - Talentos.bonus(p, Talentos.Atributo.CUSTO));
        if (Despertar.portaoAtivo(p) || Despertar.florestaAtiva(p)) custo = 0;   // Portão Interior e Coração da Floresta: nada gasta
        if (d.recurso() < custo) { avisar(p, nomeRecurso(recursoDa(d.classe())) + " insuficiente (" + Math.round(custo) + ")", ChatFormatting.RED); return; }

        boolean ok;
        try {
            ok = executar(p, h, reg.nivel);
        } catch (Exception ex) {
            FrxWorld.LOG.warn("Habilidade {} falhou: {}", h.codigo(), ex.toString());
            ok = false;
        }
        if (!ok) return;

        d.definirRecurso(d.recurso() - custo);
        Despertar.gastouRecurso(p, custo);
        int totalMs = (int) Math.max(250, h.recarga() * 1000 * (1 - 0.03 * (reg.nivel - 1)) * Math.max(0.4, 1 - Talentos.bonus(p, Talentos.Atributo.RECARGA)));
        if (Especiais.consumirReembolso(p)) totalMs = Math.min(totalMs, 1000);
        rc.put(cod, agora + totalMs);
        enviar(p, new SyncRecarga(cod, totalMs, totalMs));
        enviarRecurso(p, true);

        reg.usos++;
        int novo = Math.min(10, 1 + (int) Math.sqrt(reg.usos / 5.0));
        if (novo > reg.nivel) {
            reg.nivel = novo;
            p.sendSystemMessage(Component.literal(h.nome() + " subiu para o nível " + novo + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
            sincronizar(p);
        }
    }

    private static boolean executar(ServerPlayer p, HabilidadeDef h, int nivelHab) {
        double mult = (1 + 0.03 * (Progressao.dados(p).nivel() - 1)) * (1 + 0.08 * (nivelHab - 1)) * Talentos.fator(p, Talentos.Atributo.DANO_HAB);
        if (h.efeito().startsWith("frx:")) {
            Boolean especial = Especiais.executar(p, h, mult);
            if (especial != null) return especial;
        }
        return switch (h.tipo()) {
            case "SKILLSHOT" -> skillshot(p, h, mult);
            case "ALVO" -> alvo(p, h, mult);
            case "AREA" -> area(p, h, mult);
            case "MOBILIDADE" -> mobilidade(p, h, mult);
            case "BUFF" -> buff(p, h);
            case "INVOCAR" -> invocar(p, h, nivelHab);
            default -> false;
        };
    }

    // ------------------------------------------------------------------ tipos

    private static boolean skillshot(ServerPlayer p, HabilidadeDef h, double mult) {
        double alcance = Math.max(1, h.alcance());
        LivingEntity alvo = mirar(p, alcance, 0.35, false);
        Vec3 olho = p.getEyePosition();
        Vec3 fim = alvo != null ? alvo.getBoundingBox().getCenter() : pontoBloco(p, alcance);
        linha(p.serverLevel(), olho.add(p.getLookAngle().scale(0.8)), fim, particula(h.elemento()));
        som(p, h.elemento());
        if (alvo != null) ferir(p, alvo, h.dano() * mult, h);
        return true;
    }

    private static boolean alvo(ServerPlayer p, HabilidadeDef h, double mult) {
        double alcance = Math.max(1, h.alcance());
        if (h.dano() < 0) {
            LivingEntity aliado = mirar(p, alcance, 0.9, true);
            LivingEntity quem = aliado != null ? aliado : p;
            curar(quem, -h.dano() * mult * Talentos.fator(p, Talentos.Atributo.CURA));
            if (aliado != null) linha(p.serverLevel(), p.getEyePosition(), aliado.getBoundingBox().getCenter(), ParticleTypes.END_ROD);
            p.level().playSound(null, quem.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
            aplicarEfeito(quem, h);
            return true;
        }
        LivingEntity alvo = mirar(p, alcance, 0.9, false);
        if (alvo == null) { avisar(p, "Nenhum alvo na mira.", ChatFormatting.GRAY); return false; }
        double dano = h.dano() * mult;
        if ("LADINO".equals(h.classe())) {
            Vec3 paraJogador = p.position().subtract(alvo.position()).normalize();
            if (alvo.getLookAngle().dot(paraJogador) < -0.3) {
                dano *= 2;
                avisar(p, "Pelas costas! Dano dobrado.", ChatFormatting.DARK_RED);
            }
        }
        if ("ASSASSINO".equals(h.classe())) {
            // Assassino: +50% pelas costas e +50% em alvo abaixo de 35% da vida (somam)
            Vec3 paraJogador = p.position().subtract(alvo.position()).normalize();
            boolean costas = alvo.getLookAngle().dot(paraJogador) < -0.3;
            boolean ferido = alvo.getHealth() <= alvo.getMaxHealth() * 0.35f;
            if (costas) dano *= 1.5;
            if (ferido) dano *= 1.5;
            if (costas || ferido)
                avisar(p, costas && ferido ? "Execução pelas costas!" : costas ? "Pelas costas!" : "Execução!", ChatFormatting.DARK_RED);
        }
        linha(p.serverLevel(), p.getEyePosition(), alvo.getBoundingBox().getCenter(), particula(h.elemento()));
        som(p, h.elemento());
        ferir(p, alvo, dano, h);
        return true;
    }

    private static boolean area(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        Vec3 centro;
        if (h.alcance() > 0) {
            LivingEntity mirado = mirar(p, h.alcance(), 0.5, false);
            centro = mirado != null ? mirado.position() : pontoBloco(p, h.alcance());
        } else {
            centro = p.position();
        }
        double raio = Math.max(1, h.raio());
        ParticleOptions part = particula(h.elemento());
        for (int i = 0; i < 40; i++) {
            double ang = Math.PI * 2 * i / 40;
            nivel.sendParticles(part, centro.x + Math.cos(ang) * raio, centro.y + 0.2, centro.z + Math.sin(ang) * raio, 1, 0, 0.1, 0, 0.01);
        }
        nivel.sendParticles(part, centro.x, centro.y + 1, centro.z, 30, raio / 2, 0.6, raio / 2, 0.05);
        if (h.alcance() > 0 && "FOGO".equals(h.elemento())) {
            nivel.sendParticles(ParticleTypes.EXPLOSION, centro.x, centro.y + 0.5, centro.z, 3, 1, 0.3, 1, 0);
            nivel.playSound(null, BlockPos.containing(centro), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 1f);
        } else {
            som(p, h.elemento());
        }
        AABB caixa = new AABB(centro, centro).inflate(raio, 3, raio);
        if (h.dano() < 0) {
            for (Player aliado : nivel.getEntitiesOfClass(Player.class, caixa, e -> e.isAlive() && e.position().distanceTo(centro) <= raio + 0.5)) {
                curar(aliado, -h.dano() * mult * Talentos.fator(p, Talentos.Atributo.CURA));
                aplicarEfeito(aliado, h);
            }
            return true;
        }
        for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, caixa, e -> e != p && e.isAlive() && inimigo(p, e)
                && e.position().distanceTo(centro) <= raio + 0.5)) {
            ferir(p, e, h.dano() * mult, h);
        }
        return true;
    }

    private static boolean mobilidade(ServerPlayer p, HabilidadeDef h, double mult) {
        ServerLevel nivel = p.serverLevel();
        Vec3 inicio = p.position();
        if ("ARCANO".equals(h.elemento()) || "SOMBRA".equals(h.elemento())) {
            Vec3 olho = p.getEyePosition(), dir = p.getLookAngle();
            Vec3 fim = pontoBloco(p, Math.abs(h.alcance())).subtract(dir.scale(0.8));
            BlockPos destino = null;
            BlockPos base = BlockPos.containing(fim.x, fim.y - 1, fim.z);
            for (int dy = 0; dy <= 3 && destino == null; dy++) {
                BlockPos c = base.above(dy);
                if (nivel.getBlockState(c).isAir() && nivel.getBlockState(c.above()).isAir()) destino = c;
            }
            if (destino == null) { avisar(p, "Sem espaço para aparecer ali.", ChatFormatting.GRAY); return false; }
            nivel.sendParticles(ParticleTypes.PORTAL, inicio.x, inicio.y + 1, inicio.z, 40, 0.4, 0.8, 0.4, 0.2);
            p.teleportTo(destino.getX() + 0.5, destino.getY(), destino.getZ() + 0.5);
            p.fallDistance = 0;
            nivel.sendParticles(particula(h.elemento()), destino.getX() + 0.5, destino.getY() + 1, destino.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.05);
            nivel.playSound(null, destino, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
            return true;
        }
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-4) return false;
        dir = dir.normalize();
        double forca = h.alcance() * 0.22;
        p.setDeltaMovement(dir.x * forca, 0.35, dir.z * forca);
        p.hurtMarked = true;
        p.fallDistance = 0;
        nivel.sendParticles(ParticleTypes.CLOUD, inicio.x, inicio.y + 0.2, inicio.z, 12, 0.3, 0.1, 0.3, 0.02);
        nivel.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.8f);
        if (h.dano() > 0 && h.alcance() > 0) {
            Vec3 fim = inicio.add(dir.scale(h.alcance()));
            AABB caminho = new AABB(inicio, fim).inflate(1.5, 1, 1.5);
            for (LivingEntity e : nivel.getEntitiesOfClass(LivingEntity.class, caminho, e -> e != p && e.isAlive() && inimigo(p, e))) {
                ferir(p, e, h.dano() * mult, h);
                e.knockback(0.8, -dir.x, -dir.z);
            }
        }
        return true;
    }

    private static boolean buff(ServerPlayer p, HabilidadeDef h) {
        MobEffect ef = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(h.efeito()));
        if (ef == null) { avisar(p, "Efeito desconhecido: " + h.efeito(), ChatFormatting.RED); return false; }
        List<LivingEntity> alvos = new ArrayList<>();
        alvos.add(p);
        if (h.raio() > 0) alvos.addAll(p.serverLevel().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(h.raio()), e -> e != p && e.isAlive()));
        for (LivingEntity e : alvos) {
            e.addEffect(new MobEffectInstance(ef, (int) Math.max(20, h.duracao() * 20), Math.max(0, h.potencia() - 1)));
            p.serverLevel().sendParticles(particula(h.elemento()), e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.3f);
        return true;
    }

    /** A criatura cresce com o talento de Invocação, com o nível da habilidade (+10% por nível) e com o nível do jogador (+4% por nível). */
    private static boolean invocar(ServerPlayer p, HabilidadeDef h, int nivelHab) {
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(h.efeito()));
        if (tipo == null) { avisar(p, "Criatura desconhecida: " + h.efeito(), ChatFormatting.RED); return false; }
        ServerLevel nivel = p.serverLevel();
        double talento = Talentos.fator(p, Talentos.Atributo.INVOCACAO);
        int nh = Math.max(1, nivelHab), nj = Progressao.temDados(p) ? Math.max(1, Progressao.dados(p).nivel()) : 1;
        double forca = talento * (1 + 0.10 * (nh - 1)) * (1 + 0.04 * (nj - 1));
        long expira = nivel.getGameTime() + (long) Math.max(100, h.duracao() * 20 * talento * (1 + 0.05 * (nh - 1)));
        int qtd = Math.max(1, h.potencia());
        int criados = 0;
        for (int i = 0; i < qtd; i++) {
            Entity e = tipo.create(nivel);
            if (e == null) continue;
            double ang = Math.PI * 2 * i / qtd + ThreadLocalRandom.current().nextDouble(0.5);
            Vec3 pos = p.position().add(Math.cos(ang) * 2, 0.1, Math.sin(ang) * 2);
            e.moveTo(pos.x, pos.y, pos.z, p.getYRot(), 0);
            if (e instanceof Mob m) {
                m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.MOB_SUMMONED, null, null);
                m.setPersistenceRequired();
                if (forca > 1) {
                    var vida = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (vida != null) vida.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier("FRX invocação", forca - 1, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
                    var dano = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (dano != null) dano.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier("FRX invocação", forca - 1, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
                    m.setHealth(m.getMaxHealth());
                }
            }
            if (e instanceof TamableAnimal t) t.tame(p);
            if (e instanceof IronGolem g) g.setPlayerCreated(true);
            e.addTag(TAG_INVOCADO);
            e.getPersistentData().putLong("frx_expira", expira);
            e.getPersistentData().putUUID("frx_dono", p.getUUID());
            e.setCustomName(Component.literal("Invocação de " + p.getGameProfile().getName() + " · Nv " + nh).withStyle(ChatFormatting.LIGHT_PURPLE));
            nivel.addFreshEntity(e);
            invocados.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(e.getUUID());
            nivel.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.5, pos.z, 20, 0.4, 0.6, 0.4, 0.05);
            criados++;
        }
        if (criados == 0) return false;
        nivel.playSound(null, p.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1f, 1f);
        return true;
    }

    // ------------------------------------------------------------------ utilidades

    /** Quem a habilidade pode ferir: tudo vivo menos jogadores, aldeões, invocações, bichos domados e golens de jogador. */
    static boolean inimigo(ServerPlayer p, LivingEntity e) {
        if (e instanceof Player || e instanceof AbstractVillager || e instanceof ArmorStand) return false;
        if (e.getTags().contains(TAG_INVOCADO)) return false;
        if (e instanceof TamableAnimal t && t.isTame()) return false;
        return !(e instanceof IronGolem g && g.isPlayerCreated());
    }

    static LivingEntity mirar(ServerPlayer p, double alcance, double folga, boolean aliados) {
        Vec3 olho = p.getEyePosition();
        Vec3 fim = pontoBloco(p, alcance);
        LivingEntity melhor = null;
        double melhorD = Double.MAX_VALUE;
        AABB area = new AABB(olho, fim).inflate(folga + 1);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, area,
                x -> x != p && x.isAlive() && !x.isSpectator() && (aliados ? x instanceof Player : inimigo(p, x)))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(folga).clip(olho, fim);
            if (hit.isPresent()) {
                double dd = olho.distanceToSqr(hit.get());
                if (dd < melhorD) { melhorD = dd; melhor = e; }
            }
        }
        return melhor;
    }

    static Vec3 pontoBloco(ServerPlayer p, double alcance) {
        Vec3 olho = p.getEyePosition();
        Vec3 fim = olho.add(p.getLookAngle().scale(alcance));
        BlockHitResult r = p.level().clip(new ClipContext(olho, fim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return r.getType() == HitResult.Type.MISS ? fim : r.getLocation();
    }

    static void ferir(ServerPlayer p, LivingEntity alvo, double dano, HabilidadeDef h) {
        float v = (float) dano;
        if ("SAGRADO".equals(h.elemento()) && alvo.getMobType() == MobType.UNDEAD) v *= 2;
        DamageSource fonte = "FISICO".equals(h.elemento()) ? p.damageSources().playerAttack(p) : p.damageSources().indirectMagic(p, p);
        alvo.invulnerableTime = 0;
        emHabilidade = true;
        try { alvo.hurt(fonte, v); } finally { emHabilidade = false; }
        marcarCombate(p);
        switch (h.elemento()) {
            case "FOGO" -> alvo.setSecondsOnFire(4);
            case "GELO" -> alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            case "SOMBRA" -> alvo.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            case "NATUREZA" -> { if (h.efeito().isBlank()) alvo.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0)); }
            case "RAIO" -> {
                LightningBolt raio = EntityType.LIGHTNING_BOLT.create(p.serverLevel());
                if (raio != null) {
                    raio.moveTo(alvo.position());
                    raio.setVisualOnly(true);
                    p.serverLevel().addFreshEntity(raio);
                }
            }
            default -> { }
        }
        if (!"BUFF".equals(h.tipo())) aplicarEfeito(alvo, h);
        // invocações vão atrás do alvo
        for (UUID id : invocados.getOrDefault(p.getUUID(), Set.of())) {
            if (p.serverLevel().getEntity(id) instanceof Mob m && alvo.isAlive()) m.setTarget(alvo);
        }
    }

    private static void aplicarEfeito(LivingEntity alvo, HabilidadeDef h) {
        if (h.efeito().isBlank() || "INVOCAR".equals(h.tipo())) return;
        MobEffect ef = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(h.efeito()));
        if (ef != null) alvo.addEffect(new MobEffectInstance(ef, (int) Math.max(20, h.duracao() * 20), Math.max(0, h.potencia() - 1)));
    }

    private static void curar(LivingEntity alvo, double valor) {
        alvo.heal((float) valor);
        if (alvo.level() instanceof ServerLevel nivel)
            nivel.sendParticles(ParticleTypes.HEART, alvo.getX(), alvo.getY() + 1.2, alvo.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
    }

    static ParticleOptions particula(String elemento) {
        return switch (elemento) {
            case "FOGO" -> ParticleTypes.FLAME;
            case "GELO" -> ParticleTypes.SNOWFLAKE;
            case "RAIO" -> ParticleTypes.ELECTRIC_SPARK;
            case "SAGRADO" -> ParticleTypes.END_ROD;
            case "SOMBRA" -> ParticleTypes.LARGE_SMOKE;
            case "NATUREZA" -> ParticleTypes.HAPPY_VILLAGER;
            case "ARCANO" -> ParticleTypes.WITCH;
            default -> ParticleTypes.CRIT;
        };
    }

    static void som(ServerPlayer p, String elemento) {
        SoundEvent s = switch (elemento) {
            case "FOGO" -> SoundEvents.BLAZE_SHOOT;
            case "GELO" -> SoundEvents.GLASS_BREAK;
            case "RAIO" -> SoundEvents.TRIDENT_THUNDER;
            case "SAGRADO" -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            case "SOMBRA" -> SoundEvents.WITHER_SHOOT;
            case "ARCANO" -> SoundEvents.EVOKER_CAST_SPELL;
            default -> SoundEvents.PLAYER_ATTACK_STRONG;
        };
        p.level().playSound(null, p.blockPosition(), s, SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    static void linha(ServerLevel nivel, Vec3 de, Vec3 ate, ParticleOptions part) {
        Vec3 d = ate.subtract(de);
        int passos = (int) Math.min(120, d.length() * 2);
        for (int i = 0; i <= passos; i++) {
            Vec3 ponto = de.add(d.scale(passos == 0 ? 0 : (double) i / passos));
            nivel.sendParticles(part, ponto.x, ponto.y, ponto.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Verdadeiro enquanto o dano em curso vem de uma habilidade (o soco do Monge não soma em cima). */
    static boolean emHabilidade;

    /** Soma (ou tira) recurso do jogador, dentro dos limites, e atualiza a barra. */
    public static void darRecurso(ServerPlayer p, double quanto) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        if (recursoDa(d.classe()).isEmpty()) return;
        d.definirRecurso(Math.max(0, Math.min(maximo(p), d.recurso() + quanto)));
        enviarRecurso(p, false);
    }

    /** Encurta as recargas em andamento do jogador (fracao 1 = zera todas). */
    public static void encurtarRecargas(ServerPlayer p, double fracao) {
        Map<String, Long> rc = recargas.get(p.getUUID());
        if (rc == null) return;
        long agora = System.currentTimeMillis();
        for (Map.Entry<String, Long> en : rc.entrySet()) {
            long resta = en.getValue() - agora;
            if (resta <= 0) continue;
            long novo = Math.max(0, (long) (resta * (1 - Math.max(0, Math.min(1, fracao)))));
            en.setValue(agora + novo);
            enviar(p, new SyncRecarga(en.getKey(), (int) novo, (int) Math.max(1, resta)));
        }
    }

    static void marcarCombate(ServerPlayer p) {
        ultimoCombate.put(p.getUUID(), System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks % 10 != 0) return;
        long agora = System.currentTimeMillis();
        for (ServerPlayer p : evento.getServer().getPlayerList().getPlayers()) {
            if (!Progressao.temDados(p)) continue;
            DadosJogador d = Progressao.dados(p);
            String r = recursoDa(d.classe());
            if (r.isEmpty()) continue;
            int max = maximo(p);
            boolean combate = agora - ultimoCombate.getOrDefault(p.getUUID(), 0L) < COMBATE_MS;
            double porSegundo = switch (r) {
                case "MANA" -> combate ? 1 + max * 0.01 : 3 + max * 0.03;
                case "ENERGIA" -> 10;
                case "CHI" -> combate ? 0 : -3; // o chi só vem dos socos e esfria fora de combate
                default -> combate ? 0 : -4; // fúria esvazia fora de combate
            };
            if (porSegundo > 0) porSegundo *= Talentos.fator(p, Talentos.Atributo.REGEN) * (1 + br.com.frxcode.frxworld.racas.RacasJogador.num(p, "regenRecurso") / 100);
            d.definirRecurso(Math.max(0, Math.min(max, d.recurso() + porSegundo / 2)));
            enviarRecurso(p, false);
        }
        if (ticks % 20 == 0) expirarInvocados(evento.getServer());
    }

    private static void expirarInvocados(MinecraftServer server) {
        for (Map.Entry<UUID, Set<UUID>> ent : invocados.entrySet()) {
            ent.getValue().removeIf(id -> {
                for (ServerLevel nivel : server.getAllLevels()) {
                    Entity e = nivel.getEntity(id);
                    if (e == null) continue;
                    if (!e.isAlive()) return true;
                    if (nivel.getGameTime() >= e.getPersistentData().getLong("frx_expira")) {
                        nivel.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 15, 0.4, 0.5, 0.4, 0.02);
                        e.discard();
                        return true;
                    }
                    return false;
                }
                return true;
            });
            if (ent.getValue().isEmpty()) invocados.remove(ent.getKey());
        }
    }

    /** Invocação que ficou num chunk descarregado e já venceu some ao carregar. */
    @SubscribeEvent
    public void aoEntrarNoMundo(EntityJoinLevelEvent evento) {
        Entity e = evento.getEntity();
        if (evento.getLevel().isClientSide() || !e.getTags().contains(TAG_INVOCADO)) return;
        if (evento.getLevel().getGameTime() >= e.getPersistentData().getLong("frx_expira")) evento.setCanceled(true);
    }

    @SubscribeEvent
    public void aoFerir(LivingHurtEvent evento) {
        LivingEntity vitima = evento.getEntity();
        if (evento.getSource().getEntity() instanceof ServerPlayer atacante && atacante != vitima) {
            marcarCombate(atacante);
            ganharFuria(atacante, 6);
            for (UUID id : invocados.getOrDefault(atacante.getUUID(), Set.of())) {
                if (atacante.serverLevel().getEntity(id) instanceof Mob m && !(vitima instanceof Player) && !vitima.getTags().contains(TAG_INVOCADO)) m.setTarget(vitima);
            }
        }
        if (vitima instanceof ServerPlayer p) {
            marcarCombate(p);
            ganharFuria(p, 4);
            if (evento.getSource().getEntity() instanceof LivingEntity agressor && !(agressor instanceof Player)) {
                for (UUID id : invocados.getOrDefault(p.getUUID(), Set.of())) {
                    if (p.serverLevel().getEntity(id) instanceof Mob m && m != agressor) m.setTarget(agressor);
                }
            }
        }
    }

    static void ganharFuria(ServerPlayer p, double quanto) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        if (!"FURIA".equals(recursoDa(d.classe()))) return;
        d.definirRecurso(Math.min(maximo(p), d.recurso() + quanto));
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent evento) {
        if (evento.getEntity() instanceof ServerPlayer p) {
            aprenderPorNivel(p);
            if (Progressao.temDados(p) && Progressao.dados(p).classe().isEmpty())
                p.sendSystemMessage(Component.literal("Você ainda não tem classe. Aperte K para escolher.").withStyle(ChatFormatting.GOLD));
        }
    }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent evento) {
        if (evento.getEntity() instanceof ServerPlayer p) sincronizar(p);
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent evento) {
        UUID id = evento.getEntity().getUUID();
        ultimoEnviado.remove(id);
        ultimoCombate.remove(id);
    }

    // ------------------------------------------------------------------ comandos de admin

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("classe")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("classe", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                java.util.stream.Stream.concat(CLASSES.stream().map(String::toLowerCase), java.util.stream.Stream.of("nenhuma")), b))
                                        .executes(ctx -> {
                                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                            String c = StringArgumentType.getString(ctx, "classe").toUpperCase();
                                            if (c.equals("NENHUMA")) c = "";
                                            if (!c.isEmpty() && !CLASSES.contains(c)) {
                                                ctx.getSource().sendFailure(Component.literal("Classe inválida."));
                                                return 0;
                                            }
                                            escolherClasse(alvo, c, true);
                                            String fc = c;
                                            ctx.getSource().sendSuccess(() -> Component.literal(alvo.getGameProfile().getName() + " agora: " + nomeClasse(fc)), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("tomo")
                        .executes(ctx -> darTomo(ctx.getSource().getPlayerOrException(), ""))
                        .then(Commands.argument("habilidade", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(catalogo.keySet(), b))
                                .executes(ctx -> darTomo(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "habilidade"))))));
    }

    private static int darTomo(ServerPlayer p, String codigo) {
        ItemStack tomo = new ItemStack(Blocos.TOMO_ARCANO.get());
        if (!codigo.isEmpty()) {
            HabilidadeDef h = catalogo.get(codigo);
            if (h == null) { p.sendSystemMessage(Component.literal("Habilidade não encontrada: " + codigo).withStyle(ChatFormatting.RED)); return 0; }
            tomo.getOrCreateTag().putString(ItemTomo.NBT, codigo);
            tomo.getOrCreateTag().putString("frx_nome", h.nome() + " (" + nomeClasse(h.classe()) + ")");
        }
        if (!p.getInventory().add(tomo)) p.drop(tomo, false);
        return 1;
    }

    /** Usado pelo cliente/HUD e por outros sistemas. */
    public static Map<String, HabilidadeDef> catalogo() {
        return catalogo;
    }

    static Map<String, Long> recargasDe(UUID id) {
        return recargas.getOrDefault(id, new HashMap<>());
    }
}
