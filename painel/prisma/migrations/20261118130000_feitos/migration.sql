-- Mural dos Primeiros: quem foi o primeiro personagem a alcançar cada marco do servidor
CREATE TABLE "Feito" (
    "chave" TEXT NOT NULL,
    "nome" TEXT NOT NULL DEFAULT '',
    "conta" TEXT NOT NULL DEFAULT '',
    "vaga" INTEGER NOT NULL DEFAULT 0,
    "antigo" BOOLEAN NOT NULL DEFAULT false,
    "quando" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "Feito_pkey" PRIMARY KEY ("chave")
);
