package br.com.frxcode.frxworld.moedas;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/** Clique direito com 10 moedas troca por 1 da maior; agachado troca 1 por 10 da menor. */
public class ItemMoeda extends Item {
    private final int valor;

    public ItemMoeda(int valor) {
        super(new Item.Properties().stacksTo(64));
        this.valor = valor;
    }

    public int valor() { return valor; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level nivel, Player p, InteractionHand mao) {
        ItemStack pilha = p.getItemInHand(mao);
        if (mirandoCriatura(p)) return InteractionResultHolder.pass(pilha); // clicou num NPC (rei, banqueiro...): não mexe nas moedas
        if (nivel.isClientSide) return InteractionResultHolder.success(pilha);
        if (p.isShiftKeyDown()) {
            if (valor == 1) return InteractionResultHolder.pass(pilha);
            pilha.shrink(1);
            ItemStack menor = new ItemStack(Moedas.porValor(valor / 10), 10);
            if (!p.getInventory().add(menor)) p.drop(menor, false);
        } else {
            if (valor == 100 || pilha.getCount() < 10) return InteractionResultHolder.pass(pilha);
            pilha.shrink(10);
            ItemStack maior = new ItemStack(Moedas.porValor(valor * 10), 1);
            if (!p.getInventory().add(maior)) p.drop(maior, false);
        }
        nivel.playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
        return InteractionResultHolder.consume(pilha);
    }

    private static boolean mirandoCriatura(Player p) {
        net.minecraft.world.phys.Vec3 olho = p.getEyePosition();
        net.minecraft.world.phys.Vec3 fim = olho.add(p.getViewVector(1f).scale(6));
        net.minecraft.world.phys.AABB caixa = p.getBoundingBox().expandTowards(p.getViewVector(1f).scale(6)).inflate(1);
        return net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, olho, fim, caixa,
                e -> e instanceof net.minecraft.world.entity.LivingEntity && !e.isSpectator() && e.isPickable(), 36) != null;
    }

    @Override
    public void appendHoverText(ItemStack pilha, Level nivel, List<Component> linhas, TooltipFlag flag) {
        linhas.add(Component.literal("Vale " + valor + (valor == 1 ? " cobre" : " cobres")).withStyle(ChatFormatting.GRAY));
        if (valor < 100) linhas.add(Component.literal("Clique direito: 10 → 1 moeda maior").withStyle(ChatFormatting.DARK_GRAY));
        if (valor > 1) linhas.add(Component.literal("Agachado + clique: 1 → 10 moedas menores").withStyle(ChatFormatting.DARK_GRAY));
    }
}
