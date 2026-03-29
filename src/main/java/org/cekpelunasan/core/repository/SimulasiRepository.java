package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Simulasi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulasiRepository extends JpaRepository<Simulasi, String> {

	@Modifying
	@Query("delete FROM Simulasi ")
	void deleteAllFast();

	List<Simulasi> findBySpk(String spk);

}
