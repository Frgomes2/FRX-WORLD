"use client";

import { useEffect, useState } from "react";

/** Quanto falta até um momento, contando a cada segundo (relógio do navegador acertado pelo do servidor). */
export function Falta({ alvo, servidor }: { alvo: number; servidor: number }) {
  const [agora, setAgora] = useState(servidor);
  useEffect(() => {
    const diferenca = servidor - Date.now();
    const t = setInterval(() => setAgora(Date.now() + diferenca), 1000);
    return () => clearInterval(t);
  }, [servidor]);
  const seg = Math.max(0, Math.floor((alvo - agora) / 1000));
  if (seg === 0) return <span className="text-verde-claro">voltando agora</span>;
  const d = Math.floor(seg / 86400), h = Math.floor(seg / 3600) % 24, m = Math.floor(seg / 60) % 60, s = seg % 60;
  return <span className="tabular-nums">{d > 0 ? `${d}d ` : ""}{String(h).padStart(2, "0")}h {String(m).padStart(2, "0")}min {String(s).padStart(2, "0")}s</span>;
}
