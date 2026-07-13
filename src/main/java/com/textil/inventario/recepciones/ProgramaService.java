package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgramaService {

    private final ProgramaRepository programaRepository;
    private final ProgramaDetalleRepository programaDetalleRepository;
    private final RecepcionDetalleRepository recepcionDetalleRepository;
    private final EmpresaRepository empresaRepository;
    private final ColorRepository colorRepository;

    public List<Programa> listarProgramas() {
        return programaRepository.findAllByOrderByFechaDesc();
    }

    public Programa buscarPrograma(Long id) {
        return programaRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Programa crearPrograma(String numero, Long empresaId, LocalDate fecha, String observaciones,
                                   List<Long> colorIds, List<Integer> cantidades) {
        Programa p = new Programa();
        p.setNumero(numero.trim());
        p.setEmpresa(empresaRepository.findById(empresaId).orElseThrow());
        p.setFecha(fecha);
        p.setObservaciones(observaciones);
        p = programaRepository.save(p);

        for (int i = 0; i < colorIds.size(); i++) {
            if (colorIds.get(i) == null) continue;
            ProgramaDetalle pd = new ProgramaDetalle();
            pd.setPrograma(p);
            pd.setColor(colorRepository.findById(colorIds.get(i)).orElseThrow());
            pd.setCantidadSolicitada(cantidades.get(i));
            pd.setCantidadRecibida(0);
            programaDetalleRepository.save(pd);
        }

        return p;
    }

    public List<RecepcionDetalle> historialDeLinea(Long programaDetalleId) {
        return recepcionDetalleRepository.findByProgramaDetalleId(programaDetalleId);
    }
}
