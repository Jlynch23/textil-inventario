package com.textil.inventario.catalogo;
import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@Table(name = "colores")
public class Color extends BaseEntity {
    @Column(name = "nombre_oficial", nullable = false, unique = true, length = 100)
    private String nombreOficial;
    @Column(name = "codigo_fast_dye", length = 20)
    private String codigoFastDye;
    // Apodo/alias corto (ej. "PPT") que la gente usa en la practica en vez
    // del nombre oficial largo que trae la guia de FAST DYE. Opcional: si
    // no se define, se sigue mostrando el nombre oficial normalmente.
    @Column(length = 100)
    private String apodo;
    @Column(nullable = false)
    private Boolean activo = true;

    // Nombre a mostrar en TODA la interfaz (listas, tablas, selects,
    // reportes): el apodo si existe, si no el nombre oficial. El nombre
    // oficial NUNCA se pierde ni se reemplaza en la base de datos -- sigue
    // siendo el que se usa para el matching contra el texto de las guias
    // (OCR), solo cambia lo que ve el usuario.
    public String getNombreMostrar() {
        return (apodo != null && !apodo.isBlank()) ? apodo : nombreOficial;
    }
}
