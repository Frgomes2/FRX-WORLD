# Mercado entre jogadores

[← Índice](../README.md) · Mod: `moedas/Mercado.java` · Dados: `<mundo>/frx_mercado.json`

O **Leiloeiro** (NPC de papel `leiloeiro`; o admin coloca com `/frx npc aqui leiloeiro` dentro de uma capital) guarda os anúncios de todos. Preço fixo, sem lances; o vendedor não precisa estar no jogo.

## Balcão

| Botão | O que faz |
|---|---|
| Comprar | Vitrine paginada (45 por página), por mais novos ou mais baratos. **Shift + clique** compra com as moedas da bolsa |
| Vender o item que está na sua mão | Mostra o item e deixa montar o preço com botões (+1 cobre, +1 prata, +1 ouro, +10 ouro, +100 ouro; Shift + clique diminui). Ao anunciar, o item sai da mão |
| Meus anúncios | Lista do jogador; Shift + clique cancela e devolve o item |
| Receber | Entrega as moedas das vendas e os itens de anúncios vencidos |

## Regras

- **Taxa:** 5% do preço (mínimo 1 cobre), descontada só quando o item é vendido. É um ralo de moedas.
- **Validade:** 7 dias. Vencido, o item vai para "Receber" do vendedor.
- **Limite de anúncios:** `5 + 2 × rank da Guilda` (F = 5, S = 17).
- **Preço:** de 1 cobre a 100.000 ouro.
- **Não se vende:** moedas e armas do Códex vinculadas ao dono (as que evoluem).
- O dinheiro da venda fica no balcão até o vendedor buscar; ao entrar no jogo ele é avisado do que tem a receber, e na hora da venda também, se estiver online.
- Cada compra e cada anúncio ficam no log do servidor (`Mercado: ...`).
- Itens são gravados com todos os dados (encantamentos, forja, runas, raridade). Se um mod for removido e o item deixar de existir, o anúncio é descartado ao ligar, com aviso no log.
