package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.blocos.Blocos;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Conquistas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mapas do tesouro: um papel com uma charada (direção e distância aproximadas a partir de uma capital).
 * Usar o mapa (botão direito) diz se está quente ou frio; a menos de 6 blocos, o baú é desenterrado.
 */
public class Tesouros {
    private static final String CHAVE = "frx_tesouro";
    private static final String[] DIRECOES = {"norte", "nordeste", "leste", "sudeste", "sul", "sudoeste", "oeste", "noroeste"};

    private static String direcao(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        if (ang < 0) ang += 360;
        return DIRECOES[(int) Math.round(ang / 45) % 8];
    }

    private static boolean emCapital(int x, int z) {
        for (Reino r : Reinos.todos()) if (r.distancia(x, z) < 48) return true;
        return false;
    }

    /** Cria um mapa com um tesouro a 250–700 blocos do jogador, fora das capitais. */
    public static ItemStack criar(ServerPlayer p) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int x = p.getBlockX(), z = p.getBlockZ();
        for (int t = 0; t < 20; t++) {
            double ang = rnd.nextDouble(Math.PI * 2), dist = rnd.nextDouble(250, 700);
            int tx = p.getBlockX() + (int) (Math.cos(ang) * dist), tz = p.getBlockZ() + (int) (Math.sin(ang) * dist);
            if (emCapital(tx, tz)) continue;
            x = tx; z = tz;
            break;
        }
        Reino perto = null; int menor = Integer.MAX_VALUE;
        for (Reino r : Reinos.todos()) {
            int d = (int) Math.sqrt(Math.pow(x - r.centroX(), 2) + Math.pow(z - r.centroZ(), 2));
            if (d < menor) { menor = d; perto = r; }
        }
        ItemStack mapa = new ItemStack(Items.PAPER);
        CompoundTag t = mapa.getOrCreateTag();
        CompoundTag d = new CompoundTag();
        d.putInt("x", x); d.putInt("z", z);
        t.put(CHAVE, d);
        t.put("Enchantments", new ListTag());
        t.getList("Enchantments", 10).add(new CompoundTag());   // brilho de item encantado
        mapa.setHoverName(Component.literal("Mapa do Tesouro").withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
        ListTag lore = new ListTag();
        String onde = perto == null ? "Em algum lugar deste mundo."
                : "A uns " + Math.max(50, Math.round(menor / 50f) * 50) + " passos a " + direcao(x - perto.centroX(), z - perto.centroZ()) + " do coração de " + perto.nome() + ".";
        for (String l : new String[]{onde, "Use o mapa para sentir se está perto.", "Quando estiver em cima, use de novo para cavar."})
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(l).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)))));
        mapa.getOrCreateTagElement("display").put("Lore", lore);
        return mapa;
    }

    /** Dá um mapa com a chance (em %) informada. */
    public static void talvezDar(ServerPlayer p, int chance) {
        if (ThreadLocalRandom.current().nextInt(100) >= chance) return;
        ItemStack mapa = criar(p);
        if (!p.getInventory().add(mapa)) p.drop(mapa, false);
        p.sendSystemMessage(Component.literal("🗺 Você encontrou um Mapa do Tesouro! Leia a pista e use o mapa para procurar.").withStyle(ChatFormatting.GOLD));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2f, 0.8f);
    }

    private static void desenterrar(ServerPlayer p, int x, int z) {
        ServerLevel nivel = p.serverLevel();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        BlockPos b = new BlockPos(x, nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        nivel.setBlock(b, Blocks.CHEST.defaultBlockState(), 3);
        if (nivel.getBlockEntity(b) instanceof ChestBlockEntity bau) {
            int nv = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
            List<ItemStack> itens = new ArrayList<>();
            itens.add(Moedas.ouro(3 + nv / 4 + rnd.nextInt(4)));
            itens.add(new ItemStack(Items.DIAMOND, 2 + rnd.nextInt(4)));
            itens.add(new ItemStack(Items.EMERALD, 4 + rnd.nextInt(8)));
            itens.add(new ItemStack(Items.GOLD_INGOT, 4 + rnd.nextInt(8)));
            itens.add(new ItemStack(Items.IRON_INGOT, 6 + rnd.nextInt(10)));
            itens.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 6 + rnd.nextInt(8)));
            itens.add(new ItemStack(Items.GOLDEN_APPLE, 1 + rnd.nextInt(2)));
            if (rnd.nextInt(100) < 50) itens.add(new ItemStack(Items.ENDER_PEARL, 2 + rnd.nextInt(3)));
            if (rnd.nextInt(100) < 35) itens.add(new ItemStack(Blocos.TOMO_ARCANO.get()));
            if (rnd.nextInt(100) < 12) itens.add(new ItemStack(Items.NETHERITE_SCRAP));
            if (rnd.nextInt(100) < 6) itens.add(new ItemStack(Items.TOTEM_OF_UNDYING));
            if (rnd.nextInt(100) < 15) itens.add(criar(p));   // um mapa leva a outro
            if (rnd.nextInt(100) < 30) itens.add(Runas.aleatoria(2));
            List<Integer> vagas = new ArrayList<>();
            for (int i = 0; i < bau.getContainerSize(); i++) vagas.add(i);
            java.util.Collections.shuffle(vagas);
            for (int i = 0; i < itens.size() && i < vagas.size(); i++) bau.setItem(vagas.get(i), itens.get(i));
        }
        nivel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 60, 0.5, 0.8, 0.5, 0.4);
        nivel.playSound(null, b, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.7f);
        Conquistas.somar(p, "tesouros", 1);
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("🗺 " + p.getGameProfile().getName() + " desenterrou um tesouro!").withStyle(ChatFormatting.GOLD), false);
    }

    @SubscribeEvent
    public void aoUsar(PlayerInteractEvent.RightClickItem ev) {
        ItemStack s = ev.getItemStack();
        if (!s.is(Items.PAPER) || !s.hasTag() || !s.getTag().contains(CHAVE)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.SUCCESS);
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        if (p.getCooldowns().isOnCooldown(Items.PAPER)) return;
        p.getCooldowns().addCooldown(Items.PAPER, 30);
        if (p.level() != p.server.overworld()) { p.displayClientMessage(Component.literal("Este tesouro está no mundo principal.").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag d = s.getTag().getCompound(CHAVE);
        int x = d.getInt("x"), z = d.getInt("z");
        double dx = x - p.getX(), dz = z - p.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= 6) {
            s.shrink(1);
            desenterrar(p, x, z);
            return;
        }
        String sensacao = dist > 400 ? "Gelado. Você está muito longe" : dist > 200 ? "Frio" : dist > 80 ? "Morno" : dist > 30 ? "Quente!" : "Pelando! Está quase em cima";
        ChatFormatting cor = dist > 200 ? ChatFormatting.AQUA : dist > 80 ? ChatFormatting.YELLOW : dist > 30 ? ChatFormatting.GOLD : ChatFormatting.RED;
        p.displayClientMessage(Component.literal(sensacao + " — o mapa puxa para " + direcao(dx, dz) + ".").withStyle(cor), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS, 0.8f, (float) Math.max(0.5, 2.0 - dist / 250));
    }

    /** Chefes de masmorra e o chefe mundial podem deixar um mapa. */
    @SubscribeEvent
    public void aoMatarChefe(LivingDeathEvent ev) {
        LivingEntity e = ev.getEntity();
        if (e.level().isClientSide() || !(ev.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (e.getTags().contains(br.com.frxcode.frxworld.dungeon.Dungeons.TAG_BOSS) || e.getTags().contains("frx_chefe_mundial")) talvezDar(p, 35);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("tesouro").executes(c -> { talvezDar(c.getSource().getPlayerOrException(), 100); return 1; })));
    }
}
