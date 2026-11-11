import { lerConfig } from "@/lib/config-jogo";
import { FormForja } from "./form";

export default async function ForjaPage() {
  const config = await lerConfig();
  const chanceAte10 = config.forja.niveis.reduce((acc, n) => acc * (n.chance / 100), 1);

  return (
    <>
      <h1 className="font-titulo text-3xl">Forja</h1>
      <p className="mb-6 text-pedra-500">
        Preços e chances do ferreiro e do armeiro das capitais (o jogador clica agachado neles com a arma na mão).
        Vale sem recompilar o mod.
      </p>
      <FormForja forja={config.forja} />
      <p className="mt-4 text-xs text-pedra-500">
        Chance de ir de +0 a +10 sem nenhuma falha: {(chanceAte10 * 100).toFixed(2).replace(".", ",")}%.
      </p>
    </>
  );
}
