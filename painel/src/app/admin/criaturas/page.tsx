import { prisma } from "@/lib/prisma";
import { TelaCriaturas } from "./tela";

export const dynamic = "force-dynamic";

export default async function CriaturasPage() {
  const [criaturas, armas] = await Promise.all([
    prisma.criaturaCodex.findMany({ orderBy: [{ tipo: "asc" }, { rankMin: "asc" }, { nome: "asc" }] }),
    prisma.armaCodex.findMany({ where: { ativa: true }, select: { codigo: true, nome: true }, orderBy: { nome: "asc" } }),
  ]);
  return (
    <>
      <div className="mb-5">
        <h1 className="font-titulo text-3xl">Códex de Criaturas</h1>
        <p className="text-pedra-500">
          Chefes e monstros próprios do servidor. Você escolhe a criatura que serve de corpo, a força, os golpes, onde aparece,
          o que deixa ao morrer e a aparência — desenhada aqui ou importada como skin.
        </p>
      </div>
      <TelaCriaturas
        criaturas={criaturas.map((c) => ({
          id: c.id, codigo: c.codigo, nome: c.nome, descricao: c.descricao, tipo: c.tipo === "monstro" ? "monstro" : "chefe", entidade: c.entidade,
          rankMin: c.rankMin, rankMax: c.rankMax, vida: c.vida, dano: c.dano, velocidade: c.velocidade,
          golpes: c.golpes.split(",").filter(Boolean), lacaio: c.lacaio, locais: c.locais.split(",").filter(Boolean), peso: c.peso,
          xp: c.xp, moedas: c.moedas, dropItem: c.dropItem, dropChance: c.dropChance, dropQtd: c.dropQtd,
          escala: c.escala, brilho: c.brilho, skin: c.skin, slim: c.slim, derrotas: c.derrotas, ativa: c.ativa,
        }))}
        armas={armas}
      />
    </>
  );
}
