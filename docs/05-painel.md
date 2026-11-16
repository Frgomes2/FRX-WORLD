# 05 — Painel administrativo

[← Índice](README.md)

O painel abre sozinho pelo atalho **FRX-WORLD**, em `http://localhost:3000`. Para outros admins, fica em `https://craft.frgomes.com.br` (veja [08-jogar-com-amigos.md](08-jogar-com-amigos.md#painel-para-os-outros-admins-cloudflare-tunnel)). O primeiro admin vem do `.env`; os outros são cadastrados em **Administradores**.

## Menus

| Menu | Rota | O que faz |
|---|---|---|
| Visão geral | `/visao` | Status do servidor, jogadores online, mapa do mundo com os reinos |
| Convites | `/convites` | Gera e controla convites de acesso · **Abertura do primeiro login** (liga/desliga, capital, falas, música, volume e se a música para ao nascer) |
| Jogadores | `/jogadores` | Lista e ficha de cada jogador: nível, classe, moedas, reputação, ações de admin |
| Servidor | `/servidor` | Liga/desliga, console e logs |
| Progressão | `/progressao` | Curva de XP, zonas, **monstros com rank** (chances de Elite/Campeão), config geral |
| Missões | `/missoes` | Quadro de missões em cards por faixa de nível; editor com seletor de criatura, itens, moedas, XP sugerido e prévia em pergaminho |
| Economia | `/economia` | Lojas de cada reino (com aviso de arbitragem e ordenação) e ranking dos jogadores mais ricos |
| Reinos | `/reinos` | Mapa com foco no reino, painel do rei, coordenadas, edição de reputação e matriz de reputação (heatmap) |
| NPCs e skins | `/npcs` | Skins dos NPCs por reino e raça; cards próprios para o Mestre de Obras e para as visitas raras (Ronaldinho e Ladrão) |
| Dungeons | `/dungeons` | Anéis de distância (com aviso de buracos e sobreposição), cards das camadas; editor com abas por camada, criaturas, loot com chance e medidor de dificuldade |
| Masmorras | `/masmorras` | Masmorras dos reinos, com **nível fixo** opcional (campo `nivel`; 0 = pela distância) |
| Chefes de mods | `/externos` | Chefes e estruturas de outros mods |
| Eventos de estrada | `/eventos` | Eventos aleatórios nas estradas |
| Habilidades | `/habilidades` | Habilidades das classes. O campo **Efeito** aceita um efeito de poção ou uma mecânica do mod (`frx:puxar`, `frx:zona`...), com a lista sugerida no próprio campo |
| Forja | `/forja` | Raridades, refino e custos |
| Administradores | `/admins` | Admins do painel: adicionar, remover, trocar a própria senha |

Ainda não implementados ("Em breve"): Conteúdo, Mobs e bosses, Códex de Armas, Estados, Lei e crimes, Rituais, Eventos globais, Versões do pack.

## Detalhes úteis

- **Missões por rank**: o editor de missão tem o campo **Rank da Guilda** (`rankMin`, 0 = F … 6 = S). O bloco **Missões urgentes** da tela de Missões guarda a seção `urgentes` da configuração: `ativo`, `intervaloMin`, `duracaoMin` e `multiplicador`.
- **Música da abertura**: seção `abertura` da configuração, campos `musica` (id de som; `frxworld:abertura` é o arquivo `abertura.ogg` do mod; vazio = sem música), `volume` (0 a 2) e `pararAoNascer`. A lista de músicas da tela fica em `lib/musicas.ts`.
- **Ficha sem convite**: `POST /api/servidor/acesso` aceita `registrar: true`. Com o convite desligado, o mod chama assim no login e a ficha do jogador é criada na hora.
- **Console pelo agente**: `POST /api/servidor/comando` (`{comando}`) manda uma linha ao console do Minecraft. Só aceita chamadas do próprio PC; o que chega pelo túnel da Cloudflare é recusado.

- **Avaliação do guardião das dungeons** (`dungeons/comum.tsx`): o jogador de nível N tem cerca de `20 + 2N` de vida e bate cerca de `7 + 0,8N`. A razão entre os golpes para matar o guardião e (golpes para morrer × 3) dá a faixa: < 0,6 **Fácil** · < 1,6 **Equilibrado** · < 3 **Difícil** · acima disso, **Brutal**.
- **Reinos**: os dados vêm do disco do servidor (`posicoes.json` + `<id>/reino.json`), lidos por `lib/reinos-servidor.ts` com cache pelo horário de modificação.
- **Reputação**: o painel edita via `POST /api/painel/jogadores/{uuid}/acao` com `{acao: "reputacao"}`. O painel manda o comando ao servidor.
- **Monstros com rank** ficam na seção `zonas` da configuração:
  - `eliteChance` (5%) e `eliteNivelMin` (3);
  - `campeaoChance` (0,5%) e `campeaoNivelMin` (10).

## Rotas de API

### `/api/painel/*` (usadas pelas telas, exigem login)

`entidades`, `item-icone`, `itens`, `jogadores/[uuid]` (+ `acao`), `mapa-tile`, `monitor`, `npc-skins` (+ png), `raca-skin`, `servidor` (+ `logs`), `skin`, `textura`, `visao`.

### `/api/servidor/*` (usadas pelo mod, exigem `x-servidor-token`)

`acesso`, `config`, `convite`, `desligar`, `dungeons`, `entidades`, `eventos`, `externos`, `habilidades`, `itens`, `jogador-detalhe`, `ligar`, `lojas`, `masmorras`, `missoes`, `npc-skins`, `progresso`, `status`.

## Ideias pendentes

- Mostrar a raça na ficha do jogador.
- Editor das faixas de nível por dimensão (`zonas.dimensoes`).
- Redesenho das telas Masmorras e Chefes de mods.
