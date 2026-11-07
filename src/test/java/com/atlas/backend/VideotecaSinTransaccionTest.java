package com.atlas.backend;

import com.atlas.backend.Modelo.FormatoVideo;
import com.atlas.backend.Modelo.GrupoMuscular;
import com.atlas.backend.Modelo.VideoTecnica;
import com.atlas.backend.Repositorio.VideoTecnicaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin @Transactional a proposito: como en produccion (open-in-view=false), el JSON se escribe
 * cuando la transaccion del servicio ya cerro. VideotecaTest es @Transactional y por eso no
 * detectaba que GET /api/videoteca respondia 500 (LazyInitializationException en consejosClave).
 */
@SpringBootTest
@AutoConfigureMockMvc
class VideotecaSinTransaccionTest {

    @Autowired private MockMvc mvc;
    @Autowired private VideoTecnicaRepository videoTecnicaRepository;

    private final List<Long> creados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        videoTecnicaRepository.deleteAllById(creados);
    }

    private Long guardarVideo(String titulo) {
        VideoTecnica v = new VideoTecnica(null, titulo, "Tecnica de prueba", GrupoMuscular.PECHO,
                "Intermedio", "0:45", "https://cdn.atlasgym.com/v.mp4", null, null,
                FormatoVideo.VIDEO, "Barra", new ArrayList<>(List.of("Retraer escapulas", "Apoyar bien los pies")), null);
        Long id = videoTecnicaRepository.save(v).getId();
        creados.add(id);
        return id;
    }

    @Test
    void listarVideotecaIncluyeConsejosSinTransaccionAbierta() throws Exception {
        guardarVideo("Press banca sin transaccion");

        mvc.perform(get("/api/videoteca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == 'Press banca sin transaccion')].consejosClave[*]",
                        hasItem("Retraer escapulas")));
    }

    @Test
    void verUnVideoIncluyeConsejosSinTransaccionAbierta() throws Exception {
        Long id = guardarVideo("Remo sin transaccion");

        mvc.perform(get("/api/videoteca/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consejosClave", hasItem("Apoyar bien los pies")));
    }
}
