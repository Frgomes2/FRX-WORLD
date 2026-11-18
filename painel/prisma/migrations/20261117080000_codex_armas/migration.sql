-- Códex de Armas
CREATE TABLE "ArmaCodex" (
    "id" TEXT NOT NULL,
    "codigo" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "descricao" TEXT NOT NULL DEFAULT '',
    "itemBase" TEXT NOT NULL,
    "rank" INTEGER NOT NULL DEFAULT 0,
    "raridade" INTEGER NOT NULL DEFAULT 0,
    "dano" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "limite" INTEGER NOT NULL DEFAULT 0,
    "ferreiro" BOOLEAN NOT NULL DEFAULT true,
    "preco" INTEGER NOT NULL DEFAULT 0,
    "nivelMin" INTEGER NOT NULL DEFAULT 1,
    "evolui" BOOLEAN NOT NULL DEFAULT false,
    "danoPorNivel" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "umaPorJogador" BOOLEAN NOT NULL DEFAULT true,
    "encantos" TEXT NOT NULL DEFAULT '',
    "textura" TEXT NOT NULL DEFAULT '',
    "modelo" SERIAL NOT NULL,
    "emitidas" INTEGER NOT NULL DEFAULT 0,
    "donos" JSONB NOT NULL DEFAULT '{}',
    "ativa" BOOLEAN NOT NULL DEFAULT true,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ArmaCodex_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "ArmaCodex_codigo_key" ON "ArmaCodex"("codigo");
CREATE UNIQUE INDEX "ArmaCodex_modelo_key" ON "ArmaCodex"("modelo");
