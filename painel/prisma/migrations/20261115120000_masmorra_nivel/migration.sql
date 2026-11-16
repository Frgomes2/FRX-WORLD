-- Nível fixo por masmorra (0 = segue a zona) e a masmorra de Lubelius
ALTER TABLE "Masmorra" ADD COLUMN "nivel" INTEGER NOT NULL DEFAULT 0;

INSERT INTO "Masmorra" ("id", "nome", "chefe", "estrutura", "x", "z", "nivel", "ativo", "ordem", "refazer", "atualizadoEm")
SELECT 'lubelius-abismo', 'Arena Flamejante sob Lubelius', 'Ignis', 'cataclysm:burning_arena|cataclysm:soul_black_smith', 1792, 1600, 55, true, 6, false, now()
WHERE NOT EXISTS (SELECT 1 FROM "Masmorra" WHERE "id" = 'lubelius-abismo');
