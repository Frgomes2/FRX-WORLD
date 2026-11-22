# Códex de Armas

[← Índice](../README.md) · Mod: `armas/Codex.java` · Painel: `/admin/codex` (`src/app/admin/codex/`, `src/lib/codex.ts`) · Rotas: `/api/servidor/codex`, `/api/publico/recursos`

Armas únicas do servidor, criadas no painel. Não caem de monstro nem de baú.

## O que se define no painel

| Campo | Efeito |
|---|---|
| Item base | Dá o tipo e o comportamento (espada, machado, arco, peça de armadura). Pode ser de qualquer mod |
| Rank | Classe de equipamento (F a S): só usa quem tem esse rank da Guilda |
| Raridade | Comum a Lendário; soma o bônus normal da raridade |
| Dano extra | Dano fixo a mais (armadura a mais, se o item base for armadura) |
| Encantamentos | Lista `mod:encanto*nível`, separados por vírgula |
| Total no mundo | Quantas unidades podem existir (0 = sem limite) |
| Só uma por jogador | Cada jogador recebe no máximo uma |
| Ferreiro | À venda no ferreiro (armadura: armeiro), com preço e nível mínimo; aparece no topo da loja |
| Como o item evolui | Um de três: **não evolui**; **com o nível do jogador** (fica vinculada a quem recebeu e ganha "dano por nível" a cada nível dele); **com o uso** (nível do próprio item, ver abaixo) |
| Aparência | Desenho em pixels (16×16 ou 32×32) feito no site ou PNG importado; vazio = aparência do item base |

## Como o jogador consegue

- **Ferreiro ou armeiro:** aparece para quem tem o rank, o nível mínimo e ainda não tem a arma, enquanto houver unidades. A unidade de verdade (com número) nasce na compra.
- **Missão:** na recompensa da missão, adicione um item com o id `codex:<código>`.
- **Admin:** `/frx codex dar <jogador> <código>`.

## No jogo

- Tudo fica gravado no próprio item (`frx_codex`, `frx_codex_rank`, `frx_codex_dano`, dono, número), e a descrição mostra raridade, história, dano, "nº X de Y" e o vínculo.
- **Evolução:** a cada 2 s o mod confere as armas vinculadas no inventário do dono e recalcula `dano = base + por nível × nível`. Outro jogador não consegue bater com uma arma vinculada.
- **Estoque:** `<mundo>/frx_codex.json` guarda quantas saíram e para quem; o mod envia a contagem ao painel. "Zerar contagem" no painel (ou `/frx codex estoque <código> <n>`) devolve unidades ao total.
- Arma destruída ou perdida não volta para o total sozinha.

## Nível de uso (`armas/NivelUso.java`)

Para os itens com "Como o item evolui = com o uso". O nível é do próprio item e passa junto se ele trocar de dono.

- **Experiência da arma:** dano causado a monstros, contando só a vida que o monstro ainda tinha; o golpe final soma de novo a vida máxima dele.
- **Experiência da armadura:** cada peça vestida ganha com o dano recebido de monstros (golpe ou projétil de um monstro).
- **Não conta:** animais, jogadores, monstros de spawner (marcados ao nascer), dano sem monstro (queda, lava, cacto, afogamento) e quem está no criativo. Chefões (chefões do servidor ou vida máxima ≥ 150) valem 5 vezes.
- **Teto:** 25.000 de experiência por dia para cada item (vira à meia-noite de Brasília).
- **Curva (experiência total):** nível 1 = 5.000; 2 = 28.000; 3 = 76.000; 4 = 155.000; 5 = 269.000; 6 = 422.000; 7 = 620.000; 8 = 866.000; 9 = 1.163.000; 10 = 1.500.000. Com o teto diário, o nível 10 leva pelo menos 60 dias.
- **Bônus:** arma +2% de dano por nível; armadura −1% de dano recebido por nível de cada peça (no máximo −50% no total). Nível 5: o item passa a brilhar. Nível 10: a arma solta faíscas a cada golpe.
- **Ao subir:** som e aviso no chat para todos. O maior nível já alcançado vai ao painel (`nivelUsoMax`) e aparece no site, em Armas Únicas, em estrelas.
- **Descrição do item:** nível em estrelas, experiência até o próximo nível, quanto já ganhou hoje e como sobe e o que dá. Itens "com o nível do jogador" mostram "Evolui com o dono" e o nível do personagem.
- **NBT:** `frx_uso` (ligado), `frx_uso_xp`, `frx_uso_lv`, `frx_uso_dia`, `frx_uso_hoje`.
- Trocar o modo no painel vale também para os itens já entregues: ligar "com o jogador" vincula o item a quem está com ele; desligar tira o vínculo e o dano por nível.

## Aparência (pacote de recursos)

- O painel gera um pacote de recursos só com as armas que têm desenho: textura e modelo em `assets/frxworld/.../codex/<código>` e, para cada item base, o modelo original com um `override` por `CustomModelData` (880000 + número da arma).
- O pacote é servido em `/api/publico/recursos` (endereço público em `SITE_URL`, padrão `https://craft.frgomes.com.br`). O mod recebe o endereço e o SHA-1 junto com a lista de armas e chama `sendTexturePack` para quem entra; se o pacote mudar, reenvia a quem está online.
- O jogador precisa aceitar o pacote para ver o desenho; sem aceitar, vê o item base. Arcos e bestas com desenho próprio perdem a animação de puxar.
- O desenho do painel muda só o ícone. **Vestida**, uma armadura sempre aparece com a textura do material do item base. Para uma relíquia com visual vestido próprio, use como item base uma peça de `armas/Reliquias.java` (ex.: `frxworld:alvorecer_elmo`, `_peitoral`, `_perneiras`, `_botas`, de cristal facetado), que traz textura vestida e ícone próprios. Uma nova relíquia com visual próprio pede um material novo nessa classe e as texturas em `assets/frxworld/textures/models/armor/`.

## Inquebrável

A opção **Inquebrável (nunca desgasta)** deixa a arma sem perda de durabilidade (`Unbreakable` no item). Ligar ou desligar vale também para as unidades já entregues, que são consertadas na hora.

## Mudanças no painel valem para as armas já entregues

Se o dano extra ou o dano por nível de uma arma mudar no painel, as unidades que já têm dono são atualizadas sozinhas na próxima vez que o dono estiver online (o mod confere o inventário a cada 2 s, depois que o Códex recarrega, em até 1 minuto). O dono recebe o aviso "foi reforjada" com o dano novo.

## Armas Únicas no site

O site público tem a seção **Armas Únicas** (`#armas`, também no menu "Mais"): cada arma do Códex que já tem dono aparece com a aparência desenhada no painel, a raridade, quantas existem de quantas e o **primeiro dono**, com a data. O servidor grava o primeiro dono em `frx_codex.json` (`primeiro`: nome do personagem e quando) na entrega da primeira unidade e manda ao painel (`ArmaCodex.primeiroNome`, `primeiroEm`). Armas entregues antes desse registro usam o primeiro da lista de donos, sem data. A conquista também entra nas Crônicas.
