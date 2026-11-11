"""Reduz a caixa de um reino: guarda o original em server/frxworld/reinos-originais/<id>-<data>/
e deixa em reinos/<id>/ só as fatias dentro da nova caixa, renumeradas a partir do novo canto."""
import json, os, shutil, sys, time
raiz = os.path.join(os.path.dirname(__file__), "..", "server", "frxworld")
def reduzir(rid, ox0, oz0, larg, prof, centro_x, centro_z):
    pasta = os.path.join(raiz, "reinos", rid)
    bkp = os.path.join(raiz, "reinos-originais", f"{rid}-{time.strftime('%Y%m%d-%H%M')}")
    os.makedirs(os.path.dirname(bkp), exist_ok=True)
    shutil.move(pasta, bkp)
    os.makedirs(pasta)
    man = json.load(open(os.path.join(bkp, "reino.json"), encoding="utf-8"))
    novos = []
    for ox, oz in man["chunks"]:
        nx, nz = ox - ox0, oz - oz0
        if 0 <= nx < larg and 0 <= nz < prof:
            shutil.copy2(os.path.join(bkp, f"{ox}_{oz}.nbt"), os.path.join(pasta, f"{nx}_{nz}.nbt"))
            novos.append([nx, nz])
    man["larguraChunks"], man["profundidadeChunks"], man["chunks"] = larg, prof, novos
    if "entrada" in man:
        e = man["entrada"]; man["entrada"] = [e[0] - ox0 * 16, e[1], e[2] - oz0 * 16]
    json.dump(man, open(os.path.join(pasta, "reino.json"), "w", encoding="utf-8"), ensure_ascii=False)
    p = os.path.join(raiz, "reinos", "posicoes.json")
    pos = json.load(open(p, encoding="utf-8"))
    pos[rid]["x"], pos[rid]["z"] = centro_x, centro_z
    json.dump(pos, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(rid, "->", larg, "x", prof, "chunks,", len(novos), "fatias; original em", bkp)

if __name__ == "__main__":
    # Tempest: só a área do print (x -288..543, z -272..287) -> canto chunk (-18,-17); caixa antiga começava em (-55,-115)
    reduzir("tempest", 37, 98, 52, 35, 128, 8)
    # Dwargon: 20x20 chunks (igual ao Oeste) em volta do trono, NPCs e poço de entrada -> canto chunk (32,121); antiga (7,113)
    reduzir("dwargon", 25, 8, 20, 20, 680, 2100)
