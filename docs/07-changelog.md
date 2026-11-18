# 07 — Changelog do core mod

[← Índice](README.md)

Formato: versão, depois o que entrou. A versão do mod fica em `core-mod/gradle.properties`.

> **Situação dos testes (10/11/2026):** a última versão compilada e testada no PC é a **0.33.0** (`pack/mods/frxworld-0.33.0.jar`). As versões **0.34 a 0.40** ainda não rodaram no jogo. O roteiro de teste está no checklist "FRX-WORLD — Checklist de testes".

## 1.0.2 (18/11) — Limite de escravos por rank

- `Escravos.LIMITE` (por rank F a S): 0, 0, 4, 8, 16, 34, 68. A conta é por conta (UUID), não por personagem; o rank é o do personagem em uso.
- `<mundo>/frx_escravos.json` guarda `comprados` e `perdidos` por conta; tem = comprados − perdidos (contrato não usado conta). O contrato leva o dono em `EntityTag.ForgeData.frx_escravo_dono`; `LivingDeathEvent` e `LivingConversionEvent.Post` (aldeão que vira zumbi) devolvem a vaga.
- Aldeões comprados antes desta versão não entram na conta.
- **Biblioteca** (`painel/src/lib/livros.ts`): livro novo "Terras, Clãs e Comércio" (terrenos, ampliar e permissões, clãs, Leiloeiro, Mercador de Discos, Mercador de Escravos); páginas novas "Seus personagens" (Guia), "Contratos da Guilda" e "Coleção de criaturas" (A Guilda e os Ranks); tabela de terrenos e aldeões por rank; banco da conta e temporada por personagem.

## 1.0.1 (18/11) — Personagens (etapas 2 e 3: painel, site, Torre e temporada)

- Painel: tabela `Personagem` (migração `20261118080000_personagens`: conta, vaga, `idJogo`, nome, classe, raça, nível, rank, em uso) e `POST /api/servidor/personagens`; o mod manda a cada gravação do índice (`Personagens.avisarPainel`). A ficha do admin ganhou o bloco "Personagens da conta".
- Site: `perfilPublico` devolve `personagens` e usa o `idJogo` do personagem em uso para placar da Torre, posição na temporada e clãs; a lista de clãs resolve os membros das vagas 2 e 3 pela conta dona.
- Mod: `Torre` (placar) e `Temporadas` (pontos e prêmios pendentes) passaram a usar `Personagens.id(p)` e `Personagens.nome(p)`. O que já estava gravado fica com o personagem da vaga 1.
- **Ficha completa de cada personagem:** `Personagem.base` (campos do Jogador) e `Personagem.detalhe` (ficha inteira) são gravados pelas rotas `progresso` e `jogador-detalhe`, na vaga que o mod informa (`vaga` no corpo; `Personagens.vagaDaConta`). Admin: `/admin/jogadores/<uuid>?vaga=N` abre o retrato do personagem fora de uso (`FichaJogador` com `congelado`: sem atualização automática e sem ações). Site: `/api/publico/jogador?nick=…&vaga=N`; o nick também pode ser o nome de um personagem.
- **Clãs no site:** cada membro aparece com o nome e o nível do personagem (os de fora de uso, pelo retrato).
- A linha `Jogador` do painel continua sendo a do personagem em uso; as ações de admin (dar item, mudar nível, bolsa) só valem para ele.

## 1.0.0 (18/11) — Personagens (etapa 1: mod)

> Publicada no servidor como 0.43.2 e renumerada para 1.0.0 por ser a maior mudança do projeto; a 0.43.3 virou 1.0.1. O arquivo do mod passa a se chamar `frxworld-1.0.1.jar` no próximo reinício.

- `acesso/Personagens`: até 3 personagens por conta. Cada um é o arquivo inteiro do jogador, guardado em `<mundo>/frx_personagens/<uuid>-<vaga>.dat` (o anterior fica em `.anterior`); `<uuid>.json` é o índice (vaga em uso, nomes e retrato de cada um).
- Troca com o jogador fora: `trocar` desconecta; no tick seguinte `executar` copia `playerdata/<uuid>.dat` para a vaga em uso e põe o arquivo da outra no lugar (vaga nova = apaga o arquivo: o jogo trata como jogador novo e a abertura roda). O selo `frx_slot` no NBT persistente diz de quem é o arquivo e vence o índice se houver divergência.
- Banco da conta: o saldo (`frx_banco`) é copiado para o arquivo do personagem que entra (ou aplicado no primeiro login do novo). Terrenos, mercado, progressos e FTB Quests seguem pelo UUID da conta.
- Clãs por personagem: `Clas` passou a usar `Personagens.id(p)` (vaga 1 = UUID da conta; demais = UUID derivado) e `Personagens.jogador(...)` para achar quem está online.
- Nome do personagem no chat, em cima da cabeça e no Tab (`NameFormat`/`TabListNameFormat`).
- Cliente: `TelaPersonagens` (tipo `personagens`; abre 3 s depois do login e pela tecla F8), `Reconexao` (troca a tela de desconectado por uma espera e conecta de novo em 2 s). `AcaoHabilidade.PERSONAGEM` leva o nome (criar/renomear).
- Painel e site ainda mostram só o personagem em uso de cada conta (etapas 2 e 3).

## 0.43.1 (18/11) — Mercador de Escravos

- NPC de papel `escravos` ("Mercador de Escravos"; `/frx npc aqui escravos`; também na lista de papéis do painel). `reinos/Escravos` + `cliente/TelaEscravos` (tipo `escravos`): sete cartões, um por tipo de aldeão, com o boneco desenhado.
- Exige Rank D na Guilda (`RANK_MIN = 2`) e custa 5 de ouro (`PRECO = 500` cobre), fixos no código.
- A compra entrega um ovo de aldeão do jogo marcado com `frx_aldeao` e `EntityTag.VillagerData` (tipo escolhido, profissão `none`): o jogador usa no chão onde quiser. O contrato não funciona em gerador de monstros (`RightClickBlock` cancelado).

## 0.43.0 (18/11) — Ampliar terreno e áreas pintadas pelo cliente

- **Sobrepor terreno próprio:** `recusa` só barra sobreposição com terreno de outro dono. `uniao()` soma retângulos contando uma vez o que se sobrepõe; `areaDe` passou a ser a união dos terrenos do jogador; `blocosNovos` (união depois − união antes) é o que se cobra e o que conta no limite de área. Área inteira já do jogador é recusada. A venda devolve `devolucao(l)`: metade do pago, limitada à metade do preço dos blocos que deixam de ser dele (`blocosSoDele`), para não dar para comprar por cima e vender o de baixo com lucro. A tela recebe `novos` na escolha e `volta` em cada terreno.
- **Mapa (terceira correção):** a API de servidor do JourneyMap 6.0.7 (`ServerOverlayAPI`) aceitava o pedido sem erro e não desenhava nada. `MapaJm` agora só manda um pacote `AbrirMenu` de tipo `area`; `cliente/AreasMapa` desenha com a API de cliente (`PolygonOverlay` + `IClientAPI.show`), a mesma dos pontos do mapa. Os desenhos são removidos ao sair do servidor.

## 0.42.9 (18/11) — Terrenos maiores

- **Mapa (segunda correção):** na 0.42.8 o `MapaJm` procurava o enum `Context` em `journeymap.api.v2.client.display`, que não existe no JourneyMap 6.0.7 do 1.20.1 (lá é `journeymap.api.v2.common.Context`); dava `ClassNotFoundException` e nada era desenhado. Agora tenta os dois caminhos. A pintura ao entrar passou a esperar 3 s (`PINTAR_EM`), para o mapa do jogador já ter se apresentado ao servidor.
- **Salvamento automático a cada 30 min** (`protecao/Salvamento`): o spark mostrou um tick de 160 a 190 ms exatamente a cada 6000 ticks, o autosave do jogo (`tickCount % 6000` em `MinecraftServer.tickServer`, sem configuração). No início do tick anterior a cada múltiplo de 6000 que não é múltiplo de 36000, o mod adianta o contador em 1 por reflexão (campo achado pelo valor) e chama só `PlayerList.saveAll()`. Os chunks seguem no salvamento incremental do jogo (20 por tick). Se a reflexão falhar, fica como antes e avisa no log.
- `Terrenos.AREA_MAX` dobrou em todos os ranks (F a S): 512, 1.200, 2.200, 3.600, 5.600, 9.000 e 14.000 blocos. `LADO_MAX` subiu na mesma proporção de área (24, 34, 46, 58, 68, 92, 114), para um terreno só conseguir usar o limite inteiro. Preço por bloco e quantidade de terrenos não mudaram.

## 0.42.8 (17/11) — Discos de edição limitada

- Painel: `Disco.limite` (0 = sem limite) e `Disco.emitidas` (migração `20261117190000_disco_limite`). Campo "Unidades no mundo" no cadastro; a lista mostra "N de M no mundo" ou "N vendidos".
- Mod: `Discos` conta as vendas em `<mundo>/frx_discos.json` e avisa o painel por `POST /api/servidor/discos` a cada venda. Com o limite atingido, a compra é recusada ("esgotado"). O disco limitado leva `frx_num`/`frx_total` no NBT e mostra "Edição limitada: nº N de M".
- **Terrenos, permissões por jogador:** `Dono.permissoes` guarda, para cada liberado, a soma de `COLOCAR` (1), `QUEBRAR` (2) e `USAR` (4: baús, portas, alavancas, bichos); gravado em `permissoes` no `frx_terrenos.json` (quem já estava liberado fica com tudo). `pode(...)` recebe o que está sendo feito. Quem só pode colocar: `RightClickBlock` nega o uso do bloco clicado mas deixa usar o item da mão. A aba "Quem pode mexer" tem três botões por jogador; `PESSOA` manda `posição × 8 + permissão`.
- **Correção do mapa:** `MapaJm` mandava os conjuntos `activeUIs` e `activeMapTypes` vazios, e o JourneyMap entende vazio como "não mostrar em lugar nenhum". Agora manda todos os valores dos dois enums; os terrenos e a escolha passam a aparecer no mapa.
- `TelaDiscos` mostra `restam/limite` no cartão e trava o botão Comprar quando esgota. Disco destruído não volta ao estoque.

## 0.42.7 (17/11) — Tela de terrenos e edição de dinheiro por moeda

- **Terrenos** (`cliente/TelaTerrenos`, tipo `terrenos`): as três janelas de baú (Comprar terreno, Meus terrenos, Quem pode mexer) viraram uma tela com três abas, no padrão do banco. `Terrenos.enviar` manda tudo por `AbrirMenu`; a tela responde com `AcaoMenu("terrenos", 0, ação, valor)` e `Terrenos.acaoTela` trata (`ABA`, `CANTO1`, `CANTO2`, `COMPRAR`, `CANCELAR`, `VENDER`, `DIVISAS`, `PUBLICO`, `PESSOA`). O tipo `terreno` (singular) continua sendo o do menu do mapa.
- Aba Comprar: desenho da área vista de cima (escolha em dourado, vizinhos em verde/vermelho), preço, bolsa, três barras de limite do rank (lado, área somada, quantidade) e o motivo da recusa. Botões para marcar os cantos na posição do jogador, limpar e comprar.
- Aba Meus terrenos: lista com tamanho, coordenadas e valor pago; vender pede um segundo clique. Aba Quem pode mexer: um botão por jogador.
- **Contratos da Guilda** (`cliente/TelaContratos`, tipo `contratos`): o quadro saiu da janela de baú. Três cartões do dia (ícone, texto, barra, moedas e XP), faixa do extra por fechar os três, cartão da semana, tempo até renovar (dia e semana) e a coleção com a dica ao passar o mouse. É só leitura: não manda ação ao servidor.
- **Painel, Dinheiro dos jogadores:** o botão Editar abre, por jogador, os campos de ouro, prata e cobre da bolsa e do banco. Novo comando `/frx admin <jogador> bolsa <cobre>` (define a bolsa exata) e ação `bolsaDefinir` na API. A bolsa só muda com o jogador no jogo; o banco muda também com ele fora.

## 0.42.6 (17/11) — Estilo de música nos discos

- Painel: `Disco.estilo` (migração `20261117180000_disco_estilo`). O cadastro ganhou o campo **Estilo de música**: texto livre de até 24 letras, com sugestões (os estilos já usados e a lista `ESTILOS_DISCO` de `src/lib/discos-arte.ts`). A lista de discos do painel ganhou filtro por estilo.
- `/api/servidor/discos` devolve `estilo`; `Discos.Def` guarda o campo e a loja manda para a tela.
- `cliente/TelaDiscos`: botão **Estilo: todos / …** (aparece quando há pelo menos um disco com estilo) percorre os estilos da loja em ordem alfabética; Shift + clique volta. A seleção passou a ser pelo código do disco, não pela posição, para não trocar de disco ao filtrar.
- O item mostra `artista · estilo` na descrição. Discos comprados antes continuam como estavam.

## 0.42.5 (17/11) — Telas próprias para clãs, leiloeiro e discos

As três lojas saíram das janelas de baú (`ChestMenu`) e ganharam telas de cliente no padrão do banco: o servidor manda tudo por `AbrirMenu(tipo, NBT)` e a tela responde com `AcaoMenu(tipo, npc, ação, valor)`. Toda ação devolve a tela inteira, com o recado do que aconteceu (`msg`/`ok`).

- **Clãs** (`cliente/TelaClas`, tipo `clas`): abas Meus clãs e Todos os clãs (ranking por poder, 7 por página); na página do clã, abas Cofre (depositar/sacar com os contadores do banco, saldos e últimos movimentos), Membros (tornar oficial, passar liderança, expulsar), Pedidos (aceitar/recusar) e Gerir (nome por caixa de texto, paleta de cores, regra de saque, sair). O servidor guarda uma `Sessao` por jogador e a tela manda posições das listas, não ids. O nome novo chega por `AcaoHabilidade.NOME_CLA`. Ações perigosas pedem um segundo clique.
- **Leiloeiro** (`cliente/TelaMercado`, tipo `mercado`): abas Comprar (cartões 4 × 2 com item, preço e vendedor; busca; ordenação; roda do mouse troca de página; dica do item ao passar o mouse), Vender (grade da bolsa do jogador; preço em ouro/prata/cobre; mostra o líquido), Meus anúncios e Receber. `ANUNCIAR` leva número do item (conferência), espaço da bolsa e preço num só valor.
- **Discos** (`cliente/TelaDiscos`, tipo `discos`): cartões 4 × 2, detalhes do disco escolhido, botão Ouvir (toca o som do disco só para o jogador; para ao fechar) e Comprar.
- `NpcsReino` passa a entidade do NPC para `Clas.abrir`, `Mercado.abrir` e `Discos.abrir`; cada `acao` confere papel e distância do NPC.

## 0.42.4 (17/11) — Desenho próprio dos discos e sentinela de quedas

- **Desenho do disco:** no cadastro dá para (1) escolher uma cor e o painel desenha o disco, (2) importar um PNG 16×16 ou 32×32, (3) desenhar na grade (o mesmo editor do Códex de Armas). Colunas `Disco.textura` e `Disco.modelo` (migração `20261117170000_disco_textura`). O pacote de recursos leva a textura, um modelo por disco e reescreve `models/item/disco.json` com os overrides (`CustomModelData` 1000 + modelo; 1 a 16 continuam sendo as aparências prontas).
- O pacote passou a incluir **todos** os discos, não só os que estão à venda.
- **Sentinela de quedas** (`controle/sentinela.ps1`): lançada pelo `iniciar-frx.bat`; se o painel ou o Minecraft morrerem sem o agente ter mandado, grava `logs/quedas/queda-*.txt` (quem morreu primeiro, processos que nasceram e morreram nos primeiros 15 min, eventos do Windows e do Defender) e pede `reiniciar` ao agente na hora (até 2 vezes em 30 min). O `desligar-frx.bat` escreve `parada` em `controle/sentinela.txt` para ela sair. O `agente-frx.ps1` não foi alterado.

## 0.42.3 (17/11) — Discos de música

- **Painel → Discos de música** (`/admin/discos`): cadastro com nome, artista, descrição, preço, rank mínimo, aparência (16 desenhos de disco do próprio jogo) e o arquivo `.ogg` (até 9 MB). Modelo `Disco`, migração `20261117160000_discos`. O áudio fica em `painel/dados/discos/<codigo>.ogg`, fora do banco e do git.
- **Pacote de recursos:** `pacoteRecursos()` passou a levar os discos (`assets/frxworld/sounds.json` + `sounds/discos/<codigo>.ogg`); o pacote é enviado quando há armas com textura **ou** discos.
- **Mod:** item `frxworld:disco` (um só; código, nome e arte no NBT), `musica/Discos.java` (catálogo de `/api/servidor/discos`, loja em janela, jukebox) e papel de NPC `discos` (Mercador de Discos; `/frx npc aqui discos`).
- **Jukebox:** o disco fica guardado nos dados da jukebox comum e o mod toca `frxworld:disco.<codigo>`, para e devolve. Detalhes em [sistemas/discos.md](sistemas/discos.md).

## 0.42.2 (17/11) — Invocador: alvo marcado

- Tecla nova `key.frxworld.alvo_sombras` (padrão: botão do meio do mouse; reconfigurável em Controles). Só age para o Invocador.
- `Sombras.marcarAlvo`: a criatura na mira (até 40 blocos) vira o foco de todas as sombras e invocações do jogador (`frx_foco` na entidade; `Sombras.FOCOS` por dono). Enquanto o foco vive, `LivingChangeTargetEvent` barra qualquer troca de alvo. Marcar o mesmo de novo, ou clicar sem nada na mira, solta. O foco também se solta quando o alvo morre ou fica a mais de 64 blocos do dono.
- Sem foco, vale o comportamento de antes (monstros por perto e quem o dono ataca).

## 0.42.1 (17/11) — Lote de missões da Guilda e quadro do dia

- **1.062 missões novas** (ids `g<rank>_c_...` caça e `g<rank>_e_...` entrega), 104 a 188 por rank, todas com alvo diferente dentro do rank. Migração `20261117150000_missoes_guilda_lote`, gerada por `tools/gerar-missoes.py` (só entra alvo que existe nos arquivos de idioma do servidor e dos mods; não mexe em missão já cadastrada).
- **Prêmios:** XP = 10% do que falta para subir no nível típico do rank × peso da missão; moedas = 15/30/50/80/120/170/230 cobre (F a S) × peso. Chefes têm peso de 2,5 a 9. Repetíveis a cada 20 h.
- **Quadro do dia** (`Missoes.noQuadro`): missões do lote aparecem por sorteio diário (mesmo para todos, vira à meia-noite de Brasília): 12 do rank do jogador, 4 do rank de baixo e 2 do de cima. As cadastradas à mão, as já aceitas e a urgente aparecem sempre. Não dá para aceitar missão do lote que não esteja no quadro do dia.
- Para desligar uma missão cujo alvo não aparece no mundo: painel → Missões → desmarcar "ativa".
- **Torre e Fenda com monstros dos mods:** `Torre.MONSTROS` passou de 3–4 para 11–15 tipos por tema (79 no total: Born in Chaos, Twilight Forest, Cataclysm, Ice and Fire, Deeper Darker, Undergarden, Alex's Mobs/Caves, Mowzie's, Blue Skies, Illager Invasion). `Torre.ELITE` soma reforços do andar 61 em diante e nas masmorras de portal de rank B ou mais. `Torre.monstros(tema, elite)` só devolve o que existe no servidor. Ficaram de fora, de propósito, os que somem da sala ou trocam de corpo (enderman, piglins, hoglin).

## 0.42.0 (17/11) — Classe Druida

- **Druida (metamorfo, mana):** 14 habilidades. Formas de lobo (+25% velocidade, garras 3 + 0,28 × nível, +10% de dano recebido), urso (+25% vida, −15% de dano recebido, −10% velocidade, garras 2,5 + 0,20 × nível) e ave (voo, garras 1, +25% de dano recebido). Na forma de animal o golpe corpo a corpo ignora a arma.
- **Tempo:** a forma dura a `duração` da habilidade (45 s lobo e urso, 15 s ave). Trocar de forma não renova o relógio; ao voltar a humano, 15 s sem poder transformar. Usar a habilidade da forma atual volta na hora, sem custo e mesmo em recarga.
- **Barras por forma:** guardadas no jogador (`frx_druida`); a barra troca sozinha. Habilidades com código `lobo_`, `urso_` ou `ave_` só valem naquela forma, as demais só na humana; as de `frx:forma` e a suprema valem em qualquer uma.
- **Visual:** pelo mod Woodwalkers, por reflexão (`habilidades/Formas.java`). Com ele no pack, o FRX o tranca em "só por código" ao ligar o servidor e desliga a cópia de vida/armadura e os monstros ignorando o jogador. Sem ele, as formas funcionam sem mudar o corpo. Para instalar: `pack\adicionar-woodwalkers.bat`.
- Especiais `frx:forma` e `frx:floresta`; Despertar Coração da Floresta; três ramos de talento. Migração `20261117140000_druida`.
- `mod_version` 0.42.0 (depois do .9 sobe o número do meio).

## 0.41.9 (17/11) — Classes Bardo e Monge

- **Bardo (suporte, mana):** 12 habilidades e a passiva Inspiração (jogadores a até 8 blocos causam 8% a mais de dano). Canções que ficam tocando em volta dele, uma por vez (`frx:cancao_marcha`, `frx:cancao_cura`, `frx:cancao_guerra`, `frx:sinfonia`), mais `frx:ninar`, `frx:zombaria`, `frx:eco` e `frx:heroi`. Código em `habilidades/Cancoes.java`. Despertar: Bis! (zera recargas e enche o recurso do grupo a 12 blocos, +20% de dano por 8 s).
- **Monge (mão vazia, chi):** só vale com a classe Monge e a mão principal vazia; fora disso o combate não muda. Soco soma 2,5 + 0,3 × nível, combo de 4 golpes (o 4º é o golpe final), esquiva e redução de dano por peça de armadura a menos. Recurso novo `CHI` (0 a 100, 10 por soco, 20 no golpe final, esfria 3/s fora de combate). Especiais `frx:palma`, `frx:rajada`, `frx:meditar`, `frx:pontos`, `frx:vazio` e `frx:cem` em `habilidades/Marcial.java`. Despertar: Portão Interior (socos em dobro com onda de choque, habilidades sem custo).
- **Apoio no mod:** `Habilidades.darRecurso`, `Habilidades.encurtarRecargas` e a marca `emHabilidade` (dano de habilidade não soma o bônus do soco). Seis ramos de talento novos.
- **Painel:** classes `BARDO` e `MONGE`, 14 efeitos especiais descritos, migração `20261117130000_bardo_monge` (24 habilidades), livros e ícones.
- **Tela de escolha de classe:** os dez botões ficam em grade e a descrição aparece embaixo, só da classe clicada.
- `mod_version` 0.41.9.

## 0.41.8 (17/11) — Classe Paladino e regra de versão

- **Paladino** (oitava classe, recurso Mana): 12 habilidades (migração `20261117120000_paladino`), Aura de Proteção passiva (jogadores a até 6 blocos levam 10% menos dano; o Paladino leva 15% menos de mortos-vivos), três ramos de talento (Baluarte, Justiça, Devoção) e o Despertar **Égide da Aurora** (cúpula que barra 60% do dano e devolve o que barrou num clarão final).
- Mecânicas novas em `habilidades/Sagrado.java`: `frx:voto` (o dano do aliado vem para o Paladino), `frx:sacrificio` (dá vida para curar e limpar o grupo), `frx:intervencao` (teleporta até o aliado e toma os inimigos dele) e `frx:juizo` (coluna de luz com dano e cura por segundo).
- **Regra de versão:** cada atualização soma 1 no último número; depois do .9 sobe o do meio; mudança muito grande abre versão cheia. As notas do site foram renumeradas de 0.41.1 a 0.41.8 e o `mod_version` passou a 0.41.8.

## 0.41.0 (17/11) — Mercado entre jogadores e bestiário no site

- **Mercado** (NPC `leiloeiro`, `moedas/Mercado.java`): anúncios a preço fixo do item da mão, vitrine paginada, compra com o vendedor fora do jogo, taxa de 5% na venda, validade de 7 dias, limite de anúncios pelo rank e balcão "Receber".
- **Bestiário no site** (`components/publico/bestiario.tsx`, `bestiarioPublico`, `/api/publico/criatura-skin`): galeria das criaturas do Códex; nome, história, golpes e skin só aparecem depois da primeira derrota.
- Docs: `sistemas/mercado.md`.

## 0.41.0 (17/11) — Mestre dos Clãs e cofre do clã

- **Mestre dos Clãs** (NPC `clas`, `clas/Clas.java` + `clas/FtbTeams.java`): ver os clãs, pedir entrada, fundar, sair, membros, pedidos e **cofre do clã** (moedas separadas do banco, com registro de movimentos e regra de saque do líder).
- Site: a riqueza do clã passa a somar o cofre; as notas de atualização mostram também a hora em que entraram no ar.
- **Vários clãs por jogador (até 3):** os clãs passam a ser do próprio mod (`frx_clas.json`), com importação única dos times do FTB Teams. O líder renomeia com uma etiqueta, troca a cor, nomeia oficiais e passa a liderança pelas janelas.
- Site: estante da Biblioteca em pixel art.

## 0.41.0 (17/11) — Terrenos

- **Terrenos** (`protecao/Terrenos.java`): o jogador marca dois cantos no mapa do JourneyMap (botão direito), vê o preço pela área (1,2 cobre por bloco) e compra. Só o dono e quem ele liberar mexem; explosões e monstros não destroem.
- Terrenos comprados ficam pintados no mapa de todos com o nome do dono (`protecao/MapaJm.java`, API de sobreposições do servidor do JourneyMap); menu do mapa em `cliente/MapaTerrenos.java`.
- Limites pelo rank da Guilda: lado máximo, área somada e quantidade (F: 16 / 256 / 1 … S: 80 / 7000 / 7).
- Terras do reino fora de venda: capitais, Torre e ponto de partida automáticos; `/frx terreno proibir|liberar [raio]`. Reserva sem mapa: `/terreno`.
- Docs: `sistemas/terrenos.md`.

## 0.41.0 (17/11) — Contratos da Guilda, coleção e clãs

- **Contratos da Guilda** (`missoes/Contratos.java`): 3 contratos por dia e 1 por semana, por jogador e pelo rank. Progresso automático, recompensa na hora, extra por fechar os três do dia. Quadro em janela pelo botão "Contratos" do Cartão de Aventureiro e do balcão.
- **Coleção** (`progressao/Colecao.java`): tipos de criatura e chefões diferentes derrotados, com marcos que pagam moedas e XP e 8 conquistas novas (5 títulos).
- **Clãs:** os times do FTB Teams viram clãs no site público, com ranking de mais fortes e mais ricos; a ficha do jogador mostra clã e coleção.
- Docs: `sistemas/contratos-colecao-clas.md`.

## 0.41.0 (17/11, tarde) — Códex de Criaturas

- **Códex de Criaturas** (painel `/admin/criaturas`, mod `aventura/Criaturas.java`): chefes e monstros cadastrados no painel, com criatura base, ranks, multiplicadores de vida, dano e velocidade, lugares (masmorras de portal, Torre, emboscadas do Nether), peso ou chance, XP, moedas e item especial ao morrer (inclusive arma do Códex de Armas).
- **Chefes:** os golpes e o lacaio são escolhidos no painel. `Chefes.criar` passa a receber o lugar e sorteia primeiro entre os chefes do Códex; a lista do código vira reserva. Os 14 chefes antigos entram como cadastros iniciais (migração `20261117090000_codex_criaturas`).
- **Monstros:** podem tomar a vaga de um monstro comum nas câmaras de portal, nos andares comuns da Torre e nas hordas do Nether.
- **Aparência:** skin 64×64 desenhada no painel (editor das skins de NPC) ou importada; viaja pela rota das skins de NPC (`papel:bicho_<código>`), sem pacote de recursos. Tamanho do desenho ajustável (`RenderRacas.escala`).
- **Painel:** contagem de derrotas, duplicar e "Invocar perto de mim". Comandos `/frx criaturas lista|recarregar|invocar`.
- Docs: `sistemas/codex-criaturas.md`.

## 0.41.0 (17/11) — Assassino com 16 habilidades

- **Assassino** passa de 7 para 16 habilidades (migração `20261117070000_assassino_novas`): Passo Fantasma (nv 2), Bomba de Fumaça (4), Lâminas Envenenadas (6), Leque de Adagas (7), Emboscada (9), Golpe de Misericórdia (11), Adaga Ricochete (13), Sangria Profunda (15, por tomo) e a suprema Dança da Morte (25).
- **Seis mecânicas novas** em `habilidades/Especiais.java`: `frx:atras`, `frx:fumaca`, `frx:laminas`, `frx:emboscada`, `frx:sangria` e `frx:danca`. Nas mecânicas de golpe, o bônus do Assassino (pelas costas e alvo ferido) é aplicado por `bonusAssassino`.
- Ainda não testado em jogo.
- **Códex de Armas** (painel `/admin/codex`, mod `armas/Codex.java`): armas únicas criadas no painel, com item base, rank (classe), raridade, dano extra, encantamentos, total no mundo e "uma por jogador". Saem só do ferreiro/armeiro ou de missão (item `codex:<código>` na recompensa). Armas que evoluem ficam vinculadas ao dono e ganham dano por nível dele. Estoque em `<mundo>/frx_codex.json`, espelhado no painel. Comandos `/frx codex lista | recarregar | dar | estoque`.
- **Aparência das armas do Códex:** editor de pixels no painel (16×16 ou 32×32, partir do item base ou importar PNG). O painel monta um pacote de recursos (`/api/publico/recursos`, `lib/codex.ts`) com um `CustomModelData` por arma (880000 + número) e o mod manda o pacote a quem entra (`sendTexturePack`), reenviando quando muda. Tabela nova `ArmaCodex` (migração `20261117080000_codex_armas`).
- **Comando `/ping`** e **`/frx ping`** (`progressao/Ping.java`).
- **Menu do painel em grupos** (`components/menu.tsx`): Geral, Jogadores, Progressão e combate, Economia e itens, Mundo; cada grupo abre e fecha (o navegador lembra), com ícone por tela, e a barra lateral fica fixa ao rolar a página.

## Painel (16/11, noite) — Site público em `/` e painel em `/admin`

- **Site público** na raiz do endereço (`craft.frgomes.com.br`), sem login: status, jogadores online, acontecimentos, rankings, Torre, temporada, mapa, sistemas, mods e como entrar. Detalhes em [05-painel](05-painel.md).
- **Painel admin** mudou para `/admin` (todas as telas, links e atalhos). Os endereços antigos (`/servidor`, `/missoes`...) deixam de existir.
- **Menu "Dinheiro dos jogadores"** (`/admin/dinheiro`): bolsa, banco e total de cada um, com busca e ordenação; tira um valor da bolsa ou do banco, ou zera tudo. Só age em quem está online (usa `/frx admin <nick> tirarmoedas|banco`).
- **Dungeon de portal única por portal** (`aventura/Fenda.java`): todos que atravessam caem no mesmo lugar, antes era uma por grupo. **Lotação por rank:** F 2, E 3, D 3, C 4, B 4, A 5, S 6. Quem já entrou conta na lotação mesmo depois de morrer ou sair, e pode voltar. Ainda não testado em jogo.
- **Classes de equipamento valem para arcos e bestas** (`progressao/ClassesEquip.java`): tabela `DISTANCIA` (arco F, besta E, arcos de mods de D a A) e, para arma de longe fora da tabela, a raridade (comum D, incomum C, raro B, épico A). Acima do rank o arco não puxa nem dispara. Ainda não testado em jogo.
- **Site mais visual:** galeria de aventureiros com o boneco da skin, pódio dos três mais fortes, rostos nos rankings e **ficha do jogador** ao clicar no nome (skin, nível, raça, Guilda, feitos, habilidade única e habilidades aprendidas). A skin é a que o SkinRestorer gravou em `<mundo>/skinrestorer/<uuid>.json`.
- **Notas de atualização** no site (seção Novidades), escritas para jogador em `painel/src/lib/notas.ts`. A cada `/publicar`, acrescentar ali o que mudou.
- **Ponto de partida do mapa** corrigido: vem do servidor ou do `level.dat`, não mais do `posicoes.json` (que guarda o centro de Tempest).
- **Banco editável no painel** (`/admin/dinheiro`): clique no saldo para definir o valor, mesmo com o jogador offline. Comando novo `/frx bancooffline <uuid> <cobre>`; offline, o valor espera em `<mundo>/frx_banco_pendente.txt` e entra no próximo login.
- **Chefes das masmorras dos reinos** (`Zonas.aplicarNivel`): chefe dentro de uma área de nível fixo (`AreasNivel`) recebe o bônus de nível inteiro, mais +300% de vida e +100% de dano. Jogador abaixo do nível 50 causa no máximo 20% do dano e recebe pelo menos o triplo. Masmorra enterrada embaixo de capital nunca fica abaixo do nível 50. Vale para chefes que nascerem depois da atualização. Ainda não testado em jogo.
- **Rank da Guilda para trocar de dimensão** (`Zonas.barrarDimensao`): Nether exige Rank D e o End exige Rank C. Vale para portal, waystone e qualquer teleporte; criativo e espectador passam. O painel pode trocar em `zonas.rankMinimo = { "dimensao": "D" }`. Quem já está lá dentro não é expulso. Ainda não testado em jogo.
- **Mods novos** (instalados em 16/11, 20:57): Naturalist, Mythic Mounts, Artifacts, Macaw's (Bridges, Roofs, Doors, Furniture, Windows, Fences and Walls), Bygone Nether e Piglin Proliferation, mais as dependências Patchouli e Cloth Config API. O packwiz não puxou o Cloth Config sozinho e o servidor não subiu na primeira tentativa (Artifacts exige `cloth_config` 11.0.99+); ao adicionar mod, conferir `Missing or unsupported mandatory dependencies` no `latest.log`.
- **Nether extremamente difícil:** faixa de nível 22–40 (era 15–35), dureza da dimensão +150% de vida e +100% de dano (`Zonas.DUREZA_PADRAO`) e **emboscadas** (`aventura/Inferno.java`): a cada 6 a 11 min (a primeira aos 3 min) nasce perto de cada jogador uma horda de 4 a 7 monstros ou, em 25% das vezes, um chefão de `Chefes` do rank dele. Mods na fila: Bygone Nether (estruturas e monstros por bioma) e Piglin Proliferation. Ainda não testado em jogo.
- **Torre em grupo de até 5** (`Torre.MAX_GRUPO`): sem comando nem `/grupo`: alguém pisa no portal do saguão, contagem de 5 s, e sobem juntos todos os que estão no saguão (os 5 mais perto do portal). Por companheiro a mais: +1 nível nos monstros, +40% de vida, +10% de dano, +3 monstros por andar e chefe com +90% de vida. **XP dividida** (`xpPorMembro`): o prêmio do andar cresce 50% por companheiro e é repartido igualmente (2 = 75% cada, 3 = 67%, 4 = 62%, 5 = 60%). Ainda não testado em jogo.
- **Lojas respeitam o rank** (`MenusNpc.ofertasDe`): ferreiro, armeiro e mercador só mostram armas e armaduras de classe até o rank da Guilda do jogador. Para o Rank F, que não usa ferro, ferreiro e armeiro oferecem pedra e couro (`Lojas.basicos`). Ainda não testado em jogo.
- **Dragão no fundo do site** (`components/publico/dragao.tsx`): silhueta própria que cruza a página indo e voltando.
- **Loja da Torre em janela** (`Torre.MenuLoja`): o Mercador abre uma janela de baú com os itens; passar o mouse mostra preço e se dá para comprar, clicar compra. Feita só no servidor (`ChestMenu` com cliques bloqueados), sem código de cliente. Ainda não testado em jogo.
- **Chefes épicos dos mods na Torre** (`Torre.EPICOS`): nos andares 20, 40, 60 e 80 o chefe é sorteado entre chefes de Mutant Monsters, Twilight Forest, Mowzie's Mobs, Illager Invasion e Cataclysm (só os que existem no servidor). Vida extra menor que a dos chefes comuns (andar/40 + 60% por companheiro), nome dourado e aviso na tela. Andares 10, 30, 50, 70 e 90 seguem com `Chefes`; o 100 é o Wither. Ainda não testado em jogo.
- **Invocações que crescem** (`Habilidades.invocar`): força = talento × (1 + 10% por nível da habilidade) × (1 + 4% por nível do jogador); o nome mostra o nível. Antes só o talento contava.
- **Sombras sobem de nível com o dono** (`Sombras.aoMorrer`): abate do Invocador conta para as sombras em campo a até 24 blocos; antes só o golpe final da própria sombra contava.
- **Ajustes de admin com jogador offline** (`progressao/AjustesAdmin.java`): `/frx ajustar <nick> nivel <n> | rank <F..S> | torre`. Offline, o pedido espera em `<mundo>/frx_ajustes.txt` e entra no próximo login.
- **Barra de experiência das sombras** (`cliente/TelaSombras.java`): na ficha, "Nível n/10 · feitos/necessários abates" com barra roxa; na lista, uma barrinha ao lado do nível. O nível n pede 3 × n² abates.
- **Biblioteca no site** (`painel/src/lib/livros.ts`, `components/publico/biblioteca.tsx`): 10 livros sobre o mundo (guia, Guilda e ranks, missões, economia, Torre, portais e chefes, dimensões, raças, talentos e poderes, eventos) e 1 por classe, com leitor de páginas que vira (duas páginas no computador, uma no celular, setas do teclado). Ranks, missões do quadro e habilidades vêm do banco; o resto é texto em `livros.ts`, que precisa ser atualizado quando uma regra mudar.
- **Vida dos outros jogadores à vista** (16/11, 22:23, sem reinício): placares do jogo `frx_vida` (número com ❤ abaixo do nome) e `frx_vida_tab` (corações na lista do TAB), criados pelo console com `scoreboard objectives add ... health`. Ficam salvos no mundo; para tirar, `scoreboard objectives remove frx_vida` e `frx_vida_tab`.
- **Queda de 16/11, 22:32:** painel e servidor caíram juntos sem deixar erro (o log do servidor parou às 22:27 sem mensagem de desligamento; o `painel.log` foi sobrescrito na religada). A vigia do agente religou em cerca de 1 min. Segunda queda desse tipo (a outra foi em 15/11). Para a próxima, o `iniciar-frx.bat` passa a guardar o log anterior em `logs/painel-anterior.log`.
- **Nether recriado** (16/11, 22:42): com o servidor desligado, a pasta `server/FRX-WORLD/DIM-1` foi removida e o Nether nasce do zero, já com Bygone Nether e Piglin Proliferation. A cópia do Nether antigo foi apagada a pedido; ele só existe nos backups do mundo anteriores a essa hora.
- **Mods de rede** (16/11, 22:54): Connectivity 7.7 (+ Cupboard 4.2, CurseForge) e Krypton Reno 26.1.1 (Modrinth `krypton-fnp`). **Cuidado com o `pack/pendentes.txt`:** quando o nome não é um slug exato, o packwiz pesquisa e instala o primeiro resultado sem avisar (`cupboard` virou Aesthetic Storage e `connectivity` virou No Moon Network Fix; os três intrusos foram removidos em seguida). Use o endereço completo do projeto (`curseforge https://www.curseforge.com/minecraft/mc-mods/<slug>`) e confira no `logs/pack.log` se o nome em "successfully added" é o esperado; "Searching Modrinth..." é sinal de que ele caiu na pesquisa.
- **Tranca de baú** (`protecao/Trancas.java`): agachado, clique com um gancho de armadilha num baú, barril, fornalha ou qualquer bloco que guarda itens para trancá-lo (gasta o gancho). Só o dono abre e quebra; explosão não destrói; outro jogador não coloca funil embaixo. Agachado + gancho de novo destranca e devolve o gancho. Baú duplo conta inteiro. O dono fica no próprio bloco (`frx_tranca_dono` no ForgeData). Não cobre funis com carrinho nem máquinas do Create puxando itens. Ainda não testado em jogo.
- **Comando de ping** (`progressao/Ping.java`): `/ping` (todos) mostra o ping do jogador com nota (ótimo, bom, regular, ruim) e o TPS do servidor; `/frx ping` (admin) lista o ping de todos os online.
- Rotas sem login: `/api/publico/dados`, `/api/publico/mapa-tile`, `/api/publico/jogador` e `/api/publico/skin`. Não expõem e-mail, UUID, IP, chat, inventário nem posição de jogador.

## 0.41.0 (16/11) — Classes de equipamento, dungeon de portal, chefões e moedas só por missão e venda

- **Classes de equipamento por rank da Guilda** (`progressao/ClassesEquip.java`): armas e armaduras, inclusive de outros mods, exigem o rank da classe para usar.
- **Portais** levam a uma **dungeon temporária** (`aventura/Fenda.java`) com três câmaras e chefão; só entra quem tem o rank do portal ou maior. As ondas no mundo aberto saíram.
- **14 chefões com mecânicas** (`aventura/Chefes.java`), sorteados pelo rank; também nos andares de chefe da Torre.
- **Torre:** XP só ao vencer o andar, com curva que começa quase em zero; morte de verdade com lápide na porta, perda de XP e 10 min de espera.
- **Moedas** só por missões e venda de itens: saíram de baús, monstros, chefes, invasões, campanhas, portais, Torre, procurados, tesouros, história, temporada, soldo e FTB Quests. Runa da Fortuna passa a dar XP em dobro; temporada paga em runas.
- **Classes, cuidado na virada:** na primeira entrada de cada jogador toda a armadura vai para a mochila; armadura proibida nunca cai no chão (mochila, depois baú de ender; sem espaço, fica no corpo até abrir vaga).
- **Mochilas** (Sophisticated Backpacks): ninguém abre a mochila que outro jogador está usando (`allowOpeningOtherPlayerBackpacks = false`).
- **Floresta do Crepúsculo mais difícil:** faixa de nível 14–30 (era 10–25) e dureza da dimensão, +80% de vida e +50% de dano em todo monstro de lá (`Zonas.dureza`, ajustável em `zonas.dureza` no painel).
- **Torre mais difícil:** nível do monstro 3 + 1,4 × andar, +5% de vida e +2% de dano por andar, mais monstros por andar (até 18) e Velocidade a partir do andar 30.
- Ainda não testado em jogo.

## 0.41.0 (15/11, noite) — Aventura, Torre Infinita, runas, habilidades novas e cidades protegidas

- **Mecânicas especiais de habilidade** (`habilidades/Especiais.java`): o campo *efeito* aceita `frx:puxar`, `frx:provocar`, `frx:salto`, `frx:fenda`, `frx:executar`, `frx:vampirismo`, `frx:refletir` e `frx:lancar`. Servem para qualquer classe; o painel sugere a lista no campo.
- **Guerreiro** passa de 6 para 15 habilidades (migração `20261115180000_guerreiro_novas`): Arremesso de Machado, Gancho de Corrente, Provocar, Salto Esmagador, Postura de Revide, Fenda Sísmica, Executar, Sede de Sangue (tomo) e Ira do Titã (segunda ultimate, nível 25).
- Em jogo desde o reinício de 15/11 13:22; balanceamento ainda por ajustar.
- **Arqueiro** passa de 6 para 14 habilidades (migração `20261115190000_arqueiro_novas`), com oito mecânicas novas em `Especiais.java`: `frx:explosiva`, `frx:ricochete`, `frx:marca`, `frx:gancho`, `frx:leque`, `frx:voleio`, `frx:perfurar` e `frx:armadilha`. Ainda não testado em jogo.
- **Mago** passa de 6 para 15 habilidades (migração `20261115210000_mago_novas`), com `frx:congelar`, `frx:misseis`, `frx:escudo_mana`, `frx:trocar`, `frx:sifao`, `frx:zona` e `frx:singularidade`; `frx:ricochete` agora usa o elemento (Corrente de Raios). Ainda não testado em jogo.
- **Cosmetic Armor Reworked** no pack (esconder armadura e mostrar a skin).
- **Benefícios do rank da Guilda** (`Guilda.bonusMissao/desconto/pagarSoldo`): por rank acima de F, +10% em missões, 2% de desconto e soldo diário de 60 cobre; aparecem no cartão e na promoção. Ainda não testado em jogo.
- **Moradores das capitais** (`reinos/Populacao.java`): não morrem mais para monstro, dragão, parede ou invasão (só jogador fere); morador ou guarda que morre volta em 10 min perto de onde caiu (`frx_repor.txt`). O arquivo foi semeado com as mortes de 14 e 15/11 tiradas do log.
- **Dragões** do Ice and Fire não quebram mais blocos (`Dragon Griefing = 2`).
- **Portais:** ficam 20 min abertos (eram 12); recompensa de 300 × (rank+1)² cobre e 300 × nível de XP (eram 100 e 120); chance de tomo 30% + 10% por rank; coluna de luz visível a 500 blocos; ponto global no JourneyMap pela API de servidor (`Portais.pontoGlobal`). Ainda não testado em jogo.
- **Aventura** (pacote `aventura/`, ver [sistemas/aventura.md](sistemas/aventura.md)): história principal em 14 capítulos com o Rei Demônio no fim, temporadas mensais com placar, mural de procurados, Torre Infinita de 100 andares, eventos do céu (Lua de Sangue e chuva de meteoros) e mapas do tesouro. Ainda não testado em jogo.
- **Torre:** quadros de ranking ao lado da porta (mais longe e mais abates), andares temáticos com salas maiores (Deserto, Floresta, Geleira) e barra no topo da tela com o andar. Ainda não testado em jogo.
- **História no livro de missões:** capítulo «História Principal» no FTB Quests (14 missões, conquistas `historia_1..14`). Torre: escolha de continuar ou sair a cada andar e Cozinheiro no andar seguro.
- **Torre:** fichas, baú do andar seguro e Loja da Torre. **Ladrão** (`eventos/Ladrao.java`): evento raro (5 em 1000 por minuto) em que um ladrão leva uma moeda e foge por 15 s; quem pega recebe dez vezes. `/frx ladrao [jogador]`, `/frx ladrao chance <n>`; skin pelo painel (papel `ladrao`). Ainda não testado em jogo.
- **Runas** (`aventura/Runas.java`): 11 runas de arma e armadura em três níveis, encaixadas na bigorna. Ainda não testado em jogo.
- **Anéis:** 10 espaços de anel no Curios (`server/kubejs/data/frxworld/curios/slots/ring.json`).
- **Skins de jogador** em servidor offline: mod SkinRestorer.
- **Ronaldinho** (`eventos/Craque.java`): evento raro e aleatório. Uma vez por minuto cada jogador tem 4 em 1000 de chance (≈ 1 vez a cada 4 h de jogo): ele aparece, dribla em volta do jogador com a bola, dá a caneta (o jogador gira 180°), some numa nuvem e deixa a "Bola do Bruxo". Aviso no chat para todos. `/frx ronaldinho [jogador]` força; `/frx ronaldinho chance <0-1000>` ajusta (`frx_ronaldinho.txt`). Skin pelo painel (NPCs → Ronaldinho, papel `craque`). Ainda não testado em jogo.

## 0.41.0 (13/11) — Mundo que reage, nomeação, bestiário, missões de rank e Habilidade Suprema

- **NPCs que reagem a quem você é** (`reinos/Reacoes.java`): a fala muda com o rank da Guilda, a raça à mostra, o título em uso e o nome recebido de um rei.
  - O guarda ignora um Rank F, respeita do Rank C para cima e faz reverência a um Rank A ou a quem foi nomeado. Raças das trevas ficam sob vigia.
  - O sacerdote desconfia de vampiro, demônio e Rei Demônio, e reverencia anjo e Ser Celestial. Diante de um Deus, todos se curvam.
  - Rei, taverneiro, sábio, cartógrafo, ferreiro, armeiro, mercador, cozinheiro, banqueiro e recepcionista também têm falas próprias.
  - A raça lendária disfarçada conta como a raça do disfarce. A reação entra em cerca de 2 a cada 3 conversas; nas outras vale a fala normal ou a do painel.
- **Nomeação pelo rei** (`reinos/Nomeacao.java`): ao concluir a campanha de um reino, o rei faz a cerimônia.
  - O primeiro rei oferece três nomes e o jogador escolhe um (clique no chat). Os reis seguintes reconhecem o nome e somam a bênção do reino.
  - Cada nomeação dá **+1 coração e +2% de dano, para sempre**. Há sinos, anúncio no servidor, raio (só visual) e o título "<Nome>, o Nomeado".
  - Quem concluiu a campanha antes desta versão recebe a cerimônia ao falar com o rei.
  - Comandos: `/nomeacao`; admin: `/frx nomeacao <reino>` e `/frx nomeacao limpar`.
- **Habilidade Suprema** (`habilidades/Suprema.java`): no nível 5 da Habilidade Única, `/suprema prova` chama o Eco da habilidade (4 minutos, sem morrer; nova tentativa em 10 minutos).
  - Vencendo, a habilidade ganha nome novo, um degrau a mais no passivo e um **efeito ativo**, usado com **Agachar + F** (ou `/suprema usar`).
  - Grande Sábio → Senhor da Sabedoria; Devorador → Senhor da Gula; Fúria do Dragão → Ira do Dragão Verdadeiro; Corpo de Aço → Fortaleza Absoluta; Passo Sombrio → Soberano das Sombras; Olho do Falcão → Olho Divino; Sangue Real → Monarca Carmesim; Filho do Vento → Deus do Vento; Segunda Chance → Fênix Eterna; Instinto do Caçador → Predador Ápice.
  - Admin: `/frx suprema nivel5`, `/frx suprema dar`, `/frx suprema tirar`.
- **Bestiário** (`progressao/Bestiario.java`): capítulo novo no livro de Quests, com uma página por tipo de monstro de todos os mods.
  - A página se completa ao matar o monstro pela primeira vez (tarefa `kill` do FTB Quests) e paga XP.
  - Mostra a ameaça (Rank F a S, pela vida e ataque base), as fraquezas e os biomas onde nasce.
  - O arquivo `config/ftbquests/quests/chapters/bestiario.snbt` é refeito ao ligar o servidor quando a lista de monstros muda.
- **Missões de rank na Guilda** (`missoes/Missoes.java`, `missoes/Urgentes.java`): cada missão tem um rank mínimo (F a S), escolhido no editor do painel.
  - No quadro o título traz `[Rank X]`, a lista sai ordenada por rank e quem não tem o rank vê o motivo do bloqueio. Missões de Rank E para cima exigem registro na Guilda.
  - A migração `20261113230000_missao_rank` dá às missões já cadastradas o rank da faixa de nível delas.
  - **Urgentes:** a cada 45 minutos uma caçada vira urgente por 20 minutos, com aviso do Sistema e recompensa em dobro para quem entregar no prazo. Os três números e o liga/desliga ficam no painel (Missões → Missões urgentes). Admin: `/frx urgente` e `/frx urgente encerrar`.

- **Música na abertura isekai** (`acesso/Abertura.java`): a cena toca uma música presa ao jogador, que para quando ele nasce na capital (opcional). Painel: **Convites → Abertura do primeiro login → Música** (lista de músicas do jogo, volume e a opção "Música própria"). Para usar uma música sua, salve o OGG em `core-mod/src/main/resources/assets/frxworld/sounds/abertura.ogg` e reinicie.

- **Entrada sem convite e sem fingerprint (13/11):** `exigirConvite = false` em `server/config/frxworld-common.toml` (decisão do Flavio). O kit dos amigos passou a usar o endereço fixado do AutoModpack (`host#amp1=<fingerprint>`, em `amigos/endereco-servidor.txt`), que dispensa a tela de confirmação do certificado. Com `online-mode=false` e sem convite, qualquer pessoa com o endereço entra com qualquer nick.

- **Entradas das masmorras (14/11)** (`reinos/EntradasMasmorra.java`): toda masmorra fixa enterrada ganha um pórtico de pedra negra na superfície, com o nome flutuando e um portal (obsidiana chorosa) que leva à primeira sala; lá dentro, outro portal traz de volta. As de superfície ganham só um marco com o nome ao lado da construção. Registro por mundo em `frx_entradas.txt`. Admin: `/frx masmorra entradas` e `/frx masmorra entradas refazer`. O `/masmorras` passou a mostrar a entrada, não o centro da estrutura.
- **Dano entre aliados** ligado nos grupos; **ficha do painel** criada no login quando o convite está desligado; mods **Golden Age Combat**, **Sword Blocking Mechanics** e **Just Zoom** (cliente).

- **Tempest só com o coliseu (14/11):** caixa reduzida para 29×15 chunks (x 64 a 527, z −16 a 223), com centro em 296, 104. A praça passou para o centro do coliseu (`praca: [296, 105]`) e os NPCs voltaram ao arranjo automático em anel (as posições fixas antigas ficaram em `npcs_antes_20261114` no `posicoes.json`). Original em `server/frxworld/reinos-originais/tempest-20261114-1146`; as regiões antigas do mapa estão em `backups/regiao-tempest-20261114`.
- **Ponto inicial livre:** se o admin mudar o spawn do mundo com `/setworldspawn`, a abertura isekai passa a nascer e renascer ali e o Sistema indica a distância e a direção da capital.

- **`/publicar` (15/11):** padrão de publicação em `.claude/commands/publicar.md`: atualiza a documentação desde o último `/publicar`, faz o commit (primeira linha `publicar: …`) e envia direto para a `main`.
- **Portal de Tempest (15/11):** a chegada passou para dentro da Fábrica Ancestral (223, −19, 118); antes caía numa caverna encostada nela. A escolha da sala agora exige piso, teto e paredes de construção.

- **Cidade inicial em Lubelius (15/11):** o spawn do mundo foi para o centro de Lubelius (1788, 71, 1598). A abertura isekai nasce ali, o Prólogo passou a pedir a chegada a Lubelius e, como o nível das zonas é medido a partir do spawn, a região de nível 1 agora é a de Lubelius. Todos os jogadores foram zerados (dados antigos em `backups/jogadores-20261115`).
- **Portão de capital** (`reinos/Portao.java`): `/frx portao aqui [largura]` abre um portão em arco no muro à frente do admin, com grade erguida, lanternas e quatro guardas; `/frx portao desfazer` devolve os blocos do último.
- **Ciclo do dia** (`progressao/CicloDia.java`): dia de 20 minutos e noite de 10 (o normal é 10 e 10). `/frx ciclo <dia> <noite>` muda; registro em `<mundo>/frx_ciclo.txt`. O mod desliga o `doDaylightCycle` e anda o relógio ele mesmo.

- **Arena Flamejante sob Lubelius (15/11):** masmorra `lubelius-abismo` (`cataclysm:burning_arena`, chefe Ignis), com **nível fixo 55**. Novidades que ela trouxe:
  - **Nível fixo por masmorra** (`progressao/AreasNivel.java`, campo "Nível fixo" no painel, coluna `Masmorra.nivel`): dentro da caixa da estrutura os monstros nascem naquele nível, qualquer que seja a zona. Registro em `<mundo>/frx_areas.txt`.
  - **Masmorra dentro de capital:** se a estrutura nasceria na altura da cidade, o mod a desce inteira até caber embaixo do chão (14 blocos de folga). O ajuste fica no fim da linha do `frx_masmorras.txt`.
  - **Masmorra seca:** ao descer uma estrutura para baixo de uma capital, o mod tira a água de dentro das peças, troca por pedra a água em volta e fecha com pedra a casca de 1 bloco ao redor (aquíferos e cavernas que cruzavam a parede). A lava fica. `/frx masmorra secar <id>` refaz isso numa masmorra já colocada.
  - **Portal escolhido pelo admin:** embaixo de uma capital o pórtico não é montado sozinho; use `/frx masmorra portal <id>` em pé no lugar. O comando também serve para mudar o portal de qualquer masmorra enterrada.

- **Casas de verdade nas capitais** (`reinos/Casas.java`, 15/11): as casas coladas são maquetes maciças, com um vão fechado de 1 bloco no nível do chão. `/frx casas <reino>` transforma cada vão em cômodo: cava 2 blocos para cima, põe piso de tábua, abre uma porta para o lado mais livre, uma cama e, em 60% das casas, um baú de saque do reino. `/frx casas desfazer` devolve tudo (até o servidor reiniciar). Em Lubelius: 89 casas abertas; 44 ficaram fechadas por não terem um lado livre para a porta.

- **Mestre de Obras** (papel `pedreiro`, 15/11): NPC que vende blocos essenciais em pilhas (toras, tábuas, pedra, vidro, tijolo, lã, tochas, baú, fornalha, cama…), com o desconto da reputação. Lista fixa em `Lojas.BLOCOS`. Em capitais já povoadas, coloque com `/frx npc aqui pedreiro`.
- **Banco:** o saque só tira do cofre o que cabe na bolsa (antes, com o inventário cheio, as moedas caíam no chão e sumiam); depósitos e saques ficam no log do servidor.
- **Dormir pula a noite** mesmo com o ciclo do dia alterado; **missão urgente** também vai para o chat; **portal (gate)** mostra distância e direção no chat e é marcado sozinho no mapa do JourneyMap (`cliente/MarcadorMapa.java`, por reflexão na API v2; some quando o portal fecha); **waystone global** liberada para qualquer jogador (`globalWaystoneSetupRequiresCreativeMode = false`).

## 0.41.0 — Balanceamento, túmulo e reinos menores

- **Monstros mais fracos:** por nível, +6% de vida (era 10%), +3,5% de dano (era 6%) e +0,2 de armadura (era 0,25). Dificuldade do servidor de *hard* para *normal*.
- **Perigo por nível:** monstro acima do seu nível bate +8% por nível de diferença (era 15%), com teto de ×2,2 (era ×3).
- **Elite:** vida ×1,7 a ×2,5 (era ×2,2 a ×3,4) e dano +16% a +40% (era +30% a +70%).
- **Campeão:** vida ×4 (era ×6) e dano ×1,5 (era ×1,8).
- **Jogador forte por perto:** chance de Elite ×1,5 e de Campeão ×2 (era ×2 e ×3). O nível sobe 1/3 da diferença (era metade), até a faixa + 5 (era + 10).
- **Jogador:** +0,4 de vida por nível (nível 30 ≈ +6 corações; máximo +20 corações).
- **Túmulo:** GraveStone Mod (`gravestone-mod`, via `pack\pendentes.txt`). Ao morrer, os itens ficam num túmulo no local.
- **Tempest** reduzido para 52×35 chunks (x −288 a 543, z −272 a 287), centrado no coliseu e na praça.
- **Dwargon** reduzido para 20×20 chunks (igual ao Oeste), em volta do trono, dos NPCs e do poço de entrada.
  - Os originais estão em `server\frxworld\reinos-originais\`; script em `tools\reduzir_reino.py`.
  - Precisa de mundo novo.
- **Casinhas dos NPCs** (`reinos/Oficios.java`): cada ofício ganha uma barraca 5×5 temática com balcão, e o NPC fica dentro dela, de frente para a praça.
  - Os ofícios são mercador, comprador, ferreiro (forja), armeiro, banqueiro, sacerdote, sábio, cartógrafo e taverneiro.
  - As barracas formam um anel em volta da praça (raio 12, fugindo das ruas), ou usam as posições fixas do `posicoes.json` quando há espaço. Os guardas passaram para o raio 18.
  - Não dá para quebrar uma barraca fora do criativo (registro em `<mundo>/frx_oficios.txt`).
- **Tesouros nas masmorras** (`reinos/Masmorras.java`):
  - Ao colar uma masmorra fixa (como a Fábrica Ancestral sob o coliseu), 5 baús do Lootr são espalhados pelas salas, com a tabela `frxworld:chests/masmorra`. Ela traz diamantes, ouro, netherite, livros encantados, tomos e equipamento encantado, e tem raridade de masmorra.
  - Ao derrotar o chefe, aparece um baú de prêmio onde ele caiu (`masmorra_chefe`: lingote de netherite, tomo arcano, maçã encantada e mais).
  - `/frx masmorra baus` põe 5 baús nas salas em volta de quem rodou, para mundos já gerados.
- **Servidor offline** (TLauncher/Shiginima): `online-mode=false`; o acesso continua só pelo convite, preso ao nick (sem senha). Até usar o convite: sem chat e sem comandos. O kit do amigo agora pergunta o launcher.
- **Cambista** nas capitais (troca de moedas sem taxa) e **Rings of Ascension**: anéis comuns nos baús das masmorras e um anel forte garantido no baú do chefe.
- **Backup automático:** o atalho Desligar guarda o mundo (zip) e o banco do painel (SQL) em `backups\`, mantendo os 7 mais recentes.
- Painel: migração `20261110200000_balanco_monstros` aplica os novos valores e volta as chances de Elite/Campeão para 5% e 0,5%.
- AutoModpack: os mods dos amigos baixam sozinhos ao entrar no servidor (kit `amigos/INSTALAR.bat`).
- **Menu do banqueiro** (`TelaBanco`): clique abre a tela com saldo do cofre e da bolsa, abas Depositar / Sacar / Câmbio (Shift = de 10 em 10). O cambista abre a mesma tela só com o câmbio.
- **Menu do ferreiro e do armeiro** (`TelaForja`): aba para aprimorar/reforjar (custo, material, chance, escolha do item pela barra) e aba Loja com as ofertas do NPC. Agachado + clique ainda abre a troca clássica.
- Rede: pacotes `AbrirMenu`/`AcaoMenu`, protocolo 11 (cliente e servidor precisam do mesmo jar).
- Painel: botão **Tornar admin** na ficha; raça na ficha; editor das **faixas de nível por dimensão** (Progressão → Zonas); telas de Masmorras, Chefes de mods, Habilidades e Eventos redesenhadas; skin do Cambista em NPCs; fichas duplicadas do mesmo nick são unidas no login.
- **Lojas pelo nível do jogador** (`reinos/Lojas.java`, `reinos/Catalogo.java`):
  - Ferreiro (armas/ferramentas) e armeiro (armaduras): patamares Nv 1 ferro, 10 ferro Incomum +2, 20 diamante, 30 diamante Raro +3, 40 netherite, 50 netherite Épico +5. Aparecem o patamar do jogador e o anterior.
  - Mais o "estoque do dia": até 12 armas/armaduras dos outros mods, medidas pelo dano/defesa (só itens com receita e até o nível 60), com preço pela força.
  - Mercador: cada item do painel (Economia) tem **nível mínimo**; migração `20261111230000_loja_nivel` define níveis para os itens antigos, pausa armas/escudo/arco e cria itens de nível alto.
  - **Cozinheiro** (NPC novo, `/frx npc aqui cozinheiro`): só comida, de todos os mods, 18 itens por dia conforme o nível.
- **Janela de Sistema** (`progressao/Sistema`, `cliente/HudSistema`, pacote `AvisoSistema`): caixa azul no topo da tela para subir de nível, missão concluída, conquista/título, prova e promoção.
- **Guilda de Aventureiros** (`progressao/Guilda`, `cliente/TelaGuilda`): NPC **recepcionista** (`/frx npc aqui recepcionista`), item **Cartão de Aventureiro** (`frxworld:cartao_aventureiro`) e **prova de promoção**. O rank F–S agora só sobe na prova: nível mínimo do rank (aba Progressão → Ranks) + derrotar o Examinador em 3 min sem morrer; falhou, espera 10 min. Prêmio: 2 ouro por degrau. Protocolo de rede 12.
- **Habilidade Única** (`habilidades/Unica`): cada jogador desperta uma das 10 passivas no primeiro login (Grande Sábio, Devorador, Fúria do Dragão, Corpo de Aço, Passo Sombrio, Olho do Falcão, Sangue Real, Filho do Vento, Segunda Chance, Instinto do Caçador). Evolui do nível 1 ao 5 com monstros abatidos (50 / 200 / 600 / 1500). `/unica` mostra; admin: `/frx unica sortear` ou `/frx unica <id>`.
- **Portais de masmorra** (`reinos/Portais`): a cada 40 min, 50% de chance de abrir um portal com rank perto da capital mais próxima de um jogador. Chegar a 6 blocos começa a incursão (3 ondas + Guardião, monstros do nível do rank, listas da aba Invasões). Fechou: XP e moedas para quem estiver por perto. 12 min sem fechar (ou 8 min de luta): o portal se rompe e começa uma invasão na capital. `/portal` mostra onde está; admin: `/frx portal aqui [rank]`, `/frx portal fechar`.
- **Apresentação de chefe** (`cliente/HudChefe`): ao ver um chefe pela primeira vez (até 32 blocos), barras de cinema, nome grande e música de batalha até ele morrer ou ficar para trás. O Guardião do Portal agora conta como chefe.
- **Fases de chefe** (`progressao/Combate`): aos 66% e 33% da vida o chefe enfurece (velocidade; depois força e resistência) e quem está a até 48 blocos é avisado na janela de Sistema.
- **Dano flutuante** (`cliente/DanoFlutuante`, pacote `NumeroDano`): números saltam das criaturas quando um jogador bate (branco; dourado com "!" se tirar 30% da vida de uma vez) ou apanha (vermelho).
- **Título acima da cabeça**: o título escolhido em `/titulo` aparece numa linha roxa sobre o nome dos outros jogadores (`SyncNivel` leva o título). Protocolo de rede 13.
- **Abertura isekai no primeiro login** (`acesso/Abertura`, `cliente/TelaAbertura`): assim que o convite é aceito, tela preta com as falas do Sistema digitadas letra a letra, depois escolha de raça, escolha de classe, o despertar da Habilidade Única e o nascimento na capital inicial (que também vira o ponto de renascimento). O jogador fica imune enquanto a cena roda; a habilidade única deixa de ser anunciada no chat para quem ainda vai ver a abertura. Teste com `/frx abertura`. Painel: **Convites → Abertura do primeiro login** (ligar/desligar, capital e as falas).
- **Missões e conquistas saíram do inventário**: as abas Missões e Conquistas foram removidas da fileira do inventário (sobraram 6). As conquistas viraram o capítulo **Conquistas** do livro de Quests (28 quests agrupadas em Caçada, Presas, Chefes, Jornada, Ofício e riqueza, Os reinos e Cicatrizes), ligadas por advancement `frxworld:frx/conq_<id>` que o `Marcos` concede — inclusive para quem já tinha a conquista. As missões dos reis já estavam no capítulo "Os Oito Reinos".
- **Mercador com o menu do ferreiro**: clicar abre a mesma tela de loja, com a lista do painel filtrada pelo nível mais um estoque do dia de até 10 utilidades dos mods (itens com receita que não são arma, armadura, comida nem bloco; épico fica de fora).
- **Comprador aceita qualquer item** (`reinos/Avaliador`, `cliente/TelaVender`): preço automático pela raridade do jogo, pela raridade/forja do FRX, encantamentos e desgaste; moedas e o Cartão da Guilda não são aceitos. "Vender tudo" pede confirmação.
- **Limite de ouro por dia**: o comprador paga até um teto que cresce com o nível (padrão 20 ouro + 3 por nível), mostrado na tela e renovado todo dia.
- **Evolução de raça** (`racas/Evolucao`): ao bater um marco (nível, honra com um reino, campanha ou chefe mundial), a raça da mesma família sobe um degrau com `/evoluir` — cena com partículas, título na tela e anúncio no servidor. A troca não cobra moedas.
- **Diálogo com retrato** (`reinos/Dialogos`, `cliente/TelaDialogo`): rei, taverneiro, sacerdote, sábio e cartógrafo abrem uma caixa de fala com o NPC desenhado ao vivo, texto digitado letra a letra e opções de resposta (missão, reino, quadro, loja). Agachado + clique mantém a troca antiga.
- Painel: **Economia → Comprador** (preços por raridade, fração de venda, bônus de encantamento e o teto diário) e **NPCs e skins → Falas da caixa de diálogo** (uma fala por linha, `{jogador}` vira o nick).
- Ranckor's Rings of Power: pendente (download manual do jar em `pack\mods`).

## 0.40.0 — Fase 2: FTB Quests, Apothic Attributes e dimensões

- Bat `pack/adicionar-mods-fase2.bat`: FTB Quests (+ Library, Teams, Architectury), Apothic Attributes (+ Placebo), Twilight Forest, Aether, Blue Skies (+ Structure Gel), Undergarden e Deeper and Darker.
- `atributos/Apoth.java`: ponte com o Apothic. Talentos, raças e equipamentos passam a dar crítico, esquiva, roubo de vida, perfuração e mineração.
- `Zonas`: faixas de nível por dimensão, título ao trocar de dimensão, fatores de perigo e de XP.
- `Ranks`: Elites e Campeões respeitam a faixa, e um jogador forte por perto gera desafios maiores.
- `Marcos`: 48 advancements ocultos para as tasks do FTB Quests.
- `tools/gerar_ftbquests.py`: 11 capítulos e 78 quests.

## 0.39.0 — Monstros com rank

- `progressao/Ranks.java`: Normal (`Zumbi Lv. 3`), Elite (★1–5 e traços), Campeão (prefixo, título e barra), Chefe e Chefe Mundial.
- Rótulos no mundo (`SyncRotuloMob`, `cliente/RotulosMob`) com a cor do nível relativa à sua.
- Painel: card "Monstros com rank" em Progressão.

## 0.38.0 — Raças

- 16 raças (`racas/DefRacas`, `racas/RacasJogador`), principal + secundária, compatibilidade, troca com custo progressivo, disfarce das lendárias.
- Visual: `CamadaRaca`, aura (`ClienteRacas`), tela `TelaRacas`, sufixo no nome e no TAB.
- NPCs: 7 novos tipos de morador (beastkin, halfling, oni, vampiro, dragonoide, espírito, gigante), 35 skins novas e misturas por reino.
- Comandos `/raca` e `/frx raca`.

## 0.33.0 – 0.37.0 — Poderes especiais, Exército de Sombras e waystones

- **Despertar** (tecla B): um poder inspirado em anime para cada classe, com barra própria.
- **Exército de Sombras** na tela de Habilidades: invocar e recolher uma ou todas, libertar, 3 atalhos + Z.
- **Waystones dos reinos:** 5 por reino (centro + 4 portões), colocadas quando o chunk carrega.
- O mod envia a lista de itens ao painel (ícones e nomes nos seletores).
- Protocolo de rede **8** na 0.37 (hoje **10**).
- Painel no mesmo período: Missões, Economia, Reinos e Dungeons redesenhados.

## 0.29.0 – 0.32.0

Sem registro detalhado nesta documentação.

## 0.28.0 — Rei visível, raças com skin, forja e banqueiro

- NPCs nascem ao entrar na caixa do reino; comando `/rei`.
- Moradores com 7 raças e skins próprias (`RenderRacas`).
- Linha de raridade/aprimoramento no lore dos itens.
- Banqueiro protegido da conversão em guarda.
- Mobs do Ice and Fire fora dos reinos.

## 0.27.x — Baús por reino, saque de masmorra, Dwargon maior

- `BausReino` (Lootr, nível de saque por reino).
- `RaridadeLoot` nos baús de masmorra.
- Dwargon com 56×60 chunks.
- Menus no estilo da aba Habilidades e teclas configuráveis.

## 0.26.x — Campanhas dos 3 reinos novos e masmorras assíncronas

- Campanhas de Oeste, Lubelius e Lordes Demônios; nomes dos NPCs.
- Masmorras coladas de forma assíncrona, sem travar o servidor.
- Abas com ícone.

## 0.25.0 — Classe Assassino

Usa Energia; bônus pelas costas e contra alvos feridos. Talentos Letalidade, Sangria e Fantasma.

## 0.24.0 — Floresta Sombria em volta de Tempest

## 0.23.0 — Janela de Status (teclas E / K)

Abas Inventário, Status, Habilidades, Talentos, Reinos, Missões, Conquistas e Grupo. Protocolo **5**.

## Até 0.22.0 — Fundação

- Fases 0–2: painel, convites, servidor e pack; XP, níveis, ranks e morte; missões, quadro e taverna.
- Dungeons próprias (catacumba em 3 camadas), chefes de mods (0.8), eventos de estrada (0.9), classes, mana e habilidades (0.10).
- Reinos e capitais com NPCs (0.11–0.12), reputação e campanhas, painel de reinos (0.22).
