-- AlterTable
ALTER TABLE "Jogador" ADD COLUMN     "campanhas" JSONB NOT NULL DEFAULT '{}',
ADD COLUMN     "reputacao" JSONB NOT NULL DEFAULT '{}';

-- CreateTable
CREATE TABLE "Masmorra" (
    "id" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "chefe" TEXT NOT NULL DEFAULT '',
    "estrutura" TEXT NOT NULL,
    "x" INTEGER NOT NULL,
    "z" INTEGER NOT NULL,
    "entradaX" INTEGER,
    "entradaY" INTEGER,
    "entradaZ" INTEGER,
    "ativo" BOOLEAN NOT NULL DEFAULT true,
    "ordem" INTEGER NOT NULL DEFAULT 0,
    "refazer" BOOLEAN NOT NULL DEFAULT false,
    "colocadaX" INTEGER,
    "colocadaY" INTEGER,
    "colocadaZ" INTEGER,
    "colocadaEstrutura" TEXT,
    "colocadaEm" TIMESTAMP(3),
    "atualizadoEm" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Masmorra_pkey" PRIMARY KEY ("id")
);

-- Masmorras que já estavam no posicoes.json
INSERT INTO "Masmorra" ("id", "nome", "chefe", "estrutura", "x", "z", "entradaX", "entradaY", "entradaZ", "ordem", "atualizadoEm") VALUES
('coliseu-tempest', 'Fábrica Ancestral sob o Coliseu de Tempest', 'The Harbinger', 'cataclysm:ancient_factory', 255, 125, 255, 72, 125, 0, CURRENT_TIMESTAMP),
('dwargon-ferro', 'Câmara do Cavaleiro de Ferro (perto de Dwargon)', 'Ferrous Wroughtnaut', 'mowziesmobs:wrought_chamber|dungeons_arise:foundry|cataclysm:cursed_pyramid', 1150, 2300, NULL, NULL, NULL, 1, CURRENT_TIMESTAMP),
('sarion-gelo', 'Prisão Congelada (norte de Sarion)', 'Maledictus', 'cataclysm:frosted_prison|dungeons_arise:keep_kayra', 2200, -950, NULL, NULL, NULL, 2, CURRENT_TIMESTAMP),
('leste-mosteiro', 'Mosteiro da Montanha (norte do Império do Leste)', 'Sculptor', 'mowziesmobs:monastery|dungeons_arise:shiraz_palace', -2600, -1050, NULL, NULL, NULL, 3, CURRENT_TIMESTAMP),
('sakura-gorgona', 'Templo da Górgona (sul de Sakura)', 'Górgona', 'iceandfire:gorgon_temple|dungeons_arise:plague_asylum', -2600, 1000, NULL, NULL, NULL, 4, CURRENT_TIMESTAMP);
