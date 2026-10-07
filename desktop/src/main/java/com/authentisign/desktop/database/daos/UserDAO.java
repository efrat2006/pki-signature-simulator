package com.authentisign.desktop.database.daos;

import com.authentisign.desktop.database.entities.User;

import java.util.Optional;

public interface UserDAO extends CrudDao<User, Long>{

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    void updatePassword(Long userId, String newPasswordHash);
    void delete(Long id);
    Optional<User> findById(Long id);
    }
