import { prisma } from "@/lib/prisma";
import { Lojas } from "./lojas";
import { Riqueza } from "./riqueza";

export default async function EconomiaPage() {
  const itens = await prisma.itemLoja.findMany({ orderBy: [{ ordem: "asc" }, { criadoEm: "asc" }] });
  const jogadores = await prisma.jogador.findMany({ orderBy: [{ banco: "desc" }, { moedas: "desc" }], select: { uuid: true, nick: true, moedas: true, banco: true } });
  const lista = (loja: string) => itens.filter((i) => i.loja === loja).map((i) => ({ item: i.item, qtd: i.qtd, preco: i.preco, ativo: i.ativo, nivelMin: i.nivelMin }));

  return (
    <>
      <div className="mb-5">
        <h1 className="font-titulo text-3xl">Economia</h1>
        <p className="text-pedra-500">Lojas de todas as capitais e o dinheiro em circulação. O servidor lê as lojas a cada minuto.</p>
      </div>
      <Riqueza jogadores={jogadores} />
      <Lojas mercador={lista("MERCADOR")} comprador={lista("COMPRADOR")} />
    </>
  );
}
