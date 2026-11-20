import { prisma } from "@/lib/prisma";

/**
 * Uma linha por PERSONAGEM em vez de uma por conta (cada conta tem até 3).
 * O personagem em uso leva os números ao vivo da conta (tabela Jogador); os outros, o retrato guardado
 * na última vez em que jogaram (Personagem.base). Conta sem personagens registrados vira uma linha só (vaga 0).
 * O banco é da conta: não entra aqui, quem lista decide como mostrar.
 */
export type PersonagemGuardado = { uuid: string; vaga: number; nome: string; classe: string; nivel: number; rank: string; emUso: boolean; base: unknown };
type Retrato = { nivel?: number; rank?: string; classe?: string; moedas?: number; titulo?: string; conquistas?: number; mortes?: number };
type Conta = { uuid: string; nick: string; nivel: number; rank: string; classe: string; titulo: string; moedas: number; mortes: number; conquistas: number };

export async function personagensGuardados(): Promise<PersonagemGuardado[]> {
  return (await prisma.personagem.findMany().catch(() => [])) as unknown as PersonagemGuardado[];
}

/** nick passa a ser o nome do personagem; conta guarda o nick da conta. */
export function porPersonagem<T extends Conta>(contas: T[], guardados: PersonagemGuardado[]): (T & { conta: string; vaga: number; emUso: boolean })[] {
  const fichas: (T & { conta: string; vaga: number; emUso: boolean })[] = [];
  for (const j of contas) {
    const dele = guardados.filter((x) => x.uuid === j.uuid).sort((a, b) => a.vaga - b.vaga);
    if (!dele.length) { fichas.push({ ...j, conta: j.nick, vaga: 0, emUso: true }); continue; }
    for (const x of dele) {
      const nome = x.nome || j.nick;
      if (x.emUso) { fichas.push({ ...j, nick: nome, conta: j.nick, vaga: x.vaga, emUso: true }); continue; }
      const b = (x.base && typeof x.base === "object" ? x.base : {}) as Retrato;
      fichas.push({
        ...j, nick: nome, conta: j.nick, vaga: x.vaga, emUso: false,
        nivel: b.nivel ?? x.nivel, rank: b.rank || x.rank || "F", classe: b.classe ?? x.classe.toUpperCase(), titulo: b.titulo ?? "",
        moedas: b.moedas ?? 0, mortes: b.mortes ?? 0, conquistas: b.conquistas ?? 0,
      });
    }
  }
  return fichas;
}
