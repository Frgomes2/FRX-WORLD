package br.com.frxcode.frxworld.blocos;

import br.com.frxcode.frxworld.missoes.Missoes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Quadro de Missões das tavernas: clique com o botão direito para ver as missões. */
public class BlocoQuadro extends Block {
    public BlocoQuadro(Properties propriedades) {
        super(propriedades);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState estado, Level nivel, BlockPos pos, Player jogador, InteractionHand mao, BlockHitResult hit) {
        if (!nivel.isClientSide && jogador instanceof ServerPlayer sp) Missoes.abrirQuadro(sp);
        return InteractionResult.sidedSuccess(nivel.isClientSide);
    }
}
