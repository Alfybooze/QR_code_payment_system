package com.Alfy.xchange_Updated.Repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.Alfy.xchange_Updated.Models.Users;

@Repository
public interface UsersRepository extends JpaRepository<Users, Integer> {
    Optional<Users> findByEmail(String email); // Use 'email' if that is the correct field for login
    Optional<Users> findByUsername(String username); // Use 'username' if that is the correct field for login
    Optional<Users> findByUsernameAndPassword(String email, String password); // Use 'username' and 'password' if that is the correct field for login
    Optional<Users> getUsersById(Long id); // Use 'username' and 'email' if that is the correct field for login
    Optional<Users> findByResetToken(String token);
}


