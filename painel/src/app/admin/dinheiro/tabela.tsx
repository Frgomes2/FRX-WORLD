"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Moedas } from "@/components/itens";

type J = { uuid: string; nick: string; moedas: number; banco: number; online: boolean; nivel: number; rank: string; ultimoLogin: string | null };
type Ordem = "total" | "bolsa" | "banco" | "nick";

/** "1o 2p 5c", "125" ou "1,5o" → cobre. Vazio ou inválido → null. */
function lerValor(t: string): number | null {
  const s = t.trim().toLowerCase().replace(/\s+/g, "");
  if (!s) return null;
  if (/^\d+$/.test(s)) return Number(s);
  const m = s.match(/^(?:(\d+)o)?(?:(\d+)p)?(?:(\d+)c)?$/);
  if (!m || (!m[1] && !m[2] && !m[3])) return null;
  return Number(m[1] ?? 0) * 100 + Number(m[2] ?? 0) * 10 + Number(m[3] ?? 0);
}

export function TabelaDinheiro({ jogadores }: { jogadores: J[] }) {
  const router = useRouter();
  const [lista, setLista] = useState(jogadores);
  const [ordem, setOrdem] = useState<Ordem>("total");
  const [busca, setBusca] = useState("");
  const [valores, setValores] = useState<Record<string, string>>({});
  const [ocupado, setOcupado] = useState("");
  const [aviso, setAviso] = useState<{ ok: boolean; texto: string } | null>(null);

  const total = lista.reduce((s, j) => s + j.moedas + j.banco, 0);
  const vistos = lista
    .filter((j) => j.nick.toLowerCase().includes(busca.trim().toLowerCase()))
    .sort((a, b) => ordem === "nick" ? a.nick.localeCompare(b.nick) : ordem === "bolsa" ? b.moedas - a.moedas : ordem === "banco" ? b.banco - a.banco : b.moedas + b.banco - (a.moedas + a.banco));

  const [editando, setEditando] = useState<{ uuid: string; texto: string } | null>(null);

  /** 25438 → "254o 3p 8c" (o mesmo formato que o campo aceita). */
  const emLetras = (c: number) => [[Math.floor(c / 100), "o"], [Math.floor((c % 100) / 10), "p"], [c % 10, "c"]].filter(([n]) => n).map(([n, l]) => `${n}${l}`).join(" ") || "0";

  async function salvarBanco(j: J) {
    if (!editando) return;
    setAviso(null);
    const v = lerValor(editando.texto);
    if (v === null || v < 0 || v > 999_999_999) { setAviso({ ok: false, texto: "Valor inválido. Use cobre (125) ou letras (1o 2p 5c)." }); return; }
    setOcupado(j.uuid);
    try {
      await mandar(j, "bancoDefinir", v);
      setLista((l) => l.map((x) => (x.uuid === j.uuid ? { ...x, banco: v } : x)));
      setEditando(null);
      setAviso({ ok: true, texto: j.online ? `Banco de ${j.nick} atualizado.` : `Banco de ${j.nick} atualizado. Como está offline, o valor entra no jogo no próximo login dele.` });
    } catch (e) {
      setAviso({ ok: false, texto: e instanceof Error ? e.message : "Não deu certo." });
    } finally {
      setOcupado("");
    }
  }

  async function mandar(j: J, acao: "tirarmoedas" | "banco" | "bancoDefinir", valor: number) {
    const r = await fetch(`/api/painel/jogadores/${j.uuid}/acao`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ acao, valor }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.erro ?? "Não deu certo.");
  }

  async function agir(j: J, tipo: "bolsa" | "banco" | "zerar") {
    setAviso(null);
    let novo = { ...j };
    try {
      if (tipo === "zerar") {
        if (!window.confirm(`Zerar todo o dinheiro de ${j.nick} (bolsa e banco)? Não dá para desfazer.`)) return;
        setOcupado(j.uuid);
        if (j.moedas > 0) await mandar(j, "tirarmoedas", j.moedas);
        await mandar(j, "banco", 0);
        novo = { ...j, moedas: 0, banco: 0 };
      } else {
        const v = lerValor(valores[j.uuid] ?? "");
        if (v === null || v <= 0) { setAviso({ ok: false, texto: "Digite quanto tirar: em cobre (125) ou com letras (1o 2p 5c)." }); return; }
        const tem = tipo === "bolsa" ? j.moedas : j.banco;
        const tirar = Math.min(v, tem);
        if (tirar <= 0) { setAviso({ ok: false, texto: `${j.nick} não tem nada ${tipo === "bolsa" ? "na bolsa" : "no banco"}.` }); return; }
        setOcupado(j.uuid);
        if (tipo === "bolsa") { await mandar(j, "tirarmoedas", tirar); novo = { ...j, moedas: j.moedas - tirar }; }
        else { await mandar(j, "banco", j.banco - tirar); novo = { ...j, banco: j.banco - tirar }; }
      }
      setLista((l) => l.map((x) => (x.uuid === j.uuid ? novo : x)));
      setValores((v) => ({ ...v, [j.uuid]: "" }));
      setAviso({ ok: true, texto: `Feito: saldo de ${j.nick} ajustado. O número oficial do servidor chega em instantes.` });
      setTimeout(() => router.refresh(), 4000);
    } catch (e) {
      setAviso({ ok: false, texto: e instanceof Error ? e.message : "Não deu certo." });
    } finally {
      setOcupado("");
    }
  }

  const botao = "rounded-md border border-pedra-700 px-2 py-1 text-xs hover:border-ouro disabled:cursor-not-allowed disabled:opacity-40";
  return (
    <>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="rounded-xl border border-ouro/40 bg-pedra-900 px-4 py-2">
          <p className="text-xs uppercase tracking-wider text-pedra-500">Dinheiro no mundo</p>
          <Moedas cobre={total} tam={18} className="font-titulo text-lg" />
        </div>
        <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Procurar jogador..." aria-label="Procurar jogador"
          className="rounded-md border border-pedra-700 bg-pedra-900 px-3 py-2 text-sm outline-none focus:border-ouro" />
        <label className="flex items-center gap-2 text-sm text-pedra-500">Ordenar por
          <select value={ordem} onChange={(e) => setOrdem(e.target.value as Ordem)} className="rounded-md border border-pedra-700 bg-pedra-900 px-2 py-2 text-pergaminho">
            <option value="total">Total</option><option value="bolsa">Bolsa</option><option value="banco">Banco</option><option value="nick">Nome</option>
          </select>
        </label>
      </div>
      {aviso && <p className={`mb-3 rounded-md border px-3 py-2 text-sm ${aviso.ok ? "border-green-800 text-green-300" : "border-sangue text-red-300"}`}>{aviso.texto}</p>}
      <div className="overflow-x-auto rounded-xl border border-pedra-700 bg-pedra-900">
        <table className="w-full min-w-[820px] text-sm">
          <thead className="text-left text-xs uppercase tracking-wider text-pedra-500">
            <tr><th className="px-3 py-2">Jogador</th><th className="px-3 py-2">Bolsa</th><th className="px-3 py-2">Banco</th><th className="px-3 py-2">Total</th><th className="px-3 py-2">Tirar (só online)</th></tr>
          </thead>
          <tbody>
            {vistos.map((j) => (
              <tr key={j.uuid} className="border-t border-pedra-700">
                <td className="px-3 py-2">
                  <Link href={`/admin/jogadores/${j.uuid}`} className="font-semibold hover:text-ouro">{j.nick}</Link>
                  <span className="ml-2 text-xs text-pedra-500">Nv {j.nivel} · {j.rank}</span>
                  <span className={`ml-2 text-xs ${j.online ? "text-green-400" : "text-pedra-500"}`}>{j.online ? "● online" : "offline"}</span>
                </td>
                <td className="px-3 py-2"><Moedas cobre={j.moedas} tam={14} /></td>
                <td className="px-3 py-2">
                  {editando?.uuid === j.uuid ? (
                    <form className="flex items-center gap-1.5" onSubmit={(e) => { e.preventDefault(); salvarBanco(j); }}>
                      <input autoFocus value={editando.texto} onChange={(e) => setEditando({ uuid: j.uuid, texto: e.target.value })} aria-label={`Novo saldo do banco de ${j.nick}`}
                        onKeyDown={(e) => { if (e.key === "Escape") setEditando(null); }}
                        className="w-28 rounded-md border border-ouro bg-pedra-950 px-2 py-1 text-xs outline-none" />
                      <button type="submit" className={botao} disabled={ocupado === j.uuid}>salvar</button>
                      <button type="button" className={botao} onClick={() => setEditando(null)}>cancelar</button>
                    </form>
                  ) : (
                    <button type="button" title="Editar o saldo do banco" onClick={() => setEditando({ uuid: j.uuid, texto: emLetras(j.banco) })}
                      className="group flex items-center gap-2 rounded-md px-1 py-0.5 hover:bg-pedra-800">
                      <Moedas cobre={j.banco} tam={14} /><span className="text-xs text-pedra-500 group-hover:text-ouro">✎ editar</span>
                    </button>
                  )}
                </td>
                <td className="px-3 py-2"><Moedas cobre={j.moedas + j.banco} tam={14} /></td>
                <td className="px-3 py-2">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <input value={valores[j.uuid] ?? ""} onChange={(e) => setValores((v) => ({ ...v, [j.uuid]: e.target.value }))} placeholder="ex.: 1o 5p" aria-label={`Quanto tirar de ${j.nick}`}
                      disabled={!j.online} className="w-24 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 text-xs outline-none focus:border-ouro disabled:opacity-40" />
                    <button type="button" className={botao} disabled={!j.online || ocupado === j.uuid} onClick={() => agir(j, "bolsa")}>da bolsa</button>
                    <button type="button" className={botao} disabled={!j.online || ocupado === j.uuid} onClick={() => agir(j, "banco")}>do banco</button>
                    <button type="button" className={`${botao} border-sangue text-red-300`} disabled={!j.online || ocupado === j.uuid || j.moedas + j.banco === 0} onClick={() => agir(j, "zerar")}>zerar tudo</button>
                  </div>
                </td>
              </tr>
            ))}
            {!vistos.length && <tr><td colSpan={5} className="px-3 py-6 text-center text-pedra-500">Nenhum jogador encontrado.</td></tr>}
          </tbody>
        </table>
      </div>
      <p className="mt-3 text-xs text-pedra-500">Valor em cobre (125) ou com letras: 1o 2p 5c = 1 ouro, 2 prata, 5 cobre. O banco pode ser editado com o jogador offline (o valor entra no próximo login); a bolsa só com ele no jogo, porque as moedas da bolsa são itens.</p>
    </>
  );
}
