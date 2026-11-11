package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.LoginRequest;
import com.atlas.backend.Dto.LoginResponse;
import com.atlas.backend.Servicio.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest != null && servletRequest.getRemoteAddr() != null
                ? servletRequest.getRemoteAddr()
                : "local";
        LoginResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/perfil")
    public ResponseEntity<LoginResponse> obtenerPerfil(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        LoginResponse response = authService.obtenerPerfil(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/perfil")
    public ResponseEntity<LoginResponse> actualizarPerfil(
            Authentication authentication,
            @RequestBody Map<String, String> cambios
    ) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        LoginResponse response = authService.actualizarPerfil(authentication.getName(), cambios);
        return ResponseEntity.ok(response);
    }
}
