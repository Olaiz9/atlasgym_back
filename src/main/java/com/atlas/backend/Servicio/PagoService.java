package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.ArqueoCajaDto;
import com.atlas.backend.Dto.ArqueoOperadorDto;
import com.atlas.backend.Dto.PagoCompletarSaldoRequestDto;
import com.atlas.backend.Dto.PagoRequestDto;
import com.atlas.backend.Dto.PagoResponseDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Asistencia;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.EstadoPago;
import com.atlas.backend.Modelo.MetodoPago;
import com.atlas.backend.Modelo.Pago;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AsistenciaRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;
    private final AlumnoRepository alumnoRepository;
    private final PlanRepository planRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final AlumnoService alumnoService;

    public PagoService(PagoRepository pagoRepository,
                       AlumnoRepository alumnoRepository,
                       PlanRepository planRepository,
                       AsistenciaRepository asistenciaRepository,
                       AlumnoService alumnoService) {
        this.pagoRepository = pagoRepository;
        this.alumnoRepository = alumnoRepository;
        this.planRepository = planRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.alumnoService = alumnoService;
    }

    @Transactional
    public PagoResponseDto registrarPago(PagoRequestDto dto) {
        return registrarPago(dto, "Sistema");
    }

    @Transactional
    public PagoResponseDto registrarPago(PagoRequestDto dto, String operador) {
        if (dto.monto() == null || dto.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El monto de la cuota debe ser mayor a $0.");
        }

        Alumno alumno = alumnoRepository.findById(dto.alumnoId())
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", dto.alumnoId()));

        Plan plan = null;
        if (dto.planId() != null) {
            plan = planRepository.findById(dto.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", dto.planId()));
        } else if (alumno.getPlanAlumno() != null) {
            plan = alumno.getPlanAlumno();
        }

        MetodoPago metodo = parseMetodoPago(dto.metodo());
        EstadoPago estado = dto.estado() != null && !dto.estado().isBlank()
                ? parseEstadoPago(dto.estado())
                : EstadoPago.PAGADO;
        if (estado == EstadoPago.ANULADO) {
            throw new BusinessException("No se puede dar de alta un pago directamente en estado ANULADO.");
        }

        LocalDate fechaPago = dto.fecha() != null ? dto.fecha() : LocalDate.now();
        String periodoMes = (dto.periodoMes() != null && !dto.periodoMes().isBlank())
                ? dto.periodoMes().trim()
                : fechaPago.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        YearMonth ymNuevoInicio = parsearPeriodoMesEstricto(periodoMes);

        int cantidadMeses = (dto.cantidadMeses() != null && dto.cantidadMeses() > 0)
                ? Math.min(dto.cantidadMeses(), 24)
                : 1;
        YearMonth ymNuevoFin = cantidadMeses > 1
                ? ymNuevoInicio.plusMonths(cantidadMeses - 1L)
                : ymNuevoInicio;

        BigDecimal montoAbonado;
        if (dto.montoAbonado() != null) {
            if (dto.montoAbonado().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("El monto abonado no puede ser negativo.");
            }
            if (dto.montoAbonado().compareTo(dto.monto()) > 0) {
                throw new BusinessException("El monto abonado no puede superar el valor total de la cuota.");
            }
            if (dto.montoAbonado().compareTo(BigDecimal.ZERO) > 0 && dto.montoAbonado().compareTo(dto.monto()) < 0) {
                estado = EstadoPago.PARCIAL;
                montoAbonado = dto.montoAbonado();
            } else if (dto.montoAbonado().compareTo(dto.monto()) == 0) {
                estado = EstadoPago.PAGADO;
                montoAbonado = dto.monto();
            } else {
                // montoAbonado == 0
                if (estado == EstadoPago.PAGADO || estado == EstadoPago.PARCIAL) {
                    throw new BusinessException("Para registrar un pago o una seña parcial el importe abonado debe ser mayor a $0.");
                }
                montoAbonado = BigDecimal.ZERO;
            }
        } else {
            if (estado == EstadoPago.PARCIAL) {
                throw new BusinessException("Para registrar una seña o pago parcial debe indicar el monto abonado menor al total de la cuota.");
            }
            montoAbonado = (estado == EstadoPago.PAGADO) ? dto.monto() : BigDecimal.ZERO;
        }

        validarSinSolapamientoDePeriodo(alumno.getIdAlumno(), ymNuevoInicio, ymNuevoFin);

        Pago pago = new Pago();
        pago.setAlumno(alumno);
        pago.setPlanPago(plan);
        pago.setMonto(dto.monto());
        pago.setMontoAbonado(montoAbonado);
        pago.setFecha(fechaPago);
        pago.setPeriodoMes(periodoMes);
        pago.setCantidadMeses(cantidadMeses);
        // Snapshot inmutable: preserva el nombre real del alumno al momento del cobro
        pago.setAlumnoNombreHistorico(alumno.getNombre());
        pago.setMetodoPago(metodo);
        pago.setEstadoPago(estado);
        pago.setComprobante(dto.comprobante());
        pago.setRegistradoPor(operador != null && !operador.isBlank() ? operador.trim() : "Sistema");

        Pago guardado = pagoRepository.saveAndFlush(pago);
        if (dto.usuarioApp() != null && !dto.usuarioApp().isBlank()
                && dto.passwordApp() != null && !dto.passwordApp().isBlank()) {
            alumnoService.asignarCredenciales(alumno.getIdAlumno(), dto.usuarioApp(), dto.passwordApp());
        }
        sincronizarAsistenciaHoy(alumno);
        return mapToDto(guardado);
    }

    private YearMonth parsearPeriodoMesEstricto(String periodoMes) {
        try {
            return YearMonth.parse(periodoMes);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("El período de cuota debe tener formato YYYY-MM (ej: 2026-10).");
        }
    }

    private void validarSinSolapamientoDePeriodo(Long alumnoId, YearMonth nuevoInicio, YearMonth nuevoFin) {
        List<Pago> existentes = pagoRepository.findByAlumnoIdOrderByFechaDesc(alumnoId);
        for (Pago existente : existentes) {
            EstadoPago est = existente.getEstadoPago();
            if (est == EstadoPago.ANULADO) {
                continue;
            }
            if (est != EstadoPago.PAGADO && est != EstadoPago.PARCIAL) {
                continue;
            }
            YearMonth exInicio = obtenerPeriodoInicio(existente);
            YearMonth exFin = AlumnoService.obtenerPeriodoFinCubierto(existente);
            if (exInicio == null || exFin == null) {
                continue;
            }
            boolean solapa = !nuevoInicio.isAfter(exFin) && !nuevoFin.isBefore(exInicio);
            if (solapa) {
                YearMonth periodoSolapado = nuevoInicio.isAfter(exInicio) ? nuevoInicio : exInicio;
                if (est == EstadoPago.PARCIAL) {
                    throw new BusinessException(
                            "El socio ya registra una seña parcial para el período " + periodoSolapado
                                    + ". Utilice 'Completar saldo' sobre ese comprobante o anúlelo primero.",
                            409
                    );
                }
                throw new BusinessException(
                        "El socio ya tiene un pago acreditado que cubre el período " + periodoSolapado
                                + ". Si necesita corregirlo, anule primero el comprobante anterior.",
                        409
                );
            }
        }
    }

    private YearMonth obtenerPeriodoInicio(Pago p) {
        if (p.getPeriodoMes() != null && !p.getPeriodoMes().isBlank()) {
            try {
                return YearMonth.parse(p.getPeriodoMes().trim());
            } catch (DateTimeParseException ignored) {
            }
        }
        return p.getFecha() != null ? YearMonth.from(p.getFecha()) : null;
    }

    @Transactional
    public PagoResponseDto actualizarEstado(Long id, String nuevoEstadoStr) {
        return actualizarEstado(id, nuevoEstadoStr, null);
    }

    @Transactional
    public PagoResponseDto actualizarEstado(Long id, String nuevoEstadoStr, String operador) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));

        if (pago.getEstadoPago() == EstadoPago.ANULADO) {
            throw new BusinessException("Un comprobante anulado es inmutable por auditoría. Registre un nuevo cobro si corresponde.", 409);
        }

        EstadoPago nuevoEstado = parseEstadoPago(nuevoEstadoStr);
        if (nuevoEstado == EstadoPago.ANULADO) {
            throw new BusinessException("Para anular un pago debe utilizar la operación de anulación indicando el motivo obligatorio.");
        }

        pago.setEstadoPago(nuevoEstado);
        if (nuevoEstado == EstadoPago.PAGADO) {
            pago.setMontoAbonado(pago.getMonto());
            if (operador != null && !operador.isBlank() && (pago.getRegistradoPor() == null || pago.getRegistradoPor().isBlank())) {
                pago.setRegistradoPor(operador.trim());
            }
        } else if (nuevoEstado == EstadoPago.PENDIENTE || nuevoEstado == EstadoPago.VENCIDO) {
            pago.setMontoAbonado(BigDecimal.ZERO);
        }
        Pago actualizado = pagoRepository.saveAndFlush(pago);

        if (pago.getAlumno() != null) {
            sincronizarAsistenciaHoy(pago.getAlumno());
        }

        return mapToDto(actualizado);
    }

    @Transactional
    public PagoResponseDto completarSaldoParcial(Long id, PagoCompletarSaldoRequestDto req, String operador) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));

        if (pago.getEstadoPago() == EstadoPago.ANULADO) {
            throw new BusinessException("No se puede completar el saldo de un comprobante anulado.", 409);
        }
        if (pago.getEstadoPago() == EstadoPago.PAGADO) {
            throw new BusinessException("Este comprobante ya figura abonado en su totalidad.", 409);
        }

        BigDecimal seniaPrevia = pago.getMontoAbonado() != null ? pago.getMontoAbonado() : BigDecimal.ZERO;
        BigDecimal saldo = pago.getMonto().subtract(seniaPrevia).max(BigDecimal.ZERO);
        MetodoPago metodoOriginal = pago.getMetodoPago();

        if (req != null && req.metodo() != null && !req.metodo().isBlank()) {
            MetodoPago metodoSaldo = parseMetodoPago(req.metodo());
            pago.setMetodoPago(metodoSaldo);
            if (metodoOriginal != null && metodoOriginal != metodoSaldo && seniaPrevia.compareTo(BigDecimal.ZERO) > 0) {
                String detalle = String.format("Seña $%s (%s) + Saldo $%s (%s)",
                        seniaPrevia.stripTrailingZeros().toPlainString(),
                        metodoOriginal.name(),
                        saldo.stripTrailingZeros().toPlainString(),
                        metodoSaldo.name());
                pago.setComprobante(truncar(detalle, 255));
            }
        }
        if (req != null && req.comprobante() != null && !req.comprobante().isBlank()) {
            pago.setComprobante(truncar(req.comprobante().trim(), 255));
        }

        pago.setMontoAbonado(pago.getMonto());
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setFecha(LocalDate.now());
        if (operador != null && !operador.isBlank()) {
            pago.setRegistradoPor(operador.trim());
        }

        Pago actualizado = pagoRepository.saveAndFlush(pago);
        if (pago.getAlumno() != null) {
            sincronizarAsistenciaHoy(pago.getAlumno());
        }
        return mapToDto(actualizado);
    }

    @Transactional
    public PagoResponseDto anularPago(Long id, String motivo, String operador) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Debe ingresar el motivo de anulación del comprobante para auditoría.");
        }
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));

        if (pago.getEstadoPago() == EstadoPago.ANULADO) {
            return mapToDto(pago);
        }

        pago.setEstadoPago(EstadoPago.ANULADO);
        pago.setAnuladoPor(operador != null && !operador.isBlank() ? operador.trim() : "Sistema");
        pago.setFechaAnulacion(LocalDateTime.now());
        pago.setMotivoAnulacion(truncar(motivo.trim(), 255));

        Pago anulado = pagoRepository.saveAndFlush(pago);
        if (pago.getAlumno() != null) {
            sincronizarAsistenciaHoy(pago.getAlumno());
        }
        return mapToDto(anulado);
    }

    /**
     * Endpoint legado DELETE /api/pagos/{id}: redirigido a baja lógica auditada (ANULADO)
     * para garantizar que jamás se borre físicamente un movimiento contable en MySQL.
     */
    @Transactional
    public void eliminar(Long id, String operador) {
        anularPago(id, "Anulación administrativa", operador);
    }

    @Transactional
    public void eliminar(Long id) {
        eliminar(id, "Sistema");
    }

    @Transactional(readOnly = true)
    public ArqueoCajaDto calcularArqueoDiario(LocalDate fechaConsulta) {
        LocalDate fecha = fechaConsulta != null ? fechaConsulta : LocalDate.now();
        List<Pago> movimientosDelDia = pagoRepository.findByFechaWithRelaciones(fecha);

        LocalDateTime inicioDia = fecha.atStartOfDay();
        LocalDateTime finDia = fecha.plusDays(1).atStartOfDay();
        List<Pago> anuladosHoy = pagoRepository.findAnuladosEnVentana(inicioDia, finDia);

        BigDecimal totalRecaudado = BigDecimal.ZERO;
        BigDecimal totalEfectivo = BigDecimal.ZERO;
        BigDecimal totalTransferencia = BigDecimal.ZERO;
        BigDecimal totalTarjeta = BigDecimal.ZERO;
        int cantidadCobros = 0;
        int cantidadParciales = 0;
        BigDecimal saldoPendienteParciales = BigDecimal.ZERO;

        Map<String, BigDecimal> recaudadoPorOperador = new LinkedHashMap<>();
        Map<String, Integer> opsPorOperador = new LinkedHashMap<>();
        Set<Long> idsAnuladosContabilizados = new LinkedHashSet<>();
        BigDecimal montoAnulado = BigDecimal.ZERO;

        for (Pago p : movimientosDelDia) {
            EstadoPago estado = p.getEstadoPago() != null ? p.getEstadoPago() : EstadoPago.PAGADO;
            if (estado == EstadoPago.ANULADO) {
                idsAnuladosContabilizados.add(p.getIdPago());
                BigDecimal importeOriginal = p.getMontoAbonado() != null && p.getMontoAbonado().compareTo(BigDecimal.ZERO) > 0
                        ? p.getMontoAbonado()
                        : (p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO);
                montoAnulado = montoAnulado.add(importeOriginal);
                continue;
            }

            if (estado != EstadoPago.PAGADO && estado != EstadoPago.PARCIAL) {
                continue;
            }

            BigDecimal cobrado = p.getMontoRecaudadoEfectivo();
            if (cobrado.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            totalRecaudado = totalRecaudado.add(cobrado);
            cantidadCobros++;

            MetodoPago metodo = p.getMetodoPago() != null ? p.getMetodoPago() : MetodoPago.EFECTIVO;
            if (metodo == MetodoPago.TRANSFERENCIA) {
                totalTransferencia = totalTransferencia.add(cobrado);
            } else if (metodo == MetodoPago.TARJETA) {
                totalTarjeta = totalTarjeta.add(cobrado);
            } else {
                totalEfectivo = totalEfectivo.add(cobrado);
            }

            if (estado == EstadoPago.PARCIAL && p.getMonto() != null) {
                cantidadParciales++;
                saldoPendienteParciales = saldoPendienteParciales.add(
                        p.getMonto().subtract(cobrado).max(BigDecimal.ZERO)
                );
            }

            String op = (p.getRegistradoPor() != null && !p.getRegistradoPor().isBlank())
                    ? p.getRegistradoPor().trim()
                    : "Sin operador";
            recaudadoPorOperador.merge(op, cobrado, BigDecimal::add);
            opsPorOperador.merge(op, 1, Integer::sum);
        }

        for (Pago pAnulado : anuladosHoy) {
            if (idsAnuladosContabilizados.add(pAnulado.getIdPago())) {
                BigDecimal importeOriginal = pAnulado.getMontoAbonado() != null && pAnulado.getMontoAbonado().compareTo(BigDecimal.ZERO) > 0
                        ? pAnulado.getMontoAbonado()
                        : (pAnulado.getMonto() != null ? pAnulado.getMonto() : BigDecimal.ZERO);
                montoAnulado = montoAnulado.add(importeOriginal);
            }
        }

        List<ArqueoOperadorDto> desgloseOperadores = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : recaudadoPorOperador.entrySet()) {
            desgloseOperadores.add(new ArqueoOperadorDto(
                    entry.getKey(),
                    entry.getValue(),
                    opsPorOperador.getOrDefault(entry.getKey(), 0)
            ));
        }
        desgloseOperadores.sort(Comparator.comparing(ArqueoOperadorDto::totalRecaudado).reversed());

        return new ArqueoCajaDto(
                fecha,
                totalRecaudado,
                totalEfectivo,
                totalTransferencia,
                totalTarjeta,
                cantidadCobros,
                cantidadParciales,
                saldoPendienteParciales,
                idsAnuladosContabilizados.size(),
                montoAnulado,
                desgloseOperadores
        );
    }

    private String truncar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    private void sincronizarAsistenciaHoy(Alumno alumno) {
        EstadoCuenta nuevoEstadoCuenta = alumnoService.calcularEstadoCuenta(alumno);
        List<Asistencia> asistenciasHoy = asistenciaRepository
                .findByAlumnoIdAndFechaOrderByHoraDesc(alumno.getIdAlumno(), LocalDate.now());
        for (Asistencia a : asistenciasHoy) {
            a.setEstadoCuenta(nuevoEstadoCuenta);
            asistenciaRepository.save(a);
        }
    }

    @Transactional(readOnly = true)
    public List<PagoResponseDto> listarPorAlumno(Long alumnoId) {
        return pagoRepository.findByAlumnoIdOrderByFechaDesc(alumnoId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PagoResponseDto> listarPorMes(String periodoMes) {
        return pagoRepository.findByPeriodoMesWithRelaciones(periodoMes).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PagoResponseDto> listarTodos() {
        return pagoRepository.findAllWithRelacionesOrderByFechaDesc().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagoResponseDto buscarPorId(Long id) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));
        return mapToDto(pago);
    }

    private MetodoPago parseMetodoPago(String metodoStr) {
        if (metodoStr == null) return MetodoPago.EFECTIVO;
        String clean = metodoStr.toUpperCase().trim();
        if (clean.contains("TRANSF") || clean.contains("MERCADO")) return MetodoPago.TRANSFERENCIA;
        if (clean.contains("TARJ") || clean.contains("DEBIT") || clean.contains("CREDIT")) return MetodoPago.TARJETA;
        try {
            return MetodoPago.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return MetodoPago.EFECTIVO;
        }
    }

    private EstadoPago parseEstadoPago(String estadoStr) {
        if (estadoStr == null) return EstadoPago.PAGADO;
        String clean = estadoStr.toUpperCase().trim();
        if (clean.startsWith("PARC") || clean.contains("SENIA") || clean.contains("SEÑA")) return EstadoPago.PARCIAL;
        if (clean.startsWith("ANUL")) return EstadoPago.ANULADO;
        if (clean.startsWith("PEND")) return EstadoPago.PENDIENTE;
        if (clean.startsWith("VENC") || clean.startsWith("MOROS")) return EstadoPago.VENCIDO;
        try {
            return EstadoPago.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return EstadoPago.PAGADO;
        }
    }

    private PagoResponseDto mapToDto(Pago p) {
        String planNombre = p.getPlanPago() != null ? p.getPlanPago().getNombre() : "Sin plan";
        String alumnoNombre = p.getAlumnoNombreHistorico() != null
                ? p.getAlumnoNombreHistorico()
                : (p.getAlumno() != null ? p.getAlumno().getNombre() : "Desconocido");

        EstadoPago estado = p.getEstadoPago() != null ? p.getEstadoPago() : EstadoPago.PAGADO;
        BigDecimal montoTotal = p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO;
        BigDecimal montoAbonadoEfectivo = p.getMontoRecaudadoEfectivo();
        BigDecimal saldoPendiente;
        if (estado == EstadoPago.PAGADO || estado == EstadoPago.ANULADO) {
            saldoPendiente = BigDecimal.ZERO;
        } else {
            saldoPendiente = montoTotal.subtract(montoAbonadoEfectivo).max(BigDecimal.ZERO);
        }

        return new PagoResponseDto(
                p.getIdPago(),
                p.getAlumno() != null ? p.getAlumno().getIdAlumno() : null,
                alumnoNombre,
                planNombre,
                montoTotal,
                montoAbonadoEfectivo,
                saldoPendiente,
                p.getFecha(),
                p.getMetodoPago() != null ? p.getMetodoPago().name() : MetodoPago.EFECTIVO.name(),
                estado.name(),
                p.getPeriodoMes(),
                p.getCantidadMeses(),
                p.getComprobante(),
                p.getRegistradoPor(),
                p.getAnuladoPor(),
                p.getFechaAnulacion(),
                p.getMotivoAnulacion()
        );
    }
}
