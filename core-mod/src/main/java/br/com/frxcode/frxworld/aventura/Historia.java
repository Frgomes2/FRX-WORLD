package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * História principal: capítulos em sequência, cada um com um objetivo e uma recompensa.
 * O progresso fica no jogador (frx_historia). O último capítulo é a luta contra o Rei Demônio.
 */
public class Historia {
    private static final String CHAVE = "frx_historia", TAG_REI = "frx_rei_demonio", TAG_LACAIO = "frx_lacaio_rei";

    private enum Tipo { GUILDA, RANK, NIVEL, VISITAR, CONTADOR, TORRE, REI }

    private record Cap(String titulo, String texto, Tipo tipo, String alvo, int qtd, long moedas, long xp, String premioTitulo) { }

    private static final List<Cap> CAPS = List.of(
            new Cap("O Despertar", "Você renasceu neste mundo sem nome nem passado. A Guilda de Aventureiros é onde toda lenda começa.", Tipo.GUILDA, "", 1, 200, 150, ""),
            new Cap("Primeiro Sangue", "Os arredores estão infestados. Prove que sabe lutar.", Tipo.CONTADOR, "matar_total", 30, 300, 300, ""),
            new Cap("A Federação", "Dizem que no oeste um slime fundou uma nação de monstros. Vá ver com os próprios olhos.", Tipo.VISITAR, "tempest", 1, 500, 500, ""),
            new Cap("Prova de Valor", "Ninguém respeita um Rank F. Suba na Guilda.", Tipo.RANK, "", 1, 600, 600, ""),
            new Cap("Fendas no Céu", "Portais se abrem perto das cidades e despejam monstros. Alguém precisa fechá-los.", Tipo.CONTADOR, "portais", 1, 1000, 1000, "Selador de Fendas"),
            new Cap("O Martelo e a Montanha", "Os anões de Dwargon forjam as melhores armas. Você vai precisar delas.", Tipo.VISITAR, "dwargon", 1, 800, 800, ""),
            new Cap("Guardiões Antigos", "As masmorras escondem guardiões que não deveriam ter acordado. Derrote dois.", Tipo.CONTADOR, "chefes", 2, 1500, 1500, ""),
            new Cap("A Floresta dos Elfos", "Os magos de Sarion estudam os portais. Talvez saibam de onde eles vêm.", Tipo.VISITAR, "sarion", 1, 1000, 1000, ""),
            new Cap("O Império do Leste", "O Imperador reúne exércitos. Descubra de que lado ele está.", Tipo.VISITAR, "leste", 1, 1200, 1200, ""),
            new Cap("Sangue de Veterano", "A estrada daqui em diante não perdoa os fracos.", Tipo.NIVEL, "", 30, 2000, 2000, "Veterano"),
            new Cap("A Torre", "Uma torre sem fim guarda os segredos do mundo antigo. Chegue ao primeiro andar seguro.", Tipo.TORRE, "", 10, 2500, 2500, ""),
            new Cap("Entre a Elite", "Só os melhores recebem as missões que importam. Alcance o Rank C.", Tipo.RANK, "", 3, 3000, 3000, ""),
            new Cap("Terras Sombrias", "Os portais vêm do reino dos demônios. Atravesse a fronteira.", Tipo.VISITAR, "demonios", 1, 3500, 3500, ""),
            new Cap("O Trono Escarlate", "Dentro do reino dos demônios, use /historia desafio e enfrente o Rei Demônio. Leve aliados.", Tipo.REI, "", 1, 20000, 15000, "Matador do Rei Demônio"));

    // ------------------------------------------------------------------ progresso

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    public static int capitulo(Player p) { return Math.max(0, Math.min(CAPS.size(), dados(p).getInt("cap"))); }

    private static Reino reino(String id) {
        for (Reino r : Reinos.todos()) if (r.id().equals(id)) return r;
        return null;
    }

    private static int contador(ServerPlayer p, String chave) {
        return Progressao.temDados(p) ? Progressao.dados(p).contadores().getOrDefault(chave, 0) : 0;
    }

    /** Quanto do objetivo já foi feito (0..qtd). */
    private static int progresso(ServerPlayer p, Cap c) {
        return switch (c.tipo()) {
            case GUILDA -> Guilda.registrado(p) ? 1 : 0;
            case RANK -> Math.min(c.qtd(), Guilda.indice(p));
            case NIVEL -> Progressao.temDados(p) ? Math.min(c.qtd(), Progressao.dados(p).nivel()) : 0;
            case VISITAR -> {
                Reino r = reino(c.alvo());
                yield r != null && p.level() == p.server.overworld() && r.distancia(p.getBlockX(), p.getBlockZ()) == 0 ? 1 : 0;
            }
            case CONTADOR -> Math.max(0, Math.min(c.qtd(), contador(p, c.alvo()) - dados(p).getInt("base")));
            case TORRE -> Math.min(c.qtd(), Torre.recorde(p));
            case REI -> 0;
        };
    }

    private static String objetivo(ServerPlayer p, Cap c) {
        int feito = progresso(p, c);
        return switch (c.tipo()) {
            case GUILDA -> "Registre-se na Guilda de Aventureiros (recepcionista, em qualquer capital)";
            case RANK -> "Alcance o Rank " + ConfigJogo.nomeRank(c.qtd()) + " da Guilda (você é Rank " + Guilda.rank(p) + ")";
            case NIVEL -> "Chegue ao nível " + c.qtd() + " (" + feito + "/" + c.qtd() + ")";
            case VISITAR -> {
                Reino r = reino(c.alvo());
                yield r == null ? "Viaje até o reino" : "Viaje até " + r.nome() + " (perto de " + r.centroX() + ", " + r.centroZ() + ")";
            }
            case CONTADOR -> switch (c.alvo()) {
                case "matar_total" -> "Derrote " + c.qtd() + " monstros (" + feito + "/" + c.qtd() + ")";
                case "portais" -> "Ajude a fechar " + c.qtd() + " portal (" + feito + "/" + c.qtd() + ")";
                case "chefes" -> "Derrote " + c.qtd() + " chefes (" + feito + "/" + c.qtd() + ")";
                default -> c.alvo() + " " + feito + "/" + c.qtd();
            };
            case TORRE -> "Chegue ao andar " + c.qtd() + " da Torre Infinita (seu recorde: " + feito + ")";
            case REI -> "Derrote o Rei Demônio (/historia desafio, dentro do reino dos demônios)";
        };
    }

    private static void apresentar(ServerPlayer p) {
        int n = capitulo(p);
        if (n >= CAPS.size()) return;
        Cap c = CAPS.get(n);
        Sistema.avisar(p, "Capítulo " + (n + 1) + " — " + c.titulo(), c.texto(), Sistema.AZUL);
        p.sendSystemMessage(Component.literal("📖 Capítulo " + (n + 1) + " — " + c.titulo()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("   " + c.texto()).withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("   Objetivo: " + objetivo(p, c)).withStyle(ChatFormatting.WHITE));
        p.sendSystemMessage(Component.literal("   Recompensa: " + Moedas.texto(c.moedas()) + " e " + c.xp() + " XP"
                + (c.premioTitulo().isEmpty() ? "" : " + título «" + c.premioTitulo() + "»") + "   (/historia)").withStyle(ChatFormatting.GOLD));
    }

    private static void prepararCapitulo(ServerPlayer p) {
        int n = capitulo(p);
        CompoundTag d = dados(p);
        d.putBoolean("visto", false);
        if (n < CAPS.size() && CAPS.get(n).tipo() == Tipo.CONTADOR) d.putInt("base", contador(p, CAPS.get(n).alvo()));
    }

    private static void concluir(ServerPlayer p) {
        int n = capitulo(p);
        if (n >= CAPS.size()) return;
        Cap c = CAPS.get(n);
        dados(p).putInt("cap", n + 1);
        br.com.frxcode.frxworld.progressao.Marcos.marcar(p, "historia_" + (n + 1));
        Moedas.dar(p, c.moedas());
        Progressao.ganharXp(p, c.xp());
        Temporadas.pontuar(p, "historia", 1);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        Sistema.avisar(p, "Capítulo " + (n + 1) + " concluído: " + c.titulo(), "Recompensa: " + Moedas.texto(c.moedas()) + " e " + c.xp() + " XP"
                + (c.premioTitulo().isEmpty() ? "." : ". Novo título: «" + c.premioTitulo() + "» (/titulo)."), Sistema.OURO);
        if (!c.premioTitulo().isEmpty() || n + 1 == CAPS.size())
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("📖 " + p.getGameProfile().getName() + " concluiu o capítulo " + (n + 1) + " da história: " + c.titulo() + "!")
                    .withStyle(ChatFormatting.AQUA), false);
        if (n + 1 == CAPS.size())
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("👑 " + p.getGameProfile().getName() + " derrotou o Rei Demônio e terminou a história principal!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        prepararCapitulo(p);
        Progressao.enviarAoPainel(p, false);
    }

    /** Títulos ganhos na história. */
    public static List<String> titulos(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        int n = capitulo(p);
        for (int i = 0; i < n && i < CAPS.size(); i++) if (!CAPS.get(i).premioTitulo().isEmpty()) l.add(CAPS.get(i).premioTitulo());
        return l;
    }

    // ------------------------------------------------------------------ Rei Demônio

    private static UUID rei;
    private static ServerBossEvent barra;
    private static int reiTicks, semJogador;
    private static final Set<UUID> lacaios = new HashSet<>();

    private static void encerrarLuta(MinecraftServer s, boolean remover) {
        for (ServerLevel l : s.getAllLevels()) {
            if (remover && rei != null) { Entity e = l.getEntity(rei); if (e != null) e.discard(); }
            for (UUID u : lacaios) { Entity e = l.getEntity(u); if (e != null) e.discard(); }
        }
        lacaios.clear();
        rei = null;
        if (barra != null) { barra.removeAllPlayers(); barra = null; }
    }

    private static int desafiar(ServerPlayer p) {
        int n = capitulo(p);
        if (n != CAPS.size() - 1) { p.sendSystemMessage(Component.literal("Você ainda não chegou ao último capítulo. Veja /historia.").withStyle(ChatFormatting.GRAY)); return 0; }
        Reino r = reino("demonios");
        if (r == null || p.level() != p.server.overworld() || r.distancia(p.getBlockX(), p.getBlockZ()) != 0) {
            p.sendSystemMessage(Component.literal("O Rei Demônio só responde ao desafio dentro do reino dos demônios.").withStyle(ChatFormatting.RED)); return 0;
        }
        if (rei != null) { p.sendSystemMessage(Component.literal("O Rei Demônio já está em combate.").withStyle(ChatFormatting.RED)); return 0; }
        ServerLevel nivel = p.serverLevel();
        WitherSkeleton m = EntityType.WITHER_SKELETON.create(nivel);
        if (m == null) return 0;
        Vec3 frente = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(5));
        m.moveTo(frente.x, p.getY(), frente.z, p.getYRot() + 180, 0);
        m.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        var vida = m.getAttribute(Attributes.MAX_HEALTH);
        if (vida != null) vida.setBaseValue(350);
        var dano = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dano != null) dano.setBaseValue(12);
        var kb = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1);
        var alcance = m.getAttribute(Attributes.FOLLOW_RANGE);
        if (alcance != null) alcance.setBaseValue(48);
        Zonas.aplicarNivel(m, Math.min(ConfigJogo.zonaNivelMax(), 45));
        m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        for (EquipmentSlot sl : EquipmentSlot.values()) m.setDropChance(sl, 0f);
        m.setCustomName(Component.literal("Rei Demônio").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        m.addTag(TAG_REI);
        rei = m.getUUID();
        reiTicks = 0; semJogador = 0;
        nivel.addFreshEntity(m);
        m.setTarget(p);
        barra = new ServerBossEvent(Component.literal("Rei Demônio").withStyle(ChatFormatting.DARK_RED), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        nivel.playSound(null, m.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3f, 0.7f);
        nivel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getY() + 1, m.getZ(), 120, 1, 1.5, 1, 0.1);
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("👑 " + p.getGameProfile().getName() + " desafiou o Rei Demônio! Quem estiver no capítulo final e por perto compartilha a vitória.")
                .withStyle(ChatFormatting.DARK_RED), false);
        return 1;
    }

    private static void tickRei(MinecraftServer s) {
        ServerLevel nivel = s.overworld();
        if (!(nivel.getEntity(rei) instanceof Mob m)) { if (++semJogador > 60) encerrarLuta(s, true); return; }
        List<ServerPlayer> perto = nivel.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && p.distanceTo(m) <= 64).toList();
        if (barra != null) {
            barra.setProgress(Math.max(0, Math.min(1, m.getHealth() / m.getMaxHealth())));
            for (ServerPlayer p : new ArrayList<>(barra.getPlayers())) if (!perto.contains(p)) barra.removePlayer(p);
            for (ServerPlayer p : perto) barra.addPlayer(p);
        }
        if (perto.isEmpty()) {
            if (++semJogador > 90) {
                s.getPlayerList().broadcastSystemMessage(Component.literal("👑 O Rei Demônio voltou ao trono. Ninguém ficou para lutar.").withStyle(ChatFormatting.GRAY), false);
                encerrarLuta(s, true);
            }
            return;
        }
        semJogador = 0;
        if (m.getTarget() == null || !m.getTarget().isAlive()) m.setTarget(perto.get(nivel.random.nextInt(perto.size())));
        reiTicks++;
        if (reiTicks % 12 != 0) return;
        lacaios.removeIf(u -> !(nivel.getEntity(u) instanceof Mob l) || !l.isAlive());
        boolean furia = m.getHealth() < m.getMaxHealth() * 0.4f;
        if ((reiTicks / 12) % 2 == 0 && lacaios.size() < (furia ? 8 : 5)) {
            for (int i = 0; i < (furia ? 3 : 2); i++) {
                WitherSkeleton l = EntityType.WITHER_SKELETON.create(nivel);
                if (l == null) continue;
                double ang = nivel.random.nextDouble() * Math.PI * 2;
                l.moveTo(m.getX() + Math.cos(ang) * 3, m.getY(), m.getZ() + Math.sin(ang) * 3, 0, 0);
                l.finalizeSpawn(nivel, nivel.getCurrentDifficultyAt(l.blockPosition()), MobSpawnType.EVENT, null, null);
                Zonas.aplicarNivel(l, 30);
                l.addTag(TAG_LACAIO);
                l.setCustomName(Component.literal("Guarda Escarlate").withStyle(ChatFormatting.RED));
                nivel.addFreshEntity(l);
                lacaios.add(l.getUUID());
                nivel.sendParticles(ParticleTypes.SOUL, l.getX(), l.getY() + 1, l.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
            }
            nivel.playSound(null, m.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2f, 0.6f);
        } else {
            double raio = furia ? 10 : 7;
            for (int i = 0; i < 48; i++) {
                double ang = Math.PI * 2 * i / 48;
                nivel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX() + Math.cos(ang) * raio, m.getY() + 0.3, m.getZ() + Math.sin(ang) * raio, 1, 0, 0.1, 0, 0);
            }
            nivel.playSound(null, m.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2f, 0.5f);
            for (ServerPlayer p : perto) {
                if (p.distanceTo(m) > raio) continue;
                p.hurt(m.damageSources().mobAttack(m), furia ? 10 : 6);
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                Vec3 fora = p.position().subtract(m.position()).multiply(1, 0, 1);
                fora = fora.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : fora.normalize();
                p.setDeltaMovement(fora.x * 1.2, 0.5, fora.z * 1.2);
                p.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ev.getServer();
        if (rei != null && s.getTickCount() % 20 == 3) {
            try { tickRei(s); } catch (Exception e) { encerrarLuta(s, true); }
        }
        if (s.getTickCount() % 40 != 11) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!Progressao.temDados(p) || !p.isAlive()) continue;
            int n = capitulo(p);
            if (n >= CAPS.size()) continue;
            CompoundTag d = dados(p);
            if (!d.getBoolean("visto")) {
                if (p.tickCount < 300) continue;   // deixa a abertura e o login passarem
                if (CAPS.get(n).tipo() == Tipo.CONTADOR && !d.contains("base")) d.putInt("base", contador(p, CAPS.get(n).alvo()));
                d.putBoolean("visto", true);
                for (int i = 1; i <= n; i++) br.com.frxcode.frxworld.progressao.Marcos.marcar(p, "historia_" + i);   // livro de missões em dia
                apresentar(p);
                continue;
            }
            Cap c = CAPS.get(n);
            if (c.tipo() != Tipo.REI && progresso(p, c) >= c.qtd()) concluir(p);
        }
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        Entity e = ev.getEntity();
        if (rei == null || e.level().isClientSide() || !e.getUUID().equals(rei) || !(e.level() instanceof ServerLevel nivel)) return;
        for (ServerPlayer p : nivel.players()) {
            if (p.distanceTo(e) > 64 || capitulo(p) != CAPS.size() - 1) continue;
            concluir(p);
            Tesouros.talvezDar(p, 100);
        }
        nivel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, e.getX(), e.getY() + 1, e.getZ(), 3, 1, 1, 1, 0);
        encerrarLuta(nivel.getServer(), false);
    }

    @SubscribeEvent
    public void aoEntrarNoMundo(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide()) return;
        if (e.getTags().contains(TAG_REI) && (rei == null || !e.getUUID().equals(rei))) ev.setCanceled(true);
        if (e.getTags().contains(TAG_LACAIO) && rei == null) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) { encerrarLuta(ev.getServer(), true); }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) dados(p).putBoolean("visto", false);   // relembra o capítulo a cada login
    }

    @SubscribeEvent
    public void aoClonar(PlayerEvent.Clone ev) { }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("historia")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int n = capitulo(p);
                    if (n >= CAPS.size()) { p.sendSystemMessage(Component.literal("📖 Você terminou a história principal. O mundo lembra do seu nome.").withStyle(ChatFormatting.GOLD)); return 1; }
                    apresentar(p);
                    p.sendSystemMessage(Component.literal("   Capítulo " + (n + 1) + " de " + CAPS.size() + ".").withStyle(ChatFormatting.DARK_GRAY));
                    return 1;
                })
                .then(Commands.literal("desafio").executes(c -> desafiar(c.getSource().getPlayerOrException()))));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("historia").then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("capitulo", IntegerArgumentType.integer(1, CAPS.size() + 1)).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "jogador");
                            dados(p).putInt("cap", IntegerArgumentType.getInteger(c, "capitulo") - 1);
                            dados(p).remove("base");
                            prepararCapitulo(p);
                            c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " agora está no capítulo " + (capitulo(p) + 1) + "."), true);
                            return 1;
                        })))));
    }
}
