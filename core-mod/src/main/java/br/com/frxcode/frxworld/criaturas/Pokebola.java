package br.com.frxcode.frxworld.criaturas;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pokébola do FRX: jogue para capturar um bicho pacífico ou um bicho domado por você; jogue cheia para soltar onde cair.
 *  - Só se compra com o Treinador (NPC que o admin põe onde quiser: /frx pokebola treinador). No máximo 10 por jogador,
 *    cada uma mais cara que a anterior. Comprada, fica presa ao comprador: só ele joga, pega do chão e solta.
 *  - Domado pelo próprio dono (dragões inclusive): captura sempre. Selvagem pacífico (até 60 de vida): 60% de chance, mais até 30% com o bicho ferido.
 *    Domado por outra pessoa, monstros, aldeões, golens, NPCs, chefes e as criaturas do FRX: nunca. Nem dentro do terreno de outra pessoa.
 *  - Falhou ou soltou: a pokébola cai no chão (só o dono pega). Não queima, não explode e não some com o tempo.
 * Guardado no item: frx_dono, frx_dono_nome, frx_numero e, cheia, frx_mob (dados completos da criatura) e frx_mob_nome.
 */
public class Pokebola {
    private static final DeferredRegister<Item> ITENS = DeferredRegister.create(ForgeRegistries.ITEMS, FrxWorld.MOD_ID);
    private static final DeferredRegister<EntityType<?>> TIPOS = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FrxWorld.MOD_ID);
    public static final RegistryObject<Item> POKEBOLA = ITENS.register("pokebola", ItemPokebola::new);
    public static final RegistryObject<EntityType<Lancada>> LANCADA = TIPOS.register("pokebola",
            () -> EntityType.Builder.<Lancada>of(Lancada::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build("pokebola"));

    public static void registrar(IEventBus modBus) { ITENS.register(modBus); TIPOS.register(modBus); }

    public static final int COMPRAR = 1, MAXIMO = 10, VIDA_SELVAGEM = 60;
    /** Preço de cada pokébola, em cobre (100 = 1 ouro). */
    private static final long[] PRECO = {500, 1000, 2000, 4000, 8000, 16000, 25000, 35000, 50000, 75000};
    private static final String COMPRADAS = "frx_pokebolas", TAG_TREINADOR = "frx_treinador", PETSPHERE = "petcapturetool";

    // ------------------------------------------------------------------ item

    public static final class ItemPokebola extends Item {
        ItemPokebola() { super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()); }

        @Override public boolean isFoil(ItemStack s) { return cheia(s); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level nv, Player p, InteractionHand mao) {
            ItemStack s = p.getItemInHand(mao);
            if (nv.isClientSide()) return InteractionResultHolder.success(s);
            if (!ehDono(s, p)) {
                p.displayClientMessage(Component.literal("Esta pokébola é de " + s.getOrCreateTag().getString("frx_dono_nome") + ". Só o dono pode usar.").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(s);
            }
            Lancada b = new Lancada(nv, p);
            b.setItem(s.copy());
            b.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 1.3f, 0.8f);
            nv.addFreshEntity(b);
            nv.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.7f);
            p.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.consume(ItemStack.EMPTY);
        }

        @Override
        public void appendHoverText(ItemStack s, Level nv, List<Component> l, TooltipFlag f) {
            CompoundTag t = s.getTag();
            if (t == null || !t.contains("frx_dono")) { l.add(Component.literal("Sem dono (compre com o Treinador).").withStyle(ChatFormatting.GRAY)); return; }
            l.add(Component.literal("Nº " + t.getInt("frx_numero") + " de " + t.getString("frx_dono_nome")).withStyle(ChatFormatting.GRAY));
            l.add(cheia(s) ? Component.literal("Dentro: " + t.getString("frx_mob_nome")).withStyle(ChatFormatting.GREEN)
                    : Component.literal("Vazia. Jogue num bicho pacífico ou num bicho seu.").withStyle(ChatFormatting.DARK_GRAY));
            l.add(Component.literal(cheia(s) ? "Jogue para soltar onde ela cair." : "Só o dono pode usar.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean cheia(ItemStack s) { return s.hasTag() && s.getTag().contains("frx_mob"); }

    private static boolean ehDono(ItemStack s, Player p) {
        CompoundTag t = s.getTag();
        return t != null && t.hasUUID("frx_dono") && t.getUUID("frx_dono").equals(p.getUUID());
    }

    private static UUID dono(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.hasUUID("frx_dono") ? t.getUUID("frx_dono") : null;
    }

    // ------------------------------------------------------------------ projétil

    public static class Lancada extends ThrowableItemProjectile {
        public Lancada(EntityType<? extends Lancada> tipo, Level nv) { super(tipo, nv); }
        Lancada(Level nv, LivingEntity quem) { super(LANCADA.get(), quem, nv); }

        @Override protected Item getDefaultItem() { return POKEBOLA.get(); }

        @Override
        protected void onHit(HitResult r) {
            super.onHit(r);
            if (!(level() instanceof ServerLevel nv) || isRemoved()) return;
            ItemStack bola = getItem().copy();
            ServerPlayer dono = getOwner() instanceof ServerPlayer sp ? sp : null;
            Vec3 onde = r.getLocation();
            if (cheia(bola)) soltar(nv, bola, onde, dono);
            else if (r.getType() == HitResult.Type.ENTITY && dono != null) capturar(nv, bola, ((EntityHitResult) r).getEntity(), dono);
            cair(nv, bola, onde);
            discard();
        }
    }

    /** A pokébola volta ao chão: só o dono pega, não queima, não explode e não some. */
    private static void cair(ServerLevel nv, ItemStack bola, Vec3 onde) {
        ItemEntity it = new ItemEntity(nv, onde.x, onde.y + 0.2, onde.z, bola);
        UUID d = dono(bola);
        if (d != null) it.setTarget(d);
        it.setUnlimitedLifetime();
        it.setInvulnerable(true);
        it.setPickUpDelay(10);
        it.setDeltaMovement(0, 0.15, 0);
        nv.addFreshEntity(it);
    }

    /** Motivo de não poder capturar (null = pode). */
    private static String recusa(Entity e, ServerPlayer p) {
        if (!(e instanceof Mob m) || e instanceof Player) return "Isso não cabe numa pokébola.";
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id != null && (id.getNamespace().equals(FrxWorld.MOD_ID) || id.getNamespace().equals("easy_npc")))
            return "Essa criatura é poderosa demais para a pokébola.";
        for (String t : e.getTags()) if (t.startsWith("frx_")) return "Essa criatura não pode ser capturada.";
        if (e.getPersistentData().contains("frx_papel") || e instanceof AbstractVillager || e instanceof AbstractGolem)
            return "Aldeões, golens e NPCs não entram na pokébola.";
        if (!br.com.frxcode.frxworld.protecao.Terrenos.podeUsar(p, e.blockPosition())) return "Esse bicho está no terreno de outra pessoa.";
        // domado pelo próprio jogador (inclusive dragões e outras feras domáveis): sempre cabe
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return o.getOwnerUUID().equals(p.getUUID()) ? null : "Esse bicho tem dono.";
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return "Essa criatura é poderosa demais para a pokébola.";
        if (e instanceof Enemy || e.getType().getCategory() == MobCategory.MONSTER) return "A pokébola só serve para bichos pacíficos (ou domados por você).";
        if (m.getMaxHealth() > VIDA_SELVAGEM) return "Esse bicho é grande demais: dome ele primeiro.";
        return null;
    }

    private static void capturar(ServerLevel nv, ItemStack bola, Entity e, ServerPlayer p) {
        String nao = recusa(e, p);
        if (nao != null) { p.displayClientMessage(Component.literal(nao).withStyle(ChatFormatting.RED), true); return; }
        Mob m = (Mob) e;
        boolean meu = e instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID());
        double chance = meu ? 1 : 0.6 + 0.3 * (1 - m.getHealth() / Math.max(1, m.getMaxHealth()));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            nv.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 15, 0.3, 0.3, 0.3, 0.02);
            nv.playSound(null, e.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
            p.displayClientMessage(Component.literal("Escapou! (" + Math.round(chance * 100) + "% de chance)").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        e.ejectPassengers();
        e.stopRiding();
        CompoundTag dados = new CompoundTag();
        if (!e.save(dados)) { p.displayClientMessage(Component.literal("Não consegui guardar esse bicho.").withStyle(ChatFormatting.RED), true); return; }
        String nome = e.getDisplayName().getString();
        CompoundTag t = bola.getOrCreateTag();
        t.put("frx_mob", dados);
        t.putString("frx_mob_nome", nome);
        bola.setHoverName(Component.literal("Pokébola: " + nome).withStyle(s -> s.withItalic(false)));
        nv.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 0.5, e.getZ(), 25, 0.3, 0.4, 0.3, 0.05);
        nv.playSound(null, e.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.6f);
        e.discard();
        p.displayClientMessage(Component.literal("✦ " + nome + " foi capturado!").withStyle(ChatFormatting.GREEN), true);
    }

    private static void soltar(ServerLevel nv, ItemStack bola, Vec3 onde, ServerPlayer p) {
        CompoundTag t = bola.getOrCreateTag();
        CompoundTag dados = t.getCompound("frx_mob").copy();
        dados.remove("UUID");   // nasce com identidade nova (evita duplicar se algo der errado)
        Entity e = EntityType.loadEntityRecursive(dados, nv, x -> { x.moveTo(onde.x, onde.y, onde.z, x.getYRot(), x.getXRot()); return x; });
        if (e == null) {
            if (p != null) p.displayClientMessage(Component.literal("Não consegui soltar o bicho aqui. Tente em outro lugar.").withStyle(ChatFormatting.RED), true);
            return;
        }
        nv.addFreshEntity(e);
        t.remove("frx_mob");
        t.remove("frx_mob_nome");
        bola.resetHoverName();
        nv.sendParticles(ParticleTypes.CLOUD, onde.x, onde.y + 0.5, onde.z, 20, 0.4, 0.4, 0.4, 0.02);
        nv.playSound(null, BlockPos.containing(onde), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    // ------------------------------------------------------------------ Treinador (vendedor)

    private static int compradas(ServerPlayer p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(COMPRADAS); }

    private static void compradas(ServerPlayer p, int n) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        raiz.getCompound(Player.PERSISTED_NBT_TAG).putInt(COMPRADAS, n);
    }

    private static boolean pagar(ServerPlayer p, long valor) {
        long bolsa = Moedas.contar(p), banco = Banco.saldo(p);
        if (bolsa + banco < valor) return false;
        long daBolsa = Math.min(bolsa, valor);
        if (daBolsa > 0 && !Moedas.cobrar(p, daBolsa)) return false;
        if (valor - daBolsa > 0) Banco.definirSaldo(p, banco - (valor - daBolsa));
        return true;
    }

    private static ItemStack nova(ServerPlayer p, int numero) {
        ItemStack s = new ItemStack(POKEBOLA.get());
        CompoundTag t = s.getOrCreateTag();
        t.putUUID("frx_dono", p.getUUID());
        t.putString("frx_dono_nome", p.getGameProfile().getName());
        t.putInt("frx_numero", numero);
        return s;
    }

    private static void abrir(ServerPlayer p, Entity npc, String msg, boolean ok) {
        int n = compradas(p);
        CompoundTag d = new CompoundTag();
        d.putInt("npc", npc.getId());
        d.putInt("compradas", n);
        d.putInt("maximo", MAXIMO);
        d.putLong("preco", n < MAXIMO ? PRECO[n] : 0);
        d.putLong("dinheiro", Moedas.contar(p) + Banco.saldo(p));
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("pokebola", d));
    }

    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (npc == null || !npc.getTags().contains(TAG_TREINADOR) || npc.distanceToSqr(p) > 144) return;
        if (a.acao() != COMPRAR) { abrir(p, npc, "", false); return; }
        int n = compradas(p);
        if (n >= MAXIMO) { abrir(p, npc, "Você já comprou as " + MAXIMO + " pokébolas a que tem direito.", false); return; }
        if (!pagar(p, PRECO[n])) { abrir(p, npc, "Faltam moedas: custa " + Moedas.texto(PRECO[n]) + ".", false); return; }
        compradas(p, n + 1);
        ItemStack s = nova(p, n + 1);
        if (!p.getInventory().add(s)) p.drop(s, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
        abrir(p, npc, "Pokébola nº " + (n + 1) + " é sua. Só você pode usar.", true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void falar(PlayerInteractEvent.EntityInteract ev) {
        if (!ev.getTarget().getTags().contains(TAG_TREINADOR)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.SUCCESS);
        if (ev.getHand() == InteractionHand.MAIN_HAND && ev.getEntity() instanceof ServerPlayer p) abrir(p, ev.getTarget(), "", false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void falarEspecifico(PlayerInteractEvent.EntityInteractSpecific ev) {
        if (!ev.getTarget().getTags().contains(TAG_TREINADOR)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** A Esfera Portátil de Pets foi aposentada: não captura mais nada, mas ainda solta o que já tinha dentro. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void esferaAposentada(AttackEntityEvent ev) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(ev.getEntity().getMainHandItem().getItem());
        if (id == null || !id.getNamespace().equals(PETSPHERE)) return;
        ev.setCanceled(true);
        ev.getEntity().displayClientMessage(Component.literal("A Esfera Portátil foi aposentada: use a Pokébola do Treinador. Ela ainda solta o bicho que já está dentro.")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    private static Villager treinador(ServerLevel nv, Vec3 pos, float giro) {
        Villager v = EntityType.VILLAGER.create(nv);
        if (v == null) return null;
        v.moveTo(pos.x, pos.y, pos.z, giro, 0);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistenceRequired();
        v.addTag(TAG_TREINADOR);
        v.setCustomName(Component.literal("Treinador de Criaturas").withStyle(ChatFormatting.GOLD));
        v.setCustomNameVisible(true);
        v.setYHeadRot(giro);
        nv.addFreshEntity(v);
        return v;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("pokebola")
                        .then(Commands.literal("treinador").executes(ctx -> {
                            ServerPlayer adm = ctx.getSource().getPlayerOrException();
                            Villager v = treinador(adm.serverLevel(), adm.position(), adm.getYRot() + 180);
                            ctx.getSource().sendSuccess(() -> Component.literal(v == null ? "Não consegui criar o Treinador." : "Treinador de Criaturas criado aqui."), true);
                            return 1;
                        }))
                        .then(Commands.literal("tirartreinador").executes(ctx -> {
                            ServerPlayer adm = ctx.getSource().getPlayerOrException();
                            int n = 0;
                            for (Entity e : adm.serverLevel().getEntities((Entity) null, adm.getBoundingBox().inflate(6), x -> x.getTags().contains(TAG_TREINADOR))) { e.discard(); n++; }
                            final int k = n;
                            ctx.getSource().sendSuccess(() -> Component.literal(k + " Treinador(es) removido(s)."), true);
                            return 1;
                        }))
                        .then(Commands.literal("devolver").then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            ItemStack s = nova(alvo, Math.max(1, compradas(alvo)));
                            if (!alvo.getInventory().add(s)) alvo.drop(s, false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Pokébola devolvida a " + alvo.getGameProfile().getName() + " (não conta no limite)."), true);
                            return 1;
                        })))));
    }
}
