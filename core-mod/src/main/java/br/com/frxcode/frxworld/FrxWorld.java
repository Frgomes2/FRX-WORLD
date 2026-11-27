package br.com.frxcode.frxworld;

import br.com.frxcode.frxworld.acesso.ControleAcesso;
import br.com.frxcode.frxworld.blocos.Blocos;
import br.com.frxcode.frxworld.efeitos.Efeitos;
import br.com.frxcode.frxworld.progressao.EventosMorte;
import br.com.frxcode.frxworld.taverna.Tavernas;
import br.com.frxcode.frxworld.progressao.EventosProgressao;
import br.com.frxcode.frxworld.rede.Rede;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Ponto de entrada do core mod do FRX-WORLD.
 * Fase 0: acesso por convite. Fase 1: nível, XP e HUD.
 * A partir da fase 1 o mod é obrigatório também no cliente (HUD e rede).
 */
@Mod(FrxWorld.MOD_ID)
public class FrxWorld {
    public static final String MOD_ID = "frxworld";
    public static final Logger LOG = LogUtils.getLogger();

    public FrxWorld() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(EventosProgressao::registrarCapabilities);
        modBus.addListener(this::setup);
        Efeitos.registrar(modBus);
        Blocos.registrar(modBus);
        br.com.frxcode.frxworld.moedas.Moedas.registrar(modBus);
        br.com.frxcode.frxworld.armas.Armas.registrar(modBus);
        br.com.frxcode.frxworld.armas.Reliquias.registrar(modBus);
        br.com.frxcode.frxworld.armas.Lendarias.registrar(modBus);
        br.com.frxcode.frxworld.aventura.Vegeta.registrar(modBus);
        br.com.frxcode.frxworld.aventura.Tita.registrar(modBus);
        br.com.frxcode.frxworld.criaturas.Pokebola.registrar(modBus);
        br.com.frxcode.frxworld.aventura.Esferas.registrar(modBus);
        br.com.frxcode.frxworld.aventura.DeathNote.registrar(modBus);
        br.com.frxcode.frxworld.reinos.MenuDesencanto.registrar(modBus);
        br.com.frxcode.frxworld.reinos.MenuCostura.registrar(modBus);
        br.com.frxcode.frxworld.eventos.ItensNatal.registrar(modBus);
        br.com.frxcode.frxworld.dungeon.EstruturaGrande.registrar(modBus);
        br.com.frxcode.frxworld.reinos.Reinos.registrar(modBus);
        br.com.frxcode.frxworld.reinos.Racas.registrar(modBus);

        MinecraftForge.EVENT_BUS.register(new ControleAcesso());
        MinecraftForge.EVENT_BUS.register(new EventosProgressao());
        MinecraftForge.EVENT_BUS.register(new EventosMorte());
        MinecraftForge.EVENT_BUS.register(new Tavernas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.AreasProtegidas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.Limpeza());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.Reequipar());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Efeitos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.dungeon.Dungeons());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.dungeon.Externos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.EventosEstrada());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.Encontros());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Enigma());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Conjuntos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Habilidades());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.MarcaInvocacao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Especiais());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.Craque());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.Noel());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.Ladrao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Temporadas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Tesouros());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Procurados());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Historia());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Torre());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Ceu());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Runas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Chefes());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Fenda());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Inferno());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.DragaoFim());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Elitros());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.Pesca());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.racas.Saiyajin());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Karin());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Esferas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.DeathNote());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.SalaTempo());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Vegeta());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Tita());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Corridas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.X1());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.X1Diag());   // diagnóstico temporário do X1 sem dano
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.ConjuntosLendarios());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.AjustesAdmin());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.Trancas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Ping());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.armas.Codex());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.armas.NivelUso());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Criaturas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.missoes.Contratos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Colecao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.Terrenos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.Salvamento());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Escravos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Costureira());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.acesso.Personagens());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.AneisUnicos());
        br.com.frxcode.frxworld.protecao.AneisUnicos.ligar();
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.clas.Clas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.moedas.Mercado());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Sagrado());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Cancoes());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Marcial());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Bushido());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Formas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.musica.Discos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.ClassesEquip());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Reinos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.NpcsReino());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Oficios());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Populacao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Masmorras());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.ChefesMasmorra());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.ChefesCrepusculo());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Bencao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.criaturas.Pokebola());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.acesso.ComandosArquivo());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.acesso.PreGeracao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.aventura.Expedicao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.armas.LaminasSamurai());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.VooAnel());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.EscudoPaladino());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.protecao.PontosMapa());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.EntradasMasmorra());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Portao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Casas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.CicloDia());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.AreasNivel());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.moedas.Moedas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.moedas.TrocasMoeda());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.moedas.Banco());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Zonas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Talentos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.grupos.Grupos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.ChefeMundial());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Reputacao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Hostilidade());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.VidaNpc());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Invasoes());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Campanhas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Conquistas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.armas.Armas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.FlorestaSombria());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.BausReino());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Decoracoes());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.WaystonesReino());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.acesso.StatusPainel());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.acesso.DetalheJogador());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.npc.SkinsNpc());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.acesso.ListaEntidades());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Sombras());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Despertar());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.racas.RacasJogador());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Ranks());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Marcos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Guilda());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Unica());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Nomeacao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Suprema());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Bestiario());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.missoes.Urgentes());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Combate());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.racas.Evolucao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.acesso.Abertura());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Portais());
        LOG.info("FRX-WORLD Core carregado.");
    }

    private void setup(FMLCommonSetupEvent evento) {
        evento.enqueueWork(br.com.frxcode.frxworld.progressao.TetoAtributos::aplicar);   // armadura até 100 (antes 30)
        evento.enqueueWork(Rede::registrar);
    }
}
