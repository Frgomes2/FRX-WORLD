# Economia, moedas, lojas e banco

[← Índice](../README.md) · Código: `moedas/` (Moedas, Banco, ItemMoeda, TrocasMoeda), `reinos/Lojas.java` · Painel: **Economia**

## Moedas

**De onde vêm (desde 16/11/2026):** só de **missões** (quadro de missões e urgentes, com o bônus do rank da Guilda) e da **venda de itens** aos NPCs. A prova de promoção da Guilda continua pagando o prêmio único do rank.

Não dão mais moedas: baús (tabelas de loot do mod e baús com raridade), monstros, Elites e Campeões, chefe mundial, invasões, campanhas dos reis, portais, Torre, procurados, tesouros, história, temporada, soldo do rank e as recompensas do livro de missões (FTB Quests). Dois casos ficaram de propósito por serem a própria mecânica: o Despertar do Ladino (roubo) e o evento do Ladrão.

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
- **Admin:** o painel (Dinheiro dos jogadores) define o saldo com o jogador online ou offline (`/frx bancooffline <uuid> <cobre>`; offline, o valor espera em `<mundo>/frx_banco_pendente.txt` até o login).
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
- **Rank da Guilda filtra a loja:** ferreiro, armeiro e mercador só mostram armas e armaduras de classe até o rank do jogador (`MenusNpc.ofertasDe`). Para o Rank F, ferreiro e armeiro vendem pedra e couro (`Lojas.basicos`).
- O mod recarrega as lojas a cada minuto ou com `/frx recarregar`.

## Painel

- O resumo mostra o dinheiro no mundo, banco × bolsa e o **ranking dos mais ricos** (com a cabeça da skin).
- Ações de admin na ficha do jogador: `banco`, `tirarmoedas`.
