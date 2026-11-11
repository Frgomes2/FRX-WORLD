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

/** Portão Selado: só deixa passar quem tem a Chave da Camada daquela catacumba. Voltar é livre. */
public class BlocoPortao extends Block {
    public static final IntegerProperty CAMADA = IntegerProperty.create("camada", 1, 2);

    public BlocoPortao(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(CAMADA, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(CAMADA);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState estado, Level nivel, BlockPos pos, Player jogador, InteractionHand mao, BlockHitResult hit) {
        if (mao != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!nivel.isClientSide && jogador instanceof ServerPlayer sp) Dungeons.atravessarPortao(sp, pos, estado.getValue(CAMADA));
        return InteractionResult.sidedSuccess(nivel.isClientSide);
    }
}
