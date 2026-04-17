package com.pfe.sageline.service;

import com.pfe.sageline.repository.ValidationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TicketCodeGenerator {

    private final ValidationRepository validationRepository;

    @Transactional
    public synchronized String generate() {
        int year = LocalDate.now().getYear();
        String prefix = "VAL-" + year + "-";
        int max = validationRepository.findMaxTicketNumber(prefix + "%");
        int next = max + 1;
        return String.format("%s%04d", prefix, next);
    }
}
