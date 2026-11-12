package br.com.frxcode.frxworld.cliente;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashSet;
import java.util.Set;

/**
 * Apresentação de chefe: quando um chefe aparece na sua frente pela primeira vez, a tela ganha barras de cinema,
 * o nome dele surge grande e começa a música de batalha, que para quando ele morre ou fica para trás.
 */
public class HudChefe implements IGuiOverlay {
    private static final long DURACAO = 3800, ENTRADA = 350, SAIDA = 600;

    private static final Set<Integer> APRESENTADOS = new HashSet<>();
    private static String nome = "", sub = "";
    private static boolean mundial;
    private static long inicio;
    private static int chefeAtual = -1, semChefe, contador;
    private static SoundInstance musica;

    public static void limpar() {
        APRESENTADOS.clear();
        pararMusica();
        chefeAtual = -1;
        inicio = 0;
    }

    private static void pararMusica() {
        if (musica != null) Minecraft.getInstance().getSoundManager().stop(musica);
        musica = null;
    }

    private static void apresentar(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        nome = RotulosMob.nomeDe(e).toUpperCase();
        mundial = RotulosMob.mundial(e.getId());
        sub = (mundial ? "CHEFE MUNDIAL" : "CHEFE") + "  ·  NÍVEL " + RotulosMob.nivelDe(e.getId());
        inicio = System.currentTimeMillis();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WITHER_SPAWN, 0.7f, 0.6f));
        chefeAtual = e.getId();
        semChefe = 0;
        if (musica == null || !mc.getSoundManager().isActive(musica)) {
            mc.getMusicManager().stopPlaying();
            musica = SimpleSoundInstance.forMusic(SoundEvents.MUSIC_DRAGON.value());
            mc.getSoundManager().play(musica);
        }
    }

    /** Chamado a cada tick do cliente. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (++contador % 10 != 0) return;
        for (int id : RotulosMob.conhecidos()) {
            if (APRESENTADOS.contains(id) || !RotulosMob.chefe(id)) continue;
            Entity e = mc.level.getEntity(id);
            if (!(e instanceof LivingEntity le) || !le.isAlive() || mc.player.distanceToSqr(le) > 32 * 32 || !mc.player.hasLineOfSight(le)) continue;
            APRESENTADOS.add(id);
            apresentar(le);
            break;
        }
        if (chefeAtual >= 0) {
            Entity e = mc.level.getEntity(chefeAtual);
            boolean perto = e instanceof LivingEntity le && le.isAlive() && mc.player.distanceToSqr(le) < 72 * 72;
            semChefe = perto ? 0 : semChefe + 1;
            if (semChefe >= 8) { pararMusica(); chefeAtual = -1; }   // ~4 s sem o chefe por perto
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int largura, int altura) {
        if (inicio == 0) return;
        long t = System.currentTimeMillis() - inicio;
        if (t > DURACAO) { inicio = 0; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        float f = t < ENTRADA ? t / (float) ENTRADA : t > DURACAO - SAIDA ? Math.max(0f, (DURACAO - t) / (float) SAIDA) : 1f;
        Font font = mc.font;
        int barra = Math.round(altura * 0.13f * f);
        g.fill(0, 0, largura, barra, 0xFF000000);
        g.fill(0, altura - barra, largura, altura, 0xFF000000);

        int alfa = Math.max(4, Math.round(255 * f)) << 24;
        int cor = mundial ? 0xC62828 : 0xE53935;
        int cy = Math.round(altura * 0.62f);
        float escala = Math.min(3f, (largura - 40) / (float) Math.max(1, font.width(nome)));
        g.pose().pushPose();
        g.pose().translate(largura / 2f, cy, 0);
        g.pose().scale(escala, escala, 1f);
        g.drawString(font, nome, -font.width(nome) / 2f, 0, alfa | 0xFFFFFF, true);
        g.pose().popPose();
        int yl = cy + Math.round(9 * escala) + 4;
        int meia = Math.round(Math.min(largura / 2f - 20, font.width(nome) * escala / 2f + 20) * f);
        g.fill(largura / 2 - meia, yl, largura / 2 + meia, yl + 1, alfa | cor);
        g.drawString(font, sub, (largura - font.width(sub)) / 2f, yl + 6, alfa | cor, true);
    }
}
