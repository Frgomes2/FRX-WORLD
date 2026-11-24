package br.com.frxcode.frxworld.musica;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.blocos.Blocos;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Guilda;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discos de música cadastrados no painel (aba Discos) e vendidos pelo NPC de papel "discos".
 *
 *  - O áudio vai no pacote de recursos do painel como o som frxworld:disco.<codigo>; aqui só se manda tocar.
 *  - O disco é um item só (frxworld:disco) com o código no NBT. Ele NÃO é um disco do jogo: a jukebox comum é usada
 *    só como lugar — o disco fica guardado nos dados dela e o mod toca, para e devolve.
 *  - Clique com o disco numa jukebox vazia para tocar; clique de novo (com qualquer coisa) para tirar.
 */
public class Discos {
    public static final String NBT = "frx_disco", NBT_NOME = "frx_nome", NBT_ARTISTA = "frx_artista", NBT_DESC = "frx_desc", NBT_DUR = "frx_dur";
    private static final String NA_JUKEBOX = "frx_disco_dentro";

    public record Def(String codigo, String nome, String artista, String descricao, long preco, int rankMin, int arte, int duracao, String estilo, int limite) { }

    private static volatile Map<String, Def> catalogo = new LinkedHashMap<>();
    /** Jukeboxes tocando agora → quando a música acaba (ms). Só serve para as notinhas em cima. */
    private static final Map<GlobalPos, Long> tocando = new HashMap<>();
    private int ticks;
    /** Quantas unidades de cada disco já foram vendidas: <mundo>/frx_discos.json. Serve para o limite de unidades no mundo. */
    private static JsonObject estoque;

    private static java.nio.file.Path arquivo(net.minecraft.server.MinecraftServer s) { return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("frx_discos.json"); }

    private static JsonObject estoque(net.minecraft.server.MinecraftServer s) {
        if (estoque == null) {
            try { estoque = java.nio.file.Files.exists(arquivo(s)) ? com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(arquivo(s), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject(); }
            catch (Exception e) { FrxWorld.LOG.warn("Discos: não li frx_discos.json: {}", e.toString()); estoque = new JsonObject(); }
        }
        return estoque;
    }

    private static int emitidas(net.minecraft.server.MinecraftServer s, String codigo) { JsonObject e = estoque(s); return e.has(codigo) ? e.get(codigo).getAsInt() : 0; }
    /** Quantas ainda podem ser vendidas (-1 = sem limite). */
    private static int restam(net.minecraft.server.MinecraftServer s, Def d) { return d.limite() <= 0 ? -1 : Math.max(0, d.limite() - emitidas(s, d.codigo())); }

    private static int registrarVenda(net.minecraft.server.MinecraftServer s, Def d) {
        int n = emitidas(s, d.codigo()) + 1;
        estoque(s).addProperty(d.codigo(), n);
        try { java.nio.file.Files.writeString(arquivo(s), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(estoque), java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Discos: não gravei frx_discos.json: {}", e.toString()); }
        JsonObject corpo = new JsonObject();
        corpo.add("estoque", estoque);
        PainelApi.enviarDiscos(corpo);
        return n;
    }

    public Discos() {}

    // ------------------------------------------------------------------ painel

    public static void recarregar() {
        PainelApi.lerDiscos().whenComplete((resp, erro) -> {
            if (erro != null) { FrxWorld.LOG.warn("Não consegui ler os discos do painel: {}", erro.getMessage()); return; }
            Map<String, Def> novo = new LinkedHashMap<>();
            if (resp.has("discos") && resp.get("discos").isJsonArray()) for (JsonElement e : resp.getAsJsonArray("discos")) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    Def d = new Def(o.get("codigo").getAsString(), o.get("nome").getAsString(), o.get("artista").getAsString(), o.get("descricao").getAsString(),
                            o.get("preco").getAsLong(), Math.max(0, Math.min(6, o.get("rankMin").getAsInt())), Math.max(1, o.get("arte").getAsInt()), o.get("duracao").getAsInt(),
                            o.has("estilo") && !o.get("estilo").isJsonNull() ? o.get("estilo").getAsString().trim() : "",
                            o.has("limite") && !o.get("limite").isJsonNull() ? Math.max(0, o.get("limite").getAsInt()) : 0);
                    if (d.codigo().matches("[a-z0-9_]{1,30}")) novo.put(d.codigo(), d);
                } catch (Exception ex) { FrxWorld.LOG.warn("Disco inválido ignorado: {}", e); }
            }
            catalogo = novo;
        });
    }

    public static ItemStack criar(Def d) {
        ItemStack s = new ItemStack(Blocos.DISCO.get());
        CompoundTag t = s.getOrCreateTag();
        t.putString(NBT, d.codigo());
        t.putString(NBT_NOME, d.nome());
        t.putString(NBT_ARTISTA, d.artista().isEmpty() || d.estilo().isEmpty() ? d.artista() + d.estilo() : d.artista() + " · " + d.estilo());
        t.putString(NBT_DESC, d.descricao());
        t.putInt(NBT_DUR, d.duracao());
        t.putInt("CustomModelData", d.arte());
        return s;
    }

    private static boolean ehDisco(ItemStack s) { return s.is(Blocos.DISCO.get()) && s.hasTag() && !s.getTag().getString(NBT).isEmpty(); }

    // ------------------------------------------------------------------ loja do NPC (tela própria)

    /** Ação que a tela manda (AcaoMenu tipo "discos"): comprar o disco cujo código tem o hashCode "valor". */
    public static final int COMPRAR = 0;

    /** Clique no NPC. */
    public static void abrir(ServerPlayer p, net.minecraft.world.entity.Entity npc) { enviar(p, npc.getId(), "", true); }

    private static void enviar(ServerPlayer p, int npcId, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        net.minecraft.world.entity.Entity npc = p.level().getEntity(npcId);
        d.putInt("npc", npcId);
        d.putString("nome", npc != null && npc.getCustomName() != null ? npc.getCustomName().getString() : "Mercador de Discos");
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        d.putLong("bolsa", Moedas.contar(p));
        int rank = Guilda.indice(p);
        ListTag lista = new ListTag();
        for (Def def : aVenda()) {
            CompoundTag t = new CompoundTag();
            t.putString("codigo", def.codigo());
            t.put("item", criar(def).save(new CompoundTag()));
            t.putString("nome", def.nome());
            t.putString("artista", def.artista());
            t.putString("estilo", def.estilo());
            t.putString("descricao", def.descricao());
            t.putLong("preco", def.preco());
            t.putInt("duracao", def.duracao());
            t.putString("rank", def.rankMin() > 0 ? ConfigJogo.nomeRank(def.rankMin()) : "");
            t.putBoolean("podeRank", rank >= def.rankMin());
            t.putInt("restam", restam(p.server, def));
            t.putInt("limite", def.limite());
            lista.add(t);
        }
        d.put("discos", lista);
        br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new br.com.frxcode.frxworld.rede.AbrirMenu("discos", d));
    }

    public static void acao(ServerPlayer p, br.com.frxcode.frxworld.rede.AcaoMenu a) {
        net.minecraft.world.entity.Entity npc = p.level().getEntity(a.npc());
        if (npc == null || npc.distanceToSqr(p) > 144 || !"discos".equals(npc.getPersistentData().getString("frx_papel")) || a.acao() != COMPRAR) return;
        List<Def> lista = aVenda();
        String msg;
        boolean ok = false;
        Def d = null;
        for (Def x : lista) if (x.codigo().hashCode() == (int) a.valor()) d = x;   // a tela manda o código do disco resumido num número
        if (d == null) msg = "Esse disco saiu da loja.";
        else {
            if (Guilda.indice(p) < d.rankMin()) msg = "Requer Rank " + ConfigJogo.nomeRank(d.rankMin()) + " na Guilda.";
            else if (restam(p.server, d) == 0) msg = d.nome() + " está esgotado: as " + d.limite() + " unidades já foram vendidas.";
            else if (d.preco() > 0 && !Moedas.cobrar(p, d.preco())) msg = "Faltam moedas: custa " + Moedas.texto(d.preco()) + ".";
            else {
                ItemStack s = criar(d);
                int numero = registrarVenda(p.server, d);
                if (d.limite() > 0) { s.getTag().putInt("frx_num", numero); s.getTag().putInt("frx_total", d.limite()); }
                if (!p.getInventory().add(s)) p.drop(s, false);
                msg = "Comprado: " + d.nome() + ". Clique numa jukebox com o disco na mão.";
                ok = true;
            }
        }
        p.playNotifySound(ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, ok ? 1.2f : 1f);
        enviar(p, a.npc(), msg, ok);
    }

    // ------------------------------------------------------------------ jukebox

    public static ResourceLocation som(String codigo) { return new ResourceLocation(FrxWorld.MOD_ID, "disco." + codigo); }

    /** Disco cadastrado no painel pelo código (ou null). Os de código "luta_..." são trilhas de chefão e não vão para a loja. */
    public static Def def(String codigo) { return catalogo.get(codigo); }

    private static List<Def> aVenda() {
        List<Def> l = new ArrayList<>();
        for (Def d : catalogo.values()) if (!d.codigo().startsWith("luta_")) l.add(d);
        return l;
    }

    private static void parar(ServerLevel n, BlockPos pos, String codigo) {
        tocando.remove(GlobalPos.of(n.dimension(), pos));
        ClientboundStopSoundPacket pacote = new ClientboundStopSoundPacket(som(codigo), SoundSource.RECORDS);
        for (ServerPlayer o : n.players()) if (o.blockPosition().closerThan(pos, 128)) o.connection.send(pacote);
    }

    private static void tocar(ServerLevel n, BlockPos pos, ItemStack disco) {
        CompoundTag t = disco.getTag();
        String codigo = t.getString(NBT), nome = t.getString(NBT_NOME), artista = t.getString(NBT_ARTISTA);
        int dur = t.getInt(NBT_DUR);
        n.playSound(null, pos, SoundEvent.createVariableRangeEvent(som(codigo)), SoundSource.RECORDS, 4f, 1f);
        tocando.put(GlobalPos.of(n.dimension(), pos), System.currentTimeMillis() + (dur > 0 ? dur : 300) * 1000L);
        Component aviso = Component.literal("♪ Tocando: " + nome + (artista.isEmpty() ? "" : " — " + artista)).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer o : n.players()) if (o.blockPosition().closerThan(pos, 64)) o.displayClientMessage(aviso, true);
    }

    private static void devolver(ServerLevel n, BlockPos pos, BlockEntity be) {
        CompoundTag dados = be.getPersistentData();
        ItemStack disco = ItemStack.of(dados.getCompound(NA_JUKEBOX));
        dados.remove(NA_JUKEBOX);
        be.setChanged();
        if (disco.isEmpty()) return;
        parar(n, pos, disco.hasTag() ? disco.getTag().getString(NBT) : "");
        ItemEntity item = new ItemEntity(n, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, disco);
        item.setDefaultPickUpDelay();
        n.addFreshEntity(item);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoClicar(PlayerInteractEvent.RightClickBlock ev) {
        if (ev.getLevel().isClientSide() || !(ev.getLevel() instanceof ServerLevel n)) return;
        BlockPos pos = ev.getPos();
        BlockState estado = n.getBlockState(pos);
        if (!estado.is(Blocks.JUKEBOX) || !(n.getBlockEntity(pos) instanceof JukeboxBlockEntity be)) return;
        boolean temNosso = be.getPersistentData().contains(NA_JUKEBOX);
        ItemStack mao = ev.getItemStack();
        if (temNosso) {
            // qualquer clique tira o nosso disco (antes de o jogo tentar pôr outro por cima)
            if (ev.getHand() == InteractionHand.MAIN_HAND) devolver(n, pos, be);
        } else if (ehDisco(mao) && !estado.getValue(JukeboxBlock.HAS_RECORD)) {
            be.getPersistentData().put(NA_JUKEBOX, mao.copyWithCount(1).save(new CompoundTag()));
            be.setChanged();
            tocar(n, pos, mao);
            if (!ev.getEntity().getAbilities().instabuild) mao.shrink(1);
        } else if (ehDisco(mao)) {
            ev.getEntity().displayClientMessage(Component.literal("Tire o disco que está na jukebox primeiro.").withStyle(ChatFormatting.YELLOW), true);
        } else return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public void aoQuebrar(BlockEvent.BreakEvent ev) {
        if (!(ev.getLevel() instanceof ServerLevel n) || !ev.getState().is(Blocks.JUKEBOX)) return;
        BlockEntity be = n.getBlockEntity(ev.getPos());
        if (be != null && be.getPersistentData().contains(NA_JUKEBOX)) devolver(n, ev.getPos(), be);
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ++ticks % 20 != 0 || tocando.isEmpty()) return;
        long agora = System.currentTimeMillis();
        tocando.entrySet().removeIf(en -> {
            ServerLevel n = ev.getServer().getLevel(en.getKey().dimension());
            BlockPos pos = en.getKey().pos();
            if (n == null || agora > en.getValue()) return true;
            if (!n.isLoaded(pos)) return false;
            BlockEntity be = n.getBlockEntity(pos);
            if (be == null || !be.getPersistentData().contains(NA_JUKEBOX)) return true;
            n.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0, (ticks / 20 % 24) / 24.0, 0, 0, 1);
            return false;
        });
    }
}
