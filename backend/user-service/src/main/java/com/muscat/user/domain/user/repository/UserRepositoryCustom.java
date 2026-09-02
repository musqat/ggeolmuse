package com.muscat.user.domain.user.repository;

import com.muscat.user.domain.user.entity.User;
import java.util.Optional;

public interface UserRepositoryCustom {

  // 이메일로 사용자 조회 (계정 정보 포함)
  Optional<User> findByEmailWithAccounts(String email);
}
