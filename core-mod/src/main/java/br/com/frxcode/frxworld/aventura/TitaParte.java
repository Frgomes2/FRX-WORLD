package br.com.frxcode.frxworld.aventura;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.entity.PartEntity;

/**
 * Pedaço do corpo do Titã (tronco, cabeça). O jogo só acha uma entidade perto dos pés dela; para um gigante de 35 blocos
 * levar golpe e flecha no alto, cada região é uma parte própria, como no Dragão do Fim. O dano vai todo para o Titã.
 */
public class TitaParte extends PartEntity<TitaEntidade> {
    private final EntityDimensions tamanho;

    public TitaParte(TitaEntidade dono, float largura, float altura) {
        super(dono);
        this.tamanho = EntityDimensions.scalable(largura, altura);
        refreshDimensions();
    }

    @Override protected void defineSynchedData() { }
    @Override protected void readAdditionalSaveData(CompoundTag t) { }
    @Override protected void addAdditionalSaveData(CompoundTag t) { }

    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(DamageSource fonte, float valor) { return !isInvulnerableTo(fonte) && getParent().hurt(fonte, valor); }
    @Override public boolean is(Entity e) { return this == e || getParent() == e; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { throw new UnsupportedOperationException(); }
    @Override public EntityDimensions getDimensions(Pose p) { return tamanho; }
    @Override public boolean shouldBeSaved() { return false; }
}
