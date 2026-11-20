# FRX-WORLD — checklist de testes (noite de 18/11)

Atualizado às 08:55 de 18/11, com o servidor na versão 1.0.2. Aqui está só o que entrou depois do teste de 17/11 (versões 0.42.9 a 1.0.2) e ainda não foi testado dentro do jogo. A ordem é do que mais pode dar errado para o que menos pode. Marque o que funcionar e anote o que falhar (o que você fez, o que apareceu).

Vários itens pedem **duas contas** (você e um amigo) e, para os personagens, uma **conta de teste**.

## Por onde começar

Se o tempo for curto, esta é a ordem que mais vale:

1. **Preparação** (bloco 0): todos fecham e abrem o jogo para o pack atualizar para a 1.0.2.
2. **Personagens** (bloco 1), com uma conta de teste: é a parte mais arriscada de tudo.
3. **Terrenos** (bloco 2): ver se o terreno aparece pintado no mapa e ampliar.
4. **Mercador de Escravos** (bloco 3).
5. **Espaços e habilidades novas** (blocos 3b e 3c) e **painel e site** (bloco 4).
6. **Travadas** (bloco 5), observando a noite toda.

## 0. Preparação (admin, 5 minutos)

- [ ] Todos os jogadores fecharam e abriram o jogo, e o pack atualizou para a versão 1.0.2 do mod
- [ ] Dentro de uma capital, colocar o Mercador de Escravos: `/frx npc aqui escravos`
- [ ] Ter uma conta de teste (ou a de um amigo que tope) para criar o segundo personagem
- [ ] Ter moedas na bolsa nas contas que vão testar (pelo painel, em Dinheiro dos jogadores)
- [ ] Uma conta com Rank D ou mais na Guilda, para o Mercador de Escravos

## 1. Personagens (até 3 por conta) — risco alto

É a parte que mexe no arquivo de cada jogador. Teste primeiro com a conta de teste; a cópia de segurança de antes está em `backups\playerdata-20261118-antes-personagens`. Se algo sair errado numa troca, não tente de novo: avise o Claude.

- [ ] Entrar no servidor: uns 3 segundos depois abre a tela com três estandartes; o seu personagem aparece com o boneco e "em jogo"
- [ ] "Entrar" fecha a tela; a tecla F8 abre de novo
- [ ] "Nome": trocar o nome; ele passa a aparecer no chat, em cima da cabeça e no Tab
- [ ] "Criar personagem" numa vaga livre, dar um nome: aparece "Preparando…" e o jogo entra de novo sozinho
- [ ] O personagem novo começa a escolha de raça e classe, sem itens e no nível 1
- [ ] O saldo do banco do personagem novo é o mesmo do primeiro
- [ ] F8 → "Jogar" no primeiro personagem: volta com inventário, equipamento, nível, classe e posição exatamente como estavam
- [ ] Trocar de novo para o segundo: ele também volta como estava
- [ ] Depositar no banco com um personagem e conferir o saldo com o outro
- [ ] A bolsa de moedas é separada: cada personagem tem a sua
- [ ] Clãs: o personagem novo não está nos clãs do primeiro; o primeiro continua neles
- [ ] Os terrenos da conta valem para os dois personagens (os dois constroem)
- [ ] Torre: o recorde do primeiro personagem não aparece para o segundo
- [ ] Se a reentrada automática falhar (tela de desconectado): entrar de novo pela lista de servidores e conferir que o personagem certo carregou
- [ ] Criar o terceiro personagem e conferir que os três aparecem na tela, cada um com classe, raça e nível
- [ ] Tentar criar com nome de 2 letras: recusa e explica

## 2. Terrenos — risco alto

O desenho no mapa falhou duas vezes ontem e foi refeito de outro jeito; só dá para saber olhando.

- [ ] Depois de entrar, esperar uns 5 segundos e abrir o mapa (tecla J): o seu terreno aparece pintado de verde
- [ ] Na outra conta, o mesmo terreno aparece em vermelho (ou laranja, se ela estiver liberada)
- [ ] Marcar o 1º canto pelo mapa: aparece um quadradinho amarelo; marcar o 2º: a área escolhida fica amarela
- [ ] Ampliar: escolher uma área que passa por cima do seu terreno: o painel mostra "Área nova: N blocos (M já são seus)", cobra só os novos e deixa comprar
- [ ] Depois de comprar, as duas áreas aparecem pintadas no mapa
- [ ] Escolher uma área toda dentro do seu terreno: recusa com "Essa área inteira já é sua"
- [ ] Limites novos: no Rank F o painel mostra lado de até 24 e área de 512; no Rank E, 34 e 1.200
- [ ] Vender um terreno que está por baixo de outro seu: devolve pouco ou nada pela parte que continua sua
- [ ] Sair do servidor e entrar de novo: os terrenos continuam pintados; trocar de servidor ou sair para o menu não deixa desenho sobrando

## 3. Mercador de Escravos — risco médio

- [ ] Clicar no mercador abre o painel com sete aldeões desenhados, um por roupa
- [ ] Conta abaixo do Rank D: "Exige Rank D na Guilda" em vermelho e o botão Comprar apagado
- [ ] Conta Rank D: o painel mostra "Seus aldeões: 0 de 4"
- [ ] Escolher um aldeão e comprar: saem 5 de ouro e chega o "Contrato de Escravo"; a contagem vira 1 de 4
- [ ] Usar o contrato no chão: aparece um aldeão sem profissão, com a roupa escolhida, e o contrato some
- [ ] Com uma bancada de trabalho por perto, o aldeão pega a profissão
- [ ] Comprar até o limite: a compra seguinte é recusada, explicando que é preciso subir de rank
- [ ] Matar um aldeão comprado: a contagem cai e dá para comprar de novo
- [ ] Usar o contrato num gerador de monstros: recusa com "O contrato só serve no chão"
- [ ] O limite soma os personagens da conta: comprar com um e ver a mesma contagem com o outro

## 3b. Espaços de habilidade por rank — risco médio

Entra só depois do reinício da versão 1.0.3.

- [ ] Rank F: a barra continua com 4 espaços e a ultimate
- [ ] Rank E: aparece um 5º espaço numa linha acima da barra; na janela de habilidades (K) dá para pôr uma habilidade nele
- [ ] A tecla do espaço novo (Z por padrão) usa a habilidade; dá para trocar a tecla na janela
- [ ] Ranks D, C e B: 6º, 7º e 8º espaços (teclas H, N e Y)
- [ ] Subir de rank na prova da Guilda: o espaço novo aparece na hora, sem relogar
- [ ] Druida: os espaços extras funcionam na forma humana e nas formas de animal
- [ ] Na tela pequena, a linha de cima não cobre a barra de recurso nem o Despertar

## 3c. Habilidades novas de Ladino, Clérigo e Invocador — risco médio

Entram com o reinício da 1.0.3. Para trocar a classe de uma conta: `/frx classe <jogador> ladino|clerigo|invocador`.

- [ ] Na janela de habilidades, cada uma das três classes mostra 12 habilidades
- [ ] Ladino: Gancho do Gatuno puxa até o ponto mirado; Cortina de Fumaça cega os monstros em volta e esconde você
- [ ] Ladino: Marcar a Vítima faz o alvo brilhar; Moeda Ricochete salta entre inimigos; Troca de Lugar troca com o alvo
- [ ] Clérigo: Bênção da Vida dá regeneração aos aliados perto; Correntes de Luz quase paralisa o alvo
- [ ] Clérigo: Solo Sagrado queima quem fica na área; Manto da Fé faz o dano sair da mana; Lança de Luz atravessa os inimigos em linha
- [ ] Invocador: Correntes Espectrais arrasta o alvo até você; Dreno de Alma devolve mana; Espíritos Caçadores perseguem o alvo
- [ ] Invocador: Alcateia Sombria (nível 15) invoca 4 lobos que lutam por você
- [ ] As habilidades de Tomo (Armadilha do Gatuno, Dardos Celestiais, Círculo de Almas) aparecem como "por item" e não são aprendidas só pelo nível

## 3d. Anéis sem repetição — risco médio

Entra com o reinício da 1.0.4.

- [ ] Com um anel equipado, tentar equipar outro igual: o jogo recusa e avisa que não dá para usar dois anéis iguais
- [ ] Dois anéis diferentes continuam podendo ser usados juntos, até os 10 espaços
- [ ] Trocar um anel por outro igual no mesmo espaço funciona
- [ ] Quem estava com anéis repetidos: ao entrar, os repetidos voltam para a mochila e aparece o aviso
- [ ] Outros acessórios (amuletos, cintos) não mudaram

## 3e. Costureira e bolsos na calça — risco médio

Entra com o reinício da 1.0.5. O admin coloca a Costureira numa capital antes (`/frx npc aqui costureira`).

- [ ] Clicar na Costureira sem calça na mão e sem calça vestida: a tela pede para trazer uma calça
- [ ] Com uma calça na mão: a tela mostra a calça, "Bolsos: 0 de 12" e o preço de 2 de ouro por bolso
- [ ] Costurar 1 bolso: as moedas saem da bolsa e a calça passa a mostrar "Bolsos: 1 de 12" na dica do item
- [ ] Costurar vários de uma vez: cobra o total certo e para em 12
- [ ] Sem moedas suficientes: o botão fica apagado e nada é cobrado
- [ ] Vestindo a calça: o inventário ganha o botão "Bolsos" e a tecla O abre a janela dos bolsos
- [ ] Na janela, só os bolsos costurados aceitam itens; as placas de vidro não saem do lugar
- [ ] Guardar itens, fechar, abrir de novo: os itens continuam lá, inclusive depois de sair e entrar no jogo
- [ ] Tirar a calça com itens nos bolsos: os itens voltam para a mochila (com a mochila cheia, caem no chão)
- [ ] Caixa de shulker não entra no bolso
- [ ] Morrer com itens nos bolsos: eles caem junto com o resto da mochila, sem duplicar
- [ ] Trocar de personagem: cada personagem tem os próprios itens nos bolsos

## 3f. Site por personagem — risco médio

Entra com o reinício da 1.0.6.

- [ ] No site, quem tem dois personagens aparece duas vezes em Aventureiros, cada um com o nome, a classe e o nível dele
- [ ] No ranking "Mais fortes", os dois personagens da mesma conta aparecem em linhas separadas
- [ ] Clicar no personagem que não está em uso abre a ficha dele (com o aviso de fora de uso), não a do que está jogando
- [ ] Só o personagem em uso aparece como online
- [ ] Conta com um personagem só continua aparecendo uma vez
- [ ] No painel, em Jogadores: quem tem dois personagens aparece em duas linhas, uma "em uso" e outra "fora de uso", e cada nome abre a ficha certa
- [ ] No painel, em Dinheiro: a conta mostra o personagem em uso e, embaixo, a bolsa do outro personagem
- [ ] No painel, na Visão geral: os rankings mostram os personagens pelo nome

## 3g. Guia do Aventureiro, Mural e dragões — risco médio

Entra com o reinício da 1.0.7.

- [ ] O servidor liga normalmente com o mod Dragon Mounts e todos conseguem entrar depois de o pack atualizar
- [ ] No livro de missões aparece o capítulo "Guia do Aventureiro" com 6 missões
- [ ] Quem já é registrado na Guilda vê a primeira missão concluída em poucos segundos e consegue pegar a recompensa
- [ ] Costurar um bolso e vestir a calça conclui a missão "Bolsos na calça"
- [ ] No site, o "Mural dos Primeiros" aparece com os marcos de Guilda, Nível e Torre
- [ ] O Rank D aparece com o nome de quem é o único Rank D hoje; marcos que vários já tinham aparecem como "alcançado antes de o mural existir"
- [ ] Apertar F9 no jogo: abre a tela "Conta do site" com um código de 6 números
- [ ] No site, Minha conta → Primeiro acesso: com o nick, o código e uma senha de 8 caracteres ou mais, entra na conta
- [ ] A página mostra banco, bolsa, terrenos, os personagens e a ficha com o inventário (com os ícones dos itens), sem nenhum botão de admin
- [ ] Sair e entrar de novo só com nick e senha funciona; senha errada é recusada
- [ ] Código errado ou vencido é recusado; o mesmo código não funciona duas vezes
- [ ] Sem estar logado, /conta só mostra a tela de entrar, e ninguém vê a conta de outro jogador
- [ ] Achar um ovo de dragão num baú, chocar e montar no dragão (pode levar dias)

## 3h. Criador de Dragões e cabeçalho do site — risco médio

Entra com o reinício da 1.0.8. O admin coloca o NPC numa capital antes (`/frx npc aqui dragoes`).

- [ ] Clicar no Criador de Dragões abre a tela do ninho, com os 8 ovos desenhados e as estrelas de raridade
- [ ] Escolher um ovo mostra o nome, a raridade, onde ele choca e o preço; sem moedas o botão fica apagado
- [ ] Com moedas suficientes (o admin pode dar pelo painel), comprar tira o valor certo da bolsa e entrega o ovo do tipo escolhido
- [ ] Colocar o ovo no chão e clicar nele começa a chocar; o filhote nasce do tipo esperado quando o ninho é o certo
- [ ] Domar o filhote, esperar crescer, pôr sela e voar
- [ ] No site, o cabeçalho novo não tem barra de rolagem; a lista "Mais" abre e os atalhos levam à seção certa
- [ ] No celular, o botão ☰ abre o painel com todos os atalhos

## 3i. Site mais vivo — risco baixo

Entra com o reinício da 1.0.9.

- [ ] O topo do site muda de cara de dia e de noite no jogo, e mostra o dia, a hora e quantos estão jogando
- [ ] Com chuva ou tempestade no jogo, aparecem os riscos de chuva no topo
- [ ] Com o servidor desligado, o topo fica cinza e avisa que o mundo dorme
- [ ] A temporada mostra a contagem regressiva andando, as medalhas e a lista "Como ganhar pontos"
- [ ] O bestiário mostra a barra da coleção com a porcentagem certa
- [ ] Os rankings mostram medalhas e as barras; os números do topo sobem ao abrir a página

## 3j. Crônicas do mundo — risco médio

Entra com o reinício da 1.1.0. Só registra o que acontecer depois desse reinício.

- [ ] Subir de nível no jogo: em até um minuto aparece "Fulano subiu para o nível N" no quadro Acontecendo do site
- [ ] Trocar de personagem não cria crônica falsa de nível ou rank
- [ ] Subir de rank na Guilda vira manchete na seção "Crônicas do FRX"
- [ ] Passar de um andar múltiplo de 5 na Torre aparece no Acontecendo
- [ ] A ficha pública do personagem mostra o bloco Histórico com esses acontecimentos
- [ ] No ranking "Mais fortes", quem subiu de nível hoje mostra "+N níveis na semana"
- [ ] Com gente jogando, o servidor não ficou mais lento depois desta atualização

## 3k. Samurai e mods novos — risco alto

Entra com o reinício da 1.1.2. Use um segundo personagem (F8) para criar o Samurai.

- [ ] O servidor sobe com os quatro mods novos e todos conseguem entrar depois de o pack atualizar
- [ ] Personagem novo nasce com couro completo vestido, espada, machado, pá e picareta de pedra e 32 pães, e consegue usar tudo
- [ ] Entrar de novo com o mesmo personagem não entrega o kit outra vez
- [ ] Ao escolher Samurai, chega a Katana de Aprendiz, e o Saque funciona com ela
- [ ] Na criação do personagem, Samurai aparece na lista de classes com a descrição
- [ ] Existem katanas do mod Samurai Dynasty (ver no JEI) e dá para conseguir uma
- [ ] Com a katana na mão, depois de 3 s sem bater aparece "Katana na bainha" e o golpe seguinte mostra "Saque!" com dano maior
- [ ] Sem katana (com espada comum) não aparece Saque
- [ ] As habilidades do Samurai aparecem na tela de habilidades (K) e funcionam: Corte da Maré, Passo Relâmpago e as demais conforme o nível
- [ ] O Despertar (tecla B, nível 10) abre "Lâmina das Três Formas" e os golpes alternam fogo, lentidão e faísca
- [ ] As katanas e armaduras do mod respeitam a trava de rank da Guilda
- [ ] À noite e em cavernas tocam os sons tensos
- [ ] A criatura da neblina não aparece dentro das capitais nem mata jogador de nível baixo sem chance (anotar se acontecer)
- [ ] Com gente jogando à noite, o servidor não ficou mais lento

## 3l. Habilidades únicas novas e ajustes de 18/11 à noite — risco médio

Entra com o reinício da 1.1.4.

- [ ] Personagem novo recebe uma habilidade única que ninguém (ou quase ninguém) tem ainda
- [ ] `/unica` mostra o nome, o efeito em números e a evolução das habilidades novas
- [ ] Quem já tinha habilidade única continua com a mesma, no mesmo nível
- [ ] Os espaços extras de habilidade aparecem na mesma linha dos outros, antes da ultimate
- [ ] O mapa do site mostra a capital de cada reino com o nome
- [ ] Com o jogo em português, os avisos das luas, os dragões, o Homem da Neblina e os itens do Samurai Dynasty aparecem traduzidos

## 4. Painel e site — risco médio

- [ ] Painel → ficha de um jogador: aparece o bloco "Personagens da conta" com nome, classe, raça, nível e rank de cada um
- [ ] Clicar num personagem fora de uso (que já jogou hoje): abre a ficha guardada dele, com o aviso de que é um retrato
- [ ] Nessa ficha guardada, tentar uma ação de admin: o painel avisa que só vale para o personagem em uso
- [ ] Site → ficha pública: os personagens aparecem como botões; clicar em um troca a ficha para a dele
- [ ] Site → Clãs: cada membro aparece com o nome do personagem
- [ ] Site → ranking da Torre e temporada: aparece o nome do personagem, não o nick da conta
- [ ] Site → Biblioteca: livro novo "Terras, Clãs e Comércio"; no Guia, a página "Seus personagens"
- [ ] Site → Novidades: as notas v1.0.0, v1.0.1 e v1.0.2 aparecem com data e hora

## 5. Travadas e estabilidade — observar durante a noite

Ontem houve uma travadinha a cada 5 minutos (salvamento do mundo, que agora roda de 30 em 30) e rajadas durante uma invasão e com gente explorando área nova, ainda sem causa confirmada.

- [ ] Reparar se a travadinha de 5 em 5 minutos sumiu
- [ ] Se travar em combate, avisar o Claude na hora ("está travando agora") para ele ligar o medidor por 2 minutos
- [ ] Anotar o que estava acontecendo quando travou (invasão, alguém explorando longe, masmorra)
- [ ] Se o site ou o servidor sumirem, anotar a hora; conferir se voltaram sozinhos em cerca de 2 minutos
