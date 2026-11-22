# Personagens

[← Índice](../README.md) · Código: `acesso/Personagens.java`, `cliente/TelaPersonagens.java`, `cliente/Reconexao.java`

Cada conta pode ter até 3 personagens.

## O que é de cada um e o que é da conta

- **De cada personagem:** tudo o que o jogo guarda no arquivo do jogador (inventário, equipamento, posição, vida, XP) e o que o mod guarda nele (raça, classe, nível, habilidades, talentos, rank da Guilda, missões, contratos, bolsa de moedas), mais os clãs e o nome.
- **Da conta:** banco, terrenos, anúncios e valores a receber no mercado, progressos do Minecraft, FTB Quests e o mapa do JourneyMap.

## Como o jogador usa

- Ao entrar no servidor aparece a tela de escolha, 3 segundos depois do login. A tecla F8 (configurável em Controles → FRX-WORLD) abre a mesma tela.
- **Proteção na entrada:** do login até fechar essa tela, o jogador não toma dano (menos o de cair no vazio) e os monstros não o escolhem como alvo. A proteção acaba ao fechar a tela, trocar de personagem, andar mais de 1,5 bloco, atacar algo, sair ou depois de 5 minutos. Abrir a tela pelo F8 no meio do jogo não protege.
- Vaga livre → **Criar personagem** → nome (3 a 16 letras ou números). O jogo entra de novo e a abertura (raça e classe) roda para ele.
- **Jogar** troca para outro personagem; **Nome** renomeia.
- A troca desconecta e o jogo conecta de novo sozinho em cerca de 2 segundos.

## Arquivos

- `<mundo>/frx_personagens/<uuid>.json`: vaga em uso, nomes e retrato (classe, raça, nível, rank) de cada vaga.
- `<mundo>/frx_personagens/<uuid>-<vaga>.dat`: o arquivo do personagem que não está em uso; `.anterior` é a cópia da troca anterior.
- O personagem em uso é o `playerdata/<uuid>.dat` normal do jogo.

## Painel e site

- O mod manda o retrato de cada personagem (nome, classe, raça, nível, rank, identidade) para `POST /api/servidor/personagens`; fica na tabela `Personagem`.
- A ficha do admin mostra os personagens da conta; a ficha abaixo deles é sempre a do personagem em uso.
- O site mostra os personagens na ficha pública e usa a identidade do personagem em uso para a Torre, a temporada e os clãs.

## Limites conhecidos

- As ações de admin (dar item, mudar nível, editar a bolsa) só valem para o personagem em uso: os outros estão guardados em arquivo, fora do jogo.
- A ficha de um personagem fora de uso é o retrato da última vez em que ele jogou. Personagem que ainda não jogou depois desta versão não tem ficha guardada.
- Mortes e conquistas do retrato são as da conta no momento em que ele foi guardado.
- Placar da Torre e temporada são de cada personagem; o que existia antes ficou com a vaga 1.
