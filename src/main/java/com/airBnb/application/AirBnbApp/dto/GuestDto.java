package com.airBnb.application.AirBnbApp.dto;

import com.airBnb.application.AirBnbApp.entity.enums.Gender;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GuestDto {
    private Long id;
    private String name;
    private Gender gender;
    private LocalDate dateOfBirth;
}