-- Limite de unidades de cada disco no mundo (0 = sem limite) e quantas já foram vendidas
ALTER TABLE "Disco" ADD COLUMN "limite" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "Disco" ADD COLUMN "emitidas" INTEGER NOT NULL DEFAULT 0;
