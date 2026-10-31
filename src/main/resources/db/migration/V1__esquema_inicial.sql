-- ============================================================================
-- ATLAS Gym - Esquema Inicial Versionado (Flyway V1)
-- Compatible con MySQL 8.0+ y validación estricta Hibernate 6 (ddl-auto=validate)
-- ============================================================================

CREATE TABLE usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    activo BOOLEAN NOT NULL,
    fecha_creacion DATE,
    foto_url VARCHAR(500),
    celular VARCHAR(30),
    CONSTRAINT uk_usuarios_email UNIQUE (email)
);

CREATE TABLE planes (
    id_plan BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    dia_por_semana VARCHAR(50),
    precio DECIMAL(10, 2) NOT NULL,
    activo BOOLEAN NOT NULL
);

CREATE TABLE rutinas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    objetivo VARCHAR(50),
    es_generica BOOLEAN NOT NULL,
    fecha_creacion DATE
);

CREATE TABLE videos_tecnica (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT,
    grupo_muscular VARCHAR(50),
    nivel VARCHAR(50),
    duracion VARCHAR(50),
    url_video VARCHAR(500),
    thumbnail_url VARCHAR(500),
    gif_url VARCHAR(500),
    formato VARCHAR(20),
    equipo VARCHAR(100),
    usuario_id BIGINT,
    CONSTRAINT fk_videos_tecnica_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);

CREATE TABLE video_consejos (
    video_id BIGINT NOT NULL,
    consejo VARCHAR(255),
    CONSTRAINT fk_video_consejos_video FOREIGN KEY (video_id) REFERENCES videos_tecnica (id) ON DELETE CASCADE
);

CREATE TABLE dias_rutina (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    orden INT NOT NULL,
    rutina_id BIGINT NOT NULL,
    CONSTRAINT fk_dias_rutina_rutina FOREIGN KEY (rutina_id) REFERENCES rutinas (id) ON DELETE CASCADE
);

CREATE TABLE dias_rutina_videos (
    dia_rutina_id BIGINT NOT NULL,
    video_tecnica_id BIGINT NOT NULL,
    CONSTRAINT fk_drv_dia_rutina FOREIGN KEY (dia_rutina_id) REFERENCES dias_rutina (id) ON DELETE CASCADE,
    CONSTRAINT fk_drv_video_tecnica FOREIGN KEY (video_tecnica_id) REFERENCES videos_tecnica (id) ON DELETE CASCADE
);

CREATE TABLE ejercicios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_ejercicio VARCHAR(150) NOT NULL,
    serie INT NOT NULL,
    repeticiones VARCHAR(50),
    descanso_segundos VARCHAR(50),
    notas TEXT,
    orden INT NOT NULL,
    tipo_serie VARCHAR(30),
    dia_rutina_id BIGINT NOT NULL,
    video_tecnica_id BIGINT,
    CONSTRAINT fk_ejercicios_dia_rutina FOREIGN KEY (dia_rutina_id) REFERENCES dias_rutina (id) ON DELETE CASCADE,
    CONSTRAINT fk_ejercicios_video_tecnica FOREIGN KEY (video_tecnica_id) REFERENCES videos_tecnica (id) ON DELETE SET NULL
);

CREATE TABLE alumnos (
    id_alumno BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100),
    dni VARCHAR(20),
    email VARCHAR(100),
    celular VARCHAR(30),
    fecha_nacimiento DATE,
    fecha_alta DATE,
    activo BOOLEAN NOT NULL,
    ultima_asistencia DATE,
    foto_url VARCHAR(500),
    usuario_id BIGINT,
    plan_id BIGINT,
    rutina_id BIGINT,
    CONSTRAINT uk_alumnos_dni UNIQUE (dni),
    CONSTRAINT uk_alumnos_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_alumnos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_alumnos_plan FOREIGN KEY (plan_id) REFERENCES planes (id_plan) ON DELETE SET NULL,
    CONSTRAINT fk_alumnos_rutina FOREIGN KEY (rutina_id) REFERENCES rutinas (id) ON DELETE SET NULL
);

CREATE TABLE pagos (
    id_pago BIGINT AUTO_INCREMENT PRIMARY KEY,
    monto DECIMAL(10, 2) NOT NULL,
    fecha DATE NOT NULL,
    mes_correspondiente VARCHAR(50),
    periodo_mes VARCHAR(7),
    cantidad_meses INT DEFAULT 1,
    alumno_nombre_historico VARCHAR(150),
    metodo_pago VARCHAR(30) NOT NULL,
    estado_pago VARCHAR(30) NOT NULL,
    comprobante VARCHAR(255),
    plan_id BIGINT,
    alumno_id BIGINT,
    CONSTRAINT fk_pagos_plan FOREIGN KEY (plan_id) REFERENCES planes (id_plan) ON DELETE SET NULL,
    CONSTRAINT fk_pagos_alumno FOREIGN KEY (alumno_id) REFERENCES alumnos (id_alumno) ON DELETE SET NULL
);

CREATE INDEX idx_pagos_alumno_fecha ON pagos (alumno_id, fecha);

CREATE TABLE asistencias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha DATE NOT NULL,
    hora TIME,
    alumno_id BIGINT NOT NULL,
    metodo VARCHAR(30),
    estado_cuenta VARCHAR(30),
    CONSTRAINT fk_asistencias_alumno FOREIGN KEY (alumno_id) REFERENCES alumnos (id_alumno) ON DELETE CASCADE
);

CREATE INDEX idx_asistencias_alumno_fecha ON asistencias (alumno_id, fecha);

CREATE TABLE avisos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    mensaje TEXT NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    fecha DATE NOT NULL,
    para_todos BOOLEAN NOT NULL,
    alumno_id BIGINT,
    CONSTRAINT fk_avisos_alumno FOREIGN KEY (alumno_id) REFERENCES alumnos (id_alumno) ON DELETE CASCADE
);

CREATE TABLE aviso_leidos (
    aviso_id BIGINT NOT NULL,
    usuario_id BIGINT,
    CONSTRAINT fk_aviso_leidos_aviso FOREIGN KEY (aviso_id) REFERENCES avisos (id) ON DELETE CASCADE
);

CREATE TABLE sesiones_entrenamiento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alumno_id BIGINT NOT NULL,
    rutina_id BIGINT,
    dia_rutina_id BIGINT,
    fecha DATE NOT NULL,
    duracion_minutos INT,
    notas TEXT,
    CONSTRAINT fk_sesiones_alumno FOREIGN KEY (alumno_id) REFERENCES alumnos (id_alumno) ON DELETE CASCADE,
    CONSTRAINT fk_sesiones_rutina FOREIGN KEY (rutina_id) REFERENCES rutinas (id) ON DELETE SET NULL,
    CONSTRAINT fk_sesiones_dia_rutina FOREIGN KEY (dia_rutina_id) REFERENCES dias_rutina (id) ON DELETE SET NULL
);

CREATE INDEX idx_sesiones_alumno_fecha ON sesiones_entrenamiento (alumno_id, fecha);

CREATE TABLE sesiones_ejercicios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sesion_id BIGINT NOT NULL,
    ejercicio_id BIGINT,
    nombre_ejercicio_historico VARCHAR(150),
    CONSTRAINT fk_sesiones_ej_sesion FOREIGN KEY (sesion_id) REFERENCES sesiones_entrenamiento (id) ON DELETE CASCADE,
    CONSTRAINT fk_sesiones_ej_ejercicio FOREIGN KEY (ejercicio_id) REFERENCES ejercicios (id) ON DELETE SET NULL
);

CREATE TABLE registros_series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sesion_ejercicio_id BIGINT NOT NULL,
    serie_numero INT NOT NULL,
    kg DOUBLE PRECISION,
    reps INT NOT NULL,
    completada BOOLEAN NOT NULL,
    CONSTRAINT fk_registros_series_sesion_ej FOREIGN KEY (sesion_ejercicio_id) REFERENCES sesiones_ejercicios (id) ON DELETE CASCADE
);
