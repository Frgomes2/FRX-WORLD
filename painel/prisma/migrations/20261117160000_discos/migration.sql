-- Discos de música do Mercador de Discos (o áudio fica em arquivo, fora do banco)
CREATE TABLE "Disco" (
    "id" TEXT NOT NULL,
    "codigo" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "artista" TEXT NOT NULL DEFAULT '',
    "descricao" TEXT NOT NULL DEFAULT '',
    "preco" INTEGER NOT NULL DEFAULT 100,
    "rankMin" INTEGER NOT NULL DEFAULT 0,
    "arte" INTEGER NOT NULL DEFAULT 1,
    "duracao" INTEGER NOT NULL DEFAULT 0,
    "canais" INTEGER NOT NULL DEFAULT 0,
    "tamanho" INTEGER NOT NULL DEFAULT 0,
    "ativo" BOOLEAN NOT NULL DEFAULT true,
    "ordem" INTEGER NOT NULL DEFAULT 0,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Disco_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "Disco_codigo_key" ON "Disco"("codigo");
