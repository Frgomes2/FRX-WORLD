-- 22/11/2026: menos ouro fácil. Portais e chefes de masmorra pagam menos; só os chefes difíceis pagam muito.
UPDATE "Configuracao"
SET "valor" = jsonb_set(jsonb_set("valor",
      '{portais}', COALESCE("valor"->'portais', '{}'::jsonb) || '{"moedasPorRank": [100, 200, 400, 800, 1500, 2500, 4000]}'::jsonb),
      '{masmorras}', COALESCE("valor"->'masmorras', '{}'::jsonb) || '{"moedasPorNivel": 40}'::jsonb),
    "atualizadoEm" = NOW()
WHERE "chave" = 'jogo' AND jsonb_typeof("valor") = 'object';
