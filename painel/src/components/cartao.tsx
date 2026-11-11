export function Cartao({ titulo, valor, detalhe }: { titulo: string; valor: string | number; detalhe?: string }) {
  return (
    <div className="rounded-xl border border-pedra-700 bg-pedra-900 p-5">
      <p className="text-sm text-pedra-500">{titulo}</p>
      <p className="mt-1 font-titulo text-3xl text-ouro">{valor}</p>
      {detalhe && <p className="mt-1 text-xs text-pedra-500">{detalhe}</p>}
    </div>
  );
}
