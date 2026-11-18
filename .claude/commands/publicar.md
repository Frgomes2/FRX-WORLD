---
description: Publica o projeto — atualiza a documentação com tudo o que mudou desde o último /publicar, faz o commit e envia direto para a main.
---

# /publicar

Padrão do Flavio para todos os projetos ligados ao git. Faça os passos na ordem, sem pedir confirmação a cada um. Pare e avise só se algum passo falhar.

## 1. Achar o último /publicar

```
git log --grep="^publicar:" -1 --format="%H %ad %s" --date=short
```

- Achou: esse commit é a **base**.
- Não achou (primeira vez): a base é o último commit que já está no GitHub (`origin/main`). Diga isso no resumo final.

## 2. Levantar o que mudou desde a base

```
git log <base>..HEAD --oneline
git diff <base> --stat
git status --short
```

Conte tanto os commits feitos depois da base quanto o que ainda não foi commitado. Leia o diff dos arquivos de código para entender **o que mudou de verdade**, não só os nomes.

## 3. Conferir antes de publicar

- **Segredos fora:** nada de `painel/.env`, `server/config/frxworld-common.toml`, `server/config/resourceful-config-web.json`, tokens, senhas ou chaves. Se algum arquivo assim aparecer no `git status`, pare e avise.
- **Autor:** `git config user.name` = `Frgomes2` e `user.email` = `Frgomes2@users.noreply.github.com`. Nunca usar o e-mail real do Flavio.
- **Branch:** tem que ser a `main`. Se não for, pare e avise.
- **Compilação:** o último `logs/compilacao.log` termina em `BUILD SUCCESSFUL` e o `logs/painel-build.log` não tem erro. Se o código mudou depois do último build, peça um reinício pelo agente (`controle/pedido.txt` com `reiniciar`) e confira de novo.

## 4. Atualizar TODA a documentação afetada

Para cada mudança levantada no passo 2, atualize o documento que fala daquele assunto. Não basta o changelog.

| Mudou | Atualizar |
|---|---|
| Qualquer coisa | `docs/07-changelog.md` (entrada com a data e o que entrou) |
| Um sistema do jogo | o arquivo dele em `docs/sistemas/` (crie um novo se o sistema for novo e ponha no índice do `docs/README.md`) |
| Comando novo ou alterado | `docs/04-comandos.md` |
| Tela, menu ou configuração do painel | `docs/05-painel.md` |
| Mod externo adicionado ou removido | `docs/06-mods-externos.md` |
| Scripts, agente, backup, mundo, reinício | `docs/03-operacao.md` |
| Kit dos amigos, acesso, convites, endereço | `docs/08-jogar-com-amigos.md` |
| Estrutura de pastas, fluxo entre mod/painel/pack | `docs/02-arquitetura.md` |
| Versão do mod | `docs/README.md` e `core-mod/gradle.properties` |

**Versão:** cada atualização que vai ao ar soma 1 no último número (`0.41.8` → `0.41.9`); depois do `.9` sobe o do meio e o último zera (`0.41.9` → `0.42.0`); só uma mudança muito grande, que mexe em muita coisa, abre versão cheia (`1.0.0`). A nota nova em `painel/src/lib/notas.ts`, o `mod_version` e o `docs/README.md` levam o mesmo número, e a nota recebe a hora em que entrou no ar.

Regras: escreva em português, descreva o comportamento como está agora (não a história das tentativas), e não deixe informação velha contradizendo a nova no mesmo arquivo. Se um documento já estava certo, não mexa.

## 5. Commit

```
git add -A
git commit
```

Mensagem:

```
publicar: <resumo em uma linha do que entrou>

- <mudança 1>
- <mudança 2>
- Docs: <arquivos de documentação atualizados>
```

A primeira linha **sempre** começa com `publicar:` — é assim que o próximo /publicar acha este commit. Termine a mensagem com as linhas de atribuição que a sessão indicar, se houver.

## 6. Enviar para a main

`git push origin main`.

No PC do Flavio o shell do Claude não tem credencial do GitHub. Se o push falhar por isso, use o caminho do pacote:

1. `git bundle create logs/frx.bundle <último commit que está no GitHub>..HEAD`
2. No ambiente que tem acesso ao GitHub: `git fetch <bundle> HEAD:novo && git merge --ff-only novo && git push origin main`

Depois do push, se o git tiver deixado arquivos `.lock` em `.git`, limpe-os.

## 7. Resumo final

Responda em poucas linhas: o código do commit, o que foi publicado (em tópicos), quais documentos foram atualizados e se algo ficou de fora (e por quê).
