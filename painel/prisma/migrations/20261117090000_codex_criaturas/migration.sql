-- Códex de Criaturas (chefes e monstros próprios)
CREATE TABLE "CriaturaCodex" (
    "id" TEXT NOT NULL,
    "codigo" TEXT NOT NULL,
    "nome" TEXT NOT NULL,
    "descricao" TEXT NOT NULL DEFAULT '',
    "tipo" TEXT NOT NULL DEFAULT 'chefe',
    "entidade" TEXT NOT NULL,
    "rankMin" INTEGER NOT NULL DEFAULT 0,
    "rankMax" INTEGER NOT NULL DEFAULT 6,
    "vida" DOUBLE PRECISION NOT NULL DEFAULT 1,
    "dano" DOUBLE PRECISION NOT NULL DEFAULT 1,
    "velocidade" DOUBLE PRECISION NOT NULL DEFAULT 1,
    "golpes" TEXT NOT NULL DEFAULT '',
    "lacaio" TEXT NOT NULL DEFAULT '',
    "locais" TEXT NOT NULL DEFAULT 'portal,torre,nether',
    "peso" INTEGER NOT NULL DEFAULT 10,
    "xp" INTEGER NOT NULL DEFAULT 0,
    "moedas" INTEGER NOT NULL DEFAULT 0,
    "dropItem" TEXT NOT NULL DEFAULT '',
    "dropChance" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "dropQtd" INTEGER NOT NULL DEFAULT 1,
    "escala" DOUBLE PRECISION NOT NULL DEFAULT 1,
    "brilho" BOOLEAN NOT NULL DEFAULT false,
    "skin" TEXT NOT NULL DEFAULT '',
    "slim" BOOLEAN NOT NULL DEFAULT false,
    "derrotas" INTEGER NOT NULL DEFAULT 0,
    "ativa" BOOLEAN NOT NULL DEFAULT true,
    "criadoEm" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizadoEm" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CriaturaCodex_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "CriaturaCodex_codigo_key" ON "CriaturaCodex"("codigo");

-- os 14 chefes que já existiam no jogo entram como ponto de partida (dá para editar ou apagar)
INSERT INTO "CriaturaCodex" ("id", "codigo", "nome", "descricao", "tipo", "entidade", "rankMin", "rankMax", "golpes", "lacaio", "brilho", "atualizadoEm") VALUES
('seed_rei_goblin', 'rei_goblin', 'Rei Goblin', 'Senhor das tocas, nunca luta sozinho.', 'chefe', 'minecraft:zombie', 0, 1, 'SALTO,INVOCAR', 'minecraft:zombie', true, CURRENT_TIMESTAMP),
('seed_aranha_rainha', 'aranha_rainha', 'Aranha Rainha', 'Mãe de todo o ninho.', 'chefe', 'minecraft:spider', 0, 1, 'VENENO,INVOCAR', 'minecraft:cave_spider', true, CURRENT_TIMESTAMP),
('seed_capitao_esqueleto', 'capitao_esqueleto', 'Capitão Esqueleto', 'Ainda comanda a tropa que morreu com ele.', 'chefe', 'minecraft:skeleton', 0, 2, 'FLECHAS,INVOCAR', 'minecraft:skeleton', true, CURRENT_TIMESTAMP),
('seed_xama_do_pantano', 'xama_do_pantano', 'Xamã do Pântano', 'Fala com o lodo e o lodo responde.', 'chefe', 'minecraft:witch', 1, 3, 'VENENO,CURA,RAIO', 'minecraft:zombie', true, CURRENT_TIMESTAMP),
('seed_carrasco_de_ferro', 'carrasco_de_ferro', 'Carrasco de Ferro', 'Não conta quantos já caíram.', 'chefe', 'minecraft:vindicator', 1, 3, 'SALTO,ONDA,FURIA', 'minecraft:vindicator', true, CURRENT_TIMESTAMP),
('seed_arqueiro_gelido', 'arqueiro_gelido', 'Arqueiro Gélido', 'Cada flecha traz o inverno.', 'chefe', 'minecraft:stray', 1, 3, 'GELO,FLECHAS,ONDA', 'minecraft:stray', true, CURRENT_TIMESTAMP),
('seed_besta_de_guerra', 'besta_de_guerra', 'Besta de Guerra', 'Criada para derrubar muralhas.', 'chefe', 'minecraft:ravager', 2, 4, 'ONDA,SALTO,FURIA', 'minecraft:pillager', true, CURRENT_TIMESTAMP),
('seed_cavaleiro_do_abismo', 'cavaleiro_do_abismo', 'Cavaleiro do Abismo', 'Jurou servir ao que há lá embaixo.', 'chefe', 'minecraft:wither_skeleton', 2, 4, 'ONDA,INVOCAR,FOGO,FURIA', 'minecraft:wither_skeleton', true, CURRENT_TIMESTAMP),
('seed_senhor_das_chamas', 'senhor_das_chamas', 'Senhor das Chamas', 'O chão queima onde ele olha.', 'chefe', 'minecraft:blaze', 3, 5, 'FOGO,RAIO,INVOCAR', 'minecraft:blaze', true, CURRENT_TIMESTAMP),
('seed_mestre_das_ilusoes', 'mestre_das_ilusoes', 'Mestre das Ilusões', 'Metade do que você vê não está ali.', 'chefe', 'minecraft:illusioner', 3, 5, 'RAIO,GELO,CURA', 'minecraft:vindicator', true, CURRENT_TIMESTAMP),
('seed_bruxa_suprema', 'bruxa_suprema', 'Bruxa Suprema', 'Todas as outras aprenderam com ela.', 'chefe', 'minecraft:witch', 3, 6, 'VENENO,RAIO,GELO,CURA,INVOCAR', 'minecraft:witch', true, CURRENT_TIMESTAMP),
('seed_devorador_do_abismo', 'devorador_do_abismo', 'Devorador do Abismo', 'Fome que não acaba.', 'chefe', 'minecraft:zoglin', 4, 6, 'SALTO,ONDA,VENENO,FURIA', 'minecraft:zoglin', true, CURRENT_TIMESTAMP),
('seed_colosso_de_guerra', 'colosso_de_guerra', 'Colosso de Guerra', 'Um exército inteiro em um só corpo.', 'chefe', 'minecraft:ravager', 4, 6, 'ONDA,SALTO,FLECHAS,INVOCAR,FURIA', 'minecraft:vindicator', true, CURRENT_TIMESTAMP),
('seed_arauto_do_vazio', 'arauto_do_vazio', 'Arauto do Vazio', 'Quando ele chega, o resto já acabou.', 'chefe', 'minecraft:wither_skeleton', 5, 6, 'ONDA,RAIO,FOGO,GELO,INVOCAR,FURIA', 'minecraft:wither_skeleton', true, CURRENT_TIMESTAMP);
