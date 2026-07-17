package com.textil.inventario.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * ARQ (auditoria 17-jul-2026): sin este bean, @Async cae al default de Spring
 * (SimpleAsyncTaskExecutor), que crea un hilo NUEVO sin limite por cada
 * invocacion. Si se suben varios ZIPs grandes de Archivo Historico casi al
 * mismo tiempo, eso podria crear demasiados hilos concurrentes. Un pool
 * acotado evita ese escenario: las tareas de mas se encolan en vez de crear
 * hilos sin control.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "archivoHistoricoTaskExecutor")
    public Executor archivoHistoricoTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("archivo-historico-");
        executor.initialize();
        return executor;
    }
}
