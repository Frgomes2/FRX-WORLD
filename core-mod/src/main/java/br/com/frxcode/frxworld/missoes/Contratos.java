package br.com.frxcode.frxworld.missoes;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Colecao;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Contratos da Guilda: 3 tarefas por dia e 1 por semana, sorteadas para cada aventureiro e medidas pelo rank.
 * O progresso conta sozinho (pelos contadores das Conquistas e por alguns eventos) e a recompensa cai na hora — sem comando e sem entregar nada.
 * O quadro (janela) abre pelo botão "Contratos" do cartão ou do balcão da Guilda.
 */
public class Contratos {
    private static final String CHAVE = "frx_contratos";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** base e porRank dão o alvo do dia; semana e semanaPorRank, o da semana (0 = não entra no sorteio). */
    private record Tipo(String id, String texto, Item icone, int base, int porRank, int semana, int semanaPorRank) {
        int alvo(int rank, boolean semanal) { return Math.max(1, semanal ? semana + semanaPorRank * rank : base + porRank * rank); }
    }

    private static final List<Tipo> TIPOS = List.of(
            new Tipo("matar", "Derrote %d monstros", Items.IRON_SWORD, 25, 10, 200, 50),
            new Tipo("zumbis", "Derrote %d zumbis", Items.ROTTEN_FLESH, 10, 3, 0, 0),
            new Tipo("esqueletos", "Derrote %d esqueletos", Items.BONE, 10, 3, 0, 0),
            new Tipo("aranhas", "Derrote %d aranhas", Items.SPIDER_EYE, 8, 2, 0, 0),
            new Tipo("creepers", "Derrote %d creepers", Items.GUNPOWDER, 4, 2, 0, 0),
            new Tipo("minerar", "Minere %d minérios", Items.IRON_PICKAXE, 20, 5, 150, 30),
            new Tipo("pescar", "Pesque %d vezes", Items.FISHING_ROD, 5, 1, 0, 0),
            new Tipo("torre", "Vença %d andares da Torre", Items.LADDER, 2, 1, 10, 3),
            new Tipo("portal", "Feche %d portais", Items.ENDER_EYE, 0, 0, 2, 0),
            new Tipo("chefe", "Derrote %d chefes", Items.WITHER_SKELETON_SKULL, 0, 0, 2, 0));

    /** Contador das Conquistas → tipo de contrato. "chefe" conta pela morte de qualquer chefão (Colecao); "torre", "minerar" e "pescar" têm ganchos próprios. */
    private static final Map<String, String> DE_CONTADOR = Map.of("matar_total", "matar", "c:zumbis", "zumbis", "c:esqueletos", "esqueletos",
            "c:aranhas", "aranhas", "c:creepers", "creepers", "portais", "portal");

    public Contratos() {}

    private static Tipo tipo(String id) {
        for (Tipo t : TIPOS) if (t.id().equals(id)) return t;
        return TIPOS.get(0);
    }

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    private static long hoje() { return LocalDate.now(FUSO).toEpochDay(); }
    private static long semana() { return (hoje() + 3) / 7; }   // vira na segunda-feira

    private static CompoundTag novo(Tipo t, int rank, boolean semanal) {
        CompoundTag c = new CompoundTag();
        c.putString("tipo", t.id());
        c.putInt("alvo", t.alvo(rank, semanal));
        c.putInt("prog", 0);
        c.putBoolean("pago", false);
        return c;
    }

    /** Sorteia os contratos do dia e da semana se ainda não foram sorteados. Devolve true se o dia virou. */
    private static boolean garantir(ServerPlayer p) {
        CompoundTag d = dados(p);
        int rank = Guilda.indice(p);
        boolean virou = false;
        if (d.getLong("dia") != hoje() || !d.contains("d0")) {
            Random rnd = new Random(p.getUUID().getLeastSignificantBits() ^ (hoje() * 0x9E3779B97F4A7C15L));
            List<Tipo> opcoes = new ArrayList<>();
            for (Tipo t : TIPOS) if (t.base() > 0) opcoes.add(t);
            for (int i = 0; i < 3; i++) d.put("d" + i, novo(opcoes.remove(rnd.nextInt(opcoes.size())), rank, false));
            d.putLong("dia", hoje());
            d.putBoolean("bonus", false);
            virou = true;
        }
        if (d.getLong("semana") != semana() || !d.contains("s")) {
            Random rnd = new Random(p.getUUID().getMostSignificantBits() ^ (semana() * 0x9E3779B97F4A7C15L));
            List<Tipo> opcoes = new ArrayList<>();
            for (Tipo t : TIPOS) if (t.semana() > 0) opcoes.add(t);
            d.put("s", novo(opcoes.get(rnd.nextInt(opcoes.size())), rank, true));
            d.putLong("semana", semana());
        }
        return virou;
    }

    // ------------------------------------------------------------------ recompensas (em cobre e XP), pelo rank e pelo nível

    private static long moedasDia(int rank) { return 30 + 20L * rank; }
    private static long moedasBonus(int rank) { return 60 + 40L * rank; }
    private static long moedasSemana(int rank) { return 300 + 200L * rank; }
    private static long xpDoNivel(ServerPlayer p, double parte) { return Math.max(10, Math.round(Progressao.xpParaProximo(Progressao.dados(p).nivel()) * parte)); }

    private static void pagar(ServerPlayer p, String titulo, String texto, long moedas, long xp) {
        Moedas.dar(p, moedas);
        Progressao.ganharXp(p, xp);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
        Sistema.avisar(p, titulo, texto + " Recompensa: " + Moedas.texto(moedas) + " e " + xp + " XP.", Sistema.OURO);
    }

    // ------------------------------------------------------------------ progresso

    /** Chamado por Conquistas.somar. */
    public static void doContador(ServerPlayer p, String chave, int quanto) {
        String t = DE_CONTADOR.get(chave);
        if (t != null) progresso(p, t, quanto);
    }

    /** Soma progresso nos contratos desse tipo (do dia e da semana) e paga os que fecharem. */
    public static void progresso(ServerPlayer p, String tipo, int quanto) {
        if (quanto <= 0 || !Progressao.temDados(p) || !Guilda.registrado(p)) return;
        garantir(p);
        CompoundTag d = dados(p);
        int rank = Guilda.indice(p);
        for (String k : new String[]{"d0", "d1", "d2", "s"}) {
            CompoundTag c = d.getCompound(k);
            if (!c.getString("tipo").equals(tipo) || c.getBoolean("pago")) continue;
            int prog = Math.min(c.getInt("alvo"), c.getInt("prog") + quanto);
            c.putInt("prog", prog);
            String texto = String.format(tipo(tipo).texto(), c.getInt("alvo"));
            boolean semanal = k.equals("s");
            if (prog < c.getInt("alvo")) {
                p.displayClientMessage(Component.literal((semanal ? "Contrato da semana: " : "Contrato: ") + texto + "  " + prog + "/" + c.getInt("alvo")).withStyle(ChatFormatting.GOLD), true);
                continue;
            }
            c.putBoolean("pago", true);
            if (semanal) pagar(p, "Contrato da semana cumprido", texto + ".", moedasSemana(rank), xpDoNivel(p, 0.6));
            else pagar(p, "Contrato cumprido", texto + ".", moedasDia(rank), xpDoNivel(p, 0.10));
        }
        if (!d.getBoolean("bonus") && d.getCompound("d0").getBoolean("pago") && d.getCompound("d1").getBoolean("pago") && d.getCompound("d2").getBoolean("pago")) {
            d.putBoolean("bonus", true);
            pagar(p, "Os três contratos do dia", "A Guilda paga um extra a quem fecha o quadro.", moedasBonus(rank), xpDoNivel(p, 0.20));
        }
    }

    @SubscribeEvent
    public void aoQuebrar(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && !p.isCreative() && e.getState().is(Tags.Blocks.ORES)) progresso(p, "minerar", 1);
    }

    @SubscribeEvent
    public void aoPescar(ItemFishedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) progresso(p, "pescar", 1);
    }

    @SubscribeEvent
    public void aoEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Progressao.temDados(p) || !Guilda.registrado(p)) return;
        if (!garantir(p)) return;
        CompoundTag d = dados(p);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            CompoundTag c = d.getCompound("d" + i);
            sb.append(i == 0 ? "" : " · ").append(String.format(tipo(c.getString("tipo")).texto(), c.getInt("alvo")));
        }
        Sistema.avisar(p, "Contratos da Guilda de hoje", sb + ". Veja o quadro pelo Cartão de Aventureiro.", Sistema.AZUL);
    }

    // ------------------------------------------------------------------ quadro (cliente/TelaContratos)

    private static CompoundTag cartao(ServerPlayer p, CompoundTag c, boolean semanal) {
        Tipo t = tipo(c.getString("tipo"));
        int rank = Guilda.indice(p);
        CompoundTag o = new CompoundTag();
        o.putString("texto", String.format(t.texto(), c.getInt("alvo")));
        o.putInt("prog", c.getInt("prog")); o.putInt("alvo", c.getInt("alvo"));
        o.putBoolean("pago", c.getBoolean("pago"));
        o.putLong("moedas", semanal ? moedasSemana(rank) : moedasDia(rank));
        o.putLong("xp", xpDoNivel(p, semanal ? 0.6 : 0.10));
        o.put("item", new ItemStack(t.icone()).save(new CompoundTag()));
        return o;
    }

    /** Abre o quadro de contratos: os três do dia, o extra por fechar os três, o da semana e a coleção. */
    public static void abrir(ServerPlayer p) {
        if (!Progressao.temDados(p)) return;
        if (!Guilda.registrado(p)) { Sistema.avisar(p, "Contratos da Guilda", "Registre-se com a recepcionista da Guilda para receber contratos.", Sistema.VERMELHO); return; }
        garantir(p);
        CompoundTag d = dados(p), o = new CompoundTag();
        int rank = Guilda.indice(p), feitos = 0;
        ListTag dia = new ListTag();
        for (int i = 0; i < 3; i++) {
            dia.add(cartao(p, d.getCompound("d" + i), false));
            if (d.getCompound("d" + i).getBoolean("pago")) feitos++;
        }
        o.putString("rank", Guilda.rank(p));
        o.put("dia", dia);
        o.put("semana", cartao(p, d.getCompound("s"), true));
        o.putInt("feitos", feitos);
        o.putBoolean("bonus", d.getBoolean("bonus"));
        o.putLong("bonusMoedas", moedasBonus(rank));
        o.putLong("bonusXp", xpDoNivel(p, 0.20));
        java.time.ZonedDateTime agora = java.time.ZonedDateTime.now(FUSO);
        int faltaDia = (int) Math.max(1, java.time.Duration.between(agora, agora.toLocalDate().plusDays(1).atStartOfDay(FUSO)).toMinutes());
        o.putInt("faltaDia", faltaDia);
        o.putInt("faltaSemana", (int) (6 - (hoje() + 3) % 7) * 1440 + faltaDia);
        ItemStack colecao = Colecao.icone(p);
        o.put("colecao", colecao.save(new CompoundTag()));
        br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new br.com.frxcode.frxworld.rede.AbrirMenu("contratos", o));
    }

    /** Para a ficha do jogador no painel. */
    public static com.google.gson.JsonObject json(ServerPlayer p) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        if (!Progressao.temDados(p) || !Guilda.registrado(p)) return o;
        garantir(p);
        CompoundTag d = dados(p);
        com.google.gson.JsonArray dia = new com.google.gson.JsonArray();
        for (String k : new String[]{"d0", "d1", "d2", "s"}) {
            CompoundTag c = d.getCompound(k);
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("texto", String.format(tipo(c.getString("tipo")).texto(), c.getInt("alvo")));
            j.addProperty("prog", c.getInt("prog"));
            j.addProperty("alvo", c.getInt("alvo"));
            j.addProperty("pago", c.getBoolean("pago"));
            if (k.equals("s")) o.add("semana", j); else dia.add(j);
        }
        o.add("dia", dia);
        return o;
    }
}
