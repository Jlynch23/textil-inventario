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
        // Auditoria (concurrencia): UN SOLO worker. procesarPendientesAsync() lee
        // los PENDIENTE con findTop500(...) y NO los reclama de forma atomica, asi
        // que dos subidas de ZIP casi simultaneas con 2+ hilos leian las MISMAS
        // filas y las procesaban dos veces -> doble Recepcion/stock/kardex. Este
        // pool es exclusivo de ese metodo, asi que serializarlo (1 hilo, resto en
        // cola) elimina la carrera: la 2da subida corre cuando la 1ra ya dejo los
        // docs en PROCESADO/DUPLICADO y el dedup los saltea.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("archivo-historico-");
        executor.initialize();
        return executor;
    }
}
