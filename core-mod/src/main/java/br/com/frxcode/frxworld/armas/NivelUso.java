package br.com.frxcode.frxworld.armas;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.aventura.Chefes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nível de uso dos itens do Códex (opção "Ganha nível com uso" no painel). É do próprio item: passa junto se ele trocar de dono.
 *
 * Arma: ganha experiência com o dano causado a monstros (só a vida que o monstro ainda tinha); o golpe final soma a vida
 * máxima dele de novo. Armadura: cada peça vestida ganha com o dano recebido de monstros. Chefões valem 5 vezes.
 * Não contam animais, jogadores, monstros de spawner, quedas, lava e afins, nem quem está no criativo.
 * Cada item tem um teto de experiência por dia. 10 níveis; o 10 pede 1,5 milhão no total.
 * Bônus: arma +2% de dano por nível; armadura -1% de dano recebido por nível de cada peça. Nível 5: o item brilha.
 * Nível 10: a arma solta faíscas a cada golpe.
 *
 * Os dados ficam no NBT do item; a descrição é refeita pelo Códex a cada 2 s quando muda (ver Codex.evoluir).
 */
public class NivelUso {
    static final String LIGADO = "frx_uso", XP = "frx_uso_xp", NIVEL = "frx_uso_lv", DIA = "frx_uso_dia", HOJE = "frx_uso_hoje", MUDOU = "frx_uso_mudou";
    private static final String SPAWNER = "frx_spawner";
    /** Experiência total para chegar a cada nível (índice = nível). */
    private static final long[] TOTAL = {0, 5_000, 28_000, 76_000, 155_000, 269_000, 422_000, 620_000, 866_000, 1_163_000, 1_500_000};
    private static final int MAXIMO = 10;
    private static final double TETO_DIA = 25_000;

    public NivelUso() {}

    private static boolean ligado(ItemStack s) { return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean(LIGADO); }
    public static int nivel(ItemStack s) { return ligado(s) ? s.getTag().getInt(NIVEL) : 0; }
    private static long hoje() { return java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toEpochDay(); }
    private static String num(double v) { return String.format(new Locale("pt", "BR"), "%,d", (long) Math.floor(v)); }

    /** Monstro que vale experiência: hostil, não jogador, não de spawner. */
    private static boolean vale(Entity e) {
        return e instanceof LivingEntity && e instanceof Enemy && !(e instanceof Player) && !e.getPersistentData().getBoolean(SPAWNER);
    }

    private static boolean chefao(LivingEntity e) { return Chefes.ehChefao(e) || e.getMaxHealth() >= 150; }

    /** Soma experiência ao item; sobe de nível e avisa. */
    private static void ganhar(ServerPlayer p, ItemStack s, double qtd) {
        if (!ligado(s) || qtd <= 0 || p.isCreative()) return;
        CompoundTag t = s.getTag();
        int nv = t.getInt(NIVEL);
        if (nv >= MAXIMO) return;
        long dia = hoje();
        if (t.getLong(DIA) != dia) { t.putLong(DIA, dia); t.putDouble(HOJE, 0); }
        double livre = TETO_DIA - t.getDouble(HOJE);
        if (livre <= 0) return;
        double q = Math.min(qtd, livre);
        t.putDouble(HOJE, t.getDouble(HOJE) + q);
        double xp = t.getDouble(XP) + q;
        t.putDouble(XP, xp);
        t.putBoolean(MUDOU, true);
        if (t.getDouble(HOJE) >= TETO_DIA)
            p.displayClientMessage(Component.literal("✦ ").withStyle(ChatFormatting.GOLD).append(s.getHoverName())
                    .append(Component.literal(" já aprendeu tudo o que podia hoje. Volta a crescer amanhã.").withStyle(ChatFormatting.GRAY)), true);
        while (nv < MAXIMO && xp >= TOTAL[nv + 1]) {
            nv++;
            t.putInt(NIVEL, nv);
            FrxWorld.LOG.info("Nível de uso: {} de {} chegou ao nível {}.", Codex.codigoDe(s), p.getGameProfile().getName(), nv);
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.7f);
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("✦ ").withStyle(ChatFormatting.GOLD).append(s.getHoverName())
                    .append(Component.literal(" de " + br.com.frxcode.frxworld.acesso.Personagens.nome(p) + " chegou ao nível " + nv + "! ✦").withStyle(ChatFormatting.GOLD)), false);
            if (nv >= 5) brilhar(s);
            Codex.registrarNivelUso(Codex.codigoDe(s), nv);
        }
    }

    /** A partir do nível 5 o item brilha (como encantado), mesmo sem encantamentos. */
    static void brilhar(ItemStack s) {
        if (!s.isEnchanted() && !s.getTag().contains("Enchantments")) {
            ListTag l = new ListTag();
            l.add(new CompoundTag());
            s.getTag().put("Enchantments", l);
        }
    }

    private static List<ItemStack> armadura(Player p) {
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack s : p.getArmorSlots()) if (ligado(s) && s.getItem() instanceof ArmorItem) l.add(s);
        return l;
    }

    // ------------------------------------------------------------------ eventos

    /** Monstros de spawner não dão experiência. */
    @SubscribeEvent
    public void aoNascer(MobSpawnEvent.FinalizeSpawn ev) {
        if (ev.getSpawnType() == MobSpawnType.SPAWNER) ev.getEntity().getPersistentData().putBoolean(SPAWNER, true);
    }

    /** Bônus de dano (arma) e de proteção (armadura). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void bonus(LivingHurtEvent ev) {
        float v = ev.getAmount();
        if (ev.getSource().getEntity() instanceof Player atacante) {
            int nv = nivel(atacante.getMainHandItem());
            if (nv > 0 && !(atacante.getMainHandItem().getItem() instanceof ArmorItem)) {
                v *= 1 + 0.02f * nv;
                if (nv >= MAXIMO && atacante.level() instanceof ServerLevel n)
                    n.sendParticles(ParticleTypes.END_ROD, ev.getEntity().getX(), ev.getEntity().getY(0.6), ev.getEntity().getZ(), 8, 0.3, 0.4, 0.3, 0.05);
            }
        }
        if (ev.getEntity() instanceof Player vitima) {
            int soma = 0;
            for (ItemStack s : armadura(vitima)) soma += nivel(s);
            if (soma > 0) v *= Math.max(0.5f, 1 - 0.01f * soma);
        }
        ev.setAmount(v);
    }

    /** Experiência pelo dano que de fato entrou. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void experiencia(LivingDamageEvent ev) {
        LivingEntity alvo = ev.getEntity();
        double real = Math.min(ev.getAmount(), alvo.getHealth());
        if (real <= 0) return;
        // arma: dano em monstro
        if (ev.getSource().getEntity() instanceof ServerPlayer p && vale(alvo)) {
            ItemStack arma = p.getMainHandItem();
            if (ligado(arma) && !(arma.getItem() instanceof ArmorItem)) ganhar(p, arma, real * (chefao(alvo) ? 5 : 1));
        }
        // armadura: dano recebido de monstro
        if (alvo instanceof ServerPlayer p && vale(ev.getSource().getEntity())) {
            double mult = ev.getSource().getEntity() instanceof LivingEntity le && chefao(le) ? 5 : 1;
            for (ItemStack s : armadura(p)) ganhar(p, s, real * mult);
        }
    }

    /** Golpe final: soma a vida máxima do monstro. */
    @SubscribeEvent
    public void golpeFinal(LivingDeathEvent ev) {
        LivingEntity alvo = ev.getEntity();
        if (!(ev.getSource().getEntity() instanceof ServerPlayer p) || !vale(alvo)) return;
        ItemStack arma = p.getMainHandItem();
        if (ligado(arma) && !(arma.getItem() instanceof ArmorItem)) ganhar(p, arma, alvo.getMaxHealth() * (chefao(alvo) ? 5 : 1));
    }

    // ------------------------------------------------------------------ descrição

    /** Linhas da descrição do item: nível, progresso e como funciona (chamado por Codex.descrever). */
    static void descrever(ItemStack s, ListTag lore) {
        if (!ligado(s)) return;
        CompoundTag t = s.getTag();
        int nv = t.getInt(NIVEL);
        boolean armadura = s.getItem() instanceof ArmorItem;
        StringBuilder estrelas = new StringBuilder();
        for (int i = 1; i <= MAXIMO; i++) estrelas.append(i <= nv ? "★" : "☆");
        Codex.linhaLore(lore, "Nível do item: " + nv + " " + estrelas, ChatFormatting.GOLD);
        if (nv >= MAXIMO) Codex.linhaLore(lore, "Nível máximo alcançado", ChatFormatting.YELLOW);
        else {
            Codex.linhaLore(lore, "Experiência: " + num(t.getDouble(XP)) + " / " + num(TOTAL[nv + 1]), ChatFormatting.GRAY);
            double hojeQ = t.getLong(DIA) == hoje() ? t.getDouble(HOJE) : 0;
            Codex.linhaLore(lore, "Hoje: " + num(hojeQ) + " / " + num(TETO_DIA) + " (limite diário)", ChatFormatting.DARK_GRAY);
        }
        if (armadura) {
            Codex.linhaLore(lore, "Sobe com o dano que você recebe", ChatFormatting.DARK_AQUA);
            Codex.linhaLore(lore, "de monstros enquanto a veste.", ChatFormatting.DARK_AQUA);
            Codex.linhaLore(lore, "Cada nível: -1% de dano recebido", ChatFormatting.DARK_AQUA);
            Codex.linhaLore(lore, "(agora -" + nv + "%)", ChatFormatting.AQUA);
        } else {
            Codex.linhaLore(lore, "Sobe com o dano causado a monstros;", ChatFormatting.DARK_AQUA);
            Codex.linhaLore(lore, "o golpe final vale o dobro.", ChatFormatting.DARK_AQUA);
            Codex.linhaLore(lore, "Cada nível: +2% de dano (agora +" + (2 * nv) + "%)", ChatFormatting.AQUA);
        }
        Codex.linhaLore(lore, "Chefões valem 5x. Spawner não conta.", ChatFormatting.DARK_GRAY);
    }
}
