# Áreas protegidas, Labirinto do Dragão, túmulos e limpeza do chão

[← Índice](../README.md) · Código: `protecao/` (AreasProtegidas, Reequipar, Limpeza, AneisUnicos)

## Áreas protegidas (`AreasProtegidas.java`)

- Lidas de `<mundo>/frx_protegidas.txt`, uma por linha: `id;minX;minY;minZ;maxX;maxY;maxZ` e, opcionalmente, mais 6 números com o **refúgio** (uma zona segura dentro da área).
- Dentro da área ninguém quebra nem coloca blocos, e explosões não destroem nada. O admin (criativo + permissão 2) constrói normalmente.
- **Túmulos são exceção:** blocos do mod de túmulos (`gravestone`) podem ser quebrados em qualquer proteção: áreas protegidas, Torre, terrenos de outros e casinhas dos ofícios.
- No refúgio, monstros hostis somem a cada segundo.
- **Primeira chegada:** quem entra no refúgio pela primeira vez fica registrado por personagem em `<mundo>/frx_chegadas.json`. O primeiro de todos recebe um anúncio dourado no chat e entra no Mural dos Primeiros do site (marco `chegada:<id>`). Criativo, espectador e operadores não contam.

## Labirinto do Dragão

- Labirinto fechado de 141 × 141, com teto, do canto (1240, 63, 1711) ao (1380, 71, 1851). Área protegida `labirinto`; zona de nível 20 em `frx_areas.txt`.
- 67 geradores de monstros no piso e 20 baús Lootr (loot próprio para cada jogador).
- No centro (1310, 64, 1781) fica o salão seguro (1303–1317 × 1774–1788), com porta de ferro com botões e o **Criador de Dragões**. O salão é o refúgio da área.
- O esquema está em `cliente/jogo/schematics/labirinto_grande_141.litematic` (gerado por script, semente 2311).

## NPC fora de capital

- `/frx npc aqui <papel>` fora de uma capital cria um NPC solto (sem reino), usado no salão do labirinto.
- `/frx npc remover` tira o NPC solto mais próximo, a até 4 blocos.

## Reequipar ao pegar o túmulo (`Reequipar.java`)

- Na morte, o mod guarda o que estava vestido: armadura, mão secundária e os espaços do Curios (anéis, colares etc.).
- Quando o jogador quebra o próprio túmulo, cada item volta sozinho para o espaço em que estava, se o espaço estiver vazio. Vale por 2 horas depois da morte.

## Limpeza do chão (`Limpeza.java`)

- A cada 30 minutos, some todo item solto no chão há mais de 2 minutos, em todas as dimensões. Itens recém-caídos e túmulos não são afetados.
- O chat só mostra uma linha de resumo quando algo foi removido.

## Configurações de mods ligadas a isso

- `server/config/dragonmounts-common.toml`: `update_habitats = false` (o ovo não muda de tipo pelo bloco onde está).
- `server/config/waystones-common.toml`: `restrictRenameToOwner = true`.
- `server/config/fallingtree.json`: derruba a árvore inteira só quando há pelo menos 3 folhas naturais em volta, para não afetar construções.
