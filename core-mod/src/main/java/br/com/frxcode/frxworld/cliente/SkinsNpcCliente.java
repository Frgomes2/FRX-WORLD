package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.FrxWorld;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/** Skins de NPC vindas do painel (texturas dinâmicas) e a marcação de quais entidades são NPCs de qual reino. */
@Mod.EventBusSubscriber(modid = FrxWorld.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkinsNpcCliente {
    private SkinsNpcCliente() {}

    public record Textura(ResourceLocation rl, boolean slim) {}

    private static final Map<String, Textura> SKINS = new HashMap<>();
    private static final Map<Integer, String[]> MARCAS = new HashMap<>();

    private static ResourceLocation local(String chave) {
        return new ResourceLocation(FrxWorld.MOD_ID, "npcskin/" + chave.toLowerCase().replaceAll("[^a-z0-9_.-]", "_"));
    }

    public static void receber(String chave, boolean slim, byte[] png) {
        Minecraft mc = Minecraft.getInstance();
        Textura antiga = SKINS.remove(chave);
        if (antiga != null) mc.getTextureManager().release(antiga.rl());
        if (png == null || png.length == 0) return;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            ResourceLocation rl = local(chave);
            mc.getTextureManager().register(rl, new DynamicTexture(img));
            SKINS.put(chave, new Textura(rl, slim));
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().warn("FRX: skin de NPC inválida ({}): {}", chave, ex.toString());
        }
    }

    public static void marcar(int id, String papel, String reino) {
        MARCAS.put(id, new String[]{papel, reino});
    }

    public static String[] marca(Entity e) { return MARCAS.get(e.getId()); }

    /** Skin do painel por chave direta (ex.: "papel:vegeta"), ou null. */
    public static Textura porChave(String chave) { return SKINS.get(chave); }

    /** A skin mais específica para esta entidade (reino + papel, depois só o papel), ou null. */
    public static Textura para(Entity e) {
        String[] m = MARCAS.get(e.getId());
        if (m == null) return null;
        Textura t = SKINS.get("reino:" + m[1] + ":" + m[0]);
        return t != null ? t : SKINS.get("papel:" + m[0]);
    }

    @SubscribeEvent
    public static void aoSair(ClientPlayerNetworkEvent.LoggingOut e) {
        MARCAS.clear();
        Minecraft mc = Minecraft.getInstance();
        for (Textura t : SKINS.values()) mc.getTextureManager().release(t.rl());
        SKINS.clear();
    }
}
