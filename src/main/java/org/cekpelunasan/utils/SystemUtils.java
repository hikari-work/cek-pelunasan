package org.cekpelunasan.utils;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
public class SystemUtils {

    public String getSystemUtils() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osBean.getCpuLoad() * 100;
        long totalMemory = osBean.getTotalMemorySize() / (1024 * 1024); // MB
        long freeMemory = osBean.getFreeMemorySize() / (1024 * 1024); // MB
        long usedMemory = totalMemory - freeMemory;

        return String.format("%.2f%% | %dMB / %dMB", cpuLoad, usedMemory, totalMemory);
    }
}
