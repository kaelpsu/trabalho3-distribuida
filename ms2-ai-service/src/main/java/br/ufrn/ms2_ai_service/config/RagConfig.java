package br.ufrn.ms2_ai_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Bean
    public VectorStore vectorStore(
            EmbeddingModel embeddingModel,
            @Value("classpath:politica_smartshop.txt") Resource documentoRag) {

        log.info("Inicializando VectorStore em memoria (RAG) com o arquivo politica_smartshop.txt...");
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        TextReader textReader = new TextReader(documentoRag);

        List<Document> documentosLidos = textReader.get();

        TokenTextSplitter splitter = TokenTextSplitter.builder().build();

        List<Document> documentos = splitter.apply(documentosLidos);

        vectorStore.add(documentos);

        log.info("RAG carregado com sucesso! Total de documentos vetorizados: {}", documentos.size());
        return vectorStore;
    }
}