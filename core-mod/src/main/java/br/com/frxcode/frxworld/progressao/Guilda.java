package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.blocos.Blocos;
import br.com.frxcode.frxworld.habilidades.Habilidades;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Guilda de Aventureiros: o rank (F a S) não vem mais só do nível.
 * O jogador se registra com a recepcionista (ganha o Cartão de Aventureiro) e, ao atingir o nível de cada rank,
 * faz a prova de promoção: derrotar o Examinador da Guilda em até 3 minutos, sem morrer.
 */
public class Guilda {
    private static final String CHAVE = "frx_guilda", TAG = "frx_examinador";
    private static final long DURACAO_MS = 180_000, ESPERA_MS = 600_000;
    public static final int REGISTRAR = 0, PROVA = 1, CARTAO = 2;

    private record Prova(UUID examinador, int alvo, long fim) {}
    private static final Map<UUID, Prova> PROVAS = new HashMap<>();

    public Guilda() {}

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    public static boolean registrado(Player p) { return dados(p).getBoolean("registrado"); }

    /** Índice do rank atual (0 = F ... 6 = S). Sem registro na Guilda, é F. */
    public static int indice(Player p) { return registrado(p) ? Math.max(0, Math.min(ConfigJogo.totalRanks() - 1, dados(p).getInt("rank"))) : 0; }

    public static String rank(Player p) { return ConfigJogo.nomeRank(indice(p)); }

    /** Admin: define o rank direto (rebaixar ou promover sem prova). Mantém o registro na Guilda. */
    public static void definirRank(ServerPlayer p, int indice) {
        dados(p).putBoolean("registrado", true);
        dados(p).putInt("rank", Math.max(0, Math.min(ConfigJogo.totalRanks() - 1, indice)));
        Progressao.atualizarNome(p);
        Progressao.enviarAoPainel(p, false);
    }

    // ------------------------------------------------------------------ cartão

    private static boolean temCartao(ServerPlayer p) {
        Item c = Blocos.CARTAO.get();
        for (ItemStack s : p.getInventory().items) if (s.is(c)) return true;
        return p.getOffhandItem().is(c);
    }

    private static void preencher(ServerPlayer p, ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        DadosJogador d = Progressao.dados(p);
        t.putString("frx_dono", p.getGameProfile().getName());
        t.putString("frx_rank", rank(p));
        t.putInt("frx_nivel", d.nivel());
        t.putString("frx_classe", d.classe().isEmpty() ? "" : Habilidades.nomeClasse(d.classe()));
    }

    /** Atualiza os cartões que o jogador carrega (nível, rank e classe mudam). */
    public static void atualizarCartoes(ServerPlayer p) {
        Item c = Blocos.CARTAO.get();
        for (ItemStack s : p.getInventory().items) if (s.is(c)) preencher(p, s);
        if (p.getOffhandItem().is(c)) preencher(p, p.getOffhandItem());
    }

    private static void darCartao(ServerPlayer p) {
        ItemStack s = new ItemStack(Blocos.CARTAO.get());
        preencher(p, s);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    // ------------------------------------------------------------------ menu

    private static boolean recepcionista(Entity npc) {
        return npc != null && npc.getTags().contains("frx_npc") && "recepcionista".equals(npc.getPersistentData().getString("frx_papel"));
    }

    /** Abre o cartão/menu da Guilda. npc = null quando é só o cartão (sem ações). */
    public static void abrir(ServerPlayer p, Entity npc, String msg, boolean ok) {
        if (!Progressao.temDados(p)) return;
        DadosJogador dj = Progressao.dados(p);
        atualizarCartoes(p);
        pagarSoldo(p);
        CompoundTag d = new CompoundTag();
        d.putString("beneficios", registrado(p) ? beneficios(indice(p)) : "");
        d.putInt("npc", npc == null ? -1 : npc.getId());
        d.putString("nome", npc != null && npc.getCustomName() != null ? npc.getCustomName().getString() : "Guilda de Aventureiros");
        d.putString("jogador", p.getGameProfile().getName());
        d.putBoolean("registrado", registrado(p));
        d.putString("rank", rank(p));
        d.putInt("rankIdx", indice(p));
        d.putInt("nivel", dj.nivel());
        d.putString("classe", dj.classe().isEmpty() ? "" : Habilidades.nomeClasse(dj.classe()));
        d.putString("raca", br.com.frxcode.frxworld.racas.DefRacas.nome(br.com.frxcode.frxworld.racas.RacasJogador.principal(p)));
        d.putString("titulo", dj.titulo());
        d.putInt("abates", dj.contadores().getOrDefault("matar_total", 0));
        d.putInt("chefes", dj.contadores().getOrDefault("chefes", 0));
        d.putInt("campanhas", dj.contadores().getOrDefault("campanhas", 0));
        d.putInt("invasoes", dj.contadores().getOrDefault("invasoes", 0));
        d.putInt("mortes", dj.contadores().getOrDefault("mortes", 0));
        d.putInt("conquistas", dj.conquistas().size());
        d.putBoolean("temCartao", temCartao(p));
        d.putString("unica", br.com.frxcode.frxworld.habilidades.Unica.resumo(p));
        d.putString("unicaEfeito", br.com.frxcode.frxworld.habilidades.Unica.efeito(p));

        int alvo = indice(p) + 1;
        String estado;
        if (!registrado(p)) estado = "registrar";
        else if (alvo >= ConfigJogo.totalRanks()) estado = "max";
        else {
            d.putString("proximo", ConfigJogo.nomeRank(alvo));
            d.putInt("proximoNivel", ConfigJogo.nivelMinDoRank(alvo));
            d.putLong("premio", premio(alvo));
            d.putString("beneficiosProx", beneficios(alvo));
            long espera = dados(p).getLong("espera") - System.currentTimeMillis();
            if (PROVAS.containsKey(p.getUUID())) estado = "andamento";
            else if (dj.nivel() < ConfigJogo.nivelMinDoRank(alvo)) estado = "nivel";
            else if (espera > 0) { estado = "espera"; d.putInt("esperaSeg", (int) (espera / 1000)); }
            else estado = "pronto";
        }
        d.putString("estado", estado);
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("guilda", d));
    }

    private static long premio(int alvo) { return alvo * 200L; }   // em cobre: 2 ouro por degrau

    // ------------------------------------------------------------------ benefícios do rank

    /** Bônus de XP e moedas nas missões: +10% por rank acima de F (S = +60%). */
    public static double bonusMissao(Player p) { return indice(p) * 0.10; }

    /** Desconto da Guilda nas lojas e na forja: 2% por rank acima de F (S = 12%). Soma com a reputação. */
    public static double desconto(Player p) { return indice(p) * 0.02; }

    /** Soldo diário, em cobre: 60 por rank acima de F. */
    public static long soldo(int rank) { return rank * 60L; }

    private static String beneficios(int rank) {
        if (rank <= 0) return "Rank F ainda não tem benefícios. Suba para o E!";
        return "+" + rank * 10 + "% em missões · " + rank * 2 + "% de desconto nas lojas e na forja";
    }

    /** Paga o soldo do dia (uma vez por dia real), ao entrar no jogo ou abrir o cartão. */
    public static void pagarSoldo(ServerPlayer p) {
        if (!registrado(p)) return;
        int rank = indice(p);
        long hoje = java.time.LocalDate.now().toEpochDay();
        if (rank <= 0 || dados(p).getLong("soldoDia") == hoje) return;
        dados(p).putLong("soldoDia", hoje);
        if (true) return;   // soldo desligado em 16/11: moedas só por missão e venda
        p.sendSystemMessage(Component.literal("⚔ Soldo da Guilda (Rank " + rank(p) + "): " + Moedas.texto(soldo(rank)) + ".").withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public void aoEntrar(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) {
            try { pagarSoldo(p); } catch (Exception ignorado) { }
        }
    }

    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = p.level().getEntity(a.npc());
        if (!recepcionista(npc) || npc.distanceToSqr(p) > 100) return;
        switch (a.acao()) {
            case REGISTRAR -> {
                if (registrado(p)) { abrir(p, npc, "Você já é da Guilda.", false); return; }
                dados(p).putBoolean("registrado", true);
                dados(p).putInt("rank", 0);
                darCartao(p);
                p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
                Sistema.avisar(p, "Bem-vindo à Guilda de Aventureiros", "Você recebeu o Cartão de Aventureiro. Rank inicial: F.", Sistema.AZUL);
                abrir(p, npc, "Registro feito! Guarde bem o seu cartão.", true);
            }
            case CARTAO -> {
                if (!registrado(p)) { abrir(p, npc, "Registre-se primeiro.", false); return; }
                if (temCartao(p)) { abrir(p, npc, "Você já está com o seu cartão.", false); return; }
                darCartao(p);
                abrir(p, npc, "Aqui está uma segunda via do cartão.", true);
            }
            case PROVA -> iniciarProva(p, npc);
            default -> abrir(p, npc, "", true);
        }
    }

    // ------------------------------------------------------------------ prova de promoção

    private static void base(Skeleton e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private static void iniciarProva(ServerPlayer p, Entity npc) {
        int alvo = indice(p) + 1;
        if (!registrado(p)) { abrir(p, npc, "Registre-se primeiro.", false); return; }
        if (alvo >= ConfigJogo.totalRanks()) { abrir(p, npc, "Você já chegou ao topo.", false); return; }
        if (PROVAS.containsKey(p.getUUID())) { abrir(p, npc, "Sua prova já está em andamento.", false); return; }
        if (Progressao.dados(p).nivel() < ConfigJogo.nivelMinDoRank(alvo)) { abrir(p, npc, "Você ainda não tem nível para essa prova.", false); return; }
        if (dados(p).getLong("espera") > System.currentTimeMillis()) { abrir(p, npc, "Descanse um pouco antes de tentar de novo.", false); return; }

        ServerLevel nivel = p.serverLevel();
        Skeleton ex = EntityType.SKELETON.create(nivel);
        if (ex == null) { abrir(p, npc, "O examinador não pôde vir agora.", false); return; }
        Vec3 dir = p.position().subtract(npc.position());
        dir = dir.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 pos = p.position().add(dir.scale(4));
        ex.moveTo(pos.x, p.getY(), pos.z, p.getYRot() + 180, 0);
        ex.addTag(TAG);
        ex.getPersistentData().putString("frx_dono", p.getUUID().toString());

        Item[] espada = {Items.IRON_SWORD, Items.IRON_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.NETHERITE_SWORD};
        Item[] elmo = {Items.IRON_HELMET, Items.IRON_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET, Items.NETHERITE_HELMET};
        Item[] peito = {Items.IRON_CHESTPLATE, Items.IRON_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_CHESTPLATE};
        ex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(espada[alvo]));
        ex.setItemSlot(EquipmentSlot.HEAD, new ItemStack(elmo[alvo]));
        ex.setItemSlot(EquipmentSlot.CHEST, new ItemStack(peito[alvo]));
        for (EquipmentSlot s : EquipmentSlot.values()) ex.setDropChance(s, 0f);
        base(ex, Attributes.MAX_HEALTH, 50 + 25 * alvo);
        base(ex, Attributes.ATTACK_DAMAGE, 2 + alvo);
        base(ex, Attributes.MOVEMENT_SPEED, 0.27 + 0.01 * alvo);
        base(ex, Attributes.KNOCKBACK_RESISTANCE, 0.3);
        base(ex, Attributes.FOLLOW_RANGE, 48);
        Zonas.aplicarNivel(ex, ConfigJogo.nivelMinDoRank(alvo));   // força de um monstro do nível daquele rank
        ex.setHealth(ex.getMaxHealth());
        ex.setCustomName(Component.literal("Examinador da Guilda · Prova Rank " + ConfigJogo.nomeRank(alvo)).withStyle(ChatFormatting.GOLD));
        ex.setCustomNameVisible(true);
        ex.setPersistenceRequired();
        ex.setTarget(p);
        nivel.addFreshEntity(ex);

        PROVAS.put(p.getUUID(), new Prova(ex.getUUID(), alvo, System.currentTimeMillis() + DURACAO_MS));
        p.closeContainer();
        nivel.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1f, 1f);
        Sistema.avisar(p, "Prova de promoção: Rank " + ConfigJogo.nomeRank(alvo), "Derrote o Examinador da Guilda em 3 minutos, sem morrer.", Sistema.AZUL);
    }

    private static Entity examinador(MinecraftServer s, Prova pr) {
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(pr.examinador());
            if (e != null) return e;
        }
        return null;
    }

    private static void encerrar(MinecraftServer s, UUID jogador) {
        Prova pr = PROVAS.remove(jogador);
        if (pr == null) return;
        Entity e = examinador(s, pr);
        if (e != null && e.isAlive()) e.discard();
    }

    private static void falhar(ServerPlayer p, String motivo) {
        encerrar(p.server, p.getUUID());
        dados(p).putLong("espera", System.currentTimeMillis() + ESPERA_MS);
        Sistema.avisar(p, "Prova de promoção falhou", motivo + " Tente de novo em 10 minutos.", Sistema.VERMELHO);
    }

    private static void vencer(ServerPlayer p, Prova pr) {
        PROVAS.remove(p.getUUID());
        dados(p).putInt("rank", pr.alvo());
        String r = ConfigJogo.nomeRank(pr.alvo());
        Moedas.dar(p, premio(pr.alvo()));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        Sistema.avisar(p, "Promoção! Você agora é Rank " + r, "A Guilda reconhece a sua força. Prêmio: " + Moedas.texto(premio(pr.alvo())) + ".", Sistema.OURO);
        p.sendSystemMessage(Component.literal("Benefícios do Rank " + r + ": " + beneficios(pr.alvo()) + ".").withStyle(ChatFormatting.GREEN));
        dados(p).putLong("soldoDia", 0);
        pagarSoldo(p);
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("⚔ " + p.getGameProfile().getName() + " foi promovido ao Rank " + r + " da Guilda de Aventureiros!")
                .withStyle(ChatFormatting.GOLD), false);
        atualizarCartoes(p);
        Progressao.atualizarNome(p);
        Rede.sincronizar(p);
        Progressao.enviarAoPainel(p, false);
    }

    private static ServerPlayer dono(Entity examinador) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return null;
        try { return s.getPlayerList().getPlayer(UUID.fromString(examinador.getPersistentData().getString("frx_dono"))); }
        catch (Exception e) { return null; }
    }

    /** Só o dono da prova fere o examinador. */
    @SubscribeEvent
    public void soODono(LivingAttackEvent ev) {
        if (!ev.getEntity().getTags().contains(TAG)) return;
        Entity quem = ev.getSource().getEntity();
        if (!(quem instanceof ServerPlayer p) || !p.getUUID().toString().equals(ev.getEntity().getPersistentData().getString("frx_dono"))) ev.setCanceled(true);
    }

    /** O examinador só luta com o dono da prova (não ataca moradores nem outros jogadores). */
    @SubscribeEvent
    public void alvo(LivingChangeTargetEvent ev) {
        if (!ev.getEntity().getTags().contains(TAG)) return;
        ServerPlayer d = dono(ev.getEntity());
        if (ev.getNewTarget() != d) ev.setNewTarget(d);
    }

    @SubscribeEvent
    public void semDrops(LivingDropsEvent ev) {
        if (ev.getEntity().getTags().contains(TAG)) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void morte(LivingDeathEvent ev) {
        if (ev.getEntity().level().isClientSide()) return;
        if (ev.getEntity().getTags().contains(TAG)) {
            ServerPlayer p = dono(ev.getEntity());
            Prova pr = p == null ? null : PROVAS.get(p.getUUID());
            if (pr != null && pr.examinador().equals(ev.getEntity().getUUID())) vencer(p, pr);
        } else if (ev.getEntity() instanceof ServerPlayer p && PROVAS.containsKey(p.getUUID())) {
            falhar(p, "Você caiu diante do examinador.");
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || PROVAS.isEmpty() || ev.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = ev.getServer();
        long agora = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Prova>> it = new HashMap<>(PROVAS).entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Prova> x = it.next();
            ServerPlayer p = s.getPlayerList().getPlayer(x.getKey());
            Entity e = examinador(s, x.getValue());
            if (p == null) { encerrar(s, x.getKey()); continue; }
            if (e == null || !e.isAlive()) { if (PROVAS.containsKey(x.getKey())) falhar(p, "O examinador foi embora."); continue; }
            if (agora > x.getValue().fim()) { falhar(p, "O tempo acabou."); continue; }
            if (e.level() != p.level() || e.distanceToSqr(p) > 48 * 48) { falhar(p, "Você abandonou a prova."); continue; }
            long resta = (x.getValue().fim() - agora) / 1000;
            p.displayClientMessage(Component.literal("Prova de promoção · " + resta / 60 + ":" + String.format("%02d", resta % 60)).withStyle(ChatFormatting.GOLD), true);
        }
    }
}
