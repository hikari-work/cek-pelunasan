package org.cekpelunasan.repository;

import org.cekpelunasan.entity.Simulasi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulasiRepository extends JpaRepository<Simulasi, String> {

	List<Simulasi> findBySpk(String spk);
}
