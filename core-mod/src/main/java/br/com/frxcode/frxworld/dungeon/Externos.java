package br.com.frxcode.frxworld.dungeon;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.missoes.Missoes;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Integração com chefes e estruturas de outros mods (aba "Chefes de mods" do painel):
 * chefe morto paga XP/loot do FRX a quem estava perto (com recarga por jogador) e conta para missões;
 * ao entrar numa estrutura cadastrada, o jogador vê o nome e o nível recomendado.
 */
public class Externos {
    private static final double RAIO = 48;

    record Chefe(String nome, int nivel, long xp, List<DungeonDef.Loot> loot, int recargaHoras) {}
    record Estrutura(ResourceLocation id, String nome, int nivel) {}

    private static volatile Map<String, Chefe> chefes = new HashMap<>();
    private static volatile List<Estrutura> estruturas = new ArrayList<>();
    /** Estrutura em que cada jogador estava na última checagem (para anunciar só ao entrar). */
    private static final Map<UUID, ResourceLocation> ondeEsta = new ConcurrentHashMap<>();

    public static void recarregar() {
        PainelApi.lerExternos().whenComplete((json, erro) -> {
            if (erro != null) {
                FrxWorld.LOG.warn("Não consegui ler os chefes de mods do painel: {}", erro.getMessage());
                return;
            }
            Map<String, Chefe> novosChefes = new HashMap<>();
            List<Estrutura> novasEstruturas = new ArrayList<>();
            try {
                for (JsonElement e : json.getAsJsonArray("chefes")) {
                    JsonObject o = e.getAsJsonObject();
                    List<DungeonDef.Loot> loot = new ArrayList<>();
                    if (o.has("loot") && o.get("loot").isJsonArray()) {
                        for (JsonElement l : o.getAsJsonArray("loot")) {
                            JsonObject x = l.getAsJsonObject();
                            loot.add(new DungeonDef.Loot(x.get("item").getAsString(), x.get("qtd").getAsInt(), x.get("chance").getAsDouble()));
                        }
                    }
                    novosChefes.put(o.get("entidade").getAsString(), new Chefe(o.get("nome").getAsString(),
                            o.get("nivelRecomendado").getAsInt(), o.get("recompensaXp").getAsLong(), loot, o.get("recargaHoras").getAsInt()));
                }
                for (JsonElement e : json.getAsJsonArray("estruturas")) {
                    JsonObject o = e.getAsJsonObject();
                    ResourceLocation id = ResourceLocation.tryParse(o.get("estrutura").getAsString());
                    if (id != null) novasEstruturas.add(new Estrutura(id, o.get("nome").getAsString(), o.get("nivelRecomendado").getAsInt()));
                }
            } catch (Exception ex) {
                FrxWorld.LOG.warn("Resposta inválida de /api/servidor/externos: {}", ex.getMessage());
                return;
            }
            chefes = novosChefes;
            estruturas = novasEstruturas;
        });
    }

    // ------------------------------------------------------------------ chefes

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent evento) {
        LivingEntity mob = evento.getEntity();
        if (!(mob.level() instanceof ServerLevel nivel)) return;
        ResourceLocation tipo = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (tipo == null) return;
        Chefe c = chefes.get(tipo.toString());
        if (c == null) return;

        ServerPlayer matador = evento.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        String chave = "ext:" + tipo;
        for (ServerPlayer p : nivel.players()) {
            if (!p.isAlive() || p.distanceToSqr(mob) > RAIO * RAIO || !Progressao.temDados(p)) continue;
            // o matador já conta para a missão pelo evento normal de abate
            if (p != matador) Missoes.aoMatar(p, mob);
            premiar(p, c, chave);
        }
    }

    private static void premiar(ServerPlayer p, Chefe c, String chave) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(c.nome() + " derrotado").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Chefe de nível " + c.nivel()).withStyle(ChatFormatting.YELLOW)));

        Map<String, Long> marcas = Progressao.dados(p).dungeons();
        Long quando = marcas.get(chave);
        long resta = quando == null ? 0 : Math.max(0, c.recargaHoras() * 3_600_000L - (System.currentTimeMillis() - quando));
        if (resta > 0) {
            long h = resta / 3_600_000L, m = (resta % 3_600_000L) / 60_000L;
            p.sendSystemMessage(Component.literal("Você já recebeu o prêmio de " + c.nome() + ". Disponível de novo em "
                    + (h > 0 ? h + "h " : "") + m + "min.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        StringBuilder ganhou = new StringBuilder("+" + c.xp() + " XP");
        for (DungeonDef.Loot l : c.loot()) {
            if (ThreadLocalRandom.current().nextDouble(100) >= l.chance()) continue;
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(l.item()));
            if (item == null) continue;
            int resto = l.qtd();
            while (resto > 0) {
                int lote = Math.min(resto, item.getMaxStackSize());
                ItemStack pilha = new ItemStack(item, lote);
                if (!p.getInventory().add(pilha)) p.drop(pilha, false);
                resto -= lote;
            }
            ganhou.append(" · ").append(l.qtd()).append("× ").append(new ItemStack(item).getHoverName().getString());
        }
        if (c.recargaHoras() > 0) marcas.put(chave, System.currentTimeMillis());
        if (c.xp() > 0) Progressao.ganharXp(p, c.xp());
        p.sendSystemMessage(Component.literal("Prêmio de " + c.nome() + ": ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(ganhou.toString()).withStyle(ChatFormatting.WHITE)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
    }

    // ------------------------------------------------------------------ estruturas

    @SubscribeEvent
    public void aCadaTick(TickEvent.PlayerTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END || !(evento.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 40 != 0 || !p.isAlive()) return;
        List<Estrutura> lista = estruturas;
        if (lista.isEmpty()) return;
        ServerLevel nivel = p.serverLevel();
        var registro = nivel.registryAccess().registryOrThrow(Registries.STRUCTURE);

        Estrutura dentro = null;
        for (Estrutura e : lista) {
            Structure s = registro.get(e.id());
            if (s == null) continue;
            if (nivel.structureManager().getStructureWithPieceAt(p.blockPosition(), s).isValid()) { dentro = e; break; }
        }
        ResourceLocation antes = ondeEsta.get(p.getUUID());
        if (dentro == null) {
            if (antes != null) ondeEsta.remove(p.getUUID());
            return;
        }
        if (dentro.id().equals(antes)) return;
        ondeEsta.put(p.getUUID(), dentro.id());

        int seu = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
        ChatFormatting cor = seu >= dentro.nivel() ? ChatFormatting.GREEN : seu >= dentro.nivel() - 10 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(dentro.nome()).withStyle(ChatFormatting.GOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Nível recomendado " + dentro.nivel()
                + (seu < dentro.nivel() ? " · você é nível " + seu : "")).withStyle(cor)));
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent evento) {
        ondeEsta.remove(evento.getEntity().getUUID());
    }
}
