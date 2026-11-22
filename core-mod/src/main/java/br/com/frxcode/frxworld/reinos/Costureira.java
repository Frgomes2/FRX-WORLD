package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Costureira (NPC de papel "costureira"): mesa de costura (MenuCostura) com cinco serviços: costurar bolsos na calça (até 12),
 * passar os bolsos de uma calça para outra, remendar armaduras e élitros, bordar (acabamento de armadura e nome colorido)
 * e o manto de disfarce (30 min sem os guardas reconhecerem quem é Hostil; não vale para Inimigo).
 * A calça guarda só QUANTOS bolsos tem (marca frx_bolsos). O que está dentro dos bolsos fica guardado no personagem:
 * vale enquanto ele veste uma calça com bolsos; tirando a calça (ou vestindo uma com menos bolsos), o que não cabe volta para a mochila.
 */
public class Costureira {
    public static final int COSTURAR = 0;
    public static final int MAXIMO = 12;
    /** Marca na calça: quantos bolsos ela tem. */
    public static final String TAG = "frx_bolsos";
    private static final long PRECO = 200;                  // por bolso: 2 de ouro, em cobre
    private static final String GUARDADO = "frx_bolso_itens";
    private static final String FECHO = "frx_fecho";        // marca das placas que tampam os espaços que não são bolso
    private static final int POR_LINHA = 6, LINHAS = 2, CASAS = LINHAS * 9;

    public Costureira() {}

    // ------------------------------------------------------------------ a calça

    public static boolean calca(ItemStack s) { return !s.isEmpty() && LivingEntity.getEquipmentSlotForItem(s) == EquipmentSlot.LEGS; }

    public static int bolsos(ItemStack s) { return calca(s) && s.hasTag() ? Math.max(0, Math.min(MAXIMO, s.getTag().getInt(TAG))) : 0; }

    /** Quantos bolsos o jogador tem agora: os da calça que está vestindo. */
    public static int capacidade(Player p) { return bolsos(p.getItemBySlot(EquipmentSlot.LEGS)); }

    /** A calça que a Costureira vai mexer: a que está na mão; se a mão não tem calça, a que está vestida. */
    private static ItemStack alvo(ServerPlayer p) {
        if (calca(p.getMainHandItem())) return p.getMainHandItem();
        ItemStack v = p.getItemBySlot(EquipmentSlot.LEGS);
        return calca(v) ? v : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ a mesa de costura (MenuCostura + cliente/TelaCostura)

    /** Ações da mesa (AcaoMenu "costureira"). O nome bordado vem por AcaoHabilidade.BORDAR_NOME (precisa de texto). */
    public static final int PASSAR = 1, REMENDAR = 2, ACABAMENTO = 3, DISFARCE = 5;
    private static final long PRECO_PASSAR = 50, PRECO_ACABAMENTO = 1500, PRECO_TIRAR = 100, PRECO_NOME = 500, PRECO_DISFARCE = 3000;
    private static final long DISFARCE_MS = 30 * 60_000L, DISFARCE_MAX_MS = 60 * 60_000L;
    private static final String DISFARCE_ATE = "frx_disfarce_ate";
    /** Cores do nome bordado, na ordem da tela. */
    public static final ChatFormatting[] CORES = {ChatFormatting.WHITE, ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.DARK_GREEN,
            ChatFormatting.AQUA, ChatFormatting.DARK_AQUA, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE, ChatFormatting.RED,
            ChatFormatting.DARK_RED, ChatFormatting.GRAY};

    /** Remendo: cobre por ponto de durabilidade, pelo material (a tela faz a mesma conta). */
    public static double fatorRemendo(ItemStack s) {
        if (s.getItem() instanceof net.minecraft.world.item.ElytraItem) return 3.0;
        if (s.getItem() instanceof net.minecraft.world.item.ArmorItem a)
            return 0.4 + 0.2 * a.getMaterial().getDefenseForType(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE) + 0.3 * a.getMaterial().getToughness();
        return 0;
    }

    public static long custoRemendo(ItemStack s) {
        double f = fatorRemendo(s);
        return f <= 0 || !s.isDamaged() ? 0 : Math.max(10, (long) Math.ceil(s.getDamageValue() * f));
    }

    private static boolean reliquia(ItemStack s) { return s.hasTag() && s.getTag().contains("frx_codex"); }

    // ---- disfarce

    public static boolean disfarcado(Player p) { return pers(p).getLong(DISFARCE_ATE) > System.currentTimeMillis(); }

    private static void rasgarDisfarce(ServerPlayer p, String motivo) {
        if (!disfarcado(p)) return;
        pers(p).remove(DISFARCE_ATE);
        p.sendSystemMessage(Component.literal("✂ Seu manto de disfarce rasgou: " + motivo).withStyle(ChatFormatting.RED));
    }

    // ---- pagamento (bolsa e, se faltar, banco)

    private static boolean pagar(ServerPlayer p, long valor) {
        long bolsa = Moedas.contar(p), banco = br.com.frxcode.frxworld.moedas.Banco.saldo(p);
        if (bolsa + banco < valor) return false;
        long daBolsa = Math.min(bolsa, valor);
        if (daBolsa > 0 && !Moedas.cobrar(p, daBolsa)) return false;
        if (valor - daBolsa > 0) br.com.frxcode.frxworld.moedas.Banco.definirSaldo(p, banco - (valor - daBolsa));
        return true;
    }

    private static long dinheiro(ServerPlayer p) { return Moedas.contar(p) + br.com.frxcode.frxworld.moedas.Banco.saldo(p); }

    // ---- abrir e responder

    private static CompoundTag info(ServerPlayer p, Entity npc, String msg, boolean ok) {
        CompoundTag d = new CompoundTag();
        d.putInt("npc", npc.getId());
        d.putString("nome", npc.getCustomName() != null ? npc.getCustomName().getString() : "Costureira");
        d.putLong("dinheiro", dinheiro(p));
        d.putLong("precoBolso", PRECO); d.putLong("precoPassar", PRECO_PASSAR); d.putLong("precoAcabamento", PRECO_ACABAMENTO);
        d.putLong("precoTirar", PRECO_TIRAR); d.putLong("precoNome", PRECO_NOME); d.putLong("precoDisfarce", PRECO_DISFARCE);
        d.putInt("maximo", MAXIMO);
        d.putLong("disfarceMs", Math.max(0, pers(p).getLong(DISFARCE_ATE) - System.currentTimeMillis()));
        String reino = npc.getPersistentData().getString("frx_reino");
        d.putBoolean("inimigo", !reino.isEmpty() && Reputacao.hostilidade(p, reino) >= 3);
        // acabamentos que o servidor conhece (padrões e materiais), com o nome já pronto para mostrar
        var ra = p.server.registryAccess();
        net.minecraft.nbt.ListTag pads = new net.minecraft.nbt.ListTag(), mats = new net.minecraft.nbt.ListTag();
        var regP = ra.registryOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN);
        for (var k : regP.keySet().stream().sorted().toList()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", k.toString());
            t.putString("nome", Component.Serializer.toJson(regP.get(k).description()));
            pads.add(t);
        }
        var regM = ra.registryOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL);
        for (var k : regM.keySet().stream().sorted().toList()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", k.toString());
            t.putString("nome", Component.Serializer.toJson(regM.get(k).description()));
            mats.add(t);
        }
        d.put("padroes", pads);
        d.put("materiais", mats);
        d.putString("msg", msg);
        d.putBoolean("ok", ok);
        return d;
    }

    public static void abrir(ServerPlayer p, Entity npc) {
        CompoundTag d = info(p, npc, "", true);
        net.minecraftforge.network.NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, jog) -> new MenuCostura(id, inv, npc.getId(), d),
                Component.literal(d.getString("nome"))), buf -> { buf.writeInt(npc.getId()); buf.writeNbt(d); });
    }

    private static void responder(ServerPlayer p, Entity npc, String msg, boolean ok) {
        p.playNotifySound(ok ? SoundEvents.ARMOR_EQUIP_LEATHER : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, ok ? 1.2f : 1f);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("costureira", info(p, npc, msg, ok)));
    }

    private static Entity npcValido(ServerPlayer p, int id) {
        Entity npc = p.level().getEntity(id);
        if (npc == null || npc.distanceToSqr(p) > 144 || !"costureira".equals(npc.getPersistentData().getString("frx_papel"))) return null;
        return p.containerMenu instanceof MenuCostura m && m.npc == id ? npc : null;
    }

    public static void acao(ServerPlayer p, AcaoMenu a) {
        Entity npc = npcValido(p, a.npc());
        if (npc == null) return;
        MenuCostura m = (MenuCostura) p.containerMenu;
        ItemStack peca = m.caixa.getItem(MenuCostura.PECA), segunda = m.caixa.getItem(MenuCostura.SEGUNDA);
        String msg;
        boolean ok = false;
        switch (a.acao()) {
            case COSTURAR -> {
                int tem = bolsos(peca);
                long qtd = Math.min(a.valor(), MAXIMO - tem);
                if (!calca(peca)) msg = "Coloque uma calça no espaço da peça.";
                else if (tem >= MAXIMO) msg = "Essa calça já tem os " + MAXIMO + " bolsos. Não cabe mais nenhum.";
                else if (qtd < 1) return;
                else if (!pagar(p, qtd * PRECO)) msg = "Faltam moedas: " + (qtd == 1 ? "o bolso custa " : "os " + qtd + " bolsos custam ") + Moedas.texto(qtd * PRECO) + ".";
                else {
                    peca.getOrCreateTag().putInt(TAG, tem + (int) qtd);
                    msg = (qtd == 1 ? "Bolso costurado." : qtd + " bolsos costurados.") + " A calça agora tem " + (tem + qtd) + " de " + MAXIMO + ".";
                    ok = true;
                }
            }
            case PASSAR -> {
                int de = bolsos(peca), para = bolsos(segunda), qtd = Math.min(de, MAXIMO - para);
                if (!calca(peca) || !calca(segunda)) msg = "Coloque a calça velha no espaço da peça e a nova no da segunda calça.";
                else if (de == 0) msg = "A calça velha não tem bolsos para passar.";
                else if (qtd == 0) msg = "A calça nova já tem os " + MAXIMO + " bolsos.";
                else if (!pagar(p, qtd * PRECO_PASSAR)) msg = "Faltam moedas: passar " + qtd + " bolsos custa " + Moedas.texto(qtd * PRECO_PASSAR) + ".";
                else {
                    peca.getOrCreateTag().putInt(TAG, de - qtd);
                    if (de - qtd == 0) peca.getTag().remove(TAG);
                    segunda.getOrCreateTag().putInt(TAG, para + qtd);
                    msg = qtd + (qtd == 1 ? " bolso passou" : " bolsos passaram") + " para a calça nova (" + (para + qtd) + " de " + MAXIMO + ").";
                    ok = true;
                }
            }
            case REMENDAR -> {
                long custo = custoRemendo(peca);
                if (peca.isEmpty()) msg = "Coloque a armadura ou os élitros no espaço da peça.";
                else if (fatorRemendo(peca) <= 0) msg = "Eu remendo armaduras e élitros. Armas, com o ferreiro.";
                else if (!peca.isDamaged()) msg = "Essa peça está inteira. Nada para remendar.";
                else if (!pagar(p, custo)) msg = "Faltam moedas: o remendo custa " + Moedas.texto(custo) + ".";
                else { peca.setDamageValue(0); msg = "Remendado. Ficou como nova."; ok = true; }
            }
            case ACABAMENTO -> {
                var ra = p.server.registryAccess();
                var regP = ra.registryOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN);
                var regM = ra.registryOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL);
                var lp = regP.keySet().stream().sorted().toList();
                var lm = regM.keySet().stream().sorted().toList();
                if (peca.isEmpty() || !peca.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR)) msg = "Coloque uma peça de armadura que aceite acabamento.";
                else if (reliquia(peca)) msg = "Relíquias do Códex já têm a aparência delas.";
                else if (a.valor() < 0) {
                    if (!peca.hasTag() || !peca.getTag().contains("Trim")) msg = "Essa peça não tem acabamento.";
                    else if (!pagar(p, PRECO_TIRAR)) msg = "Faltam moedas: tirar o acabamento custa " + Moedas.texto(PRECO_TIRAR) + ".";
                    else { peca.getTag().remove("Trim"); msg = "Acabamento descosturado."; ok = true; }
                } else {
                    int ip = (int) (a.valor() / 1000), im = (int) (a.valor() % 1000);
                    if (ip >= lp.size() || im >= lm.size()) return;
                    if (!pagar(p, PRECO_ACABAMENTO)) msg = "Faltam moedas: o acabamento custa " + Moedas.texto(PRECO_ACABAMENTO) + ".";
                    else {
                        var hp = regP.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TRIM_PATTERN, lp.get(ip)));
                        var hm = regM.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TRIM_MATERIAL, lm.get(im)));
                        net.minecraft.world.item.armortrim.ArmorTrim.setTrim(ra, peca, new net.minecraft.world.item.armortrim.ArmorTrim(hm, hp));
                        msg = "Acabamento bordado. Ficou uma beleza.";
                        ok = true;
                    }
                }
            }
            case DISFARCE -> {
                String reino = npc.getPersistentData().getString("frx_reino");
                long ate = pers(p).getLong(DISFARCE_ATE), agora = System.currentTimeMillis();
                if (!reino.isEmpty() && Reputacao.hostilidade(p, reino) >= 3) msg = "Para inimigos deste reino, nenhum manto basta. Eu não te vi aqui.";
                else if (ate - agora > DISFARCE_MAX_MS - DISFARCE_MS) msg = "Você já está bem coberto. Volte quando o manto estiver gasto.";
                else if (!pagar(p, PRECO_DISFARCE)) msg = "Faltam moedas: o manto custa " + Moedas.texto(PRECO_DISFARCE) + ".";
                else {
                    pers(p).putLong(DISFARCE_ATE, Math.max(ate, agora) + DISFARCE_MS);
                    for (net.minecraft.world.entity.Mob g : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(32), Hostilidade::guarda))
                        if (g.getTarget() == p) g.setTarget(null);
                    msg = "Pronto. Com esse manto, os guardas não vão te reconhecer por 30 minutos.";
                    ok = true;
                }
            }
            default -> { return; }
        }
        m.caixa.setChanged();
        m.broadcastChanges();
        responder(p, npc, msg, ok);
    }

    /** Nome bordado (AcaoHabilidade.BORDAR_NOME): numero = cor, texto = o nome. */
    public static void bordarNome(ServerPlayer p, int cor, String texto) {
        if (!(p.containerMenu instanceof MenuCostura m)) return;
        Entity npc = npcValido(p, m.npc);
        if (npc == null) return;
        ItemStack peca = m.caixa.getItem(MenuCostura.PECA);
        String nome = texto == null ? "" : texto.replace("§", "").trim().replaceAll("\\s+", " ");
        String msg;
        boolean ok = false;
        if (peca.isEmpty()) msg = "Coloque a peça no espaço da peça.";
        else if (reliquia(peca)) msg = "Relíquias do Códex já têm nome próprio.";
        else if (nome.length() < 2 || nome.length() > 30) msg = "O nome precisa ter de 2 a 30 letras.";
        else if (!pagar(p, PRECO_NOME)) msg = "Faltam moedas: bordar o nome custa " + Moedas.texto(PRECO_NOME) + ".";
        else {
            ChatFormatting c = CORES[Math.max(0, Math.min(CORES.length - 1, cor))];
            peca.setHoverName(Component.literal(nome).withStyle(st -> st.withColor(c).withItalic(false)));
            msg = "Nome bordado: " + nome + ".";
            ok = true;
            m.caixa.setChanged();
            m.broadcastChanges();
        }
        responder(p, npc, msg, ok);
    }

    /** Disfarçado que ataca alguém dentro de uma capital rasga o manto; o manto também acaba sozinho. */
    @SubscribeEvent
    public void aoAtacarDisfarcado(net.minecraftforge.event.entity.living.LivingAttackEvent ev) {
        if (ev.getSource().getEntity() instanceof ServerPlayer p && disfarcado(p) && NpcsReino.reinoDe(p) != null && ev.getEntity() != p)
            rasgarDisfarce(p, "você atacou alguém dentro da cidade.");
    }

    @SubscribeEvent
    public void fimDoDisfarce(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 100 != 7) return;
        long ate = pers(p).getLong(DISFARCE_ATE);
        if (ate > 0 && ate <= System.currentTimeMillis()) {
            pers(p).remove(DISFARCE_ATE);
            p.sendSystemMessage(Component.literal("✂ Seu manto de disfarce se desfez. Os guardas voltam a te reconhecer.").withStyle(ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------------ o que está nos bolsos (guardado no personagem)

    private static CompoundTag pers(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static NonNullList<ItemStack> ler(Player p) {
        NonNullList<ItemStack> l = NonNullList.withSize(MAXIMO, ItemStack.EMPTY);
        CompoundTag pers = pers(p);
        if (pers.contains(GUARDADO)) ContainerHelper.loadAllItems(pers.getCompound(GUARDADO), l);
        return l;
    }

    private static void gravar(Player p, NonNullList<ItemStack> l) {
        boolean vazio = true;
        for (ItemStack s : l) if (!s.isEmpty()) { vazio = false; break; }
        if (vazio) pers(p).remove(GUARDADO);
        else pers(p).put(GUARDADO, ContainerHelper.saveAllItems(new CompoundTag(), l));
    }

    /** Casa da janela (2 linhas de 9) → número do bolso (0 a 11), ou -1 nas 3 colunas da direita, que não são bolso. */
    private static int bolsoDa(int casa) { return casa % 9 < POR_LINHA ? (casa / 9) * POR_LINHA + casa % 9 : -1; }

    private static ItemStack fecho(boolean bolsoQueFalta) {
        ItemStack s = new ItemStack(bolsoQueFalta ? Items.GRAY_STAINED_GLASS_PANE : Items.BLACK_STAINED_GLASS_PANE);
        s.getOrCreateTag().putBoolean(FECHO, true);
        s.setHoverName(Component.literal(bolsoQueFalta ? "Sem bolso aqui: a Costureira costura mais" : " ").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)));
        return s;
    }

    /** Coisas que já guardam coisas (caixas de shulker e parecidos) não entram no bolso. */
    private static boolean cabe(ItemStack s) { return s.getItem().canFitInsideContainerItems() && !(s.hasTag() && s.getTag().getBoolean(FECHO)); }

    /** Janela dos bolsos: um baú de 2 linhas em que só os bolsos costurados aceitam itens. */
    private static final class MenuBolsos extends ChestMenu {
        final SimpleContainer caixa;
        final ServerPlayer dono;
        final int abertos;

        MenuBolsos(int id, Inventory inv, SimpleContainer caixa, ServerPlayer dono, int abertos) {
            super(MenuType.GENERIC_9x2, id, inv, caixa, LINHAS);
            this.caixa = caixa; this.dono = dono; this.abertos = abertos;
            NonNullList<ItemStack> guardado = ler(dono);
            for (int i = 0; i < CASAS; i++) {
                final boolean livre = aberto(i);
                caixa.setItem(i, livre ? guardado.get(bolsoDa(i)) : fecho(bolsoDa(i) >= 0));
                Slot velho = slots.get(i);
                Slot novo = new Slot(caixa, i, velho.x, velho.y) {
                    @Override public boolean mayPlace(ItemStack s) { return livre && cabe(s); }
                    @Override public boolean mayPickup(Player j) { return livre; }
                };
                novo.index = i;
                slots.set(i, novo);
            }
            caixa.addListener(c -> salvar());
        }

        boolean aberto(int casa) { int b = bolsoDa(casa); return b >= 0 && b < abertos; }

        void salvar() {
            NonNullList<ItemStack> l = NonNullList.withSize(MAXIMO, ItemStack.EMPTY);
            for (int i = 0; i < CASAS; i++) if (aberto(i)) l.set(bolsoDa(i), caixa.getItem(i));
            gravar(dono, l);
        }

        @Override
        public void clicked(int casa, int botao, ClickType tipo, Player jogador) {
            if (casa >= 0 && casa < CASAS && !aberto(casa)) { sendAllDataToRemote(); return; }   // placa de fecho: nada acontece
            super.clicked(casa, botao, tipo, jogador);
        }

        @Override public void removed(Player jogador) { super.removed(jogador); salvar(); }
        /** Trocou ou tirou a calça com a janela aberta: fecha. */
        @Override public boolean stillValid(Player jogador) { return jogador.isAlive() && capacidade(jogador) == abertos; }
    }

    // ------------------------------------------------------------------ bolsos dentro do inventário (painel ao lado da mochila)

    /** Manda ao cliente quantos bolsos a calça tem e o que há em cada um (o painel do inventário desenha a partir disto). */
    public static void enviarBolsos(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        d.putInt("n", capacidade(p));
        d.put("itens", ContainerHelper.saveAllItems(new CompoundTag(), ler(p)));
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("bolsosdados", d));
    }

    /**
     * Clique num bolso do painel do inventário. modo: -1 só pede os dados; 0 botão esquerdo (pega, põe ou troca);
     * 1 botão direito (pega metade ou põe um); 2 com Shift (manda o item do bolso para a mochila).
     * Tudo é decidido aqui no servidor, com o item que está "na mão do mouse" (o carregado do inventário).
     */
    public static void clique(ServerPlayer p, int bolso, int modo) {
        if (modo < 0 || modo > 2 || p.isCreative() || p.isSpectator() || !p.isAlive() || p.containerMenu != p.inventoryMenu
                || bolso < 0 || bolso >= capacidade(p)) { enviarBolsos(p); return; }
        arrumar(p);
        NonNullList<ItemStack> l = ler(p);
        ItemStack dentro = l.get(bolso), mao = p.inventoryMenu.getCarried();
        if (modo == 2) {
            if (!dentro.isEmpty()) p.getInventory().add(dentro);
        } else if (mao.isEmpty()) {
            if (!dentro.isEmpty()) {
                if (modo == 1) p.inventoryMenu.setCarried(dentro.split((dentro.getCount() + 1) / 2));
                else { p.inventoryMenu.setCarried(dentro); dentro = ItemStack.EMPTY; }
            }
        } else if (cabe(mao)) {
            if (dentro.isEmpty()) {
                dentro = modo == 1 ? mao.split(1) : mao.split(mao.getCount());
            } else if (ItemStack.isSameItemSameTags(dentro, mao)) {
                int quanto = Math.min(modo == 1 ? 1 : mao.getCount(), dentro.getMaxStackSize() - dentro.getCount());
                if (quanto > 0) { dentro.grow(quanto); mao.shrink(quanto); }
            } else if (modo == 0) {
                p.inventoryMenu.setCarried(dentro);
                dentro = mao;
                mao = ItemStack.EMPTY;
            }
            if (mao.isEmpty() && p.inventoryMenu.getCarried() == mao) p.inventoryMenu.setCarried(ItemStack.EMPTY);
        }
        l.set(bolso, dentro.isEmpty() ? ItemStack.EMPTY : dentro);
        gravar(p, l);
        p.inventoryMenu.broadcastChanges();
        enviarBolsos(p);
    }

    /** Abre os bolsos (tecla ou botão do inventário). */
    public static void abrirBolsos(ServerPlayer p) {
        int n = capacidade(p);
        if (n <= 0) {
            p.displayClientMessage(Component.literal(p.getItemBySlot(EquipmentSlot.LEGS).isEmpty() ? "Você está sem calça. Os bolsos são costurados na calça, pela Costureira."
                    : "Sua calça não tem bolsos. Leve-a à Costureira.").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        arrumar(p);
        p.openMenu(new SimpleMenuProvider((id, inv, jogador) -> new MenuBolsos(id, inv, new SimpleContainer(CASAS), p, n),
                Component.literal("Bolsos da calça · " + n + " de " + MAXIMO)));
    }

    /** O que está em bolso que o jogador não tem mais (tirou a calça, vestiu uma com menos bolsos) volta para a mochila; o que não couber cai no chão. */
    private static void arrumar(ServerPlayer p) {
        if (!pers(p).contains(GUARDADO)) return;
        int n = capacidade(p);
        NonNullList<ItemStack> l = ler(p);
        boolean sobra = false;
        for (int i = n; i < MAXIMO; i++) if (!l.get(i).isEmpty()) { sobra = true; break; }
        if (!sobra) return;
        if (p.containerMenu instanceof MenuBolsos) { p.closeContainer(); l = ler(p); }
        for (int i = n; i < MAXIMO; i++) {
            ItemStack s = l.get(i);
            if (s.isEmpty()) continue;
            l.set(i, ItemStack.EMPTY);
            if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
        }
        gravar(p, l);
        p.displayClientMessage(Component.literal("O que estava nos bolsos voltou para a mochila.").withStyle(ChatFormatting.YELLOW), true);
    }

    @SubscribeEvent
    public void aoPassarTempo(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 20 != 0 || !p.isAlive()) return;
        arrumar(p);
    }

    /** Morrer derruba o que está nos bolsos junto com o resto da mochila (a não ser que o mundo guarde o inventário na morte). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void aoCairItens(LivingDropsEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !pers(p).contains(GUARDADO)) return;
        if (p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        if (p.containerMenu instanceof MenuBolsos) p.doCloseContainer();
        NonNullList<ItemStack> l = ler(p);
        for (ItemStack s : l) if (!s.isEmpty()) ev.getDrops().add(new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), s));
        pers(p).remove(GUARDADO);
    }
}
