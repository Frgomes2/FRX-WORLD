package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.progressao.Zonas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O Nether caça quem entra: de tempos em tempos uma emboscada nasce perto de cada jogador que está lá.
 * Na maioria das vezes é uma horda; às vezes, um chefão (aventura/Chefes) do rank do jogador.
 * Soma-se à dureza da dimensão (Zonas.DUREZA_PADRAO) e ao rank mínimo para entrar.
 */
public class Inferno {
    private static final String[] HORDA = {"minecraft:wither_skeleton", "minecraft:wither_skeleton", "minecraft:blaze", "minecraft:piglin_brute", "minecraft:hoglin", "minecraft:magma_cube"};
    private static final long PRIMEIRA_MIN = 3, INTERVALO_MIN = 6, INTERVALO_MAX = 11;
    private static final int CHANCE_CHEFE = 25;   // % das emboscadas
    private static final Map<UUID, Long> proxima = new HashMap<>();

    public Inferno() {}

    @SubscribeEvent
    public void aCada30s(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 600 != 17) return;
        ServerLevel nether = ev.getServer().getLevel(Level.NETHER);
        if (nether == null) return;
        long agora = System.currentTimeMillis();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        proxima.keySet().removeIf(u -> { ServerPlayer p = ev.getServer().getPlayerList().getPlayer(u); return p == null || p.level() != nether; });
        for (ServerPlayer p : nether.players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            Long quando = proxima.get(p.getUUID());
            if (quando == null) { proxima.put(p.getUUID(), agora + PRIMEIRA_MIN * 60_000); continue; }
            if (agora < quando) continue;
            proxima.put(p.getUUID(), agora + rnd.nextLong(INTERVALO_MIN, INTERVALO_MAX + 1) * 60_000);
            try { emboscada(nether, p, rnd); } catch (Exception e) { br.com.frxcode.frxworld.FrxWorld.LOG.warn("Emboscada no Nether falhou: {}", e.toString()); }
        }
    }

    /** Chão firme com dois blocos livres em cima, de 7 a 14 blocos do jogador. null se não achou. */
    private static BlockPos lugar(ServerLevel n, ServerPlayer p, ThreadLocalRandom rnd) {
        for (int t = 0; t < 40; t++) {
            double ang = rnd.nextDouble() * Math.PI * 2, dist = rnd.nextDouble(7, 14);
            int x = p.getBlockX() + (int) Math.round(Math.cos(ang) * dist), z = p.getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos pe = new BlockPos(x, p.getBlockY() + dy, z);
                if (pe.getY() < n.getMinBuildHeight() + 2 || pe.getY() > 120) continue;
                boolean chao = n.getBlockState(pe.below()).isSolidRender(n, pe.below());
                boolean livre = n.getBlockState(pe).getCollisionShape(n, pe).isEmpty() && n.getBlockState(pe.above()).getCollisionShape(n, pe.above()).isEmpty()
                        && n.getFluidState(pe).isEmpty() && n.getFluidState(pe.above()).isEmpty();
                if (chao && livre) return pe;
            }
        }
        return null;
    }

    private static void emboscada(ServerLevel n, ServerPlayer p, ThreadLocalRandom rnd) {
        BlockPos centro = lugar(n, p, rnd);
        if (centro == null) return;
        int nivel = Zonas.nivel(n, centro);
        if (rnd.nextInt(100) < CHANCE_CHEFE) {
            Mob chefe = Chefes.criar(n, new Vec3(centro.getX() + 0.5, centro.getY(), centro.getZ() + 0.5), Math.max(2, Guilda.indice(p)), nivel + 3, 0, "nether");
            if (chefe != null) {
                chefe.setTarget(p);
                n.playSound(null, centro, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 0.7f);
                Sistema.avisar(p, "O Nether mandou um campeão", chefe.getName().getString() + " veio atrás de você. Lute ou corra.", Sistema.VERMELHO);
                return;
            }
        }
        int qtd = rnd.nextInt(4, 8), feitos = 0;
        for (int k = 0; k < qtd; k++) {
            BlockPos pe = k == 0 ? centro : lugar(n, p, rnd);
            if (pe == null) continue;
            Criaturas.Def cria = Criaturas.sortearMonstro("nether", Guilda.indice(p));
            EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(cria != null ? cria.entidade() : HORDA[rnd.nextInt(HORDA.length)]));
            Entity e = tipo == null ? null : tipo.create(n);
            if (!(e instanceof Mob m)) continue;
            m.moveTo(pe.getX() + 0.5, pe.getY(), pe.getZ() + 0.5, rnd.nextFloat() * 360, 0);
            m.finalizeSpawn(n, n.getCurrentDifficultyAt(pe), MobSpawnType.EVENT, null, null);
            if (cria != null) Criaturas.aplicar(m, cria);
            m.addTag("frx_emboscada");
            m.setTarget(p);
            n.addFreshEntity(m);
            feitos++;
        }
        if (feitos == 0) return;
        n.playSound(null, centro, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.4f, 0.6f);
        Sistema.avisar(p, "Emboscada no Nether", feitos + " criaturas cercaram você. Aqui ninguém descansa.", Sistema.VERMELHO);
    }
}
