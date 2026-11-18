package br.com.frxcode.frxworld.habilidades;

import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exército de sombras do Invocador (estilo "Levante-se!" de Solo Leveling):
 * - quando o Invocador (ou uma sombra dele) mata uma criatura, a alma fica no lugar por alguns segundos;
 * - gastando mana ele tenta erguê-la ("/sombra erguer" ou clique no chat) — pode falhar, 3 tentativas;
 * - a sombra fica guardada no exército e pode ser invocada quando quiser como aliada,
 *   mas mais fraca que o original (vida e dano bem menores), ficando mais forte conforme mata.
 */
public class Sombras {
    public static final String TAG = "frx_sombra";
    private static final String NBT = "frx_sombras";
    private static final int JANELA_TICKS = 20 * 20;      // a alma espera 20 s
    private static final int TENTATIVAS = 3;
    private static final long DESCANSO_MS = 60_000;       // sombra derrotada volta depois de 1 min

    public Sombras() {}

    private record Alma(EntityType<?> tipo, String nome, float vida, boolean chefe, ServerLevel nivel, Vec3 pos, long expira, int[] tentativas) {}

    private static final Map<UUID, List<Alma>> ALMAS = new HashMap<>();
    private int ticks;

    // ------------------------------------------------------------------ regras (quanto custa, quantas cabem)

    private static boolean ehInvocador(ServerPlayer p) {
        return Progressao.temDados(p) && "INVOCADOR".equals(Progressao.dados(p).classe());
    }

    static int nivel(ServerPlayer p) { return Progressao.dados(p).nivel(); }

    static int vagas(ServerPlayer p) { return Math.min(30, 2 + nivel(p) / 6); }

    static int ativasMax(ServerPlayer p) { return Math.min(10, 1 + nivel(p) / 10); }

    static int custoErguer(Alma a) { return (int) Math.round((15 + a.vida() * 0.6) * (a.chefe() ? 2 : 1)); }

    static int custoInvocar(float vida) { return (int) Math.round(8 + vida * 0.15); }

    static double chance(ServerPlayer p, Alma a) {
        double c = 0.95 - a.vida() / (40.0 + nivel(p) * 10.0);
        if (a.chefe()) c *= 0.5;
        return Math.max(0.10, Math.min(0.95, c));
    }

    /** Força da sombra em relação ao original: 40% no nível 0 até 70% no nível 10. */
    static double forca(int nivelSombra) { return 0.40 + 0.03 * Math.min(10, nivelSombra); }

    static int nivelSombra(int abates) { return Math.min(10, (int) Math.floor(Math.sqrt(abates / 3.0))); }

    // ------------------------------------------------------------------ dados do exército (NBT do jogador)

    private static ListTag exercito(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(NBT)) pers.put(NBT, new ListTag());
        return pers.getList(NBT, Tag.TAG_COMPOUND);
    }

    private static CompoundTag porId(Player p, String id) {
        for (Tag t : exercito(p)) if (t instanceof CompoundTag c && c.getString("id").equals(id)) return c;
        return null;
    }

    private static Entity ativa(ServerPlayer p, CompoundTag s) {
        if (!s.hasUUID("ent")) return null;
        Entity e = p.serverLevel().getEntity(s.getUUID("ent"));
        return e != null && e.isAlive() ? e : null;
    }

    private static int contarAtivas(ServerPlayer p) {
        int n = 0;
        for (Tag t : exercito(p)) if (t instanceof CompoundTag c && ativa(p, c) != null) n++;
        return n;
    }

    // ------------------------------------------------------------------ almas no chão

    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoMorrer(LivingDeathEvent e) {
        LivingEntity morto = e.getEntity();
        if (morto.level().isClientSide() || !(morto.level() instanceof ServerLevel nivel)) return;

        // uma sombra caiu: descansa e volta depois
        if (morto.getTags().contains(TAG)) {
            UUID dono = morto.getPersistentData().hasUUID("frx_dono") ? morto.getPersistentData().getUUID("frx_dono") : null;
            ServerPlayer p = dono == null ? null : nivel.getServer().getPlayerList().getPlayer(dono);
            if (p != null) {
                CompoundTag s = porId(p, morto.getPersistentData().getString("frx_sombra_id"));
                if (s != null) {
                    s.putLong("descansa", System.currentTimeMillis() + DESCANSO_MS);
                    s.remove("ent");
                    p.displayClientMessage(Component.literal("⚫ " + s.getString("nome") + " voltou às sombras para se recompor (1 min).").withStyle(ChatFormatting.DARK_PURPLE), true);
                    sincronizar(p);
                }
            }
            return;
        }

        if (morto instanceof Player || morto instanceof AbstractVillager || morto.getTags().contains(Habilidades.TAG_INVOCADO)
                || morto.getTags().contains("frx_npc") || morto.getTags().contains("frx_cidadao")) return;
        if (morto instanceof TamableAnimal t && t.isTame()) return;

        ServerPlayer p = null;
        Entity matador = e.getSource().getEntity();
        if (matador instanceof ServerPlayer sp) {
            p = sp;
            // o dono matou: as sombras dele que estão em campo e por perto também aprendem (antes só contava o golpe final da própria sombra)
            boolean mudou = false;
            ListTag ex = exercito(sp);
            for (int i = 0; i < ex.size(); i++) {
                CompoundTag s = ex.getCompound(i);
                if (!s.hasUUID("ent") || !(nivel.getEntity(s.getUUID("ent")) instanceof Mob sombra) || !sombra.isAlive() || sombra.distanceToSqr(morto) > 24 * 24) continue;
                int antes = nivelSombra(s.getInt("abates"));
                s.putInt("abates", s.getInt("abates") + 1);
                int depois = nivelSombra(s.getInt("abates"));
                mudou = true;
                if (depois > antes) {
                    sp.sendSystemMessage(Component.literal("⚫ " + s.getString("nome") + " subiu para o nível " + depois + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
                    aplicarForca(sombra, s.getFloat("vida"), depois);
                }
            }
            if (mudou) sincronizar(sp);
        }
        else if (matador != null && matador.getTags().contains(TAG) && matador.getPersistentData().hasUUID("frx_dono")) {
            p = nivel.getServer().getPlayerList().getPlayer(matador.getPersistentData().getUUID("frx_dono"));
            CompoundTag s = p == null ? null : porId(p, matador.getPersistentData().getString("frx_sombra_id"));
            if (s != null) {
                int antes = nivelSombra(s.getInt("abates"));
                s.putInt("abates", s.getInt("abates") + 1);
                int depois = nivelSombra(s.getInt("abates"));
                if (depois > antes) {
                    p.sendSystemMessage(Component.literal("⚫ " + s.getString("nome") + " subiu para o nível " + depois + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
                    if (matador instanceof Mob m) aplicarForca(m, s.getFloat("vida"), depois);
                }
                sincronizar(p);
            }
        }
        if (p == null || !ehInvocador(p)) return;

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(morto.getType());
        if (id == null) return;
        Alma a = new Alma(morto.getType(), morto.getType().getDescription().getString(), morto.getMaxHealth(),
                morto.getType().is(Tags.EntityTypes.BOSSES), nivel, morto.position(), nivel.getGameTime() + JANELA_TICKS, new int[]{0});
        List<Alma> lista = ALMAS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        lista.add(0, a);
        while (lista.size() > 5) lista.remove(lista.size() - 1);

        int custo = custoErguer(a);
        MutableComponent msg = Component.literal("⚫ A sombra de " + a.nome() + " aguarda... ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal("[Levante-se! · " + custo + " mana · " + Math.round(chance(p, a) * 100) + "%]")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sombra erguer"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Erguer esta criatura como sombra do seu exército (20 s)")))));
        p.sendSystemMessage(msg);
    }

    // sombras não deixam itens nem XP
    @SubscribeEvent
    public void semDrops(LivingDropsEvent e) { if (e.getEntity().getTags().contains(TAG)) e.setCanceled(true); }

    @SubscribeEvent
    public void semXp(LivingExperienceDropEvent e) { if (e.getEntity().getTags().contains(TAG)) e.setCanceled(true); }

    // sombras nunca atacam o dono, jogadores, aldeões ou outras invocações
    @SubscribeEvent
    public void alvo(LivingChangeTargetEvent e) {
        if (!e.getEntity().getTags().contains(TAG)) return;
        LivingEntity alvo = e.getNewTarget();
        if (alvo == null) return;
        if (alvo instanceof Player || alvo instanceof AbstractVillager || alvo.getTags().contains(Habilidades.TAG_INVOCADO)
                || alvo.getTags().contains("frx_npc") || alvo.getTags().contains("frx_cidadao") || (alvo instanceof TamableAnimal t && t.isTame()))
            e.setCanceled(true);
    }

    // ------------------------------------------------------------------ alvo marcado pelo Invocador

    private static final String FOCO = "frx_foco";
    /** Invocador → criatura que ele mandou as sombras e invocações focarem. */
    private static final Map<UUID, UUID> FOCOS = new HashMap<>();

    private static List<Mob> tropa(ServerPlayer p) {
        List<Mob> l = new ArrayList<>();
        for (UUID id : Habilidades.invocadosDe(p.getUUID())) if (p.serverLevel().getEntity(id) instanceof Mob m && m.isAlive()) l.add(m);
        return l;
    }

    private static void soltarFoco(ServerPlayer p, boolean avisar) {
        boolean tinha = FOCOS.remove(p.getUUID()) != null;
        for (Mob m : tropa(p)) { m.getPersistentData().remove(FOCO); if (tinha) m.setTarget(null); }
        if (avisar) avisar(p, tinha ? "Alvo solto: as sombras voltam a atacar o que estiver por perto." : "Nenhuma criatura na mira para marcar.", ChatFormatting.GRAY);
    }

    /** Tecla de marcar alvo: a criatura na mira vira o foco de todas as sombras e invocações; sem nada na mira, solta o foco. */
    static void marcarAlvo(ServerPlayer p) {
        LivingEntity alvo = Habilidades.mirar(p, 40, 0.6, false);
        if (alvo == null || alvo.getTags().contains("frx_npc") || alvo.getTags().contains("frx_cidadao")) { soltarFoco(p, true); return; }
        if (alvo.getUUID().equals(FOCOS.get(p.getUUID()))) { soltarFoco(p, true); return; }   // marcar o mesmo de novo desmarca
        FOCOS.put(p.getUUID(), alvo.getUUID());
        List<Mob> tropa = tropa(p);
        for (Mob m : tropa) { m.getPersistentData().putUUID(FOCO, alvo.getUUID()); m.setTarget(alvo); }
        alvo.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
        p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, alvo.getX(), alvo.getY() + alvo.getBbHeight() + 0.4, alvo.getZ(), 12, 0.2, 0.2, 0.2, 0.01);
        p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7f, 0.6f);
        avisar(p, "⚫ Alvo marcado: " + alvo.getDisplayName().getString() + (tropa.isEmpty() ? " (nenhuma sombra invocada agora)" : " — " + tropa.size() + (tropa.size() == 1 ? " sombra foca nele" : " sombras focam nele")), ChatFormatting.LIGHT_PURPLE);
    }

    /** Enquanto houver alvo marcado e vivo, a sombra não troca de alvo. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void manterFoco(LivingChangeTargetEvent e) {
        LivingEntity eu = e.getEntity();
        if (eu.level().isClientSide() || !eu.getPersistentData().hasUUID(FOCO) || !(eu.level() instanceof net.minecraft.server.level.ServerLevel nv)) return;
        Entity foco = nv.getEntity(eu.getPersistentData().getUUID(FOCO));
        if (!(foco instanceof LivingEntity vivo) || !vivo.isAlive()) { eu.getPersistentData().remove(FOCO); return; }
        if (e.getNewTarget() != vivo) e.setCanceled(true);
    }

    private static void tickFocos(net.minecraft.server.MinecraftServer s) {
        for (var it = FOCOS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            Entity foco = p == null ? null : p.serverLevel().getEntity(en.getValue());
            if (p == null || !(foco instanceof LivingEntity vivo) || !vivo.isAlive() || vivo.distanceTo(p) > 64) {
                it.remove();
                if (p != null) {
                    for (Mob m : tropa(p)) m.getPersistentData().remove(FOCO);
                    avisar(p, "⚫ O alvo marcado caiu ou ficou longe: as sombras voltam a atacar o que estiver por perto.", ChatFormatting.GRAY);
                }
                continue;
            }
            for (Mob m : tropa(p)) {   // cobre as sombras invocadas depois da marcação e as que perderam o alvo
                if (!m.getPersistentData().hasUUID(FOCO)) m.getPersistentData().putUUID(FOCO, vivo.getUUID());
                if (m.getTarget() != vivo) m.setTarget(vivo);
            }
        }
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 10 != 0) return;
        if (!FOCOS.isEmpty()) tickFocos(e.getServer());
        // almas brilhando no chão
        for (List<Alma> lista : ALMAS.values()) {
            lista.removeIf(a -> a.nivel().getGameTime() > a.expira() || a.tentativas()[0] >= TENTATIVAS);
            for (Alma a : lista) a.nivel().sendParticles(ParticleTypes.SOUL, a.pos().x, a.pos().y + 0.4, a.pos().z, 3, 0.3, 0.4, 0.3, 0.01);
        }
        if (ticks % 20 != 0) return;
        // fumaça escura nas sombras ativas
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            for (Tag t : exercito(p)) {
                if (!(t instanceof CompoundTag s)) continue;
                Entity ent = ativa(p, s);
                if (ent == null) continue;
                p.serverLevel().sendParticles(ParticleTypes.SMOKE, ent.getX(), ent.getY() + ent.getBbHeight() * 0.6, ent.getZ(), 4, 0.25, 0.4, 0.25, 0.005);
                p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ent.getX(), ent.getY() + 0.1, ent.getZ(), 1, 0.2, 0.05, 0.2, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------ erguer, invocar, guardar

    static void erguer(ServerPlayer p) {
        if (!ehInvocador(p)) { avisar(p, "Só o Invocador comanda as sombras.", ChatFormatting.RED); return; }
        List<Alma> lista = ALMAS.getOrDefault(p.getUUID(), List.of());
        Alma a = null;
        for (Alma x : lista) if (x.nivel() == p.serverLevel() && x.pos().distanceTo(p.position()) < 16) { a = x; break; }
        if (a == null) { avisar(p, "Nenhuma alma por perto. Mate uma criatura e use logo em seguida (20 s).", ChatFormatting.GRAY); return; }
        if (a.chefe() && nivel(p) < 25) { avisar(p, "A alma de um chefe é forte demais. Precisa do nível 25.", ChatFormatting.RED); return; }
        ListTag ex = exercito(p);
        if (ex.size() >= vagas(p)) { avisar(p, "Seu exército está cheio (" + ex.size() + "/" + vagas(p) + "). Liberte uma sombra com /sombra.", ChatFormatting.RED); return; }
        DadosJogador d = Progressao.dados(p);
        int custo = custoErguer(a);
        if (d.recurso() < custo) { avisar(p, "Mana insuficiente: precisa de " + custo + ".", ChatFormatting.RED); return; }
        d.definirRecurso(d.recurso() - custo);
        Habilidades.atualizarRecurso(p);
        a.tentativas()[0]++;
        ServerLevel nv = p.serverLevel();
        nv.sendParticles(ParticleTypes.SCULK_SOUL, a.pos().x, a.pos().y + 0.5, a.pos().z, 25, 0.4, 0.8, 0.4, 0.03);
        if (ThreadLocalRandom.current().nextDouble() >= chance(p, a)) {
            nv.playSound(null, BlockPos.containing(a.pos()), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2f, 0.8f);
            int resta = TENTATIVAS - a.tentativas()[0];
            avisar(p, "A sombra de " + a.nome() + " resistiu..." + (resta > 0 ? " (" + resta + " tentativa" + (resta > 1 ? "s" : "") + ")" : " e se foi."), ChatFormatting.DARK_PURPLE);
            return;
        }
        lista.remove(a);
        CompoundTag s = new CompoundTag();
        s.putString("id", UUID.randomUUID().toString().substring(0, 8));
        s.putString("tipo", String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(a.tipo())));
        s.putString("nome", a.nome());
        s.putFloat("vida", a.vida());
        s.putBoolean("chefe", a.chefe());
        s.putInt("abates", 0);
        s.putLong("criada", System.currentTimeMillis());
        ex.add(s);
        nv.playSound(null, BlockPos.containing(a.pos()), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.8f, 1.3f);
        nv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, a.pos().x, a.pos().y + 0.2, a.pos().z, 40, 0.5, 1.0, 0.5, 0.05);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("LEVANTE-SE!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(a.nome() + " agora serve você").withStyle(ChatFormatting.LIGHT_PURPLE)));
        p.sendSystemMessage(Component.literal("⚫ Nova sombra: " + a.nome() + " (" + ex.size() + "/" + vagas(p) + "). ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(botao("[Invocar agora]", "/sombra invocar " + s.getString("id"), "Chamar " + a.nome())));
        sincronizar(p);
    }

    static void invocar(ServerPlayer p, String id) {
        if (!ehInvocador(p)) { avisar(p, "Só o Invocador comanda as sombras.", ChatFormatting.RED); return; }
        CompoundTag s = porId(p, id);
        if (s == null) { avisar(p, "Sombra não encontrada.", ChatFormatting.RED); return; }
        if (ativa(p, s) != null) { avisar(p, s.getString("nome") + " já está ao seu lado.", ChatFormatting.GRAY); return; }
        long descansa = s.getLong("descansa") - System.currentTimeMillis();
        if (descansa > 0) { avisar(p, s.getString("nome") + " ainda se recompõe (" + (descansa / 1000 + 1) + " s).", ChatFormatting.GRAY); return; }
        if (contarAtivas(p) >= ativasMax(p)) { avisar(p, "Você só consegue manter " + ativasMax(p) + " sombra(s) ao mesmo tempo no seu nível.", ChatFormatting.RED); return; }
        DadosJogador d = Progressao.dados(p);
        int custo = custoInvocar(s.getFloat("vida"));
        if (d.recurso() < custo) { avisar(p, "Mana insuficiente: precisa de " + custo + ".", ChatFormatting.RED); return; }
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(s.getString("tipo")));
        if (tipo == null) { avisar(p, "Essa criatura não existe mais no modpack.", ChatFormatting.RED); return; }
        ServerLevel nv = p.serverLevel();
        Entity e = tipo.create(nv);
        if (!(e instanceof Mob m)) { if (e != null) e.discard(); avisar(p, "Essa sombra não consegue tomar forma.", ChatFormatting.RED); return; }
        d.definirRecurso(d.recurso() - custo);
        Habilidades.atualizarRecurso(p);

        double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        Vec3 pos = p.position().add(Math.cos(ang) * 2, 0.1, Math.sin(ang) * 2);
        m.moveTo(pos.x, pos.y, pos.z, p.getYRot(), 0);
        m.finalizeSpawn(nv, nv.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.MOB_SUMMONED, null, null);
        m.setPersistenceRequired();
        int ns = nivelSombra(s.getInt("abates"));
        aplicarForca(m, s.getFloat("vida"), ns);
        m.addTag(TAG);
        m.addTag(Habilidades.TAG_INVOCADO);
        m.getPersistentData().putUUID("frx_dono", p.getUUID());
        m.getPersistentData().putString("frx_sombra_id", s.getString("id"));
        m.getPersistentData().putLong("frx_expira", Long.MAX_VALUE);
        m.setCustomName(Component.literal("⚫ " + s.getString("nome") + " · Nv " + ns).withStyle(ChatFormatting.DARK_PURPLE));
        m.setCustomNameVisible(true);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        m.targetSelector.removeAllGoals(g -> true);
        m.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(m, LivingEntity.class, 10, true, false,
                x -> x instanceof Enemy && !x.getTags().contains(Habilidades.TAG_INVOCADO) && !x.getTags().contains("frx_npc")));
        m.goalSelector.addGoal(1, new SeguirDono(m, p.getUUID()));
        nv.addFreshEntity(m);
        s.putUUID("ent", m.getUUID());
        Habilidades.registrarInvocado(p, m);
        nv.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.5, pos.z, 25, 0.4, 0.8, 0.4, 0.04);
        nv.playSound(null, BlockPos.containing(pos), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.8f, 0.6f);
        avisar(p, "⚫ " + s.getString("nome") + " atende ao seu chamado. (−" + custo + " mana)", ChatFormatting.LIGHT_PURPLE);
    }

    static void aplicarForca(Mob m, float vidaOriginal, int nivelSombra) {
        double f = forca(nivelSombra);
        UUID uid = UUID.nameUUIDFromBytes("frxworld:sombra".getBytes());
        var vida = m.getAttribute(Attributes.MAX_HEALTH);
        if (vida != null) { vida.removeModifier(uid); vida.addPermanentModifier(new AttributeModifier(uid, "FRX sombra", f - 1, AttributeModifier.Operation.MULTIPLY_TOTAL)); }
        var dano = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dano != null) { dano.removeModifier(uid); dano.addPermanentModifier(new AttributeModifier(uid, "FRX sombra", f - 1, AttributeModifier.Operation.MULTIPLY_TOTAL)); }
        m.setHealth(m.getMaxHealth());
        if (m.hasCustomName()) m.setCustomName(Component.literal(m.getCustomName().getString().replaceAll("Nv \\d+$", "Nv " + nivelSombra)).withStyle(ChatFormatting.DARK_PURPLE));
    }

    static int guardar(ServerPlayer p, String id) {
        int n = 0;
        for (Tag t : exercito(p)) {
            if (!(t instanceof CompoundTag s) || (!id.equals("todas") && !s.getString("id").equals(id))) continue;
            Entity e = ativa(p, s);
            if (e != null) {
                p.serverLevel().sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
                e.discard();
                n++;
            }
            s.remove("ent");
        }
        return n;
    }

    static void libertar(ServerPlayer p, String id) {
        ListTag ex = exercito(p);
        for (int i = 0; i < ex.size(); i++) {
            CompoundTag s = ex.getCompound(i);
            if (!s.getString("id").equals(id)) continue;
            guardar(p, id);
            ex.remove(i);
            CompoundTag at = atalhos(p);
            for (int k = 0; k < ATALHOS; k++) if (at.getString("a" + k).equals(id)) at.putString("a" + k, "");
            avisar(p, "⚫ " + s.getString("nome") + " foi libertada do seu exército.", ChatFormatting.GRAY);
            return;
        }
        avisar(p, "Sombra não encontrada.", ChatFormatting.RED);
    }

    static void mostrar(ServerPlayer p) {
        ListTag ex = exercito(p);
        p.sendSystemMessage(Component.literal("⚫ Exército de sombras · " + ex.size() + "/" + vagas(p) + " · ao mesmo tempo: " + contarAtivas(p) + "/" + ativasMax(p))
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        if (ex.isEmpty()) {
            p.sendSystemMessage(Component.literal("  Mate uma criatura e clique em [Levante-se!] em até 20 s para erguer sua primeira sombra.").withStyle(ChatFormatting.GRAY));
            return;
        }
        long agora = System.currentTimeMillis();
        for (int i = 0; i < ex.size(); i++) {
            CompoundTag s = ex.getCompound(i);
            String id = s.getString("id");
            int ns = nivelSombra(s.getInt("abates"));
            boolean viva = ativa(p, s) != null;
            long desc = s.getLong("descansa") - agora;
            MutableComponent l = Component.literal("  " + (i + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal((s.getBoolean("chefe") ? "☠ " : "") + s.getString("nome")).withStyle(viva ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE))
                    .append(Component.literal(" Nv " + ns + " · " + s.getInt("abates") + " abates · " + Math.round(forca(ns) * 100) + "% da força ").withStyle(ChatFormatting.GRAY));
            if (viva) l.append(botao("[Guardar]", "/sombra guardar " + id, "Mandar de volta às sombras"));
            else if (desc > 0) l.append(Component.literal("(volta em " + (desc / 1000 + 1) + " s)").withStyle(ChatFormatting.DARK_GRAY));
            else l.append(botao("[Invocar · " + custoInvocar(s.getFloat("vida")) + " mana]", "/sombra invocar " + id, "Chamar ao seu lado"));
            l.append(" ").append(Component.literal("[Libertar]").withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sombra libertar " + id))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Remove para sempre (confirme com Enter)")))));
            p.sendSystemMessage(l);
        }
        p.sendSystemMessage(Component.literal("  ").append(botao("[Invocar todas]", "/sombra todas", "Chamar o máximo que você consegue manter"))
                .append(" ").append(botao("[Guardar todas]", "/sombra guardar todas", "Mandar todas de volta")));
    }

    private static Component botao(String texto, String cmd, String dica) {
        return Component.literal(texto).withStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(dica))));
    }

    private static void avisar(ServerPlayer p, String t, ChatFormatting cor) {
        p.displayClientMessage(Component.literal(t).withStyle(cor), true);
    }

    /** Para a ficha do painel. */
    public static JsonArray json(ServerPlayer p) {
        JsonArray a = new JsonArray();
        for (Tag t : exercito(p)) {
            if (!(t instanceof CompoundTag s)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", s.getString("id"));
            o.addProperty("tipo", s.getString("tipo"));
            o.addProperty("nome", s.getString("nome"));
            o.addProperty("abates", s.getInt("abates"));
            o.addProperty("nivel", nivelSombra(s.getInt("abates")));
            o.addProperty("chefe", s.getBoolean("chefe"));
            o.addProperty("ativa", ativa(p, s) != null);
            a.add(o);
        }
        return a;
    }

    // sair do jogo ou trocar de dimensão recolhe as sombras
    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { guardar(p, "todas"); ALMAS.remove(p.getUUID()); }
    }

    @SubscribeEvent
    public void aoTrocarDimensao(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) guardar(p, "todas");
    }


    // ------------------------------------------------------------------ tela do cliente (Habilidades → Exército) e teclas rápidas

    private static final int ATALHOS = 3;

    private static CompoundTag atalhos(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains("frx_sombras_atalhos")) pers.put("frx_sombras_atalhos", new CompoundTag());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        return pers.getCompound("frx_sombras_atalhos");
    }

    /** Manda o exército atualizado para a tela do jogador. */
    public static void sincronizar(ServerPlayer p) {
        if (!ehInvocador(p)) return;
        List<br.com.frxcode.frxworld.rede.SyncSombras.Info> l = new ArrayList<>();
        long agora = System.currentTimeMillis();
        for (Tag t : exercito(p)) {
            if (!(t instanceof CompoundTag s)) continue;
            Entity e = ativa(p, s);
            long desc = s.getLong("descansa") - agora;
            int ns = nivelSombra(s.getInt("abates"));
            float vida = e instanceof LivingEntity le ? le.getHealth() : 0, max = e instanceof LivingEntity le2 ? le2.getMaxHealth() : 0;
            l.add(new br.com.frxcode.frxworld.rede.SyncSombras.Info(s.getString("id"), s.getString("tipo"), s.getString("nome"), ns, s.getInt("abates"),
                    (int) Math.round(forca(ns) * 100), s.getBoolean("chefe"), e != null ? 1 : desc > 0 ? 2 : 0, desc > 0 ? (int) (desc / 1000 + 1) : 0,
                    custoInvocar(s.getFloat("vida")), vida, max));
        }
        CompoundTag at = atalhos(p);
        List<String> a = new ArrayList<>();
        for (int i = 0; i < ATALHOS; i++) a.add(at.getString("a" + i));
        br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new br.com.frxcode.frxworld.rede.SyncSombras(vagas(p), contarAtivas(p), ativasMax(p), l, a));
    }

    static void invocarTodas(ServerPlayer p) {
        if (!ehInvocador(p)) { avisar(p, "Só o Invocador comanda as sombras.", ChatFormatting.RED); return; }
        if (exercito(p).isEmpty()) { avisar(p, "Seu exército está vazio. Mate uma criatura e use /levantese.", ChatFormatting.GRAY); return; }
        int antes = contarAtivas(p);
        for (Tag t : new ArrayList<>(exercito(p))) {
            if (contarAtivas(p) >= ativasMax(p)) break;
            if (t instanceof CompoundTag s && ativa(p, s) == null && s.getLong("descansa") < System.currentTimeMillis()) invocar(p, s.getString("id"));
        }
        int n = contarAtivas(p) - antes;
        if (n > 1) avisar(p, "⚫ " + n + " sombras atendem ao seu chamado. (" + contarAtivas(p) + "/" + ativasMax(p) + ")", ChatFormatting.LIGHT_PURPLE);
    }

    private static void guardarAvisando(ServerPlayer p, String id) {
        int n = guardar(p, id);
        avisar(p, n > 0 ? "⚫ " + n + " sombra(s) voltaram às trevas." : "Nenhuma sombra ativa.", ChatFormatting.DARK_PURPLE);
    }

    /** Pedido da tela ou das teclas rápidas. */
    public static void acao(ServerPlayer p, int acao, int numero, String id) {
        if (acao != br.com.frxcode.frxworld.rede.AcaoSombra.PEDIR && !ehInvocador(p)) { avisar(p, "Só o Invocador comanda as sombras.", ChatFormatting.RED); return; }
        switch (acao) {
            case br.com.frxcode.frxworld.rede.AcaoSombra.MARCAR_ALVO -> marcarAlvo(p);
            case br.com.frxcode.frxworld.rede.AcaoSombra.INVOCAR -> invocar(p, id);
            case br.com.frxcode.frxworld.rede.AcaoSombra.GUARDAR -> guardarAvisando(p, id);
            case br.com.frxcode.frxworld.rede.AcaoSombra.INVOCAR_TODAS -> invocarTodas(p);
            case br.com.frxcode.frxworld.rede.AcaoSombra.GUARDAR_TODAS -> guardarAvisando(p, "todas");
            case br.com.frxcode.frxworld.rede.AcaoSombra.LIBERTAR -> libertar(p, id);
            case br.com.frxcode.frxworld.rede.AcaoSombra.DEFINIR_ATALHO -> {
                if (numero < 0 || numero >= ATALHOS) return;
                CompoundTag at = atalhos(p);
                for (int i = 0; i < ATALHOS; i++) if (!id.isEmpty() && at.getString("a" + i).equals(id)) at.putString("a" + i, "");   // uma sombra em um atalho só
                at.putString("a" + numero, id);
            }
            case br.com.frxcode.frxworld.rede.AcaoSombra.USAR_ATALHO -> {
                if (numero == ATALHOS) {   // tecla "todas": se alguma está fora, recolhe; senão chama todas
                    if (contarAtivas(p) > 0) guardarAvisando(p, "todas"); else invocarTodas(p);
                } else if (numero >= 0 && numero < ATALHOS) {
                    String sid = atalhos(p).getString("a" + numero);
                    CompoundTag s = sid.isEmpty() ? null : porId(p, sid);
                    if (s == null) avisar(p, "Atalho " + (numero + 1) + " vazio. Escolha a sombra em Habilidades → Exército.", ChatFormatting.GRAY);
                    else if (ativa(p, s) != null) guardarAvisando(p, sid);
                    else invocar(p, sid);
                }
            }
            default -> { }
        }
        sincronizar(p);
    }

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("sombra")
                .executes(ctx -> { mostrar(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("erguer").executes(ctx -> { erguer(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("invocar").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> { invocar(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")); return 1; })))
                .then(Commands.literal("todas").executes(ctx -> { invocarTodas(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("guardar").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    int n = guardar(p, StringArgumentType.getString(ctx, "id"));
                    avisar(p, n > 0 ? "⚫ " + n + " sombra(s) voltaram às trevas." : "Nenhuma sombra ativa.", ChatFormatting.DARK_PURPLE);
                    return 1;
                })))
                .then(Commands.literal("libertar").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> { libertar(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")); return 1; }))));
        evento.getDispatcher().register(Commands.literal("levantese").executes(ctx -> { erguer(ctx.getSource().getPlayerOrException()); return 1; }));
    }

    /** A sombra segue o dono e se teletransporta se ficar muito longe. */
    static final class SeguirDono extends Goal {
        private final Mob m;
        private final UUID dono;
        private int recalcular;

        SeguirDono(Mob m, UUID dono) {
            this.m = m;
            this.dono = dono;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private ServerPlayer dono() {
            return m.level() instanceof ServerLevel nv && nv.getPlayerByUUID(dono) instanceof ServerPlayer p ? p : null;
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = dono();
            if (p == null) return false;
            double d = m.distanceToSqr(p);
            return d > (m.getTarget() == null ? 36 : 400);
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer p = dono();
            return p != null && m.distanceToSqr(p) > 9 && (m.getTarget() == null || m.distanceToSqr(p) > 400);
        }

        @Override
        public void tick() {
            ServerPlayer p = dono();
            if (p == null) return;
            if (m.distanceToSqr(p) > 32 * 32) {
                m.moveTo(p.getX() + ThreadLocalRandom.current().nextDouble(-2, 2), p.getY(), p.getZ() + ThreadLocalRandom.current().nextDouble(-2, 2), m.getYRot(), 0);
                m.getNavigation().stop();
                return;
            }
            if (--recalcular <= 0) {
                recalcular = 10;
                m.getNavigation().moveTo(p, 1.25);
            }
            m.getLookControl().setLookAt(p, 10f, m.getMaxHeadXRot());
        }

        @Override
        public void stop() { m.getNavigation().stop(); }
    }
}
