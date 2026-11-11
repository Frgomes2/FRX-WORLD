package br.com.frxcode.frxworld.dungeon;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/** /frx dungeon: constrói uma cripta à frente do jogador (para testes e para posicionar à mão). */
public final class Construir {
    private Construir() {}

    public static boolean cripta(ServerPlayer jogador) {
        ServerLevel nivel = jogador.serverLevel();
        Optional<StructureTemplate> modelo = nivel.getStructureManager().get(new ResourceLocation(FrxWorld.MOD_ID, "cripta"));
        if (modelo.isEmpty()) return false;
        // entrada do modelo no lado norte (z=0, x=7): gira para ficar de frente para o jogador
        Rotation rot = switch (jogador.getDirection()) {
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE; // SOUTH
        };
        BlockPos frente = jogador.blockPosition().relative(jogador.getDirection(), 2).below();
        BlockPos entrada = new BlockPos(7, 0, 0).rotate(rot);
        BlockPos origem = frente.subtract(entrada);
        modelo.get().placeInWorld(nivel, origem, origem, new StructurePlaceSettings().setRotation(rot), nivel.random, 2);
        return true;
    }
}
