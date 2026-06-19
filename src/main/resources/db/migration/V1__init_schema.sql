-- =====================================================
-- TEXTIL INVENTARIO - Esquema V1
-- =====================================================

-- SEGURIDAD
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    rol_id BIGINT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuarios_rol FOREIGN KEY (rol_id) REFERENCES roles(id)
);

-- CATÁLOGO
CREATE TABLE empresas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    ruc VARCHAR(11) NOT NULL UNIQUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ubicaciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    tipo ENUM('ALMACEN','TIENDA') NOT NULL,
    es_principal BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tipos_tela (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE titulos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    valor VARCHAR(20) NOT NULL UNIQUE,
    descripcion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE colores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_oficial VARCHAR(100) NOT NULL UNIQUE,
    codigo_fast_dye VARCHAR(20),
    familia VARCHAR(50),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE articulos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_interno VARCHAR(50) NOT NULL UNIQUE,
    tipo_tela_id BIGINT NOT NULL,
    titulo_id BIGINT NOT NULL,
    color_id BIGINT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_articulos_tipo_tela FOREIGN KEY (tipo_tela_id) REFERENCES tipos_tela(id),
    CONSTRAINT fk_articulos_titulo FOREIGN KEY (titulo_id) REFERENCES titulos(id),
    CONSTRAINT fk_articulos_color FOREIGN KEY (color_id) REFERENCES colores(id),
    CONSTRAINT uq_articulo UNIQUE (tipo_tela_id, titulo_id, color_id)
);

-- DOCUMENTOS
CREATE TABLE documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo ENUM('GUIA','FACTURA') NOT NULL,
    numero_doc VARCHAR(50) NOT NULL,
    empresa_id BIGINT NOT NULL,
    fecha_emision DATE,
    ruta_archivo VARCHAR(500),
    ocr_raw TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    CONSTRAINT fk_documentos_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id)
);

-- RECEPCIONES
CREATE TABLE recepciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    numero_guia VARCHAR(50) NOT NULL,
    fecha_guia DATE NOT NULL,
    fecha_recepcion DATE NOT NULL,
    estado ENUM('PENDIENTE','CONFIRMADA','CON_DIFERENCIAS') NOT NULL DEFAULT 'PENDIENTE',
    observaciones TEXT,
    usuario_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_recepciones_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT fk_recepciones_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE recepcion_detalles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recepcion_id BIGINT NOT NULL,
    articulo_id BIGINT NOT NULL,
    programa_tenido VARCHAR(20),
    rollos_guia INT NOT NULL,
    rollos_recibidos INT,
    peso_bruto_kg DECIMAL(10,2),
    diferencia_rollos INT GENERATED ALWAYS AS (rollos_recibidos - rollos_guia) STORED,
    observacion TEXT,
    CONSTRAINT fk_det_recepcion FOREIGN KEY (recepcion_id) REFERENCES recepciones(id),
    CONSTRAINT fk_det_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id)
);

-- TRANSFERENCIAS
CREATE TABLE transferencias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    ubicacion_origen_id BIGINT NOT NULL,
    ubicacion_destino_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    estado ENUM('PENDIENTE','CONFIRMADA') NOT NULL DEFAULT 'PENDIENTE',
    observaciones TEXT,
    usuario_solicitante_id BIGINT NOT NULL,
    usuario_confirmador_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_trans_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT fk_trans_origen FOREIGN KEY (ubicacion_origen_id) REFERENCES ubicaciones(id),
    CONSTRAINT fk_trans_destino FOREIGN KEY (ubicacion_destino_id) REFERENCES ubicaciones(id),
    CONSTRAINT fk_trans_solicitante FOREIGN KEY (usuario_solicitante_id) REFERENCES usuarios(id)
);

CREATE TABLE transferencia_detalles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transferencia_id BIGINT NOT NULL,
    articulo_id BIGINT NOT NULL,
    rollos INT NOT NULL,
    peso_kg DECIMAL(10,2),
    CONSTRAINT fk_tdet_transferencia FOREIGN KEY (transferencia_id) REFERENCES transferencias(id),
    CONSTRAINT fk_tdet_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id)
);

-- INVENTARIO
CREATE TABLE stock_actual (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    articulo_id BIGINT NOT NULL,
    ubicacion_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    rollos INT NOT NULL DEFAULT 0,
    peso_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id),
    CONSTRAINT fk_stock_ubicacion FOREIGN KEY (ubicacion_id) REFERENCES ubicaciones(id),
    CONSTRAINT fk_stock_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT uq_stock UNIQUE (articulo_id, ubicacion_id, empresa_id)
);

CREATE TABLE kardex_movimientos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tipo_movimiento ENUM('INGRESO','TRANSFERENCIA_OUT','TRANSFERENCIA_IN','AJUSTE') NOT NULL,
    articulo_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    ubicacion_origen_id BIGINT,
    ubicacion_destino_id BIGINT,
    rollos INT NOT NULL,
    peso_kg DECIMAL(10,2),
    recepcion_detalle_id BIGINT,
    transferencia_id BIGINT,
    usuario_id BIGINT NOT NULL,
    observaciones TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kardex_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id),
    CONSTRAINT fk_kardex_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT fk_kardex_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- ÍNDICES
CREATE INDEX idx_kardex_articulo_fecha ON kardex_movimientos(articulo_id, fecha);
CREATE INDEX idx_kardex_empresa ON kardex_movimientos(empresa_id);
CREATE INDEX idx_stock_ubicacion ON stock_actual(ubicacion_id);
CREATE INDEX idx_recepciones_empresa ON recepciones(empresa_id, fecha_recepcion);
