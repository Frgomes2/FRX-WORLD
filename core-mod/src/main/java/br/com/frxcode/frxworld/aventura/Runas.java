package br.com.frxcode.frxworld.aventura;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Zonas;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runas: pedras (fragmento de eco com a marca frx_runa) que se encaixam em armas e armaduras na bigorna.
 * Cada item aceita runas de tipos diferentes conforme o rank de quem encaixa (2 até D, 3 C, 4 B, 5 A e S); duas runas iguais na bigorna viram uma de nível maior (I a III).
 * As de arma agem ao bater; as de armadura somam os níveis de todas as peças vestidas.
 */
public class Runas {
    private static final String CHAVE = "frx_runa", NO_ITEM = "frx_runas", MARCA = "◆ Runa ";
    private static final int NIVEL_MAX = 3;

    /** Vagas de runa por item conforme o rank da Guilda de quem encaixa: até D 2, C 3, B 4, A e S 5. */
    public static int vagas(net.minecraft.world.entity.player.Player p) {
        int rank = p == null ? 0 : br.com.frxcode.frxworld.progressao.Guilda.indice(p);
        return rank >= 5 ? 5 : rank == 4 ? 4 : rank == 3 ? 3 : 2;
    }
    private static final String[] ROMANOS = {"", "I", "II", "III"};

    private record Tipo(String id, String nome, boolean arma, ChatFormatting cor, String efeito) { }

    private static final Map<String, Tipo> TIPOS = new LinkedHashMap<>();
    private static void t(String id, String nome, boolean arma, ChatFormatting cor, String efeito) { TIPOS.put(id, new Tipo(id, nome, arma, cor, efeito)); }
    static {
        t("sangue", "de Sangue", true, ChatFormatting.DARK_RED, "Cura 3% do dano causado por nível");
        t("gelo", "de Gelo", true, ChatFormatting.AQUA, "10% por nível de congelar o alvo por 3 s");
        t("fogo", "de Fogo", true, ChatFormatting.GOLD, "+1 de dano por nível e incendeia o alvo");
        t("trovao", "do Trovão", true, ChatFormatting.YELLOW, "8% por nível de cair um raio (+dano)");
        t("fortuna", "da Fortuna", true, ChatFormatting.GREEN, "12% por nível de XP em dobro ao matar");
        t("execucao", "da Execução", true, ChatFormatting.RED, "+12% de dano por nível em alvos abaixo de 30% de vida");
        t("pedra", "de Pedra", false, ChatFormatting.GRAY, "-2% de dano recebido por nível");
        t("espinhos", "de Espinhos", false, ChatFormatting.DARK_GREEN, "Devolve 6% do dano recebido por nível");
        t("vigor", "do Vigor", false, ChatFormatting.LIGHT_PURPLE, "+1 coração por nível");
        t("vento", "do Vento", false, ChatFormatting.WHITE, "+3% de velocidade por nível");
        t("fenix", "da Fênix", false, ChatFormatting.GOLD, "Salva de um golpe fatal (recarga de 10 min, -1 min por nível)");
    }

    // ------------------------------------------------------------------ itens

    public static ItemStack criar(String tipo, int nivel) {
        Tipo t = TIPOS.get(tipo);
        if (t == null) return ItemStack.EMPTY;
        nivel = Math.max(1, Math.min(NIVEL_MAX, nivel));
        ItemStack s = new ItemStack(Items.ECHO_SHARD);
        CompoundTag d = new CompoundTag();
        d.putString("tipo", tipo);
        d.putInt("nivel", nivel);
        s.getOrCreateTag().put(CHAVE, d);
        ListTag brilho = new ListTag();
        brilho.add(new CompoundTag());
        s.getOrCreateTag().put("Enchantments", brilho);
        s.setHoverName(Component.literal("Runa " + t.nome() + " " + ROMANOS[nivel]).withStyle(st -> st.withColor(t.cor()).withItalic(false)));
        ListTag lore = new ListTag();
        for (String l : new String[]{t.efeito() + ".", t.arma() ? "Encaixa em armas." : "Encaixa em armaduras.", "Use na bigorna: item à esquerda, runa à direita.", "Duas runas iguais na bigorna sobem de nível.", "Vagas por item: 2 (até Rank D), 3 (C), 4 (B), 5 (A e S)."})
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(l).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)))));
        s.getOrCreateTagElement("display").put("Lore", lore);
        return s;
    }

    /** Runas encaixadas no item: tipo → nível, na ordem em que estão. */
    public static Map<String, Integer> doItem(ItemStack s) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (s.isEmpty() || !s.hasTag() || !s.getTag().contains(NO_ITEM)) return m;
        CompoundTag r = s.getTag().getCompound(NO_ITEM);
        for (String k : r.getAllKeys()) if (TIPOS.containsKey(k)) m.put(k, Math.max(1, Math.min(NIVEL_MAX, r.getInt(k))));
        return m;
    }

    public static String nomeRuna(String tipo, int nivel) {
        Tipo t = TIPOS.get(tipo);
        return t == null ? tipo : "Runa " + t.nome() + " " + ROMANOS[Math.max(1, Math.min(NIVEL_MAX, nivel))];
    }

    public static int corRuna(String tipo) {
        Tipo t = TIPOS.get(tipo);
        Integer c = t == null ? null : t.cor().getColor();
        return 0xFF000000 | (c == null ? 0x555555 : c);
    }

    /** Tira a runa do item (que fica sem ela) e devolve a pedra da runa, inteira. */
    public static ItemStack tirar(ItemStack s, String tipo) {
        int nv = nivel(s, tipo);
        if (nv <= 0) return ItemStack.EMPTY;
        CompoundTag r = s.getTag().getCompound(NO_ITEM);
        r.remove(tipo);
        if (r.isEmpty()) s.getTag().remove(NO_ITEM);
        reescreverLore(s);
        return criar(tipo, nv);
    }

    /** Runa de tipo sorteado, no nível pedido. */
    public static ItemStack criarAleatoriaNivel(int nivel) {
        String[] ids = TIPOS.keySet().toArray(new String[0]);
        return criar(ids[ThreadLocalRandom.current().nextInt(ids.length)], nivel);
    }

    public static ItemStack aleatoria(int nivelMax) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String[] ids = TIPOS.keySet().toArray(new String[0]);
        int sorte = rnd.nextInt(100), nivel = nivelMax >= 3 && sorte < 10 ? 3 : nivelMax >= 2 && sorte < 35 ? 2 : 1;
        return criar(ids[rnd.nextInt(ids.length)], nivel);
    }

    /** Dá uma runa com a chance (em %) informada. */
    public static void talvezDar(ServerPlayer p, int chance, int nivelMax) {
        if (ThreadLocalRandom.current().nextInt(100) >= chance) return;
        ItemStack r = aleatoria(nivelMax);
        Component nome = r.getHoverName();
        if (!p.getInventory().add(r)) p.drop(r, false);
        p.sendSystemMessage(Component.literal("◆ Você encontrou uma ").withStyle(ChatFormatting.LIGHT_PURPLE).append(nome).append(Component.literal("! Encaixe na bigorna (/runas).").withStyle(ChatFormatting.LIGHT_PURPLE)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.8f);
    }

    private static CompoundTag runa(ItemStack s) {
        return s.is(Items.ECHO_SHARD) && s.hasTag() && s.getTag().contains(CHAVE) ? s.getTag().getCompound(CHAVE) : null;
    }

    private static boolean ehArma(ItemStack s) {
        return s.getItem() instanceof TieredItem || s.getItem() instanceof ProjectileWeaponItem || s.getItem() instanceof TridentItem;
    }

    private static boolean ehArmadura(ItemStack s) { return s.getItem() instanceof ArmorItem; }

    /** Nível da runa do tipo neste item (0 se não tiver). */
    private static int nivel(ItemStack s, String tipo) {
        if (s.isEmpty() || !s.hasTag() || !s.getTag().contains(NO_ITEM)) return 0;
        return s.getTag().getCompound(NO_ITEM).getInt(tipo);
    }

    private static int naArmadura(LivingEntity e, String tipo) {
        int total = 0;
        for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) total += nivel(e.getItemBySlot(sl), tipo);
        return total;
    }

    /** Recoloca as linhas "◆ Runa" na descrição (também usado quando outro sistema reescreve a descrição do item). */
    public static void reescreverLore(ItemStack s) {
        if (s.isEmpty() || !s.hasTag()) return;
        CompoundTag display = s.getOrCreateTagElement("display");
        ListTag antigo = display.getList("Lore", Tag.TAG_STRING), novo = new ListTag();
        for (int i = 0; i < antigo.size(); i++) if (!antigo.getString(i).contains(MARCA)) novo.add(antigo.get(i));
        CompoundTag r = s.getTag().getCompound(NO_ITEM);
        for (String tipo : r.getAllKeys()) {
            Tipo t = TIPOS.get(tipo);
            if (t == null) continue;
            novo.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(MARCA + t.nome() + " " + ROMANOS[Math.max(1, Math.min(3, r.getInt(tipo)))])
                    .withStyle(st -> st.withColor(t.cor()).withItalic(false)))));
        }
        display.put("Lore", novo);
    }

    // ------------------------------------------------------------------ bigorna

    @SubscribeEvent
    public void naBigorna(AnvilUpdateEvent ev) {
        ItemStack esq = ev.getLeft(), dir = ev.getRight();
        CompoundTag rd = runa(dir);
        if (rd == null) return;
        Tipo t = TIPOS.get(rd.getString("tipo"));
        if (t == null) return;
        int nv = rd.getInt("nivel");
        CompoundTag re = runa(esq);
        if (re != null) {   // runa + runa igual = nível seguinte
            if (!re.getString("tipo").equals(t.id()) || re.getInt("nivel") != nv || nv >= NIVEL_MAX || esq.getCount() != 1) { ev.setCanceled(true); return; }
            ev.setOutput(criar(t.id(), nv + 1));
            ev.setCost(4 * nv);
            ev.setMaterialCost(1);
            return;
        }
        if (!(t.arma() ? ehArma(esq) : ehArmadura(esq)) || esq.getCount() != 1) { ev.setCanceled(true); return; }
        CompoundTag atuais = esq.hasTag() && esq.getTag().contains(NO_ITEM) ? esq.getTag().getCompound(NO_ITEM) : new CompoundTag();
        boolean troca = atuais.contains(t.id());                       // mesma runa de nível maior substitui
        if (troca ? atuais.getInt(t.id()) >= nv : atuais.size() >= vagas(ev.getPlayer())) {
            if (!troca && ev.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp)
                sp.displayClientMessage(Component.literal("Este item já tem " + atuais.size() + " runa(s): no seu rank cabem " + vagas(sp)
                        + " (C 3, B 4, A e S 5). Tire uma no sábio para trocar.").withStyle(ChatFormatting.RED), true);
            ev.setCanceled(true);
            return;
        }
        ItemStack saida = esq.copy();
        CompoundTag novas = atuais.copy();
        novas.putInt(t.id(), nv);
        saida.getOrCreateTag().put(NO_ITEM, novas);
        reescreverLore(saida);
        ev.setOutput(saida);
        ev.setCost(3 * nv);
        ev.setMaterialCost(1);
    }

    // ------------------------------------------------------------------ efeitos

    private static ServerPlayer atacante(net.minecraft.world.damagesource.DamageSource fonte) {
        if (!(fonte.getEntity() instanceof ServerPlayer p)) return null;
        if (fonte.is(DamageTypes.THORNS) || fonte.is(DamageTypes.MAGIC) || fonte.is(DamageTypes.INDIRECT_MAGIC)) return null;
        return fonte.getDirectEntity() == p || fonte.getDirectEntity() instanceof AbstractArrow ? p : null;
    }

    @SubscribeEvent
    public void aoFerir(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide() || ev.getAmount() <= 0) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ServerPlayer p = atacante(ev.getSource());
        if (p != null && p != vitima) {
            ItemStack arma = p.getMainHandItem();
            float dano = ev.getAmount();
            int fogo = nivel(arma, "fogo"), gelo = nivel(arma, "gelo"), trovao = nivel(arma, "trovao"), exec = nivel(arma, "execucao");
            if (fogo > 0) { dano += fogo; vitima.setSecondsOnFire(2 + fogo); }
            if (exec > 0 && vitima.getHealth() < vitima.getMaxHealth() * 0.3f) dano *= 1 + 0.12f * exec;
            if (gelo > 0 && rnd.nextInt(100) < 10 * gelo) {
                vitima.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5));
                vitima.setTicksFrozen(200);
                p.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, vitima.getX(), vitima.getY() + 1, vitima.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            }
            if (trovao > 0 && rnd.nextInt(100) < 8 * trovao) {
                dano += 3 + trovao;
                LightningBolt raio = EntityType.LIGHTNING_BOLT.create(p.serverLevel());
                if (raio != null) { raio.moveTo(vitima.position()); raio.setVisualOnly(true); p.serverLevel().addFreshEntity(raio); }
            }
            ev.setAmount(dano);
        }
        if (vitima instanceof Player) {
            int pedra = naArmadura(vitima, "pedra");
            if (pedra > 0) ev.setAmount(ev.getAmount() * (1 - Math.min(0.30f, 0.02f * pedra)));
        }
    }

    @SubscribeEvent
    public void aoSofrerDano(LivingDamageEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide() || ev.getAmount() <= 0) return;
        ServerPlayer p = atacante(ev.getSource());
        if (p != null && p != vitima) {
            int sangue = nivel(p.getMainHandItem(), "sangue");
            if (sangue > 0) p.heal(Math.min(4f, ev.getAmount() * 0.03f * sangue));
        }
        if (vitima instanceof ServerPlayer alvo && ev.getSource().getEntity() instanceof LivingEntity agressor && agressor != alvo && !ev.getSource().is(DamageTypes.THORNS)) {
            int espinhos = naArmadura(alvo, "espinhos");
            if (espinhos > 0) agressor.hurt(alvo.damageSources().thorns(alvo), ev.getAmount() * 0.06f * espinhos);
        }
    }

    /** Fênix: uma vez a cada tantos minutos, o golpe fatal não mata. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void fenix(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.isCanceled()) return;
        int nv = naArmadura(p, "fenix");
        if (nv <= 0) return;
        CompoundTag pers = p.getPersistentData();
        long agora = System.currentTimeMillis(), recarga = Math.max(180, 600 - 60L * nv) * 1000;
        if (agora - pers.getLong("frx_fenix") < recarga) return;
        pers.putLong("frx_fenix", agora);
        ev.setCanceled(true);
        p.setHealth(p.getMaxHealth() * 0.3f);
        p.removeAllEffects();
        p.clearFire();
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        p.serverLevel().sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 80, 0.6, 1, 0.6, 0.1);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.2f);
        p.sendSystemMessage(Component.literal("◆ A Runa da Fênix salvou a sua vida! Próxima vez em " + recarga / 60000 + " min.").withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public void aoMatar(LivingDeathEvent ev) {
        LivingEntity morto = ev.getEntity();
        if (morto.level().isClientSide() || !(ev.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (morto.getTags().contains(br.com.frxcode.frxworld.dungeon.Dungeons.TAG_BOSS) || morto.getTags().contains("frx_chefe_mundial")) talvezDar(p, 60, 2);
        int fortuna = nivel(p.getMainHandItem(), "fortuna");
        if (fortuna > 0 && morto instanceof Enemy && ThreadLocalRandom.current().nextInt(100) < 12 * fortuna) {
            long extra = br.com.frxcode.frxworld.aventura.Torre.semXpDeAbate(morto) ? 0 : br.com.frxcode.frxworld.progressao.Progressao.xpDoMob(morto);
            if (extra > 0) {
                br.com.frxcode.frxworld.progressao.Progressao.ganharXp(p, extra);
                p.displayClientMessage(Component.literal("◆ Fortuna: +" + extra + " XP").withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    private static final UUID ID_VIGOR = UUID.fromString("8c2b1f0a-5d3e-4a77-9b61-2f4e6a8c0d11"), ID_VENTO = UUID.fromString("8c2b1f0a-5d3e-4a77-9b61-2f4e6a8c0d12");

    private static void ajustar(ServerPlayer p, Attribute atributo, UUID id, double valor, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(atributo);
        if (inst == null) return;
        AttributeModifier atual = inst.getModifier(id);
        if (atual != null && Math.abs(atual.getAmount() - valor) < 1e-6) return;
        if (atual != null) inst.removeModifier(id);
        if (valor > 0) inst.addTransientModifier(new AttributeModifier(id, "FRX runa", valor, op));
    }

    @SubscribeEvent
    public void aCadaSegundo(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 20 != 13) return;
        ajustar(p, Attributes.MAX_HEALTH, ID_VIGOR, 2.0 * naArmadura(p, "vigor"), AttributeModifier.Operation.ADDITION);
        ajustar(p, Attributes.MOVEMENT_SPEED, ID_VENTO, 0.03 * naArmadura(p, "vento"), AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("runas").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            p.sendSystemMessage(Component.literal("◆ Runas — encaixe na bigorna: item à esquerda, runa à direita. Por item: 2 até o Rank D, 3 no C, 4 no B, 5 no A e S (você: " + vagas(p) + "), de tipos diferentes. Duas iguais viram uma de nível maior (até III).").withStyle(ChatFormatting.LIGHT_PURPLE));
            p.sendSystemMessage(Component.literal("   Caem de chefes, procurados, chefes da Torre e baús de tesouro; a Loja da Torre vende.").withStyle(ChatFormatting.GRAY));
            for (Tipo t : TIPOS.values())
                p.sendSystemMessage(Component.literal("   Runa " + t.nome()).withStyle(t.cor()).append(Component.literal(" (" + (t.arma() ? "arma" : "armadura") + ") — " + t.efeito()).withStyle(ChatFormatting.GRAY)));
            return 1;
        }));
        ev.getDispatcher().register(Commands.literal("frx").requires(c -> c.hasPermission(2))
                .then(Commands.literal("runa").then(Commands.argument("tipo", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(TIPOS.keySet(), b))
                        .executes(c -> dar(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "tipo"), 1))
                        .then(Commands.argument("nivel", IntegerArgumentType.integer(1, NIVEL_MAX))
                                .executes(c -> dar(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "tipo"), IntegerArgumentType.getInteger(c, "nivel")))))));
    }

    private static int dar(ServerPlayer p, String tipo, int nivel) {
        ItemStack r = criar(tipo, nivel);
        if (r.isEmpty()) { p.sendSystemMessage(Component.literal("Tipos: " + String.join(", ", TIPOS.keySet())).withStyle(ChatFormatting.RED)); return 0; }
        if (!p.getInventory().add(r)) p.drop(r, false);
        return 1;
    }
}
