package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.armas.Codex;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sábio das capitais: extrai um encantamento de um item (ou de um livro com vários) para um livro em branco, numa bancada
 * como a bigorna (MenuDesencanto: espaço do item e do livro; a tela mostra os encantamentos para escolher).
 * Caro de propósito: moedas (bolsa e, se faltar, banco), níveis de experiência e um livro; encantamentos altos podem falhar
 * (o livro, as moedas e a experiência se perdem, o item fica como estava). Relíquias do Códex e maldições não saem.
 * Preços no painel (Economia → Extrair encantamento), seção "desencanto" da configuração.
 */
public final class Desencanto {
    public static final int ABRIR = 0, EXTRAIR = 1, TIRAR_RUNA = 2;

    private Desencanto() {}

    // ------------------------------------------------------------------ preços

    private static double num(String k, double padrao) {
        try { JsonObject c = ConfigJogo.secaoCrua("desencanto"); if (c != null && c.has(k)) return c.get(k).getAsDouble(); } catch (Exception ignorado) { }
        return padrao;
    }

    public static boolean ativo() {
        try { JsonObject c = ConfigJogo.secaoCrua("desencanto"); if (c != null && c.has("ativo")) return c.get("ativo").getAsBoolean(); } catch (Exception ignorado) { }
        return true;
    }

    /** Peso da raridade do encantamento (tesouro, como Remendo, ainda multiplica). */
    private static double peso(Enchantment e) {
        double r = switch (e.getRarity()) {
            case COMMON -> num("multComum", 1);
            case UNCOMMON -> num("multIncomum", 1.5);
            case RARE -> num("multRaro", 2.5);
            case VERY_RARE -> num("multMuitoRaro", 4);
        };
        return e.isTreasureOnly() ? r * num("multTesouro", 1.5) : r;
    }

    record Custo(long moedas, int xp, int chance) {}

    /** Tirar uma runa encaixada: preço pelo nível dela (I, II, III). Padrão 10, 25 e 50 ouro. */
    static long precoRuna(int nivel) {
        long[] padrao = {1000, 2500, 5000};
        int n = Math.max(1, Math.min(3, nivel));
        return Math.max(0, (long) num("runa" + n, padrao[n - 1]));
    }

    static Custo custo(Enchantment e, int nivel) {
        double m = peso(e);
        long moedas = Math.round(num("moedasPorNivel", 2000) * m * nivel);
        int xp = (int) Math.min(num("xpMax", 30), Math.round(num("xpBase", 5) + num("xpPorNivel", 3) * nivel * m));
        int chance = nivel >= 5 ? (int) num("chance5", 70) : nivel == 4 ? (int) num("chance4", 85) : 100;
        return new Custo(moedas, xp, Math.max(1, Math.min(100, chance)));
    }

    // ------------------------------------------------------------------ item

    private static String bloqueio(ItemStack s) {
        if (s.isEmpty()) return "Segure na mão principal o item com o encantamento.";
        if (Codex.codigoDe(s) != null) return "Relíquias do Códex não se separam dos seus encantamentos.";
        if (lista(s).isEmpty()) return "Esse item não tem nenhum encantamento para extrair.";
        return null;
    }

    private static List<Map.Entry<Enchantment, Integer>> lista(ItemStack s) {
        return new ArrayList<>(EnchantmentHelper.getEnchantments(s).entrySet());
    }

    /** Paga com a bolsa e, se faltar, com o banco. */
    private static boolean pagar(ServerPlayer p, long valor) {
        long bolsa = Moedas.contar(p), banco = Banco.saldo(p);
        if (bolsa + banco < valor) return false;
        long daBolsa = Math.min(bolsa, valor);
        if (daBolsa > 0 && !Moedas.cobrar(p, daBolsa)) return false;
        if (valor - daBolsa > 0) Banco.definirSaldo(p, banco - (valor - daBolsa));
        return true;
    }

    // ------------------------------------------------------------------ tela

    /** Preços (para a tela calcular igual ao servidor) e dados do jogador. */
    private static CompoundTag info(ServerPlayer p, Entity npc, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        d.putInt("npc", npc.getId());
        d.putString("nome", npc.getCustomName() != null ? npc.getCustomName().getString() : "Sábio");
        d.putLong("bolsa", Moedas.contar(p));
        d.putLong("banco", Banco.saldo(p));
        d.putInt("xp", p.experienceLevel);
        d.putBoolean("criativo", p.isCreative());
        d.putBoolean("ativo", ativo());
        for (int n = 1; n <= 3; n++) d.putLong("runa" + n, precoRuna(n));
        for (String k : new String[]{"moedasPorNivel", "multComum", "multIncomum", "multRaro", "multMuitoRaro", "multTesouro", "xpBase", "xpPorNivel", "xpMax", "chance4", "chance5"})
            d.putDouble(k, num(k, switch (k) {
                case "moedasPorNivel" -> 2000; case "multComum" -> 1; case "multIncomum" -> 1.5; case "multRaro" -> 2.5; case "multMuitoRaro" -> 4;
                case "multTesouro" -> 1.5; case "xpBase" -> 5; case "xpPorNivel" -> 3; case "xpMax" -> 30; case "chance4" -> 85; default -> 70;
            }));
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        return d;
    }

    /** Abre a bancada do sábio (espaço do item e do livro, como na bigorna). */
    public static void abrir(ServerPlayer p, Entity npc) {
        CompoundTag d = info(p, npc, "", true);
        Component titulo = Component.literal(d.getString("nome"));
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, jog) -> new MenuDesencanto(id, inv, npc.getId(), d), titulo),
                buf -> { buf.writeInt(npc.getId()); buf.writeNbt(d); });
    }

    private static void responder(ServerPlayer p, Entity npc, String msg, boolean ok) {
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("desencanto", info(p, npc, msg, ok)));
    }

    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (npc == null || npc.distanceToSqr(p) > 144 || !"sabio".equals(npc.getPersistentData().getString("frx_papel"))) return;
        if (!(p.containerMenu instanceof MenuDesencanto m) || m.npc != a.npc()) return;
        if (a.acao() == TIRAR_RUNA) { tirarRuna(p, npc, m, (int) a.valor()); return; }
        if (a.acao() != EXTRAIR) { responder(p, npc, "", true); return; }
        ItemStack s = m.caixa.getItem(MenuDesencanto.ITEM);
        ItemStack livroBranco = m.caixa.getItem(MenuDesencanto.LIVRO);
        String b = ativo() ? bloqueio(s) : "O sábio não está atendendo agora.";
        if (b != null) { responder(p, npc, b.replace("Segure na mão principal o item", "Coloque o item"), false); return; }
        List<Map.Entry<Enchantment, Integer>> l = lista(s);
        int i = (int) a.valor();
        if (i < 0 || i >= l.size()) { responder(p, npc, "", true); return; }
        Enchantment e = l.get(i).getKey();
        int nv = l.get(i).getValue();
        Custo c = custo(e, nv);
        String msg;
        boolean ok = false;
        if (e.isCurse()) msg = "Maldições não se deixam prender num livro.";
        else if (!MenuDesencanto.livroBranco(livroBranco)) msg = "Coloque um livro em branco (ou um livro encantado vazio) no espaço do livro.";
        else if (!p.isCreative() && p.experienceLevel < c.xp()) msg = "Faltam níveis de experiência: são " + c.xp() + ", você tem " + p.experienceLevel + ".";
        else if (Moedas.contar(p) + Banco.saldo(p) < c.moedas()) msg = "Faltam moedas: custa " + Moedas.texto(c.moedas()) + ".";
        else if (!pagar(p, c.moedas())) msg = "Não consegui cobrar as moedas.";
        else {
            livroBranco.shrink(1);
            if (!p.isCreative()) p.giveExperienceLevels(-c.xp());
            String nome = e.getFullname(nv).getString();
            if (ThreadLocalRandom.current().nextInt(100) < c.chance()) {
                Map<Enchantment, Integer> resto = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(s));
                resto.remove(e);
                if (s.is(Items.ENCHANTED_BOOK) && resto.isEmpty()) m.caixa.setItem(MenuDesencanto.ITEM, new ItemStack(Items.BOOK));
                else EnchantmentHelper.setEnchantments(resto, s);
                ItemStack livro = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(e, nv));
                if (m.caixa.getItem(MenuDesencanto.LIVRO).isEmpty()) m.caixa.setItem(MenuDesencanto.LIVRO, livro);   // como na bigorna: sai no lugar do livro
                else if (!p.getInventory().add(livro)) p.drop(livro, false);
                p.level().playSound(null, npc.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1f, 1f);
                p.serverLevel().sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.5, 0.6, 0.5, 0.6);
                msg = "Feito. " + nome + " agora mora num livro.";
                ok = true;
                FrxWorld.LOG.info("Desencanto: {} extraiu {} {} por {} e {} níveis.", p.getGameProfile().getName(), ForgeRegistries.ENCHANTMENTS.getKey(e), nv, c.moedas(), c.xp());
            } else {
                p.level().playSound(null, npc.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1f, 0.8f);
                p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 30, 0.4, 0.5, 0.4, 0.02);
                msg = "A magia escapou e o livro virou cinzas. " + nome + " continua no item.";
                FrxWorld.LOG.info("Desencanto: {} falhou ao extrair {} {} (perdeu {} e {} níveis).", p.getGameProfile().getName(), ForgeRegistries.ENCHANTMENTS.getKey(e), nv, c.moedas(), c.xp());
            }
            m.caixa.setChanged();
            m.broadcastChanges();
        }
        if (!ok && !msg.startsWith("A magia")) p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 1f);
        responder(p, npc, msg, ok);
    }

    /** Tira a runa escolhida do item da bancada; a runa volta inteira para a mochila. */
    private static void tirarRuna(ServerPlayer p, Entity npc, MenuDesencanto m, int indice) {
        ItemStack s = m.caixa.getItem(MenuDesencanto.ITEM);
        List<Map.Entry<String, Integer>> l = new ArrayList<>(br.com.frxcode.frxworld.aventura.Runas.doItem(s).entrySet());
        String msg;
        boolean ok = false;
        if (s.isEmpty()) msg = "Coloque a arma ou a armadura no espaço do item.";
        else if (!ativo()) msg = "O sábio não está atendendo agora.";
        else if (l.isEmpty()) msg = "Esse item não tem runa encaixada.";
        else if (indice < 0 || indice >= l.size()) { responder(p, npc, "", true); return; }
        else {
            String tipo = l.get(indice).getKey();
            int nv = l.get(indice).getValue();
            long custo = precoRuna(nv);
            if (!pagar(p, custo)) msg = "Faltam moedas: tirar essa runa custa " + Moedas.texto(custo) + ".";
            else {
                ItemStack runa = br.com.frxcode.frxworld.aventura.Runas.tirar(s, tipo);
                if (!p.getInventory().add(runa)) p.drop(runa, false);
                m.caixa.setChanged();
                m.broadcastChanges();
                p.level().playSound(null, npc.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1f, 1f);
                msg = "A " + br.com.frxcode.frxworld.aventura.Runas.nomeRuna(tipo, nv) + " saiu inteira. Está na sua mochila.";
                ok = true;
                FrxWorld.LOG.info("Desencanto: {} tirou {} {} por {}.", p.getGameProfile().getName(), tipo, nv, custo);
            }
        }
        if (!ok) p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 1f);
        responder(p, npc, msg, ok);
    }
}
