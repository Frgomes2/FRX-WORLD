package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * NPCs com corpo de jogador:
 * - skin feita no painel (aba NPCs) para o papel/reino, quando existe — vale também para o rei e os guardas;
 * - senão, moradores das raças do FRX (anões, elfos, hobgoblins...) com a skin da raça (raça + UUID).
 */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RenderRacas {
    private RenderRacas() {}

    private static final int SKINS_POR_RACA = 5;
    private static final Map<String, ResourceLocation[]> SKINS = new HashMap<>();
    private static Desenho largo, fino;
    private static boolean desenhando;

    @SubscribeEvent
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity ent = e.getEntity();
        if (desenhando || ent instanceof Player) return;
        SkinsNpcCliente.Textura custom = SkinsNpcCliente.para(ent);
        ResourceLocation tex;
        boolean slim = false;
        if (custom != null) {
            tex = custom.rl();
            slim = custom.slim();
        } else if (ent instanceof Villager v && ehRaca(v)) {
            tex = skinRaca(v);
        } else {
            return;
        }
        Desenho d = desenho(slim);
        if (d == null) return;
        e.setCanceled(true);
        desenhando = true;
        try {
            d.textura = tex;
            float giro = Mth.rotLerp(e.getPartialTick(), ent.yRotO, ent.getYRot());
            d.render(ent, giro, e.getPartialTick(), e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight());
        } finally {
            desenhando = false;
        }
    }

    private static Desenho desenho(boolean slim) {
        if (slim ? fino != null : largo != null) return slim ? fino : largo;
        try {
            Minecraft mc = Minecraft.getInstance();
            EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(),
                    mc.getItemRenderer(), mc.getBlockRenderer(), mc.getEntityRenderDispatcher().getItemInHandRenderer(),
                    mc.getResourceManager(), mc.getEntityModels(), mc.font);
            Desenho d = new Desenho(ctx, slim);
            if (slim) fino = d; else largo = d;
            return d;
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().warn("FRX: não foi possível criar o desenho dos NPCs: {}", ex.toString());
            return null;
        }
    }

    static boolean ehRaca(Villager v) {
        ResourceLocation tipo = BuiltInRegistries.VILLAGER_TYPE.getKey(v.getVillagerData().getType());
        return tipo != null && FrxWorld.MOD_ID.equals(tipo.getNamespace());
    }

    static String raca(LivingEntity e) {
        if (!(e instanceof Villager v)) return "";
        ResourceLocation tipo = BuiltInRegistries.VILLAGER_TYPE.getKey(v.getVillagerData().getType());
        return tipo == null || !FrxWorld.MOD_ID.equals(tipo.getNamespace()) ? "" : tipo.getPath();
    }

    static ResourceLocation skinRaca(Villager v) {
        String r = raca(v);
        ResourceLocation[] lista = SKINS.computeIfAbsent(r, k -> {
            ResourceLocation[] a = new ResourceLocation[SKINS_POR_RACA];
            for (int i = 0; i < a.length; i++) a[i] = new ResourceLocation(FrxWorld.MOD_ID, "textures/entity/raca/" + k + "_" + i + ".png");
            return a;
        });
        return lista[(v.getUUID().hashCode() & 0x7fffffff) % lista.length];
    }

    static float escala(LivingEntity e) {
        String[] marca = SkinsNpcCliente.marca(e);
        if (marca != null && marca[1].startsWith("x")) {   // criatura do Códex: o tamanho vem do painel
            try { return 0.9375f * Mth.clamp(Float.parseFloat(marca[1].substring(1)), 0.4f, 3f); } catch (NumberFormatException ex) { return 0.9375f; }
        }
        return switch (raca(e)) {
            case "anao" -> 0.78f;
            case "elfo" -> 1.05f;
            case "demonio" -> 1.08f;
            case "hobgoblin" -> 0.97f;
            case "halfling" -> 0.68f;
            case "gigante" -> 1.18f;
            case "oni" -> 1.04f;
            case "dragonoide" -> 1.0f;
            default -> 0.9375f;
        };
    }

    static final class Desenho extends LivingEntityRenderer<LivingEntity, PlayerModel<LivingEntity>> {
        ResourceLocation textura;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Desenho(EntityRendererProvider.Context ctx, boolean slim) {
            super(ctx, new PlayerModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5f);
            addLayer(new HumanoidArmorLayer(this, new HumanoidModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)), Minecraft.getInstance().getModelManager()));
            addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        }

        @Override
        public ResourceLocation getTextureLocation(LivingEntity e) { return textura; }

        @Override
        protected void scale(LivingEntity e, PoseStack pose, float parcial) {
            float s = escala(e);
            pose.scale(s, s, s);
        }

        @Override
        protected boolean shouldShowName(LivingEntity e) {
            return e.hasCustomName() && (e.shouldShowName() || e == this.entityRenderDispatcher.crosshairPickEntity);
        }
    }
}
