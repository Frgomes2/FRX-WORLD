-- 22/11/2026: invasões menos frequentes (1 tentativa por hora, 25% de chance; a mesma capital só de 3 em 3 horas, no código).
UPDATE "Configuracao"
SET "valor" = jsonb_set("valor", '{invasoes}',
      COALESCE("valor"->'invasoes', '{}'::jsonb) || '{"intervaloMinutos": 60, "chance": 25}'::jsonb),
    "atualizadoEm" = NOW()
WHERE "chave" = 'jogo' AND jsonb_typeof("valor") = 'object' AND jsonb_typeof("valor"->'invasoes') = 'object';
