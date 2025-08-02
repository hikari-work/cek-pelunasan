package org.cekpelunasan.controller;


import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.service.repayment.RepaymentService;
import org.cekpelunasan.service.savings.SavingsService;
import org.cekpelunasan.utils.PenaltyUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Controller
public class RepaymentController {

	private static final Logger log = LoggerFactory.getLogger(RepaymentController.class);
	private final RepaymentService repaymentService;
	private final PenaltyUtils penaltyUtils;
	private final SavingsService savingsService;

	public RepaymentController(RepaymentService repaymentService, PenaltyUtils penaltyUtils, SavingsService savingsService) {
		this.repaymentService = repaymentService;
		this.penaltyUtils = penaltyUtils;
		this.savingsService = savingsService;
	}

	@GetMapping("/pelunasan")
	public String showWebApp(@RequestParam(required = false) String initData, Model model) {
		model.addAttribute("repayments", repaymentService.findAllLimited());
		return "pelunasan";
	}

	@GetMapping("/api/search/pelunasan")
	@ResponseBody
	public List<Repayment> searchRepayments(@RequestParam String searchTerm) {
		if (searchTerm == null || searchTerm.trim().isEmpty()) {
			log.info("Searching for all repayments...");
			List<Repayment> allLimited = repaymentService.findAllLimited();
			return getRepayments(allLimited);
		}
		List<Repayment> repayments = repaymentService.searchByIdOrNameLimited(searchTerm.trim());
		return getRepayments(repayments);
	}

	@NotNull
	private List<Repayment> getRepayments(List<Repayment> allLimited) {
		allLimited.forEach(r -> {
			Map<String, Long> penalty = penaltyUtils.penalty(r.getStartDate(), r.getPenaltyLoan(), r.getProduct(), r);
			r.setPenaltyLoan(penalty.get("penalty"));
			r.setTotalPay(r.getAmount() +  r.getPenaltyLoan() + r.getPenaltyRepayment() + r.getInterest());
		});
		return allLimited;
	}

	@GetMapping("/tabungan")
	public String showWebAppTabungan(@RequestParam(required = false) String initData, Model model) {
		model.addAttribute("repayments", savingsService.findAll());
		return "tabungan";
	}

	@GetMapping("/api/search/tabungan")
	@ResponseBody
	public List<Savings> searchTabungan(@RequestParam String searchTerm) {
		if (searchTerm == null || searchTerm.trim().isEmpty()) {
			log.info("Searching for all savings...");
			return savingsService.findAll();
		}
		return savingsService.findAllTabByNameOrNomor(searchTerm.trim());
	}


	@PostMapping("/api/initdata")
	public String initData(@RequestBody String initData) {
		log.info("initData: {}", initData);
		if (initData == null || initData.trim().isEmpty()) {
			return "OK";
		}
		String[] data = initData.split(",");
		if (data.length != 10) {
			return "OK";
		}
		log.info("initData: {}", initData);
		return "OK";
	}
}