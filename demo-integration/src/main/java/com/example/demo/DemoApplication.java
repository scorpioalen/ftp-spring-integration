package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ImageBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.file.Files;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.ftp.session.DefaultFtpSessionFactory;
import org.springframework.integration.transformer.GenericTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.springframework.util.ReflectionUtils.rethrowRuntimeException;

@SpringBootApplication
@EnableAutoConfiguration
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    DefaultFtpSessionFactory defaultFtpSessionFactory(@Value("${ftp.username}") String username, @Value("${ftp" +
            ".password}") String password, @Value("${ftp.host}") String host, @Value("${ftp.port}") int port) {
        DefaultFtpSessionFactory dfs = new DefaultFtpSessionFactory();
        dfs.setHost(host);
        dfs.setUsername(username);
        dfs.setPassword(password);
        dfs.setPort(port);
        dfs.setBufferSize(100000000);
        return dfs;
    }

    @Bean
    IntegrationFlow files(@Value("${ftp.in.dir}") File in, Environment env, DefaultFtpSessionFactory dfs) {
        GenericTransformer<File, Message<String>> fileMessageGenericTransformer = (File source) -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 PrintStream printStream = new PrintStream(baos)) {
                ImageBanner imgb = new ImageBanner(new FileSystemResource(source));
                imgb.printBanner(env, getClass(), printStream);
                return MessageBuilder.withPayload(new String(baos.toByteArray())).setHeader(FileHeaders.FILENAME,
                        source
                        .getAbsoluteFile()
                        .getName()).build();

            } catch (IOException e) {
                rethrowRuntimeException(e);
            }
            return null;
        };
        return IntegrationFlows.from(Files.inboundAdapter(in).autoCreateDirectory(true).preventDuplicates(true)
                .patternFilter("*.jpg"), poller -> poller.poller(pm -> pm.fixedDelay(1000)))
                .transform(File.class, fileMessageGenericTransformer).handleWithAdapter(adapters ->
                        adapters.ftp(dfs).remoteDirectory("${ftp.upload.dir}").fileNameGenerator(message -> {
                                    Object o = message.getHeaders().get(FileHeaders.FILENAME);
                                    String fileName = (String) (o);
                                    return fileName.split("//.")[0] + ".txt";
                                }
                        )
                ).get();
    }

}
