package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.progressao.Progressao;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vida dos moradores: memória, freguesia, rotina de dia e noite e a rixa entre ferreiro e armeiro.
 * - Memória: o lojista lembra a última compra e quantas vezes o jogador comprou ali; o taverneiro lembra a última missão.
 * - Freguês: 10 compras no mesmo lojista dão 3% de desconto; 30 dão 5%.
 * - Rotina: à noite ferreiro, armeiro e mercador deixam a loja com o aprendiz (10% mais caro) e vão para a taverna,
 *   onde só conversam. É lá, à noite, que ferreiro e armeiro pedem favores.
 * - Rixa: os dois disputam a encomenda do rei. Um favor por noite em cada capital: ajudar um sobe a estima dele e desce
 *   a do outro. Estima 3 = 5% de desconto, 6 = 8%; estima -3 ou menos = 5% mais caro.
 */
public class VidaNpc {
    private static final String CHAVE = "frx_vida", TAG_FOLGA = "frx_folga";
    private static final String[] MESTRES = {"ferreiro", "armeiro", "mercador"};
    private static final VillagerProfession[] PROFISSOES = {VillagerProfession.WEAPONSMITH, VillagerProfession.ARMORER, VillagerProfession.TOOLSMITH};
    /** Mestres de folga na taverna, por reino. */
    private static final Map<String, List<UUID>> FOLGA = new HashMap<>();
    private static final Set<UUID> VIVOS = new HashSet<>();

    // ------------------------------------------------------------------ dados do jogador

    private static CompoundTag dados(ServerPlayer p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    private static boolean mestre(String papel) { return papel.equals("ferreiro") || papel.equals("armeiro") || papel.equals("mercador"); }
    public static boolean noite(Level nivel) { long t = nivel.getDayTime() % 24000L; return t >= 13000 && t < 23000; }
    public static int compras(ServerPlayer p, String reino, String papel) { return dados(p).getInt("c_" + reino + "_" + papel); }
    public static int estima(ServerPlayer p, String reino, String papel) { return dados(p).getInt("e_" + reino + "_" + papel); }
    private static String trato(ServerPlayer p) { String n = Nomeacao.nome(p); return n.isEmpty() ? p.getGameProfile().getName() : n; }

    /** Compra feita num lojista do reino. */
    static void comprou(ServerPlayer p, Entity npc, String item) {
        String reino = npc.getPersistentData().getString("frx_reino"), papel = npc.getPersistentData().getString("frx_papel");
        if (reino.isEmpty()) return;
        CompoundTag d = dados(p);
        int antes = d.getInt("c_" + reino + "_" + papel);
        d.putInt("c_" + reino + "_" + papel, antes + 1);
        d.putString("u_" + reino + "_" + papel, item);
        if (antes + 1 == 10 || antes + 1 == 30)
            p.sendSystemMessage(Component.literal("✦ Você virou freguês da casa: " + (antes + 1 == 10 ? 3 : 5) + "% de desconto com este lojista.").withStyle(ChatFormatting.GREEN));
    }

    /** Missão entregue no quadro: o taverneiro comenta depois. */
    public static void missao(ServerPlayer p, String titulo) { dados(p).putString("missao", titulo); }

    /** Desconto do lojista para este jogador: reputação, mais freguesia e estima, menos o aprendiz da noite. */
    static double ajustar(double desc, ServerPlayer p, Entity npc) {
        String reino = npc.getPersistentData().getString("frx_reino"), papel = npc.getPersistentData().getString("frx_papel");
        if (reino.isEmpty()) return desc;
        int c = compras(p, reino, papel), e = estima(p, reino, papel);
        desc += c >= 30 ? 0.05 : c >= 10 ? 0.03 : 0;
        desc += e >= 6 ? 0.08 : e >= 3 ? 0.05 : e <= -3 ? -0.05 : 0;
        if (mestre(papel) && noite(npc.level())) desc -= 0.10;
        return Math.min(0.5, desc);
    }

    /** Linha que o lojista diz ao abrir a loja ("" = nada a dizer). */
    static String saudacao(ServerPlayer p, Entity npc) {
        String reino = npc.getPersistentData().getString("frx_reino"), papel = npc.getPersistentData().getString("frx_papel");
        if (reino.isEmpty()) return "";
        if (mestre(papel) && noite(npc.level())) return "O mestre está na taverna. À noite quem atende sou eu, e cobro 10% a mais.";
        CompoundTag d = dados(p);
        int c = compras(p, reino, papel), e = estima(p, reino, papel);
        String ultima = d.getString("u_" + reino + "_" + papel);
        if (e <= -3) return "Soube que você anda ajudando o meu concorrente. Para você, 5% a mais.";
        if (e >= 3) return "Amigo da casa paga menos, " + trato(p) + ". Não esqueço quem me ajudou.";
        if (c >= 10) return "Meu freguês! " + (ultima.isEmpty() ? "" : "Ainda serve bem, a última compra (" + ultima + ")? ") + "Seu desconto está valendo.";
        if (c >= 1 && !ultima.isEmpty()) return "De volta, " + trato(p) + "? Da última vez você levou " + ultima + ".";
        return "";
    }

    /** O que o morador lembra do jogador, para a caixa de fala ("" = nada). */
    static String memoria(ServerPlayer p, String papel, String reino) {
        int h = Reputacao.hostilidade(p, reino);
        if (papel.equals("taverneiro")) {
            if (Reputacao.piorHostilidade(p) >= 3) return "Tem cartaz com a sua cara em mais de uma cidade, " + trato(p) + ". Beba rápido e saia pelos fundos.";
            if (h == 1) return "Andam falando mal de você por aqui. Eu sirvo qualquer um, mas fale baixo.";
            String missao = dados(p).getString("missao");
            if (!missao.isEmpty()) return "Soube que você resolveu \"" + missao + "\". O quadro tem mais, se a mão ainda não cansou.";
        }
        if (papel.equals("rei") && Reputacao.indice(Reputacao.pontos(p, reino)) >= 3)
            return "O reino lembra do que você fez por nós, " + trato(p) + ". Minhas portas estão abertas.";
        return "";
    }

    // ------------------------------------------------------------------ taverna à noite

    /** Fala do mestre de folga (papel "folga_ferreiro" etc.). */
    static String falaFolga(ServerPlayer p, Entity npc) {
        String quem = npc.getPersistentData().getString("frx_papel").substring(6), reino = npc.getPersistentData().getString("frx_reino");
        int e = estima(p, reino, quem), c = compras(p, reino, quem);
        if (e >= 3) return "Senta aqui, " + trato(p) + ". Quem me ajuda bebe na minha conta.";
        if (e <= -3) return "Você tem coragem de vir à minha mesa depois de ajudar o outro.";
        if (c >= 10) return "Olha o meu freguês! A loja está com o aprendiz, mas a conversa é por minha conta.";
        return "Loja fechada para mim a esta hora. O aprendiz cuida de lá; aqui eu só bebo e converso.";
    }

    /** Conversa na taverna: boatos que só se ouvem à noite. */
    static String conversa(ServerPlayer p, Entity npc) {
        String papel = npc.getPersistentData().getString("frx_papel"), reino = npc.getPersistentData().getString("frx_reino");
        Reino rv = Reputacao.reino(Reputacao.rival(reino) == null ? "" : Reputacao.rival(reino));
        List<String> l = new ArrayList<>();
        if (papel.equals("folga_ferreiro")) {
            l.add("O armeiro jura que a encomenda do rei é dele. Só se for por cima da minha bigorna.");
            l.add("Aço bom se conhece pelo som. O do armeiro soa como panela.");
        } else if (papel.equals("folga_armeiro")) {
            l.add("O ferreiro faz espada bonita. Pena que quem volta vivo é quem usa a minha armadura.");
            l.add("O rei vai escolher um de nós dois para a encomenda. Quem me ajudar não vai se arrepender.");
        } else {
            l.add("De dia eu vendo, de noite eu ouço. E o que eu ouço vale mais do que o que eu vendo.");
            l.add("Ferreiro e armeiro não se falam desde que o rei abriu a encomenda. Cada um quer um favor de quem passa.");
        }
        if (rv != null) l.add("Dizem que em " + rv.nome() + " não gostam de quem é bem-visto aqui. Escolha bem para quem você trabalha.");
        l.add("Fora das muralhas tem um sujeito que compra e vende o que os reinos não deixam. Eu não disse nada.");
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p && Progressao.temDados(o) && Reputacao.piorHostilidade(o) >= 3) l.add("Ouvi que puseram a cabeça de " + o.getGameProfile().getName() + " a prêmio. Tem ouro fácil para quem achar.");
        return l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    private static Item[] itens(String quem) { return quem.equals("ferreiro") ? new Item[]{Items.IRON_INGOT, Items.COAL} : new Item[]{Items.LEATHER, Items.IRON_INGOT}; }
    private static int[] quantos(String quem) { return quem.equals("ferreiro") ? new int[]{16, 16} : new int[]{12, 8}; }
    private static String lista(String quem) { return quem.equals("ferreiro") ? "16 lingotes de ferro e 16 carvões" : "12 couros e 8 lingotes de ferro"; }
    private static String outro(String quem) { return quem.equals("ferreiro") ? "armeiro" : "ferreiro"; }

    private static int contar(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** O pedido do ferreiro ou do armeiro, na taverna. entregar = o jogador trouxe o material. */
    static String favor(ServerPlayer p, Entity npc, boolean entregar) {
        String quem = npc.getPersistentData().getString("frx_papel").substring(6), reino = npc.getPersistentData().getString("frx_reino");
        if (!quem.equals("ferreiro") && !quem.equals("armeiro")) return "Eu? Não preciso de nada. Só de mais uma caneca.";
        CompoundTag d = dados(p);
        long dia = npc.level().getDayTime() / 24000L;
        if (d.contains("f_" + reino) && d.getLong("f_" + reino) == dia) return "Você já fez a sua parte nesta cidade hoje. Volte outra noite.";
        if (!entregar) return "Estou disputando a encomenda do rei com o " + outro(quem) + ". Traga-me " + lista(quem)
                + " e eu passo na frente. Aviso: ele não vai gostar de saber que você me ajudou.";
        Item[] is = itens(quem);
        int[] qs = quantos(quem);
        for (int i = 0; i < is.length; i++) if (contar(p, is[i]) < qs[i]) return "Ainda falta material. Eu pedi " + lista(quem) + ".";
        for (int i = 0; i < is.length; i++) {
            int falta = qs[i];
            for (ItemStack s : p.getInventory().items) {
                if (falta <= 0) break;
                if (!s.is(is[i])) continue;
                int n = Math.min(falta, s.getCount());
                s.shrink(n);
                falta -= n;
            }
        }
        d.putLong("f_" + reino, dia);
        int e = Math.min(9, d.getInt("e_" + reino + "_" + quem) + 1), o = Math.max(-9, d.getInt("e_" + reino + "_" + outro(quem)) - 1);
        d.putInt("e_" + reino + "_" + quem, e);
        d.putInt("e_" + reino + "_" + outro(quem), o);
        Reputacao.ganhar(p, reino, 15, true);
        Progressao.ganharXp(p, Progressao.dados(p).nivel() * 20L);
        return "Isso me põe na frente. " + (e >= 6 ? "Para você, 8% a menos na minha loja, sempre." : e >= 3 ? "Para você, 5% a menos na minha loja."
                : "Mais " + (3 - e) + " destes e você paga menos na minha loja.") + (o <= -3 ? " O " + outro(quem) + " já está cobrando mais caro de você." : "");
    }

    // ------------------------------------------------------------------ rotina: aprendiz na loja, mestre na taverna

    private static BlockPos lugarLivre(ServerLevel nivel, BlockPos centro, Set<BlockPos> usados) {
        for (int raio = 2; raio <= 4; raio++)
            for (int dx = -raio; dx <= raio; dx++)
                for (int dz = -raio; dz <= raio; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != raio) continue;
                    BlockPos b = centro.offset(dx, 0, dz);
                    if (usados.contains(b)) continue;
                    if (nivel.getBlockState(b).getCollisionShape(nivel, b).isEmpty() && nivel.getBlockState(b.above()).getCollisionShape(nivel, b.above()).isEmpty()
                            && !nivel.getBlockState(b.below()).getCollisionShape(nivel, b.below()).isEmpty()
                            && nivel.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(b)).isEmpty()) return b;
                }
        return null;
    }

    /** Avisa quem está vendo o NPC que a skin dele mudou (mestre de dia, aprendiz de noite). */
    private static void remarcar(Entity e) {
        String[] pr = br.com.frxcode.frxworld.npc.SkinsNpc.papelEReino(e);
        if (pr != null) br.com.frxcode.frxworld.rede.Rede.CANAL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> e),
                new br.com.frxcode.frxworld.rede.MarcaNpc(e.getId(), pr[0], pr[1]));
    }

    private static void abrirFolga(ServerLevel nivel, Entity taverneiro, String reino) {
        Reino r = Reputacao.reino(reino);
        if (r == null) return;
        List<UUID> ids = new ArrayList<>();
        Set<BlockPos> usados = new HashSet<>();
        for (int i = 0; i < MESTRES.length; i++) {
            BlockPos b = lugarLivre(nivel, taverneiro.blockPosition(), usados);
            Villager v = b == null ? null : EntityType.VILLAGER.create(nivel);
            if (v == null) continue;
            usados.add(b);
            v.setVillagerData(v.getVillagerData().setType(Racas.de(r, nivel.getRandom())).setProfession(PROFISSOES[i]).setLevel(5));
            v.setVillagerXp(10_000);
            v.setNoAi(true);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, (float) Math.toDegrees(Math.atan2(-(taverneiro.getX() - b.getX() - 0.5), taverneiro.getZ() - b.getZ() - 0.5)), 0);
            v.setYHeadRot(v.getYRot());
            v.setCustomName(Component.literal(NpcsReino.nome(r, MESTRES[i])).withStyle(ChatFormatting.YELLOW));
            v.setCustomNameVisible(true);
            v.addTag(NpcsReino.TAG);
            v.addTag(TAG_FOLGA);
            v.getPersistentData().putString("frx_papel", "folga_" + MESTRES[i]);
            v.getPersistentData().putString("frx_reino", reino);
            VIVOS.add(v.getUUID());   // antes de entrar no mundo: aoEntrar só deixa passar os de agora
            nivel.addFreshEntity(v);
            ids.add(v.getUUID());
        }
        FOLGA.put(reino, ids);
    }

    private static void fecharFolga(MinecraftServer s) {
        for (List<UUID> ids : FOLGA.values())
            for (UUID id : ids)
                for (ServerLevel n : s.getAllLevels()) { Entity e = n.getEntity(id); if (e != null) e.discard(); }
        FOLGA.clear();
        VIVOS.clear();
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 100 != 17) return;
        MinecraftServer s = ev.getServer();
        boolean noite = noite(s.overworld());
        if (!noite && !FOLGA.isEmpty()) fecharFolga(s);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel nivel) || NpcsReino.reinoDe(p) == null) continue;
            for (Villager v : nivel.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(64, 24, 64), x -> x.getTags().contains(NpcsReino.TAG))) {
                String papel = v.getPersistentData().getString("frx_papel"), reino = v.getPersistentData().getString("frx_reino");
                if (reino.isEmpty()) continue;
                CompoundTag d = v.getPersistentData();
                if (mestre(papel)) {
                    // de noite a placa da loja mostra o aprendiz; de dia volta o nome do mestre
                    if (noite && !d.contains("frx_nome_dia") && v.getCustomName() != null) {
                        d.putString("frx_nome_dia", Component.Serializer.toJson(v.getCustomName()));
                        v.setCustomName(Component.literal("Aprendiz do " + Character.toUpperCase(papel.charAt(0)) + papel.substring(1)).withStyle(ChatFormatting.GRAY));
                        remarcar(v);
                    } else if (!noite && d.contains("frx_nome_dia")) {
                        Component nome = Component.Serializer.fromJson(d.getString("frx_nome_dia"));
                        if (nome != null) v.setCustomName(nome);
                        d.remove("frx_nome_dia");
                        remarcar(v);
                    }
                } else if (noite && papel.equals("taverneiro") && !FOLGA.containsKey(reino)) {
                    abrirFolga(nivel, v, reino);
                }
            }
        }
    }

    /** Mestre de folga que sobrou de outra noite (queda do servidor, chunk descarregado) some ao carregar. */
    @SubscribeEvent
    public void aoEntrar(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || !e.getTags().contains(TAG_FOLGA)) return;
        if (!VIVOS.contains(e.getUUID())) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void aoParar(ServerStoppingEvent ev) { fecharFolga(ev.getServer()); }
}
