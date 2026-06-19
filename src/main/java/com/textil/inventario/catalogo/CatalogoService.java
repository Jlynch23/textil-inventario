package com.textil.inventario.catalogo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final EmpresaRepository empresaRepository;
    private final UbicacionRepository ubicacionRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final ArticuloRepository articuloRepository;

    // EMPRESAS
    public List<Empresa> listarEmpresas() { return empresaRepository.findByActivoTrue(); }
    public Empresa guardarEmpresa(Empresa e) { return empresaRepository.save(e); }
    public Empresa buscarEmpresa(Long id) { return empresaRepository.findById(id).orElseThrow(); }

    // UBICACIONES
    public List<Ubicacion> listarUbicaciones() { return ubicacionRepository.findByActivoTrue(); }
    public Ubicacion guardarUbicacion(Ubicacion u) { return ubicacionRepository.save(u); }
    public Ubicacion buscarUbicacion(Long id) { return ubicacionRepository.findById(id).orElseThrow(); }

    // TIPOS DE TELA
    public List<TipoTela> listarTiposTela() { return tipoTelaRepository.findByActivoTrue(); }
    public TipoTela guardarTipoTela(TipoTela t) { return tipoTelaRepository.save(t); }

    // TÍTULOS
    public List<Titulo> listarTitulos() { return tituloRepository.findByActivoTrue(); }
    public Titulo guardarTitulo(Titulo t) { return tituloRepository.save(t); }

    // COLORES
    public List<Color> listarColores() { return colorRepository.findByActivoTrue(); }
    public Color guardarColor(Color c) { return colorRepository.save(c); }
    public Color buscarColor(Long id) { return colorRepository.findById(id).orElseThrow(); }

    // ARTÍCULOS
    public List<Articulo> listarArticulos() { return articuloRepository.findByActivoTrue(); }
    public Articulo guardarArticulo(Articulo a) { return articuloRepository.save(a); }
    public Articulo buscarArticulo(Long id) { return articuloRepository.findById(id).orElseThrow(); }
}
