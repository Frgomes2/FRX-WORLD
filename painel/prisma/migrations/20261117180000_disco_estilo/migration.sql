-- Estilo de música do disco (rock, sertanejo, trilha...), usado no filtro do Mercador de Discos
ALTER TABLE "Disco" ADD COLUMN "estilo" TEXT NOT NULL DEFAULT '';
