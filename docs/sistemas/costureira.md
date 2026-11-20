# Costureira e bolsos na calça

[← Índice](../README.md)

A Costureira é um NPC de capital que costura bolsos na calça do jogador. Cada bolso é um espaço a mais de inventário. Entrou na versão 1.0.5.

## Para o jogador

- Leve a calça na mão, ou vá vestindo uma, e clique na Costureira. Serve qualquer calça (qualquer item que veste nas pernas, inclusive de outros mods).
- Cada bolso custa 2 de ouro. A calça aceita até 12 bolsos. A tela deixa costurar um por vez ou todos os que as moedas pagam.
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
| Tela da Costureira | `cliente/TelaCostureira.java` (menu `costureira`) |
| Botão do inventário e dica do item | `cliente/BolsosCliente.java` |
| Tecla | `Teclas.BOLSOS` (`key.frxworld.bolsos`) |

- A calça guarda só a quantidade de bolsos, na marca `frx_bolsos` do item.
- O conteúdo fica em `frx_bolso_itens`, nos dados persistentes do jogador. Por isso acompanha o personagem, não a calça.
- Preço (`PRECO`, em cobre) e limite (`MAXIMO`) são constantes de `Costureira.java`.
- O admin coloca a Costureira com `/frx npc aqui costureira`, dentro de uma capital.
