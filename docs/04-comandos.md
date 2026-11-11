# 04 — Comandos

[← Índice](README.md)

`<j>` = nome do jogador. `[...]` = opcional. Comandos `/frx` são de admin (permissão de operador).

## Jogador

| Comando | O que faz | Sistema |
|---|---|---|
| `/convite <código>` | Usa o convite no primeiro login | Acesso |
| `/nivel` | Mostra nível e XP | Progressão |
| `/conquistas` | Lista conquistas | Progressão |
| `/raca` | Abre/mostra a sua raça | [Raças](sistemas/racas.md) |
| `/raca revelar` | Revela uma raça lendária disfarçada | Raças |
| `/raca ver <j>` | Vê a raça visível de outro jogador | Raças |
| `/talentos aprender <talento>` · `/talentos resetar` | Árvore de talentos | [Classes](sistemas/classes-habilidades.md) |
| `/sombra erguer` · `invocar` · `todas` · `guardar` · `libertar` | Exército de sombras (Invocador) | Classes |
| `/levantese` | Atalho de "erguer" as sombras | Classes |
| `/saldo` | Mostra suas moedas | [Economia](sistemas/economia.md) |
| `/banco` · `/banco sacar <v>` · `/banco depositar <v>\|tudo` | Banco | Economia |
| `/reputacao` | Reputação com cada reino | [Reinos](sistemas/reinos.md) |
| `/titulo usar <título>` | Escolhe o título exibido | Reinos |
| `/rei` | Fala com o rei do reino atual | Reinos |
| `/campanha aceitar` | Aceita a campanha do rei | [Quests](sistemas/quests.md) |
| `/invasao` | Estado da invasão atual | Reinos |
| `/masmorras` | Lista as masmorras | [Dungeons](sistemas/dungeons-chefes.md) |
| `/chefe` | Informações do chefe mundial | Dungeons |
| `/grupo convidar <j>` · `aceitar` · `recusar` · `sair` · `expulsar <j>` · `chat` | Grupos | Grupos |
| `/g <mensagem>` | Chat do grupo | Grupos |

**Teclas:** `B` = Despertar · `Z` = atalho de sombra 4 (os atalhos 1–3 começam sem tecla) · teclas de habilidade configuráveis em *Controles → FRX-WORLD*.

## Admin (`/frx`)

### Progressão e jogador

| Comando | O que faz |
|---|---|
| `/frx xp <j> <n>` · `/frx nivel <j> <n>` | Dá XP / define nível |
| `/frx classe ...` | Define a classe |
| `/frx tomo ...` | Dá tomo de habilidade |
| `/frx talentos resetar <j>` | Reseta talentos |
| `/frx despertar` | Enche a barra de Despertar (teste) |
| `/frx raca <j> definir <principal> [secundaria]` · `liberar <raca>` · `limpar` | Raças |
| `/frx admin <j> ficha` · `curar` · `banco` · `tirarmoedas <v>` · `reputacao ...` · `talento ...` | Ficha e ajustes do jogador |
| `/frx admin <j> habilidade aprender\|esquecer\|nivel\|todas` | Habilidades do jogador |
| `/frx moedas <j> <cobre>` | Dá moedas (em cobre) |
| `/frx reputacao ...` | Ajusta reputação |
| `/frx recarregar` | Recarrega a config do painel |

### Itens e forja

| Comando | O que faz |
|---|---|
| `/frx raridade <N>` | Define a raridade do item na mão |
| `/frx forja <N>` | Define o refino +N do item na mão |

### Mundo, reinos e conteúdo

| Comando | O que faz |
|---|---|
| `/frx reino <id>` | Teleporta para o reino |
| `/frx waystones` · `/frx waystones refazer` | Lista / recoloca as 5 waystones de cada reino |
| `/frx npc recriar` · `/frx npc aqui` | NPCs dos reinos |
| `/frx populacao refazer` | Refaz a população dos reinos |
| `/frx decor refazer` | Refaz decorações |
| `/frx campanha pular` | Pula etapa da campanha |
| `/frx invasao parar` | Encerra invasão |
| `/frx taverna ...` · `/frx quadro ...` | Contratos da taverna / quadro de missões |
| `/frx dungeon ...` · `/frx masmorra ...` · `/frx cripta resetar` | Dungeons e masmorras |
| `/frx chefe agora` · `tempo` · `cancelar` | Chefe mundial |
| `/frx evento ...` | Evento de estrada |

> Para a sintaxe exata de cada subcomando, use o autocompletar (Tab) no jogo.
