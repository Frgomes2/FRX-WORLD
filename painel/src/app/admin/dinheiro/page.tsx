import { prisma } from "@/lib/prisma";
import { TabelaDinheiro } from "./tabela";

export const dynamic = "force-dynamic";

export default async function DinheiroPage() {
  const jogadores = await prisma.jogador.findMany({
    orderBy: [{ nick: "asc" }],
    select: { uuid: true, nick: true, moedas: true, banco: true, online: true, nivel: true, rank: true, ultimoLogin: true },
  });
  return (
    <>
      <div className="mb-5">
        <h1 className="font-titulo text-3xl">Dinheiro dos jogadores</h1>
        <p className="text-pedra-500">Quanto cada um tem na bolsa e no banco. Clique no saldo do banco para editar, mesmo com o jogador offline. Tirar da bolsa exige o jogador online.</p>
      </div>
      <TabelaDinheiro jogadores={jogadores.map((j) => ({ ...j, ultimoLogin: j.ultimoLogin?.toISOString() ?? null }))} />
    </>
  );
}
