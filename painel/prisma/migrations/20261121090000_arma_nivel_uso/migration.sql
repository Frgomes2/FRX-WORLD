-- Nível de uso dos itens do Códex: opção por item e o maior nível já alcançado (site)
ALTER TABLE "ArmaCodex" ADD COLUMN "nivelUso" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "ArmaCodex" ADD COLUMN "nivelUsoMax" INTEGER NOT NULL DEFAULT 0;
