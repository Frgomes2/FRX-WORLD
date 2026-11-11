/** Valor em cobre → "1 ouro, 2 prata, 5 cobre". */
export function textoMoedas(c: number) {
  const ouro = Math.floor(c / 100), prata = Math.floor((c % 100) / 10), cobre = c % 10;
  const p: string[] = [];
  if (ouro) p.push(`${ouro} ouro`);
  if (prata) p.push(`${prata} prata`);
  if (cobre || !p.length) p.push(`${cobre} cobre`);
  return p.join(", ");
}
