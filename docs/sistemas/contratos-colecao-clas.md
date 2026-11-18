# Contratos da Guilda, Coleção e Clãs

[← Índice](../README.md) · Mod: `missoes/Contratos.java`, `progressao/Colecao.java` · Painel: `src/lib/clas.ts`, `src/lib/publico.ts`, site público (seção Clãs e ficha do jogador)

## Contratos da Guilda

Para quem está registrado na Guilda: **3 contratos por dia** e **1 por semana**, sorteados para cada jogador (semente = jogador + data) e medidos pelo rank.

| Tipo | Dia (alvo no Rank F, + por rank) | Semana |
|---|---|---|
| Derrotar monstros | 25, +10 | 200, +50 |
| Zumbis · esqueletos · aranhas · creepers | 10 +3 · 10 +3 · 8 +2 · 4 +2 | — |
| Minerar minérios (`forge:ores`) | 20, +5 | 150, +30 |
| Pescar | 5, +1 | — |
| Andares da Torre | 2, +1 | 10, +3 |
| Fechar portais | — | 2 |
| Derrotar chefões | — | 2 |

- O progresso conta sozinho (contadores das Conquistas, quebra de minério, pesca, andar vencido na Torre, morte de chefão a até 40 blocos) e aparece na barra de ação.
- A recompensa cai na hora, sem entregar nada: contrato do dia `30 + 20×rank` de cobre e 10% do XP do nível; os três do dia dão um extra de `60 + 40×rank` e 20%; o da semana `300 + 200×rank` e 60%.
- O dia vira à meia-noite e a semana na segunda-feira (fuso de São Paulo). Ao entrar no jogo no dia novo, o jogador recebe o aviso com os três contratos.
- **Quadro:** botão "Contratos" no Cartão de Aventureiro e no balcão da Guilda (`TelaGuilda` → ação 3 → `Contratos.abrir`). É uma tela própria só de leitura (`cliente/TelaContratos`): cartões do dia com barra e recompensa, extra, contrato da semana e tempo até renovar; a última linha mostra o resumo da Coleção.
- Dados em `frx_contratos` (NBT persistente do jogador).

## Coleção

- Cada **tipo de criatura hostil** derrotado pela primeira vez entra na coleção do jogador; cada **chefão diferente** (os de `Chefes`, inclusive os do Códex de Criaturas) entra para todos a até 40 blocos.
- Marcos de criaturas (10, 25, 50, 100, 150, 200) e de chefões (3, 7, 14) pagam moedas e 25% do XP do nível.
- Conquistas novas pelos contadores `colecao` e `colecao_chefes`: Curioso, Naturalista, Caçador de Raridades, Enciclopédia Viva, Mestre do Bestiário, Caça-Chefes, Terror dos Chefes, Senhor dos Chefes (cinco delas dão título).
- A ficha enviada ao painel leva `colecao` e `contratos`; a ficha pública do site mostra "N de M tipos" e os chefões derrotados.
- Dados em `frx_colecao` (NBT persistente do jogador). Só contam abates feitos depois desta versão.

## Clãs

- Os clãs são do próprio mod (`clas/Clas.java`) e ficam em `<mundo>/frx_clas.json`. O FTB Teams só aceita um time por jogador, por isso deixou de ser a base; os times que existiam nele são importados uma única vez (`clas/FtbTeams.java`, por reflexão), com membros e cofre.
- Um jogador participa de **até 3 clãs** ao mesmo tempo; cada clã tem até 30 membros.
- **Mestre dos Clãs** (NPC de papel `clas`; o admin coloca com `/frx npc aqui clas` dentro de uma capital). Tudo em janelas:
  - **Balcão:** os clãs do jogador, a lista de todos (do mais forte ao mais fraco, com líder, membros, poder, riqueza e cofre; clicar pede entrada ou cancela o pedido) e **fundar** o "Clã de Fulano".
  - **Página do clã:** ficha, **cofre**, **membros**, **pedidos** (líder e oficiais aceitam ou recusam; quem é aceito entra na hora, mesmo fora do jogo) e **sair** (Shift + clique).
  - **Líder:** muda o **nome** segurando uma etiqueta renomeada na bigorna (3 a 24 letras; a etiqueta é gasta), troca a **cor**, torna membros **oficiais**, **expulsa** (Shift + clique) e **passa a liderança** clicando no membro com um bloco de ouro na mão.
  - O líder só sai depois de passar a liderança, a não ser que esteja sozinho. O último a sair leva o que restou no cofre, e o clã deixa de existir.
- **Cofre do clã:** moedas separadas do banco de cada um. Qualquer membro deposita (1 prata, 1 ouro, 10 ouro, 100 ouro ou tudo). O saque é de qualquer membro por padrão; o líder pode restringir a líder e oficiais. Os 8 últimos movimentos ficam registrados e todo saque avisa os membros online.
- Nível e dinheiro de quem está fora do jogo vêm do resumo guardado no mesmo arquivo, atualizado a cada minuto.
- O painel lê `frx_clas.json` (`lib/clas.ts`).
- Site público, seção **Clãs**: ranking por poder (soma dos níveis) ou riqueza (cofre + bolsa e banco de todos), com líder, nível médio, conquistas e membros clicáveis. A ficha do jogador mostra todos os clãs dele e o cargo em cada um.
- O grupo do inventário (`grupos/Grupos.java`) continua sendo a equipe temporária da sessão (XP dividido, Torre, portal); o clã é o vínculo fixo. O FTB Teams continua instalado só para o livro de missões.
