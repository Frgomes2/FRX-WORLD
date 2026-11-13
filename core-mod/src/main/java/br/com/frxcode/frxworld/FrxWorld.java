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
        br.com.frxcode.frxworld.dungeon.EstruturaGrande.registrar(modBus);
        br.com.frxcode.frxworld.reinos.Reinos.registrar(modBus);
        br.com.frxcode.frxworld.reinos.Racas.registrar(modBus);

        MinecraftForge.EVENT_BUS.register(new ControleAcesso());
        MinecraftForge.EVENT_BUS.register(new EventosProgressao());
        MinecraftForge.EVENT_BUS.register(new EventosMorte());
        MinecraftForge.EVENT_BUS.register(new Tavernas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.dungeon.Dungeons());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.dungeon.Externos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.EventosEstrada());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Habilidades());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Reinos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.NpcsReino());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Oficios());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Populacao());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Masmorras());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.moedas.Moedas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.moedas.TrocasMoeda());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.moedas.Banco());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.progressao.Zonas());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.habilidades.Talentos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.grupos.Grupos());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.eventos.ChefeMundial());
        MinecraftForge.EVENT_BUS.register(new br.com.frxcode.frxworld.reinos.Reputacao());
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
        evento.enqueueWork(Rede::registrar);
    }
}
