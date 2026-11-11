-- AlterTable
ALTER TABLE "Jogador" ADD COLUMN     "mortes" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN     "nivel" INTEGER NOT NULL DEFAULT 1,
ADD COLUMN     "rank" TEXT NOT NULL DEFAULT 'F',
ADD COLUMN     "xp" INTEGER NOT NULL DEFAULT 0;

-- CreateTable
CREATE TABLE "Configuracao" (
    "chave" TEXT NOT NULL,
    "valor" JSONB NOT NULL,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Configuracao_pkey" PRIMARY KEY ("chave")
);
