# FRX-WORLD — Documentação (índice principal)

> Modpack MMORPG para **Minecraft 1.20.1 / Forge 47.4.10**, com core mod próprio (`frxworld`) e painel administrativo em Next.js.
> Versão atual do core mod: **0.41.0**.

Este é o ponto de entrada de toda a documentação. Cada arquivo cobre um assunto; comece por aqui e siga os links.

## Mapa da documentação

| # | Documento | Para que serve |
|---|---|---|
| 01 | [Visão geral](01-visao-geral.md) | O que é o FRX-WORLD, pilares de design e a cadeia de sistemas |
| 02 | [Arquitetura](02-arquitetura.md) | Pastas do repositório, pacotes do mod, rede, painel, banco e integrações |
| 03 | [Operação](03-operacao.md) | Atalhos de ligar/desligar, compilação, pack (packwiz), versões, sincronização |
| 04 | [Comandos](04-comandos.md) | Todos os comandos de jogador e de admin (`/frx ...`) |
| 05 | [Painel administrativo](05-painel.md) | Menus do painel, o que cada tela faz, rotas de API |
| 06 | [Mods externos](06-mods-externos.md) | Mods do pack, dependências e a Fase 2 (FTB Quests, Apothic, dimensões) |
| 07 | [Changelog](07-changelog.md) | Histórico de versões do core mod |
| 08 | [Jogar com amigos](08-jogar-com-amigos.md) | Servidor no seu PC + túnel playit.gg, kit do amigo, convites e painel para outros admins (Cloudflare Tunnel) |

### Sistemas de jogo (`sistemas/`)

Na ordem da cadeia de progressão:

| Sistema | Documento |
|---|---|
| Raças (16 raças, principal + secundária) | [sistemas/racas.md](sistemas/racas.md) |
| Classes, habilidades, Despertar, Sombras e talentos | [sistemas/classes-habilidades.md](sistemas/classes-habilidades.md) |
| Atributos (Apothic), forja e equipamentos | [sistemas/atributos-forja.md](sistemas/atributos-forja.md) |
| Progressão, zonas, dimensões e monstros com rank | [sistemas/progressao-zonas.md](sistemas/progressao-zonas.md) |
| Quests: FTB Quests, marcos, taverna e campanhas | [sistemas/quests.md](sistemas/quests.md) |
| Reinos, NPCs, reputação e waystones | [sistemas/reinos.md](sistemas/reinos.md) |
| Dungeons, masmorras e chefes | [sistemas/dungeons-chefes.md](sistemas/dungeons-chefes.md) |
| Economia, moedas, lojas e banco | [sistemas/economia.md](sistemas/economia.md) |

## A cadeia de sistemas

```
Raças → Classes → Atributos → Talentos → Forja → Equipamentos
      → Dimensões → Quests → Reinos → Taverna → Economia / Reputação
```

O **FTB Quests** é a espinha dorsal da campanha. Os reis contam a história política e a taverna fornece os contratos repetíveis.

## Convenções desta documentação

- Idioma: português (pt-BR). Nomes de classes e pacotes Java aparecem como estão no código.
- Pacote base do mod: `br.com.frxcode.frxworld` (caminhos curtos como `racas/DefRacas.java` partem daí).
- Comandos de admin começam com `/frx`. Os de jogador têm nome próprio (`/raca`, `/banco`, `/sombra`...).
- Números de balanceamento citados aqui refletem o código da versão indicada no topo. Ao mudar o código, atualize o documento do sistema e o [changelog](07-changelog.md).
- Documento de design original (visão completa, em PDF): [FRX-WORLD_Documento_de_Design.pdf](FRX-WORLD_Documento_de_Design.pdf).

## Como manter

1. Mudou um sistema → atualize o arquivo dele em `sistemas/`.
2. Criou um comando → adicione em [04-comandos.md](04-comandos.md).
3. Nova tela no painel → [05-painel.md](05-painel.md).
4. Nova versão do mod → linha nova no [07-changelog.md](07-changelog.md) e a versão no topo deste índice.
