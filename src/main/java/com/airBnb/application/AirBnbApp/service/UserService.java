package com.airBnb.application.AirBnbApp.service;

import com.airBnb.application.AirBnbApp.dto.ProfileUpdateRequestDto;
import com.airBnb.application.AirBnbApp.dto.UserDto;
import com.airBnb.application.AirBnbApp.entity.User;

public interface UserService {

    User getUserById(Long id);

    void updateProfile(ProfileUpdateRequestDto profileUpdateRequestDto);

    UserDto getMyProfile();
}