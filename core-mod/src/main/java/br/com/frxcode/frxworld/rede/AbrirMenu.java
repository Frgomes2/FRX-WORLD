package br.com.frxcode.frxworld.rede;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: abre (ou atualiza) o menu de um NPC. tipo: "banco" ou "forja". */
public record AbrirMenu(String tipo, CompoundTag dados) {

    public void codificar(FriendlyByteBuf b) { b.writeUtf(tipo, 16); b.writeNbt(dados); }

    public static AbrirMenu decodificar(FriendlyByteBuf b) {
        String t = b.readUtf(16);
        CompoundTag d = b.readNbt();
        return new AbrirMenu(t, d == null ? new CompoundTag() : d);
    }

    public void tratar(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> br.com.frxcode.frxworld.cliente.ClienteMenus.abrir(this));
    }
}
