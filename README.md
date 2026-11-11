# FRX-WORLD

Modpack MMORPG de fantasia medieval para Minecraft Java 1.20.1 (Forge), com painel web de administração.
📚 **Documentação completa: [docs/README.md](docs/README.md)** (índice principal com arquitetura, operação, comandos, painel e todos os sistemas de jogo).
O documento de design original está em `docs/FRX-WORLD_Documento_de_Design.pdf`.

## Estrutura

| Pasta | O que é |
|---|---|
| `core-mod/` | Core mod em Java (Forge 1.20.1 / 47.4.10): raças, classes, reinos, dungeons, economia... |
| `server/` | Servidor Forge local (scripts de instalação e início, configs iniciais). |
| `pack/` | Modpack no formato packwiz (lista de mods e configs que os jogadores baixam). |
| `painel/` | Painel admin em Next.js + TypeScript + Tailwind, Prisma + PostgreSQL, Auth.js. |
| `docs/` | Documentação em Markdown (comece por `docs/README.md`) e documento de design. |
| `docker-compose.yml` | PostgreSQL do painel. |

## Pré-requisitos (Windows)

1. **Java 17** (Temurin): https://adoptium.net/temurin/releases/?version=17. Pode ter outras versões instaladas: os scripts (`java17.bat`) acham o JDK 17 sozinhos.
2. **Node.js 20 ou mais novo**: https://nodejs.org
3. **PostgreSQL**: Docker Desktop (usa o `docker-compose.yml`) **ou** PostgreSQL instalado direto no Windows.
4. **Prism Launcher**: https://prismlauncher.org (para jogar e testar o pack).
5. **packwiz.exe**: baixe o build de Windows em https://nightly.link/packwiz/packwiz/workflows/go/main e coloque em `pack/`.

## Atalhos (uso do dia a dia)

Rode **uma vez** `atalhos\criar-atalhos.bat`. Ele cria na Área de Trabalho:

- **FRX-WORLD**: abre o Docker, sobe o banco, publica o pack, liga o painel e abre http://localhost:3000/servidor.
  Daí é só clicar em **Iniciar** na aba Servidor.
- **Desligar FRX-WORLD**: para o servidor salvando o mundo, fecha painel e pack e para o banco.

## Fase 0 — passo a passo

### 1. Banco e painel
```bat
docker compose up -d
cd painel
copy .env.example .env
```
Edite o `.env`: troque `AUTH_SECRET` (gere com `npx auth secret`), `ADMIN_SENHA` e `SERVIDOR_TOKEN`.
```bat
npm install
npm run db:migrate -- --name inicial
npm run db:seed
npm run dev
```
Abra http://localhost:3000, entre com `ADMIN_EMAIL` / `ADMIN_SENHA` e gere um convite em **Convites**.

### 2. Core mod
```bat
cd core-mod
compilar.bat
```
A primeira compilação demora (baixa o Minecraft e o Forge). O jar sai em `core-mod\build\libs\`.

### 3. Servidor
1. Coloque em `server\config\frxworld-common.toml` o **mesmo** `SERVIDOR_TOKEN` do `.env` do painel.
2. Rode `server\instalar-servidor.bat` (baixa e instala o Forge, pergunta sobre o EULA).
3. Rode `server\iniciar.bat` (copia o core mod compilado para `mods\` e liga o servidor).

### 4. Entrar no jogo
1. No Prism: nova instância **Minecraft 1.20.1** com **Forge 47.4.10**.
2. Conecte em `localhost`.
3. Na primeira entrada você fica parado na chegada: digite `/convite FRX-XXXX-XXXX` com o código gerado no painel.
4. Código aceito = conta liberada para sempre. O jogador aparece em **Jogadores** no painel.

### 5. Mods de terceiros (pack)
```bat
cd pack
adicionar-mods.bat
packwiz serve
```
Com o `packwiz serve` rodando, o pack fica em http://localhost:8080/pack.toml.
Para instalar no servidor e nos clientes use o packwiz-installer-bootstrap
(https://github.com/packwiz/packwiz-installer-bootstrap/releases):

- **Servidor** (dentro de `server\`): `java -jar packwiz-installer-bootstrap.jar -g -s server http://localhost:8080/pack.toml`
- **Prism** (instância → Editar → Configurações → Comandos personalizados → Comando pré-lançamento):
  `"$INST_JAVA" -jar packwiz-installer-bootstrap.jar http://localhost:8080/pack.toml`
  (coloque o `packwiz-installer-bootstrap.jar` dentro da pasta `.minecraft` da instância)

> Amigos de fora da sua rede: use playit.gg ou Tailscale (por causa de CGNAT) tanto para o servidor (porta 25565) quanto para o pack (porta 8080).

## Como o acesso por convite funciona

```
Jogador entra ──► core mod pergunta ao painel: POST /api/servidor/acesso
                    ├─ liberado → "Bem-vindo ao FRX-WORLD"
                    └─ não liberado → fica parado (sem interagir, sem dano) e recebe instrução
/convite CÓDIGO ──► POST /api/servidor/convite → código válido e não usado → conta liberada
```
Sem convite dentro de `segundosParaConvite` (padrão 180 s), o jogador é desconectado.
Se o painel estiver fora do ar, o servidor desconecta com aviso. Para testar sem o painel,
use `exigirConvite = false` no `frxworld-common.toml`.

## Próximas fases
Ver seção 17 do documento de design. Fase 1: nível, XP, HUD, ranks e punição por morte básica.
