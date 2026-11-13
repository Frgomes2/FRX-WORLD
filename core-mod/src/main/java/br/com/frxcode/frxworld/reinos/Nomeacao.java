package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.rede.Rede;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Nomeação: ao concluir a campanha de um reino, o rei dá um nome ao jogador numa cerimônia,
 * com anúncio no servidor e um bônus que não se perde. O primeiro rei dá o nome (o jogador escolhe
 * entre três); os reis seguintes reconhecem o nome e somam a própria bênção.
 */
public class Nomeacao {
    private static final String CHAVE = "frx_nomeacao";
    private static final UUID MOD_VIDA = UUID.fromString("3a6b0f52-7d1e-4f0b-8c11-5e2a9d4b7c01");
    /** Por nomeação: +1 coração e +2% de dano. */
    public static final double VIDA_POR = 2.0, DANO_POR = 0.02;

    private static final String[] INICIO = {"Kael", "Ryu", "Shin", "Vel", "Zeph", "Aka", "Sora", "Rai", "Gren", "Lyr", "Dra", "Haku", "Beni", "Shu", "Ran", "Gel", "Mil", "Dia", "Tes", "Kar", "Yuu", "Ren", "Ars", "Ei"};
    private static final String[] FIM = {"dor", "zen", "mar", "ion", "ris", "gard", "thas", "rei", "ka", "mir", "os", "en", "ga", "to", "ha", "nar", "el", "us"};

    private record Cena(String rei, String reinoNome, String nome, boolean primeira, int[] passo) {}
    private static final Map<UUID, Cena> CENAS = new HashMap<>();

    public Nomeacao() {}

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    /** Nome recebido de um rei ("" se ainda não foi nomeado). */
    public static String nome(Player p) { return dados(p).getString("nome"); }

    public static List<String> reinos(Player p) {
        List<String> l = new ArrayList<>();
        for (Tag t : dados(p).getList("reinos", Tag.TAG_STRING)) l.add(t.getAsString());
        return l;
    }

    public static int total(Player p) { return dados(p).getList("reinos", Tag.TAG_STRING).size(); }
    public static boolean tem(Player p, String reino) { return reinos(p).contains(reino); }

    /** Título que entra na lista de títulos do jogador. */
    public static String titulo(Player p) {
        String n = nome(p);
        return n.isEmpty() ? "" : n + ", o Nomeado";
    }

    public static String bonus(Player p) {
        int t = total(p);
        return "+" + t + " coração" + (t == 1 ? "" : "es") + " e +" + (int) Math.round(t * DANO_POR * 100) + "% de dano";
    }

    public static void aplicar(ServerPlayer p) {
        AttributeInstance vida = p.getAttribute(Attributes.MAX_HEALTH);
        if (vida == null) return;
        vida.removeModifier(MOD_VIDA);
        int t = total(p);
        if (t > 0) vida.addTransientModifier(new AttributeModifier(MOD_VIDA, "FRX nomeacao", t * VIDA_POR, AttributeModifier.Operation.ADDITION));
    }

    private static List<String> opcoes(ServerPlayer p, String reino) {
        Random r = new Random(p.getUUID().getLeastSignificantBits() ^ reino.hashCode() * 31L);
        List<String> l = new ArrayList<>();
        while (l.size() < 3) {
            String n = INICIO[r.nextInt(INICIO.length)] + FIM[r.nextInt(FIM.length)];
            if (!l.contains(n)) l.add(n);
        }
        return l;
    }

    private static void falaRei(ServerPlayer p, String rei, String texto) {
        p.sendSystemMessage(Component.literal("[" + rei + "] ").withStyle(ChatFormatting.GOLD).append(Component.literal(texto).withStyle(ChatFormatting.WHITE)));
    }

    /** Chamado quando a campanha do reino termina (ou ao falar com o rei depois, para quem concluiu antes da novidade). */
    static void aoConcluir(ServerPlayer p, Campanhas.Campanha c) {
        if (tem(p, c.reino()) || CENAS.containsKey(p.getUUID())) return;
        if (nome(p).isEmpty()) {
            dados(p).putString("pendente", c.reino());
            falaRei(p, c.rei(), "Um herói assim não pode seguir sem um nome dado por um rei. Escolha o nome que vai carregar para sempre:");
            MutableComponent linha = Component.literal("   ");
            List<String> ops = opcoes(p, c.reino());
            for (int i = 0; i < ops.size(); i++)
                linha.append(Component.literal("[" + ops.get(i) + "]  ").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nomeacao escolher " + (i + 1)))));
            p.sendSystemMessage(linha);
            Sistema.avisar(p, "Cerimônia de Nomeação", "O rei " + c.rei() + " vai lhe dar um nome. Escolha um dos três no chat (ou fale com o rei de novo).", Sistema.OURO);
            return;
        }
        comecar(p, c, nome(p), false);
    }

    private static void comecar(ServerPlayer p, Campanhas.Campanha c, String nome, boolean primeira) {
        dados(p).remove("pendente");
        CENAS.put(p.getUUID(), new Cena(c.rei(), c.nomeReino(), nome, primeira, new int[]{0}));
        ListTag l = dados(p).getList("reinos", Tag.TAG_STRING);
        l.add(StringTag.valueOf(c.reino()));
        dados(p).put("reinos", l);
        if (primeira) dados(p).putString("nome", nome);
        if (p.getServer() != null) p.getServer().getPlayerList().broadcastSystemMessage(Component.literal("🔔 Os sinos de " + c.nomeReino()
                + " tocam: o rei " + c.rei() + " convoca o reino para a cerimônia de nomeação de " + p.getGameProfile().getName() + "!").withStyle(ChatFormatting.YELLOW), false);
    }

    private static void passo(ServerPlayer p, Cena c, int n) {
        ServerLevel nivel = p.serverLevel();
        String nick = p.getGameProfile().getName();
        switch (n) {
            case 0 -> {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 3, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 260, 0, false, false));
                nivel.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2f, 0.7f);
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 10));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Cerimônia de Nomeação").withStyle(ChatFormatting.GOLD)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(c.reinoNome()).withStyle(ChatFormatting.YELLOW)));
                falaRei(p, c.rei(), "Povo de " + c.reinoNome() + "! Diante de vocês está quem salvou este reino.");
            }
            case 1 -> {
                nivel.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2f, 0.8f);
                nivel.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 150, 1.2, 1.0, 1.2, 0.6);
                falaRei(p, c.rei(), c.primeira() ? "Um nome dado por um rei liga a alma à terra. Quem o recebe nunca mais é o mesmo."
                        : "Outro rei já lhe deu um nome, e eu o reconheço. Receba também a bênção de " + c.reinoNome() + ".");
            }
            case 2 -> {
                nivel.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2f, 0.9f);
                nivel.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 80, 0.8, 1.2, 0.8, 0.05);
                falaRei(p, c.rei(), c.primeira() ? "Ajoelhe-se, " + nick + ". De hoje em diante, o mundo vai chamar você de..."
                        : "Erga a cabeça, " + c.nome() + ". Que a minha coroa some força à sua.");
            }
            default -> {
                LightningBolt raio = EntityType.LIGHTNING_BOLT.create(nivel);
                if (raio != null) { raio.moveTo(p.getX(), p.getY(), p.getZ()); raio.setVisualOnly(true); nivel.addFreshEntity(raio); }
                nivel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 200, 0.8, 1.4, 0.8, 0.4);
                nivel.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 1f);
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 90, 30));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(c.nome().toUpperCase()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                        (c.primeira() ? "Nomeado pelo rei " + c.rei() : "Abençoado por " + c.reinoNome()) + "  ·  " + bonus(p)).withStyle(ChatFormatting.YELLOW)));
                aplicar(p);
                p.setHealth(p.getMaxHealth());
                if (c.primeira() && Progressao.temDados(p)) {
                    Progressao.dados(p).definirTitulo(titulo(p));
                    Progressao.atualizarNome(p);
                    Rede.sincronizar(p);
                }
                Sistema.avisar(p, c.primeira() ? "Você recebeu um nome: " + c.nome() : "Bênção de " + c.reinoNome(),
                        "Bônus permanente das nomeações: " + bonus(p) + ". Veja em /nomeacao.", Sistema.OURO);
                if (p.getServer() != null) p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(c.primeira()
                        ? "👑 O rei " + c.rei() + " de " + c.reinoNome() + " deu um nome a " + nick + ": de hoje em diante, " + c.nome() + "!"
                        : "👑 O rei " + c.rei() + " reconheceu o nome de " + c.nome() + " (" + nick + ") e lhe deu a bênção de " + c.reinoNome() + "!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                br.com.frxcode.frxworld.racas.Evolucao.checar(p);
                Progressao.enviarAoPainel(p, false);
            }
        }
    }

    private static void escolher(ServerPlayer p, int i) {
        String reino = dados(p).getString("pendente");
        Campanhas.Campanha c = Campanhas.TODAS.get(reino);
        if (reino.isEmpty() || c == null || !nome(p).isEmpty()) {
            p.sendSystemMessage(Component.literal("Nenhum rei está esperando a sua escolha agora.").withStyle(ChatFormatting.GRAY));
            return;
        }
        List<String> ops = opcoes(p, reino);
        comecar(p, c, ops.get(Math.max(0, Math.min(ops.size() - 1, i - 1))), true);
    }

    private static void mostrar(ServerPlayer p) {
        if (total(p) == 0) {
            p.sendSystemMessage(Component.literal("Você ainda não foi nomeado. Conclua a missão épica de um rei (/campanha).").withStyle(ChatFormatting.GRAY));
            return;
        }
        p.sendSystemMessage(Component.literal("👑 " + nome(p)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        List<String> nomes = new ArrayList<>();
        for (String id : reinos(p)) { Reino r = Reputacao.reino(id); nomes.add(r != null ? r.nome() : id); }
        p.sendSystemMessage(Component.literal("  Reinos que o nomearam ou abençoaram: " + String.join(", ", nomes)).withStyle(ChatFormatting.WHITE));
        p.sendSystemMessage(Component.literal("  Bônus permanente: " + bonus(p)).withStyle(ChatFormatting.AQUA));
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || CENAS.isEmpty()) return;
        for (Map.Entry<UUID, Cena> x : new HashMap<>(CENAS).entrySet()) {
            ServerPlayer p = ev.getServer().getPlayerList().getPlayer(x.getKey());
            Cena c = x.getValue();
            int t = c.passo()[0]++;
            if (p == null) { if (t > 200) CENAS.remove(x.getKey()); continue; }
            if (t % 50 != 0) continue;
            int n = t / 50;
            passo(p, c, n);
            if (n >= 3) CENAS.remove(x.getKey());
        }
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) aplicar(p);
    }

    @SubscribeEvent
    public void aoRenascer(PlayerEvent.PlayerRespawnEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) aplicar(p);
    }

    @SubscribeEvent
    public void aoMudarDimensao(PlayerEvent.PlayerChangedDimensionEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) aplicar(p);
    }

    @SubscribeEvent
    public void dano(LivingHurtEvent ev) {
        if (!(ev.getSource().getEntity() instanceof ServerPlayer a) || ev.getEntity() == a) return;
        int t = total(a);
        if (t > 0) ev.setAmount((float) (ev.getAmount() * (1 + t * DANO_POR)));
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("nomeacao")
                .executes(ctx -> { mostrar(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("escolher").then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
                        .executes(ctx -> { escolher(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n")); return 1; }))));
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("nomeacao")
                        .then(Commands.literal("limpar").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            dados(p).remove("nome"); dados(p).remove("reinos"); dados(p).remove("pendente");
                            aplicar(p);
                            ctx.getSource().sendSuccess(() -> Component.literal("Nomeações apagadas."), false);
                            return 1;
                        }))
                        .then(Commands.argument("reino", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Campanhas.TODAS.keySet(), b))
                                .executes(ctx -> {
                                    Campanhas.Campanha c = Campanhas.TODAS.get(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "reino"));
                                    if (c == null) { ctx.getSource().sendFailure(Component.literal("Reino sem campanha.")); return 0; }
                                    aoConcluir(ctx.getSource().getPlayerOrException(), c);
                                    return 1;
                                }))));
    }
}
