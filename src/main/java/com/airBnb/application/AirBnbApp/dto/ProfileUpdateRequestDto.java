package com.airBnb.application.AirBnbApp.dto;

import com.airBnb.application.AirBnbApp.entity.enums.Gender;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProfileUpdateRequestDto {
    private String name ;
    private LocalDate dateOfBirth ;
    private Gender gender ;
}
