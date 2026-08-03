package com.textil.inventario.recepciones;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FILE-02: la resolución de rutas de documentos para descarga debe quedar DENTRO
 * de documentos.ruta-base; una ruta con traversal fuera de la base se rechaza.
 */
class DocumentoStorageServiceTest {

    private DocumentoStorageService conBase(String base) {
        DocumentoStorageService s = new DocumentoStorageService();
        ReflectionTestUtils.setField(s, "rutaBase", base);
        return s;
    }

    @Test
    void resolverRutaSegura_dentroDeBase_ok() {
        DocumentoStorageService s = conBase("documentos");
        Path r = s.resolverRutaSegura("documentos/Guias/EmpresaX/2026-01-01_abcd1234.pdf");
        assertThat(r.toString()).contains("Guias").endsWith(".pdf");
    }

    @Test
    void resolverRutaSegura_traversalFueraDeBase_lanza() {
        DocumentoStorageService s = conBase("documentos");
        assertThatThrownBy(() -> s.resolverRutaSegura("documentos/../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fuera del almacenamiento");
    }

    @Test
    void resolverRutaSegura_vacia_lanza() {
        DocumentoStorageService s = conBase("documentos");
        assertThatThrownBy(() -> s.resolverRutaSegura("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
