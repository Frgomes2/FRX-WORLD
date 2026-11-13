# 07 — Changelog do core mod

[← Índice](README.md)

Formato: versão, depois o que entrou. A versão do mod fica em `core-mod/gradle.properties`.

> **Situação dos testes (10/11/2026):** a última versão compilada e testada no PC é a **0.33.0** (`pack/mods/frxworld-0.33.0.jar`). As versões **0.34 a 0.40** ainda não rodaram no jogo. O roteiro de teste está no checklist "FRX-WORLD — Checklist de testes".

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
