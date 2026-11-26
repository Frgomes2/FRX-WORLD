// FRX: a Esfera Portátil de Pets foi trocada pela Pokébola do Treinador (2.0.12).
// Sem receita; quem ainda tem uma só consegue soltar o bicho que está dentro.
ServerEvents.recipes(event => {
  event.remove({ output: 'petcapturetool:capture_tool' })
})
