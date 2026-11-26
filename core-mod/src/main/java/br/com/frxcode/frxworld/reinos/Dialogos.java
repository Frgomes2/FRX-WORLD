package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Caixa de fala com o retrato do NPC e opções de resposta, no lugar da frase solta no chat.
 * Vale para o rei, o taverneiro e os lojistas do reino (sacerdote, sábio, cartógrafo...).
 */
public final class Dialogos {
    /** Opções: id curto que volta do cliente. */
    public static final String SAIR = "sair", LOJA = "loja", MISSAO = "missao", REINO = "reino", QUADRO = "quadro", FALAR = "falar", FAVOR = "favor", PERDAO = "perdao", PEDIDO = "pedido", ENTREGAR = "entregar", DESENCANTO = "desencanto", BENCAO = "bencao";

    private Dialogos() {}

    /** Papéis que abrem a caixa de fala (os outros têm menu próprio). */
    public static boolean temDialogo(String papel) {
        return switch (papel) {
            case "rei", "taverneiro", "sacerdote", "sabio", "cartografo" -> true;
            default -> papel.startsWith("folga_");
        };
    }

    private static String nome(Entity npc, String padrao) {
        return npc.getCustomName() != null ? npc.getCustomName().getString() : padrao;
    }

    /** Falas extras escritas no painel (aba Reinos → diálogos), por papel. */
    private static String falaDoPainel(String papel) {
        JsonObject o = ConfigJogo.secaoCrua("dialogos");
        try {
            if (o != null && o.has(papel) && o.get(papel).isJsonArray()) {
                JsonArray a = o.getAsJsonArray(papel);
                if (a.size() > 0) return a.get(ThreadLocalRandom.current().nextInt(a.size())).getAsString();
            }
        } catch (Exception ignorado) { }
        return "";
    }

    private static String falaPadrao(ServerPlayer p, Entity npc, String papel) {
        if (papel.startsWith("folga_")) return VidaNpc.falaFolga(p, npc);
        String lembranca = VidaNpc.memoria(p, papel, npc.getPersistentData().getString("frx_reino"));
        if (!lembranca.isEmpty() && ThreadLocalRandom.current().nextInt(100) < 50) return lembranca;
        String reacao = Reacoes.fala(p, papel);
        if (!reacao.isEmpty() && ThreadLocalRandom.current().nextInt(100) < 70) return reacao;
        String doPainel = falaDoPainel(papel);
        if (!doPainel.isEmpty()) return doPainel.replace("{jogador}", p.getGameProfile().getName());
        Reino r = Reputacao.reino(npc.getPersistentData().getString("frx_reino"));
        String reino = r != null ? r.nome() : "este reino";
        return switch (papel) {
            case "rei" -> Reputacao.hostilidade(p, npc.getPersistentData().getString("frx_reino")) > 0
                    ? "Você tem coragem de aparecer diante de mim, " + p.getGameProfile().getName() + ". Fale logo."
                    : "Seja bem-vindo a " + reino + ", " + p.getGameProfile().getName() + ".";
            case "taverneiro" -> "Senta aí, " + p.getGameProfile().getName() + ". O quadro está cheio de trabalho hoje.";
            case "sacerdote" -> "Que a luz guie seus passos, viajante.";
            case "sabio" -> "Conhecimento é a arma mais poderosa. Tenho livros que nem o rei leu.";
            case "cartografo" -> "Mapas de terras distantes, quer ver?";
            default -> "Mantenha a ordem no reino.";
        };
    }

    // ------------------------------------------------------------------ abrir

    public static void abrir(ServerPlayer p, Entity npc, String texto) {
        String papel = npc.getPersistentData().getString("frx_papel");
        CompoundTag d = new CompoundTag();
        d.putInt("npc", npc.getId());
        d.putString("nome", nome(npc, "Morador"));
        d.putString("papel", papel);
        d.putString("texto", texto == null || texto.isEmpty() ? falaPadrao(p, npc, papel) : texto);
        Reino r = Reputacao.reino(npc.getPersistentData().getString("frx_reino"));
        d.putString("reino", r != null ? r.nome() : "");

        ListTag l = new ListTag();
        for (String[] o : opcoes(papel, p, npc.getPersistentData().getString("frx_reino"))) {
            CompoundTag x = new CompoundTag();
            x.putString("id", o[0]);
            x.putString("texto", o[1]);
            l.add(x);
        }
        d.put("opcoes", l);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("dialogo", d));
    }

    /** Opções da conversa, na mesma ordem no servidor e no cliente (o cliente devolve o índice). */
    private static List<String[]> opcoes(String papel, ServerPlayer p, String reinoId) {
        List<String[]> opcoes = new ArrayList<>();
        switch (papel) {
            case "rei" -> {
                opcoes.add(new String[]{MISSAO, "Qual é a minha missão?"});
                opcoes.add(new String[]{REINO, "Fale-me do reino"});
                if (!reinoId.isEmpty() && Reputacao.pontos(p, reinoId) <= Reputacao.MALVISTO)
                    opcoes.add(new String[]{PERDAO, "Quero limpar meu nome (multa: " + br.com.frxcode.frxworld.moedas.Moedas.texto(Hostilidade.multa(p, reinoId)) + ")"});
                else if (!reinoId.isEmpty() && FavorReal.liberado(p, reinoId))
                    opcoes.add(new String[]{FAVOR, "Tem algum serviço para mim, Majestade?"});
            }
            case "taverneiro" -> {
                opcoes.add(new String[]{QUADRO, "Ver o quadro de missões"});
                opcoes.add(new String[]{FALAR, "Alguma novidade?"});
            }
            case "folga_ferreiro", "folga_armeiro" -> {
                opcoes.add(new String[]{PEDIDO, "Precisa de alguma coisa?"});
                opcoes.add(new String[]{ENTREGAR, "Trouxe o que você pediu"});
                opcoes.add(new String[]{FALAR, "O que andam dizendo por aí?"});
            }
            case "folga_mercador" -> opcoes.add(new String[]{FALAR, "O que andam dizendo por aí?"});
            case "sabio" -> {
                opcoes.add(new String[]{LOJA, "Ver o que você vende"});
                if (Desencanto.ativo()) opcoes.add(new String[]{DESENCANTO, "Extrair um encantamento"});
                if ("lubelius".equals(reinoId)) opcoes.add(new String[]{BENCAO, "Pedir a bênção para a minha armadura"});
                opcoes.add(new String[]{FALAR, "Conversar"});
            }
            default -> {
                opcoes.add(new String[]{LOJA, "Ver o que você vende"});
                opcoes.add(new String[]{FALAR, "Conversar"});
            }
        }
        opcoes.add(new String[]{SAIR, "Até logo"});
        return opcoes;
    }

    // ------------------------------------------------------------------ respostas

    public static void acao(ServerPlayer p, AcaoMenu a, Entity npc) {
        String papel = npc.getPersistentData().getString("frx_papel"), reinoId = npc.getPersistentData().getString("frx_reino");
        List<String[]> lista = opcoes(papel, p, reinoId);
        int i = (int) a.valor();
        String op = i >= 0 && i < lista.size() ? lista.get(i)[0] : SAIR;
        switch (op) {
            case MISSAO -> {
                p.closeContainer();
                Campanhas.falar(p, reinoId);
            }
            case REINO -> {
                Reino r = Reputacao.reino(reinoId);
                int pontos = Reputacao.pontos(p, reinoId);
                String nivelRep = Reputacao.nomeNivel(pontos);
                Reino rv = Reputacao.reino(Reputacao.rival(reinoId) == null ? "" : Reputacao.rival(reinoId));
                abrir(p, npc, (r != null ? r.nome() : "Nosso reino") + " o reconhece como " + nivelRep + " (" + pontos + " de reputação). "
                        + (pontos <= Reputacao.MALVISTO ? "Pague a multa ou prove seu valor, e talvez isso mude."
                        : pontos >= 500 ? "Nossos mercadores guardam coisas especiais para você." : "Ajude nosso povo e isso vai mudar.")
                        + (rv != null ? " E saiba: quem serve a " + rv.nome() + " perde o nosso respeito." : ""));
            }
            case PERDAO -> abrir(p, npc, Hostilidade.pagarMulta(p, reinoId));
            case FAVOR -> abrir(p, npc, FavorReal.falar(p, reinoId));
            case QUADRO -> {
                p.closeContainer();
                br.com.frxcode.frxworld.missoes.Missoes.abrirQuadro(p);
            }
            case LOJA -> {
                p.closeContainer();
                if (npc instanceof AbstractVillager v) {
                    v.setTradingPlayer(p);
                    v.openTradingScreen(p, Component.literal(nome(npc, "Loja")), 5);
                }
            }
            case DESENCANTO -> Desencanto.abrir(p, npc);
            case BENCAO -> Bencao.abrir(p, npc);
            case PEDIDO -> abrir(p, npc, VidaNpc.favor(p, npc, false));
            case ENTREGAR -> abrir(p, npc, VidaNpc.favor(p, npc, true));
            case FALAR -> abrir(p, npc, papel.startsWith("folga_") || papel.equals("taverneiro") && VidaNpc.noite(npc.level())
                    ? VidaNpc.conversa(p, npc) : falaPadrao(p, npc, papel));
            default -> p.closeContainer();
        }
    }
}
