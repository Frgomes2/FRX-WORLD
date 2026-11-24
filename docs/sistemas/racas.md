# Raças

[← Índice](../README.md) · Código: `racas/DefRacas.java`, `racas/RacasJogador.java` · Cliente: `ClienteRacas`, `TelaRacas`, `CamadaRaca` · Desde a **0.38**

## Conceito

O jogador escolhe uma raça **principal** e, se quiser, uma **secundária**:

- A principal vale **100%** dos bônus e penalidades.
- A secundária vale uma fração que depende da **compatibilidade** entre as duas.
- Sem secundária, o jogador é **puro-sangue** e a principal vale **130%**.

O visual não entrega todo o poder: as raças lendárias andam disfarçadas.

## As 16 raças

### Comuns (livres na criação)

| Raça | Família | Vantagens | Preço |
|---|---|---|---|
| Humano | neutro | +10% XP, +10% reputação ganha | — |
| Elfo | luz | +15% recurso máximo, +15% dano com arco | −1 coração |
| Anão | terra | +3 armadura, +1 resistência, +20% mineração | −5% velocidade |
| Beastkin | fera | +10% velocidade, visão noturna | −10% recurso máximo |
| Halfling | terra | +2 sorte, 10% de esquiva | −10% dano corpo a corpo |

### Especiais (nível 20 **ou** Honrado, com 500 de reputação, no reino ligado)

| Raça | Família | Reino | Vantagens | Preço |
|---|---|---|---|---|
| Demônio | trevas | Lordes Demônios | Imune a fogo/lava, +15% dano mágico | +15% dano mágico recebido |
| Vampiro | trevas | Lubelius | Roubo de vida 8%, visão noturna | Fraco sob o sol |
| Dragonoide | draco | Tempest | +3 corações, metade do dano de fogo | Fome 25% mais rápida |
| Oni | fera | Império do Leste | +12% dano corpo a corpo, +1 coração | −15% recurso máximo |
| Espírito | luz | Sarion | +25% regeneração de recurso, queda lenta | −2 corações |
| Gigante | terra | Dwargon | +4 corações, +1 de alcance, resiste a empurrões | −8% velocidade |

### Lendárias (por feitos; nunca podem ser secundária)

| Raça | Como liberar | Vantagens | Preço |
|---|---|---|---|
| Anjo | Concluir a campanha de Lubelius | Plana, sem dano de queda, +30% cura recebida | −10% dano |
| Rei Demônio | Concluir a campanha dos Lordes Demônios | Aura (monstros a 6 blocos ficam fracos e lentos), +15% dano, imune a fogo | −25% cura recebida |
| Dragão | Derrotar um dragão (tags `c:dragoes` / `c:dragao_end`) | Sopro de fogo a cada 30 s, +5 corações, imune a fogo | −5% velocidade, fome 50% mais rápida |
| Ser Celestial | Chegar ao nível 100 | Regenera toda a vida fora de combate, +30% regen. de recurso, sem dano de queda | −10% dano |
| Saiyajin | Vencer o Príncipe Vegeta no topo do Templo Karin (ver [aventura.md](aventura.md#templo-karin-e-príncipe-vegeta-karinjava-vegetajava)) | +15% dano, +2 corações, Zenkai e transformações Super Saiyajin | Fome 50% mais rápida (ainda mais transformado) |

### Divina

| Raça | Como liberar | Efeito |
|---|---|---|
| Deus | Só por evento ou admin | Um pouco de tudo: vida, dano, velocidade, XP e recurso |

### Saiyajin (`racas/Saiyajin.java`)

- **Zenkai:** quando o Saiyajin fica com menos de 20% de vida e sobrevive, depois de 8 s e com a vida acima de 40% ganha +1% de dano permanente (até +15%, no máximo uma vez a cada 10 minutos). Fica salvo no jogador (`frx_zenkai`).
- **Transformação (tecla U, reconfigurável):** cada aperto sobe uma forma; agachado + U volta ao normal.

| Forma | Nível | Dano | Velocidade | Armadura | Fome por segundo |
|---|---|---|---|---|---|
| Super Saiyajin | 20 | +25% | +12% | +2 | 0,2 |
| Super Saiyajin 2 | 40 | +45% (golpe dá choque: +15% e lentidão) | +18% | +4 | 0,4 |
| Super Saiyajin 3 | 60 | +70% (com choque) | +25% | +6 | 0,9 |

- Transformar leva 2 s parado (aura subindo, chão rachando, raio e explosão no fim que empurra quem estiver a 4 blocos, sem dano). Não dá para transformar com menos de 6 de fome; com 3 ou menos, volta ao normal.
- A forma 3 dura até 60 s e depois deixa lento, fraco e cansado por 10 s; só pode repetir 30 s depois.
- Transformado, o disfarce cai: o nome mostra "Super Saiyajin N" e `CamadaRaca` desenha o cabelo dourado espetado (longo na forma 3), que brilha no escuro. Morrer, sair ou trocar de dimensão desfaz a forma.

## Compatibilidade (peso da secundária)

| Classificação | Peso | Quando |
|---|---|---|
| Puro-sangue | 130% (principal) | Sem secundária |
| Harmônica | 60% | Mesma família, ou draco + fera |
| Estável | 50% | Envolve Humano, principal divina, ou demais pares |
| Instável | 35% | Luz × trevas, espírito + gigante, espírito + dragonoide |
| Proibida | — | Gigante + Halfling, lendária como secundária, ou luz × trevas com principal lendária |

Exemplos: Elfo + Vampiro = Instável · Elfo + Oni = Estável · Anão + Gigante = Harmônica.

## Troca de raça

- A primeira escolha e a primeira troca são grátis.
- Depois: 10 ouro, 50 ouro, e então 200 ouro × (trocas − 2). O valor sai da bolsa ou do banco.
- Trocar **nunca** apaga nível, conquistas, habilidades ou marcos.
- O admin define sem custo: `/frx raca <j> definir`.

## Visual e disfarce

- O nome aparece na cabeça, no chat e no TAB como `Nick · Elfo/Oni`.
- Traços em `CamadaRaca`: orelhas (elfo, beastkin), cauda (beastkin), chifres (oni, demônio), olhos brilhando (vampiro, espírito), auréola (anjo).
  - Os traços da secundária aparecem menores e sem cauda.
  - Não muda a hitbox.
- **Lendárias** andam disfarçadas: o nome mostra uma raça comum, com partículas discretas.
  - Exceção: a **Saiyajin** não se disfarça; aparece no nome e no TAB.
  - `/raca revelar` mostra a verdadeira.
  - NPCs reconhecem ao conversar.
  - Jogadores de nível 50+ percebem com `/raca ver <j>`.

## Integração

`RacasJogador.num(p, chave)` entra em:

- `Progressao.ganharXp` (`xp`);
- `Reputacao.ganhar` (`reputacao`);
- `Habilidades.maximo` (`recursoMax`) e na regeneração (`regenRecurso`).

Com o Apothic Attributes ativo, esquiva, roubo de vida, cura recebida, mineração e dano de arco viram atributos do Apothic, e os handlers próprios ficam desligados. Veja [atributos-forja.md](atributos-forja.md).

## NPCs

Os moradores dos reinos também têm raças. Veja [reinos.md](reinos.md#população-e-raças-dos-npcs).
