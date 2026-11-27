package br.com.frxcode.frxworld.aventura;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Corpo do Titã que marcha para Lubelius. Não tem vontade própria: quem move, ataca e decide tudo é Tita.java.
 * Atravessa árvores e morros (gigante), só leva dano de jogadores e não é empurrado.
 */
public class TitaEntidade extends Monster {
    /** Altura de cada região do corpo: pernas (a própria entidade), tronco e cabeça (partes). */
    public static final float PERNAS = 13.5f, TRONCO = 13.5f, CABECA = 8f;
    private final TitaParte[] partes;

    public TitaEntidade(EntityType<? extends TitaEntidade> tipo, Level nivel) {
        super(tipo, nivel);
        this.partes = new TitaParte[]{new TitaParte(this, 11f, TRONCO), new TitaParte(this, 9f, CABECA)};
        setId(ENTITY_COUNTER.getAndAdd(partes.length + 1) + 1);   // ids das partes logo depois do id do corpo (igual ao Dragão do Fim)
        this.xpReward = 0;
        this.noCulling = true;
        setPersistenceRequired();
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 5000)
                .add(Attributes.ATTACK_DAMAGE, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 6);
    }

    @Override protected void registerGoals() { }

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
        setNoGravity(true);
        setDeltaMovement(0, 0, 0);
        moverParte(partes[0], PERNAS);
        moverParte(partes[1], PERNAS + TRONCO);
    }

    private void moverParte(TitaParte parte, double altura) {
        parte.setPos(getX(), getY() + altura, getZ());
        parte.xo = parte.xOld = parte.getX();
        parte.yo = parte.yOld = parte.getY();
        parte.zo = parte.zOld = parte.getZ();
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (partes != null) for (int i = 0; i < partes.length; i++) partes[i].setId(id + i + 1);
    }

    @Override public boolean isMultipartEntity() { return true; }
    @Override public net.minecraftforge.entity.PartEntity<?>[] getParts() { return partes; }

    @Override
    public boolean hurt(DamageSource fonte, float valor) {
        if (!(fonte.getEntity() instanceof Player) && !fonte.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(fonte, valor);
    }

    /** Passo lento e largo, do tamanho dele. */
    @Override
    protected void updateWalkAnimation(float andou) {
        walkAnimation.update(andou > 0.002f ? 0.5f : 0f, 0.4f);
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity e) { }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float altura, float mult, DamageSource f) { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
}
