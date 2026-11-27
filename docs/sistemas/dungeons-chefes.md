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
- **Chefe derrotado:** um baú de prêmio aparece onde ele caiu (`frxworld:chests/masmorra_chefe`). O baú só aparece 3 s depois que o corpo some (no máximo 30 s após a morte), porque alguns chefes explodem no fim da animação; fica protegido de explosões por 2 minutos.
- **Chefes de fim de jogo:** chefe dentro de masmorra com nível próprio (campo Nível na tela Masmorras; `AreasNivel`) recebe o bônus de nível inteiro, mais +300% de vida e +100% de dano. Jogador abaixo do nível 50 causa no máximo 20% do dano e recebe pelo menos o triplo. Masmorra enterrada embaixo de capital nunca fica abaixo do nível 50.
- `/masmorras` · admin `/frx masmorra` (lista) e `/frx masmorra baus` (põe 5 baús nas salas em volta de você).

### Entradas (`reinos/EntradasMasmorra.java`)

- **Masmorra enterrada** (o topo da estrutura fica abaixo do chão): ganha um **pórtico** de pedra negra na superfície, com o nome flutuando. No centro há um bloco de obsidiana chorosa: quem pisa é levado para dentro. Na sala de chegada, outro bloco igual ("Saída") traz de volta. Há 4 s de recarga entre viagens e 3 s de proteção ao chegar.
- **Masmorra de superfície:** ganha só um **marco** (o mesmo pórtico, sem portal) ao lado da construção. A entrada é a da própria estrutura.
- **Sala de chegada:** o mod recalcula a estrutura (mesma semente, sem colocar blocos), percorre as peças e só aceita um ponto com 3×3×3 livre e com piso, teto e paredes de construção em pelo menos três lados. Pedra e terra natural são descartadas, para não cair em caverna encostada. Entre os pontos válidos, fica o mais alto e mais central.
- O pórtico usa a `entrada` da configuração quando existe; senão, fica sobre o centro da estrutura.
- Registro por mundo em `<mundo>/frx_entradas.txt` (`id;sx;sy;sz;dx;dy;dz`, ou só `id;sx;sy;sz` para marco). Masmorras novas ganham a entrada cerca de 30 s depois de colocadas.
- `/masmorras` e o painel mostram a entrada, não o centro da estrutura.
- Admin: `/frx masmorra entradas` lista; `/frx masmorra entradas refazer` refaz todas; `/frx masmorra entradas refazer <id>` refaz uma, no mesmo lugar.

| Masmorra | Tipo | Entrada (x, y, z) |
|---|---|---|
| Fábrica Ancestral (Tempest) | portal | 255, 72, 125 |
| Câmara do Cavaleiro de Ferro (Dwargon) | portal | 1144, 63, 2276 |
| Templo da Górgona (Sakura) | portal | −2605, 65, 989 |
| Prisão Congelada (Sarion) | marco | 2061, 74, −984 |
| Mosteiro (Leste) | marco | −2708, 58, −1079 |

## Chefes e estruturas de mods (`dungeon/Externos.java`)

- Cadastro no painel (menu **Chefes de mods**, modelos `ChefeExterno` e `EstruturaExterna`).
- Chefe de outro mod morto paga a quem estava perto e conta para missões. Prêmio **uma vez por recarga** por jogador (moedas, XP e loot). **Moedas pela dificuldade** (`Recompensa.chefaoExterno`): nível < 40 = 20 cobre por nível (Aptrgangr 30 = 6 ouro); 40 a 54 = 50 por nível (Frostmaw 40 = 20 ouro); 55 ou mais, os especiais = 3 × nível² (Ignis 60 = 108 ouro, Tremorzilla 80 = 192 ouro). Catacumbas: `masmorras.moedasPorNivel` (padrão 40 cobre) × nível do chefão; a XP cresce com o nível de quem lutou: XP do painel × (1 + nível/50). Recebe rank `[Chefe]`.
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

## Chefes das masmorras fixas renascem (2.0.8)

- Vale para as masmorras do `frx_masmorras.txt` (as que ficam embaixo dos reinos e do coliseu de Tempest).
- Quando o chefe morre a até 110 blocos da masmorra, o servidor guarda o tipo, o nível e o lugar onde ele apareceu pela primeira vez.
- **7 dias reais** depois, o chat avisa que ele voltou e ele nasce assim que alguém chegar perto (o pedaço do mundo precisa estar carregado).
- **Volta mais forte:** cada renascimento é uma geração nova (até a 10ª): +25% de vida, +15% de dano, +2 de armadura e +5% de resistência a empurrão por geração. O nome ganha a marca (ex.: "Ignis ✦3"), o aviso no chat diz o tamanho do reforço e a barra da masmorra mostra a geração. A geração fica gravada no chefe e passa de uma morte para a outra (`geracao` em `frx_chefes_masmorra.json`). Chefe que já tinha morrido antes desta regra volta como ✦1.
- Conta como chefe: tudo que tem a marca `forge:bosses` (Ignis, Maledictus, Harbinger etc.) o Ferrous Wroughtnaut e o Lich (The Graveyard). Chefes de evento do próprio mod não entram.
- Um de cada tipo por masmorra: se já houver um vivo ali (invocado por jogador no altar, por exemplo), não nasce outro.
- Chefes mortos antes da 2.0.8 não ficaram registrados: o admin vai até a sala e usa `/frx masmorra chefe <tipo>`.
- Admin: `/frx masmorra chefes` (lista e prazos) e `/frx masmorra renascer` (libera todos agora).

## Pórtico de masmorra em cada capital (2.0.9)

- Toda masmorra fixa pertence a um reino (pelo começo do id: `dwargon-ferro` → Dwargon, `sakura-…` → Distrito Sakura, `coliseu-tempest` → Tempest).
- Dentro da capital há um pórtico de pedra-negra com o nome da masmorra. Pisar no portal leva à masmorra; o portal de lá traz de volta ao pórtico por onde o jogador entrou.
- Quando a masmorra fica longe da capital (Dwargon, Sarion, Leste, Sakura), a entrada antiga ao lado dela continua funcionando.
- O servidor escolhe o lugar do pórtico sozinho, perto da praça, num quadrado 7×7 plano e livre. Registro em `<mundo>/frx_portais_reino.txt`; apagar a linha de uma masmorra faz o servidor montar de novo no próximo início.
- Os pórticos aparecem no JourneyMap como quadrados roxos.

| Reino | Masmorra | Chefe |
|---|---|---|
| Tempest | Fábrica Ancestral (embaixo do coliseu) | The Harbinger |
| Lubelius | Arena Flamejante (embaixo da capital) | Ignis |
| Sarion | Prisão Congelada (ao norte) | Maledictus |
| Dwargon | Câmara do Cavaleiro de Ferro (a leste) | Ferrous Wroughtnaut |
| Leste | Mosteiro da Montanha (ao norte) | Ancient Remnant |
| Sakura | Templo da Górgona (ao sul) | Lich |
| Oeste | Cidadela em Ruínas (embaixo da capital) | Ender Guardian (ou o da estrutura que couber) |
| Demônios | Forja das Almas (embaixo da capital) | Netherite Monstrosity (ou o da estrutura que couber) |

### Contador do chefe (2.0.10)

Com o chefe morto, quem está dentro da masmorra vê no alto da tela uma barra roxa com o nome dele e o tempo que falta para voltar, contando a cada segundo. A barra enche conforme os 7 dias passam e some quando ele renasce.

## Chefes do Twilight Forest renascem (3.0.10)

`reinos/ChefesCrepusculo.java`. No mod original, o bloco que invoca o chefe some depois da luta e ele nunca mais volta. No FRX-WORLD:

- Vale para Naga, Lich, Minoshroom, Hydra, Cavaleiros Fantasmas, Ur-Ghast, Yeti Alfa e Rainha da Neve que nasceram do invocador da construção (chefe de ovo ou comando não tem "casa" e não renasce; cópias do Lich não contam).
- Na morte, guarda onde ficava o invocador. **3 dias reais** depois, o servidor avisa no chat e recoloca o invocador assim que o lugar estiver carregado; o chefe aparece quando alguém chega perto, do jeito normal do mod.
- A construção é reativada (`tffeature conquer reactivate`), então volta a ter os monstros dela.
- Mesma regra de gerações das masmorras: cada volta é mais forte (até ✦10, +25% de vida e +15% de dano por geração).
- Chefes mortos antes da 3.0.10 não têm registro e não voltam sozinhos.
- Estado em `<mundo>/frx_chefes_crepusculo.json`. Admin: `/frx crepusculo chefes` (lista) e `/frx crepusculo renascer` (libera todos os pendentes agora).
