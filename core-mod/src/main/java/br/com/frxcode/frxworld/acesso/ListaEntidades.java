package br.com.frxcode.frxworld.acesso;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Ao ligar, manda ao painel a lista de criaturas que existem no modpack (id, categoria, cores do ovo, se é chefe),
 * para os seletores de monstros com imagem (invasões, floresta, chefe mundial...).
 */
public class ListaEntidades {
    public ListaEntidades() {}

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent e) {
        JsonArray lista = new JsonArray();
        for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(t);
            if (id == null) continue;
            SpawnEggItem ovo = ForgeSpawnEggItem.fromEntityType(t);
            if (ovo == null) ovo = SpawnEggItem.byId(t);
            MobCategory cat = t.getCategory();
            boolean chefe = t.is(Tags.EntityTypes.BOSSES);
            if (cat == MobCategory.MISC && ovo == null && !chefe) continue; // flechas, itens, barcos...
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            o.addProperty("categoria", cat.getName());
            o.addProperty("chave", t.getDescriptionId());
            if (chefe) o.addProperty("chefe", true);
            if (ovo != null) {
                o.addProperty("cor1", ovo.getColor(0) & 0xFFFFFF);
                o.addProperty("cor2", ovo.getColor(1) & 0xFFFFFF);
            }
            lista.add(o);
        }
        JsonObject corpo = new JsonObject();
        corpo.add("entidades", lista);
        PainelApi.enviarEntidades(corpo);
        br.com.frxcode.frxworld.FrxWorld.LOG.info("Lista de criaturas enviada ao painel: {}", lista.size());

        // itens do modpack (seletores de item com ícone: missões, lojas, recompensas)
        JsonArray itens = new JsonArray();
        for (net.minecraft.world.item.Item it : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
            if (id == null || it == net.minecraft.world.item.Items.AIR) continue;
            JsonArray x = new JsonArray();
            x.add(id.toString());
            x.add(it.getDescriptionId());
            x.add(it.getMaxStackSize());
            itens.add(x);
        }
        JsonObject ci = new JsonObject();
        ci.add("itens", itens);
        PainelApi.enviarItens(ci);
        br.com.frxcode.frxworld.FrxWorld.LOG.info("Lista de itens enviada ao painel: {}", itens.size());
    }
}
