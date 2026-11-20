-- Bestiário: quem derrotou cada criatura do Códex pela primeira vez (vazio = derrotada antes de existir este registro)
ALTER TABLE "CriaturaCodex" ADD COLUMN "primeiroNome" TEXT NOT NULL DEFAULT '';
ALTER TABLE "CriaturaCodex" ADD COLUMN "primeiroEm" TIMESTAMP(3);
