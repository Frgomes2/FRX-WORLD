-- AlterTable
ALTER TABLE "Jogador" ADD COLUMN     "classe" TEXT NOT NULL DEFAULT '';

-- CreateTable
CREATE TABLE "Habilidade" (
    "id" TEXT NOT NULL,
    "codigo" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "descricao" TEXT NOT NULL DEFAULT '',
    "classe" TEXT NOT NULL,
    "tipo" TEXT NOT NULL,
    "elemento" TEXT NOT NULL DEFAULT 'FISICO',
    "icone" TEXT NOT NULL DEFAULT 'minecraft:book',
    "custo" INTEGER NOT NULL DEFAULT 20,
    "cooldown" DOUBLE PRECISION NOT NULL DEFAULT 5,
    "dano" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "alcance" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "raio" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "duracao" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "efeito" TEXT NOT NULL DEFAULT '',
    "potencia" INTEGER NOT NULL DEFAULT 0,
    "nivelReq" INTEGER NOT NULL DEFAULT 1,
    "ultimate" BOOLEAN NOT NULL DEFAULT false,
    "aprendizado" TEXT NOT NULL DEFAULT 'NIVEL',
    "ativa" BOOLEAN NOT NULL DEFAULT true,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "Habilidade_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Habilidade_codigo_key" ON "Habilidade"("codigo");
