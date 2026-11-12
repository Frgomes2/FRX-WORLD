-- Loja do mercador por nível do jogador
ALTER TABLE "ItemLoja" ADD COLUMN "nivelMin" INTEGER NOT NULL DEFAULT 1;

-- itens que já existiam ganham um nível mínimo
UPDATE "ItemLoja" SET "nivelMin" = 10 WHERE "loja" = 'MERCADOR' AND "item" IN ('minecraft:saddle', 'minecraft:name_tag', 'minecraft:firework_rocket');
UPDATE "ItemLoja" SET "nivelMin" = 15 WHERE "loja" = 'MERCADOR' AND "item" IN ('minecraft:ender_pearl', 'minecraft:potion#strong_healing', 'minecraft:experience_bottle');
UPDATE "ItemLoja" SET "nivelMin" = 20 WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:golden_apple';

-- armas, escudo e arco passam a ser do ferreiro/armeiro (ficam pausados no mercador)
UPDATE "ItemLoja" SET "ativo" = false WHERE "loja" = 'MERCADOR' AND "item" IN ('minecraft:iron_sword', 'minecraft:iron_pickaxe', 'minecraft:bow', 'minecraft:shield');

-- itens novos para níveis mais altos (só se ainda não existirem)
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-0', 'MERCADOR', 'minecraft:golden_carrot', 8, 60, 10, true, 900, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:golden_carrot');
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-1', 'MERCADOR', 'minecraft:potion#strength', 1, 150, 20, true, 901, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:potion#strength');
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-2', 'MERCADOR', 'minecraft:potion#regeneration', 1, 150, 20, true, 902, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:potion#regeneration');
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-3', 'MERCADOR', 'minecraft:potion#swiftness', 1, 80, 10, true, 903, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:potion#swiftness');
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-4', 'MERCADOR', 'minecraft:diamond', 1, 400, 25, true, 904, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:diamond');
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-5', 'MERCADOR', 'minecraft:experience_bottle', 16, 200, 30, true, 905, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:experience_bottle');
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-6', 'MERCADOR', 'minecraft:totem_of_undying', 1, 2500, 35, true, 906, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:totem_of_undying');
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-7', 'MERCADOR', 'minecraft:netherite_scrap', 1, 1500, 40, true, 907, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:netherite_scrap');
INSERT INTO "ItemLoja" ("id", "loja", "item", "qtd", "preco", "nivelMin", "ativo", "ordem", "criadoEm", "atualizadoEm")
SELECT 'nivel-8', 'MERCADOR', 'minecraft:enchanted_golden_apple', 1, 5000, 50, true, 908, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "ItemLoja" WHERE "loja" = 'MERCADOR' AND "item" = 'minecraft:enchanted_golden_apple');
