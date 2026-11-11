package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.grupos.Grupos;
import br.com.frxcode.frxworld.habilidades.Habilidades;
import br.com.frxcode.frxworld.habilidades.Talentos;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.reinos.Campanhas;
import br.com.frxcode.frxworld.reinos.Reputacao;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Monta tudo o que a janela de status do jogador (tecla E) mostra, num JSON só. */
public final class JanelaStatus {
    private JanelaStatus() {}

    private static double attr(ServerPlayer p, Attribute a) {
        var i = p.getAttribute(a);
        return i == null ? 0 : i.getValue();
    }

    public static String montar(ServerPlayer p) {
        JsonObject o = new JsonObject();
        if (!Progressao.temDados(p)) return o.toString();
        DadosJogador d = Progressao.dados(p);
        o.addProperty("nome", p.getGameProfile().getName());
        o.addProperty("nivel", d.nivel());
        o.addProperty("nivelMax", ConfigJogo.nivelMaximo());
        o.addProperty("rank", ConfigJogo.rankDoNivel(d.nivel()));
        o.addProperty("xp", d.xp());
        o.addProperty("xpProximo", Progressao.xpParaProximo(d.nivel()));
        o.addProperty("classe", d.classe().isEmpty() ? "" : Habilidades.nomeClasse(d.classe()));
        if (!d.classe().isEmpty()) {
            o.addProperty("recurso", Habilidades.nomeRecurso(Habilidades.recursoDa(d.classe())));
            o.addProperty("recursoAtual", (int) Math.floor(d.recurso()));
            o.addProperty("recursoMax", Habilidades.maximo(p));
        }
        o.addProperty("moedas", Moedas.texto(Moedas.contar(p)));
        o.addProperty("banco", Moedas.texto(Banco.saldo(p)));
        o.addProperty("titulo", d.titulo());
        JsonArray ts = new JsonArray();
        for (String t : Reputacao.titulos(p)) ts.add(t);
        o.add("titulos", ts);
        o.addProperty("zona", Zonas.nivel(p.level(), p.blockPosition()));

        JsonObject at = new JsonObject();
        at.addProperty("vida", (int) Math.ceil(p.getHealth()));
        at.addProperty("vidaMax", (int) Math.ceil(p.getMaxHealth()));
        at.addProperty("ataque", Math.round(attr(p, Attributes.ATTACK_DAMAGE) * 10) / 10.0);
        at.addProperty("velAtaque", Math.round(attr(p, Attributes.ATTACK_SPEED) * 10) / 10.0);
        at.addProperty("armadura", Math.round(attr(p, Attributes.ARMOR) * 10) / 10.0);
        at.addProperty("resistencia", Math.round(attr(p, Attributes.ARMOR_TOUGHNESS) * 10) / 10.0);
        at.addProperty("velocidade", Math.round(attr(p, Attributes.MOVEMENT_SPEED) / 0.1 * 100));
        at.addProperty("sorte", Math.round(attr(p, Attributes.LUCK) * 10) / 10.0);
        at.addProperty("critico", Math.round(Talentos.bonus(p, Talentos.Atributo.CRITICO) * 100));
        at.addProperty("esquiva", Math.round(Math.min(.5, Talentos.bonus(p, Talentos.Atributo.ESQUIVA)) * 100));
        at.addProperty("roubo", Math.round(Talentos.bonus(p, Talentos.Atributo.ROUBO_VIDA) * 100));
        o.add("atributos", at);

        JsonObject es = new JsonObject();
        for (String k : new String[]{"matar_total", "mortes", "chefes", "invasoes", "campanhas", "forja_max"})
            es.addProperty(k, d.contadores().getOrDefault(k, 0));
        es.addProperty("conquistas", d.conquistas().size());
        o.add("estatisticas", es);

        o.add("talentos", Talentos.json(p));
        o.add("reinos", Reputacao.json(p));
        o.add("campanhas", Campanhas.json(p));
        o.add("conquistas", Conquistas.json(p));
        o.add("grupo", Grupos.json(p));
        return o.toString();
    }
}
