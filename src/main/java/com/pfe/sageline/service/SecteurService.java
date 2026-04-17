package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.SecteurRequestDTO;
import com.pfe.sageline.dtos.response.SecteurResponseDTO;
import com.pfe.sageline.entity.Secteur;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.SecteurMapper;
import com.pfe.sageline.repository.SecteurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SecteurService {

    private final SecteurRepository secteurRepository;
    private final SecteurMapper secteurMapper;

    @Transactional(readOnly = true)
    public List<SecteurResponseDTO> findAll() {
        return secteurRepository.findAll().stream()
                .map(secteurMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SecteurResponseDTO> findAllActive() {
        return secteurRepository.findByActiveTrue().stream()
                .map(secteurMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SecteurResponseDTO findById(Long id) {
        Secteur secteur = secteurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Secteur", "id", id));
        return secteurMapper.toResponseDTO(secteur, true);
    }

    @Transactional(readOnly = true)
    public SecteurResponseDTO findByCode(String code) {
        Secteur secteur = secteurRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Secteur", "code", code));
        return secteurMapper.toResponseDTO(secteur, true);
    }

    public SecteurResponseDTO create(SecteurRequestDTO dto) {
        if (secteurRepository.existsByCode(dto.getCode().toUpperCase())) {
            throw new ValidationException("Un secteur avec le code '" + dto.getCode() + "' existe déjà");
        }
        Secteur secteur = secteurMapper.toEntity(dto);
        secteur = secteurRepository.save(secteur);
        return secteurMapper.toResponseDTO(secteur);
    }

    public SecteurResponseDTO update(Long id, SecteurRequestDTO dto) {
        Secteur secteur = secteurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Secteur", "id", id));

        if (!secteur.getCode().equalsIgnoreCase(dto.getCode())
                && secteurRepository.existsByCode(dto.getCode().toUpperCase())) {
            throw new ValidationException("Un secteur avec le code '" + dto.getCode() + "' existe déjà");
        }

        secteurMapper.updateEntity(secteur, dto);
        secteur = secteurRepository.save(secteur);
        return secteurMapper.toResponseDTO(secteur);
    }

    public void delete(Long id) {
        Secteur secteur = secteurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Secteur", "id", id));
        if (secteur.getPhases() != null && !secteur.getPhases().isEmpty()) {
            throw new ValidationException("Impossible de supprimer un secteur qui contient des phases");
        }
        secteurRepository.delete(secteur);
    }
}
