-- Personagens de cada conta (até 3): o servidor manda nome, classe, raça, nível e rank de cada vaga
CREATE TABLE "Personagem" (
    "id" TEXT NOT NULL,
    "uuid" TEXT NOT NULL,
    "vaga" INTEGER NOT NULL,
    "idJogo" TEXT NOT NULL DEFAULT '',
    "nome" TEXT NOT NULL DEFAULT '',
    "classe" TEXT NOT NULL DEFAULT '',
    "raca" TEXT NOT NULL DEFAULT '',
    "nivel" INTEGER NOT NULL DEFAULT 1,
    "rank" TEXT NOT NULL DEFAULT '',
    "emUso" BOOLEAN NOT NULL DEFAULT false,
    "base" JSONB,
    "detalhe" JSONB,
    "detalheEm" TIMESTAMP(3),
    "atualizadoEm" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "Personagem_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "Personagem_uuid_vaga_key" ON "Personagem"("uuid", "vaga");
