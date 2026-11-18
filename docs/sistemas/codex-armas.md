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
| Evolui com o jogador | A arma fica vinculada a quem recebeu e ganha "dano por nível" a cada nível dele |
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

## Aparência (pacote de recursos)

- O painel gera um pacote de recursos só com as armas que têm desenho: textura e modelo em `assets/frxworld/.../codex/<código>` e, para cada item base, o modelo original com um `override` por `CustomModelData` (880000 + número da arma).
- O pacote é servido em `/api/publico/recursos` (endereço público em `SITE_URL`, padrão `https://craft.frgomes.com.br`). O mod recebe o endereço e o SHA-1 junto com a lista de armas e chama `sendTexturePack` para quem entra; se o pacote mudar, reenvia a quem está online.
- O jogador precisa aceitar o pacote para ver o desenho; sem aceitar, vê o item base. Arcos e bestas com desenho próprio perdem a animação de puxar.
