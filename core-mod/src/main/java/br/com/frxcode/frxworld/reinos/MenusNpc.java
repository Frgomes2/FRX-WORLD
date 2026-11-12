package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.armas.Forja;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.moedas.ItemMoeda;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.network.PacketDistributor;

/** Menus visuais dos NPCs: banqueiro (depositar, sacar, câmbio) e ferreiro/armeiro (forja e loja). */
public final class MenusNpc {
    private MenusNpc() {}

    private static String nome(Entity npc, String padrao) { return npc.getCustomName() != null ? npc.getCustomName().getString() : padrao; }

    private static void enviar(ServerPlayer p, String tipo, CompoundTag d) {
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu(tipo, d));
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

    private static void entregar(ServerPlayer p, ItemStack pilha) {
        if (!p.getInventory().add(pilha)) p.drop(pilha, false);
    }

    // ------------------------------------------------------------------ banco

    public static void abrirBanco(ServerPlayer p, Entity npc, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        d.putInt("npc", npc.getId());
        d.putString("nome", nome(npc, "Banqueiro"));
        d.putLong("saldo", Banco.saldo(p));
        d.putLong("bolsa", Moedas.contar(p));
        d.putInt("ouro", contar(p, Moedas.OURO.get()));
        d.putInt("prata", contar(p, Moedas.PRATA.get()));
        d.putInt("cobre", contar(p, Moedas.COBRE.get()));
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        d.putBoolean("soCambio", "cambista".equals(npc.getPersistentData().getString("frx_papel")));
        enviar(p, "banco", d);
    }

    private static void banco(ServerPlayer p, Entity npc, AcaoMenu a) {
        long v = a.valor();
        String msg; boolean ok = true;
        if ("cambista".equals(npc.getPersistentData().getString("frx_papel")) && a.acao() != AcaoMenu.CAMBIO && a.acao() != AcaoMenu.JUNTAR) return;
        switch (a.acao()) {
            case AcaoMenu.DEPOSITAR -> {
                long bolsa = Moedas.contar(p);
                if (v <= 0 || v > bolsa) v = bolsa;
                if (v <= 0) { msg = "Você não tem moedas na bolsa."; ok = false; }
                else { Banco.depositar(p, v); msg = "Depositado: " + Moedas.texto(v) + "."; }
            }
            case AcaoMenu.SACAR -> {
                long s = Banco.saldo(p);
                if (v <= 0 || v > s) v = s;
                if (v <= 0) { msg = "O cofre está vazio."; ok = false; }
                else { Banco.sacar(p, v); msg = "Sacado: " + Moedas.texto(v) + "."; }
            }
            case AcaoMenu.JUNTAR -> {
                long total = Moedas.contar(p);
                if (total <= 0) { msg = "Você não tem moedas na bolsa."; ok = false; }
                else { Moedas.cobrar(p, 0); msg = "Moedas juntadas nas maiores."; }
            }
            case AcaoMenu.CAMBIO -> {
                int tipo = (int) (v & 0xF), vezes = (int) Math.max(1, Math.min(64, v >> 4));
                Item de, para; int tira, da;
                switch (tipo) {
                    case AcaoMenu.OURO_PRATA -> { de = Moedas.OURO.get(); para = Moedas.PRATA.get(); tira = 1; da = 10; }
                    case AcaoMenu.PRATA_OURO -> { de = Moedas.PRATA.get(); para = Moedas.OURO.get(); tira = 10; da = 1; }
                    case AcaoMenu.PRATA_COBRE -> { de = Moedas.PRATA.get(); para = Moedas.COBRE.get(); tira = 1; da = 10; }
                    default -> { de = Moedas.COBRE.get(); para = Moedas.PRATA.get(); tira = 10; da = 1; }
                }
                vezes = Math.min(vezes, contar(p, de) / tira);
                if (vezes <= 0) { msg = "Faltam moedas para essa troca."; ok = false; }
                else {
                    tirar(p, de, tira * vezes);
                    int total = da * vezes;
                    while (total > 0) { int lote = Math.min(64, total); entregar(p, new ItemStack(para, lote)); total -= lote; }
                    msg = "Trocado: " + (tira * vezes) + "× " + new ItemStack(de).getHoverName().getString() + " por " + (da * vezes) + "× " + new ItemStack(para).getHoverName().getString() + ".";
                }
            }
            default -> msg = "";
        }
        if (ok && !msg.isEmpty()) p.level().playSound(null, npc.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f);
        abrirBanco(p, npc, msg, ok);
    }

    // ------------------------------------------------------------------ forja + loja

    private static long valorMoeda(ItemStack s) { return s.getItem() instanceof ItemMoeda m ? (long) m.valor() * s.getCount() : -1; }

    /** Loja do ferreiro/armeiro: itens do patamar do nível do jogador, já com o desconto da reputação. */
    private static java.util.List<MerchantOffer> ofertasDe(ServerPlayer p, Entity npc) {
        String papel = npc.getPersistentData().getString("frx_papel"), reino = npc.getPersistentData().getString("frx_reino");
        int nivel = br.com.frxcode.frxworld.progressao.Progressao.dados(p).nivel();
        double desc = Reputacao.desconto(p, reino);
        if (papel.equals("cozinheiro")) return Lojas.comida(nivel, desc, p.server, reino + papel);
        return Lojas.forja(papel.equals("armeiro"), nivel, desc, p.server, reino + papel);
    }

    private static boolean temCusto(ServerPlayer p, ItemStack c) {
        if (c.isEmpty()) return true;
        long v = valorMoeda(c);
        return v >= 0 ? Moedas.contar(p) >= v : contar(p, c.getItem()) >= c.getCount();
    }

    public static void abrirForja(ServerPlayer p, Entity npc, String msg, boolean ok) {
        boolean reforjar = "armeiro".equals(npc.getPersistentData().getString("frx_papel"));
        boolean soLoja = "cozinheiro".equals(npc.getPersistentData().getString("frx_papel"));
        CompoundTag d = soLoja ? new CompoundTag() : Forja.infoMenu(p, npc, reforjar);
        d.putBoolean("soLoja", soLoja);
        d.putInt("npc", npc.getId());
        d.putString("nome", nome(npc, soLoja ? "Cozinheiro" : reforjar ? "Armeiro" : "Ferreiro"));
        d.putBoolean("reforjar", reforjar);
        d.putLong("bolsa", Moedas.contar(p));
        d.putInt("slot", p.getInventory().selected);
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        ListTag barra = new ListTag();
        for (int i = 0; i < 9; i++) barra.add(p.getInventory().getItem(i).save(new CompoundTag()));
        d.put("barra", barra);
        ListTag ofertas = new ListTag();
        d.putString("dicaLoja", soLoja ? "O cardápio muda todo dia e melhora com o seu nível." : Lojas.proximoTier(br.com.frxcode.frxworld.progressao.Progressao.dados(p).nivel()));
        {
            for (MerchantOffer o : ofertasDe(p, npc)) {
                CompoundTag x = new CompoundTag();
                ItemStack a = o.getBaseCostA().copy();
                x.put("a", a.save(new CompoundTag()));
                x.put("b", o.getCostB().save(new CompoundTag()));
                x.put("r", o.getResult().save(new CompoundTag()));
                x.putBoolean("fora", o.isOutOfStock());
                x.putBoolean("pode", !o.isOutOfStock() && temCusto(p, a) && temCusto(p, o.getCostB()));
                ofertas.add(x);
            }
        }
        d.put("ofertas", ofertas);
        enviar(p, "forja", d);
    }

    private static void pagarCusto(ServerPlayer p, ItemStack c) {
        if (c.isEmpty()) return;
        long v = valorMoeda(c);
        if (v >= 0) Moedas.cobrar(p, v); else tirar(p, c.getItem(), c.getCount());
    }

    private static void forja(ServerPlayer p, Entity npc, AcaoMenu a) {
        boolean reforjar = "armeiro".equals(npc.getPersistentData().getString("frx_papel"));
        if ("cozinheiro".equals(npc.getPersistentData().getString("frx_papel")) && a.acao() != AcaoMenu.COMPRAR) { abrirForja(p, npc, "", true); return; }
        switch (a.acao()) {
            case AcaoMenu.FORJAR -> {
                String[] r = Forja.executarMenu(p, npc, reforjar);
                abrirForja(p, npc, r[1], r[0].equals("1"));
            }
            case AcaoMenu.SEGURAR -> {
                int slot = (int) a.valor();
                if (slot >= 0 && slot < 9) {
                    p.getInventory().selected = slot;
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot));
                }
                abrirForja(p, npc, "", true);
            }
            case AcaoMenu.COMPRAR -> {
                java.util.List<MerchantOffer> lista = ofertasDe(p, npc);
                int i = (int) (a.valor() & 0xFFFF);
                if (i < 0 || i >= lista.size() || Item.getId(lista.get(i).getResult().getItem()) != (int) (a.valor() >> 16)) {
                    abrirForja(p, npc, "A loja mudou. Veja a lista de novo.", false); return;
                }
                MerchantOffer o = lista.get(i);
                ItemStack ca = o.getBaseCostA().copy();
                if (o.isOutOfStock()) { abrirForja(p, npc, "Esgotado por enquanto. Volte mais tarde.", false); return; }
                if (!temCusto(p, ca) || !temCusto(p, o.getCostB())) { abrirForja(p, npc, "Você não tem o que essa troca pede.", false); return; }
                // moedas dos dois custos somadas numa cobrança só (o troco muda as pilhas)
                long va = valorMoeda(ca), vb = o.getCostB().isEmpty() ? -1 : valorMoeda(o.getCostB());
                if (va >= 0 && vb >= 0) {
                    if (Moedas.contar(p) < va + vb) { abrirForja(p, npc, "Faltam moedas.", false); return; }
                    Moedas.cobrar(p, va + vb);
                } else { pagarCusto(p, ca); pagarCusto(p, o.getCostB()); }
                ItemStack res = o.getResult().copy();
                String nomeItem = res.getHoverName().getString();
                int qtd = res.getCount();
                entregar(p, res);
                p.level().playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
                abrirForja(p, npc, "Comprado: " + (qtd > 1 ? qtd + "× " : "") + nomeItem + ".", true);
            }
            default -> abrirForja(p, npc, "", true);
        }
    }

    // ------------------------------------------------------------------ entrada

    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (npc == null || !npc.getTags().contains(NpcsReino.TAG) || npc.distanceToSqr(p) > 100) return;
        String papel = npc.getPersistentData().getString("frx_papel");
        if (a.tipo().equals("banco") && (papel.equals("banqueiro") || papel.equals("cambista"))) banco(p, npc, a);
        else if (a.tipo().equals("forja") && (papel.equals("ferreiro") || papel.equals("armeiro") || papel.equals("cozinheiro"))) forja(p, npc, a);
    }
}
