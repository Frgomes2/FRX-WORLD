package br.com.frxcode.frxworld.eventos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Conquistas;
import br.com.frxcode.frxworld.progressao.Sistema;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Torneio de pesca: todo peixe pescado ganha peso (kg) sorteado na faixa da espécie e uma nota de 1 a 5 estrelas.
 * O placar da semana guarda o melhor peixe de cada jogador pela porcentagem da faixa da espécie (um atum enorme e
 * uma tilápia enorme valem igual). Domingo à meia-noite (Brasília) os três primeiros levam prêmio e o placar zera.
 * O Peixe-Rei Dourado (lendário) só morde à noite ou com chuva. Recordes por espécie ficam para sempre.
 * Tudo em <mundo>/frx_pesca.json (o site lê direto).
 */
public class Pesca {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String TAG_PESO = "frx_peso", TAG_PCT = "frx_peso_pct";
    private static final long[] PREMIOS = {3000, 1500, 800};   // cobre: 30, 15 e 8 ouro
    private static final long PREMIO_LENDARIO = 2500;
    private static final double CHANCE_LENDARIO = 0.004;      // por peixe, à noite ou com chuva
    private static final Map<String, double[]> FAIXAS = new HashMap<>();
    private static final Map<String, String> NOMES = new HashMap<>();
    private static JsonObject dados;
    private static boolean sujo;

    static {
        f("minecraft:cod", 1, 12, "Bacalhau"); f("minecraft:salmon", 1, 20, "Salmão"); f("minecraft:tropical_fish", 0.05, 1, "Peixe Tropical"); f("minecraft:pufferfish", 0.2, 3, "Baiacu");
        f("aquaculture:atlantic_cod", 2, 25, "Bacalhau do Atlântico"); f("aquaculture:blackfish", 1, 5, "Peixe-Negro"); f("aquaculture:pacific_halibut", 5, 200, "Linguado do Pacífico");
        f("aquaculture:atlantic_halibut", 5, 300, "Linguado do Atlântico"); f("aquaculture:atlantic_herring", 0.1, 1, "Arenque"); f("aquaculture:pink_salmon", 1, 6, "Salmão Rosa");
        f("aquaculture:pollock", 1, 20, "Escamudo"); f("aquaculture:rainbow_trout", 0.5, 10, "Truta Arco-Íris"); f("aquaculture:bayad", 1, 15, "Bayad"); f("aquaculture:boulti", 0.5, 4, "Tilápia do Nilo");
        f("aquaculture:capitaine", 2, 100, "Perca do Nilo"); f("aquaculture:synodontis", 0.1, 1, "Synodontis"); f("aquaculture:smallmouth_bass", 0.5, 5, "Robalo de Boca Pequena");
        f("aquaculture:bluegill", 0.1, 1.5, "Bluegill"); f("aquaculture:brown_trout", 0.5, 15, "Truta Marrom"); f("aquaculture:carp", 1, 30, "Carpa"); f("aquaculture:catfish", 1, 100, "Bagre");
        f("aquaculture:gar", 2, 60, "Peixe-Agulha"); f("aquaculture:minnow", 0.01, 0.1, "Lambari"); f("aquaculture:muskellunge", 3, 30, "Muskellunge"); f("aquaculture:perch", 0.1, 2, "Perca");
        f("aquaculture:arapaima", 20, 200, "Pirarucu"); f("aquaculture:piranha", 0.3, 3, "Piranha"); f("aquaculture:tambaqui", 3, 40, "Tambaqui"); f("aquaculture:brown_shrooma", 0.5, 3, "Cogupeixe Marrom");
        f("aquaculture:red_shrooma", 0.5, 3, "Cogupeixe Vermelho"); f("aquaculture:jellyfish", 0.1, 5, "Água-Viva"); f("aquaculture:red_grouper", 1, 20, "Garoupa Vermelha"); f("aquaculture:tuna", 10, 400, "Atum");
        f("aquaculture:goldfish", 0.05, 0.5, "Peixinho Dourado");
    }

    private static void f(String id, double min, double max, String nome) { FAIXAS.put(id, new double[]{min, max}); NOMES.put(id, nome); }

    public Pesca() {}

    // ------------------------------------------------------------------ arquivo

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_pesca.json"); }

    private static JsonObject dados(MinecraftServer s) {
        if (dados != null) return dados;
        try { if (Files.exists(arquivo(s))) dados = new Gson().fromJson(Files.readString(arquivo(s), StandardCharsets.UTF_8), JsonObject.class); }
        catch (Exception e) { FrxWorld.LOG.warn("Pesca: não li frx_pesca.json: {}", e.toString()); }
        if (dados == null) dados = new JsonObject();
        if (!dados.has("semana")) { dados.addProperty("semana", semanaAgora()); dados.addProperty("fim", fimDaSemana()); }
        for (String k : new String[]{"linhas", "recordes"}) if (!dados.has(k)) dados.add(k, new JsonObject());
        if (!dados.has("campeoes")) dados.add("campeoes", new JsonArray());
        return dados;
    }

    private static void gravar(MinecraftServer s) {
        if (!sujo || dados == null) return;
        try { Files.writeString(arquivo(s), new GsonBuilder().setPrettyPrinting().create().toJson(dados), StandardCharsets.UTF_8); sujo = false; }
        catch (Exception e) { FrxWorld.LOG.warn("Pesca: não gravei frx_pesca.json: {}", e.toString()); }
    }

    private static String semanaAgora() {
        ZonedDateTime z = ZonedDateTime.now(FUSO);
        return z.get(IsoFields.WEEK_BASED_YEAR) + "-S" + String.format("%02d", z.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Domingo seguinte, 23:59:59 (a semana ISO vai de segunda a domingo). */
    private static long fimDaSemana() {
        ZonedDateTime z = ZonedDateTime.now(FUSO).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).withHour(23).withMinute(59).withSecond(59);
        return z.toInstant().toEpochMilli();
    }

    // ------------------------------------------------------------------ peixe

    static String nomePeixe(String id) { return NOMES.getOrDefault(id, id); }

    private static String kg(double p) { return String.format(Locale.forLanguageTag("pt-BR"), p < 1 ? "%.2f" : "%.1f", p) + " kg"; }

    private static String estrelas(double pct) {
        int n = Math.max(1, Math.min(5, 1 + (int) (pct * 5)));
        return "★".repeat(n) + "☆".repeat(5 - n);
    }

    private static void lore(ItemStack s, List<Component> linhas) {
        CompoundTag d = s.getOrCreateTagElement("display");
        ListTag l = new ListTag();
        for (Component c : linhas) l.add(StringTag.valueOf(Component.Serializer.toJson(c)));
        d.put("Lore", l);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoPescar(ItemFishedEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean noite = !p.level().isDay() || p.level().isRaining();
        for (int i = 0; i < ev.getDrops().size(); i++) {
            ItemStack s = ev.getDrops().get(i);
            ResourceLocation rid = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (rid == null) continue;
            String id = rid.toString();
            if (!FAIXAS.containsKey(id) && !s.is(ItemTags.FISHES)) continue;
            boolean lendario = noite && rnd.nextDouble() < CHANCE_LENDARIO;
            if (lendario) {
                s = new ItemStack(Items.TROPICAL_FISH);
                ev.getDrops().set(i, s);
                id = "frx:peixe_rei";
            }
            double[] fx = lendario ? new double[]{8, 40} : FAIXAS.getOrDefault(id, new double[]{0.5, 5});
            double r = Math.pow(rnd.nextDouble(), 2.2);   // peixes grandes são raros
            double peso = fx[0] + (fx[1] - fx[0]) * r;
            double pct = lendario ? 1.5 : r;               // o lendário vale mais que qualquer peixe comum no placar
            String nome = lendario ? "Peixe-Rei Dourado" : nomePeixe(id);
            s.getOrCreateTag().putDouble(TAG_PESO, peso);
            s.getOrCreateTag().putDouble(TAG_PCT, pct);
            List<Component> l = new ArrayList<>();
            l.add(Component.literal("⚖ " + kg(peso) + "  " + (lendario ? "✦ LENDÁRIO ✦" : estrelas(pct))).withStyle(lendario ? ChatFormatting.GOLD : ChatFormatting.AQUA));
            l.add(Component.literal("Pescado por " + p.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
            lore(s, l);
            if (lendario) {
                s.setHoverName(Component.literal("Peixe-Rei Dourado").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                ListTag brilho = new ListTag(); CompoundTag e = new CompoundTag(); e.putString("id", "minecraft:luck_of_the_sea"); e.putShort("lvl", (short) 1); brilho.add(e);
                s.getOrCreateTag().put("Enchantments", brilho);
                s.getOrCreateTag().putInt("HideFlags", 1);   // brilho sem mostrar o encantamento
                Moedas.dar(p, PREMIO_LENDARIO);
                p.server.getPlayerList().broadcastSystemMessage(Component.literal("🎣 " + p.getGameProfile().getName() + " fisgou o lendário Peixe-Rei Dourado (" + kg(peso) + ")!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            }
            registrar(p, id, nome, peso, pct);
        }
    }

    private static void registrar(ServerPlayer p, String id, String nome, double peso, double pct) {
        JsonObject d = dados(p.server);
        Conquistas.somar(p, "pesca_peixes", 1);
        JsonObject linhas = d.getAsJsonObject("linhas");
        String u = p.getUUID().toString();
        JsonObject eu = linhas.has(u) ? linhas.getAsJsonObject(u) : new JsonObject();
        eu.addProperty("nome", p.getGameProfile().getName());
        eu.addProperty("qtd", (eu.has("qtd") ? eu.get("qtd").getAsInt() : 0) + 1);
        boolean melhor = !eu.has("pct") || pct > eu.get("pct").getAsDouble();
        if (melhor) { eu.addProperty("pct", pct); eu.addProperty("peso", peso); eu.addProperty("especie", nome); }
        linhas.add(u, eu);
        // recorde da espécie (para sempre)
        JsonObject rec = d.getAsJsonObject("recordes");
        boolean recorde = !rec.has(nome) || peso > rec.getAsJsonObject(nome).get("peso").getAsDouble();
        if (recorde) {
            JsonObject r = new JsonObject();
            r.addProperty("nome", p.getGameProfile().getName()); r.addProperty("peso", peso); r.addProperty("quando", System.currentTimeMillis());
            boolean tinha = rec.has(nome);
            rec.add(nome, r);
            if (tinha && pct >= 0.5) p.server.getPlayerList().broadcastSystemMessage(Component.literal("🎣 Recorde: " + p.getGameProfile().getName() + " pescou um " + nome + " de " + kg(peso) + "!")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        sujo = true;
        int pos = posicao(d, u);
        MutableComponent msg = Component.literal("🎣 " + nome + ": " + kg(peso) + " " + (pct > 1 ? "✦" : estrelas(pct))).withStyle(ChatFormatting.AQUA);
        if (melhor) msg.append(Component.literal("  · seu melhor da semana (" + pos + "º no torneio)").withStyle(ChatFormatting.GOLD));
        else if (recorde) msg.append(Component.literal("  · recorde da espécie").withStyle(ChatFormatting.GOLD));
        p.displayClientMessage(msg, true);
    }

    private static List<Map.Entry<String, JsonObject>> placar(JsonObject d) {
        List<Map.Entry<String, JsonObject>> l = new ArrayList<>();
        for (var e : d.getAsJsonObject("linhas").entrySet()) l.add(Map.entry(e.getKey(), e.getValue().getAsJsonObject()));
        l.sort((a, b) -> Double.compare(b.getValue().get("pct").getAsDouble(), a.getValue().get("pct").getAsDouble()));
        return l;
    }

    private static int posicao(JsonObject d, String u) {
        List<Map.Entry<String, JsonObject>> l = placar(d);
        for (int i = 0; i < l.size(); i++) if (l.get(i).getKey().equals(u)) return i + 1;
        return l.size();
    }

    // ------------------------------------------------------------------ fim da semana

    @SubscribeEvent
    public void aCadaMinuto(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 1200 != 611) return;
        MinecraftServer s = ev.getServer();
        JsonObject d = dados(s);
        if (!d.get("semana").getAsString().equals(semanaAgora())) encerrar(s, d);
        gravar(s);
    }

    private static void encerrar(MinecraftServer s, JsonObject d) {
        List<Map.Entry<String, JsonObject>> l = placar(d);
        StringBuilder txt = new StringBuilder();
        for (int i = 0; i < Math.min(3, l.size()); i++) {
            JsonObject o = l.get(i).getValue();
            String nome = o.get("nome").getAsString();
            txt.append(i == 0 ? "" : " · ").append(i + 1).append("º ").append(nome).append(" (").append(o.get("especie").getAsString()).append(", ").append(kg(o.get("peso").getAsDouble())).append(")");
            ServerPlayer p = s.getPlayerList().getPlayer(java.util.UUID.fromString(l.get(i).getKey()));
            if (i == 0) {
                JsonObject c = new JsonObject();
                c.addProperty("semana", d.get("semana").getAsString()); c.addProperty("nome", nome);
                c.addProperty("especie", o.get("especie").getAsString()); c.addProperty("peso", o.get("peso").getAsDouble());
                d.getAsJsonArray("campeoes").add(c);
            }
            if (p != null) {
                Moedas.dar(p, PREMIOS[i]);
                if (i == 0) Conquistas.somar(p, "pesca_vitorias", 1);
                Sistema.avisar(p, "Torneio de pesca", "Você ficou em " + (i + 1) + "º lugar e ganhou " + PREMIOS[i] / 100 + " moedas de ouro.", Sistema.OURO);
            } else {
                // offline: entra no próximo login pelos ajustes de admin
                br.com.frxcode.frxworld.progressao.AjustesAdmin.pendente(s, nome, "moedas", String.valueOf(PREMIOS[i]));
                br.com.frxcode.frxworld.progressao.AjustesAdmin.pendente(s, nome, "aviso", "Torneio de pesca: você ficou em " + (i + 1) + "º lugar e ganhou " + PREMIOS[i] / 100 + " moedas de ouro.");
                if (i == 0) br.com.frxcode.frxworld.progressao.AjustesAdmin.pendente(s, nome, "contador", "pesca_vitorias");
            }
        }
        if (txt.length() > 0) s.getPlayerList().broadcastSystemMessage(Component.literal("🎣 Fim do torneio de pesca da semana! " + txt).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        d.add("linhas", new JsonObject());
        d.addProperty("semana", semanaAgora());
        d.addProperty("fim", fimDaSemana());
        sujo = true;
        FrxWorld.LOG.info("Pesca: torneio encerrado: {}", txt);
    }

    // ------------------------------------------------------------------ comando

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("pesca").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            JsonObject d = dados(p.server);
            long resta = Math.max(0, d.get("fim").getAsLong() - System.currentTimeMillis());
            p.sendSystemMessage(Component.literal("🎣 Torneio de pesca da semana · termina em " + resta / 86_400_000L + "d " + (resta / 3_600_000L) % 24 + "h").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            List<Map.Entry<String, JsonObject>> l = placar(d);
            for (int i = 0; i < Math.min(5, l.size()); i++) {
                JsonObject o = l.get(i).getValue();
                p.sendSystemMessage(Component.literal("  " + (i + 1) + "º " + o.get("nome").getAsString() + " — " + o.get("especie").getAsString() + ", " + kg(o.get("peso").getAsDouble()))
                        .withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            }
            if (l.isEmpty()) p.sendSystemMessage(Component.literal("  Ninguém pescou nada ainda esta semana.").withStyle(ChatFormatting.GRAY));
            p.sendSystemMessage(Component.literal("  Prêmios: 1º 30 ouro · 2º 15 · 3º 8. Vale o maior peixe para a espécie. À noite ou com chuva, o Peixe-Rei Dourado pode morder.").withStyle(ChatFormatting.GRAY));
            return 1;
        }));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2)).then(Commands.literal("pesca").then(Commands.literal("encerrar").executes(c -> {
            JsonObject d = dados(c.getSource().getServer());
            encerrar(c.getSource().getServer(), d);
            gravar(c.getSource().getServer());
            return 1;
        }))));
    }
}
