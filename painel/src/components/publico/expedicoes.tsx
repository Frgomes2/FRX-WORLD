"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";


/**
 * Contagem regressiva pelo relógio do SERVIDOR: a página manda o horário do servidor ao renderizar e a contagem
 * confere de novo pela API, guardando a diferença para o relógio do navegador. Celular com hora errada não atrapalha.
 */
export function Contagem({ alvo, servidor, className = "font-titulo text-5xl tabular-nums tracking-wider text-ouro sm:text-6xl" }: { alvo: number; servidor: number; className?: string }) {
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
    <p className={className} aria-live="off">
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

/** Rosa dos ventos das Terras Inexploradas (gira devagar). */
export function Rosa({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 200 200" className={`frx-rosa ${className}`} aria-hidden="true">
      <circle cx="100" cy="100" r="92" fill="none" stroke="currentColor" strokeWidth="1.5" strokeDasharray="2 6" />
      <circle cx="100" cy="100" r="74" fill="none" stroke="currentColor" strokeWidth="1" />
      <circle cx="100" cy="100" r="18" fill="none" stroke="currentColor" strokeWidth="1.5" />
      <polygon points="100,8 110,90 100,100 90,90" fill="currentColor" />
      <polygon points="100,192 110,110 100,100 90,110" fill="currentColor" opacity="0.55" />
      <polygon points="8,100 90,90 100,100 90,110" fill="currentColor" opacity="0.55" />
      <polygon points="192,100 110,90 100,100 110,110" fill="currentColor" opacity="0.55" />
      <polygon points="35,35 96,92 100,100 92,96" fill="currentColor" opacity="0.35" />
      <polygon points="165,35 104,92 100,100 108,96" fill="currentColor" opacity="0.35" />
      <polygon points="35,165 96,108 100,100 92,104" fill="currentColor" opacity="0.35" />
      <polygon points="165,165 104,108 100,100 108,104" fill="currentColor" opacity="0.35" />
      <text x="100" y="30" textAnchor="middle" fontSize="14" fill="currentColor" fontWeight="700">N</text>
    </svg>
  );
}
