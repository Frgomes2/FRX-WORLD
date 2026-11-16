"""Gera a campanha principal do FRX-WORLD no formato do FTB Quests 1.20.1 (2001.x).

Uso: python3 gerar_ftbquests.py <pasta config/ftbquests/quests>
As tarefas usam conquistas ocultas do core mod (frxworld:frx/...), dimensões e mortes de chefes.
As recompensas chamam /frx xp e /frx moedas (moedas em cobre: 100 = 1 ouro).
ATENÇÃO: sobrescreve os capítulos gerados (os arquivos que este script cria). Edições feitas no jogo nesses capítulos se perdem.
"""
import hashlib, os, sys

SAIDA = sys.argv[1] if len(sys.argv) > 1 else "quests"


def qid(*partes):
    """ID estável de 16 dígitos hexadecimais (FTB Quests usa long em hex)."""
    h = hashlib.sha1("|".join(partes).encode()).hexdigest()[:16].upper()
    return "1234567"[int(h[0], 16) % 7] + h[1:]   # primeiro dígito 1–7: cabe num long positivo


def s(t):
    return '"' + t.replace("\\", "\\\\").replace('"', '\\"') + '"'


REINOS = [("tempest", "Tempest", "minecraft:light_blue_banner"), ("dwargon", "Dwargon", "minecraft:anvil"),
          ("sarion", "Sarion", "minecraft:amethyst_shard"), ("leste", "Império do Leste", "minecraft:red_banner"),
          ("leste-sakura", "Sakura", "minecraft:pink_petals"), ("oeste", "Oeste", "minecraft:shield"),
          ("lubelius", "Lubelius", "minecraft:totem_of_undying"), ("demonios", "Lordes Demônios", "minecraft:wither_skeleton_skull")]


class Cap:
    def __init__(self, arq, titulo, icone, ordem):
        self.arq, self.titulo, self.icone, self.ordem, self.quests = arq, titulo, icone, ordem, []

    def q(self, chave, titulo, desc, tarefas, x, y, deps=(), recompensas=(), forma="", tam=1.0):
        self.quests.append(dict(id=qid(self.arq, chave), chave=chave, titulo=titulo, desc=desc, tarefas=tarefas, x=x, y=y,
                                deps=list(deps), recompensas=list(recompensas), forma=forma, tam=tam))
        return qid(self.arq, chave)

    def snbt(self):
        L = ["{", "\tdefault_hide_dependency_lines: false", '\tdefault_quest_shape: ""', f"\tfilename: {s(self.arq)}", '\tgroup: ""',
             f"\ticon: {s(self.icone)}", f"\tid: {s(qid('cap', self.arq))}", f"\torder_index: {self.ordem}", "\tquest_links: [ ]", "\tquests: ["]
        for q in self.quests:
            L.append("\t\t{")
            if q["deps"]:
                L.append("\t\t\tdependencies: [" + " ".join(s(d) for d in q["deps"]) + "]")
            L.append("\t\t\tdescription: [" + " ".join(s(t) for t in q["desc"]) + "]")
            L.append(f"\t\t\tid: {s(q['id'])}")
            if q["recompensas"]:
                L.append("\t\t\trewards: [")
                for i, r in enumerate(q["recompensas"]):
                    rid = qid(self.arq, q["chave"], "r", str(i))
                    if r[0] == "cmd":
                        L += ["\t\t\t\t{", f"\t\t\t\t\tcommand: {s(r[1])}", "\t\t\t\t\televate_perms: true", f"\t\t\t\t\tid: {s(rid)}",
                              "\t\t\t\t\tsilent: true", f"\t\t\t\t\ttitle: {s(r[2])}", '\t\t\t\t\ttype: "command"', "\t\t\t\t}"]
                    else:   # item
                        L += ["\t\t\t\t{", f"\t\t\t\t\tcount: {r[2]}", f"\t\t\t\t\tid: {s(rid)}", f"\t\t\t\t\titem: {s(r[1])}", '\t\t\t\t\ttype: "item"', "\t\t\t\t}"]
                L.append("\t\t\t]")
            if q["forma"]:
                L.append(f"\t\t\tshape: {s(q['forma'])}")
            if q["tam"] != 1.0:
                L.append(f"\t\t\tsize: {q['tam']}d")
            L.append("\t\t\ttasks: [")
            for i, t in enumerate(q["tarefas"]):
                tid = qid(self.arq, q["chave"], "t", str(i))
                L.append("\t\t\t\t{")
                if t[0] == "adv":
                    L += [f"\t\t\t\t\tadvancement: {s('frxworld:frx/' + t[1])}", '\t\t\t\t\tcriterion: ""', f"\t\t\t\t\tid: {s(tid)}", '\t\t\t\t\ttype: "advancement"']
                elif t[0] == "dim":
                    L += [f"\t\t\t\t\tdimension: {s(t[1])}", f"\t\t\t\t\tid: {s(tid)}", '\t\t\t\t\ttype: "dimension"']
                elif t[0] == "kill":
                    L += [f"\t\t\t\t\tentity: {s(t[1])}", f"\t\t\t\t\tid: {s(tid)}", '\t\t\t\t\ttype: "kill"', f"\t\t\t\t\tvalue: {t[2]}L"]
                else:
                    L += [f"\t\t\t\t\tid: {s(tid)}", '\t\t\t\t\ttype: "checkmark"']
                L.append("\t\t\t\t}")
            L.append("\t\t\t]")
            L.append(f"\t\t\ttitle: {s(q['titulo'])}")
            L.append(f"\t\t\tx: {float(q['x'])}d")
            L.append(f"\t\t\ty: {float(q['y'])}d")
            L.append("\t\t}")
        L += ["\t]", f"\ttitle: {s(self.titulo)}", "}"]
        return "\n".join(L) + "\n"


def premio(xp, cobre):
    r = []
    if xp: r.append(("cmd", f"/frx xp {{p}} {xp}", f"{xp} XP"))
    if cobre: r.append(("cmd", f"/frx moedas {{p}} {cobre}", f"{cobre // 100} ouro" if cobre >= 100 else f"{cobre} cobre"))
    return r


caps = []

# ------------------------------------------------------------------ 0. Prólogo
c = Cap("prologo", "Prólogo: O Despertar", "minecraft:compass", 0); caps.append(c)
a = c.q("classe", "Escolha seu caminho", ["Aperte K e escolha sua classe.", "", "Cada classe tem 4 habilidades, uma ultimate e um Despertar inspirado num anime."],
        [("adv", "classe")], 0, 0, recompensas=premio(100, 50), forma="gear", tam=1.5)
b = c.q("raca", "Sangue e origem", ["Abra a aba Status (E) → ✦ Raça, ou use /raca.", "", "Principal 100%, secundária pela compatibilidade."],
        [("adv", "raca")], 2, 0, [a], premio(100, 50))
t = c.q("tempest", "O Império Sagrado de Lubelius", ["Lubelius é a capital onde tudo começa. Visite a praça, fale com os NPCs e registre-se na Guilda."],
        [("adv", "capital_lubelius")], 4, 0, [b], premio(150, 100))
tv = c.q("taverna", "Primeiro contrato", ["Na taverna, o Quadro de Missões tem contratos repetíveis: caça, coleta e mais.", "", "Conclua um."],
         [("adv", "taverna_1")], 6, -1, [t], premio(200, 150))
n5 = c.q("nivel5", "Aventureiro", ["Chegue ao nível 5."], [("adv", "nivel_5")], 6, 1, [t], premio(0, 200))
n10 = c.q("nivel10", "O mundo se abre", ["Chegue ao nível 10.", "", "O Despertar libera e a Floresta do Crepúsculo passa a ser a sua faixa."],
          [("adv", "nivel_10")], 8, 0, [tv, n5], [("item", "minecraft:golden_apple", 2)] + premio(300, 300), "gear", 1.5)
c.q("despertar", "Despertar", ["Encha a barra lutando e aperte B para soltar o poder da sua classe."], [("adv", "despertar")], 10, 0, [n10], premio(300, 200))

# ------------------------------------------------------------------ 1. Reinos
c = Cap("reinos", "Os Oito Reinos", "minecraft:bell", 1); caps.append(c)
for i, (rid, nome, icone) in enumerate(REINOS):
    y = i * 1.5
    v = c.q(f"visitar_{rid}", f"Chegar a {nome}", [f"Encontre a capital de {nome}. Os portões têm waystones."], [("adv", f"capital_{rid}")], 0, y, recompensas=premio(150, 100))
    h = c.q(f"honrado_{rid}", f"Honrado em {nome}", [f"Chegue a Honrado (500 de reputação) com {nome}.", "", "Libera a raça especial ligada a este reino."],
            [("adv", f"honrado_{rid}")], 2, y, [v], premio(500, 400))
    c.q(f"campanha_{rid}", f"A palavra do rei: {nome}", [f"Conclua a campanha do soberano de {nome}."], [("adv", f"campanha_{rid}")], 4, y, [h],
        [("item", "minecraft:diamond", 3)] + premio(1500, 1500))

# ------------------------------------------------------------------ História principal (aventura/Historia.java)
# Os capítulos são concluídos pelo mod (conquistas frxworld:frx/historia_N), que também paga a recompensa. Manter igual a Historia.CAPS.
HISTORIA = [
    ("O Despertar", "Você renasceu neste mundo sem nome nem passado. A Guilda de Aventureiros é onde toda lenda começa.", "Registre-se na Guilda (recepcionista, em qualquer capital).", 200, 150, "minecraft:writable_book"),
    ("Primeiro Sangue", "Os arredores estão infestados. Prove que sabe lutar.", "Derrote 30 monstros.", 300, 300, "minecraft:iron_sword"),
    ("A Federação", "Dizem que no oeste um slime fundou uma nação de monstros. Vá ver com os próprios olhos.", "Viaje até a Federação de Jura Tempest.", 500, 500, "minecraft:slime_ball"),
    ("Prova de Valor", "Ninguém respeita um Rank F. Suba na Guilda.", "Alcance o Rank E na prova de promoção.", 600, 600, "minecraft:name_tag"),
    ("Fendas no Céu", "Portais se abrem perto das cidades e despejam monstros. Alguém precisa fechá-los.", "Ajude a fechar 1 portal (/portal). Título: Selador de Fendas.", 1000, 1000, "minecraft:crying_obsidian"),
    ("O Martelo e a Montanha", "Os anões de Dwargon forjam as melhores armas. Você vai precisar delas.", "Viaje até o Reino Armado de Dwargon.", 800, 800, "minecraft:anvil"),
    ("Guardiões Antigos", "As masmorras escondem guardiões que não deveriam ter acordado.", "Derrote 2 chefes.", 1500, 1500, "minecraft:wither_skeleton_skull"),
    ("A Floresta dos Elfos", "Os magos de Sarion estudam os portais. Talvez saibam de onde eles vêm.", "Viaje até a Dinastia Feiticeira de Sarion.", 1000, 1000, "minecraft:amethyst_shard"),
    ("O Império do Leste", "O Imperador reúne exércitos. Descubra de que lado ele está.", "Viaje até o Império do Leste.", 1200, 1200, "minecraft:red_banner"),
    ("Sangue de Veterano", "A estrada daqui em diante não perdoa os fracos.", "Chegue ao nível 30. Título: Veterano.", 2000, 2000, "minecraft:diamond_chestplate"),
    ("A Torre", "Uma torre sem fim guarda os segredos do mundo antigo.", "Chegue ao andar 10 da Torre Infinita (/torre mostra onde fica a entrada).", 2500, 2500, "minecraft:deepslate_bricks"),
    ("Entre a Elite", "Só os melhores recebem as missões que importam.", "Alcance o Rank C da Guilda.", 3000, 3000, "minecraft:golden_helmet"),
    ("Terras Sombrias", "Os portais vêm do reino dos demônios. Atravesse a fronteira.", "Viaje até o reino dos Lordes Demônios.", 3500, 3500, "minecraft:soul_lantern"),
    ("O Trono Escarlate", "O fim da jornada. Leve aliados.", "Dentro do reino dos demônios, use /historia desafio e derrote o Rei Demônio. Título: Matador do Rei Demônio.", 20000, 15000, "minecraft:nether_star"),
]
c = Cap("historia", "História Principal", "minecraft:enchanted_book", 1); caps.append(c)
ant = None
for i, (tit, texto, objetivo, cobre, xp, icone) in enumerate(HISTORIA):
    fila, col = divmod(i, 7)
    x = (col if fila % 2 == 0 else 6 - col) * 2
    ultimo = i == len(HISTORIA) - 1
    ant = c.q(f"cap{i + 1}", f"{i + 1}. {tit}", [texto, "", "Objetivo: " + objetivo, "",
                                                 f"Recompensa (entregue na hora pelo Sistema): {cobre // 100} ouro e {xp} XP.", "Acompanhe também com /historia."],
              [("adv", f"historia_{i + 1}")], x, fila * 2.5, [ant] if ant else [], forma="gear" if ultimo or i == 0 else "", tam=2.0 if ultimo else 1.5 if i == 0 else 1.0)

# ------------------------------------------------------------------ dimensões
def dimensao(arq, titulo, icone, ordem, nivel, dim, chefes, desc_dim):
    c = Cap(arq, f"{titulo} · Nv {nivel}", icone, ordem); caps.append(c)
    base = c.q("nivel", f"Pronto para {titulo}", [f"Chegue ao nível {nivel}.", "", "Abaixo disso a região é extremamente perigosa."],
               [("adv", f"nivel_{nivel}")], 0, 0, recompensas=premio(0, 100 * nivel // 5))
    ent = c.q("entrar", f"Entrar: {titulo}", desc_dim, [("dim", dim)], 2, 0, [base], premio(50 * nivel, 50 * nivel), "gear", 1.5)
    ant = ent
    for i, (eid, nome, qtd, extra) in enumerate(chefes):
        xp = int(80 * nivel * (1 + i * 0.5))
        rec = premio(xp, 40 * nivel * (1 + i))
        if extra: rec = [("item", extra[0], extra[1])] + rec
        ant = c.q(f"chefe_{i}", nome, [f"Derrote {nome}." if qtd == 1 else f"Derrote {qtd}× {nome}."], [("kill", eid, qtd)], 4 + i * 2, 0, [ant], rec,
                  "hexagon" if qtd == 1 else "", 1.25 if qtd == 1 else 1.0)
    return c


dimensao("crepusculo", "Floresta do Crepúsculo", "twilightforest:twilight_portal_miniature_structure", 2, 10, "twilightforest:twilight_forest", [
    ("twilightforest:naga", "a Naga", 1, ("minecraft:diamond", 1)), ("twilightforest:lich", "o Lich", 1, ("minecraft:diamond", 2)),
    ("twilightforest:minoshroom", "o Minoshroom", 1, None), ("twilightforest:hydra", "a Hidra", 1, ("minecraft:diamond", 3)),
    ("twilightforest:knight_phantom", "o Cavaleiro Fantasma", 3, None), ("twilightforest:ur_ghast", "o Ur-Ghast", 1, None),
    ("twilightforest:alpha_yeti", "o Yeti Alfa", 1, None), ("twilightforest:snow_queen", "a Rainha da Neve", 1, ("minecraft:netherite_scrap", 1))],
    ["Construa o portal: um lago de 2×2 cercado de flores, e jogue um diamante.", "", "Monstros Nv 10–25. A Floresta tem sua própria trilha de chefes."])
dimensao("nether", "Nether", "minecraft:netherrack", 3, 15, "minecraft:the_nether", [
    ("minecraft:blaze", "Blazes", 10, None), ("minecraft:wither_skeleton", "Esqueletos Wither", 5, None), ("minecraft:wither", "o Wither", 1, ("minecraft:nether_star", 1))],
    ["Monstros Nv 15–35. O nível sobe rápido: cada bloco no Nether vale 8 na superfície."])
dimensao("undergarden", "Undergarden", "undergarden:depthrock", 4, 20, "undergarden:undergarden", [
    ("undergarden:masticator", "o Mastigador", 1, None), ("undergarden:forgotten_guardian", "o Guardião Esquecido", 1, ("minecraft:diamond", 3))],
    ["Portal: moldura de pedra-abismal (como a do Nether) acesa com o Catalisador.", "", "Monstros Nv 20–35."])
dimensao("everbright", "Everbright", "blue_skies:turquoise_stonebrick", 5, 25, "blue_skies:everbright", [
    ("blue_skies:summoner", "o Invocador", 1, None), ("blue_skies:alchemist", "o Alquimista", 1, ("minecraft:diamond", 3))],
    ["A dimensão gelada do Blue Skies. Monstros Nv 25–40."])
dimensao("aether", "Aether", "aether:holystone", 6, 30, "aether:the_aether", [
    ("aether:slider", "o Deslizador", 1, None), ("aether:valkyrie_queen", "a Rainha Valquíria", 1, None), ("aether:sun_spirit", "o Espírito do Sol", 1, ("minecraft:netherite_scrap", 2))],
    ["Portal: moldura de glowstone acesa com um balde de água.", "", "Monstros Nv 30–45."])
dimensao("fim", "O Fim", "minecraft:end_stone", 7, 30, "minecraft:the_end", [
    ("minecraft:ender_dragon", "o Dragão do Fim", 1, ("minecraft:elytra", 1))],
    ["Encontre a fortaleza e ative o portal com olhos de Ender.", "", "Monstros Nv 30–50."])
dimensao("everdawn", "Everdawn", "blue_skies:lunar_stonebrick", 8, 35, "blue_skies:everdawn", [
    ("blue_skies:starlit_crusher", "o Esmagador Estelar", 1, None), ("blue_skies:arachnarch", "a Aracnarca", 1, ("minecraft:netherite_scrap", 2))],
    ["A dimensão do crepúsculo eterno do Blue Skies. Monstros Nv 35–50."])
dimensao("outro_lado", "O Outro Lado", "deeperdarker:heart_of_the_deep", 9, 45, "deeperdarker:otherside", [
    ("deeperdarker:stalker", "Espreitadores", 3, None), ("minecraft:warden", "o Warden", 1, ("minecraft:netherite_ingot", 1))],
    ["Portal escondido nas Cidades Antigas, aceso com o coração do Warden.", "", "Monstros Nv 45–60. A região mais perigosa do mundo."])

# ------------------------------------------------------------------ Lendas
c = Cap("lendas", "Lendas", "minecraft:nether_star", 10); caps.append(c)
el = c.q("elite", "Caçador de Elites", ["Derrote um monstro [Elite]."], [("adv", "elite")], 0, 0, recompensas=premio(500, 300))
cp = c.q("campeao", "Matador de Campeões", ["Derrote um [CAMPEÃO]: eles surgem raramente e têm título próprio."], [("adv", "campeao")], 2, 0, [el], [("item", "minecraft:diamond", 2)] + premio(2000, 1500))
dg = c.q("dungeon", "Profundezas da Cripta", ["Desça as 3 camadas de uma dungeon e derrote o guardião final."], [("adv", "dungeon")], 0, 2, recompensas=premio(1500, 1000))
cm = c.q("chefe_mundial", "Contra o Titã", ["Ajude a derrotar um Chefe Mundial."], [("adv", "chefe_mundial")], 2, 2, [dg], premio(3000, 2500))
re = c.q("raca_especial", "Sangue raro", ["Assuma uma raça especial como principal."], [("adv", "raca_especial")], 4, 1, [cp], premio(1000, 500))
n50 = c.q("nivel50", "Veterano", ["Chegue ao nível 50."], [("adv", "nivel_50")], 4, 3, [cm], premio(0, 5000))
rl = c.q("raca_lendaria", "Despertar lendário", ["Desperte uma raça lendária: Anjo, Rei Demônio, Dragão ou Ser Celestial."], [("adv", "raca_lendaria")], 6, 1, [re],
         [("item", "minecraft:enchanted_golden_apple", 1)] + premio(5000, 5000), "gear", 1.5)
c.q("nivel100", "Imortal", ["Chegue ao nível 100."], [("adv", "nivel_100")], 6, 3, [n50, rl], [("item", "minecraft:nether_star", 1)] + premio(0, 20000), "gear", 2.0)

# ------------------------------------------------------------------ gravar
os.makedirs(os.path.join(SAIDA, "chapters"), exist_ok=True)
os.makedirs(os.path.join(SAIDA, "reward_tables"), exist_ok=True)
with open(os.path.join(SAIDA, "data.snbt"), "w", encoding="utf-8") as f:
    f.write("""{
\tdefault_autoclaim_rewards: "disabled"
\tdefault_consume_items: false
\tdefault_quest_disable_jei: false
\tdefault_quest_shape: "circle"
\tdefault_reward_team: false
\tdetection_delay: 20
\tdisable_gui: false
\tdrop_loot_crates: false
\temergency_items_cooldown: 300
\tgrid_scale: 0.5d
\ticon: "minecraft:writable_book"
\tlock_message: ""
\tloot_crate_no_drop: {
\t\tboss: 0
\t\tmonster: 600
\t\tpassive: 4000
\t}
\tpause_game: false
\tprogression_mode: "flexible"
\ttitle: "FRX-WORLD · Campanha"
\tversion: 13
}
""")
for cap in caps:
    if cap.arq != "historia" and cap.ordem >= 1: cap.ordem += 1   # a História entra logo depois do Prólogo
with open(os.path.join(SAIDA, "chapter_groups.snbt"), "w", encoding="utf-8") as f:
    f.write("{\n\tchapter_groups: [ ]\n}\n")
for c in caps:
    with open(os.path.join(SAIDA, "chapters", c.arq + ".snbt"), "w", encoding="utf-8") as f:
        f.write(c.snbt())
print("capitulos:", len(caps), "quests:", sum(len(c.quests) for c in caps))
