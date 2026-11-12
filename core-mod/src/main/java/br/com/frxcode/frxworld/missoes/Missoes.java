package br.com.frxcode.frxworld.missoes;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.acesso.PainelApi;
import br.com.frxcode.frxworld.progressao.DadosJogador;
import br.com.frxcode.frxworld.progressao.DadosJogador.RegistroMissao;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.rede.AbrirQuadro;
import br.com.frxcode.frxworld.rede.AcaoMissao;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.rede.SyncRastreador;
import br.com.frxcode.frxworld.rede.VisaoMissao;
import com.google.gson.JsonElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Missões do quadro: catálogo vindo do painel, estados por jogador, aceitar/entregar e progresso. */
public final class Missoes {
    public static final int MAX_ATIVAS = 5;

    private static volatile Map<String, MissaoDef> catalogo = new LinkedHashMap<>();

    private Missoes() {}

    // ------------------------------------------------------------------ catálogo

    public static void recarregar() {
        PainelApi.lerMissoes().whenComplete((arr, erro) -> {
            if (erro != null) {
                FrxWorld.LOG.warn("Não consegui ler as missões do painel: {}", erro.getMessage());
                return;
            }
            Map<String, MissaoDef> novo = new LinkedHashMap<>();
            for (JsonElement e : arr) {
                try {
                    MissaoDef m = MissaoDef.deJson(e.getAsJsonObject());
                    novo.put(m.id(), m);
                } catch (Exception ex) {
                    FrxWorld.LOG.warn("Missão inválida ignorada: {}", e);
                }
            }
            catalogo = novo;
        });
    }

    // ------------------------------------------------------------------ nomes amigáveis

    private static String nomeDoAlvo(MissaoDef m) {
        ResourceLocation id = ResourceLocation.tryParse(m.alvo());
        if (id == null) return m.alvo();
        if (m.caca()) {
            EntityType<?> tipo = ForgeRegistries.ENTITY_TYPES.getValue(id);
            return tipo != null ? tipo.getDescription().getString() : m.alvo();
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null ? new ItemStack(item).getHoverName().getString() : m.alvo();
    }

    private static String textoRecompensa(MissaoDef m) {
        StringBuilder sb = new StringBuilder(m.recompensaXp() + " XP");
        if (m.recompensaMoedas() > 0) sb.append(" · ").append(br.com.frxcode.frxworld.moedas.Moedas.texto(m.recompensaMoedas()));
        for (MissaoDef.ItemRecompensa i : m.itens()) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(i.item()));
            String nome = item != null ? new ItemStack(item).getHoverName().getString() : i.item();
            sb.append(" · ").append(i.qtd()).append("× ").append(nome);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ estado por jogador

    private static int contarItens(ServerPlayer p, String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        if (item == null) return 0;
        int total = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) total += s.getCount();
        return total;
    }

    private static int progresso(ServerPlayer p, MissaoDef m, RegistroMissao r) {
        return m.caca() ? r.progresso : contarItens(p, m.alvo());
    }

    private static boolean liberadaDeNovo(MissaoDef m, RegistroMissao r) {
        return m.repetivel() && System.currentTimeMillis() - r.concluidaEm >= m.intervaloHoras() * 3_600_000L;
    }

    private static boolean preRequisitoOk(DadosJogador d, MissaoDef m) {
        if (m.preRequisitoId() == null || !catalogo.containsKey(m.preRequisitoId())) return true;
        RegistroMissao pre = d.missoes().get(m.preRequisitoId());
        return pre != null && pre.concluida;
    }

    private static VisaoMissao visao(ServerPlayer p, DadosJogador d, MissaoDef m) {
        RegistroMissao r = d.missoes().get(m.id());
        String objetivo = (m.caca() ? "Caçar " : "Entregar ") + m.quantidade() + "× " + nomeDoAlvo(m);
        int estado;
        int prog = 0;
        String nota = "";

        if (r != null && !r.concluida) {
            prog = Math.min(m.quantidade(), progresso(p, m, r));
            estado = prog >= m.quantidade() ? VisaoMissao.PRONTA : VisaoMissao.ATIVA;
        } else if (r != null && !liberadaDeNovo(m, r)) {
            estado = VisaoMissao.CONCLUIDA;
            if (m.repetivel()) {
                long falta = m.intervaloHoras() * 3_600_000L - (System.currentTimeMillis() - r.concluidaEm);
                long h = falta / 3_600_000L, min = (falta % 3_600_000L) / 60_000L;
                nota = "Disponível de novo em " + (h > 0 ? h + "h " : "") + min + "min";
            }
        } else if (d.nivel() < m.nivelMin()) {
            estado = VisaoMissao.BLOQUEADA;
            nota = "Requer nível " + m.nivelMin();
        } else if (!preRequisitoOk(d, m)) {
            estado = VisaoMissao.BLOQUEADA;
            MissaoDef pre = catalogo.get(m.preRequisitoId());
            nota = "Conclua antes: " + (pre != null ? pre.titulo() : "?");
        } else {
            estado = VisaoMissao.DISPONIVEL;
        }
        return new VisaoMissao(m.id(), m.titulo(), m.descricao(), objetivo, m.nivelMin(),
                textoRecompensa(m), estado, prog, m.quantidade(), nota);
    }

    // ------------------------------------------------------------------ quadro

    public static void abrirQuadro(ServerPlayer p) {
        enviarQuadro(p, "");
    }

    private static void enviarQuadro(ServerPlayer p, String mensagem) {
        DadosJogador d = Progressao.dados(p);
        List<VisaoMissao> lista = new ArrayList<>();
        for (MissaoDef m : catalogo.values()) lista.add(visao(p, d, m));
        lista.sort(Comparator.comparingInt(VisaoMissao::estado).thenComparingInt(VisaoMissao::nivelMin));
        if (lista.isEmpty() && mensagem.isEmpty()) mensagem = "O quadro está vazio. Volte mais tarde, aventureiro.";
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirQuadro(lista, mensagem));
    }

    public static void executar(ServerPlayer p, String id, int acao) {
        MissaoDef m = catalogo.get(id);
        DadosJogador d = Progressao.dados(p);
        if (m == null) {
            enviarQuadro(p, "Essa missão não existe mais.");
            return;
        }
        VisaoMissao v = visao(p, d, m);
        String msg;

        switch (acao) {
            case AcaoMissao.ACEITAR -> {
                long ativas = d.missoes().values().stream().filter(r -> !r.concluida).count();
                if (v.estado() != VisaoMissao.DISPONIVEL) msg = "Você não pode aceitar essa missão agora.";
                else if (ativas >= MAX_ATIVAS) msg = "Você já tem " + MAX_ATIVAS + " missões em andamento.";
                else {
                    RegistroMissao r = new RegistroMissao();
                    d.missoes().put(id, r);
                    msg = "Missão aceita: " + m.titulo();
                    p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
                }
            }
            case AcaoMissao.ENTREGAR -> {
                if (v.estado() != VisaoMissao.PRONTA) msg = "A missão ainda não está completa.";
                else {
                    if (!m.caca()) removerItens(p, m.alvo(), m.quantidade());
                    RegistroMissao r = d.missoes().get(id);
                    r.concluida = true;
                    r.concluidaEm = System.currentTimeMillis();
                    r.progresso = 0;
                    darRecompensa(p, m);
                    msg = "Missão concluída: " + m.titulo();
                    br.com.frxcode.frxworld.progressao.Sistema.avisar(p, "Missão concluída", m.titulo() + " — recompensa: " + textoRecompensa(m), br.com.frxcode.frxworld.progressao.Sistema.VERDE);
                    p.sendSystemMessage(Component.literal("✔ " + m.titulo() + " ").withStyle(ChatFormatting.GREEN)
                            .append(Component.literal("+" + textoRecompensa(m)).withStyle(ChatFormatting.GOLD)));
                    p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
                }
            }
            case AcaoMissao.ABANDONAR -> {
                RegistroMissao r = d.missoes().get(id);
                if (r == null || r.concluida) msg = "Essa missão não está em andamento.";
                else {
                    // se já tinha concluído antes (repetível), volta ao registro de concluída antiga não existe: apenas remove
                    d.missoes().remove(id);
                    msg = "Missão abandonada.";
                }
            }
            default -> msg = "";
        }
        enviarQuadro(p, msg);
        sincronizarRastreador(p);
    }

    private static void removerItens(ServerPlayer p, String itemId, int quantidade) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        int falta = quantidade;
        for (ItemStack s : p.getInventory().items) {
            if (falta <= 0) break;
            if (s.is(item)) {
                int tira = Math.min(falta, s.getCount());
                s.shrink(tira);
                falta -= tira;
            }
        }
        p.getInventory().setChanged();
    }

    private static void darRecompensa(ServerPlayer p, MissaoDef m) {
        for (MissaoDef.ItemRecompensa i : m.itens()) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(i.item()));
            if (item == null) continue;
            int resto = i.qtd();
            while (resto > 0) {
                int lote = Math.min(resto, item.getMaxStackSize());
                ItemStack pilha = new ItemStack(item, lote);
                if (!p.getInventory().add(pilha)) p.drop(pilha, false);
                resto -= lote;
            }
        }
        br.com.frxcode.frxworld.moedas.Moedas.dar(p, m.recompensaMoedas());
        br.com.frxcode.frxworld.reinos.Reputacao.porMissao(p, m.recompensaXp());
        Progressao.ganharXp(p, m.recompensaXp());
    }

    // ------------------------------------------------------------------ progresso e HUD

    /** Chamado ao matar um mob: avança missões de caça ativas daquele tipo. */
    public static void aoMatar(ServerPlayer p, LivingEntity mob) {
        ResourceLocation tipo = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (tipo == null) return;
        String id = tipo.toString();
        DadosJogador d = Progressao.dados(p);
        boolean mudou = false;
        for (Map.Entry<String, RegistroMissao> e : d.missoes().entrySet()) {
            RegistroMissao r = e.getValue();
            MissaoDef m = catalogo.get(e.getKey());
            if (r.concluida || m == null || !m.caca() || !m.alvo().equals(id) || r.progresso >= m.quantidade()) continue;
            r.progresso++;
            mudou = true;
            if (r.progresso == m.quantidade()) {
                p.sendSystemMessage(Component.literal("Missão pronta para entregar: " + m.titulo()).withStyle(ChatFormatting.GOLD));
                p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.2f);
            }
        }
        if (mudou) sincronizarRastreador(p);
    }

    public static void sincronizarRastreador(ServerPlayer p) {
        if (!Progressao.temDados(p)) return;
        DadosJogador d = Progressao.dados(p);
        List<SyncRastreador.Linha> linhas = new ArrayList<>();
        d.missoes().forEach((id, r) -> {
            MissaoDef m = catalogo.get(id);
            if (m == null || r.concluida) return;
            int prog = Math.min(m.quantidade(), progresso(p, m, r));
            linhas.add(new SyncRastreador.Linha(m.titulo(), prog, m.quantidade(), prog >= m.quantidade()));
        });
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new SyncRastreador(linhas));
    }
}
