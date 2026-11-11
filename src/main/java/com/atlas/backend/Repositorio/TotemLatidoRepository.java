package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.TotemLatido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TotemLatidoRepository extends JpaRepository<TotemLatido, Long> {
}
