-- CreateTable
CREATE TABLE "Missao" (
    "id" TEXT NOT NULL,
    "titulo" TEXT NOT NULL,
    "descricao" TEXT NOT NULL DEFAULT '',
    "tipo" TEXT NOT NULL DEFAULT 'MATAR',
    "alvo" TEXT NOT NULL,
    "quantidade" INTEGER NOT NULL DEFAULT 10,
    "nivelMin" INTEGER NOT NULL DEFAULT 1,
    "recompensaXp" INTEGER NOT NULL DEFAULT 100,
    "recompensaItens" JSONB NOT NULL DEFAULT '[]',
    "repetivel" BOOLEAN NOT NULL DEFAULT false,
    "intervaloHoras" INTEGER NOT NULL DEFAULT 0,
    "preRequisitoId" TEXT,
    "ativa" BOOLEAN NOT NULL DEFAULT true,
    "ordem" INTEGER NOT NULL DEFAULT 0,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Missao_pkey" PRIMARY KEY ("id")
);
