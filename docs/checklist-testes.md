# FRX-WORLD — checklist de testes (noite de 17/11)

Atualizado às 19:45 de 17/11. O servidor roda a versão 0.42.5; a 0.42.7 (estilo de música nos discos, painel novo dos terrenos e edição de dinheiro por moeda) entra no próximo reinício. Tudo abaixo entrou em 16 e 17/11 e ainda não foi testado dentro do jogo. A ordem é do que mais pode dar errado para o que menos pode. Marque `[x]` no que funcionar e anote ao lado o que falhar (o que você fez, o que apareceu).

O ideal é testar com **duas contas** (você e um amigo): vários itens dependem de um segundo jogador.

## Por onde começar

A lista cresceu muito hoje. Se o tempo for curto, esta é a ordem que mais vale:

1. **Preparação** (bloco 0) e conferir que todos atualizaram o pack e aceitaram o pacote de recursos.
2. **Classes novas** — Paladino, Bardo, Monge e Druida (8b, 8c, 8d): quatro classes sem nenhum teste.
3. **Torre com monstros dos mods** (8f): um monstro preso trava o andar.
4. **Missões e quadro do dia** (8e) e **alvo das sombras** (8g).
5. **Discos de música** (8h) e **painéis novos** de clãs, leiloeiro e discos (8i).
6. **Terrenos, clãs e mercado** (1, 2, 3).
7. O resto, quando der.

## 0. Preparação (admin, 5 minutos)

- [ ] Dentro de uma capital, colocar o Mestre dos Clãs: `/frx npc aqui clas`
- [ ] Dentro de uma capital, colocar o Leiloeiro: `/frx npc aqui leiloeiro`
- [ ] Dentro de uma capital, colocar o Mercador de Discos: `/frx npc aqui discos`
- [ ] No painel, cadastrar pelo menos um disco de música (arquivo .ogg)
- [ ] Todos os jogadores com o pack atualizado (versão 0.42.7 do mod, mais Woodwalkers e CraftedCore) e com o pacote de recursos aceito
- [ ] Para testar classes sem criar conta: `/frx classe <jogador> paladino|bardo|monge|druida|invocador`
- [ ] Ter moedas na bolsa nas duas contas (pelo painel, em Dinheiro dos jogadores, ou por missão)
- [ ] As duas contas registradas na Guilda (recepcionista)

## 1. Terrenos pelo mapa — risco alto

O menu do mapa roda no jogo do jogador e é a parte que eu menos consegui conferir.

- [ ] Abrir o mapa (tecla J), botão direito: aparecem os três itens "Terreno: …"
- [ ] Marcar o 1º canto: chega a mensagem no chat e aparece um quadradinho amarelo no mapa
- [ ] Marcar o 2º canto: abre o painel "Terrenos" na aba Comprar, com o desenho da área, o tamanho, o preço e as barras de limite do rank
- [ ] Comprar: as moedas saem da bolsa e o terreno aparece pintado de verde no mapa, com "Seu terreno"
- [ ] Na outra conta, o mesmo terreno aparece em vermelho, com "Terreno de Fulano"
- [ ] A outra conta **não** consegue quebrar, colocar bloco, abrir baú nem porta dentro do terreno
- [ ] Ao entrar e sair do terreno aparece "Terreno de Fulano" / "Terras livres"
- [ ] "Terreno: meus terrenos e permissões" abre o painel; na aba "Quem pode mexer", clicar na outra conta e conferir que ela passa a construir (e o terreno fica laranja no mapa dela)
- [ ] Aba "Quem pode mexer": ligar só "Colocar" para a outra conta: ela coloca blocos, mas não quebra nem abre baú
- [ ] Ligar só "Quebrar": ela quebra, mas não coloca; ligar só "Baús e portas": abre baú e porta, mas não coloca nem quebra
- [ ] Desligar os três: a outra conta volta a não mexer em nada e o terreno fica vermelho no mapa dela
- [ ] Ampliar: escolher uma área que passa por cima do seu terreno: o painel mostra "Área nova: N blocos (M já são seus)", cobra só os novos e deixa comprar
- [ ] Escolher uma área toda dentro do seu terreno: recusa com "Essa área inteira já é sua"
- [ ] Aba Meus terrenos: "Abrir para todos" e "Fechar os terrenos"
- [ ] "Mostrar as divisas": partículas no chão em volta do terreno
- [ ] Tentar comprar área maior que o limite do rank (Rank F: lado até 24 e 512 blocos de área): o painel diz o motivo, a barra do limite fica vermelha e "Comprar" fica apagado
- [ ] Tentar comprar em cima da capital ou da Torre: recusa com "terras do reino"
- [ ] Vender o terreno (clicar nele, "Vender", "Confirmar a venda?"): volta metade das moedas e some do mapa
- [ ] Aba Comprar sem o mapa: "1º canto aqui", andar, "2º canto aqui": o desenho da área aparece; "Limpar" apaga a escolha
- [ ] Com um terreno vizinho por perto, ele aparece no desenho da área (verde se é seu, vermelho se é de outro)
- [ ] Creeper explodindo dentro do terreno não quebra blocos
- [ ] Admin: `/frx terreno proibir 2` marca a área (aparece cinza no seu mapa) e ninguém compra ali; `/frx terreno liberar 2` desfaz
- [ ] Se o menu do mapa **não** aparecer: `/terreno` abre o mesmo painel

## 2. Mestre dos Clãs — risco alto

- [ ] Clicar no NPC abre "Mestre dos Clãs" com "Seus clãs: N de 3"
- [ ] O clã antigo ("Fatallz's Party") aparece na lista de todos os clãs
- [ ] Fundar um clã (Shift + clique): vira líder e abre a página do clã
- [ ] Na outra conta: ver a lista, clicar no clã para pedir entrada; o líder recebe o aviso
- [ ] Líder: abrir "Pedidos", aceitar; a outra conta recebe "Você entrou no clã"
- [ ] A outra conta pede entrada em um **segundo** clã e é aceita (dois clãs ao mesmo tempo)
- [ ] Cofre: depositar 1 ouro, sacar 1 prata; o registro de movimentos mostra os dois
- [ ] Saque avisa o outro membro que está online
- [ ] Líder muda "Saques: só o líder e os oficiais"; o membro comum deixa de conseguir sacar
- [ ] Renomear: etiqueta renomeada na bigorna, na mão, clicar em "Mudar o nome"
- [ ] Trocar a cor (um clique)
- [ ] Tornar a outra conta oficial (clique no membro); ela passa a aceitar pedidos
- [ ] Passar a liderança (clicar no membro segurando um bloco de ouro)
- [ ] Expulsar (Shift + clique no membro)
- [ ] Sair do clã (Shift + clique); o último a sair leva o que estava no cofre
- [ ] No site, seção Clãs: o clã aparece com membros, poder, riqueza e cofre

## 3. Mercado (Leiloeiro) — risco médio

- [ ] Clicar no NPC abre o balcão com Comprar, Vender, Meus anúncios e Receber
- [ ] Com um item na mão, "Vender": aparece o item, os botões mudam o preço (Shift + clique diminui)
- [ ] Anunciar: o item sai da mão e aparece em "Meus anúncios"
- [ ] Na outra conta, "Comprar": o item está na vitrine com preço e vendedor; Shift + clique compra
- [ ] O comprador recebe o item **com encantamentos, forja e raridade intactos**
- [ ] O vendedor recebe o aviso e, em "Receber", pega o preço menos 5%
- [ ] Vender com o vendedor fora do jogo; ao entrar ele é avisado do que tem a receber
- [ ] Cancelar um anúncio (Shift + clique em "Meus anúncios"): o item volta
- [ ] Tentar vender moedas: recusa
- [ ] Trocar a ordem da vitrine (mais novos / mais baratos)

## 4. Contratos da Guilda — risco médio

- [ ] Ao entrar no jogo aparece o aviso "Contratos da Guilda de hoje"
- [ ] Cartão de Aventureiro (botão direito): aparece o botão "Contratos" e ele abre o quadro
- [ ] O mesmo botão aparece no balcão da recepcionista
- [ ] Matar monstros: a barra de ação mostra o progresso do contrato
- [ ] Completar um contrato: moedas e XP caem na hora, com aviso
- [ ] Minerar minério e pescar contam (se forem contratos do dia)
- [ ] Completar os três do dia: chega o pagamento extra
- [ ] O quadro é um painel de pergaminho: três cartões do dia com barra de progresso e recompensa, a faixa do extra, o contrato da semana e "renovam em…"
- [ ] Na última linha do quadro, o resumo da coleção; passar o mouse mostra os detalhes

## 5. Coleção — risco baixo

- [ ] Matar um tipo de monstro pela primeira vez: aviso "Coleção: … — N tipos"
- [ ] Matar o mesmo tipo de novo: não avisa
- [ ] Aos 10 tipos: aviso de marco com moedas e XP, e a conquista "Curioso"
- [ ] No site, ficha do jogador: aparece "N de M tipos de criatura derrotados"

## 6. Códex de Criaturas e bestiário — risco médio

- [ ] Painel `/admin/criaturas`: os 14 chefes aparecem; abrir um, mudar algo e salvar
- [ ] "Desenhar" abre o editor de skin; salvar a skin no chefe
- [ ] "Importar skin" com um PNG 64×64 baixado
- [ ] "Invocar perto de mim" (com seu nick): o chefe aparece a 4 blocos
- [ ] Chefe com skin aparece com corpo de jogador e a skin; mudar o "Tamanho do desenho" e invocar de novo
- [ ] O chefe usa os golpes marcados (aviso na tela antes de cada um)
- [ ] Ao morrer: XP, moedas e item especial configurados chegam; "Derrotado 1×" no painel
- [ ] Criar um monstro com chance alta (ex.: 80%) em "Masmorras de portal" e conferir que aparece numa dungeon de portal
- [ ] No site, Bestiário: os chefes aparecem como "? ? ?"; o que você derrotou passa a mostrar nome, história, golpes e skin

## 7. Códex de Armas — risco médio

- [ ] Painel `/admin/codex`: criar uma arma com desenho próprio e venda no ferreiro
- [ ] Ao entrar no jogo, aparece o pedido do pacote de recursos; aceitar
- [ ] No ferreiro, a arma aparece no topo para quem tem o rank; comprar
- [ ] A arma mostra o desenho novo na mão e "nº 1 de N" na descrição
- [ ] O painel mostra "No mundo: 1 de N" e com quem está
- [ ] Arma que evolui: só o dono consegue bater com ela; o dano sobe quando o dono sobe de nível
- [ ] Arma como recompensa de missão (`codex:código`)

## 8. Classe Assassino — risco médio

- [ ] As 9 habilidades novas aparecem na tela de habilidades
- [ ] Passo Fantasma leva para trás do alvo
- [ ] Bomba de Fumaça, Lâminas Envenenadas e Sangria Profunda aplicam o efeito
- [ ] Dança da Morte (suprema) salta entre inimigos

## 8b. Classe Paladino (nova) — risco médio

Precisa de uma conta sem classe (ou um admin trocar a classe) e, para metade dos itens, de um aliado por perto.

- [ ] Na tela de escolha de classe aparece o botão "Paladino · Mana"
- [ ] As 12 habilidades aparecem e as de nível 1 e 2 já podem ir para a barra
- [ ] Golpe do Cruzado fere; em zumbi ou esqueleto o dano é o dobro
- [ ] Imposição das Mãos cura o aliado na mira (sem mira, cura você)
- [ ] Escudo Arremessado ricocheteia entre inimigos
- [ ] Desafio Sagrado faz os monstros largarem o aliado e virem para você
- [ ] Voto do Guardião: o aliado leva metade do dano e o resto aparece em você
- [ ] Intervenção leva você até o aliado e os dois ganham corações extras
- [ ] Sacrifício tira vida de você e cura o aliado
- [ ] Juízo Final (nível 25): coluna de luz por 8 s, com raios nos inimigos
- [ ] Aura de Proteção: o aliado a menos de 6 blocos de você leva um pouco menos de dano
- [ ] Despertar (tecla B, nível 10): a barra enche apanhando; a cúpula aparece e, no fim, o clarão fere em volta
- [ ] Talentos: os três ramos (Baluarte, Justiça, Devoção) aparecem
- [ ] No site, a Biblioteca ganhou o livro do Paladino

## 8c. Classes Bardo e Monge (novas) — risco alto

Para trocar a classe de uma conta: `/frx classe <jogador> bardo` ou `monge`. O Bardo pede um aliado por perto em metade dos itens.

- [ ] A tela de escolha de classe mostra dez botões e, ao clicar em um, a descrição aparece embaixo
- [ ] Bardo: as 12 habilidades aparecem; Nota Dissonante fere em linha reta
- [ ] Canção da Marcha: você e o aliado ficam mais rápidos; notas musicais giram em volta
- [ ] Começar outra canção para a anterior (aviso "a canção anterior parou")
- [ ] Balada Restauradora cura os dois a cada segundo
- [ ] Hino de Guerra: o aliado causa mais dano enquanto está por perto
- [ ] Canção de Ninar: os monstros em volta param e largam o alvo
- [ ] Zombaria: o alvo brilha e leva mais dano de todos
- [ ] Refrão Ecoante: as recargas do aliado encurtam na barra dele
- [ ] Sinfonia Final (nível 25): cura, velocidade e dano nos inimigos por 10 s
- [ ] Inspiração: o aliado a menos de 8 blocos do Bardo bate um pouco mais forte
- [ ] Despertar do Bardo (Bis!): zera as recargas e enche o recurso dos dois
- [ ] Monge: a barra de recurso se chama Chi e começa vazia
- [ ] Soco de mão vazia fere bem mais que meio coração; aparece "Combo 1 de 4", 2, 3 e "Golpe final!"
- [ ] O golpe final empurra o monstro e cada soco enche o chi (10; 20 no final)
- [ ] Com espada na mão o Monge bate como qualquer um (sem combo, sem chi)
- [ ] Outra classe de mão vazia continua com o soco fraco de sempre
- [ ] Parado fora de combate, o chi vai esvaziando
- [ ] Palma de Ferro arremessa o alvo; Rajada de Socos dá seis golpes seguidos
- [ ] Meditação cura e enche o chi sem gastar nada
- [ ] Toque dos Sete Pontos: depois de 4 s o alvo estoura com dano extra
- [ ] Sem armadura, aparece "Esquivou!" de vez em quando e o dano recebido é menor
- [ ] Cem Punhos (nível 25): socos em tudo em volta por 6 s e um estouro no fim
- [ ] Despertar do Monge (Portão Interior): socos em dobro, habilidades sem gastar chi, exaustão no fim
- [ ] No site, a Biblioteca ganhou os livros do Bardo e do Monge

## 8d. Classe Druida (nova) — risco alto

Trocar a classe: `/frx classe <jogador> druida`. O corpo de animal vem do mod Woodwalkers, que já está no pack.

- [ ] As 14 habilidades aparecem; Chicote de Espinhos e Rejuvenescer funcionam na forma humana
- [ ] Forma de Lobo (nível 2): aviso com o tempo, mais velocidade, e a barra troca para as habilidades de lobo
- [ ] Na forma de lobo, bater com espada na mão causa o dano das garras (não o da espada)
- [ ] Na forma de lobo, tentar uma magia humana avisa "só funciona na forma de humano"
- [ ] Usar Forma de Lobo de novo volta a humano na hora e a barra humana volta como estava
- [ ] Depois de voltar, tentar transformar antes de 15 s avisa o tempo que falta
- [ ] A forma acaba sozinha aos 45 s, com aviso 5 s antes
- [ ] Forma de Urso (nível 10): mais corações, mais lento; Patada e Rugido funcionam
- [ ] Passar de lobo para urso não renova os 45 s
- [ ] Forma de Ave (nível 20): voa por 15 s e, ao acabar no ar, cai devagar
- [ ] Tecla K dentro de uma forma organiza a barra daquela forma, e ela é lembrada na próxima vez
- [ ] Morrer ou sair do jogo transformado: volta humano, com a barra humana certa
- [ ] Com o Woodwalkers: o corpo vira lobo, urso polar e papagaio; os outros jogadores veem; o nome continua aparecendo
- [ ] Com o Woodwalkers: outra classe **não** consegue se transformar (nem pela tecla do mod)
- [ ] Com o Woodwalkers: monstros continuam atacando o Druida transformado
- [ ] Ira da Floresta (nível 25) funciona em qualquer forma
- [ ] No site, a Biblioteca ganhou o livro do Druida

## 8e. Missões da Guilda em lote e quadro do dia — risco médio

- [ ] O quadro de missões abre com cerca de 12 missões do seu rank, 4 do rank de baixo e 2 do de cima (bloqueadas)
- [ ] Os nomes dos alvos de mods aparecem certos no objetivo (ex.: criaturas da Floresta do Crepúsculo)
- [ ] Aceitar uma caçada, matar o alvo: o rastreador conta e a missão fica pronta para entregar
- [ ] Aceitar uma encomenda com os itens na mochila e entregar: os itens saem e o prêmio chega
- [ ] As duas contas veem as mesmas missões do dia (no mesmo rank)
- [ ] No dia seguinte o quadro traz outras missões; a que você tinha aceitado continua lá
- [ ] Painel → Missões: a lista abre com as mais de mil missões sem travar
- [ ] Anotar qualquer missão cujo alvo você nunca viu no mundo (dá para desativar no painel)

## 8f. Torre e portais com monstros dos mods — risco médio

- [ ] Torre, andares 1 a 10: aparecem monstros de outros mods junto com zumbis e esqueletos
- [ ] Todo andar termina: nenhum monstro fica preso na parede, voando fora de alcance ou sem atacar (anotar qual, se acontecer)
- [ ] A contagem "faltam N" chega a zero e o andar seguinte começa
- [ ] Um andar de cada tema (Deserto 11+, Floresta 21+, Fornalha 31+, Geleira 41+, Abismo 51+) com os monstros novos
- [ ] Masmorra de portal: também traz monstros dos mods

## 8g. Invocador: alvo das sombras — risco médio

- [ ] Com sombras invocadas, clicar com o botão do meio numa criatura: aviso "Alvo marcado", ela brilha e as sombras vão nela
- [ ] As sombras ignoram outros monstros por perto enquanto o alvo está vivo
- [ ] Quando o alvo morre, as sombras voltam a atacar o que estiver perto
- [ ] Clicar de novo no mesmo alvo, ou sem nada na mira, solta o foco
- [ ] Uma sombra invocada depois da marcação também vai no alvo
- [ ] O botão aparece em Opções → Controles → FRX-WORLD e pode ser trocado
- [ ] Em outra classe, o botão do meio continua normal

## 8h. Discos de música — risco alto

- [ ] Painel → Discos de música: cadastrar um disco com um arquivo .ogg; aparece na lista com duração e tamanho
- [ ] Enviar um .mp3: o painel recusa e explica
- [ ] Editar o disco sem trocar o arquivo; o player de prévia toca a música no painel
- [ ] No jogo: `/frx npc aqui discos`; o Mercador de Discos abre a loja com o disco (até 1 minuto depois de cadastrar)
- [ ] Ao entrar (ou logo depois de cadastrar), o jogo pede o pacote de recursos; aceitar
- [ ] Comprar: as moedas saem e o disco chega com nome, artista e a aparência escolhida
- [ ] Clicar numa jukebox com o disco: a música toca, aparece "Tocando: …" e notas em cima da jukebox
- [ ] Clicar de novo na jukebox: o disco sai e a música para
- [ ] Quebrar a jukebox com o disco dentro: o disco cai
- [ ] A outra conta, por perto, também ouve
- [ ] Um disco comum do Minecraft continua funcionando na mesma jukebox
- [ ] Cadastro: "Gerar disco nessa cor" desenha o disco; retocar na grade; "Importar PNG" com uma imagem 16×16
- [ ] No jogo, o disco comprado aparece com o desenho próprio (depois de aceitar o pacote novo)

## 8i. Painéis novos (clãs, leiloeiro, discos) — risco alto

As três telas rodam no jogo do jogador e não foram testadas. Os blocos 2, 3 e 8h continuam valendo para as regras; aqui é a tela.

- [ ] Mestre dos Clãs abre um painel de pergaminho (não mais um baú), com as abas "Meus clãs" e "Todos os clãs"
- [ ] "Fundar um clã novo" pede um segundo clique ("Confirmar?") e abre a aba Gerir
- [ ] Gerir: digitar o nome e "Mudar o nome"; clicar numa cor da paleta; trocar a regra de saque
- [ ] Cofre: + e − nos contadores, Depositar, Sacar, "tudo"; saldo e últimos movimentos atualizam
- [ ] Membros: "Tornar oficial", "Líder" e "Expulsar" (os dois últimos pedem confirmação)
- [ ] Pedidos: Aceitar e Recusar; em "Todos os clãs", "Pedir entrada" vira "Cancelar"
- [ ] Leiloeiro: aba Comprar mostra os cartões; digitar na busca filtra; o botão de ordem troca; a roda do mouse muda de página
- [ ] Passar o mouse num cartão mostra a dica do item (encantamentos, forja); clicar seleciona e libera "Comprar"
- [ ] Aba Vender: a sua bolsa aparece em grade; clicar num item, definir o preço e "Anunciar": o item sai da bolsa
- [ ] Abas "Meus anúncios" (tirar do mercado) e "Receber"
- [ ] Mercador de Discos: cartões dos discos; "Ouvir" toca a música e "Parar" para; fechar a tela também para
- [ ] Painel → Discos de música: o campo "Estilo de música" sugere estilos e aceita outro; salvar e reabrir mantém; os botões de estilo acima da lista filtram
- [ ] Mercador de Discos: com discos de dois estilos, o botão "Estilo: todos" troca para cada estilo e mostra só os discos dele; Shift + clique volta
- [ ] O estilo aparece nos detalhes do disco na loja e na descrição do item comprado
- [ ] Disco raro: no painel, pôr "Unidades no mundo" = 2; na loja o cartão mostra 2/2; comprar duas vezes: os discos saem "nº 1 de 2" e "nº 2 de 2" e a loja passa a mostrar esgotado
- [ ] Depois das compras, a lista do painel mostra "2 de 2 no mundo (esgotado)"
- [ ] "Comprar" no disco: as moedas saem e o disco chega
- [ ] Nenhuma das telas deixa texto vazando para fora da moldura (anotar onde, se acontecer)

## 8j. Mercador de Escravos — risco médio

- [ ] `/frx npc aqui escravos` coloca o Mercador de Escravos; clicar abre o painel com sete aldeões desenhados
- [ ] Conta abaixo do Rank D: "Exige Rank D na Guilda" em vermelho e o botão Comprar apagado
- [ ] Conta Rank D ou mais: escolher um aldeão e comprar: saem 5 de ouro e chega o "Contrato de Escravo"
- [ ] Usar o contrato no chão: aparece um aldeão sem profissão, com a roupa escolhida, e o contrato some
- [ ] Com uma bancada de trabalho por perto, o aldeão pega a profissão
- [ ] Usar o contrato num gerador de monstros: recusa com "O contrato só serve no chão"
- [ ] Limite: no Rank D o painel mostra "Seus aldeões: 0 de 4"; depois de comprar quatro, o quinto é recusado
- [ ] Matar um aldeão comprado: a contagem cai e dá para comprar de novo

## 8k. Personagens (até 3 por conta) — risco alto

Antes de testar com a sua conta principal, vale testar com uma conta de teste: é a parte que mexe no arquivo do jogador.

- [ ] Entrar no servidor: uns 3 segundos depois abre a tela com três estandartes; o seu personagem aparece com o boneco e "em jogo"
- [ ] "Entrar" fecha a tela; a tecla F8 abre de novo
- [ ] "Nome": trocar o nome; ele passa a aparecer no chat, em cima da cabeça e no Tab
- [ ] "Criar personagem" numa vaga livre, dar um nome: o jogo sai e entra de novo sozinho, e a escolha de raça e classe começa
- [ ] O personagem novo começa sem itens, no nível 1, e o saldo do banco é o mesmo do outro
- [ ] F8 → "Jogar" no primeiro personagem: volta com inventário, nível, classe e posição exatamente como estavam
- [ ] Depositar no banco com um personagem e conferir o saldo com o outro
- [ ] Clãs: o personagem novo não está nos clãs do primeiro; o primeiro continua neles
- [ ] Os terrenos da conta valem para os dois personagens
- [ ] Na pasta do mundo, `frx_personagens` tem o índice e um arquivo por personagem guardado

## 9. Itens de 16/11 ainda sem teste

- [ ] Portal: dois jogadores entram pelo mesmo portal e caem na mesma dungeon; a lotação barra o excedente (Rank F: 2)
- [ ] Arco e besta acima do rank da Guilda não disparam
- [ ] Chefe de masmorra embaixo de reino: muito mais forte; jogador abaixo do nível 50 quase não causa dano
- [ ] Nether barrado abaixo do Rank D; End barrado abaixo do Rank C
- [ ] Nether: emboscada depois de alguns minutos; estruturas e monstros novos
- [ ] Torre em grupo: contagem de 10 s na porta leva todos juntos; XP dividida
- [ ] Torre: chefe épico de mod no andar 20
- [ ] Loja da Torre abre em janela
- [ ] Lojas mostram só equipamento que o rank pode usar
- [ ] Invocações e sombras sobem de nível; barra de XP na tela das sombras
- [ ] Tranca de baú: agachado + gancho de armadilha; a outra conta não abre
- [ ] `/ping` mostra a conexão
- [ ] Vida dos outros jogadores aparece embaixo do nome e na lista (Tab)
- [ ] Maycon ao entrar: nível 18, Rank E e Torre zerada

## 10. Site e painel — risco baixo

- [ ] `craft.frgomes.com.br` abre; seções Clãs, Bestiário, Biblioteca e Novidades aparecem
- [ ] Biblioteca: estante em pixel art; clicar num livro abre e as setas folheiam
- [ ] Novidades mostra data e hora
- [ ] Clicar em "jogando agora" lista quem está online
- [ ] Ficha do jogador: skin, habilidades, clãs e coleção
- [ ] Painel `/admin`: menu em grupos que abrem e fecham
- [ ] Painel "Dinheiro dos jogadores": "Editar" num jogador online, mudar ouro, prata e cobre da bolsa e salvar; as moedas mudam no inventário dele
- [ ] Mesmo painel: mudar o banco de um jogador online (vale na hora) e de um offline (vale quando ele entrar); com ele offline, os campos da bolsa ficam travados

## 11. Estabilidade — observar durante a noite

Hoje o painel e o servidor caíram sozinhos três vezes (09:19, 10:32 e 16:41), sempre um ou dois minutos depois de ligar; a vigia religou em 5 a 6 minutos. Nos outros oito reinícios do dia não caíram. A causa ainda é desconhecida. Desde as 17:04 roda a sentinela de quedas, que registra o que aconteceu e religa na hora.

- [ ] Se o site ou o servidor sumirem, anotar a hora e o que você estava fazendo no PC
- [ ] Depois de cada reinício, conferir em 2 minutos se o site continua abrindo
- [ ] Se cair: conferir se voltou sozinho em cerca de 2 minutos e se apareceu um arquivo `logs\quedas\queda-*.txt` (é ele que vai mostrar a causa)
