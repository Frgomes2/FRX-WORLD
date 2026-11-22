package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.aventura.Chefes;
import br.com.frxcode.frxworld.moedas.Moedas;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Coleção do aventureiro: cada tipo de criatura hostil derrotado pela primeira vez e cada chefão diferente entram na lista.
 * Marcos da coleção pagam moedas e XP na hora; os títulos vêm das Conquistas (contadores "colecao" e "colecao_chefes").
 */
public class Colecao {
    private static final String CHAVE = "frx_colecao";
    /** tipos de criatura → prêmio em cobre. */
    private static final int[][] MARCOS = {{10, 100}, {25, 300}, {50, 800}, {100, 2000}, {150, 3500}, {200, 5000}};
    /** chefões diferentes → prêmio em cobre. */
    private static final int[][] MARCOS_CHEFES = {{3, 300}, {7, 1000}, {14, 3000}};
    private static int totalMonstros = -1;

    public Colecao() {}

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    private static ListTag lista(Player p, String nome) {
        CompoundTag d = dados(p);
        if (!d.contains(nome, Tag.TAG_LIST)) d.put(nome, new ListTag());
        return d.getList(nome, Tag.TAG_STRING);
    }

    private static boolean tem(ListTag l, String v) {
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(v)) return true;
        return false;
    }

    public static int criaturas(Player p) { return lista(p, "criaturas").size(); }
    public static int chefes(Player p) { return lista(p, "chefes").size(); }

    /** Quantos tipos de monstro existem no servidor (de todos os mods). */
    public static int totalMonstros() {
        if (totalMonstros < 0) {
            int n = 0;
            for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES) if (t.getCategory() == MobCategory.MONSTER) n++;
            totalMonstros = n;
        }
        return totalMonstros;
    }

    private static int proximo(int[][] marcos, int tenho) {
        for (int[] m : marcos) if (m[0] > tenho) return m[0];
        return 0;
    }

    private static void marco(ServerPlayer p, int[][] marcos, int tenho, String oQue) {
        for (int[] m : marcos) {
            if (m[0] != tenho) continue;
            long xp = Math.max(20, Math.round(Progressao.xpParaProximo(Progressao.dados(p).nivel()) * 0.25));
            Moedas.dar(p, m[1]);
            Progressao.ganharXp(p, xp);
            Sistema.avisar(p, "Marco da coleção", tenho + " " + oQue + ". Recompensa: " + Moedas.texto(m[1]) + " e " + xp + " XP.", Sistema.ROXO);
        }
    }

    private static void registrarCriatura(ServerPlayer p, LivingEntity morto) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(morto.getType());
        if (id == null) return;
        ListTag l = lista(p, "criaturas");
        if (tem(l, id.toString())) return;
        l.add(StringTag.valueOf(id.toString()));
        int n = l.size(), prox = proximo(MARCOS, n);
        p.displayClientMessage(Component.literal("Coleção: " + morto.getType().getDescription().getString() + " — " + n + " tipos" + (prox > 0 ? " (próximo marco: " + prox + ")" : "")).withStyle(ChatFormatting.AQUA), true);
        Conquistas.maximo(p, "colecao", n);
        marco(p, MARCOS, n, "tipos de criatura derrotados");
    }

    public static void registrarChefe(ServerPlayer p, String nome) {
        ListTag l = lista(p, "chefes");
        if (nome.isEmpty() || tem(l, nome)) return;
        l.add(StringTag.valueOf(nome));
        Sistema.avisar(p, "Novo chefão na coleção", nome + " · " + l.size() + " chefões diferentes derrotados.", Sistema.ROXO);
        Conquistas.maximo(p, "colecao_chefes", l.size());
        marco(p, MARCOS_CHEFES, l.size(), "chefões diferentes derrotados");
    }

    @SubscribeEvent
    public void aoMorrer(LivingDeathEvent e) {
        LivingEntity morto = e.getEntity();
        if (morto instanceof Player || !(morto.level() instanceof ServerLevel n)) return;
        // chefão: os do servidor, os marcados como chefe pelo jogo (forge:bosses) e qualquer criatura com 200+ de vida
        // (chefes de outros mods, como o Ignis do Cataclysm). Vale para todos que estavam na luta.
        boolean chefao = Chefes.ehChefao(morto) || morto.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES) || morto.getMaxHealth() >= 200;
        if (chefao) {
            String nome = morto.getName().getString().replace("☠", "").trim();
            for (ServerPlayer p : n.players()) if (p.isAlive() && !p.isSpectator() && p.distanceTo(morto) <= 40 && Progressao.temDados(p)) {
                registrarChefe(p, nome);
                br.com.frxcode.frxworld.missoes.Contratos.progresso(p, "chefe", 1);
            }
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !Progressao.temDados(p)) return;
        if (morto instanceof Enemy || morto.getType().getCategory() == MobCategory.MONSTER) registrarCriatura(p, morto);
    }

    /** Item-resumo para o quadro de contratos. */
    public static ItemStack icone(ServerPlayer p) {
        int c = criaturas(p), ch = chefes(p), pc = proximo(MARCOS, c), pch = proximo(MARCOS_CHEFES, ch);
        ItemStack s = new ItemStack(Items.KNOWLEDGE_BOOK);
        s.setHoverName(Component.literal("Sua coleção").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.AQUA)));
        List<String> linhas = new ArrayList<>();
        linhas.add("Criaturas derrotadas: " + c + " de " + totalMonstros() + " tipos");
        linhas.add(pc > 0 ? "Próximo marco: " + pc + " tipos" : "Todos os marcos de criaturas alcançados");
        linhas.add("Chefões diferentes: " + ch);
        linhas.add(pch > 0 ? "Próximo marco: " + pch + " chefões" : "Todos os marcos de chefões alcançados");
        linhas.add("Cada marco paga moedas e XP; alguns dão título");
        ListTag l = new ListTag();
        for (String t : linhas) l.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(t).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)))));
        s.getOrCreateTagElement("display").put("Lore", l);
        return s;
    }

    /** Para a ficha do jogador no painel e no site. */
    public static JsonObject json(ServerPlayer p) {
        JsonObject o = new JsonObject();
        o.addProperty("criaturas", criaturas(p));
        o.addProperty("totalCriaturas", totalMonstros());
        o.addProperty("proximoMarco", proximo(MARCOS, criaturas(p)));
        JsonArray ch = new JsonArray();
        ListTag l = lista(p, "chefes");
        for (int i = 0; i < l.size(); i++) ch.add(l.getString(i));
        o.add("chefes", ch);
        return o;
    }

    /** Admin: /frx colecao chefe <jogador> <nome>, para registrar um chefão derrotado antes de ele contar na coleção. */
    @SubscribeEvent
    public void comandos(net.minecraftforge.event.RegisterCommandsEvent ev) {
        ev.getDispatcher().register(net.minecraft.commands.Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("colecao").then(net.minecraft.commands.Commands.literal("chefe")
                        .then(net.minecraft.commands.Commands.argument("jogador", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(net.minecraft.commands.Commands.argument("nome", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer alvo = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "jogador");
                                            String nome = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "nome").trim();
                                            registrarChefe(alvo, nome);
                                            Progressao.enviarAoPainel(alvo, false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("[coleção] " + nome + " registrado para " + alvo.getGameProfile().getName() + "."), true);
                                            return 1;
                                        }))))));
    }
}
