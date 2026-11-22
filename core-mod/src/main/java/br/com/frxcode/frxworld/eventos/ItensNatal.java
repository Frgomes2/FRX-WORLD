package br.com.frxcode.frxworld.eventos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Itens do Papai Noel: a Caixa de Presente (clique com ela na mão para abrir e sortear um prêmio) e o Gorro de Natal
 * (só visual: vai na cabeça, sem proteção). Não têm receita: vêm do evento do Noel.
 */
public class ItensNatal {
    private static final DeferredRegister<Item> ITENS = DeferredRegister.create(ForgeRegistries.ITEMS, FrxWorld.MOD_ID);

    /** Material do gorro: sem proteção e sem desgaste. Textura em textures/models/armor/natal_layer_1.png. */
    private static final ArmorMaterial NATAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(ArmorItem.Type t) { return 0; }
        @Override public int getDefenseForType(ArmorItem.Type t) { return 0; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return FrxWorld.MOD_ID + ":natal"; }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0; }
    };

    public static final RegistryObject<Item> PRESENTE = ITENS.register("presente", () -> new Presente(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GORRO = ITENS.register("gorro_natal", () -> new ArmorItem(NATAL, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private ItensNatal() {}

    public static void registrar(IEventBus modBus) { ITENS.register(modBus); }

    public static ItemStack presente() { return new ItemStack(PRESENTE.get()); }

    /** Caixa de Presente: abre na mão e sorteia o prêmio. */
    private static final class Presente extends Item {
        Presente(Properties p) { super(p); }

        @Override
        public void appendHoverText(ItemStack s, Level n, List<Component> l, TooltipFlag f) {
            l.add(Component.literal("Um presente do Papai Noel.").withStyle(ChatFormatting.GRAY));
            l.add(Component.literal("Clique com ele na mão para abrir.").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level n, Player jogador, InteractionHand mao) {
            ItemStack s = jogador.getItemInHand(mao);
            if (n.isClientSide() || !(jogador instanceof ServerPlayer p)) return InteractionResultHolder.sidedSuccess(s, n.isClientSide());
            s.shrink(1);
            abrir(p);
            return InteractionResultHolder.consume(s);
        }
    }

    private static void dar(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    private static ItemStack nomeado(Item i, int qtd, String nome) {
        ItemStack s = new ItemStack(i, qtd);
        if (nome != null) s.setHoverName(Component.literal(nome).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.RED)));
        return s;
    }

    /** O sorteio do presente. */
    static void abrir(ServerPlayer p) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int sorte = rnd.nextInt(100);
        String ganhou;
        if (sorte < 40) {
            long moedas = 500 + rnd.nextInt(26) * 100L;   // 5 a 30 ouro
            Moedas.dar(p, moedas);
            ganhou = Moedas.texto(moedas);
        } else if (sorte < 65) {
            int qual = rnd.nextInt(3);
            if (qual == 0) { dar(p, nomeado(Items.COOKIE, 8, "Biscoitos de Natal")); ganhou = "8 Biscoitos de Natal"; }
            else if (qual == 1) { dar(p, nomeado(Items.PUMPKIN_PIE, 3, "Torta de Natal")); ganhou = "3 Tortas de Natal"; }
            else { dar(p, nomeado(Items.CAKE, 1, "Bolo de Natal")); ganhou = "um Bolo de Natal"; }
        } else if (sorte < 80) {
            ItemStack fogos = new ItemStack(Items.FIREWORK_ROCKET, 8);
            CompoundTag f = fogos.getOrCreateTagElement("Fireworks");
            f.putByte("Flight", (byte) 1);
            ListTag ex = new ListTag();
            CompoundTag e = new CompoundTag();
            e.putByte("Type", (byte) 1);
            e.putIntArray("Colors", new int[]{0xE53935, 0x43A047, 0xFFFFFF});
            e.putBoolean("Twinkle", true);
            ex.add(e);
            f.put("Explosions", ex);
            dar(p, fogos);
            ganhou = "8 fogos de artifício natalinos";
        } else if (sorte < 90) {
            dar(p, new ItemStack(Items.EXPERIENCE_BOTTLE, 8));
            ganhou = "8 frascos de experiência";
        } else {
            int raro = rnd.nextInt(4);
            if (raro == 0) { ItemStack r = br.com.frxcode.frxworld.aventura.Runas.aleatoria(2); ganhou = r.getHoverName().getString(); dar(p, r); }
            else if (raro == 1) { dar(p, new ItemStack(br.com.frxcode.frxworld.blocos.Blocos.TOMO_ARCANO.get())); ganhou = "um Tomo Arcano"; }
            else if (raro == 2) { br.com.frxcode.frxworld.aventura.Tesouros.talvezDar(p, 100); ganhou = "um Mapa do Tesouro"; }
            else { dar(p, new ItemStack(GORRO.get())); ganhou = "o Gorro de Natal"; }
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("🎁 " + p.getGameProfile().getName() + " abriu um presente do Papai Noel e ganhou " + ganhou + "!").withStyle(ChatFormatting.RED), false);
        }
        ServerLevel n = p.serverLevel();
        n.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.2, p.getZ(), 20, 0.3, 0.3, 0.3, 0.08);
        n.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.2, p.getZ(), 15, 0.4, 0.4, 0.4, 0.02);
        n.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS, 1f, 1.3f);
        p.displayClientMessage(Component.literal("🎁 Você ganhou " + ganhou + "!").withStyle(ChatFormatting.RED), true);
    }
}
