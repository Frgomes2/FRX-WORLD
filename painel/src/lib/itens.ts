import "server-only";
import { prisma } from "@/lib/prisma";
import { traduzir } from "@/lib/jar-icones";

export type ItemJogo = { id: string; nome: string; mod: string; pilha: number };

const g = globalThis as unknown as { __frxItens?: { em: number; lista: ItemJogo[] } };

/** Itens do modpack (lista mandada pelo mod ao ligar), com nome traduzido pelos arquivos de idioma. */
export async function listaItens(): Promise<ItemJogo[] | null> {
  const linha = await prisma.configuracao.findUnique({ where: { chave: "itens" } });
  if (!linha) return null;
  const em = linha.atualizadoEm.getTime();
  if (g.__frxItens?.em === em) return g.__frxItens.lista;
  const brutos = (linha.valor as { itens?: [string, string, number][] }).itens ?? [];
  const lista = brutos.map(([id, chave, pilha]) => {
    const [ns, nome] = id.split(":");
    let n: string | null = null;
    try { n = traduzir(chave); } catch { n = null; }
    return { id, nome: n ?? nome.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()), mod: ns, pilha };
  }).sort((a, b) => (a.mod === "minecraft" ? 0 : 1) - (b.mod === "minecraft" ? 0 : 1) || a.nome.localeCompare(b.nome, "pt-BR"));
  g.__frxItens = { em, lista };
  return lista;
}
