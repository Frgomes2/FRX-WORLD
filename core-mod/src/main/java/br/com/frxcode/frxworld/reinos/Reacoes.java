package br.com.frxcode.frxworld.reinos;

import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.racas.DefRacas;
import br.com.frxcode.frxworld.racas.RacasJogador;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NPCs que reagem a quem o jogador é: o rank da Guilda, a raça que está à mostra, o título em uso
 * e o nome recebido de um rei mudam a fala de guardas, sacerdotes, reis e lojistas.
 */
public final class Reacoes {
    private Reacoes() {}

    private static String um(List<String> l) { return l.get(ThreadLocalRandom.current().nextInt(l.size())); }

    /** Fala de reação para este jogador, ou "" quando o NPC não tem nada de especial a dizer. */
    public static String fala(ServerPlayer p, String papel) {
        String nick = p.getGameProfile().getName();
        boolean registrado = Guilda.registrado(p);
        int rank = Guilda.indice(p);                       // 0 = F ... 6 = S
        String r = ConfigJogo.nomeRank(rank);
        String raca = RacasJogador.racaVisivel(p);         // "" se ainda não escolheu; disfarce conta
        DefRacas.Raca def = DefRacas.de(raca);
        String familia = def == null ? "" : def.familia(), nomeRaca = def == null ? "" : def.nome();
        String titulo = Progressao.temDados(p) ? Progressao.dados(p).titulo() : "";
        String nomeado = Nomeacao.nome(p);
        String trato = !nomeado.isEmpty() ? nomeado : nick;
        boolean trevas = familia.equals("trevas"), luzAlta = raca.equals("anjo") || raca.equals("celestial");
        boolean lenda = rank >= 5 || !nomeado.isEmpty() || titulo.startsWith("Lenda") || titulo.startsWith("Herói");
        List<String> l = new ArrayList<>();

        if (raca.equals("deus")) {
            switch (papel) {
                case "sacerdote" -> l.add("*cai de joelhos* Uma divindade... no meu templo. Perdoe a simplicidade deste altar.");
                case "rei" -> l.add("Até um rei se curva diante de um deus. O trono é seu enquanto estiver entre nós, " + trato + ".");
                case "guarda" -> l.add("*larga a lança e se ajoelha* P-perdão! Não ousaria barrar o seu caminho.");
                default -> l.add("*baixa os olhos* É... é verdade o que dizem. Um deus caminha entre nós.");
            }
            return um(l);
        }

        switch (papel) {
            case "guarda" -> {
                if (trevas && rank < 5) {
                    l.add("Um " + nomeRaca + " dentro das muralhas... Estou de olho em você. Um passo em falso e acabou.");
                    l.add("Mãos onde eu possa ver, " + nomeRaca + ". A cidade tolera, mas não esquece.");
                } else if (lenda) {
                    l.add("*faz uma reverência* " + (rank >= 5 ? "Rank " + r + "! " : "") + "É uma honra, " + trato + ". A cidade dorme tranquila com você aqui.");
                    l.add("*bate continência* Passagem livre para " + (titulo.isEmpty() ? trato : titulo) + "!");
                } else if (rank >= 3) {
                    l.add("Aventureiro Rank " + r + ". Bom ter gente forte por perto. Siga em paz.");
                    l.add("Rank " + r + "? Se a muralha cair, quero você do meu lado.");
                } else if (!registrado) {
                    l.add("Sem cartão da Guilda? Fique onde eu possa ver você.");
                    l.add("Mais um forasteiro sem registro. Procure a recepcionista da Guilda antes de arrumar confusão.");
                } else if (rank == 0) {
                    l.add("Rank F? *nem levanta os olhos* Circulando, novato. Não atrapalhe a patrulha.");
                    l.add("Hm. Rank F. Volte quando tiver matado algo maior que um slime.");
                } else {
                    l.add("Rank " + r + ". Ainda é pouco, mas todo mundo começa de algum lugar.");
                }
                if (luzAlta) l.add("*tira o elmo* Um " + nomeRaca + "... Nunca pensei que veria um de perto. Abençoe a nossa guarda.");
            }
            case "sacerdote" -> {
                if (raca.equals("vampiro")) {
                    l.add("*aperta o amuleto* Um vampiro no meu templo... Não tente nada. A água benta está logo ali.");
                    l.add("Sinto o frio da noite em você, vampiro. Vendo poções, não perdão.");
                } else if (raca.equals("rei_demonio")) {
                    l.add("*recua um passo* O abismo em pessoa... Diga logo o que quer e vá embora, Rei Demônio.");
                } else if (trevas) {
                    l.add("Um filho das trevas diante do altar. A luz não fecha a porta para ninguém... mas eu fico de olho.");
                } else if (luzAlta) {
                    l.add("*junta as mãos* Um " + nomeRaca + "! Rezei a vida inteira por um sinal assim. Este templo é a sua casa.");
                    l.add("A luz em pessoa visita os fiéis. Diga do que precisa, " + nomeRaca + ", e será seu.");
                } else if (raca.equals("espirito") || raca.equals("elfo")) {
                    l.add("A luz gosta de quem carrega magia antiga, " + nomeRaca + ". Seja bem-vindo.");
                } else if (lenda) {
                    l.add("As preces do povo carregam o seu nome, " + trato + ". Que a luz continue ao seu lado.");
                }
            }
            case "rei" -> {
                if (!nomeado.isEmpty()) l.add("Sempre uma alegria rever " + nomeado + ". O nome que carrega pesa mais que qualquer coroa.");
                if (raca.equals("rei_demonio")) l.add("Um Rei Demônio na minha corte. Somos dois soberanos, então. Que venha em paz.");
                else if (raca.equals("dragao")) l.add("Um dragão em forma de gente... Meus antepassados caçavam os seus. Hoje prefiro tê-lo como aliado.");
                else if (rank >= 5) l.add("Um Rank " + r + " diante do trono. A Guilda fala muito de você, " + trato + ".");
                else if (rank <= 1 && nomeado.isEmpty()) l.add("Você ainda é verde, " + nick + ", mas todo herói que conheci começou assim.");
            }
            case "taverneiro" -> {
                if (rank >= 5) l.add("Olha quem chegou! Rank " + r + " na minha taverna. A primeira caneca é por conta da casa, " + trato + ".");
                else if (!registrado) l.add("Sem cartão da Guilda eu só posso servir bebida. Fale com a recepcionista e volte para o quadro.");
                else if (rank == 0) l.add("Novato, hein? As missões de Rank F ficam embaixo do quadro. Comece por elas e não morra.");
                else l.add("Rank " + r + ", certo? Separei no quadro o que serve para você.");
                if (raca.equals("anao")) l.add("Um anão! Finalmente alguém que aguenta a cerveja da casa.");
                if (raca.equals("oni")) l.add("Só peço uma coisa, oni: se for brigar, que seja lá fora.");
            }
            case "sabio" -> {
                if (raca.equals("elfo")) l.add("Um elfo! Vocês viram nascer metade dos livros que eu guardo.");
                else if (raca.equals("espirito")) l.add("Fascinante... um espírito que conversa. Posso fazer umas perguntas depois?");
                else if (trevas) l.add("Conhecimento não tem lado, " + nomeRaca + ". Só não toque nos tomos sagrados.");
                else if (rank >= 4) l.add("Rank " + r + " e ainda vem estudar. É assim que se chega longe.");
            }
            case "cartografo" -> {
                if (rank >= 4) l.add("Rank " + r + "? Então você já pisou em lugares que eu só desenhei. Conte-me o que viu!");
                else if (rank <= 1) l.add("Cuidado com as bordas do mapa, " + nick + ". Lá os monstros não perdoam Rank " + r + ".");
            }
            case "ferreiro", "armeiro" -> {
                if (raca.equals("anao")) l.add("Um anão sabe reconhecer bom aço. Pode examinar à vontade, irmão de forja.");
                else if (raca.equals("gigante")) l.add("Para o seu tamanho eu vou precisar de muito mais metal, gigante.");
                else if (rank >= 5) l.add("Uma lâmina minha na mão de um Rank " + r + " é a melhor propaganda que existe.");
                else if (rank == 0 && registrado) l.add("Rank F? Comece com ferro. O resto você compra quando sobreviver.");
            }
            case "mercador", "comprador", "cozinheiro" -> {
                if (raca.equals("halfling")) l.add("Um halfling! Com vocês eu nunca levo a melhor na pechincha.");
                else if (raca.equals("vampiro") && papel.equals("cozinheiro")) l.add("Hã... Não sirvo o que você costuma beber. Mas a carne é malpassada.");
                else if (rank >= 5) l.add("Rank " + r + "! Para você eu sempre tenho o melhor da prateleira.");
                else if (trevas) l.add("Dinheiro de " + nomeRaca.toLowerCase() + " vale igual. Só não assuste os outros clientes.");
            }
            case "banqueiro", "cambista" -> {
                if (rank >= 5) l.add("Clientes Rank " + r + " têm atendimento preferencial. Em que posso servir, " + trato + "?");
                else if (raca.equals("dragonoide") || raca.equals("dragao")) l.add("Dizem que a sua gente dorme em cima do ouro. Aqui ele fica mais seguro, garanto.");
            }
            case "recepcionista" -> {
                if (rank >= 5) l.add("*levanta depressa* " + trato + "! Rank " + r + "... os novatos não param de falar de você.");
                else if (!registrado) l.add("Rosto novo! Registre-se e receba o seu Cartão de Aventureiro.");
            }
            default -> { }
        }
        return l.isEmpty() ? "" : um(l);
    }
}
