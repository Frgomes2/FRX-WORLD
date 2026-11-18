# Terrenos (casa própria)

[← Índice](../README.md) · Mod: `protecao/Terrenos.java`, `protecao/MapaJm.java`, `cliente/MapaTerrenos.java` · Dados: `<mundo>/frx_terrenos.json`

O jogador escolhe uma área retangular do mundo normal (dois cantos), paga pelo tamanho e passa a ser o único a construir ali.

## Comprar pelo mapa (JourneyMap)

1. Abrir o mapa em tela cheia (tecla J) e clicar com o **botão direito** no primeiro canto → **Terreno: marcar o 1º canto aqui**.
2. Botão direito no canto oposto → **Terreno: marcar o 2º canto aqui e ver o preço**.
3. Abre a tela **Terrenos** na aba **Comprar**: desenho da área vista de cima com os terrenos vizinhos, tamanho, preço, limites do rank em barras e o botão de comprar (ou o motivo de não poder).

A escolha aparece em amarelo no mapa e em partículas no chão. Depois da compra, o terreno fica pintado no mapa de **todos** os jogadores com o nome do dono: verde (seu), laranja (liberado para você), vermelho (de outro).

- O menu do mapa usa a API do JourneyMap no cliente (`FULLSCREEN_POPUP_MENU_EVENT`) e só manda a posição ao servidor (`AcaoMenu "terreno"`).
- A pintura usa a API de sobreposições do servidor do JourneyMap (`ServerOverlayAPI`). As duas são chamadas por reflexão: sem o mod, o resto continua funcionando.
- Reserva sem mapa: o botão direito → **Terreno: meus terrenos e permissões** ou o comando `/terreno` abrem a tela **Terrenos**; a aba **Comprar** também marca os cantos na posição em que o jogador está.

## Preço e limites

- Preço: **1,2 cobre por bloco** de área (16×16 = 3 ouro e 8 cobre), pago com as moedas da bolsa. Vender devolve metade do que foi pago.
- Lado mínimo: 5 blocos. O terreno vai do fundo ao céu.

| Rank da Guilda | Lado máximo | Área somada | Terrenos |
|---|---|---|---|
| F | 24 | 512 | 1 |
| E | 34 | 1200 | 2 |
| D | 46 | 2200 | 3 |
| C | 58 | 3600 | 4 |
| B | 68 | 5600 | 5 |
| A | 92 | 9000 | 6 |
| S | 114 | 14000 | 7 |

Terrenos não podem se sobrepor nem pegar terras do reino.

## Ampliar um terreno

A área escolhida pode passar por cima de terrenos do próprio jogador (nunca dos de outro). Nesse caso só os blocos novos são cobrados e só eles entram no limite de área do rank; o terreno novo conta como mais um na quantidade. Na venda, o que outro terreno do jogador ainda cobre não é devolvido.

## Tela Terrenos

Tela de cliente (`cliente/TelaTerrenos`), com três abas:

- **Comprar:** a área escolhida, o preço e os limites; botões "1º canto aqui", "2º canto aqui", "Limpar" e "Comprar".
- **Meus terrenos:** lista dos terrenos; clicar seleciona. "Vender" pede um segundo clique e devolve metade do valor pago. "Mostrar as divisas" desenha as divisas no chão por 1 minuto. "Abrir para todos" / "Fechar os terrenos".
- **Quem pode mexer:** jogadores online e os já liberados, um por linha, com três botões: **Colocar** (blocos e baldes), **Quebrar** (blocos e bichos/molduras) e **Baús e portas** (abrir e usar qualquer bloco, e mexer em bichos). Desligar os três tira o jogador da lista. Vale para todos os terrenos do dono.

## Proteção

Dentro de terreno comprado, quem não é dono nem foi liberado não quebra, não coloca, não usa blocos (portas, baús, alavancas), não usa balde, não pisoteia plantação e não bate nem mexe em animais, molduras, suportes e barcos. Explosões não levam blocos e monstros não pegam blocos nem quebram portas. Ao entrar e sair aparece "Terreno de Fulano" / "Terras livres". Admin em modo criativo passa por tudo.

## Terras do reino (fora de venda)

- Automático: a área das capitais (com 8 blocos de margem), a Torre (24 blocos), o ponto de partida do mundo (40 blocos) e as salas distantes do jogo (X/Z ≥ 20000).
- Admin: `/frx terreno proibir [raio]` marca o pedaço de 16×16 em que está (raio em pedaços, até 12: raio 2 = 80×80 blocos) e mostra a área no mapa do admin; `/frx terreno liberar [raio]` desfaz.
- `/frx terreno info` mostra a situação do lugar; `/frx terreno tomar` tira o dono do terreno em que o admin está (sem reembolso).

## Limites conhecidos

- Pistões, água/lava escorrendo e fogo vindos de fora não são barrados.
- Comprar uma área onde outro jogador já construiu sem comprar passa a casa para o comprador.
- As terras do reino não aparecem pintadas no mapa dos jogadores; a recusa vem na hora de comprar.
