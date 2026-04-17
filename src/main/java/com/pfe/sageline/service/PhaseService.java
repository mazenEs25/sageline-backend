package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.PhaseRequestDTO;
import com.pfe.sageline.dtos.response.PhaseResponseDTO;
import com.pfe.sageline.entity.Phase;
import com.pfe.sageline.entity.Secteur;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.PhaseMapper;
import com.pfe.sageline.repository.PhaseRepository;
import com.pfe.sageline.repository.SecteurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PhaseService {

    private final PhaseRepository phaseRepository;
    private final SecteurRepository secteurRepository;
    private final PhaseMapper phaseMapper;

    @Transactional(readOnly = true)
    public List<PhaseResponseDTO> findAll() {
        return phaseRepository.findAllByOrderBySecteurIdAscOrderIndexAsc().stream()
                .map(phaseMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PhaseResponseDTO> findBySecteurId(Long secteurId) {
        return phaseRepository.findBySecteurId(secteurId).stream()
                .map(phaseMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PhaseResponseDTO findById(Long id) {
        Phase phase = phaseRepository.findByIdWithSecteur(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phase", "id", id));
        return phaseMapper.toResponseDTO(phase);
    }

    public PhaseResponseDTO create(PhaseRequestDTO dto) {
        if (phaseRepository.existsByCode(dto.getCode().toUpperCase())) {
            throw new ValidationException("Une phase avec le code '" + dto.getCode() + "' existe déjà");
        }
        Secteur secteur = secteurRepository.findById(dto.getSecteurId())
                .orElseThrow(() -> new ResourceNotFoundException("Secteur", "id", dto.getSecteurId()));

        Phase phase = phaseMapper.toEntity(dto, secteur);
        phase = phaseRepository.save(phase);
        return phaseMapper.toResponseDTO(phase);
    }

    public PhaseResponseDTO update(Long id, PhaseRequestDTO dto) {
        Phase phase = phaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phase", "id", id));

        if (!phase.getCode().equalsIgnoreCase(dto.getCode())
                && phaseRepository.existsByCode(dto.getCode().toUpperCase())) {
            throw new ValidationException("Une phase avec le code '" + dto.getCode() + "' existe déjà");
        }

        phaseMapper.updateEntity(phase, dto);

        if (dto.getSecteurId() != null
                && (phase.getSecteur() == null || !dto.getSecteurId().equals(phase.getSecteur().getId()))) {
            Secteur secteur = secteurRepository.findById(dto.getSecteurId())
                    .orElseThrow(() -> new ResourceNotFoundException("Secteur", "id", dto.getSecteurId()));
            phase.setSecteur(secteur);
        }

        phase = phaseRepository.save(phase);
        return phaseMapper.toResponseDTO(phase);
    }

    public void delete(Long id) {
        Phase phase = phaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phase", "id", id));
        if (phase.getProductionLines() != null && !phase.getProductionLines().isEmpty()) {
            throw new ValidationException("Impossible de supprimer une phase qui contient des lignes");
        }
        phaseRepository.delete(phase);
    }
}
