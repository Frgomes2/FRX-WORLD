package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Criador de Dragões (NPC de papel "dragoes"): vende ovos de dragão do mod Dragon Mounts: Legacy.
 * São caríssimos de propósito; quanto mais raro o tipo, mais caro. O ovo é o item do próprio mod
 * (dragonmounts:dragon_egg) com o tipo gravado em BlockEntityTag.Breed. Sem o mod instalado, o NPC avisa e não vende.
 */
public class Dragoes {
    public static final int COMPRAR = 0;
    private static final ResourceLocation OVO = new ResourceLocation("dragonmounts", "dragon_egg");

    /** id do tipo no mod, nome, raridade (1 a 5), preço em cobre (100 de cobre = 1 de ouro), onde o ovo choca como este tipo. */
    private record Tipo(String id, String nome, int raridade, long preco, String ninho) {}
    private static final String[] RARIDADES = {"", "Incomum", "Raro", "Épico", "Lendário", "Mítico"};
    private static final Tipo[] TIPOS = {
            new Tipo("forest", "Dragão da Floresta", 1, 150_000, "Faz as plantas crescerem por onde anda."),
            new Tipo("water", "Dragão da Água", 1, 150_000, "Não se afoga: o dragão dos mares."),
            new Tipo("ice", "Dragão de Gelo", 2, 200_000, "Congela a água em volta e deixa neve no chão."),
            new Tipo("fire", "Dragão de Fogo", 2, 250_000, "Imune a fogo e lava."),
            new Tipo("aether", "Dragão do Éter", 3, 400_000, "O mais veloz no voo."),
            new Tipo("nether", "Dragão do Nether", 3, 500_000, "Imune a fogo e lava, com couraça reforçada."),
            new Tipo("ghost", "Dragão Fantasma", 4, 750_000, "Não se afoga; onde pisa, as plantas secam."),
            new Tipo("end", "Dragão do Fim", 5, 1_200_000, "O mais resistente: tem a maior vida."),
    };

    private Dragoes() {}

    private static Item itemOvo() {
        Item i = ForgeRegistries.ITEMS.getValue(OVO);
        return i == null || i == Items.AIR ? null : i;
    }

    private static ItemStack ovo(Item item, Tipo t) {
        ItemStack s = new ItemStack(item);
        CompoundTag bloco = new CompoundTag();
        bloco.putString("Breed", "dragonmounts:" + t.id());
        s.getOrCreateTag().put("BlockEntityTag", bloco);
        return s;
    }

    public static void abrir(ServerPlayer p, Entity npc) { enviar(p, npc.getId(), "", true); }

    private static void enviar(ServerPlayer p, int npcId, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        Entity npc = p.level().getEntity(npcId);
        Item item = itemOvo();
        d.putInt("npc", npcId);
        d.putString("nome", npc != null && npc.getCustomName() != null ? npc.getCustomName().getString() : "Criador de Dragões");
        d.putString("msg", item == null && msg.isEmpty() ? "Os ovos ainda não chegaram. Volte outro dia." : msg);
        d.putBoolean("ok", ok && item != null);
        d.putBoolean("temOvos", item != null);
        d.putLong("bolsa", Moedas.contar(p));
        ListTag tipos = new ListTag();
        for (Tipo t : TIPOS) {
            CompoundTag o = new CompoundTag();
            o.putString("nome", t.nome()); o.putInt("raridade", t.raridade()); o.putString("raridadeNome", RARIDADES[t.raridade()]);
            o.putLong("preco", t.preco()); o.putString("ninho", t.ninho());
            if (item != null) o.put("item", ovo(item, t).save(new CompoundTag()));
            tipos.add(o);
        }
        d.put("tipos", tipos);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("dragoes", d));
    }

    /** A tela manda COMPRAR com valor = posição do tipo na lista. */
    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (npc == null || npc.distanceToSqr(p) > 144 || !"dragoes".equals(npc.getPersistentData().getString("frx_papel")) || a.acao() != COMPRAR) return;
        if (a.valor() < 0 || a.valor() >= TIPOS.length) return;
        Tipo t = TIPOS[(int) a.valor()];
        Item item = itemOvo();
        String msg;
        boolean ok = false;
        if (item == null) msg = "Os ovos ainda não chegaram. Volte outro dia.";
        else if (p.getInventory().getFreeSlot() < 0) msg = "Sua mochila está cheia. Um ovo desses não se põe no chão.";
        else if (!Moedas.cobrar(p, t.preco())) msg = "Faltam moedas: o ovo custa " + Moedas.texto(t.preco()) + ".";
        else {
            ItemStack s = ovo(item, t);
            if (!p.getInventory().add(s)) p.drop(s, false);
            msg = "O ovo do " + t.nome() + " é seu. Cuide bem dele.";
            ok = true;
            br.com.frxcode.frxworld.FrxWorld.LOG.info("Dragoes: {} comprou ovo {} por {}", p.getGameProfile().getName(), t.id(), t.preco());
        }
        p.playNotifySound(ok ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, ok ? 0.5f : 0.8f, 1f);
        enviar(p, a.npc(), msg, ok);
    }
}
