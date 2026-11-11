package br.com.frxcode.frxworld.moedas;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;

/** Moeda dos reinos: cobre (1), prata (10) e ouro (100). Vale nos NPCs, ganha em missões e vendendo itens. */
public class Moedas {
    private static final DeferredRegister<Item> ITENS = DeferredRegister.create(ForgeRegistries.ITEMS, FrxWorld.MOD_ID);

    public static final RegistryObject<Item> COBRE = ITENS.register("moeda_cobre", () -> new ItemMoeda(1));
    public static final RegistryObject<Item> PRATA = ITENS.register("moeda_prata", () -> new ItemMoeda(10));
    public static final RegistryObject<Item> OURO = ITENS.register("moeda_ouro", () -> new ItemMoeda(100));

    public Moedas() {}

    public static void registrar(IEventBus modBus) {
        ITENS.register(modBus);
        modBus.addListener(Moedas::aba);
    }

    private static void aba(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            e.accept(COBRE);
            e.accept(PRATA);
            e.accept(OURO);
        }
    }

    static Item porValor(int valor) {
        return valor >= 100 ? OURO.get() : valor >= 10 ? PRATA.get() : COBRE.get();
    }

    public static ItemStack cobre(int n) { return new ItemStack(COBRE.get(), n); }
    public static ItemStack prata(int n) { return new ItemStack(PRATA.get(), n); }
    public static ItemStack ouro(int n) { return new ItemStack(OURO.get(), n); }

    /** Dá o valor (em cobre) já trocado nas moedas maiores. */
    public static void dar(ServerPlayer p, long valor) {
        if (valor <= 0) return;
        long ouro = valor / 100, prata = (valor % 100) / 10, cobre = valor % 10;
        entregar(p, OURO.get(), ouro);
        entregar(p, PRATA.get(), prata);
        entregar(p, COBRE.get(), cobre);
    }

    private static void entregar(ServerPlayer p, Item item, long qtd) {
        while (qtd > 0) {
            int lote = (int) Math.min(qtd, 64);
            ItemStack s = new ItemStack(item, lote);
            if (!p.getInventory().add(s)) p.drop(s, false);
            qtd -= lote;
        }
    }

    /** Tira o valor da bolsa (dando troco). Retorna false se não tiver o suficiente. */
    public static boolean cobrar(ServerPlayer p, long valor) {
        long total = contar(p);
        if (total < valor) return false;
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof ItemMoeda) s.setCount(0);
        p.getInventory().setChanged();
        dar(p, total - valor);
        return true;
    }

    public static long contar(Player p) {
        long total = 0;
        for (ItemStack s : p.getInventory().items)
            if (s.getItem() instanceof ItemMoeda m) total += (long) m.valor() * s.getCount();
        return total;
    }

    public static String texto(long valor) {
        long ouro = valor / 100, prata = (valor % 100) / 10, cobre = valor % 10;
        StringBuilder sb = new StringBuilder();
        if (ouro > 0) sb.append(ouro).append(" ouro");
        if (prata > 0) sb.append(sb.length() > 0 ? ", " : "").append(prata).append(" prata");
        if (cobre > 0 || sb.length() == 0) sb.append(sb.length() > 0 ? ", " : "").append(cobre).append(" cobre");
        return sb.toString();
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("saldo").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            long v = contar(p);
            p.sendSystemMessage(Component.literal("Sua bolsa: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(texto(v)).withStyle(ChatFormatting.YELLOW)));
            return (int) Math.min(v, Integer.MAX_VALUE);
        }));
        evento.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("moedas").then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("cobre", IntegerArgumentType.integer(1)).executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            int v = IntegerArgumentType.getInteger(ctx, "cobre");
                            dar(alvo, v);
                            ctx.getSource().sendSuccess(() -> Component.literal("Deu " + texto(v) + " para " + alvo.getName().getString()), true);
                            return 1;
                        })))));
    }
}
