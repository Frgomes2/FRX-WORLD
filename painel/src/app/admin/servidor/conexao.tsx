"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type Amostra = { t: number; ms: number };
type Jogador = { nick: string; online: boolean; agora: number | null; media: number; pior: number; melhor: number; amostras: Amostra[] };
type Dados = {
  agora: number; ligado: boolean;
  servidor: { tps: number; mspt: number; msptMedio: number | null; msptPico: number | null; memPct: number | null; cpu: number | null } | null;
  jogadores: Jogador[];
};

const DURACAO = 60_000;
const cor = (ms: number) => (ms < 100 ? "#4ade80" : ms < 250 ? "#facc15" : "#f87171");
const nota = (ms: number) => (ms < 100 ? "boa" : ms < 250 ? "regular" : ms < 1000 ? "ruim" : "muito ruim");

/** Conclusão em português a partir do servidor e dos pings. */
function parecer(d: Dados): { tom: "ok" | "aviso" | "ruim"; titulo: string; texto: string } {
  if (!d.ligado || !d.servidor) return { tom: "aviso", titulo: "Servidor desligado", texto: "Não há o que medir enquanto o servidor não estiver ligado." };
  const on = d.jogadores.filter((j) => j.online);
  const pesado = d.servidor.tps < 18 || (d.servidor.msptMedio ?? 0) > 45;
  if (pesado) return { tom: "ruim", titulo: "O servidor está pesado", texto: `TPS ${d.servidor.tps} e ${d.servidor.msptMedio ?? d.servidor.mspt} ms por tick. O atraso que os jogadores sentem vem do próprio servidor: veja a aba Monitoramento.` };
  if (!on.length) return { tom: "ok", titulo: "Servidor saudável, ninguém online", texto: "Peça para alguém entrar e rode o teste de novo para medir a rede." };
  const ruins = on.filter((j) => j.media >= 250);
  if (!ruins.length) return { tom: "ok", titulo: "Tudo certo", texto: "O servidor está leve e o ping de todos está aceitável." };
  if (ruins.length === on.length && on.length > 1)
    return { tom: "ruim", titulo: "Rede ruim para todos: túnel ou internet do servidor", texto: "O servidor está leve, mas todos estão com ping alto. Feche e abra o playit, confira se há VPN ligada ou algo usando a internet do PC do servidor." };
  if (ruins.length === on.length)
    return { tom: "aviso", titulo: "Ping alto, mas só há um jogador online", texto: "Com uma pessoa só não dá para saber se é o túnel ou a internet dela. Meça de novo com mais gente." };
  return { tom: "aviso", titulo: "Rede ruim só para alguns", texto: `O servidor e o túnel estão bem. O problema parece ser a internet de: ${ruins.map((j) => j.nick).join(", ")}.` };
}

export function Conexao() {
  const [d, setD] = useState<Dados | null>(null);
  const [erro, setErro] = useState("");
  const [teste, setTeste] = useState<{ inicio: number; fim: number } | null>(null);
  const [resultado, setResultado] = useState<Dados | null>(null);
  const testeRef = useRef(teste);
  useEffect(() => { testeRef.current = teste; }, [teste]);

  const buscar = useCallback(async () => {
    try {
      const t = testeRef.current;
      const r = await fetch(`/api/painel/conexao${t ? `?desde=${t.inicio}` : ""}`, { cache: "no-store" });
      if (!r.ok) { setErro("Não consegui ler os dados do servidor."); return; }
      const j = (await r.json()) as Dados;
      setErro("");
      if (t && j.agora >= t.fim) { setResultado(j); setTeste(null); } else setD(j);
    } catch { setErro("Não consegui ler os dados do servidor."); }
  }, []);

  useEffect(() => {
    const primeira = setTimeout(buscar, 0);
    const id = setInterval(buscar, 5000);
    return () => { clearTimeout(primeira); clearInterval(id); };
  }, [buscar]);

  const rodar = () => { const agora = Date.now(); setResultado(null); setTeste({ inicio: agora, fim: agora + DURACAO }); };
  const mostra = resultado ?? d;
  const p = mostra ? parecer(mostra) : null;
  const resta = teste && d ? Math.max(0, Math.ceil((teste.fim - d.agora) / 1000)) : 0;
  const tomCor = { ok: "border-green-500/50 bg-green-500/10", aviso: "border-yellow-500/50 bg-yellow-500/10", ruim: "border-red-500/50 bg-red-500/10" };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-pedra-700 bg-pedra-900/70 p-4">
        <div className="min-w-0 flex-1">
          <h2 className="font-titulo text-lg text-ouro">📶 Teste de conexão</h2>
          <p className="text-sm text-pedra-500">Mede por 1 minuto o ping de quem está online e diz se o atraso vem do servidor, do túnel ou da internet de alguém.</p>
        </div>
        <button onClick={rodar} disabled={!!teste || !d?.ligado}
          className="rounded-lg bg-gradient-to-b from-ouro to-ouro-escuro px-5 py-2 text-sm font-semibold text-pedra-950 disabled:cursor-not-allowed disabled:opacity-50">
          {teste ? `Medindo… ${resta}s` : resultado ? "Rodar de novo" : "Rodar teste"}
        </button>
      </div>
      {teste && (
        <div className="h-1.5 overflow-hidden rounded-full bg-pedra-800">
          <div className="h-full bg-ouro transition-all duration-1000" style={{ width: `${Math.min(100, 100 - (resta / (DURACAO / 1000)) * 100)}%` }} />
        </div>
      )}
      {erro && <p className="text-sm text-red-400">{erro}</p>}

      {p && (
        <div className={`rounded-xl border p-4 ${tomCor[p.tom]}`}>
          <p className="font-semibold text-pergaminho">{resultado ? "Resultado: " : "Agora: "}{p.titulo}</p>
          <p className="mt-1 text-sm text-pergaminho/80">{p.texto}</p>
        </div>
      )}

      {mostra?.servidor && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {([
            ["TPS", String(mostra.servidor.tps), mostra.servidor.tps >= 19 ? "#4ade80" : mostra.servidor.tps >= 15 ? "#facc15" : "#f87171", "20 é o máximo"],
            ["Tempo por tick", `${mostra.servidor.msptMedio ?? mostra.servidor.mspt} ms`, (mostra.servidor.msptMedio ?? mostra.servidor.mspt) < 40 ? "#4ade80" : "#f87171", `pico ${mostra.servidor.msptPico ?? "—"} ms · trava acima de 50`],
            ["Processador", mostra.servidor.cpu === null ? "—" : `${mostra.servidor.cpu}%`, (mostra.servidor.cpu ?? 0) < 85 ? "#4ade80" : "#f87171", "da máquina"],
            ["Memória", mostra.servidor.memPct === null ? "—" : `${mostra.servidor.memPct}%`, (mostra.servidor.memPct ?? 0) < 90 ? "#4ade80" : "#f87171", "do servidor do jogo"],
          ] as [string, string, string, string][]).map(([rot, val, c, sub]) => (
            <div key={rot} className="rounded-xl border border-pedra-700 bg-pedra-900/70 p-3">
              <p className="text-xs uppercase tracking-wider text-pedra-500">{rot}</p>
              <p className="font-titulo text-2xl" style={{ color: c }}>{val}</p>
              <p className="text-[11px] text-pedra-500">{sub}</p>
            </div>
          ))}
        </div>
      )}

      <div className="overflow-x-auto rounded-xl border border-pedra-700 bg-pedra-900/70">
        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase tracking-wider text-pedra-500">
            <tr><th className="p-3">Jogador</th><th className="p-3">Agora</th><th className="p-3">Média</th><th className="p-3">Melhor</th><th className="p-3">Pior</th><th className="p-3">{resultado ? "Durante o teste" : "Últimos 30 min"}</th></tr>
          </thead>
          <tbody>
            {!mostra?.jogadores.length && <tr><td colSpan={6} className="p-4 text-pedra-500">{mostra?.ligado ? "Ninguém online para medir." : "Servidor desligado."}</td></tr>}
            {mostra?.jogadores.map((j) => {
              const teto = Math.max(300, ...j.amostras.map((a) => a.ms));
              const ult = j.amostras.slice(-60);
              return (
                <tr key={j.nick} className="border-t border-pedra-800">
                  <td className="p-3 font-semibold text-pergaminho">{j.nick}{!j.online && <span className="ml-2 text-xs font-normal text-pedra-500">saiu</span>}</td>
                  <td className="p-3" style={{ color: j.agora === null ? undefined : cor(j.agora) }}>{j.agora === null ? "—" : `${j.agora} ms`}</td>
                  <td className="p-3" style={{ color: cor(j.media) }}>{j.media} ms <span className="text-xs text-pedra-500">({nota(j.media)})</span></td>
                  <td className="p-3 text-pedra-500">{j.melhor} ms</td>
                  <td className="p-3" style={{ color: cor(j.pior) }}>{j.pior} ms</td>
                  <td className="p-3">
                    <div className="flex h-8 items-end gap-px" title={`${j.amostras.length} medições`}>
                      {ult.map((a) => (
                        <span key={a.t} title={`${new Date(a.t).toLocaleTimeString("pt-BR")} · ${a.ms} ms`} className="w-1.5 rounded-sm"
                          style={{ height: `${Math.max(8, Math.min(100, (a.ms / teto) * 100))}%`, background: cor(a.ms) }} />
                      ))}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-pedra-500">Ping bom: até 100 ms. Regular: até 250 ms. Acima disso o jogador sente atraso. O servidor informa o ping a cada 10 segundos, e o valor de cada jogador é uma média móvel do próprio jogo, então mudanças bruscas levam alguns segundos para aparecer.</p>
    </div>
  );
}
