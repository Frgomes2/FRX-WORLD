-- Missões separadas por rank da Guilda (0 = F, 1 = E, 2 = D, 3 = C, 4 = B, 5 = A, 6 = S)
ALTER TABLE "Missao" ADD COLUMN "rankMin" INTEGER NOT NULL DEFAULT 0;

-- as missões que já existiam ganham o rank da faixa de nível delas
UPDATE "Missao" SET "rankMin" = CASE
  WHEN "nivelMin" >= 90 THEN 6
  WHEN "nivelMin" >= 70 THEN 5
  WHEN "nivelMin" >= 50 THEN 4
  WHEN "nivelMin" >= 35 THEN 3
  WHEN "nivelMin" >= 20 THEN 2
  WHEN "nivelMin" >= 10 THEN 1
  ELSE 0 END;
