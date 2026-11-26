package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.aventura.TitaEntidade;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Titã: corpo de jogador 18 vezes maior. A skin pode ser trocada no painel (NPCs e skins → Titã). */
public class RenderTita extends HumanoidMobRenderer<TitaEntidade, PlayerModel<TitaEntidade>> {
    private static final ResourceLocation PADRAO = new ResourceLocation(FrxWorld.MOD_ID, "textures/entity/tita.png");
    public static final float ESCALA = 18f;

    public RenderTita(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 9f);
    }

    @Override
    protected void scale(TitaEntidade e, PoseStack ps, float pt) { ps.scale(ESCALA, ESCALA, ESCALA); }

    @Override
    public ResourceLocation getTextureLocation(TitaEntidade e) {
        SkinsNpcCliente.Textura t = SkinsNpcCliente.porChave("papel:tita");
        return t != null ? t.rl() : PADRAO;
    }
}
