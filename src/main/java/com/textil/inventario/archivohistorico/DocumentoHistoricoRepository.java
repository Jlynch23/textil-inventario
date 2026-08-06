package com.textil.inventario.archivohistorico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentoHistoricoRepository extends JpaRepository<DocumentoHistorico, Long> {

    List<DocumentoHistorico> findTop500ByEstadoProcesoOrderByIdAsc(DocumentoHistorico.EstadoProceso estado);

    long countByEstadoProceso(DocumentoHistorico.EstadoProceso estado);

    @Query("""
        SELECT d FROM DocumentoHistorico d
        WHERE (:empresaId IS NULL OR d.empresa.id = :empresaId)
          AND (:tipoDocumento IS NULL OR d.tipoDocumento = :tipoDocumento)
          AND (:estadoProceso IS NULL OR d.estadoProceso = :estadoProceso)
          AND (:anio IS NULL OR YEAR(d.fechaDocumento) = :anio)
          AND (:busqueda IS NULL OR :busqueda = ''
               OR LOWER(d.numeroGuia) LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(d.numeroFactura) LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(d.razonSocialDetectada) LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(d.nombreOriginal) LIKE LOWER(CONCAT('%', :busqueda, '%')))
        ORDER BY d.fechaDocumento DESC NULLS LAST, d.id DESC
        """)
    List<DocumentoHistorico> buscarConFiltros(@Param("empresaId") Long empresaId,
                                               @Param("tipoDocumento") DocumentoHistorico.TipoDocumentoHistorico tipoDocumento,
                                               @Param("estadoProceso") DocumentoHistorico.EstadoProceso estadoProceso,
                                               @Param("anio") Integer anio,
                                               @Param("busqueda") String busqueda);

    // M6: lookup indexado de duplicados (reemplaza el escaneo O(n^2) de
    // findByTipoDocumento + filtro en memoria). Excluye el propio doc y solo
    // mira los ya PROCESADOS, igual que buscarYaImportado.
    java.util.Optional<DocumentoHistorico> findFirstByTipoDocumentoAndNumeroNormalizadoAndEstadoProcesoAndIdNot(
            DocumentoHistorico.TipoDocumentoHistorico tipoDocumento,
            String numeroNormalizado,
            DocumentoHistorico.EstadoProceso estadoProceso,
            Long id);

    // Vinculacion FACTURA <-> GUIAS. La comparacion exacta por numeroGuia no
    // sirve porque la factura suele mencionar el numero SIN ceros a la izquierda
    // ("TG01-21376") mientras la guia lo guarda CON ceros ("TG01-00021376", tal
    // como lo lee la IA de la guia misma) -- por eso se compara la forma
    // normalizada, que es justamente lo que V40 dejo persistido e indexado en
    // numero_normalizado. Devuelve lista y no Optional porque un mismo numero
    // puede tener mas de una fila (los reimportados quedan como DUPLICADO).
    List<DocumentoHistorico> findByTipoDocumentoAndNumeroNormalizado(
            DocumentoHistorico.TipoDocumentoHistorico tipoDocumento,
            String numeroNormalizado);

    // "¿Que factura menciona esta guia?" — mira DENTRO de guias_referenciadas,
    // que es texto separado por comas con los numeros SIN normalizar, asi que el
    // indice de numero_normalizado no aplica y la comparacion sigue en Java.
    // Lo que si se puede es no traer las filas que no pueden coincidir: una
    // factura sin numero o que no referencia ninguna guia nunca es respuesta.
    // (Un fix completo pide una tabla de referencias factura->guia en vez de un
    // campo de texto; queda para cuando se toque el modelo.)
    List<DocumentoHistorico> findByTipoDocumentoAndGuiasReferenciadasIsNotNullAndNumeroFacturaIsNotNull(
            DocumentoHistorico.TipoDocumentoHistorico tipoDocumento);

    // Documentos que quedaron sin empresa asignada (deteccion fallida en su
    // momento). Se usan para re-intentar la deteccion sin re-leer el PDF.
    List<DocumentoHistorico> findByEmpresaIsNull();
}
