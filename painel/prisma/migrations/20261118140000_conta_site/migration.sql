-- Conta do jogador no site: senha criada no primeiro acesso (com o código mostrado dentro do jogo) e sessões abertas
CREATE TABLE "ContaSite" (
    "uuid" TEXT NOT NULL,
    "senha" TEXT NOT NULL DEFAULT '',
    "codigo" TEXT NOT NULL DEFAULT '',
    "codigoAte" TIMESTAMP(3),
    "erros" INTEGER NOT NULL DEFAULT 0,
    "travadaAte" TIMESTAMP(3),
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ContaSite_pkey" PRIMARY KEY ("uuid")
);
CREATE TABLE "SessaoSite" (
    "id" TEXT NOT NULL,
    "uuid" TEXT NOT NULL,
    "expira" TIMESTAMP(3) NOT NULL,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "SessaoSite_pkey" PRIMARY KEY ("id")
);
CREATE INDEX "SessaoSite_uuid_idx" ON "SessaoSite"("uuid");
