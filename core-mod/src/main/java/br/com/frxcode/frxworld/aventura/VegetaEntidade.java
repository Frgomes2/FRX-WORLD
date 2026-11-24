package br.com.frxcode.frxworld.aventura;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Corpo do Príncipe Vegeta (chefão do topo do Templo Karin). Os ataques especiais, as fases e a escala
 * por jogadores ficam em Vegeta.java; aqui só o corpo, o soco e a fase (o cliente troca a textura).
 */
public class VegetaEntidade extends Monster {
    private static final EntityDataAccessor<Integer> FASE = SynchedEntityData.defineId(VegetaEntidade.class, EntityDataSerializers.INT);

    public VegetaEntidade(EntityType<? extends VegetaEntidade> tipo, Level nivel) {
        super(tipo, nivel);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 700)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 4);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FASE, 0);
    }

    /** 0 = normal, 1 = cabelo dourado, 2 = dourado com relâmpagos. */
    public int fase() { return entityData.get(FASE); }

    public void fase(int f) { entityData.set(FASE, f); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float altura, float mult, DamageSource fonte) { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("FrxFase", fase());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        fase(t.getInt("FrxFase"));
    }
}
