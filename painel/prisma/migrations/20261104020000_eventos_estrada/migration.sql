-- CreateTable
CREATE TABLE "EventoEstrada" (
    "id" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "tipo" TEXT NOT NULL DEFAULT 'EMBOSCADA',
    "mensagem" TEXT NOT NULL DEFAULT '',
    "peso" INTEGER NOT NULL DEFAULT 10,
    "nivelMin" INTEGER NOT NULL DEFAULT 1,
    "nivelMax" INTEGER NOT NULL DEFAULT 1000,
    "mobTipo" TEXT NOT NULL DEFAULT 'minecraft:zombie',
    "mobQtd" INTEGER NOT NULL DEFAULT 4,
    "vida" INTEGER NOT NULL DEFAULT 0,
    "dano" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "preco" INTEGER NOT NULL DEFAULT 0,
    "recompensaXp" INTEGER NOT NULL DEFAULT 0,
    "loot" JSONB NOT NULL DEFAULT '[]',
    "ativo" BOOLEAN NOT NULL DEFAULT true,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "EventoEstrada_pkey" PRIMARY KEY ("id")
);
