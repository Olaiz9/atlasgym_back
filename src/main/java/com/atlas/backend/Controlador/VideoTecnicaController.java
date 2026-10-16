package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.VideoTecnicaDto;
import com.atlas.backend.Modelo.GrupoMuscular;
import com.atlas.backend.Servicio.VideoTecnicaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/videoteca")
public class VideoTecnicaController {

    private final VideoTecnicaService videoTecnicaService;

    public VideoTecnicaController(VideoTecnicaService videoTecnicaService) {
        this.videoTecnicaService = videoTecnicaService;
    }

    @GetMapping
    public ResponseEntity<List<VideoTecnicaDto>> listarTodos() {
        return ResponseEntity.ok(videoTecnicaService.listarTodos());
    }

    @GetMapping("/grupo/{grupo}")
    public ResponseEntity<List<VideoTecnicaDto>> listarPorGrupo(@PathVariable GrupoMuscular grupo) {
        return ResponseEntity.ok(videoTecnicaService.listarPorGrupo(grupo));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<VideoTecnicaDto>> buscarPorTitulo(@RequestParam String query) {
        return ResponseEntity.ok(videoTecnicaService.buscarPorTitulo(query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VideoTecnicaDto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(videoTecnicaService.buscarPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VideoTecnicaDto> crear(@RequestBody VideoTecnicaDto dto) {
        VideoTecnicaDto creado = videoTecnicaService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        videoTecnicaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
