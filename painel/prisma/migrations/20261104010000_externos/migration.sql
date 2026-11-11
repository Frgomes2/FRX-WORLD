-- CreateTable
CREATE TABLE "ChefeExterno" (
    "id" TEXT NOT NULL,
    "entidade" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "origem" TEXT NOT NULL DEFAULT '',
    "nivelRecomendado" INTEGER NOT NULL DEFAULT 1,
    "recompensaXp" INTEGER NOT NULL DEFAULT 0,
    "loot" JSONB NOT NULL DEFAULT '[]',
    "recargaHoras" INTEGER NOT NULL DEFAULT 0,
    "ativo" BOOLEAN NOT NULL DEFAULT true,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "ChefeExterno_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "EstruturaExterna" (
    "id" TEXT NOT NULL,
    "estrutura" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "origem" TEXT NOT NULL DEFAULT '',
    "nivelRecomendado" INTEGER NOT NULL DEFAULT 1,
    "ativa" BOOLEAN NOT NULL DEFAULT true,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "EstruturaExterna_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "ChefeExterno_entidade_key" ON "ChefeExterno"("entidade");

-- CreateIndex
CREATE UNIQUE INDEX "EstruturaExterna_estrutura_key" ON "EstruturaExterna"("estrutura");
