package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.habilidades.MarcaInvocacao;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Team;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Invocações do Invocador (time "frx_invocacoes", ver habilidades/MarcaInvocacao) são desenhadas escurecidas e
 * puxadas para o roxo, como sombras: o corpo é redesenhado com as cores de cada vértice multiplicadas.
 */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SombraInvocacao {
    private static final float R = 0.38f, G = 0.30f, B = 0.55f;   // tom de sombra arroxeada
    private static boolean desenhando;

    private SombraInvocacao() {}

    private static boolean invocacao(LivingEntity e) {
        Team t = e.getTeam();
        return t != null && MarcaInvocacao.TIME.equals(t.getName());
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void antes(RenderLivingEvent.Pre ev) {
        if (desenhando || !invocacao(ev.getEntity())) return;
        ev.setCanceled(true);
        desenhando = true;
        try {
            LivingEntity e = ev.getEntity();
            float yaw = Mth.lerp(ev.getPartialTick(), e.yRotO, e.getYRot());
            ev.getRenderer().render(e, yaw, ev.getPartialTick(), ev.getPoseStack(), new Tinta(ev.getMultiBufferSource()), ev.getPackedLight());
        } finally {
            desenhando = false;
        }
    }

    /** Fonte de buffers que escurece a cor de tudo o que passa por ela. */
    private record Tinta(MultiBufferSource fonte) implements MultiBufferSource {
        @Override public VertexConsumer getBuffer(RenderType tipo) {
            VertexConsumer b = fonte.getBuffer(tipo);
            return tipo.toString().contains("text") ? b : new Escuro(b);   // o nome flutuante continua legível
        }
    }

    private record Escuro(VertexConsumer v) implements VertexConsumer {
        private static int m(int c, float f) { return Math.max(0, Math.min(255, (int) (c * f))); }
        @Override public VertexConsumer vertex(double x, double y, double z) { v.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { v.color(m(r, R), m(g, G), m(b, B), a); return this; }
        @Override public VertexConsumer uv(float u, float w) { v.uv(u, w); return this; }
        @Override public VertexConsumer overlayCoords(int u, int w) { v.overlayCoords(u, w); return this; }
        @Override public VertexConsumer uv2(int u, int w) { v.uv2(u, w); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { v.normal(x, y, z); return this; }
        @Override public void endVertex() { v.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { v.defaultColor(m(r, R), m(g, G), m(b, B), a); }
        @Override public void unsetDefaultColor() { v.unsetDefaultColor(); }
        @Override public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float w, int overlay, int luz, float nx, float ny, float nz) {
            v.vertex(x, y, z, r * R, g * G, b * B, a, u, w, overlay, luz, nx, ny, nz);
        }
    }
}
