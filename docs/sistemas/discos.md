# Discos de música

[← Índice](../README.md) · Código: `musica/Discos.java`, `musica/ItemDisco.java` · Painel: `/admin/discos`, `src/lib/discos.ts`, `src/lib/codex.ts` (pacote de recursos)

O Mercador de Discos vende discos cujas músicas o dono do servidor envia pelo painel.

## Cadastro no painel

- **Discos de música** (grupo Economia e itens): nome, artista, estilo de música, descrição, preço em moedas, rank mínimo da Guilda, ordem na loja, aparência e o arquivo.
- **Arquivo:** só `.ogg` (OGG Vorbis), até 9 MB. O painel confere o formato e lê duração e canais. MP3 não é aceito porque o Minecraft não toca.
- **Mono ou estéreo:** em mono o som diminui com a distância da jukebox (cerca de 64 blocos). Em estéreo ele toca no volume cheio para quem estava por perto quando começou.
- **Estilo de música:** texto livre (até 24 letras). O campo sugere os estilos já usados e uma lista pronta; o mesmo estilo precisa ser escrito sempre igual, porque o filtro da loja agrupa pelo texto (sem diferenciar maiúsculas). A lista do painel também filtra por estilo.
- **Unidades no mundo:** 0 = sem limite. Com um número, o mercador vende só essa quantidade (contada em `<mundo>/frx_discos.json`, informada ao painel a cada venda), cada disco sai numerado ("nº 3 de 10") e depois a loja mostra "esgotado". Disco destruído ou perdido não volta ao estoque; baixar o limite abaixo do já vendido só esgota a loja.
- O áudio fica em `painel/dados/discos/<codigo>.ogg` (pasta fora do git). O código é gerado do nome e não muda depois.
- **Aparência:** três caminhos, todos na mesma tela. Escolher uma cor (o painel desenha o disco nela), importar um PNG quadrado de 16×16 ou 32×32, ou desenhar na grade. Sem desenho próprio, vale uma das 16 aparências prontas do jogo. O desenho vai no pacote de recursos como `CustomModelData` 1000 + número do disco.

## Como chega aos jogadores

- As músicas entram no **pacote de recursos** gerado pelo painel (o mesmo das armas do Códex), como o som `frxworld:disco.<codigo>`. O jogo de cada jogador baixa o pacote ao entrar e de novo quando ele muda.
- Quem recusar o pacote compra e usa o disco normalmente, mas não ouve a música.
- O tamanho de todas as músicas somadas é o tamanho do download; a tela do painel mostra o total.

## No jogo

- **NPC:** papel `discos` ("Mercador de Discos"). Colocar com `/frx npc aqui discos`. A loja é uma tela própria (`cliente/TelaDiscos`): cartões 4 × 2, botão **Estilo** que filtra pelo estilo de música (aparece quando algum disco tem estilo; Shift + clique volta), **Ouvir** para escutar antes e **Comprar**.
- **Item:** `frxworld:disco`, um só para todos os discos; o NBT guarda código, nome, artista, descrição, duração e a aparência (`CustomModelData` 1 a 16).
- **Jukebox:** é a jukebox comum. Clicar com o disco numa jukebox vazia guarda o disco nos dados dela e toca a música; clicar de novo (com qualquer coisa na mão) devolve o disco e para o som. Quebrar a jukebox também devolve.
- O disco **não** é um disco do jogo: funis não o movem e o comparador não o lê.
- Quem chega perto depois de a música começar não ouve (como nos discos do jogo); basta tirar e pôr de novo.
