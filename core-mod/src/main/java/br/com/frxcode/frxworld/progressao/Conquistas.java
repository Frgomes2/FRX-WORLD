package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.moedas.Banco;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conquistas: contadores no jogador (abates, nível, forja, chefes, invasões, campanhas…).
 * Algumas dão título (/titulo). Anunciadas para todo o servidor.
 */
public class Conquistas {

    /** chave = contador; alvo = valor para conquistar; ids = entidades (para abates específicos). */
    public record Conquista(String id, String nome, String descricao, String chave, int alvo, String titulo, Set<String> ids) {}

    public static final Map<String, Conquista> TODAS = new LinkedHashMap<>();

    private static void c(String id, String nome, String desc, String chave, int alvo, String titulo, String... ids) {
        TODAS.put(id, new Conquista(id, nome, desc, chave, alvo, titulo, Set.of(ids)));
    }

    static {
        c("primeiro_sangue", "Primeiro Sangue", "Derrote seu primeiro monstro", "matar_total", 1, "");
        c("cacador", "Caçador", "Derrote 100 monstros", "matar_total", 100, "");
        c("exterminador", "Exterminador", "Derrote 1.000 monstros", "matar_total", 1000, "Exterminador");
        c("lenda_viva", "Lenda Viva", "Derrote 5.000 monstros", "matar_total", 5000, "Lenda Viva");
        c("zumbis", "Matador de Zumbis", "Derrote 100 zumbis", "c:zumbis", 100, "Matador de Zumbis",
                "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager");
        c("esqueletos", "Quebra-Ossos", "Derrote 100 esqueletos", "c:esqueletos", 100, "Quebra-Ossos",
                "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton");
        c("aranhas", "Aracnofóbico", "Derrote 50 aranhas", "c:aranhas", 50, "", "minecraft:spider", "minecraft:cave_spider");
        c("creepers", "Desarmador", "Derrote 50 creepers", "c:creepers", 50, "Desarmador", "minecraft:creeper");
        c("endermen", "Olhar Firme", "Derrote 50 endermen", "c:endermen", 50, "", "minecraft:enderman");
        c("dragao_end", "Matador de Dragões", "Derrote o Dragão do Fim", "c:dragao_end", 1, "Matador de Dragões", "minecraft:ender_dragon");
        c("wither", "Ruína do Wither", "Derrote o Wither", "c:wither", 1, "Ruína do Wither", "minecraft:wither");
        c("dragoes", "Domador de Dragões", "Derrote um dragão de Ice and Fire", "c:dragoes", 1, "Domador de Dragões",
                "iceandfire:fire_dragon", "iceandfire:ice_dragon", "iceandfire:lightning_dragon");
        c("harbinger", "Desmontador", "Derrote o Harbinger da Fábrica Ancestral", "c:harbinger", 1, "Desmontador", "cataclysm:the_harbinger");
        c("nivel10", "Aventureiro", "Chegue ao nível 10", "nivel", 10, "");
        c("nivel25", "Experiente", "Chegue ao nível 25", "nivel", 25, "");
        c("nivel50", "Veterano", "Chegue ao nível 50", "nivel", 50, "Veterano");
        c("nivel100", "Imortal", "Chegue ao nível 100", "nivel", 100, "Imortal");
        c("forja5", "Aprendiz de Ferreiro", "Aprimore um item até +5", "forja_max", 5, "");
        c("forja10", "Mestre Ferreiro", "Aprimore um item até +10", "forja_max", 10, "Mestre Ferreiro");
        c("lendario", "Toque de Ouro", "Reforje um item até Lendário", "reforja_lendaria", 1, "");
        c("magnata", "Magnata", "Tenha 100 ouro guardados no banco", "banco_max", 10000, "Magnata");
        c("chefe1", "Caçador de Titãs", "Ajude a derrotar um chefe mundial", "chefes", 1, "");
        c("chefe5", "Flagelo dos Titãs", "Ajude a derrotar 5 chefes mundiais", "chefes", 5, "Flagelo dos Titãs");
        c("invasao1", "Defensor", "Vença uma invasão", "invasoes", 1, "");
        c("invasao10", "Guardião dos Reinos", "Vença 10 invasões", "invasoes", 10, "Guardião dos Reinos");
        c("campanha1", "Escolhido do Rei", "Complete a missão épica de um rei", "campanhas", 1, "");
        c("campanha5", "Herói das Cinco Coroas", "Complete a missão épica dos 5 reis", "campanhas", 5, "Herói das Cinco Coroas");
        c("teimoso", "Cabeça Dura", "Morra 50 vezes", "mortes", 50, "Cabeça Dura");
    }

    // ------------------------------------------------------------------ contadores

    public static void somar(ServerPlayer p, String chave, int quanto) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        d.contadores().merge(chave, quanto, Integer::sum);
        checar(p, chave);
        br.com.frxcode.frxworld.aventura.Temporadas.doContador(p, chave, quanto);
    }

    /** Guarda o maior valor já visto (nível, forja, banco). */
    public static void maximo(ServerPlayer p, String chave, int valor) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        if (valor > d.contadores().getOrDefault(chave, 0)) {
            d.contadores().put(chave, valor);
            checar(p, chave);
        }
    }

    private static void checar(ServerPlayer p, String chave) {
        DadosJogador d = Progressao.dados(p);
        int v = d.contadores().getOrDefault(chave, 0);
        for (Conquista c : TODAS.values()) {
            if (!c.chave().equals(chave) || v < c.alvo() || d.conquistas().containsKey(c.id())) continue;
            d.conquistas().put(c.id(), System.currentTimeMillis());
            MutableComponent msg = Component.literal("🏆 " + p.getGameProfile().getName() + " conquistou ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("[" + c.nome() + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(c.descricao())))));
            if (!c.titulo().isEmpty()) msg.append(Component.literal("  (título: " + c.titulo() + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (p.getServer() != null) p.getServer().getPlayerList().broadcastSystemMessage(msg, false);
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            Sistema.avisar(p, "Conquista desbloqueada: " + c.nome(), c.titulo().isEmpty() ? c.descricao() : c.descricao() + " Novo título: «" + c.titulo() + "».", Sistema.ROXO);
            Progressao.enviarAoPainel(p, false);
        }
    }

    /** Títulos das conquistas obtidas. */
    public static List<String> titulos(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        if (!Progressao.temDados(p)) return l;
        for (String id : Progressao.dados(p).conquistas().keySet()) {
            Conquista c = TODAS.get(id);
            if (c != null && !c.titulo().isEmpty()) l.add(c.titulo());
        }
        return l;
    }

    /** Para a janela de status (tecla E). */
    public static com.google.gson.JsonArray json(ServerPlayer p) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        if (!Progressao.temDados(p)) return a;
        DadosJogador d = Progressao.dados(p);
        for (Conquista c : TODAS.values()) {
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("nome", c.nome());
            j.addProperty("descricao", c.descricao());
            j.addProperty("titulo", c.titulo());
            j.addProperty("obtida", d.conquistas().containsKey(c.id()));
            j.addProperty("progresso", Math.min(c.alvo(), d.contadores().getOrDefault(c.chave(), 0)));
            j.addProperty("alvo", c.alvo());
            a.add(j);
        }
        return a;
    }

    public static int total(ServerPlayer p) { return Progressao.temDados(p) ? Progressao.dados(p).conquistas().size() : 0; }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent e) {
        LivingEntity morto = e.getEntity();
        if (morto instanceof ServerPlayer p) { somar(p, "mortes", 1); return; }
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(morto.getType());
        String ids = id == null ? "" : id.toString();
        if (morto instanceof Enemy || morto.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) somar(p, "matar_total", 1);
        java.util.Set<String> chaves = new java.util.HashSet<>();
        for (Conquista c : TODAS.values()) if (c.ids().contains(ids)) chaves.add(c.chave());
        for (String k : chaves) somar(p, k, 1);
    }

    /** Nível e banco conferidos a cada 5 s. */
    @SubscribeEvent
    public void aCadaTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 100 != 0 || !Progressao.temDados(p)) return;
        maximo(p, "nivel", Progressao.dados(p).nivel());
        maximo(p, "banco_max", (int) Math.min(Integer.MAX_VALUE, Banco.saldo(p)));
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("conquistas").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            DadosJogador d = Progressao.dados(p);
            p.sendSystemMessage(Component.literal("══════ Conquistas (" + d.conquistas().size() + "/" + TODAS.size() + ") ══════").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (Conquista c : TODAS.values()) {
                boolean tem = d.conquistas().containsKey(c.id());
                int v = Math.min(c.alvo(), d.contadores().getOrDefault(c.chave(), 0));
                MutableComponent l = Component.literal(tem ? " ✔ " : " ✖ ").withStyle(tem ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                        .append(Component.literal(c.nome()).withStyle(tem ? ChatFormatting.YELLOW : ChatFormatting.GRAY))
                        .append(Component.literal(" — " + c.descricao()).withStyle(ChatFormatting.DARK_GRAY));
                if (!tem && c.alvo() > 1) l.append(Component.literal(" (" + v + "/" + c.alvo() + ")").withStyle(ChatFormatting.GRAY));
                if (!c.titulo().isEmpty()) l.append(Component.literal(" · título").withStyle(ChatFormatting.LIGHT_PURPLE));
                p.sendSystemMessage(l);
            }
            p.sendSystemMessage(Component.literal("Use /titulo para escolher um título.").withStyle(ChatFormatting.DARK_GRAY));
            return 1;
        }));
    }
}
