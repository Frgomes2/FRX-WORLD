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
    public static final String SAIR = "sair", LOJA = "loja", MISSAO = "missao", REINO = "reino", QUADRO = "quadro", FALAR = "falar";

    private Dialogos() {}

    /** Papéis que abrem a caixa de fala (os outros têm menu próprio). */
    public static boolean temDialogo(String papel) {
        return switch (papel) {
            case "rei", "taverneiro", "sacerdote", "sabio", "cartografo" -> true;
            default -> false;
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
        String reacao = Reacoes.fala(p, papel);
        if (!reacao.isEmpty() && ThreadLocalRandom.current().nextInt(100) < 70) return reacao;
        String doPainel = falaDoPainel(papel);
        if (!doPainel.isEmpty()) return doPainel.replace("{jogador}", p.getGameProfile().getName());
        Reino r = Reputacao.reino(npc.getPersistentData().getString("frx_reino"));
        String reino = r != null ? r.nome() : "este reino";
        return switch (papel) {
            case "rei" -> "Seja bem-vindo a " + reino + ", " + p.getGameProfile().getName() + ".";
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
        for (String[] o : opcoes(papel)) {
            CompoundTag x = new CompoundTag();
            x.putString("id", o[0]);
            x.putString("texto", o[1]);
            l.add(x);
        }
        d.put("opcoes", l);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("dialogo", d));
    }

    /** Opções da conversa, na mesma ordem no servidor e no cliente (o cliente devolve o índice). */
    private static List<String[]> opcoes(String papel) {
        List<String[]> opcoes = new ArrayList<>();
        switch (papel) {
            case "rei" -> {
                opcoes.add(new String[]{MISSAO, "Qual é a minha missão?"});
                opcoes.add(new String[]{REINO, "Fale-me do reino"});
            }
            case "taverneiro" -> {
                opcoes.add(new String[]{QUADRO, "Ver o quadro de missões"});
                opcoes.add(new String[]{FALAR, "Alguma novidade?"});
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
        List<String[]> lista = opcoes(papel);
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
                String nivelRep = Reputacao.NIVEIS.get(Reputacao.indice(pontos)).nome();
                abrir(p, npc, (r != null ? r.nome() : "Nosso reino") + " o reconhece como " + nivelRep + " (" + pontos + " de reputação). "
                        + (pontos >= 500 ? "Nossos mercadores guardam coisas especiais para você." : "Ajude nosso povo e isso vai mudar."));
            }
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
            case FALAR -> abrir(p, npc, falaPadrao(p, npc, papel));
            default -> p.closeContainer();
        }
    }
}
