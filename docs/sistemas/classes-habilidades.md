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
- **Atalhos:** 3 atalhos para sombras específicas (sem tecla no início) e a tecla **Z** para todas. Ficam salvos em NBT (`frx_sombras_atalhos`) e só funcionam no Invocador.
- **Comportamento:**
  - As sombras seguem o dono e atacam o que ele ataca.
  - Uma sombra que morre leva 60 s para se recompor.
  - Ao sair ou morrer, as sombras fora são recolhidas.
- **Comandos:** `/sombra erguer|invocar|todas|guardar|libertar` e `/levantese`.

## Talentos

Código: `habilidades/Talentos.java`. Tela: aba Talentos da janela de Status.

- **1 ponto a cada 2 níveis** (nível 100 = 50 pontos).
- **3 ramos por classe**, com 5 talentos em linha. O máximo de pontos por talento é 5, 5, 5, 3 e 1.
- O 2º talento do ramo pede 5 pontos no ramo, o 3º pede 10, o 4º 15 e o último (capstone) 20.
- Os talentos dão atributos, como dano, vida, crítico, esquiva e roubo de vida. Com o Apothic ativo, crítico, roubo de vida, esquiva e dano de arco viram atributos do Apothic. Veja [atributos-forja.md](atributos-forja.md).
- `/talentos aprender <id>` · `/talentos resetar` · admin `/frx talentos resetar <j>`.
