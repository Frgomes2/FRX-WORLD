-- AlterTable
ALTER TABLE "Missao" ADD COLUMN     "recompensaMoedas" INTEGER NOT NULL DEFAULT 20;

UPDATE "Missao" SET "recompensaMoedas" = GREATEST(5, "recompensaXp" / 5);
