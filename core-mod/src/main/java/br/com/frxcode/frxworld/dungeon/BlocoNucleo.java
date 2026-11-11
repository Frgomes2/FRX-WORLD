package br.com.frxcode.frxworld.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Núcleo da Dungeon. andar=0: guardião final; andar=1/2: guardião do andar (dá a chave do Portão Selado).
 * Clique para ver a dungeon, agachado + clique para despertar o guardião.
 */
public class BlocoNucleo extends Block {
    public static final IntegerProperty ANDAR = IntegerProperty.create("andar", 0, 2);

    public BlocoNucleo(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ANDAR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ANDAR);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState estado, Level nivel, BlockPos pos, Player jogador, InteractionHand mao, BlockHitResult hit) {
        if (mao != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!nivel.isClientSide && jogador instanceof ServerPlayer sp) {
            Dungeons.interagir(sp, pos, estado.getValue(ANDAR), sp.isShiftKeyDown());
        }
        return InteractionResult.sidedSuccess(nivel.isClientSide);
    }
}
