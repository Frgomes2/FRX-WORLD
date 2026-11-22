-- 21/11/2026: Nether e End viram as fases depois da superfície (1–60). Nether 55–80, End 75–100.
UPDATE "Configuracao"
SET "valor" = jsonb_set("valor", '{zonas,dimensoes}',
      COALESCE("valor"->'zonas'->'dimensoes', '{}'::jsonb)
      || '{"minecraft:the_nether": [55, 80], "minecraft:the_end": [75, 100]}'::jsonb),
    "atualizadoEm" = NOW()
WHERE "chave" = 'jogo' AND jsonb_typeof("valor") = 'object' AND jsonb_typeof("valor"->'zonas') = 'object';
