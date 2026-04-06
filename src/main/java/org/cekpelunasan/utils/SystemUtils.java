package org.cekpelunasan.utils;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

/**
 * Mengambil informasi penggunaan CPU dan memori dari sistem operasi tempat aplikasi berjalan.
 * <p>
 * Berguna untuk menampilkan status kesehatan server secara cepat, misalnya di pesan
 * status bot atau dashboard monitoring sederhana. Data diambil langsung dari JVM
 * melalui {@link OperatingSystemMXBean} sehingga tidak perlu menjalankan perintah
 * sistem operasi eksternal.
 * </p>
 */
public class SystemUtils {

	/**
	 * Menghasilkan satu baris ringkasan kondisi server berisi persentase CPU dan pemakaian memori.
	 * <p>
	 * Format yang dikembalikan: {@code "XX.XX% | XXXXmb / XXXXmb"}, misalnya
	 * {@code "12.50% | 512MB / 2048MB"}.
	 * </p>
	 * <p>
	 * Catatan: nilai CPU load adalah rata-rata sistem secara keseluruhan — bukan hanya
	 * proses JVM ini. Demikian juga memori yang dilaporkan adalah memori total OS,
	 * bukan hanya heap Java.
	 * </p>
	 *
	 * @return string ringkasan penggunaan CPU dan RAM dalam satu baris
	 */
	public String getSystemUtils() {
		OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		double cpuLoad = osBean.getCpuLoad() * 100;
		long totalMemory = osBean.getTotalMemorySize() / (1024 * 1024);
		long freeMemory = osBean.getFreeMemorySize() / (1024 * 1024);
		long usedMemory = totalMemory - freeMemory;

		return String.format("%.2f%% | %dMB / %dMB", cpuLoad, usedMemory, totalMemory);
	}
}
