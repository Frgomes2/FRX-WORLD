package br.com.frxcode.frxworld.armas;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.progressao.Progressao;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Códex de Armas: armas únicas criadas no painel (menu Códex de Armas). Não caem de monstro nem de baú:
 * só saem do ferreiro/armeiro ou como recompensa de missão (item "codex:<código>"), e cada uma tem um total no mundo.
 * Tudo o que a arma faz fica gravado no próprio item (dano extra, rank, dono), então a descrição aparece igual para todos.
 * Armas que evoluem ficam vinculadas a quem recebeu e ganham dano a cada nível do dono.
 * Estoque emitido em <mundo>/frx_codex.json; o painel recebe a contagem para mostrar.
 */
public class Codex {
    public static final String TAG = "frx_codex", RANK = "frx_codex_rank";
    private static final String NUM = "frx_codex_num", TOTAL = "frx_codex_total", DONO = "frx_codex_dono", DONO_NOME = "frx_codex_dono_nome",
            DANO = "frx_codex_dano", BASE = "frx_codex_base", POR_NIVEL = "frx_codex_pn", NV = "frx_codex_nv", DESC = "frx_codex_desc";
    private static final UUID MOD_DANO = UUID.fromString("2f6c1d7a-9b3e-4f55-8a21-6c0d4e9b7a11");

    public record Def(String codigo, String nome, String descricao, String itemBase, int rank, int raridade, double dano, int limite,
                      boolean ferreiro, int preco, int nivelMin, boolean evolui, double danoPorNivel, boolean umaPorJogador, List<String> encantos, int modelo,
                      boolean inquebravel, boolean nivelUso) { }

    private static volatile Map<String, Def> DEFS = Map.of();
    private static JsonObject estoque = new JsonObject();
    private static MinecraftServer servidor;

    public Codex() {}

    // ------------------------------------------------------------------ definições (painel)

    public static void recarregar() {
        PainelApi.lerCodex().thenAccept(resp -> {
            Map<String, Def> novo = new LinkedHashMap<>();
            JsonArray arr = resp.has("armas") && resp.get("armas").isJsonArray() ? resp.getAsJsonArray("armas") : new JsonArray();
            for (JsonElement el : arr) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    List<String> enc = new ArrayList<>();
                    if (o.has("encantos") && o.get("encantos").isJsonArray()) for (JsonElement x : o.getAsJsonArray("encantos")) enc.add(x.getAsString());
                    Def d = new Def(o.get("codigo").getAsString(), o.get("nome").getAsString(), o.get("descricao").getAsString(), o.get("itemBase").getAsString(),
                            o.get("rank").getAsInt(), o.get("raridade").getAsInt(), o.get("dano").getAsDouble(), o.get("limite").getAsInt(),
                            o.get("ferreiro").getAsBoolean(), o.get("preco").getAsInt(), o.get("nivelMin").getAsInt(),
                            o.get("evolui").getAsBoolean(), o.get("danoPorNivel").getAsDouble(), o.get("umaPorJogador").getAsBoolean(), enc,
                            o.has("modelo") ? o.get("modelo").getAsInt() : 0,
                            o.has("inquebravel") && o.get("inquebravel").getAsBoolean(),
                            o.has("nivelUso") && o.get("nivelUso").getAsBoolean());
                    novo.put(d.codigo(), d);
                } catch (Exception e) { FrxWorld.LOG.warn("Códex: arma inválida vinda do painel: {}", e.toString()); }
            }
            DEFS = novo;
            // pacote de recursos com a aparência das armas: se mudou, quem está online recebe o novo
            String url = "", sha = "";
            if (resp.has("pacote") && resp.get("pacote").isJsonObject()) {
                JsonObject pc = resp.getAsJsonObject("pacote");
                url = pc.get("url").getAsString(); sha = pc.get("sha1").getAsString();
            }
            boolean mudou = !sha.equals(pacoteSha);
            pacoteUrl = url; pacoteSha = sha;
            MinecraftServer s = servidor;
            if (mudou && s != null && !sha.isEmpty()) s.execute(() -> { for (ServerPlayer p : s.getPlayerList().getPlayers()) enviarPacote(p); });
        }).exceptionally(e -> null);
    }

    private static volatile String pacoteUrl = "", pacoteSha = "";

    private static void enviarPacote(ServerPlayer p) {
        if (pacoteUrl.isEmpty() || pacoteSha.isEmpty()) return;
        p.sendTexturePack(pacoteUrl, pacoteSha, false, Component.literal("O FRX-WORLD usa um pacote com a aparência das armas únicas do Códex e as músicas dos discos. Aceite para ver e ouvir tudo."));
    }

    /** Quem entra recebe o pacote de recursos das armas (o jogo guarda e só baixa de novo se mudar). */
    @SubscribeEvent
    public void aoEntrar(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) enviarPacote(p);
    }

    public static Def def(String codigo) { return DEFS.get(codigo); }
    public static String codigoDe(ItemStack s) { return s.hasTag() && s.getTag().contains(TAG) ? s.getTag().getString(TAG) : null; }

    // ------------------------------------------------------------------ estoque (mundo)

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_codex.json"); }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        servidor = ev.getServer();
        try {
            estoque = Files.exists(arquivo(servidor)) ? JsonParser.parseString(Files.readString(arquivo(servidor), StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        } catch (Exception e) { FrxWorld.LOG.warn("Códex: não li frx_codex.json: {}", e.toString()); estoque = new JsonObject(); }
        recarregar();
        avisarPainel();
    }

    private static JsonObject linha(String codigo) {
        if (!estoque.has(codigo)) { JsonObject o = new JsonObject(); o.addProperty("emitidas", 0); o.add("donos", new JsonObject()); estoque.add(codigo, o); }
        JsonObject o = estoque.getAsJsonObject(codigo);
        if (!o.has("donos")) o.add("donos", new JsonObject());
        return o;
    }

    private static void gravar() {
        if (servidor == null) return;
        try { Files.writeString(arquivo(servidor), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(estoque), StandardCharsets.UTF_8); }
        catch (Exception e) { FrxWorld.LOG.warn("Códex: não gravei frx_codex.json: {}", e.toString()); }
        avisarPainel();
    }

    /** Maior nível de uso já alcançado por uma unidade deste item (vai para o site, em Armas Únicas). */
    static void registrarNivelUso(String codigo, int nivel) {
        if (codigo == null) return;
        JsonObject l = linha(codigo);
        if (l.has("nivelUso") && l.get("nivelUso").getAsInt() >= nivel) return;
        l.addProperty("nivelUso", nivel);
        gravar();
    }

    private static void avisarPainel() {
        // armas entregues antes de existir o registro do primeiro dono: vale o primeiro da lista, sem data
        for (var en : estoque.entrySet()) {
            if (!en.getValue().isJsonObject()) continue;
            JsonObject l = en.getValue().getAsJsonObject();
            if (l.has("primeiro") || !l.has("donos") || l.getAsJsonObject("donos").size() == 0) continue;
            JsonObject pr = new JsonObject();
            pr.addProperty("nome", l.getAsJsonObject("donos").entrySet().iterator().next().getValue().getAsString());
            pr.addProperty("quando", 0);
            l.add("primeiro", pr);
        }
        JsonObject corpo = new JsonObject();
        corpo.add("estoque", estoque);
        PainelApi.enviarCodex(corpo);
    }

    public static int emitidas(String codigo) { return estoque.has(codigo) ? estoque.getAsJsonObject(codigo).get("emitidas").getAsInt() : 0; }
    public static int restam(Def d) { return d.limite() <= 0 ? Integer.MAX_VALUE : Math.max(0, d.limite() - emitidas(d.codigo())); }
    private static boolean jaTem(ServerPlayer p, Def d) { return estoque.has(d.codigo()) && linha(d.codigo()).getAsJsonObject("donos").has(p.getUUID().toString()); }

    /** null = pode receber; senão, o motivo. */
    public static String recusa(ServerPlayer p, Def d) {
        if (restam(d) <= 0) return d.nome() + " está esgotada: todas as " + d.limite() + " já têm dono.";
        if (d.umaPorJogador() && jaTem(p, d)) return "Você já recebeu " + d.nome() + ". Cada aventureiro só pode ter uma.";
        return null;
    }

    // ------------------------------------------------------------------ o item

    static void linhaLore(ListTag lore, String texto, ChatFormatting cor) {
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(texto).withStyle(st -> st.withItalic(false).withColor(cor)))));
    }

    /** Reescreve a descrição a partir do que está gravado no item. */
    private static void descrever(ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        s.getOrCreateTagElement("display").remove("Lore");
        t.remove("frx_lore");
        Armas.atualizarDescricao(s);   // primeira linha: raridade e aprimoramento
        ListTag lore = s.getOrCreateTagElement("display").getList("Lore", net.minecraft.nbt.Tag.TAG_STRING);
        String desc = t.getString(DESC);
        StringBuilder atual = new StringBuilder();
        for (String palavra : desc.split("\\s+")) {
            if (palavra.isEmpty()) continue;
            if (atual.length() + palavra.length() > 38) { linhaLore(lore, atual.toString().trim(), ChatFormatting.GRAY); atual.setLength(0); }
            atual.append(palavra).append(' ');
        }
        if (atual.length() > 0) linhaLore(lore, atual.toString().trim(), ChatFormatting.GRAY);
        double dano = t.getDouble(DANO);
        if (dano > 0) linhaLore(lore, "+" + String.format(java.util.Locale.ROOT, "%.1f", dano) + (s.getItem() instanceof ArmorItem ? " de armadura" : " de dano"), ChatFormatting.RED);
        if (t.getDouble(POR_NIVEL) > 0) {
            linhaLore(lore, "Evolui com o dono: +" + String.format(java.util.Locale.ROOT, "%.2f", t.getDouble(POR_NIVEL)) + (s.getItem() instanceof ArmorItem ? " de armadura" : " de dano"), ChatFormatting.LIGHT_PURPLE);
            linhaLore(lore, "por nível do personagem (agora " + Math.max(1, t.getInt(NV)) + ")", ChatFormatting.LIGHT_PURPLE);
        }
        NivelUso.descrever(s, lore);   // nível do próprio item, que sobe com o uso
        if (t.hasUUID(DONO)) linhaLore(lore, "Vinculada a " + t.getString(DONO_NOME) + ": só ele empunha", ChatFormatting.DARK_PURPLE);
        int num = t.getInt(NUM), total = t.getInt(TOTAL);
        linhaLore(lore, num > 0 ? (total > 0 ? "Arma única · nº " + num + " de " + total : "Arma única · nº " + num) : (total > 0 ? "Arma única · só " + total + " no mundo" : "Arma única"), ChatFormatting.GOLD);
        s.getOrCreateTagElement("display").put("Lore", lore);
        // a descrição foi refeita do zero: devolve as linhas das runas e da bênção que o item já tinha
        if (t.contains("frx_runas")) br.com.frxcode.frxworld.aventura.Runas.reescreverLore(s);
        br.com.frxcode.frxworld.reinos.Bencao.reescreverLore(s);
    }

    /** Monta a arma. numero 0 = vitrine da loja (sem dono nem número). */
    private static ItemStack montar(Def d, int numero, ServerPlayer dono) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(d.itemBase()));
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        CompoundTag t = s.getOrCreateTag();
        t.putString(TAG, d.codigo());
        t.putInt(RANK, Math.max(0, Math.min(6, d.rank())));
        t.putInt(NUM, numero);
        t.putInt(TOTAL, Math.max(0, d.limite()));
        t.putString(DESC, d.descricao());
        if (d.modelo() > 0) t.putInt("CustomModelData", d.modelo());   // aparência desenhada no painel
        t.putDouble(BASE, d.dano());
        if (d.inquebravel()) t.putBoolean("Unbreakable", true);
        if (d.nivelUso() && !d.evolui()) t.putBoolean(NivelUso.LIGADO, true);
        int nivel = dono != null && Progressao.temDados(dono) ? Progressao.dados(dono).nivel() : 1;
        if (d.evolui()) {
            t.putDouble(POR_NIVEL, d.danoPorNivel());
            t.putInt(NV, nivel);
            if (dono != null) { t.putUUID(DONO, dono.getUUID()); t.putString(DONO_NOME, dono.getGameProfile().getName()); }
        }
        t.putDouble(DANO, d.dano() + (d.evolui() ? d.danoPorNivel() * nivel : 0));
        for (String e : d.encantos()) {
            String[] c = e.split("\\*");
            Enchantment enc = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.tryParse(c[0].trim()));
            int nv = 1;
            try { if (c.length > 1) nv = Integer.parseInt(c[1].trim()); } catch (NumberFormatException ignorado) { }
            if (enc != null) s.enchant(enc, Math.max(1, Math.min(10, nv)));
        }
        t.putInt(Armas.RARIDADE, Math.max(0, Math.min(4, d.raridade())));
        ChatFormatting cor = switch (Math.max(0, Math.min(4, d.raridade()))) { case 1 -> ChatFormatting.GREEN; case 2 -> ChatFormatting.BLUE; case 3 -> ChatFormatting.DARK_PURPLE; case 4 -> ChatFormatting.GOLD; default -> ChatFormatting.WHITE; };
        s.setHoverName(Component.literal(d.nome()).withStyle(st -> st.withItalic(false).withColor(cor).withBold(true)));
        descrever(s);
        return s;
    }

    public static ItemStack vitrine(Def d) { return montar(d, 0, null); }

    /** Entrega uma unidade nova ao jogador, contando no total do mundo. Vazio se não pode (ver recusa). */
    public static ItemStack emitir(ServerPlayer p, Def d) {
        if (recusa(p, d) != null) return ItemStack.EMPTY;
        JsonObject l = linha(d.codigo());
        int numero = l.get("emitidas").getAsInt() + 1;
        ItemStack s = montar(d, numero, p);
        if (s.isEmpty()) return s;
        l.addProperty("emitidas", numero);
        l.getAsJsonObject("donos").addProperty(p.getUUID().toString(), p.getGameProfile().getName());
        if (!l.has("primeiro")) {   // primeiro dono: vai para o mural de armas do site
            JsonObject pr = new JsonObject();
            pr.addProperty("nome", br.com.frxcode.frxworld.acesso.Personagens.nome(p));
            pr.addProperty("quando", System.currentTimeMillis());
            l.add("primeiro", pr);
        }
        gravar();
        FrxWorld.LOG.info("Códex: {} nº {} entregue a {}", d.codigo(), numero, p.getGameProfile().getName());
        return s;
    }

    /** Recompensa de missão: item "codex:<código>". true se tratou (entregou ou avisou por que não). */
    public static boolean darPorCodigo(ServerPlayer p, String codigo) {
        Def d = DEFS.get(codigo);
        if (d == null) { p.sendSystemMessage(Component.literal("A arma prometida não existe mais no Códex. Avise a administração.").withStyle(ChatFormatting.RED)); return true; }
        String motivo = recusa(p, d);
        if (motivo != null) { p.sendSystemMessage(Component.literal(motivo).withStyle(ChatFormatting.RED)); return true; }
        ItemStack s = emitir(p, d);
        if (s.isEmpty()) return true;
        Component nome = s.getHoverName();
        if (!p.getInventory().add(s)) p.drop(s, false);
        p.sendSystemMessage(Component.literal("⚔ Você recebeu uma arma única: ").withStyle(ChatFormatting.GOLD).append(nome));
        return true;
    }

    /** Armas do Códex à venda neste NPC para este jogador (a vitrine; a unidade de verdade nasce na compra). */
    public static List<MerchantOffer> ofertas(ServerPlayer p, boolean armeiro, double desconto) {
        List<MerchantOffer> l = new ArrayList<>();
        int nivel = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
        for (Def d : DEFS.values()) {
            if (!d.ferreiro() || d.preco() <= 0 || nivel < d.nivelMin() || recusa(p, d) != null) continue;
            ItemStack v = vitrine(d);
            if (v.isEmpty() || (v.getItem() instanceof ArmorItem) != armeiro) continue;
            l.add(br.com.frxcode.frxworld.reinos.Lojas.ofertaAvulsa(v, d.preco(), desconto));
        }
        return l;
    }

    // ------------------------------------------------------------------ efeitos

    /** O dano (ou a armadura) extra vem do número gravado no item. */
    @SubscribeEvent
    public void atributos(ItemAttributeModifierEvent e) {
        ItemStack s = e.getItemStack();
        if (!s.hasTag() || !s.getTag().contains(TAG)) return;
        double v = s.getTag().getDouble(DANO);
        if (v <= 0) return;
        if (s.getItem() instanceof ArmorItem a) {
            if (e.getSlotType() == a.getEquipmentSlot()) e.addModifier(Attributes.ARMOR, new AttributeModifier(MOD_DANO, "FRX codex", v, AttributeModifier.Operation.ADDITION));
        } else if (e.getSlotType() == EquipmentSlot.MAINHAND) {
            e.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(MOD_DANO, "FRX codex", v, AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * A cada 2 s: armas que evoluem acompanham o nível do dono, e toda arma do Códex já entregue acompanha o que
     * está no painel (dano extra e dano por nível): mudar a arma no painel muda também as que já têm dono.
     */
    @SubscribeEvent
    public void evoluir(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 40 != 11 || !Progressao.temDados(p)) return;
        int nivel = Progressao.dados(p).nivel();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty() || !s.hasTag()) continue;
            CompoundTag t = s.getTag();
            if (!t.contains(TAG)) continue;
            Def d = DEFS.get(t.getString(TAG));
            boolean mudouPainel = d != null && (Math.abs(t.getDouble(BASE) - d.dano()) > 1e-6 || (t.contains(POR_NIVEL) && Math.abs(t.getDouble(POR_NIVEL) - d.danoPorNivel()) > 1e-6));
            if (d != null && t.getBoolean("Unbreakable") != d.inquebravel()) {
                if (d.inquebravel()) { t.putBoolean("Unbreakable", true); s.setDamageValue(0); }
                else t.remove("Unbreakable");
                if (d.inquebravel()) p.sendSystemMessage(Component.literal("⚔ ").withStyle(ChatFormatting.GOLD).append(s.getHoverName())
                        .append(Component.literal(" agora é inquebrável: nunca mais se desgasta.").withStyle(ChatFormatting.GOLD)));
            }
            // modo de evoluir trocado no painel: "com o jogador" liga ou desliga também nos itens que já têm dono
            if (d != null && !d.evolui() && t.contains(POR_NIVEL)) {
                t.remove(POR_NIVEL); t.remove(NV); t.remove(DONO); t.remove(DONO_NOME);
                t.putDouble(DANO, t.getDouble(BASE));
                t.putBoolean(NivelUso.MUDOU, true);
            } else if (d != null && d.evolui() && !t.contains(POR_NIVEL)) {
                t.putDouble(POR_NIVEL, d.danoPorNivel());
                t.putInt(NV, nivel);
                t.putUUID(DONO, p.getUUID());
                t.putString(DONO_NOME, p.getGameProfile().getName());
                t.putDouble(DANO, t.getDouble(BASE) + d.danoPorNivel() * nivel);
                t.putBoolean(NivelUso.MUDOU, true);
            }
            if (d != null && t.getBoolean(NivelUso.LIGADO) != (d.nivelUso() && !d.evolui())) {   // opção ligada ou desligada no painel
                if (d.nivelUso() && !d.evolui()) t.putBoolean(NivelUso.LIGADO, true); else t.remove(NivelUso.LIGADO);
                t.putBoolean(NivelUso.MUDOU, true);
            }
            // 3.0.10: refaz uma vez a descrição para devolver as linhas de runa e bênção apagadas pela versão antiga
            if (!t.getBoolean("frx_lore2")) { t.putBoolean("frx_lore2", true); t.putBoolean(NivelUso.MUDOU, true); }
            if (t.getBoolean(NivelUso.MUDOU)) { t.remove(NivelUso.MUDOU); descrever(s); }
            if (mudouPainel) {
                t.putDouble(BASE, d.dano());
                if (t.contains(POR_NIVEL)) t.putDouble(POR_NIVEL, d.danoPorNivel());
                double novo = d.dano() + (t.contains(POR_NIVEL) ? t.getDouble(POR_NIVEL) * Math.max(1, t.getInt(NV)) : 0);
                t.putDouble(DANO, novo);
                descrever(s);
                p.sendSystemMessage(Component.literal("⚔ ").withStyle(ChatFormatting.GOLD).append(s.getHoverName())
                        .append(Component.literal(" foi reforjada: agora dá +" + String.format(java.util.Locale.ROOT, "%.1f", novo) + " de dano.").withStyle(ChatFormatting.GOLD)));
            }
            if (t.getDouble(POR_NIVEL) <= 0 || !t.hasUUID(DONO) || !t.getUUID(DONO).equals(p.getUUID()) || t.getInt(NV) == nivel) continue;
            boolean subiu = nivel > t.getInt(NV);
            t.putInt(NV, nivel);
            t.putDouble(DANO, t.getDouble(BASE) + t.getDouble(POR_NIVEL) * nivel);
            descrever(s);
            if (subiu) p.sendSystemMessage(Component.literal("⚔ ").withStyle(ChatFormatting.LIGHT_PURPLE).append(s.getHoverName())
                    .append(Component.literal(" ficou mais forte com você (nível " + nivel + ").").withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }

    /** Arma vinculada só obedece ao dono. */
    @SubscribeEvent
    public void aoBater(AttackEntityEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || p.isCreative()) return;
        ItemStack s = p.getMainHandItem();
        if (!s.hasTag() || !s.getTag().hasUUID(DONO) || s.getTag().getUUID(DONO).equals(p.getUUID())) return;
        ev.setCanceled(true);
        p.displayClientMessage(Component.literal("Esta arma só obedece a " + s.getTag().getString(DONO_NOME) + ".").withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ admin

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.CommandSourceStack> codigos = (c, b) -> SharedSuggestionProvider.suggest(DEFS.keySet(), b);
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("codex")
                        .then(Commands.literal("lista").executes(c -> {
                            if (DEFS.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Nenhuma arma no Códex (crie no painel)."), false);
                            for (Def d : DEFS.values())
                                c.getSource().sendSuccess(() -> Component.literal(d.codigo() + " — " + d.nome() + " · " + emitidas(d.codigo()) + (d.limite() > 0 ? "/" + d.limite() : "") + " no mundo"), false);
                            return 1;
                        }))
                        .then(Commands.literal("recarregar").executes(c -> { recarregar(); c.getSource().sendSuccess(() -> Component.literal("Códex: lendo do painel."), true); return 1; }))
                        .then(Commands.literal("dar").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("codigo", StringArgumentType.word()).suggests(codigos).executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "jogador");
                                    Def d = DEFS.get(StringArgumentType.getString(c, "codigo"));
                                    if (d == null) { c.getSource().sendFailure(Component.literal("Arma desconhecida.")); return 0; }
                                    String motivo = recusa(p, d);
                                    if (motivo != null) { c.getSource().sendFailure(Component.literal(motivo)); return 0; }
                                    darPorCodigo(p, d.codigo());
                                    c.getSource().sendSuccess(() -> Component.literal("[codex] " + d.nome() + " entregue a " + p.getGameProfile().getName()), true);
                                    return 1;
                                }))))
                        .then(Commands.literal("estoque").then(Commands.argument("codigo", StringArgumentType.word()).suggests(codigos)
                                .then(Commands.argument("emitidas", IntegerArgumentType.integer(0)).executes(c -> {
                                    String cod = StringArgumentType.getString(c, "codigo");
                                    int n = IntegerArgumentType.getInteger(c, "emitidas");
                                    linha(cod).addProperty("emitidas", n);
                                    if (n == 0) linha(cod).add("donos", new JsonObject());
                                    gravar();
                                    c.getSource().sendSuccess(() -> Component.literal("[codex] " + cod + ": " + n + " emitidas"), true);
                                    return 1;
                                }))))));
    }
}
