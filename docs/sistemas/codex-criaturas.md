# Códex de Criaturas

[← Índice](../README.md) · Mod: `aventura/Criaturas.java`, `aventura/Chefes.java` · Painel: `/admin/criaturas` (`src/app/admin/criaturas/`, `src/lib/criaturas-codex.ts`) · Rotas: `/api/servidor/criaturas`, `/api/servidor/npc-skins`

Chefes e monstros próprios do servidor, criados no painel. Os 14 chefes que existiam no código entram como cadastros iniciais e podem ser editados ou apagados.

## O que se define no painel

| Campo | Efeito |
|---|---|
| Tipo | **Chefe** (barra de vida, golpes anunciados, um por vez) ou **Monstro** (aparece no meio dos comuns) |
| Criatura base | Dá o corpo, a área de acerto, o jeito de andar e de bater e os sons. Pode ser de qualquer mod |
| Rank de/até | Ranks dos lugares em que aparece (F a S): rank do portal, andar da Torre ÷ 15, rank da Guilda do jogador no Nether |
| Vida ×, Dano ×, Velocidade × | Multiplicadores por cima do que o lugar já dá (nível da zona, vida de chefe, bônus de andar). 1 = igual aos outros |
| Golpes (chefe) | Chamar reforços, onda de choque, salto, raio, chão em chamas, congelar, veneno, chuva de flechas, cura e fúria. Um golpe sorteado a cada 7 s (5 s em fúria) |
| Lacaio (chefe) | Criatura chamada em "Chamar reforços" |
| Onde aparece | Masmorras de portal, Torre Infinita, emboscadas do Nether. Sem nenhum marcado, só aparece invocada |
| Peso / chance | Chefe: peso no sorteio entre os chefes do lugar e do rank. Monstro: chance (%) de tomar a vaga de cada monstro comum criado |
| XP e moedas a mais | Para cada jogador a até 32 blocos quando a criatura morre; soma ao que o lugar já dá |
| Item especial | Item (ou `codex:<código>` de uma arma do Códex de Armas), com chance e quantidade; vai para quem deu o golpe final |
| Aparência | Skin 64×64 no formato de jogador, desenhada no painel (mesmo editor das skins de NPC) ou importada em PNG (64×64 ou 64×32). Vazio = aparência da criatura base |
| Tamanho do desenho | Só com skin própria; muda o desenho, não a área de acerto |
| Contorno brilhante | Efeito de brilho permanente |

## Como entra no jogo

- **Chefes:** `Chefes.criar(..., local)` sorteia primeiro entre os chefes do Códex para o lugar e o rank (`Criaturas.sortearChefe`). Se não houver nenhum, usa a lista de fábrica (`Chefes.DEFS`). Lugares: último salão das masmorras de portal, andares 10, 30, 50, 70 e 90 da Torre, campeão das emboscadas do Nether, Guardião do Dragão na ilha do End (lugar `fim`, que também sorteia os lacaios do dragão). Os andares 20, 40, 60 e 80 da Torre continuam com os chefes épicos dos mods e o 100 com o Senhor da Torre.
- **Monstros:** a cada monstro comum criado nas câmaras de portal, nos andares comuns da Torre e nas hordas do Nether, `Criaturas.sortearMonstro` pode trocá-lo por um do Códex. O monstro recebe o nível do lugar e depois os multiplicadores.
- **Marcação:** a criatura leva a tag `frx_criatura` e o código em `frx_criatura` (dados persistentes).
- **Skin:** as skins vão para o mod pela mesma rota das skins de NPC, com a chave `papel:bicho_<código>`. Ao entrar no campo de visão, o servidor manda `MarcaNpc(id, "bicho_<código>", "x<tamanho>")` e o cliente desenha um corpo de jogador com a skin (`RenderRacas`). Não precisa de pacote de recursos.
- **Morte:** recompensas extras, item especial e aviso ao painel, que soma em "Derrotado N×".

## Painel

- Abas Chefes e Monstros, com retrato, golpes, lugares, recompensas e contagem de derrotas.
- **Duplicar** cria uma cópia para variar um cadastro.
- **Invocar perto de mim:** digita-se o nick e a criatura aparece a 4 blocos do jogador, com o nível da zona em que ele está.
- O servidor relê o Códex a cada minuto e logo após cada alteração; as skins chegam aos jogadores em até 30 s.

## Bestiário no site

O site público tem a seção **Bestiário** com as criaturas ativas. Enquanto "Derrotado" está em zero, o cartão mostra só "? ? ?", o tipo, a faixa de rank e onde aparece; depois da primeira derrota entram nome, história, golpes, contagem e a skin (rota `/api/publico/criatura-skin`, que só responde para criaturas já derrotadas). O cartão mostra também **quem derrotou primeiro** e quando (`CriaturaCodex.primeiroNome` e `primeiroEm`, gravados pelo mod na primeira derrota); criaturas vencidas antes desse registro aparecem como "sem registro".

## Banco

Tabela `CriaturaCodex` (migração `20261117090000_codex_criaturas`). O código tem até 30 caracteres e não muda depois de criado.

## Comandos de admin

`/frx criaturas lista` · `recarregar` · `invocar <código> <jogador>` (usados pelo painel).

## Limites conhecidos

- Com skin, o corpo desenhado é sempre o de jogador. Bases de duas pernas (zumbi, esqueleto, saqueador, piglin) ficam coerentes; em bases grandes ou de quatro patas a área de acerto não bate com o desenho.
- Chefes do mundo aberto, procurados, chefe mundial e chefes de mods não passam pelo Códex.
