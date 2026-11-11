-- Balanceamento (10/11/2026): monstros mais fracos por nível e chances de Elite/Campeão de volta ao padrão.
UPDATE "Configuracao"
SET "valor" = jsonb_set("valor", '{zonas}',
      COALESCE("valor"->'zonas', '{}'::jsonb)
      || '{"vidaPct": 6, "danoPct": 3.5, "armaduraPorNivel": 0.2, "eliteChance": 5, "campeaoChance": 0.5}'::jsonb),
    "atualizadoEm" = NOW()
WHERE "chave" = 'jogo' AND jsonb_typeof("valor") = 'object';
