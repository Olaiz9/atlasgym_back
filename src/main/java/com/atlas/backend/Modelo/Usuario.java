package com.atlas.backend.Modelo;

import java.time.LocalDate;

public class Usuario {

    private Long id;
    private String email;
    private String password;
    private Rol rol;
    private boolean activo;
    private LocalDate fechaCreacion;

    public Usuario() {
    }

    public Usuario(Long id, String email, String password, Rol rol, boolean activo, LocalDate fechaCreacion) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.rol = rol;
        this.activo = activo;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDate getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDate fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
