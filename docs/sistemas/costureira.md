# Costureira e bolsos na calça

[← Índice](../README.md)

A Costureira é um NPC de capital com uma **mesa de costura** (desde a 1.3.3): espaços de inventário de verdade e cinco abas. Bolsos entraram na 1.0.5.

## A mesa de costura

Clicar na Costureira abre a mesa (`reinos/MenuCostura`, menu `frxworld:costura`, tela `cliente/TelaCostura`): ateliê de linho com costura tracejada na borda, abas de fita no topo, o espaço da **peça** e o da **2ª calça** à esquerda e o inventário embaixo. O dinheiro conta bolsa e banco (o que faltar na bolsa sai do banco). Ao fechar, o que ficou nos espaços volta ao jogador.

| Aba | O que faz | Preço |
|---|---|---|
| **Bolsos** | Costura bolsos na calça da peça (+1 ou completar até 12) | 2 ouro por bolso |
| **Passar** | Passa os bolsos da calça velha (peça) para a nova (2ª calça), até 12 | 50 cobre por bolso |
| **Remendos** | Conserta por completo armaduras e élitros (armas ficam com o ferreiro) | cobre por ponto de dano × fator do material: élitros 3; armadura 0,4 + 0,2 × defesa do peitoral do material + 0,3 × resistência (couro ≈ 1, ferro ≈ 1,6, diamante ≈ 2,8, netherite ≈ 3,5); mínimo 10 |
| **Bordados** | Acabamento de armadura (padrão e material à escolha, com prévia) em peças que aceitam acabamento; nome colorido (13 cores, até 30 letras) em qualquer peça | acabamento 15 ouro, tirar 1 ouro, nome 5 ouro |
| **Disfarce** | Manto de 30 min (acumula até 1 h): quem é Hostil a um reino entra, compra e sai sem os guardas reconhecerem (guardas não atacam, lojistas atendem, não aparece nos avisos de procurado) | 30 ouro |

- Relíquias do Códex não recebem acabamento nem nome bordado.
- O disfarce não vale para quem é **Inimigo** do reino da Costureira (nem ela vende) nem em reinos onde o jogador é Inimigo. Atacar qualquer coisa dentro de uma capital rasga o manto; ao vencer, aparece um aviso. Fica em `frx_disfarce_ate` (dados persistentes do jogador) e é conferido em `Hostilidade` (recusa das lojas, guardas e avisos de procurado).
- O nome bordado vai pela rede como `AcaoHabilidade.BORDAR_NOME` (cor no número, nome no texto); o resto como `AcaoMenu` "costureira" (`COSTURAR`, `PASSAR`, `REMENDAR`, `ACABAMENTO` com valor = padrão × 1000 + material ou −1 para tirar, `DISFARCE`).

## Para o jogador

- Na mesa, ponha a calça no espaço da peça (aba Bolsos). Serve qualquer calça (qualquer item que veste nas pernas, inclusive de outros mods). Cada bolso custa 2 de ouro; até 12 por calça. Uma calça vestida colocada na mesa deixa de estar vestida: o que estava nos bolsos volta para a mochila, como ao tirar a calça.
- Vestindo a calça, os bolsos aparecem **dentro do inventário**, num painel à esquerda da mochila (2 colunas, um espaço por bolso costurado). Clique esquerdo pega, põe ou troca; direito pega metade ou põe um; com Shift o item vai para a mochila. O painel não existe no modo criativo.
- O painel é desenhado pelo cliente (`cliente/BolsosCliente`), mas quem mexe nos itens é o servidor (`Costureira.clique`, ação de rede `bolso`), usando o item preso ao mouse do inventário; o conteúdo volta pelo pacote `bolsosdados`.
- A tecla **O** (Opções → Controles → FRX-WORLD) continua abrindo a janela antiga, para usar os bolsos sem abrir o inventário.
- A janela tem 2 linhas de 6 bolsos. Os lugares ainda sem bolso aparecem tampados.
- A dica da calça mostra "Bolsos: n de 12".

## Regras

| Situação | O que acontece |
|---|---|
| Tirar a calça, ou vestir uma com menos bolsos | O que não cabe mais volta para a mochila; o que sobrar cai no chão |
| Calça quebra | O mesmo de tirar a calça |
| Morrer | O que está nos bolsos cai com o resto da mochila (não cai com `keepInventory`) |
| Trocar de personagem | Cada personagem tem o próprio conteúdo de bolsos |
| Caixa de shulker e outros itens que guardam itens | Não entram no bolso |
| Vender ou dar a calça | Os bolsos costurados vão com ela; o conteúdo fica com quem guardou |

## Como é feito

| Parte | Onde |
|---|---|
| NPC, preço, limite, janela dos bolsos, morte | `core-mod/.../reinos/Costureira.java` |
| Mesa de costura | `reinos/MenuCostura.java` (menu `frxworld:costura`) e `cliente/TelaCostura.java`, ligadas em `cliente/RegistroTelas.java` |
| Botão do inventário e dica do item | `cliente/BolsosCliente.java` |
| Tecla | `Teclas.BOLSOS` (`key.frxworld.bolsos`) |

- A calça guarda só a quantidade de bolsos, na marca `frx_bolsos` do item.
- O conteúdo fica em `frx_bolso_itens`, nos dados persistentes do jogador. Por isso acompanha o personagem, não a calça.
- Preço (`PRECO`, em cobre) e limite (`MAXIMO`) são constantes de `Costureira.java`.
- O admin coloca a Costureira com `/frx npc aqui costureira`, dentro de uma capital.
