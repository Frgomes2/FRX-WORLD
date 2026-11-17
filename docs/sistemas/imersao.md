# Imersão: Sistema, Guilda, habilidade única, nomeação e mundo vivo

[← Índice](../README.md)

Sistemas que dão ao servidor a cara de RPG de anime. Todos são do core mod e guardam os dados do jogador no NBT persistente (`Player.PERSISTED_NBT_TAG`), uma chave por sistema.

| Sistema | Código | Chave no jogador | Painel |
|---|---|---|---|
| Janela do Sistema | `progressao/Sistema.java`, `cliente/HudSistema.java` | — | — |
| Abertura isekai | `acesso/Abertura.java`, `cliente/TelaAbertura.java` | `frx_abertura` | Convites → Abertura do primeiro login |
| Guilda de Aventureiros | `progressao/Guilda.java`, `cliente/TelaGuilda.java` | `frx_guilda` | Progressão (ranks) |
| Missões de rank e urgentes | `missoes/Missoes.java`, `missoes/Urgentes.java` | dados de progressão | Missões |
| Habilidade Única | `habilidades/Unica.java` | `frx_unica` | ficha do jogador |
| Habilidade Suprema | `habilidades/Suprema.java` | `frx_unica` | — |
| Evolução de raça | `racas/Evolucao.java` | `frx_evolucao` | — |
| Nomeação pelo rei | `reinos/Nomeacao.java` | `frx_nomeacao` | — |
| NPCs que reagem | `reinos/Reacoes.java` | — | NPCs → Diálogos (falas normais) |
| Diálogo com retrato | `reinos/Dialogos.java`, `cliente/TelaDialogo.java` | — | NPCs → Diálogos |
| Portais (gates) | `reinos/Portais.java` | — | — |
| Chefes e dano flutuante | `progressao/Combate.java`, `cliente/HudChefe.java`, `cliente/DanoFlutuante.java` | — | — |
| Bestiário | `progressao/Bestiario.java` | progresso do FTB Quests | — |
| Conquistas no livro | `progressao/Marcos.java`, capítulo `conquistas.snbt` | progresso do FTB Quests | — |

## Janela do Sistema

`Sistema.avisar(jogador, título, texto, cor)` mostra a janela azul do "Sistema" no canto da tela. Cores: `AZUL`, `OURO`, `VERDE`, `VERMELHO`, `ROXO`. Todo aviso importante do servidor (promoção, missão concluída, habilidade, portal) passa por ela.

## Abertura isekai

No primeiro login: falas do Sistema, escolha de raça, escolha de classe, despertar da Habilidade Única e nascimento. Teste com `/frx abertura` e `/frx abertura reset`.

- **Música:** a cena toca um som preso ao jogador (segue o teleporte) pelo volume geral, e cala a música de fundo do jogo. O painel escolhe a música, o volume e se ela para ao nascer. `frxworld:abertura` é o arquivo `core-mod/src/main/resources/assets/frxworld/sounds/abertura.ogg`, que fica só no PC (fora do git) e chega aos jogadores dentro do mod.
- **Onde nasce:** no centro da capital do painel, ou no spawn do mundo se o admin o mudou com `/setworldspawn` (veja [reinos.md](reinos.md)).

## Guilda de Aventureiros

- A **recepcionista** registra o jogador e entrega o **Cartão de Aventureiro**.
- O rank (F, E, D, C, B, A, S) só sobe com a **prova de promoção**: derrotar o Examinador em 3 minutos, sem morrer. Falhou, espera 10 minutos.
- A prova exige o nível mínimo do rank, definido no painel (Progressão).
- O título em uso e o rank aparecem acima da cabeça do jogador.
- **Classes de equipamento** (`progressao/ClassesEquip.java`): cada arma e armadura tem uma classe de F a S, inclusive as dos outros mods, e só quem tem aquele rank da Guilda usa. O item pode ficar na mochila ou em baús à vontade.
  - Armadura: defesa (ajustada pela peça) + resistência. Couro = F; cota de malha, ferro e ouro = E; diamante = D; netherita = C; acima disso, B, A e S.
  - Arma (espadas, machados e armas de outros mods): bônus de dano do material. Madeira, ouro e pedra = F; ferro = E; diamante = D; netherita = C; acima, B, A e S. Tridente = D. Ferramentas são livres.
  - Arcos, bestas e outras armas de longe: tabela `DISTANCIA` em `ClassesEquip`. Arco e estilingue = F; besta e zarabatana = E; arco de ender (Twilight) = D; arcos de gelo, buscador e fênix = C; arco triplo, de osso de dragão e arco curto ancestral = B; dreadbow, arco amaldiçoado e raygun = A. Arma de longe de mod fora da tabela vai pela raridade (comum D, incomum C, raro B, épico A). Acima do rank não puxa nem dispara.
  - Armadura acima do rank sai do corpo e vai para a mochila ou, se estiver cheia, para o baú de ender; nunca cai no chão (sem espaço em nenhum dos dois, fica no corpo até abrir vaga). Arma acima do rank não acerta ninguém. A classe aparece na descrição do item.
  - Na primeira entrada depois da regra, toda a armadura do jogador é guardada na mochila.
  - Exceções em `server/config/frx_classes.txt` ou por `/frx classe definir <F..S|livre|automatica>` com o item na mão; `/frx classe` mostra a classe calculada.
- **Benefícios do rank** (por degrau acima de F): +10% de XP e moedas nas missões e 2% de desconto nas lojas e na forja (soma com a reputação, teto de 50%). O cartão mostra o que o próximo rank dá. O soldo diário foi desligado em 16/11.
- **Grupos:** o dano entre aliados está ligado (decisão de 13/11/2026); membros do mesmo grupo podem se ferir.

### Missões de rank

- Cada missão do painel tem **Rank da Guilda** (campo `rankMin`, 0 = F até 6 = S).
- No quadro, o título sai como `[Rank X] título`, e a lista é ordenada por estado, rank e nível.
- Missões de Rank E para cima exigem registro na Guilda. Quem não tem o rank vê o motivo do bloqueio.

### Missões urgentes

- A cada intervalo (padrão 45 min), uma **caçada** do quadro vira urgente por um prazo (padrão 20 min).
- O Sistema avisa quem está online e tem nível e rank para ela. Entregando no prazo, XP e moedas são multiplicados (padrão ×2).
- Depois do prazo, a missão continua valendo com a recompensa normal.
- Painel: **Missões → Missões urgentes**. Admin: `/frx urgente` e `/frx urgente encerrar`.

## Habilidade Única e Habilidade Suprema

- Cada jogador desperta **uma** habilidade passiva, sorteada entre dez. Ela sobe do nível 1 ao 5 pelos monstros abatidos (0, 50, 200, 600 e 1500). Veja com `/unica`.
- No nível 5, `/suprema prova` chama o **Eco** da habilidade: 4 minutos, sem morrer. Falhou, espera 10 minutos.
- Vencendo, a habilidade ganha nome novo, um degrau a mais no passivo e um **efeito ativo**, usado com **Agachar + F** ou `/suprema usar`.

| Única | Suprema | Efeito ativo | Recarga |
|---|---|---|---|
| Grande Sábio | Senhor da Sabedoria | Revela inimigos a 24 blocos e dá Força II por 15 s | 90 s |
| Devorador | Senhor da Gula | Arranca vida dos inimigos a 8 blocos e cura o jogador | 75 s |
| Fúria do Dragão | Ira do Dragão Verdadeiro | Onda que fere e arremessa a 8 blocos | 75 s |
| Corpo de Aço | Fortaleza Absoluta | Resistência IV e corações extras por 8 s | 90 s |
| Passo Sombrio | Soberano das Sombras | Invisível e veloz por 10 s; monstros perdem o alvo | 90 s |
| Olho do Falcão | Olho Divino | Todos os golpes são críticos por 10 s | 90 s |
| Sangue Real | Monarca Carmesim | 30% do dano volta como vida por 10 s | 90 s |
| Filho do Vento | Deus do Vento | Avanço e Velocidade III por 10 s | 45 s |
| Segunda Chance | Fênix Eterna | Cura toda a vida e limpa efeitos ruins | 180 s |
| Instinto do Caçador | Predador Ápice | Marca o inimigo mais forte: +40% de dano nele por 12 s | 75 s |

Admin: `/frx unica sortear`, `/frx unica <id>`, `/frx suprema nivel5`, `/frx suprema dar`, `/frx suprema tirar`.

## Evolução de raça

Quando o jogador cumpre a condição (nível, honra com um reino, campanha ou chefe mundial), a raça da mesma família sobe um degrau, com cena e anúncio. Comando: `/evoluir [raça]`.

## Nomeação pelo rei

- Ao concluir a campanha de um reino, o rei faz a cerimônia. O **primeiro** rei oferece três nomes e o jogador escolhe um no chat.
- Os reis seguintes reconhecem o nome e somam a bênção do reino.
- Cada nomeação dá **+1 coração e +2% de dano**, para sempre, e o título "<Nome>, o Nomeado".
- Quem concluiu a campanha antes de o sistema existir recebe a cerimônia ao falar com o rei.
- Comandos: `/nomeacao`; admin: `/frx nomeacao <reino>` e `/frx nomeacao limpar`.

## NPCs que reagem e diálogo com retrato

- Rei, taverneiro, sacerdote, sábio e cartógrafo abrem a caixa de diálogo com retrato e opções. As falas normais vêm do painel (NPCs → Diálogos).
- `Reacoes.fala(jogador, papel)` troca a fala em cerca de 2 a cada 3 conversas, conforme o **rank**, a **raça à mostra**, o **título** e o **nome recebido**.
- Exemplos: o guarda ignora um Rank F e faz reverência a um Rank A; o sacerdote desconfia de vampiro e reverencia anjo; diante de um Deus, todos se curvam.
- A raça lendária disfarçada conta como a raça do disfarce.

## Portais (gates)

Perto de uma capital, a cada 40 minutos há 50% de chance de abrir um portal, que leva a uma dungeon temporária com chefão (ver [aventura.md](aventura.md#dungeon-de-portal-fendajava)). Se ninguém fechar, o portal rompe e vira invasão. Comandos: `/portal`; admin: `/frx portal aqui [rank]` e `/frx portal fechar`.

## Bestiário

- Capítulo do livro de Quests com uma página por tipo de monstro, de todos os mods (433 na última geração).
- A página se completa ao matar o monstro (tarefa `kill` do FTB Quests) e paga XP.
- Mostra a ameaça (Rank F a S, pela vida e ataque base), as fraquezas e os biomas onde nasce.
- O arquivo `server/config/ftbquests/quests/chapters/bestiario.snbt` é **gerado pelo mod** ao ligar o servidor. Não edite à mão: ele é refeito quando a lista de monstros muda.

## Conquistas no livro

As 28 conquistas ficam no capítulo **Conquistas** do FTB Quests. Cada uma é uma tarefa de avanço oculto `frxworld:frx/conq_<id>`, marcado por `Marcos.marcar`.
