package br.com.frxcode.frxworld.racas;

import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Evolução de raça: ao bater os marcos (nível, honra com um reino, campanha ou chefe),
 * a raça da mesma família sobe de degrau — goblin → hobgoblin → ogro — com cena e anúncio no servidor.
 */
public class Evolucao {
    private static final String CHAVE = "frx_evolucao";

    public Evolucao() {}

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    /** Para onde a raça atual pode evoluir: mesma família, um degrau acima e já liberada. */
    public static List<DefRacas.Raca> caminhos(ServerPlayer p) {
        List<DefRacas.Raca> l = new ArrayList<>();
        DefRacas.Raca atual = DefRacas.de(RacasJogador.principal(p));
        if (atual == null || !RacasJogador.escolheu(p)) return l;
        for (DefRacas.Raca r : DefRacas.TODAS.values()) {
            if (r.nivel() != atual.nivel() + 1 || !r.familia().equals(atual.familia())) continue;
            if (r.nivel() >= DefRacas.DIVINA) continue;
            if (RacasJogador.liberada(p, r.id())) l.add(r);
        }
        return l;
    }

    /** Avisa quando um marco libera a evolução (uma vez por raça de destino). */
    public static void checar(ServerPlayer p) {
        List<DefRacas.Raca> c = caminhos(p);
        if (c.isEmpty()) return;
        CompoundTag d = dados(p);
        StringBuilder nomes = new StringBuilder();
        boolean novo = false;
        for (DefRacas.Raca r : c) {
            if (!d.getBoolean("aviso_" + r.id())) { d.putBoolean("aviso_" + r.id(), true); novo = true; }
            nomes.append(nomes.length() > 0 ? ", " : "").append(r.nome());
        }
        if (!novo) return;
        DefRacas.Raca atual = DefRacas.de(RacasJogador.principal(p));
        Sistema.avisar(p, "Sua raça pode evoluir", (atual == null ? "" : atual.nome() + " → ") + nomes + ". Use /evoluir quando estiver pronto.", Sistema.ROXO);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 1.2f);
    }

    /** A cena: luz, partículas, título na tela e anúncio no servidor. */
    private static void cena(ServerPlayer p, DefRacas.Raca de, DefRacas.Raca para) {
        ServerLevel nivel = p.serverLevel();
        nivel.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 120, 0.6, 1.2, 0.6, 0.12);
        nivel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 0.3, p.getZ(), 60, 0.5, 0.4, 0.5, 0.05);
        nivel.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.8f);
        nivel.playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.5f, 0.7f);
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, true));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 25));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(para.nome().toUpperCase()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal((de == null ? "" : de.nome() + " → ") + para.nome() + "  ·  " + DefRacas.NOME_NIVEL[Math.max(0, Math.min(3, para.nivel()))])
                        .withStyle(ChatFormatting.WHITE)));
        Sistema.avisar(p, "Evolução: " + para.nome(), para.descricao(), Sistema.ROXO);
        if (p.getServer() != null)
            p.getServer().getPlayerList().broadcastSystemMessage(Component.literal("✦ " + p.getGameProfile().getName() + " evoluiu para ")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(para.nome()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal("!").withStyle(ChatFormatting.LIGHT_PURPLE)), false);
    }

    public static void evoluir(ServerPlayer p, String destino) {
        List<DefRacas.Raca> c = caminhos(p);
        if (c.isEmpty()) {
            p.sendSystemMessage(Component.literal("Sua raça ainda não pode evoluir. Suba de nível, ganhe honra com um reino ou conclua a campanha.").withStyle(ChatFormatting.GRAY));
            return;
        }
        DefRacas.Raca alvo = null;
        if (destino == null || destino.isEmpty()) {
            if (c.size() == 1) alvo = c.get(0);
            else {
                p.sendSystemMessage(Component.literal("Escolha o caminho da evolução:").withStyle(ChatFormatting.LIGHT_PURPLE));
                for (DefRacas.Raca r : c)
                    p.sendSystemMessage(Component.literal("  [" + r.nome() + "] ").withStyle(net.minecraft.network.chat.Style.EMPTY
                            .withColor(ChatFormatting.GOLD).withBold(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/evoluir " + r.id()))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(r.descricao()))))
                            .append(Component.literal(r.descricao()).withStyle(ChatFormatting.GRAY)));
                return;
            }
        } else {
            for (DefRacas.Raca r : c) if (r.id().equals(destino)) alvo = r;
            if (alvo == null) { p.sendSystemMessage(Component.literal("Esse caminho não está liberado para você.").withStyle(ChatFormatting.RED)); return; }
        }
        DefRacas.Raca antes = DefRacas.de(RacasJogador.principal(p));
        RacasJogador.escolher(p, alvo.id(), RacasJogador.secundaria(p), true);   // evolução não cobra troca
        dados(p).putString("ultima", alvo.id());
        cena(p, antes, alvo);
        Progressao.enviarAoPainel(p, false);
    }

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("evoluir")
                .executes(ctx -> { evoluir(ctx.getSource().getPlayerOrException(), null); return 1; })
                .then(Commands.argument("raca", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                caminhos(c.getSource().getPlayer()).stream().map(DefRacas.Raca::id), b))
                        .executes(ctx -> { evoluir(ctx.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "raca")); return 1; })));
    }
}
