# Atributos, forja e equipamentos

[← Índice](../README.md) · Código: `armas/` (Armas, Forja, Raridade, RaridadeLoot), `atributos/Apoth.java` · Painel: menu **Forja**

## Raridade

Vale para armas, ferramentas e armaduras.

| Raridade | Cor | Dano extra | Por peça de armadura: armadura / resistência / vida |
|---|---|---|---|
| Comum | branco | 0% | 0 / 0 / 0 |
| Incomum | verde | +8% | 0,5 / 0 / 0 |
| Raro | azul | +15% | 1,0 / 0,5 / 0 |
| Épico | roxo | +25% | 1,5 / 1,0 / 1,0 |
| Lendário | dourado | +40% | 2,0 / 1,5 / 2,0 |

- Os baús de masmorra (`RaridadeLoot`) trazem itens de raridade melhor e extras.
- **Armeiro** (agachar + clicar com o item na mão): **reforja** a raridade. Nunca piora.

## Aprimoramento +N (Ferreiro)

- **Ferreiro** (agachar + clicar): aprimora de **+1 até +10**, cobrando moedas e minério.
- Cada nível dá **+6% de dano** (armas) ou **+0,3 de armadura** (por peça).
- Chance de sucesso por nível alvo: +1 a +4 = 100% · +5 90% · +6 80% · +7 70% · +8 55% · +9 45% · +10 25%. Os valores podem mudar no painel.
- Se falhar, perde o material, mas o item **não quebra**.
- O item ganha a linha `★ Raridade · Aprimoramento +N · +X% de dano` no lore (tag `frx_lore`).
- Admin: `/frx raridade <0-4>` e `/frx forja <0-10>` no item da mão.

## Apothic Attributes

O FRX usa **só o Apothic Attributes** (sem o Apotheosis completo). Ele dá a infraestrutura de atributos, e a progressão continua nossa.

- Ponte por reflexão em `atributos/Apoth.java`:
  - `ativo()` diz se o mod está carregado;
  - `attr(nome)` busca o atributo;
  - `definir(entidade, atributo, chave, valor)` aplica um modificador ADDITION com UUID fixo.
- Namespace na 1.20.1: **`attributeslib`**.
- Atributos usados: `crit_chance`, `crit_damage`, `life_steal`, `dodge_chance`, `armor_pierce`, `arrow_damage`, `draw_speed`, `healing_received`, `mining_speed`.
- **Sem o Apothic** (`ativo() == false`), os handlers próprios do FRX (crítico, esquiva, roubo de vida dos talentos e raças) continuam funcionando.
- **Com o Apothic**, eles saem do caminho (retornam cedo) para não contar em dobro.

### O que cada sistema concede

| Fonte | Atributos |
|---|---|
| Talentos | Chance crítica, roubo de vida, esquiva, dano de flecha |
| Raças | Esquiva (Halfling), roubo de vida (Vampiro), cura recebida (Anjo, Rei Demônio), mineração (Anão), dano de arco (Elfo) |
| Armas por raridade (Comum → Lendário) | Chance crítica 0 / 1 / 3 / 5 / 8% (+2% em +5 e +3% em +10). Dano crítico 0 / 0 / 5 / 10 / 25% + 1% por +N |
| Arma Lendária | +3% de roubo de vida |
| Arma +7 ou mais | Perfuração de armadura = N − 6 |
| Arcos | Velocidade de puxar e crítico |
| Ferramentas | Mineração +5% por raridade + 4% por +N |
| Armaduras | Esquiva: Épica 1%, Lendária 2%, +1% em +10 |

## Onde entra na cadeia

```
Raças → Classes → Atributos → Talentos → Forja → Equipamentos → ...
```

Raça e classe dão a base, os talentos ajustam e a forja/raridade escala com o equipamento. O nível das dimensões cobra tudo isso. Veja [progressao-zonas.md](progressao-zonas.md).
