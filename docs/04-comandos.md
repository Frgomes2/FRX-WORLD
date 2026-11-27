# 04 — Comandos

[← Índice](README.md)

`<j>` = nome do jogador. `[...]` = opcional. Comandos `/frx` são de admin (permissão de operador).

## Jogador

| Comando | O que faz | Sistema |
|---|---|---|
| `/convite <código>` | Usa o convite no primeiro login (só quando `exigirConvite = true`; hoje está desligado) | Acesso |
| `/nivel` | Mostra nível e XP | Progressão |
| `/conquistas` | Lista conquistas | Progressão |
| `/raca` | Abre/mostra a sua raça | [Raças](sistemas/racas.md) |
| `/raca revelar` | Revela uma raça lendária disfarçada | Raças |
| `/raca ver <j>` | Vê a raça visível de outro jogador | Raças |
| `/talentos aprender <talento>` · `/talentos resetar` | Árvore de talentos | [Classes](sistemas/classes-habilidades.md) |
| `/sombra erguer` · `invocar` · `todas` · `guardar` · `libertar` | Exército de sombras (Invocador) | Classes |
| `/levantese` | Atalho de "erguer" as sombras | Classes |
| `/saldo` | Mostra suas moedas | [Economia](sistemas/economia.md) |
| `/banco` · `/banco sacar <v>` · `/banco depositar <v>\|tudo` | Banco | Economia |
| `/frx colecao chefe <jogador> <nome>` | Admin: registra um chefão derrotado na coleção do jogador | Progressão |
| `/enigma` | Abre o Diário do Enigma (e entrega um novo a quem não tem) | Aventura |
| `/reputacao` | Reputação com cada reino | [Reinos](sistemas/reinos.md) |
| `/titulo usar <título>` | Escolhe o título exibido | Reinos |
| `/rei` | Fala com o rei do reino atual | Reinos |
| `/campanha aceitar` | Aceita a campanha do rei | [Quests](sistemas/quests.md) |
| `/invasao` | Estado da invasão atual | Reinos |
| `/masmorras` | Lista as masmorras | [Dungeons](sistemas/dungeons-chefes.md) |
| `/chefe` | Informações do chefe mundial | Dungeons |
| `/unica` | Mostra a Habilidade Única e o progresso | [Imersão](sistemas/imersao.md) |
| `/suprema` · `/suprema prova` · `/suprema usar` | Habilidade Suprema: situação, prova e efeito ativo (também com Agachar + F) | Imersão |
| `/nomeacao` | Nome recebido dos reis e bônus | Imersão |
| `/evoluir [raça]` | Evolui a raça quando há caminho liberado | Imersão |
| `/portal` | Situação do portal (gate) aberto | Imersão |

**Teclas:** `B` = Despertar · `Z` = atalho de sombra 4 (os atalhos 1–3 começam sem tecla) · teclas de habilidade configuráveis em *Controles → FRX-WORLD*.

## Admin (`/frx`)

### Progressão e jogador

| Comando | O que faz |
|---|---|
| `/frx xp <j> <n>` · `/frx nivel <j> <n>` | Dá XP / define nível |
| `/frx classe ...` | Define a classe |
| `/frx tomo ...` | Dá tomo de habilidade |
| `/frx talentos resetar <j>` | Reseta talentos |
| `/frx despertar` | Enche a barra de Despertar (teste) |
| `/frx raca <j> definir <principal> [secundaria]` · `liberar <raca>` · `limpar` | Raças |
| `/frx admin <j> ficha` · `curar` · `banco` · `bolsa <cobre>` (define a bolsa exata) · `tirarmoedas <v>` · `reputacao ...` · `talento ...` | Ficha e ajustes do jogador |
| `/frx bancooffline <uuid> <cobre>` | Define o saldo do banco; com o jogador offline, o valor entra no próximo login (usado pelo painel) |
| `/frx codex lista` · `recarregar` · `dar <jogador> <código>` · `estoque <código> <n>` | Códex de Armas: lista, relê do painel, entrega uma arma, ajusta a contagem do mundo |
| `/frx criaturas lista` · `recarregar` · `invocar <código> <jogador>` | Códex de Criaturas: lista, relê do painel, faz a criatura aparecer perto de um jogador |
| `/frx terreno proibir [raio]` · `liberar [raio]` · `info` · `tomar` | Terrenos: marca ou libera terras do reino em volta do admin, mostra a situação do lugar, tira o dono do terreno |
| `/terreno` (jogadores) | Abre o painel de terrenos (reserva para quem não usa o mapa) |
| `/ping` | Todos: o seu ping até o servidor e o TPS |
| `/frx ping` | Admin: ping de todos os jogadores online |
| `/frx ajustar <nick> nivel <n>` · `rank <F..S>` · `torre` · `moedas <cobre>` · `item <id> <qtd>` · `chefe <nome>` · `aviso <texto>` | Define nível, rank da Guilda, zera a Torre, entrega moedas ou itens, registra um chefão na coleção ou deixa um recado; offline, entra no próximo login (`frx_ajustes.txt`; moedas pendentes se somam) |
| `/frx admin <j> habilidade aprender\|esquecer\|nivel\|todas` | Habilidades do jogador |
| `/frx moedas <j> <cobre>` | Dá moedas (em cobre) |
| `/frx reputacao ...` | Ajusta reputação |
| `/frx recarregar` | Recarrega a config do painel |
| `/frx abertura` · `/frx abertura reset` | Mostra a abertura isekai (prévia) / faz o jogador passar por ela de novo |
| `/frx unica sortear` · `/frx unica <id>` | Troca a Habilidade Única |
| `/frx suprema nivel5` · `dar` · `tirar` | Leva a Única ao nível 5 / dá a Suprema sem prova / desfaz |
| `/frx nomeacao <reino>` · `/frx nomeacao limpar` | Dispara a cerimônia de nomeação / apaga as nomeações |
| `/frx urgente` · `/frx urgente encerrar` | Sorteia uma missão urgente agora / encerra a atual |

### Itens e forja

| Comando | O que faz |
|---|---|
| `/frx raridade <N>` | Define a raridade do item na mão |
| `/frx forja <N>` | Define o refino +N do item na mão |

### Mundo, reinos e conteúdo

| Comando | O que faz |
|---|---|
| `/frx reino <id>` | Teleporta para o reino |
| `/frx waystones` · `/frx waystones refazer` | Lista / recoloca as 5 waystones de cada reino |
| `/frx npc recriar` · `/frx npc aqui` | NPCs dos reinos |
| `/frx populacao refazer` | Refaz a população dos reinos |
| `/frx decor refazer` | Refaz decorações |
| `/frx campanha pular` | Pula etapa da campanha |
| `/frx invasao parar` | Encerra invasão |
| `/frx taverna ...` · `/frx quadro ...` | Contratos da taverna / quadro de missões |
| `/frx dungeon ...` · `/frx masmorra ...` · `/frx cripta resetar` | Dungeons e masmorras |
| `/frx chefe agora` · `tempo` · `cancelar` | Chefe mundial |
| `/frx evento ...` | Evento de estrada |
| `/frx masmorra entradas` · `/frx masmorra entradas refazer [id]` | Lista as entradas das masmorras / refaz todas ou só uma |
| `/frx portal aqui [rank]` · `/frx portal fechar` | Abre / fecha um portal (gate) |
| `/setworldspawn` (vanilla) | Marca o ponto inicial: a abertura isekai passa a nascer e renascer ali |

> Para a sintaxe exata de cada subcomando, use o autocompletar (Tab) no jogo.

## Aventura (15/11)

| Comando | O que faz |
|---|---|
| `/historia` · `/historia desafio` | Capítulo atual da história · desafia o Rei Demônio (último capítulo, no reino dos demônios) |
| `/temporada` | Placar da temporada do mês |
| `/procurados` | Contrato de procurado em aberto |
| `/torre` · `/torre sair` · `/torre continuar` | Recorde e entrada da Torre Infinita · desiste da subida · começa o próximo andar já |
| `/torre placar` · `/torre loja` · `/torre comprar <item>` | Rankings da Torre · Loja da Torre (fichas) · compra |
| `/runas` | Lista e explica as runas |
| `/portal sair` | Desiste da dungeon de portal |
| `/frx classe` · `/frx classe definir <F..S\|livre\|automatica>` | Mostra a classe do item na mão · fixa uma exceção |
| `/frx torre aqui` · `desfazer` · `andar <n>` | Ergue a torre onde o admin está · remove · define o andar seguro do admin |
| `/frx historia <jogador> <capítulo>` | Põe o jogador num capítulo |
| `/frx temporada fechar` | Encerra a temporada agora |
| `/frx procurado` · `encerrar` | Cria um procurado ao lado · remove |
| `/frx ceu lua` · `meteoros` · `parar` | Força os eventos do céu |
| `/frx tesouro` | Dá um Mapa do Tesouro |
| `/frx runa <tipo> [nível]` | Dá uma runa |
| `/frx ladrao [jogador]` · `chance <0-1000>` | Solta o ladrão · ajusta a chance |
| `/frx ronaldinho [jogador]` · `chance <0-1000>` | Chama o Ronaldinho · ajusta a chance |
| `/pesca` | Placar do torneio de pesca da semana (todos) |
| `/x1` | Abre a aba de duelos X1; `/x1 aceitar\|recusar\|cancelar\|desistir\|assistir\|sair\|placar` (todos) |
| `/frx pesca encerrar` | Encerra o torneio agora e paga os prêmios |
| `/frx karin construir` | Constrói (ou refaz) o Templo Karin a leste de Lubelius |
| `/frx arena criar\|spawn\|espectador\|raio\|ir\|remover\|lista` | Arenas do X1 (ver [sistemas/x1.md](sistemas/x1.md)) |
| `/frx esferas ver` | Mostra o estado e o lugar de cada Esfera do Dragão |
| `/frx esferas espalhar` | Esconde as 7 esferas de novo (e tira da pedra) |
| `/frx sala construir` | Constrói a Sala do Tempo e abre a passagem no salão do Templo Karin |
| `/frx sala ir` | Leva você para dentro da Sala do Tempo |
| `/frx deathnote soltar` | Derruba um Death Note perto de você (com o aviso geral) |
| `/frx deathnote dar` | Coloca um Death Note no seu inventário |
| `/frx tita iniciar` | Começa o evento do Titã agora (ignora o limite diário e o mínimo de jogadores) |
| `/frx tita parar` | Encerra o evento do Titã sem vencedor nem punição |
| `/frx tita abrirlojas` | Faz os NPCs de Lubelius voltarem a atender antes dos 30 minutos |
| `/frx masmorra chefes` | Lista os chefes de masmorra mortos e quanto falta para cada um renascer |
| `/frx masmorra renascer` | Libera agora todos os chefes pendentes (nascem quando alguém chegar perto) |
| `/frx crepusculo chefes` | Lista os chefes do Twilight Forest mortos e quanto falta para cada um voltar |
| `/frx crepusculo renascer` | Libera agora os chefes do Twilight pendentes (o invocador volta quando o lugar carregar) |
| `/chefes` (jogadores) | Situação dos chefes dos reinos e do Twilight: vivo, geração e quanto falta para voltar |
| `/frx crepusculo registrar <chefe>` | Registra, onde o admin está (o lugar do invocador), um chefe do Twilight morto antes da regra de renascer |
| `/frx masmorra chefe <tipo>` | Marca um chefe desse tipo para nascer onde você está (para os que morreram antes da 2.0.8) |
| `/frx pokebola treinador` | Cria o Treinador de Criaturas (vende as pokébolas) onde você está, virado para você |
| `/frx pokebola tirartreinador` | Remove o Treinador que estiver a até 6 blocos |
| `/frx pokebola devolver <jogador>` | Dá uma pokébola vazia ao jogador sem contar no limite (para quem perdeu uma) |
| `/frx painel reiniciar` | Reinicia só o painel (site, admin, páginas dos jogadores); o jogo continua no ar |
| `/frx karin ir` | Leva você à ponte da base do Templo Karin |
| `/corridas` | Lista as pistas, recordes e onde fica cada largada (todos) |
| `/frx corrida criar <id> <pe\|cavalo\|barco\|elitro> <nome>` | Cria uma pista |
| `/frx corrida ponto <id> [x y z]` | Marca o próximo ponto onde você está (ou nas coordenadas): o 1º é a largada, o último a chegada; zera os recordes da pista |
| `/corrida iniciar <id>` | Começa a corrida (é o botão que aparece no chat na largada) |
| `/frx corrida placar <id> [tamanho]` · `limite <id> <min>` | Placar gigante onde você está, virado para trás de você (tamanho 1–12, padrão 3) · tempo máximo da corrida (padrão 15) |
| `/frx corrida voltas <id> <n>` | Pista de várias voltas: vira circuito e a chegada é a linha de largada, depois do último ponto |
| `/frx corrida desfazer\|zerar\|remover <id>` · `raio <id> <blocos>` | Tira o último ponto · zera os recordes · apaga a pista · muda o tamanho dos pontos (padrão 5; élitro 10) |
| `/frx noel [treno\|perdido\|jogador]` · `chance <0-1000>` | Chama o Papai Noel (trenó ou perdido) · ajusta a chance |

## Mundo e cidades (15/11)

| Comando | O que faz |
|---|---|
| `/frx portao aqui [largura 3-9]` · `/frx portao desfazer` | Abre um portão em arco, com guardas, no muro à frente do admin · desfaz |
| `/frx ciclo <dia> <noite>` | Duração do dia e da noite em minutos (padrão 20 e 10) |
| `/frx casas <reino>` · `/frx casas desfazer` | Abre as casas maciças do reino: interior, porta, cama e baú · desfaz (até reiniciar) |
| `/frx masmorra portal <id>` · `/frx masmorra secar <id>` | Põe o pórtico com portal na entrada da masmorra · tira a água de dentro |
| `/frx masmorra entradas [refazer [id]]` | Lista ou refaz as entradas das masmorras |
| `/frx npc aqui pedreiro` | Coloca o Mestre de Obras (loja de blocos) |
| `/frx npc aqui clas` · `leiloeiro` · `discos` · `escravos` · `costureira` · `dragoes` | Coloca o Mestre dos Clãs, o Leiloeiro, o Mercador de Discos, o Mercador de Escravos e a Costureira |
| `/frx encontro viajante` · `mercador` · `estrela` · `bau` · `carta` | Força um encontro ao lado do admin, para testar |
| `/frx npc recriar <reino>` | Recria os NPCs de um reino sem precisar estar nele (funciona pelo console) |
| `/frx npc aqui contrabandista` | Coloca o Contrabandista. Só funciona fora das capitais |
| `/frx populacao refazer` | Remove os moradores carregados e povoa de novo |

## Terras Inexploradas (3.0.0)

- `/expedicao` (todos): expedição atual, troca e quanto falta. `/expedicao sair`: volta ao mundo principal.
- Admin: `/expedicao info | portal | ir | abrir | fechar | trocar | historico`. Detalhes em [sistemas/expedicao.md](sistemas/expedicao.md).


## Mapa (3.0.5)

| Comando | O que faz |
|---|---|
| `/frx mapa pontos` | Lista os pontos globais do JourneyMap |
| `/frx mapa limparpontos` | Apaga no servidor os pontos globais de reino, de teste e repetidos (apagar pelo mapa do jogo só tira da tela de quem apagou) |
| `/frx mapa publicos` · `/frx mapa apagarpublico <n>` | Lista / apaga um ponto público de jogador (apaga também no JourneyMap do dono) |
