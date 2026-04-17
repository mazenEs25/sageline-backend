package com.pfe.sageline.service;


import com.pfe.sageline.dtos.request.ValidationZoneRequestDTO;
import com.pfe.sageline.dtos.response.ValidationZoneResponseDTO;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.mappers.ValidationZoneMapper;
import com.pfe.sageline.repository.ProductionLineRepository;
import com.pfe.sageline.repository.ValidationZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ValidationZoneService {
    
    private final ValidationZoneRepository validationZoneRepository;
    private final ProductionLineRepository productionLineRepository;
    private final ValidationZoneMapper validationZoneMapper;
    
    public ValidationZoneResponseDTO createValidationZone(ValidationZoneRequestDTO requestDTO) {
        // Vérifier si la ligne de production existe
        ProductionLine line = productionLineRepository.findById(requestDTO.getProductionLineId())
                .orElseThrow(() -> new RuntimeException("Production line not found"));
        
        // Vérifier si une zone avec ce nom existe déjà pour cette ligne
        if (validationZoneRepository.existsByNameAndProductionLineId(
                requestDTO.getName(), requestDTO.getProductionLineId())) {
            throw new RuntimeException("Validation zone with this name already exists for this production line");
        }
        
        ValidationZone zone = validationZoneMapper.toEntity(requestDTO);
        zone.setProductionLine(line);
        
        ValidationZone savedZone = validationZoneRepository.save(zone);
        return validationZoneMapper.toResponseDTO(savedZone);
    }
    
    @Transactional(readOnly = true)
    public ValidationZoneResponseDTO getValidationZoneById(Long id) {
        ValidationZone zone = validationZoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Validation zone not found with id: " + id));
        return validationZoneMapper.toResponseDTO(zone);
    }
    
    @Transactional(readOnly = true)
    public List<ValidationZoneResponseDTO> getAllValidationZones() {
        return validationZoneRepository.findAll().stream()
                .map(validationZoneMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ValidationZoneResponseDTO> getValidationZonesByProductionLine(Long lineId) {
        return validationZoneRepository.findByProductionLineId(lineId).stream()
                .map(validationZoneMapper::toResponseDTO)
                .collect(Collectors.toList());
    }
    
    public ValidationZoneResponseDTO updateValidationZone(Long id, ValidationZoneRequestDTO requestDTO) {
        ValidationZone zone = validationZoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Validation zone not found with id: " + id));
        
        // Vérifier si le nom est modifié et s'il existe déjà
        if (!zone.getName().equals(requestDTO.getName()) 
                && validationZoneRepository.existsByNameAndProductionLineId(
                        requestDTO.getName(), requestDTO.getProductionLineId())) {
            throw new RuntimeException("Validation zone with this name already exists for this production line");
        }
        
        validationZoneMapper.updateEntityFromDTO(requestDTO, zone);
        
        // Mettre à jour la ligne de production si changée
        if (!zone.getProductionLine().getId().equals(requestDTO.getProductionLineId())) {
            ProductionLine line = productionLineRepository.findById(requestDTO.getProductionLineId())
                    .orElseThrow(() -> new RuntimeException("Production line not found"));
            zone.setProductionLine(line);
        }
        
        ValidationZone updatedZone = validationZoneRepository.save(zone);
        return validationZoneMapper.toResponseDTO(updatedZone);
    }
    
    public void deleteValidationZone(Long id) {
        if (!validationZoneRepository.existsById(id)) {
            throw new RuntimeException("Validation zone not found with id: " + id);
        }
        validationZoneRepository.deleteById(id);
    }
    @Transactional(readOnly = true)
    public Long countValidationsByZone(Long zoneId) {
        return validationZoneRepository.countValidationsByZoneId(zoneId);
    }
}
