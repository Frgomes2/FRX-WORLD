# 05 — Painel administrativo

[← Índice](README.md)

O painel abre sozinho pelo atalho **FRX-WORLD**, em `http://localhost:3000`. Para outros admins, fica em `https://craft.frgomes.com.br` (veja [08-jogar-com-amigos.md](08-jogar-com-amigos.md#painel-para-os-outros-admins-cloudflare-tunnel)). O primeiro admin vem do `.env`; os outros são cadastrados em **Administradores**.

## Site público e endereço do admin

Desde 16/11/2026 o endereço se divide em dois:

- **`/` é o site público** (sem login), feito para os jogadores: status ligado/desligado, quem está jogando, acontecimentos, rankings (mais fortes, começando agora, mais ricos, conquistas, mortes), ranks da Guilda, classes, placar da Torre e da temporada, torneio de pesca da semana, recordes das corridas, duelos X1 (quem mais venceu e últimas lutas), mapa explorado, sistemas do servidor, lista de mods com link e como entrar. Atualiza sozinho a cada 15 s.
- **`/admin` é o painel** (tudo o que antes ficava na raiz: `/admin/servidor`, `/admin/missoes`...). O login continua em `/login` e leva para `/admin`.

Arquivos: `src/app/page.tsx`, `src/components/publico/` (`site.tsx`, `mapa.tsx`, `jogadores.tsx`), `src/lib/publico.ts`, `src/lib/notas.ts` (notas de atualização, em linguagem de jogador) e `src/app/api/publico/` (`dados`, `mapa-tile`, `jogador`, `skin`), as únicas rotas de API sem login.

A seção **Biblioteca** traz livros folheáveis sobre as regras do servidor (`src/lib/livros.ts` + `components/publico/biblioteca.tsx`): ranks, missões e habilidades saem do banco, o resto é texto que deve acompanhar as mudanças de regra.

Clicar num jogador abre a ficha pública: skin, nível, raça, Guilda, Torre, temporada, habilidade única e habilidades aprendidas (vêm do campo `detalhe` do jogador). Não mostra inventário, baú de ender nem posição.

**O que o site público nunca mostra** (regra em `lib/publico.ts`): e-mail, UUID, IP, endereço do servidor, conversa do chat e a posição dos jogadores. O mapa mostra a superfície, o ponto de partida, a Torre e a capital de cada reino (desde 18/11; `PUBLICO_MAPA_REINOS=0` no `.env` esconde os reinos).

## Menus

| Menu | Rota | O que faz |
|---|---|---|
| Visão geral | `/admin` | Status do servidor, jogadores online, mapa do mundo com os reinos |
| Convites | `/admin/convites` | Gera e controla convites de acesso · **Abertura do primeiro login** (liga/desliga, capital, falas, música, volume e se a música para ao nascer) |
| Jogadores | `/admin/jogadores` | Lista e ficha de cada jogador: nível, classe, moedas, reputação, ações de admin |
| Servidor | `/admin/servidor` | Liga/desliga, console e logs |
| Progressão | `/admin/progressao` | Curva de XP, zonas, **monstros com rank** (chances de Elite/Campeão), config geral |
| Missões | `/admin/missoes` | Quadro de missões em cards por faixa de nível; editor com seletor de criatura, itens, moedas, XP sugerido e prévia em pergaminho |
| Economia | `/admin/economia` | Lojas de cada reino (com aviso de arbitragem e ordenação), ranking dos jogadores mais ricos, comprador e rendimento do cofre (taxas, teto, dias offline e simulação) preços do sábio para extrair encantamento e moedas por rank da recompensa dos portais |
| Dinheiro dos jogadores | `/admin/dinheiro` | Bolsa, banco e total de cada jogador; o botão Editar define ouro, prata e cobre da bolsa (só com o jogador online) e do banco (também offline) |
| Reinos | `/admin/reinos` | Mapa com foco no reino, painel do rei, coordenadas, edição de reputação e matriz de reputação (heatmap) |
| NPCs e skins | `/admin/npcs` | Skins dos NPCs por reino e raça; cards próprios para o Mestre de Obras e para as visitas raras (Ronaldinho, Ladrão e Papai Noel) |
| Dungeons | `/admin/dungeons` | Anéis de distância (com aviso de buracos e sobreposição), cards das camadas; editor com abas por camada, criaturas, loot com chance e medidor de dificuldade |
| Masmorras | `/admin/masmorras` | Masmorras dos reinos, com **nível fixo** opcional (campo `nivel`; 0 = pela distância) |
| Chefes de mods | `/admin/externos` | Chefes e estruturas de outros mods |
| Eventos de estrada | `/admin/eventos` | Eventos aleatórios nas estradas |
| Habilidades | `/admin/habilidades` | Habilidades das classes. O campo **Efeito** aceita um efeito de poção ou uma mecânica do mod (`frx:puxar`, `frx:zona`...), com a lista sugerida no próprio campo |
| Forja | `/admin/forja` | Raridades, refino e custos |
| Códex de Armas | `/admin/codex` | Armas únicas: item base, rank, raridade, dano, total no mundo, venda no ferreiro, recompensa de missão (`codex:<código>`), evolução com o dono e aparência (editor de pixels ou PNG importado) |
| Códex de Criaturas | `/admin/criaturas` | Chefes e monstros próprios: criatura base, ranks, vida/dano/velocidade, golpes, lacaio, lugares, peso ou chance, XP/moedas/item ao morrer, skin 64×64 desenhada ou importada, tamanho, botão de invocar. Ver [sistemas/codex-criaturas.md](sistemas/codex-criaturas.md) |
| Discos de música | `/admin/discos` | Discos vendidos pelo Mercador de Discos: arquivo .ogg, artista, estilo, preço, rank, unidades no mundo (0 = sem limite) e a aparência (cor, PNG ou desenho) |
| Administradores | `/admin/admins` | Admins do painel: adicionar, remover, trocar a própria senha |

Ainda não implementados ("Em breve"): Conteúdo, Estados, Lei e crimes, Rituais, Eventos globais, Versões do pack.

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

## Personagens na ficha do jogador

**Crônicas (`src/lib/cronicas.ts`)**: o painel guarda os acontecimentos (nível, rank, conquistas, Torre, bestiário, Mural) na tabela `Cronica` e uma foto diária do ranking em `FotoRanking`. Alimentam o quadro Acontecendo, a seção Crônicas do FRX, o histórico da ficha pública e a variação do ranking.

**Conta do jogador (`/conta`)**: página do site onde o jogador entra com nick e senha (criada no primeiro acesso com o código que o jogo mostra na tecla F9) e consulta o próprio inventário, moedas, terrenos e personagens. Não faz parte do admin e não usa o login do admin. Código em `src/lib/conta-site.ts`, `src/app/conta/` e `src/app/api/publico/conta/`.

A lista de Jogadores, o Dinheiro e os rankings da Visão geral mostram uma linha por personagem (`src/lib/personagens.ts`); o personagem fora de uso aparece com os números da última vez em que foi jogado. A ficha (`/admin/jogadores/<uuid>`) mostra os personagens da conta. Clicar num personagem fora de uso abre o retrato guardado dele (`?vaga=N`), sem atualização automática e sem ações: dar item, mudar nível e editar a bolsa só valem para o personagem em uso. Detalhes em [sistemas/personagens.md](sistemas/personagens.md).

## Rotas de API

### `/api/painel/*` (usadas pelas telas, exigem login)

`entidades`, `item-icone`, `itens`, `jogadores/[uuid]` (+ `acao`), `mapa-tile`, `monitor`, `npc-skins` (+ png), `raca-skin`, `servidor` (+ `logs`), `skin`, `textura`, `visao`.

### `/api/servidor/*` (usadas pelo mod, exigem `x-servidor-token`)

`acesso`, `config`, `convite`, `desligar`, `dungeons`, `entidades`, `eventos`, `externos`, `habilidades`, `itens`, `codex`, `criaturas`, `discos`, `personagens`, `jogador-detalhe`, `ligar`, `lojas`, `masmorras`, `missoes`, `npc-skins`, `progresso`, `status`.

## Ideias pendentes

- Mostrar a raça na ficha do jogador.
- Editor das faixas de nível por dimensão (`zonas.dimensoes`).
- Redesenho das telas Masmorras e Chefes de mods.

**Memória e processador no topo do site**: os quadros do topo mostram o uso de memória do servidor do jogo (o que o mod informa a cada 10 s: usada sobre o máximo da JVM) e o uso do processador da máquina (`usoCpu()` em `src/lib/status-servidor.ts`, medido pelo próprio painel entre uma consulta e a seguinte). Só aparecem com o servidor ligado.

**Teste de conexão (`/admin/servidor`, aba Teste de conexão)**: para quando reclamarem de lag. Mostra o ping de cada jogador online, com média, melhor, pior e gráfico dos últimos 30 minutos, e a saúde do servidor. "Rodar teste" mede por 1 minuto e dá o parecer: se o TPS caiu, o problema é o servidor; se o servidor está leve e todos têm ping alto, é o túnel do playit ou a internet do PC; se só alguns têm ping alto, é a internet deles. O ping vem do próprio jogo (o mod manda a cada 10 s), sem precisar de comando.

## Expedições (site público, 3.0.1)

- `/expedicoes` e `/expedicoes/<número>`: Terras Inexploradas (detalhes em [sistemas/expedicao.md](sistemas/expedicao.md)). Link "Expedições" no menu "Mais" do site.
- APIs: `POST /api/servidor/expedicoes` (servidor, com token) e `GET /api/publico/expedicoes` (atual, histórico, números e o relógio do servidor).
- Pesos do MVP: Configuracao `expedicao_pesos`.

