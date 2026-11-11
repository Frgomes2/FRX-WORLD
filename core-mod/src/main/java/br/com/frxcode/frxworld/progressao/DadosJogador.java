package br.com.frxcode.frxworld.progressao;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Dados do FRX-WORLD salvos no jogador: nível e XP próprios (a XP vanilla continua servindo para encantar)
 * e o andamento das missões do quadro.
 */
public class DadosJogador {
    private int nivel = 1;
    private long xp = 0; // XP acumulada dentro do nível atual

    /** Andamento de uma missão: ativa com progresso, ou concluída em tal momento. */
    public static final class RegistroMissao {
        public boolean concluida;
        public int progresso;
        public long concluidaEm;
    }

    private final Map<String, RegistroMissao> missoes = new HashMap<>();
    /** Dungeon (dimensão + posição do núcleo) → quando o jogador recebeu o prêmio final. */
    private final Map<String, Long> dungeons = new HashMap<>();

    /** Habilidade aprendida: nível (1–10) e quantas vezes foi usada. */
    public static final class RegistroHabilidade {
        public int nivel = 1;
        public int usos = 0;
    }

    private String classe = "";
    private double recurso = 0;
    private final Map<String, RegistroHabilidade> habilidades = new HashMap<>();
    /** Barra de combate: 4 ativas + ultimate (posição 4). "" = vazio. */
    private final String[] barra = {"", "", "", "", ""};

    /** Reputação por reino (id do reino → pontos) e título escolhido ("" = nenhum). */
    private final Map<String, Integer> reputacao = new HashMap<>();
    private String titulo = "";
    public Map<String, Integer> reputacao() { return reputacao; }
    public String titulo() { return titulo; }
    public void definirTitulo(String t) { titulo = t == null ? "" : t; }

    /** Campanha do rei por reino: etapa atual (0..n; n = concluída) e progresso da etapa. */
    private final Map<String, Integer> campanhaEtapa = new HashMap<>();
    private final Map<String, Integer> campanhaProgresso = new HashMap<>();
    /** Contadores para conquistas (abates, chefes, invasões…) e conquistas obtidas (id → quando). */
    private final Map<String, Integer> contadores = new HashMap<>();
    private final Map<String, Long> conquistas = new HashMap<>();
    public Map<String, Integer> campanhaEtapa() { return campanhaEtapa; }
    public Map<String, Integer> campanhaProgresso() { return campanhaProgresso; }
    public Map<String, Integer> contadores() { return contadores; }
    public Map<String, Long> conquistas() { return conquistas; }

    /** Talentos: id → pontos investidos. */
    private final Map<String, Integer> talentos = new HashMap<>();
    public Map<String, Integer> talentos() { return talentos; }

    public String classe() { return classe; }
    public void definirClasse(String c) { classe = c == null ? "" : c; }
    public double recurso() { return recurso; }
    public void definirRecurso(double r) { recurso = r; }
    public Map<String, RegistroHabilidade> habilidades() { return habilidades; }
    public String[] barra() { return barra; }

    public int nivel() { return nivel; }
    public long xp() { return xp; }
    public Map<String, RegistroMissao> missoes() { return missoes; }
    public Map<String, Long> dungeons() { return dungeons; }

    public void definir(int nivel, long xp) {
        this.nivel = Math.max(1, nivel);
        this.xp = Math.max(0, xp);
    }

    public void copiarDe(DadosJogador outro) {
        carregar(outro.salvar());
    }

    public CompoundTag salvar() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("nivel", nivel);
        tag.putLong("xp", xp);
        ListTag lista = new ListTag();
        missoes.forEach((id, r) -> {
            CompoundTag m = new CompoundTag();
            m.putString("id", id);
            m.putBoolean("concluida", r.concluida);
            m.putInt("progresso", r.progresso);
            m.putLong("concluidaEm", r.concluidaEm);
            lista.add(m);
        });
        tag.put("missoes", lista);
        CompoundTag dg = new CompoundTag();
        dungeons.forEach(dg::putLong);
        tag.put("dungeons", dg);
        tag.putString("classe", classe);
        tag.putDouble("recurso", recurso);
        CompoundTag hab = new CompoundTag();
        habilidades.forEach((id, r) -> {
            CompoundTag h = new CompoundTag();
            h.putInt("nivel", r.nivel);
            h.putInt("usos", r.usos);
            hab.put(id, h);
        });
        tag.put("habilidades", hab);
        CompoundTag br = new CompoundTag();
        for (int i = 0; i < barra.length; i++) br.putString(String.valueOf(i), barra[i]);
        tag.put("barra", br);
        CompoundTag tal = new CompoundTag();
        talentos.forEach(tal::putInt);
        tag.put("talentos", tal);
        CompoundTag rep = new CompoundTag();
        reputacao.forEach(rep::putInt);
        tag.put("reputacao", rep);
        tag.putString("titulo", titulo);
        CompoundTag ce = new CompoundTag(); campanhaEtapa.forEach(ce::putInt); tag.put("campanhaEtapa", ce);
        CompoundTag cp = new CompoundTag(); campanhaProgresso.forEach(cp::putInt); tag.put("campanhaProgresso", cp);
        CompoundTag ct = new CompoundTag(); contadores.forEach(ct::putInt); tag.put("contadores", ct);
        CompoundTag cq = new CompoundTag(); conquistas.forEach(cq::putLong); tag.put("conquistas", cq);
        return tag;
    }

    public void carregar(CompoundTag tag) {
        definir(tag.contains("nivel") ? tag.getInt("nivel") : 1, tag.getLong("xp"));
        missoes.clear();
        ListTag lista = tag.getList("missoes", Tag.TAG_COMPOUND);
        for (int i = 0; i < lista.size(); i++) {
            CompoundTag m = lista.getCompound(i);
            RegistroMissao r = new RegistroMissao();
            r.concluida = m.getBoolean("concluida");
            r.progresso = m.getInt("progresso");
            r.concluidaEm = m.getLong("concluidaEm");
            missoes.put(m.getString("id"), r);
        }
        dungeons.clear();
        CompoundTag dg = tag.getCompound("dungeons");
        for (String k : dg.getAllKeys()) dungeons.put(k, dg.getLong(k));
        classe = tag.getString("classe");
        recurso = tag.getDouble("recurso");
        habilidades.clear();
        CompoundTag hab = tag.getCompound("habilidades");
        for (String k : hab.getAllKeys()) {
            RegistroHabilidade r = new RegistroHabilidade();
            r.nivel = Math.max(1, hab.getCompound(k).getInt("nivel"));
            r.usos = hab.getCompound(k).getInt("usos");
            habilidades.put(k, r);
        }
        CompoundTag br = tag.getCompound("barra");
        for (int i = 0; i < barra.length; i++) barra[i] = br.getString(String.valueOf(i));
        talentos.clear();
        CompoundTag tal = tag.getCompound("talentos");
        for (String k : tal.getAllKeys()) if (tal.getInt(k) > 0) talentos.put(k, tal.getInt(k));
        reputacao.clear();
        CompoundTag rep = tag.getCompound("reputacao");
        for (String k : rep.getAllKeys()) reputacao.put(k, rep.getInt(k));
        titulo = tag.getString("titulo");
        campanhaEtapa.clear(); CompoundTag ce = tag.getCompound("campanhaEtapa"); for (String k : ce.getAllKeys()) campanhaEtapa.put(k, ce.getInt(k));
        campanhaProgresso.clear(); CompoundTag cp = tag.getCompound("campanhaProgresso"); for (String k : cp.getAllKeys()) campanhaProgresso.put(k, cp.getInt(k));
        contadores.clear(); CompoundTag ct = tag.getCompound("contadores"); for (String k : ct.getAllKeys()) contadores.put(k, ct.getInt(k));
        conquistas.clear(); CompoundTag cq = tag.getCompound("conquistas"); for (String k : cq.getAllKeys()) conquistas.put(k, cq.getLong(k));
    }
}
