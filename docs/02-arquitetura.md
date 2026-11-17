# 02 — Arquitetura

[← Índice](README.md)

## Repositório (`C:\xampp\htdocs\FRX-WORLD`)

| Pasta / arquivo | Conteúdo |
|---|---|
| `core-mod/` | Código-fonte do mod Forge `frxworld` (Gradle). `gradle.properties` guarda a `mod_version` |
| `painel/` | Painel administrativo (Next.js 16 + Prisma 7 + PostgreSQL) |
| `pack/` | Pack packwiz (`pack.toml`, `index.toml`, `mods/`), `packwiz.exe` e os `.bat` de adicionar mods |
| `server/` | Servidor Forge: `config/`, `frxworld/reinos/`, `iniciar.bat`, `atualizar-mods.bat` |
| `cliente/` | Instância de jogo local, atualizada pelo pack |
| `reinos/` | Fontes das construções dos reinos |
| `tools/` | Utilitários, como `gerar_ftbquests.py` |
| `atalhos/` | `criar-atalhos.bat`, que cria os atalhos da área de trabalho |
| `docs/` | Esta documentação |
| `logs/` | `compilacao.log`, `pack.log` e outros |
| `iniciar-frx.bat` / `desligar-frx.bat` | Ligam e desligam tudo (usados pelos atalhos) |
| `docker-compose.yml` | Banco PostgreSQL 16 do painel (porta 5433 no host) |
| `java17.bat` | Aponta o Java 17 usado pelo servidor e pela compilação |

## Core mod — pacotes

Pacote base: `br.com.frxcode.frxworld`. A entrada é `FrxWorld.java`, que registra os sistemas no event bus do Forge. As opções de servidor ficam em `Config.java`.

| Pacote | Classes principais | Responsabilidade |
|---|---|---|
| `acesso` | ControleAcesso, PainelApi, DetalheJogador, StatusPainel, ListaEntidades | Convites, comunicação com o painel, ficha do jogador |
| `armas` | Armas, Forja, Raridade, RaridadeLoot | Raridades, refino +N, Reforja Lendária, atributos de itens |
| `atributos` | Apoth | Ponte por reflexão com o Apothic Attributes |
| `blocos` | Blocos, BlocoQuadro | Blocos do mod (quadro de missões) |
| `cliente` | Telas, HUDs, camadas de render, teclas | Tudo que só roda no cliente |
| `dungeon` | Dungeons, DungeonDef, Construir, BlocoNucleo, BlocoPortao, Externos | Dungeons em camadas e estruturas externas |
| `efeitos` | Efeitos, EfeitoFerido | Efeitos de poção próprios |
| `aventura` | Historia, Temporadas, Procurados, Torre, Ceu, Tesouros, Runas, Chefes, Fenda, Inferno | História principal, placar mensal, procurados, Torre Infinita, eventos do céu, mapas do tesouro, runas, chefões com mecânicas, dungeon de portal e emboscadas do Nether |
| `eventos` | ChefeMundial, EventosEstrada, Craque, Ladrao | Chefe mundial, eventos de estrada e as visitas raras (Ronaldinho e ladrão) |
| `grupos` | Grupos | Grupos (party) e chat de grupo |
| `habilidades` | Habilidades, HabilidadeDef, Especiais, Talentos, Despertar, Sombras, ItemTomo | Classes, habilidades, talentos e poderes especiais |
| `missoes` | Missoes, MissaoDef | Missões do quadro (vindas do painel) |
| `moedas` | Moedas, Banco, ItemMoeda, TrocasMoeda | Moedas físicas, saldo e banco |
| `npc` | SkinsNpc | Skins dos NPCs |
| `progressao` | Progressao, DadosJogador, ConfigJogo, Zonas, Ranks, Marcos, Conquistas, Guilda, ClassesEquip, AjustesAdmin | Nível, XP, zonas (faixa, dureza e rank mínimo por dimensão), ranks de monstros, marcos, Guilda, classes de equipamento por rank e ajustes de admin com jogador offline |
| `racas` | DefRacas, RacasJogador | Sistema de raças do jogador |
| `rede` | Rede + pacotes Sync*/Acao* | Pacotes cliente↔servidor |
| `reinos` | Reinos, Reino, NpcsReino, Populacao, Racas, Reputacao, Campanhas, Lojas, Masmorras, Invasoes, WaystonesReino, Decoracoes... | Os 8 reinos e tudo que vive neles |
| `taverna` | Tavernas | Contratos da taverna |

### Dados do jogador

- Capability `DadosJogador`, acessada por `Progressao.dados(p)`: nível, XP, classe e o resto da progressão.
- NBT persistente do jogador (`PERSISTED_NBT_TAG`) para dados que sobrevivem à morte, como `frx_sombras_atalhos`.
- Os modificadores de atributo são transitórios, com UUIDs fixos, e são reaplicados ao entrar ou renascer.

### Rede

- Canal único `Rede.CANAL` (SimpleChannel), versão do protocolo **"10"**. Ao adicionar ou mudar um pacote, suba esse número.
- Convenção de nomes:
  - `Sync*`: do servidor para o cliente (estado).
  - `Acao*`: do cliente para o servidor (pedido do jogador).

## Painel

O mesmo aplicativo serve duas coisas: o **site público** na raiz (`src/app/page.tsx`, `src/components/publico/`, `src/lib/publico.ts`, `livros.ts`, `notas.ts`, rotas `/api/publico/*`) e o **painel admin** em `/admin` (`src/app/admin/`, com login). Detalhes em [05-painel](05-painel.md).

- **Stack:** Next.js 16 (App Router, server actions), Prisma 7, PostgreSQL 16 (Docker), autenticação com admin único.
- **Variáveis do `.env`:** `DATABASE_URL`, `AUTH_SECRET`, `AUTH_TRUST_HOST`, `ADMIN_EMAIL`, `ADMIN_SENHA`, `ADMIN_NOME`, `SERVIDOR_TOKEN` e `SERVIDOR_DIR` (a pasta do servidor, lida pelo painel).
- **Modelos Prisma:** Admin, Convite, Jogador, Configuracao, Missao, Dungeon, ChefeExterno, EstruturaExterna, EventoEstrada, Habilidade, ItemLoja, Masmorra, NpcSkin.

### Comunicação mod ↔ painel

```
Mod (PainelApi)  ── POST/GET  /api/servidor/*  (header x-servidor-token) ──▶  Painel
Painel (servidorMc.comando) ── comandos de console ──▶ Servidor Minecraft
Painel ── lê arquivos em SERVIDOR_DIR (reinos, logs, config) ──▶ disco
Visitante ── GET / e /api/publico/* (sem login) ──▶ Painel (site público)
```

- `painel.url` e `painel.token` ficam no config do mod. O token tem de ser igual a `SERVIDOR_TOKEN`.
- O mod baixa a configuração (missões, lojas, dungeons, habilidades, progressão...) das rotas `/api/servidor/*`. `/frx recarregar` busca tudo de novo.

Rotas em [05-painel.md](05-painel.md).

## Arquivos do mundo gerados pelo mod

| Arquivo | Conteúdo |
|---|---|
| `server/frxworld/reinos/posicoes.json` | Posição de cada reino (o painel lê para o mapa) |
| `server/frxworld/reinos/<id>/reino.json` | Dados de cada reino |
| `<mundo>/frx_waystones.txt` | Registro das waystones já colocadas |
| `server/config/ftbquests/quests/` | Capítulos do FTB Quests, gerados por `tools/gerar_ftbquests.py` |
| `data/frxworld/advancements/frx/*.json` (no jar) | Os 48 marcos ocultos |
