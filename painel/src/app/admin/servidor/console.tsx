"use client";

import { useEffect, useRef, useState } from "react";

type Estado = "parado" | "iniciando" | "online" | "parando";
type Evento =
  | { tipo: "linha"; texto: string }
  | { tipo: "historico"; linhas: string[] }
  | { tipo: "estado"; estado: Estado; jogadores: string[]; iniciadoEm: number | null };

const ROTULO: Record<Estado, { texto: string; cor: string }> = {
  parado: { texto: "Parado", cor: "bg-pedra-500" },
  iniciando: { texto: "Iniciando...", cor: "bg-yellow-500 animate-pulse" },
  online: { texto: "Online", cor: "bg-green-500" },
  parando: { texto: "Parando...", cor: "bg-orange-500 animate-pulse" },
};

function corDaLinha(l: string) {
  switch (true) {
    case l.startsWith("[painel]"):
      return "text-sky-300";
    case l.startsWith(">"):
      return "text-ouro";
    case /\/(ERROR|FATAL)\]|ERROR\]/.test(l):
      return "text-red-400";
    case /\/WARN\]|WARN\]/.test(l):
      return "text-yellow-300";
    case /frxworld|FRX-WORLD/i.test(l):
      return "text-ouro";
    default:
      return "text-pergaminho/85";
  }
}

function tempo(desde: number | null) {
  if (!desde) return "—";
  const s = Math.floor((Date.now() - desde) / 1000);
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  return h ? `${h}h ${m}min` : `${m}min ${s % 60}s`;
}

export function Console() {
  const [estado, setEstado] = useState<Estado>("parado");
  const [jogadores, setJogadores] = useState<string[]>([]);
  const [iniciadoEm, setIniciadoEm] = useState<number | null>(null);
  const [linhas, setLinhas] = useState<string[]>([]);
  const [comando, setComando] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [conectado, setConectado] = useState(false);
  const [seguir, setSeguir] = useState(true);
  const [, setTique] = useState(0);
  const caixa = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const fonte = new EventSource("/api/painel/servidor/logs");
    fonte.onopen = () => setConectado(true);
    fonte.onerror = () => setConectado(false);
    fonte.onmessage = (m) => {
      const ev = JSON.parse(m.data) as Evento;
      switch (ev.tipo) {
        case "historico":
          setLinhas(ev.linhas);
          break;
        case "linha":
          setLinhas((ls) => (ls.length > 3000 ? [...ls.slice(-2500), ev.texto] : [...ls, ev.texto]));
          break;
        case "estado":
          setEstado(ev.estado);
          setJogadores(ev.jogadores);
          setIniciadoEm(ev.iniciadoEm);
          break;
      }
    };
    const relogio = setInterval(() => setTique((t) => t + 1), 1000);
    return () => { fonte.close(); clearInterval(relogio); };
  }, []);

  useEffect(() => {
    if (seguir && caixa.current) caixa.current.scrollTop = caixa.current.scrollHeight;
  }, [linhas, seguir]);

  async function acao(nome: string, extra?: Record<string, string>) {
    setErro(null);
    const r = await fetch("/api/painel/servidor", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ acao: nome, ...extra }),
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) setErro(j.erro ?? "Falha na ação.");
  }

  async function reiniciarPainel() {
    if (!confirm("Reiniciar só o painel (site, admin e páginas dos jogadores)? O jogo continua no ar e ninguém é desconectado. O painel fica fora por 1 a 3 minutos (mais se precisar compilar).")) return;
    await acao("reiniciarPainel");
    setLinhas((ls) => [...ls, "[painel] Reiniciando só o painel... esta página volta sozinha quando ele subir."]);
  }

  async function gerarMundo() {
    if (!confirm("Gerar um mundo novo? O atual fica guardado em server/mundos-antigos (nada é apagado).")) return;
    const seed = prompt("Seed do mundo (deixe vazio para aleatória):", "") ?? "";
    await acao("novoMundo", { seed });
    if (!erro) setLinhas((ls) => [...ls, `[painel] Mundo novo preparado${seed ? ` (seed ${seed})` : ""}. Clique em Iniciar para gerar.`]);
  }

  function enviarComando(e: React.FormEvent) {
    e.preventDefault();
    if (!comando.trim()) return;
    acao("comando", { comando });
    setComando("");
  }

  const rodando = estado !== "parado";
  const botao = "rounded-md px-4 py-2 font-semibold disabled:cursor-not-allowed disabled:opacity-40";

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-6 rounded-xl border border-pedra-700 bg-pedra-900 p-5">
        <div className="flex items-center gap-3">
          <span className={`h-3 w-3 rounded-full ${ROTULO[estado].cor}`} />
          <span className="font-titulo text-2xl">{ROTULO[estado].texto}</span>
        </div>
        <div className="text-sm text-pedra-500">
          Ligado há <span className="text-pergaminho">{tempo(iniciadoEm)}</span>
        </div>
        <div className="text-sm text-pedra-500">
          Jogadores: <span className="text-pergaminho">{jogadores.length}</span>
          {jogadores.length > 0 && <span className="ml-1 text-pergaminho/70">({jogadores.join(", ")})</span>}
        </div>
        <div className="flex flex-wrap gap-2 sm:ml-auto">
          <button onClick={() => acao("iniciar")} disabled={rodando}
            className={`${botao} bg-green-600 text-white hover:bg-green-500`}>Iniciar</button>
          <button onClick={() => acao("reiniciar")} disabled={estado === "parando" || estado === "iniciando"}
            className={`${botao} bg-ouro text-pedra-950 hover:bg-ouro-escuro`}>Reiniciar</button>
          <button onClick={reiniciarPainel}
            className={`${botao} border border-sky-700 text-sky-300 hover:border-sky-400`}>Reiniciar só o painel</button>
          <button onClick={gerarMundo} disabled={rodando}
            className={`${botao} border border-pedra-700 text-pergaminho hover:border-ouro`}>Novo mundo</button>
          <button onClick={() => acao("parar")} disabled={estado !== "online" && estado !== "iniciando"}
            className={`${botao} bg-sangue text-white hover:bg-red-700`}>Parar</button>
        </div>
      </div>

      {erro && <p className="rounded-md border border-red-900 bg-red-950/50 px-4 py-2 text-sm text-red-300">{erro}</p>}

      <div className="overflow-hidden rounded-xl border border-pedra-700 bg-black/60">
        <div className="flex items-center justify-between border-b border-pedra-700 bg-pedra-800 px-4 py-2 text-xs text-pedra-500">
          <span>Console {conectado ? "· ao vivo" : "· reconectando..."}</span>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={seguir} onChange={(e) => setSeguir(e.target.checked)} />
            Rolar automático
          </label>
        </div>
        <div ref={caixa} className="h-[55vh] overflow-y-auto px-4 py-3 font-mono text-xs leading-5">
          {linhas.length === 0 && <p className="text-pedra-500">Sem saída ainda. Clique em Iniciar.</p>}
          {linhas.map((l, i) => (
            <div key={i} className={`whitespace-pre-wrap break-all ${corDaLinha(l)}`}>{l}</div>
          ))}
        </div>
        <form onSubmit={enviarComando} className="flex border-t border-pedra-700">
          <span className="px-3 py-2 font-mono text-ouro">&gt;</span>
          <input value={comando} onChange={(e) => setComando(e.target.value)} disabled={estado !== "online"}
            placeholder={estado === "online" ? "Comando do servidor (ex.: say Olá, op Frgomes, frx nivel Frgomes 10)" : "Servidor desligado"}
            className="flex-1 bg-transparent py-2 pr-3 font-mono text-sm outline-none disabled:opacity-50" />
        </form>
      </div>
    </div>
  );
}
