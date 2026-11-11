package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Traços da raça no corpo do jogador (sem mudar a hitbox): orelhas, chifres, cauda, olhos e auréola.
 * Raça principal = traços completos; secundária = só detalhes da cabeça, menores.
 */
public class CamadaRaca extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation BRANCO = new ResourceLocation(FrxWorld.MOD_ID, "textures/entity/raca_partes/branco.png");

    public CamadaRaca(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pai) {
        super(pai);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer p, float limb, float limbAmt,
                       float pt, float idade, float yaw, float pitch) {
        ClienteRacas.Visivel v = ClienteRacas.de(p.getUUID());
        if (v == null || p.isInvisible()) return;
        try {
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(BRANCO));
            VertexConsumer brilho = buf.getBuffer(RenderType.eyes(BRANCO));
            boolean capacete = !p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();

            ps.pushPose();
            getParentModel().head.translateAndRotate(ps);
            cabeca(ps, vc, brilho, luz, v.principal(), 1f, true, capacete);
            if (!v.secundaria().isEmpty()) cabeca(ps, vc, brilho, luz, v.secundaria(), 0.6f, false, capacete);
            ps.popPose();

            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            cauda(ps, vc, luz, v.principal(), idade, limbAmt);
            ps.popPose();
        } catch (Throwable ignorar) {
            // desenho nunca derruba o jogo
        }
    }

    // ------------------------------------------------------------------ cabeça

    private static void cabeca(PoseStack ps, VertexConsumer vc, VertexConsumer brilho, int luz, String raca, float e, boolean principal, boolean capacete) {
        switch (raca) {
            case "elfo" -> {
                if (capacete) return;
                int pele = 0xFFF0D2B4;
                for (int s = -1; s <= 1; s += 2) {
                    caixaLado(ps, vc, luz, s, 4f, 5.5f * e + 4f * (1 - e), -4.5f, -3f, -0.5f, 0.5f, pele);
                    caixaLado(ps, vc, luz, s, 5f, 4f + 2.5f * e, -4f - 2f * e, -3.5f, -0.4f, 0.4f, pele);
                }
            }
            case "beastkin" -> {
                if (capacete) return;
                int pelo = 0xFF8A5A3A, rosa = 0xFFE0A0A0;
                float h = 2.5f * e;
                for (int s = -1; s <= 1; s += 2) {
                    caixaLado(ps, vc, luz, s, 1.5f, 4f, -8f - h, -8f, -1f, 0.6f, pelo);
                    caixaLado(ps, vc, luz, s, 2.2f, 3.3f, -7.9f - h * 0.75f, -8f, -1.05f, -0.9f, rosa);
                }
            }
            case "oni" -> {
                int osso = 0xFFEADFC0, ponta = 0xFFB03020;
                for (int s = -1; s <= 1; s += 2) {
                    caixaLado(ps, vc, luz, s, 1.3f, 2.5f, -8f - 2f * e, -8f, -2.6f, -1.4f, osso);
                    caixaLado(ps, vc, luz, s, 1.6f, 2.2f, -8f - 3f * e, -8f - 2f * e, -2.3f, -1.7f, ponta);
                }
                if (principal) olhos(ps, brilho, 0xFFFFC040);
            }
            case "demonio", "rei_demonio" -> {
                int cor = 0xFF2A2626;
                float g = "rei_demonio".equals(raca) ? 1.3f : 1f;
                for (int s = -1; s <= 1; s += 2) {
                    caixaLado(ps, vc, luz, s, 2.8f, 4.2f, -8f - 1.5f * e * g, -7.5f, -1.2f, 0.4f, cor);
                    caixaLado(ps, vc, luz, s, 3.8f, 4.9f, -8f - 3.2f * e * g, -8f - 1.4f * e * g, -1.0f, 0.2f, cor);
                    caixaLado(ps, vc, luz, s, 4.3f, 5.0f, -8f - 4.2f * e * g, -8f - 3.1f * e * g, -0.8f, -0.1f, 0xFF4A1A1A);
                }
                if (principal) olhos(ps, brilho, "rei_demonio".equals(raca) ? 0xFFFF2020 : 0xFFFFB020);
            }
            case "vampiro" -> { if (principal) olhos(ps, brilho, 0xFFE01818); }
            case "espirito" -> { if (principal) olhos(ps, brilho, 0xFF60E0FF); }
            case "dragonoide", "dragao" -> {
                int cor = "dragao".equals(raca) ? 0xFFB8862A : 0xFF3A7A4A;
                float g = "dragao".equals(raca) ? 1.4f : 1f;
                for (int s = -1; s <= 1; s += 2) {
                    ps.pushPose();
                    ps.translate(s * 2.5f / 16f, -7.5f / 16f, 1.5f / 16f);
                    ps.mulPose(Axis.XP.rotationDegrees(35));
                    caixa(ps, vc, luz, -0.5f * g, -0.5f * g, 0, 0.5f * g, 0.5f * g, 3.5f * e * g, cor);
                    ps.popPose();
                }
                if (principal && "dragao".equals(raca)) olhos(ps, brilho, 0xFFFFD040);
            }
            case "anjo", "celestial", "deus" -> {
                if (!principal) return;
                int cor = "celestial".equals(raca) ? 0xFFF0F8FF : 0xFFFFE070;
                float r = "deus".equals(raca) ? 3.6f : 3f, y = -10.5f;
                caixa(ps, brilho, luz, -r, y, -r, r, y + 0.5f, -r + 0.5f, cor);
                caixa(ps, brilho, luz, -r, y, r - 0.5f, r, y + 0.5f, r, cor);
                caixa(ps, brilho, luz, -r, y, -r, -r + 0.5f, y + 0.5f, r, cor);
                caixa(ps, brilho, luz, r - 0.5f, y, -r, r, y + 0.5f, r, cor);
                if ("celestial".equals(raca)) olhos(ps, brilho, 0xFFE8F8FF);
            }
            default -> { }
        }
    }

    /** Olhos que brilham no escuro, na altura padrão dos olhos da skin. */
    private static void olhos(PoseStack ps, VertexConsumer vc, int cor) {
        int luz = 0xF000F0;
        caixa(ps, vc, luz, -3f, -4f, -4.08f, -1f, -3f, -4.02f, cor);
        caixa(ps, vc, luz, 1f, -4f, -4.08f, 3f, -3f, -4.02f, cor);
    }

    // ------------------------------------------------------------------ cauda

    private static void cauda(PoseStack ps, VertexConsumer vc, int luz, String raca, float idade, float andar) {
        int cor;
        float larg, comp;
        switch (raca) {
            case "beastkin" -> { cor = 0xFF8A5A3A; larg = 0.8f; comp = 7f; }
            case "demonio" -> { cor = 0xFF5A1A1A; larg = 0.45f; comp = 8f; }
            case "dragonoide" -> { cor = 0xFF3A7A4A; larg = 1.1f; comp = 7f; }
            case "dragao" -> { cor = 0xFFB8862A; larg = 1.3f; comp = 8.5f; }
            default -> { return; }
        }
        float balanco = Mth.sin(idade * 0.09f) * 12f + Mth.sin(idade * 0.6f) * andar * 10f;
        ps.pushPose();
        ps.translate(0, 10.5f / 16f, 2f / 16f);
        ps.mulPose(Axis.YP.rotationDegrees(balanco));
        ps.mulPose(Axis.XP.rotationDegrees(-35));
        caixa(ps, vc, luz, -larg, -larg, 0, larg, larg, comp * 0.55f, cor);
        ps.translate(0, 0, comp * 0.55f / 16f);
        ps.mulPose(Axis.XP.rotationDegrees(-20));
        caixa(ps, vc, luz, -larg * 0.8f, -larg * 0.8f, 0, larg * 0.8f, larg * 0.8f, comp * 0.45f, cor);
        if ("demonio".equals(raca)) caixa(ps, vc, luz, -1.2f, -0.3f, comp * 0.45f, 1.2f, 0.3f, comp * 0.45f + 1.5f, cor);
        ps.popPose();
    }

    // ------------------------------------------------------------------ caixas (em pixels do modelo)

    /** Caixa espelhada: s = +1 lado direito da tela (x positivo), −1 o outro. */
    private static void caixaLado(PoseStack ps, VertexConsumer vc, int luz, int s, float xa, float xb, float y0, float y1, float z0, float z1, int cor) {
        if (s > 0) caixa(ps, vc, luz, xa, y0, z0, xb, y1, z1, cor);
        else caixa(ps, vc, luz, -xb, y0, z0, -xa, y1, z1, cor);
    }

    private static void caixa(PoseStack ps, VertexConsumer vc, int luz, float x0, float y0, float z0, float x1, float y1, float z1, int cor) {
        float a = x0 / 16f, b = x1 / 16f, c = Math.min(y0, y1) / 16f, d = Math.max(y0, y1) / 16f, e = z0 / 16f, f = z1 / 16f;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int r = (cor >> 16) & 255, g = (cor >> 8) & 255, bl = cor & 255, al = (cor >>> 24) & 255;
        // frente (z-), trás (z+), esquerda (x-), direita (x+), cima (y-), baixo (y+)
        face(vc, m, n, luz, r, g, bl, al, 0, 0, -1, a, c, e, b, c, e, b, d, e, a, d, e);
        face(vc, m, n, luz, r, g, bl, al, 0, 0, 1, b, c, f, a, c, f, a, d, f, b, d, f);
        face(vc, m, n, luz, r, g, bl, al, -1, 0, 0, a, c, f, a, c, e, a, d, e, a, d, f);
        face(vc, m, n, luz, r, g, bl, al, 1, 0, 0, b, c, e, b, c, f, b, d, f, b, d, e);
        face(vc, m, n, luz, r, g, bl, al, 0, -1, 0, a, c, f, b, c, f, b, c, e, a, c, e);
        face(vc, m, n, luz, r, g, bl, al, 0, 1, 0, a, d, e, b, d, e, b, d, f, a, d, f);
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, int luz, int r, int g, int b, int a, float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x4, y4, z4).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, nx, ny, nz).endVertex();
    }
}
