# Terras Inexploradas (expedição)

Mundo à parte, como o Nether, só para explorar. Cada expedição é uma área nova de **2000 × 2000** com terreno, estruturas e masmorras nunca vistos, incluindo o que mods novos trouxerem. O mundo principal e o Nether nunca são tocados.

Código: `aventura/Expedicao.java`. Mundo: `frxworld:expedicao` (`data/frxworld/dimension/expedicao.json`, cópia da geração do Mundo Normal do Terralith). Estado: `server/FRX-WORLD/frx_expedicao.json`.

## Como funciona

- **Mesma seed, área nova.** O Forge 1.20.1 não cria nem apaga dimensões com o servidor ligado, e todas usam a seed do mundo. Por isso o mundo é fixo e cada expedição usa uma área diferente dele: a #1 fica em 200000, 200000, e as seguintes andam 4000 blocos (40 por linha). O histórico guarda número, data e centro de cada uma.
- **Troca a cada 2 dias às 05:00** (Brasília), com o servidor ligado: aviso 10 min e 1 min antes (a entrada fecha no último minuto); quem estiver lá volta ao mundo principal com tudo; a borda muda para a área nova.
- **Servidor desligado na hora:** a troca acontece ao ligar, antes de o mundo carregar.
- **Limpeza:** ao ligar o servidor, os arquivos de região das áreas que não são a atual nem a próxima são apagados. Só dentro de `dimensions/frxworld/expedicao/{region,entities,poi}`, com trava de caminho.
- **Pré-geração:** com o servidor vazio, o Chunky gera a área atual (se faltar) e depois a próxima. Assim a troca é instantânea. Pausa quando alguém entra (`PreGeracao`).
- **Borda:** a borda do Minecraft da expedição é ajustada para a área; quem passar é trazido de volta. Quem sair do jogo lá dentro e voltar depois da troca aparece no pórtico do mundo principal.

## Regras

- Entrada só pelo **pórtico das Terras Inexploradas** (no mundo principal), para **Rank D da Guilda ou mais**. Volta pelo pórtico da chegada (centro da área) ou `/expedicao sair`.
- **Morrer lá não é morte:** volta ao mundo principal com todos os itens, sem lápide, e fica **30 minutos** sem poder entrar.
- Não funcionam lá: portais de qualquer tipo (Nether, End, Aether, Twilight, mods), waystones (nem colocar), camas e âncoras de renascimento. Ninguém entra por outro caminho além do pórtico.
- Baús, construções e bichos deixados lá somem com a área. Terrenos não podem ser comprados lá (só no mundo principal).
- Drops de chefe e loot normais.
- Os pórticos não quebram (nem por explosão), exceto por admin no criativo.

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/expedicao` | todos | Número, data, tamanho, troca e quanto falta |
| `/expedicao sair` | quem está lá | Volta ao mundo principal |
| `/expedicao info` | admin | Tudo acima + centro, pré-geração, pórticos |
| `/expedicao portal` | admin | Constrói o pórtico de entrada onde você está (mundo principal) |
| `/expedicao ir` | admin | Entra mesmo fechada (para testar antes de abrir) |
| `/expedicao abrir` / `fechar` | admin | Liga/desliga a expedição (fechada: sem troca, sem pré-geração, sem entrada) |
| `/expedicao trocar` | admin | Força a troca agora |
| `/expedicao historico` | admin | Últimas 10 expedições |

## Primeira vez

1. `/expedicao ir` no criativo e andar um pouco: confere se a geração está ok (sem queda).
2. `/expedicao portal` no lugar do pórtico de entrada.
3. `/expedicao abrir`.

## Estatísticas e site (3.0.1)

**O que o mod conta** (por conta, só com a expedição aberta e fora do criativo de admin):

| Dado | Como |
|---|---|
| Primeira entrada, última atividade, tempo lá | Relógio, a cada segundo dentro da expedição |
| Área explorada | Chunks (16×16) diferentes visitados; no site em km² (chunk = 256 m²) |
| Estruturas | A cada 5 s, estrutura de mod em que o jogador está dentro (minas, portais em ruínas e tesouro enterrado não contam) |
| Chefes | Chefão morto a até 40 blocos (mesma regra da Coleção: chefes do servidor, marca `forge:bosses` ou 200+ de vida) |
| Tesouros | Baú do Lootr aberto pela primeira vez por aquele jogador |
| Quedas | Mortes (que lá só mandam de volta) |

Fica em `server/FRX-WORLD/frx_expedicao_stats.json` e vai para o painel a cada minuto com gente lá; na troca, a expedição que terminou é fechada no site.

**MVP:** pontuação = chunks × 0,5 + chefes × 40 + estruturas × 15 + tesouros × 5 + minutos × 0,2 − quedas × 10. Os pesos ficam na Configuracao `expedicao_pesos` do banco (`{"chunks":0.5,"chefes":40,"estruturas":15,"tesouros":5,"mortes":-10,"minutos":0.2}`); mudar lá muda todos os rankings, inclusive os antigos.

**Site:**
- `/expedicoes`: expedição atual (contagem regressiva pelo relógio do servidor, quem está lá agora, números, ranking), números gerais e histórico.
- `/expedicoes/<número>`: datas, duração, versão, números, destaques (maior explorador, caçador de chefes, estruturas, tesouros) e ranking completo.
- Ficha do jogador: participações, estruturas, chefes, área, vezes MVP e as últimas colocações.
- Nunca mostra posições: nem de jogadores nem das estruturas.

**Preparado para depois:** tipos de expedição (`tipo`, `nome`, `descricao`, `modificadores`, `regras` na tabela `Expedicao`), conquistas de expedição (tabela `Feito`) e mapa da expedição depois que ela termina.

