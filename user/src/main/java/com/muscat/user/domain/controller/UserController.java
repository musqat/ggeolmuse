package com.muscat.user.domain.controller;

import com.muscat.user.domain.dto.ChangePasswordRequest;
import com.muscat.user.domain.dto.DeleteAccountRequest;
import com.muscat.user.domain.dto.UpdateProfileRequest;
import com.muscat.user.domain.entity.User;
import com.muscat.user.domain.repository.UserRepository;
import com.muscat.user.domain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final UserRepository userRepository;

  @GetMapping("/me")
  public ResponseEntity<User> getMyProfile(Authentication auth) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    String email = jwt.getClaimAsString("email");

    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    return ResponseEntity.ok(user);
  }


  @PutMapping("/me")
  public ResponseEntity<User> updateProfile(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    String email = jwt.getClaimAsString("email");

    User updatedUser = userService.updateProfile(email, request);
    return ResponseEntity.ok(updatedUser);
  }

  @PutMapping("/me/password")
  public ResponseEntity<String> changePassword(Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    String email = jwt.getClaimAsString("email");

    userService.changePassword(email, request);
    return ResponseEntity.ok("Password changed successfully");
  }

  @DeleteMapping("/me")
  public ResponseEntity<String> deleteAccount(Authentication auth, @Valid @RequestBody DeleteAccountRequest request) {
    Jwt jwt = (Jwt) auth.getPrincipal();
    String email = jwt.getClaimAsString("email");

    userService.deleteAccount(email, request.getPassword());
    return ResponseEntity.ok("Account deleted successfully");
  }

}
