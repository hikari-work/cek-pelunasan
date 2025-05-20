package org.cekpelunasan.repository;

import org.cekpelunasan.entity.KolekTas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KolekTasRepository extends JpaRepository<KolekTas, Long> {
	List<KolekTas> findByKelompokIgnoreCase(String kelompok);

	List<KolekTas> findByKantorIgnoreCase(String kantor);

	Page<KolekTas> findByKelompokIgnoreCase(String kelompok, Pageable pageable);

	Page<KolekTas> findByKantorIgnoreCase(String kantor, Pageable pageable);
}
