# 01 — Visão geral

[← Índice](README.md)

## O que é

O FRX-WORLD é um servidor de Minecraft 1.20.1 (Forge) que funciona como um MMORPG: raças, classes, níveis, reinos com reis e NPCs, dungeons, chefes mundiais, economia com moedas e lojas, além de uma campanha guiada.

Ele tem três peças:

| Peça | Pasta | Papel |
|---|---|---|
| Core mod `frxworld` | `core-mod/` | Toda a lógica de RPG dentro do jogo |
| Painel | `painel/` | Site Next.js para o admin configurar tudo e acompanhar jogadores |
| Pack | `pack/` | Lista de mods (packwiz) baixada pelo servidor e pelos jogadores |

## Pilares de design

- **Progressão por camadas:** raça e classe definem a base; atributos, talentos e forja refinam; equipamentos e dimensões dão o desafio.
- **O mundo reage ao nível:** cada dimensão tem uma faixa de nível. Abaixo dela é perigo extremo, dentro é normal, acima o jogador domina e desafios especiais aparecem.
- **Três fontes de missão com papéis distintos:**
  - FTB Quests: campanha principal, exploração e desbloqueios.
  - Reis: campanhas narrativas e políticas de cada reino.
  - Taverna: contratos repetíveis.
- **Tudo configurável pelo painel**, sem reiniciar sempre que possível (`/frx recarregar`).
- **O visual não entrega o poder:** um Rei Demônio pode andar disfarçado; NPCs e jogadores experientes reconhecem o título.

## Cadeia de sistemas

```
Raças → Classes → Atributos → Talentos → Forja → Equipamentos
      → Dimensões → Quests → Reinos → Taverna → Economia / Reputação
```

## Faixas de nível por dimensão

| Dimensão | Faixa de nível |
|---|---|
| Superfície (Overworld) | 1–60, sobe com a distância do spawn (250 blocos por nível) |
| Floresta do Crepúsculo (Twilight Forest) | 10–25 |
| Nether | 55–80 (Rank C) |
| Undergarden | 20–35 |
| Everbright (Blue Skies) | 25–40 |
| Aether | 30–45 |
| O Fim (End) | 75–100 (Rank A) |
| Everdawn (Blue Skies) | 35–50 |
| O Outro Lado (Deeper and Darker) | 45–60 |

Detalhes em [sistemas/progressao-zonas.md](sistemas/progressao-zonas.md).
