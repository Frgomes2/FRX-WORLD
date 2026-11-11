-- Skins de NPC feitas no painel
CREATE TABLE "NpcSkin" (
    "chave" TEXT NOT NULL,
    "slim" BOOLEAN NOT NULL DEFAULT false,
    "png" BYTEA NOT NULL,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "NpcSkin_pkey" PRIMARY KEY ("chave")
);
