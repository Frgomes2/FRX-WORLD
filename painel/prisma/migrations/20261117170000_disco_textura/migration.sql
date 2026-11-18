-- Disco com desenho próprio (PNG 16x16 ou 32x32 em base64) e número do CustomModelData no pacote de recursos
ALTER TABLE "Disco" ADD COLUMN "textura" TEXT NOT NULL DEFAULT '';
ALTER TABLE "Disco" ADD COLUMN "modelo" SERIAL NOT NULL;
CREATE UNIQUE INDEX "Disco_modelo_key" ON "Disco"("modelo");
