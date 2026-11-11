import { prisma } from "@/lib/prisma";
import { VisaoAoVivo, type DadosBanco } from "./visao/visao-ao-vivo";

export default async function VisaoGeral() {
  const [jogadores, ativos, convitesLivres, todos, masmorras] = await Promise.all([
    prisma.jogador.count(),
    prisma.jogador.count({ where: { ativo: true } }),
    prisma.convite.count({ where: { usadoEm: null, revogado: false } }),
    prisma.jogador.findMany({
      select: { nick: true, uuid: true, nivel: true, rank: true, classe: true, titulo: true, moedas: true, banco: true, mortes: true, conquistas: true, ultimoLogin: true },
    }),
    prisma.masmorra.findMany({ where: { ativo: true }, select: { id: true, nome: true, chefe: true, colocadaX: true, colocadaZ: true, x: true, z: true }, orderBy: { ordem: "asc" } }),
  ]);

  const porNivel = [...todos].sort((a, b) => b.nivel - a.nivel || b.conquistas - a.conquistas).slice(0, 5);
  const porRiqueza = [...todos].sort((a, b) => b.moedas + b.banco - (a.moedas + a.banco)).slice(0, 5);
  const porMortes = [...todos].filter((j) => j.mortes > 0).sort((a, b) => b.mortes - a.mortes).slice(0, 3);
  const classes: Record<string, number> = {};
  for (const j of todos) classes[j.classe || "SEM CLASSE"] = (classes[j.classe || "SEM CLASSE"] ?? 0) + 1;

  const dados: DadosBanco = {
    jogadores,
    ativos,
    convitesLivres,
    economia: {
      bolsa: todos.reduce((s, j) => s + j.moedas, 0),
      banco: todos.reduce((s, j) => s + j.banco, 0),
      mortes: todos.reduce((s, j) => s + j.mortes, 0),
      conquistas: todos.reduce((s, j) => s + j.conquistas, 0),
    },
    porNivel: porNivel.map((j) => ({ nick: j.nick, uuid: j.uuid, valor: j.nivel, extra: `${j.rank} · ${j.classe || "sem classe"}`, titulo: j.titulo })),
    porRiqueza: porRiqueza.map((j) => ({ nick: j.nick, uuid: j.uuid, valor: j.moedas + j.banco, extra: `banco ${j.banco}`, titulo: j.titulo })),
    porMortes: porMortes.map((j) => ({ nick: j.nick, uuid: j.uuid, valor: j.mortes, extra: "", titulo: j.titulo })),
    classes,
    masmorras: masmorras.map((m) => ({ id: m.id, nome: m.nome, chefe: m.chefe, x: m.colocadaX ?? m.x, z: m.colocadaZ ?? m.z, colocada: m.colocadaX !== null })),
  };

  return (
    <>
      <h1 className="font-titulo text-3xl">Visão geral</h1>
      <p className="mb-6 text-pedra-500">O reino ao vivo: atualiza sozinho a cada 10 segundos.</p>
      <VisaoAoVivo banco={dados} />
    </>
  );
}
