ALTER TABLE "ArmaCodex" ADD COLUMN IF NOT EXISTS "inquebravel" BOOLEAN NOT NULL DEFAULT false;
UPDATE "ArmaCodex" SET "inquebravel" = true WHERE "codigo" = 'enigma_premio';
