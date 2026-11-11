# Dungeons, masmorras e chefes

[← Índice](../README.md) · Código: `dungeon/`, `reinos/Masmorras.java`, `eventos/` · Painel: **Dungeons**, **Masmorras**, **Chefes de mods**, **Eventos de estrada**

## Dungeons (criptas FRX)

- **Estrutura** `frxworld:catacumba`, em 3 camadas (`Construir`, `EstruturaGrande`).
- O **Núcleo** (`BlocoNucleo`) escolhe a dungeon cadastrada no painel pela **distância do spawn**: cada dungeon cobre um anel de distância.
- Ao ativar, desperta o **guardião** (rank `[Chefe]`, nível recomendado + 5, barra de vida) e os lacaios.
- O prêmio final vai para quem estiver perto, com recarga por jogador. Os portões usam `BlocoPortao`.
- **Painel:**
  - anéis de distância com aviso de buracos e sobreposição;
  - editor com abas por camada, seletor de criatura e "Gerar camadas";
  - loot com chance;
  - medidor de dificuldade (Fácil / Equilibrado / Difícil / Brutal; fórmula em [05-painel.md](../05-painel.md)).
- Admin: `/frx dungeon ...` · `/frx cripta resetar`.
- Marco: `dungeon`.

## Masmorras dos reinos (`reinos/Masmorras.java`)

- Masmorras fixas de outros mods em pontos escolhidos. Por exemplo, a Fábrica Ancestral do Cataclysm embaixo do coliseu de Tempest.
- Configuração em `posicoes.json` → `masmorras: [{id, estrutura, x, z, entrada:[x,y,z]}]`. Cada uma é colocada uma vez por mundo (registro em `<mundo>/frx_masmorras.txt`), com um poço de escada até a estrutura.
- A colagem é assíncrona: espera os chunks e só cola quando não há jogador online.
- **Tesouros:** 5 baús do Lootr espalhados pelas salas ao colar a masmorra (`frxworld:chests/masmorra`). Cada jogador abre o seu.
- **Chefe derrotado:** um baú de prêmio aparece onde ele caiu (`frxworld:chests/masmorra_chefe`).
- `/masmorras` · admin `/frx masmorra` (lista) e `/frx masmorra baus` (põe 5 baús nas salas em volta de você).

## Chefes e estruturas de mods (`dungeon/Externos.java`)

- Cadastro no painel (menu **Chefes de mods**, modelos `ChefeExterno` e `EstruturaExterna`).
- Chefe de outro mod morto paga XP e loot do FRX a quem estava perto (com recarga por jogador) e conta para missões. Recebe rank `[Chefe]`.
- Ao entrar numa estrutura cadastrada, o jogador vê o nome e o nível recomendado.

## Chefe mundial (`eventos/ChefeMundial.java`)

- Em horários marcados no painel (Progressão → Chefe mundial), um chefe fortíssimo aparece longe das capitais. Rank `[Chefe Mundial]`, no nível máximo.
- Avisos com 10, 5 e 1 min de antecedência e barra de vida para todos.
- A recompensa é dividida pelo dano causado: moedas, XP e arma Lendária ou Épica para os 3 primeiros.
- `/chefe` · admin `/frx chefe agora|tempo|cancelar`.
- Marco: `chefe_mundial`.

## Eventos de estrada (`eventos/EventosEstrada.java`)

- Enquanto o jogador viaja, sorteia pelo nível dele uma emboscada, um chefe errante, um mercador raro ou um tesouro.
- Vencer paga XP e loot a quem estiver perto.
- Configuração no painel (menu **Eventos de estrada**). Admin: `/frx evento ...`.

## Elites e Campeões

São monstros comuns promovidos e não dependem de dungeon. Veja [progressao-zonas.md](progressao-zonas.md#monstros-com-rank).
