package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.rede.SyncRotuloMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Letreiro em cima dos monstros: "Zumbi Lv. 3"; Elite com estrelas; Campeão com título; Chefe e Chefe Mundial com barra de vida.
 * A cor do nível compara com o seu: cinza (bem mais fraco) … vermelho (muito mais forte).
 */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RotulosMob {
    private record Info(int nivel, String rank, int estrelas, String tracos, String prefixo, String titulo) {}

    private static final Map<Integer, Info> INFO = new ConcurrentHashMap<>();
    private static final Map<String, String> TRACOS = Map.of(
            "berserker", "Berserker", "blindado", "Blindado", "veloz", "Veloz", "vampirico", "Vampírico",
            "peconhento", "Peçonhento", "incendiario", "Incendiário", "gelido", "Gélido");

    private RotulosMob() {}

    public static void aplicar(SyncRotuloMob m) {
        INFO.put(m.entidade(), new Info(m.nivel(), m.rank(), m.estrelas(), m.tracos(), m.prefixo(), m.titulo()));
    }

    @SubscribeEvent
    public static void sair(EntityLeaveLevelEvent ev) {
        if (ev.getLevel().isClientSide()) INFO.remove(ev.getEntity().getId());
    }

    @SubscribeEvent
    public static void sairDoJogo(ClientPlayerNetworkEvent.LoggingOut ev) {
        INFO.clear();
    }

    /** Some com o nome padrão de quem tem letreiro nosso (evita dois nomes). */
    @SubscribeEvent
    public static void nomePadrao(RenderNameTagEvent ev) {
        if (!(ev.getEntity() instanceof Player) && INFO.containsKey(ev.getEntity().getId())) ev.setResult(Event.Result.DENY);
    }

    private static ChatFormatting corNivel(int nivel) {
        int d = nivel - ClienteDados.meuNivel;
        if (d <= -10) return ChatFormatting.GRAY;
        if (d <= -3) return ChatFormatting.GREEN;
        if (d <= 2) return ChatFormatting.WHITE;
        if (d <= 5) return ChatFormatting.YELLOW;
        if (d <= 9) return ChatFormatting.GOLD;
        return ChatFormatting.RED;
    }

    private static Component barra(LivingEntity e, int tam, ChatFormatting cor) {
        float f = e.getMaxHealth() <= 0 ? 0 : Math.max(0, Math.min(1, e.getHealth() / e.getMaxHealth()));
        int cheio = Math.round(f * tam);
        if (cheio == 0 && f > 0) cheio = 1;
        return Component.literal("█".repeat(cheio)).withStyle(cor).append(Component.literal("█".repeat(tam - cheio)).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent nome(LivingEntity e, Info i) {
        if (e.hasCustomName() && e.getCustomName() != null) return Component.literal(e.getCustomName().getString());
        MutableComponent n = e.getType().getDescription().copy();
        if ("elite".equals(i.rank())) {
            String t = TRACOS.get(i.tracos().split(",")[0]);
            if (t != null) n.append(" " + t);
        } else if ("campeao".equals(i.rank()) && !i.prefixo().isEmpty()) n.append(" " + i.prefixo());
        return n;
    }

    @SubscribeEvent
    public static void desenhar(RenderLivingEvent.Post<?, ?> ev) {
        LivingEntity e = ev.getEntity();
        if (e instanceof Player) return;
        Info i = INFO.get(e.getId());
        Minecraft mc = Minecraft.getInstance();
        if (i == null || e.isInvisible() || mc.player == null || !e.isAlive() || mc.options.hideGui) return;
        int alcance = switch (i.rank()) { case "elite" -> 28; case "campeao" -> 48; case "chefe", "mundial" -> 64; default -> 14; };
        if (mc.player.distanceToSqr(e) > (double) alcance * alcance || !mc.player.hasLineOfSight(e)) return;

        ChatFormatting cn = corNivel(i.nivel());
        List<Component> linhas = new ArrayList<>();
        switch (i.rank()) {
            case "elite" -> {
                linhas.add(Component.literal("[Elite]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                linhas.add(nome(e, i).withStyle(ChatFormatting.WHITE));
                linhas.add(Component.literal("Lv. " + i.nivel()).withStyle(cn));
                int s = Math.max(1, Math.min(5, i.estrelas()));
                linhas.add(Component.literal("★".repeat(s)).withStyle(ChatFormatting.YELLOW).append(Component.literal("★".repeat(5 - s)).withStyle(ChatFormatting.DARK_GRAY)));
                linhas.add(barra(e, 10, ChatFormatting.RED));
            }
            case "campeao" -> {
                linhas.add(Component.literal("[CAMPEÃO]").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                linhas.add(nome(e, i).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
                linhas.add(Component.literal("Lv. " + i.nivel()).withStyle(cn));
                if (!i.titulo().isEmpty()) linhas.add(Component.literal("“" + i.titulo() + "”").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                linhas.add(barra(e, 16, ChatFormatting.LIGHT_PURPLE));
            }
            case "chefe" -> {
                linhas.add(Component.literal("[CHEFE]").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                linhas.add(nome(e, i).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
                linhas.add(Component.literal("Lv. " + i.nivel()).withStyle(cn));
                linhas.add(barra(e, 20, ChatFormatting.RED));
            }
            case "mundial" -> {
                linhas.add(Component.literal("CHEFE MUNDIAL").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                linhas.add(Component.literal(nome(e, i).getString().replace("☠", "").trim().toUpperCase()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                linhas.add(Component.literal("Lv. " + i.nivel()).withStyle(cn));
                linhas.add(barra(e, 20, ChatFormatting.DARK_RED));
            }
            default -> linhas.add(nome(e, i).withStyle(ChatFormatting.WHITE).append(Component.literal(" Lv. " + i.nivel()).withStyle(cn)));
        }

        PoseStack ps = ev.getPoseStack();
        ps.pushPose();
        ps.translate(0, e.getBbHeight() + 0.5f, 0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f m = ps.last().pose();
        Font fonte = mc.font;
        int fundo = (int) (mc.options.getBackgroundOpacity(0.25f) * 255f) << 24;
        int y = -(linhas.size() - 1) * 10;
        for (Component l : linhas) {
            fonte.drawInBatch(l, -fonte.width(l) / 2f, y, 0xFFFFFFFF, false, m, ev.getMultiBufferSource(), Font.DisplayMode.NORMAL, fundo, ev.getPackedLight());
            y += 10;
        }
        ps.popPose();
    }
}
