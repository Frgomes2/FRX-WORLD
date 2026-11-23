package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.progressao.Conquistas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O Dragão do Fim vira chefão de Rank A: nível 80, 2500 de vida, golpes em dobro e, abaixo do nível do Rank A,
 * os ataques do jogador mal arranham. Enquanto ele vive, a ilha manda lacaios (e de vez em quando um chefão)
 * contra quem estiver lutando. Quem ajudou (causou dano) ganha 50 mil moedas de ouro no banco e a conquista Matador de Dragões.
 */
public class DragaoFim {
    private static final String MARCA = "frx_dragao", LACAIO = "frx_lacaio_dragao", AJUDANTES = "frx_dragao_ajudantes";
    private static final int NIVEL = 80, RANK = 5;                 // Rank A
    private static final double VIDA = 2500, ARMADURA = 12, DANO = 2.0;
    private static final long PREMIO = 5_000_000;                  // 50 mil moedas de ouro por jogador, direto no banco
    private static final int RAIO = 180, LACAIOS_POR_JOGADOR = 4, LACAIOS_MAX = 16, CHANCE_CHEFE = 20;
    private static final String[] LACAIOS = {"minecraft:enderman", "minecraft:enderman", "minecraft:phantom", "minecraft:vex", "minecraft:endermite"};
    private static final UUID ID_VIDA = id("dragao_vida"), ID_ARMADURA = id("dragao_armadura");

    public DragaoFim() {}

    private static UUID id(String n) { return UUID.nameUUIDFromBytes(("frxworld:" + n).getBytes(StandardCharsets.UTF_8)); }

    private static int nivelMinimo() { return ConfigJogo.nivelMinDoRank(RANK); }

    private static void somar(EnderDragon d, net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, double valor) {
        AttributeInstance inst = d.getAttribute(a);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, "FRX dragão", valor, AttributeModifier.Operation.ADDITION));
    }

    /** Antes das zonas (prioridade alta): nível fixo e atributos próprios. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoNascer(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !(ev.getEntity() instanceof EnderDragon d) || ev.getLevel().dimension() != Level.END) return;
        Zonas.marcarNivel(d, NIVEL);
        AttributeInstance vida = d.getAttribute(Attributes.MAX_HEALTH);
        if (vida != null) somar(d, Attributes.MAX_HEALTH, ID_VIDA, VIDA - vida.getBaseValue());
        somar(d, Attributes.ARMOR, ID_ARMADURA, ARMADURA);
        if (!d.getPersistentData().getBoolean(MARCA)) {
            d.getPersistentData().putBoolean(MARCA, true);
            d.setHealth(d.getMaxHealth());
            if (ev.getLevel() instanceof ServerLevel n && !n.players().isEmpty())
                n.getServer().getPlayerList().broadcastSystemMessage(Component.literal("🐉 O Dragão do Fim despertou. Só aventureiros de Rank A têm chance contra ele.")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void dano(LivingHurtEvent ev) {
        if (ev.getEntity().level().isClientSide()) return;
        // golpes, sopro e bolas de fogo do dragão
        if (ev.getEntity() instanceof ServerPlayer && ev.getSource().getEntity() instanceof EnderDragon) {
            ev.setAmount((float) (ev.getAmount() * DANO));
            return;
        }
        if (ev.getEntity() instanceof EnderDragon d && d.level().dimension() == Level.END && ev.getSource().getEntity() instanceof ServerPlayer p) {
            anotar(d, p);
            if (!Progressao.temDados(p) || Progressao.dados(p).nivel() >= nivelMinimo()) return;
            ev.setAmount(ev.getAmount() * 0.2f);
            if (p.getRandom().nextInt(4) == 0) p.displayClientMessage(Component.literal("O Dragão do Fim é para aventureiros de nível " + nivelMinimo()
                    + " ou mais: seus golpes mal arranham.").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof EnderDragon d) || !(d.level() instanceof ServerLevel n) || n.dimension() != Level.END) return;
        List<String> nomes = new ArrayList<>();
        net.minecraft.nbt.ListTag lista = d.getPersistentData().getList(AJUDANTES, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < lista.size(); i++) {
            ServerPlayer p;
            try { p = n.getServer().getPlayerList().getPlayer(UUID.fromString(lista.getString(i))); } catch (IllegalArgumentException e) { continue; }
            if (p == null) continue;
            Banco.definirSaldo(p, Banco.saldo(p) + PREMIO);
            Conquistas.maximo(p, "c:dragao_end", 1);
            ConjuntosLendarios.darMaterial(p, br.com.frxcode.frxworld.armas.Lendarias.FRAGMENTO.get(), 4);
            nomes.add(p.getGameProfile().getName());
            br.com.frxcode.frxworld.FrxWorld.LOG.info("Dragão do Fim: {} recebeu {} cobre no banco", p.getGameProfile().getName(), PREMIO);
            Sistema.avisar(p, "O Dragão do Fim caiu", "Você ajudou a derrotá-lo: " + String.format(java.util.Locale.ROOT, "%,d", PREMIO / 100).replace(',', '.')
                    + " moedas de ouro foram guardadas no seu banco.", Sistema.OURO);
        }
        for (Mob m : lacaios(n)) m.discard();
        if (!nomes.isEmpty())
            n.getServer().getPlayerList().broadcastSystemMessage(Component.literal("🐉 O Dragão do Fim foi derrotado por " + String.join(", ", nomes) + "!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    /** Guarda no próprio dragão quem causou dano (sobrevive a reinícios). */
    private static void anotar(EnderDragon d, ServerPlayer p) {
        net.minecraft.nbt.ListTag lista = d.getPersistentData().getList(AJUDANTES, net.minecraft.nbt.Tag.TAG_STRING);
        String id = p.getUUID().toString();
        for (int i = 0; i < lista.size(); i++) if (lista.getString(i).equals(id)) return;
        lista.add(net.minecraft.nbt.StringTag.valueOf(id));
        d.getPersistentData().put(AJUDANTES, lista);
    }

    private static List<Mob> lacaios(ServerLevel n) {
        return n.getEntitiesOfClass(Mob.class, new AABB(-RAIO * 2, n.getMinBuildHeight(), -RAIO * 2, RAIO * 2, n.getMaxBuildHeight(), RAIO * 2),
                m -> m.isAlive() && m.getTags().contains(LACAIO));
    }

    /** A cada 25 s, enquanto o dragão vive, a ilha manda reforços contra quem está lutando. */
    @SubscribeEvent
    public void reforcos(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 500 != 123) return;
        ServerLevel n = ev.getServer().getLevel(Level.END);
        if (n == null || n.players().isEmpty() || n.getDragons().isEmpty()) return;
        List<ServerPlayer> lutando = n.players().stream().filter(p -> !p.isCreative() && !p.isSpectator() && p.isAlive()
                && p.getX() * p.getX() + p.getZ() * p.getZ() < (double) RAIO * RAIO).toList();
        if (lutando.isEmpty()) return;
        int vivos = lacaios(n).size(), teto = Math.min(LACAIOS_MAX, LACAIOS_POR_JOGADOR * lutando.size());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (ServerPlayer p : lutando) {
            if (vivos >= teto) break;
            try {
                if (rnd.nextInt(100) < CHANCE_CHEFE) {
                    BlockPos pe = chao(n, p, rnd);
                    if (pe != null) {
                        Mob chefe = Chefes.criar(n, Vec3.atBottomCenterOf(pe), RANK, NIVEL + 5, 0, "fim");
                        if (chefe != null) {
                            chefe.addTag(LACAIO);
                            chefe.setTarget(p);
                            vivos++;
                            n.playSound(null, pe, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5f, 1.4f);
                            Sistema.avisar(p, "Guardião do Dragão", chefe.getName().getString() + " veio defender o dragão.", Sistema.ROXO);
                        }
                    }
                }
                int qtd = rnd.nextInt(2, 4);
                for (int k = 0; k < qtd && vivos < teto; k++) if (lacaio(n, p, rnd)) vivos++;
            } catch (Exception e) {
                br.com.frxcode.frxworld.FrxWorld.LOG.warn("Reforços do dragão falharam: {}", e.toString());
            }
        }
    }

    private static boolean lacaio(ServerLevel n, ServerPlayer p, ThreadLocalRandom rnd) {
        Criaturas.Def cria = Criaturas.sortearMonstro("fim", RANK);
        String id = cria != null ? cria.entidade() : LACAIOS[rnd.nextInt(LACAIOS.length)];
        EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
        Entity e = tipo == null ? null : tipo.create(n);
        if (!(e instanceof Mob m)) return false;
        boolean voa = id.equals("minecraft:phantom") || id.equals("minecraft:vex");
        BlockPos pe = voa ? p.blockPosition().offset(rnd.nextInt(-8, 9), rnd.nextInt(6, 12), rnd.nextInt(-8, 9)) : chao(n, p, rnd);
        if (pe == null) return false;
        m.moveTo(pe.getX() + 0.5, pe.getY(), pe.getZ() + 0.5, rnd.nextFloat() * 360, 0);
        m.finalizeSpawn(n, n.getCurrentDifficultyAt(pe), MobSpawnType.EVENT, null, null);
        if (cria != null) Criaturas.aplicar(m, cria);
        m.addTag(LACAIO);
        m.setTarget(p);
        return n.addFreshEntity(m);
    }

    /** Chão firme da ilha (nunca o vazio) de 6 a 14 blocos do jogador. */
    private static BlockPos chao(ServerLevel n, ServerPlayer p, ThreadLocalRandom rnd) {
        for (int t = 0; t < 30; t++) {
            double ang = rnd.nextDouble() * Math.PI * 2, dist = rnd.nextDouble(6, 14);
            int x = p.getBlockX() + (int) Math.round(Math.cos(ang) * dist), z = p.getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
            BlockPos pe = n.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (pe.getY() < 40 || Math.abs(pe.getY() - p.getBlockY()) > 12) continue;
            if (n.getBlockState(pe.below()).isSolidRender(n, pe.below())) return pe;
        }
        return null;
    }
}
