package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecepcionDocumentoRepository extends JpaRepository<RecepcionDocumento, Long> {
    List<RecepcionDocumento> findByRecepcionId(Long recepcionId);
}
