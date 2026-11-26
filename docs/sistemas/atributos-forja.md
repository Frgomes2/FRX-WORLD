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

- **Ferreiro** (agachar + clicar): aprimora de **+1 até +10**, cobrando moedas e minério. **De +11 a +15 só para Rank A ou S** da Guilda, com os materiais das armaduras lendárias: +11 25 ouro + 2 Essências Abissais (30%), +12 35 ouro + 3 (25%), +13 50 ouro + 4 (20%), +14 70 ouro + 2 Fragmentos Celestiais (15%), +15 100 ouro + 3 (10%). Esses níveis não vêm do painel (`Forja.custoAprimorar`); na tela da forja, quem não tem o rank vê o aviso.
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

## Armaduras lendárias (`armas/Lendarias.java`, `aventura/ConjuntosLendarios.java`)

- Três conjuntos depois do netherite, feitos na **mesa de ferraria** (o material vai no espaço do molde e no do acréscimo: 2 por peça; mantém encantamentos, forja e runas). Receitas em `data/frxworld/recipes/<conjunto>_<peça>.json`.

| Conjunto | Vem de | Defesa (elmo/peitoral/perneiras/botas) | Resistência | Classe | Bônus com as 4 peças |
|---|---|---|---|---|---|
| Draconiana | netherite + Escama Draconiana | 3/8/6/3 | 4 | B | imune a fogo; quem bate pega fogo 4 s |
| Abissal | Draconiana + Essência Abissal | 4/9/7/4 | 5 | A | +4 corações; 12% de esquiva |
| Celestial | Abissal + Fragmento Celestial | 5/11/9/5 | 6 | S | sem dano de queda; efeitos ruins somem; Regeneração II |

- **Materiais:** Escama — dragões do Ice and Fire com 100+ de vida (2–4 para cada um a até 48 blocos), chefes de outros mods de nível 40–54 (1–2, no prêmio) e chefes da Torre dos andares 30–49 (2, primeira vez). Essência — chefes de outros mods de nível 55+ (2) e chefes da Torre dos andares 50–79 (1; 2 a partir do 70). Fragmento — Dragão do Fim (4 por ajudante) e chefes da Torre do andar 80+ (1).
- A classe sai da regra de `ClassesEquip` (defesa + resistência). Descrição dos itens mostra o bônus e de onde vem cada material. Livro "Depois do Netherite" na biblioteca; o armeiro comenta o caminho.

## Bênção da Capitã Hinata (2.0.12)

Na sábia de Lubelius, Hinata Sakaguchi, opção "Pedir a bênção para a minha armadura". A tela mostra as quatro peças vestidas.

- Cada peça de armadura ganha **um** bônus, o da classe do jogador, em graus I a V. Em quatro peças, o bônus soma.
- Chance e preço por grau: I 100% e 10 ouro · II 80% e 20 · III 60% e 40 · IV 40% e 80 · V 20% e 150. O pagamento sai da bolsa e, se faltar, do banco.
- Grau IV: 2 Escamas Draconianas. Grau V: 2 Essências Abissais. Os dois exigem reputação **Honrado** (500) com Lubelius.
- Falhou: perde o pagamento e o material; a peça continua no grau que estava.
- A bênção guarda a classe. Se o jogador mudar de classe, ela fica inativa; abençoar de novo recomeça do grau I na classe nova.
- Feito para ser abençoado na armadura de verdade: o visual continua livre com o Cosmetic Armor Reworked.

| Classe | Bônus por grau, em cada peça | Grau V em 4 peças |
|---|---|---|
| Guerreiro | +0,5% de roubo de vida | +10% |
| Arqueiro | +0,6% de dano de flechas e projéteis | +12% |
| Mago | +0,6% de dano das habilidades | +12% |
| Clérigo | +1% de cura das habilidades | +20% |
| Ladino | +0,5% de chance de esquiva | +10% |
| Assassino | +0,6% de chance de crítico | +12% |
| Invocador | +0,8% de força das invocações | +16% |
| Paladino | +0,3 de armadura | +6 |
| Bardo | +1% de regeneração de recurso | +20% |
| Monge | +0,4% de velocidade | +8% |
| Druida | 0,6% menos recarga das habilidades | 12% menos |
| Samurai | +0,6% de dano corpo a corpo | +12% |
| Cartomante | 0,6% menos custo de recurso | 12% menos |

## Lâminas do Epic Samurais (3.0.2)

No mod original as katanas batem como espada de netherite. O core mod soma dano fixo na mão principal (aparece na descrição):

| Arma | Dano a mais |
|---|---|
| Katana | +3 |
| Katana de netherite | +4 |
| Katanas de yokai (Oni, Kitsune, Onibi, Akaname, Jorogumo) | +5 |
| Wakizashi / de netherite | +2 / +3 |
| Odachi / de netherite | +3 / +4 |

O Samurai ainda soma os 10% da Katana e o Saque. Para mudar os valores: `BONUS` em `armas/LaminasSamurai.java`.

## Armaduras do Epic Samurais (3.0.5)

Bônus por peça, somado ao do mod (`armaduras` em `armas/LaminasSamurai.java`):

| Material | Armadura | Resistência | Vida | Contra repulsão |
|---|---|---|---|---|
| Ferro, ouro, aço, chapéu de palha | +1 | +0,5 | — | — |
| Diamante, rubi, jade, água-marinha, prata, ônix, ametista, quartzo | +2 | +1,5 | +meio coração | — |
| Netherite, Battlemage, Warden | +3 | +2,5 | +1 coração | +5% |

