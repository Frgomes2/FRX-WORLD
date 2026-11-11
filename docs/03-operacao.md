# 03 — Operação

[← Índice](README.md)

## Ligar e desligar (o dia a dia)

No dia a dia só se usam os **dois atalhos da área de trabalho**. Eles são criados uma vez por `atalhos\criar-atalhos.bat`.

| Atalho | Script | O que faz |
|---|---|---|
| **FRX-WORLD** | `iniciar-frx.bat` | Liga tudo e abre o painel |
| **Desligar FRX-WORLD** | `desligar-frx.bat` | Salva o mundo e desliga tudo |

### O que o `iniciar-frx.bat` faz, em ordem

1. **Banco:** abre o Docker Desktop se precisar e roda `docker compose up -d`.
2. **Core mod (1b):** se algum arquivo em `core-mod/src`, `gradle.properties` ou `build.gradle` for mais novo que o jar, compila sozinho com `core-mod\compilar.bat`. O log vai para `logs\compilacao.log` e, se falhar, as últimas 25 linhas aparecem na tela.
3. **Pack (2a/2):**
   - Se existir `pack\pendentes.txt`, adiciona os mods listados ali. Cada linha tem o formato `mr|cf slug`, e o arquivo vai para `logs\pendentes-aplicados.txt`.
   - Depois roda `packwiz refresh` + `packwiz serve` em `http://localhost:8080/pack.toml` e atualiza os mods do servidor e do cliente.
4. **Painel (3):** `prisma migrate deploy`, `prisma generate`, compila o painel se o código mudou (`logs\painel-build.log`) e sobe em modo produção (`next start`).
5. **Túnel (3a):** se o playit.gg estiver instalado, liga o túnel para os amigos. Veja [08-jogar-com-amigos.md](08-jogar-com-amigos.md).
6. **Servidor (3b):** liga o servidor Minecraft.
7. **Navegador (4):** abre o painel.

### O que o `desligar-frx.bat` faz

1. Para o servidor Minecraft salvando o mundo. Usa o `SERVIDOR_TOKEN` do `painel\.env`.
2. **Backup:** compacta o mundo em `backups\mundo-AAAAMMDD-HHMM.zip` e salva o banco do painel em `backups\painel-AAAAMMDD-HHMM.sql`. Guarda só os 7 mais recentes de cada.
3. Fecha o painel, o pack e o túnel do playit.
4. Para o banco.

### Restaurar um backup

- **Mundo:** com tudo desligado, troque a pasta `server\FRX-WORLD` pelo conteúdo do zip escolhido. O zip já contém a pasta `FRX-WORLD`.
- **Painel:** com o banco ligado (`docker compose up -d`), rode:
  `docker compose exec -T banco psql -U frx -d frxworld < backups\painel-AAAAMMDD-HHMM.sql`
  Para uma restauração limpa, apague e recrie o banco antes.

> O EULA do Minecraft (`server\eula.txt`) é aceito pelo próprio dono do servidor, nunca por script.

## Compilar o mod

- Automático: o atalho de ligar compila sempre que o código mudou.
- Manual: `core-mod\compilar.bat`. O jar sai em `core-mod\build\libs\frxworld-<versao>.jar` e é copiado para `pack\mods\` (substituindo o anterior), de onde servidor e cliente o recebem.
- A versão fica em `core-mod\gradle.properties` (`mod_version`). Suba a versão a cada entrega e registre no [changelog](07-changelog.md).
- Pacotes de rede mudaram? Suba também o `PROTOCOLO` em `rede/Rede.java`.

## Pack (packwiz)

| Script | Uso |
|---|---|
| `pack\servir.bat` | `packwiz refresh` + `packwiz serve` (o atalho já faz isso) |
| `pack\adicionar-mods.bat` | Mods da base do pack |
| `pack\adicionar-mods-fase2.bat` | Mods da Fase 2 (FTB Quests, Apothic Attributes, dimensões). Veja [06-mods-externos.md](06-mods-externos.md) |
| `server\atualizar-mods.bat` | Baixa no servidor os mods do pack (precisa do pack no ar) |

Para adicionar um mod, rode no `pack\`:

- `packwiz mr add <slug>` (Modrinth) ou `packwiz cf add <slug>` (CurseForge);
- depois `packwiz refresh`.

O `packwiz.exe` só roda no Windows, e o Modrinth/CurseForge precisa estar acessível pela rede do PC.

## FTB Quests — gerar os capítulos

Os capítulos da campanha são gerados por script, não editados à mão:

```
python tools\gerar_ftbquests.py
```

O script escreve em `server\config\ftbquests\quests\` os arquivos `data.snbt`, `chapter_groups.snbt` e `chapters/*.snbt`. São 11 capítulos e 78 quests. Para mudar a campanha, edite o script e gere de novo. Detalhes em [sistemas/quests.md](sistemas/quests.md).

## Recarregar sem reiniciar

- `/frx recarregar` busca de novo no painel as missões, lojas, dungeons, habilidades e a configuração de progressão.
- Configurações em `server\config\frxworld-common.toml` (URL e token do painel, convite, curva de XP) exigem reinício.

## Testes do painel (desenvolvimento)

No ambiente de desenvolvimento, o painel é validado com:

- `npx tsc --noEmit`;
- `npx eslint .`;
- `npx next build`.

Depois sobe com `next start` apontando `SERVIDOR_DIR` para uma cópia do servidor. O código Java de cliente só compila no PC (pelo atalho).

## Mundo novo (definitivo)

Antes de abrir o servidor para os amigos num mundo novo, gere o mapa antes com o Chunky. Gerar na hora em que alguém chega trava o servidor ("Can't keep up"), por causa dos mods de estruturas, dos reinos e das casinhas.

1. Desligue o servidor, apague a pasta `server\FRX-WORLD` e ligue pelo atalho.
2. No console do painel (aba Servidor), digite:
   ```
   chunky world minecraft:overworld
   chunky center 0 0
   chunky radius 3000
   chunky start
   ```
   Isso cobre todas as capitais. Deixe rodando sem jogadores, por algumas horas.
3. Acompanhe com `chunky progress`. Se o servidor for desligado no meio, o Chunky continua sozinho na próxima vez (`continueOnRestart: true` em `server\config\chunky\config.json`).
4. Depois, as dimensões: o mesmo com `chunky world minecraft:the_nether` (raio 1000), `twilightforest:twilight_forest` (1000) etc.

Para um mundo de teste, basta `chunky radius 500`, que cobre Tempest e a floresta em volta.
