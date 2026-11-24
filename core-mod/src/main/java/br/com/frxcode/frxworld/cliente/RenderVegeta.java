package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.aventura.VegetaEntidade;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Príncipe Vegeta com corpo de jogador; a partir da fase 1 a textura troca para o cabelo dourado. */
public class RenderVegeta extends HumanoidMobRenderer<VegetaEntidade, PlayerModel<VegetaEntidade>> {
    private static final ResourceLocation NORMAL = new ResourceLocation(FrxWorld.MOD_ID, "textures/entity/principe_vegeta.png");
    private static final ResourceLocation DOURADO = new ResourceLocation(FrxWorld.MOD_ID, "textures/entity/principe_vegeta_ssj.png");

    public RenderVegeta(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(VegetaEntidade e) {
        // skin trocada no painel (NPCs e skins → Príncipe Vegeta) vale no lugar da padrão
        SkinsNpcCliente.Textura t = SkinsNpcCliente.porChave(e.fase() >= 1 ? "papel:vegeta_ssj" : "papel:vegeta");
        if (t == null && e.fase() >= 1) t = SkinsNpcCliente.porChave("papel:vegeta");
        if (t != null) return t.rl();
        return e.fase() >= 1 ? DOURADO : NORMAL;
    }
}
