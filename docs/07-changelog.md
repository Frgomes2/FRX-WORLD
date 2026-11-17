# 07 — Changelog do core mod

[← Índice](README.md)

Formato: versão, depois o que entrou. A versão do mod fica em `core-mod/gradle.properties`.

> **Situação dos testes (10/11/2026):** a última versão compilada e testada no PC é a **0.33.0** (`pack/mods/frxworld-0.33.0.jar`). As versões **0.34 a 0.40** ainda não rodaram no jogo. O roteiro de teste está no checklist "FRX-WORLD — Checklist de testes".

## Painel (16/11, noite) — Site público em `/` e painel em `/admin`

- **Site público** na raiz do endereço (`craft.frgomes.com.br`), sem login: status, jogadores online, acontecimentos, rankings, Torre, temporada, mapa, sistemas, mods e como entrar. Detalhes em [05-painel](05-painel.md).
- **Painel admin** mudou para `/admin` (todas as telas, links e atalhos). Os endereços antigos (`/servidor`, `/missoes`...) deixam de existir.
- **Menu "Dinheiro dos jogadores"** (`/admin/dinheiro`): bolsa, banco e total de cada um, com busca e ordenação; tira um valor da bolsa ou do banco, ou zera tudo. Só age em quem está online (usa `/frx admin <nick> tirarmoedas|banco`).
- **Dungeon de portal única por portal** (`aventura/Fenda.java`): todos que atravessam caem no mesmo lugar, antes era uma por grupo. **Lotação por rank:** F 2, E 3, D 3, C 4, B 4, A 5, S 6. Quem já entrou conta na lotação mesmo depois de morrer ou sair, e pode voltar. Ainda não testado em jogo.
- **Classes de equipamento valem para arcos e bestas** (`progressao/ClassesEquip.java`): tabela `DISTANCIA` (arco F, besta E, arcos de mods de D a A) e, para arma de longe fora da tabela, a raridade (comum D, incomum C, raro B, épico A). Acima do rank o arco não puxa nem dispara. Ainda não testado em jogo.
- **Site mais visual:** galeria de aventureiros com o boneco da skin, pódio dos três mais fortes, rostos nos rankings e **ficha do jogador** ao clicar no nome (skin, nível, raça, Guilda, feitos, habilidade única e habilidades aprendidas). A skin é a que o SkinRestorer gravou em `<mundo>/skinrestorer/<uuid>.json`.
- **Notas de atualização** no site (seção Novidades), escritas para jogador em `painel/src/lib/notas.ts`. A cada `/publicar`, acrescentar ali o que mudou.
- **Ponto de partida do mapa** corrigido: vem do servidor ou do `level.dat`, não mais do `posicoes.json` (que guarda o centro de Tempest).
- **Banco editável no painel** (`/admin/dinheiro`): clique no saldo para definir o valor, mesmo com o jogador offline. Comando novo `/frx bancooffline <uuid> <cobre>`; offline, o valor espera em `<mundo>/frx_banco_pendente.txt` e entra no próximo login.
- **Chefes das masmorras dos reinos** (`Zonas.aplicarNivel`): chefe dentro de uma área de nível fixo (`AreasNivel`) recebe o bônus de nível inteiro, mais +300% de vida e +100% de dano. Jogador abaixo do nível 50 causa no máximo 20% do dano e recebe pelo menos o triplo. Masmorra enterrada embaixo de capital nunca fica abaixo do nível 50. Vale para chefes que nascerem depois da atualização. Ainda não testado em jogo.
- **Rank da Guilda para trocar de dimensão** (`Zonas.barrarDimensao`): Nether exige Rank D e o End exige Rank C. Vale para portal, waystone e qualquer teleporte; criativo e espectador passam. O painel pode trocar em `zonas.rankMinimo = { "dimensao": "D" }`. Quem já está lá dentro não é expulso. Ainda não testado em jogo.
- **Mods novos** (instalados em 16/11, 20:57): Naturalist, Mythic Mounts, Artifacts, Macaw's (Bridges, Roofs, Doors, Furniture, Windows, Fences and Walls), Bygone Nether e Piglin Proliferation, mais as dependências Patchouli e Cloth Config API. O packwiz não puxou o Cloth Config sozinho e o servidor não subiu na primeira tentativa (Artifacts exige `cloth_config` 11.0.99+); ao adicionar mod, conferir `Missing or unsupported mandatory dependencies` no `latest.log`.
- **Nether extremamente difícil:** faixa de nível 22–40 (era 15–35), dureza da dimensão +150% de vida e +100% de dano (`Zonas.DUREZA_PADRAO`) e **emboscadas** (`aventura/Inferno.java`): a cada 6 a 11 min (a primeira aos 3 min) nasce perto de cada jogador uma horda de 4 a 7 monstros ou, em 25% das vezes, um chefão de `Chefes` do rank dele. Mods na fila: Bygone Nether (estruturas e monstros por bioma) e Piglin Proliferation. Ainda não testado em jogo.
- **Torre em grupo de até 5** (`Torre.MAX_GRUPO`): sem comando nem `/grupo`: alguém pisa no portal do saguão, contagem de 5 s, e sobem juntos todos os que estão no saguão (os 5 mais perto do portal). Por companheiro a mais: +1 nível nos monstros, +40% de vida, +10% de dano, +3 monstros por andar e chefe com +90% de vida. **XP dividida** (`xpPorMembro`): o prêmio do andar cresce 50% por companheiro e é repartido igualmente (2 = 75% cada, 3 = 67%, 4 = 62%, 5 = 60%). Ainda não testado em jogo.
- **Lojas respeitam o rank** (`MenusNpc.ofertasDe`): ferreiro, armeiro e mercador só mostram armas e armaduras de classe até o rank da Guilda do jogador. Para o Rank F, que não usa ferro, ferreiro e armeiro oferecem pedra e couro (`Lojas.basicos`). Ainda não testado em jogo.
- **Dragão no fundo do site** (`components/publico/dragao.tsx`): silhueta própria que cruza a página indo e voltando.
- **Loja da Torre em janela** (`Torre.MenuLoja`): o Mercador abre uma janela de baú com os itens; passar o mouse mostra preço e se dá para comprar, clicar compra. Feita só no servidor (`ChestMenu` com cliques bloqueados), sem código de cliente. Ainda não testado em jogo.
- **Chefes épicos dos mods na Torre** (`Torre.EPICOS`): nos andares 20, 40, 60 e 80 o chefe é sorteado entre chefes de Mutant Monsters, Twilight Forest, Mowzie's Mobs, Illager Invasion e Cataclysm (só os que existem no servidor). Vida extra menor que a dos chefes comuns (andar/40 + 60% por companheiro), nome dourado e aviso na tela. Andares 10, 30, 50, 70 e 90 seguem com `Chefes`; o 100 é o Wither. Ainda não testado em jogo.
- **Invocações que crescem** (`Habilidades.invocar`): força = talento × (1 + 10% por nível da habilidade) × (1 + 4% por nível do jogador); o nome mostra o nível. Antes só o talento contava.
- **Sombras sobem de nível com o dono** (`Sombras.aoMorrer`): abate do Invocador conta para as sombras em campo a até 24 blocos; antes só o golpe final da própria sombra contava.
- **Ajustes de admin com jogador offline** (`progressao/AjustesAdmin.java`): `/frx ajustar <nick> nivel <n> | rank <F..S> | torre`. Offline, o pedido espera em `<mundo>/frx_ajustes.txt` e entra no próximo login.
- **Barra de experiência das sombras** (`cliente/TelaSombras.java`): na ficha, "Nível n/10 · feitos/necessários abates" com barra roxa; na lista, uma barrinha ao lado do nível. O nível n pede 3 × n² abates.
- **Biblioteca no site** (`painel/src/lib/livros.ts`, `components/publico/biblioteca.tsx`): 10 livros sobre o mundo (guia, Guilda e ranks, missões, economia, Torre, portais e chefes, dimensões, raças, talentos e poderes, eventos) e 1 por classe, com leitor de páginas que vira (duas páginas no computador, uma no celular, setas do teclado). Ranks, missões do quadro e habilidades vêm do banco; o resto é texto em `livros.ts`, que precisa ser atualizado quando uma regra mudar.
- **Vida dos outros jogadores à vista** (16/11, 22:23, sem reinício): placares do jogo `frx_vida` (número com ❤ abaixo do nome) e `frx_vida_tab` (corações na lista do TAB), criados pelo console com `scoreboard objectives add ... health`. Ficam salvos no mundo; para tirar, `scoreboard objectives remove frx_vida` e `frx_vida_tab`.
- **Queda de 16/11, 22:32:** painel e servidor caíram juntos sem deixar erro (o log do servidor parou às 22:27 sem mensagem de desligamento; o `painel.log` foi sobrescrito na religada). A vigia do agente religou em cerca de 1 min. Segunda queda desse tipo (a outra foi em 15/11). Para a próxima, o `iniciar-frx.bat` passa a guardar o log anterior em `logs/painel-anterior.log`.
- Rotas sem login: `/api/publico/dados`, `/api/publico/mapa-tile`, `/api/publico/jogador` e `/api/publico/skin`. Não expõem e-mail, UUID, IP, chat, inventário nem posição de jogador.

## 0.41.0 (16/11) — Classes de equipamento, dungeon de portal, chefões e moedas só por missão e venda

- **Classes de equipamento por rank da Guilda** (`progressao/ClassesEquip.java`): armas e armaduras, inclusive de outros mods, exigem o rank da classe para usar.
- **Portais** levam a uma **dungeon temporária** (`aventura/Fenda.java`) com três câmaras e chefão; só entra quem tem o rank do portal ou maior. As ondas no mundo aberto saíram.
- **14 chefões com mecânicas** (`aventura/Chefes.java`), sorteados pelo rank; também nos andares de chefe da Torre.
- **Torre:** XP só ao vencer o andar, com curva que começa quase em zero; morte de verdade com lápide na porta, perda de XP e 10 min de espera.
- **Moedas** só por missões e venda de itens: saíram de baús, monstros, chefes, invasões, campanhas, portais, Torre, procurados, tesouros, história, temporada, soldo e FTB Quests. Runa da Fortuna passa a dar XP em dobro; temporada paga em runas.
- **Classes, cuidado na virada:** na primeira entrada de cada jogador toda a armadura vai para a mochila; armadura proibida nunca cai no chão (mochila, depois baú de ender; sem espaço, fica no corpo até abrir vaga).
- **Mochilas** (Sophisticated Backpacks): ninguém abre a mochila que outro jogador está usando (`allowOpeningOtherPlayerBackpacks = false`).
- **Floresta do Crepúsculo mais difícil:** faixa de nível 14–30 (era 10–25) e dureza da dimensão, +80% de vida e +50% de dano em todo monstro de lá (`Zonas.dureza`, ajustável em `zonas.dureza` no painel).
- **Torre mais difícil:** nível do monstro 3 + 1,4 × andar, +5% de vida e +2% de dano por andar, mais monstros por andar (até 18) e Velocidade a partir do andar 30.
- Ainda não testado em jogo.

## 0.41.0 (15/11, noite) — Aventura, Torre Infinita, runas, habilidades novas e cidades protegidas

- **Mecânicas especiais de habilidade** (`habilidades/Especiais.java`): o campo *efeito* aceita `frx:puxar`, `frx:provocar`, `frx:salto`, `frx:fenda`, `frx:executar`, `frx:vampirismo`, `frx:refletir` e `frx:lancar`. Servem para qualquer classe; o painel sugere a lista no campo.
- **Guerreiro** passa de 6 para 15 habilidades (migração `20261115180000_guerreiro_novas`): Arremesso de Machado, Gancho de Corrente, Provocar, Salto Esmagador, Postura de Revide, Fenda Sísmica, Executar, Sede de Sangue (tomo) e Ira do Titã (segunda ultimate, nível 25).
- Em jogo desde o reinício de 15/11 13:22; balanceamento ainda por ajustar.
- **Arqueiro** passa de 6 para 14 habilidades (migração `20261115190000_arqueiro_novas`), com oito mecânicas novas em `Especiais.java`: `frx:explosiva`, `frx:ricochete`, `frx:marca`, `frx:gancho`, `frx:leque`, `frx:voleio`, `frx:perfurar` e `frx:armadilha`. Ainda não testado em jogo.
- **Mago** passa de 6 para 15 habilidades (migração `20261115210000_mago_novas`), com `frx:congelar`, `frx:misseis`, `frx:escudo_mana`, `frx:trocar`, `frx:sifao`, `frx:zona` e `frx:singularidade`; `frx:ricochete` agora usa o elemento (Corrente de Raios). Ainda não testado em jogo.
- **Cosmetic Armor Reworked** no pack (esconder armadura e mostrar a skin).
- **Benefícios do rank da Guilda** (`Guilda.bonusMissao/desconto/pagarSoldo`): por rank acima de F, +10% em missões, 2% de desconto e soldo diário de 60 cobre; aparecem no cartão e na promoção. Ainda não testado em jogo.
- **Moradores das capitais** (`reinos/Populacao.java`): não morrem mais para monstro, dragão, parede ou invasão (só jogador fere); morador ou guarda que morre volta em 10 min perto de onde caiu (`frx_repor.txt`). O arquivo foi semeado com as mortes de 14 e 15/11 tiradas do log.
- **Dragões** do Ice and Fire não quebram mais blocos (`Dragon Griefing = 2`).
- **Portais:** ficam 20 min abertos (eram 12); recompensa de 300 × (rank+1)² cobre e 300 × nível de XP (eram 100 e 120); chance de tomo 30% + 10% por rank; coluna de luz visível a 500 blocos; ponto global no JourneyMap pela API de servidor (`Portais.pontoGlobal`). Ainda não testado em jogo.
- **Aventura** (pacote `aventura/`, ver [sistemas/aventura.md](sistemas/aventura.md)): história principal em 14 capítulos com o Rei Demônio no fim, temporadas mensais com placar, mural de procurados, Torre Infinita de 100 andares, eventos do céu (Lua de Sangue e chuva de meteoros) e mapas do tesouro. Ainda não testado em jogo.
- **Torre:** quadros de ranking ao lado da porta (mais longe e mais abates), andares temáticos com salas maiores (Deserto, Floresta, Geleira) e barra no topo da tela com o andar. Ainda não testado em jogo.
- **História no livro de missões:** capítulo «História Principal» no FTB Quests (14 missões, conquistas `historia_1..14`). Torre: escolha de continuar ou sair a cada andar e Cozinheiro no andar seguro.
- **Torre:** fichas, baú do andar seguro e Loja da Torre. **Ladrão** (`eventos/Ladrao.java`): evento raro (5 em 1000 por minuto) em que um ladrão leva uma moeda e foge por 15 s; quem pega recebe dez vezes. `/frx ladrao [jogador]`, `/frx ladrao chance <n>`; skin pelo painel (papel `ladrao`). Ainda não testado em jogo.
- **Runas** (`aventura/Runas.java`): 11 runas de arma e armadura em três níveis, encaixadas na bigorna. Ainda não testado em jogo.
- **Anéis:** 10 espaços de anel no Curios (`server/kubejs/data/frxworld/curios/slots/ring.json`).
- **Skins de jogador** em servidor offline: mod SkinRestorer.
- **Ronaldinho** (`eventos/Craque.java`): evento raro e aleatório. Uma vez por minuto cada jogador tem 4 em 1000 de chance (≈ 1 vez a cada 4 h de jogo): ele aparece, dribla em volta do jogador com a bola, dá a caneta (o jogador gira 180°), some numa nuvem e deixa a "Bola do Bruxo". Aviso no chat para todos. `/frx ronaldinho [jogador]` força; `/frx ronaldinho chance <0-1000>` ajusta (`frx_ronaldinho.txt`). Skin pelo painel (NPCs → Ronaldinho, papel `craque`). Ainda não testado em jogo.

## 0.41.0 (13/11) — Mundo que reage, nomeação, bestiário, missões de rank e Habilidade Suprema

- **NPCs que reagem a quem você é** (`reinos/Reacoes.java`): a fala muda com o rank da Guilda, a raça à mostra, o título em uso e o nome recebido de um rei.
  - O guarda ignora um Rank F, respeita do Rank C para cima e faz reverência a um Rank A ou a quem foi nomeado. Raças das trevas ficam sob vigia.
  - O sacerdote desconfia de vampiro, demônio e Rei Demônio, e reverencia anjo e Ser Celestial. Diante de um Deus, todos se curvam.
  - Rei, taverneiro, sábio, cartógrafo, ferreiro, armeiro, mercador, cozinheiro, banqueiro e recepcionista também têm falas próprias.
  - A raça lendária disfarçada conta como a raça do disfarce. A reação entra em cerca de 2 a cada 3 conversas; nas outras vale a fala normal ou a do painel.
- **Nomeação pelo rei** (`reinos/Nomeacao.java`): ao concluir a campanha de um reino, o rei faz a cerimônia.
  - O primeiro rei oferece três nomes e o jogador escolhe um (clique no chat). Os reis seguintes reconhecem o nome e somam a bênção do reino.
  - Cada nomeação dá **+1 coração e +2% de dano, para sempre**. Há sinos, anúncio no servidor, raio (só visual) e o título "<Nome>, o Nomeado".
  - Quem concluiu a campanha antes desta versão recebe a cerimônia ao falar com o rei.
  - Comandos: `/nomeacao`; admin: `/frx nomeacao <reino>` e `/frx nomeacao limpar`.
- **Habilidade Suprema** (`habilidades/Suprema.java`): no nível 5 da Habilidade Única, `/suprema prova` chama o Eco da habilidade (4 minutos, sem morrer; nova tentativa em 10 minutos).
  - Vencendo, a habilidade ganha nome novo, um degrau a mais no passivo e um **efeito ativo**, usado com **Agachar + F** (ou `/suprema usar`).
  - Grande Sábio → Senhor da Sabedoria; Devorador → Senhor da Gula; Fúria do Dragão → Ira do Dragão Verdadeiro; Corpo de Aço → Fortaleza Absoluta; Passo Sombrio → Soberano das Sombras; Olho do Falcão → Olho Divino; Sangue Real → Monarca Carmesim; Filho do Vento → Deus do Vento; Segunda Chance → Fênix Eterna; Instinto do Caçador → Predador Ápice.
  - Admin: `/frx suprema nivel5`, `/frx suprema dar`, `/frx suprema tirar`.
- **Bestiário** (`progressao/Bestiario.java`): capítulo novo no livro de Quests, com uma página por tipo de monstro de todos os mods.
  - A página se completa ao matar o monstro pela primeira vez (tarefa `kill` do FTB Quests) e paga XP.
  - Mostra a ameaça (Rank F a S, pela vida e ataque base), as fraquezas e os biomas onde nasce.
  - O arquivo `config/ftbquests/quests/chapters/bestiario.snbt` é refeito ao ligar o servidor quando a lista de monstros muda.
- **Missões de rank na Guilda** (`missoes/Missoes.java`, `missoes/Urgentes.java`): cada missão tem um rank mínimo (F a S), escolhido no editor do painel.
  - No quadro o título traz `[Rank X]`, a lista sai ordenada por rank e quem não tem o rank vê o motivo do bloqueio. Missões de Rank E para cima exigem registro na Guilda.
  - A migração `20261113230000_missao_rank` dá às missões já cadastradas o rank da faixa de nível delas.
  - **Urgentes:** a cada 45 minutos uma caçada vira urgente por 20 minutos, com aviso do Sistema e recompensa em dobro para quem entregar no prazo. Os três números e o liga/desliga ficam no painel (Missões → Missões urgentes). Admin: `/frx urgente` e `/frx urgente encerrar`.

- **Música na abertura isekai** (`acesso/Abertura.java`): a cena toca uma música presa ao jogador, que para quando ele nasce na capital (opcional). Painel: **Convites → Abertura do primeiro login → Música** (lista de músicas do jogo, volume e a opção "Música própria"). Para usar uma música sua, salve o OGG em `core-mod/src/main/resources/assets/frxworld/sounds/abertura.ogg` e reinicie.

- **Entrada sem convite e sem fingerprint (13/11):** `exigirConvite = false` em `server/config/frxworld-common.toml` (decisão do Flavio). O kit dos amigos passou a usar o endereço fixado do AutoModpack (`host#amp1=<fingerprint>`, em `amigos/endereco-servidor.txt`), que dispensa a tela de confirmação do certificado. Com `online-mode=false` e sem convite, qualquer pessoa com o endereço entra com qualquer nick.

- **Entradas das masmorras (14/11)** (`reinos/EntradasMasmorra.java`): toda masmorra fixa enterrada ganha um pórtico de pedra negra na superfície, com o nome flutuando e um portal (obsidiana chorosa) que leva à primeira sala; lá dentro, outro portal traz de volta. As de superfície ganham só um marco com o nome ao lado da construção. Registro por mundo em `frx_entradas.txt`. Admin: `/frx masmorra entradas` e `/frx masmorra entradas refazer`. O `/masmorras` passou a mostrar a entrada, não o centro da estrutura.
- **Dano entre aliados** ligado nos grupos; **ficha do painel** criada no login quando o convite está desligado; mods **Golden Age Combat**, **Sword Blocking Mechanics** e **Just Zoom** (cliente).

- **Tempest só com o coliseu (14/11):** caixa reduzida para 29×15 chunks (x 64 a 527, z −16 a 223), com centro em 296, 104. A praça passou para o centro do coliseu (`praca: [296, 105]`) e os NPCs voltaram ao arranjo automático em anel (as posições fixas antigas ficaram em `npcs_antes_20261114` no `posicoes.json`). Original em `server/frxworld/reinos-originais/tempest-20261114-1146`; as regiões antigas do mapa estão em `backups/regiao-tempest-20261114`.
- **Ponto inicial livre:** se o admin mudar o spawn do mundo com `/setworldspawn`, a abertura isekai passa a nascer e renascer ali e o Sistema indica a distância e a direção da capital.

- **`/publicar` (15/11):** padrão de publicação em `.claude/commands/publicar.md`: atualiza a documentação desde o último `/publicar`, faz o commit (primeira linha `publicar: …`) e envia direto para a `main`.
- **Portal de Tempest (15/11):** a chegada passou para dentro da Fábrica Ancestral (223, −19, 118); antes caía numa caverna encostada nela. A escolha da sala agora exige piso, teto e paredes de construção.

- **Cidade inicial em Lubelius (15/11):** o spawn do mundo foi para o centro de Lubelius (1788, 71, 1598). A abertura isekai nasce ali, o Prólogo passou a pedir a chegada a Lubelius e, como o nível das zonas é medido a partir do spawn, a região de nível 1 agora é a de Lubelius. Todos os jogadores foram zerados (dados antigos em `backups/jogadores-20261115`).
- **Portão de capital** (`reinos/Portao.java`): `/frx portao aqui [largura]` abre um portão em arco no muro à frente do admin, com grade erguida, lanternas e quatro guardas; `/frx portao desfazer` devolve os blocos do último.
- **Ciclo do dia** (`progressao/CicloDia.java`): dia de 20 minutos e noite de 10 (o normal é 10 e 10). `/frx ciclo <dia> <noite>` muda; registro em `<mundo>/frx_ciclo.txt`. O mod desliga o `doDaylightCycle` e anda o relógio ele mesmo.

- **Arena Flamejante sob Lubelius (15/11):** masmorra `lubelius-abismo` (`cataclysm:burning_arena`, chefe Ignis), com **nível fixo 55**. Novidades que ela trouxe:
  - **Nível fixo por masmorra** (`progressao/AreasNivel.java`, campo "Nível fixo" no painel, coluna `Masmorra.nivel`): dentro da caixa da estrutura os monstros nascem naquele nível, qualquer que seja a zona. Registro em `<mundo>/frx_areas.txt`.
  - **Masmorra dentro de capital:** se a estrutura nasceria na altura da cidade, o mod a desce inteira até caber embaixo do chão (14 blocos de folga). O ajuste fica no fim da linha do `frx_masmorras.txt`.
  - **Masmorra seca:** ao descer uma estrutura para baixo de uma capital, o mod tira a água de dentro das peças, troca por pedra a água em volta e fecha com pedra a casca de 1 bloco ao redor (aquíferos e cavernas que cruzavam a parede). A lava fica. `/frx masmorra secar <id>` refaz isso numa masmorra já colocada.
  - **Portal escolhido pelo admin:** embaixo de uma capital o pórtico não é montado sozinho; use `/frx masmorra portal <id>` em pé no lugar. O comando também serve para mudar o portal de qualquer masmorra enterrada.

- **Casas de verdade nas capitais** (`reinos/Casas.java`, 15/11): as casas coladas são maquetes maciças, com um vão fechado de 1 bloco no nível do chão. `/frx casas <reino>` transforma cada vão em cômodo: cava 2 blocos para cima, põe piso de tábua, abre uma porta para o lado mais livre, uma cama e, em 60% das casas, um baú de saque do reino. `/frx casas desfazer` devolve tudo (até o servidor reiniciar). Em Lubelius: 89 casas abertas; 44 ficaram fechadas por não terem um lado livre para a porta.

- **Mestre de Obras** (papel `pedreiro`, 15/11): NPC que vende blocos essenciais em pilhas (toras, tábuas, pedra, vidro, tijolo, lã, tochas, baú, fornalha, cama…), com o desconto da reputação. Lista fixa em `Lojas.BLOCOS`. Em capitais já povoadas, coloque com `/frx npc aqui pedreiro`.
- **Banco:** o saque só tira do cofre o que cabe na bolsa (antes, com o inventário cheio, as moedas caíam no chão e sumiam); depósitos e saques ficam no log do servidor.
- **Dormir pula a noite** mesmo com o ciclo do dia alterado; **missão urgente** também vai para o chat; **portal (gate)** mostra distância e direção no chat e é marcado sozinho no mapa do JourneyMap (`cliente/MarcadorMapa.java`, por reflexão na API v2; some quando o portal fecha); **waystone global** liberada para qualquer jogador (`globalWaystoneSetupRequiresCreativeMode = false`).

## 0.41.0 — Balanceamento, túmulo e reinos menores

- **Monstros mais fracos:** por nível, +6% de vida (era 10%), +3,5% de dano (era 6%) e +0,2 de armadura (era 0,25). Dificuldade do servidor de *hard* para *normal*.
- **Perigo por nível:** monstro acima do seu nível bate +8% por nível de diferença (era 15%), com teto de ×2,2 (era ×3).
- **Elite:** vida ×1,7 a ×2,5 (era ×2,2 a ×3,4) e dano +16% a +40% (era +30% a +70%).
- **Campeão:** vida ×4 (era ×6) e dano ×1,5 (era ×1,8).
- **Jogador forte por perto:** chance de Elite ×1,5 e de Campeão ×2 (era ×2 e ×3). O nível sobe 1/3 da diferença (era metade), até a faixa + 5 (era + 10).
- **Jogador:** +0,4 de vida por nível (nível 30 ≈ +6 corações; máximo +20 corações).
- **Túmulo:** GraveStone Mod (`gravestone-mod`, via `pack\pendentes.txt`). Ao morrer, os itens ficam num túmulo no local.
- **Tempest** reduzido para 52×35 chunks (x −288 a 543, z −272 a 287), centrado no coliseu e na praça.
- **Dwargon** reduzido para 20×20 chunks (igual ao Oeste), em volta do trono, dos NPCs e do poço de entrada.
  - Os originais estão em `server\frxworld\reinos-originais\`; script em `tools\reduzir_reino.py`.
  - Precisa de mundo novo.
- **Casinhas dos NPCs** (`reinos/Oficios.java`): cada ofício ganha uma barraca 5×5 temática com balcão, e o NPC fica dentro dela, de frente para a praça.
  - Os ofícios são mercador, comprador, ferreiro (forja), armeiro, banqueiro, sacerdote, sábio, cartógrafo e taverneiro.
  - As barracas formam um anel em volta da praça (raio 12, fugindo das ruas), ou usam as posições fixas do `posicoes.json` quando há espaço. Os guardas passaram para o raio 18.
  - Não dá para quebrar uma barraca fora do criativo (registro em `<mundo>/frx_oficios.txt`).
- **Tesouros nas masmorras** (`reinos/Masmorras.java`):
  - Ao colar uma masmorra fixa (como a Fábrica Ancestral sob o coliseu), 5 baús do Lootr são espalhados pelas salas, com a tabela `frxworld:chests/masmorra`. Ela traz diamantes, ouro, netherite, livros encantados, tomos e equipamento encantado, e tem raridade de masmorra.
  - Ao derrotar o chefe, aparece um baú de prêmio onde ele caiu (`masmorra_chefe`: lingote de netherite, tomo arcano, maçã encantada e mais).
  - `/frx masmorra baus` põe 5 baús nas salas em volta de quem rodou, para mundos já gerados.
- **Servidor offline** (TLauncher/Shiginima): `online-mode=false`; o acesso continua só pelo convite, preso ao nick (sem senha). Até usar o convite: sem chat e sem comandos. O kit do amigo agora pergunta o launcher.
- **Cambista** nas capitais (troca de moedas sem taxa) e **Rings of Ascension**: anéis comuns nos baús das masmorras e um anel forte garantido no baú do chefe.
- **Backup automático:** o atalho Desligar guarda o mundo (zip) e o banco do painel (SQL) em `backups\`, mantendo os 7 mais recentes.
- Painel: migração `20261110200000_balanco_monstros` aplica os novos valores e volta as chances de Elite/Campeão para 5% e 0,5%.
- AutoModpack: os mods dos amigos baixam sozinhos ao entrar no servidor (kit `amigos/INSTALAR.bat`).
- **Menu do banqueiro** (`TelaBanco`): clique abre a tela com saldo do cofre e da bolsa, abas Depositar / Sacar / Câmbio (Shift = de 10 em 10). O cambista abre a mesma tela só com o câmbio.
- **Menu do ferreiro e do armeiro** (`TelaForja`): aba para aprimorar/reforjar (custo, material, chance, escolha do item pela barra) e aba Loja com as ofertas do NPC. Agachado + clique ainda abre a troca clássica.
- Rede: pacotes `AbrirMenu`/`AcaoMenu`, protocolo 11 (cliente e servidor precisam do mesmo jar).
- Painel: botão **Tornar admin** na ficha; raça na ficha; editor das **faixas de nível por dimensão** (Progressão → Zonas); telas de Masmorras, Chefes de mods, Habilidades e Eventos redesenhadas; skin do Cambista em NPCs; fichas duplicadas do mesmo nick são unidas no login.
- **Lojas pelo nível do jogador** (`reinos/Lojas.java`, `reinos/Catalogo.java`):
  - Ferreiro (armas/ferramentas) e armeiro (armaduras): patamares Nv 1 ferro, 10 ferro Incomum +2, 20 diamante, 30 diamante Raro +3, 40 netherite, 50 netherite Épico +5. Aparecem o patamar do jogador e o anterior.
  - Mais o "estoque do dia": até 12 armas/armaduras dos outros mods, medidas pelo dano/defesa (só itens com receita e até o nível 60), com preço pela força.
  - Mercador: cada item do painel (Economia) tem **nível mínimo**; migração `20261111230000_loja_nivel` define níveis para os itens antigos, pausa armas/escudo/arco e cria itens de nível alto.
  - **Cozinheiro** (NPC novo, `/frx npc aqui cozinheiro`): só comida, de todos os mods, 18 itens por dia conforme o nível.
- **Janela de Sistema** (`progressao/Sistema`, `cliente/HudSistema`, pacote `AvisoSistema`): caixa azul no topo da tela para subir de nível, missão concluída, conquista/título, prova e promoção.
- **Guilda de Aventureiros** (`progressao/Guilda`, `cliente/TelaGuilda`): NPC **recepcionista** (`/frx npc aqui recepcionista`), item **Cartão de Aventureiro** (`frxworld:cartao_aventureiro`) e **prova de promoção**. O rank F–S agora só sobe na prova: nível mínimo do rank (aba Progressão → Ranks) + derrotar o Examinador em 3 min sem morrer; falhou, espera 10 min. Prêmio: 2 ouro por degrau. Protocolo de rede 12.
- **Habilidade Única** (`habilidades/Unica`): cada jogador desperta uma das 10 passivas no primeiro login (Grande Sábio, Devorador, Fúria do Dragão, Corpo de Aço, Passo Sombrio, Olho do Falcão, Sangue Real, Filho do Vento, Segunda Chance, Instinto do Caçador). Evolui do nível 1 ao 5 com monstros abatidos (50 / 200 / 600 / 1500). `/unica` mostra; admin: `/frx unica sortear` ou `/frx unica <id>`.
- **Portais de masmorra** (`reinos/Portais`): a cada 40 min, 50% de chance de abrir um portal com rank perto da capital mais próxima de um jogador. Chegar a 6 blocos começa a incursão (3 ondas + Guardião, monstros do nível do rank, listas da aba Invasões). Fechou: XP e moedas para quem estiver por perto. 12 min sem fechar (ou 8 min de luta): o portal se rompe e começa uma invasão na capital. `/portal` mostra onde está; admin: `/frx portal aqui [rank]`, `/frx portal fechar`.
- **Apresentação de chefe** (`cliente/HudChefe`): ao ver um chefe pela primeira vez (até 32 blocos), barras de cinema, nome grande e música de batalha até ele morrer ou ficar para trás. O Guardião do Portal agora conta como chefe.
- **Fases de chefe** (`progressao/Combate`): aos 66% e 33% da vida o chefe enfurece (velocidade; depois força e resistência) e quem está a até 48 blocos é avisado na janela de Sistema.
- **Dano flutuante** (`cliente/DanoFlutuante`, pacote `NumeroDano`): números saltam das criaturas quando um jogador bate (branco; dourado com "!" se tirar 30% da vida de uma vez) ou apanha (vermelho).
- **Título acima da cabeça**: o título escolhido em `/titulo` aparece numa linha roxa sobre o nome dos outros jogadores (`SyncNivel` leva o título). Protocolo de rede 13.
- **Abertura isekai no primeiro login** (`acesso/Abertura`, `cliente/TelaAbertura`): assim que o convite é aceito, tela preta com as falas do Sistema digitadas letra a letra, depois escolha de raça, escolha de classe, o despertar da Habilidade Única e o nascimento na capital inicial (que também vira o ponto de renascimento). O jogador fica imune enquanto a cena roda; a habilidade única deixa de ser anunciada no chat para quem ainda vai ver a abertura. Teste com `/frx abertura`. Painel: **Convites → Abertura do primeiro login** (ligar/desligar, capital e as falas).
- **Missões e conquistas saíram do inventário**: as abas Missões e Conquistas foram removidas da fileira do inventário (sobraram 6). As conquistas viraram o capítulo **Conquistas** do livro de Quests (28 quests agrupadas em Caçada, Presas, Chefes, Jornada, Ofício e riqueza, Os reinos e Cicatrizes), ligadas por advancement `frxworld:frx/conq_<id>` que o `Marcos` concede — inclusive para quem já tinha a conquista. As missões dos reis já estavam no capítulo "Os Oito Reinos".
- **Mercador com o menu do ferreiro**: clicar abre a mesma tela de loja, com a lista do painel filtrada pelo nível mais um estoque do dia de até 10 utilidades dos mods (itens com receita que não são arma, armadura, comida nem bloco; épico fica de fora).
- **Comprador aceita qualquer item** (`reinos/Avaliador`, `cliente/TelaVender`): preço automático pela raridade do jogo, pela raridade/forja do FRX, encantamentos e desgaste; moedas e o Cartão da Guilda não são aceitos. "Vender tudo" pede confirmação.
- **Limite de ouro por dia**: o comprador paga até um teto que cresce com o nível (padrão 20 ouro + 3 por nível), mostrado na tela e renovado todo dia.
- **Evolução de raça** (`racas/Evolucao`): ao bater um marco (nível, honra com um reino, campanha ou chefe mundial), a raça da mesma família sobe um degrau com `/evoluir` — cena com partículas, título na tela e anúncio no servidor. A troca não cobra moedas.
- **Diálogo com retrato** (`reinos/Dialogos`, `cliente/TelaDialogo`): rei, taverneiro, sacerdote, sábio e cartógrafo abrem uma caixa de fala com o NPC desenhado ao vivo, texto digitado letra a letra e opções de resposta (missão, reino, quadro, loja). Agachado + clique mantém a troca antiga.
- Painel: **Economia → Comprador** (preços por raridade, fração de venda, bônus de encantamento e o teto diário) e **NPCs e skins → Falas da caixa de diálogo** (uma fala por linha, `{jogador}` vira o nick).
- Ranckor's Rings of Power: pendente (download manual do jar em `pack\mods`).

## 0.40.0 — Fase 2: FTB Quests, Apothic Attributes e dimensões

- Bat `pack/adicionar-mods-fase2.bat`: FTB Quests (+ Library, Teams, Architectury), Apothic Attributes (+ Placebo), Twilight Forest, Aether, Blue Skies (+ Structure Gel), Undergarden e Deeper and Darker.
- `atributos/Apoth.java`: ponte com o Apothic. Talentos, raças e equipamentos passam a dar crítico, esquiva, roubo de vida, perfuração e mineração.
- `Zonas`: faixas de nível por dimensão, título ao trocar de dimensão, fatores de perigo e de XP.
- `Ranks`: Elites e Campeões respeitam a faixa, e um jogador forte por perto gera desafios maiores.
- `Marcos`: 48 advancements ocultos para as tasks do FTB Quests.
- `tools/gerar_ftbquests.py`: 11 capítulos e 78 quests.

## 0.39.0 — Monstros com rank

- `progressao/Ranks.java`: Normal (`Zumbi Lv. 3`), Elite (★1–5 e traços), Campeão (prefixo, título e barra), Chefe e Chefe Mundial.
- Rótulos no mundo (`SyncRotuloMob`, `cliente/RotulosMob`) com a cor do nível relativa à sua.
- Painel: card "Monstros com rank" em Progressão.

## 0.38.0 — Raças

- 16 raças (`racas/DefRacas`, `racas/RacasJogador`), principal + secundária, compatibilidade, troca com custo progressivo, disfarce das lendárias.
- Visual: `CamadaRaca`, aura (`ClienteRacas`), tela `TelaRacas`, sufixo no nome e no TAB.
- NPCs: 7 novos tipos de morador (beastkin, halfling, oni, vampiro, dragonoide, espírito, gigante), 35 skins novas e misturas por reino.
- Comandos `/raca` e `/frx raca`.

## 0.33.0 – 0.37.0 — Poderes especiais, Exército de Sombras e waystones

- **Despertar** (tecla B): um poder inspirado em anime para cada classe, com barra própria.
- **Exército de Sombras** na tela de Habilidades: invocar e recolher uma ou todas, libertar, 3 atalhos + Z.
- **Waystones dos reinos:** 5 por reino (centro + 4 portões), colocadas quando o chunk carrega.
- O mod envia a lista de itens ao painel (ícones e nomes nos seletores).
- Protocolo de rede **8** na 0.37 (hoje **10**).
- Painel no mesmo período: Missões, Economia, Reinos e Dungeons redesenhados.

## 0.29.0 – 0.32.0

Sem registro detalhado nesta documentação.

## 0.28.0 — Rei visível, raças com skin, forja e banqueiro

- NPCs nascem ao entrar na caixa do reino; comando `/rei`.
- Moradores com 7 raças e skins próprias (`RenderRacas`).
- Linha de raridade/aprimoramento no lore dos itens.
- Banqueiro protegido da conversão em guarda.
- Mobs do Ice and Fire fora dos reinos.

## 0.27.x — Baús por reino, saque de masmorra, Dwargon maior

- `BausReino` (Lootr, nível de saque por reino).
- `RaridadeLoot` nos baús de masmorra.
- Dwargon com 56×60 chunks.
- Menus no estilo da aba Habilidades e teclas configuráveis.

## 0.26.x — Campanhas dos 3 reinos novos e masmorras assíncronas

- Campanhas de Oeste, Lubelius e Lordes Demônios; nomes dos NPCs.
- Masmorras coladas de forma assíncrona, sem travar o servidor.
- Abas com ícone.

## 0.25.0 — Classe Assassino

Usa Energia; bônus pelas costas e contra alvos feridos. Talentos Letalidade, Sangria e Fantasma.

## 0.24.0 — Floresta Sombria em volta de Tempest

## 0.23.0 — Janela de Status (teclas E / K)

Abas Inventário, Status, Habilidades, Talentos, Reinos, Missões, Conquistas e Grupo. Protocolo **5**.

## Até 0.22.0 — Fundação

- Fases 0–2: painel, convites, servidor e pack; XP, níveis, ranks e morte; missões, quadro e taverna.
- Dungeons próprias (catacumba em 3 camadas), chefes de mods (0.8), eventos de estrada (0.9), classes, mana e habilidades (0.10).
- Reinos e capitais com NPCs (0.11–0.12), reputação e campanhas, painel de reinos (0.22).
