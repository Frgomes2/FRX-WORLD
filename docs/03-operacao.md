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

### Reinício automático de madrugada (3.0.14)

O próprio mod (`ReinicioDiario`) escreve `reiniciar` em `controle/pedido.txt` às **05:00** (horário de Brasília), só com o servidor vazio, ligado há pelo menos 3 h, uma vez por dia (tenta até as 07:00). O agente faz o resto: salva, faz backup e liga de novo, com as atualizações prontas. Hora em `manutencao.reinicioDiario` no `server/config/frxworld-common.toml`; vazio desliga.

### O que o `desligar-frx.bat` faz

1. Para o servidor Minecraft salvando o mundo. Usa o `SERVIDOR_TOKEN` do `painel\.env`. Se a porta 25565 continuar aberta 30 s depois (Minecraft que ficou sem painel), encerra o processo dela à força.
2. **Backup:** compacta o mundo em `backups\mundo-AAAAMMDD-HHMM.zip` e salva o banco do painel em `backups\painel-AAAAMMDD-HHMM.sql`. A limpeza (`controle\limpar-backups.ps1`) guarda em camadas: os 7 mais recentes, o último de cada dia dos últimos 7 dias e o último de cada semana das últimas 5 semanas. O backup só acontece ao desligar ou reiniciar; não há backup periódico com o servidor ligado nem cópia fora do PC.
3. Fecha o painel, o pack e o túnel do playit.
4. Para o banco.

### Restaurar um backup

- **Mundo:** com tudo desligado, troque a pasta `server\FRX-WORLD` pelo conteúdo do zip escolhido. O zip já contém a pasta `FRX-WORLD`.
- **Painel:** com o banco ligado (`docker compose up -d`), rode:
  `docker compose exec -T banco psql -U frx -d frxworld < backups\painel-AAAAMMDD-HHMM.sql`
  Para uma restauração limpa, apague e recrie o banco antes.

> O EULA do Minecraft (`server\eula.txt`) é aceito pelo próprio dono do servidor, nunca por script.

## Controle remoto pelo Claude (agente)

O Claude também pode ligar, desligar e reiniciar o servidor sem ninguém clicar nos atalhos.

- **Instalação, uma vez:** `controle\instalar-agente.bat`. Ele cria o atalho "FRX-WORLD agente" na pasta Inicializar do Windows e já põe o agente para rodar.
- O agente (`controle\agente-frx.ps1`) roda escondido e olha `controle\pedido.txt` a cada 3 s. Os pedidos são `ligar`, `desligar` e `reiniciar`.
  - Ele roda os mesmos `.bat` dos atalhos. O `iniciar-frx.bat /auto` funciona sem perguntas, sem pausas e sem abrir o navegador.
- **Estado:** `controle\estado.txt` (ligando, ligado, desligado, erro…). Eventos em `logs\agente-eventos.log`; a saída de cada execução vai para um arquivo próprio em `logs\agente\`.
- **Vigia do servidor:** depois de um `ligar` ou `reiniciar`, o agente confere a porta 25565 a cada 30 s. Se ela ficar fechada por 1 minuto, ele roda o desligar e o ligar sozinho (até 3 vezes em 30 minutos; na quarta queda ele para e avisa no estado).
  - `desligar` desarma a vigia. Parar o servidor pelo console do painel **não** desarma: a vigia religa. Use o atalho Desligar ou o pedido `desligar`.
  - Para pausar sem mexer no servidor, crie `controle\vigia.txt` com a palavra `desligada`.
  - Quando o PC acaba de ligar, a vigia não sobe o servidor sozinha: ela espera um `ligar`.
- **Queda sem aviso:** quando o painel (node) morre, o servidor do jogo morre junto e o site sai do ar; a vigia religa em cerca de 1 minuto. O `iniciar-frx.bat` copia o log anterior para `logs\painel-anterior.log` antes de religar. A causa conhecida é o próprio node travar (aparece `WerFault.exe` na foto da sentinela); o painel sobe com `NODE_OPTIONS=--report-on-fatalerror --report-uncaught-exception`, e o relatório do travamento fica em `logs\node`. O painel ignora Ctrl+C/Ctrl+Break na janela e anota no log quando recebe um.
- **Console:** o pedido `comando <texto>` manda uma linha para o console do Minecraft (várias linhas, vários comandos). Passa pela rota `/api/servidor/comando` do painel, que só aceita chamadas do próprio PC.
- **Atualização:** se o `agente-frx.ps1` mudar, o agente se reinicia sozinho com a versão nova.
- **Para remover:** apague "FRX-WORLD agente" em `shell:startup`.

## Quedas e salvamento automático

- **Sentinela** (`controle\sentinela.ps1`, ligada pelo `iniciar-frx.bat`): se o painel ou o servidor morrem sem que alguém tenha pedido, grava `logs\quedas\queda-*.txt` e pede o reinício ao agente (no máximo 2 em 30 minutos). O `desligar-frx.bat` avisa a sentinela antes de parar (`controle\sentinela.txt`).
- **Retrato antes de desligar** (`controle\registrar-estado.ps1`): a cada desligamento grava `logs\quedas\estado-*.txt` com os processos do FRX, a memória livre e os avisos do Windows da última meia hora.
- **Salvamento do mundo a cada 30 minutos** (`protecao/Salvamento.java`): o salvamento completo do jogo, que trava um tick a cada 5 minutos, passou a rodar de 30 em 30; os jogadores continuam salvos a cada 5 e os chunks, aos poucos, o tempo todo. O log diz `Salvamento automático: a cada 30 minutos` ao ligar; se disser que continua a cada 5, o contorno não funcionou.
- **Medir travadas:** `spark tps`, `spark health --memory` e `spark tickmonitor --threshold-tick 80 --without-gc` pelo console (o vigia escreve no chat dos admins e some no reinício).
- **Cópias manuais** ficam em `backups\` (fora do git). `backups\playerdata-20261118-antes-personagens` é a pasta dos jogadores de antes do sistema de personagens.

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

> **Feito em 13/11/2026:** o mundo de teste foi movido para `backups\mundo-teste-20261113` e o mundo atual já nasceu com os reinos reduzidos, as barracas e os baús. A pré-geração do Overworld (raio 3000) começou no mesmo dia; as outras dimensões ainda faltam.

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

## Arquivos que o mod grava na pasta do mundo (15/11/2026)

Ficam em `server/FRX-WORLD/` e valem só para aquele mundo (mundo novo começa sem eles):

| Arquivo | O que guarda |
|---|---|
| `frx_torre.txt` · `frx_torre_placar.json` | Posição e porta da Torre Infinita · rankings (recorde e abates) |
| `frx_temporada.json` | Placar do mês, prêmios pendentes e títulos de temporada |
| `frx_terrenos.json` | Terrenos comprados, permissões de cada dono e terras do reino marcadas pelo admin |
| `frx_clas.json` | Clãs: membros, cargos, cofre e pedidos |
| `frx_protegidas.txt` · `frx_chegadas.json` | Áreas protegidas e seus refúgios · primeira chegada de cada personagem a cada refúgio |
| `frx_mercado.json` | Anúncios e valores a receber do Leiloeiro |
| `frx_codex.json` · `frx_discos.json` · `frx_escravos.json` | Unidades emitidas de cada arma do Códex · discos vendidos · aldeões comprados e perdidos por conta |
| `frx_personagens/` | Índice e arquivo de cada personagem fora de uso (`<uuid>.json`, `<uuid>-<vaga>.dat`) |
| `frx_banco_pendente.txt` | Saldos de banco definidos pelo painel para quem estava offline |
| `frx_repor.txt` | Moradores e guardas das capitais à espera de voltar |
| `frx_ciclo.txt` | Duração do dia e da noite |
| `frx_ronaldinho.txt` · `frx_ladrao.txt` · `frx_noel.txt` | Chance das visitas raras (por mil, por minuto) |
| `frx_pesca.json` | Torneio de pesca: semana, placar, recordes por espécie e campeões (o site lê) |
| `frx_corridas.json` | Pistas de corrida, pontos, 10 melhores tempos e prêmio diário (o site lê) |

## Conferir a compilação antes de reiniciar

Um erro de compilação no mod não impede o servidor de subir: ele sobe com o jar anterior e nada de novo entra. Depois de cada reinício, confira que `logs/compilacao.log` termina em `BUILD SUCCESSFUL`.

O painel é o processo que mantém o servidor do Minecraft: se o painel cair, o servidor cai junto. Se o site e o jogo sumirem ao mesmo tempo, peça `diagnostico` ao agente e veja se a porta 3000 está em `LISTENING`; se não estiver, `reiniciar` traz os dois de volta.

## Painel e jogo separados (2.0.14)

- O Minecraft roda separado do painel. Se o painel cair, o jogo continua e os jogadores não são desconectados.
- A sentinela percebe que só o painel caiu e pede `ligar`: o `iniciar-frx.bat` sobe só o painel (não mexe nos mods do servidor) e o painel novo **assume** o jogo que já estava rodando. O console da aba Servidor passa a vir do `server/logs/latest.log`, e os comandos vão pelo arquivo `server/frx_comandos.txt`, que o core mod lê a cada meio segundo.
- O `desligar-frx.bat` para o jogo pelo painel; se o painel não estiver de pé, manda `stop` pelo arquivo e espera até 2 min o mundo salvar. Só encerra à força se o servidor não fechar.
- Diagnóstico das quedas do painel: `logs/painel-saida.log` (memória a cada minuto, quem chamou `process.exit`, exceções). Se o painel sumir sem nada registrado ali, ele foi encerrado por outro programa.

### Reiniciar só o painel (2.0.15)

O painel é um programa só: site público, admin e páginas dos jogadores. Para aplicar uma mudança nele sem derrubar o jogo:

- botão **Reiniciar só o painel** na aba Servidor; ou
- dois cliques no `reiniciar-painel.bat`; ou
- o comando `/frx painel reiniciar` (também pelo arquivo `server/frx_comandos.txt`, que funciona com o painel travado).

O .bat confere o código antes (com erro, não mexe em nada), fecha só o painel, compila se precisar, sobe de novo e o painel assume o jogo. O site fica fora por 1 a 3 minutos (mais se compilar); os jogadores continuam no jogo.

## Pré-geração do mapa (Chunky)

- O Chunky (só no servidor) gera o terreno antes de alguém explorar. As tarefas ficam em `server/config/chunky/tasks/` e continuam sozinhas depois de um reinício (`continueOnRestart`).
- Tarefa atual: Mundo Normal, quadrado de raio 5000 em volta de 0,0. Já existiam: Nether e Twilight Forest com raio 1000.
- O core mod (`PreGeracao`) pausa a tarefa quando alguém entra e continua depois de 2 minutos com o servidor vazio. Uma tarefa pausada à mão também volta quando o servidor esvazia.
- Comandos úteis no console: `chunky progress`, `chunky pause`, `chunky continue`, `chunky cancel`. Para uma área nova: `chunky world <dimensão>`, `chunky center <x> <z>`, `chunky radius <r>`, `chunky start`.

## Terras Inexploradas (expedição)

- Ao ligar o servidor, o core mod faz a troca atrasada (se passou das 05:00 do dia da troca) e apaga as regiões antigas de `server/FRX-WORLD/dimensions/frxworld/expedicao/`. Nada fora dessa pasta é apagado.
- A pré-geração da expedição usa o Chunky (tarefa `frxworld:expedicao`) e segue a mesma pausa automática do mundo principal.
- Estado e histórico: `server/FRX-WORLD/frx_expedicao.json`.

