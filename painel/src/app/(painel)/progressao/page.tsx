import { CONFIG_PADRAO, lerConfig } from "@/lib/config-jogo";
import { EditorProgressao } from "./editor";

export default async function ProgressaoPage() {
  const config = await lerConfig();
  const { progressao, ranks, morte, ferido, invasoes, chefeMundial, floresta, zonas } = config;
  const p = CONFIG_PADRAO;
  return (
    <>
      <h1 className="font-titulo text-3xl">Progressão</h1>
      <p className="mb-6 text-pedra-500">Como os jogadores evoluem e o que o mundo joga contra eles. Vale no servidor sem recompilar o mod.</p>
      <EditorProgressao
        inicial={{ progressao, ranks, morte, ferido, invasoes, chefeMundial, floresta, zonas }}
        padrao={{ progressao: p.progressao, ranks: p.ranks, morte: p.morte, ferido: p.ferido, invasoes: p.invasoes, chefeMundial: p.chefeMundial, floresta: p.floresta, zonas: p.zonas }}
      />
    </>
  );
}
