package com.thiago.hotelinfo.controller;

import com.thiago.hotelinfo.dto.PriceResponse;
import com.thiago.hotelinfo.exception.NotFoundException;
import com.thiago.hotelinfo.model.ServiceType;
import com.thiago.hotelinfo.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {
    private final ServiceTypeRepository serviceTypeRepository;

    @GetMapping
    public PriceResponse getPrice(
        @RequestParam String serviceType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        ServiceType st = serviceTypeRepository.findByNameIgnoreCase(serviceType)
            .orElseThrow(() -> new NotFoundException("Service type not found: " + serviceType));

        LocalDate priceDate = (date != null) ? date : LocalDate.now();

        return new PriceResponse(
            st.getName(),
            priceDate,
            st.getPricePerSlot(),
            "BRL"
        );
    }
}
