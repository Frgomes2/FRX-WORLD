package br.com.frxcode.frxworld.progressao;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Anexa DadosJogador a cada jogador e salva no arquivo do jogador. */
public class ProvedorDados implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<DadosJogador> DADOS = CapabilityManager.get(new CapabilityToken<>() {});

    private final DadosJogador dados = new DadosJogador();
    private final LazyOptional<DadosJogador> opcional = LazyOptional.of(() -> dados);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction lado) {
        return cap == DADOS ? opcional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return dados.salvar();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        dados.carregar(tag);
    }
}
