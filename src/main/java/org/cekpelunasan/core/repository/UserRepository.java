package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	List<User> findByUserCode(String userCode);
}
