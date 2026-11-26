-- Terras Inexploradas: expedições e o que cada conta fez em cada uma
CREATE TABLE "Expedicao" (
    "numero" INTEGER NOT NULL,
    "tipo" TEXT NOT NULL DEFAULT 'normal',
    "nome" TEXT NOT NULL DEFAULT '',
    "descricao" TEXT NOT NULL DEFAULT '',
    "modificadores" JSONB NOT NULL DEFAULT '{}',
    "regras" JSONB NOT NULL DEFAULT '{}',
    "inicio" TIMESTAMP(3) NOT NULL,
    "proxima" TIMESTAMP(3) NOT NULL,
    "fim" TIMESTAMP(3),
    "tamanho" INTEGER NOT NULL DEFAULT 2000,
    "versao" TEXT NOT NULL DEFAULT '',
    "ativa" BOOLEAN NOT NULL DEFAULT true,
    "estruturas" INTEGER NOT NULL DEFAULT 0,
    "agora" JSONB NOT NULL DEFAULT '[]',
    "atualizadaEm" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "Expedicao_pkey" PRIMARY KEY ("numero")
);
CREATE TABLE "ParticipacaoExpedicao" (
    "id" TEXT NOT NULL,
    "numero" INTEGER NOT NULL,
    "uuid" TEXT NOT NULL,
    "nick" TEXT NOT NULL,
    "primeira" TIMESTAMP(3) NOT NULL,
    "ultima" TIMESTAMP(3) NOT NULL,
    "tempoSeg" INTEGER NOT NULL DEFAULT 0,
    "chunks" INTEGER NOT NULL DEFAULT 0,
    "mortes" INTEGER NOT NULL DEFAULT 0,
    "chefes" INTEGER NOT NULL DEFAULT 0,
    "estruturas" INTEGER NOT NULL DEFAULT 0,
    "tesouros" INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT "ParticipacaoExpedicao_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "ParticipacaoExpedicao_numero_uuid_key" ON "ParticipacaoExpedicao"("numero", "uuid");
CREATE INDEX "ParticipacaoExpedicao_uuid_idx" ON "ParticipacaoExpedicao"("uuid");
ALTER TABLE "ParticipacaoExpedicao" ADD CONSTRAINT "ParticipacaoExpedicao_numero_fkey" FOREIGN KEY ("numero") REFERENCES "Expedicao"("numero") ON DELETE CASCADE ON UPDATE CASCADE;
