-- Ficha completa do jogador (aba Jogadores)
ALTER TABLE "Jogador" ADD COLUMN "detalhe" JSONB;
ALTER TABLE "Jogador" ADD COLUMN "detalheEm" TIMESTAMP(3);
ALTER TABLE "Jogador" ADD COLUMN "online" BOOLEAN NOT NULL DEFAULT false;
