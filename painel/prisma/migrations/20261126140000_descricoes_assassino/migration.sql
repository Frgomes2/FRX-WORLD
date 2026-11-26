-- 3.0.5: descrições do Assassino de acordo com o comportamento novo (Passo Fantasma atordoa; Mil Cortes salta em volta do alvo)
UPDATE "Habilidade" SET "descricao" = 'Some e reaparece atrás do alvo da mira, já golpeando pelas costas. O alvo fica atordoado por um instante.'
WHERE "codigo" = 'passo_fantasma';
UPDATE "Habilidade" SET "descricao" = 'Some e reaparece em volta do alvo da mira seis vezes, um golpe a cada salto. Quase sempre fatal num alvo ferido.'
WHERE "codigo" = 'mil_cortes';
