package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Contrabandista (NPC de papel "contrabandista", colocado fora das capitais): o outro lado da política dos reinos.
 * Dá três serviços por dia contra os reinos (entregar carga desviada, infiltrar-se na capital, derrubar guardas),
 * que pagam moedas, tiram reputação do reino alvo e agradam o rival dele. A mercadoria abre conforme a má fama:
 * Malvisto, Hostil ou Inimigo de algum reino.
 */
public final class Contrabando {
    public static final int SERVICO = 0, COMPRAR = 1;
    private static final String CHAVE = "frx_contrabando";
    private static final int ENTREGA = 0, INFILTRAR = 1, GUARDAS = 2;
    private static final Item[] CARGA = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.LEATHER, Items.GUNPOWDER};
    private static final String[] CARGA_NOME = {"lingotes de ferro", "lingotes de ouro", "diamantes", "couros", "pólvoras"};
    private static final int[] CARGA_QTD = {24, 12, 6, 32, 24};
    private static final int[] PERDA = {40, 60, 100};
    private static final String[] GRAUS = {"qualquer um", "Malvisto", "Hostil", "Inimigo"};

    /** grau = má fama mínima com algum reino (1 Malvisto, 2 Hostil, 3 Inimigo). */
    private record Mercadoria(int grau, long preco, String nota, Supplier<ItemStack> item) {}
    private static final Mercadoria[] LOJA = {
            new Mercadoria(1, 400, "Para sumir de vista por um instante.", () -> new ItemStack(Items.ENDER_PEARL, 8)),
            new Mercadoria(1, 600, "Oito minutos sem ninguém ver você.", () -> PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LONG_INVISIBILITY)),
            new Mercadoria(1, 350, "Para descer muralhas sem barulho.", () -> PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LONG_SLOW_FALLING)),
            new Mercadoria(2, 400, "Mais barato que na loja do reino.", () -> new ItemStack(Items.TOTEM_OF_UNDYING)),
            new Mercadoria(2, 700, "Não pergunte de onde veio.", () -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)),
            new Mercadoria(2, 6000, "Lâmina Épica +3. Leve, rápida e sem brasão.", () -> arma(Items.NETHERITE_SWORD, "Adaga do Contrabandista", 3, 3)),
            new Mercadoria(3, 2500, "Asas para quem não pode usar a estrada.", () -> new ItemStack(Items.ELYTRA)),
            new Mercadoria(3, 10000, "Lendária +5. Feita para quem os reis temem.", () -> arma(Items.NETHERITE_SWORD, "Lâmina do Renegado", 4, 5)),
            new Mercadoria(3, 10000, "Lendária +5. Nenhum guarda atravessa.", () -> arma(Items.NETHERITE_CHESTPLATE, "Couraça do Renegado", 4, 5)),
    };

    private Contrabando() {}

    private static ItemStack arma(Item item, String nome, int raridade, int nivel) {
        ItemStack s = new ItemStack(item);
        br.com.frxcode.frxworld.armas.Armas.definirRaridade(s, raridade);
        br.com.frxcode.frxworld.armas.Armas.definirNivel(s, nivel);
        s.setHoverName(net.minecraft.network.chat.Component.literal(nome));
        return s;
    }

    private static long hoje() { return (System.currentTimeMillis() - 3 * 3_600_000L) / 86_400_000L; }

    private static CompoundTag pers(ServerPlayer p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Os três serviços de hoje (cria na primeira consulta do dia). */
    private static ListTag servicos(ServerPlayer p) {
        CompoundTag pers = pers(p);
        CompoundTag d = pers.getCompound(CHAVE);
        if (pers.contains(CHAVE) && d.getLong("dia") == hoje()) return d.getList("lista", Tag.TAG_COMPOUND);
        d = new CompoundTag();
        d.putLong("dia", hoje());
        Random rnd = new Random(p.getUUID().getLeastSignificantBits() ^ (hoje() * 31L));
        List<Reino> reinos = new ArrayList<>(Reinos.todos());
        ListTag l = new ListTag();
        for (int tipo = 0; tipo < 3 && !reinos.isEmpty(); tipo++) {
            Reino r = reinos.remove(rnd.nextInt(reinos.size()));
            CompoundTag c = new CompoundTag();
            c.putInt("tipo", tipo);
            c.putString("reino", r.id());
            int carga = rnd.nextInt(CARGA.length);
            c.putInt("carga", carga);
            c.putInt("meta", tipo == ENTREGA ? CARGA_QTD[carga] : tipo == GUARDAS ? 2 : 1);
            l.add(c);
        }
        d.put("lista", l);
        pers.put(CHAVE, d);
        return l;
    }

    private static boolean temHoje(ServerPlayer p) {
        CompoundTag raiz = p.getPersistentData();
        return raiz.contains(Player.PERSISTED_NBT_TAG) && raiz.getCompound(Player.PERSISTED_NBT_TAG).contains(CHAVE)
                && raiz.getCompound(Player.PERSISTED_NBT_TAG).getCompound(CHAVE).getLong("dia") == hoje();
    }

    private static void avancar(ServerPlayer p, int tipo, String reino) {
        if (!temHoje(p)) return;
        ListTag l = servicos(p);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            if (c.getInt("tipo") != tipo || !c.getString("reino").equals(reino) || c.getBoolean("pago") || c.getInt("prog") >= c.getInt("meta")) continue;
            c.putInt("prog", c.getInt("prog") + 1);
            if (c.getInt("prog") >= c.getInt("meta"))
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("☠ Serviço feito. O Contrabandista espera você com o pagamento.").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        }
    }

    /** O jogador chegou ao coração de uma capital. */
    static void infiltrou(ServerPlayer p, String reino) { avancar(p, INFILTRAR, reino); }
    /** O jogador derrubou um guarda do reino. */
    static void guardaMorto(ServerPlayer p, String reino) { avancar(p, GUARDAS, reino); }

    private static long premio(ServerPlayer p, int tipo) {
        int nivel = Progressao.temDados(p) ? Progressao.dados(p).nivel() : 1;
        return tipo == ENTREGA ? 800 + nivel * 30L : tipo == INFILTRAR ? 1000 + nivel * 40L : 1500 + nivel * 50L;
    }

    private static String nomeReino(String id) { Reino r = Reputacao.reino(id); return r != null ? Reputacao.curto(r.nome()) : id; }

    private static int contar(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void tirar(ServerPlayer p, Item item, int quanto) {
        for (ItemStack s : p.getInventory().items) {
            if (quanto <= 0) break;
            if (!s.is(item)) continue;
            int n = Math.min(quanto, s.getCount());
            s.shrink(n);
            quanto -= n;
        }
    }

    public static void abrir(ServerPlayer p, Entity npc) { enviar(p, npc.getId(), "", true); }

    private static void enviar(ServerPlayer p, int npcId, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        int grau = Reputacao.piorHostilidade(p);
        d.putInt("npc", npcId);
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        d.putLong("bolsa", Moedas.contar(p));
        d.putInt("grau", grau);
        d.putString("fama", grau == 0 ? "Sem má fama" : GRAUS[grau] + " de algum reino");
        ListTag ss = new ListTag();
        for (Tag t : servicos(p)) {
            CompoundTag c = (CompoundTag) t, o = new CompoundTag();
            int tipo = c.getInt("tipo"), meta = c.getInt("meta");
            String reino = nomeReino(c.getString("reino")), rival = Reputacao.rival(c.getString("reino"));
            int prog = tipo == ENTREGA && !c.getBoolean("pago") ? Math.min(meta, contar(p, CARGA[c.getInt("carga")])) : c.getInt("prog");
            o.putString("titulo", tipo == ENTREGA ? "Carga desviada" : tipo == INFILTRAR ? "Olhos na capital" : "Patrulha a menos");
            o.putString("texto", tipo == ENTREGA ? "Traga " + meta + " " + CARGA_NOME[c.getInt("carga")] + " que iam para " + reino + "."
                    : tipo == INFILTRAR ? "Chegue ao coração da capital de " + reino + " e volte."
                    : "Derrube " + meta + " guardas de " + reino + ".");
            o.putString("efeito", "−" + PERDA[tipo] + " com " + reino + (rival != null ? " · +" + PERDA[tipo] / 2 + " com " + nomeReino(rival) : ""));
            o.putLong("premio", premio(p, tipo));
            o.putInt("prog", prog);
            o.putInt("meta", meta);
            o.putInt("estado", c.getBoolean("pago") ? 2 : prog >= meta ? 1 : 0);
            ss.add(o);
        }
        d.put("servicos", ss);
        ListTag ms = new ListTag();
        for (Mercadoria m : LOJA) {
            CompoundTag o = new CompoundTag();
            ItemStack s = m.item().get();
            o.put("item", s.save(new CompoundTag()));
            o.putString("nome", s.getHoverName().getString() + (s.getCount() > 1 ? " ×" + s.getCount() : ""));
            o.putString("nota", m.nota());
            o.putLong("preco", m.preco());
            o.putInt("grau", m.grau());
            o.putString("grauNome", GRAUS[m.grau()]);
            ms.add(o);
        }
        d.put("loja", ms);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("contrabando", d));
    }

    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (npc == null || npc.distanceToSqr(p) > 144 || !"contrabandista".equals(npc.getPersistentData().getString("frx_papel"))) return;
        String msg = "";
        boolean ok = false;
        int i = (int) a.valor();
        if (a.acao() == SERVICO) {
            ListTag l = servicos(p);
            if (i < 0 || i >= l.size()) return;
            CompoundTag c = l.getCompound(i);
            int tipo = c.getInt("tipo"), meta = c.getInt("meta");
            String reino = c.getString("reino"), rival = Reputacao.rival(reino);
            if (c.getBoolean("pago")) msg = "Esse já está pago. Amanhã tem mais.";
            else if (tipo == ENTREGA && contar(p, CARGA[c.getInt("carga")]) < meta) msg = "Falta carga. Eu disse " + meta + " " + CARGA_NOME[c.getInt("carga")] + ".";
            else if (tipo != ENTREGA && c.getInt("prog") < meta) msg = "Ainda não está feito. Volte quando terminar.";
            else {
                if (tipo == ENTREGA) tirar(p, CARGA[c.getInt("carga")], meta);
                c.putBoolean("pago", true);
                Moedas.dar(p, premio(p, tipo));
                Reputacao.perder(p, reino, PERDA[tipo]);
                if (rival != null) Reputacao.ganhar(p, rival, PERDA[tipo] / 2, true, false);
                msg = "Bom trabalho. Aqui está a sua parte.";
                ok = true;
            }
        } else if (a.acao() == COMPRAR) {
            if (i < 0 || i >= LOJA.length) return;
            Mercadoria m = LOJA[i];
            if (Reputacao.piorHostilidade(p) < m.grau()) msg = "Isso eu só vendo a quem já é " + GRAUS[m.grau()] + " de algum reino.";
            else if (p.getInventory().getFreeSlot() < 0) msg = "Sua mochila está cheia.";
            else if (!Moedas.cobrar(p, m.preco())) msg = "Faltam moedas: custa " + Moedas.texto(m.preco()) + ".";
            else {
                ItemStack s = m.item().get();
                if (!p.getInventory().add(s)) p.drop(s, false);
                msg = "Negócio fechado. Você nunca me viu.";
                ok = true;
            }
        } else return;
        p.playNotifySound(ok ? SoundEvents.CHAIN_PLACE : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, ok ? 0.7f : 1f);
        enviar(p, a.npc(), msg, ok);
    }
}
