"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

const FUSO = "America/Sao_Paulo";
export const dataHora = (ms: number) => new Date(ms).toLocaleString("pt-BR", { timeZone: FUSO, day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
export const data = (ms: number) => new Date(ms).toLocaleDateString("pt-BR", { timeZone: FUSO, day: "2-digit", month: "2-digit", year: "numeric" });

/**
 * Contagem regressiva pelo relógio do SERVIDOR: a página manda o horário do servidor ao renderizar e a contagem
 * confere de novo pela API, guardando a diferença para o relógio do navegador. Celular com hora errada não atrapalha.
 */
export function Contagem({ alvo, servidor }: { alvo: number; servidor: number }) {
  const router = useRouter();
  const diferenca = useRef(servidor - Date.now());
  const [agora, setAgora] = useState(servidor);
  const recarregou = useRef(false);
  useEffect(() => {
    let vivo = true;
    const antes = Date.now();
    fetch("/api/publico/expedicoes", { cache: "no-store" }).then((r) => (r.ok ? r.json() : null)).then((j) => {
      if (vivo && j && typeof j.agora === "number") diferenca.current = j.agora + (Date.now() - antes) / 2 - Date.now();
    }).catch(() => {});
    const t = setInterval(() => setAgora(Date.now() + diferenca.current), 1000);
    return () => { vivo = false; clearInterval(t); };
  }, []);
  const resta = Math.max(0, alvo - agora);
  useEffect(() => {
    if (resta > 0 || recarregou.current) return;
    recarregou.current = true;
    const t = setTimeout(() => router.refresh(), 45_000);   // a troca leva alguns segundos; depois a página mostra a expedição nova
    return () => clearTimeout(t);
  }, [resta, router]);
  const h = Math.floor(resta / 3_600_000), m = Math.floor((resta % 3_600_000) / 60_000), s = Math.floor((resta % 60_000) / 1000);
  const dois = (n: number) => String(n).padStart(2, "0");
  if (resta <= 0) return <p className="font-titulo text-3xl text-ouro">Trocando de expedição...</p>;
  return (
    <p className="font-titulo text-5xl tabular-nums tracking-wider text-ouro sm:text-6xl" aria-live="off">
      {h >= 24 ? `${Math.floor(h / 24)}d ` : ""}{dois(h % 24)}:{dois(m)}:{dois(s)}
    </p>
  );
}

/** Atualiza a página a cada minuto (quem está na expedição agora, números). */
export function Atualizar() {
  const router = useRouter();
  useEffect(() => {
    const t = setInterval(() => router.refresh(), 60_000);
    return () => clearInterval(t);
  }, [router]);
  return null;
}
