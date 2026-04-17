package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.ProductionLineRequestDTO;
import com.pfe.sageline.dtos.response.ProductionLineResponseDTO;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.mappers.ProductionLineMapper;
import com.pfe.sageline.repository.ProductionLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductionLineService {
    
    private final ProductionLineRepository productionLineRepository;
    private final ProductionLineMapper productionLineMapper;
    
    public ProductionLineResponseDTO createProductionLine(ProductionLineRequestDTO requestDTO) {
        // Vérifier si le code existe déjà
        if (productionLineRepository.existsByCode(requestDTO.getCode())) {
            throw new RuntimeException("Production line code already exists");
        }
        
        ProductionLine line = productionLineMapper.toEntity(requestDTO);
        ProductionLine savedLine = productionLineRepository.save(line);
        return productionLineMapper.toResponseDTO(savedLine);
    }
    
    @Transactional(readOnly = true)
    public ProductionLineResponseDTO getProductionLineById(Long id) {
        ProductionLine line = productionLineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Production line not found with id: " + id));
        return productionLineMapper.toResponseDTO(line);
    }
    
    @Transactional(readOnly = true)
    public ProductionLineResponseDTO getProductionLineByCode(String code) {
        ProductionLine line = productionLineRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Production line not found with code: " + code));
        return productionLineMapper.toResponseDTO(line);
    }
    
    @Transactional(readOnly = true)
    public List<ProductionLineResponseDTO> getAllProductionLines() {
        return productionLineRepository.findAll().stream()
                .map(productionLineMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ProductionLineResponseDTO> getActiveProductionLines() {
        return productionLineRepository.findByActive(true).stream()
                .map(productionLineMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
    
    public ProductionLineResponseDTO updateProductionLine(Long id, ProductionLineRequestDTO requestDTO) {
        ProductionLine line = productionLineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Production line not found with id: " + id));
        
        // Vérifier code si modifié
        if (!line.getCode().equals(requestDTO.getCode()) 
                && productionLineRepository.existsByCode(requestDTO.getCode())) {
            throw new RuntimeException("Production line code already exists");
        }
        
        productionLineMapper.updateEntityFromDTO(requestDTO, line);
        ProductionLine updatedLine = productionLineRepository.save(line);
        return productionLineMapper.toResponseDTO(updatedLine);
    }
    
    public void deleteProductionLine(Long id) {
        if (!productionLineRepository.existsById(id)) {
            throw new RuntimeException("Production line not found with id: " + id);
        }
        productionLineRepository.deleteById(id);
    }
    
    public ProductionLineResponseDTO activateProductionLine(Long id) {
        ProductionLine line = productionLineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Production line not found with id: " + id));
        line.setActive(true);
        ProductionLine updatedLine = productionLineRepository.save(line);
        return productionLineMapper.toResponseDTO(updatedLine);
    }
    
    public ProductionLineResponseDTO deactivateProductionLine(Long id) {
        ProductionLine line = productionLineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Production line not found with id: " + id));
        line.setActive(false);
        ProductionLine updatedLine = productionLineRepository.save(line);
        return productionLineMapper.toResponseDTO(updatedLine);
    }
    public ProductionLineResponseDTO setLineActive(Long id, Boolean active) {
        if (active) {
            return activateProductionLine(id);
        } else {
            return deactivateProductionLine(id);
        }
    }
    public List<ProductionLineResponseDTO> getLinesByPhase(Long phaseId) {
        return productionLineRepository.findByPhaseId(phaseId).stream()
                .map(productionLineMapper::toResponseDTO).toList();
    }

    public List<ProductionLineResponseDTO> getLinesBySecteur(Long secteurId) {
        return productionLineRepository.findByPhaseSecteurId(secteurId).stream()
                .map(productionLineMapper::toResponseDTO).toList();
    }

    public boolean existsByCode(String code) {
        return productionLineRepository.existsByCode(code);
    }
}
