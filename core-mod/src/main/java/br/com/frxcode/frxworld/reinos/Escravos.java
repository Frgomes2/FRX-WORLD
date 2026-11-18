package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Mercador de Escravos (NPC de papel "escravos"): vende aldeões sem profissão a quem tem Rank D ou mais na Guilda.
 * O jogador recebe um contrato (um ovo de aldeão marcado); usar o contrato no chão faz o aldeão aparecer ali.
 * O aldeão é um aldeão comum do jogo: pega profissão ao achar uma bancada de trabalho livre.
 */
public class Escravos {
    public static final int COMPRAR = 0;
    private static final int RANK_MIN = 2;          // D (0 = F ... 6 = S)
    private static final long PRECO = 500;          // 5 de ouro, em cobre
    private static final String MARCA = "frx_aldeao", DONO = "frx_escravo_dono";
    /** Quantos aldeões comprados a conta pode ter ao mesmo tempo, pelo rank da Guilda do personagem (F a S). */
    private static final int[] LIMITE = {0, 0, 4, 8, 16, 34, 68};
    /** Conta → {comprados, perdidos}: <mundo>/frx_escravos.json. Os que a conta tem = comprados − perdidos (contrato ainda não usado conta). */
    private static com.google.gson.JsonObject contas;

    private static java.nio.file.Path arquivo(net.minecraft.server.MinecraftServer s) { return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("frx_escravos.json"); }

    private static com.google.gson.JsonObject contas(net.minecraft.server.MinecraftServer s) {
        if (contas == null) {
            try { contas = java.nio.file.Files.exists(arquivo(s)) ? com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(arquivo(s), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject() : new com.google.gson.JsonObject(); }
            catch (Exception e) { br.com.frxcode.frxworld.FrxWorld.LOG.warn("Escravos: não li frx_escravos.json: {}", e.toString()); contas = new com.google.gson.JsonObject(); }
        }
        return contas;
    }

    private static int numero(net.minecraft.server.MinecraftServer s, String conta, String campo) {
        com.google.gson.JsonObject c = contas(s);
        return c.has(conta) && c.getAsJsonObject(conta).has(campo) ? c.getAsJsonObject(conta).get(campo).getAsInt() : 0;
    }

    private static void somar(net.minecraft.server.MinecraftServer s, String conta, String campo) {
        com.google.gson.JsonObject c = contas(s);
        if (!c.has(conta)) c.add(conta, new com.google.gson.JsonObject());
        c.getAsJsonObject(conta).addProperty(campo, numero(s, conta, campo) + 1);
        try { java.nio.file.Files.writeString(arquivo(s), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(c), java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { br.com.frxcode.frxworld.FrxWorld.LOG.warn("Escravos: não gravei frx_escravos.json: {}", e.toString()); }
    }

    /** Quantos aldeões comprados a conta tem agora (vivos ou ainda no contrato). */
    private static int tem(ServerPlayer p) { return Math.max(0, numero(p.server, p.getUUID().toString(), "comprados") - numero(p.server, p.getUUID().toString(), "perdidos")); }
    private static int limite(ServerPlayer p) { return Guilda.registrado(p) ? LIMITE[Math.max(0, Math.min(6, Guilda.indice(p)))] : 0; }
    /** Tipo do jogo (a roupa muda com o bioma) → nome mostrado. */
    private static final String[][] TIPOS = {
            {"plains", "Planície"}, {"desert", "Deserto"}, {"savanna", "Savana"}, {"taiga", "Taiga"},
            {"snow", "Neve"}, {"jungle", "Selva"}, {"swamp", "Pântano"}};

    public Escravos() {}

    private static Component linha(String t, ChatFormatting cor) { return Component.literal(t).withStyle(st -> st.withItalic(false).withColor(cor)); }

    private static ItemStack contrato(int tipo, ServerPlayer dono) {
        ItemStack s = new ItemStack(Items.VILLAGER_SPAWN_EGG);
        CompoundTag t = s.getOrCreateTag();
        t.putBoolean(MARCA, true);
        CompoundTag dados = new CompoundTag(), entidade = new CompoundTag();
        dados.putString("type", "minecraft:" + TIPOS[tipo][0]);
        dados.putString("profession", "minecraft:none");
        dados.putInt("level", 1);
        entidade.put("VillagerData", dados);
        CompoundTag forge = new CompoundTag();              // o aldeão nasce sabendo de quem é: quando morre, a vaga volta
        forge.putString(DONO, dono.getUUID().toString());
        entidade.put("ForgeData", forge);
        t.put("EntityTag", entidade);
        s.setHoverName(linha("Contrato de Escravo · " + TIPOS[tipo][1], ChatFormatting.GOLD));
        ListTag l = new ListTag();
        l.add(StringTag.valueOf(Component.Serializer.toJson(linha("Use no chão para trazer o aldeão.", ChatFormatting.GRAY))));
        l.add(StringTag.valueOf(Component.Serializer.toJson(linha("Sem profissão: ponha uma bancada de trabalho por perto.", ChatFormatting.DARK_GRAY))));
        s.getOrCreateTagElement("display").put("Lore", l);
        return s;
    }

    public static void abrir(ServerPlayer p, Entity npc) { enviar(p, npc.getId(), "", true); }

    private static void enviar(ServerPlayer p, int npcId, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        Entity npc = p.level().getEntity(npcId);
        d.putInt("npc", npcId);
        d.putString("nome", npc != null && npc.getCustomName() != null ? npc.getCustomName().getString() : "Mercador de Escravos");
        d.putString("msg", msg); d.putBoolean("ok", ok);
        d.putLong("bolsa", Moedas.contar(p));
        d.putLong("preco", PRECO);
        d.putString("rank", ConfigJogo.nomeRank(RANK_MIN));
        d.putBoolean("podeRank", Guilda.registrado(p) && Guilda.indice(p) >= RANK_MIN);
        d.putInt("tem", tem(p));
        d.putInt("limite", limite(p));
        ListTag tipos = new ListTag();
        for (String[] t : TIPOS) {
            CompoundTag o = new CompoundTag();
            o.putString("tipo", "minecraft:" + t[0]); o.putString("nome", t[1]);
            tipos.add(o);
        }
        d.put("tipos", tipos);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("escravos", d));
    }

    /** A tela manda COMPRAR com valor = posição do tipo na lista. */
    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (npc == null || npc.distanceToSqr(p) > 144 || !"escravos".equals(npc.getPersistentData().getString("frx_papel")) || a.acao() != COMPRAR) return;
        String msg;
        boolean ok = false;
        if (a.valor() < 0 || a.valor() >= TIPOS.length) return;
        if (!Guilda.registrado(p) || Guilda.indice(p) < RANK_MIN) msg = "Só vendo a aventureiros de Rank " + ConfigJogo.nomeRank(RANK_MIN) + " ou mais na Guilda.";
        else if (tem(p) >= limite(p)) msg = "Você já tem " + tem(p) + " de " + limite(p) + ", o máximo do Rank " + Guilda.rank(p) + ". Suba de rank na Guilda para ter mais.";
        else if (!Moedas.cobrar(p, PRECO)) msg = "Faltam moedas: custa " + Moedas.texto(PRECO) + ".";
        else {
            ItemStack s = contrato((int) a.valor(), p);
            somar(p.server, p.getUUID().toString(), "comprados");
            if (!p.getInventory().add(s)) p.drop(s, false);
            msg = "Negócio fechado. Use o contrato no chão, onde quer o aldeão.";
            ok = true;
        }
        p.playNotifySound(ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, ok ? 1.2f : 1f);
        enviar(p, a.npc(), msg, ok);
    }

    /** Aldeão comprado que morre (ou vira zumbi) devolve a vaga ao dono. */
    private static void perdeu(net.minecraft.world.entity.Entity e) {
        if (e.level().isClientSide() || e.getServer() == null) return;
        String dono = e.getPersistentData().getString(DONO);
        if (dono.isEmpty()) return;
        e.getPersistentData().remove(DONO);
        somar(e.getServer(), dono, "perdidos");
    }

    @SubscribeEvent
    public void aoMorrer(net.minecraftforge.event.entity.living.LivingDeathEvent ev) { perdeu(ev.getEntity()); }

    @SubscribeEvent
    public void aoVirarOutro(net.minecraftforge.event.entity.living.LivingConversionEvent.Post ev) { perdeu(ev.getEntity()); }

    /** O contrato não troca o bicho de um gerador de monstros (senão um contrato só viraria aldeões sem fim). */
    @SubscribeEvent
    public void aoClicarNoBloco(PlayerInteractEvent.RightClickBlock ev) {
        ItemStack s = ev.getItemStack();
        if (!s.is(Items.VILLAGER_SPAWN_EGG) || !s.hasTag() || !s.getTag().getBoolean(MARCA)) return;
        if (!ev.getLevel().getBlockState(ev.getPos()).is(Blocks.SPAWNER)) return;
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.FAIL);
        if (ev.getEntity() instanceof ServerPlayer p) p.displayClientMessage(Component.literal("O contrato só serve no chão.").withStyle(ChatFormatting.RED), true);
    }
}
