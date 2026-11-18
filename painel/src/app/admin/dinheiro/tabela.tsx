"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Fragment, useState } from "react";
import { Moedas } from "@/components/itens";

type J = { uuid: string; nick: string; moedas: number; banco: number; online: boolean; nivel: number; rank: string; ultimoLogin: string | null };
type Ordem = "total" | "bolsa" | "banco" | "nick";
type Tres = { ouro: string; prata: string; cobre: string };
type Edicao = { uuid: string; bolsa: Tres; banco: Tres };

const MAXIMO = 999_999_999;
const partes = (c: number): Tres => ({ ouro: String(Math.floor(c / 100)), prata: String(Math.floor((c % 100) / 10)), cobre: String(c % 10) });
/** Ouro, prata e cobre digitados → valor em cobre. Campo vazio vale 0; qualquer coisa que não seja número inteiro → null. */
function somar(t: Tres): number | null {
  const n = [t.ouro, t.prata, t.cobre].map((x) => (x.trim() === "" ? 0 : /^\d{1,9}$/.test(x.trim()) ? Number(x.trim()) : NaN));
  if (n.some(Number.isNaN)) return null;
  const v = n[0] * 100 + n[1] * 10 + n[2];
  return v > MAXIMO ? null : v;
}

function CamposMoeda({ titulo, nota, valor, mudar, travado, antes }: { titulo: string; nota: string; valor: Tres; mudar: (t: Tres) => void; travado: boolean; antes: number }) {
  const total = somar(valor);
  const campo = "w-20 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1.5 text-sm outline-none focus:border-ouro disabled:opacity-40";
  const tipos = [["ouro", "Ouro"], ["prata", "Prata"], ["cobre", "Cobre"]] as const;
  return (
    <fieldset disabled={travado} className="min-w-0 flex-1 rounded-lg border border-pedra-700 p-3">
      <legend className="px-1 text-xs uppercase tracking-wider text-pedra-500">{titulo}</legend>
      <div className="flex flex-wrap items-end gap-2">
        {tipos.map(([k, nome]) => (
          <label key={k} className="block text-xs text-pedra-500">{nome}
            <input inputMode="numeric" value={valor[k]} onChange={(e) => mudar({ ...valor, [k]: e.target.value.replace(/\D/g, "") })}
              onFocus={(e) => e.target.select()} className={`mt-1 block ${campo}`} />
          </label>
        ))}
        <div className="flex gap-1.5">
          <button type="button" onClick={() => mudar(partes(0))} className="rounded-md border border-pedra-700 px-2 py-1.5 text-xs hover:border-ouro disabled:opacity-40">zerar</button>
          <button type="button" onClick={() => mudar(partes(antes))} className="rounded-md border border-pedra-700 px-2 py-1.5 text-xs hover:border-ouro disabled:opacity-40">desfazer</button>
        </div>
      </div>
      <p className="mt-2 flex flex-wrap items-center gap-2 text-xs text-pedra-500">
        {total === null ? <span className="text-red-300">Valor inválido ou alto demais.</span> : total === antes ? <span>Sem mudança.</span> : (
          <><span>Fica com</span><Moedas cobre={total} tam={13} /><span className={total > antes ? "text-green-400" : "text-red-300"}>({total > antes ? "+" : "−"}{Math.abs(total - antes)} em cobre)</span></>
        )}
      </p>
      <p className="mt-1 text-xs text-pedra-500">{nota}</p>
    </fieldset>
  );
}

export function TabelaDinheiro({ jogadores }: { jogadores: J[] }) {
  const router = useRouter();
  const [lista, setLista] = useState(jogadores);
  const [ordem, setOrdem] = useState<Ordem>("total");
  const [busca, setBusca] = useState("");
  const [ocupado, setOcupado] = useState("");
  const [aviso, setAviso] = useState<{ ok: boolean; texto: string } | null>(null);
  const [edicao, setEdicao] = useState<Edicao | null>(null);

  const total = lista.reduce((s, j) => s + j.moedas + j.banco, 0);
  const vistos = lista
    .filter((j) => j.nick.toLowerCase().includes(busca.trim().toLowerCase()))
    .sort((a, b) => ordem === "nick" ? a.nick.localeCompare(b.nick) : ordem === "bolsa" ? b.moedas - a.moedas : ordem === "banco" ? b.banco - a.banco : b.moedas + b.banco - (a.moedas + a.banco));

  async function mandar(j: J, acao: "bolsaDefinir" | "bancoDefinir", valor: number) {
    const r = await fetch(`/api/painel/jogadores/${j.uuid}/acao`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ acao, valor }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.erro ?? "Não deu certo.");
  }

  async function salvar(j: J) {
    if (!edicao) return;
    setAviso(null);
    const bolsa = j.online ? somar(edicao.bolsa) : j.moedas, banco = somar(edicao.banco);
    if (bolsa === null || banco === null) { setAviso({ ok: false, texto: "Confira os valores: só números inteiros, sem passar de 9.999.999 de ouro." }); return; }
    if (bolsa === j.moedas && banco === j.banco) { setEdicao(null); return; }
    setOcupado(j.uuid);
    let novo = j;
    try {
      // um de cada vez: se o segundo falhar, a tela fica com o que de fato mudou
      if (banco !== j.banco) { await mandar(j, "bancoDefinir", banco); novo = { ...novo, banco }; }
      if (bolsa !== j.moedas) { await mandar(j, "bolsaDefinir", bolsa); novo = { ...novo, moedas: bolsa }; }
      setEdicao(null);
      setAviso({ ok: true, texto: j.online ? `Dinheiro de ${j.nick} atualizado. O número conferido pelo servidor chega em instantes.` : `Banco de ${j.nick} atualizado. Como ele está fora do jogo, o valor entra no próximo login.` });
      setTimeout(() => router.refresh(), 4000);
    } catch (e) {
      setAviso({ ok: false, texto: e instanceof Error ? e.message : "Não deu certo." });
    } finally {
      const fim = novo;
      setLista((l) => l.map((x) => (x.uuid === j.uuid ? fim : x)));
      setOcupado("");
    }
  }

  const botao = "rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro disabled:cursor-not-allowed disabled:opacity-40";
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
        <table className="w-full min-w-[720px] text-sm">
          <thead className="text-left text-xs uppercase tracking-wider text-pedra-500">
            <tr><th className="px-3 py-2">Jogador</th><th className="px-3 py-2">Bolsa</th><th className="px-3 py-2">Banco</th><th className="px-3 py-2">Total</th><th className="px-3 py-2"><span className="sr-only">Editar</span></th></tr>
          </thead>
          <tbody>
            {vistos.map((j) => {
              const aberto = edicao?.uuid === j.uuid;
              return (
                <Fragment key={j.uuid}>
                  <tr className="border-t border-pedra-700">
                    <td className="px-3 py-2">
                      <Link href={`/admin/jogadores/${j.uuid}`} className="font-semibold hover:text-ouro">{j.nick}</Link>
                      <span className="ml-2 text-xs text-pedra-500">Nv {j.nivel} · {j.rank}</span>
                      <span className={`ml-2 text-xs ${j.online ? "text-green-400" : "text-pedra-500"}`}>{j.online ? "● online" : "offline"}</span>
                    </td>
                    <td className="px-3 py-2"><Moedas cobre={j.moedas} tam={14} /></td>
                    <td className="px-3 py-2"><Moedas cobre={j.banco} tam={14} /></td>
                    <td className="px-3 py-2"><Moedas cobre={j.moedas + j.banco} tam={14} /></td>
                    <td className="px-3 py-2 text-right">
                      <button type="button" className={`${botao} ${aberto ? "border-ouro text-ouro" : ""}`} aria-expanded={aberto}
                        onClick={() => { setAviso(null); setEdicao(aberto ? null : { uuid: j.uuid, bolsa: partes(j.moedas), banco: partes(j.banco) }); }}>{aberto ? "Fechar" : "Editar"}</button>
                    </td>
                  </tr>
                  {aberto && edicao && (
                    <tr className="bg-pedra-950/60">
                      <td colSpan={5} className="px-3 pb-4 pt-1">
                        <form onSubmit={(e) => { e.preventDefault(); salvar(j); }}>
                          <div className="flex flex-wrap gap-3">
                            <CamposMoeda titulo="Bolsa (com o jogador)" antes={j.moedas} valor={edicao.bolsa} travado={!j.online} mudar={(t) => setEdicao({ ...edicao, bolsa: t })}
                              nota={j.online ? "As moedas da bolsa são trocadas na hora, no inventário dele." : `${j.nick} está fora do jogo. A bolsa só muda com ele dentro, porque as moedas são itens no inventário.`} />
                            <CamposMoeda titulo="Banco" antes={j.banco} valor={edicao.banco} travado={false} mudar={(t) => setEdicao({ ...edicao, banco: t })}
                              nota={j.online ? "Vale na hora." : "Vale no próximo login dele."} />
                          </div>
                          <div className="mt-3 flex flex-wrap items-center gap-2">
                            <button type="submit" disabled={ocupado === j.uuid} className="rounded-md bg-ouro px-4 py-1.5 text-sm font-semibold text-pedra-950 disabled:opacity-50">{ocupado === j.uuid ? "Salvando..." : "Salvar"}</button>
                            <button type="button" className={botao} onClick={() => setEdicao(null)}>Cancelar</button>
                            <span className="text-xs text-pedra-500">10 de cobre viram 1 de prata e 10 de prata viram 1 de ouro: se você puser 25 de prata, o jogo guarda 2 de ouro e 5 de prata.</span>
                          </div>
                        </form>
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
            {!vistos.length && <tr><td colSpan={5} className="px-3 py-6 text-center text-pedra-500">Nenhum jogador encontrado.</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  );
}
