package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Favor real: serviço diário que o rei só confia a quem é Honrado ou mais no reino.
 * Derrotar monstros a até 200 blocos da capital; paga reputação, moedas e XP. Um por dia em cada reino.
 */
public final class FavorReal {
    private static final String CHAVE = "frx_favor";

    private FavorReal() {}

    private static long hoje() { return (System.currentTimeMillis() - 3 * 3_600_000L) / 86_400_000L; }

    private static CompoundTag dados(ServerPlayer p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    public static boolean liberado(ServerPlayer p, String reino) { return Reputacao.indice(Reputacao.pontos(p, reino)) >= 2; }

    /** Conversa com o rei: aceita, acompanha ou entrega o favor do dia. Devolve a fala do rei. */
    static String falar(ServerPlayer p, String reino) {
        if (!liberado(p, reino)) return "Ainda não o conheço o bastante para confiar um serviço da coroa.";
        CompoundTag d = dados(p);
        CompoundTag f = d.getCompound(reino);
        int idx = Reputacao.indice(Reputacao.pontos(p, reino));
        if (!d.contains(reino) || f.getLong("dia") != hoje()) {
            f = new CompoundTag();
            f.putLong("dia", hoje());
            f.putInt("meta", 15 + 5 * idx);
            f.putInt("prog", 0);
            d.put(reino, f);
            return "Os arredores andam perigosos. Derrote " + f.getInt("meta") + " monstros a até 200 blocos da capital e volte a mim. A coroa saberá agradecer.";
        }
        if (f.getBoolean("pago")) return "Você já serviu à coroa hoje. Volte amanhã.";
        int falta = f.getInt("meta") - f.getInt("prog");
        if (falta > 0) return "Ainda faltam " + falta + " monstros. Os arredores da capital contam com você.";
        f.putBoolean("pago", true);
        int nivel = Progressao.dados(p).nivel();
        long moedas = 500 + nivel * 30L;
        Moedas.dar(p, moedas);
        Progressao.ganharXp(p, nivel * 60L);
        Reputacao.ganhar(p, reino, 60 + 20 * idx, true);
        return "Serviço cumprido. Receba " + Moedas.texto(moedas) + " e a gratidão do reino.";
    }

    /** Abate de monstro perto da capital. */
    static void abate(ServerPlayer p, String reino) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG) || !raiz.getCompound(Player.PERSISTED_NBT_TAG).contains(CHAVE)) return;
        CompoundTag d = dados(p);
        if (!d.contains(reino)) return;
        CompoundTag f = d.getCompound(reino);
        if (f.getLong("dia") != hoje() || f.getBoolean("pago") || f.getInt("prog") >= f.getInt("meta")) return;
        f.putInt("prog", f.getInt("prog") + 1);
        if (f.getInt("prog") >= f.getInt("meta"))
            p.sendSystemMessage(Component.literal("✦ Favor real cumprido. Volte ao rei para receber.").withStyle(net.minecraft.ChatFormatting.GOLD));
    }
}
