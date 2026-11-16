package br.com.frxcode.frxworld.reinos;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portão principal de uma capital murada. O admin fica na rua, de frente para o muro, e usa /frx portao aqui:
 * o mod acha o muro à frente, abre uma passagem em arco na largura pedida, põe a grade erguida, lanternas
 * e dois guardas de cada lado. /frx portao desfazer devolve os blocos do último portão.
 */
public class Portao {
    private record Troca(BlockPos pos, BlockState antes) {}
    private record Feito(List<Troca> blocos, List<UUID> guardas) {}
    private static final Map<UUID, Feito> ULTIMO = new HashMap<>();

    public Portao() {}

    private static boolean solido(ServerLevel n, BlockPos p) {
        BlockState b = n.getBlockState(p);
        return !b.isAir() && b.isSolidRender(n, p);
    }

    private static void por(ServerLevel n, BlockPos p, BlockState novo, List<Troca> trocas) {
        BlockState antes = n.getBlockState(p);
        if (antes == novo) return;
        trocas.add(new Troca(p.immutable(), antes));
        n.setBlock(p, novo, 3);
    }

    private static int abrir(CommandSourceStack src, int largura) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel n = p.serverLevel();
        Reino r = NpcsReino.reinoDe(p);
        if (r == null) { src.sendFailure(Component.literal("Fique dentro de uma capital, na rua, de frente para o muro.")); return 0; }
        Direction d = p.getDirection();                 // para onde o jogador olha (norte, sul, leste ou oeste)
        Direction lado = d.getClockWise();
        BlockPos pe = p.blockPosition();
        int meia = Math.max(1, largura / 2);

        // 1) acha o muro: primeira coluna à frente com pelo menos 4 blocos sólidos empilhados a partir do pé
        int inicio = -1;
        for (int i = 1; i <= 48 && inicio < 0; i++) {
            BlockPos b = pe.relative(d, i);
            int altos = 0;
            for (int dy = 0; dy < 6; dy++) if (solido(n, b.above(dy))) altos++;
            if (altos >= 4) inicio = i;
        }
        if (inicio < 0) { src.sendFailure(Component.literal("Não achei muro nos próximos 48 blocos à sua frente. Chegue mais perto e olhe reto para ele.")); return 0; }
        // 2) espessura: até a coluna deixar de ser muro
        int fim = inicio;
        while (fim - inicio < 14) {
            BlockPos b = pe.relative(d, fim + 1);
            int altos = 0;
            for (int dy = 0; dy < 6; dy++) if (solido(n, b.above(dy))) altos++;
            if (altos < 4) break;
            fim++;
        }
        if (fim - inicio >= 14) { src.sendFailure(Component.literal("Isso à frente tem mais de 14 blocos de espessura: parece morro ou construção, não muro.")); return 0; }
        // 3) altura do muro, no meio da espessura
        BlockPos meio = pe.relative(d, (inicio + fim) / 2);
        int topo = 0;
        while (topo < 40 && solido(n, meio.above(topo))) topo++;
        int vao = Math.max(3, Math.min(6, topo - 2));    // deixa pelo menos 2 blocos de muro por cima, quando dá
        // 4) material do muro: o bloco mais comum na face
        Map<BlockState, Integer> conta = new HashMap<>();
        for (int w = -meia - 2; w <= meia + 2; w++) for (int dy = 0; dy < Math.max(4, topo); dy++) {
            BlockState b = n.getBlockState(pe.relative(d, inicio).relative(lado, w).above(dy));
            if (!b.isAir()) conta.merge(b, 1, Integer::sum);
        }
        BlockState muro = Blocks.STONE_BRICKS.defaultBlockState();
        int maior = 0;
        for (Map.Entry<BlockState, Integer> e : conta.entrySet()) if (e.getValue() > maior) { maior = e.getValue(); muro = e.getKey(); }
        BlockState piso = n.getBlockState(pe.below());
        if (piso.isAir() || !piso.isSolidRender(n, pe.below())) piso = Blocks.STONE_BRICKS.defaultBlockState();

        List<Troca> trocas = new ArrayList<>();
        // 5) abre o vão e calça o chão, do lado de dentro até 3 blocos para fora
        for (int i = inicio - 1; i <= fim + 3; i++)
            for (int w = -meia; w <= meia; w++) {
                BlockPos base = pe.relative(d, i).relative(lado, w);
                boolean noMuro = i >= inicio && i <= fim;
                // arco: os cantos de cima ficam fechados
                for (int dy = 0; dy < vao; dy++) {
                    boolean canto = noMuro && dy == vao - 1 && Math.abs(w) == meia && meia >= 2;
                    if (canto) por(n, base.above(dy), muro, trocas);
                    else if (noMuro || !n.getBlockState(base.above(dy)).isAir() && i > fim) por(n, base.above(dy), Blocks.AIR.defaultBlockState(), trocas);
                }
                por(n, base.below(), piso, trocas);
            }
        // 6) ombreiras e verga em volta do vão, para o corte não ficar com a parede crua
        for (int i = inicio; i <= fim; i++) {
            for (int dy = 0; dy <= vao; dy++) for (int w : new int[]{-meia - 1, meia + 1}) {
                BlockPos b = pe.relative(d, i).relative(lado, w).above(dy);
                if (!solido(n, b)) por(n, b, muro, trocas);
            }
            for (int w = -meia; w <= meia; w++) {
                BlockPos b = pe.relative(d, i).relative(lado, w).above(vao);
                if (!solido(n, b)) por(n, b, muro, trocas);
            }
        }
        // 7) grade erguida (barras de ferro) no alto do vão, no meio da espessura
        int iGrade = (inicio + fim) / 2;
        for (int w = -meia; w <= meia; w++) {
            BlockPos b = pe.relative(d, iGrade).relative(lado, w).above(vao - 1);
            if (n.getBlockState(b).isAir()) por(n, b, Blocks.IRON_BARS.defaultBlockState(), trocas);
        }
        // 8) lanternas penduradas na verga, dentro e fora
        for (int i : new int[]{inicio, fim}) for (int w : new int[]{-meia + 1, meia - 1}) {
            BlockPos b = pe.relative(d, i).relative(lado, w).above(vao - 1);
            if (n.getBlockState(b).isAir() && solido(n, b.above())) por(n, b, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), trocas);
        }
        // 9) guardas: dois por dentro e dois por fora, ao lado do vão
        List<UUID> guardas = new ArrayList<>();
        for (int i : new int[]{inicio - 2, fim + 2}) for (int w : new int[]{-meia - 1, meia + 1}) {
            BlockPos b = pe.relative(d, i).relative(lado, w);
            int sobe = 0;
            while (sobe < 4 && !n.getBlockState(b).isAir()) { b = b.above(); sobe++; }
            while (sobe == 0 && b.getY() > pe.getY() - 4 && n.getBlockState(b.below()).isAir()) b = b.below();
            Entity g = NpcsReino.criarGuarda(n, r, b);
            if (g == null) continue;
            NpcsReino.marcarNpc(g, r, "guarda");
            g.addTag("frx_portao");
            n.addFreshEntity(g);
            guardas.add(g.getUUID());
        }
        ULTIMO.put(p.getUUID(), new Feito(trocas, guardas));
        int esp = fim - inicio + 1, alt = topo, v = vao, q = guardas.size();
        src.sendSuccess(() -> Component.literal("Portão aberto em " + r.nome() + ": muro de " + esp + " de espessura e " + alt + " de altura, vão de "
                + (meia * 2 + 1) + "×" + v + ", " + q + " guardas. Se ficou errado: /frx portao desfazer").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int desfazer(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Feito f = ULTIMO.remove(p.getUUID());
        if (f == null) { src.sendFailure(Component.literal("Não há portão seu para desfazer (o registro some quando o servidor reinicia).")); return 0; }
        ServerLevel n = p.serverLevel();
        for (int i = f.blocos().size() - 1; i >= 0; i--) n.setBlock(f.blocos().get(i).pos(), f.blocos().get(i).antes(), 3);
        for (UUID u : f.guardas()) { Entity e = n.getEntity(u); if (e != null) e.discard(); }
        src.sendSuccess(() -> Component.literal("Portão desfeito: " + f.blocos().size() + " blocos devolvidos.").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("portao")
                        .then(Commands.literal("aqui").executes(ctx -> abrir(ctx.getSource(), 5))
                                .then(Commands.argument("largura", com.mojang.brigadier.arguments.IntegerArgumentType.integer(3, 9))
                                        .executes(ctx -> abrir(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "largura")))))
                        .then(Commands.literal("desfazer").executes(ctx -> desfazer(ctx.getSource())))));
    }
}
