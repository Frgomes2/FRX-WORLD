# Economia, moedas, lojas e banco

[← Índice](../README.md) · Código: `moedas/` (Moedas, Banco, ItemMoeda, TrocasMoeda), `reinos/Lojas.java` · Painel: **Economia**

## Moedas

| Moeda | Valor |
|---|---|
| Cobre | 1 |
| Prata | 10 cobre |
| Ouro | 100 cobre |

- São itens físicos que caem na morte (o banco protege).
- Valores internos e comandos usam **cobre**. Por exemplo, `/frx moedas <j> 1000` = 10 ouro.
- **Todo comércio com aldeões usa prata no lugar da esmeralda** (1 esmeralda = 1 prata), por `TrocasMoeda`.
- **Fontes:** missões, taverna, campanhas, quests do FTB, Elites, chefes, venda de itens e o Ladino (Despertar "Roubar!").
- **Gastos:** lojas, forja (aprimorar e reforjar) e troca de raça.
- `/saldo` mostra o que está na bolsa.

## Banco (`moedas/Banco.java`)

- Moedas guardadas no banco **não caem na morte**. O saldo fica no NBT persistente do jogador.
- **Banqueiro** (NPC das capitais): clique = ver saldo · agachado + clique = depositar tudo.
- `/banco` mostra o saldo em qualquer lugar. `/banco sacar|depositar <valor>|tudo` só funciona perto de um banqueiro.
- Pagamentos como a troca de raça tiram da bolsa e, se faltar, do banco.

## Lojas (`reinos/Lojas.java`)

- Cada capital tem **Mercador** (vende ao jogador) e **Comprador** (compra do jogador). Os preços são em cobre.
- Tudo é editado no painel (menu **Economia**):
  - lista de itens com ícone, busca e filtro por mod;
  - preço, quantidade e ordem (▲▼);
  - "Salvar loja" troca a loja inteira numa transação (`salvarLoja`).
- **Aviso de arbitragem:** se um item custa no Mercador menos do que o Comprador paga, a linha fica vermelha.
- **Reputação dá desconto:** de 3% (Amigável) até 20% (Herói). Veja [reinos.md](reinos.md#reputação-reputacaojava).
- O mod recarrega as lojas a cada minuto ou com `/frx recarregar`.

## Painel

- O resumo mostra o dinheiro no mundo, banco × bolsa e o **ranking dos mais ricos** (com a cabeça da skin).
- Ações de admin na ficha do jogador: `banco`, `tirarmoedas`.
