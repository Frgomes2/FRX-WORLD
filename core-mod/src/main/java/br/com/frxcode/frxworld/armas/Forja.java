package br.com.frxcode.frxworld.armas;

import br.com.frxcode.frxworld.moedas.Moedas;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ferreiro (agachado + clique com o item na mão): aprimora +1 até +10, cobrando moedas e minério, com chance de falha.
 * Armeiro (agachado + clique): reforja a raridade (nunca piora).
 * O primeiro clique mostra o preço; o segundo, em até 8 s, confirma.
 */
public final class Forja {
    private Forja() {}

    private record Pendente(String acao, int slot, long quando) {}
    private static final Map<UUID, Pendente> PENDENTES = new HashMap<>();

    private static final int[] SUCESSO = {100, 100, 100, 100, 90, 80, 70, 55, 45, 35, 25};

    private record Custo(long moedas, Item material, int qtd) {}

    // ------------------------------------------------------------------ tabela (aba Forja do painel; sem config = padrão abaixo)

    private static com.google.gson.JsonObject cfg() { return br.com.frxcode.frxworld.progressao.ConfigJogo.secaoCrua("forja"); }

    private static Item item(String id, Item padrao) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id == null ? "" : id);
        Item i = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        return i == null || i == Items.AIR ? padrao : i;
    }

    private static com.google.gson.JsonObject linha(int alvo) {
        try {
            com.google.gson.JsonArray n = cfg().getAsJsonArray("niveis");
            return n.get(alvo - 1).getAsJsonObject();
        } catch (Exception e) { return null; }
    }

    private static int chance(int alvo) {
        com.google.gson.JsonObject l = linha(alvo);
        try { if (l != null) return Math.max(0, Math.min(100, l.get("chance").getAsInt())); } catch (Exception ignorado) {}
        return SUCESSO[alvo];
    }

    private static int anunciarAPartirDe() {
        try { return cfg().get("anunciarAPartirDe").getAsInt(); } catch (Exception e) { return 7; }
    }

    private static Custo custoAprimorar(int alvo) {
        com.google.gson.JsonObject l = linha(alvo);
        if (l != null) {
            try {
                return new Custo(Math.max(0, l.get("moedas").getAsLong()), item(l.get("material").getAsString(), Items.IRON_INGOT),
                        Math.max(0, l.get("qtd").getAsInt()));
            } catch (Exception ignorado) {}
        }
        long moedas = 15L * alvo * alvo;
        if (alvo <= 3) return new Custo(moedas, Items.IRON_INGOT, alvo * 2);
        if (alvo <= 6) return new Custo(moedas, Items.DIAMOND, alvo - 3);
        if (alvo <= 9) return new Custo(moedas, Items.DIAMOND, (alvo - 5) * 2);
        return new Custo(moedas, Items.NETHERITE_SCRAP, 2);
    }

    private static Custo custoReforjar(int raridade) {
        try {
            com.google.gson.JsonObject r = cfg().getAsJsonObject("reforja");
            return new Custo(r.get("moedasBase").getAsLong() * (raridade + 1) * (raridade + 1), item(r.get("material").getAsString(), Items.AMETHYST_SHARD),
                    r.get("qtdBase").getAsInt() + raridade * r.get("qtdPorRaridade").getAsInt());
        } catch (Exception ignorado) {}
        return new Custo(50L * (raridade + 1) * (raridade + 1), Items.AMETHYST_SHARD, 2 + raridade * 2);
    }

    private static void dizer(ServerPlayer p, Entity npc, String texto, ChatFormatting cor) {
        String nome = npc.getCustomName() != null ? npc.getCustomName().getString() : "Ferreiro";
        p.sendSystemMessage(Component.literal("[" + nome + "] ").withStyle(ChatFormatting.GOLD).append(Component.literal(texto).withStyle(cor)));
    }

    private static int contar(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void tirar(ServerPlayer p, Item item, int qtd) {
        for (ItemStack s : p.getInventory().items) {
            if (qtd <= 0) break;
            if (s.is(item)) { int t = Math.min(qtd, s.getCount()); s.shrink(t); qtd -= t; }
        }
        p.getInventory().setChanged();
    }

    private static String descrever(Custo c) {
        return Moedas.texto(c.moedas()) + " + " + c.qtd() + "× " + new ItemStack(c.material()).getHoverName().getString();
    }

    /** Chamado pelo clique agachado num ferreiro/armeiro de capital. */
    public static void aoClicar(ServerPlayer p, Entity npc, boolean reforjar) {
        ItemStack item = p.getMainHandItem();
        if (!Armas.forjavel(item)) {
            dizer(p, npc, reforjar ? "Traga uma arma ou armadura na mão e eu reforjo a qualidade dela."
                    : "Segure a arma, ferramenta ou armadura que quer aprimorar e fale comigo agachado.", ChatFormatting.WHITE);
            return;
        }
        int slot = p.getInventory().selected;
        String acao = reforjar ? "reforjar" : "aprimorar";
        Pendente pend = PENDENTES.get(p.getUUID());
        boolean confirmou = pend != null && pend.acao().equals(acao) && pend.slot() == slot && System.currentTimeMillis() - pend.quando() < 8000;

        if (reforjar) {
            int r = Armas.raridade(item);
            if (r >= 4) { dizer(p, npc, "Isso já é Lendário. Não há o que melhorar na têmpera.", ChatFormatting.WHITE); return; }
            Custo c = comDesconto(p, npc, custoReforjar(r));
            if (!confirmou) {
                PENDENTES.put(p.getUUID(), new Pendente(acao, slot, System.currentTimeMillis()));
                dizer(p, npc, "Reforjar (" + Raridade.de(r).nome + " → sorteio, nunca piora) custa " + descrever(c)
                        + ". Clique agachado de novo para confirmar.", ChatFormatting.YELLOW);
                return;
            }
            PENDENTES.remove(p.getUUID());
            if (!pagar(p, npc, c)) return;
            int novo = Math.max(r, Armas.sortear(Armas.BAU, p.getRandom()));
            Armas.definirRaridade(item, novo);
            if (novo == 4 && r < 4) br.com.frxcode.frxworld.progressao.Conquistas.somar(p, "reforja_lendaria", 1);
            p.level().playSound(null, npc.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.NEUTRAL, 1f, 1f);
            Raridade nr = Raridade.de(novo);
            dizer(p, npc, novo > r ? "Ficou " + nr.nome + "!" : "Continua " + nr.nome + ". O metal não colaborou desta vez.", novo > r ? nr.cor : ChatFormatting.GRAY);
            return;
        }

        int n = Armas.nivel(item);
        if (n >= Armas.NIVEL_MAX) { dizer(p, npc, "Isso já está no máximo, +" + Armas.NIVEL_MAX + ".", ChatFormatting.WHITE); return; }
        int alvo = n + 1;
        Custo c = comDesconto(p, npc, custoAprimorar(alvo));
        int chance = chance(alvo);
        if (!confirmou) {
            PENDENTES.put(p.getUUID(), new Pendente(acao, slot, System.currentTimeMillis()));
            dizer(p, npc, "Aprimorar para +" + alvo + " custa " + descrever(c) + ". Chance de sucesso: " + chance + "%"
                    + (chance < 100 ? " (se falhar, perde o material mas o item não quebra)" : "") + ". Clique agachado de novo para confirmar.", ChatFormatting.YELLOW);
            return;
        }
        PENDENTES.remove(p.getUUID());
        if (!pagar(p, npc, c)) return;
        if (p.getRandom().nextInt(100) < chance) {
            Armas.definirNivel(item, alvo);
            br.com.frxcode.frxworld.progressao.Conquistas.maximo(p, "forja_max", alvo);
            p.level().playSound(null, npc.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 1f, 1f);
            dizer(p, npc, "Pronto! " + item.getHoverName().getString() + " agora é +" + alvo + ".", ChatFormatting.GREEN);
            if (alvo >= anunciarAPartirDe() && p.getServer() != null)
                p.getServer().getPlayerList().broadcastSystemMessage(Component.literal("⚒ " + p.getName().getString() + " forjou "
                        + item.getHoverName().getString() + " +" + alvo + "!").withStyle(ChatFormatting.GOLD), false);
        } else {
            p.level().playSound(null, npc.blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.NEUTRAL, 1f, 0.8f);
            dizer(p, npc, "A forja falhou... o material se perdeu. Tente de novo.", ChatFormatting.RED);
        }
    }

    /** Desconto da reputação com o reino do ferreiro/armeiro. */
    private static Custo comDesconto(ServerPlayer p, Entity npc, Custo c) {
        double desc = br.com.frxcode.frxworld.reinos.Reputacao.desconto(p, npc.getPersistentData().getString("frx_reino"));
        return desc <= 0 ? c : new Custo(Math.max(1, Math.round(c.moedas() * (1 - desc))), c.material(), c.qtd());
    }

    private static boolean pagar(ServerPlayer p, Entity npc, Custo c) {
        if (contar(p, c.material()) < c.qtd()) {
            dizer(p, npc, "Faltam " + (c.qtd() - contar(p, c.material())) + "× " + new ItemStack(c.material()).getHoverName().getString() + ".", ChatFormatting.RED);
            return false;
        }
        if (Moedas.contar(p) < c.moedas()) {
            dizer(p, npc, "Faltam moedas: " + Moedas.texto(c.moedas() - Moedas.contar(p)) + ".", ChatFormatting.RED);
            return false;
        }
        tirar(p, c.material(), c.qtd());
        Moedas.cobrar(p, c.moedas());
        return true;
    }
}
