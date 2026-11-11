# 08 — Jogar com amigos (servidor no seu PC)

[← Índice](README.md)

O servidor roda no seu PC. Os amigos entram no jogo pelo **playit.gg** e baixam os mods pelo **Cloudflare Tunnel**. Os dois são grátis, não precisam abrir porta no roteador e funcionam mesmo com CGNAT.

```
Amigo ─ jogo ──▶ playit.gg   (impairment-marcus.tun.ply.gg)  ──▶ localhost:25565 (Minecraft)
Amigo ─ mods ──▶ Cloudflare  (https://pack.frgomes.com.br)   ──▶ localhost:8080  (packwiz serve)
Admin ─ painel ▶ Cloudflare  (https://craft.frgomes.com.br)  ──▶ localhost:3000  (painel)
```

No playit grátis, o túnel TCP genérico (que serviria para o pack) é só do plano Premium. Por isso o pack vai pela Cloudflare, no mesmo túnel do painel.

## Configuração (uma vez, no seu PC)

1. Crie uma conta em **playit.gg** e instale o programa para Windows.
2. Abra o playit e vincule-o à sua conta (ele abre um link de "claim" no navegador).
3. No site do playit, em **Tunnels → Add Tunnel**, crie o túnel do jogo: tipo *Minecraft Java*, porta local **25565**. Endereço atual: `impairment-marcus.tun.ply.gg` (já está no `LEIA-ME.txt` do kit).
4. **Pack:** no túnel `frx-world` da Cloudflare, adicione a rota `pack` · `frgomes.com.br` → `http://localhost:8080`, com o campo **Path vazio** (se tiver algo, só aquele arquivo passa e o resto dá 404). O `amigos\endereco-pack.txt` já aponta para `https://pack.frgomes.com.br/pack.toml`.
5. Pronto. O atalho **FRX-WORLD** liga o playit junto (passo `[3a]`) e o **Desligar** fecha. O túnel da Cloudflare é um serviço do Windows e fica sempre ligado.

## Kit do amigo (`amigos\`)

Compacte a pasta `amigos\` (já com o `endereco-pack.txt` preenchido) e mande para cada amigo.

| Arquivo | O que faz |
|---|---|
| `LEIA-ME.txt` | Passo a passo para o amigo |
| `1-instalar-forge.bat` | Instala o Forge 1.20.1-47.4.10 no Minecraft Launcher (uma vez) |
| `2-atualizar-mods.bat` | Baixa/atualiza os mods em `%APPDATA%\.frx-world` a partir do seu pack. Rodar antes de cada sessão |
| `endereco-pack.txt` | URL do pack (você preenche) |
| `packwiz-installer-bootstrap.jar` | Programa que baixa os mods |

**O amigo precisa de:**
- Minecraft Java original (o servidor é `online-mode=true`);
- Java 17 (Temurin);
- uns 8 GB de RAM.

## Convites

O servidor exige convite no primeiro login (`acesso.exigirConvite = true`):

1. No painel, abra **Convites** e gere um código.
2. Mande o código para o amigo.
3. Ao entrar, ele digita `/convite CÓDIGO` em até 3 minutos.

Assim, só entra quem você convidou, mesmo com o endereço público.

## Sem o túnel do pack

Se não der para expor o pack, mande junto com o kit a pasta `cliente\jogo\mods` (cerca de 550 MB). O amigo copia para `%APPDATA%\.frx-world\mods`. Cada versão nova do core mod exige mandar de novo pelo menos o `frxworld-<versão>.jar`, porque a versão do cliente tem de ser igual à do servidor.

## Limites de rodar em casa

- O servidor só fica no ar com o seu PC ligado e o atalho rodado.
- A sua internet de **upload** limita quantos jogadores entram bem. Para um grupo pequeno de amigos costuma bastar.
- Quando o grupo crescer ou o servidor precisar ficar 24 h no ar, mude para um VPS (por exemplo, um Hostinger de 16 GB em São Paulo).

---

## Painel para os outros admins (Cloudflare Tunnel)

O painel continua rodando no seu PC e fica acessível em **https://craft.frgomes.com.br** através de um Cloudflare Tunnel. É grátis, tem HTTPS e não precisa abrir porta no roteador.

```
Admin (navegador) ──HTTPS──▶ Cloudflare ──túnel──▶ cloudflared (seu PC) ──▶ localhost:3000 (painel)
```

### Configuração (uma vez)

1. O domínio `frgomes.com.br` precisa estar com o DNS na Cloudflare.
2. Instale o cloudflared no Windows: `winget install --id Cloudflare.cloudflared` (num terminal como administrador).
3. No painel da Cloudflare, em **Zero Trust → Networks → Tunnels**:
   - Crie um túnel `frx-world` do tipo **Cloudflared**.
   - Escolha **Windows** e rode, como administrador, o comando `cloudflared.exe service install <token>` que ele mostrar. Isso instala o túnel como **serviço do Windows**: ele liga sozinho junto com o PC.
4. Ainda no túnel, em **Public Hostname**:
   - subdomínio `craft`, domínio `frgomes.com.br`;
   - serviço **HTTP** → `localhost:3000`.
5. Ligue pelo atalho **FRX-WORLD** e abra `https://craft.frgomes.com.br`.

Com o PC desligado ou o painel parado, o endereço mostra um erro da Cloudflare (502). É o esperado.

### Cadastrar os admins

No painel: **Administradores** → Adicionar admin (nome, e-mail, senha inicial). Passe o e-mail e a senha para o amigo; ele troca a senha em **Administradores → Minha senha**.

- Todo admin pode tudo: ligar e desligar o servidor, console, jogadores, economia e conteúdo.
- Ninguém remove a si mesmo nem o último admin.
- **Login:** 5 senhas erradas seguidas bloqueiam aquele e-mail por 15 minutos.

### Segurança

- O painel roda em **modo produção** (`next start`): o atalho só recompila (`logs\painel-build.log`) quando o código do painel muda.
- As rotas `/api/servidor/*`, usadas pelo mod e pelos atalhos, só respondem a chamadas locais. Chamadas que chegam pela Cloudflare recebem **403**, mesmo com o token (`src/proxy.ts`).
- **Camada extra opcional:** em Zero Trust → Access, crie uma aplicação para `craft.frgomes.com.br` com política "e-mails permitidos". A Cloudflare passa a pedir um código por e-mail antes de mostrar a tela de login.

---

## Amigos com TLauncher / Shiginima (servidor sem autenticação da Mojang)

Esses launchers não fazem login na Mojang. Para eles entrarem, o servidor precisa rodar com `online-mode=false` em `server\server.properties` (e `enforce-secure-profile=false`). **Essa troca é decisão do dono do servidor e se faz à mão.** Sem autenticação, qualquer pessoa pode entrar com qualquer nick, inclusive o seu.

O core mod (0.41+) protege isso sozinho quando detecta o servidor offline:

1. Primeiro login: `/convite CÓDIGO` e depois `/registrar SENHA SENHA`. A senha fica presa ao nick.
2. Nas outras vezes: `/login SENHA`.
3. Até entrar na conta, o jogador fica parado e protegido, sem chat e sem comandos (só convite, login e registrar). Com 5 senhas erradas, é desconectado.
4. Esqueceu a senha: `/frx senha <nick> resetar`. No próximo login ele cria outra.

As senhas ficam só como hash (PBKDF2 com sal) em `server\frxworld\senhas.json`, fora do mundo e fora do git.

Com o servidor online (`online-mode=true`), nada disso aparece: só o convite.

**Ao trocar para offline:**
- Os UUIDs mudam, então os jogadores começam do zero. Faça a troca junto com o mundo novo.
- Dê OP de novo ao seu nick pelo console: `op SeuNick`.
- O kit do amigo (`2-atualizar-mods.bat`) pergunta o launcher: com TLauncher ou Shiginima, os mods vão para a `.minecraft`. O LEIA-ME explica cada launcher.
