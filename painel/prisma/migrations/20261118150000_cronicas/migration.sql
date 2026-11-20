-- Crônicas do mundo (acontecimentos guardados para o feed, o jornal e o histórico de cada personagem) e a foto diária do ranking
CREATE TABLE "Cronica" (
    "id" TEXT NOT NULL,
    "chave" TEXT,
    "tipo" TEXT NOT NULL,
    "icone" TEXT NOT NULL DEFAULT '',
    "texto" TEXT NOT NULL,
    "nome" TEXT NOT NULL DEFAULT '',
    "conta" TEXT NOT NULL DEFAULT '',
    "vaga" INTEGER NOT NULL DEFAULT 0,
    "destaque" BOOLEAN NOT NULL DEFAULT false,
    "semente" BOOLEAN NOT NULL DEFAULT false,
    "quando" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "Cronica_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "Cronica_chave_key" ON "Cronica"("chave");
CREATE INDEX "Cronica_quando_idx" ON "Cronica"("quando");
CREATE INDEX "Cronica_conta_vaga_idx" ON "Cronica"("conta", "vaga");
CREATE TABLE "FotoRanking" (
    "dia" TEXT NOT NULL,
    "conta" TEXT NOT NULL,
    "vaga" INTEGER NOT NULL,
    "nivel" INTEGER NOT NULL,
    "posicao" INTEGER NOT NULL,
    CONSTRAINT "FotoRanking_pkey" PRIMARY KEY ("dia", "conta", "vaga")
);
