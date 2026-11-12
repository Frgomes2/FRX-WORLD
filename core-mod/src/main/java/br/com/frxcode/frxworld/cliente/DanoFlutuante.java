package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.rede.NumeroDano;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Números de dano que saltam das criaturas e somem subindo. */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DanoFlutuante {
    private static final long VIDA_MS = 1100;

    private record Numero(double x, double y, double z, String texto, int cor, float escala, long inicio) {}

    private static final List<Numero> NUMEROS = new ArrayList<>();

    private DanoFlutuante() {}

    public static void adicionar(NumeroDano m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity e = mc.level.getEntity(m.entidade());
        if (e == null || e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        if (mc.player.distanceToSqr(e) > 40 * 40) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float v = m.valor();
        String texto = v >= 10 ? String.valueOf(Math.round(v)) : String.valueOf(Math.round(v * 10) / 10f);
        if (texto.endsWith(".0")) texto = texto.substring(0, texto.length() - 2);
        int cor = m.tipo() == 2 ? 0xFF5555 : m.tipo() == 1 ? 0xFFAA00 : 0xFFFFFF;
        float escala = m.tipo() == 1 ? 1.7f : 1.15f;
        synchronized (NUMEROS) {
            if (NUMEROS.size() > 60) NUMEROS.remove(0);
            NUMEROS.add(new Numero(e.getX() + (r.nextDouble() - 0.5) * 0.7, e.getY() + e.getBbHeight() * 0.85, e.getZ() + (r.nextDouble() - 0.5) * 0.7,
                    m.tipo() == 1 ? texto + "!" : texto, cor, escala, System.currentTimeMillis()));
        }
    }

    @SubscribeEvent
    public static void sair(ClientPlayerNetworkEvent.LoggingOut ev) {
        synchronized (NUMEROS) { NUMEROS.clear(); }
    }

    @SubscribeEvent
    public static void desenhar(RenderLevelStageEvent ev) {
        if (ev.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        List<Numero> copia;
        long agora = System.currentTimeMillis();
        synchronized (NUMEROS) {
            for (Iterator<Numero> it = NUMEROS.iterator(); it.hasNext(); ) if (agora - it.next().inicio() > VIDA_MS) it.remove();
            if (NUMEROS.isEmpty()) return;
            copia = new ArrayList<>(NUMEROS);
        }
        Vec3 cam = ev.getCamera().getPosition();
        PoseStack ps = ev.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        for (Numero n : copia) {
            float t = (agora - n.inicio()) / (float) VIDA_MS;                 // 0..1
            float sobe = (1 - (1 - t) * (1 - t)) * 0.9f;                       // sobe rápido e desacelera
            int alfa = Math.max(8, Math.round(255 * (t < 0.6f ? 1f : (1 - t) / 0.4f)));
            float pulo = t < 0.15f ? 1f + (0.15f - t) * 3f : 1f;               // "pop" no começo
            ps.pushPose();
            ps.translate(n.x() - cam.x, n.y() + sobe - cam.y, n.z() - cam.z);
            ps.mulPose(ev.getCamera().rotation());
            float s = 0.025f * n.escala() * pulo;
            ps.scale(-s, -s, s);
            font.drawInBatch(n.texto(), -font.width(n.texto()) / 2f, 0, (alfa << 24) | n.cor(), false, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            font.drawInBatch(n.texto(), -font.width(n.texto()) / 2f, 0, (alfa << 24) | n.cor(), false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }
}
