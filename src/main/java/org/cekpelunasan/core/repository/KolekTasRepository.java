package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.KolekTas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


@Repository
public interface KolekTasRepository extends JpaRepository<KolekTas, Long> {

	@Modifying
	@Query("DELETE FROM KolekTas")
	void deleteAllFast();

	Page<KolekTas> findByKelompokIgnoreCase(String kelompok, Pageable pageable);

}
