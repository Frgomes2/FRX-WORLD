-- CreateTable
CREATE TABLE "Dungeon" (
    "id" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "descricao" TEXT NOT NULL DEFAULT '',
    "distanciaMin" INTEGER NOT NULL DEFAULT 0,
    "distanciaMax" INTEGER NOT NULL DEFAULT 100000,
    "nivelRecomendado" INTEGER NOT NULL DEFAULT 1,
    "bossTipo" TEXT NOT NULL DEFAULT 'minecraft:zombie',
    "bossNome" TEXT NOT NULL DEFAULT 'Guardião',
    "bossVida" INTEGER NOT NULL DEFAULT 100,
    "bossDano" DOUBLE PRECISION NOT NULL DEFAULT 6,
    "bossVelocidade" DOUBLE PRECISION NOT NULL DEFAULT 1,
    "lacaioTipo" TEXT NOT NULL DEFAULT 'minecraft:zombie',
    "lacaioQtd" INTEGER NOT NULL DEFAULT 3,
    "recompensaXp" INTEGER NOT NULL DEFAULT 500,
    "loot" JSONB NOT NULL DEFAULT '[]',
    "recargaHoras" INTEGER NOT NULL DEFAULT 24,
    "ativa" BOOLEAN NOT NULL DEFAULT true,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Dungeon_pkey" PRIMARY KEY ("id")
);
