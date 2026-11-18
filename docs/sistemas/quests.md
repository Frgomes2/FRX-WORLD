# Quests: FTB Quests, marcos, taverna e campanhas

[← Índice](../README.md)

## Três fontes de missão, três papéis

| Fonte | Papel | Onde | Código / dados |
|---|---|---|---|
| **FTB Quests** | Campanha principal: progressão, exploração e desbloqueios. É a espinha dorsal | Livro de quests (tecla do FTB) | `server/config/ftbquests/quests/`, gerado por `tools/gerar_ftbquests.py` |
| **Reis** | Campanhas narrativas e políticas de cada reino (5 etapas) | Clique no rei · `/campanha` | `reinos/Campanhas.java` |
| **Taverna** | Contratos repetíveis (caçar, coletar) | Quadro de Missões na taverna | `taverna/Tavernas.java`, `missoes/Missoes.java` · painel **Missões** |

## FTB Quests (Fase 2, versão 0.40)

> Precisa dos mods da Fase 2 instalados. Veja [06-mods-externos.md](../06-mods-externos.md).

### Estrutura (11 capítulos, 78 quests)

| Capítulo | Conteúdo |
|---|---|
| Prólogo | Escolher classe e raça, primeiros níveis, primeira taverna |
| Os Oito Reinos | Visitar as capitais, ficar Honrado, campanhas dos reis |
| Floresta do Crepúsculo | Nv 10–25 |
| Nether | Nv 15–35 |
| Undergarden | Nv 20–35 |
| Everbright | Nv 25–40 |
| Aether | Nv 30–45 |
| O Fim | Nv 30–50 |
| Everdawn | Nv 35–50 |
| O Outro Lado | Nv 45–60 |
| Lendas | Raças lendárias, chefe mundial, Campeões, nível 100 |

### Como as quests se ligam ao mod

- **Tasks usadas:** `advancement` (marcos do FRX e conquistas vanilla), `dimension` (entrar na dimensão), `kill` (chefes dos mods) e `checkmark`.
- **Marcos:** o mod concede 48 advancements ocultos (`frxworld:frx/<marco>`) quando o jogador:
  - escolhe classe ou raça, usa o Despertar;
  - faz taverna ou dungeon, mata Elite ou Campeão;
  - sobe de nível, visita uma capital, fica Honrado ou conclui uma campanha.

  Lista completa em [progressao-zonas.md](progressao-zonas.md#marcos-conquistas-ocultas).
- **Recompensas:** comandos com `{p}` e `elevate_perms`, como `/frx xp {p} N` e `/frx moedas {p} <cobre>`, para pagar na economia do FRX.

### Editar a campanha

1. Edite `tools/gerar_ftbquests.py`. Os capítulos e as quests são listas no próprio script.
2. Rode `python tools\gerar_ftbquests.py`. Ele reescreve `data.snbt` (versão 13), `chapter_groups.snbt` e `chapters/*.snbt`.
   - Os IDs têm 16 dígitos hexadecimais e são estáveis (o primeiro dígito fica entre 1 e 7).
3. No jogo, `/ftbquests reload` (ou reinicie).

> Não edite no editor do FTB dentro do jogo: a próxima geração sobrescreve. Se preferir editar no jogo, pare de usar o script.

### Pendências

- Confirmar os IDs dos chefes nas tasks `kill`: `undergarden:forgotten_guardian`, `blue_skies:arachnarch`, `deeperdarker:stalker` e outros. Uma quest com "?" indica ID errado.

## Taverna e Quadro de Missões

- As tavernas são estruturas injetadas nas vilas e também construídas nas capitais. O **taverneiro** abre o **Quadro de Missões** (`TelaQuadro`).
- As missões vêm do painel (menu **Missões**): caçar uma criatura ou coletar itens, com faixa de nível e recompensas em XP, moedas e itens.
  - O mod recarrega a cada minuto ou com `/frx recarregar`.
- Admin: `/frx taverna ...`, `/frx quadro ...`.
- **Lote da Guilda e quadro do dia:** além das missões cadastradas à mão, há um lote de mais de mil (ids `g<rank>_...`, 100+ por rank, gerado por `tools/gerar-missoes.py`). Como são muitas, o quadro sorteia todo dia 12 do rank do jogador, 4 do rank de baixo e 2 do de cima (bloqueadas); a seleção é igual para todos e vira à meia-noite de Brasília. Missões à mão, as já aceitas e a urgente aparecem sempre. Os números ficam em `Missoes.DO_RANK`, `DO_RANK_ABAIXO` e `DO_RANK_ACIMA`.
- Marcos: `taverna_1`, `taverna_10`.

## Campanhas dos reis

Veja [reinos.md](reinos.md#campanhas-dos-reis).
