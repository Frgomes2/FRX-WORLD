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
- **Rendimento do cofre:** o saldo rende uma vez por dia, na virada do dia (horário de Brasília). A taxa começa na inicial (padrão 0,5% ao dia) e sobe o aumento (0,1%) a cada período (7 dias) sem sacar, até a máxima (1%). Sacar volta à taxa inicial; depositar não atrapalha (o primeiro depósito num cofre vazio começa a contagem). Só rende sobre o saldo até o teto (50.000 cobre = 500 ouro). Offline, o jogador recebe ao voltar, contando no máximo os últimos `diasOffline` (7). A fração de cobre que sobra fica guardada e soma no dia seguinte. O andamento é da conta, em `<mundo>/frx_banco_rendimento.json` (`pago`, `desde`, `resto`); a conferência roda a cada 20 segundos para quem está online há mais de 10 segundos. A tela do banqueiro mostra a taxa no cartão do cofre e uma linha com quanto rende amanhã e quando a taxa sobe; a ficha do jogador no painel mostra a mesma linha. Configuração no painel: **Economia → Rendimento do cofre** (seção `banco` da configuração do jogo, com simulação).
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

## Sábio: extrair encantamento e tirar runas (`reinos/Desencanto.java`, `reinos/MenuDesencanto.java`, `cliente/TelaDesencanto.java`)

- Na conversa com o sábio das capitais, a opção **"Extrair um encantamento"** abre a bancada arcana, no jeito da bigorna: um espaço para o item encantado (com um círculo de runas em volta), um para o livro em branco e o inventário embaixo. À direita, cada encantamento do item numa ficha com a chance.
- O jogador escolhe **um** encantamento e aperta "Extrair": ele sai do item e o livro encantado aparece no espaço do livro (ou vai para a mochila, se ainda havia mais livros em branco ali). De um livro com vários encantamentos também dá para separar um. Ao fechar, o que ficou nos espaços volta para o jogador.
- É a primeira tela com espaços de inventário própria do mod: menu `frxworld:desencanto` (`MenuDesencanto`, aberto com `NetworkHooks.openScreen`, levando os preços para a tela calcular igual ao servidor), ligado à tela em `cliente/RegistroTelas.java`.
- **Custo (caro de propósito):** moedas = `moedasPorNivel` (2.000 cobre) × peso da raridade (comum 1, incomum 1,5, raro 2,5, muito raro 4; ×1,5 se for de tesouro) × nível; níveis de experiência = `xpBase` (5) + `xpPorNivel` (3) × nível × peso, até `xpMax` (30); e um livro em branco. As moedas saem da bolsa e, se faltar, do banco. Exemplos: Afiação V = 100 ouro e 20 níveis; Fortuna III = 150 ouro e 28 níveis; Remendo = 75 ouro e 16 níveis.
- **Chance:** até o nível III não falha; nível IV 85%; V ou mais 70%. Se falhar, perde moedas, níveis e o livro; o item fica igual.
- **Não sai:** relíquias do Códex (o encantamento fica preso à relíquia) e maldições.
- Configuração no painel: **Economia → Sábio — extrair encantamento e tirar runas** (seção `desencanto`), com uma tabela de quanto custa cada exemplo.
- **Aba Runas:** a bancada tem duas abas no topo, Encantamentos e Runas. Na de Runas, o item da bancada mostra as runas encaixadas; a escolhida sai inteira para a mochila, por `runa1`/`runa2`/`runa3` (10, 25 e 50 ouro). O espaço do livro não é usado.
