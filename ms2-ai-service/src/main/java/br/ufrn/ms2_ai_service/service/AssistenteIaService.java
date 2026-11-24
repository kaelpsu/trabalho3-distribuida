package br.ufrn.ms2_ai_service.service;

import br.ufrn.ms2_ai_service.config.GeminiProperties;
import br.ufrn.ms2_ai_service.controller.AtendimentoGraphqlController.RespostaAtendimento;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RefreshScope
public class AssistenteIaService {

    private static final Logger log = LoggerFactory.getLogger(AssistenteIaService.class);
    private final GeminiProperties geminiProperties;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;

    @Value("${smartshop.ia.mock-mode:false}")
    private boolean mockMode;

    @Value("classpath:prompt/systemTemplate.st")
    private Resource templateSystem;

    @Value("classpath:prompt/userTemplate.st")
    private Resource templateUser;

    public AssistenteIaService( 
                ChatClient.Builder chatClientBuilder,
                ChatMemory chatMemory,
                VectorStore vectorStore,
                GeminiProperties geminiProperties) {

        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.geminiProperties = geminiProperties;
    }

    @RateLimiter(name = "ms2AiServerRateLimiter")
    public RespostaAtendimento gerarRespostaAtendimentoComRateLimiter(
            String clienteId, String pedidoId, String pergunta, String protocolo) {
        return gerarRespostaAtendimento(clienteId, pedidoId, pergunta, protocolo);
    }

    // O Chaos Monkey (watcher.service=true) intercepta este método automaticamente
    public RespostaAtendimento gerarRespostaAtendimento(
            String clienteId, String pedidoId, String pergunta, String protocolo) {

        if (mockMode) {
            log.info("[MOCK-MODE ATIVO] Simulando resposta da IA sem chamar API do Gemini (Cliente: {}, Pedido: {}, Protocolo: {})",
                    clienteId, pedidoId, protocolo);

            String respostaSimulada = String.format(
                    "[MODO SIMULADO] Atendimento para o cliente %s sobre o pedido %s (Protocolo: %s). " +
                    "Pergunta recebida: \"%s\". Conforme a politica SmartShop, trocas VIP possuem 45 dias e reembolsos via PIX ocorrem em ate 2 horas.",
                    clienteId, pedidoId, protocolo, pergunta
            );

            return new RespostaAtendimento(
                    protocolo,
                    clienteId,
                    pedidoId,
                    respostaSimulada,
                    "GEMINI-MOCK-MODE",
                    "MOCK****************MODE"
            );
        }

        log.info("Consultando Gemini via Spring AI (Cliente: {}, Pedido: {})", clienteId, pedidoId);

        String respostaIa = chatClient.prompt()
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore).build()
                )
                // Mantém o histórico de conversa separado por clienteId (Gerenciando Múltiplas Conversas)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, clienteId))
                .system(systemSpec -> systemSpec
                        .text(templateSystem)
                        .param("Loja", "SmartShop")
                )
                .user(userSpec -> userSpec
                        .text(templateUser)
                        .param("Loja", "SmartShop")
                        .param("ClienteId", clienteId)
                        .param("PedidoId", pedidoId)
                        .param("Protocolo", protocolo)
                        .param("Pergunta", pergunta))
                .call()
                .content();

        String chave = geminiProperties.getApiKey();
        String chaveMascarada = (chave != null && chave.length() > 8)
                ? chave.substring(0, 4) + "****************" + chave.substring(chave.length() - 4)
                : "SEGREDO_NAO_ENCONTRADO";

        String modelo = (geminiProperties.getModel() != null)
                ? geminiProperties.getModel()
                : "MODELO_NAO_ENCONTRADO_NO_VAULT";

        log.info("Resposta gerada pelo Gemini - modelo={}, chaveVault={}, clienteId={}, protocolo={}",
                modelo, chaveMascarada, clienteId, protocolo);

        return new RespostaAtendimento(
                protocolo,
                clienteId,
                pedidoId,
                respostaIa,
                modelo,
                chaveMascarada
        );
    }
}