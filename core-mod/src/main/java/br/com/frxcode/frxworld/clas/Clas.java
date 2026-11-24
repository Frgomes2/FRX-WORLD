package br.com.frxcode.frxworld.clas;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
 * Clãs do FRX-WORLD e o Mestre dos Clãs (NPC de papel "clas"): ver os clãs, pedir para entrar, fundar, sair, ver poder e riqueza,
 * administrar membros e usar o cofre do clã. Um jogador pode participar de até {@link #MAX_POR_JOGADOR} clãs ao mesmo tempo.
 * Os clãs são do próprio mod (o FTB Teams só aceita um time por jogador): tudo fica em frx_clas.json, junto com um resumo de
 * nível e dinheiro de cada jogador para somar quem está fora do jogo. Os times que existiam no FTB Teams são importados uma vez.
 */
public class Clas {
    public static final int MAX_POR_JOGADOR = 3, MAX_MEMBROS = 30;
    private static final long[] VALORES = {10, 100, 1000, 10000};   // 1 prata, 1 ouro, 10 ouro, 100 ouro
    private static final String[] CORES = {"#D43A2F", "#F09A1A", "#F5D04A", "#5BC25B", "#2F8F6B", "#4AA3FF", "#1F4F8F", "#7ADFFF", "#B085F5", "#7B3FBF", "#FF7AD9", "#C89A5A", "#E9DFC8", "#8A8A8A"};
    private static final Item[] BANDEIRAS = {Items.RED_BANNER, Items.ORANGE_BANNER, Items.YELLOW_BANNER, Items.LIME_BANNER, Items.GREEN_BANNER, Items.LIGHT_BLUE_BANNER, Items.BLUE_BANNER, Items.CYAN_BANNER,
            Items.MAGENTA_BANNER, Items.PURPLE_BANNER, Items.PINK_BANNER, Items.BROWN_BANNER, Items.WHITE_BANNER, Items.GRAY_BANNER};

    private record Resumo(String nome, int nivel, long dinheiro) { }

    private static final class Cla {
        UUID id = UUID.randomUUID();
        String nome = "Clã";
        int cor;
        UUID lider;
        long cofre;
        boolean saqueLideres;
        final Map<UUID, String> membros = new java.util.LinkedHashMap<>();   // uuid → "lider" | "oficial" | "membro"
        final Map<UUID, String> pedidos = new java.util.LinkedHashMap<>();   // uuid → nick
        final List<String> registro = new ArrayList<>();
        boolean lideranca(UUID j) { String c = membros.get(j); return "lider".equals(c) || "oficial".equals(c); }
    }

    private static final Map<UUID, Cla> CLAS = new java.util.LinkedHashMap<>();
    private static final Map<UUID, Resumo> RESUMOS = new HashMap<>();
    private static MinecraftServer servidor;
    private static boolean sujo, importouFtb;

    public Clas() {}

    // ------------------------------------------------------------------ arquivo

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_clas.json"); }

    private static void lerResumos(JsonObject jogadores) {
        for (Map.Entry<String, JsonElement> e : jogadores.entrySet()) {
            JsonObject j = e.getValue().getAsJsonObject();
            RESUMOS.put(UUID.fromString(e.getKey()), new Resumo(j.get("nome").getAsString(), j.get("nivel").getAsInt(), j.get("dinheiro").getAsLong()));
        }
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        servidor = ev.getServer();
        RESUMOS.clear(); CLAS.clear(); importouFtb = false;
        try {
            Path a = arquivo(servidor);
            if (!Files.exists(a)) return;
            JsonObject raiz = JsonParser.parseString(Files.readString(a, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!raiz.has("clas")) { lerResumos(raiz); return; }   // formato antigo: só os resumos
            importouFtb = raiz.has("importouFtb") && raiz.get("importouFtb").getAsBoolean();
            if (raiz.has("jogadores")) lerResumos(raiz.getAsJsonObject("jogadores"));
            for (JsonElement e : raiz.getAsJsonArray("clas")) {
                JsonObject j = e.getAsJsonObject();
                Cla c = new Cla();
                c.id = UUID.fromString(j.get("id").getAsString());
                c.nome = j.get("nome").getAsString();
                c.cor = Math.floorMod(j.get("cor").getAsInt(), CORES.length);
                c.lider = UUID.fromString(j.get("lider").getAsString());
                c.cofre = j.get("cofre").getAsLong();
                c.saqueLideres = j.has("saqueLideres") && j.get("saqueLideres").getAsBoolean();
                for (Map.Entry<String, JsonElement> m : j.getAsJsonObject("membros").entrySet()) c.membros.put(UUID.fromString(m.getKey()), m.getValue().getAsString());
                if (j.has("pedidos")) for (Map.Entry<String, JsonElement> m : j.getAsJsonObject("pedidos").entrySet()) c.pedidos.put(UUID.fromString(m.getKey()), m.getValue().getAsString());
                if (j.has("registro")) for (JsonElement r : j.getAsJsonArray("registro")) c.registro.add(r.getAsString());
                if (!c.membros.isEmpty()) CLAS.put(c.id, c);
            }
            FrxWorld.LOG.info("Clãs: {} carregados.", CLAS.size());
        } catch (Exception e) { FrxWorld.LOG.warn("Clãs: não li frx_clas.json: {}", e.toString()); }
    }

    private static void gravar() {
        if (servidor == null) return;
        sujo = false;
        JsonObject raiz = new JsonObject(), jogadores = new JsonObject();
        com.google.gson.JsonArray clas = new com.google.gson.JsonArray();
        for (Cla c : CLAS.values()) {
            JsonObject j = new JsonObject(), membros = new JsonObject(), pedidos = new JsonObject();
            com.google.gson.JsonArray registro = new com.google.gson.JsonArray();
            j.addProperty("id", c.id.toString()); j.addProperty("nome", c.nome); j.addProperty("cor", c.cor); j.addProperty("corHex", CORES[c.cor]);
            j.addProperty("lider", c.lider.toString()); j.addProperty("cofre", c.cofre); j.addProperty("saqueLideres", c.saqueLideres);
            c.membros.forEach((id, cargo) -> membros.addProperty(id.toString(), cargo));
            c.pedidos.forEach((id, nick) -> pedidos.addProperty(id.toString(), nick));
            for (String r : c.registro) registro.add(r);
            j.add("membros", membros); j.add("pedidos", pedidos); j.add("registro", registro);
            clas.add(j);
        }
        RESUMOS.forEach((id, r) -> { JsonObject j = new JsonObject(); j.addProperty("nome", r.nome()); j.addProperty("nivel", r.nivel()); j.addProperty("dinheiro", r.dinheiro()); jogadores.add(id.toString(), j); });
        raiz.addProperty("importouFtb", importouFtb);
        raiz.add("clas", clas); raiz.add("jogadores", jogadores);
        try { Files.writeString(arquivo(servidor), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(raiz), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Clãs: não gravei frx_clas.json: {}", e.toString()); }
    }

    /** Uma vez só: os times (party) que já existiam no FTB Teams viram clãs, com os membros e o cofre que tinham. */
    private static void importarDoFtb() {
        if (importouFtb || !FtbTeams.disponivel()) return;
        importouFtb = true;
        int n = 0;
        for (Object t : FtbTeams.clas()) {
            Cla c = new Cla();
            c.nome = limparNome(FtbTeams.nome(t));
            c.cor = CLAS.size() % CORES.length;
            c.lider = FtbTeams.lider(t);
            c.cofre = FtbTeams.extra(t).getLong("frx_cofre");
            for (UUID m : FtbTeams.membros(t)) c.membros.put(m, m.equals(c.lider) ? "lider" : FtbTeams.cargo(t, m).equals("officer") ? "oficial" : "membro");
            if (c.membros.isEmpty() || !c.membros.containsKey(c.lider)) continue;
            CLAS.put(c.id, c);
            n++;
        }
        FrxWorld.LOG.info("Clãs: {} importados do FTB Teams.", n);
        gravar();
    }

    private static String limparNome(String bruto) {
        String n = bruto == null ? "" : bruto.replaceAll("[\\p{Cntrl}§]", "").trim();
        if (n.length() > 24) n = n.substring(0, 24).trim();
        return n.length() < 3 ? "Clã" : n;
    }

    // ------------------------------------------------------------------ resumo dos jogadores (para somar quem está fora do jogo)

    private static void resumir(ServerPlayer p) {
        if (!Progressao.temDados(p)) return;
        Resumo novo = new Resumo(br.com.frxcode.frxworld.acesso.Personagens.nome(p), Progressao.dados(p).nivel(), Moedas.contar(p) + Banco.saldo(p));
        if (!novo.equals(RESUMOS.put(br.com.frxcode.frxworld.acesso.Personagens.id(p), novo))) sujo = true;
    }

    private static Resumo resumo(UUID id) {
        Resumo r = RESUMOS.get(id);
        return r != null ? r : new Resumo("?", 0, 0);
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        int t = ev.getServer().getTickCount();
        if (t == 200) importarDoFtb();
        if (t % 1200 != 555) return;
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) resumir(p);
        if (sujo) gravar();
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) { if (ev.getEntity() instanceof ServerPlayer p) resumir(p); }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) { if (sujo) gravar(); }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        p.server.execute(() -> {
            resumir(p);
            int pedidos = 0;
            for (Cla c : clasDe(br.com.frxcode.frxworld.acesso.Personagens.id(p))) if (c.lideranca(br.com.frxcode.frxworld.acesso.Personagens.id(p))) pedidos += c.pedidos.size();
            if (pedidos > 0) Sistema.avisar(p, "Pedidos para entrar no clã", pedidos + (pedidos == 1 ? " pedido espera" : " pedidos esperam") + " a sua resposta no Mestre dos Clãs.", Sistema.AZUL);
        });
    }

    // ------------------------------------------------------------------ números do clã

    /** O personagem está em algum clã? (marco do Guia do Aventureiro) */
    /** Os dois personagens têm pelo menos um clã em comum? */
    public static boolean mesmoCla(ServerPlayer a, ServerPlayer b) {
        UUID ia = br.com.frxcode.frxworld.acesso.Personagens.id(a), ib = br.com.frxcode.frxworld.acesso.Personagens.id(b);
        for (Cla c : CLAS.values()) if (c.membros.containsKey(ia) && c.membros.containsKey(ib)) return true;
        return false;
    }

    /** Ids dos clãs do personagem (para bônus de clã, como o desejo do Shenlong). */
    public static List<UUID> idsDe(ServerPlayer p) {
        List<UUID> l = new ArrayList<>();
        for (Cla c : clasDe(br.com.frxcode.frxworld.acesso.Personagens.id(p))) l.add(c.id);
        return l;
    }

    public static boolean temCla(ServerPlayer p) { return !clasDe(br.com.frxcode.frxworld.acesso.Personagens.id(p)).isEmpty(); }
    private static List<Cla> clasDe(UUID jogador) { List<Cla> l = new ArrayList<>(); for (Cla c : CLAS.values()) if (c.membros.containsKey(jogador)) l.add(c); return l; }
    private static int poder(Cla c) { int n = 0; for (UUID m : c.membros.keySet()) n += resumo(m).nivel(); return n; }
    private static long riqueza(Cla c) { long n = c.cofre; for (UUID m : c.membros.keySet()) n += resumo(m).dinheiro(); return n; }
    private static String nomeCargo(String cargo) { return "lider".equals(cargo) ? "líder" : "oficial".equals(cargo) ? "oficial" : "membro"; }
    private static ChatFormatting corTexto(Cla c) { return ChatFormatting.GOLD; }

    private static void registrar(Cla c, String texto) {
        c.registro.add(0, texto);
        while (c.registro.size() > 8) c.registro.remove(c.registro.size() - 1);
    }

    private static void avisarLideres(Cla c, String titulo, String texto) {
        if (servidor == null) return;
        for (UUID m : c.membros.keySet()) {
            ServerPlayer o = br.com.frxcode.frxworld.acesso.Personagens.jogador(servidor, m);
            if (o != null && c.lideranca(m)) Sistema.avisar(o, titulo, texto, Sistema.AZUL);
        }
    }

    private static void avisarMembros(Cla c, UUID menos, String titulo, String texto) {
        if (servidor == null) return;
        for (UUID m : c.membros.keySet()) {
            ServerPlayer o = br.com.frxcode.frxworld.acesso.Personagens.jogador(servidor, m);
            if (o != null && !m.equals(menos)) Sistema.avisar(o, titulo, texto, Sistema.OURO);
        }
    }

    // ------------------------------------------------------------------ painel do Mestre dos Clãs (tela própria, como a do banco)

    /** Ações que a tela manda (AcaoMenu tipo "clas"). */
    public static final int VER_MEUS = 0, VER_TODOS = 1, ABRIR_CLA = 2, ABA = 3, FUNDAR = 4, PEDIR = 5, DEPOSITAR = 6, SACAR = 7, REGRA_SAQUE = 8,
            COR = 9, SAIR = 10, OFICIAL = 11, EXPULSAR = 12, LIDERANCA = 13, ACEITAR = 14, RECUSAR = 15;

    /** O que cada jogador está vendo: a tela manda posições das listas, e é daqui que elas viram clãs e pessoas de verdade. */
    private static final class Sessao {
        int npc = -1;
        String tela = "meus";
        UUID cla;
        int aba;
        /** Nome digitado na tela para o clã que está sendo fundado (null = "Clã de Fulano"). */
        String nomeNovo;
        List<UUID> clas = new ArrayList<>();
        List<UUID> membros = new ArrayList<>();
        List<UUID> pedidos = new ArrayList<>();
    }
    private static final Map<UUID, Sessao> SESSOES = new HashMap<>();

    private static void som(ServerPlayer p, boolean ok) {
        p.playNotifySound(ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, ok ? 1.2f : 1f);
    }

    private static int corRgb(Cla c) {
        try { return Integer.parseInt(CORES[Math.floorMod(c.cor, CORES.length)].substring(1), 16); } catch (Exception e) { return 0xB8892A; }
    }

    private static CompoundTag cartao(Cla c, UUID jogador) {
        CompoundTag t = new CompoundTag();
        t.putString("nome", c.nome);
        t.putInt("cor", corRgb(c));
        t.putInt("membros", c.membros.size());
        t.putInt("poder", poder(c));
        t.putLong("cofre", c.cofre);
        t.putLong("riqueza", riqueza(c));
        t.putString("lider", resumo(c.lider).nome());
        t.putString("cargo", c.membros.containsKey(jogador) ? nomeCargo(c.membros.get(jogador)) : "");
        t.putInt("pedidos", c.lideranca(jogador) ? c.pedidos.size() : 0);
        return t;
    }

    /** Clique no NPC. */
    public static void abrir(ServerPlayer p, net.minecraft.world.entity.Entity npc) {
        importarDoFtb();
        resumir(p);
        Sessao s = new Sessao();
        s.npc = npc.getId();
        SESSOES.put(p.getUUID(), s);
        enviar(p, s, "", true);
    }

    private static void enviar(ServerPlayer p, Sessao s, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        net.minecraft.world.entity.Entity npc = p.level().getEntity(s.npc);
        d.putInt("npc", s.npc);
        d.putString("nome", npc != null && npc.getCustomName() != null ? npc.getCustomName().getString() : "Mestre dos Clãs");
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        d.putLong("bolsa", Moedas.contar(p));
        List<Cla> meus = clasDe(br.com.frxcode.frxworld.acesso.Personagens.id(p));
        d.putInt("meusQtd", meus.size());
        d.putInt("max", MAX_POR_JOGADOR);
        d.putInt("total", CLAS.size());
        Cla aberto = s.cla == null ? null : CLAS.get(s.cla);
        if ("cla".equals(s.tela) && (aberto == null || !aberto.membros.containsKey(br.com.frxcode.frxworld.acesso.Personagens.id(p)))) { s.tela = "meus"; s.cla = null; }
        d.putString("tela", s.tela);
        s.clas.clear();
        ListTag lista = new ListTag();
        if ("meus".equals(s.tela)) {
            for (Cla c : meus) { s.clas.add(c.id); lista.add(cartao(c, br.com.frxcode.frxworld.acesso.Personagens.id(p))); }
        } else if ("todos".equals(s.tela)) {
            List<Cla> todos = new ArrayList<>(CLAS.values());
            todos.sort((a, b) -> Integer.compare(poder(b), poder(a)));
            boolean limite = meus.size() >= MAX_POR_JOGADOR;
            for (Cla c : todos) {
                if (s.clas.size() >= 60) break;
                CompoundTag t = cartao(c, br.com.frxcode.frxworld.acesso.Personagens.id(p));
                // 0 = pode pedir, 1 = pedido enviado, 2 = já é membro, 3 = clã cheio, 4 = jogador no limite de clãs
                t.putInt("estado", c.membros.containsKey(br.com.frxcode.frxworld.acesso.Personagens.id(p)) ? 2 : c.pedidos.containsKey(br.com.frxcode.frxworld.acesso.Personagens.id(p)) ? 1 : c.membros.size() >= MAX_MEMBROS ? 3 : limite ? 4 : 0);
                s.clas.add(c.id);
                lista.add(t);
            }
        } else {
            Cla c = aberto;
            CompoundTag t = cartao(c, br.com.frxcode.frxworld.acesso.Personagens.id(p));
            boolean lider = c.lider.equals(br.com.frxcode.frxworld.acesso.Personagens.id(p)), lideranca = c.lideranca(br.com.frxcode.frxworld.acesso.Personagens.id(p));
            t.putBoolean("souLider", lider);
            t.putBoolean("lideranca", lideranca);
            t.putInt("corIdx", Math.floorMod(c.cor, CORES.length));
            t.putBoolean("saqueLideres", c.saqueLideres);
            t.putBoolean("podeSacar", !c.saqueLideres || lideranca);
            ListTag reg = new ListTag();
            for (String r : c.registro) reg.add(StringTag.valueOf(r));
            t.put("registro", reg);
            s.membros.clear();
            List<UUID> ordem = new ArrayList<>(c.membros.keySet());
            ordem.sort((a, b) -> {
                int ca = "lider".equals(c.membros.get(a)) ? 0 : "oficial".equals(c.membros.get(a)) ? 1 : 2, cb = "lider".equals(c.membros.get(b)) ? 0 : "oficial".equals(c.membros.get(b)) ? 1 : 2;
                return ca != cb ? Integer.compare(ca, cb) : Integer.compare(resumo(b).nivel(), resumo(a).nivel());
            });
            ListTag ms = new ListTag();
            for (UUID m : ordem) {
                Resumo r = resumo(m);
                CompoundTag mt = new CompoundTag();
                mt.putString("nick", r.nome());
                mt.putString("cargo", nomeCargo(c.membros.get(m)));
                mt.putInt("nivel", r.nivel());
                mt.putLong("dinheiro", r.dinheiro());
                mt.putBoolean("online", br.com.frxcode.frxworld.acesso.Personagens.jogador(p.server, m) != null);
                mt.putBoolean("eu", m.equals(br.com.frxcode.frxworld.acesso.Personagens.id(p)));
                s.membros.add(m);
                ms.add(mt);
            }
            t.put("lista", ms);
            s.pedidos.clear();
            ListTag ps = new ListTag();
            if (lideranca) for (Map.Entry<UUID, String> e : c.pedidos.entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.putString("nick", e.getValue());
                pt.putInt("nivel", resumo(e.getKey()).nivel());
                s.pedidos.add(e.getKey());
                ps.add(pt);
            }
            t.put("pedidosLista", ps);
            ListTag cores = new ListTag();
            for (String cor : CORES) { CompoundTag ct = new CompoundTag(); ct.putInt("rgb", Integer.parseInt(cor.substring(1), 16)); cores.add(ct); }
            t.put("cores", cores);
            d.put("cla", t);
            d.putInt("aba", s.aba);
        }
        d.put("clas", lista);
        br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new br.com.frxcode.frxworld.rede.AbrirMenu("clas", d));
    }

    private static Sessao sessao(ServerPlayer p, int npcId) {
        Sessao s = SESSOES.get(p.getUUID());
        net.minecraft.world.entity.Entity npc = p.level().getEntity(npcId);
        if (s == null || s.npc != npcId || npc == null || npc.distanceToSqr(p) > 144 || !"clas".equals(npc.getPersistentData().getString("frx_papel"))) return null;
        return s;
    }

    private static <T> T pos(List<T> l, long i) { return i >= 0 && i < l.size() ? l.get((int) i) : null; }

    /** A tela mandou uma ação. Toda resposta é a tela inteira de novo, com o recado do que aconteceu. */
    public static void acao(ServerPlayer p, br.com.frxcode.frxworld.rede.AcaoMenu a) {
        Sessao s = sessao(p, a.npc());
        if (s == null) return;
        UUID eu = br.com.frxcode.frxworld.acesso.Personagens.id(p);
        String nick = br.com.frxcode.frxworld.acesso.Personagens.nome(p);
        Cla c = s.cla == null ? null : CLAS.get(s.cla);
        if (c != null && !c.membros.containsKey(eu)) c = null;
        long v = a.valor();
        String msg = "";
        boolean ok = true;
        switch (a.acao()) {
            case VER_MEUS -> { s.tela = "meus"; s.cla = null; }
            case VER_TODOS -> { s.tela = "todos"; s.cla = null; }
            case ABRIR_CLA -> {
                UUID id = pos(s.clas, v);
                Cla alvo = id == null ? null : CLAS.get(id);
                if (alvo != null && alvo.membros.containsKey(eu)) { s.tela = "cla"; s.cla = id; s.aba = 0; }
            }
            case ABA -> { if (c != null) s.aba = (int) Math.max(0, Math.min(3, v)); }
            case FUNDAR -> {
                Cla sozinho = null;
                for (Cla o : CLAS.values()) if (o.lider.equals(eu) && o.membros.size() == 1) sozinho = o;
                if (clasDe(eu).size() >= MAX_POR_JOGADOR) { msg = "Você já participa de " + MAX_POR_JOGADOR + " clãs, que é o máximo."; ok = false; }
                else if (sozinho != null) { msg = "Você já tem um clã só seu (" + sozinho.nome + "). Chame alguém antes de fundar outro."; ok = false; }
                else {
                    Cla novo = new Cla();
                    novo.nome = s.nomeNovo != null ? s.nomeNovo : limparNome("Clã de " + nick);
                    s.nomeNovo = null;
                    novo.cor = java.util.concurrent.ThreadLocalRandom.current().nextInt(CORES.length);
                    novo.lider = eu;
                    novo.membros.put(eu, "lider");
                    CLAS.put(novo.id, novo);
                    gravar();
                    p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.1f);
                    s.tela = "cla"; s.cla = novo.id; s.aba = 3;
                    msg = "Clã fundado! Escolha o nome e a cor aqui e chame os amigos.";
                }
            }
            case PEDIR -> {
                UUID id = pos(s.clas, v);
                Cla alvo = id == null ? null : CLAS.get(id);
                if (alvo == null || alvo.membros.containsKey(eu)) break;
                if (alvo.pedidos.remove(eu) != null) msg = "Pedido para o " + alvo.nome + " cancelado.";
                else if (clasDe(eu).size() >= MAX_POR_JOGADOR) { msg = "Você já participa de " + MAX_POR_JOGADOR + " clãs."; ok = false; }
                else if (alvo.membros.size() >= MAX_MEMBROS) { msg = "O " + alvo.nome + " está cheio."; ok = false; }
                else {
                    alvo.pedidos.put(eu, nick);
                    avisarLideres(alvo, "Pedido para entrar no clã", nick + " (nível " + resumo(eu).nivel() + ") quer entrar no " + alvo.nome + ". Fale com o Mestre dos Clãs para aceitar.");
                    msg = "Pedido enviado ao " + alvo.nome + ". Você entra assim que o líder aceitar.";
                }
                gravar();
            }
            case DEPOSITAR -> {
                if (c == null) break;
                long antes = c.cofre;
                if (v <= 0 || Moedas.contar(p) < v || !Moedas.cobrar(p, v)) { msg = "Você não tem esse valor na bolsa."; ok = false; break; }
                c.cofre = antes + v;
                registrar(c, nick + " depositou " + Moedas.texto(v));
                FrxWorld.LOG.info("Clã {}: {} depositou {}; cofre {} -> {}", c.nome, nick, v, antes, c.cofre);
                msg = "Depositado: " + Moedas.texto(v) + ".";
                resumir(p); gravar();
            }
            case SACAR -> {
                if (c == null) break;
                long antes = c.cofre;
                if (c.saqueLideres && !c.lideranca(eu)) { msg = "Neste clã só o líder e os oficiais sacam do cofre."; ok = false; break; }
                if (v <= 0 || antes < v) { msg = "O cofre não tem esse valor."; ok = false; break; }
                c.cofre = antes - v;
                Moedas.dar(p, v);
                registrar(c, nick + " sacou " + Moedas.texto(v));
                FrxWorld.LOG.info("Clã {}: {} sacou {}; cofre {} -> {}", c.nome, nick, v, antes, c.cofre);
                avisarMembros(c, eu, "Cofre do " + c.nome, nick + " sacou " + Moedas.texto(v) + ". Restam " + Moedas.texto(c.cofre) + ".");
                msg = "Sacado: " + Moedas.texto(v) + ".";
                resumir(p); gravar();
            }
            case REGRA_SAQUE -> {
                if (c == null || !c.lider.equals(eu)) break;
                c.saqueLideres = !c.saqueLideres;
                msg = c.saqueLideres ? "Agora só o líder e os oficiais sacam." : "Agora qualquer membro saca.";
                gravar();
            }
            case COR -> {
                if (c == null || !c.lider.equals(eu) || v < 0 || v >= CORES.length) break;
                c.cor = (int) v;
                msg = "Cor do clã trocada.";
                gravar();
            }
            case SAIR -> {
                if (c == null) break;
                boolean ultimo = c.membros.size() <= 1;
                if (c.lider.equals(eu) && !ultimo) { msg = "Você é o líder: passe a liderança na aba Membros antes de sair."; ok = false; break; }
                long resto = ultimo ? c.cofre : 0;   // o último a sair leva o que sobrou no cofre: o clã deixa de existir
                c.membros.remove(eu);
                if (ultimo) { CLAS.remove(c.id); if (resto > 0) Moedas.dar(p, resto); }
                else avisarMembros(c, eu, "Clã", nick + " saiu do " + c.nome + ".");
                gravar();
                msg = "Você saiu do " + c.nome + (ultimo ? ", que foi desfeito" + (resto > 0 ? ". Você levou " + Moedas.texto(resto) + " do cofre." : ".") : ".");
                s.tela = "meus"; s.cla = null;
            }
            case OFICIAL, EXPULSAR, LIDERANCA -> {
                UUID alvo = pos(s.membros, v);
                if (c == null || !c.lider.equals(eu) || alvo == null || alvo.equals(eu) || !c.membros.containsKey(alvo)) break;
                String outro = resumo(alvo).nome();
                ServerPlayer o = br.com.frxcode.frxworld.acesso.Personagens.jogador(p.server, alvo);
                if (a.acao() == EXPULSAR) {
                    c.membros.remove(alvo);
                    if (o != null) Sistema.avisar(o, "Clã", "Você foi retirado do " + c.nome + ".", Sistema.VERMELHO);
                    msg = outro + " foi retirado do clã.";
                } else if (a.acao() == LIDERANCA) {
                    c.membros.put(eu, "oficial");
                    c.membros.put(alvo, "lider");
                    c.lider = alvo;
                    avisarMembros(c, null, "Clã", outro + " é o novo líder do " + c.nome + ".");
                    msg = outro + " agora é o líder. Você ficou como oficial.";
                } else {
                    boolean oficial = "oficial".equals(c.membros.get(alvo));
                    c.membros.put(alvo, oficial ? "membro" : "oficial");
                    if (o != null) Sistema.avisar(o, "Clã", oficial ? "Você deixou de ser oficial do " + c.nome + "." : "Você agora é oficial do " + c.nome + ": pode aceitar pedidos" + (c.saqueLideres ? " e sacar do cofre." : "."), Sistema.AZUL);
                    msg = outro + (oficial ? " voltou a ser membro." : " agora é oficial.");
                }
                gravar();
            }
            case ACEITAR, RECUSAR -> {
                UUID alvo = pos(s.pedidos, v);
                if (c == null || !c.lideranca(eu) || alvo == null) break;
                String outro = c.pedidos.remove(alvo);
                if (outro == null) break;
                ServerPlayer o = br.com.frxcode.frxworld.acesso.Personagens.jogador(p.server, alvo);
                if (a.acao() == RECUSAR) {
                    if (o != null) Sistema.avisar(o, "Pedido recusado", c.nome + " não aceitou o seu pedido.", Sistema.VERMELHO);
                    msg = "Pedido de " + outro + " recusado.";
                } else if (clasDe(alvo).size() >= MAX_POR_JOGADOR) { msg = outro + " já participa de " + MAX_POR_JOGADOR + " clãs."; ok = false; }
                else if (c.membros.size() >= MAX_MEMBROS) { c.pedidos.put(alvo, outro); msg = "O clã está cheio (" + MAX_MEMBROS + " membros)."; ok = false; }
                else {
                    c.membros.put(alvo, "membro");
                    if (!RESUMOS.containsKey(alvo)) RESUMOS.put(alvo, new Resumo(outro, 0, 0));
                    if (o != null) Sistema.avisar(o, "Você entrou no clã", c.nome + " aceitou o seu pedido.", Sistema.VERDE);
                    avisarMembros(c, alvo, "Clã", outro + " entrou no " + c.nome + ".");
                    msg = outro + " entrou no clã.";
                }
                gravar();
            }
            default -> { }
        }
        if (!msg.isEmpty()) som(p, ok);
        enviar(p, s, msg, ok);
    }

    /** Fundar um clã com o nome digitado na tela (vazio = "Clã de Fulano"). Chega por AcaoHabilidade, que leva texto. */
    public static void fundar(ServerPlayer p, String texto) {
        Sessao s = SESSOES.get(p.getUUID());
        if (s == null || sessao(p, s.npc) == null) return;
        String novo = texto == null ? "" : texto.replaceAll("[\\p{Cntrl}§]", "").trim();
        s.nomeNovo = null;
        if (!novo.isEmpty()) {
            String erro = null;
            if (novo.length() < 3 || novo.length() > 24) erro = "O nome do clã precisa ter de 3 a 24 letras.";
            else for (Cla o : CLAS.values()) if (o.nome.equalsIgnoreCase(novo)) erro = "Já existe um clã com esse nome.";
            if (erro != null) { som(p, false); enviar(p, s, erro, false); return; }
            s.nomeNovo = novo;
        }
        acao(p, new br.com.frxcode.frxworld.rede.AcaoMenu("clas", s.npc, FUNDAR, 0));
        s.nomeNovo = null;
    }

    /** O líder digitou um nome novo na tela (chega por AcaoHabilidade, que leva texto). */
    public static void renomear(ServerPlayer p, String texto) {
        Sessao s = SESSOES.get(p.getUUID());
        if (s == null || sessao(p, s.npc) == null) return;
        Cla c = s.cla == null ? null : CLAS.get(s.cla);
        if (c == null || !c.lider.equals(br.com.frxcode.frxworld.acesso.Personagens.id(p))) return;
        String novo = texto == null ? "" : texto.replaceAll("[\\p{Cntrl}§]", "").trim();
        String msg;
        boolean ok = false;
        if (novo.length() < 3 || novo.length() > 24) msg = "O nome do clã precisa ter de 3 a 24 letras.";
        else if (novo.equals(c.nome)) msg = "O clã já tem esse nome.";
        else {
            Cla igual = null;
            for (Cla o : CLAS.values()) if (o != c && o.nome.equalsIgnoreCase(novo)) igual = o;
            if (igual != null) msg = "Já existe um clã com esse nome.";
            else {
                String antigo = c.nome;
                c.nome = novo;
                gravar();
                avisarMembros(c, br.com.frxcode.frxworld.acesso.Personagens.id(p), "Clã", "O " + antigo + " agora se chama " + novo + ".");
                msg = "O clã agora se chama " + novo + ".";
                ok = true;
            }
        }
        som(p, ok);
        enviar(p, s, msg, ok);
    }

    @SubscribeEvent
    public void aoSairSessao(PlayerEvent.PlayerLoggedOutEvent ev) { SESSOES.remove(ev.getEntity().getUUID()); }
}
