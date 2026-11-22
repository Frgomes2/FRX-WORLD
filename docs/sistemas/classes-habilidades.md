# Classes, habilidades, Despertar, Sombras e talentos

[← Índice](../README.md) · Código: `habilidades/` (Habilidades, HabilidadeDef, Talentos, Despertar, Sombras, ItemTomo) · Cliente: `TelaHabilidades`, `TelaSombras`, `HudHabilidades`

## Classes e recursos

| Classe | Recurso | Ramos de talento |
|---|---|---|
| Guerreiro | Fúria (começa em 0, enche lutando) | Fúria · Baluarte · Senhor da Guerra |
| Arqueiro | Energia | Atirador · Patrulheiro · Caçador |
| Mago | Mana | Arcano · Elementalista · Sábio |
| Clérigo | Mana | Luz · Templário · Oráculo |
| Ladino | Energia | Assassino · Sombra · Trapaceiro |
| Assassino | Energia | Letalidade · Sangria · Fantasma |
| Invocador | Mana | Convocação · Pacto · Ritualista |

- **Mana:** máximo = 100 + 4 × (nível − 1), até 500, mais bônus de raça e talento. Regenera mais rápido fora de combate.
- **Energia:** 100, regenera 10 por ciclo.
- **Fúria:** 100, enche ao bater e apanhar.

## Habilidades

- As habilidades são cadastradas no **painel** (menu Habilidades, modelo `Habilidade`):
  - campos: código, classe, tipo, elemento, custo, recarga, dano, alcance, raio, duração, efeito, nível exigido e ultimate;
  - aprendizado automático ou por **tomo** (`ItemTomo`).
- Cada classe tem 4 habilidades + 1 ultimate na barra. As teclas são configuráveis na tela de Habilidades (tecla **K**).
- Admin: `/frx admin <j> habilidade aprender|esquecer|nivel|todas` e `/frx tomo`.

### Mecânicas especiais (`Especiais.java`)

No campo **efeito**, em vez de um efeito de poção, pode ir uma mecânica do mod (prefixo `frx:`). Os números continuam vindo do painel.

| Efeito | O que faz |
|---|---|
| `frx:puxar` | Fisga o alvo da mira e arrasta até o jogador (chefes imunes a empurrão só ficam lentos) |
| `frx:provocar` | Monstros no raio passam a atacar o jogador, que ganha Resistência |
| `frx:salto` | Salto na direção da mira; dano em área ao tocar o chão |
| `frx:fenda` | Linha no chão que atravessa e levanta todos os inimigos |
| `frx:executar` | Dano até ×3 conforme a vida que falta; se matar, recarga de 1 s e metade do custo de volta |
| `frx:vampirismo` | Potência × 10% do dano causado vira cura (máx. 8 por golpe) |
| `frx:refletir` | Potência × 10% do dano recebido volta no agressor |
| `frx:lancar` | Explosão em volta que arremessa para o alto; Absorção no jogador |
| `frx:explosiva` | Tiro que explode no impacto (raio) |
| `frx:ricochete` | Acerta o alvo e salta em até *potência* inimigos próximos, −25% por salto |
| `frx:marca` | Alvo brilha e leva potência × 10% a mais de dano de quem marcou |
| `frx:gancho` | Puxa o jogador até o bloco mirado, sem dano de queda |
| `frx:leque` | Cone de 5 flechas à frente, com empurrão |
| `frx:voleio` | Salto para trás acertando o alvo da mira |
| `frx:perfurar` | Linha que atravessa todos; dano até ×2 com a distância |
| `frx:armadilha` | Armadilha no chão: prende quem pisar (45 s armada, máx. 2 por jogador) |
| `frx:congelar` | Nova em volta do jogador: dano e inimigos congelados no lugar |
| `frx:misseis` | *Potência* mísseis teleguiados em sequência; mudam de alvo se o primeiro morrer |
| `frx:escudo_mana` | Potência × 10% do dano recebido sai do recurso (2 por ponto); quebra sem recurso |
| `frx:trocar` | Troca de lugar com o alvo (inimigo ou aliado); chefes imunes a empurrão não trocam |
| `frx:sifao` | Dano e devolve potência × 10 de recurso |
| `frx:zona` | Zona persistente com dano por segundo, no elemento da habilidade (máx. 2 por jogador) |
| `frx:singularidade` | Buraco negro que puxa, fere por segundo e explode (×2) no fim |
| `frx:atras` | Aparece atrás do alvo da mira, virado para as costas dele, e golpeia |
| `frx:fumaca` | Em volta (raio): inimigos cegos, lentos e sem alvo pela duração; o jogador fica invisível e rápido |
| `frx:laminas` | Pela duração, todo golpe do jogador envenena (potência = nível; mortos-vivos definham) |
| `frx:emboscada` | Invisível e rápido pela duração; o próximo golpe sai potência × 50% mais forte e paralisa |
| `frx:sangria` | Golpe e sangramento: metade do dano a cada segundo pela duração |
| `frx:danca` | Salta por até potência inimigos no raio, um golpe pelas costas em cada, e volta ao ponto de partida; imune a dano durante |

Guerreiro (15 habilidades, 4 + ultimate na barra — o jogador monta a build): Golpe Brutal, Arremesso de Machado (nv 2), Investida (3), Gancho de Corrente (4), Grito de Guerra (5), Provocar (6), Salto Esmagador (8), Postura de Revide (9), Terremoto (10), Fenda Sísmica (11), Redemoinho de Aço (12, tomo), Executar (14), Sede de Sangue (16, tomo), Fúria Imortal (20, ultimate), Ira do Titã (25, ultimate).

Arqueiro (14 habilidades): Tiro Preciso, Recuo (3), Flecha Explosiva (4), Voleio de Recuo (5), Chuva de Flechas (6), Marca do Caçador (7), Leque de Flechas (8), Armadilha de Caçador (9), Olho de Águia (10), Flecha-Gancho (11), Flecha Perfurante (12, tomo), Tiro Ricochete (14, tomo), Tempestade de Flechas (20, ultimate), Disparo do Falcão (25, ultimate).

Mago (15 habilidades): Bola de Fogo, Lança de Gelo (3), Nova de Gelo (4), Teleporte (5), Mísseis Arcanos (6), Telecinese (7), Barreira de Mana (8), Transposição (9), Meteoro (10), Sifão Arcano (11), Raio (12, tomo), Corrente de Raios (15), Nevasca (16, tomo), Chuva de Meteoros (20, ultimate), Singularidade (25, ultimate).

## Despertar (poder especial inspirado em anime)

Tecla **B**. Libera no **nível 10**.

- Uma barra fina acima da barra de recurso enche lutando do jeito da classe. Em 100%, pisca **"PRONTO [B]"**.
- Depois de 30 s fora de combate, cai 1% por segundo.
- Código em `habilidades/Despertar.java`, pacote `SyncDespertar`, ação `AcaoHabilidade.DESPERTAR` (3).
- `/frx despertar` enche a barra (teste).

| Classe | Poder | Enche com | Efeito | Preço |
|---|---|---|---|---|
| Guerreiro | **Armadura do Berserker** | Lutar | Bate mais forte, não é empurrado, a vida não cai abaixo de 1 | −1 de vida a cada 2 s; no fim, fraqueza e lentidão por 6 s |
| Arqueiro | **Chuva de Luz** | Acertar flechas (mais de longe, a partir de 20 blocos) | Flechas de luz por 3 s numa área a até 48 blocos; não acerta aliados, aldeões nem pets | — |
| Mago | **Explosão!** | Gastar mana em habilidades | Canto de 3 s com círculo de fogo, depois uma explosão enorme que não quebra blocos nem fere o mago | Mana zerada e 8 s lento e fraco. Um golpe de 30% da vida interrompe e a barra fica em 50% |
| Clérigo | **Estandarte da Donzela** | Curar e lutar | Anel de luz (raio 7) por 10–18 s: aliados e aldeões curam e recebem metade do dano; mortos-vivos queimam, outros mobs ficam lentos | — |
| Ladino | **Roubar!** | Lutar | Rouba armas, saque e moedas dos mobs próximos; 5 s invisível e rápido; mobs fracos e lentos | Roubar o mesmo mob de novo dá só moedas |
| Assassino | **Respiração do Trovão** | Lutar | Investida de 12 blocos cortando quem está no caminho; até 3 investidas em 5 s | Para antes de paredes, sem dano de queda |
| Invocador | **Levante-se!** | — | B ergue a sombra mais próxima (igual a `/levantese`) | — |

Ganhar barra de Despertar conta como marco (`despertar`).

## Exército de Sombras (Invocador)

Código: `habilidades/Sombras.java` · Pacotes `SyncSombras` e `AcaoSombra` · Tela `TelaSombras` (K → botão **⚫ Exército**).

- **Erguer:** ao matar um mob, a alma espera **20 s**. `/levantese`, B ou o botão no chat gastam mana e tentam erguer. Chefes só a partir do nível 25, pelo dobro da mana.
- **Limites** crescem com o nível. No nível 30: até 7 sombras no exército e 4 fora ao mesmo tempo.
- **Tela do Exército:**
  - lista com ovo, nome, nível e estado (fora, guardada ou se recompondo);
  - modelo 3D roxo;
  - ações Invocar/Recolher, Invocar todas/Recolher todas e Libertar (com confirmação).
- **Nível da sombra:** de 0 a 10, com 3 × n² abates para o nível n (3, 12, 27... 300). Contam os abates da própria sombra e os do dono enquanto ela está em campo a até 24 blocos. A força parte de 40% do monstro original e sobe 3% por nível. A tela mostra o progresso em barra.
- **Invocações por habilidade** (tipo `INVOCAR`): a criatura nasce com força = talento de Invocação × (1 + 10% por nível da habilidade até o 10 e 4% do 11 ao 30) × (1 + 4% por nível do jogador); o nome mostra o nível da habilidade.
- **Atalhos:** 3 atalhos para sombras específicas (sem tecla no início) e a tecla **Z** para todas. Ficam salvos em NBT (`frx_sombras_atalhos`) e só funcionam no Invocador.
- **Comportamento:**
  - As sombras seguem o dono e atacam o que ele ataca.
  - Uma sombra que morre leva 60 s para se recompor.
  - Ao sair ou morrer, as sombras fora são recolhidas.
  - **Marcar:** aponta para uma criatura e a tropa ataca; com PvP ligado, vale para outro jogador (menos membros do mesmo clã), que recebe um aviso. Apontando para um bloco a até 40 blocos, a tropa vai até lá e fica de **posto**; o posto se desfaz se o dono se afastar mais de 48 blocos, e apontar para o céu libera a tropa.
  - Invocações (sombras, golem e as demais) não atacam jogadores, aldeões, NPCs, animais domados nem outras invocações, a não ser o alvo marcado.
- **Identificação (`habilidades/MarcaInvocacao.java`, `cliente/SombraInvocacao.java`):** toda invocação entra no time `frx_invocacoes` (roxo) ao entrar no mundo e sai ao sair (o placar não acumula). No jogo de todos, ela aparece escurecida e puxada para o roxo, como uma sombra (o nome flutuante continua legível), com uma aura de fumaça e faíscas roxas. O dano entre invocações e jogadores segue o botão **"Dano em jogadores"** do menu do Exército (NBT `frx_invoc_pvp`, desligado por padrão): desligado, as invocações não ferem jogadores e nenhum jogador fere as invocações (bom para dungeons em grupo); ligado, vale nos dois sentidos. O dono nunca fere as próprias invocações nem é ferido por elas; com o dono offline, vale desligado.
- **Comandos:** `/sombra erguer|invocar|todas|guardar|libertar` e `/levantese`.

## Talentos

Código: `habilidades/Talentos.java`. Tela: aba Talentos da janela de Status.

- **1 ponto a cada 2 níveis** (nível 100 = 50 pontos).
- **3 ramos por classe**, com 5 talentos em linha. O máximo de pontos por talento é 5, 5, 5, 3 e 1.
- O 2º talento do ramo pede 5 pontos no ramo, o 3º pede 10, o 4º 15 e o último (capstone) 20.
- Os talentos dão atributos, como dano, vida, crítico, esquiva e roubo de vida. Com o Apothic ativo, crítico, roubo de vida, esquiva e dano de arco viram atributos do Apothic. Veja [atributos-forja.md](atributos-forja.md).
- `/talentos aprender <id>` · `/talentos resetar` · admin `/frx talentos resetar <j>`.

## Espaços da barra por rank da Guilda

A barra tem 4 espaços comuns e 1 de ultimate para todos. O rank da Guilda libera mais espaços comuns: Rank E o 5º, D o 6º, C o 7º e B o 8º (A e S ficam com 8). Os extras aparecem numa linha acima da barra, com as teclas Z, H, N e Y por padrão; todas as teclas são trocadas na janela de habilidades (tecla K). Quem perde o rank mantém a habilidade no espaço, mas não consegue usá-la até recuperar o rank.

## Samurai (1.1.2)

- **Recurso:** Energia. **Arma:** katana (qualquer item cujo id contenha `katana`, `odachi`, `nodachi`, `wakizashi`, `tachi` ou `uchigatana`; as do mod Samurai Dynasty servem).
- **Saque:** 3 s sem golpear embainham a katana; o golpe corpo a corpo seguinte causa 75% a mais. Todo golpe com katana causa 10% a mais. Código em `habilidades/Bushido.java`.
- **Formas:** as habilidades usam os elementos Gelo (Maré), Fogo (Brasa) e Raio (Relâmpago).
- **Despertar:** Lâmina das Três Formas (todo golpe é Saque e alterna as três formas).
- **Talentos:** Fio da Lâmina (dano físico e crítico), Três Formas (dano de habilidade) e Honra (armadura, vida e resistência).

## Cartomante (1.2.1)

- **Recurso:** Mana. Classe da sorte, original do FRX-WORLD. Código: `habilidades/Cartas.java`.
- **Mão:** 3 cartas sorteadas, mostradas na barra de ação (a da vez entre colchetes). Toda habilidade de dano gasta a carta da vez e puxa outra para o fim da mão.
- **Naipes:** Espadas +40% de dano · Copas cura 20% do dano causado · Ouros devolve 60% da mana gasta · Paus prende o alvo por 2 s · Coringa (8% por carta) faz os quatro.
- **Habilidades:** Carta Cortante (1), Embaralhar (2), Cara ou Coroa (4), Leque de Cartas (5), Truque de Mão (7), Carta Marcada (8), Maré de Sorte (10), Mão Cheia (12), Cartas na Mesa (14), Blefe (16), Ás na Manga (18, Tomo), Casa Cheia (25, ultimate).
- **Não gastam carta:** Embaralhar, Maré de Sorte, Blefe, Truque de Mão. Mão Cheia gasta as três. Casa Cheia vale como Coringa e não mexe na mão.
- **Despertar:** Mão Perfeita, 8 a 12 s com toda carta saindo Coringa.
- **Talentos:** Naipes (dano), Baralho (recarga e custo), Sorte (vida, crítico, resistência).
- A mão fica só na memória do servidor: ao sair e voltar, o jogador recebe uma mão nova.

## Nível das habilidades

- Cada habilidade sobe com o uso, de 1 a **30** (`Habilidades.NIVEL_MAX`). Nível n pede 5 × (n − 1)² usos: 2 = 5, 5 = 80, 10 = 405, 20 = 1.805, 30 = 4.205.
- Por nível, do 2 ao 10: +8% de dano e −3% de recarga. Do 11 ao 30: +3% de dano e −1% de recarga. No 30: dano ×2,32 e recarga −47% (antes dos talentos).


## Teto de armadura (`progressao/TetoAtributos.java`)

O Minecraft limita a armadura a 30; o mod sobe o limite para **100** (servidor e cliente). O dano vem da fórmula do Apothic Attributes (`config/attributeslib.cfg`): dano que passa = `a / (a + armadura)`, com `a = 10` em golpes até 20 de dano (`10 + (dano − 20)/2` acima disso). Exemplos para golpes comuns: 20 de armadura → 33%; 30 → 25%; 40 → 20%; 60 → 14%; 100 → 9%. A resistência (toughness) não entra nessa fórmula padrão. A Proteção dos encantamentos reduz à parte (`1 − min(0,025 × pontos, 0,85)`).
