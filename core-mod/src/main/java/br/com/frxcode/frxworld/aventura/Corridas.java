package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Conquistas;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corridas: pistas com largada, pontos de controle e chegada, a pé, a cavalo, de barco ou de élitro.
 * Na largada (anel de fogo), do jeito certo (montado no cavalo, no barco...), aparece o botão "Iniciar corrida". Clicando, começa a
 * contagem de 5 s com a montaria travada; no "Vai!" e na chegada estouram fogos. Uma corrida por vez em cada pista.
 * Pista pode ter várias voltas, limite de tempo próprio e um placar gigante (texto flutuante) com os melhores tempos.
 * O próximo ponto aparece como uma coluna de luz; o tempo corre na barra de ação. Recordes por pista (10 melhores)
 * em <mundo>/frx_corridas.json, que o site lê. Pistas criadas pelo admin com /frx corrida.
 */
public class Corridas {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final long PREMIO_DIA = 200, PREMIO_RECORDE = 1000;   // cobre: 2 ouro na 1ª do dia em cada pista, 10 ouro pelo recorde
    private static final long LIMITE_PADRAO_MIN = 15, CONTAGEM_MS = 5000;
    private static final List<String> TIPOS = List.of("pe", "cavalo", "barco", "elitro");
    private static JsonObject dados;
    private static boolean sujo;

    /** Largada em grupo: todos que estavam na linha quando alguém clicou em Iniciar. */
    private static final class Grupo {
        final String pista; final List<UUID> todos = new ArrayList<>(); final List<String> chegada = new ArrayList<>(); final List<Long> tempos = new ArrayList<>();
        Grupo(String pista) { this.pista = pista; }
    }
    private static final long PREMIO_VENCEDOR = 500;   // 5 ouro para quem vence uma largada com 2 ou mais

    private static final class Volta {
        Grupo grupo;
        final String pista; int proximo = 1, volta = 1; long inicio, inicioVolta; long largada; long foraDesde; Vec3 ultimo, travado; int ultimoSeg = -1;
        Volta(String pista, long largada) { this.pista = pista; this.largada = largada; }
    }
    private static final Map<UUID, Volta> voltas = new HashMap<>();
    private static final Map<UUID, Long> dica = new HashMap<>();
    /** Quem acabou de chegar numa pista de volta (chegada = largada) só larga de novo depois de sair do anel. */
    private static final Map<UUID, String> saindo = new HashMap<>();

    private static long limiteMs(JsonObject pista) { return (pista.has("limite") ? pista.get("limite").getAsLong() : LIMITE_PADRAO_MIN) * 60_000L; }

    /** Quem está correndo nesta pista agora (só uma corrida por vez). */
    private static UUID ocupada(String id) {
        for (Map.Entry<UUID, Volta> e : voltas.entrySet()) if (e.getValue().pista.equals(id)) return e.getKey();
        return null;
    }

    /**
     * Circuito (pista de voltas ou marcada como circuito) fecha na largada: depois do último ponto marcado vem a
     * linha de largada/chegada, a não ser que o último ponto já esteja em cima dela. Pista de ida (A → B) termina no último ponto.
     */
    private static boolean fechaNaLargada(JsonObject pista) {
        boolean circuito = voltasDa(pista) > 1 || (pista.has("circuito") && pista.get("circuito").getAsBoolean());
        int n = qtdPontos(pista);
        return circuito && n >= 2 && ponto(pista, n - 1).distanceTo(ponto(pista, 0)) > raio(pista) * 1.5;
    }

    /** Índice do último alvo de cada volta (a linha de chegada). */
    private static int ultimoAlvo(JsonObject pista) { return fechaNaLargada(pista) ? qtdPontos(pista) : qtdPontos(pista) - 1; }

    /** Posição do alvo k (k == qtdPontos é a própria largada, quando o circuito fecha nela). */
    private static Vec3 alvo(JsonObject pista, int k) { return k >= qtdPontos(pista) ? ponto(pista, 0) : ponto(pista, k); }

    private static int voltasDa(JsonObject pista) { return pista.has("voltas") ? Math.max(1, pista.get("voltas").getAsInt()) : 1; }

    public Corridas() {}

    /** Está no meio de uma corrida? (o X1 não chama quem está correndo) */
    public static boolean correndo(net.minecraft.world.entity.player.Player p) { return voltas.containsKey(p.getUUID()); }

    // ------------------------------------------------------------------ arquivo

    private static Path arquivo(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_corridas.json"); }

    private static JsonObject dados(MinecraftServer s) {
        if (dados != null) return dados;
        try { if (Files.exists(arquivo(s))) dados = new Gson().fromJson(Files.readString(arquivo(s), StandardCharsets.UTF_8), JsonObject.class); }
        catch (Exception e) { FrxWorld.LOG.warn("Corridas: não li frx_corridas.json: {}", e.toString()); }
        if (dados == null) dados = new JsonObject();
        if (!dados.has("pistas")) dados.add("pistas", new JsonObject());
        if (!dados.has("diario")) dados.add("diario", new JsonObject());
        return dados;
    }

    private static void gravar(MinecraftServer s) {
        if (!sujo || dados == null) return;
        try { Files.writeString(arquivo(s), new GsonBuilder().setPrettyPrinting().create().toJson(dados), StandardCharsets.UTF_8); sujo = false; }
        catch (Exception e) { FrxWorld.LOG.warn("Corridas: não gravei frx_corridas.json: {}", e.toString()); }
    }

    private static JsonObject pistas(MinecraftServer s) { return dados(s).getAsJsonObject("pistas"); }

    private static Vec3 ponto(JsonObject pista, int i) {
        JsonArray a = pista.getAsJsonArray("pontos").get(i).getAsJsonArray();
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static int qtdPontos(JsonObject pista) { return pista.getAsJsonArray("pontos").size(); }

    private static double raio(JsonObject pista) { return pista.has("raio") ? pista.get("raio").getAsDouble() : pista.get("tipo").getAsString().equals("elitro") ? 10 : 5; }

    static String nomeTipo(String t) {
        return switch (t) { case "cavalo" -> "a cavalo"; case "barco" -> "de barco"; case "elitro" -> "de élitro"; default -> "a pé"; };
    }

    static String tempo(long ms) { return String.format("%d:%02d.%02d", ms / 60_000, (ms / 1000) % 60, (ms / 10) % 100); }

    /** O jogador está do jeito que a pista pede? */
    private static boolean jeitoCerto(ServerPlayer p, String tipo) {
        return switch (tipo) {
            case "cavalo" -> p.getVehicle() instanceof AbstractHorse;
            case "barco" -> p.getVehicle() instanceof Boat;
            case "elitro" -> !p.isPassenger();
            default -> !p.isPassenger() && !p.isFallFlying() && !p.getAbilities().flying;
        };
    }

    private static String comoFicar(String tipo) {
        return switch (tipo) { case "cavalo" -> "montado num cavalo"; case "barco" -> "dentro de um barco"; case "elitro" -> "com élitros"; default -> "a pé, sem montaria"; };
    }

    private static void titulo(ServerPlayer p, String t, String sub, ChatFormatting cor) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 6));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t).withStyle(cor, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
    }

    // ------------------------------------------------------------------ ciclo

    @SubscribeEvent
    public void aoTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.level().dimension() != Level.OVERWORLD) return;
        if (p.tickCount % 2 != 0) return;
        long agora = System.currentTimeMillis();
        Volta v = voltas.get(p.getUUID());
        JsonObject todas = pistas(p.server);
        if (v != null) {
            JsonObject pista = todas.has(v.pista) ? todas.getAsJsonObject(v.pista) : null;
            if (pista == null) { voltas.remove(p.getUUID()); return; }
            correr(p, v, pista, agora);
            return;
        }
        if (p.tickCount % 10 != 0 || p.isSpectator()) return;
        // perto de alguma largada?
        for (Map.Entry<String, JsonElement> e : todas.entrySet()) {
            JsonObject pista = e.getValue().getAsJsonObject();
            if (qtdPontos(pista) < 2) continue;
            Vec3 l = ponto(pista, 0);
            double d = p.position().distanceTo(l);
            if (d > 48) continue;
            ServerLevel n = p.serverLevel();
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                n.sendParticles(p, ParticleTypes.FLAME, true, l.x + Math.cos(a) * 2.5, l.y + 0.2, l.z + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
            }
            if (e.getKey().equals(saindo.get(p.getUUID()))) { if (d > 8) saindo.remove(p.getUUID()); continue; }
            if (d > 3) continue;
            String tipo = pista.get("tipo").getAsString();
            if (!jeitoCerto(p, tipo)) {
                if (agora - dica.getOrDefault(p.getUUID(), 0L) > 3000) {
                    dica.put(p.getUUID(), agora);
                    p.displayClientMessage(Component.literal("🏁 " + pista.get("nome").getAsString() + " (" + nomeTipo(tipo) + "): fique na largada " + comoFicar(tipo) + " para largar.").withStyle(ChatFormatting.YELLOW), true);
                }
                continue;
            }
            if (agora - dica.getOrDefault(p.getUUID(), 0L) < 6000) continue;
            dica.put(p.getUUID(), agora);
            UUID dono = ocupada(e.getKey());
            if (dono != null) {
                ServerPlayer outro = p.server.getPlayerList().getPlayer(dono);
                p.displayClientMessage(Component.literal("🏁 Pista ocupada: " + (outro != null ? outro.getGameProfile().getName() : "alguém") + " está correndo. Espere terminar.").withStyle(ChatFormatting.YELLOW), true);
                continue;
            }
            p.displayClientMessage(Component.literal("🏁 " + pista.get("nome").getAsString() + ": clique em [Iniciar corrida] no chat · quem estiver na linha larga junto").withStyle(ChatFormatting.GOLD), true);
            p.sendSystemMessage(Component.literal("🏁 " + pista.get("nome").getAsString() + " · " + nomeTipo(tipo) + (voltasDa(pista) > 1 ? " · " + voltasDa(pista) + " voltas" : "") + "  ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("[▶ Iniciar corrida]").withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/corrida iniciar " + e.getKey()))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Contagem de 5 s e largada"))))));
            return;
        }
    }

    private static void cancelar(ServerPlayer p, String motivo) {
        Volta v = voltas.remove(p.getUUID());
        if (v != null) { saindo.put(p.getUUID(), v.pista); atualizarPlacar(p.server, v.pista); fimDoGrupo(p.server, v.grupo); }
        p.displayClientMessage(Component.literal("🏁 Corrida cancelada: " + motivo).withStyle(ChatFormatting.RED), true);
        p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 1f);
    }

    private static void correr(ServerPlayer p, Volta v, JsonObject pista, long agora) {
        String tipo = pista.get("tipo").getAsString();
        Vec3 pos = p.position();
        // teleporte (waystone, pérola...) acaba com a corrida
        if (v.ultimo != null && pos.distanceTo(v.ultimo) > 40) { cancelar(p, "teleporte não vale."); return; }
        v.ultimo = pos;
        if (!p.isAlive()) { cancelar(p, "você caiu."); return; }
        // contagem
        if (v.inicio == 0) {
            if (!jeitoCerto(p, tipo)) { cancelar(p, "desceu da montaria na largada."); return; }
            // travado na largada até o "Vai!"
            net.minecraft.world.entity.Entity quem = p.getVehicle() != null ? p.getVehicle() : p;
            if (v.travado != null && quem.position().distanceTo(v.travado) > 0.6) {
                quem.teleportTo(v.travado.x, v.travado.y, v.travado.z);
                quem.setDeltaMovement(Vec3.ZERO);
            }
            v.ultimo = quem.position();
            long falta = v.largada - agora;
            int seg = (int) Math.ceil(falta / 1000.0);
            if (falta > 0) {
                if (seg != v.ultimoSeg) {
                    v.ultimoSeg = seg;
                    titulo(p, String.valueOf(seg), pista.get("nome").getAsString(), seg <= 1 ? ChatFormatting.GREEN : seg <= 3 ? ChatFormatting.GOLD : ChatFormatting.RED);
                    p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 0.8f + 0.1f * (5 - seg));
                }
                return;
            }
            v.inicio = agora;
            v.inicioVolta = agora;
            v.travado = null;
            if (quem instanceof net.minecraft.world.entity.LivingEntity le) le.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            titulo(p, "VAI!", "Siga a coluna de luz", ChatFormatting.GREEN);
            p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 1.6f);
            fogos(p.serverLevel(), ponto(pista, 0), 4);
            return;
        }
        if (agora - v.inicio > limiteMs(pista)) { cancelar(p, "passou de " + limiteMs(pista) / 60_000 + " minutos."); atualizarPlacar(p.server, v.pista); return; }
        if (!jeitoCerto(p, tipo)) {
            if (v.foraDesde == 0) v.foraDesde = agora;
            else if (agora - v.foraDesde > 4000) { cancelar(p, "esta pista é " + nomeTipo(tipo) + "."); return; }
            p.displayClientMessage(Component.literal("Volte " + comoFicar(tipo) + " ou a corrida acaba!").withStyle(ChatFormatting.RED), true);
            return;
        }
        v.foraDesde = 0;
        int ultimo = ultimoAlvo(pista);
        Vec3 alvo = alvo(pista, v.proximo);
        double r = raio(pista);
        double dx = pos.x - alvo.x, dz = pos.z - alvo.z;
        if (dx * dx + dz * dz <= r * r && Math.abs(pos.y - alvo.y) <= r * 1.5) {
            if (v.proximo == ultimo) {
                if (v.volta < voltasDa(pista)) {
                    voltaFeita(p, v, pista, agora);
                    v.volta++;
                    v.proximo = 1;
                    titulo(p, "Volta " + v.volta + "/" + voltasDa(pista), tempo(agora - v.inicio), ChatFormatting.AQUA);
                    p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.2f);
                    return;
                }
                chegar(p, v, pista, agora);
                return;
            }
            v.proximo++;
            p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1.2f);
            alvo = alvo(pista, v.proximo);
        }
        // coluna de luz no próximo ponto (só para quem corre)
        if (p.tickCount % 6 == 0) {
            ServerLevel n = p.serverLevel();
            boolean chegada = v.proximo == ultimo;
            DustParticleOptions cor = new DustParticleOptions(chegada ? new Vector3f(1f, 0.85f, 0.1f) : new Vector3f(0.2f, 0.9f, 1f), 2f);
            for (int y = 0; y < 14; y++) n.sendParticles(p, cor, true, alvo.x, alvo.y + y * 0.8, alvo.z, 1, 0.15, 0.1, 0.15, 0);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                n.sendParticles(p, ParticleTypes.END_ROD, true, alvo.x + Math.cos(a) * r, alvo.y + 0.5, alvo.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        int dist = (int) Math.round(pos.distanceTo(alvo));
        p.displayClientMessage(Component.literal("🏁 " + tempo(agora - v.inicio) + (voltasDa(pista) > 1 ? "   volta " + v.volta + "/" + voltasDa(pista) : "")
                + "   ponto " + v.proximo + "/" + ultimo + " · " + dist + " m").withStyle(ChatFormatting.AQUA), true);
    }

    private static void chegar(ServerPlayer p, Volta v, JsonObject pista, long agora) {
        voltas.remove(p.getUUID());
        saindo.put(p.getUUID(), v.pista);   // não larga de novo enquanto não sair do anel
        voltaFeita(p, v, pista, agora);
        long ms = agora - v.inicio;
        MinecraftServer s = p.server;
        Grupo g = v.grupo;
        if (g != null && g.todos.size() > 1) {
            g.chegada.add(p.getGameProfile().getName());
            g.tempos.add(ms);
            int lugar = g.chegada.size();
            for (UUID u : g.todos) {
                ServerPlayer q = s.getPlayerList().getPlayer(u);
                if (q != null) q.sendSystemMessage(Component.literal("🏁 " + p.getGameProfile().getName() + " cruzou a chegada em " + lugar + "º lugar (" + tempo(ms) + ")")
                        .withStyle(lugar == 1 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
            }
            if (lugar == 1) Moedas.dar(p, PREMIO_VENCEDOR);
        }
        String nome = pista.get("nome").getAsString();
        if (!pista.has("recordes")) pista.add("recordes", new JsonArray());
        JsonArray rec = pista.getAsJsonArray("recordes");
        long recordeAntes = rec.isEmpty() ? Long.MAX_VALUE : rec.get(0).getAsJsonObject().get("ms").getAsLong();
        long meuAntes = Long.MAX_VALUE;
        String u = p.getUUID().toString();
        List<JsonObject> l = new ArrayList<>();
        for (JsonElement e : rec) { JsonObject o = e.getAsJsonObject(); if (o.get("uuid").getAsString().equals(u)) meuAntes = o.get("ms").getAsLong(); else l.add(o); }
        JsonObject meu = new JsonObject();
        meu.addProperty("uuid", u); meu.addProperty("nome", p.getGameProfile().getName());
        meu.addProperty("ms", Math.min(ms, meuAntes)); meu.addProperty("quando", ms < meuAntes ? agora : 0);
        if (ms >= meuAntes) for (JsonElement e : rec) if (e.getAsJsonObject().get("uuid").getAsString().equals(u)) meu = e.getAsJsonObject();
        l.add(meu);
        l.sort((a, b) -> Long.compare(a.get("ms").getAsLong(), b.get("ms").getAsLong()));
        JsonArray novo = new JsonArray();
        for (int i = 0; i < Math.min(10, l.size()); i++) novo.add(l.get(i));
        pista.add("recordes", novo);
        pista.addProperty("feitas", (pista.has("feitas") ? pista.get("feitas").getAsInt() : 0) + 1);
        sujo = true;
        Conquistas.somar(p, "corridas", 1);
        boolean recorde = ms < recordeAntes;
        fogos(p.serverLevel(), alvo(pista, ultimoAlvo(pista)), 6);
        titulo(p, g != null && g.todos.size() > 1 ? g.chegada.size() + "º LUGAR!" : "CHEGADA!", tempo(ms) + (recorde ? " · NOVO RECORDE!" : ms < meuAntes && meuAntes != Long.MAX_VALUE ? " · seu melhor tempo" : ""), ChatFormatting.GOLD);
        p.level().playSound(null, p.blockPosition(), recorde ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
        // prêmio: 1ª chegada do dia em cada pista
        String hoje = LocalDate.now(FUSO).toString();
        JsonObject diario = dados(s).getAsJsonObject("diario");
        JsonObject eu = diario.has(u) && diario.getAsJsonObject(u).has("dia") && diario.getAsJsonObject(u).get("dia").getAsString().equals(hoje) ? diario.getAsJsonObject(u) : new JsonObject();
        eu.addProperty("dia", hoje);
        if (!eu.has("pistas")) eu.add("pistas", new JsonArray());
        boolean primeira = true;
        for (JsonElement e : eu.getAsJsonArray("pistas")) if (e.getAsString().equals(v.pista)) primeira = false;
        if (primeira) { eu.getAsJsonArray("pistas").add(v.pista); Moedas.dar(p, PREMIO_DIA); }
        diario.add(u, eu);
        if (recorde && recordeAntes != Long.MAX_VALUE) {
            Moedas.dar(p, PREMIO_RECORDE);
            Conquistas.somar(p, "corrida_recordes", 1);
            s.getPlayerList().broadcastSystemMessage(Component.literal("🏁 Novo recorde em " + nome + " (" + nomeTipo(pista.get("tipo").getAsString()) + "): "
                    + p.getGameProfile().getName() + " em " + tempo(ms) + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        } else if (recorde) {
            Conquistas.somar(p, "corrida_recordes", 1);
            s.getPlayerList().broadcastSystemMessage(Component.literal("🏁 " + p.getGameProfile().getName() + " fez o primeiro tempo de " + nome + ": " + tempo(ms) + ".").withStyle(ChatFormatting.GOLD), false);
        }
        p.sendSystemMessage(Component.literal("🏁 " + nome + ": " + tempo(ms) + (meuAntes != Long.MAX_VALUE ? " (seu melhor: " + tempo(Math.min(ms, meuAntes)) + ")" : "")
                + (primeira ? " · +2 ouro (primeira do dia)" : "") + (recorde && recordeAntes != Long.MAX_VALUE ? " · +10 ouro pelo recorde" : "")).withStyle(ChatFormatting.AQUA));
        gravar(s);
        atualizarPlacar(s, v.pista);
        fimDoGrupo(s, g);
    }

    /** Fecha uma volta: guarda a volta mais rápida da pista (para sempre, até zerar os recordes). */
    private static void voltaFeita(ServerPlayer p, Volta v, JsonObject pista, long agora) {
        long ms = agora - v.inicioVolta;
        v.inicioVolta = agora;
        if (voltasDa(pista) < 2) return;   // com uma volta só, a volta é o próprio tempo da corrida
        JsonObject antes = pista.has("voltaRapida") ? pista.getAsJsonObject("voltaRapida") : null;
        if (antes != null && antes.get("ms").getAsLong() <= ms) return;
        JsonObject vr = new JsonObject();
        vr.addProperty("nome", p.getGameProfile().getName()); vr.addProperty("uuid", p.getUUID().toString());
        vr.addProperty("ms", ms); vr.addProperty("quando", agora);
        pista.add("voltaRapida", vr);
        sujo = true;
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("⚡ Volta mais rápida em " + pista.get("nome").getAsString() + ": "
                + p.getGameProfile().getName() + " em " + tempo(ms) + "!").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        atualizarPlacar(p.server, v.pista);
    }

    /** Quando o último do grupo chega (ou desiste), anuncia o resultado da largada. */
    private static void fimDoGrupo(MinecraftServer s, Grupo g) {
        if (g == null || g.todos.size() < 2) return;
        for (Volta o : voltas.values()) if (o.grupo == g) return;
        if (g.chegada.isEmpty()) return;
        StringBuilder t = new StringBuilder();
        String[] med = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < g.chegada.size(); i++) t.append(i == 0 ? "" : "  ").append(i < 3 ? med[i] : (i + 1) + "º").append(" ").append(g.chegada.get(i)).append(" ").append(tempo(g.tempos.get(i)));
        JsonObject pista = pistas(s).has(g.pista) ? pistas(s).getAsJsonObject(g.pista) : null;
        s.getPlayerList().broadcastSystemMessage(Component.literal("🏁 Resultado em " + (pista != null ? pista.get("nome").getAsString() : g.pista) + ": " + t
                + " · " + g.chegada.get(0) + " vence e leva " + PREMIO_VENCEDOR / 100 + " ouro!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    // ------------------------------------------------------------------ fogos e placar

    private static final int[][] CORES = {{0xFF3B3B, 0xFFD23B}, {0x3BB4FF, 0xFFFFFF}, {0x6BFF3B, 0xFFD23B}, {0xD23BFF, 0x3BB4FF}};

    /** Fogos de artifício em volta de um ponto. */
    static void fogos(ServerLevel n, Vec3 c, int qtd) {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < qtd; i++) {
            net.minecraft.world.item.ItemStack f = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
            net.minecraft.nbt.CompoundTag fw = new net.minecraft.nbt.CompoundTag();
            fw.putByte("Flight", (byte) (1 + rnd.nextInt(2)));
            net.minecraft.nbt.ListTag ex = new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.CompoundTag e = new net.minecraft.nbt.CompoundTag();
            int[] cor = CORES[rnd.nextInt(CORES.length)];
            e.putByte("Type", (byte) rnd.nextInt(5));
            e.putIntArray("Colors", new int[]{cor[0]});
            e.putIntArray("FadeColors", new int[]{cor[1]});
            e.putBoolean("Flicker", true);
            e.putBoolean("Trail", true);
            ex.add(e);
            fw.put("Explosions", ex);
            f.getOrCreateTag().put("Fireworks", fw);
            double a = Math.PI * 2 * i / qtd;
            n.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(n, c.x + Math.cos(a) * 4, c.y + 1, c.z + Math.sin(a) * 4, f));
        }
    }

    private static final String TAG_PLACAR = "frx_placar_corrida";

    /** Placar gigante (texto flutuante) da pista, se ela tiver um. Refaz o texto com os 10 melhores tempos. */
    static void atualizarPlacar(MinecraftServer s, String id) {
        JsonObject todas = pistas(s);
        if (!todas.has(id)) return;
        JsonObject pista = todas.getAsJsonObject(id);
        if (!pista.has("placar")) return;
        JsonArray pl = pista.getAsJsonArray("placar");
        ServerLevel n = s.overworld();
        double x = pl.get(0).getAsDouble(), y = pl.get(1).getAsDouble(), z = pl.get(2).getAsDouble();
        float giro = pl.get(3).getAsFloat(), escala = pl.size() > 4 ? pl.get(4).getAsFloat() : 3f;
        if (!n.hasChunkAt(net.minecraft.core.BlockPos.containing(x, y, z))) { pista.addProperty("placarPendente", true); return; }
        for (net.minecraft.world.entity.Entity e : n.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(x - 3, y - 3, z - 3, x + 3, y + 3, z + 3),
                e -> e.getTags().contains(TAG_PLACAR + ":" + id))) e.discard();
        net.minecraft.network.chat.MutableComponent t = Component.literal("🏁 " + pista.get("nome").getAsString() + "\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        t.append(Component.literal(nomeTipo(pista.get("tipo").getAsString()) + (voltasDa(pista) > 1 ? " · " + voltasDa(pista) + " voltas" : "") + "\n\n").withStyle(ChatFormatting.GRAY));
        JsonArray rec = pista.has("recordes") ? pista.getAsJsonArray("recordes") : new JsonArray();
        if (pista.has("voltaRapida")) {
            JsonObject vr = pista.getAsJsonObject("voltaRapida");
            t.append(Component.literal("⚡ Volta mais rápida: " + vr.get("nome").getAsString() + " " + tempo(vr.get("ms").getAsLong()) + "\n\n").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
        if (rec.isEmpty()) t.append(Component.literal("Ninguém completou ainda.\nSeja o primeiro!").withStyle(ChatFormatting.WHITE));
        String[] med = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < Math.min(10, rec.size()); i++) {
            JsonObject o = rec.get(i).getAsJsonObject();
            t.append(Component.literal((i < 3 ? med[i] : (i + 1) + ".") + " " + o.get("nome").getAsString() + "  ").withStyle(i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
            t.append(Component.literal(tempo(o.get("ms").getAsLong()) + (i < Math.min(10, rec.size()) - 1 ? "\n" : "")).withStyle(ChatFormatting.AQUA));
        }
        List<String> correndo = new ArrayList<>();
        for (Map.Entry<UUID, Volta> e : voltas.entrySet()) if (e.getValue().pista.equals(id)) {
            ServerPlayer c = s.getPlayerList().getPlayer(e.getKey());
            if (c != null) correndo.add(c.getGameProfile().getName());
        }
        if (!correndo.isEmpty()) t.append(Component.literal("\n\n▶ Correndo agora: " + String.join(", ", correndo)).withStyle(ChatFormatting.GREEN));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("text", Component.Serializer.toJson(t));
        tag.putString("billboard", "fixed");
        tag.putString("alignment", "center");
        tag.putInt("line_width", 400);
        tag.putInt("background", 0xC0101018);
        tag.putBoolean("shadow", true);
        net.minecraft.nbt.CompoundTag tr = new net.minecraft.nbt.CompoundTag();
        tr.put("left_rotation", lista(0f, 0f, 0f, 1f));
        tr.put("right_rotation", lista(0f, 0f, 0f, 1f));
        tr.put("translation", lista(0f, 0f, 0f));
        tr.put("scale", lista(escala, escala, escala));
        tag.put("transformation", tr);
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf(TAG_PLACAR + ":" + id));
        tag.put("Tags", tags);
        net.minecraft.world.entity.Entity e = net.minecraft.world.entity.EntityType.loadEntityRecursive(tag, n, en -> { en.moveTo(x, y, z, giro, 0f); return en; });
        if (e != null) n.addFreshEntity(e);
        pista.remove("placarPendente");
        sujo = true;
    }

    private static net.minecraft.nbt.ListTag lista(float... v) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (float f : v) l.add(net.minecraft.nbt.FloatTag.valueOf(f));
        return l;
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        Volta v = voltas.remove(ev.getEntity().getUUID());
        if (v != null && ev.getEntity() instanceof ServerPlayer p) { atualizarPlacar(p.server, v.pista); fimDoGrupo(p.server, v.grupo); }
    }

    @SubscribeEvent
    public void aoTrocarDimensao(PlayerEvent.PlayerChangedDimensionEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && voltas.containsKey(p.getUUID())) cancelar(p, "saiu do mundo.");
    }

    @SubscribeEvent
    public void salvar(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        if (ev.getServer().getTickCount() % 200 == 41)
            for (Map.Entry<String, JsonElement> e : pistas(ev.getServer()).entrySet())
                if (e.getValue().getAsJsonObject().has("placarPendente")) atualizarPlacar(ev.getServer(), e.getKey());
        if (ev.getServer().getTickCount() % 1200 == 77) gravar(ev.getServer());
    }

    // ------------------------------------------------------------------ comandos

    private static int marcar(CommandSourceStack src, String id, int x, int y, int z) {
        JsonObject todas = pistas(src.getServer());
        if (!todas.has(id)) { src.sendFailure(Component.literal("Pista não existe.")); return 0; }
        JsonArray a = new JsonArray(); a.add(x + 0.5); a.add(y); a.add(z + 0.5);
        JsonObject pi = todas.getAsJsonObject(id);
        pi.getAsJsonArray("pontos").add(a);
        pi.add("recordes", new JsonArray()); pi.remove("voltaRapida");   // traçado mudou: tempos antigos não valem
        sujo = true; gravar(src.getServer());
        int n = qtdPontos(pi);
        return responder(src, (n == 1 ? "Largada" : "Ponto " + (n - 1)) + " marcado em " + x + ", " + y + ", " + z + ". O último ponto marcado é a chegada.");
    }

    /** Põe o placar gigante da pista onde o admin está, virado para onde ele olha (o texto fica de frente para quem está atrás dele). */
    private static int placarAqui(CommandSourceStack src, String id, float tamanho) {
        ServerPlayer p;
        try { p = src.getPlayerOrException(); } catch (Exception e) { return 0; }
        JsonObject todas = pistas(src.getServer());
        if (!todas.has(id)) { src.sendFailure(Component.literal("Pista não existe.")); return 0; }
        JsonObject pista = todas.getAsJsonObject(id);
        if (pista.has("placar")) {   // apaga o antigo
            JsonArray pl = pista.getAsJsonArray("placar");
            double x = pl.get(0).getAsDouble(), y = pl.get(1).getAsDouble(), z = pl.get(2).getAsDouble();
            for (net.minecraft.world.entity.Entity e : p.serverLevel().getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(x - 3, y - 3, z - 3, x + 3, y + 3, z + 3),
                    e -> e.getTags().contains(TAG_PLACAR + ":" + id))) e.discard();
        }
        JsonArray a = new JsonArray();
        a.add(p.getX()); a.add(p.getY() + 1); a.add(p.getZ()); a.add(p.getYRot() + 180f); a.add(tamanho);
        pista.add("placar", a);
        sujo = true;
        atualizarPlacar(src.getServer(), id);
        gravar(src.getServer());
        return responder(src, "Placar de " + id + " colocado aqui, tamanho " + (int) tamanho + ". Ele mostra os 10 melhores tempos e quem está correndo; para mudar de lugar, use de novo.");
    }

    private static int responder(CommandSourceStack src, String t) { src.sendSuccess(() -> Component.literal("[corrida] " + t), true); return 1; }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("corrida").then(Commands.literal("iniciar").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            String id = StringArgumentType.getString(c, "id");
            JsonObject todas = pistas(p.server);
            if (!todas.has(id) || qtdPontos(todas.getAsJsonObject(id)) < 2) { p.sendSystemMessage(Component.literal("Essa pista não existe.").withStyle(ChatFormatting.RED)); return 0; }
            JsonObject pista = todas.getAsJsonObject(id);
            String tipo = pista.get("tipo").getAsString();
            if (voltas.containsKey(p.getUUID())) { p.sendSystemMessage(Component.literal("Você já está correndo.").withStyle(ChatFormatting.RED)); return 0; }
            if (p.position().distanceTo(ponto(pista, 0)) > 4) { p.sendSystemMessage(Component.literal("Vá até a largada (anel de fogo) para começar.").withStyle(ChatFormatting.RED)); return 0; }
            if (!jeitoCerto(p, tipo)) { p.sendSystemMessage(Component.literal("Esta pista é " + nomeTipo(tipo) + ": fique " + comoFicar(tipo) + ".").withStyle(ChatFormatting.RED)); return 0; }
            UUID dono = ocupada(id);
            if (dono != null) { p.sendSystemMessage(Component.literal("A pista está ocupada: espere a corrida em andamento terminar.").withStyle(ChatFormatting.RED)); return 0; }
            // todos que estão na linha, do jeito certo, largam juntos
            Grupo g = new Grupo(id);
            long largada = System.currentTimeMillis() + CONTAGEM_MS;
            List<String> nomes = new ArrayList<>();
            for (ServerPlayer q : p.serverLevel().players()) {
                if (q != p && (q.isSpectator() || voltas.containsKey(q.getUUID()) || !jeitoCerto(q, tipo) || q.position().distanceTo(ponto(pista, 0)) > raio(pista) + 1)) continue;
                Volta v = new Volta(id, largada);
                v.grupo = g;
                net.minecraft.world.entity.Entity quem = q.getVehicle() != null ? q.getVehicle() : q;
                v.travado = quem.position();
                v.ultimo = quem.position();
                if (quem instanceof net.minecraft.world.entity.LivingEntity le)   // montaria parada durante a contagem
                    le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, (int) (CONTAGEM_MS / 50), 10, false, false));
                voltas.put(q.getUUID(), v);
                saindo.remove(q.getUUID());
                g.todos.add(q.getUUID());
                nomes.add(q.getGameProfile().getName());
            }
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("🏁 Largada em " + pista.get("nome").getAsString() + ": " + String.join(", ", nomes)
                    + (nomes.size() > 1 ? " disputam quem chega primeiro!" : " corre contra o relógio!")).withStyle(ChatFormatting.GOLD), false);
            atualizarPlacar(p.server, id);
            return 1;
        }))));
        ev.getDispatcher().register(Commands.literal("corridas").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            JsonObject todas = pistas(p.server);
            p.sendSystemMessage(Component.literal("🏁 Pistas de corrida (fique na largada, no anel de fogo, para largar)").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (todas.size() == 0) p.sendSystemMessage(Component.literal("  Nenhuma pista ainda.").withStyle(ChatFormatting.GRAY));
            for (Map.Entry<String, JsonElement> e : todas.entrySet()) {
                JsonObject pi = e.getValue().getAsJsonObject();
                if (qtdPontos(pi) < 2) continue;
                Vec3 l = ponto(pi, 0);
                JsonArray rec = pi.has("recordes") ? pi.getAsJsonArray("recordes") : new JsonArray();
                String r = rec.isEmpty() ? "sem recorde" : "recorde " + tempo(rec.get(0).getAsJsonObject().get("ms").getAsLong()) + " (" + rec.get(0).getAsJsonObject().get("nome").getAsString() + ")";
                p.sendSystemMessage(Component.literal("  " + pi.get("nome").getAsString() + " · " + nomeTipo(pi.get("tipo").getAsString()) + " · " + (qtdPontos(pi) - 1) + " pontos · "
                        + r + " · largada em " + (int) l.x + ", " + (int) l.y + ", " + (int) l.z).withStyle(ChatFormatting.WHITE));
            }
            return 1;
        }));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2)).then(Commands.literal("corrida")
                .then(Commands.literal("criar").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("tipo", StringArgumentType.word()).suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(TIPOS, b))
                                .then(Commands.argument("nome", StringArgumentType.greedyString()).executes(c -> {
                                    String id = StringArgumentType.getString(c, "id"), tipo = StringArgumentType.getString(c, "tipo");
                                    if (!TIPOS.contains(tipo)) { c.getSource().sendFailure(Component.literal("Tipo: pe, cavalo, barco ou elitro.")); return 0; }
                                    JsonObject pi = new JsonObject();
                                    pi.addProperty("nome", StringArgumentType.getString(c, "nome")); pi.addProperty("tipo", tipo);
                                    pi.add("pontos", new JsonArray()); pi.add("recordes", new JsonArray());
                                    pistas(c.getSource().getServer()).add(id, pi);
                                    sujo = true; gravar(c.getSource().getServer());
                                    return responder(c.getSource(), "Pista " + id + " criada. Agora vá até a largada e use /frx corrida ponto " + id + "; depois cada ponto, e o último é a chegada.");
                                })))))
                .then(Commands.literal("ponto").then(Commands.argument("id", StringArgumentType.word())
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            return marcar(c.getSource(), StringArgumentType.getString(c, "id"), p.getBlockX(), p.getBlockY(), p.getBlockZ());
                        })
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(c -> {
                            net.minecraft.core.BlockPos b = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(c, "pos");
                            return marcar(c.getSource(), StringArgumentType.getString(c, "id"), b.getX(), b.getY(), b.getZ());
                        }))))
                .then(Commands.literal("desfazer").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    JsonObject todas = pistas(c.getSource().getServer());
                    String id = StringArgumentType.getString(c, "id");
                    if (!todas.has(id) || qtdPontos(todas.getAsJsonObject(id)) == 0) { c.getSource().sendFailure(Component.literal("Nada para desfazer.")); return 0; }
                    JsonArray pts = todas.getAsJsonObject(id).getAsJsonArray("pontos");
                    pts.remove(pts.size() - 1);
                    sujo = true; gravar(c.getSource().getServer());
                    return responder(c.getSource(), "Último ponto tirado. A pista tem " + pts.size() + " pontos.");
                })))
                .then(Commands.literal("raio").then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("blocos", IntegerArgumentType.integer(2, 30)).executes(c -> {
                    JsonObject todas = pistas(c.getSource().getServer());
                    String id = StringArgumentType.getString(c, "id");
                    if (!todas.has(id)) { c.getSource().sendFailure(Component.literal("Pista não existe.")); return 0; }
                    todas.getAsJsonObject(id).addProperty("raio", IntegerArgumentType.getInteger(c, "blocos"));
                    sujo = true; gravar(c.getSource().getServer());
                    return responder(c.getSource(), "Raio dos pontos de " + id + ": " + IntegerArgumentType.getInteger(c, "blocos") + " blocos.");
                }))))
                .then(Commands.literal("voltas").then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("n", IntegerArgumentType.integer(1, 20)).executes(c -> {
                    JsonObject todas = pistas(c.getSource().getServer());
                    String id = StringArgumentType.getString(c, "id");
                    if (!todas.has(id)) { c.getSource().sendFailure(Component.literal("Pista não existe.")); return 0; }
                    todas.getAsJsonObject(id).addProperty("voltas", IntegerArgumentType.getInteger(c, "n"));
                    todas.getAsJsonObject(id).add("recordes", new JsonArray()); todas.getAsJsonObject(id).remove("voltaRapida");
                    sujo = true; gravar(c.getSource().getServer());
                    return responder(c.getSource(), id + ": " + IntegerArgumentType.getInteger(c, "n") + " volta(s). A chegada de cada volta é a linha de largada, depois do último ponto.");
                }))))
                .then(Commands.literal("placar").then(Commands.argument("id", StringArgumentType.word())
                        .executes(c -> placarAqui(c.getSource(), StringArgumentType.getString(c, "id"), 3f))
                        .then(Commands.argument("tamanho", IntegerArgumentType.integer(1, 12)).executes(c -> placarAqui(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "tamanho"))))))
                .then(Commands.literal("limite").then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("minutos", IntegerArgumentType.integer(1, 60)).executes(c -> {
                    JsonObject todas = pistas(c.getSource().getServer());
                    String id = StringArgumentType.getString(c, "id");
                    if (!todas.has(id)) { c.getSource().sendFailure(Component.literal("Pista não existe.")); return 0; }
                    todas.getAsJsonObject(id).addProperty("limite", IntegerArgumentType.getInteger(c, "minutos"));
                    sujo = true; gravar(c.getSource().getServer());
                    return responder(c.getSource(), id + ": a corrida é cancelada depois de " + IntegerArgumentType.getInteger(c, "minutos") + " minutos.");
                }))))
                .then(Commands.literal("zerar").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    JsonObject todas = pistas(c.getSource().getServer());
                    String id = StringArgumentType.getString(c, "id");
                    if (!todas.has(id)) { c.getSource().sendFailure(Component.literal("Pista não existe.")); return 0; }
                    todas.getAsJsonObject(id).add("recordes", new JsonArray());
                    todas.getAsJsonObject(id).remove("voltaRapida");
                    sujo = true; gravar(c.getSource().getServer());
                    atualizarPlacar(c.getSource().getServer(), id);
                    return responder(c.getSource(), "Recordes de " + id + " zerados.");
                })))
                .then(Commands.literal("remover").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    JsonObject todas = pistas(c.getSource().getServer());
                    String id = StringArgumentType.getString(c, "id");
                    if (todas.remove(id) == null) { c.getSource().sendFailure(Component.literal("Pista não existe.")); return 0; }
                    sujo = true; gravar(c.getSource().getServer());
                    return responder(c.getSource(), "Pista " + id + " removida.");
                })))));
    }
}
