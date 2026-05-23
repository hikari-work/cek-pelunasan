package utils

import (
	"fmt"

	"github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/mem"
)

// SystemSummary -> "12.50% | 512MB / 2048MB". Padanan dari SystemUtils Java.
// CPU diambil non-blocking (interval 0): hasil snapshot kasar; cocok untuk display
// status di chat bot, bukan untuk metrik akurat.
func SystemSummary() string {
	cpuPct := 0.0
	if vals, err := cpu.Percent(0, false); err == nil && len(vals) > 0 {
		cpuPct = vals[0]
	}

	totalMB, usedMB := uint64(0), uint64(0)
	if vm, err := mem.VirtualMemory(); err == nil {
		totalMB = vm.Total / (1024 * 1024)
		usedMB = vm.Used / (1024 * 1024)
	}

	return fmt.Sprintf("%.2f%% | %dMB / %dMB", cpuPct, usedMB, totalMB)
}
