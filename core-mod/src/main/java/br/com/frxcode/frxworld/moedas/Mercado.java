package br.com.frxcode.frxworld.moedas;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Sistema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mercado entre jogadores (NPC de papel "leiloeiro"): o jogador anuncia o item que está na mão por um preço fixo
 * e qualquer outro compra depois, mesmo com o vendedor fora do jogo. O dinheiro da venda (menos a taxa) e os itens
 * de anúncios vencidos ou cancelados ficam guardados no balcão até o dono buscar. Tudo em janelas; dados em frx_mercado.json.
 */
public class Mercado {
    private static final int TAXA_PCT = 5, DIAS = 7, POR_PAGINA = 45;
    private static final long PRECO_MAX = 10_000_000L, VALIDADE_MS = DIAS * 86_400_000L;
    private static final long[] PASSOS = {1, 10, 100, 1000, 10000};
    private static final String[] NOME_PASSO = {"1 cobre", "1 prata", "1 ouro", "10 ouro", "100 ouro"};
    private static final Item[] ICONE_PASSO = {Items.COPPER_INGOT, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.GOLD_INGOT, Items.GOLD_BLOCK};

    private static final class Anuncio {
        int id;
        UUID vendedor;
        String nome = "?";
        ItemStack item = ItemStack.EMPTY;
        long preco, criado;
    }

    private static final class Caixa {
        long moedas;
        final List<ItemStack> itens = new ArrayList<>();
        boolean vazia() { return moedas <= 0 && itens.isEmpty(); }
    }

    private static final List<Anuncio> ANUNCIOS = new ArrayList<>();
    private static final Map<UUID, Caixa> CAIXAS = new HashMap<>();
    private static MinecraftServer servidor;
    private static boolean sujo;
    private static int proximoId = 1;

    public Mercado() {}

    // ------------------------------------------------------------------ arquivo

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_mercado.json"); }

    private static String texto(ItemStack s) { return s.save(new CompoundTag()).toString(); }

    private static ItemStack item(String snbt) {
        try { return ItemStack.of(TagParser.parseTag(snbt)); } catch (Exception e) { return ItemStack.EMPTY; }
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        servidor = ev.getServer();
        ANUNCIOS.clear(); CAIXAS.clear(); proximoId = 1;
        try {
            Path a = arquivo(servidor);
            if (!Files.exists(a)) return;
            JsonObject raiz = JsonParser.parseString(Files.readString(a, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : raiz.getAsJsonArray("anuncios")) {
                JsonObject j = e.getAsJsonObject();
                Anuncio an = new Anuncio();
                an.id = j.get("id").getAsInt(); an.vendedor = UUID.fromString(j.get("vendedor").getAsString()); an.nome = j.get("nome").getAsString();
                an.item = item(j.get("item").getAsString()); an.preco = j.get("preco").getAsLong(); an.criado = j.get("criado").getAsLong();
                proximoId = Math.max(proximoId, an.id + 1);
                if (!an.item.isEmpty()) ANUNCIOS.add(an);
                else FrxWorld.LOG.warn("Mercado: o anúncio {} de {} tem um item que não existe mais e foi descartado.", an.id, an.nome);
            }
            for (Map.Entry<String, JsonElement> e : raiz.getAsJsonObject("caixas").entrySet()) {
                JsonObject j = e.getValue().getAsJsonObject();
                Caixa c = new Caixa();
                c.moedas = j.get("moedas").getAsLong();
                for (JsonElement i : j.getAsJsonArray("itens")) { ItemStack s = item(i.getAsString()); if (!s.isEmpty()) c.itens.add(s); }
                if (!c.vazia()) CAIXAS.put(UUID.fromString(e.getKey()), c);
            }
            FrxWorld.LOG.info("Mercado: {} anúncios, {} balcões com algo a receber.", ANUNCIOS.size(), CAIXAS.size());
        } catch (Exception e) {
            FrxWorld.LOG.warn("Mercado: não li frx_mercado.json: {}", e.toString());
        }
    }

    private static void gravar() {
        if (servidor == null) return;
        sujo = false;
        JsonObject raiz = new JsonObject(), caixas = new JsonObject();
        JsonArray anuncios = new JsonArray();
        for (Anuncio an : ANUNCIOS) {
            JsonObject j = new JsonObject();
            j.addProperty("id", an.id); j.addProperty("vendedor", an.vendedor.toString()); j.addProperty("nome", an.nome);
            j.addProperty("item", texto(an.item)); j.addProperty("preco", an.preco); j.addProperty("criado", an.criado);
            anuncios.add(j);
        }
        CAIXAS.forEach((id, c) -> {
            if (c.vazia()) return;
            JsonObject j = new JsonObject();
            JsonArray itens = new JsonArray();
            for (ItemStack s : c.itens) itens.add(texto(s));
            j.addProperty("moedas", c.moedas); j.add("itens", itens);
            caixas.add(id.toString(), j);
        });
        raiz.add("anuncios", anuncios); raiz.add("caixas", caixas);
        try {
            Path a = arquivo(servidor), tmp = a.resolveSibling("frx_mercado.json.tmp");
            Files.writeString(tmp, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(raiz), StandardCharsets.UTF_8);
            Files.move(tmp, a, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { FrxWorld.LOG.warn("Mercado: não gravei frx_mercado.json: {}", e.toString()); }
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) { if (sujo) gravar(); }

    // ------------------------------------------------------------------ regras

    private static Caixa caixa(UUID id) { return CAIXAS.computeIfAbsent(id, k -> new Caixa()); }
    private static int limite(ServerPlayer p) { return 5 + 2 * Guilda.indice(p); }
    private static int meus(UUID id) { int n = 0; for (Anuncio a : ANUNCIOS) if (a.vendedor.equals(id)) n++; return n; }
    private static long taxa(long preco) { return Math.max(1, preco * TAXA_PCT / 100); }

    /** Por que este item não pode ser anunciado (null = pode). */
    private static String recusa(ItemStack s) {
        if (s.isEmpty()) return "Segure na mão o item que você quer vender.";
        if (s.getItem() instanceof ItemMoeda) return "Moedas não se vendem no mercado.";
        CompoundTag t = s.getTag();
        if (t != null && t.contains("frx_codex_dono")) return "Esta arma é vinculada a você e não pode ser vendida.";
        return null;
    }

    /** Anúncios vencidos voltam para o balcão do vendedor. */
    private static void vencer() {
        long agora = System.currentTimeMillis();
        boolean mudou = false;
        for (Anuncio a : new ArrayList<>(ANUNCIOS)) {
            if (agora - a.criado < VALIDADE_MS) continue;
            ANUNCIOS.remove(a);
            caixa(a.vendedor).itens.add(a.item);
            mudou = true;
            ServerPlayer v = servidor == null ? null : servidor.getPlayerList().getPlayer(a.vendedor);
            if (v != null) Sistema.avisar(v, "Anúncio vencido", "Ninguém comprou " + a.item.getHoverName().getString() + " em " + DIAS + " dias. O item espera você no Leiloeiro.", Sistema.OURO);
        }
        if (mudou) gravar();
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        int t = ev.getServer().getTickCount();
        if (t % 12000 == 600) vencer();
        if (t % 200 == 91 && sujo) gravar();
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Caixa c = CAIXAS.get(p.getUUID());
        if (c != null && !c.vazia())
            p.server.execute(() -> Sistema.avisar(p, "Mercado", "Você tem " + (c.moedas > 0 ? Moedas.texto(c.moedas) : "") + (c.moedas > 0 && !c.itens.isEmpty() ? " e " : "")
                    + (c.itens.isEmpty() ? "" : c.itens.size() + (c.itens.size() == 1 ? " item" : " itens")) + " para buscar no Leiloeiro.", Sistema.OURO));
    }

    // ------------------------------------------------------------------ painel do Leiloeiro (tela própria)

    /** Ações que a tela manda (AcaoMenu tipo "mercado"). */
    public static final int COMPRAR = 0, CANCELAR = 1, ANUNCIAR = 2, RECEBER = 3;

    private static void som(ServerPlayer p, boolean ok) {
        p.playNotifySound(ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, ok ? 1.2f : 1f);
    }

    private static String dias(long criado) {
        long resta = VALIDADE_MS - (System.currentTimeMillis() - criado);
        long d = resta / 86_400_000L, h = resta % 86_400_000L / 3_600_000L;
        return d > 0 ? d + (d == 1 ? " dia" : " dias") : Math.max(1, h) + " h";
    }

    /** Clique no NPC. */
    public static void abrir(ServerPlayer p, net.minecraft.world.entity.Entity npc) {
        vencer();
        enviar(p, npc.getId(), "", true);
    }

    private static void enviar(ServerPlayer p, int npcId, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        net.minecraft.world.entity.Entity npc = p.level().getEntity(npcId);
        d.putInt("npc", npcId);
        d.putString("nome", npc != null && npc.getCustomName() != null ? npc.getCustomName().getString() : "Leiloeiro");
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        d.putLong("bolsa", Moedas.contar(p));
        d.putInt("limite", limite(p));
        d.putInt("meusQtd", meus(p.getUUID()));
        d.putInt("taxa", TAXA_PCT);
        d.putInt("dias", DIAS);
        Caixa c = CAIXAS.get(p.getUUID());
        d.putLong("receberMoedas", c == null ? 0 : c.moedas);
        d.putInt("receberItens", c == null ? 0 : c.itens.size());
        net.minecraft.nbt.ListTag lista = new net.minecraft.nbt.ListTag();
        List<Anuncio> ordem = new ArrayList<>(ANUNCIOS);
        ordem.sort((a, b) -> Long.compare(b.criado, a.criado));
        for (Anuncio a : ordem) {
            if (lista.size() >= 300) break;
            CompoundTag t = new CompoundTag();
            t.putInt("id", a.id);
            t.put("item", a.item.save(new CompoundTag()));
            t.putLong("preco", a.preco);
            t.putString("vendedor", a.nome);
            t.putBoolean("meu", a.vendedor.equals(p.getUUID()));
            t.putString("resta", dias(a.criado));
            lista.add(t);
        }
        d.put("anuncios", lista);
        br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new br.com.frxcode.frxworld.rede.AbrirMenu("mercado", d));
    }

    /** A tela mandou uma ação. Toda resposta é a tela inteira de novo, com o recado do que aconteceu. */
    public static void acao(ServerPlayer p, br.com.frxcode.frxworld.rede.AcaoMenu a) {
        net.minecraft.world.entity.Entity npc = p.level().getEntity(a.npc());
        if (npc == null || npc.distanceToSqr(p) > 144 || !"leiloeiro".equals(npc.getPersistentData().getString("frx_papel"))) return;
        UUID eu = p.getUUID();
        String nick = p.getGameProfile().getName(), msg = "";
        boolean ok = true;
        long v = a.valor();
        switch (a.acao()) {
            case COMPRAR -> {
                Anuncio an = null;
                for (Anuncio x : ANUNCIOS) if (x.id == (int) v) an = x;
                if (an == null) { msg = "Alguém comprou esse item antes de você."; ok = false; }
                else if (an.vendedor.equals(eu)) { msg = "Esse anúncio é seu. Para tirá-lo, use “Meus anúncios”."; ok = false; }
                else if (p.getInventory().getFreeSlot() < 0) { msg = "Sua bolsa está cheia."; ok = false; }
                else if (!Moedas.cobrar(p, an.preco)) { msg = "Faltam moedas: custa " + Moedas.texto(an.preco) + "."; ok = false; }
                else {
                    ANUNCIOS.remove(an);
                    long liquido = an.preco - taxa(an.preco);
                    caixa(an.vendedor).moedas += liquido;
                    String nomeItem = an.item.getCount() + "× " + an.item.getHoverName().getString();
                    p.getInventory().add(an.item);
                    gravar();
                    FrxWorld.LOG.info("Mercado: {} comprou {} de {} por {} (vendedor recebe {})", nick, nomeItem, an.nome, an.preco, liquido);
                    ServerPlayer vend = p.server.getPlayerList().getPlayer(an.vendedor);
                    if (vend != null) Sistema.avisar(vend, "Você vendeu no mercado", nick + " comprou " + nomeItem + ". " + Moedas.texto(liquido) + " esperam você no Leiloeiro.", Sistema.OURO);
                    msg = "Comprado: " + nomeItem + " por " + Moedas.texto(an.preco) + ".";
                }
            }
            case CANCELAR -> {
                Anuncio an = null;
                for (Anuncio x : ANUNCIOS) if (x.id == (int) v && x.vendedor.equals(eu)) an = x;
                if (an == null) { msg = "Esse anúncio já não está no mercado."; ok = false; break; }
                ANUNCIOS.remove(an);
                boolean coube = p.getInventory().getFreeSlot() >= 0;
                if (coube) p.getInventory().add(an.item); else caixa(eu).itens.add(an.item);
                gravar();
                msg = coube ? "Anúncio retirado: o item voltou para a sua bolsa." : "Anúncio retirado: a bolsa está cheia, o item ficou em “Receber”.";
            }
            case ANUNCIAR -> {
                // valor = número do item (conferência) << 46 | espaço da bolsa (0 a 35) << 40 | preço em cobre
                long preco = v & ((1L << 40) - 1);
                int espaco = (int) ((v >>> 40) & 63), numero = (int) (v >>> 46);
                ItemStack pilha = espaco < 36 ? p.getInventory().getItem(espaco) : ItemStack.EMPTY;
                String recusa = recusa(pilha);
                if (pilha.isEmpty() || Item.getId(pilha.getItem()) != numero) { msg = "Sua bolsa mudou. Escolha o item de novo."; ok = false; }
                else if (recusa != null) { msg = recusa; ok = false; }
                else if (preco < 1 || preco > PRECO_MAX) { msg = "Defina um preço entre 1 cobre e " + Moedas.texto(PRECO_MAX) + "."; ok = false; }
                else if (meus(eu) >= limite(p)) { msg = "Você já tem " + limite(p) + " anúncios, o seu limite. Ele cresce com o rank da Guilda."; ok = false; }
                else {
                    Anuncio an = new Anuncio();
                    an.id = proximoId++; an.vendedor = eu; an.nome = nick; an.item = pilha.copy(); an.preco = preco; an.criado = System.currentTimeMillis();
                    p.getInventory().setItem(espaco, ItemStack.EMPTY);
                    ANUNCIOS.add(an);
                    gravar();
                    FrxWorld.LOG.info("Mercado: {} anunciou {}x {} por {}", nick, an.item.getCount(), an.item.getHoverName().getString(), an.preco);
                    msg = "No ar: " + an.item.getCount() + "× " + an.item.getHoverName().getString() + " por " + Moedas.texto(preco) + ", por " + DIAS + " dias.";
                }
            }
            case RECEBER -> {
                Caixa c = CAIXAS.get(eu);
                if (c == null || c.vazia()) { msg = "Não há nada para receber."; ok = false; break; }
                long moedas = c.moedas;
                c.moedas = 0;
                if (moedas > 0) Moedas.dar(p, moedas);
                int entregues = 0;
                for (ItemStack s : new ArrayList<>(c.itens)) {
                    if (p.getInventory().getFreeSlot() < 0) break;
                    c.itens.remove(s);
                    p.getInventory().add(s);
                    entregues++;
                }
                int sobrou = c.itens.size();
                if (c.vazia()) CAIXAS.remove(eu);
                gravar();
                msg = (moedas > 0 ? "Recebido: " + Moedas.texto(moedas) + ". " : "") + (entregues > 0 ? entregues + (entregues == 1 ? " item devolvido. " : " itens devolvidos. ") : "")
                        + (sobrou > 0 ? "Ainda há " + sobrou + " item(ns): abra espaço na bolsa." : "");
            }
            default -> { }
        }
        if (!msg.isEmpty()) som(p, ok);
        enviar(p, a.npc(), msg, ok);
    }
}
