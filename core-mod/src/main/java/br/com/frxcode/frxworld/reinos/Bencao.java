package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.armas.Lendarias;
import br.com.frxcode.frxworld.habilidades.Talentos;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bênção da Capitã Hinata (a sábia de Lubelius): abençoa uma peça de armadura vestida com o atributo da classe do jogador.
 *  - Graus I a V. Chance de 100, 80, 60, 40 e 20%; preço de 10, 20, 40, 80 e 150 ouro (bolsa e, se faltar, banco).
 *  - Grau IV pede 2 Escamas Draconianas e o grau V 2 Essências Abissais, e os dois pedem reputação Honrado com Lubelius.
 *  - Falhou: perde o pagamento (e o material); a peça fica como estava.
 *  - Só vale enquanto o jogador for da classe da bênção. Peça abençoada para outra classe recomeça do grau I.
 *  - O bônus entra em Talentos.bonus, junto com o dos talentos (até 4 peças).
 * Guardado no item: tag "frx_bencao" {classe, grau}.
 */
public class Bencao {
    public static final int ABENCOAR = 1;
    public static final String TAG = "frx_bencao";
    private static final String MARCA_LORE = "Bênção de Hinata";
    private static final int[] CHANCE = {100, 80, 60, 40, 20};
    private static final long[] PRECO = {1000, 2000, 4000, 8000, 15000};
    private static final String[] GRAU = {"", "I", "II", "III", "IV", "V"};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int HONRADO = 500;

    /** Atributo de cada classe e quanto cada grau dá (por peça). porcento = mostrar em %. */
    private record Def(Talentos.Atributo atributo, double porGrau, String texto, boolean porcento) {}

    private static final Map<String, Def> CLASSES = new HashMap<>();
    private static final Map<String, String> NOMES = new HashMap<>();

    static {
        def("GUERREIRO", "Guerreiro", Talentos.Atributo.ROUBO_VIDA, 0.005, "de roubo de vida", true);
        def("ARQUEIRO", "Arqueiro", Talentos.Atributo.DANO_DISTANCIA, 0.006, "de dano de flechas e projéteis", true);
        def("MAGO", "Mago", Talentos.Atributo.DANO_HAB, 0.006, "de dano das habilidades", true);
        def("CLERIGO", "Clérigo", Talentos.Atributo.CURA, 0.01, "de cura das habilidades", true);
        def("LADINO", "Ladino", Talentos.Atributo.ESQUIVA, 0.005, "de chance de esquiva", true);
        def("ASSASSINO", "Assassino", Talentos.Atributo.CRITICO, 0.006, "de chance de crítico", true);
        def("INVOCADOR", "Invocador", Talentos.Atributo.INVOCACAO, 0.008, "de força das invocações", true);
        def("PALADINO", "Paladino", Talentos.Atributo.ARMADURA, 0.3, "de armadura", false);
        def("BARDO", "Bardo", Talentos.Atributo.REGEN, 0.01, "de regeneração de recurso", true);
        def("MONGE", "Monge", Talentos.Atributo.VELOCIDADE, 0.004, "de velocidade", true);
        def("DRUIDA", "Druida", Talentos.Atributo.RECARGA, 0.006, "menos recarga das habilidades", true);
        def("SAMURAI", "Samurai", Talentos.Atributo.DANO_FISICO, 0.006, "de dano corpo a corpo", true);
        def("CARTOMANTE", "Cartomante", Talentos.Atributo.CUSTO, 0.006, "menos custo de recurso", true);
    }

    private static void def(String id, String nome, Talentos.Atributo a, double porGrau, String texto, boolean porcento) {
        CLASSES.put(id, new Def(a, porGrau, texto, porcento));
        NOMES.put(id, nome);
    }

    public Bencao() {}

    private static String classe(ServerPlayer p) { return Progressao.temDados(p) ? Progressao.dados(p).classe() : ""; }

    private static String valor(Def d, int grau) {
        double v = d.porGrau() * grau * (d.porcento() ? 100 : 1);
        String n = Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : String.format("%.1f", v).replace('.', ',');
        return "+" + n + (d.porcento() ? "% " : " ") + d.texto();
    }

    // ------------------------------------------------------------------ o bônus

    /** Quanto a armadura abençoada vestida soma neste atributo (só a parte da classe atual do jogador). */
    public static double bonus(ServerPlayer p, Talentos.Atributo a) {
        String c = classe(p);
        Def d = CLASSES.get(c);
        if (d == null || d.atributo() != a) return 0;
        double soma = 0;
        for (EquipmentSlot s : SLOTS) {
            ItemStack it = p.getItemBySlot(s);
            if (it.isEmpty() || !it.hasTag() || !it.getTag().contains(TAG)) continue;
            CompoundTag b = it.getTag().getCompound(TAG);
            if (c.equals(b.getString("classe"))) soma += d.porGrau() * Math.max(0, Math.min(5, b.getInt("grau")));
        }
        return soma;
    }

    /** Trocar de armadura muda o bônus: os atributos fixos (armadura, velocidade, dano físico, crítico...) são refeitos. */
    @SubscribeEvent
    public void trocou(LivingEquipmentChangeEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.getSlot().getType() != EquipmentSlot.Type.ARMOR) return;
        boolean antes = ev.getFrom().hasTag() && ev.getFrom().getTag().contains(TAG), depois = ev.getTo().hasTag() && ev.getTo().getTag().contains(TAG);
        if (antes || depois) Talentos.aplicar(p);
    }

    // ------------------------------------------------------------------ Hinata

    public static boolean ehHinata(Entity npc) {
        return npc != null && "sabio".equals(npc.getPersistentData().getString("frx_papel")) && "lubelius".equals(npc.getPersistentData().getString("frx_reino"));
    }

    private static boolean pagar(ServerPlayer p, long valor) {
        long bolsa = Moedas.contar(p), banco = Banco.saldo(p);
        if (bolsa + banco < valor) return false;
        long daBolsa = Math.min(bolsa, valor);
        if (daBolsa > 0 && !Moedas.cobrar(p, daBolsa)) return false;
        if (valor - daBolsa > 0) Banco.definirSaldo(p, banco - (valor - daBolsa));
        return true;
    }

    private static int contar(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void tirar(ServerPlayer p, Item item, int qtd) {
        for (ItemStack s : p.getInventory().items) {
            if (qtd <= 0) return;
            if (!s.is(item)) continue;
            int t = Math.min(qtd, s.getCount());
            s.shrink(t);
            qtd -= t;
        }
    }

    private static Item material(int alvo) { return alvo == 4 ? Lendarias.ESCAMA.get() : alvo == 5 ? Lendarias.ESSENCIA.get() : null; }

    /** Grau atual da peça para a classe do jogador (0 = sem bênção ou de outra classe). */
    private static int grau(ItemStack s, String c) {
        if (!s.hasTag() || !s.getTag().contains(TAG)) return 0;
        CompoundTag b = s.getTag().getCompound(TAG);
        return c.equals(b.getString("classe")) ? b.getInt("grau") : 0;
    }

    public static void abrir(ServerPlayer p, Entity npc) { mandar(p, npc, "", false); }

    private static void mandar(ServerPlayer p, Entity npc, String msg, boolean ok) {
        String c = classe(p);
        Def d = CLASSES.get(c);
        CompoundTag t = new CompoundTag();
        t.putInt("npc", npc.getId());
        t.putString("msg", msg);
        t.putBoolean("ok", ok);
        t.putLong("dinheiro", Moedas.contar(p) + Banco.saldo(p));
        t.putString("classe", d == null ? "" : NOMES.get(c));
        t.putString("efeito", d == null ? "" : valor(d, 1) + " por grau, em cada peça");
        int rep = Reputacao.pontos(p, "lubelius");
        ListTag pecas = new ListTag();
        for (EquipmentSlot s : SLOTS) {
            ItemStack it = p.getItemBySlot(s);
            CompoundTag e = new CompoundTag();
            boolean armadura = !it.isEmpty() && it.getItem() instanceof ArmorItem;
            e.putBoolean("vazio", !armadura);
            if (armadura) {
                e.put("item", it.save(new CompoundTag()));
                e.putString("nome", it.getHoverName().getString());
                String estado;
                int g = d == null ? 0 : grau(it, c);
                if (it.hasTag() && it.getTag().contains(TAG) && g == 0) {
                    String outra = it.getTag().getCompound(TAG).getString("classe");
                    estado = "Bênção de " + NOMES.getOrDefault(outra, outra) + " grau " + GRAU[Math.max(0, Math.min(5, it.getTag().getCompound(TAG).getInt("grau")))] + " (inativa)";
                } else estado = g == 0 ? "Sem bênção" : "Grau " + GRAU[g] + ": " + valor(d, g);
                e.putString("estado", estado);
                e.putInt("grau", g);
                if (d != null && g < 5) {
                    int alvo = g + 1;
                    e.putInt("alvo", alvo);
                    e.putLong("preco", PRECO[alvo - 1]);
                    e.putInt("chance", CHANCE[alvo - 1]);
                    StringBuilder pede = new StringBuilder();
                    boolean pode = Moedas.contar(p) + Banco.saldo(p) >= PRECO[alvo - 1];
                    Item mat = material(alvo);
                    if (mat != null) {
                        pede.append("2× ").append(new ItemStack(mat).getHoverName().getString()).append(" · reputação Honrado com Lubelius");
                        pode &= contar(p, mat) >= 2 && rep >= HONRADO;
                    }
                    e.putString("pede", pede.toString());
                    e.putString("proximo", valor(d, alvo));
                    e.putBoolean("pode", pode);
                }
            }
            pecas.add(e);
        }
        t.put("pecas", pecas);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("bencao", t));
    }

    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (!ehHinata(npc) || npc.distanceToSqr(p) > 144) return;
        if (a.acao() != ABENCOAR) { abrir(p, npc); return; }
        String c = classe(p);
        Def d = CLASSES.get(c);
        if (d == null) { mandar(p, npc, "Escolha uma classe antes de pedir a bênção.", false); return; }
        int i = (int) a.valor();
        if (i < 0 || i >= SLOTS.length) return;
        ItemStack it = p.getItemBySlot(SLOTS[i]);
        if (it.isEmpty() || !(it.getItem() instanceof ArmorItem)) { mandar(p, npc, "Vista a peça que quer abençoar.", false); return; }
        int g = grau(it, c);
        if (g >= 5) { mandar(p, npc, "Esta peça já tem a bênção mais alta.", false); return; }
        int alvo = g + 1;
        Item mat = material(alvo);
        if (mat != null && Reputacao.pontos(p, "lubelius") < HONRADO) { mandar(p, npc, "O grau " + GRAU[alvo] + " só para quem é Honrado em Lubelius.", false); return; }
        if (mat != null && contar(p, mat) < 2) { mandar(p, npc, "O grau " + GRAU[alvo] + " pede 2× " + new ItemStack(mat).getHoverName().getString() + ".", false); return; }
        if (!pagar(p, PRECO[alvo - 1])) { mandar(p, npc, "Faltam moedas: custa " + Moedas.texto(PRECO[alvo - 1]) + ".", false); return; }
        if (mat != null) tirar(p, mat, 2);
        boolean deu = ThreadLocalRandom.current().nextInt(100) < CHANCE[alvo - 1];
        if (!deu) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
            mandar(p, npc, "A bênção não pegou desta vez. A peça continua como estava.", false);
            return;
        }
        CompoundTag b = new CompoundTag();
        b.putString("classe", c);
        b.putInt("grau", alvo);
        it.getOrCreateTag().put(TAG, b);
        escreverLore(it, d, alvo, NOMES.get(c));
        Talentos.aplicar(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        mandar(p, npc, "✦ Bênção grau " + GRAU[alvo] + ": " + valor(d, alvo) + ".", true);
    }

    /** Uma linha no fim da descrição do item com a bênção (troca a antiga). */
    private static void escreverLore(ItemStack it, Def d, int grau, String nomeClasse) {
        CompoundTag disp = it.getOrCreateTagElement("display");
        ListTag lore = disp.contains("Lore", Tag.TAG_LIST) ? disp.getList("Lore", Tag.TAG_STRING) : new ListTag();
        for (int k = lore.size() - 1; k >= 0; k--) if (lore.getString(k).contains(MARCA_LORE)) lore.remove(k);
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("✦ " + MARCA_LORE + " " + GRAU[grau] + " (" + nomeClasse + "): " + valor(d, grau))
                .withStyle(s -> s.withColor(ChatFormatting.AQUA).withItalic(false)))));
        disp.put("Lore", lore);
    }
}
