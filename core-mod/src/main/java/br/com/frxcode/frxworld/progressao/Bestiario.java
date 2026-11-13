package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.FrxWorld;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bestiário: capítulo do livro de Quests com uma página por tipo de monstro do servidor (de todos os mods).
 * A página se completa quando o jogador mata aquele monstro pela primeira vez e mostra a ameaça,
 * as fraquezas e onde ele vive. O capítulo é refeito ao ligar o servidor, então acompanha os mods instalados.
 */
public class Bestiario {
    private static final String[] RANK = {"F", "E", "D", "C", "B", "A", "S"};

    private record Ficha(ResourceLocation id, EntityType<?> tipo, double vida, double ataque, String fraquezas, String onde, String mod) {
        int ameaca() {
            double f = vida + ataque * 6;
            return f < 30 ? 0 : f < 50 ? 1 : f < 80 ? 2 : f < 130 ? 3 : f < 220 ? 4 : f < 400 ? 5 : 6;
        }
    }

    public Bestiario() {}

    private static String hex(String semente) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(semente.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02X", h[i]));
            if (sb.charAt(0) > '7') sb.setCharAt(0, (char) ('0' + (sb.charAt(0) > '9' ? sb.charAt(0) - 'A' + 2 : sb.charAt(0) - '8')));   // id positivo
            return sb.toString();
        } catch (Exception e) {
            return String.format("%016X", semente.hashCode() & 0x7FFFFFFFL);
        }
    }

    private static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    private static String bonito(String caminho) {
        StringBuilder sb = new StringBuilder();
        for (String parte : caminho.split("[_/]")) if (!parte.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(parte.charAt(0))).append(parte.substring(1));
        return sb.toString();
    }

    private static String nomeMod(String ns) {
        if (ns.equals("minecraft")) return "Minecraft";
        return ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(bonito(ns));
    }

    private static String fraquezas(MinecraftServer s, EntityType<?> tipo) {
        List<String> l = new ArrayList<>();
        try {
            Entity e = tipo.create(s.overworld());
            if (e instanceof LivingEntity v) {
                MobType m = v.getMobType();
                if (m == MobType.UNDEAD) { l.add("encantamento Julgamento (Smite)"); l.add("poções de cura ferem, veneno não"); }
                else if (m == MobType.ARTHROPOD) l.add("encantamento Ruína dos Artrópodes");
                else if (m == MobType.WATER) l.add("encantamento Empalamento (tridente)");
                if (v.isSensitiveToWater()) l.add("água e chuva");
            }
            if (e != null) e.discard();
        } catch (Throwable ignorado) { }
        l.add(tipo.fireImmune() ? "imune a fogo" : "fogo");
        return String.join(", ", l);
    }

    private static List<Ficha> fichas(MinecraftServer s) {
        // onde cada tipo nasce: biomas que o têm na lista de monstros
        Map<EntityType<?>, List<String>> biomas = new HashMap<>();
        try {
            for (Map.Entry<ResourceKey<Biome>, Biome> b : s.registryAccess().registryOrThrow(Registries.BIOME).entrySet()) {
                for (MobCategory cat : new MobCategory[]{MobCategory.MONSTER, MobCategory.WATER_CREATURE, MobCategory.UNDERGROUND_WATER_CREATURE})
                    for (MobSpawnSettings.SpawnerData d : b.getValue().getMobSettings().getMobs(cat).unwrap()) {
                        List<String> l = biomas.computeIfAbsent(d.type, k -> new ArrayList<>());
                        String nome = bonito(b.getKey().location().getPath());
                        if (!l.contains(nome)) l.add(nome);
                    }
            }
        } catch (Throwable t) {
            FrxWorld.LOG.warn("Bestiário: não consegui ler os biomas: {}", t.toString());
        }

        List<Ficha> l = new ArrayList<>();
        for (Map.Entry<ResourceKey<EntityType<?>>, EntityType<?>> x : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            EntityType<?> tipo = x.getValue();
            ResourceLocation id = x.getKey().location();
            if (tipo.getCategory() != MobCategory.MONSTER || id.getNamespace().equals("frxworld")) continue;
            try {
                @SuppressWarnings("unchecked")
                EntityType<? extends LivingEntity> vivo = (EntityType<? extends LivingEntity>) tipo;
                if (!DefaultAttributes.hasSupplier(tipo)) continue;
                AttributeSupplier a = DefaultAttributes.getSupplier(vivo);
                double vida = a.hasAttribute(Attributes.MAX_HEALTH) ? a.getBaseValue(Attributes.MAX_HEALTH) : 20;
                double ataque = a.hasAttribute(Attributes.ATTACK_DAMAGE) ? a.getBaseValue(Attributes.ATTACK_DAMAGE) : 0;
                List<String> b = biomas.getOrDefault(tipo, List.of());
                String onde = b.isEmpty() ? "não nasce solto na natureza: procure em estruturas, masmorras, invasões e eventos"
                        : String.join(", ", b.subList(0, Math.min(5, b.size()))) + (b.size() > 5 ? " e mais " + (b.size() - 5) + " biomas" : "");
                l.add(new Ficha(id, tipo, vida, ataque, fraquezas(s, tipo), onde, nomeMod(id.getNamespace())));
            } catch (Throwable ignorado) { }
        }
        l.sort(Comparator.comparing((Ficha f) -> !f.id().getNamespace().equals("minecraft")).thenComparing(f -> f.mod())
                .thenComparingDouble(Ficha::vida).thenComparing(f -> f.id().toString()));
        return l;
    }

    private static String capitulo(List<Ficha> fichas) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n\tdefault_hide_dependency_lines: true\n\tdefault_quest_shape: \"hexagon\"\n\tfilename: \"bestiario\"\n\tgroup: \"\"\n")
                .append("\ticon: \"minecraft:zombie_head\"\n\tid: \"").append(hex("frxworld:bestiario")).append("\"\n\torder_index: 12\n\tquest_links: [ ]\n\tquests: [\n");
        String modAtual = null;
        int col = 0;
        double y = 0;
        Map<String, Integer> usados = new LinkedHashMap<>();
        for (Ficha f : fichas) {
            if (!f.mod().equals(modAtual)) {
                if (modAtual != null) y += 2.0;
                modAtual = f.mod();
                col = 0;
            } else if (col >= 12) { col = 0; y += 1.0; }
            String chave = f.id().toString();
            if (usados.put(chave, 1) != null) continue;
            int xp = (int) Math.max(20, Math.min(500, Math.round(f.vida() * 2)));
            Item ovo = ForgeSpawnEggItem.fromEntityType(f.tipo());
            ResourceLocation ovoId = ovo == null ? null : ForgeRegistries.ITEMS.getKey(ovo);
            sb.append("\t\t{\n\t\t\tdescription: [\n")
                    .append("\t\t\t\t").append(q("Ameaça: Rank " + RANK[f.ameaca()] + " · vida base " + (int) Math.round(f.vida()) + " · ataque base " + (int) Math.round(f.ataque()))).append("\n")
                    .append("\t\t\t\t").append(q("O nível de cada um muda com a zona onde ele aparece (veja acima da cabeça dele).")).append("\n")
                    .append("\t\t\t\t\"\"\n")
                    .append("\t\t\t\t").append(q("Fraquezas: " + f.fraquezas())).append("\n")
                    .append("\t\t\t\t").append(q("Onde vive: " + f.onde())).append("\n")
                    .append("\t\t\t\t\"\"\n")
                    .append("\t\t\t\t").append(q("Mate um para registrar a página.")).append("\n")
                    .append("\t\t\t]\n");
            if (ovoId != null) sb.append("\t\t\ticon: ").append(q(ovoId.toString())).append("\n");
            sb.append("\t\t\tid: \"").append(hex("frxworld:bestiario:" + chave)).append("\"\n")
                    .append("\t\t\trewards: [{\n\t\t\t\tcommand: \"/frx xp {p} ").append(xp).append("\"\n\t\t\t\televate_perms: true\n\t\t\t\tid: \"")
                    .append(hex("frxworld:bestiario:premio:" + chave)).append("\"\n\t\t\t\tsilent: true\n\t\t\t\ttitle: \"").append(xp).append(" XP\"\n\t\t\t\ttype: \"command\"\n\t\t\t}]\n")
                    .append("\t\t\tsubtitle: ").append(q(f.mod() + " · Rank " + RANK[f.ameaca()])).append("\n")
                    .append("\t\t\ttasks: [{\n\t\t\t\tentity: ").append(q(chave)).append("\n\t\t\t\tid: \"").append(hex("frxworld:bestiario:tarefa:" + chave))
                    .append("\"\n\t\t\t\ttype: \"kill\"\n\t\t\t\tvalue: 1L\n\t\t\t}]\n")
                    .append("\t\t\ttitle: ").append(q("{" + f.tipo().getDescriptionId() + "}")).append("\n")
                    .append("\t\t\tx: ").append(col * 1.0).append("d\n\t\t\ty: ").append(y).append("d\n\t\t}\n");
            col++;
        }
        sb.append("\t]\n\tsubtitle: [").append(q("Uma página por monstro. Mate cada tipo para registrar.")).append("]\n\ttitle: \"Bestiário\"\n}\n");
        return sb.toString();
    }

    @SubscribeEvent
    public void aoLigar(ServerStartedEvent ev) {
        MinecraftServer s = ev.getServer();
        try {
            Path arq = FMLPaths.GAMEDIR.get().resolve("config/ftbquests/quests/chapters/bestiario.snbt");
            if (!Files.isDirectory(arq.getParent())) return;
            List<Ficha> fichas = fichas(s);
            if (fichas.isEmpty()) return;
            String novo = capitulo(fichas);
            String atual = Files.exists(arq) ? Files.readString(arq, StandardCharsets.UTF_8) : "";
            if (novo.equals(atual)) return;
            Files.writeString(arq, novo, StandardCharsets.UTF_8);
            FrxWorld.LOG.info("Bestiário: capítulo refeito com {} monstros.", fichas.size());
            try {
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), "ftbquests reload");
            } catch (Throwable t) {
                FrxWorld.LOG.info("Bestiário: o livro de Quests carrega o capítulo novo no próximo reinício.");
            }
        } catch (Throwable t) {
            FrxWorld.LOG.warn("Bestiário: não consegui gerar o capítulo: {}", t.toString());
        }
    }
}
