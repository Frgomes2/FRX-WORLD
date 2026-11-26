# Áreas protegidas, Labirinto do Dragão, túmulos e limpeza do chão

[← Índice](../README.md) · Código: `protecao/` (AreasProtegidas, Reequipar, Limpeza, AneisUnicos)

## Áreas protegidas (`AreasProtegidas.java`)

- Lidas de `<mundo>/frx_protegidas.txt`, uma por linha: `id;minX;minY;minZ;maxX;maxY;maxZ` e, opcionalmente, mais 6 números com o **refúgio** (uma zona segura dentro da área). Terminar a linha com `;livre` libera construir e quebrar para todos (a área continua sem explosões e o refúgio sem monstros).
- Dentro da área ninguém quebra nem coloca blocos, e explosões não destroem nada. Monstros também não mexem nos blocos (enderman, dragão, wither, bola de fogo) e balde de água ou lava não despeja. O admin (criativo + permissão 2) constrói normalmente.
- **Túmulos são exceção:** blocos do mod de túmulos (`gravestone`) podem ser quebrados em qualquer proteção: áreas protegidas, Torre, terrenos de outros e casinhas dos ofícios.
- No refúgio, monstros hostis somem a cada segundo e nenhum monstro nasce (natural, spawner ou evento; só comando e ovo).
- **Primeira chegada** (só nas áreas `labirinto*`): quem entra no refúgio pela primeira vez fica registrado por personagem em `<mundo>/frx_chegadas.json`. O primeiro de todos recebe um anúncio dourado no chat e entra no Mural dos Primeiros do site (marco `chegada:<id>`). Criativo, espectador e operadores não contam.

## Labirinto do Dragão

- Labirinto fechado de 141 × 141, com teto, do canto (1240, 63, 1711) ao (1380, 71, 1851). Área protegida `labirinto`; zona de nível 20 em `frx_areas.txt`.
- 67 geradores de monstros no piso e 20 baús Lootr (loot próprio para cada jogador).
- **Dificuldade (ajustada em 22/11):** os monstros dos geradores (husk, esqueleto wither, stray, aranha das cavernas, vingador e bruxa) têm metade da vida e da armadura originais, 55% do dano e 0,3 de resistência a repulsão; cada gerador solta 2 por vez (antes 4), no máximo 4 por perto (antes 8), a cada 15–30 s (antes 8–21 s). Por cima disso ainda vale o bônus da zona de nível 20. Comandos aplicados em `logs/lab-facil2.txt` (`data merge block` — `setblock` em cima de um gerador não troca os dados); os originais estão em `logs/lab-comandos2.txt`.
- **Anel de Voo:** só existe aqui. Os 2 baús Lootr de chefe do salão (1304, 65, 1783 e 1304, 65, 1785) usam a tabela `frxworld:chests/labirinto_salao`, a única com o anel (1 entre 10 anéis em cada baú, por jogador). Ele foi tirado dos baús de chefe das masmorras e das cidades do End.
- No centro (1310, 64, 1781) fica o salão seguro (1303–1317 × 1774–1788), com porta de ferro com botões e o **Criador de Dragões**. O salão é o refúgio da área.
- O esquema está em `cliente/jogo/schematics/labirinto_grande_141.litematic` (gerado por script, semente 2311).

## Templo Karin

- Área `karin` de (2015, 40, 1595) a (2105, 319, 1685): ninguém quebra nem coloca bloco. As regras de escalada e o chefão estão em [aventura.md](aventura.md).

## Hipódromo

- Pista de corrida a cavalo perto de Lubelius, colada com o Litematica (origem 1523, 63, 1724). Área `hipodromo;…;livre` de (1413, 40, 1726) a (1521, 140, 1928), com o refúgio na área inteira: os jogadores constroem à vontade, mas explosão não estraga nada, monstro não nasce e monstro que entra some.
- O chão de areia fica na altura 64, apoiado numa camada de pedra em 63 (a pista do esquema flutuava sobre o mar).

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
